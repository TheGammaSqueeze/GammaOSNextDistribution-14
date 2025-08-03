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
 *   Functions generated:85
 */

#include <base/functional/bind.h>
#include <base/functional/callback.h>

#include <cstdint>

#include "main/shim/btm_api.h"
#include "stack/include/bt_octets.h"
#include "stack/include/btm_ble_api_types.h"
#include "test/common/mock_functions.h"
#include "types/bt_transport.h"
#include "types/raw_address.h"

Octet16 octet16;

bool bluetooth::shim::BTM_HasEirService(const uint32_t* /* p_eir_uuid */,
                                        uint16_t /* uuid16 */) {
  inc_func_call_count(__func__);
  return false;
}
bool bluetooth::shim::BTM_ReadConnectedTransportAddress(
    RawAddress* /* remote_bda */, tBT_TRANSPORT /* transport */) {
  inc_func_call_count(__func__);
  return false;
}
bool bluetooth::shim::BTM_ReadRemoteConnectionAddr(
    const RawAddress& /* pseudo_addr */, RawAddress& /* conn_addr */,
    tBLE_ADDR_TYPE* /* p_addr_type */, bool /* ota_address */) {
  inc_func_call_count(__func__);
  return false;
}

tBTM_INQ_INFO* bluetooth::shim::BTM_InqDbFirst(void) {
  inc_func_call_count(__func__);
  return nullptr;
}
tBTM_INQ_INFO* bluetooth::shim::BTM_InqDbNext(tBTM_INQ_INFO* /* p_cur */) {
  inc_func_call_count(__func__);
  return nullptr;
}
tBTM_INQ_INFO* bluetooth::shim::BTM_InqDbRead(const RawAddress& /* p_bda */) {
  inc_func_call_count(__func__);
  return nullptr;
}
tBTM_STATUS bluetooth::shim::BTM_BleObserve(
    bool /* start */, uint8_t /* duration_sec */,
    tBTM_INQ_RESULTS_CB* /* p_results_cb */, tBTM_CMPL_CB* /* p_cmpl_cb */) {
  inc_func_call_count(__func__);
  return BTM_SUCCESS;
}
void bluetooth::shim::BTM_BleOpportunisticObserve(
    bool /* enable */, tBTM_INQ_RESULTS_CB* /* p_results_cb */) {
  inc_func_call_count(__func__);
}
void bluetooth::shim::BTM_BleTargetAnnouncementObserve(
    bool /* enable */, tBTM_INQ_RESULTS_CB* /* p_results_cb */) {
  inc_func_call_count(__func__);
}
tBTM_STATUS bluetooth::shim::BTM_CancelRemoteDeviceName(void) {
  inc_func_call_count(__func__);
  return BTM_SUCCESS;
}
tBTM_STATUS bluetooth::shim::BTM_ClearInqDb(const RawAddress* /* p_bda */) {
  inc_func_call_count(__func__);
  return BTM_SUCCESS;
}
tBTM_STATUS bluetooth::shim::BTM_ReadRemoteDeviceName(
    const RawAddress& /* raw_address */, tBTM_NAME_CMPL_CB* /* callback */,
    tBT_TRANSPORT /* transport */) {
  inc_func_call_count(__func__);
  return BTM_SUCCESS;
}

tBTM_STATUS bluetooth::shim::BTM_SetConnectability(uint16_t /* page_mode */,
                                                   uint16_t /* window */,
                                                   uint16_t /* interval */) {
  inc_func_call_count(__func__);
  return BTM_SUCCESS;
}
tBTM_STATUS bluetooth::shim::BTM_SetDeviceClass(DEV_CLASS /* dev_class */) {
  inc_func_call_count(__func__);
  return BTM_SUCCESS;
}
tBTM_STATUS bluetooth::shim::BTM_SetDiscoverability(
    uint16_t /* discoverable_mode */, uint16_t /* window */,
    uint16_t /* interval */) {
  inc_func_call_count(__func__);
  return BTM_SUCCESS;
}

tBTM_STATUS bluetooth::shim::BTM_SetInquiryMode(uint8_t /* inquiry_mode */) {
  inc_func_call_count(__func__);
  return BTM_SUCCESS;
}
uint16_t bluetooth::shim::BTM_GetHCIConnHandle(
    const RawAddress& /* remote_bda */, tBT_TRANSPORT /* transport */) {
  inc_func_call_count(__func__);
  return 0;
}
uint8_t bluetooth::shim::BTM_BleMaxMultiAdvInstanceCount() {
  inc_func_call_count(__func__);
  return 0;
}
void bluetooth::shim::BTM_AddEirService(uint32_t* /* p_eir_uuid */,
                                        uint16_t /* uuid16 */) {
  inc_func_call_count(__func__);
}
void bluetooth::shim::BTM_BleAdvFilterParamSetup(
    tBTM_BLE_SCAN_COND_OP /* action */, tBTM_BLE_PF_FILT_INDEX /* filt_index */,
    std::unique_ptr<btgatt_filt_param_setup_t> /* p_filt_params */,
    tBTM_BLE_PF_PARAM_CB /* cb */) {
  inc_func_call_count(__func__);
}

void bluetooth::shim::BTM_BleSetConnScanParams(uint32_t /* scan_interval */,
                                               uint32_t /* scan_window */) {
  inc_func_call_count(__func__);
}

void bluetooth::shim::BTM_BleSetPrefConnParams(
    const RawAddress& /* bd_addr */, uint16_t /* min_conn_int */,
    uint16_t /* max_conn_int */, uint16_t /* peripheral_latency */,
    uint16_t /* supervision_tout */) {
  inc_func_call_count(__func__);
}

void bluetooth::shim::BTM_EnableInterlacedInquiryScan() {
  inc_func_call_count(__func__);
}
void bluetooth::shim::BTM_EnableInterlacedPageScan() {
  inc_func_call_count(__func__);
}

void bluetooth::shim::BTM_ReadConnectionAddr(const RawAddress& /* remote_bda */,
                                             RawAddress& /* local_conn_addr */,
                                             tBLE_ADDR_TYPE* /* p_addr_type */,
                                             bool /* ota_address */) {
  inc_func_call_count(__func__);
}

void bluetooth::shim::SendRemoteNameRequest(
    const RawAddress& /* raw_address */) {
  inc_func_call_count(__func__);
}
void btm_api_process_extended_inquiry_result(
    RawAddress /* raw_address */, uint8_t /* page_scan_rep_mode */,
    DEV_CLASS /* device_class */, uint16_t /* clock_offset */,
    int8_t /* rssi */, const uint8_t* /* eir_data */, size_t /* eir_len */) {
  inc_func_call_count(__func__);
}
void btm_api_process_inquiry_result(const RawAddress& /* raw_address */,
                                    uint8_t /* page_scan_rep_mode */,
                                    DEV_CLASS /* device_class */,
                                    uint16_t /* clock_offset */) {
  inc_func_call_count(__func__);
}
void btm_api_process_inquiry_result_with_rssi(RawAddress /* raw_address */,
                                              uint8_t /* page_scan_rep_mode */,
                                              DEV_CLASS /* device_class */,
                                              uint16_t /* clock_offset */,
                                              int8_t /* rssi */) {
  inc_func_call_count(__func__);
}

tBTM_STATUS bluetooth::shim::BTM_ClearEventFilter() {
  inc_func_call_count(__func__);
  return BTM_SUCCESS;
}

tBTM_STATUS bluetooth::shim::BTM_ClearEventMask() {
  inc_func_call_count(__func__);
  return BTM_SUCCESS;
}

tBTM_STATUS bluetooth::shim::BTM_ClearFilterAcceptList() {
  inc_func_call_count(__func__);
  return BTM_SUCCESS;
}

tBTM_STATUS bluetooth::shim::BTM_DisconnectAllAcls() {
  inc_func_call_count(__func__);
  return BTM_SUCCESS;
}

tBTM_STATUS bluetooth::shim::BTM_LeRand(LeRandCallback /* cb */) {
  inc_func_call_count(__func__);
  return BTM_SUCCESS;
}

tBTM_STATUS bluetooth::shim::BTM_SetEventFilterConnectionSetupAllDevices() {
  inc_func_call_count(__func__);
  return BTM_SUCCESS;
}

tBTM_STATUS bluetooth::shim::BTM_AllowWakeByHid(
    std::vector<RawAddress> /* classic_hid_devices */,
    std::vector<std::pair<RawAddress, uint8_t>> /* le_hid_devices */) {
  inc_func_call_count(__func__);
  return BTM_SUCCESS;
}

tBTM_STATUS bluetooth::shim::BTM_RestoreFilterAcceptList(
    std::vector<std::pair<RawAddress, uint8_t>> /* le_devices */) {
  inc_func_call_count(__func__);
  return BTM_SUCCESS;
}

tBTM_STATUS bluetooth::shim::BTM_SetDefaultEventMaskExcept(
    uint64_t /* mask */, uint64_t /* le_mask */) {
  inc_func_call_count(__func__);
  return BTM_SUCCESS;
}

tBTM_STATUS bluetooth::shim::BTM_SetEventFilterInquiryResultAllDevices() {
  inc_func_call_count(__func__);
  return BTM_SUCCESS;
}

tBTM_STATUS bluetooth::shim::BTM_BleResetId() {
  inc_func_call_count(__func__);
  return BTM_SUCCESS;
}
size_t bluetooth::shim::BTM_BleGetNumberOfAdvertisingInstancesInUse() {
  inc_func_call_count(__func__);
  return 0;
}
