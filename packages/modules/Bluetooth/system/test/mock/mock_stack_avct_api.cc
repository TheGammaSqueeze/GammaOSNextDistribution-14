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
 *   Functions generated:9
 */

#include "avct_api.h"
#include "stack/include/bt_hdr.h"
#include "test/common/mock_functions.h"
#include "types/raw_address.h"

uint16_t AVCT_CreateBrowse(uint8_t /* handle */, uint8_t /* role */) {
  inc_func_call_count(__func__);
  return 0;
}
uint16_t AVCT_CreateConn(uint8_t* /* p_handle */, tAVCT_CC* /* p_cc */,
                         const RawAddress& /* peer_addr */) {
  inc_func_call_count(__func__);
  return 0;
}
uint16_t AVCT_GetBrowseMtu(uint8_t /* handle */) {
  inc_func_call_count(__func__);
  return 0;
}
uint16_t AVCT_GetPeerMtu(uint8_t /* handle */) {
  inc_func_call_count(__func__);
  return 0;
}
uint16_t AVCT_MsgReq(uint8_t /* handle */, uint8_t /* label */,
                     uint8_t /* cr */, BT_HDR* /* p_msg */) {
  inc_func_call_count(__func__);
  return 0;
}
uint16_t AVCT_RemoveBrowse(uint8_t /* handle */) {
  inc_func_call_count(__func__);
  return 0;
}
uint16_t AVCT_RemoveConn(uint8_t /* handle */) {
  inc_func_call_count(__func__);
  return 0;
}
void AVCT_Deregister(void) { inc_func_call_count(__func__); }
void AVCT_Register() { inc_func_call_count(__func__); }
