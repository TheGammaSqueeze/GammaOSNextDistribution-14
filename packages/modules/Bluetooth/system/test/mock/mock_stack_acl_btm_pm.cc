/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Generated mock file from original source file
 *   Functions generated:17
 */

#include <base/strings/stringprintf.h>

#include <cstdint>

#include "stack/btm/power_mode.h"
#include "stack/include/btm_status.h"
#include "test/common/mock_functions.h"
#include "types/raw_address.h"

bool BTM_ReadPowerMode(const RawAddress& /* remote_bda */,
                       tBTM_PM_MODE* /* p_mode */) {
  inc_func_call_count(__func__);
  return false;
}
bool BTM_SetLinkPolicyActiveMode(const RawAddress& /* remote_bda */) {
  inc_func_call_count(__func__);
  return false;
}
tBTM_CONTRL_STATE BTM_PM_ReadControllerState(void) {
  inc_func_call_count(__func__);
  return BTM_CONTRL_UNKNOWN;
}
uint8_t BTM_PM_ReadSniffLinkCount(void) {
  inc_func_call_count(__func__);
  return 0;
}
uint8_t BTM_PM_ReadBleLinkCount(void) {
  inc_func_call_count(__func__);
  return 0;
}
bool BTM_PM_DeviceInScanState(void) {
  inc_func_call_count(__func__);
  return false;
}
uint32_t BTM_PM_ReadBleScanDutyCycle(void) {
  inc_func_call_count(__func__);
  return 0;
}
tBTM_STATUS BTM_PmRegister(uint8_t /* mask */, uint8_t* /* p_pm_id */,
                           tBTM_PM_STATUS_CBACK* /* p_cb */) {
  inc_func_call_count(__func__);
  return BTM_SUCCESS;
}
tBTM_STATUS BTM_SetPowerMode(uint8_t /* pm_id */,
                             const RawAddress& /* remote_bda */,
                             const tBTM_PM_PWR_MD* /* p_mode */) {
  inc_func_call_count(__func__);
  return BTM_SUCCESS;
}
tBTM_STATUS BTM_SetSsrParams(const RawAddress& /* remote_bda */,
                             uint16_t /* max_lat */, uint16_t /* min_rmt_to */,
                             uint16_t /* min_loc_to */) {
  inc_func_call_count(__func__);
  return BTM_SUCCESS;
}
void BTM_PM_OnConnected(uint16_t /* handle */,
                        const RawAddress& /* remote_bda */) {
  inc_func_call_count(__func__);
}
void BTM_PM_OnDisconnected(uint16_t /* handle */) {
  inc_func_call_count(__func__);
}
void btm_pm_on_mode_change(tHCI_STATUS /* status */, uint16_t /* handle */,
                           tHCI_MODE /* current_mode */,
                           uint16_t /* interval */) {
  inc_func_call_count(__func__);
}
void btm_pm_on_sniff_subrating(tHCI_STATUS /* status */, uint16_t /* handle */,
                               uint16_t /* maximum_transmit_latency */,
                               uint16_t /* maximum_receive_latency */,
                               uint16_t /* minimum_remote_timeout */,
                               uint16_t /* minimum_local_timeout */) {
  inc_func_call_count(__func__);
}
void btm_pm_proc_cmd_status(tHCI_STATUS /* status */) {
  inc_func_call_count(__func__);
}
void btm_pm_proc_mode_change(tHCI_STATUS /* hci_status */,
                             uint16_t /* hci_handle */,
                             tHCI_MODE /* hci_mode */,
                             uint16_t /* interval */) {
  inc_func_call_count(__func__);
}
void btm_pm_proc_ssr_evt(uint8_t* /* p */, uint16_t /* evt_len */) {
  inc_func_call_count(__func__);
}
void btm_pm_reset(void) { inc_func_call_count(__func__); }
void process_ssr_event(tHCI_STATUS /* status */, uint16_t /* handle */,
                       uint16_t /* max_tx_lat */, uint16_t /* max_rx_lat */) {
  inc_func_call_count(__func__);
}
