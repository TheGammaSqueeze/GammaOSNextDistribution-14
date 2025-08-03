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

#include "a2dp_api.h"
#include "stack/a2dp/a2dp_int.h"
#include "test/common/mock_functions.h"
#include "types/raw_address.h"

tA2DP_STATUS A2DP_AddRecord(uint16_t /* service_uuid */,
                            char* /* p_service_name */,
                            char* /* p_provider_name */,
                            uint16_t /* features */,
                            uint32_t /* sdp_handle */) {
  inc_func_call_count(__func__);
  return A2DP_SUCCESS;
}
tA2DP_STATUS A2DP_FindService(uint16_t /* service_uuid */,
                              const RawAddress& /* bd_addr */,
                              tA2DP_SDP_DB_PARAMS* /* p_db */,
                              tA2DP_FIND_CBACK* /* p_cback */) {
  inc_func_call_count(__func__);
  return A2DP_SUCCESS;
}
uint16_t A2DP_GetAvdtpVersion() {
  inc_func_call_count(__func__);
  return 0;
}
uint8_t A2DP_BitsSet(uint64_t /* num */) {
  inc_func_call_count(__func__);
  return 1;
}
void A2DP_Init(void) { inc_func_call_count(__func__); }
void a2dp_set_avdt_sdp_ver(uint16_t /* avdt_sdp_ver */) {
  inc_func_call_count(__func__);
}
