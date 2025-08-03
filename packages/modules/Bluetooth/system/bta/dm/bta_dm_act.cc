/******************************************************************************
 *
 *  Copyright 2003-2014 Broadcom Corporation
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

/******************************************************************************
 *
 *  This file contains the action functions for device manager state
 *  machine.
 *
 ******************************************************************************/

#define LOG_TAG "bt_bta_dm"

#include <android_bluetooth_flags.h>
#include <android_bluetooth_sysprop.h>
#include <base/location.h>
#include <base/logging.h>
#include <bluetooth/log.h>

#include <cstdint>
#include <vector>

#include "bta/dm/bta_dm_disc.h"
#include "bta/dm/bta_dm_gatt_client.h"
#include "bta/dm/bta_dm_int.h"
#include "bta/dm/bta_dm_sec_int.h"
#include "bta/include/bta_api.h"
#include "bta/include/bta_le_audio_api.h"
#include "bta/include/bta_sdp_api.h"
#include "bta/include/bta_sec_api.h"
#include "bta/sys/bta_sys.h"
#include "btif/include/btif_dm.h"
#include "btif/include/stack_manager_t.h"
#include "device/include/controller.h"
#include "hci/controller_interface.h"
#include "include/bind_helpers.h"
#include "include/check.h"
#include "internal_include/bt_target.h"
#include "main/shim/acl_api.h"
#include "main/shim/btm_api.h"
#include "main/shim/entry.h"
#include "osi/include/allocator.h"
#include "osi/include/osi.h"  // UNUSED_ATTR
#include "osi/include/properties.h"
#include "stack/gatt/connection_manager.h"
#include "stack/include/acl_api.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/bt_types.h"
#include "stack/include/bt_uuid16.h"
#include "stack/include/btm_client_interface.h"
#include "stack/include/gatt_api.h"
#include "stack/include/l2c_api.h"
#include "stack/include/main_thread.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

using bluetooth::Uuid;
using namespace bluetooth;

bool ble_vnd_is_included();
void BTIF_dm_disable();
void BTIF_dm_enable();
void btm_ble_scanner_init(void);

static void bta_dm_local_name_cback(void* p_name);
static void bta_dm_check_av();

void BTA_dm_update_policy(tBTA_SYS_CONN_STATUS status, uint8_t id,
                          uint8_t app_id, const RawAddress& peer_addr);

/* Extended Inquiry Response */
static void bta_dm_set_eir(char* local_name);

static void bta_dm_disable_conn_down_timer_cback(void* data);
static void bta_dm_rm_cback(tBTA_SYS_CONN_STATUS status, tBTA_SYS_ID id,
                            uint8_t app_id, const RawAddress& peer_addr);
static void bta_dm_adjust_roles(bool delay_role_switch);
tBTM_CONTRL_STATE bta_dm_pm_obtain_controller_state(void);
static void bta_dm_ctrl_features_rd_cmpl_cback(tHCI_STATUS result);

static const char kPropertySniffOffloadEnabled[] =
    "bluetooth.sniff_offload.enabled";

#ifndef BTA_DM_BLE_ADV_CHNL_MAP
#define BTA_DM_BLE_ADV_CHNL_MAP \
  (BTM_BLE_ADV_CHNL_37 | BTM_BLE_ADV_CHNL_38 | BTM_BLE_ADV_CHNL_39)
#endif

/* Disable timer interval (in milliseconds) */
#ifndef BTA_DM_DISABLE_TIMER_MS
#define BTA_DM_DISABLE_TIMER_MS (2000)
#endif

/* Disable timer retrial interval (in milliseconds) */
#ifndef BTA_DM_DISABLE_TIMER_RETRIAL_MS
#define BTA_DM_DISABLE_TIMER_RETRIAL_MS 1500
#endif

/* Disable connection down timer (in milliseconds) */
#ifndef BTA_DM_DISABLE_CONN_DOWN_TIMER_MS
#define BTA_DM_DISABLE_CONN_DOWN_TIMER_MS 100
#endif

/* Switch delay timer (in milliseconds) */
#ifndef BTA_DM_SWITCH_DELAY_TIMER_MS
#define BTA_DM_SWITCH_DELAY_TIMER_MS 500
#endif

/* Sysprop path for page timeout */
#ifndef PROPERTY_PAGE_TIMEOUT
#define PROPERTY_PAGE_TIMEOUT "bluetooth.core.classic.page_timeout"
#endif

namespace {

struct WaitForAllAclConnectionsToDrain {
  uint64_t time_to_wait_in_ms;
  unsigned long TimeToWaitInMs() const {
    return static_cast<unsigned long>(time_to_wait_in_ms);
  }
  void* AlarmCallbackData() const {
    return const_cast<void*>(static_cast<const void*>(this));
  }

  static const WaitForAllAclConnectionsToDrain* FromAlarmCallbackData(
      void* data);
  static bool IsFirstPass(const WaitForAllAclConnectionsToDrain*);
} first_pass =
    {
        .time_to_wait_in_ms = static_cast<uint64_t>(BTA_DM_DISABLE_TIMER_MS),
},
  second_pass = {
      .time_to_wait_in_ms =
          static_cast<uint64_t>(BTA_DM_DISABLE_TIMER_RETRIAL_MS),
};

bool WaitForAllAclConnectionsToDrain::IsFirstPass(
    const WaitForAllAclConnectionsToDrain* pass) {
  return pass == &first_pass;
}

const WaitForAllAclConnectionsToDrain*
WaitForAllAclConnectionsToDrain::FromAlarmCallbackData(void* data) {
  return const_cast<const WaitForAllAclConnectionsToDrain*>(
      static_cast<WaitForAllAclConnectionsToDrain*>(data));
}

}  // namespace

static void bta_dm_delay_role_switch_cback(void* data);
static void bta_dm_wait_for_acl_to_drain_cback(void* data);

/** Initialises the BT device manager */
void bta_dm_enable(tBTA_DM_SEC_CBACK* p_sec_cback,
                   tBTA_DM_ACL_CBACK *p_acl_cback) {

  if (p_acl_cback != NULL) bta_dm_acl_cb.p_acl_cback = p_acl_cback;

  bta_dm_sec_enable(p_sec_cback);
}

/*******************************************************************************
 *
 * Function         bta_dm_init_cb
 *
 * Description      Initializes the bta_dm_cb control block
 *
 *
 * Returns          void
 *
 ******************************************************************************/
static void bta_dm_init_cb(void) {
  bta_dm_cb = {};

  bta_dm_cb.disable_timer = alarm_new("bta_dm.disable_timer");
  bta_dm_cb.switch_delay_timer = alarm_new("bta_dm.switch_delay_timer");
  for (size_t i = 0; i < BTA_DM_NUM_PM_TIMER; i++) {
    for (size_t j = 0; j < BTA_DM_PM_MODE_TIMER_MAX; j++) {
      bta_dm_cb.pm_timer[i].timer[j] = alarm_new("bta_dm.pm_timer");
    }
  }
}

/*******************************************************************************
 *
 * Function         bta_dm_deinit_cb
 *
 * Description      De-initializes the bta_dm_cb control block
 *
 *
 * Returns          void
 *
 ******************************************************************************/
static void bta_dm_deinit_cb(void) {
  /*
   * TODO: Should alarm_free() the bta_dm_cb timers during graceful
   * shutdown.
   */
  alarm_free(bta_dm_cb.disable_timer);
  alarm_free(bta_dm_cb.switch_delay_timer);
  for (size_t i = 0; i < BTA_DM_NUM_PM_TIMER; i++) {
    for (size_t j = 0; j < BTA_DM_PM_MODE_TIMER_MAX; j++) {
      alarm_free(bta_dm_cb.pm_timer[i].timer[j]);
    }
  }
  bta_dm_cb = {};
}

void BTA_dm_on_hw_off() {
  BTIF_dm_disable();

  /* reinitialize the control block */
  bta_dm_deinit_cb();

  bta_dm_disc_stop();
}

void BTA_dm_on_hw_on() {
  uint8_t key_mask = 0;
  tBTA_BLE_LOCAL_ID_KEYS id_key;

  /* make sure the control block is properly initialized */
  bta_dm_init_cb();

  bta_dm_disc_start(
      osi_property_get_bool("bluetooth.gatt.delay_close.enabled", true));

  memset(&bta_dm_conn_srvcs, 0, sizeof(bta_dm_conn_srvcs));
  memset(&bta_dm_di_cb, 0, sizeof(tBTA_DM_DI_CB));

  DEV_CLASS dev_class = btif_dm_get_local_class_of_device();
  log::info("Read default class of device [0x{:x}, 0x{:x}, 0x{:x}]",
            dev_class[0], dev_class[1], dev_class[2]);

  get_btm_client_interface().local.BTM_SetDeviceClass(dev_class);

  /* load BLE local information: ID keys, ER if available */
  Octet16 er;
  btif_dm_get_ble_local_keys(&key_mask, &er, &id_key);

  if (key_mask & BTA_BLE_LOCAL_KEY_TYPE_ER) {
    get_btm_client_interface().security.BTM_BleLoadLocalKeys(
        BTA_BLE_LOCAL_KEY_TYPE_ER, (tBTM_BLE_LOCAL_KEYS*)&er);
  }
  if (key_mask & BTA_BLE_LOCAL_KEY_TYPE_ID) {
    get_btm_client_interface().security.BTM_BleLoadLocalKeys(
        BTA_BLE_LOCAL_KEY_TYPE_ID, (tBTM_BLE_LOCAL_KEYS*)&id_key);
  }

  btm_dm_sec_init();
  btm_sec_on_hw_on();

  get_btm_client_interface().link_policy.BTM_WritePageTimeout(
      osi_property_get_int32(PROPERTY_PAGE_TIMEOUT,
                             p_bta_dm_cfg->page_timeout));

  if (ble_vnd_is_included()) {
    get_btm_client_interface().ble.BTM_BleReadControllerFeatures(
        bta_dm_ctrl_features_rd_cmpl_cback);
  } else {
    /* Set controller features even if vendor support is not included */
    if (bta_dm_acl_cb.p_acl_cback)
      bta_dm_acl_cb.p_acl_cback(BTA_DM_LE_FEATURES_READ, NULL);
  }

  btm_ble_scanner_init();

  /* Earlier, we used to invoke BTM_ReadLocalAddr which was just copying the
     bd_addr
     from the control block and invoking the callback which was sending the
     DM_ENABLE_EVT.
     But then we have a few HCI commands being invoked above which were still
     in progress
     when the ENABLE_EVT was sent. So modified this to fetch the local name
     which forces
     the DM_ENABLE_EVT to be sent only after all the init steps are complete
     */
  get_btm_client_interface().local.BTM_ReadLocalDeviceNameFromController(
      bta_dm_local_name_cback);

  bta_sys_rm_register(bta_dm_rm_cback);

  /* if sniff is offload, no need to handle it in the stack */
  if (IS_FLAG_ENABLED(enable_sniff_offload) &&
      osi_property_get_bool(kPropertySniffOffloadEnabled, false)) {
  } else {
    /* initialize bluetooth low power manager */
    bta_dm_init_pm();
  }

  bta_dm_disc_gattc_register();
}

/** Disables the BT device manager */
void bta_dm_disable() {
  /* Set l2cap idle timeout to 0 (so BTE immediately disconnects ACL link after
   * last channel is closed) */
  L2CA_SetIdleTimeoutByBdAddr(RawAddress::kAny, 0, BT_TRANSPORT_BR_EDR);
  L2CA_SetIdleTimeoutByBdAddr(RawAddress::kAny, 0, BT_TRANSPORT_LE);

  /* disable all active subsystems */
  bta_sys_disable();

  BTM_SetDiscoverability(BTM_NON_DISCOVERABLE);
  BTM_SetConnectability(BTM_NON_CONNECTABLE);

  bta_dm_disable_pm();
  bta_dm_disc_disable_search_and_disc();
  bta_dm_cb.disabling = true;

  connection_manager::reset(false);

  // We can shut down faster if there are no ACL links
  if (BTM_GetNumAclLinks() == 0) {
    // Time to wait after receiving shutdown request to delay the actual
    // shutdown process. This time may be zero which invokes immediate shutdown.
    const uint64_t disable_delay_ms = GET_SYSPROP(Bta, disable_delay, 200);
    switch (disable_delay_ms) {
      case 0:
        log::debug("Immediately disabling device manager");
        bta_dm_disable_conn_down_timer_cback(nullptr);
        break;
      default:
        log::debug("Set timer to delay disable initiation:{} ms",
                   static_cast<unsigned long>(disable_delay_ms));
        alarm_set_on_mloop(bta_dm_cb.disable_timer, disable_delay_ms,
                           bta_dm_disable_conn_down_timer_cback, nullptr);
    }
  } else {
    log::debug("Set timer to wait for all ACL connections to close:{} ms",
               first_pass.TimeToWaitInMs());
    alarm_set_on_mloop(bta_dm_cb.disable_timer, first_pass.time_to_wait_in_ms,
                       bta_dm_wait_for_acl_to_drain_cback,
                       first_pass.AlarmCallbackData());
  }
}

/*******************************************************************************
 *
 * Function         bta_dm_wait_for_all_acl_to_drain
 *
 * Description      Called if the disable timer expires
 *                  Used to close ACL connections which are still active
 *
 * Returns          true if there is a device being forcefully disconnected
 *
 ******************************************************************************/
static bool force_disconnect_all_acl_connections() {
  const bool is_force_disconnect_needed = (bta_dm_cb.device_list.count > 0);

  for (auto i = 0; i < bta_dm_cb.device_list.count; i++) {
    btm_remove_acl(bta_dm_cb.device_list.peer_device[i].peer_bdaddr,
                   bta_dm_cb.device_list.peer_device[i].transport);
  }
  return is_force_disconnect_needed;
}

static void bta_dm_wait_for_acl_to_drain_cback(void* data) {
  ASSERT(data != nullptr);
  const WaitForAllAclConnectionsToDrain* pass =
      WaitForAllAclConnectionsToDrain::FromAlarmCallbackData(data);

  if (BTM_GetNumAclLinks() && force_disconnect_all_acl_connections() &&
      WaitForAllAclConnectionsToDrain::IsFirstPass(pass)) {
    /* DISABLE_EVT still need to be sent out to avoid java layer disable timeout
     */
    log::debug(
        "Set timer for second pass to wait for all ACL connections to close:{} "
        "ms",
        second_pass.TimeToWaitInMs());
    alarm_set_on_mloop(bta_dm_cb.disable_timer, second_pass.time_to_wait_in_ms,
                       bta_dm_wait_for_acl_to_drain_cback,
                       second_pass.AlarmCallbackData());
  } else {
    // No ACL links to close were up or is second pass at ACL closure
    log::info("Ensuring all ACL connections have been properly flushed");
    bluetooth::shim::ACL_Shutdown();

    bta_dm_cb.disabling = false;

    bta_sys_remove_uuid(UUID_SERVCLASS_PNP_INFORMATION);
    BTIF_dm_disable();
  }
}

/** Sets local device name */
void bta_dm_set_dev_name(const std::vector<uint8_t>& name) {
  get_btm_client_interface().local.BTM_SetLocalDeviceName(
      (const char*)name.data());
  bta_dm_set_eir((char*)name.data());
}

/** Sets discoverability, connectability and pairability */
bool BTA_DmSetVisibility(bt_scan_mode_t mode) {
  tBTA_DM_DISC disc_mode_param;
  tBTA_DM_CONN conn_mode_param;

  switch (mode) {
    case BT_SCAN_MODE_NONE:
      disc_mode_param = BTA_DM_NON_DISC;
      conn_mode_param = BTA_DM_NON_CONN;
      break;

    case BT_SCAN_MODE_CONNECTABLE:
      disc_mode_param = BTA_DM_NON_DISC;
      conn_mode_param = BTA_DM_CONN;
      break;

    case BT_SCAN_MODE_CONNECTABLE_DISCOVERABLE:
      disc_mode_param = BTA_DM_GENERAL_DISC;
      conn_mode_param = BTA_DM_CONN;
      break;

    case BT_SCAN_MODE_CONNECTABLE_LIMITED_DISCOVERABLE:
      disc_mode_param = BTA_DM_LIMITED_DISC;
      conn_mode_param = BTA_DM_CONN;
      break;

    default:
      return false;
  }

  BTM_SetDiscoverability(disc_mode_param);
  BTM_SetConnectability(conn_mode_param);
  return true;
}
void bta_dm_process_remove_device_no_callback(const RawAddress& bd_addr) {
  /* need to remove all pending background connection before unpair */
  bta_dm_disc_gatt_cancel_open(bd_addr);

  get_btm_client_interface().security.BTM_SecDeleteDevice(bd_addr);

  /* remove all cached GATT information */
  bta_dm_disc_gatt_refresh(bd_addr);
}

void bta_dm_process_remove_device(const RawAddress& bd_addr) {
  bta_dm_process_remove_device_no_callback(bd_addr);

  /* Conclude service search if it was pending */
  bta_dm_disc_remove_device(bd_addr);

  if (bta_dm_sec_cb.p_sec_cback) {
    tBTA_DM_SEC sec_event;
    sec_event.dev_unpair.bd_addr = bd_addr;
    bta_dm_sec_cb.p_sec_cback(BTA_DM_DEV_UNPAIRED_EVT, &sec_event);
  }
}

/** Removes device, disconnects ACL link if required */
void bta_dm_remove_device(const RawAddress& bd_addr) {
  /* If ACL exists for the device in the remove_bond message*/
  bool is_bd_addr_connected =
      get_btm_client_interface().peer.BTM_IsAclConnectionUp(bd_addr,
                                                            BT_TRANSPORT_LE) ||
      get_btm_client_interface().peer.BTM_IsAclConnectionUp(
          bd_addr, BT_TRANSPORT_BR_EDR);

  tBT_TRANSPORT other_transport = BT_TRANSPORT_AUTO;
  if (is_bd_addr_connected) {
    log::verbose("ACL Up count: {}", bta_dm_cb.device_list.count);

    /* Take the link down first, and mark the device for removal when
     * disconnected */
    for (int i = 0; i < bta_dm_cb.device_list.count; i++) {
      auto& peer_device = bta_dm_cb.device_list.peer_device[i];
      if (peer_device.peer_bdaddr == bd_addr) {
        peer_device.conn_state = BTA_DM_UNPAIRING;

        /* Make sure device is not in acceptlist before we disconnect */
        GATT_CancelConnect(0, bd_addr, false);

        btm_remove_acl(bd_addr, peer_device.transport);
        log::verbose("transport: {}", peer_device.transport);

        /* save the other transport to check if device is connected on
         * other_transport */
        if (peer_device.transport == BT_TRANSPORT_LE)
          other_transport = BT_TRANSPORT_BR_EDR;
        else
          other_transport = BT_TRANSPORT_LE;

        break;
      }
    }
  }

  RawAddress other_address = bd_addr;
  RawAddress other_address2 = bd_addr;

  // If it is DUMO device and device is paired as different address, unpair that
  // device
  bool other_address_connected =
      (other_transport)
          ? get_btm_client_interface().peer.BTM_ReadConnectedTransportAddress(
                &other_address, other_transport)
          : (get_btm_client_interface().peer.BTM_ReadConnectedTransportAddress(
                 &other_address, BT_TRANSPORT_BR_EDR) ||
             get_btm_client_interface().peer.BTM_ReadConnectedTransportAddress(
                 &other_address2, BT_TRANSPORT_LE));
  if (other_address == bd_addr) other_address = other_address2;

  if (other_address_connected) {
    // Get real transport
    if (other_transport == BT_TRANSPORT_AUTO) {
      bool connected_with_br_edr =
          get_btm_client_interface().peer.BTM_IsAclConnectionUp(
              other_address, BT_TRANSPORT_BR_EDR);
      other_transport =
          connected_with_br_edr ? BT_TRANSPORT_BR_EDR : BT_TRANSPORT_LE;
    }
    log::info("other_address {} with transport {} connected",
              ADDRESS_TO_LOGGABLE_CSTR(other_address), other_transport);
    /* Take the link down first, and mark the device for removal when
     * disconnected */
    for (int i = 0; i < bta_dm_cb.device_list.count; i++) {
      auto& peer_device = bta_dm_cb.device_list.peer_device[i];
      if (peer_device.peer_bdaddr == other_address &&
          peer_device.transport == other_transport) {
        peer_device.conn_state = BTA_DM_UNPAIRING;
        log::info("Remove ACL of address {}",
                  ADDRESS_TO_LOGGABLE_CSTR(other_address));

        /* Make sure device is not in acceptlist before we disconnect */
        GATT_CancelConnect(0, bd_addr, false);

        btm_remove_acl(other_address, peer_device.transport);
        break;
      }
    }
  }

  /* Delete the device mentioned in the msg */
  if (!is_bd_addr_connected) {
    bta_dm_process_remove_device(bd_addr);
  }

  /* Delete the other paired device too */
  if (!other_address_connected && !other_address.IsEmpty()) {
    bta_dm_process_remove_device(other_address);
  }
}

/** This function forces to close the connection to a remote device and
 * optionaly remove the device from security database if required. */
void bta_dm_close_acl(const RawAddress& bd_addr, bool remove_dev,
                      tBT_TRANSPORT transport) {
  uint8_t index;

  log::verbose("bta_dm_close_acl");

  if (BTM_IsAclConnectionUp(bd_addr, transport)) {
    for (index = 0; index < bta_dm_cb.device_list.count; index++) {
      if (bta_dm_cb.device_list.peer_device[index].peer_bdaddr == bd_addr)
        break;
    }
    if (index != bta_dm_cb.device_list.count) {
      if (remove_dev) {
        log::info("Setting remove_dev_pending for {}",
                  ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
        bta_dm_cb.device_list.peer_device[index].remove_dev_pending = true;
      }
    } else {
      log::error("unknown device, remove ACL failed");
    }

    /* Make sure device is not in acceptlist before we disconnect */
    GATT_CancelConnect(0, bd_addr, false);

    /* Disconnect the ACL link */
    btm_remove_acl(bd_addr, transport);
  }
  /* if to remove the device from security database ? do it now */
  else if (remove_dev) {
    bta_dm_process_remove_device_no_callback(bd_addr);
  }
  /* otherwise, no action needed */
}

/*******************************************************************************
 *
 * Function         bta_dm_local_name_cback
 *
 * Description      Callback from btm after local name is read
 *
 *
 * Returns          void
 *
 ******************************************************************************/
static void bta_dm_local_name_cback(UNUSED_ATTR void* p_name) {
  BTIF_dm_enable();
}

static void handle_role_change(const RawAddress& bd_addr, tHCI_ROLE new_role,
                               tHCI_STATUS hci_status) {
  tBTA_DM_PEER_DEVICE* p_dev = bta_dm_find_peer_device(bd_addr);
  if (!p_dev) {
    log::warn(
        "Unable to find device for role change peer:{} new_role:{} "
        "hci_status:{}",
        ADDRESS_TO_LOGGABLE_CSTR(bd_addr), RoleText(new_role),
        hci_error_code_text(hci_status));
    return;
  }

  log::info(
      "Role change callback peer:{} info:{} new_role:{} dev count:{} "
      "hci_status:{}",
      ADDRESS_TO_LOGGABLE_CSTR(bd_addr), p_dev->info_text(), RoleText(new_role),
      bta_dm_cb.device_list.count, hci_error_code_text(hci_status));

  if (p_dev->is_av_active()) {
    bool need_policy_change = false;

    /* there's AV activity on this link */
    if (new_role == HCI_ROLE_PERIPHERAL && bta_dm_cb.device_list.count > 1 &&
        hci_status == HCI_SUCCESS) {
      /* more than one connections and the AV connection is role switched
       * to peripheral
       * switch it back to central and remove the switch policy */
      get_btm_client_interface().link_policy.BTM_SwitchRoleToCentral(bd_addr);
      need_policy_change = true;
    } else if (p_bta_dm_cfg->avoid_scatter && (new_role == HCI_ROLE_CENTRAL)) {
      /* if the link updated to be central include AV activities, remove
       * the switch policy */
      need_policy_change = true;
    }

    if (need_policy_change) {
      get_btm_client_interface().link_policy.BTM_block_role_switch_for(
          p_dev->peer_bdaddr);
    }
  } else {
    /* there's AV no activity on this link and role switch happened
     * check if AV is active
     * if so, make sure the AV link is central */
    bta_dm_check_av();
  }
  bta_sys_notify_role_chg(bd_addr, new_role, hci_status);
}

void BTA_dm_report_role_change(const RawAddress bd_addr, tHCI_ROLE new_role,
                               tHCI_STATUS hci_status) {
  do_in_main_thread(FROM_HERE, base::BindOnce(handle_role_change, bd_addr,
                                              new_role, hci_status));
}

void handle_remote_features_complete(const RawAddress& bd_addr) {
  tBTA_DM_PEER_DEVICE* p_dev = bta_dm_find_peer_device(bd_addr);
  if (!p_dev) {
    log::warn("Unable to find device peer:{}",
              ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
    return;
  }

  if (bluetooth::shim::GetController()->SupportsSniffSubrating() &&
      acl_peer_supports_sniff_subrating(bd_addr)) {
    log::debug("Device supports sniff subrating peer:{}",
               ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
    p_dev->set_both_device_ssr_capable();
  } else {
    log::debug("Device does NOT support sniff subrating peer:{}",
               ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
  }
}

void BTA_dm_notify_remote_features_complete(const RawAddress bd_addr) {
  do_in_main_thread(FROM_HERE,
                    base::BindOnce(handle_remote_features_complete, bd_addr));
}

static tBTA_DM_PEER_DEVICE* allocate_device_for(const RawAddress& bd_addr,
                                                tBT_TRANSPORT transport) {
  for (uint8_t i = 0; i < bta_dm_cb.device_list.count; i++) {
    auto device = &bta_dm_cb.device_list.peer_device[i];
    if (device->peer_bdaddr == bd_addr && device->transport == transport) {
      return device;
    }
  }

  if (bta_dm_cb.device_list.count < BTA_DM_NUM_PEER_DEVICE) {
    auto device =
        &bta_dm_cb.device_list.peer_device[bta_dm_cb.device_list.count];
    device->peer_bdaddr = bd_addr;
    bta_dm_cb.device_list.count++;
    if (transport == BT_TRANSPORT_LE) {
      bta_dm_cb.device_list.le_count++;
    }
    return device;
  }
  return nullptr;
}

void bta_dm_acl_up(const RawAddress& bd_addr, tBT_TRANSPORT transport,
                   uint16_t acl_handle) {
  auto device = allocate_device_for(bd_addr, transport);
  if (device == nullptr) {
    log::warn("Unable to allocate device resources for new connection");
    return;
  }
  log::info("Acl connected peer:{} transport:{} handle:{}",
            ADDRESS_TO_LOGGABLE_CSTR(bd_addr), bt_transport_text(transport),
            acl_handle);
  device->conn_state = BTA_DM_CONNECTED;
  device->pref_role = BTA_ANY_ROLE;
  device->reset_device_info();
  device->transport = transport;

  if (bluetooth::shim::GetController()->SupportsSniffSubrating() &&
      acl_peer_supports_sniff_subrating(bd_addr)) {
    // NOTE: This callback assumes upon ACL connection that
    // the read remote features has completed and is valid.
    // The only guaranteed contract for valid read remote features
    // data is when the BTA_dm_notify_remote_features_complete()
    // callback has completed.  The below assignment is kept for
    // transitional informational purposes only.
    device->set_both_device_ssr_capable();
  }

  if (bta_dm_acl_cb.p_acl_cback) {
    tBTA_DM_ACL conn{};
    conn.link_up.bd_addr = bd_addr;
    conn.link_up.transport_link_type = transport;
    conn.link_up.acl_handle = acl_handle;

    bta_dm_acl_cb.p_acl_cback(BTA_DM_LINK_UP_EVT, &conn);
    log::debug("Executed security callback for new connection available");
  }
  bta_dm_adjust_roles(true);
}

void BTA_dm_acl_up(const RawAddress bd_addr, tBT_TRANSPORT transport,
                   uint16_t acl_handle) {
  do_in_main_thread(
      FROM_HERE, base::BindOnce(bta_dm_acl_up, bd_addr, transport, acl_handle));
}

static void bta_dm_acl_up_failed(const RawAddress bd_addr,
                                 tBT_TRANSPORT transport, tHCI_STATUS status) {
  if (bta_dm_acl_cb.p_acl_cback) {
    tBTA_DM_ACL conn = {};
    conn.link_up_failed.bd_addr = bd_addr;
    conn.link_up_failed.transport_link_type = transport;
    conn.link_up_failed.status = status;
    bta_dm_acl_cb.p_acl_cback(BTA_DM_LINK_UP_FAILED_EVT, &conn);
  }
}

void BTA_dm_acl_up_failed(const RawAddress bd_addr, tBT_TRANSPORT transport,
                          tHCI_STATUS status) {
  do_in_main_thread(FROM_HERE, base::BindOnce(bta_dm_acl_up_failed, bd_addr,
                                              transport, status));
}

static void bta_dm_acl_down(const RawAddress& bd_addr,
                            tBT_TRANSPORT transport) {
  bool issue_unpair_cb = false;
  bool remove_device = false;

  for (uint8_t i = 0; i < bta_dm_cb.device_list.count; i++) {
    auto device = &bta_dm_cb.device_list.peer_device[i];
    if (device->peer_bdaddr != bd_addr || device->transport != transport)
      continue;

    if (device->conn_state == BTA_DM_UNPAIRING) {
      issue_unpair_cb = get_btm_client_interface().security.BTM_SecDeleteDevice(
          device->peer_bdaddr);

      /* remove all cached GATT information */
      get_gatt_interface().BTA_GATTC_Refresh(bd_addr);

      log::verbose("Unpairing: issue unpair CB = {}", issue_unpair_cb);
    }

    remove_device = device->remove_dev_pending;

    // Iterate to the one before the last when shrinking the list,
    // otherwise we memcpy garbage data into the record.
    // Then clear out the last item in the list since we are shrinking.
    for (; i < bta_dm_cb.device_list.count - 1; i++) {
      memcpy(&bta_dm_cb.device_list.peer_device[i],
             &bta_dm_cb.device_list.peer_device[i + 1],
             sizeof(bta_dm_cb.device_list.peer_device[i]));
    }
    if (bta_dm_cb.device_list.count > 0) {
      int clear_index = bta_dm_cb.device_list.count - 1;
      memset(&bta_dm_cb.device_list.peer_device[clear_index], 0,
             sizeof(bta_dm_cb.device_list.peer_device[clear_index]));
    }
    break;
  }
  if (bta_dm_cb.device_list.count) bta_dm_cb.device_list.count--;
  if ((transport == BT_TRANSPORT_LE) && (bta_dm_cb.device_list.le_count)) {
    bta_dm_cb.device_list.le_count--;
  }

  bta_dm_disc_acl_down(bd_addr, transport);

  if (bta_dm_cb.disabling) {
    if (!BTM_GetNumAclLinks()) {
      /*
       * Start a timer to make sure that the profiles
       * get the disconnect event.
       */
      alarm_set_on_mloop(bta_dm_cb.disable_timer,
                         BTA_DM_DISABLE_CONN_DOWN_TIMER_MS,
                         bta_dm_disable_conn_down_timer_cback, NULL);
    }
  }
  if (remove_device) {
    log::info("remove_dev_pending actually removing {}",
              ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
    bta_dm_process_remove_device_no_callback(bd_addr);
  }

  if (bta_dm_acl_cb.p_acl_cback) {
    tBTA_DM_ACL conn{};
    conn.link_down.bd_addr = bd_addr;
    conn.link_down.transport_link_type = transport;

    bta_dm_acl_cb.p_acl_cback(BTA_DM_LINK_DOWN_EVT, &conn);
  }

  // TODO: reorganize and factor out the following logic
  if (issue_unpair_cb && bta_dm_sec_cb.p_sec_cback) {
    tBTA_DM_SEC conn{};
    conn.dev_unpair.bd_addr = bd_addr;
    conn.dev_unpair.transport_link_type = transport;

    bta_dm_sec_cb.p_sec_cback(BTA_DM_DEV_UNPAIRED_EVT, &conn);
  }

  bta_dm_adjust_roles(true);
}

void BTA_dm_acl_down(const RawAddress bd_addr, tBT_TRANSPORT transport) {
  do_in_main_thread(FROM_HERE,
                    base::BindOnce(bta_dm_acl_down, bd_addr, transport));
}

/*******************************************************************************
 *
 * Function         bta_dm_check_av
 *
 * Description      This function checks if AV is active
 *                  if yes, make sure the AV link is central
 *
 ******************************************************************************/
static void bta_dm_check_av() {
  uint8_t i;
  tBTA_DM_PEER_DEVICE* p_dev;

  if (bta_dm_cb.cur_av_count) {
    log::info("av_count:{}", bta_dm_cb.cur_av_count);
    for (i = 0; i < bta_dm_cb.device_list.count; i++) {
      p_dev = &bta_dm_cb.device_list.peer_device[i];
      log::warn("[{}]: state:{}, info:{}", i, p_dev->conn_state,
                p_dev->info_text());
      if ((p_dev->conn_state == BTA_DM_CONNECTED) && p_dev->is_av_active()) {
        /* make central and take away the role switch policy */
        get_btm_client_interface().link_policy.BTM_SwitchRoleToCentral(
            p_dev->peer_bdaddr);
        /* else either already central or can not switch for some reasons */
        get_btm_client_interface().link_policy.BTM_block_role_switch_for(
            p_dev->peer_bdaddr);
        break;
      }
    }
  }
}

/*******************************************************************************
 *
 * Function         bta_dm_disable_conn_down_timer_cback
 *
 * Description      Sends disable event to application
 *
 *
 * Returns          void
 *
 ******************************************************************************/
static void bta_dm_disable_conn_down_timer_cback(UNUSED_ATTR void* data) {
  /* disable the power managment module */
  bta_dm_disable_pm();

  bta_dm_cb.disabling = false;
  log::info("Stack device manager shutdown completed");
  future_ready(stack_manager_get_hack_future(), FUTURE_SUCCESS);
}

/*******************************************************************************
 *
 * Function         bta_dm_rm_cback
 *
 * Description      Role management callback from sys
 *
 *
 * Returns          void
 *
 ******************************************************************************/
static void bta_dm_rm_cback(tBTA_SYS_CONN_STATUS status, tBTA_SYS_ID id,
                            uint8_t app_id, const RawAddress& peer_addr) {
  uint8_t j;
  tBTA_PREF_ROLES role;
  tBTA_DM_PEER_DEVICE* p_dev;

  log::debug("BTA Role management callback count:{} status:{} peer:{}",
             bta_dm_cb.cur_av_count, bta_sys_conn_status_text(status),
             ADDRESS_TO_LOGGABLE_CSTR(peer_addr));

  p_dev = bta_dm_find_peer_device(peer_addr);
  if (status == BTA_SYS_CONN_OPEN) {
    if (p_dev) {
      /* Do not set to connected if we are in the middle of unpairing. When AV
       * stream is
       * started it fakes out a SYS_CONN_OPEN to potentially trigger a role
       * switch command.
       * But this should not be done if we are in the middle of unpairing.
       */
      if (p_dev->conn_state != BTA_DM_UNPAIRING)
        p_dev->conn_state = BTA_DM_CONNECTED;

      for (j = 1; j <= p_bta_dm_rm_cfg[0].app_id; j++) {
        if (((p_bta_dm_rm_cfg[j].app_id == app_id) ||
             (p_bta_dm_rm_cfg[j].app_id == BTA_ALL_APP_ID)) &&
            (p_bta_dm_rm_cfg[j].id == id)) {
          ASSERT_LOG(p_bta_dm_rm_cfg[j].cfg <= BTA_PERIPHERAL_ROLE_ONLY,
                     "Passing illegal preferred role:0x%02x [0x%02x<=>0x%02x]",
                     p_bta_dm_rm_cfg[j].cfg, BTA_ANY_ROLE,
                     BTA_PERIPHERAL_ROLE_ONLY);
          role = static_cast<tBTA_PREF_ROLES>(p_bta_dm_rm_cfg[j].cfg);
          if (role > p_dev->pref_role) p_dev->pref_role = role;
          break;
        }
      }
    }
  }

  if (BTA_ID_AV == id) {
    if (status == BTA_SYS_CONN_BUSY) {
      if (p_dev) p_dev->set_av_active();
      /* AV calls bta_sys_conn_open with the A2DP stream count as app_id */
      if (BTA_ID_AV == id) bta_dm_cb.cur_av_count = bta_dm_get_av_count();
    } else if (status == BTA_SYS_CONN_IDLE) {
      if (p_dev) p_dev->reset_av_active();

      /* get cur_av_count from connected services */
      if (BTA_ID_AV == id) bta_dm_cb.cur_av_count = bta_dm_get_av_count();
    }
  }

  /* Don't adjust roles for each busy/idle state transition to avoid
     excessive switch requests when individual profile busy/idle status
     changes */
  if ((status != BTA_SYS_CONN_BUSY) && (status != BTA_SYS_CONN_IDLE))
    bta_dm_adjust_roles(false);
}

/*******************************************************************************
 *
 * Function         bta_dm_delay_role_switch_cback
 *
 * Description      Callback from btm to delay a role switch
 *
 * Returns          void
 *
 ******************************************************************************/
static void bta_dm_delay_role_switch_cback(UNUSED_ATTR void* data) {
  log::verbose("initiating Delayed RS");
  bta_dm_adjust_roles(false);
}

/*******************************************************************************
 *
 * Function         bta_dm_adjust_roles
 *
 * Description      Adjust roles
 *
 *
 * Returns          void
 *
 ******************************************************************************/
static void bta_dm_adjust_roles(bool delay_role_switch) {
  uint8_t i;
  uint8_t link_count = bta_dm_cb.device_list.count;
  if (link_count) {
    for (i = 0; i < bta_dm_cb.device_list.count; i++) {
      if (bta_dm_cb.device_list.peer_device[i].conn_state == BTA_DM_CONNECTED &&
          bta_dm_cb.device_list.peer_device[i].transport ==
              BT_TRANSPORT_BR_EDR) {
        if ((bta_dm_cb.device_list.peer_device[i].pref_role ==
             BTA_CENTRAL_ROLE_ONLY) ||
            (link_count > 1)) {
          /* Initiating immediate role switch with certain remote devices
            has caused issues due to role  switch colliding with link encryption
            setup and
            causing encryption (and in turn the link) to fail .  These device .
            Firmware
            versions are stored in a rejectlist and role switch with these
            devices are
            delayed to avoid the collision with link encryption setup */

          if (bta_dm_cb.device_list.peer_device[i].pref_role !=
                  BTA_PERIPHERAL_ROLE_ONLY &&
              !delay_role_switch) {
            get_btm_client_interface().link_policy.BTM_SwitchRoleToCentral(
                bta_dm_cb.device_list.peer_device[i].peer_bdaddr);
          } else {
            alarm_set_on_mloop(bta_dm_cb.switch_delay_timer,
                               BTA_DM_SWITCH_DELAY_TIMER_MS,
                               bta_dm_delay_role_switch_cback, NULL);
          }
        }
      }
    }
  }
}

/*******************************************************************************
 *
 * Function         find_utf8_char_boundary
 *
 * Description      This function checks a UTF8 string |utf8str| starting at
 *                  |offset|, moving backwards and returns the offset of the
 *                  next valid UTF8 character boundary found.
 *
 * Returns          Offset of UTF8 character boundary
 *
 ******************************************************************************/
static size_t find_utf8_char_boundary(const char* utf8str, size_t offset) {
  CHECK(utf8str);
  CHECK(offset > 0);

  while (--offset) {
    uint8_t ch = (uint8_t)utf8str[offset];
    if ((ch & 0x80) == 0x00)  // ASCII
      return offset + 1;
    if ((ch & 0xC0) == 0xC0)  // Multi-byte sequence start
      return offset;
  }

  return 0;
}

/*******************************************************************************
 *
 * Function         bta_dm_set_eir
 *
 * Description      This function creates EIR tagged data and writes it to
 *                  controller.
 *
 * Returns          None
 *
 ******************************************************************************/
static void bta_dm_set_eir(char* local_name) {
  uint8_t* p;
  uint8_t* p_length;
#if (BTA_EIR_CANNED_UUID_LIST != TRUE)
  uint8_t* p_type;
  uint8_t max_num_uuid;
#if (BTA_EIR_SERVER_NUM_CUSTOM_UUID > 0)
  uint8_t custom_uuid_idx;
#endif  // BTA_EIR_SERVER_NUM_CUSTOM_UUID
#endif  // BTA_EIR_CANNED_UUID_LIST
#if (BTM_EIR_DEFAULT_FEC_REQUIRED == FALSE)
  uint8_t free_eir_length = HCI_EXT_INQ_RESPONSE_LEN;
#else  // BTM_EIR_DEFAULT_FEC_REQUIRED
  uint8_t free_eir_length = HCI_DM5_PACKET_SIZE;
#endif  // BTM_EIR_DEFAULT_FEC_REQUIRED
  uint8_t num_uuid;
  uint8_t data_type;
  uint8_t local_name_len;

  /* wait until complete to disable */
  if (alarm_is_scheduled(bta_dm_cb.disable_timer)) return;

#if (BTA_EIR_CANNED_UUID_LIST != TRUE)
  /* if local name is not provided, get it from controller */
  if (local_name == NULL) {
    if (BTM_ReadLocalDeviceName((const char**)&local_name) != BTM_SUCCESS) {
      log::error("Fail to read local device name for EIR");
    }
  }
#endif  // BTA_EIR_CANNED_UUID_LIST

  /* Allocate a buffer to hold HCI command */
  BT_HDR* p_buf = (BT_HDR*)osi_malloc(BTM_CMD_BUF_SIZE);
  ASSERT(p_buf != nullptr);
  p = (uint8_t*)p_buf + BTM_HCI_EIR_OFFSET;

  memset(p, 0x00, HCI_EXT_INQ_RESPONSE_LEN);

  log::info("Generating extended inquiry response packet EIR");

  if (local_name)
    local_name_len = strlen(local_name);
  else
    local_name_len = 0;

  data_type = HCI_EIR_COMPLETE_LOCAL_NAME_TYPE;
  /* if local name is longer than minimum length of shortened name */
  /* check whether it needs to be shortened or not */
  if (local_name_len > p_bta_dm_eir_cfg->bta_dm_eir_min_name_len) {
/* get number of UUID 16-bit list */
#if (BTA_EIR_CANNED_UUID_LIST == TRUE)
    num_uuid = p_bta_dm_eir_cfg->bta_dm_eir_uuid16_len / Uuid::kNumBytes16;
#else   // BTA_EIR_CANNED_UUID_LIST
    max_num_uuid = (free_eir_length - 2) / Uuid::kNumBytes16;
    data_type = get_btm_client_interface().eir.BTM_GetEirSupportedServices(
        bta_dm_cb.eir_uuid, &p, max_num_uuid, &num_uuid);
    p = (uint8_t*)p_buf + BTM_HCI_EIR_OFFSET; /* reset p */
#endif  // BTA_EIR_CANNED_UUID_LIST

    /* if UUID doesn't fit remaing space, shorten local name */
    if (local_name_len > (free_eir_length - 4 - num_uuid * Uuid::kNumBytes16)) {
      local_name_len = find_utf8_char_boundary(
          local_name, p_bta_dm_eir_cfg->bta_dm_eir_min_name_len);
      log::warn("local name is shortened ({})", local_name_len);
      data_type = HCI_EIR_SHORTENED_LOCAL_NAME_TYPE;
    } else {
      data_type = HCI_EIR_COMPLETE_LOCAL_NAME_TYPE;
    }
  }

  UINT8_TO_STREAM(p, local_name_len + 1);
  UINT8_TO_STREAM(p, data_type);

  if (local_name != NULL) {
    memcpy(p, local_name, local_name_len);
    p += local_name_len;
  }
  free_eir_length -= local_name_len + 2;

#if (BTA_EIR_CANNED_UUID_LIST == TRUE)
  /* if UUID list is provided as static data in configuration */
  if ((p_bta_dm_eir_cfg->bta_dm_eir_uuid16_len > 0) &&
      (p_bta_dm_eir_cfg->bta_dm_eir_uuid16)) {
    if (free_eir_length > Uuid::kNumBytes16 + 2) {
      free_eir_length -= 2;

      if (free_eir_length >= p_bta_dm_eir_cfg->bta_dm_eir_uuid16_len) {
        num_uuid = p_bta_dm_eir_cfg->bta_dm_eir_uuid16_len / Uuid::kNumBytes16;
        data_type = HCI_EIR_COMPLETE_16BITS_UUID_TYPE;
      } else /* not enough room for all UUIDs */
      {
        log::warn("BTA EIR: UUID 16-bit list is truncated");
        num_uuid = free_eir_length / Uuid::kNumBytes16;
        data_type = HCI_EIR_MORE_16BITS_UUID_TYPE;
      }
      UINT8_TO_STREAM(p, num_uuid * Uuid::kNumBytes16 + 1);
      UINT8_TO_STREAM(p, data_type);
      memcpy(p, p_bta_dm_eir_cfg->bta_dm_eir_uuid16,
             num_uuid * Uuid::kNumBytes16);
      p += num_uuid * Uuid::kNumBytes16;
      free_eir_length -= num_uuid * Uuid::kNumBytes16;
    }
  }
#else /* (BTA_EIR_CANNED_UUID_LIST == TRUE) */
  /* if UUID list is dynamic */
  if (free_eir_length >= 2) {
    p_length = p++;
    p_type = p++;
    num_uuid = 0;

    max_num_uuid = (free_eir_length - 2) / Uuid::kNumBytes16;
    data_type = get_btm_client_interface().eir.BTM_GetEirSupportedServices(
        bta_dm_cb.eir_uuid, &p, max_num_uuid, &num_uuid);

    if (data_type == HCI_EIR_MORE_16BITS_UUID_TYPE) {
      log::warn("BTA EIR: UUID 16-bit list is truncated");
    }
#if (BTA_EIR_SERVER_NUM_CUSTOM_UUID > 0)
    else {
      for (custom_uuid_idx = 0;
           custom_uuid_idx < BTA_EIR_SERVER_NUM_CUSTOM_UUID;
           custom_uuid_idx++) {
        const Uuid& curr = bta_dm_cb.bta_custom_uuid[custom_uuid_idx].custom_uuid;
        if (curr.GetShortestRepresentationSize() == Uuid::kNumBytes16) {
          if (num_uuid < max_num_uuid) {
            UINT16_TO_STREAM(p, curr.As16Bit());
            num_uuid++;
          } else {
            data_type = HCI_EIR_MORE_16BITS_UUID_TYPE;
            log::warn("BTA EIR: UUID 16-bit list is truncated");
            break;
          }
        }
      }
    }
#endif /* (BTA_EIR_SERVER_NUM_CUSTOM_UUID > 0) */

    UINT8_TO_STREAM(p_length, num_uuid * Uuid::kNumBytes16 + 1);
    UINT8_TO_STREAM(p_type, data_type);
    free_eir_length -= num_uuid * Uuid::kNumBytes16 + 2;
  }
#endif /* (BTA_EIR_CANNED_UUID_LIST == TRUE) */

#if (BTA_EIR_CANNED_UUID_LIST != TRUE && BTA_EIR_SERVER_NUM_CUSTOM_UUID > 0)
  /* Adding 32-bit UUID list */
  if (free_eir_length >= 2) {
    p_length = p++;
    p_type = p++;
    num_uuid = 0;
    data_type = HCI_EIR_COMPLETE_32BITS_UUID_TYPE;

    max_num_uuid = (free_eir_length - 2) / Uuid::kNumBytes32;

    for (custom_uuid_idx = 0; custom_uuid_idx < BTA_EIR_SERVER_NUM_CUSTOM_UUID;
         custom_uuid_idx++) {
      const Uuid& curr = bta_dm_cb.bta_custom_uuid[custom_uuid_idx].custom_uuid;
      if (curr.GetShortestRepresentationSize() == Uuid::kNumBytes32) {
        if (num_uuid < max_num_uuid) {
          UINT32_TO_STREAM(p, curr.As32Bit());
          num_uuid++;
        } else {
          data_type = HCI_EIR_MORE_32BITS_UUID_TYPE;
          log::warn("BTA EIR: UUID 32-bit list is truncated");
          break;
        }
      }
    }

    UINT8_TO_STREAM(p_length, num_uuid * Uuid::kNumBytes32 + 1);
    UINT8_TO_STREAM(p_type, data_type);
    free_eir_length -= num_uuid * Uuid::kNumBytes32 + 2;
  }

  /* Adding 128-bit UUID list */
  if (free_eir_length >= 2) {
    p_length = p++;
    p_type = p++;
    num_uuid = 0;
    data_type = HCI_EIR_COMPLETE_128BITS_UUID_TYPE;

    max_num_uuid = (free_eir_length - 2) / Uuid::kNumBytes128;

    for (custom_uuid_idx = 0; custom_uuid_idx < BTA_EIR_SERVER_NUM_CUSTOM_UUID;
         custom_uuid_idx++) {
      const Uuid& curr = bta_dm_cb.bta_custom_uuid[custom_uuid_idx].custom_uuid;
      if (curr.GetShortestRepresentationSize() == Uuid::kNumBytes128) {
        if (num_uuid < max_num_uuid) {
          ARRAY16_TO_STREAM(p, curr.To128BitBE().data());
          num_uuid++;
        } else {
          data_type = HCI_EIR_MORE_128BITS_UUID_TYPE;
          log::warn("BTA EIR: UUID 128-bit list is truncated");
          break;
        }
      }
    }

    UINT8_TO_STREAM(p_length, num_uuid * Uuid::kNumBytes128 + 1);
    UINT8_TO_STREAM(p_type, data_type);
    free_eir_length -= num_uuid * Uuid::kNumBytes128 + 2;
  }
#endif /* ( BTA_EIR_CANNED_UUID_LIST != TRUE \
          )&&(BTA_EIR_SERVER_NUM_CUSTOM_UUID > 0) */

  /* if Flags are provided in configuration */
  if ((p_bta_dm_eir_cfg->bta_dm_eir_flag_len > 0) &&
      (p_bta_dm_eir_cfg->bta_dm_eir_flags) &&
      (free_eir_length >= p_bta_dm_eir_cfg->bta_dm_eir_flag_len + 2)) {
    UINT8_TO_STREAM(p, p_bta_dm_eir_cfg->bta_dm_eir_flag_len + 1);
    UINT8_TO_STREAM(p, HCI_EIR_FLAGS_TYPE);
    memcpy(p, p_bta_dm_eir_cfg->bta_dm_eir_flags,
           p_bta_dm_eir_cfg->bta_dm_eir_flag_len);
    p += p_bta_dm_eir_cfg->bta_dm_eir_flag_len;
    free_eir_length -= p_bta_dm_eir_cfg->bta_dm_eir_flag_len + 2;
  }

  /* if Manufacturer Specific are provided in configuration */
  if ((p_bta_dm_eir_cfg->bta_dm_eir_manufac_spec_len > 0) &&
      (p_bta_dm_eir_cfg->bta_dm_eir_manufac_spec) &&
      (free_eir_length >= p_bta_dm_eir_cfg->bta_dm_eir_manufac_spec_len + 2)) {
    p_length = p;

    UINT8_TO_STREAM(p, p_bta_dm_eir_cfg->bta_dm_eir_manufac_spec_len + 1);
    UINT8_TO_STREAM(p, HCI_EIR_MANUFACTURER_SPECIFIC_TYPE);
    memcpy(p, p_bta_dm_eir_cfg->bta_dm_eir_manufac_spec,
           p_bta_dm_eir_cfg->bta_dm_eir_manufac_spec_len);
    p += p_bta_dm_eir_cfg->bta_dm_eir_manufac_spec_len;
    free_eir_length -= p_bta_dm_eir_cfg->bta_dm_eir_manufac_spec_len + 2;

  } else {
    p_length = NULL;
  }

  /* if Inquiry Tx Resp Power compiled */
  if ((p_bta_dm_eir_cfg->bta_dm_eir_inq_tx_power) && (free_eir_length >= 3)) {
    UINT8_TO_STREAM(p, 2); /* Length field */
    UINT8_TO_STREAM(p, HCI_EIR_TX_POWER_LEVEL_TYPE);
    UINT8_TO_STREAM(p, *(p_bta_dm_eir_cfg->bta_dm_eir_inq_tx_power));
    free_eir_length -= 3;
  }

  if (free_eir_length)
    UINT8_TO_STREAM(p, 0); /* terminator of significant part */

  get_btm_client_interface().eir.BTM_WriteEIR(p_buf);
}

#if (BTA_EIR_CANNED_UUID_LIST != TRUE)
/*******************************************************************************
 *
 * Function         bta_dm_get_cust_uuid_index
 *
 * Description      Get index of custom uuid from list
 *                  Note, handle equals to 0 means to find a vacant
 *                  from list.
 *
 * Returns          Index of array
 *                  bta_dm_cb.bta_custom_uuid[BTA_EIR_SERVER_NUM_CUSTOM_UUID]
 *
 ******************************************************************************/
static uint8_t bta_dm_get_cust_uuid_index(uint32_t handle) {
#if (BTA_EIR_SERVER_NUM_CUSTOM_UUID > 0)
  uint8_t c_uu_idx = 0;

  while(c_uu_idx < BTA_EIR_SERVER_NUM_CUSTOM_UUID &&
      bta_dm_cb.bta_custom_uuid[c_uu_idx].handle != handle) {
    c_uu_idx++;
  }

  return c_uu_idx;
#else
  return 0;
#endif
}

/*******************************************************************************
 *
 * Function         bta_dm_update_cust_uuid
 *
 * Description      Update custom uuid with given value
 *
 * Returns          None
 *
 ******************************************************************************/
static void bta_dm_update_cust_uuid(uint8_t c_uu_idx, const Uuid& uuid, uint32_t handle) {
#if (BTA_EIR_SERVER_NUM_CUSTOM_UUID > 0)
  if (c_uu_idx < BTA_EIR_SERVER_NUM_CUSTOM_UUID) {
    tBTA_CUSTOM_UUID& curr = bta_dm_cb.bta_custom_uuid[c_uu_idx];
    curr.custom_uuid.UpdateUuid(uuid);
    curr.handle = handle;
  } else {
    log::error("invalid uuid index {}", c_uu_idx);
  }
#endif
}

/*******************************************************************************
 *
 * Function         bta_dm_eir_update_cust_uuid
 *
 * Description      This function adds or removes custom service UUID in EIR database.
 *
 * Returns          None
 *
 ******************************************************************************/
void bta_dm_eir_update_cust_uuid(const tBTA_CUSTOM_UUID& curr, bool adding) {
  log::verbose("");
#if (BTA_EIR_SERVER_NUM_CUSTOM_UUID > 0)
  uint8_t c_uu_idx = 0;
  if (adding) {
    c_uu_idx = bta_dm_get_cust_uuid_index(0); /* find a vacant from uuid list */
    bta_dm_update_cust_uuid(c_uu_idx, curr.custom_uuid, curr.handle);
  } else {
    c_uu_idx = bta_dm_get_cust_uuid_index(curr.handle); /* find the uuid from uuid list */
    bta_dm_update_cust_uuid(c_uu_idx, curr.custom_uuid, 0);
  }

  /* Update EIR when UUIDs are changed */
  if (c_uu_idx <= BTA_EIR_SERVER_NUM_CUSTOM_UUID) {
    bta_dm_set_eir(NULL);
  }
#endif
}

/*******************************************************************************
 *
 * Function         bta_dm_eir_update_uuid
 *
 * Description      This function adds or removes service UUID in EIR database.
 *
 * Returns          None
 *
 ******************************************************************************/
void bta_dm_eir_update_uuid(uint16_t uuid16, bool adding) {
  /* if this UUID is not advertised in EIR */
  if (!BTM_HasEirService(p_bta_dm_eir_cfg->uuid_mask, uuid16)) return;

  if (adding) {
    log::info("EIR Adding UUID=0x{:04X} into extended inquiry response",
              uuid16);

    get_btm_client_interface().eir.BTM_AddEirService(bta_dm_cb.eir_uuid,
                                                     uuid16);
  } else {
    log::info("EIR Removing UUID=0x{:04X} from extended inquiry response",
              uuid16);

    get_btm_client_interface().eir.BTM_RemoveEirService(bta_dm_cb.eir_uuid,
                                                        uuid16);
  }

  bta_dm_set_eir(NULL);
}
#endif  // BTA_EIR_CANNED_UUID_LIST

tBTA_DM_PEER_DEVICE* find_connected_device(
    const RawAddress& bd_addr, UNUSED_ATTR tBT_TRANSPORT transport) {
  for (uint8_t i = 0; i < bta_dm_cb.device_list.count; i++) {
    if (bta_dm_cb.device_list.peer_device[i].peer_bdaddr == bd_addr &&
        bta_dm_cb.device_list.peer_device[i].conn_state == BTA_DM_CONNECTED)
      return &bta_dm_cb.device_list.peer_device[i];
  }
  return nullptr;
}

bool bta_dm_check_if_only_hd_connected(const RawAddress& peer_addr) {
  log::verbose("count({})", bta_dm_conn_srvcs.count);

  for (uint8_t j = 0; j < bta_dm_conn_srvcs.count; j++) {
    // Check if profiles other than hid are connected
    if ((bta_dm_conn_srvcs.conn_srvc[j].id != BTA_ID_HD) &&
        bta_dm_conn_srvcs.conn_srvc[j].peer_bdaddr == peer_addr) {
      log::verbose("Another profile (id={}) is connected",
                   bta_dm_conn_srvcs.conn_srvc[j].id);
      return false;
    }
  }

  return true;
}

/** This function set the preferred connection parameters */
void bta_dm_ble_set_conn_params(const RawAddress& bd_addr,
                                uint16_t conn_int_min, uint16_t conn_int_max,
                                uint16_t peripheral_latency,
                                uint16_t supervision_tout) {
  L2CA_AdjustConnectionIntervals(&conn_int_min, &conn_int_max,
                                 BTM_BLE_CONN_INT_MIN);

  get_btm_client_interface().ble.BTM_BleSetPrefConnParams(
      bd_addr, conn_int_min, conn_int_max, peripheral_latency,
      supervision_tout);
}

/** This function update LE connection parameters */
void bta_dm_ble_update_conn_params(const RawAddress& bd_addr, uint16_t min_int,
                                   uint16_t max_int, uint16_t latency,
                                   uint16_t timeout, uint16_t min_ce_len,
                                   uint16_t max_ce_len) {
  L2CA_AdjustConnectionIntervals(&min_int, &max_int, BTM_BLE_CONN_INT_MIN);

  if (!L2CA_UpdateBleConnParams(bd_addr, min_int, max_int, latency, timeout,
                                min_ce_len, max_ce_len)) {
    log::error("Update connection parameters failed!");
  }
}

/** This function set the maximum transmission packet size */
void bta_dm_ble_set_data_length(const RawAddress& bd_addr) {
  const controller_t* controller = controller_get_interface();
  uint16_t max_len = controller->get_ble_maximum_tx_data_length();

  if (BTM_SetBleDataLength(bd_addr, max_len) != BTM_SUCCESS) {
    log::info("Unable to set ble data length:{}", max_len);
  }
}

/** This function returns system context info */
static tBTM_CONTRL_STATE bta_dm_obtain_system_context() {
  uint32_t total_acl_num = bta_dm_cb.device_list.count;
  uint32_t sniff_acl_num = BTM_PM_ReadSniffLinkCount();
  uint32_t le_acl_num = BTM_PM_ReadBleLinkCount();
  uint32_t active_acl_num = total_acl_num - sniff_acl_num - le_acl_num;
  uint32_t le_adv_num =
      bluetooth::shim::BTM_BleGetNumberOfAdvertisingInstancesInUse();
  uint32_t le_scan_duty_cycle = BTM_PM_ReadBleScanDutyCycle();
  bool is_inquiry_active = BTM_PM_DeviceInScanState();
  bool is_le_audio_active = LeAudioClient::IsLeAudioClientInStreaming();
  bool is_av_active = false;
  bool is_sco_active = false;

  for (int i = 0; i < bta_dm_cb.device_list.count; i++) {
    tBTA_DM_PEER_DEVICE* p_dev = &bta_dm_cb.device_list.peer_device[i];
    if (p_dev->conn_state == BTA_DM_CONNECTED && p_dev->is_av_active()) {
      is_av_active = true;
      break;
    }
  }
  for (int j = 0; j < bta_dm_conn_srvcs.count; j++) {
    /* check for SCO connected index */
    if (bta_dm_conn_srvcs.conn_srvc[j].id == BTA_ID_AG ||
        bta_dm_conn_srvcs.conn_srvc[j].id == BTA_ID_HS) {
      if (bta_dm_conn_srvcs.conn_srvc[j].state == BTA_SYS_SCO_OPEN) {
        is_sco_active = true;
        break;
      }
    }
  }

  tBTM_CONTRL_STATE ctrl_state = 0;
  set_num_acl_active_to_ctrl_state(active_acl_num, ctrl_state);
  set_num_acl_sniff_to_ctrl_state(sniff_acl_num, ctrl_state);
  set_num_acl_le_to_ctrl_state(le_acl_num, ctrl_state);
  set_num_le_adv_to_ctrl_state(le_adv_num, ctrl_state);
  set_le_scan_mode_to_ctrl_state(le_scan_duty_cycle, ctrl_state);

  if (is_inquiry_active) {
    ctrl_state |= BTM_CONTRL_INQUIRY;
  }
  if (is_sco_active) {
    ctrl_state |= BTM_CONTRL_SCO;
  }
  if (is_av_active) {
    ctrl_state |= BTM_CONTRL_A2DP;
  }
  if (is_le_audio_active) {
    ctrl_state |= BTM_CONTRL_LE_AUDIO;
  }
  log::debug(
      "active_acl_num {} sniff_acl_num {} le_acl_num {} le_adv_num {} "
      "le_scan_duty {} inquiry {} sco {} a2dp {} le_audio {} ctrl_state 0x{:x}",
      active_acl_num, sniff_acl_num, le_acl_num, le_adv_num, le_scan_duty_cycle,
      is_inquiry_active, is_sco_active, is_av_active, is_le_audio_active,
      ctrl_state);
  return ctrl_state;
}

/*******************************************************************************
 *
 * Function         bta_ble_enable_scan_cmpl
 *
 * Description      ADV payload filtering enable / disable complete callback
 *
 *
 * Returns          None
 *
 ******************************************************************************/
static void bta_ble_energy_info_cmpl(tBTM_BLE_TX_TIME_MS tx_time,
                                     tBTM_BLE_RX_TIME_MS rx_time,
                                     tBTM_BLE_IDLE_TIME_MS idle_time,
                                     tBTM_BLE_ENERGY_USED energy_used,
                                     tHCI_STATUS status) {
  tBTA_STATUS st = (status == HCI_SUCCESS) ? BTA_SUCCESS : BTA_FAILURE;
  tBTM_CONTRL_STATE ctrl_state = BTM_CONTRL_UNKNOWN;

  if (BTA_SUCCESS == st) {
    ctrl_state = IS_FLAG_ENABLED(bt_system_context_report)
                     ? bta_dm_obtain_system_context()
                     : bta_dm_pm_obtain_controller_state();
  }

  if (bta_dm_cb.p_energy_info_cback)
    bta_dm_cb.p_energy_info_cback(tx_time, rx_time, idle_time, energy_used,
                                  ctrl_state, st);
}

/** This function obtains the energy info */
void bta_dm_ble_get_energy_info(
    tBTA_BLE_ENERGY_INFO_CBACK* p_energy_info_cback) {
  bta_dm_cb.p_energy_info_cback = p_energy_info_cback;
  tBTM_STATUS btm_status = get_btm_client_interface().ble.BTM_BleGetEnergyInfo(
      bta_ble_energy_info_cmpl);
  if (btm_status != BTM_CMD_STARTED)
    bta_ble_energy_info_cmpl(0, 0, 0, 0, HCI_ERR_UNSPECIFIED);
}

/*******************************************************************************
 *
 * Function         bta_dm_clear_event_filter
 *
 * Description      clears out the event filter.
 *
 * Parameters:
 *
 ******************************************************************************/
void bta_dm_clear_event_filter(void) {
  log::verbose("bta_dm_clear_event_filter in bta_dm_act");
  bluetooth::shim::BTM_ClearEventFilter();
}

/*******************************************************************************
 *
 * Function         bta_dm_clear_event_mask
 *
 * Description      Clears out the event mask in the controller.
 *
 ******************************************************************************/
void bta_dm_clear_event_mask(void) {
  log::verbose("bta_dm_clear_event_mask in bta_dm_act");
  bluetooth::shim::BTM_ClearEventMask();
}

/*******************************************************************************
 *
 * Function         bta_dm_clear_filter_accept_list
 *
 * Description      Clears out the connect list in the controller.
 *
 ******************************************************************************/
void bta_dm_clear_filter_accept_list(void) {
  log::verbose("bta_dm_clear_filter_accept_list in bta_dm_act");
  bluetooth::shim::BTM_ClearFilterAcceptList();
}

/*******************************************************************************
 *
 * Function         bta_dm_disconnect_all_acls
 *
 * Description      Disconnects all ACL connections.
 *
 ******************************************************************************/
void bta_dm_disconnect_all_acls(void) {
  log::verbose("bta_dm_disconnect_all_acls in bta_dm_act");
  bluetooth::shim::BTM_DisconnectAllAcls();
}

/*******************************************************************************
 *
 * Function         bta_dm_le_rand
 *
 * Description      Generates a random number from the controller.
 *
 * Parameters:      |cb| Callback to receive the random number.
 *
 ******************************************************************************/
void bta_dm_le_rand(LeRandCallback cb) {
  log::verbose("bta_dm_le_rand in bta_dm_act");
  bluetooth::shim::BTM_LeRand(std::move(cb));
}

/*******************************************************************************
 *
 * Function        BTA_DmSetEventFilterConnectionSetupAllDevices
 *
 * Description    Tell the controller to allow all devices
 *
 * Parameters
 *
 *******************************************************************************/
void bta_dm_set_event_filter_connection_setup_all_devices() {
  // Autoplumbed
  bluetooth::shim::BTM_SetEventFilterConnectionSetupAllDevices();
}

/*******************************************************************************
 *
 * Function        BTA_DmAllowWakeByHid
 *
 * Description     Allow the device to be woken by HID devices
 *
 * Parameters      std::vector of Classic Address and LE (Address, Address Type)
 *
 *******************************************************************************/
void bta_dm_allow_wake_by_hid(
    std::vector<RawAddress> classic_hid_devices,
    std::vector<std::pair<RawAddress, uint8_t>> le_hid_devices) {
  // If there are any entries in the classic hid list, we should also make
  // the adapter connectable for classic.
  if (classic_hid_devices.size() > 0) {
    BTM_SetConnectability(BTA_DM_CONN);
  }

  bluetooth::shim::BTM_AllowWakeByHid(std::move(classic_hid_devices),
                                      std::move(le_hid_devices));
}

/*******************************************************************************
 *
 * Function        BTA_DmRestoreFilterAcceptList
 *
 * Description    Floss: Restore the state of the for the filter accept list
 *
 * Parameters
 *
 *******************************************************************************/
void bta_dm_restore_filter_accept_list(
    std::vector<std::pair<RawAddress, uint8_t>> le_devices) {
  // Autoplumbed
  bluetooth::shim::BTM_RestoreFilterAcceptList(le_devices);
}

/*******************************************************************************
 *
 * Function       BTA_DmSetDefaultEventMaskExcept
 *
 * Description    Floss: Set the default event mask for Classic and LE except
 *                the given values (they will be disabled in the final set
 *                mask).
 *
 * Parameters     Bits set for event mask and le event mask that should be
 *                disabled in the final value.
 *
 *******************************************************************************/
void bta_dm_set_default_event_mask_except(uint64_t mask, uint64_t le_mask) {
  // Autoplumbed
  bluetooth::shim::BTM_SetDefaultEventMaskExcept(mask, le_mask);
}

/*******************************************************************************
 *
 * Function        BTA_DmSetEventFilterInquiryResultAllDevices
 *
 * Description    Floss: Set the event filter to inquiry result device all
 *
 * Parameters
 *
 *******************************************************************************/
void bta_dm_set_event_filter_inquiry_result_all_devices() {
  // Autoplumbed
  bluetooth::shim::BTM_SetEventFilterInquiryResultAllDevices();
}

/*******************************************************************************
 *
 * Function         bta_dm_ble_reset_id
 *
 * Description      Reset the local adapter BLE keys.
 *
 * Parameters:
 *
 ******************************************************************************/
void bta_dm_ble_reset_id(void) {
  log::verbose("bta_dm_ble_reset_id in bta_dm_act");
  bluetooth::shim::BTM_BleResetId();
}

/*******************************************************************************
 *
 * Function         bta_dm_ctrl_features_rd_cmpl_cback
 *
 * Description      callback to handle controller feature read complete
 *
 * Parameters:
 *
 ******************************************************************************/
static void bta_dm_ctrl_features_rd_cmpl_cback(tHCI_STATUS result) {
  log::verbose("status = {}", result);
  if (result == HCI_SUCCESS) {
    if (bta_dm_acl_cb.p_acl_cback)
      bta_dm_acl_cb.p_acl_cback(BTA_DM_LE_FEATURES_READ, NULL);
  } else {
    log::error("Ctrl BLE feature read failed: status :{}", result);
  }
}

/*******************************************************************************
 *
 * Function         bta_dm_ble_subrate_request
 *
 * Description      This function requests BLE subrate procedure.
 *
 * Parameters:
 *
 ******************************************************************************/
void bta_dm_ble_subrate_request(const RawAddress& bd_addr, uint16_t subrate_min,
                                uint16_t subrate_max, uint16_t max_latency,
                                uint16_t cont_num, uint16_t timeout) {
    // Logging done in l2c_ble.cc
    L2CA_SubrateRequest(bd_addr, subrate_min, subrate_max, max_latency,
                        cont_num, timeout);
}

namespace bluetooth {
namespace legacy {
namespace testing {
tBTA_DM_PEER_DEVICE* allocate_device_for(const RawAddress& bd_addr,
                                         tBT_TRANSPORT transport) {
  return ::allocate_device_for(bd_addr, transport);
}

void bta_dm_init_cb() { ::bta_dm_init_cb(); }
void bta_dm_deinit_cb() { ::bta_dm_deinit_cb(); }
void BTA_dm_on_hw_on() { ::BTA_dm_on_hw_on(); }

}  // namespace testing
}  // namespace legacy
}  // namespace bluetooth