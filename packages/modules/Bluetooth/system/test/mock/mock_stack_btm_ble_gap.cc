/*
 * Copyright 2021 The Android Open Source Project
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
 *   Functions generated:47
 */

#include <base/functional/bind.h>
#include <base/strings/string_number_conversions.h>

#include <cstdint>
#include <vector>

#include "stack/btm/btm_ble_int.h"
#include "stack/btm/btm_ble_int_types.h"
#include "stack/include/bt_dev_class.h"
#include "stack/include/btm_api_types.h"
#include "stack/include/hci_error_code.h"
#include "test/common/mock_functions.h"
#include "types/ble_address_with_type.h"
#include "types/raw_address.h"

using StartSyncCb = base::Callback<void(
    uint8_t /*status*/, uint16_t /*sync_handle*/, uint8_t /*advertising_sid*/,
    uint8_t /*address_type*/, RawAddress /*address*/, uint8_t /*phy*/,
    uint16_t /*interval*/)>;
using SyncReportCb = base::Callback<void(
    uint16_t /*sync_handle*/, int8_t /*tx_power*/, int8_t /*rssi*/,
    uint8_t /*status*/, std::vector<uint8_t> /*data*/)>;
using SyncLostCb = base::Callback<void(uint16_t /*sync_handle*/)>;
using SyncTransferCb = base::Callback<void(uint8_t /*status*/, RawAddress)>;

bool ble_vnd_is_included() {
  inc_func_call_count(__func__);
  return false;
}
bool BTM_BleConfigPrivacy(bool /* privacy_mode */) {
  inc_func_call_count(__func__);
  return false;
}
bool BTM_BleLocalPrivacyEnabled(void) {
  inc_func_call_count(__func__);
  return false;
}
bool btm_ble_cancel_remote_name(const RawAddress& /* remote_bda */) {
  inc_func_call_count(__func__);
  return false;
}
bool btm_ble_clear_topology_mask(tBTM_BLE_STATE_MASK /* request_state_mask */) {
  inc_func_call_count(__func__);
  return false;
}
bool btm_ble_set_topology_mask(tBTM_BLE_STATE_MASK /* request_state_mask */) {
  inc_func_call_count(__func__);
  return false;
}
bool btm_ble_topology_check(tBTM_BLE_STATE_MASK /* request_state_mask */) {
  inc_func_call_count(__func__);
  return false;
}
tBTM_STATUS BTM_BleObserve(bool /* start */, uint8_t /* duration */,
                           tBTM_INQ_RESULTS_CB* /* p_results_cb */,
                           tBTM_CMPL_CB* /* p_cmpl_cb */,
                           bool /* low_latency_scan */) {
  inc_func_call_count(__func__);
  return BTM_SUCCESS;
}
void BTM_BleOpportunisticObserve(bool /* enable */,
                                 tBTM_INQ_RESULTS_CB* /* p_results_cb */) {
  inc_func_call_count(__func__);
}
void BTM_BleTargetAnnouncementObserve(bool /* enable */,
                                      tBTM_INQ_RESULTS_CB* /* p_results_cb */) {
  inc_func_call_count(__func__);
}
tBTM_STATUS btm_ble_read_remote_name(const RawAddress& /* remote_bda */,
                                     tBTM_CMPL_CB* /* p_cb */) {
  inc_func_call_count(__func__);
  return BTM_SUCCESS;
}
tBTM_STATUS btm_ble_set_connectability(uint16_t /* combined_mode */) {
  inc_func_call_count(__func__);
  return BTM_SUCCESS;
}
tBTM_STATUS btm_ble_set_discoverability(uint16_t /* combined_mode */) {
  inc_func_call_count(__func__);
  return BTM_SUCCESS;
}
tBTM_STATUS btm_ble_start_inquiry(uint8_t /* duration */) {
  inc_func_call_count(__func__);
  return BTM_SUCCESS;
}
uint16_t BTM_BleReadConnectability() {
  inc_func_call_count(__func__);
  return 0;
}
uint16_t BTM_BleReadDiscoverability() {
  inc_func_call_count(__func__);
  return 0;
}
uint8_t BTM_BleMaxMultiAdvInstanceCount(void) {
  inc_func_call_count(__func__);
  return 0;
}
void BTM_BleGetDynamicAudioBuffer(
    tBTM_BT_DYNAMIC_AUDIO_BUFFER_CB /* p_dynamic_audio_buffer_cb*/[]) {
  inc_func_call_count(__func__);
}
void BTM_BleGetVendorCapabilities(tBTM_BLE_VSC_CB* /* p_cmn_vsc_cb */) {
  inc_func_call_count(__func__);
}
void BTM_BleSetScanParams(uint32_t /* scan_interval */,
                          uint32_t /* scan_window */,
                          tBLE_SCAN_MODE /* scan_mode */,
                          base::Callback<void(uint8_t)> /* cb */) {
  inc_func_call_count(__func__);
}
void btm_ble_decrement_link_topology_mask(uint8_t /* link_role */) {
  inc_func_call_count(__func__);
}
void btm_ble_dir_adv_tout(void) { inc_func_call_count(__func__); }
void btm_ble_free() { inc_func_call_count(__func__); }
void btm_ble_increment_link_topology_mask(uint8_t /* link_role */) {
  inc_func_call_count(__func__);
}
void btm_ble_init(void) { inc_func_call_count(__func__); }
DEV_CLASS btm_ble_get_appearance_as_cod(
    std::vector<uint8_t> const& /* data */) {
  inc_func_call_count(__func__);
  return kDevClassUnclassified;
}
void btm_ble_process_adv_addr(RawAddress& /* bda */,
                              tBLE_ADDR_TYPE* /* addr_type */) {
  inc_func_call_count(__func__);
}
void btm_ble_process_adv_pkt_cont(
    uint16_t /* evt_type */, tBLE_ADDR_TYPE /* addr_type */,
    const RawAddress& /* bda */, uint8_t /* primary_phy */,
    uint8_t /* secondary_phy */, uint8_t /* advertising_sid */,
    int8_t /* tx_power */, int8_t /* rssi */, uint16_t /* periodic_adv_int */,
    uint8_t /* data_len */, const uint8_t* /* data */,
    const RawAddress& /* original_bda */) {
  inc_func_call_count(__func__);
}
void btm_ble_process_adv_pkt_cont_for_inquiry(
    uint16_t /* evt_type */, tBLE_ADDR_TYPE /* addr_type */,
    const RawAddress& /* bda */, uint8_t /* primary_phy */,
    uint8_t /* secondary_phy */, uint8_t /* advertising_sid */,
    int8_t /* tx_power */, int8_t /* rssi */, uint16_t /* periodic_adv_int */,
    std::vector<uint8_t> /* advertising_data */) {
  inc_func_call_count(__func__);
}
void btm_ble_read_remote_features_complete(uint8_t* /* p */,
                                           uint8_t /* length */) {
  inc_func_call_count(__func__);
}
void btm_ble_read_remote_name_cmpl(bool /* status */,
                                   const RawAddress& /* bda */,
                                   uint16_t /* length */, char* /* p_name */) {
  inc_func_call_count(__func__);
}
void btm_ble_set_adv_flag(uint16_t /* connect_mode */,
                          uint16_t /* disc_mode */) {
  inc_func_call_count(__func__);
}
void btm_ble_stop_inquiry(void) { inc_func_call_count(__func__); }
void btm_ble_update_dmt_flag_bits(uint8_t* /* adv_flag_value */,
                                  const uint16_t /* connect_mode */,
                                  const uint16_t /* disc_mode */) {
  inc_func_call_count(__func__);
}
void btm_ble_update_mode_operation(uint8_t /* link_role */,
                                   const RawAddress* /* bd_addr */,
                                   tHCI_STATUS /* status */) {
  inc_func_call_count(__func__);
}
void btm_ble_write_adv_enable_complete(uint8_t* /* p */,
                                       uint16_t /* evt_len */) {
  inc_func_call_count(__func__);
}
void btm_send_hci_set_scan_params(uint8_t /* scan_type */,
                                  uint16_t /* scan_int */,
                                  uint16_t /* scan_win */,
                                  tBLE_ADDR_TYPE /* addr_type_own */,
                                  uint8_t /* scan_filter_policy */) {
  inc_func_call_count(__func__);
}
void btm_ble_periodic_adv_sync_tx_rcvd(const uint8_t* /* p */,
                                       uint16_t /* param_len */) {
  inc_func_call_count(__func__);
}
void btm_ble_biginfo_adv_report_rcvd(const uint8_t* /* p */,
                                     uint16_t /* param_len */) {
  inc_func_call_count(__func__);
}
