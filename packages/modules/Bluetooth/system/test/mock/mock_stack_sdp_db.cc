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

#include <stdio.h>

#include "stack/sdp/sdpint.h"
#include "test/common/mock_functions.h"

bool SDP_AddAdditionProtoLists(uint32_t /* handle */, uint16_t /* num_elem */,
                               tSDP_PROTO_LIST_ELEM* /* p_proto_list */) {
  inc_func_call_count(__func__);
  return false;
}
bool SDP_AddAttribute(uint32_t /* handle */, uint16_t /* attr_id */,
                      uint8_t /* attr_type */, uint32_t /* attr_len */,
                      uint8_t* /* p_val */) {
  inc_func_call_count(__func__);
  return false;
}
bool SDP_AddLanguageBaseAttrIDList(uint32_t /* handle */, uint16_t /* lang */,
                                   uint16_t /* char_enc */,
                                   uint16_t /* base_id */) {
  inc_func_call_count(__func__);
  return false;
}
bool SDP_AddProfileDescriptorList(uint32_t /* handle */,
                                  uint16_t /* profile_uuid */,
                                  uint16_t /* version */) {
  inc_func_call_count(__func__);
  return false;
}
bool SDP_AddProtocolList(uint32_t /* handle */, uint16_t /* num_elem */,
                         tSDP_PROTOCOL_ELEM* /* p_elem_list */) {
  inc_func_call_count(__func__);
  return false;
}
bool SDP_AddSequence(uint32_t /* handle */, uint16_t /* attr_id */,
                     uint16_t /* num_elem */, uint8_t /* type */[],
                     uint8_t /* len */[], uint8_t* /* p_val */[]) {
  inc_func_call_count(__func__);
  return false;
}
bool SDP_AddServiceClassIdList(uint32_t /* handle */,
                               uint16_t /* num_services */,
                               uint16_t* /* p_service_uuids */) {
  inc_func_call_count(__func__);
  return false;
}
bool SDP_AddUuidSequence(uint32_t /* handle */, uint16_t /* attr_id */,
                         uint16_t /* num_uuids */, uint16_t* /* p_uuids */) {
  inc_func_call_count(__func__);
  return false;
}
bool SDP_DeleteRecord(uint32_t /* handle */) {
  inc_func_call_count(__func__);
  return false;
}
tSDP_ATTRIBUTE* sdp_db_find_attr_in_rec(tSDP_RECORD* /* p_rec */,
                                        uint16_t /* start_attr */,
                                        uint16_t /* end_attr */) {
  inc_func_call_count(__func__);
  return nullptr;
}
tSDP_RECORD* sdp_db_find_record(uint32_t /* handle */) {
  inc_func_call_count(__func__);
  return nullptr;
}
const tSDP_RECORD* sdp_db_service_search(const tSDP_RECORD* /* p_rec */,
                                         tSDP_UUID_SEQ* /* p_seq */) {
  inc_func_call_count(__func__);
  return nullptr;
}
uint32_t SDP_CreateRecord(void) {
  inc_func_call_count(__func__);
  return 0;
}
