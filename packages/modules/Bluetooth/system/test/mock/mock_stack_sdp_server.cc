/*
 * Copyright 2023 The Android Open Source Project
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

#include "test/common/mock_functions.h"
#include "types/raw_address.h"

void update_pce_entry_to_interop_database(RawAddress /* remote_addr */) {
  inc_func_call_count(__func__);
}

bool is_sdp_pbap_pce_disabled(RawAddress /* remote_address */) {
  inc_func_call_count(__func__);
  return false;
}
void sdp_save_local_pse_record_attributes(
    int32_t /* rfcomm_channel_number */, int32_t /* l2cap_psm */,
    int32_t /* profile_version */, uint32_t /* supported_features */,
    uint32_t /* supported_repositories */) {
  inc_func_call_count(__func__);
}
