/*
 * Copyright 2022 The Android Open Source Project
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
 *   Functions generated:52
 *
 *  mockcify.pl ver 0.5.0
 */
// Mock include file to share data between tests and mock
#include "test/mock/mock_stack_btm_ble.h"

#include <cstdint>
#include <optional>

#include "stack/include/btm_api_types.h"
#include "stack/include/btm_status.h"
#include "test/common/mock_functions.h"

// Original usings

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace stack_btm_ble {

// Function state capture and return values, if needed
struct BTM_BleConfirmReply BTM_BleConfirmReply;
struct BTM_BleDataSignature BTM_BleDataSignature;
struct BTM_BleLoadLocalKeys BTM_BleLoadLocalKeys;
struct BTM_BleOobDataReply BTM_BleOobDataReply;
struct BTM_BlePasskeyReply BTM_BlePasskeyReply;
struct BTM_BleReadPhy BTM_BleReadPhy;
struct BTM_BleReceiverTest BTM_BleReceiverTest;
struct BTM_BleSecureConnectionOobDataReply BTM_BleSecureConnectionOobDataReply;
struct BTM_BleSetPhy BTM_BleSetPhy;
struct BTM_BleSetPrefConnParams BTM_BleSetPrefConnParams;
struct BTM_BleTestEnd BTM_BleTestEnd;
struct BTM_BleTransmitterTest BTM_BleTransmitterTest;
struct BTM_BleVerifySignature BTM_BleVerifySignature;
struct BTM_GetDeviceDHK BTM_GetDeviceDHK;
struct BTM_GetDeviceEncRoot BTM_GetDeviceEncRoot;
struct BTM_GetDeviceIDRoot BTM_GetDeviceIDRoot;
struct BTM_ReadConnectedTransportAddress BTM_ReadConnectedTransportAddress;
struct BTM_ReadDevInfo BTM_ReadDevInfo;
struct BTM_GetRemoteDeviceName BTM_GetRemoteDeviceName;
struct BTM_SecAddBleDevice BTM_SecAddBleDevice;
struct BTM_SecAddBleKey BTM_SecAddBleKey;
struct BTM_SecurityGrant BTM_SecurityGrant;
struct BTM_SetBleDataLength BTM_SetBleDataLength;
struct BTM_UseLeLink BTM_UseLeLink;
struct btm_ble_connected btm_ble_connected;
struct btm_ble_get_acl_remote_addr btm_ble_get_acl_remote_addr;
struct btm_ble_get_enc_key_type btm_ble_get_enc_key_type;
struct btm_ble_link_encrypted btm_ble_link_encrypted;
struct btm_ble_link_sec_check btm_ble_link_sec_check;
struct btm_ble_ltk_request btm_ble_ltk_request;
struct btm_ble_ltk_request_reply btm_ble_ltk_request_reply;
struct btm_ble_read_sec_key_size btm_ble_read_sec_key_size;
struct btm_ble_reset_id btm_ble_reset_id;
struct btm_ble_set_encryption btm_ble_set_encryption;
struct btm_ble_set_keep_rfu_in_auth_req btm_ble_set_keep_rfu_in_auth_req;
struct btm_ble_set_no_disc_if_pair_fail btm_ble_set_no_disc_if_pair_fail;
struct btm_ble_set_test_local_sign_cntr_value
    btm_ble_set_test_local_sign_cntr_value;
struct btm_ble_set_test_mac_value btm_ble_set_test_mac_value;
struct btm_ble_start_encrypt btm_ble_start_encrypt;
struct btm_ble_start_sec_check btm_ble_start_sec_check;
struct btm_ble_test_command_complete btm_ble_test_command_complete;
struct btm_ble_update_sec_key_size btm_ble_update_sec_key_size;
struct btm_get_local_div btm_get_local_div;
struct btm_proc_smp_cback btm_proc_smp_cback;
struct btm_sec_save_le_key btm_sec_save_le_key;
struct doNothing doNothing;
struct read_phy_cb read_phy_cb;

}  // namespace stack_btm_ble
}  // namespace mock
}  // namespace test

// Mocked function return values, if any
namespace test {
namespace mock {
namespace stack_btm_ble {

bool BTM_GetRemoteDeviceName::return_value = false;
bool BTM_BleDataSignature::return_value = false;
bool BTM_BleVerifySignature::return_value = false;
const Octet16 BTM_GetDeviceDHK::return_value{0xd5, 0xcb, 0x84, 0x54, 0xd1, 0x77,
                                             0x73, 0x3e, 0xff, 0xff, 0xb2, 0xec,
                                             0x71, 0x2b, 0xae, 0xab};
const Octet16 BTM_GetDeviceEncRoot::return_value{
    0xd5, 0xcb, 0x84, 0x54, 0xd1, 0x77, 0x73, 0x3e,
    0xff, 0xff, 0xb2, 0xec, 0x71, 0x2b, 0xae, 0xab};
const Octet16 BTM_GetDeviceIDRoot::return_value{
    0xd5, 0xcb, 0x84, 0x54, 0xd1, 0x77, 0x73, 0x3e,
    0xff, 0xff, 0xb2, 0xec, 0x71, 0x2b, 0xae, 0xab};
bool BTM_ReadConnectedTransportAddress::return_value = false;
tBTM_STATUS BTM_SetBleDataLength::return_value = 0;
bool BTM_UseLeLink::return_value = false;
bool btm_ble_get_acl_remote_addr::return_value = false;
bool btm_ble_get_enc_key_type::return_value = false;
uint8_t btm_ble_read_sec_key_size::return_value = 0;
tBTM_STATUS btm_ble_set_encryption::return_value = 0;
tBTM_STATUS btm_ble_start_encrypt::return_value = 0;
tBTM_STATUS btm_ble_start_sec_check::return_value = BTM_SUCCESS;
bool btm_get_local_div::return_value = false;
tBTM_STATUS btm_proc_smp_cback::return_value = 0;

}  // namespace stack_btm_ble
}  // namespace mock
}  // namespace test

// Mocked functions, if any
void BTM_BleConfirmReply(const RawAddress& bd_addr, uint8_t res) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble::BTM_BleConfirmReply(bd_addr, res);
}
bool BTM_BleDataSignature(const RawAddress& bd_addr, uint8_t* p_text,
                          uint16_t len, BLE_SIGNATURE signature) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_ble::BTM_BleDataSignature(bd_addr, p_text, len,
                                                         signature);
}
void BTM_BleLoadLocalKeys(uint8_t key_type, tBTM_BLE_LOCAL_KEYS* p_key) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble::BTM_BleLoadLocalKeys(key_type, p_key);
}
void BTM_BleOobDataReply(const RawAddress& bd_addr, uint8_t res, uint8_t len,
                         uint8_t* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble::BTM_BleOobDataReply(bd_addr, res, len, p_data);
}
void BTM_BlePasskeyReply(const RawAddress& bd_addr, uint8_t res,
                         uint32_t passkey) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble::BTM_BlePasskeyReply(bd_addr, res, passkey);
}
void BTM_BleReadPhy(
    const RawAddress& bd_addr,
    base::Callback<void(uint8_t tx_phy, uint8_t rx_phy, uint8_t status)> cb) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble::BTM_BleReadPhy(bd_addr, cb);
}
void BTM_BleReceiverTest(uint8_t rx_freq, tBTM_CMPL_CB* p_cmd_cmpl_cback) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble::BTM_BleReceiverTest(rx_freq, p_cmd_cmpl_cback);
}
void BTM_BleSecureConnectionOobDataReply(const RawAddress& bd_addr,
                                         uint8_t* p_c, uint8_t* p_r) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble::BTM_BleSecureConnectionOobDataReply(bd_addr, p_c,
                                                                 p_r);
}
void BTM_BleSetPhy(const RawAddress& bd_addr, uint8_t tx_phys, uint8_t rx_phys,
                   uint16_t phy_options) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble::BTM_BleSetPhy(bd_addr, tx_phys, rx_phys,
                                           phy_options);
}
void BTM_BleSetPrefConnParams(const RawAddress& bd_addr, uint16_t min_conn_int,
                              uint16_t max_conn_int,
                              uint16_t peripheral_latency,
                              uint16_t supervision_tout) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble::BTM_BleSetPrefConnParams(
      bd_addr, min_conn_int, max_conn_int, peripheral_latency,
      supervision_tout);
}
void BTM_BleTestEnd(tBTM_CMPL_CB* p_cmd_cmpl_cback) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble::BTM_BleTestEnd(p_cmd_cmpl_cback);
}
void BTM_BleTransmitterTest(uint8_t tx_freq, uint8_t test_data_len,
                            uint8_t packet_payload,
                            tBTM_CMPL_CB* p_cmd_cmpl_cback) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble::BTM_BleTransmitterTest(
      tx_freq, test_data_len, packet_payload, p_cmd_cmpl_cback);
}
bool BTM_BleVerifySignature(const RawAddress& bd_addr, uint8_t* p_orig,
                            uint16_t len, uint32_t counter, uint8_t* p_comp) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_ble::BTM_BleVerifySignature(bd_addr, p_orig, len,
                                                           counter, p_comp);
}
const Octet16& BTM_GetDeviceDHK() {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_ble::BTM_GetDeviceDHK();
}
const Octet16& BTM_GetDeviceEncRoot() {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_ble::BTM_GetDeviceEncRoot();
}
const Octet16& BTM_GetDeviceIDRoot() {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_ble::BTM_GetDeviceIDRoot();
}
bool BTM_ReadConnectedTransportAddress(RawAddress* remote_bda,
                                       tBT_TRANSPORT transport) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_ble::BTM_ReadConnectedTransportAddress(
      remote_bda, transport);
}
void BTM_ReadDevInfo(const RawAddress& remote_bda, tBT_DEVICE_TYPE* p_dev_type,
                     tBLE_ADDR_TYPE* p_addr_type) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble::BTM_ReadDevInfo(remote_bda, p_dev_type,
                                             p_addr_type);
}
bool BTM_GetRemoteDeviceName(const RawAddress& bd_addr, BD_NAME bd_name) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_ble::BTM_GetRemoteDeviceName(bd_addr, bd_name);
}
void BTM_SecAddBleDevice(const RawAddress& bd_addr, tBT_DEVICE_TYPE dev_type,
                         tBLE_ADDR_TYPE addr_type) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble::BTM_SecAddBleDevice(bd_addr, dev_type, addr_type);
}
void BTM_SecAddBleKey(const RawAddress& bd_addr, tBTM_LE_KEY_VALUE* p_le_key,
                      tBTM_LE_KEY_TYPE key_type) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble::BTM_SecAddBleKey(bd_addr, p_le_key, key_type);
}
void BTM_SecurityGrant(const RawAddress& bd_addr, uint8_t res) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble::BTM_SecurityGrant(bd_addr, res);
}
tBTM_STATUS BTM_SetBleDataLength(const RawAddress& bd_addr,
                                 uint16_t tx_pdu_length) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_ble::BTM_SetBleDataLength(bd_addr,
                                                         tx_pdu_length);
}
bool BTM_UseLeLink(const RawAddress& bd_addr) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_ble::BTM_UseLeLink(bd_addr);
}
void btm_ble_connected(const RawAddress& bda, uint16_t handle, uint8_t enc_mode,
                       uint8_t role, tBLE_ADDR_TYPE addr_type,
                       bool addr_matched,
                       bool can_read_discoverable_characteristics) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble::btm_ble_connected(
      bda, handle, enc_mode, role, addr_type, addr_matched,
      can_read_discoverable_characteristics);
}
bool btm_ble_get_acl_remote_addr(uint16_t hci_handle, RawAddress& conn_addr,
                                 tBLE_ADDR_TYPE* p_addr_type) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_ble::btm_ble_get_acl_remote_addr(
      hci_handle, conn_addr, p_addr_type);
}
bool btm_ble_get_enc_key_type(const RawAddress& bd_addr, uint8_t* p_key_types) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_ble::btm_ble_get_enc_key_type(bd_addr,
                                                             p_key_types);
}
void btm_ble_link_encrypted(const RawAddress& bd_addr, uint8_t encr_enable) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble::btm_ble_link_encrypted(bd_addr, encr_enable);
}
void btm_ble_link_sec_check(const RawAddress& bd_addr,
                            tBTM_LE_AUTH_REQ auth_req,
                            tBTM_BLE_SEC_REQ_ACT* p_sec_req_act) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble::btm_ble_link_sec_check(bd_addr, auth_req,
                                                    p_sec_req_act);
}
void btm_ble_ltk_request(uint16_t handle, BT_OCTET8 rand, uint16_t ediv) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble::btm_ble_ltk_request(handle, rand, ediv);
}
void btm_ble_ltk_request_reply(const RawAddress& bda, bool use_stk,
                               const Octet16& stk) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble::btm_ble_ltk_request_reply(bda, use_stk, stk);
}
uint8_t btm_ble_read_sec_key_size(const RawAddress& bd_addr) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_ble::btm_ble_read_sec_key_size(bd_addr);
}
void btm_ble_reset_id(void) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble::btm_ble_reset_id();
}
tBTM_STATUS btm_ble_set_encryption(const RawAddress& bd_addr,
                                   tBTM_BLE_SEC_ACT sec_act,
                                   uint8_t link_role) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_ble::btm_ble_set_encryption(bd_addr, sec_act,
                                                           link_role);
}
void btm_ble_set_keep_rfu_in_auth_req(bool keep_rfu) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble::btm_ble_set_keep_rfu_in_auth_req(keep_rfu);
}
void btm_ble_set_no_disc_if_pair_fail(bool disable_disc) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble::btm_ble_set_no_disc_if_pair_fail(disable_disc);
}
void btm_ble_set_test_local_sign_cntr_value(bool enable,
                                            uint32_t test_local_sign_cntr) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble::btm_ble_set_test_local_sign_cntr_value(
      enable, test_local_sign_cntr);
}
void btm_ble_set_test_mac_value(bool enable, uint8_t* p_test_mac_val) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble::btm_ble_set_test_mac_value(enable, p_test_mac_val);
}
tBTM_STATUS btm_ble_start_encrypt(const RawAddress& bda, bool use_stk,
                                  Octet16* p_stk) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_ble::btm_ble_start_encrypt(bda, use_stk, p_stk);
}
tBTM_STATUS btm_ble_start_sec_check(const RawAddress& bd_addr, uint16_t psm,
                                    bool is_originator,
                                    tBTM_SEC_CALLBACK* p_callback,
                                    void* p_ref_data) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_ble::btm_ble_start_sec_check(
      bd_addr, psm, is_originator, p_callback, p_ref_data);
}
void btm_ble_test_command_complete(uint8_t* p) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble::btm_ble_test_command_complete(p);
}
void btm_ble_update_sec_key_size(const RawAddress& bd_addr,
                                 uint8_t enc_key_size) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble::btm_ble_update_sec_key_size(bd_addr, enc_key_size);
}
bool btm_get_local_div(const RawAddress& bd_addr, uint16_t* p_div) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_ble::btm_get_local_div(bd_addr, p_div);
}
tBTM_STATUS btm_proc_smp_cback(tSMP_EVT event, const RawAddress& bd_addr,
                               const tSMP_EVT_DATA* p_data) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_ble::btm_proc_smp_cback(event, bd_addr, p_data);
}
void btm_sec_save_le_key(const RawAddress& bd_addr, tBTM_LE_KEY_TYPE key_type,
                         tBTM_LE_KEY_VALUE* p_keys, bool pass_to_application) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble::btm_sec_save_le_key(bd_addr, key_type, p_keys,
                                                 pass_to_application);
}
void doNothing(uint8_t* data, uint16_t len) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble::doNothing(data, len);
}
void read_phy_cb(
    base::Callback<void(uint8_t tx_phy, uint8_t rx_phy, uint8_t status)> cb,
    uint8_t* data, uint16_t len) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble::read_phy_cb(cb, data, len);
}
// Mocked functions complete
// END mockcify generation

std::optional<Octet16> BTM_BleGetPeerLTK(const RawAddress /* address */) {
  return std::nullopt;
}
