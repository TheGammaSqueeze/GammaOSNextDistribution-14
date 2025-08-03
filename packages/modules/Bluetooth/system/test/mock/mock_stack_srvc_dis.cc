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
 *   Functions generated:10
 */

#include <cstdint>

#include "stack/include/srvc_api.h"
#include "stack/srvc/srvc_eng_int.h"
#include "test/common/mock_functions.h"
#include "types/raw_address.h"

bool DIS_ReadDISInfo(const RawAddress& /* peer_bda */,
                     tDIS_READ_CBACK* /* p_cback */,
                     tDIS_ATTR_MASK /* mask */) {
  inc_func_call_count(__func__);
  return false;
}
bool dis_gatt_c_read_dis_req(uint16_t /* conn_id */) {
  inc_func_call_count(__func__);
  return false;
}
bool dis_valid_handle_range(uint16_t /* handle */) {
  inc_func_call_count(__func__);
  return false;
}
tDIS_STATUS DIS_SrInit(tDIS_ATTR_MASK /* dis_attr_mask */) {
  inc_func_call_count(__func__);
  return 0;
}
tDIS_STATUS DIS_SrUpdate(tDIS_ATTR_BIT /* dis_attr_bit */,
                         tDIS_ATTR* /* p_info */) {
  inc_func_call_count(__func__);
  return 0;
}
uint8_t dis_read_attr_value(uint8_t /* clcb_idx */, uint16_t /* handle */,
                            tGATT_VALUE* /* p_value */, bool /* is_long */,
                            tGATT_STATUS* /* p_status */) {
  inc_func_call_count(__func__);
  return 0;
}
uint8_t dis_write_attr_value(tGATT_WRITE_REQ* /* p_data */,
                             tGATT_STATUS* /* p_status */) {
  inc_func_call_count(__func__);
  return 0;
}
void dis_c_cmpl_cback(tSRVC_CLCB* /* p_clcb */, tGATTC_OPTYPE /* op */,
                      tGATT_STATUS /* status */,
                      tGATT_CL_COMPLETE* /* p_data */) {
  inc_func_call_count(__func__);
}
