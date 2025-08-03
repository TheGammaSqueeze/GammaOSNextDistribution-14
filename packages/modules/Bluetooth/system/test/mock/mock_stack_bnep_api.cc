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
 *   Functions generated:11
 */

#include "stack/include/bnep_api.h"
#include "stack/include/bt_hdr.h"
#include "test/common/mock_functions.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

using namespace bluetooth;

tBNEP_RESULT BNEP_Connect(const RawAddress& /* p_rem_bda */,
                          const Uuid& /* src_uuid */,
                          const Uuid& /* dst_uuid */, uint16_t* /* p_handle */,
                          uint32_t /* mx_chan_id */) {
  inc_func_call_count(__func__);
  return 0;
}
tBNEP_RESULT BNEP_ConnectResp(uint16_t /* handle */, tBNEP_RESULT /* resp */) {
  inc_func_call_count(__func__);
  return 0;
}
tBNEP_RESULT BNEP_Disconnect(uint16_t /* handle */) {
  inc_func_call_count(__func__);
  return 0;
}
tBNEP_RESULT BNEP_Register(tBNEP_REGISTER* /* p_reg_info */) {
  inc_func_call_count(__func__);
  return 0;
}
tBNEP_RESULT BNEP_SetMulticastFilters(uint16_t /* handle */,
                                      uint16_t /* num_filters */,
                                      uint8_t* /* p_start_array */,
                                      uint8_t* /* p_end_array */) {
  inc_func_call_count(__func__);
  return 0;
}
tBNEP_RESULT BNEP_SetProtocolFilters(uint16_t /* handle */,
                                     uint16_t /* num_filters */,
                                     uint16_t* /* p_start_array */,
                                     uint16_t* /* p_end_array */) {
  inc_func_call_count(__func__);
  return 0;
}
tBNEP_RESULT BNEP_Write(uint16_t /* handle */,
                        const RawAddress& /* dest_addr */,
                        uint8_t* /* p_data */, uint16_t /* len */,
                        uint16_t /* protocol */,
                        const RawAddress& /* src_addr */,
                        bool /* fw_ext_present */) {
  inc_func_call_count(__func__);
  return 0;
}
tBNEP_RESULT BNEP_WriteBuf(uint16_t /* handle */,
                           const RawAddress& /* dest_addr */,
                           BT_HDR* /* p_buf */, uint16_t /* protocol */,
                           const RawAddress& /* src_addr */,
                           bool /* fw_ext_present */) {
  inc_func_call_count(__func__);
  return 0;
}
void BNEP_Deregister(void) { inc_func_call_count(__func__); }
void BNEP_Init(void) { inc_func_call_count(__func__); }
