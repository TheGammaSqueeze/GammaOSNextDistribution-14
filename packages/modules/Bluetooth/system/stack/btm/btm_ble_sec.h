/*
 * Copyright 2023 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

#pragma once

#include <bluetooth/log.h>

#include <string>

#include "macros.h"
#include "stack/include/btm_ble_sec_api_types.h"
#include "stack/include/btm_sec_api_types.h"
#include "stack/include/btm_status.h"
#include "types/raw_address.h"

typedef enum : uint8_t {
  BTM_BLE_SEC_REQ_ACT_NONE = 0,
  /* encrypt the link using current key or key refresh */
  BTM_BLE_SEC_REQ_ACT_ENCRYPT = 1,
  BTM_BLE_SEC_REQ_ACT_PAIR = 2,
  /* discard the sec request while encryption is started but not completed */
  BTM_BLE_SEC_REQ_ACT_DISCARD = 3,
} tBTM_BLE_SEC_REQ_ACT;

inline std::string btm_ble_sec_req_act_text(
    const tBTM_BLE_SEC_REQ_ACT& action) {
  switch (action) {
    CASE_RETURN_TEXT(BTM_BLE_SEC_REQ_ACT_NONE);
    CASE_RETURN_TEXT(BTM_BLE_SEC_REQ_ACT_ENCRYPT);
    CASE_RETURN_TEXT(BTM_BLE_SEC_REQ_ACT_PAIR);
    CASE_RETURN_TEXT(BTM_BLE_SEC_REQ_ACT_DISCARD);
    default:
      return "UNKNOWN ACTION";
  }
}
/* LE security function from btm_sec.cc */
void btm_ble_link_sec_check(const RawAddress& bd_addr,
                            tBTM_LE_AUTH_REQ auth_req,
                            tBTM_BLE_SEC_REQ_ACT* p_sec_req_act);
void btm_ble_ltk_request_reply(const RawAddress& bda, bool use_stk,
                               const Octet16& stk);
tBTM_STATUS btm_proc_smp_cback(tSMP_EVT event, const RawAddress& bd_addr,
                               const tSMP_EVT_DATA* p_data);
tBTM_STATUS btm_ble_set_encryption(const RawAddress& bd_addr,
                                   tBTM_BLE_SEC_ACT sec_act, uint8_t link_role);
tBTM_STATUS btm_ble_start_encrypt(const RawAddress& bda, bool use_stk,
                                  Octet16* p_stk);
void btm_ble_link_encrypted(const RawAddress& bd_addr, uint8_t encr_enable);

void btm_ble_reset_id(void);

bool btm_get_local_div(const RawAddress& bd_addr, uint16_t* p_div);
bool btm_ble_get_enc_key_type(const RawAddress& bd_addr, uint8_t* p_key_types);

void btm_sec_save_le_key(const RawAddress& bd_addr, tBTM_LE_KEY_TYPE key_type,
                         tBTM_LE_KEY_VALUE* p_keys, bool pass_to_application);
void btm_ble_update_sec_key_size(const RawAddress& bd_addr,
                                 uint8_t enc_key_size);
uint8_t btm_ble_read_sec_key_size(const RawAddress& bd_addr);

tBTM_STATUS btm_ble_start_sec_check(const RawAddress& bd_addr, uint16_t psm,
                                    bool is_originator,
                                    tBTM_SEC_CALLBACK* p_callback,
                                    void* p_ref_data);

namespace fmt {
template <>
struct formatter<tBTM_BLE_SEC_REQ_ACT>
    : string_formatter<tBTM_BLE_SEC_REQ_ACT, &btm_ble_sec_req_act_text> {};
}  // namespace fmt
