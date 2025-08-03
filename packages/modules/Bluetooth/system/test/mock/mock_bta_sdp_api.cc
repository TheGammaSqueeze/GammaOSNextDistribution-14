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
 *   Functions generated:5
 *
 *  mockcify.pl ver 0.6.1
 */
// Mock include file to share data between tests and mock
#include "test/mock/mock_bta_sdp_api.h"

#include "test/common/mock_functions.h"

// Original usings

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace bta_sdp_api {

// Function state capture and return values, if needed
struct BTA_SdpCreateRecordByUser BTA_SdpCreateRecordByUser;
struct BTA_SdpDumpsys BTA_SdpDumpsys;
struct BTA_SdpEnable BTA_SdpEnable;
struct BTA_SdpRemoveRecordByUser BTA_SdpRemoveRecordByUser;
struct BTA_SdpSearch BTA_SdpSearch;

}  // namespace bta_sdp_api
}  // namespace mock
}  // namespace test

// Mocked function return values, if any
namespace test {
namespace mock {
namespace bta_sdp_api {

tBTA_SDP_STATUS BTA_SdpCreateRecordByUser::return_value = BTA_SDP_SUCCESS;
tBTA_SDP_STATUS BTA_SdpEnable::return_value = BTA_SDP_SUCCESS;
tBTA_SDP_STATUS BTA_SdpRemoveRecordByUser::return_value = BTA_SDP_SUCCESS;
tBTA_SDP_STATUS BTA_SdpSearch::return_value = BTA_SDP_SUCCESS;

}  // namespace bta_sdp_api
}  // namespace mock
}  // namespace test

// Mocked functions, if any
tBTA_SDP_STATUS BTA_SdpCreateRecordByUser(void* user_data) {
  inc_func_call_count(__func__);
  return test::mock::bta_sdp_api::BTA_SdpCreateRecordByUser(user_data);
}
void BTA_SdpDumpsys(int fd) {
  inc_func_call_count(__func__);
  test::mock::bta_sdp_api::BTA_SdpDumpsys(fd);
}
tBTA_SDP_STATUS BTA_SdpEnable(tBTA_SDP_DM_CBACK* p_cback) {
  inc_func_call_count(__func__);
  return test::mock::bta_sdp_api::BTA_SdpEnable(p_cback);
}
tBTA_SDP_STATUS BTA_SdpRemoveRecordByUser(void* user_data) {
  inc_func_call_count(__func__);
  return test::mock::bta_sdp_api::BTA_SdpRemoveRecordByUser(user_data);
}
tBTA_SDP_STATUS BTA_SdpSearch(const RawAddress& bd_addr,
                              const bluetooth::Uuid& uuid) {
  inc_func_call_count(__func__);
  return test::mock::bta_sdp_api::BTA_SdpSearch(bd_addr, uuid);
}
// Mocked functions complete
// END mockcify generation
