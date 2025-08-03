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
 *   Functions generated:62
 *
 *  mockcify.pl ver 0.3.0
 */

#include <cstdint>
#include <functional>

// Original included files, if any
#include <cstdint>

#include "bta/dm/bta_dm_disc_int.h"
#include "bta/dm/bta_dm_sec_int.h"
#include "types/raw_address.h"

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace bta_dm_act {

// Shared state between mocked functions and tests
// Name: BTA_DmSetVisibility
// Params: bt_scan_mode_t mode
// Return: bool
struct BTA_DmSetVisibility {
  bool return_value{false};
  std::function<bool(bt_scan_mode_t mode)> body{
      [this](bt_scan_mode_t mode) { return return_value; }};
  bool operator()(bt_scan_mode_t mode) { return body(mode); };
};
extern struct BTA_DmSetVisibility BTA_DmSetVisibility;

// Name: BTA_dm_acl_down
// Params: const RawAddress bd_addr, tBT_TRANSPORT transport
// Return: void
struct BTA_dm_acl_down {
  std::function<void(const RawAddress bd_addr, tBT_TRANSPORT transport)> body{
      [](const RawAddress bd_addr, tBT_TRANSPORT transport) {}};
  void operator()(const RawAddress bd_addr, tBT_TRANSPORT transport) {
    body(bd_addr, transport);
  };
};
extern struct BTA_dm_acl_down BTA_dm_acl_down;

// Name: BTA_dm_acl_up
// Params: const RawAddress bd_addr, tBT_TRANSPORT transport
// Return: void
struct BTA_dm_acl_up {
  std::function<void(const RawAddress bd_addr, tBT_TRANSPORT transport,
                     uint16_t acl_handle)>
      body{[](const RawAddress bd_addr, tBT_TRANSPORT transport,
              uint16_t acl_handle) {}};
  void operator()(const RawAddress bd_addr, tBT_TRANSPORT transport,
                  uint16_t acl_handle) {
    body(bd_addr, transport, acl_handle);
  };
};
extern struct BTA_dm_acl_up BTA_dm_acl_up;

// Name: BTA_dm_acl_up_failed
// Params: const RawAddress bd_addr, tBT_TRANSPORT transport, tHCI_STATUS
// hci_status Return: void
struct BTA_dm_acl_up_failed {
  std::function<void(const RawAddress bd_addr, tBT_TRANSPORT transport,
                     tHCI_STATUS hci_status)>
      body{[](const RawAddress bd_addr, tBT_TRANSPORT transport,
              tHCI_STATUS hci_status) {}};
  void operator()(const RawAddress bd_addr, tBT_TRANSPORT transport,
                  tHCI_STATUS hci_status) {
    body(bd_addr, transport, hci_status);
  };
};
extern struct BTA_dm_acl_up_failed BTA_dm_acl_up_failed;

// Name: BTA_dm_notify_remote_features_complete
// Params: const RawAddress bd_addr
// Return: void
struct BTA_dm_notify_remote_features_complete {
  std::function<void(const RawAddress bd_addr)> body{
      [](const RawAddress bd_addr) {}};
  void operator()(const RawAddress bd_addr) { body(bd_addr); };
};
extern struct BTA_dm_notify_remote_features_complete
    BTA_dm_notify_remote_features_complete;

// Name: BTA_dm_on_hw_off
// Params:
// Return: void
struct BTA_dm_on_hw_off {
  std::function<void()> body{[]() {}};
  void operator()() { body(); };
};
extern struct BTA_dm_on_hw_off BTA_dm_on_hw_off;

// Name: BTA_dm_on_hw_on
// Params:
// Return: void
struct BTA_dm_on_hw_on {
  std::function<void()> body{[]() {}};
  void operator()() { body(); };
};
extern struct BTA_dm_on_hw_on BTA_dm_on_hw_on;

// Name: BTA_dm_report_role_change
// Params: const RawAddress bd_addr, tHCI_ROLE new_role, tHCI_STATUS hci_status
// Return: void
struct BTA_dm_report_role_change {
  std::function<void(const RawAddress bd_addr, tHCI_ROLE new_role,
                     tHCI_STATUS hci_status)>
      body{[](const RawAddress bd_addr, tHCI_ROLE new_role,
              tHCI_STATUS hci_status) {}};
  void operator()(const RawAddress bd_addr, tHCI_ROLE new_role,
                  tHCI_STATUS hci_status) {
    body(bd_addr, new_role, hci_status);
  };
};
extern struct BTA_dm_report_role_change BTA_dm_report_role_change;

// Name: bta_dm_acl_up
// Params: const RawAddress& bd_addr, tBT_TRANSPORT transport
// Return: void
struct bta_dm_acl_up {
  std::function<void(const RawAddress& bd_addr, tBT_TRANSPORT transport,
                     uint16_t acl_handle)>
      body{[](const RawAddress& bd_addr, tBT_TRANSPORT transport,
              uint16_t acl_handle) {}};
  void operator()(const RawAddress& bd_addr, tBT_TRANSPORT transport,
                  uint16_t acl_handle) {
    body(bd_addr, transport, acl_handle);
  };
};
extern struct bta_dm_acl_up bta_dm_acl_up;

// Name: bta_dm_add_ble_device
// Params: const RawAddress& bd_addr, tBLE_ADDR_TYPE addr_type, tBT_DEVICE_TYPE
// dev_type Return: void
struct bta_dm_add_ble_device {
  std::function<void(const RawAddress& bd_addr, tBLE_ADDR_TYPE addr_type,
                     tBT_DEVICE_TYPE dev_type)>
      body{[](const RawAddress& bd_addr, tBLE_ADDR_TYPE addr_type,
              tBT_DEVICE_TYPE dev_type) {}};
  void operator()(const RawAddress& bd_addr, tBLE_ADDR_TYPE addr_type,
                  tBT_DEVICE_TYPE dev_type) {
    body(bd_addr, addr_type, dev_type);
  };
};
extern struct bta_dm_add_ble_device bta_dm_add_ble_device;

// Name: bta_dm_add_blekey
// Params: const RawAddress& bd_addr, tBTA_LE_KEY_VALUE blekey, tBTM_LE_KEY_TYPE
// key_type Return: void
struct bta_dm_add_blekey {
  std::function<void(const RawAddress& bd_addr, tBTA_LE_KEY_VALUE blekey,
                     tBTM_LE_KEY_TYPE key_type)>
      body{[](const RawAddress& bd_addr, tBTA_LE_KEY_VALUE blekey,
              tBTM_LE_KEY_TYPE key_type) {}};
  void operator()(const RawAddress& bd_addr, tBTA_LE_KEY_VALUE blekey,
                  tBTM_LE_KEY_TYPE key_type) {
    body(bd_addr, blekey, key_type);
  };
};
extern struct bta_dm_add_blekey bta_dm_add_blekey;

// Name: bta_dm_add_device
// Params: std::unique_ptr<tBTA_DM_API_ADD_DEVICE> msg
// Return: void
struct bta_dm_add_device {
  std::function<void(std::unique_ptr<tBTA_DM_API_ADD_DEVICE> msg)> body{
      [](std::unique_ptr<tBTA_DM_API_ADD_DEVICE> msg) {}};
  void operator()(std::unique_ptr<tBTA_DM_API_ADD_DEVICE> msg) {
    body(std::move(msg));
  };
};
extern struct bta_dm_add_device bta_dm_add_device;

// Name: bta_dm_ble_config_local_privacy
// Params: bool privacy_enable
// Return: void
struct bta_dm_ble_config_local_privacy {
  std::function<void(bool privacy_enable)> body{[](bool privacy_enable) {}};
  void operator()(bool privacy_enable) { body(privacy_enable); };
};
extern struct bta_dm_ble_config_local_privacy bta_dm_ble_config_local_privacy;

// Name: bta_dm_ble_confirm_reply
// Params: const RawAddress& bd_addr, bool accept
// Return: void
struct bta_dm_ble_confirm_reply {
  std::function<void(const RawAddress& bd_addr, bool accept)> body{
      [](const RawAddress& bd_addr, bool accept) {}};
  void operator()(const RawAddress& bd_addr, bool accept) {
    body(bd_addr, accept);
  };
};
extern struct bta_dm_ble_confirm_reply bta_dm_ble_confirm_reply;

// Name: bta_dm_ble_csis_observe
// Params: bool observe, tBTA_DM_SEARCH_CBACK* p_cback
// Return: void
struct bta_dm_ble_csis_observe {
  std::function<void(bool observe, tBTA_DM_SEARCH_CBACK* p_cback)> body{
      [](bool observe, tBTA_DM_SEARCH_CBACK* p_cback) {}};
  void operator()(bool observe, tBTA_DM_SEARCH_CBACK* p_cback) {
    body(observe, p_cback);
  };
};
extern struct bta_dm_ble_csis_observe bta_dm_ble_csis_observe;

// Name: bta_dm_ble_get_energy_info
// Params: tBTA_BLE_ENERGY_INFO_CBACK* p_energy_info_cback
// Return: void
struct bta_dm_ble_get_energy_info {
  std::function<void(tBTA_BLE_ENERGY_INFO_CBACK* p_energy_info_cback)> body{
      [](tBTA_BLE_ENERGY_INFO_CBACK* p_energy_info_cback) {}};
  void operator()(tBTA_BLE_ENERGY_INFO_CBACK* p_energy_info_cback) {
    body(p_energy_info_cback);
  };
};
extern struct bta_dm_ble_get_energy_info bta_dm_ble_get_energy_info;

// Name: bta_dm_ble_observe
// Params: bool start, uint8_t duration, tBTA_DM_SEARCH_CBACK* p_cback
// Return: void
struct bta_dm_ble_observe {
  std::function<void(bool start, uint8_t duration,
                     tBTA_DM_SEARCH_CBACK* p_cback)>
      body{[](bool start, uint8_t duration, tBTA_DM_SEARCH_CBACK* p_cback) {}};
  void operator()(bool start, uint8_t duration, tBTA_DM_SEARCH_CBACK* p_cback) {
    body(start, duration, p_cback);
  };
};
extern struct bta_dm_ble_observe bta_dm_ble_observe;

// Name: bta_dm_clear_event_filter
// Params: None
// Return: void
struct bta_dm_clear_event_filter {
  std::function<void()> body{[]() {}};
  void operator()() { body(); };
};
extern struct bta_dm_clear_event_filter bta_dm_clear_event_filter;

// Name: bta_dm_ble_reset_id
// Params: None
// Return: void
struct bta_dm_ble_reset_id {
  std::function<void()> body{[]() {}};
  void operator()() { body(); };
};
extern struct bta_dm_ble_reset_id bta_dm_ble_reset_id;

// Name: bta_dm_ble_passkey_reply
// Params: const RawAddress& bd_addr, bool accept, uint32_t passkey
// Return: void
struct bta_dm_ble_passkey_reply {
  std::function<void(const RawAddress& bd_addr, bool accept, uint32_t passkey)>
      body{[](const RawAddress& bd_addr, bool accept, uint32_t passkey) {}};
  void operator()(const RawAddress& bd_addr, bool accept, uint32_t passkey) {
    body(bd_addr, accept, passkey);
  };
};
extern struct bta_dm_ble_passkey_reply bta_dm_ble_passkey_reply;

// Name: bta_dm_ble_scan
// Params: bool start, uint8_t duration_sec
// Return: void
struct bta_dm_ble_scan {
  std::function<void(bool start, uint8_t duration_sec)> body{
      [](bool start, uint8_t duration_sec) {}};
  void operator()(bool start, uint8_t duration_sec) {
    body(start, duration_sec);
  };
};
extern struct bta_dm_ble_scan bta_dm_ble_scan;

// Name: bta_dm_ble_set_conn_params
// Params: const RawAddress& bd_addr, uint16_t conn_int_min, uint16_t
// conn_int_max, uint16_t peripheral_latency, uint16_t supervision_tout Return:
// void
struct bta_dm_ble_set_conn_params {
  std::function<void(const RawAddress& bd_addr, uint16_t conn_int_min,
                     uint16_t conn_int_max, uint16_t peripheral_latency,
                     uint16_t supervision_tout)>
      body{[](const RawAddress& bd_addr, uint16_t conn_int_min,
              uint16_t conn_int_max, uint16_t peripheral_latency,
              uint16_t supervision_tout) {}};
  void operator()(const RawAddress& bd_addr, uint16_t conn_int_min,
                  uint16_t conn_int_max, uint16_t peripheral_latency,
                  uint16_t supervision_tout) {
    body(bd_addr, conn_int_min, conn_int_max, peripheral_latency,
         supervision_tout);
  };
};
extern struct bta_dm_ble_set_conn_params bta_dm_ble_set_conn_params;

// Name: bta_dm_ble_set_data_length
// Params: const RawAddress& bd_addr
// Return: void
struct bta_dm_ble_set_data_length {
  std::function<void(const RawAddress& bd_addr)> body{
      [](const RawAddress& bd_addr) {}};
  void operator()(const RawAddress& bd_addr) { body(bd_addr); };
};
extern struct bta_dm_ble_set_data_length bta_dm_ble_set_data_length;

// Name: bta_dm_ble_update_conn_params
// Params: const RawAddress& bd_addr, uint16_t min_int, uint16_t max_int,
// uint16_t latency, uint16_t timeout, uint16_t min_ce_len, uint16_t max_ce_len
// Return: void
struct bta_dm_ble_update_conn_params {
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
extern struct bta_dm_ble_update_conn_params bta_dm_ble_update_conn_params;

// Name: bta_dm_bond
// Params: const RawAddress& bd_addr, tBLE_ADDR_TYPE addr_type, tBT_TRANSPORT
// transport, tBT_DEVICE_TYPE device_type Return: void
struct bta_dm_bond {
  std::function<void(const RawAddress& bd_addr, tBLE_ADDR_TYPE addr_type,
                     tBT_TRANSPORT transport, tBT_DEVICE_TYPE device_type)>
      body{[](const RawAddress& bd_addr, tBLE_ADDR_TYPE addr_type,
              tBT_TRANSPORT transport, tBT_DEVICE_TYPE device_type) {}};
  void operator()(const RawAddress& bd_addr, tBLE_ADDR_TYPE addr_type,
                  tBT_TRANSPORT transport, tBT_DEVICE_TYPE device_type) {
    body(bd_addr, addr_type, transport, device_type);
  };
};
extern struct bta_dm_bond bta_dm_bond;

// Name: bta_dm_bond_cancel
// Params: const RawAddress& bd_addr
// Return: void
struct bta_dm_bond_cancel {
  std::function<void(const RawAddress& bd_addr)> body{
      [](const RawAddress& bd_addr) {}};
  void operator()(const RawAddress& bd_addr) { body(bd_addr); };
};
extern struct bta_dm_bond_cancel bta_dm_bond_cancel;

// Name: bta_dm_check_if_only_hd_connected
// Params: const RawAddress& peer_addr
// Return: bool
struct bta_dm_check_if_only_hd_connected {
  bool return_value{false};
  std::function<bool(const RawAddress& peer_addr)> body{
      [this](const RawAddress& peer_addr) { return return_value; }};
  bool operator()(const RawAddress& peer_addr) { return body(peer_addr); };
};
extern struct bta_dm_check_if_only_hd_connected
    bta_dm_check_if_only_hd_connected;

// Name: bta_dm_ci_rmt_oob_act
// Params: std::unique_ptr<tBTA_DM_CI_RMT_OOB> msg
// Return: void
struct bta_dm_ci_rmt_oob_act {
  std::function<void(std::unique_ptr<tBTA_DM_CI_RMT_OOB> msg)> body{
      [](std::unique_ptr<tBTA_DM_CI_RMT_OOB> msg) {}};
  void operator()(std::unique_ptr<tBTA_DM_CI_RMT_OOB> msg) {
    body(std::move(msg));
  };
};
extern struct bta_dm_ci_rmt_oob_act bta_dm_ci_rmt_oob_act;

// Name: bta_dm_close_acl
// Params: const RawAddress& bd_addr, bool remove_dev, tBT_TRANSPORT transport
// Return: void
struct bta_dm_close_acl {
  std::function<void(const RawAddress& bd_addr, bool remove_dev,
                     tBT_TRANSPORT transport)>
      body{[](const RawAddress& bd_addr, bool remove_dev,
              tBT_TRANSPORT transport) {}};
  void operator()(const RawAddress& bd_addr, bool remove_dev,
                  tBT_TRANSPORT transport) {
    body(bd_addr, remove_dev, transport);
  };
};
extern struct bta_dm_close_acl bta_dm_close_acl;

// Name: bta_dm_confirm
// Params: const RawAddress& bd_addr, bool accept
// Return: void
struct bta_dm_confirm {
  std::function<void(const RawAddress& bd_addr, bool accept)> body{
      [](const RawAddress& bd_addr, bool accept) {}};
  void operator()(const RawAddress& bd_addr, bool accept) {
    body(bd_addr, accept);
  };
};
extern struct bta_dm_confirm bta_dm_confirm;

// Name: bta_dm_disable
// Params:
// Return: void
struct bta_dm_disable {
  std::function<void()> body{[]() {}};
  void operator()() { body(); };
};
extern struct bta_dm_disable bta_dm_disable;

// Name: bta_dm_eir_update_cust_uuid
// Params: const tBTA_CUSTOM_UUID& curr, bool adding
// Return: void
struct bta_dm_eir_update_cust_uuid {
  std::function<void(const tBTA_CUSTOM_UUID& curr, bool adding)> body{
      [](const tBTA_CUSTOM_UUID& curr, bool adding) {}};
  void operator()(const tBTA_CUSTOM_UUID& curr, bool adding) {
    body(curr, adding);
  };
};
extern struct bta_dm_eir_update_cust_uuid bta_dm_eir_update_cust_uuid;

// Name: bta_dm_eir_update_uuid
// Params: uint16_t uuid16, bool adding
// Return: void
struct bta_dm_eir_update_uuid {
  std::function<void(uint16_t uuid16, bool adding)> body{
      [](uint16_t uuid16, bool adding) {}};
  void operator()(uint16_t uuid16, bool adding) { body(uuid16, adding); };
};
extern struct bta_dm_eir_update_uuid bta_dm_eir_update_uuid;

// Name: bta_dm_enable
// Params: tBTA_DM_SEC_CBACK* p_sec_cback
// Return: void
struct bta_dm_enable {
  std::function<void(tBTA_DM_SEC_CBACK* p_sec_cback)> body{
      [](tBTA_DM_SEC_CBACK* p_sec_cback) {}};
  void operator()(tBTA_DM_SEC_CBACK* p_sec_cback) { body(p_sec_cback); };
};
extern struct bta_dm_enable bta_dm_enable;

// Name: bta_dm_encrypt_cback
// Params: const RawAddress* bd_addr, tBT_TRANSPORT transport, void* p_ref_data,
// tBTM_STATUS result Return: void
struct bta_dm_encrypt_cback {
  std::function<void(const RawAddress* bd_addr, tBT_TRANSPORT transport,
                     void* p_ref_data, tBTM_STATUS result)>
      body{[](const RawAddress* bd_addr, tBT_TRANSPORT transport,
              void* p_ref_data, tBTM_STATUS result) {}};
  void operator()(const RawAddress* bd_addr, tBT_TRANSPORT transport,
                  void* p_ref_data, tBTM_STATUS result) {
    body(bd_addr, transport, p_ref_data, result);
  };
};
extern struct bta_dm_encrypt_cback bta_dm_encrypt_cback;

// Name: bta_dm_is_search_request_queued
// Params:
// Return: bool
struct bta_dm_is_search_request_queued {
  bool return_value{false};
  std::function<bool()> body{[this]() { return return_value; }};
  bool operator()() { return body(); };
};
extern struct bta_dm_is_search_request_queued bta_dm_is_search_request_queued;

// Name: bta_dm_pin_reply
// Params: std::unique_ptr<tBTA_DM_API_PIN_REPLY> msg
// Return: void
struct bta_dm_pin_reply {
  std::function<void(std::unique_ptr<tBTA_DM_API_PIN_REPLY> msg)> body{
      [](std::unique_ptr<tBTA_DM_API_PIN_REPLY> msg) {}};
  void operator()(std::unique_ptr<tBTA_DM_API_PIN_REPLY> msg) {
    body(std::move(msg));
  };
};
extern struct bta_dm_pin_reply bta_dm_pin_reply;

// Name: bta_dm_process_remove_device
// Params: const RawAddress& bd_addr
// Return: void
struct bta_dm_process_remove_device {
  std::function<void(const RawAddress& bd_addr)> body{
      [](const RawAddress& bd_addr) {}};
  void operator()(const RawAddress& bd_addr) { body(bd_addr); };
};
extern struct bta_dm_process_remove_device bta_dm_process_remove_device;

// Name: bta_dm_remove_device
// Params: const RawAddress& bd_addr
// Return: void
struct bta_dm_remove_device {
  std::function<void(const RawAddress& bd_addr)> body{
      [](const RawAddress& bd_addr) {}};
  void operator()(const RawAddress& bd_addr) { body(bd_addr); };
};
extern struct bta_dm_remove_device bta_dm_remove_device;

// Name: bta_dm_remote_key_missing
// Params: const RawAddress bd_addr
// Return: void
struct bta_dm_remote_key_missing {
  std::function<void(const RawAddress bd_addr)> body{
      [](const RawAddress bd_addr) {}};
  void operator()(const RawAddress bd_addr) { body(bd_addr); };
};
extern struct bta_dm_remote_key_missing bta_dm_remote_key_missing;

// Name: bta_dm_rm_cback
// Params: tBTA_SYS_CONN_STATUS status, uint8_t id, uint8_t app_id, const
// RawAddress& peer_addr Return: void
struct bta_dm_rm_cback {
  std::function<void(tBTA_SYS_CONN_STATUS status, uint8_t id, uint8_t app_id,
                     const RawAddress& peer_addr)>
      body{[](tBTA_SYS_CONN_STATUS status, uint8_t id, uint8_t app_id,
              const RawAddress& peer_addr) {}};
  void operator()(tBTA_SYS_CONN_STATUS status, uint8_t id, uint8_t app_id,
                  const RawAddress& peer_addr) {
    body(status, id, app_id, peer_addr);
  };
};
extern struct bta_dm_rm_cback bta_dm_rm_cback;

// Name: bta_dm_sdp_result
// Params: tBTA_DM_MSG* p_data
// Return: void
struct bta_dm_sdp_result {
  std::function<void(tBTA_DM_MSG* p_data)> body{[](tBTA_DM_MSG* p_data) {}};
  void operator()(tBTA_DM_MSG* p_data) { body(p_data); };
};
extern struct bta_dm_sdp_result bta_dm_sdp_result;

// Name: bta_dm_search_cancel_cmpl
// Params:
// Return: void
struct bta_dm_search_cancel_cmpl {
  std::function<void()> body{[]() {}};
  void operator()() { body(); };
};
extern struct bta_dm_search_cancel_cmpl bta_dm_search_cancel_cmpl;

// Name: bta_dm_search_cancel_notify
// Params:
// Return: void
struct bta_dm_search_cancel_notify {
  std::function<void()> body{[]() {}};
  void operator()() { body(); };
};
extern struct bta_dm_search_cancel_notify bta_dm_search_cancel_notify;

// Name: bta_dm_search_result
// Params: tBTA_DM_MSG* p_data
// Return: void
struct bta_dm_search_result {
  std::function<void(tBTA_DM_MSG* p_data)> body{[](tBTA_DM_MSG* p_data) {}};
  void operator()(tBTA_DM_MSG* p_data) { body(p_data); };
};
extern struct bta_dm_search_result bta_dm_search_result;

// Name: bta_dm_set_dev_name
// Params: const std::vector<uint8_t>& name
// Return: void
struct bta_dm_set_dev_name {
  std::function<void(const std::vector<uint8_t>& name)> body{
      [](const std::vector<uint8_t>& name) {}};
  void operator()(const std::vector<uint8_t>& name) { body(name); };
};
extern struct bta_dm_set_dev_name bta_dm_set_dev_name;

// Name: bta_dm_set_encryption
// Params: const RawAddress& bd_addr, tBT_TRANSPORT transport,
// tBTA_DM_ENCRYPT_CBACK* p_callback, tBTM_BLE_SEC_ACT sec_act Return: void
struct bta_dm_set_encryption {
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
extern struct bta_dm_set_encryption bta_dm_set_encryption;

// Name: handle_remote_features_complete
// Params: const RawAddress& bd_addr
// Return: void
struct handle_remote_features_complete {
  std::function<void(const RawAddress& bd_addr)> body{
      [](const RawAddress& bd_addr) {}};
  void operator()(const RawAddress& bd_addr) { body(bd_addr); };
};
extern struct handle_remote_features_complete handle_remote_features_complete;

}  // namespace bta_dm_act
}  // namespace mock
}  // namespace test

// END mockcify generation
