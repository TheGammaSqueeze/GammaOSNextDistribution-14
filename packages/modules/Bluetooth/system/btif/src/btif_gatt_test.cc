/******************************************************************************
 *
 *  Copyright 2009-2013 Broadcom Corporation
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

#define LOG_TAG "bt_btif_gatt"

#include <bluetooth/log.h>
#include <hardware/bluetooth.h>
#include <hardware/bt_gatt.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "gatt_api.h"
#include "internal_include/bte_appl.h"
#include "os/log.h"
#include "stack/include/btm_ble_sec_api.h"
#include "types/ble_address_with_type.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

using bluetooth::Uuid;
using namespace bluetooth;

/*******************************************************************************
 * Typedefs & Macros
 ******************************************************************************/

typedef struct {
  tGATT_IF gatt_if;
  uint16_t conn_id;
} btif_test_cb_t;

/*******************************************************************************
 * Static variables
 ******************************************************************************/

static const char* disc_name[GATT_DISC_MAX] = {"Unknown",
                                               "GATT_DISC_SRVC_ALL",
                                               "GATT_DISC_SRVC_BY_UUID",
                                               "GATT_DISC_INC_SRVC",
                                               "GATT_DISC_CHAR",
                                               "GATT_DISC_CHAR_DSCPT"};

static btif_test_cb_t test_cb;

/*******************************************************************************
 * Callback functions
 ******************************************************************************/

static void btif_test_connect_cback(tGATT_IF, const RawAddress&,
                                    uint16_t conn_id, bool connected,
                                    tGATT_DISCONN_REASON, tBT_TRANSPORT) {
  log::info("conn_id={}, connected={}", conn_id, connected);
  test_cb.conn_id = connected ? conn_id : 0;
}

static void btif_test_command_complete_cback(uint16_t conn_id, tGATTC_OPTYPE op,
                                             tGATT_STATUS status,
                                             tGATT_CL_COMPLETE* p_data) {
  log::info("op_code=0x{:02x}, conn_id=0x{:x}. status=0x{:x}", op, conn_id,
            status);

  switch (op) {
    case GATTC_OPTYPE_READ:
    case GATTC_OPTYPE_WRITE:
    case GATTC_OPTYPE_CONFIG:
    case GATTC_OPTYPE_EXE_WRITE:
    case GATTC_OPTYPE_NOTIFICATION:
      break;

    case GATTC_OPTYPE_INDICATION:
      GATTC_SendHandleValueConfirm(conn_id, p_data->cid);
      break;

    default:
      log::info("Unknown op_code (0x{:02x})", op);
      break;
  }
}

static void btif_test_discovery_result_cback(uint16_t /* conn_id */,
                                             tGATT_DISC_TYPE disc_type,
                                             tGATT_DISC_RES* p_data) {
  log::info("------ GATT Discovery result {:<22s} -------",
            disc_name[disc_type]);
  log::info("Attribute handle: 0x{:04x} ({})", p_data->handle, p_data->handle);

  if (disc_type != GATT_DISC_CHAR_DSCPT) {
    log::info("Attribute type: {}", p_data->type.ToString());
  }

  switch (disc_type) {
    case GATT_DISC_SRVC_ALL:
      log::info("Handle range: 0x{:04x} ~ 0x{:04x} ({} ~ {})", p_data->handle,
                p_data->value.group_value.e_handle, p_data->handle,
                p_data->value.group_value.e_handle);
      log::info("Service UUID: {}",
                p_data->value.group_value.service_type.ToString());
      break;

    case GATT_DISC_SRVC_BY_UUID:
      log::info("Handle range: 0x{:04x} ~ 0x{:04x} ({} ~ {})", p_data->handle,
                p_data->value.handle, p_data->handle, p_data->value.handle);
      break;

    case GATT_DISC_INC_SRVC:
      log::info("Handle range: 0x{:04x} ~ 0x{:04x} ({} ~ {})",
                p_data->value.incl_service.s_handle,
                p_data->value.incl_service.e_handle,
                p_data->value.incl_service.s_handle,
                p_data->value.incl_service.e_handle);
      log::info("Service UUID: {}",
                p_data->value.incl_service.service_type.ToString());
      break;

    case GATT_DISC_CHAR:
      log::info("Properties: 0x{:02x}", p_data->value.dclr_value.char_prop);
      log::info("Characteristic UUID: {}",
                p_data->value.dclr_value.char_uuid.ToString());
      break;

    case GATT_DISC_CHAR_DSCPT:
      log::info("Descriptor UUID: {}", p_data->type.ToString());
      break;
    case GATT_DISC_MAX:
      log::error("Unknown discovery item");
      break;
  }

  log::info("-----------------------------------------------------------");
}

static void btif_test_discovery_complete_cback(uint16_t /* conn_id */,
                                               tGATT_DISC_TYPE /* disc_type */,
                                               tGATT_STATUS status) {
  log::info("status={}", status);
}

static tGATT_CBACK btif_test_callbacks = {
    btif_test_connect_cback,
    btif_test_command_complete_cback,
    btif_test_discovery_result_cback,
    btif_test_discovery_complete_cback,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
};

/*******************************************************************************
 * Implementation
 ******************************************************************************/

bt_status_t btif_gattc_test_command_impl(int command,
                                         const btgatt_test_params_t* params) {
  switch (command) {
    case 0x01: /* Enable */
    {
      log::info("ENABLE - enable={}", params->u1);
      if (params->u1) {
        std::array<uint8_t, Uuid::kNumBytes128> tmp;
        tmp.fill(0xAE);
        test_cb.gatt_if =
            GATT_Register(bluetooth::Uuid::From128BitBE(tmp),
                          std::string("GattTest"), &btif_test_callbacks, false);
        GATT_StartIf(test_cb.gatt_if);
      } else {
        GATT_Deregister(test_cb.gatt_if);
        test_cb.gatt_if = 0;
      }
      break;
    }

    case 0x02: /* Connect */
    {
      log::info("CONNECT - device={} (dev_type={}, addr_type={})",
                ADDRESS_TO_LOGGABLE_CSTR(*params->bda1), params->u1,
                params->u2);

      if (params->u1 == BT_DEVICE_TYPE_BLE)
        BTM_SecAddBleDevice(*params->bda1, BT_DEVICE_TYPE_BLE,
                            static_cast<tBLE_ADDR_TYPE>(params->u2));

      if (!GATT_Connect(test_cb.gatt_if, *params->bda1,
                        BTM_BLE_DIRECT_CONNECTION, BT_TRANSPORT_LE, false)) {
        log::error("GATT_Connect failed!");
      }
      break;
    }

    case 0x03: /* Disconnect */
    {
      log::info("DISCONNECT - conn_id={}", test_cb.conn_id);
      GATT_Disconnect(test_cb.conn_id);
      break;
    }

    case 0x04: /* Discover */
    {
      if (params->u1 >= GATT_DISC_MAX) {
        log::error("DISCOVER - Invalid type ({})!", params->u1);
        return (bt_status_t)0;
      }

      log::info("DISCOVER ({}), conn_id={}, uuid={}, handles=0x{:04x}-0x{:04x}",
                disc_name[params->u1], test_cb.conn_id,
                params->uuid1->ToString(), params->u2, params->u3);
      GATTC_Discover(test_cb.conn_id, static_cast<tGATT_DISC_TYPE>(params->u1),
                     params->u2, params->u3, *params->uuid1);
      break;
    }

    case 0xF0: /* Pairing configuration */
      log::info("Setting pairing config auth={}, iocaps={}, keys={}/{}/{}",
                params->u1, params->u2, params->u3, params->u4, params->u5);

      bte_appl_cfg.ble_auth_req = params->u1;
      bte_appl_cfg.ble_io_cap = params->u2;
      bte_appl_cfg.ble_init_key = params->u3;
      bte_appl_cfg.ble_resp_key = params->u4;
      bte_appl_cfg.ble_max_key_size = params->u5;
      break;

    default:
      log::error("UNKNOWN TEST COMMAND 0x{:02x}", command);
      break;
  }
  return (bt_status_t)0;
}
