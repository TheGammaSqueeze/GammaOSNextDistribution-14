/*
 * Copyright 2023 The Android Open Source Project
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
 *
 *  mockcify.pl ver 0.6.1
 */

// Mock include file to share data between tests and mock
#include "test/mock/mock_bta_dm_api.h"

#include <cstdint>

#include "test/common/mock_functions.h"

// Original usings

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace bta_dm_api {

// Function state capture and return values, if needed
struct BTA_DmAddBleDevice BTA_DmAddBleDevice;
struct BTA_DmAddBleKey BTA_DmAddBleKey;
struct BTA_DmAddDevice BTA_DmAddDevice;
struct BTA_DmAllowWakeByHid BTA_DmAllowWakeByHid;
struct BTA_DmBleConfigLocalPrivacy BTA_DmBleConfigLocalPrivacy;
struct BTA_DmBleConfirmReply BTA_DmBleConfirmReply;
struct BTA_DmBleCsisObserve BTA_DmBleCsisObserve;
struct BTA_DmBleGetEnergyInfo BTA_DmBleGetEnergyInfo;
struct BTA_DmBleObserve BTA_DmBleObserve;
struct BTA_DmBlePasskeyReply BTA_DmBlePasskeyReply;
struct BTA_DmBleRequestMaxTxDataLength BTA_DmBleRequestMaxTxDataLength;
struct BTA_DmBleResetId BTA_DmBleResetId;
struct BTA_DmBleScan BTA_DmBleScan;
struct BTA_DmBleSecurityGrant BTA_DmBleSecurityGrant;
struct BTA_DmBleSubrateRequest BTA_DmBleSubrateRequest;
struct BTA_DmBleUpdateConnectionParams BTA_DmBleUpdateConnectionParams;
struct BTA_DmBond BTA_DmBond;
struct BTA_DmBondCancel BTA_DmBondCancel;
struct BTA_DmCheckLeAudioCapable BTA_DmCheckLeAudioCapable;
struct BTA_DmClearEventFilter BTA_DmClearEventFilter;
struct BTA_DmClearEventMask BTA_DmClearEventMask;
struct BTA_DmClearFilterAcceptList BTA_DmClearFilterAcceptList;
struct BTA_DmCloseACL BTA_DmCloseACL;
struct BTA_DmConfirm BTA_DmConfirm;
struct BTA_DmDisconnectAllAcls BTA_DmDisconnectAllAcls;
struct BTA_DmDiscover BTA_DmDiscover;
struct BTA_DmGetConnectionState BTA_DmGetConnectionState;
struct BTA_DmLeRand BTA_DmLeRand;
struct BTA_DmLocalOob BTA_DmLocalOob;
struct BTA_DmPinReply BTA_DmPinReply;
struct BTA_DmRemoveDevice BTA_DmRemoveDevice;
struct BTA_DmRestoreFilterAcceptList BTA_DmRestoreFilterAcceptList;
struct BTA_DmSearch BTA_DmSearch;
struct BTA_DmSearchCancel BTA_DmSearchCancel;
struct BTA_DmSetBlePrefConnParams BTA_DmSetBlePrefConnParams;
struct BTA_DmSetDefaultEventMaskExcept BTA_DmSetDefaultEventMaskExcept;
struct BTA_DmSetDeviceName BTA_DmSetDeviceName;
struct BTA_DmSetEncryption BTA_DmSetEncryption;
struct BTA_DmSetEventFilterConnectionSetupAllDevices
    BTA_DmSetEventFilterConnectionSetupAllDevices;
struct BTA_DmSetEventFilterInquiryResultAllDevices
    BTA_DmSetEventFilterInquiryResultAllDevices;
struct BTA_DmSetLocalDiRecord BTA_DmSetLocalDiRecord;
struct BTA_DmSirkConfirmDeviceReply BTA_DmSirkConfirmDeviceReply;
struct BTA_DmSirkSecCbRegister BTA_DmSirkSecCbRegister;
struct BTA_EnableTestMode BTA_EnableTestMode;
struct BTA_GetEirService BTA_GetEirService;
struct BTA_VendorInit BTA_VendorInit;
struct BTA_dm_init BTA_dm_init;

}  // namespace bta_dm_api
}  // namespace mock
}  // namespace test

// Mocked function return values, if any
namespace test {
namespace mock {
namespace bta_dm_api {

bool BTA_DmCheckLeAudioCapable::return_value = false;
bool BTA_DmGetConnectionState::return_value = false;
tBTA_STATUS BTA_DmRemoveDevice::return_value = BTA_SUCCESS;
tBTA_STATUS BTA_DmSetLocalDiRecord::return_value = BTA_SUCCESS;

}  // namespace bta_dm_api
}  // namespace mock
}  // namespace test

// Mocked functions, if any
void BTA_DmAddBleDevice(const RawAddress& bd_addr, tBLE_ADDR_TYPE addr_type,
                        tBT_DEVICE_TYPE dev_type) {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_DmAddBleDevice(bd_addr, addr_type, dev_type);
}
void BTA_DmAddBleKey(const RawAddress& bd_addr, tBTA_LE_KEY_VALUE* p_le_key,
                     tBTM_LE_KEY_TYPE key_type) {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_DmAddBleKey(bd_addr, p_le_key, key_type);
}
void BTA_DmAddDevice(const RawAddress& bd_addr, DEV_CLASS dev_class,
                     const LinkKey& link_key, uint8_t key_type,
                     uint8_t pin_length) {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_DmAddDevice(bd_addr, dev_class, link_key,
                                          key_type, pin_length);
}
void BTA_DmAllowWakeByHid(
    std::vector<RawAddress> classic_hid_devices,
    std::vector<std::pair<RawAddress, uint8_t>> le_hid_devices) {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_DmAllowWakeByHid(classic_hid_devices,
                                               le_hid_devices);
}
void BTA_DmBleConfigLocalPrivacy(bool privacy_enable) {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_DmBleConfigLocalPrivacy(privacy_enable);
}
void BTA_DmBleConfirmReply(const RawAddress& bd_addr, bool accept) {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_DmBleConfirmReply(bd_addr, accept);
}
void BTA_DmBleCsisObserve(bool observe, tBTA_DM_SEARCH_CBACK* p_results_cb) {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_DmBleCsisObserve(observe, p_results_cb);
}
void BTA_DmBleGetEnergyInfo(tBTA_BLE_ENERGY_INFO_CBACK* p_cmpl_cback) {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_DmBleGetEnergyInfo(p_cmpl_cback);
}
void BTA_DmBleObserve(bool start, uint8_t duration,
                      tBTA_DM_SEARCH_CBACK* p_results_cb) {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_DmBleObserve(start, duration, p_results_cb);
}
void BTA_DmBlePasskeyReply(const RawAddress& bd_addr, bool accept,
                           uint32_t passkey) {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_DmBlePasskeyReply(bd_addr, accept, passkey);
}
void BTA_DmBleRequestMaxTxDataLength(const RawAddress& remote_device) {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_DmBleRequestMaxTxDataLength(remote_device);
}
void BTA_DmBleResetId(void) {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_DmBleResetId();
}
void BTA_DmBleScan(bool start, uint8_t duration_sec, bool low_latency_scan) {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_DmBleScan(start, duration_sec, low_latency_scan);
}
void BTA_DmBleSecurityGrant(const RawAddress& bd_addr,
                            tBTA_DM_BLE_SEC_GRANT res) {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_DmBleSecurityGrant(bd_addr, res);
}
void BTA_DmBleSubrateRequest(const RawAddress& bd_addr, uint16_t subrate_min,
                             uint16_t subrate_max, uint16_t max_latency,
                             uint16_t cont_num, uint16_t timeout) {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_DmBleSubrateRequest(
      bd_addr, subrate_min, subrate_max, max_latency, cont_num, timeout);
}
void BTA_DmBleUpdateConnectionParams(const RawAddress& bd_addr,
                                     uint16_t min_int, uint16_t max_int,
                                     uint16_t latency, uint16_t timeout,
                                     uint16_t min_ce_len, uint16_t max_ce_len) {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_DmBleUpdateConnectionParams(
      bd_addr, min_int, max_int, latency, timeout, min_ce_len, max_ce_len);
}
void BTA_DmBond(const RawAddress& bd_addr, tBLE_ADDR_TYPE addr_type,
                tBT_TRANSPORT transport, tBT_DEVICE_TYPE device_type) {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_DmBond(bd_addr, addr_type, transport,
                                     device_type);
}
void BTA_DmBondCancel(const RawAddress& bd_addr) {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_DmBondCancel(bd_addr);
}
bool BTA_DmCheckLeAudioCapable(const RawAddress& address) {
  inc_func_call_count(__func__);
  return test::mock::bta_dm_api::BTA_DmCheckLeAudioCapable(address);
}
void BTA_DmClearEventFilter(void) {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_DmClearEventFilter();
}
void BTA_DmClearEventMask(void) {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_DmClearEventMask();
}
void BTA_DmClearFilterAcceptList(void) {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_DmClearFilterAcceptList();
}
void BTA_DmCloseACL(const RawAddress& bd_addr, bool remove_dev,
                    tBT_TRANSPORT transport) {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_DmCloseACL(bd_addr, remove_dev, transport);
}
void BTA_DmConfirm(const RawAddress& bd_addr, bool accept) {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_DmConfirm(bd_addr, accept);
}
void BTA_DmDisconnectAllAcls() {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_DmDisconnectAllAcls();
}
void BTA_DmDiscover(const RawAddress& bd_addr, tBTA_DM_SEARCH_CBACK* p_cback,
                    tBT_TRANSPORT transport) {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_DmDiscover(bd_addr, p_cback, transport);
}
bool BTA_DmGetConnectionState(const RawAddress& bd_addr) {
  inc_func_call_count(__func__);
  return test::mock::bta_dm_api::BTA_DmGetConnectionState(bd_addr);
}
void BTA_DmLeRand(LeRandCallback cb) {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_DmLeRand(std::move(cb));
}
void BTA_DmLocalOob(void) {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_DmLocalOob();
}
void BTA_DmPinReply(const RawAddress& bd_addr, bool accept, uint8_t pin_len,
                    uint8_t* p_pin) {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_DmPinReply(bd_addr, accept, pin_len, p_pin);
}
tBTA_STATUS BTA_DmRemoveDevice(const RawAddress& bd_addr) {
  inc_func_call_count(__func__);
  return test::mock::bta_dm_api::BTA_DmRemoveDevice(bd_addr);
}
void BTA_DmRestoreFilterAcceptList(
    std::vector<std::pair<RawAddress, uint8_t>> le_devices) {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_DmRestoreFilterAcceptList(le_devices);
}
void BTA_DmSearch(tBTA_DM_SEARCH_CBACK* p_cback) {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_DmSearch(p_cback);
}
void BTA_DmSearchCancel(void) {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_DmSearchCancel();
}
void BTA_DmSetBlePrefConnParams(const RawAddress& bd_addr,
                                uint16_t min_conn_int, uint16_t max_conn_int,
                                uint16_t peripheral_latency,
                                uint16_t supervision_tout) {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_DmSetBlePrefConnParams(
      bd_addr, min_conn_int, max_conn_int, peripheral_latency,
      supervision_tout);
}
void BTA_DmSetDefaultEventMaskExcept(uint64_t mask, uint64_t le_mask) {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_DmSetDefaultEventMaskExcept(mask, le_mask);
}
void BTA_DmSetDeviceName(const char* p_name) {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_DmSetDeviceName(p_name);
}
void BTA_DmSetEncryption(const RawAddress& bd_addr, tBT_TRANSPORT transport,
                         tBTA_DM_ENCRYPT_CBACK* p_callback,
                         tBTM_BLE_SEC_ACT sec_act) {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_DmSetEncryption(bd_addr, transport, p_callback,
                                              sec_act);
}
void BTA_DmSetEventFilterConnectionSetupAllDevices() {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_DmSetEventFilterConnectionSetupAllDevices();
}
void BTA_DmSetEventFilterInquiryResultAllDevices() {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_DmSetEventFilterInquiryResultAllDevices();
}
tBTA_STATUS BTA_DmSetLocalDiRecord(tSDP_DI_RECORD* p_device_info,
                                   uint32_t* p_handle) {
  inc_func_call_count(__func__);
  return test::mock::bta_dm_api::BTA_DmSetLocalDiRecord(p_device_info,
                                                        p_handle);
}
void BTA_DmSirkConfirmDeviceReply(const RawAddress& bd_addr, bool accept) {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_DmSirkConfirmDeviceReply(bd_addr, accept);
}
void BTA_DmSirkSecCbRegister(tBTA_DM_SEC_CBACK* p_cback) {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_DmSirkSecCbRegister(p_cback);
}
void BTA_EnableTestMode(void) {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_EnableTestMode();
}
void BTA_GetEirService(const uint8_t* p_eir, size_t eir_len,
                       tBTA_SERVICE_MASK* p_services) {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_GetEirService(p_eir, eir_len, p_services);
}
void BTA_VendorInit(void) {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_VendorInit();
}
void BTA_dm_init() {
  inc_func_call_count(__func__);
  test::mock::bta_dm_api::BTA_dm_init();
}
// Mocked functions complete
// END mockcify generation
