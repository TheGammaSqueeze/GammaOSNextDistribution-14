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

#include "bta/include/bta_pan_api.h"
#include "test/common/mock_functions.h"
#include "types/raw_address.h"

void BTA_PanClose(uint16_t handle) { inc_func_call_count(__func__); }
void BTA_PanDisable(void) { inc_func_call_count(__func__); }
void BTA_PanEnable(tBTA_PAN_CBACK p_cback) { inc_func_call_count(__func__); }
void BTA_PanOpen(const RawAddress& bd_addr, tBTA_PAN_ROLE local_role,
                 tBTA_PAN_ROLE peer_role) {
  inc_func_call_count(__func__);
}
void BTA_PanSetRole(tBTA_PAN_ROLE role, const tBTA_PAN_ROLE_INFO p_user_info,
                    const tBTA_PAN_ROLE_INFO p_nap_info) {
  inc_func_call_count(__func__);
}
