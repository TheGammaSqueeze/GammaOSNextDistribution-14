/******************************************************************************
 *
 *  Copyright 1999-2012 Broadcom Corporation
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
 *  This file contains functions for BLE acceptlist operation.
 *
 ******************************************************************************/

#define LOG_TAG "ble_bgconn"

#include "stack/btm/btm_ble_bgconn.h"

#include <bluetooth/log.h>

#include <cstdint>
#include <unordered_map>

#include "device/include/controller.h"
#include "main/shim/acl_api.h"
#include "os/log.h"
#include "stack/btm/btm_ble_int.h"
#include "stack/btm/btm_dev.h"
#include "stack/btm/btm_int_types.h"
#include "types/raw_address.h"

using namespace bluetooth;

extern tBTM_CB btm_cb;

// Unfortunately (for now?) we have to maintain a copy of the device acceptlist
// on the host to determine if a device is pending to be connected or not. This
// controls whether the host should keep trying to scan for acceptlisted
// peripherals or not.
// TODO: Move all of this to controller/le/background_list or similar?
struct BackgroundConnection {
  RawAddress address;
  uint8_t addr_type;
  bool in_controller_wl;
  uint8_t addr_type_in_wl;
  bool pending_removal;
};

struct BgConnHash {
  std::size_t operator()(const RawAddress& x) const {
    const uint8_t* a = x.address;
    return a[0] ^ (a[1] << 8) ^ (a[2] << 16) ^ (a[3] << 24) ^ a[4] ^
           (a[5] << 8);
  }
};

static std::unordered_map<RawAddress, BackgroundConnection, BgConnHash>
    background_connections;

/*******************************************************************************
 *
 * Function         btm_update_scanner_filter_policy
 *
 * Description      This function updates the filter policy of scanner
 ******************************************************************************/
void btm_update_scanner_filter_policy(tBTM_BLE_SFP scan_policy) {
  uint32_t scan_interval = !btm_cb.ble_ctr_cb.inq_var.scan_interval
                               ? BTM_BLE_GAP_DISC_SCAN_INT
                               : btm_cb.ble_ctr_cb.inq_var.scan_interval;
  uint32_t scan_window = !btm_cb.ble_ctr_cb.inq_var.scan_window
                             ? BTM_BLE_GAP_DISC_SCAN_WIN
                             : btm_cb.ble_ctr_cb.inq_var.scan_window;

  log::verbose("");

  btm_cb.ble_ctr_cb.inq_var.sfp = scan_policy;
  btm_cb.ble_ctr_cb.inq_var.scan_type =
      btm_cb.ble_ctr_cb.inq_var.scan_type == BTM_BLE_SCAN_MODE_NONE
          ? BTM_BLE_SCAN_MODE_ACTI
          : btm_cb.ble_ctr_cb.inq_var.scan_type;

  btm_send_hci_set_scan_params(btm_cb.ble_ctr_cb.inq_var.scan_type,
                               (uint16_t)scan_interval, (uint16_t)scan_window,
                               btm_cb.ble_ctr_cb.addr_mgnt_cb.own_addr_type,
                               scan_policy);
}

/** Adds the device into acceptlist. Returns false if acceptlist is full and
 * device can't be added, true otherwise. */
bool BTM_AcceptlistAdd(const RawAddress& address) {
  return BTM_AcceptlistAdd(address, false);
}

/** Adds the device into acceptlist and indicates whether to using direct
 * connect parameters. Returns false if acceptlist is full and device can't
 * be added, true otherwise. */
bool BTM_AcceptlistAdd(const RawAddress& address, bool is_direct) {
  if (!controller_get_interface()->SupportsBle()) {
    log::warn("Controller does not support Le");
    return false;
  }

  return bluetooth::shim::ACL_AcceptLeConnectionFrom(
      BTM_Sec_GetAddressWithType(address), is_direct);
}

/** Removes the device from acceptlist */
void BTM_AcceptlistRemove(const RawAddress& address) {
  if (!controller_get_interface()->SupportsBle()) {
    log::warn("Controller does not support Le");
    return;
  }

  bluetooth::shim::ACL_IgnoreLeConnectionFrom(
      BTM_Sec_GetAddressWithType(address));
  return;
}

/** Clear the acceptlist, end any pending acceptlist connections */
void BTM_AcceptlistClear() {
  if (!controller_get_interface()->SupportsBle()) {
    log::warn("Controller does not support Le");
    return;
  }
  bluetooth::shim::ACL_IgnoreAllLeConnections();
}
