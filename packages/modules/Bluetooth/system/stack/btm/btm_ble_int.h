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
 *  this file contains the main Bluetooth Manager (BTM) internal
 *  definitions.
 *
 ******************************************************************************/

#ifndef BTM_BLE_INT_H
#define BTM_BLE_INT_H

#include "stack/btm/btm_ble_int_types.h"
#include "stack/btm/security_device_record.h"
#include "stack/include/hci_error_code.h"
#include "types/ble_address_with_type.h"
#include "types/raw_address.h"

void btm_send_hci_set_scan_params(uint8_t scan_type, uint16_t scan_int,
                                  uint16_t scan_win,
                                  tBLE_ADDR_TYPE addr_type_own,
                                  uint8_t scan_filter_policy);

void btm_ble_init(void);
void btm_ble_free();
void btm_ble_connected(const RawAddress& bda, uint16_t handle, uint8_t enc_mode,
                       uint8_t role, tBLE_ADDR_TYPE addr_type,
                       bool addr_matched,
                       bool can_read_discoverable_characteristics);

/* acceptlist function */
void btm_update_scanner_filter_policy(tBTM_BLE_SFP scan_policy);

/* background connection function */
void btm_ble_update_mode_operation(uint8_t link_role, const RawAddress* bda,
                                   tHCI_STATUS status);
/* BLE address management */
void btm_gen_resolvable_private_addr(
    base::Callback<void(const RawAddress& rpa)> cb);

tBTM_SEC_DEV_REC* btm_ble_resolve_random_addr(const RawAddress& random_bda);
void btm_gen_resolve_paddr_low(const RawAddress& address);

void btm_ble_batchscan_init(void);
void btm_ble_adv_filter_init(void);
bool btm_ble_topology_check(tBTM_BLE_STATE_MASK request);
bool btm_ble_clear_topology_mask(tBTM_BLE_STATE_MASK request_state);
bool btm_ble_set_topology_mask(tBTM_BLE_STATE_MASK request_state);

void btm_ble_scanner_init(void);
void btm_ble_scanner_cleanup(void);

#endif
