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
 *   Functions generated:14
 */

#include <base/logging.h>

#include "stack/avrc/avrc_int.h"
#include "stack/include/avrc_api.h"
#include "stack/include/bt_hdr.h"
#include "test/common/mock_functions.h"
#include "types/raw_address.h"

bool avrcp_absolute_volume_is_enabled() {
  inc_func_call_count(__func__);
  return true;
}
uint16_t AVRC_Close(uint8_t /* handle */) {
  inc_func_call_count(__func__);
  return 0;
}
uint16_t AVRC_CloseBrowse(uint8_t /* handle */) {
  inc_func_call_count(__func__);
  return 0;
}
uint16_t AVRC_GetControlProfileVersion() {
  inc_func_call_count(__func__);
  return 0;
}
uint16_t AVRC_GetProfileVersion() {
  inc_func_call_count(__func__);
  return 0;
}
uint16_t AVRC_MsgReq(uint8_t /* handle */, uint8_t /* label */,
                     uint8_t /* ctype */, BT_HDR* /* p_pkt */,
                     bool /* is_new_avrcp */) {
  inc_func_call_count(__func__);
  return 0;
}
uint16_t AVRC_Open(uint8_t* /* p_handle */, tAVRC_CONN_CB* /* p_ccb */,
                   const RawAddress& /* peer_addr */) {
  inc_func_call_count(__func__);
  return 0;
}
uint16_t AVRC_OpenBrowse(uint8_t /* handle */, uint8_t /* conn_role */) {
  inc_func_call_count(__func__);
  return 0;
}
void AVRC_SaveControllerVersion(const RawAddress& /* bdaddr */,
                                uint16_t /* version */) {
  inc_func_call_count(__func__);
}

void AVRC_UpdateCcb(RawAddress* /* addr */, uint32_t /* company_id */) {
  inc_func_call_count(__func__);
}

uint16_t AVRC_PassCmd(uint8_t /* handle */, uint8_t /* label */,
                      tAVRC_MSG_PASS* /* p_msg */) {
  inc_func_call_count(__func__);
  return 0;
}
uint16_t AVRC_PassRsp(uint8_t /* handle */, uint8_t /* label */,
                      tAVRC_MSG_PASS* /* p_msg */) {
  inc_func_call_count(__func__);
  return 0;
}
void avrc_flush_cmd_q(uint8_t /* handle */) { inc_func_call_count(__func__); }
void avrc_process_timeout(void* /* data */) { inc_func_call_count(__func__); }
void avrc_send_next_vendor_cmd(uint8_t /* handle */) {
  inc_func_call_count(__func__);
}
void avrc_start_cmd_timer(uint8_t /* handle */, uint8_t /* label */,
                          uint8_t /* msg_mask */) {
  inc_func_call_count(__func__);
}
