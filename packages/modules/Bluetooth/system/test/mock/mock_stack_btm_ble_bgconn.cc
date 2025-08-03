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
 *
 *  mockcify.pl ver 0.2
 */
// Mock include file to share data between tests and mock
#include "test/mock/mock_stack_btm_ble_bgconn.h"

#include "test/common/mock_functions.h"
#include "types/raw_address.h"

// Mocked compile conditionals, if any
// Mocked internal structures, if any
struct BackgroundConnection {};
struct BgConnHash {};

namespace test {
namespace mock {
namespace stack_btm_ble_bgconn {

// Function state capture and return values, if needed
struct btm_update_scanner_filter_policy btm_update_scanner_filter_policy;
struct BTM_SetLeConnectionModeToFast BTM_SetLeConnectionModeToFast;
struct BTM_SetLeConnectionModeToSlow BTM_SetLeConnectionModeToSlow;
struct BTM_AcceptlistAdd BTM_AcceptlistAdd;
struct BTM_AcceptlistAddDirect BTM_AcceptlistAddDirect;
struct BTM_AcceptlistRemove BTM_AcceptlistRemove;
struct BTM_AcceptlistClear BTM_AcceptlistClear;

}  // namespace stack_btm_ble_bgconn
}  // namespace mock
}  // namespace test

void btm_update_scanner_filter_policy(tBTM_BLE_SFP scan_policy) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble_bgconn::btm_update_scanner_filter_policy(
      scan_policy);
}
bool BTM_SetLeConnectionModeToFast() {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_ble_bgconn::BTM_SetLeConnectionModeToFast();
}
void BTM_SetLeConnectionModeToSlow() {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble_bgconn::BTM_SetLeConnectionModeToSlow();
}
bool BTM_AcceptlistAdd(const RawAddress& address) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_ble_bgconn::BTM_AcceptlistAdd(address);
}
bool BTM_AcceptlistAdd(const RawAddress& address, bool is_direct) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_ble_bgconn::BTM_AcceptlistAddDirect(address,
                                                                   is_direct);
}
void BTM_AcceptlistRemove(const RawAddress& address) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble_bgconn::BTM_AcceptlistRemove(address);
}
void BTM_AcceptlistClear() {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble_bgconn::BTM_AcceptlistClear();
}

// END mockcify generation
