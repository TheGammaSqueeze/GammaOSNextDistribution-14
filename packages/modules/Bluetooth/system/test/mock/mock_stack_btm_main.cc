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
 *   Functions generated:7
 */

#include <string>

#include "test/common/mock_functions.h"
#include "types/ble_address_with_type.h"
#include "types/raw_address.h"

void BTM_LogHistory(const std::string& /* tag */,
                    const RawAddress& /* bd_addr */,
                    const std::string& /* msg */) {
  inc_func_call_count(__func__);
}
void BTM_LogHistory(const std::string& /* tag */,
                    const RawAddress& /* bd_addr */,
                    const std::string& /* msg */,
                    const std::string& /* extra */) {
  inc_func_call_count(__func__);
}
void BTM_LogHistory(const std::string& /* tag */,
                    const tBLE_BD_ADDR& /* ble_bd_addr */,
                    const std::string& /* msg */) {
  inc_func_call_count(__func__);
}
void BTM_LogHistory(const std::string& /* tag */,
                    const tBLE_BD_ADDR& /* ble_bd_addr */,
                    const std::string& /* msg */,
                    const std::string& /* extra */) {
  inc_func_call_count(__func__);
}
void btm_free(void) { inc_func_call_count(__func__); }
void btm_init(void) { inc_func_call_count(__func__); }
