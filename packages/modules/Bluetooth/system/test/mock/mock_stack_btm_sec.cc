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
 *   Functions generated:66
 *
 *  mockcify.pl ver 0.6.0
 */
// Mock include file to share data between tests and mock
#include "test/mock/mock_stack_btm_sec.h"

#include <cstdint>
#include <string>

#include "stack/include/btm_sec_api_types.h"
#include "stack/include/btm_status.h"
#include "test/common/mock_functions.h"
#include "types/raw_address.h"

// Original usings

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace stack_btm_sec {

// Function state capture and return values, if needed
struct BTM_BothEndsSupportSecureConnections
    BTM_BothEndsSupportSecureConnections;
struct BTM_CanReadDiscoverableCharacteristics
    BTM_CanReadDiscoverableCharacteristics;
struct BTM_ConfirmReqReply BTM_ConfirmReqReply;
struct BTM_GetClockOffset BTM_GetClockOffset;
struct BTM_GetPeerDeviceTypeFromFeatures BTM_GetPeerDeviceTypeFromFeatures;
struct BTM_IsAuthenticated BTM_IsAuthenticated;
struct BTM_IsEncrypted BTM_IsEncrypted;
struct BTM_IsLinkKeyAuthed BTM_IsLinkKeyAuthed;
struct BTM_IsLinkKeyKnown BTM_IsLinkKeyKnown;
struct BTM_PINCodeReply BTM_PINCodeReply;
struct BTM_PasskeyReqReply BTM_PasskeyReqReply;
struct BTM_PeerSupportsSecureConnections BTM_PeerSupportsSecureConnections;
struct BTM_ReadLocalOobData BTM_ReadLocalOobData;
struct BTM_RemoteOobDataReply BTM_RemoteOobDataReply;
struct BTM_SecAddRmtNameNotifyCallback BTM_SecAddRmtNameNotifyCallback;
struct BTM_SecBond BTM_SecBond;
struct BTM_SecBondCancel BTM_SecBondCancel;
struct BTM_SecClrService BTM_SecClrService;
struct BTM_SecClrServiceByPsm BTM_SecClrServiceByPsm;
struct BTM_SecDeleteRmtNameNotifyCallback BTM_SecDeleteRmtNameNotifyCallback;
struct BTM_SecGetDeviceLinkKeyType BTM_SecGetDeviceLinkKeyType;
struct BTM_SecIsSecurityPending BTM_SecIsSecurityPending;
struct BTM_SecRegister BTM_SecRegister;
struct BTM_SetEncryption BTM_SetEncryption;
struct BTM_SetPinType BTM_SetPinType;
struct BTM_SetSecurityLevel BTM_SetSecurityLevel;
struct BTM_update_version_info BTM_update_version_info;
struct NotifyBondingCanceled NotifyBondingCanceled;
struct btm_create_conn_cancel_complete btm_create_conn_cancel_complete;
struct btm_get_dev_class btm_get_dev_class;
struct btm_io_capabilities_req btm_io_capabilities_req;
struct btm_io_capabilities_rsp btm_io_capabilities_rsp;
struct btm_proc_sp_req_evt btm_proc_sp_req_evt;
struct btm_read_local_oob_complete btm_read_local_oob_complete;
struct btm_rem_oob_req btm_rem_oob_req;
struct btm_sec_abort_access_req btm_sec_abort_access_req;
struct btm_sec_auth_complete btm_sec_auth_complete;
struct btm_sec_bond_by_transport btm_sec_bond_by_transport;
struct btm_sec_check_pending_reqs btm_sec_check_pending_reqs;
struct btm_sec_clear_ble_keys btm_sec_clear_ble_keys;
struct btm_sec_conn_req btm_sec_conn_req;
struct btm_sec_connected btm_sec_connected;
struct btm_sec_cr_loc_oob_data_cback_event btm_sec_cr_loc_oob_data_cback_event;
struct btm_sec_dev_rec_cback_event btm_sec_dev_rec_cback_event;
struct btm_sec_dev_reset btm_sec_dev_reset;
struct btm_sec_disconnect btm_sec_disconnect;
struct btm_sec_disconnected btm_sec_disconnected;
struct btm_sec_encrypt_change btm_sec_encrypt_change;
struct btm_sec_encryption_change_evt btm_sec_encryption_change_evt;
struct btm_sec_is_a_bonded_dev btm_sec_is_a_bonded_dev;
struct btm_sec_l2cap_access_req btm_sec_l2cap_access_req;
struct btm_sec_l2cap_access_req_by_requirement
    btm_sec_l2cap_access_req_by_requirement;
struct btm_sec_link_key_notification btm_sec_link_key_notification;
struct btm_sec_encryption_key_refresh_complete
    btm_sec_encryption_key_refresh_complete;
struct btm_sec_link_key_request btm_sec_link_key_request;
struct btm_sec_mx_access_request btm_sec_mx_access_request;
struct btm_sec_pin_code_request btm_sec_pin_code_request;
struct btm_sec_rmt_host_support_feat_evt btm_sec_rmt_host_support_feat_evt;
struct btm_sec_rmt_name_request_complete btm_sec_rmt_name_request_complete;
struct btm_sec_role_changed btm_sec_role_changed;
struct btm_sec_set_peer_sec_caps btm_sec_set_peer_sec_caps;
struct btm_sec_update_clock_offset btm_sec_update_clock_offset;
struct btm_simple_pair_complete btm_simple_pair_complete;

struct BTM_IsRemoteNameKnown BTM_IsRemoteNameKnown;

}  // namespace stack_btm_sec
}  // namespace mock
}  // namespace test

// Mocked function return values, if any
namespace test {
namespace mock {
namespace stack_btm_sec {

bool BTM_BothEndsSupportSecureConnections::return_value = false;
bool BTM_CanReadDiscoverableCharacteristics::return_value = false;
uint16_t BTM_GetClockOffset::return_value = 0;
tBT_DEVICE_TYPE BTM_GetPeerDeviceTypeFromFeatures::return_value = 0;
bool BTM_IsAuthenticated::return_value = false;
bool BTM_IsEncrypted::return_value = false;
bool BTM_IsLinkKeyAuthed::return_value = false;
bool BTM_IsLinkKeyKnown::return_value = false;
bool BTM_PeerSupportsSecureConnections::return_value = false;
bool BTM_SecAddRmtNameNotifyCallback::return_value = false;
tBTM_STATUS BTM_SecBond::return_value = 0;
tBTM_STATUS BTM_SecBondCancel::return_value = 0;
uint8_t BTM_SecClrService::return_value = 0;
uint8_t BTM_SecClrServiceByPsm::return_value = 0;
bool BTM_SecDeleteRmtNameNotifyCallback::return_value = false;
tBTM_LINK_KEY_TYPE BTM_SecGetDeviceLinkKeyType::return_value = 0;
bool BTM_SecIsSecurityPending::return_value = false;
bool BTM_SecRegister::return_value = false;
tBTM_STATUS BTM_SetEncryption::return_value = 0;
bool BTM_SetSecurityLevel::return_value = false;
const DEV_CLASS btm_get_dev_class::return_value = kDevClassEmpty;
tBTM_STATUS btm_sec_bond_by_transport::return_value = 0;
tBTM_STATUS btm_sec_disconnect::return_value = 0;
bool btm_sec_is_a_bonded_dev::return_value = false;
tBTM_STATUS btm_sec_l2cap_access_req::return_value = 0;
tBTM_STATUS btm_sec_l2cap_access_req_by_requirement::return_value = 0;
tBTM_STATUS btm_sec_mx_access_request::return_value = 0;

bool BTM_IsRemoteNameKnown::return_value = false;

}  // namespace stack_btm_sec
}  // namespace mock
}  // namespace test

// Mocked functions, if any
bool BTM_BothEndsSupportSecureConnections(const RawAddress& bd_addr) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_sec::BTM_BothEndsSupportSecureConnections(
      bd_addr);
}
bool BTM_CanReadDiscoverableCharacteristics(const RawAddress& bd_addr) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_sec::BTM_CanReadDiscoverableCharacteristics(
      bd_addr);
}
void BTM_ConfirmReqReply(tBTM_STATUS res, const RawAddress& bd_addr) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_sec::BTM_ConfirmReqReply(res, bd_addr);
}
uint16_t BTM_GetClockOffset(const RawAddress& remote_bda) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_sec::BTM_GetClockOffset(remote_bda);
}
tBT_DEVICE_TYPE BTM_GetPeerDeviceTypeFromFeatures(const RawAddress& bd_addr) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_sec::BTM_GetPeerDeviceTypeFromFeatures(bd_addr);
}
bool BTM_IsAuthenticated(const RawAddress& bd_addr, tBT_TRANSPORT transport) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_sec::BTM_IsAuthenticated(bd_addr, transport);
}
bool BTM_IsEncrypted(const RawAddress& bd_addr, tBT_TRANSPORT transport) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_sec::BTM_IsEncrypted(bd_addr, transport);
}
bool BTM_IsLinkKeyAuthed(const RawAddress& bd_addr, tBT_TRANSPORT transport) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_sec::BTM_IsLinkKeyAuthed(bd_addr, transport);
}
bool BTM_IsLinkKeyKnown(const RawAddress& bd_addr, tBT_TRANSPORT transport) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_sec::BTM_IsLinkKeyKnown(bd_addr, transport);
}
void BTM_PINCodeReply(const RawAddress& bd_addr, tBTM_STATUS res,
                      uint8_t pin_len, uint8_t* p_pin) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_sec::BTM_PINCodeReply(bd_addr, res, pin_len, p_pin);
}
void BTM_PasskeyReqReply(tBTM_STATUS res, const RawAddress& bd_addr,
                         uint32_t passkey) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_sec::BTM_PasskeyReqReply(res, bd_addr, passkey);
}
bool BTM_PeerSupportsSecureConnections(const RawAddress& bd_addr) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_sec::BTM_PeerSupportsSecureConnections(bd_addr);
}
void BTM_ReadLocalOobData(void) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_sec::BTM_ReadLocalOobData();
}
void BTM_RemoteOobDataReply(tBTM_STATUS res, const RawAddress& bd_addr,
                            const Octet16& c, const Octet16& r) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_sec::BTM_RemoteOobDataReply(res, bd_addr, c, r);
}
bool BTM_SecAddRmtNameNotifyCallback(tBTM_RMT_NAME_CALLBACK* p_callback) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_sec::BTM_SecAddRmtNameNotifyCallback(p_callback);
}
tBTM_STATUS BTM_SecBond(const RawAddress& bd_addr, tBLE_ADDR_TYPE addr_type,
                        tBT_TRANSPORT transport, tBT_DEVICE_TYPE device_type) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_sec::BTM_SecBond(bd_addr, addr_type, transport,
                                                device_type);
}
tBTM_STATUS BTM_SecBondCancel(const RawAddress& bd_addr) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_sec::BTM_SecBondCancel(bd_addr);
}
uint8_t BTM_SecClrService(uint8_t service_id) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_sec::BTM_SecClrService(service_id);
}
uint8_t BTM_SecClrServiceByPsm(uint16_t psm) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_sec::BTM_SecClrServiceByPsm(psm);
}
bool BTM_SecDeleteRmtNameNotifyCallback(tBTM_RMT_NAME_CALLBACK* p_callback) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_sec::BTM_SecDeleteRmtNameNotifyCallback(
      p_callback);
}
tBTM_LINK_KEY_TYPE BTM_SecGetDeviceLinkKeyType(const RawAddress& bd_addr) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_sec::BTM_SecGetDeviceLinkKeyType(bd_addr);
}
bool BTM_SecIsSecurityPending(const RawAddress& bd_addr) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_sec::BTM_SecIsSecurityPending(bd_addr);
}
bool BTM_SecRegister(const tBTM_APPL_INFO* p_cb_info) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_sec::BTM_SecRegister(p_cb_info);
}
tBTM_STATUS BTM_SetEncryption(const RawAddress& bd_addr,
                              tBT_TRANSPORT transport,
                              tBTM_SEC_CALLBACK* p_callback, void* p_ref_data,
                              tBTM_BLE_SEC_ACT sec_act) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_sec::BTM_SetEncryption(
      bd_addr, transport, p_callback, p_ref_data, sec_act);
}
void BTM_SetPinType(uint8_t pin_type, PIN_CODE pin_code, uint8_t pin_code_len) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_sec::BTM_SetPinType(pin_type, pin_code, pin_code_len);
}
bool BTM_SetSecurityLevel(bool is_originator, const char* p_name,
                          uint8_t service_id, uint16_t sec_level, uint16_t psm,
                          uint32_t mx_proto_id, uint32_t mx_chan_id) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_sec::BTM_SetSecurityLevel(
      is_originator, p_name, service_id, sec_level, psm, mx_proto_id,
      mx_chan_id);
}
void BTM_update_version_info(const RawAddress& bd_addr,
                             const remote_version_info& remote_version_info) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_sec::BTM_update_version_info(bd_addr,
                                                     remote_version_info);
}
void NotifyBondingCanceled(tBTM_STATUS btm_status) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_sec::NotifyBondingCanceled(btm_status);
}
void btm_create_conn_cancel_complete(uint8_t status, const RawAddress bd_addr) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_sec::btm_create_conn_cancel_complete(status, bd_addr);
}
const DEV_CLASS btm_get_dev_class(const RawAddress& bda) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_sec::btm_get_dev_class(bda);
}
void btm_io_capabilities_req(RawAddress p) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_sec::btm_io_capabilities_req(p);
}
void btm_io_capabilities_rsp(const tBTM_SP_IO_RSP evt_data) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_sec::btm_io_capabilities_rsp(evt_data);
}
void btm_proc_sp_req_evt(tBTM_SP_EVT event, const RawAddress bd_addr,
                         uint32_t value) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_sec::btm_proc_sp_req_evt(event, bd_addr, value);
}
void btm_read_local_oob_complete(const tBTM_SP_LOC_OOB evt_data) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_sec::btm_read_local_oob_complete(evt_data);
}
void btm_rem_oob_req(const RawAddress bd_addr) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_sec::btm_rem_oob_req(bd_addr);
}
void btm_sec_abort_access_req(const RawAddress& bd_addr) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_sec::btm_sec_abort_access_req(bd_addr);
}
void btm_sec_auth_complete(uint16_t handle, tHCI_STATUS status) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_sec::btm_sec_auth_complete(handle, status);
}
tBTM_STATUS btm_sec_bond_by_transport(const RawAddress& bd_addr,
                                      tBLE_ADDR_TYPE addr_type,
                                      tBT_TRANSPORT transport) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_sec::btm_sec_bond_by_transport(
      bd_addr, addr_type, transport);
}
void btm_sec_check_pending_reqs(void) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_sec::btm_sec_check_pending_reqs();
}
void btm_sec_clear_ble_keys(tBTM_SEC_DEV_REC* p_dev_rec) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_sec::btm_sec_clear_ble_keys(p_dev_rec);
}
void btm_sec_conn_req(const RawAddress& bda, const DEV_CLASS dc) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_sec::btm_sec_conn_req(bda, dc);
}
void btm_sec_connected(const RawAddress& bda, uint16_t handle,
                       tHCI_STATUS status, uint8_t enc_mode,
                       tHCI_ROLE assigned_role) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_sec::btm_sec_connected(bda, handle, status, enc_mode,
                                               assigned_role);
}
void btm_sec_cr_loc_oob_data_cback_event(const RawAddress& address,
                                         tSMP_LOC_OOB_DATA loc_oob_data) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_sec::btm_sec_cr_loc_oob_data_cback_event(address,
                                                                 loc_oob_data);
}
void btm_sec_dev_rec_cback_event(tBTM_SEC_DEV_REC* p_dev_rec,
                                 tBTM_STATUS btm_status, bool is_le_transport) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_sec::btm_sec_dev_rec_cback_event(p_dev_rec, btm_status,
                                                         is_le_transport);
}
void btm_sec_dev_reset(void) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_sec::btm_sec_dev_reset();
}
tBTM_STATUS btm_sec_disconnect(uint16_t handle, tHCI_STATUS reason,
                               std::string comment) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_sec::btm_sec_disconnect(handle, reason, comment);
}
void btm_sec_disconnected(uint16_t handle, tHCI_REASON reason,
                          std::string comment) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_sec::btm_sec_disconnected(handle, reason, comment);
}
void btm_sec_encrypt_change(uint16_t handle, tHCI_STATUS status,
                            uint8_t encr_enable) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_sec::btm_sec_encrypt_change(handle, status,
                                                    encr_enable);
}
void btm_sec_encryption_change_evt(uint16_t handle, tHCI_STATUS status,
                                   uint8_t encr_enable) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_sec::btm_sec_encryption_change_evt(handle, status,
                                                           encr_enable);
}
bool btm_sec_is_a_bonded_dev(const RawAddress& bda) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_sec::btm_sec_is_a_bonded_dev(bda);
}
tBTM_STATUS btm_sec_l2cap_access_req(const RawAddress& bd_addr, uint16_t psm,
                                     bool is_originator,
                                     tBTM_SEC_CALLBACK* p_callback,
                                     void* p_ref_data) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_sec::btm_sec_l2cap_access_req(
      bd_addr, psm, is_originator, p_callback, p_ref_data);
}
tBTM_STATUS btm_sec_l2cap_access_req_by_requirement(
    const RawAddress& bd_addr, uint16_t security_required, bool is_originator,
    tBTM_SEC_CALLBACK* p_callback, void* p_ref_data) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_sec::btm_sec_l2cap_access_req_by_requirement(
      bd_addr, security_required, is_originator, p_callback, p_ref_data);
}
void btm_sec_link_key_notification(const RawAddress& p_bda,
                                   const Octet16& link_key, uint8_t key_type) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_sec::btm_sec_link_key_notification(p_bda, link_key,
                                                           key_type);
}
void btm_sec_encryption_key_refresh_complete(uint16_t handle,
                                             tHCI_STATUS status) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_sec::btm_sec_encryption_key_refresh_complete(handle,
                                                                     status);
}
void btm_sec_link_key_request(const RawAddress bda) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_sec::btm_sec_link_key_request(bda);
}
tBTM_STATUS btm_sec_mx_access_request(const RawAddress& bd_addr,
                                      bool is_originator,
                                      uint16_t security_required,
                                      tBTM_SEC_CALLBACK* p_callback,
                                      void* p_ref_data) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_sec::btm_sec_mx_access_request(
      bd_addr, is_originator, security_required, p_callback, p_ref_data);
}
void btm_sec_pin_code_request(const RawAddress bda) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_sec::btm_sec_pin_code_request(bda);
}
void btm_sec_rmt_host_support_feat_evt(const RawAddress bd_addr,
                                       uint8_t features_0) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_sec::btm_sec_rmt_host_support_feat_evt(bd_addr,
                                                               features_0);
}
void btm_sec_rmt_name_request_complete(const RawAddress* p_bd_addr,
                                       const uint8_t* p_bd_name,
                                       tHCI_STATUS status) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_sec::btm_sec_rmt_name_request_complete(
      p_bd_addr, p_bd_name, status);
}
void btm_sec_role_changed(tHCI_STATUS hci_status, const RawAddress& bd_addr,
                          tHCI_ROLE new_role) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_sec::btm_sec_role_changed(hci_status, bd_addr,
                                                  new_role);
}
void btm_sec_set_peer_sec_caps(uint16_t hci_handle, bool ssp_supported,
                               bool sc_supported,
                               bool hci_role_switch_supported,
                               bool br_edr_supported, bool le_supported) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_sec::btm_sec_set_peer_sec_caps(
      hci_handle, ssp_supported, sc_supported, hci_role_switch_supported,
      br_edr_supported, le_supported);
}
void btm_sec_update_clock_offset(uint16_t handle, uint16_t clock_offset) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_sec::btm_sec_update_clock_offset(handle, clock_offset);
}
void btm_simple_pair_complete(const RawAddress bd_addr, uint8_t status) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_sec::btm_simple_pair_complete(bd_addr, status);
}
bool BTM_IsRemoteNameKnown(const RawAddress& bd_addr, tBT_TRANSPORT transport) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_sec::BTM_IsRemoteNameKnown(bd_addr, transport);
}
bool BTM_BleIsLinkKeyKnown(const RawAddress /* address */) {
  inc_func_call_count(__func__);
  return false;
}
// Mocked functions complete
// END mockcify generation
