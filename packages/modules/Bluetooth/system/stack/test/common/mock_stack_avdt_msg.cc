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

#include <vector>

#include "stack/avdt/avdt_int.h"
#include "stack/include/bt_hdr.h"
#include "test/common/mock_functions.h"

/*
 * TODO: This way of mocking is primitive.
 * Need to consider more sophisticated existing methods.
 */

static std::vector<uint8_t> _rsp_sig_ids{};

void avdt_msg_send_rsp(AvdtpCcb* p_ccb, uint8_t sig_id, tAVDT_MSG* p_params) {
  inc_func_call_count(__func__);
  _rsp_sig_ids.push_back(sig_id);
}

size_t mock_avdt_msg_send_rsp_get_count(void) {
  return _rsp_sig_ids.size();
}

void mock_avdt_msg_send_rsp_clear_history(void) {
  _rsp_sig_ids.clear();
}

uint8_t mock_avdt_msg_send_rsp_get_sig_id_at(size_t nth) {
  return _rsp_sig_ids[nth];
}

void avdt_msg_ind(AvdtpCcb* p_ccb, BT_HDR* p_buf) {
  inc_func_call_count(__func__);
}

void avdt_msg_send_rej(AvdtpCcb* p_ccb, uint8_t sig_id, tAVDT_MSG* p_params) {
  inc_func_call_count(__func__);
}

static std::vector<uint8_t> _cmd_sig_ids{};

void avdt_msg_send_cmd(AvdtpCcb* p_ccb, void* p_scb, uint8_t sig_id,
                       tAVDT_MSG* p_params) {
  inc_func_call_count(__func__);
  _cmd_sig_ids.push_back(sig_id);
}

size_t mock_avdt_msg_send_cmd_get_count(void) {
  return _cmd_sig_ids.size();
}

void mock_avdt_msg_send_cmd_clear_history(void) {
  _cmd_sig_ids.clear();
}

uint8_t mock_avdt_msg_send_cmd_get_sig_id_at(size_t nth) {
  return _cmd_sig_ids[nth];
}

bool avdt_msg_send(AvdtpCcb* p_ccb, BT_HDR* p_msg) {
  inc_func_call_count(__func__);
  return true;
}

const uint8_t avdt_msg_rej_2_evt[AVDT_CCB_NUM_ACTIONS] = {};
