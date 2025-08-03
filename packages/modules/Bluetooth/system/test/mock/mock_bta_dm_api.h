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
#pragma once

/*
 * Generated mock file from original source file
 *   Functions generated:47
 *
 *  mockcify.pl ver 0.6.1
 */

#include <cstdint>
#include <functional>

// Original included files, if any
#include <base/functional/bind.h>

#include <vector>

#include "bta/include/bta_api.h"
#include "bta/include/bta_sec_api.h"
#include "stack/include/bt_device_type.h"
#include "stack/include/bt_octets.h"
#include "types/ble_address_with_type.h"
#include "types/raw_address.h"

// Original usings

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace bta_dm_api {

// Shared state between mocked functions and tests
// Name: BTA_DmAddBleDevice
// Params: const RawAddress& bd_addr, tBLE_ADDR_TYPE addr_type, tBT_DEVICE_TYPE
// dev_type Return: void
struct BTA_DmAddBleDevice {
  std::function<void(const RawAddress& bd_addr, tBLE_ADDR_TYPE addr_type,
                     tBT_DEVICE_TYPE dev_type)>
      body{[](const RawAddress& bd_addr, tBLE_ADDR_TYPE addr_type,
              tBT_DEVICE_TYPE dev_type) {}};
  void operator()(const RawAddress& bd_addr, tBLE_ADDR_TYPE addr_type,
                  tBT_DEVICE_TYPE dev_type) {
    body(bd_addr, addr_type, dev_type);
  };
};
extern struct BTA_DmAddBleDevice BTA_DmAddBleDevice;

// Name: BTA_DmAddBleKey
// Params: const RawAddress& bd_addr, tBTA_LE_KEY_VALUE* p_le_key,
// tBTM_LE_KEY_TYPE key_type Return: void
struct BTA_DmAddBleKey {
  std::function<void(const RawAddress& bd_addr, tBTA_LE_KEY_VALUE* p_le_key,
                     tBTM_LE_KEY_TYPE key_type)>
      body{[](const RawAddress& bd_addr, tBTA_LE_KEY_VALUE* p_le_key,
              tBTM_LE_KEY_TYPE key_type) {}};
  void operator()(const RawAddress& bd_addr, tBTA_LE_KEY_VALUE* p_le_key,
                  tBTM_LE_KEY_TYPE key_type) {
    body(bd_addr, p_le_key, key_type);
  };
};
extern struct BTA_DmAddBleKey BTA_DmAddBleKey;

// Name: BTA_DmAddDevice
// Params: const RawAddress& bd_addr, DEV_CLASS dev_class, const LinkKey&
// link_key, uint8_t key_type, uint8_t pin_length Return: void
struct BTA_DmAddDevice {
  std::function<void(const RawAddress& bd_addr, DEV_CLASS dev_class,
                     const LinkKey& link_key, uint8_t key_type,
                     uint8_t pin_length)>
      body{[](const RawAddress& bd_addr, DEV_CLASS dev_class,
              const LinkKey& link_key, uint8_t key_type,
              uint8_t pin_length) {}};
  void operator()(const RawAddress& bd_addr, DEV_CLASS dev_class,
                  const LinkKey& link_key, uint8_t key_type,
                  uint8_t pin_length) {
    body(bd_addr, dev_class, link_key, key_type, pin_length);
  };
};
extern struct BTA_DmAddDevice BTA_DmAddDevice;

// Name: BTA_DmAllowWakeByHid
// Params:  std::vector<RawAddress> classic_hid_devices,
// std::vector<std::pair<RawAddress, uint8_t>> le_hid_devices Return: void
struct BTA_DmAllowWakeByHid {
  std::function<void(
      std::vector<RawAddress> classic_hid_devices,
      std::vector<std::pair<RawAddress, uint8_t>> le_hid_devices)>
      body{[](std::vector<RawAddress> classic_hid_devices,
              std::vector<std::pair<RawAddress, uint8_t>> le_hid_devices) {}};
  void operator()(std::vector<RawAddress> classic_hid_devices,
                  std::vector<std::pair<RawAddress, uint8_t>> le_hid_devices) {
    body(classic_hid_devices, le_hid_devices);
  };
};
extern struct BTA_DmAllowWakeByHid BTA_DmAllowWakeByHid;

// Name: BTA_DmBleConfigLocalPrivacy
// Params: bool privacy_enable
// Return: void
struct BTA_DmBleConfigLocalPrivacy {
  std::function<void(bool privacy_enable)> body{[](bool privacy_enable) {}};
  void operator()(bool privacy_enable) { body(privacy_enable); };
};
extern struct BTA_DmBleConfigLocalPrivacy BTA_DmBleConfigLocalPrivacy;

// Name: BTA_DmBleConfirmReply
// Params: const RawAddress& bd_addr, bool accept
// Return: void
struct BTA_DmBleConfirmReply {
  std::function<void(const RawAddress& bd_addr, bool accept)> body{
      [](const RawAddress& bd_addr, bool accept) {}};
  void operator()(const RawAddress& bd_addr, bool accept) {
    body(bd_addr, accept);
  };
};
extern struct BTA_DmBleConfirmReply BTA_DmBleConfirmReply;

// Name: BTA_DmBleCsisObserve
// Params: bool observe, tBTA_DM_SEARCH_CBACK* p_results_cb
// Return: void
struct BTA_DmBleCsisObserve {
  std::function<void(bool observe, tBTA_DM_SEARCH_CBACK* p_results_cb)> body{
      [](bool observe, tBTA_DM_SEARCH_CBACK* p_results_cb) {}};
  void operator()(bool observe, tBTA_DM_SEARCH_CBACK* p_results_cb) {
    body(observe, p_results_cb);
  };
};
extern struct BTA_DmBleCsisObserve BTA_DmBleCsisObserve;

// Name: BTA_DmBleGetEnergyInfo
// Params: tBTA_BLE_ENERGY_INFO_CBACK* p_cmpl_cback
// Return: void
struct BTA_DmBleGetEnergyInfo {
  std::function<void(tBTA_BLE_ENERGY_INFO_CBACK* p_cmpl_cback)> body{
      [](tBTA_BLE_ENERGY_INFO_CBACK* p_cmpl_cback) {}};
  void operator()(tBTA_BLE_ENERGY_INFO_CBACK* p_cmpl_cback) {
    body(p_cmpl_cback);
  };
};
extern struct BTA_DmBleGetEnergyInfo BTA_DmBleGetEnergyInfo;

// Name: BTA_DmBleObserve
// Params: bool start, uint8_t duration, tBTA_DM_SEARCH_CBACK* p_results_cb
// Return: void
struct BTA_DmBleObserve {
  std::function<void(bool start, uint8_t duration,
                     tBTA_DM_SEARCH_CBACK* p_results_cb)>
      body{[](bool start, uint8_t duration,
              tBTA_DM_SEARCH_CBACK* p_results_cb) {}};
  void operator()(bool start, uint8_t duration,
                  tBTA_DM_SEARCH_CBACK* p_results_cb) {
    body(start, duration, p_results_cb);
  };
};
extern struct BTA_DmBleObserve BTA_DmBleObserve;

// Name: BTA_DmBlePasskeyReply
// Params: const RawAddress& bd_addr, bool accept, uint32_t passkey
// Return: void
struct BTA_DmBlePasskeyReply {
  std::function<void(const RawAddress& bd_addr, bool accept, uint32_t passkey)>
      body{[](const RawAddress& bd_addr, bool accept, uint32_t passkey) {}};
  void operator()(const RawAddress& bd_addr, bool accept, uint32_t passkey) {
    body(bd_addr, accept, passkey);
  };
};
extern struct BTA_DmBlePasskeyReply BTA_DmBlePasskeyReply;

// Name: BTA_DmBleRequestMaxTxDataLength
// Params: const RawAddress& remote_device
// Return: void
struct BTA_DmBleRequestMaxTxDataLength {
  std::function<void(const RawAddress& remote_device)> body{
      [](const RawAddress& remote_device) {}};
  void operator()(const RawAddress& remote_device) { body(remote_device); };
};
extern struct BTA_DmBleRequestMaxTxDataLength BTA_DmBleRequestMaxTxDataLength;

// Name: BTA_DmBleResetId
// Params: void
// Return: void
struct BTA_DmBleResetId {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct BTA_DmBleResetId BTA_DmBleResetId;

// Name: BTA_DmBleScan
// Params: bool start, uint8_t duration_sec, bool low_latency_scan
// Return: void
struct BTA_DmBleScan {
  std::function<void(bool start, uint8_t duration_sec, bool low_latency_scan)>
      body{[](bool start, uint8_t duration_sec, bool low_latency_scan) {}};
  void operator()(bool start, uint8_t duration_sec, bool low_latency_scan) {
    body(start, duration_sec, low_latency_scan);
  };
};
extern struct BTA_DmBleScan BTA_DmBleScan;

// Name: BTA_DmBleSecurityGrant
// Params: const RawAddress& bd_addr, tBTA_DM_BLE_SEC_GRANT res
// Return: void
struct BTA_DmBleSecurityGrant {
  std::function<void(const RawAddress& bd_addr, tBTA_DM_BLE_SEC_GRANT res)>
      body{[](const RawAddress& bd_addr, tBTA_DM_BLE_SEC_GRANT res) {}};
  void operator()(const RawAddress& bd_addr, tBTA_DM_BLE_SEC_GRANT res) {
    body(bd_addr, res);
  };
};
extern struct BTA_DmBleSecurityGrant BTA_DmBleSecurityGrant;

// Name: BTA_DmBleSubrateRequest
// Params: const RawAddress& bd_addr, uint16_t subrate_min, uint16_t
// subrate_max, uint16_t max_latency, uint16_t cont_num, uint16_t timeout
// Return: void
struct BTA_DmBleSubrateRequest {
  std::function<void(const RawAddress& bd_addr, uint16_t subrate_min,
                     uint16_t subrate_max, uint16_t max_latency,
                     uint16_t cont_num, uint16_t timeout)>
      body{[](const RawAddress& bd_addr, uint16_t subrate_min,
              uint16_t subrate_max, uint16_t max_latency, uint16_t cont_num,
              uint16_t timeout) {}};
  void operator()(const RawAddress& bd_addr, uint16_t subrate_min,
                  uint16_t subrate_max, uint16_t max_latency, uint16_t cont_num,
                  uint16_t timeout) {
    body(bd_addr, subrate_min, subrate_max, max_latency, cont_num, timeout);
  };
};
extern struct BTA_DmBleSubrateRequest BTA_DmBleSubrateRequest;

// Name: BTA_DmBleUpdateConnectionParams
// Params: const RawAddress& bd_addr, uint16_t min_int, uint16_t max_int,
// uint16_t latency, uint16_t timeout, uint16_t min_ce_len, uint16_t max_ce_len
// Return: void
struct BTA_DmBleUpdateConnectionParams {
  std::function<void(const RawAddress& bd_addr, uint16_t min_int,
                     uint16_t max_int, uint16_t latency, uint16_t timeout,
                     uint16_t min_ce_len, uint16_t max_ce_len)>
      body{[](const RawAddress& bd_addr, uint16_t min_int, uint16_t max_int,
              uint16_t latency, uint16_t timeout, uint16_t min_ce_len,
              uint16_t max_ce_len) {}};
  void operator()(const RawAddress& bd_addr, uint16_t min_int, uint16_t max_int,
                  uint16_t latency, uint16_t timeout, uint16_t min_ce_len,
                  uint16_t max_ce_len) {
    body(bd_addr, min_int, max_int, latency, timeout, min_ce_len, max_ce_len);
  };
};
extern struct BTA_DmBleUpdateConnectionParams BTA_DmBleUpdateConnectionParams;

// Name: BTA_DmBond
// Params: const RawAddress& bd_addr, tBLE_ADDR_TYPE addr_type, tBT_TRANSPORT
// transport, tBT_DEVICE_TYPE device_type Return: void
struct BTA_DmBond {
  std::function<void(const RawAddress& bd_addr, tBLE_ADDR_TYPE addr_type,
                     tBT_TRANSPORT transport, tBT_DEVICE_TYPE device_type)>
      body{[](const RawAddress& bd_addr, tBLE_ADDR_TYPE addr_type,
              tBT_TRANSPORT transport, tBT_DEVICE_TYPE device_type) {}};
  void operator()(const RawAddress& bd_addr, tBLE_ADDR_TYPE addr_type,
                  tBT_TRANSPORT transport, tBT_DEVICE_TYPE device_type) {
    body(bd_addr, addr_type, transport, device_type);
  };
};
extern struct BTA_DmBond BTA_DmBond;

// Name: BTA_DmBondCancel
// Params: const RawAddress& bd_addr
// Return: void
struct BTA_DmBondCancel {
  std::function<void(const RawAddress& bd_addr)> body{
      [](const RawAddress& bd_addr) {}};
  void operator()(const RawAddress& bd_addr) { body(bd_addr); };
};
extern struct BTA_DmBondCancel BTA_DmBondCancel;

// Name: BTA_DmCheckLeAudioCapable
// Params: const RawAddress& address
// Return: bool
struct BTA_DmCheckLeAudioCapable {
  static bool return_value;
  std::function<bool(const RawAddress& address)> body{
      [](const RawAddress& address) { return return_value; }};
  bool operator()(const RawAddress& address) { return body(address); };
};
extern struct BTA_DmCheckLeAudioCapable BTA_DmCheckLeAudioCapable;

// Name: BTA_DmClearEventFilter
// Params: void
// Return: void
struct BTA_DmClearEventFilter {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct BTA_DmClearEventFilter BTA_DmClearEventFilter;

// Name: BTA_DmClearEventMask
// Params: void
// Return: void
struct BTA_DmClearEventMask {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct BTA_DmClearEventMask BTA_DmClearEventMask;

// Name: BTA_DmClearFilterAcceptList
// Params: void
// Return: void
struct BTA_DmClearFilterAcceptList {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct BTA_DmClearFilterAcceptList BTA_DmClearFilterAcceptList;

// Name: BTA_DmCloseACL
// Params: const RawAddress& bd_addr, bool remove_dev, tBT_TRANSPORT transport
// Return: void
struct BTA_DmCloseACL {
  std::function<void(const RawAddress& bd_addr, bool remove_dev,
                     tBT_TRANSPORT transport)>
      body{[](const RawAddress& bd_addr, bool remove_dev,
              tBT_TRANSPORT transport) {}};
  void operator()(const RawAddress& bd_addr, bool remove_dev,
                  tBT_TRANSPORT transport) {
    body(bd_addr, remove_dev, transport);
  };
};
extern struct BTA_DmCloseACL BTA_DmCloseACL;

// Name: BTA_DmConfirm
// Params: const RawAddress& bd_addr, bool accept
// Return: void
struct BTA_DmConfirm {
  std::function<void(const RawAddress& bd_addr, bool accept)> body{
      [](const RawAddress& bd_addr, bool accept) {}};
  void operator()(const RawAddress& bd_addr, bool accept) {
    body(bd_addr, accept);
  };
};
extern struct BTA_DmConfirm BTA_DmConfirm;

// Name: BTA_DmDisconnectAllAcls
// Params:
// Return: void
struct BTA_DmDisconnectAllAcls {
  std::function<void()> body{[]() {}};
  void operator()() { body(); };
};
extern struct BTA_DmDisconnectAllAcls BTA_DmDisconnectAllAcls;

// Name: BTA_DmDiscover
// Params: const RawAddress& bd_addr, tBTA_DM_SEARCH_CBACK* p_cback,
// tBT_TRANSPORT transport Return: void
struct BTA_DmDiscover {
  std::function<void(const RawAddress& bd_addr, tBTA_DM_SEARCH_CBACK* p_cback,
                     tBT_TRANSPORT transport)>
      body{[](const RawAddress& bd_addr, tBTA_DM_SEARCH_CBACK* p_cback,
              tBT_TRANSPORT transport) {}};
  void operator()(const RawAddress& bd_addr, tBTA_DM_SEARCH_CBACK* p_cback,
                  tBT_TRANSPORT transport) {
    body(bd_addr, p_cback, transport);
  };
};
extern struct BTA_DmDiscover BTA_DmDiscover;

// Name: BTA_DmGetConnectionState
// Params: const RawAddress& bd_addr
// Return: bool
struct BTA_DmGetConnectionState {
  static bool return_value;
  std::function<bool(const RawAddress& bd_addr)> body{
      [](const RawAddress& bd_addr) { return return_value; }};
  bool operator()(const RawAddress& bd_addr) { return body(bd_addr); };
};
extern struct BTA_DmGetConnectionState BTA_DmGetConnectionState;

// Name: BTA_DmLeRand
// Params: LeRandCallback cb
// Return: void
struct BTA_DmLeRand {
  std::function<void(LeRandCallback cb)> body{[](LeRandCallback cb) {}};
  void operator()(LeRandCallback cb) { body(std::move(cb)); };
};
extern struct BTA_DmLeRand BTA_DmLeRand;

// Name: BTA_DmLocalOob
// Params: void
// Return: void
struct BTA_DmLocalOob {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct BTA_DmLocalOob BTA_DmLocalOob;

// Name: BTA_DmPinReply
// Params: const RawAddress& bd_addr, bool accept, uint8_t pin_len, uint8_t*
// p_pin Return: void
struct BTA_DmPinReply {
  std::function<void(const RawAddress& bd_addr, bool accept, uint8_t pin_len,
                     uint8_t* p_pin)>
      body{[](const RawAddress& bd_addr, bool accept, uint8_t pin_len,
              uint8_t* p_pin) {}};
  void operator()(const RawAddress& bd_addr, bool accept, uint8_t pin_len,
                  uint8_t* p_pin) {
    body(bd_addr, accept, pin_len, p_pin);
  };
};
extern struct BTA_DmPinReply BTA_DmPinReply;

// Name: BTA_DmRemoveDevice
// Params: const RawAddress& bd_addr
// Return: tBTA_STATUS
struct BTA_DmRemoveDevice {
  static tBTA_STATUS return_value;
  std::function<tBTA_STATUS(const RawAddress& bd_addr)> body{
      [](const RawAddress& bd_addr) { return return_value; }};
  tBTA_STATUS operator()(const RawAddress& bd_addr) { return body(bd_addr); };
};
extern struct BTA_DmRemoveDevice BTA_DmRemoveDevice;

// Name: BTA_DmRestoreFilterAcceptList
// Params:  std::vector<std::pair<RawAddress, uint8_t>> le_devices
// Return: void
struct BTA_DmRestoreFilterAcceptList {
  std::function<void(std::vector<std::pair<RawAddress, uint8_t>> le_devices)>
      body{[](std::vector<std::pair<RawAddress, uint8_t>> le_devices) {}};
  void operator()(std::vector<std::pair<RawAddress, uint8_t>> le_devices) {
    body(le_devices);
  };
};
extern struct BTA_DmRestoreFilterAcceptList BTA_DmRestoreFilterAcceptList;

// Name: BTA_DmSearch
// Params: tBTA_DM_SEARCH_CBACK* p_cback
// Return: void
struct BTA_DmSearch {
  std::function<void(tBTA_DM_SEARCH_CBACK* p_cback)> body{
      [](tBTA_DM_SEARCH_CBACK* p_cback) {}};
  void operator()(tBTA_DM_SEARCH_CBACK* p_cback) { body(p_cback); };
};
extern struct BTA_DmSearch BTA_DmSearch;

// Name: BTA_DmSearchCancel
// Params: void
// Return: void
struct BTA_DmSearchCancel {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct BTA_DmSearchCancel BTA_DmSearchCancel;

// Name: BTA_DmSetBlePrefConnParams
// Params: const RawAddress& bd_addr, uint16_t min_conn_int, uint16_t
// max_conn_int, uint16_t peripheral_latency, uint16_t supervision_tout Return:
// void
struct BTA_DmSetBlePrefConnParams {
  std::function<void(const RawAddress& bd_addr, uint16_t min_conn_int,
                     uint16_t max_conn_int, uint16_t peripheral_latency,
                     uint16_t supervision_tout)>
      body{[](const RawAddress& bd_addr, uint16_t min_conn_int,
              uint16_t max_conn_int, uint16_t peripheral_latency,
              uint16_t supervision_tout) {}};
  void operator()(const RawAddress& bd_addr, uint16_t min_conn_int,
                  uint16_t max_conn_int, uint16_t peripheral_latency,
                  uint16_t supervision_tout) {
    body(bd_addr, min_conn_int, max_conn_int, peripheral_latency,
         supervision_tout);
  };
};
extern struct BTA_DmSetBlePrefConnParams BTA_DmSetBlePrefConnParams;

// Name: BTA_DmSetDefaultEventMaskExcept
// Params: uint64_t mask, uint64_t le_mask
// Return: void
struct BTA_DmSetDefaultEventMaskExcept {
  std::function<void(uint64_t mask, uint64_t le_mask)> body{
      [](uint64_t mask, uint64_t le_mask) {}};
  void operator()(uint64_t mask, uint64_t le_mask) { body(mask, le_mask); };
};
extern struct BTA_DmSetDefaultEventMaskExcept BTA_DmSetDefaultEventMaskExcept;

// Name: BTA_DmSetDeviceName
// Params: const char* p_name
// Return: void
struct BTA_DmSetDeviceName {
  std::function<void(const char* p_name)> body{[](const char* p_name) {}};
  void operator()(const char* p_name) { body(p_name); };
};
extern struct BTA_DmSetDeviceName BTA_DmSetDeviceName;

// Name: BTA_DmSetEncryption
// Params: const RawAddress& bd_addr, tBT_TRANSPORT transport,
// tBTA_DM_ENCRYPT_CBACK* p_callback, tBTM_BLE_SEC_ACT sec_act Return: void
struct BTA_DmSetEncryption {
  std::function<void(const RawAddress& bd_addr, tBT_TRANSPORT transport,
                     tBTA_DM_ENCRYPT_CBACK* p_callback,
                     tBTM_BLE_SEC_ACT sec_act)>
      body{[](const RawAddress& bd_addr, tBT_TRANSPORT transport,
              tBTA_DM_ENCRYPT_CBACK* p_callback, tBTM_BLE_SEC_ACT sec_act) {}};
  void operator()(const RawAddress& bd_addr, tBT_TRANSPORT transport,
                  tBTA_DM_ENCRYPT_CBACK* p_callback, tBTM_BLE_SEC_ACT sec_act) {
    body(bd_addr, transport, p_callback, sec_act);
  };
};
extern struct BTA_DmSetEncryption BTA_DmSetEncryption;

// Name: BTA_DmSetEventFilterConnectionSetupAllDevices
// Params:
// Return: void
struct BTA_DmSetEventFilterConnectionSetupAllDevices {
  std::function<void()> body{[]() {}};
  void operator()() { body(); };
};
extern struct BTA_DmSetEventFilterConnectionSetupAllDevices
    BTA_DmSetEventFilterConnectionSetupAllDevices;

// Name: BTA_DmSetEventFilterInquiryResultAllDevices
// Params:
// Return: void
struct BTA_DmSetEventFilterInquiryResultAllDevices {
  std::function<void()> body{[]() {}};
  void operator()() { body(); };
};
extern struct BTA_DmSetEventFilterInquiryResultAllDevices
    BTA_DmSetEventFilterInquiryResultAllDevices;

// Name: BTA_DmSetLocalDiRecord
// Params: tSDP_DI_RECORD* p_device_info, uint32_t* p_handle
// Return: tBTA_STATUS
struct BTA_DmSetLocalDiRecord {
  static tBTA_STATUS return_value;
  std::function<tBTA_STATUS(tSDP_DI_RECORD* p_device_info, uint32_t* p_handle)>
      body{[](tSDP_DI_RECORD* p_device_info, uint32_t* p_handle) {
        return return_value;
      }};
  tBTA_STATUS operator()(tSDP_DI_RECORD* p_device_info, uint32_t* p_handle) {
    return body(p_device_info, p_handle);
  };
};
extern struct BTA_DmSetLocalDiRecord BTA_DmSetLocalDiRecord;

// Name: BTA_DmSirkConfirmDeviceReply
// Params: const RawAddress& bd_addr, bool accept
// Return: void
struct BTA_DmSirkConfirmDeviceReply {
  std::function<void(const RawAddress& bd_addr, bool accept)> body{
      [](const RawAddress& bd_addr, bool accept) {}};
  void operator()(const RawAddress& bd_addr, bool accept) {
    body(bd_addr, accept);
  };
};
extern struct BTA_DmSirkConfirmDeviceReply BTA_DmSirkConfirmDeviceReply;

// Name: BTA_DmSirkSecCbRegister
// Params: tBTA_DM_SEC_CBACK* p_cback
// Return: void
struct BTA_DmSirkSecCbRegister {
  std::function<void(tBTA_DM_SEC_CBACK* p_cback)> body{
      [](tBTA_DM_SEC_CBACK* p_cback) {}};
  void operator()(tBTA_DM_SEC_CBACK* p_cback) { body(p_cback); };
};
extern struct BTA_DmSirkSecCbRegister BTA_DmSirkSecCbRegister;

// Name: BTA_EnableTestMode
// Params: void
// Return: void
struct BTA_EnableTestMode {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct BTA_EnableTestMode BTA_EnableTestMode;

// Name: BTA_GetEirService
// Params: const uint8_t* p_eir, size_t eir_len, tBTA_SERVICE_MASK* p_services
// Return: void
struct BTA_GetEirService {
  std::function<void(const uint8_t* p_eir, size_t eir_len,
                     tBTA_SERVICE_MASK* p_services)>
      body{[](const uint8_t* p_eir, size_t eir_len,
              tBTA_SERVICE_MASK* p_services) {}};
  void operator()(const uint8_t* p_eir, size_t eir_len,
                  tBTA_SERVICE_MASK* p_services) {
    body(p_eir, eir_len, p_services);
  };
};
extern struct BTA_GetEirService BTA_GetEirService;

// Name: BTA_VendorInit
// Params: void
// Return: void
struct BTA_VendorInit {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct BTA_VendorInit BTA_VendorInit;

// Name: BTA_dm_init
// Params:
// Return: void
struct BTA_dm_init {
  std::function<void()> body{[]() {}};
  void operator()() { body(); };
};
extern struct BTA_dm_init BTA_dm_init;

}  // namespace bta_dm_api
}  // namespace mock
}  // namespace test

// END mockcify generation
