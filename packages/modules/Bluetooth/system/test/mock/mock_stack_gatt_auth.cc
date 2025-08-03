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
 *   Functions generated:10
 */

#include <cstdint>

#include "stack/gatt/gatt_int.h"
#include "stack/include/bt_hdr.h"
#include "test/common/mock_functions.h"
#include "types/raw_address.h"

bool gatt_security_check_start(tGATT_CLCB* /* p_clcb */) {
  inc_func_call_count(__func__);
  return false;
}
tGATT_SEC_ACTION gatt_determine_sec_act(tGATT_CLCB* /* p_clcb */) {
  inc_func_call_count(__func__);
  return GATT_SEC_NONE;
}
tGATT_SEC_ACTION gatt_get_sec_act(tGATT_TCB* /* p_tcb */) {
  inc_func_call_count(__func__);
  return GATT_SEC_NONE;
}
tGATT_STATUS gatt_get_link_encrypt_status(tGATT_TCB& /* tcb */) {
  inc_func_call_count(__func__);
  return GATT_SUCCESS;
}
void gatt_notify_enc_cmpl(const RawAddress& /* bd_addr */) {
  inc_func_call_count(__func__);
}
void gatt_set_sec_act(tGATT_TCB* /* p_tcb */, tGATT_SEC_ACTION /* sec_act */) {
  inc_func_call_count(__func__);
}
void gatt_verify_signature(tGATT_TCB& /* tcb */, uint16_t /* cid */,
                           BT_HDR* /* p_buf */) {
  inc_func_call_count(__func__);
}
