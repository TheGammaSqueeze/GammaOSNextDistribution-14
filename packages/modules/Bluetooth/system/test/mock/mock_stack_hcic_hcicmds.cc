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
 *   Functions generated:75
 *
 *  mockcify.pl ver 0.3.0
 */
// Mock include file to share data between tests and mock
#include "test/mock/mock_stack_hcic_hcicmds.h"

#include <cstdint>

#include "test/common/mock_functions.h"

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace stack_hcic_hcicmds {

// Function state capture and return values, if needed
struct btsnd_hcic_accept_conn btsnd_hcic_accept_conn;
struct btsnd_hcic_accept_esco_conn btsnd_hcic_accept_esco_conn;
struct btsnd_hcic_add_SCO_conn btsnd_hcic_add_SCO_conn;
struct btsnd_hcic_auth_request btsnd_hcic_auth_request;
struct btsnd_hcic_change_name btsnd_hcic_change_name;
struct btsnd_hcic_create_conn_cancel btsnd_hcic_create_conn_cancel;
struct btsnd_hcic_delete_stored_key btsnd_hcic_delete_stored_key;
struct btsnd_hcic_enable_test_mode btsnd_hcic_enable_test_mode;
struct btsnd_hcic_enhanced_accept_synchronous_connection
    btsnd_hcic_enhanced_accept_synchronous_connection;
struct btsnd_hcic_enhanced_flush btsnd_hcic_enhanced_flush;
struct btsnd_hcic_enhanced_set_up_synchronous_connection
    btsnd_hcic_enhanced_set_up_synchronous_connection;
struct btsnd_hcic_exit_park_mode btsnd_hcic_exit_park_mode;
struct btsnd_hcic_exit_sniff_mode btsnd_hcic_exit_sniff_mode;
struct btsnd_hcic_hold_mode btsnd_hcic_hold_mode;
struct btsnd_hcic_io_cap_req_neg_reply btsnd_hcic_io_cap_req_neg_reply;
struct btsnd_hcic_io_cap_req_reply btsnd_hcic_io_cap_req_reply;
struct btsnd_hcic_link_key_neg_reply btsnd_hcic_link_key_neg_reply;
struct btsnd_hcic_link_key_req_reply btsnd_hcic_link_key_req_reply;
struct btsnd_hcic_park_mode btsnd_hcic_park_mode;
struct btsnd_hcic_pin_code_neg_reply btsnd_hcic_pin_code_neg_reply;
struct btsnd_hcic_pin_code_req_reply btsnd_hcic_pin_code_req_reply;
struct btsnd_hcic_read_encryption_key_size btsnd_hcic_read_encryption_key_size;
struct btsnd_hcic_read_failed_contact_counter
    btsnd_hcic_read_failed_contact_counter;
struct btsnd_hcic_read_local_oob_data btsnd_hcic_read_local_oob_data;
struct btsnd_hcic_read_name btsnd_hcic_read_name;
struct btsnd_hcic_read_rmt_clk_offset btsnd_hcic_read_rmt_clk_offset;
struct btsnd_hcic_read_rssi btsnd_hcic_read_rssi;
struct btsnd_hcic_read_tx_power btsnd_hcic_read_tx_power;
struct btsnd_hcic_reject_conn btsnd_hcic_reject_conn;
struct btsnd_hcic_reject_esco_conn btsnd_hcic_reject_esco_conn;
struct btsnd_hcic_rem_oob_neg_reply btsnd_hcic_rem_oob_neg_reply;
struct btsnd_hcic_rem_oob_reply btsnd_hcic_rem_oob_reply;
struct btsnd_hcic_rmt_ext_features btsnd_hcic_rmt_ext_features;
struct btsnd_hcic_rmt_name_req btsnd_hcic_rmt_name_req;
struct btsnd_hcic_rmt_name_req_cancel btsnd_hcic_rmt_name_req_cancel;
struct btsnd_hcic_rmt_ver_req btsnd_hcic_rmt_ver_req;
struct btsnd_hcic_set_conn_encrypt btsnd_hcic_set_conn_encrypt;
struct btsnd_hcic_set_event_filter btsnd_hcic_set_event_filter;
struct btsnd_hcic_setup_esco_conn btsnd_hcic_setup_esco_conn;
struct btsnd_hcic_sniff_mode btsnd_hcic_sniff_mode;
struct btsnd_hcic_sniff_sub_rate btsnd_hcic_sniff_sub_rate;
struct btsnd_hcic_user_conf_reply btsnd_hcic_user_conf_reply;
struct btsnd_hcic_user_passkey_neg_reply btsnd_hcic_user_passkey_neg_reply;
struct btsnd_hcic_user_passkey_reply btsnd_hcic_user_passkey_reply;
struct btsnd_hcic_vendor_spec_cmd btsnd_hcic_vendor_spec_cmd;
struct btsnd_hcic_write_auth_enable btsnd_hcic_write_auth_enable;
struct btsnd_hcic_write_auto_flush_tout btsnd_hcic_write_auto_flush_tout;
struct btsnd_hcic_write_cur_iac_lap btsnd_hcic_write_cur_iac_lap;
struct btsnd_hcic_write_def_policy_set btsnd_hcic_write_def_policy_set;
struct btsnd_hcic_write_dev_class btsnd_hcic_write_dev_class;
struct btsnd_hcic_write_ext_inquiry_response
    btsnd_hcic_write_ext_inquiry_response;
struct btsnd_hcic_write_inqscan_cfg btsnd_hcic_write_inqscan_cfg;
struct btsnd_hcic_write_inqscan_type btsnd_hcic_write_inqscan_type;
struct btsnd_hcic_write_inquiry_mode btsnd_hcic_write_inquiry_mode;
struct btsnd_hcic_write_link_super_tout btsnd_hcic_write_link_super_tout;
struct btsnd_hcic_write_page_tout btsnd_hcic_write_page_tout;
struct btsnd_hcic_write_pagescan_cfg btsnd_hcic_write_pagescan_cfg;
struct btsnd_hcic_write_pagescan_type btsnd_hcic_write_pagescan_type;
struct btsnd_hcic_write_pin_type btsnd_hcic_write_pin_type;
struct btsnd_hcic_write_policy_set btsnd_hcic_write_policy_set;
struct btsnd_hcic_write_scan_enable btsnd_hcic_write_scan_enable;
struct btsnd_hcic_write_voice_settings btsnd_hcic_write_voice_settings;

}  // namespace stack_hcic_hcicmds
}  // namespace mock
}  // namespace test

// Mocked functions, if any
void btsnd_hcic_accept_conn(const RawAddress& dest, uint8_t role) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_accept_conn(dest, role);
}
void btsnd_hcic_accept_esco_conn(const RawAddress& bd_addr,
                                 uint32_t transmit_bandwidth,
                                 uint32_t receive_bandwidth,
                                 uint16_t max_latency, uint16_t content_fmt,
                                 uint8_t retrans_effort,
                                 uint16_t packet_types) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_accept_esco_conn(
      bd_addr, transmit_bandwidth, receive_bandwidth, max_latency, content_fmt,
      retrans_effort, packet_types);
}
void btsnd_hcic_add_SCO_conn(uint16_t handle, uint16_t packet_types) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_add_SCO_conn(handle, packet_types);
}
void btsnd_hcic_auth_request(uint16_t handle) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_auth_request(handle);
}
void btsnd_hcic_change_name(BD_NAME name) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_change_name(name);
}
void btsnd_hcic_create_conn_cancel(const RawAddress& dest) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_create_conn_cancel(dest);
}
void btsnd_hcic_delete_stored_key(const RawAddress& bd_addr,
                                  bool delete_all_flag) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_delete_stored_key(bd_addr,
                                                               delete_all_flag);
}
void btsnd_hcic_enable_test_mode(void) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_enable_test_mode();
}
void btsnd_hcic_enhanced_accept_synchronous_connection(
    const RawAddress& bd_addr, enh_esco_params_t* p_params) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::
      btsnd_hcic_enhanced_accept_synchronous_connection(bd_addr, p_params);
}
void btsnd_hcic_enhanced_flush(uint16_t handle, uint8_t packet_type) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_enhanced_flush(handle,
                                                            packet_type);
}
void btsnd_hcic_enhanced_set_up_synchronous_connection(
    uint16_t conn_handle, enh_esco_params_t* p_params) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::
      btsnd_hcic_enhanced_set_up_synchronous_connection(conn_handle, p_params);
}
void btsnd_hcic_exit_park_mode(uint16_t handle) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_exit_park_mode(handle);
}
void btsnd_hcic_exit_sniff_mode(uint16_t handle) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_exit_sniff_mode(handle);
}
void btsnd_hcic_hold_mode(uint16_t handle, uint16_t max_hold_period,
                          uint16_t min_hold_period) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_hold_mode(handle, max_hold_period,
                                                       min_hold_period);
}
void btsnd_hcic_io_cap_req_neg_reply(const RawAddress& bd_addr,
                                     uint8_t err_code) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_io_cap_req_neg_reply(bd_addr,
                                                                  err_code);
}
void btsnd_hcic_io_cap_req_reply(const RawAddress& bd_addr, uint8_t capability,
                                 uint8_t oob_present, uint8_t auth_req) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_io_cap_req_reply(
      bd_addr, capability, oob_present, auth_req);
}
void btsnd_hcic_link_key_neg_reply(const RawAddress& bd_addr) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_link_key_neg_reply(bd_addr);
}
void btsnd_hcic_link_key_req_reply(const RawAddress& bd_addr,
                                   const LinkKey& link_key) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_link_key_req_reply(bd_addr,
                                                                link_key);
}
void btsnd_hcic_park_mode(uint16_t handle, uint16_t beacon_max_interval,
                          uint16_t beacon_min_interval) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_park_mode(
      handle, beacon_max_interval, beacon_min_interval);
}
void btsnd_hcic_pin_code_neg_reply(const RawAddress& bd_addr) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_pin_code_neg_reply(bd_addr);
}
void btsnd_hcic_pin_code_req_reply(const RawAddress& bd_addr,
                                   uint8_t pin_code_len, PIN_CODE pin_code) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_pin_code_req_reply(
      bd_addr, pin_code_len, pin_code);
}
void btsnd_hcic_read_encryption_key_size(uint16_t handle, ReadEncKeySizeCb cb) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_read_encryption_key_size(
      handle, std::move(cb));
}
void btsnd_hcic_read_failed_contact_counter(uint16_t handle) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_read_failed_contact_counter(
      handle);
}
void btsnd_hcic_read_local_oob_data(void) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_read_local_oob_data();
}
void btsnd_hcic_read_name(void) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_read_name();
}
void btsnd_hcic_read_rmt_clk_offset(uint16_t handle) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_read_rmt_clk_offset(handle);
}
void btsnd_hcic_read_rssi(uint16_t handle) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_read_rssi(handle);
}
void btsnd_hcic_read_tx_power(uint16_t handle, uint8_t type) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_read_tx_power(handle, type);
}
void btsnd_hcic_reject_conn(const RawAddress& dest, uint8_t reason) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_reject_conn(dest, reason);
}
void btsnd_hcic_reject_esco_conn(const RawAddress& bd_addr, uint8_t reason) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_reject_esco_conn(bd_addr, reason);
}
void btsnd_hcic_rem_oob_neg_reply(const RawAddress& bd_addr) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_rem_oob_neg_reply(bd_addr);
}
void btsnd_hcic_rem_oob_reply(const RawAddress& bd_addr, const Octet16& c,
                              const Octet16& r) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_rem_oob_reply(bd_addr, c, r);
}
void btsnd_hcic_rmt_ext_features(uint16_t handle, uint8_t page_num) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_rmt_ext_features(handle, page_num);
}
void btsnd_hcic_rmt_name_req(const RawAddress& bd_addr,
                             uint8_t page_scan_rep_mode, uint8_t page_scan_mode,
                             uint16_t clock_offset) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_rmt_name_req(
      bd_addr, page_scan_rep_mode, page_scan_mode, clock_offset);
}
void btsnd_hcic_rmt_name_req_cancel(const RawAddress& bd_addr) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_rmt_name_req_cancel(bd_addr);
}
void btsnd_hcic_rmt_ver_req(uint16_t handle) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_rmt_ver_req(handle);
}
void btsnd_hcic_set_conn_encrypt(uint16_t handle, bool enable) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_set_conn_encrypt(handle, enable);
}
void btsnd_hcic_set_event_filter(uint8_t filt_type, uint8_t filt_cond_type,
                                 uint8_t* filt_cond, uint8_t filt_cond_len) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_set_event_filter(
      filt_type, filt_cond_type, filt_cond, filt_cond_len);
}
void btsnd_hcic_setup_esco_conn(uint16_t handle, uint32_t transmit_bandwidth,
                                uint32_t receive_bandwidth,
                                uint16_t max_latency, uint16_t voice,
                                uint8_t retrans_effort, uint16_t packet_types) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_setup_esco_conn(
      handle, transmit_bandwidth, receive_bandwidth, max_latency, voice,
      retrans_effort, packet_types);
}
void btsnd_hcic_sniff_mode(uint16_t handle, uint16_t max_sniff_period,
                           uint16_t min_sniff_period, uint16_t sniff_attempt,
                           uint16_t sniff_timeout) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_sniff_mode(
      handle, max_sniff_period, min_sniff_period, sniff_attempt, sniff_timeout);
}
void btsnd_hcic_sniff_sub_rate(uint16_t handle, uint16_t max_lat,
                               uint16_t min_remote_lat,
                               uint16_t min_local_lat) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_sniff_sub_rate(
      handle, max_lat, min_remote_lat, min_local_lat);
}
void btsnd_hcic_user_conf_reply(const RawAddress& bd_addr, bool is_yes) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_user_conf_reply(bd_addr, is_yes);
}
void btsnd_hcic_user_passkey_neg_reply(const RawAddress& bd_addr) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_user_passkey_neg_reply(bd_addr);
}
void btsnd_hcic_user_passkey_reply(const RawAddress& bd_addr, uint32_t value) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_user_passkey_reply(bd_addr, value);
}
void btsnd_hcic_vendor_spec_cmd(uint16_t opcode, uint8_t len, uint8_t* p_data,
                                tBTM_VSC_CMPL_CB* p_cmd_cplt_cback) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_vendor_spec_cmd(
      opcode, len, p_data, p_cmd_cplt_cback);
}
void btsnd_hcic_write_auth_enable(uint8_t flag) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_write_auth_enable(flag);
}
void btsnd_hcic_write_auto_flush_tout(uint16_t handle, uint16_t tout) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_write_auto_flush_tout(handle,
                                                                   tout);
}
void btsnd_hcic_write_cur_iac_lap(uint8_t num_cur_iac, LAP* const iac_lap) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_write_cur_iac_lap(num_cur_iac,
                                                               iac_lap);
}
void btsnd_hcic_write_def_policy_set(uint16_t settings) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_write_def_policy_set(settings);
}
void btsnd_hcic_write_dev_class(DEV_CLASS dev_class) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_write_dev_class(dev_class);
}
void btsnd_hcic_write_ext_inquiry_response(void* buffer, uint8_t fec_req) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_write_ext_inquiry_response(
      buffer, fec_req);
}
void btsnd_hcic_write_inqscan_cfg(uint16_t interval, uint16_t window) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_write_inqscan_cfg(interval,
                                                               window);
}
void btsnd_hcic_write_inqscan_type(uint8_t type) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_write_inqscan_type(type);
}
void btsnd_hcic_write_inquiry_mode(uint8_t mode) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_write_inquiry_mode(mode);
}
void btsnd_hcic_write_link_super_tout(uint16_t handle, uint16_t timeout) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_write_link_super_tout(handle,
                                                                   timeout);
}
void btsnd_hcic_write_page_tout(uint16_t timeout) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_write_page_tout(timeout);
}
void btsnd_hcic_write_pagescan_cfg(uint16_t interval, uint16_t window) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_write_pagescan_cfg(interval,
                                                                window);
}
void btsnd_hcic_write_pagescan_type(uint8_t type) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_write_pagescan_type(type);
}
void btsnd_hcic_write_pin_type(uint8_t type) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_write_pin_type(type);
}
void btsnd_hcic_write_policy_set(uint16_t handle, uint16_t settings) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_write_policy_set(handle, settings);
}
void btsnd_hcic_write_scan_enable(uint8_t flag) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_write_scan_enable(flag);
}
void btsnd_hcic_write_voice_settings(uint16_t flags) {
  inc_func_call_count(__func__);
  test::mock::stack_hcic_hcicmds::btsnd_hcic_write_voice_settings(flags);
}
// Mocked functions complete
// END mockcify generation
