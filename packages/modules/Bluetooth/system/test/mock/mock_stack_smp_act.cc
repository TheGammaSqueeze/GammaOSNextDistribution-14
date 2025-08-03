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
// Mock include file to share data between tests and mock
#include "test/mock/mock_stack_smp_act.h"

#include <cstdint>

// Original included files, if any
#include "test/common/mock_functions.h"
#include "types/raw_address.h"

// Mocked compile conditionals, if any
// Mocked internal structures, if any

namespace test {
namespace mock {
namespace stack_smp_act {

// Function state capture and return values, if needed
struct smp_send_app_cback smp_send_app_cback;
struct smp_send_pair_fail smp_send_pair_fail;
struct smp_send_pair_req smp_send_pair_req;
struct smp_send_pair_rsp smp_send_pair_rsp;
struct smp_send_confirm smp_send_confirm;
struct smp_send_rand smp_send_rand;
struct smp_send_pair_public_key smp_send_pair_public_key;
struct smp_send_commitment smp_send_commitment;
struct smp_send_dhkey_check smp_send_dhkey_check;
struct smp_send_keypress_notification smp_send_keypress_notification;
struct smp_send_enc_info smp_send_enc_info;
struct smp_send_id_info smp_send_id_info;
struct smp_send_csrk_info smp_send_csrk_info;
struct smp_send_ltk_reply smp_send_ltk_reply;
struct smp_proc_sec_req smp_proc_sec_req;
struct smp_proc_sec_grant smp_proc_sec_grant;
struct smp_proc_pair_fail smp_proc_pair_fail;
struct smp_proc_pair_cmd smp_proc_pair_cmd;
struct smp_proc_confirm smp_proc_confirm;
struct smp_proc_rand smp_proc_rand;
struct smp_process_pairing_public_key smp_process_pairing_public_key;
struct smp_process_pairing_commitment smp_process_pairing_commitment;
struct smp_process_dhkey_check smp_process_dhkey_check;
struct smp_process_keypress_notification smp_process_keypress_notification;
struct smp_br_process_pairing_command smp_br_process_pairing_command;
struct smp_br_process_security_grant smp_br_process_security_grant;
struct smp_br_check_authorization_request smp_br_check_authorization_request;
struct smp_br_select_next_key smp_br_select_next_key;
struct smp_proc_enc_info smp_proc_enc_info;
struct smp_proc_central_id smp_proc_central_id;
struct smp_proc_id_info smp_proc_id_info;
struct smp_proc_id_addr smp_proc_id_addr;
struct smp_proc_srk_info smp_proc_srk_info;
struct smp_proc_compare smp_proc_compare;
struct smp_proc_sl_key smp_proc_sl_key;
struct smp_start_enc smp_start_enc;
struct smp_proc_discard smp_proc_discard;
struct smp_enc_cmpl smp_enc_cmpl;
struct smp_check_auth_req smp_check_auth_req;
struct smp_key_pick_key smp_key_pick_key;
struct smp_key_distribution smp_key_distribution;
struct smp_decide_association_model smp_decide_association_model;
struct smp_process_io_response smp_process_io_response;
struct smp_br_process_peripheral_keys_response
    smp_br_process_peripheral_keys_response;
struct smp_br_send_pair_response smp_br_send_pair_response;
struct smp_pairing_cmpl smp_pairing_cmpl;
struct smp_pair_terminate smp_pair_terminate;
struct smp_idle_terminate smp_idle_terminate;
struct smp_both_have_public_keys smp_both_have_public_keys;
struct smp_start_secure_connection_phase1 smp_start_secure_connection_phase1;
struct smp_process_local_nonce smp_process_local_nonce;
struct smp_process_peer_nonce smp_process_peer_nonce;
struct smp_match_dhkey_checks smp_match_dhkey_checks;
struct smp_move_to_secure_connections_phase2
    smp_move_to_secure_connections_phase2;
struct smp_phase_2_dhkey_checks_are_present
    smp_phase_2_dhkey_checks_are_present;
struct smp_wait_for_both_public_keys smp_wait_for_both_public_keys;
struct smp_start_passkey_verification smp_start_passkey_verification;
struct smp_process_secure_connection_oob_data
    smp_process_secure_connection_oob_data;
struct smp_set_local_oob_keys smp_set_local_oob_keys;
struct smp_set_local_oob_random_commitment smp_set_local_oob_random_commitment;
struct smp_link_encrypted smp_link_encrypted;
struct smp_cancel_start_encryption_attempt smp_cancel_start_encryption_attempt;
struct smp_proc_ltk_request smp_proc_ltk_request;
struct smp_process_secure_connection_long_term_key
    smp_process_secure_connection_long_term_key;
struct smp_set_derive_link_key smp_set_derive_link_key;
struct smp_derive_link_key_from_long_term_key
    smp_derive_link_key_from_long_term_key;
struct smp_br_process_link_key smp_br_process_link_key;
struct smp_br_pairing_complete smp_br_pairing_complete;

}  // namespace stack_smp_act
}  // namespace mock
}  // namespace test

// Mocked functions, if any
void smp_send_app_cback(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_send_app_cback(p_cb, p_data);
}
void smp_send_pair_fail(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_send_pair_fail(p_cb, p_data);
}
void smp_send_pair_req(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_send_pair_req(p_cb, p_data);
}
void smp_send_pair_rsp(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_send_pair_rsp(p_cb, p_data);
}
void smp_send_confirm(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_send_confirm(p_cb, p_data);
}
void smp_send_rand(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_send_rand(p_cb, p_data);
}
void smp_send_pair_public_key(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_send_pair_public_key(p_cb, p_data);
}
void smp_send_commitment(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_send_commitment(p_cb, p_data);
}
void smp_send_dhkey_check(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_send_dhkey_check(p_cb, p_data);
}
void smp_send_keypress_notification(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_send_keypress_notification(p_cb, p_data);
}
void smp_send_enc_info(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_send_enc_info(p_cb, p_data);
}
void smp_send_id_info(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_send_id_info(p_cb, p_data);
}
void smp_send_csrk_info(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_send_csrk_info(p_cb, p_data);
}
void smp_send_ltk_reply(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_send_ltk_reply(p_cb, p_data);
}
void smp_proc_sec_req(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_proc_sec_req(p_cb, p_data);
}
void smp_proc_sec_grant(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_proc_sec_grant(p_cb, p_data);
}
void smp_proc_pair_fail(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_proc_pair_fail(p_cb, p_data);
}
void smp_proc_pair_cmd(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_proc_pair_cmd(p_cb, p_data);
}
void smp_proc_confirm(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_proc_confirm(p_cb, p_data);
}
void smp_proc_rand(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_proc_rand(p_cb, p_data);
}
void smp_process_pairing_public_key(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_process_pairing_public_key(p_cb, p_data);
}
void smp_process_pairing_commitment(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_process_pairing_commitment(p_cb, p_data);
}
void smp_process_dhkey_check(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_process_dhkey_check(p_cb, p_data);
}
void smp_process_keypress_notification(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_process_keypress_notification(p_cb, p_data);
}
void smp_br_process_pairing_command(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_br_process_pairing_command(p_cb, p_data);
}
void smp_br_process_security_grant(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_br_process_security_grant(p_cb, p_data);
}
void smp_br_check_authorization_request(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_br_check_authorization_request(p_cb, p_data);
}
void smp_br_select_next_key(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_br_select_next_key(p_cb, p_data);
}
void smp_proc_enc_info(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_proc_enc_info(p_cb, p_data);
}
void smp_proc_central_id(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_proc_central_id(p_cb, p_data);
}
void smp_proc_id_info(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_proc_id_info(p_cb, p_data);
}
void smp_proc_id_addr(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_proc_id_addr(p_cb, p_data);
}
void smp_proc_srk_info(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_proc_srk_info(p_cb, p_data);
}
void smp_proc_compare(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_proc_compare(p_cb, p_data);
}
void smp_proc_sl_key(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_proc_sl_key(p_cb, p_data);
}
void smp_start_enc(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_start_enc(p_cb, p_data);
}
void smp_proc_discard(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_proc_discard(p_cb, p_data);
}
void smp_enc_cmpl(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_enc_cmpl(p_cb, p_data);
}
void smp_check_auth_req(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_check_auth_req(p_cb, p_data);
}
void smp_key_pick_key(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_key_pick_key(p_cb, p_data);
}
void smp_key_distribution(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_key_distribution(p_cb, p_data);
}
void smp_decide_association_model(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_decide_association_model(p_cb, p_data);
}
void smp_process_io_response(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_process_io_response(p_cb, p_data);
}
void smp_br_process_peripheral_keys_response(tSMP_CB* p_cb,
                                             tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_br_process_peripheral_keys_response(p_cb,
                                                                     p_data);
}
void smp_br_send_pair_response(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_br_send_pair_response(p_cb, p_data);
}
void smp_pairing_cmpl(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_pairing_cmpl(p_cb, p_data);
}
void smp_pair_terminate(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_pair_terminate(p_cb, p_data);
}
void smp_idle_terminate(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_idle_terminate(p_cb, p_data);
}
void smp_both_have_public_keys(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_both_have_public_keys(p_cb, p_data);
}
void smp_start_secure_connection_phase1(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_start_secure_connection_phase1(p_cb, p_data);
}
void smp_process_local_nonce(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_process_local_nonce(p_cb, p_data);
}
void smp_process_peer_nonce(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_process_peer_nonce(p_cb, p_data);
}
void smp_match_dhkey_checks(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_match_dhkey_checks(p_cb, p_data);
}
void smp_move_to_secure_connections_phase2(tSMP_CB* p_cb,
                                           tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_move_to_secure_connections_phase2(p_cb,
                                                                   p_data);
}
void smp_phase_2_dhkey_checks_are_present(tSMP_CB* p_cb,
                                          tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_phase_2_dhkey_checks_are_present(p_cb, p_data);
}
void smp_wait_for_both_public_keys(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_wait_for_both_public_keys(p_cb, p_data);
}
void smp_start_passkey_verification(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_start_passkey_verification(p_cb, p_data);
}
void smp_process_secure_connection_oob_data(tSMP_CB* p_cb,
                                            tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_process_secure_connection_oob_data(p_cb,
                                                                    p_data);
}
void smp_set_local_oob_keys(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_set_local_oob_keys(p_cb, p_data);
}
void smp_set_local_oob_random_commitment(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_set_local_oob_random_commitment(p_cb, p_data);
}
void smp_link_encrypted(const RawAddress& bda, uint8_t encr_enable) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_link_encrypted(bda, encr_enable);
}
void smp_cancel_start_encryption_attempt() {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_cancel_start_encryption_attempt();
}
bool smp_proc_ltk_request(const RawAddress& bda) {
  inc_func_call_count(__func__);
  return test::mock::stack_smp_act::smp_proc_ltk_request(bda);
}
void smp_process_secure_connection_long_term_key(void) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_process_secure_connection_long_term_key();
}
void smp_set_derive_link_key(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_set_derive_link_key(p_cb, p_data);
}
void smp_derive_link_key_from_long_term_key(tSMP_CB* p_cb,
                                            tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_derive_link_key_from_long_term_key(p_cb,
                                                                    p_data);
}
void smp_br_process_link_key(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_br_process_link_key(p_cb, p_data);
}
void smp_br_pairing_complete(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  inc_func_call_count(__func__);
  test::mock::stack_smp_act::smp_br_pairing_complete(p_cb, p_data);
}

// END mockcify generation
