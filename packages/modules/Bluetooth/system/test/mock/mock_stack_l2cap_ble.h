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
 *   Functions generated:22
 *
 *  mockcify.pl ver 0.2
 */

#include <cstdint>
#include <functional>

// Original included files, if any
#include "stack/l2cap/l2c_int.h"
#include "types/ble_address_with_type.h"
#include "types/hci_role.h"
#include "types/raw_address.h"

// Mocked compile conditionals, if any
namespace test {
namespace mock {
namespace stack_l2cap_ble {

// Shared state between mocked functions and tests
// Name: L2CA_UpdateBleConnParams
// Params: const RawAddress& rem_bda, uint16_t min_int, uint16_t max_int,
// uint16_t latency, uint16_t timeout, uint16_t min_ce_len, uint16_t max_ce_len
// Returns: bool
struct L2CA_UpdateBleConnParams {
  std::function<bool(const RawAddress& rem_bda, uint16_t min_int,
                     uint16_t max_int, uint16_t latency, uint16_t timeout,
                     uint16_t min_ce_len, uint16_t max_ce_len)>
      body{[](const RawAddress& /* rem_bda */, uint16_t /* min_int */,
              uint16_t /* max_int */, uint16_t /* latency */,
              uint16_t /* timeout */, uint16_t /* min_ce_len */,
              uint16_t /* max_ce_len */) { return false; }};
  bool operator()(const RawAddress& rem_bda, uint16_t min_int, uint16_t max_int,
                  uint16_t latency, uint16_t timeout, uint16_t min_ce_len,
                  uint16_t max_ce_len) {
    return body(rem_bda, min_int, max_int, latency, timeout, min_ce_len,
                max_ce_len);
  };
};
extern struct L2CA_UpdateBleConnParams L2CA_UpdateBleConnParams;
// Name: L2CA_LockBleConnParamsForServiceDiscovery
// Params: const RawAddress& rem_bda, bool enable
// Returns: void
struct L2CA_LockBleConnParamsForServiceDiscovery {
  std::function<void(const RawAddress& rem_bda, bool enable)> body{
      [](const RawAddress& /* rem_bda */, bool /* enable */) { return false; }};
  void operator()(const RawAddress& rem_bda, bool enable) {
    return body(rem_bda, enable);
  };
};
extern struct L2CA_LockBleConnParamsForServiceDiscovery
    L2CA_LockBleConnParamsForServiceDiscovery;
// Name: L2CA_LockBleConnParamsForProfileConnection
// Params: const RawAddress& rem_bda, bool enable
// Returns: void
struct L2CA_LockBleConnParamsForProfileConnection {
  std::function<void(const RawAddress& rem_bda, bool enable)> body{
      [](const RawAddress& /* rem_bda */, bool /* enable */) { return false; }};
  void operator()(const RawAddress& rem_bda, bool enable) {
    return body(rem_bda, enable);
  };
};
extern struct L2CA_LockBleConnParamsForProfileConnection
    L2CA_LockBleConnParamsForProfileConnection;
struct L2CA_ConsolidateParams {
  std::function<void(const RawAddress& identity_addr, const RawAddress& rpa)>
      body{[](const RawAddress& /* identity_addr */,
              const RawAddress& /* rpa */) { return false; }};
  void operator()(const RawAddress& identity_addr, const RawAddress& rpa) {
    body(identity_addr, rpa);
  };
};
extern struct L2CA_ConsolidateParams L2CA_ConsolidateParams;
// Name: L2CA_GetBleConnRole
// Params: const RawAddress& bd_addr
// Returns: hci_role_t
struct L2CA_GetBleConnRole {
  std::function<hci_role_t(const RawAddress& bd_addr)> body{
      [](const RawAddress& /* bd_addr */) { return HCI_ROLE_CENTRAL; }};
  hci_role_t operator()(const RawAddress& bd_addr) { return body(bd_addr); };
};
extern struct L2CA_GetBleConnRole L2CA_GetBleConnRole;
// Name: l2cble_notify_le_connection
// Params: const RawAddress& bda
// Returns: void
struct l2cble_notify_le_connection {
  std::function<void(const RawAddress& bda)> body{
      [](const RawAddress& /* bda */) {}};
  void operator()(const RawAddress& bda) { body(bda); };
};
extern struct l2cble_notify_le_connection l2cble_notify_le_connection;
// Name: l2cble_conn_comp
// Params: uint16_t handle, uint8_t role, const RawAddress& bda, tBLE_ADDR_TYPE
// type, uint16_t conn_interval, uint16_t conn_latency, uint16_t conn_timeout
// Returns: bool
struct l2cble_conn_comp {
  std::function<bool(uint16_t handle, uint8_t role, const RawAddress& bda,
                     tBLE_ADDR_TYPE type, uint16_t conn_interval,
                     uint16_t conn_latency, uint16_t conn_timeout)>
      body{[](uint16_t /* handle */, uint8_t /* role */,
              const RawAddress& /* bda */, tBLE_ADDR_TYPE /* type */,
              uint16_t /* conn_interval */, uint16_t /* conn_latency */,
              uint16_t /* conn_timeout */) { return false; }};
  bool operator()(uint16_t handle, uint8_t role, const RawAddress& bda,
                  tBLE_ADDR_TYPE type, uint16_t conn_interval,
                  uint16_t conn_latency, uint16_t conn_timeout) {
    return body(handle, role, bda, type, conn_interval, conn_latency,
                conn_timeout);
  };
};
extern struct l2cble_conn_comp l2cble_conn_comp;
// Name: l2cble_process_conn_update_evt
// Params: uint16_t handle, uint8_t status, uint16_t interval, uint16_t latency,
// uint16_t timeout Returns: void
struct l2cble_process_conn_update_evt {
  std::function<void(uint16_t handle, uint8_t status, uint16_t interval,
                     uint16_t latency, uint16_t timeout)>
      body{[](uint16_t /* handle */, uint8_t /* status */,
              uint16_t /* interval */, uint16_t /* latency */,
              uint16_t /* timeout */) {}};
  void operator()(uint16_t handle, uint8_t status, uint16_t interval,
                  uint16_t latency, uint16_t timeout) {
    body(handle, status, interval, latency, timeout);
  };
};
extern struct l2cble_process_conn_update_evt l2cble_process_conn_update_evt;
// Name: l2cble_process_sig_cmd
// Params: tL2C_LCB* p_lcb, uint8_t* p, uint16_t pkt_len
// Returns: void
struct l2cble_process_sig_cmd {
  std::function<void(tL2C_LCB* p_lcb, uint8_t* p, uint16_t pkt_len)> body{
      [](tL2C_LCB* /* p_lcb */, uint8_t* /* p */, uint16_t /* pkt_len */) {}};
  void operator()(tL2C_LCB* p_lcb, uint8_t* p, uint16_t pkt_len) {
    body(p_lcb, p, pkt_len);
  };
};
extern struct l2cble_process_sig_cmd l2cble_process_sig_cmd;
// Name: l2cble_create_conn
// Params: tL2C_LCB* p_lcb
// Returns: bool
struct l2cble_create_conn {
  std::function<bool(tL2C_LCB* p_lcb)> body{
      [](tL2C_LCB* /* p_lcb */) { return false; }};
  bool operator()(tL2C_LCB* p_lcb) { return body(p_lcb); };
};
extern struct l2cble_create_conn l2cble_create_conn;
// Name: l2c_link_processs_ble_num_bufs
// Params: uint16_t num_lm_ble_bufs
// Returns: void
struct l2c_link_processs_ble_num_bufs {
  std::function<void(uint16_t num_lm_ble_bufs)> body{
      [](uint16_t /* num_lm_ble_bufs */) {}};
  void operator()(uint16_t num_lm_ble_bufs) { body(num_lm_ble_bufs); };
};
extern struct l2c_link_processs_ble_num_bufs l2c_link_processs_ble_num_bufs;
// Name: l2c_ble_link_adjust_allocation
// Params: void
// Returns: void
struct l2c_ble_link_adjust_allocation {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct l2c_ble_link_adjust_allocation l2c_ble_link_adjust_allocation;
// Name: l2cble_process_rc_param_request_evt
// Params: uint16_t handle, uint16_t int_min, uint16_t int_max, uint16_t
// latency, uint16_t timeout Returns: void
struct l2cble_process_rc_param_request_evt {
  std::function<void(uint16_t handle, uint16_t int_min, uint16_t int_max,
                     uint16_t latency, uint16_t timeout)>
      body{[](uint16_t /* handle */, uint16_t /* int_min */,
              uint16_t /* int_max */, uint16_t /* latency */,
              uint16_t /* timeout */) {}};
  void operator()(uint16_t handle, uint16_t int_min, uint16_t int_max,
                  uint16_t latency, uint16_t timeout) {
    body(handle, int_min, int_max, latency, timeout);
  };
};
extern struct l2cble_process_rc_param_request_evt
    l2cble_process_rc_param_request_evt;
// Name: l2cble_update_data_length
// Params: tL2C_LCB* p_lcb
// Returns: void
struct l2cble_update_data_length {
  std::function<void(tL2C_LCB* p_lcb)> body{[](tL2C_LCB* /* p_lcb */) {}};
  void operator()(tL2C_LCB* p_lcb) { body(p_lcb); };
};
extern struct l2cble_update_data_length l2cble_update_data_length;
// Name: l2cble_process_data_length_change_event
// Params: uint16_t handle, uint16_t tx_data_len, uint16_t rx_data_len
// Returns: void
struct l2cble_process_data_length_change_event {
  std::function<void(uint16_t handle, uint16_t tx_data_len,
                     uint16_t rx_data_len)>
      body{[](uint16_t /* handle */, uint16_t /* tx_data_len */,
              uint16_t /* rx_data_len */) {}};
  void operator()(uint16_t handle, uint16_t tx_data_len, uint16_t rx_data_len) {
    body(handle, tx_data_len, rx_data_len);
  };
};
extern struct l2cble_process_data_length_change_event
    l2cble_process_data_length_change_event;
// Name: l2cble_credit_based_conn_req
// Params: tL2C_CCB* p_ccb
// Returns: void
struct l2cble_credit_based_conn_req {
  std::function<void(tL2C_CCB* p_ccb)> body{[](tL2C_CCB* /* p_ccb */) {}};
  void operator()(tL2C_CCB* p_ccb) { body(p_ccb); };
};
extern struct l2cble_credit_based_conn_req l2cble_credit_based_conn_req;
// Name: l2cble_credit_based_conn_res
// Params: tL2C_CCB* p_ccb, uint16_t result
// Returns: void
struct l2cble_credit_based_conn_res {
  std::function<void(tL2C_CCB* p_ccb, uint16_t result)> body{
      [](tL2C_CCB* /* p_ccb */, uint16_t /* result */) {}};
  void operator()(tL2C_CCB* p_ccb, uint16_t result) { body(p_ccb, result); };
};
extern struct l2cble_credit_based_conn_res l2cble_credit_based_conn_res;
// Name: l2cble_send_flow_control_credit
// Params: tL2C_CCB* p_ccb, uint16_t credit_value
// Returns: void
struct l2cble_send_flow_control_credit {
  std::function<void(tL2C_CCB* p_ccb, uint16_t credit_value)> body{
      [](tL2C_CCB* /* p_ccb */, uint16_t /* credit_value */) {}};
  void operator()(tL2C_CCB* p_ccb, uint16_t credit_value) {
    body(p_ccb, credit_value);
  };
};
extern struct l2cble_send_flow_control_credit l2cble_send_flow_control_credit;
// Name: l2cble_send_peer_disc_req
// Params: tL2C_CCB* p_ccb
// Returns: void
struct l2cble_send_peer_disc_req {
  std::function<void(tL2C_CCB* p_ccb)> body{[](tL2C_CCB* /* p_ccb */) {}};
  void operator()(tL2C_CCB* p_ccb) { body(p_ccb); };
};
extern struct l2cble_send_peer_disc_req l2cble_send_peer_disc_req;
// Name: l2cble_sec_comp
// Params: const RawAddress* bda, tBT_TRANSPORT transport, void* p_ref_data,
// tBTM_STATUS status Returns: void
struct l2cble_sec_comp {
  std::function<void(const RawAddress* bda, tBT_TRANSPORT transport,
                     void* p_ref_data, tBTM_STATUS status)>
      body{[](const RawAddress* /* bda */, tBT_TRANSPORT /* transport */,
              void* /* p_ref_data */, tBTM_STATUS /* status */) {}};
  void operator()(const RawAddress* bda, tBT_TRANSPORT transport,
                  void* p_ref_data, tBTM_STATUS status) {
    body(bda, transport, p_ref_data, status);
  };
};
extern struct l2cble_sec_comp l2cble_sec_comp;
// Name: l2ble_sec_access_req
// Params: const RawAddress& bd_addr, uint16_t psm, bool is_originator,
// tL2CAP_SEC_CBACK* p_callback, void* p_ref_data Returns: tL2CAP_LE_RESULT_CODE
struct l2ble_sec_access_req {
  std::function<tL2CAP_LE_RESULT_CODE(
      const RawAddress& bd_addr, uint16_t psm, bool is_originator,
      tBTM_SEC_CALLBACK* p_callback, void* p_ref_data)>
      body{[](const RawAddress& /* bd_addr */, uint16_t /* psm */,
              bool /* is_originator */, tBTM_SEC_CALLBACK* /* p_callback */,
              void* /* p_ref_data */) { return L2CAP_LE_RESULT_CONN_OK; }};
  tL2CAP_LE_RESULT_CODE operator()(const RawAddress& bd_addr, uint16_t psm,
                                   bool is_originator,
                                   tBTM_SEC_CALLBACK* p_callback,
                                   void* p_ref_data) {
    return body(bd_addr, psm, is_originator, p_callback, p_ref_data);
  };
};
extern struct l2ble_sec_access_req l2ble_sec_access_req;
// Name: L2CA_AdjustConnectionIntervals
// Params: uint16_t* min_interval, uint16_t* max_interval, uint16_t
// floor_interval Returns: void
struct L2CA_AdjustConnectionIntervals {
  std::function<void(uint16_t* min_interval, uint16_t* max_interval,
                     uint16_t floor_interval)>
      body{[](uint16_t* /* min_interval */, uint16_t* /* max_interval */,
              uint16_t /* floor_interval */) {}};
  void operator()(uint16_t* min_interval, uint16_t* max_interval,
                  uint16_t floor_interval) {
    body(min_interval, max_interval, floor_interval);
  };
};
extern struct L2CA_AdjustConnectionIntervals L2CA_AdjustConnectionIntervals;
// Name: l2cble_use_preferred_conn_params
// Params: const RawAddress& bda
// Returns: void
struct l2cble_use_preferred_conn_params {
  std::function<void(const RawAddress& bda)> body{
      [](const RawAddress& /* bda */) {}};
  void operator()(const RawAddress& bda) { body(bda); };
};
extern struct l2cble_use_preferred_conn_params l2cble_use_preferred_conn_params;
// Name: L2CA_SubrateRequest
// Params:
// Returns: bool
struct L2CA_SubrateRequest {
  std::function<bool(const RawAddress& rem_bda, uint16_t subrate_min,
                     uint16_t subrate_max, uint16_t max_latency,
                     uint16_t cont_num, uint16_t timeout)>
      body{[](const RawAddress& /* rem_bda */, uint16_t /* subrate_min */,
              uint16_t /* subrate_max */, uint16_t /* max_latency */,
              uint16_t /* cont_num */,
              uint16_t /* timeout */) { return false; }};
  bool operator()(const RawAddress& rem_bda, uint16_t subrate_min,
                  uint16_t subrate_max, uint16_t max_latency, uint16_t cont_num,
                  uint16_t timeout) {
    return body(rem_bda, subrate_min, subrate_max, max_latency, cont_num,
                timeout);
  };
};
extern struct L2CA_SubrateRequest L2CA_SubrateRequest;
// Name: l2cble_process_subrate_change_evt
// Params: const RawAddress& bda
// Returns: void
struct l2cble_process_subrate_change_evt {
  std::function<void(uint16_t handle, uint8_t status, uint16_t subrate_factor,
                     uint16_t peripheral_latency, uint16_t cont_num,
                     uint16_t timeout)>
      body{[](uint16_t /* handle */, uint8_t /* status */,
              uint16_t /* subrate_factor */, uint16_t /* peripheral_latency */,
              uint16_t /* cont_num */, uint16_t /* timeout */) {}};
  void operator()(uint16_t handle, uint8_t status, uint16_t subrate_factor,
                  uint16_t peripheral_latency, uint16_t cont_num,
                  uint16_t timeout) {
    body(handle, status, subrate_factor, peripheral_latency, cont_num, timeout);
  };
};
extern struct l2cble_process_subrate_change_evt
    l2cble_process_subrate_change_evt;

}  // namespace stack_l2cap_ble
}  // namespace mock
}  // namespace test

// END mockcify generation
