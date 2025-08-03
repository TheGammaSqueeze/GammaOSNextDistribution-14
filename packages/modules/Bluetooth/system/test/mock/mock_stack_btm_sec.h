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
 *   Functions generated:66
 *
 *  mockcify.pl ver 0.6.0
 */

#include <cstdint>
#include <functional>
#include <string>

// Original included files, if any
#include "bt_dev_class.h"
#include "stack/btm/security_device_record.h"
#include "stack/include/bt_device_type.h"
#include "stack/include/btm_status.h"
#include "stack/include/hci_error_code.h"
#include "stack/include/security_client_callbacks.h"
#include "types/bt_transport.h"
#include "types/hci_role.h"
#include "types/raw_address.h"

// Original usings

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace stack_btm_sec {

// Shared state between mocked functions and tests
// Name: BTM_BothEndsSupportSecureConnections
// Params: const RawAddress& bd_addr
// Return: bool
struct BTM_BothEndsSupportSecureConnections {
  static bool return_value;
  std::function<bool(const RawAddress& bd_addr)> body{
      [](const RawAddress& /* bd_addr */) { return return_value; }};
  bool operator()(const RawAddress& bd_addr) { return body(bd_addr); };
};
extern struct BTM_BothEndsSupportSecureConnections
    BTM_BothEndsSupportSecureConnections;

// Name: BTM_CanReadDiscoverableCharacteristics
// Params: const RawAddress& bd_addr
// Return: bool
struct BTM_CanReadDiscoverableCharacteristics {
  static bool return_value;
  std::function<bool(const RawAddress& bd_addr)> body{
      [](const RawAddress& /* bd_addr */) { return return_value; }};
  bool operator()(const RawAddress& bd_addr) { return body(bd_addr); };
};
extern struct BTM_CanReadDiscoverableCharacteristics
    BTM_CanReadDiscoverableCharacteristics;

// Name: BTM_ConfirmReqReply
// Params: tBTM_STATUS res, const RawAddress& bd_addr
// Return: void
struct BTM_ConfirmReqReply {
  std::function<void(tBTM_STATUS res, const RawAddress& bd_addr)> body{
      [](tBTM_STATUS /* res */, const RawAddress& /* bd_addr */) {}};
  void operator()(tBTM_STATUS res, const RawAddress& bd_addr) {
    body(res, bd_addr);
  };
};
extern struct BTM_ConfirmReqReply BTM_ConfirmReqReply;

// Name: BTM_GetClockOffset
// Params: const RawAddress& remote_bda
// Return: uint16_t
struct BTM_GetClockOffset {
  static uint16_t return_value;
  std::function<uint16_t(const RawAddress& remote_bda)> body{
      [](const RawAddress& /* remote_bda */) { return return_value; }};
  uint16_t operator()(const RawAddress& remote_bda) {
    return body(remote_bda);
  };
};
extern struct BTM_GetClockOffset BTM_GetClockOffset;

// Name: BTM_GetPeerDeviceTypeFromFeatures
// Params: const RawAddress& bd_addr
// Return: tBT_DEVICE_TYPE
struct BTM_GetPeerDeviceTypeFromFeatures {
  static tBT_DEVICE_TYPE return_value;
  std::function<tBT_DEVICE_TYPE(const RawAddress& bd_addr)> body{
      [](const RawAddress& /* bd_addr */) { return return_value; }};
  tBT_DEVICE_TYPE operator()(const RawAddress& bd_addr) {
    return body(bd_addr);
  };
};
extern struct BTM_GetPeerDeviceTypeFromFeatures
    BTM_GetPeerDeviceTypeFromFeatures;

// Name: BTM_IsAuthenticated
// Params: const RawAddress& bd_addr, tBT_TRANSPORT transport
// Return: bool
struct BTM_IsAuthenticated {
  static bool return_value;
  std::function<bool(const RawAddress& bd_addr, tBT_TRANSPORT transport)> body{
      [](const RawAddress& /* bd_addr */, tBT_TRANSPORT /* transport */) {
        return return_value;
      }};
  bool operator()(const RawAddress& bd_addr, tBT_TRANSPORT transport) {
    return body(bd_addr, transport);
  };
};
extern struct BTM_IsAuthenticated BTM_IsAuthenticated;

// Name: BTM_IsEncrypted
// Params: const RawAddress& bd_addr, tBT_TRANSPORT transport
// Return: bool
struct BTM_IsEncrypted {
  static bool return_value;
  std::function<bool(const RawAddress& bd_addr, tBT_TRANSPORT transport)> body{
      [](const RawAddress& /* bd_addr */, tBT_TRANSPORT /* transport */) {
        return return_value;
      }};
  bool operator()(const RawAddress& bd_addr, tBT_TRANSPORT transport) {
    return body(bd_addr, transport);
  };
};
extern struct BTM_IsEncrypted BTM_IsEncrypted;

// Name: BTM_IsLinkKeyAuthed
// Params: const RawAddress& bd_addr, tBT_TRANSPORT transport
// Return: bool
struct BTM_IsLinkKeyAuthed {
  static bool return_value;
  std::function<bool(const RawAddress& bd_addr, tBT_TRANSPORT transport)> body{
      [](const RawAddress& /* bd_addr */, tBT_TRANSPORT /* transport */) {
        return return_value;
      }};
  bool operator()(const RawAddress& bd_addr, tBT_TRANSPORT transport) {
    return body(bd_addr, transport);
  };
};
extern struct BTM_IsLinkKeyAuthed BTM_IsLinkKeyAuthed;

// Name: BTM_IsLinkKeyKnown
// Params: const RawAddress& bd_addr, tBT_TRANSPORT transport
// Return: bool
struct BTM_IsLinkKeyKnown {
  static bool return_value;
  std::function<bool(const RawAddress& bd_addr, tBT_TRANSPORT transport)> body{
      [](const RawAddress& /* bd_addr */, tBT_TRANSPORT /* transport */) {
        return return_value;
      }};
  bool operator()(const RawAddress& bd_addr, tBT_TRANSPORT transport) {
    return body(bd_addr, transport);
  };
};
extern struct BTM_IsLinkKeyKnown BTM_IsLinkKeyKnown;

// Name: BTM_PINCodeReply
// Params: const RawAddress& bd_addr, tBTM_STATUS res, uint8_t pin_len, uint8_t*
// p_pin Return: void
struct BTM_PINCodeReply {
  std::function<void(const RawAddress& bd_addr, tBTM_STATUS res,
                     uint8_t pin_len, uint8_t* p_pin)>
      body{[](const RawAddress& /* bd_addr */, tBTM_STATUS /* res */,
              uint8_t /* pin_len */, uint8_t* /* p_pin */) {}};
  void operator()(const RawAddress& bd_addr, tBTM_STATUS res, uint8_t pin_len,
                  uint8_t* p_pin) {
    body(bd_addr, res, pin_len, p_pin);
  };
};
extern struct BTM_PINCodeReply BTM_PINCodeReply;

// Name: BTM_PasskeyReqReply
// Params: tBTM_STATUS res, const RawAddress& bd_addr, uint32_t passkey
// Return: void
struct BTM_PasskeyReqReply {
  std::function<void(tBTM_STATUS res, const RawAddress& bd_addr,
                     uint32_t passkey)>
      body{[](tBTM_STATUS /* res */, const RawAddress& /* bd_addr */,
              uint32_t /* passkey */) {}};
  void operator()(tBTM_STATUS res, const RawAddress& bd_addr,
                  uint32_t passkey) {
    body(res, bd_addr, passkey);
  };
};
extern struct BTM_PasskeyReqReply BTM_PasskeyReqReply;

// Name: BTM_PeerSupportsSecureConnections
// Params: const RawAddress& bd_addr
// Return: bool
struct BTM_PeerSupportsSecureConnections {
  static bool return_value;
  std::function<bool(const RawAddress& bd_addr)> body{
      [](const RawAddress& /* bd_addr */) { return return_value; }};
  bool operator()(const RawAddress& bd_addr) { return body(bd_addr); };
};
extern struct BTM_PeerSupportsSecureConnections
    BTM_PeerSupportsSecureConnections;

// Name: BTM_ReadLocalOobData
// Params: void
// Return: void
struct BTM_ReadLocalOobData {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct BTM_ReadLocalOobData BTM_ReadLocalOobData;

// Name: BTM_RemoteOobDataReply
// Params: tBTM_STATUS res, const RawAddress& bd_addr, const Octet16& c, const
// Octet16& r Return: void
struct BTM_RemoteOobDataReply {
  std::function<void(tBTM_STATUS res, const RawAddress& bd_addr,
                     const Octet16& c, const Octet16& r)>
      body{[](tBTM_STATUS /* res */, const RawAddress& /* bd_addr */,
              const Octet16& /* c */, const Octet16& /* r */) {}};
  void operator()(tBTM_STATUS res, const RawAddress& bd_addr, const Octet16& c,
                  const Octet16& r) {
    body(res, bd_addr, c, r);
  };
};
extern struct BTM_RemoteOobDataReply BTM_RemoteOobDataReply;

// Name: BTM_SecAddRmtNameNotifyCallback
// Params: tBTM_RMT_NAME_CALLBACK* p_callback
// Return: bool
struct BTM_SecAddRmtNameNotifyCallback {
  static bool return_value;
  std::function<bool(tBTM_RMT_NAME_CALLBACK* p_callback)> body{
      [](tBTM_RMT_NAME_CALLBACK* /* p_callback */) { return return_value; }};
  bool operator()(tBTM_RMT_NAME_CALLBACK* p_callback) {
    return body(p_callback);
  };
};
extern struct BTM_SecAddRmtNameNotifyCallback BTM_SecAddRmtNameNotifyCallback;

// Name: BTM_SecBond
// Params: const RawAddress& bd_addr, tBLE_ADDR_TYPE addr_type, tBT_TRANSPORT
// transport, tBT_DEVICE_TYPE device_type, uint8_t pin_len, uint8_t* p_pin
// Return: tBTM_STATUS
struct BTM_SecBond {
  static tBTM_STATUS return_value;
  std::function<tBTM_STATUS(const RawAddress& bd_addr, tBLE_ADDR_TYPE addr_type,
                            tBT_TRANSPORT transport,
                            tBT_DEVICE_TYPE device_type)>
      body{[](const RawAddress& /* bd_addr */, tBLE_ADDR_TYPE /* addr_type */,
              tBT_TRANSPORT /* transport */,
              tBT_DEVICE_TYPE /* device_type */) { return return_value; }};
  tBTM_STATUS operator()(const RawAddress& bd_addr, tBLE_ADDR_TYPE addr_type,
                         tBT_TRANSPORT transport, tBT_DEVICE_TYPE device_type) {
    return body(bd_addr, addr_type, transport, device_type);
  };
};
extern struct BTM_SecBond BTM_SecBond;

// Name: BTM_SecBondCancel
// Params: const RawAddress& bd_addr
// Return: tBTM_STATUS
struct BTM_SecBondCancel {
  static tBTM_STATUS return_value;
  std::function<tBTM_STATUS(const RawAddress& bd_addr)> body{
      [](const RawAddress& /* bd_addr */) { return return_value; }};
  tBTM_STATUS operator()(const RawAddress& bd_addr) { return body(bd_addr); };
};
extern struct BTM_SecBondCancel BTM_SecBondCancel;

// Name: BTM_SecClrService
// Params: uint8_t service_id
// Return: uint8_t
struct BTM_SecClrService {
  static uint8_t return_value;
  std::function<uint8_t(uint8_t service_id)> body{
      [](uint8_t /* service_id */) { return return_value; }};
  uint8_t operator()(uint8_t service_id) { return body(service_id); };
};
extern struct BTM_SecClrService BTM_SecClrService;

// Name: BTM_SecClrServiceByPsm
// Params: uint16_t psm
// Return: uint8_t
struct BTM_SecClrServiceByPsm {
  static uint8_t return_value;
  std::function<uint8_t(uint16_t psm)> body{
      [](uint16_t /* psm */) { return return_value; }};
  uint8_t operator()(uint16_t psm) { return body(psm); };
};
extern struct BTM_SecClrServiceByPsm BTM_SecClrServiceByPsm;

// Name: BTM_SecDeleteRmtNameNotifyCallback
// Params: tBTM_RMT_NAME_CALLBACK* p_callback
// Return: bool
struct BTM_SecDeleteRmtNameNotifyCallback {
  static bool return_value;
  std::function<bool(tBTM_RMT_NAME_CALLBACK* p_callback)> body{
      [](tBTM_RMT_NAME_CALLBACK* /* p_callback */) { return return_value; }};
  bool operator()(tBTM_RMT_NAME_CALLBACK* p_callback) {
    return body(p_callback);
  };
};
extern struct BTM_SecDeleteRmtNameNotifyCallback
    BTM_SecDeleteRmtNameNotifyCallback;

// Name: BTM_SecGetDeviceLinkKeyType
// Params: const RawAddress& bd_addr
// Return: tBTM_LINK_KEY_TYPE
struct BTM_SecGetDeviceLinkKeyType {
  static tBTM_LINK_KEY_TYPE return_value;
  std::function<tBTM_LINK_KEY_TYPE(const RawAddress& bd_addr)> body{
      [](const RawAddress& /* bd_addr */) { return return_value; }};
  tBTM_LINK_KEY_TYPE operator()(const RawAddress& bd_addr) {
    return body(bd_addr);
  };
};
extern struct BTM_SecGetDeviceLinkKeyType BTM_SecGetDeviceLinkKeyType;

// Name: BTM_SecIsSecurityPending
// Params: const RawAddress& bd_addr
// Return: bool
struct BTM_SecIsSecurityPending {
  static bool return_value;
  std::function<bool(const RawAddress& bd_addr)> body{
      [](const RawAddress& /* bd_addr */) { return return_value; }};
  bool operator()(const RawAddress& bd_addr) { return body(bd_addr); };
};
extern struct BTM_SecIsSecurityPending BTM_SecIsSecurityPending;

// Name: BTM_SecRegister
// Params: const tBTM_APPL_INFO* p_cb_info
// Return: bool
struct BTM_SecRegister {
  static bool return_value;
  std::function<bool(const tBTM_APPL_INFO* p_cb_info)> body{
      [](const tBTM_APPL_INFO* /* p_cb_info */) { return return_value; }};
  bool operator()(const tBTM_APPL_INFO* p_cb_info) { return body(p_cb_info); };
};
extern struct BTM_SecRegister BTM_SecRegister;

// Name: BTM_SetEncryption
// Params: const RawAddress& bd_addr, tBT_TRANSPORT transport,
// tBTM_SEC_CALLBACK* p_callback, void* p_ref_data, tBTM_BLE_SEC_ACT sec_act
// Return: tBTM_STATUS
struct BTM_SetEncryption {
  static tBTM_STATUS return_value;
  std::function<tBTM_STATUS(const RawAddress& bd_addr, tBT_TRANSPORT transport,
                            tBTM_SEC_CALLBACK* p_callback, void* p_ref_data,
                            tBTM_BLE_SEC_ACT sec_act)>
      body{[](const RawAddress& /* bd_addr */, tBT_TRANSPORT /* transport */,
              tBTM_SEC_CALLBACK* /* p_callback */, void* /* p_ref_data */,
              tBTM_BLE_SEC_ACT /* sec_act */) { return return_value; }};
  tBTM_STATUS operator()(const RawAddress& bd_addr, tBT_TRANSPORT transport,
                         tBTM_SEC_CALLBACK* p_callback, void* p_ref_data,
                         tBTM_BLE_SEC_ACT sec_act) {
    return body(bd_addr, transport, p_callback, p_ref_data, sec_act);
  };
};
extern struct BTM_SetEncryption BTM_SetEncryption;

// Name: BTM_SetPinType
// Params: uint8_t pin_type, PIN_CODE pin_code, uint8_t pin_code_len
// Return: void
struct BTM_SetPinType {
  std::function<void(uint8_t pin_type, PIN_CODE pin_code, uint8_t pin_code_len)>
      body{[](uint8_t /* pin_type */, PIN_CODE /* pin_code */,
              uint8_t /* pin_code_len */) {}};
  void operator()(uint8_t pin_type, PIN_CODE pin_code, uint8_t pin_code_len) {
    body(pin_type, pin_code, pin_code_len);
  };
};
extern struct BTM_SetPinType BTM_SetPinType;

// Name: BTM_SetSecurityLevel
// Params: bool is_originator, const char* p_name, uint8_t service_id, uint16_t
// sec_level, uint16_t psm, uint32_t mx_proto_id, uint32_t mx_chan_id Return:
// bool
struct BTM_SetSecurityLevel {
  static bool return_value;
  std::function<bool(bool is_originator, const char* p_name, uint8_t service_id,
                     uint16_t sec_level, uint16_t psm, uint32_t mx_proto_id,
                     uint32_t mx_chan_id)>
      body{[](bool /* is_originator */, const char* /* p_name */,
              uint8_t /* service_id */, uint16_t /* sec_level */,
              uint16_t /* psm */, uint32_t /* mx_proto_id */,
              uint32_t /* mx_chan_id */) { return return_value; }};
  bool operator()(bool is_originator, const char* p_name, uint8_t service_id,
                  uint16_t sec_level, uint16_t psm, uint32_t mx_proto_id,
                  uint32_t mx_chan_id) {
    return body(is_originator, p_name, service_id, sec_level, psm, mx_proto_id,
                mx_chan_id);
  };
};
extern struct BTM_SetSecurityLevel BTM_SetSecurityLevel;

// Name: BTM_update_version_info
// Params: const RawAddress& bd_addr, const remote_version_info&
// remote_version_info Return: void
struct BTM_update_version_info {
  std::function<void(const RawAddress& bd_addr,
                     const remote_version_info& remote_version_info)>
      body{[](const RawAddress& /* bd_addr */,
              const remote_version_info& /* remote_version_info */) {}};
  void operator()(const RawAddress& bd_addr,
                  const remote_version_info& remote_version_info) {
    body(bd_addr, remote_version_info);
  };
};
extern struct BTM_update_version_info BTM_update_version_info;

// Name: NotifyBondingCanceled
// Params: tBTM_STATUS btm_status
// Return: void
struct NotifyBondingCanceled {
  std::function<void(tBTM_STATUS btm_status)> body{
      [](tBTM_STATUS /* btm_status */) {}};
  void operator()(tBTM_STATUS btm_status) { body(btm_status); };
};
extern struct NotifyBondingCanceled NotifyBondingCanceled;

// Name: btm_create_conn_cancel_complete
// Params: uint8_t status, RawAddress bd_addr
// Return: void
struct btm_create_conn_cancel_complete {
  std::function<void(uint8_t status, const RawAddress bd_addr)> body{
      [](uint8_t /* status */, const RawAddress /* bd_addr */) {}};
  void operator()(uint8_t status, const RawAddress bd_addr) {
    body(status, bd_addr);
  };
};
extern struct btm_create_conn_cancel_complete btm_create_conn_cancel_complete;

// Name: btm_get_dev_class
// Params: const RawAddress& bda
// Return: DEV_CLASS
struct btm_get_dev_class {
  static const DEV_CLASS return_value;
  std::function<const DEV_CLASS(const RawAddress& bda)> body{
      [](const RawAddress& /* bda */) { return return_value; }};
  const DEV_CLASS operator()(const RawAddress& bda) { return body(bda); };
};
extern struct btm_get_dev_class btm_get_dev_class;

// Name: btm_io_capabilities_req
// Params: RawAddress p
// Return: void
struct btm_io_capabilities_req {
  std::function<void(RawAddress p)> body{[](RawAddress /* p */) {}};
  void operator()(RawAddress p) { body(p); };
};
extern struct btm_io_capabilities_req btm_io_capabilities_req;

// Name: btm_io_capabilities_rsp
// Params: tBTM_SP_IO_RSP evt_data
// Return: void
struct btm_io_capabilities_rsp {
  std::function<void(const tBTM_SP_IO_RSP evt_data)> body{
      [](const tBTM_SP_IO_RSP /* evt_data */) {}};
  void operator()(const tBTM_SP_IO_RSP evt_data) { body(evt_data); };
};
extern struct btm_io_capabilities_rsp btm_io_capabilities_rsp;

// Name: btm_proc_sp_req_evt
// Params: tBTM_SP_EVT event, const uint8_t* p
// Return: void
struct btm_proc_sp_req_evt {
  std::function<void(tBTM_SP_EVT event, const RawAddress bda, uint32_t value)>
      body{[](tBTM_SP_EVT /* event */, const RawAddress /* bda */,
              uint32_t /* value */) {}};
  void operator()(tBTM_SP_EVT event, const RawAddress bda, uint32_t value) {
    body(event, bda, value);
  };
};
extern struct btm_proc_sp_req_evt btm_proc_sp_req_evt;

// Name: btm_read_local_oob_complete
// Params:
// tBTM_SP_LOC_OOB evt_data;
// uint8_t status;
// Return: void
struct btm_read_local_oob_complete {
  std::function<void(const tBTM_SP_LOC_OOB evt_data)> body{
      [](const tBTM_SP_LOC_OOB /* evt_data */) {}};
  void operator()(const tBTM_SP_LOC_OOB evt_data) { body(evt_data); };
};
extern struct btm_read_local_oob_complete btm_read_local_oob_complete;

// Name: btm_rem_oob_req
// Params: RawAddress bda
// Return: void
struct btm_rem_oob_req {
  std::function<void(const RawAddress bda)> body{
      [](const RawAddress /* bda */) {}};
  void operator()(const RawAddress bda) { body(bda); };
};
extern struct btm_rem_oob_req btm_rem_oob_req;

// Name: btm_sec_abort_access_req
// Params: const RawAddress& bd_addr
// Return: void
struct btm_sec_abort_access_req {
  std::function<void(const RawAddress& bd_addr)> body{
      [](const RawAddress& /* bd_addr */) {}};
  void operator()(const RawAddress& bd_addr) { body(bd_addr); };
};
extern struct btm_sec_abort_access_req btm_sec_abort_access_req;

// Name: btm_sec_auth_complete
// Params: uint16_t handle, tHCI_STATUS status
// Return: void
struct btm_sec_auth_complete {
  std::function<void(uint16_t handle, tHCI_STATUS status)> body{
      [](uint16_t /* handle */, tHCI_STATUS /* status */) {}};
  void operator()(uint16_t handle, tHCI_STATUS status) {
    body(handle, status);
  };
};
extern struct btm_sec_auth_complete btm_sec_auth_complete;

// Name: btm_sec_bond_by_transport
// Params: const RawAddress& bd_addr, tBLE_ADDR_TYPE addr_type, tBT_TRANSPORT
// transport, uint8_t pin_len, uint8_t* p_pin Return: tBTM_STATUS
struct btm_sec_bond_by_transport {
  static tBTM_STATUS return_value;
  std::function<tBTM_STATUS(const RawAddress& bd_addr, tBLE_ADDR_TYPE addr_type,
                            tBT_TRANSPORT transport)>
      body{[](const RawAddress& /* bd_addr */, tBLE_ADDR_TYPE /* addr_type */,
              tBT_TRANSPORT /* transport */) { return return_value; }};
  tBTM_STATUS operator()(const RawAddress& bd_addr, tBLE_ADDR_TYPE addr_type,
                         tBT_TRANSPORT transport) {
    return body(bd_addr, addr_type, transport);
  };
};
extern struct btm_sec_bond_by_transport btm_sec_bond_by_transport;

// Name: btm_sec_check_pending_reqs
// Params: void
// Return: void
struct btm_sec_check_pending_reqs {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct btm_sec_check_pending_reqs btm_sec_check_pending_reqs;

// Name: btm_sec_clear_ble_keys
// Params: tBTM_SEC_DEV_REC* p_dev_rec
// Return: void
struct btm_sec_clear_ble_keys {
  std::function<void(tBTM_SEC_DEV_REC* p_dev_rec)> body{
      [](tBTM_SEC_DEV_REC* /* p_dev_rec */) {}};
  void operator()(tBTM_SEC_DEV_REC* p_dev_rec) { body(p_dev_rec); };
};
extern struct btm_sec_clear_ble_keys btm_sec_clear_ble_keys;

// Name: btm_sec_conn_req
// Params: const RawAddress& bda, const DEV_CLASS dc
// Return: void
struct btm_sec_conn_req {
  std::function<void(const RawAddress& bda, const DEV_CLASS dc)> body{
      [](const RawAddress& /* bda */, const DEV_CLASS /* dc */) {}};
  void operator()(const RawAddress& bda, const DEV_CLASS dc) { body(bda, dc); };
};
extern struct btm_sec_conn_req btm_sec_conn_req;

// Name: btm_sec_connected
// Params: const RawAddress& bda, uint16_t handle, tHCI_STATUS status, uint8_t
// enc_mode, tHCI_ROLE assigned_role Return: void
struct btm_sec_connected {
  std::function<void(const RawAddress& bda, uint16_t handle, tHCI_STATUS status,
                     uint8_t enc_mode, tHCI_ROLE assigned_role)>
      body{[](const RawAddress& /* bda */, uint16_t /* handle */,
              tHCI_STATUS /* status */, uint8_t /* enc_mode */,
              tHCI_ROLE /* assigned_role */) {}};
  void operator()(const RawAddress& bda, uint16_t handle, tHCI_STATUS status,
                  uint8_t enc_mode, tHCI_ROLE assigned_role) {
    body(bda, handle, status, enc_mode, assigned_role);
  };
};
extern struct btm_sec_connected btm_sec_connected;

// Name: btm_sec_cr_loc_oob_data_cback_event
// Params: const RawAddress& address, tSMP_LOC_OOB_DATA loc_oob_data
// Return: void
struct btm_sec_cr_loc_oob_data_cback_event {
  std::function<void(const RawAddress& address, tSMP_LOC_OOB_DATA loc_oob_data)>
      body{[](const RawAddress& /* address */,
              tSMP_LOC_OOB_DATA /* loc_oob_data */) {}};
  void operator()(const RawAddress& address, tSMP_LOC_OOB_DATA loc_oob_data) {
    body(address, loc_oob_data);
  };
};
extern struct btm_sec_cr_loc_oob_data_cback_event
    btm_sec_cr_loc_oob_data_cback_event;

// Name: btm_sec_dev_rec_cback_event
// Params: tBTM_SEC_DEV_REC* p_dev_rec, tBTM_STATUS btm_status, bool
// is_le_transport Return: void
struct btm_sec_dev_rec_cback_event {
  std::function<void(tBTM_SEC_DEV_REC* p_dev_rec, tBTM_STATUS btm_status,
                     bool is_le_transport)>
      body{[](tBTM_SEC_DEV_REC* /* p_dev_rec */, tBTM_STATUS /* btm_status */,
              bool /* is_le_transport */) {}};
  void operator()(tBTM_SEC_DEV_REC* p_dev_rec, tBTM_STATUS btm_status,
                  bool is_le_transport) {
    body(p_dev_rec, btm_status, is_le_transport);
  };
};
extern struct btm_sec_dev_rec_cback_event btm_sec_dev_rec_cback_event;

// Name: btm_sec_dev_reset
// Params: void
// Return: void
struct btm_sec_dev_reset {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct btm_sec_dev_reset btm_sec_dev_reset;

// Name: btm_sec_disconnect
// Params: uint16_t handle, tHCI_STATUS reason, std::string comment
// Return: tBTM_STATUS
struct btm_sec_disconnect {
  static tBTM_STATUS return_value;
  std::function<tBTM_STATUS(uint16_t handle, tHCI_STATUS reason,
                            std::string comment)>
      body{[](uint16_t /* handle */, tHCI_STATUS /* reason */,
              std::string /* comment */) { return return_value; }};
  tBTM_STATUS operator()(uint16_t handle, tHCI_STATUS reason,
                         std::string comment) {
    return body(handle, reason, comment);
  };
};
extern struct btm_sec_disconnect btm_sec_disconnect;

// Name: btm_sec_disconnected
// Params: uint16_t handle, tHCI_REASON reason, std::string comment
// Return: void
struct btm_sec_disconnected {
  std::function<void(uint16_t handle, tHCI_REASON reason, std::string comment)>
      body{[](uint16_t /* handle */, tHCI_REASON /* reason */,
              std::string /* comment */) {}};
  void operator()(uint16_t handle, tHCI_REASON reason, std::string comment) {
    body(handle, reason, comment);
  };
};
extern struct btm_sec_disconnected btm_sec_disconnected;

// Name: btm_sec_encrypt_change
// Params: uint16_t handle, tHCI_STATUS status, uint8_t encr_enable
// Return: void
struct btm_sec_encrypt_change {
  std::function<void(uint16_t handle, tHCI_STATUS status, uint8_t encr_enable)>
      body{[](uint16_t /* handle */, tHCI_STATUS /* status */,
              uint8_t /* encr_enable */) {}};
  void operator()(uint16_t handle, tHCI_STATUS status, uint8_t encr_enable) {
    body(handle, status, encr_enable);
  };
};
extern struct btm_sec_encrypt_change btm_sec_encrypt_change;

// Name: btm_sec_encryption_change_evt
// Params: uint16_t handle, tHCI_STATUS status, uint8_t encr_enable
// Return: void
struct btm_sec_encryption_change_evt {
  std::function<void(uint16_t handle, tHCI_STATUS status, uint8_t encr_enable)>
      body{[](uint16_t /* handle */, tHCI_STATUS /* status */,
              uint8_t /* encr_enable */) {}};
  void operator()(uint16_t handle, tHCI_STATUS status, uint8_t encr_enable) {
    body(handle, status, encr_enable);
  };
};
extern struct btm_sec_encryption_change_evt btm_sec_encryption_change_evt;

// Name: btm_sec_is_a_bonded_dev
// Params: const RawAddress& bda
// Return: bool
struct btm_sec_is_a_bonded_dev {
  static bool return_value;
  std::function<bool(const RawAddress& bda)> body{
      [](const RawAddress& /* bda */) { return return_value; }};
  bool operator()(const RawAddress& bda) { return body(bda); };
};
extern struct btm_sec_is_a_bonded_dev btm_sec_is_a_bonded_dev;

// Name: btm_sec_l2cap_access_req
// Params: const RawAddress& bd_addr, uint16_t psm, bool is_originator,
// tBTM_SEC_CALLBACK* p_callback, void* p_ref_data Return: tBTM_STATUS
struct btm_sec_l2cap_access_req {
  static tBTM_STATUS return_value;
  std::function<tBTM_STATUS(const RawAddress& bd_addr, uint16_t psm,
                            bool is_originator, tBTM_SEC_CALLBACK* p_callback,
                            void* p_ref_data)>
      body{[](const RawAddress& /* bd_addr */, uint16_t /* psm */,
              bool /* is_originator */, tBTM_SEC_CALLBACK* /* p_callback */,
              void* /* p_ref_data */) { return return_value; }};
  tBTM_STATUS operator()(const RawAddress& bd_addr, uint16_t psm,
                         bool is_originator, tBTM_SEC_CALLBACK* p_callback,
                         void* p_ref_data) {
    return body(bd_addr, psm, is_originator, p_callback, p_ref_data);
  };
};
extern struct btm_sec_l2cap_access_req btm_sec_l2cap_access_req;

// Name: btm_sec_l2cap_access_req_by_requirement
// Params: const RawAddress& bd_addr, uint16_t security_required, bool
// is_originator, tBTM_SEC_CALLBACK* p_callback, void* p_ref_data Return:
// tBTM_STATUS
struct btm_sec_l2cap_access_req_by_requirement {
  static tBTM_STATUS return_value;
  std::function<tBTM_STATUS(const RawAddress& bd_addr,
                            uint16_t security_required, bool is_originator,
                            tBTM_SEC_CALLBACK* p_callback, void* p_ref_data)>
      body{[](const RawAddress& /* bd_addr */, uint16_t /* security_required */,
              bool /* is_originator */, tBTM_SEC_CALLBACK* /* p_callback */,
              void* /* p_ref_data */) { return return_value; }};
  tBTM_STATUS operator()(const RawAddress& bd_addr, uint16_t security_required,
                         bool is_originator, tBTM_SEC_CALLBACK* p_callback,
                         void* p_ref_data) {
    return body(bd_addr, security_required, is_originator, p_callback,
                p_ref_data);
  };
};
extern struct btm_sec_l2cap_access_req_by_requirement
    btm_sec_l2cap_access_req_by_requirement;

// Name: btm_sec_link_key_notification
// Params: const RawAddress& p_bda, const Octet16& link_key, uint8_t key_type
// Return: void
struct btm_sec_link_key_notification {
  std::function<void(const RawAddress& p_bda, const Octet16& link_key,
                     uint8_t key_type)>
      body{[](const RawAddress& /* p_bda */, const Octet16& /* link_key */,
              uint8_t /* key_type */) {}};
  void operator()(const RawAddress& p_bda, const Octet16& link_key,
                  uint8_t key_type) {
    body(p_bda, link_key, key_type);
  };
};
extern struct btm_sec_link_key_notification btm_sec_link_key_notification;

// Name: btm_sec_encryption_key_refresh_complete
// Params: uint16_t handle, tHCI_STATUS status
// Return: void
struct btm_sec_encryption_key_refresh_complete {
  std::function<void(uint16_t handle, tHCI_STATUS status)> body{
      [](uint16_t /* handle */, tHCI_STATUS /* status */) -> void {}};
  void operator()(uint16_t handle, tHCI_STATUS status) {
    body(handle, status);
  };
};
extern struct btm_sec_encryption_key_refresh_complete
    btm_sec_encryption_key_refresh_complete;

// Name: btm_sec_link_key_request
// Params: const uint8_t* p_event
// Return: void
struct btm_sec_link_key_request {
  std::function<void(const RawAddress bda)> body{
      [](const RawAddress /* bda */) {}};
  void operator()(const RawAddress bda) { body(bda); };
};
extern struct btm_sec_link_key_request btm_sec_link_key_request;

// Name: btm_sec_mx_access_request
// Params: const RawAddress& bd_addr, bool is_originator, uint16_t
// security_required, tBTM_SEC_CALLBACK* p_callback, void* p_ref_data Return:
// tBTM_STATUS
struct btm_sec_mx_access_request {
  static tBTM_STATUS return_value;
  std::function<tBTM_STATUS(const RawAddress& bd_addr, bool is_originator,
                            uint16_t security_required,
                            tBTM_SEC_CALLBACK* p_callback, void* p_ref_data)>
      body{[](const RawAddress& /* bd_addr */, bool /* is_originator */,
              uint16_t /* security_required */,
              tBTM_SEC_CALLBACK* /* p_callback */,
              void* /* p_ref_data */) { return return_value; }};
  tBTM_STATUS operator()(const RawAddress& bd_addr, bool is_originator,
                         uint16_t security_required,
                         tBTM_SEC_CALLBACK* p_callback, void* p_ref_data) {
    return body(bd_addr, is_originator, security_required, p_callback,
                p_ref_data);
  };
};
extern struct btm_sec_mx_access_request btm_sec_mx_access_request;

// Name: btm_sec_pin_code_request
// Params: const uint8_t* p_event
// Return: void
struct btm_sec_pin_code_request {
  std::function<void(const RawAddress bda)> body{
      [](const RawAddress /* bda */) {}};
  void operator()(const RawAddress bda) { body(bda); };
};
extern struct btm_sec_pin_code_request btm_sec_pin_code_request;

// Name: btm_sec_rmt_host_support_feat_evt
// Params: const uint8_t* p
// Return: void
struct btm_sec_rmt_host_support_feat_evt {
  std::function<void(const RawAddress bd_addr, uint8_t features_0)> body{
      [](const RawAddress /* bd_addr */, uint8_t /* features_0 */) {}};
  void operator()(const RawAddress bd_addr, uint8_t features_0) {
    body(bd_addr, features_0);
  };
};
extern struct btm_sec_rmt_host_support_feat_evt
    btm_sec_rmt_host_support_feat_evt;

// Name: btm_sec_rmt_name_request_complete
// Params: const RawAddress* p_bd_addr, const uint8_t* p_bd_name, tHCI_STATUS
// status Return: void
struct btm_sec_rmt_name_request_complete {
  std::function<void(const RawAddress* p_bd_addr, const uint8_t* p_bd_name,
                     tHCI_STATUS status)>
      body{[](const RawAddress* /* p_bd_addr */, const uint8_t* /* p_bd_name */,
              tHCI_STATUS /* status */) {}};
  void operator()(const RawAddress* p_bd_addr, const uint8_t* p_bd_name,
                  tHCI_STATUS status) {
    body(p_bd_addr, p_bd_name, status);
  };
};
extern struct btm_sec_rmt_name_request_complete
    btm_sec_rmt_name_request_complete;

// Name: btm_sec_role_changed
// Params: tHCI_STATUS hci_status, const RawAddress& bd_addr, tHCI_ROLE new_role
// Return: void
struct btm_sec_role_changed {
  std::function<void(tHCI_STATUS hci_status, const RawAddress& bd_addr,
                     tHCI_ROLE new_role)>
      body{[](tHCI_STATUS /* hci_status */, const RawAddress& /* bd_addr */,
              tHCI_ROLE /* new_role */) {}};
  void operator()(tHCI_STATUS hci_status, const RawAddress& bd_addr,
                  tHCI_ROLE new_role) {
    body(hci_status, bd_addr, new_role);
  };
};
extern struct btm_sec_role_changed btm_sec_role_changed;

// Name: btm_sec_set_peer_sec_caps
// Params: uint16_t hci_handle, bool ssp_supported, bool sc_supported, bool
// hci_role_switch_supported, bool br_edr_supported, bool le_supported Return:
// void
struct btm_sec_set_peer_sec_caps {
  std::function<void(uint16_t hci_handle, bool ssp_supported, bool sc_supported,
                     bool hci_role_switch_supported, bool br_edr_supported,
                     bool le_supported)>
      body{[](uint16_t /* hci_handle */, bool /* ssp_supported */,
              bool /* sc_supported */, bool /* hci_role_switch_supported */,
              bool /* br_edr_supported */, bool /* le_supported */) {}};
  void operator()(uint16_t hci_handle, bool ssp_supported, bool sc_supported,
                  bool hci_role_switch_supported, bool br_edr_supported,
                  bool le_supported) {
    body(hci_handle, ssp_supported, sc_supported, hci_role_switch_supported,
         br_edr_supported, le_supported);
  };
};
extern struct btm_sec_set_peer_sec_caps btm_sec_set_peer_sec_caps;

// Name: btm_sec_update_clock_offset
// Params: uint16_t handle, uint16_t clock_offset
// Return: void
struct btm_sec_update_clock_offset {
  std::function<void(uint16_t handle, uint16_t clock_offset)> body{
      [](uint16_t /* handle */, uint16_t /* clock_offset */) {}};
  void operator()(uint16_t handle, uint16_t clock_offset) {
    body(handle, clock_offset);
  };
};
extern struct btm_sec_update_clock_offset btm_sec_update_clock_offset;

// Name: btm_simple_pair_complete
// Params: RawAddress bd_addr, uint8_t status
// Return: void
struct btm_simple_pair_complete {
  std::function<void(const RawAddress bd_addr, uint8_t status)> body{
      [](const RawAddress /* bd_addr */, uint8_t /* status */) {}};
  void operator()(const RawAddress bd_addr, uint8_t status) {
    body(bd_addr, status);
  };
};
extern struct btm_simple_pair_complete btm_simple_pair_complete;

// Name: BTM_IsRemoteNameKnown
// Params: const RawAddress& bd_addr, tBT_TRANSPORT transport
// Return: bool
struct BTM_IsRemoteNameKnown {
  static bool return_value;
  std::function<bool(const RawAddress& bd_addr, tBT_TRANSPORT transport)> body{
      [](const RawAddress& /* bd_addr */, tBT_TRANSPORT /* transport */) {
        return return_value;
      }};
  bool operator()(const RawAddress& bd_addr, tBT_TRANSPORT transport) {
    return body(bd_addr, transport);
  };
};
extern struct BTM_IsRemoteNameKnown BTM_IsRemoteNameKnown;

}  // namespace stack_btm_sec
}  // namespace mock
}  // namespace test

// END mockcify generation
