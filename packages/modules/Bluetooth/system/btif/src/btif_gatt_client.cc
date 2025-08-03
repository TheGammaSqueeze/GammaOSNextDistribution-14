/******************************************************************************
 *
 *  Copyright 2009-2014 Broadcom Corporation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

/*******************************************************************************
 *
 *  Filename:      btif_gatt_client.c
 *
 *  Description:   GATT client implementation
 *
 ******************************************************************************/

#define LOG_TAG "bt_btif_gattc"

#include <base/at_exit.h>
#include <base/functional/bind.h>
#include <base/logging.h>
#include <base/threading/thread.h>
#include <bluetooth/log.h>
#include <hardware/bluetooth.h>
#include <hardware/bt_gatt.h>
#include <hardware/bt_gatt_types.h>

#include <string>

#include "bta/include/bta_sec_api.h"
#include "bta_api.h"
#include "bta_gatt_api.h"
#include "btif_common.h"
#include "btif_config.h"
#include "btif_gatt.h"
#include "btif_gatt_util.h"
#include "device/include/controller.h"
#include "include/check.h"
#include "os/log.h"
#include "osi/include/allocator.h"
#include "stack/include/acl_api.h"
#include "stack/include/acl_api_types.h"
#include "stack/include/main_thread.h"
#include "storage/config_keys.h"
#include "types/ble_address_with_type.h"
#include "types/bluetooth/uuid.h"
#include "types/bt_transport.h"
#include "types/raw_address.h"

using base::Bind;
using base::Owned;
using bluetooth::Uuid;
using std::vector;
using namespace bluetooth;

bool btif_get_address_type(const RawAddress& bda, tBLE_ADDR_TYPE* p_addr_type);
bool btif_get_device_type(const RawAddress& bda, int* p_device_type);

bt_status_t btif_gattc_test_command_impl(int command,
                                         const btgatt_test_params_t* params);
extern const btgatt_callbacks_t* bt_gatt_callbacks;

/*******************************************************************************
 *  Constants & Macros
 ******************************************************************************/
#define CLI_CBACK_WRAP_IN_JNI(P_CBACK, P_CBACK_WRAP)               \
  do {                                                             \
    if (bt_gatt_callbacks && bt_gatt_callbacks->client->P_CBACK) { \
      log::verbose("HAL bt_gatt_callbacks->client->{}", #P_CBACK); \
      do_in_jni_thread(P_CBACK_WRAP);                              \
    } else {                                                       \
      ASSERTC(0, "Callback is NULL", 0);                           \
    }                                                              \
  } while (0)

#define CLI_CBACK_IN_JNI(P_CBACK, ...)                                         \
  do {                                                                         \
    if (bt_gatt_callbacks && bt_gatt_callbacks->client->P_CBACK) {             \
      log::verbose("HAL bt_gatt_callbacks->client->{}", #P_CBACK);             \
      do_in_jni_thread(Bind(bt_gatt_callbacks->client->P_CBACK, __VA_ARGS__)); \
    } else {                                                                   \
      ASSERTC(0, "Callback is NULL", 0);                                       \
    }                                                                          \
  } while (0)

#define CHECK_BTGATT_INIT()                \
  do {                                     \
    if (bt_gatt_callbacks == NULL) {       \
      log::warn("BTGATT not initialized"); \
      return BT_STATUS_NOT_READY;          \
    } else {                               \
      log::debug("");                      \
    }                                      \
  } while (0)

namespace {

uint8_t rssi_request_client_if;

static void btif_gattc_upstreams_evt(uint16_t event, char* p_param) {
  log::debug("Event {} [{}]",
             gatt_client_event_text(static_cast<tBTA_GATTC_EVT>(event)), event);

  tBTA_GATTC* p_data = (tBTA_GATTC*)p_param;
  switch (event) {
    case BTA_GATTC_EXEC_EVT: {
      HAL_CBACK(bt_gatt_callbacks, client->execute_write_cb,
                p_data->exec_cmpl.conn_id, p_data->exec_cmpl.status);
      break;
    }

    case BTA_GATTC_SEARCH_CMPL_EVT: {
      HAL_CBACK(bt_gatt_callbacks, client->search_complete_cb,
                p_data->search_cmpl.conn_id, p_data->search_cmpl.status);
      break;
    }

    case BTA_GATTC_NOTIF_EVT: {
      btgatt_notify_params_t data;

      data.bda = p_data->notify.bda;
      memcpy(data.value, p_data->notify.value, p_data->notify.len);

      data.handle = p_data->notify.handle;
      data.is_notify = p_data->notify.is_notify;
      data.len = p_data->notify.len;

      HAL_CBACK(bt_gatt_callbacks, client->notify_cb, p_data->notify.conn_id,
                data);

      if (!p_data->notify.is_notify)
        BTA_GATTC_SendIndConfirm(p_data->notify.conn_id, p_data->notify.cid);

      break;
    }

    case BTA_GATTC_OPEN_EVT: {
      log::debug("BTA_GATTC_OPEN_EVT {}",
                 ADDRESS_TO_LOGGABLE_CSTR(p_data->open.remote_bda));
      HAL_CBACK(bt_gatt_callbacks, client->open_cb, p_data->open.conn_id,
                p_data->open.status, p_data->open.client_if,
                p_data->open.remote_bda);

      if (GATT_DEF_BLE_MTU_SIZE != p_data->open.mtu && p_data->open.mtu) {
        HAL_CBACK(bt_gatt_callbacks, client->configure_mtu_cb,
                  p_data->open.conn_id, p_data->open.status, p_data->open.mtu);
      }

      if (p_data->open.status == GATT_SUCCESS)
        btif_gatt_check_encrypted_link(p_data->open.remote_bda,
                                       p_data->open.transport);
      break;
    }

    case BTA_GATTC_CLOSE_EVT: {
      HAL_CBACK(bt_gatt_callbacks, client->close_cb, p_data->close.conn_id,
                p_data->close.status, p_data->close.client_if,
                p_data->close.remote_bda);
      break;
    }

    case BTA_GATTC_ACL_EVT:
    case BTA_GATTC_DEREG_EVT:
    case BTA_GATTC_SEARCH_RES_EVT:
    case BTA_GATTC_CANCEL_OPEN_EVT:
    case BTA_GATTC_SRVC_DISC_DONE_EVT:
      log::debug("Ignoring event ({})", event);
      break;

    case BTA_GATTC_CFG_MTU_EVT: {
      HAL_CBACK(bt_gatt_callbacks, client->configure_mtu_cb,
                p_data->cfg_mtu.conn_id, p_data->cfg_mtu.status,
                p_data->cfg_mtu.mtu);
      break;
    }

    case BTA_GATTC_CONGEST_EVT:
      HAL_CBACK(bt_gatt_callbacks, client->congestion_cb,
                p_data->congest.conn_id, p_data->congest.congested);
      break;

    case BTA_GATTC_PHY_UPDATE_EVT:
      HAL_CBACK(bt_gatt_callbacks, client->phy_updated_cb,
                p_data->phy_update.conn_id, p_data->phy_update.tx_phy,
                p_data->phy_update.rx_phy, p_data->phy_update.status);
      break;

    case BTA_GATTC_CONN_UPDATE_EVT:
      HAL_CBACK(bt_gatt_callbacks, client->conn_updated_cb,
                p_data->conn_update.conn_id, p_data->conn_update.interval,
                p_data->conn_update.latency, p_data->conn_update.timeout,
                p_data->conn_update.status);
      break;

    case BTA_GATTC_SRVC_CHG_EVT:
      HAL_CBACK(bt_gatt_callbacks, client->service_changed_cb,
                p_data->service_changed.conn_id);
      break;

    case BTA_GATTC_SUBRATE_CHG_EVT:
      HAL_CBACK(bt_gatt_callbacks, client->subrate_chg_cb,
                p_data->subrate_chg.conn_id, p_data->subrate_chg.subrate_factor,
                p_data->subrate_chg.latency, p_data->subrate_chg.cont_num,
                p_data->subrate_chg.timeout, p_data->subrate_chg.status);
      break;

    default:
      log::error("Unhandled event ({})!", event);
      break;
  }
}

static void bta_gattc_cback(tBTA_GATTC_EVT event, tBTA_GATTC* p_data) {
  log::debug("gatt client callback event:{} [{}]",
             gatt_client_event_text(event), event);
  bt_status_t status =
      btif_transfer_context(btif_gattc_upstreams_evt, (uint16_t)event,
                            (char*)p_data, sizeof(tBTA_GATTC), NULL);
  ASSERTC(status == BT_STATUS_SUCCESS, "Context transfer failed!", status);
}

void btm_read_rssi_cb(void* p_void) {
  tBTM_RSSI_RESULT* p_result = (tBTM_RSSI_RESULT*)p_void;

  if (!p_result) return;

  CLI_CBACK_IN_JNI(read_remote_rssi_cb, rssi_request_client_if,
                   p_result->rem_bda, p_result->rssi, p_result->status);
}

/*******************************************************************************
 *  Client API Functions
 ******************************************************************************/

static bt_status_t btif_gattc_register_app(const Uuid& uuid,
                                           bool eatt_support) {
  CHECK_BTGATT_INIT();

  return do_in_jni_thread(Bind(
      [](const Uuid& uuid, bool eatt_support) {
        BTA_GATTC_AppRegister(
            bta_gattc_cback,
            base::Bind(
                [](const Uuid& uuid, uint8_t client_id, uint8_t status) {
                  do_in_jni_thread(Bind(
                      [](const Uuid& uuid, uint8_t client_id, uint8_t status) {
                        HAL_CBACK(bt_gatt_callbacks, client->register_client_cb,
                                  status, client_id, uuid);
                      },
                      uuid, client_id, status));
                },
                uuid),
            eatt_support);
      },
      uuid, eatt_support));
}

static void btif_gattc_unregister_app_impl(int client_if) {
  BTA_GATTC_AppDeregister(client_if);
}

static bt_status_t btif_gattc_unregister_app(int client_if) {
  CHECK_BTGATT_INIT();
  return do_in_jni_thread(Bind(&btif_gattc_unregister_app_impl, client_if));
}

void btif_gattc_open_impl(int client_if, RawAddress address,
                          tBLE_ADDR_TYPE addr_type, bool is_direct,
                          int transport_p, bool opportunistic,
                          int initiating_phys) {
  int device_type = BT_DEVICE_TYPE_UNKNOWN;
  tBT_TRANSPORT transport = (tBT_TRANSPORT)BT_TRANSPORT_LE;

  if (addr_type == BLE_ADDR_RANDOM) {
    device_type = BT_DEVICE_TYPE_BLE;
    BTA_DmAddBleDevice(address, addr_type, device_type);
  } else {
    // Ensure device is in inquiry database
    addr_type = BLE_ADDR_PUBLIC;
    if (btif_get_address_type(address, &addr_type) &&
        btif_get_device_type(address, &device_type) &&
        device_type != BT_DEVICE_TYPE_BREDR) {
      BTA_DmAddBleDevice(address, addr_type, device_type);
    }
  }

  // Check for background connections
  if (!is_direct) {
    // Check for privacy 1.0 and 1.1 controller and do not start background
    // connection if RPA offloading is not supported, since it will not
    // connect after change of random address
    if (!controller_get_interface()->SupportsBlePrivacy() &&
        (addr_type == BLE_ADDR_RANDOM) && BTM_BLE_IS_RESOLVE_BDA(address)) {
      tBTM_BLE_VSC_CB vnd_capabilities;
      BTM_BleGetVendorCapabilities(&vnd_capabilities);
      if (!vnd_capabilities.rpa_offloading) {
        HAL_CBACK(bt_gatt_callbacks, client->open_cb, 0, BT_STATUS_UNSUPPORTED,
                  client_if, address);
        return;
      }
    }
  }

  // Determine transport
  if (transport_p != BT_TRANSPORT_AUTO) {
    transport = transport_p;
  } else {
    switch (device_type) {
      case BT_DEVICE_TYPE_BREDR:
        transport = BT_TRANSPORT_BR_EDR;
        break;

      case BT_DEVICE_TYPE_BLE:
        transport = BT_TRANSPORT_LE;
        break;

      case BT_DEVICE_TYPE_DUMO:
        if (addr_type == BLE_ADDR_RANDOM)
          transport = BT_TRANSPORT_LE;
        else
          transport = BT_TRANSPORT_BR_EDR;
        break;
      default:
        log::error("Unknown device type {}", device_type);
        break;
    }
  }

  // Connect!
  log::info("Transport={}, device type={}, address type ={}, phy={}", transport,
            device_type, addr_type, initiating_phys);
  tBTM_BLE_CONN_TYPE type =
      is_direct ? BTM_BLE_DIRECT_CONNECTION : BTM_BLE_BKG_CONNECT_ALLOW_LIST;
  BTA_GATTC_Open(client_if, address, addr_type, type, transport, opportunistic,
                 initiating_phys);
}

static bt_status_t btif_gattc_open(int client_if, const RawAddress& bd_addr,
                                   uint8_t addr_type, bool is_direct,
                                   int transport, bool opportunistic,
                                   int initiating_phys) {
  CHECK_BTGATT_INIT();
  // Closure will own this value and free it.
  return do_in_jni_thread(Bind(&btif_gattc_open_impl, client_if, bd_addr,
                               addr_type, is_direct, transport, opportunistic,
                               initiating_phys));
}

void btif_gattc_close_impl(int client_if, RawAddress address, int conn_id) {
  log::info("client_if={}, conn_id={}, address={}", client_if, conn_id,
            ADDRESS_TO_LOGGABLE_CSTR(address));
  // Disconnect established connections
  if (conn_id != 0) {
    BTA_GATTC_Close(conn_id);
  } else {
    BTA_GATTC_CancelOpen(client_if, address, true);
  }

  // Cancel pending background connections (remove from acceptlist)
  BTA_GATTC_CancelOpen(client_if, address, false);
}

static bt_status_t btif_gattc_close(int client_if, const RawAddress& bd_addr,
                                    int conn_id) {
  CHECK_BTGATT_INIT();
  return do_in_jni_thread(
      Bind(&btif_gattc_close_impl, client_if, bd_addr, conn_id));
}

static bt_status_t btif_gattc_refresh(int client_if,
                                      const RawAddress& bd_addr) {
  CHECK_BTGATT_INIT();
  return do_in_jni_thread(Bind(&BTA_GATTC_Refresh, bd_addr));
}

static bt_status_t btif_gattc_search_service(int conn_id,
                                             const Uuid* filter_uuid) {
  CHECK_BTGATT_INIT();

  if (filter_uuid) {
    Uuid* uuid = new Uuid(*filter_uuid);
    return do_in_jni_thread(
        Bind(&BTA_GATTC_ServiceSearchRequest, conn_id, base::Owned(uuid)));
  } else {
    return do_in_jni_thread(
        Bind(&BTA_GATTC_ServiceSearchRequest, conn_id, nullptr));
  }
}

static void btif_gattc_discover_service_by_uuid(int conn_id, const Uuid& uuid) {
  do_in_jni_thread(Bind(&BTA_GATTC_DiscoverServiceByUuid, conn_id, uuid));
}

void btif_gattc_get_gatt_db_impl(int conn_id) {
  btgatt_db_element_t* db = NULL;
  int count = 0;
  BTA_GATTC_GetGattDb(conn_id, 0x0000, 0xFFFF, &db, &count);

  HAL_CBACK(bt_gatt_callbacks, client->get_gatt_db_cb, conn_id, db, count);
  osi_free(db);
}

static bt_status_t btif_gattc_get_gatt_db(int conn_id) {
  CHECK_BTGATT_INIT();
  return do_in_jni_thread(Bind(&btif_gattc_get_gatt_db_impl, conn_id));
}

void read_char_cb(uint16_t conn_id, tGATT_STATUS status, uint16_t handle,
                  uint16_t len, uint8_t* value, void* data) {
  btgatt_read_params_t* params = new btgatt_read_params_t;
  params->value_type = 0x00 /* GATTC_READ_VALUE_TYPE_VALUE */;
  params->status = status;
  params->handle = handle;
  params->value.len = len;
  CHECK(len <= GATT_MAX_ATTR_LEN);
  if (len > 0) memcpy(params->value.value, value, len);

  // clang-tidy analyzer complains about |params| is leaked.  It doesn't know
  // that |param| will be freed by the callback function.
  CLI_CBACK_IN_JNI(read_characteristic_cb, conn_id, status, /* NOLINT */
                   base::Owned(params));
}

static bt_status_t btif_gattc_read_char(int conn_id, uint16_t handle,
                                        int auth_req) {
  CHECK_BTGATT_INIT();
  return do_in_jni_thread(Bind(&BTA_GATTC_ReadCharacteristic, conn_id, handle,
                               auth_req, read_char_cb, nullptr));
}

void read_using_char_uuid_cb(uint16_t conn_id, tGATT_STATUS status,
                             uint16_t handle, uint16_t len, uint8_t* value,
                             void* data) {
  btgatt_read_params_t* params = new btgatt_read_params_t;
  params->value_type = 0x00 /* GATTC_READ_VALUE_TYPE_VALUE */;
  params->status = status;
  params->handle = handle;
  params->value.len = len;
  CHECK(len <= GATT_MAX_ATTR_LEN);
  if (len > 0) memcpy(params->value.value, value, len);

  // clang-tidy analyzer complains about |params| is leaked.  It doesn't know
  // that |param| will be freed by the callback function.
  CLI_CBACK_IN_JNI(read_characteristic_cb, conn_id, status, /* NOLINT */
                   base::Owned(params));
}

static bt_status_t btif_gattc_read_using_char_uuid(int conn_id,
                                                   const Uuid& uuid,
                                                   uint16_t s_handle,
                                                   uint16_t e_handle,
                                                   int auth_req) {
  CHECK_BTGATT_INIT();
  return do_in_jni_thread(Bind(&BTA_GATTC_ReadUsingCharUuid, conn_id, uuid,
                               s_handle, e_handle, auth_req,
                               read_using_char_uuid_cb, nullptr));
}

void read_desc_cb(uint16_t conn_id, tGATT_STATUS status, uint16_t handle,
                  uint16_t len, uint8_t* value, void* data) {
  btgatt_read_params_t params;
  params.value_type = 0x00 /* GATTC_READ_VALUE_TYPE_VALUE */;
  params.status = status;
  params.handle = handle;
  params.value.len = len;
  CHECK(len <= GATT_MAX_ATTR_LEN);
  if (len > 0) memcpy(params.value.value, value, len);

  CLI_CBACK_IN_JNI(read_descriptor_cb, conn_id, status, params);
}

static bt_status_t btif_gattc_read_char_descr(int conn_id, uint16_t handle,
                                              int auth_req) {
  CHECK_BTGATT_INIT();
  return do_in_jni_thread(Bind(&BTA_GATTC_ReadCharDescr, conn_id, handle,
                               auth_req, read_desc_cb, nullptr));
}

void write_char_cb(uint16_t conn_id, tGATT_STATUS status, uint16_t handle,
                   uint16_t len, const uint8_t* value, void* data) {
  std::vector<uint8_t> val(value, value + len);
  CLI_CBACK_WRAP_IN_JNI(
      write_characteristic_cb,
      base::BindOnce(
          [](write_characteristic_callback cb, uint16_t conn_id,
             tGATT_STATUS status, uint16_t handle,
             std::vector<uint8_t> moved_value) {
            cb(conn_id, status, handle, moved_value.size(), moved_value.data());
          },
          bt_gatt_callbacks->client->write_characteristic_cb, conn_id, status,
          handle, std::move(val)));
}

static bt_status_t btif_gattc_write_char(int conn_id, uint16_t handle,
                                         int write_type, int auth_req,
                                         const uint8_t* val, size_t len) {
  CHECK_BTGATT_INIT();

  std::vector<uint8_t> value(val, val + len);

  if (value.size() > GATT_MAX_ATTR_LEN) value.resize(GATT_MAX_ATTR_LEN);

  return do_in_jni_thread(Bind(&BTA_GATTC_WriteCharValue, conn_id, handle,
                               write_type, std::move(value), auth_req,
                               write_char_cb, nullptr));
}

void write_descr_cb(uint16_t conn_id, tGATT_STATUS status, uint16_t handle,
                    uint16_t len, const uint8_t* value, void* data) {
  std::vector<uint8_t> val(value, value + len);

  CLI_CBACK_WRAP_IN_JNI(
      write_descriptor_cb,
      base::BindOnce(
          [](write_descriptor_callback cb, uint16_t conn_id,
             tGATT_STATUS status, uint16_t handle,
             std::vector<uint8_t> moved_value) {
            cb(conn_id, status, handle, moved_value.size(), moved_value.data());
          },
          bt_gatt_callbacks->client->write_descriptor_cb, conn_id, status,
          handle, std::move(val)));
}

static bt_status_t btif_gattc_write_char_descr(int conn_id, uint16_t handle,
                                               int auth_req, const uint8_t* val,
                                               size_t len) {
  CHECK_BTGATT_INIT();

  std::vector<uint8_t> value(val, val + len);

  if (value.size() > GATT_MAX_ATTR_LEN) value.resize(GATT_MAX_ATTR_LEN);

  return do_in_jni_thread(Bind(&BTA_GATTC_WriteCharDescr, conn_id, handle,
                               std::move(value), auth_req, write_descr_cb,
                               nullptr));
}

static bt_status_t btif_gattc_execute_write(int conn_id, int execute) {
  CHECK_BTGATT_INIT();
  return do_in_jni_thread(
      Bind(&BTA_GATTC_ExecuteWrite, conn_id, (uint8_t)execute));
}

static void btif_gattc_reg_for_notification_impl(tGATT_IF client_if,
                                                 const RawAddress& bda,
                                                 uint16_t handle) {
  tGATT_STATUS status =
      BTA_GATTC_RegisterForNotifications(client_if, bda, handle);

  // TODO(jpawlowski): conn_id is currently unused
  HAL_CBACK(bt_gatt_callbacks, client->register_for_notification_cb,
            /* conn_id */ 0, 1, status, handle);
}

bt_status_t btif_gattc_reg_for_notification(int client_if,
                                            const RawAddress& bd_addr,
                                            uint16_t handle) {
  CHECK_BTGATT_INIT();

  return do_in_jni_thread(
      Bind(base::IgnoreResult(&btif_gattc_reg_for_notification_impl), client_if,
           bd_addr, handle));
}

static void btif_gattc_dereg_for_notification_impl(tGATT_IF client_if,
                                                   const RawAddress& bda,
                                                   uint16_t handle) {
  tGATT_STATUS status =
      BTA_GATTC_DeregisterForNotifications(client_if, bda, handle);

  // TODO(jpawlowski): conn_id is currently unused
  HAL_CBACK(bt_gatt_callbacks, client->register_for_notification_cb,
            /* conn_id */ 0, 0, status, handle);
}

bt_status_t btif_gattc_dereg_for_notification(int client_if,
                                              const RawAddress& bd_addr,
                                              uint16_t handle) {
  CHECK_BTGATT_INIT();

  return do_in_jni_thread(
      Bind(base::IgnoreResult(&btif_gattc_dereg_for_notification_impl),
           client_if, bd_addr, handle));
}

static bt_status_t btif_gattc_read_remote_rssi(int client_if,
                                               const RawAddress& bd_addr) {
  CHECK_BTGATT_INIT();
  rssi_request_client_if = client_if;

  return do_in_jni_thread(
      Bind(base::IgnoreResult(&BTM_ReadRSSI), bd_addr, btm_read_rssi_cb));
}

static bt_status_t btif_gattc_configure_mtu(int conn_id, int mtu) {
  CHECK_BTGATT_INIT();
  return do_in_jni_thread(
      Bind(base::IgnoreResult(
        static_cast<void (*)(uint16_t,uint16_t)>(&BTA_GATTC_ConfigureMTU)),
        conn_id, mtu));
}

static void btif_gattc_conn_parameter_update_impl(
    RawAddress addr, int min_interval, int max_interval, int latency,
    int timeout, uint16_t min_ce_len, uint16_t max_ce_len) {
  if (BTA_DmGetConnectionState(addr))
    BTA_DmBleUpdateConnectionParams(addr, min_interval, max_interval, latency,
                                    timeout, min_ce_len, max_ce_len);
  else
    BTA_DmSetBlePrefConnParams(addr, min_interval, max_interval, latency,
                               timeout);
}

bt_status_t btif_gattc_conn_parameter_update(const RawAddress& bd_addr,
                                             int min_interval, int max_interval,
                                             int latency, int timeout,
                                             uint16_t min_ce_len,
                                             uint16_t max_ce_len) {
  CHECK_BTGATT_INIT();
  return do_in_jni_thread(Bind(
      base::IgnoreResult(&btif_gattc_conn_parameter_update_impl), bd_addr,
      min_interval, max_interval, latency, timeout, min_ce_len, max_ce_len));
}

static bt_status_t btif_gattc_set_preferred_phy(const RawAddress& bd_addr,
                                                uint8_t tx_phy, uint8_t rx_phy,
                                                uint16_t phy_options) {
  CHECK_BTGATT_INIT();
  do_in_main_thread(FROM_HERE,
                    Bind(&BTM_BleSetPhy, bd_addr, tx_phy, rx_phy, phy_options));
  return BT_STATUS_SUCCESS;
}

static bt_status_t btif_gattc_read_phy(
    const RawAddress& bd_addr,
    base::Callback<void(uint8_t tx_phy, uint8_t rx_phy, uint8_t status)> cb) {
  CHECK_BTGATT_INIT();
  do_in_main_thread(FROM_HERE, Bind(&BTM_BleReadPhy, bd_addr,
                                    jni_thread_wrapper(FROM_HERE, cb)));
  return BT_STATUS_SUCCESS;
}

static int btif_gattc_get_device_type(const RawAddress& bd_addr) {
  int device_type = 0;

  if (btif_config_get_int(bd_addr.ToString().c_str(), BTIF_STORAGE_KEY_DEV_TYPE,
                          &device_type))
    return device_type;
  return 0;
}

static bt_status_t btif_gattc_test_command(int command,
                                           const btgatt_test_params_t& params) {
  return btif_gattc_test_command_impl(command, &params);
}

static void btif_gattc_subrate_request_impl(RawAddress addr, int subrate_min,
                                            int subrate_max, int max_latency,
                                            int cont_num, int sup_timeout) {
  if (BTA_DmGetConnectionState(addr)) {
    BTA_DmBleSubrateRequest(addr, subrate_min, subrate_max, max_latency,
                            cont_num, sup_timeout);
  }
}

static bt_status_t btif_gattc_subrate_request(const RawAddress& bd_addr,
                                              int subrate_min, int subrate_max,
                                              int max_latency, int cont_num,
                                              int sup_timeout) {
  CHECK_BTGATT_INIT();
  return do_in_jni_thread(
      Bind(base::IgnoreResult(&btif_gattc_subrate_request_impl), bd_addr,
           subrate_min, subrate_max, max_latency, cont_num, sup_timeout));
}

}  // namespace

const btgatt_client_interface_t btgattClientInterface = {
    btif_gattc_register_app,
    btif_gattc_unregister_app,
    btif_gattc_open,
    btif_gattc_close,
    btif_gattc_refresh,
    btif_gattc_search_service,
    btif_gattc_discover_service_by_uuid,
    btif_gattc_read_char,
    btif_gattc_read_using_char_uuid,
    btif_gattc_write_char,
    btif_gattc_read_char_descr,
    btif_gattc_write_char_descr,
    btif_gattc_execute_write,
    btif_gattc_reg_for_notification,
    btif_gattc_dereg_for_notification,
    btif_gattc_read_remote_rssi,
    btif_gattc_get_device_type,
    btif_gattc_configure_mtu,
    btif_gattc_conn_parameter_update,
    btif_gattc_set_preferred_phy,
    btif_gattc_read_phy,
    btif_gattc_test_command,
    btif_gattc_get_gatt_db,
    btif_gattc_subrate_request,
};
