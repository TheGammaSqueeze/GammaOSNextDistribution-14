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
 *   Functions generated:72
 */

#include <cstdint>

#include "stack/include/bt_hdr.h"
#include "stack/l2cap/l2c_int.h"
#include "test/common/mock_functions.h"
#include "types/raw_address.h"

BT_HDR* l2cu_build_header(tL2C_LCB* /* p_lcb */, uint16_t /* len */,
                          uint8_t /* cmd */, uint8_t /* signal_id */) {
  inc_func_call_count(__func__);
  return nullptr;
}
bool l2c_is_cmd_rejected(uint8_t /* cmd_code */, uint8_t /* signal_id */,
                         tL2C_LCB* /* p_lcb */) {
  inc_func_call_count(__func__);
  return false;
}
bool l2cu_create_conn_le(tL2C_LCB* /* p_lcb */) {
  inc_func_call_count(__func__);
  return false;
}
bool l2cu_create_conn_le(tL2C_LCB* /* p_lcb */, uint8_t /* initiating_phys */) {
  inc_func_call_count(__func__);
  return false;
}
bool l2cu_initialize_fixed_ccb(tL2C_LCB* /* p_lcb */,
                               uint16_t /* fixed_cid */) {
  inc_func_call_count(__func__);
  return false;
}
bool l2cu_is_ccb_active(tL2C_CCB* /* p_ccb */) {
  inc_func_call_count(__func__);
  return false;
}
bool l2cu_lcb_disconnecting(void) {
  inc_func_call_count(__func__);
  return false;
}
bool l2cu_set_acl_priority(const RawAddress& /* bd_addr */,
                           tL2CAP_PRIORITY /* priority */,
                           bool /* reset_after_rs */) {
  inc_func_call_count(__func__);
  return false;
}
bool l2cu_start_post_bond_timer(uint16_t /* handle */) {
  inc_func_call_count(__func__);
  return false;
}
tL2C_CCB* l2cu_allocate_ccb(tL2C_LCB* /* p_lcb */, uint16_t /* cid */,
                            bool /* is_eatt */) {
  inc_func_call_count(__func__);
  return nullptr;
}
tL2C_CCB* l2cu_find_ccb_by_cid(tL2C_LCB* /* p_lcb */,
                               uint16_t /* local_cid */) {
  inc_func_call_count(__func__);
  return nullptr;
}
tL2C_CCB* l2cu_find_ccb_by_remote_cid(tL2C_LCB* /* p_lcb */,
                                      uint16_t /* remote_cid */) {
  inc_func_call_count(__func__);
  return nullptr;
}
tL2C_LCB* l2cu_allocate_lcb(const RawAddress& /* p_bd_addr */,
                            bool /* is_bonding */,
                            tBT_TRANSPORT /* transport */) {
  inc_func_call_count(__func__);
  return nullptr;
}
tL2C_LCB* l2cu_find_lcb_by_bd_addr(const RawAddress& /* p_bd_addr */,
                                   tBT_TRANSPORT /* transport */) {
  inc_func_call_count(__func__);
  return nullptr;
}
tL2C_LCB* l2cu_find_lcb_by_handle(uint16_t /* handle */) {
  inc_func_call_count(__func__);
  return nullptr;
}
tL2C_LCB* l2cu_find_lcb_by_state(tL2C_LINK_STATE /* state */) {
  inc_func_call_count(__func__);
  return nullptr;
}
tL2C_RCB* l2cu_allocate_ble_rcb(uint16_t /* psm */) {
  inc_func_call_count(__func__);
  return nullptr;
}
tL2C_RCB* l2cu_allocate_rcb(uint16_t /* psm */) {
  inc_func_call_count(__func__);
  return nullptr;
}
tL2C_RCB* l2cu_find_ble_rcb_by_psm(uint16_t /* psm */) {
  inc_func_call_count(__func__);
  return nullptr;
}
tL2C_RCB* l2cu_find_rcb_by_psm(uint16_t /* psm */) {
  inc_func_call_count(__func__);
  return nullptr;
}
uint8_t l2cu_get_num_hi_priority(void) {
  inc_func_call_count(__func__);
  return 0;
}
uint8_t l2cu_process_peer_cfg_req(tL2C_CCB* /* p_ccb */,
                                  tL2CAP_CFG_INFO* /* p_cfg */) {
  inc_func_call_count(__func__);
  return 0;
}
void l2cu_adj_id(tL2C_LCB* /* p_lcb */) { inc_func_call_count(__func__); }
void l2cu_adjust_out_mps(tL2C_CCB* /* p_ccb */) {
  inc_func_call_count(__func__);
}
void l2cu_change_pri_ccb(tL2C_CCB* /* p_ccb */,
                         tL2CAP_CHNL_PRIORITY /* priority */) {
  inc_func_call_count(__func__);
}
void l2cu_check_channel_congestion(tL2C_CCB* /* p_ccb */) {
  inc_func_call_count(__func__);
}
void l2cu_create_conn_after_switch(tL2C_LCB* /* p_lcb */) {
  inc_func_call_count(__func__);
}
void l2cu_create_conn_br_edr(tL2C_LCB* /* p_lcb */) {
  inc_func_call_count(__func__);
}
void l2cu_dequeue_ccb(tL2C_CCB* /* p_ccb */) { inc_func_call_count(__func__); }
void l2cu_device_reset(void) { inc_func_call_count(__func__); }
void l2cu_disconnect_chnl(tL2C_CCB* /* p_ccb */) {
  inc_func_call_count(__func__);
}
void l2cu_enqueue_ccb(tL2C_CCB* /* p_ccb */) { inc_func_call_count(__func__); }
void l2cu_no_dynamic_ccbs(tL2C_LCB* /* p_lcb */) {
  inc_func_call_count(__func__);
}
void l2cu_process_fixed_chnl_resp(tL2C_LCB* /* p_lcb */) {
  inc_func_call_count(__func__);
}
void l2cu_process_fixed_disc_cback(tL2C_LCB* /* p_lcb */) {
  inc_func_call_count(__func__);
}
void l2cu_process_our_cfg_req(tL2C_CCB* /* p_ccb */,
                              tL2CAP_CFG_INFO* /* p_cfg */) {
  inc_func_call_count(__func__);
}
void l2cu_process_our_cfg_rsp(tL2C_CCB* /* p_ccb */,
                              tL2CAP_CFG_INFO* /* p_cfg */) {
  inc_func_call_count(__func__);
}
void l2cu_process_peer_cfg_rsp(tL2C_CCB* /* p_ccb */,
                               tL2CAP_CFG_INFO* /* p_cfg */) {
  inc_func_call_count(__func__);
}
void l2cu_reject_ble_coc_connection(tL2C_LCB* /* p_lcb */, uint8_t /* rem_id */,
                                    uint16_t /* result */) {
  inc_func_call_count(__func__);
}
void l2cu_reject_ble_connection(tL2C_CCB* /* p_ccb */, uint8_t /* rem_id */,
                                uint16_t /* result */) {
  inc_func_call_count(__func__);
}
void l2cu_reject_connection(tL2C_LCB* /* p_lcb */, uint16_t /* remote_cid */,
                            uint8_t /* rem_id */, uint16_t /* result */) {
  inc_func_call_count(__func__);
}
void l2cu_reject_credit_based_conn_req(tL2C_LCB* /* p_lcb */,
                                       uint8_t /* rem_id */,
                                       uint8_t /* num_of_channels */,
                                       uint16_t /* result */) {
  inc_func_call_count(__func__);
}
void l2cu_release_ble_rcb(tL2C_RCB* /* p_rcb */) {
  inc_func_call_count(__func__);
}
void l2cu_release_ccb(tL2C_CCB* /* p_ccb */) { inc_func_call_count(__func__); }
void l2cu_release_lcb(tL2C_LCB* /* p_lcb */) { inc_func_call_count(__func__); }
void l2cu_release_rcb(tL2C_RCB* /* p_rcb */) { inc_func_call_count(__func__); }
void l2cu_resubmit_pending_sec_req(const RawAddress* /* p_bda */) {
  inc_func_call_count(__func__);
}
void l2cu_send_ble_reconfig_rsp(tL2C_LCB* /* p_lcb */, uint8_t /* rem_id */,
                                uint16_t /* result */) {
  inc_func_call_count(__func__);
}
void l2cu_send_credit_based_reconfig_req(tL2C_CCB* /* p_ccb */,
                                         tL2CAP_LE_CFG_INFO* /* p_cfg */) {
  inc_func_call_count(__func__);
}
void l2cu_send_peer_ble_credit_based_conn_req(tL2C_CCB* /* p_ccb */) {
  inc_func_call_count(__func__);
}
void l2cu_send_peer_ble_credit_based_conn_res(tL2C_CCB* /* p_ccb */,
                                              uint16_t /* result */) {
  inc_func_call_count(__func__);
}
void l2cu_send_peer_ble_credit_based_disconn_req(tL2C_CCB* /* p_ccb */) {
  inc_func_call_count(__func__);
}
void l2cu_send_peer_ble_flow_control_credit(tL2C_CCB* /* p_ccb */,
                                            uint16_t /* credit_value */) {
  inc_func_call_count(__func__);
}
void l2cu_send_peer_ble_par_req(tL2C_LCB* /* p_lcb */, uint16_t /* min_int */,
                                uint16_t /* max_int */, uint16_t /* latency */,
                                uint16_t /* timeout */) {
  inc_func_call_count(__func__);
}
void l2cu_send_peer_ble_par_rsp(tL2C_LCB* /* p_lcb */, uint16_t /* reason */,
                                uint8_t /* rem_id */) {
  inc_func_call_count(__func__);
}
void l2cu_send_peer_cmd_reject(tL2C_LCB* /* p_lcb */, uint16_t /* reason */,
                               uint8_t /* rem_id */, uint16_t /* p1 */,
                               uint16_t /* p2 */) {
  inc_func_call_count(__func__);
}
void l2cu_send_peer_config_rej(tL2C_CCB* /* p_ccb */, uint8_t* /* p_data */,
                               uint16_t /* data_len */,
                               uint16_t /* rej_len */) {
  inc_func_call_count(__func__);
}
void l2cu_send_peer_config_req(tL2C_CCB* /* p_ccb */,
                               tL2CAP_CFG_INFO* /* p_cfg */) {
  inc_func_call_count(__func__);
}
void l2cu_send_peer_config_rsp(tL2C_CCB* /* p_ccb */,
                               tL2CAP_CFG_INFO* /* p_cfg */) {
  inc_func_call_count(__func__);
}
void l2cu_send_peer_connect_req(tL2C_CCB* /* p_ccb */) {
  inc_func_call_count(__func__);
}
void l2cu_send_peer_connect_rsp(tL2C_CCB* /* p_ccb */, uint16_t /* result */,
                                uint16_t /* status */) {
  inc_func_call_count(__func__);
}
void l2cu_send_peer_credit_based_conn_req(tL2C_CCB* /* p_ccb */) {
  inc_func_call_count(__func__);
}
void l2cu_send_peer_credit_based_conn_res(
    tL2C_CCB* /* p_ccb */, std::vector<uint16_t>& /* accepted_cids */,
    uint16_t /* result */) {
  inc_func_call_count(__func__);
}
void l2cu_send_peer_disc_req(tL2C_CCB* /* p_ccb */) {
  inc_func_call_count(__func__);
}
void l2cu_send_peer_disc_rsp(tL2C_LCB* /* p_lcb */, uint8_t /* remote_id */,
                             uint16_t /* local_cid */,
                             uint16_t /* remote_cid */) {
  inc_func_call_count(__func__);
}
void l2cu_send_peer_echo_rsp(tL2C_LCB* /* p_lcb */, uint8_t /* signal_id */,
                             uint8_t* /* p_data */, uint16_t /* data_len */) {
  inc_func_call_count(__func__);
}
void l2cu_send_peer_info_req(tL2C_LCB* /* p_lcb */, uint16_t /* info_type */) {
  inc_func_call_count(__func__);
}
void l2cu_send_peer_info_rsp(tL2C_LCB* /* p_lcb */, uint8_t /* remote_id */,
                             uint16_t /* info_type */) {
  inc_func_call_count(__func__);
}
void l2cu_set_acl_hci_header(BT_HDR* /* p_buf */, tL2C_CCB* /* p_ccb */) {
  inc_func_call_count(__func__);
}
void l2cu_set_lcb_handle(struct t_l2c_linkcb& /* p_lcb */,
                         uint16_t /* handle */) {
  inc_func_call_count(__func__);
}
void l2cu_set_non_flushable_pbf(bool /* is_supported */) {
  inc_func_call_count(__func__);
}
void l2cu_update_lcb_4_bonding(const RawAddress& /* p_bd_addr */,
                               bool /* is_bonding */) {
  inc_func_call_count(__func__);
}
