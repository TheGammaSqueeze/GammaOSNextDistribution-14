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
/*
 * Generated mock file from original source file
 *   Functions generated:3
 *
 *  mockcify.pl ver 0.6.2
 */

// Mock include file to share data between tests and mock
#include "test/mock/mock_bta_dm_main.h"

#include "test/common/mock_functions.h"

// Original usings

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace bta_dm_main {

// Function state capture and return values, if needed
struct DumpsysBtaDm DumpsysBtaDm;
struct bta_dm_search_sm_disable bta_dm_search_sm_disable;
struct bta_dm_search_sm_execute bta_dm_search_sm_execute;

}  // namespace bta_dm_main
}  // namespace mock
}  // namespace test

// Mocked function return values, if any
namespace test {
namespace mock {
namespace bta_dm_main {

bool bta_dm_search_sm_execute::return_value = false;

}  // namespace bta_dm_main
}  // namespace mock
}  // namespace test

// Mocked functions, if any
void DumpsysBtaDm(int fd) {
  inc_func_call_count(__func__);
  test::mock::bta_dm_main::DumpsysBtaDm(fd);
}
void bta_dm_search_sm_disable() {
  inc_func_call_count(__func__);
  test::mock::bta_dm_main::bta_dm_search_sm_disable();
}
bool bta_dm_search_sm_execute(const BT_HDR_RIGID* p_msg) {
  inc_func_call_count(__func__);
  return test::mock::bta_dm_main::bta_dm_search_sm_execute(p_msg);
}
// Mocked functions complete
// END mockcify generation
