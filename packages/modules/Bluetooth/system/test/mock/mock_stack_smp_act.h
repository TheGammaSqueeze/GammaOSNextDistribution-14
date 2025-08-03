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
 *   Functions generated:71
 *
 *  mockcify.pl ver 0.2
 */

#include <cstdint>
#include <functional>

// Original included files, if any
#include "stack/smp/smp_int.h"
#include "types/raw_address.h"

// Mocked compile conditionals, if any
namespace test {
namespace mock {
namespace stack_smp_act {

// Shared state between mocked functions and tests
// Name: smp_send_app_cback
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_send_app_cback {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_send_app_cback smp_send_app_cback;
// Name: smp_send_pair_fail
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_send_pair_fail {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_send_pair_fail smp_send_pair_fail;
// Name: smp_send_pair_req
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_send_pair_req {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_send_pair_req smp_send_pair_req;
// Name: smp_send_pair_rsp
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_send_pair_rsp {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_send_pair_rsp smp_send_pair_rsp;
// Name: smp_send_confirm
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_send_confirm {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_send_confirm smp_send_confirm;
// Name: smp_send_rand
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_send_rand {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_send_rand smp_send_rand;
// Name: smp_send_pair_public_key
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_send_pair_public_key {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_send_pair_public_key smp_send_pair_public_key;
// Name: smp_send_commitment
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_send_commitment {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_send_commitment smp_send_commitment;
// Name: smp_send_dhkey_check
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_send_dhkey_check {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_send_dhkey_check smp_send_dhkey_check;
// Name: smp_send_keypress_notification
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_send_keypress_notification {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_send_keypress_notification smp_send_keypress_notification;
// Name: smp_send_enc_info
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_send_enc_info {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_send_enc_info smp_send_enc_info;
// Name: smp_send_id_info
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_send_id_info {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_send_id_info smp_send_id_info;
// Name: smp_send_csrk_info
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_send_csrk_info {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_send_csrk_info smp_send_csrk_info;
// Name: smp_send_ltk_reply
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_send_ltk_reply {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_send_ltk_reply smp_send_ltk_reply;
// Name: smp_proc_sec_req
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_proc_sec_req {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_proc_sec_req smp_proc_sec_req;
// Name: smp_proc_sec_grant
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_proc_sec_grant {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_proc_sec_grant smp_proc_sec_grant;
// Name: smp_proc_pair_fail
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_proc_pair_fail {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_proc_pair_fail smp_proc_pair_fail;
// Name: smp_proc_pair_cmd
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_proc_pair_cmd {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_proc_pair_cmd smp_proc_pair_cmd;
// Name: smp_proc_confirm
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_proc_confirm {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_proc_confirm smp_proc_confirm;

// Name: smp_proc_rand
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_proc_rand {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_proc_rand smp_proc_rand;
// Name: smp_process_pairing_public_key
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_process_pairing_public_key {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_process_pairing_public_key smp_process_pairing_public_key;
// Name: smp_process_pairing_commitment
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_process_pairing_commitment {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_process_pairing_commitment smp_process_pairing_commitment;
// Name: smp_process_dhkey_check
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_process_dhkey_check {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_process_dhkey_check smp_process_dhkey_check;
// Name: smp_process_keypress_notification
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_process_keypress_notification {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_process_keypress_notification
    smp_process_keypress_notification;
// Name: smp_br_process_pairing_command
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_br_process_pairing_command {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_br_process_pairing_command smp_br_process_pairing_command;
// Name: smp_br_process_security_grant
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_br_process_security_grant {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_br_process_security_grant smp_br_process_security_grant;
// Name: smp_br_check_authorization_request
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_br_check_authorization_request {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_br_check_authorization_request
    smp_br_check_authorization_request;
// Name: smp_br_select_next_key
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_br_select_next_key {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_br_select_next_key smp_br_select_next_key;
// Name: smp_proc_enc_info
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_proc_enc_info {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_proc_enc_info smp_proc_enc_info;
// Name: smp_proc_central_id
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_proc_central_id {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_proc_central_id smp_proc_central_id;
// Name: smp_proc_id_info
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_proc_id_info {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_proc_id_info smp_proc_id_info;
// Name: smp_proc_id_addr
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_proc_id_addr {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_proc_id_addr smp_proc_id_addr;
// Name: smp_proc_srk_info
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_proc_srk_info {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_proc_srk_info smp_proc_srk_info;
// Name: smp_proc_compare
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_proc_compare {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_proc_compare smp_proc_compare;
// Name: smp_proc_sl_key
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_proc_sl_key {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_proc_sl_key smp_proc_sl_key;
// Name: smp_start_enc
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_start_enc {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_start_enc smp_start_enc;
// Name: smp_proc_discard
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_proc_discard {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_proc_discard smp_proc_discard;
// Name: smp_enc_cmpl
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_enc_cmpl {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_enc_cmpl smp_enc_cmpl;
// Name: smp_check_auth_req
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_check_auth_req {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_check_auth_req smp_check_auth_req;
// Name: smp_key_pick_key
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_key_pick_key {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_key_pick_key smp_key_pick_key;
// Name: smp_key_distribution
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_key_distribution {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_key_distribution smp_key_distribution;
// Name: smp_decide_association_model
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_decide_association_model {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_decide_association_model smp_decide_association_model;
// Name: smp_process_io_response
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_process_io_response {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_process_io_response smp_process_io_response;
// Name: smp_br_process_peripheral_keys_response
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_br_process_peripheral_keys_response {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_br_process_peripheral_keys_response
    smp_br_process_peripheral_keys_response;
// Name: smp_br_send_pair_response
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_br_send_pair_response {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_br_send_pair_response smp_br_send_pair_response;
// Name: smp_pairing_cmpl
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_pairing_cmpl {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_pairing_cmpl smp_pairing_cmpl;
// Name: smp_pair_terminate
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_pair_terminate {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_pair_terminate smp_pair_terminate;
// Name: smp_idle_terminate
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_idle_terminate {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_idle_terminate smp_idle_terminate;
// Name: smp_both_have_public_keys
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_both_have_public_keys {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_both_have_public_keys smp_both_have_public_keys;
// Name: smp_start_secure_connection_phase1
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_start_secure_connection_phase1 {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_start_secure_connection_phase1
    smp_start_secure_connection_phase1;
// Name: smp_process_local_nonce
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_process_local_nonce {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_process_local_nonce smp_process_local_nonce;
// Name: smp_process_peer_nonce
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_process_peer_nonce {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_process_peer_nonce smp_process_peer_nonce;
// Name: smp_match_dhkey_checks
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_match_dhkey_checks {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_match_dhkey_checks smp_match_dhkey_checks;
// Name: smp_move_to_secure_connections_phase2
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_move_to_secure_connections_phase2 {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_move_to_secure_connections_phase2
    smp_move_to_secure_connections_phase2;
// Name: smp_phase_2_dhkey_checks_are_present
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_phase_2_dhkey_checks_are_present {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_phase_2_dhkey_checks_are_present
    smp_phase_2_dhkey_checks_are_present;
// Name: smp_wait_for_both_public_keys
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_wait_for_both_public_keys {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_wait_for_both_public_keys smp_wait_for_both_public_keys;
// Name: smp_start_passkey_verification
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_start_passkey_verification {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_start_passkey_verification smp_start_passkey_verification;
// Name: smp_process_secure_connection_oob_data
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_process_secure_connection_oob_data {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_process_secure_connection_oob_data
    smp_process_secure_connection_oob_data;
// Name: smp_set_local_oob_keys
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_set_local_oob_keys {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_set_local_oob_keys smp_set_local_oob_keys;
// Name: smp_set_local_oob_random_commitment
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_set_local_oob_random_commitment {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_set_local_oob_random_commitment
    smp_set_local_oob_random_commitment;
// Name: smp_link_encrypted
// Params: const RawAddress& bda, uint8_t encr_enable
// Returns: void
struct smp_link_encrypted {
  std::function<void(const RawAddress& bda, uint8_t encr_enable)> body{
      [](const RawAddress& /* bda */, uint8_t /* encr_enable */) {}};
  void operator()(const RawAddress& bda, uint8_t encr_enable) {
    body(bda, encr_enable);
  };
};
extern struct smp_link_encrypted smp_link_encrypted;
// Name: smp_cancel_start_encryption_attempt
// Params:
// Returns: void
struct smp_cancel_start_encryption_attempt {
  std::function<void()> body{[]() {}};
  void operator()() { body(); };
};
extern struct smp_cancel_start_encryption_attempt
    smp_cancel_start_encryption_attempt;
// Name: smp_proc_ltk_request
// Params: const RawAddress& bda
// Returns: bool
struct smp_proc_ltk_request {
  std::function<bool(const RawAddress& bda)> body{
      [](const RawAddress& /* bda */) { return false; }};
  bool operator()(const RawAddress& bda) { return body(bda); };
};
extern struct smp_proc_ltk_request smp_proc_ltk_request;
// Name: smp_process_secure_connection_long_term_key
// Params: void
// Returns: void
struct smp_process_secure_connection_long_term_key {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct smp_process_secure_connection_long_term_key
    smp_process_secure_connection_long_term_key;
// Name: smp_set_derive_link_key
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_set_derive_link_key {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_set_derive_link_key smp_set_derive_link_key;
// Name: smp_derive_link_key_from_long_term_key
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_derive_link_key_from_long_term_key {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_derive_link_key_from_long_term_key
    smp_derive_link_key_from_long_term_key;
// Name: smp_br_process_link_key
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_br_process_link_key {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_br_process_link_key smp_br_process_link_key;
// Name: smp_br_pairing_complete
// Params: tSMP_CB* p_cb, tSMP_INT_DATA* p_data
// Returns: void
struct smp_br_pairing_complete {
  std::function<void(tSMP_CB* p_cb, tSMP_INT_DATA* p_data)> body{
      [](tSMP_CB* /* p_cb */, tSMP_INT_DATA* /* p_data */) {}};
  void operator()(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) { body(p_cb, p_data); };
};
extern struct smp_br_pairing_complete smp_br_pairing_complete;

}  // namespace stack_smp_act
}  // namespace mock
}  // namespace test

// END mockcify generation
