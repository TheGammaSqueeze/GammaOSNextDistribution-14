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
 *   Functions generated:8
 */

#include <cstdint>

#include "bta/hf_client/bta_hf_client_int.h"
#include "bta/include/bta_hf_client_api.h"
#include "test/common/mock_functions.h"
#include "types/raw_address.h"

tBTA_STATUS BTA_HfClientEnable(tBTA_HF_CLIENT_CBACK* p_cback,
                               tBTA_HF_CLIENT_FEAT features,
                               const char* p_service_name) {
  inc_func_call_count(__func__);
  return BTA_SUCCESS;
}
void BTA_HfClientAudioClose(uint16_t handle) { inc_func_call_count(__func__); }
void BTA_HfClientAudioOpen(uint16_t handle) { inc_func_call_count(__func__); }
void BTA_HfClientClose(uint16_t handle) { inc_func_call_count(__func__); }
void BTA_HfClientDisable(void) { inc_func_call_count(__func__); }
void BTA_HfClientDumpStatistics(int fd) { inc_func_call_count(__func__); }
bt_status_t BTA_HfClientOpen(const RawAddress& bd_addr, uint16_t* p_handle) {
  inc_func_call_count(__func__);
  return BT_STATUS_SUCCESS;
}
void BTA_HfClientSendAT(uint16_t handle, tBTA_HF_CLIENT_AT_CMD_TYPE at,
                        uint32_t val1, uint32_t val2, const char* str) {
  inc_func_call_count(__func__);
}
int get_default_hf_client_features() {
  inc_func_call_count(__func__);
  return 0;
}
