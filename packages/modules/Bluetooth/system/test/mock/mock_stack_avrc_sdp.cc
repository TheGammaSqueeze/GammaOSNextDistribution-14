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
 *  mockcify.pl ver 0.6.3
 */

// Mock include file to share data between tests and mock
#include "test/mock/mock_stack_avrc_sdp.h"

#include <cstdint>

#include "test/common/mock_functions.h"

// Original usings
using bluetooth::Uuid;
using namespace bluetooth::legacy::stack::sdp;

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace stack_avrc_sdp {

// Function state capture and return values, if needed
struct AVRC_AddRecord AVRC_AddRecord;
struct AVRC_FindService AVRC_FindService;
struct AVRC_Init AVRC_Init;
struct AVRC_RemoveRecord AVRC_RemoveRecord;

}  // namespace stack_avrc_sdp
}  // namespace mock
}  // namespace test

// Mocked function return values, if any
namespace test {
namespace mock {
namespace stack_avrc_sdp {

uint16_t AVRC_AddRecord::return_value = 0;
uint16_t AVRC_FindService::return_value = 0;
uint16_t AVRC_RemoveRecord::return_value = 0;

}  // namespace stack_avrc_sdp
}  // namespace mock
}  // namespace test

// Mocked functions, if any
uint16_t AVRC_AddRecord(uint16_t service_uuid, const char* p_service_name,
                        const char* p_provider_name, uint16_t categories,
                        uint32_t sdp_handle, bool browse_supported,
                        uint16_t profile_version, uint16_t cover_art_psm) {
  inc_func_call_count(__func__);
  return test::mock::stack_avrc_sdp::AVRC_AddRecord(
      service_uuid, p_service_name, p_provider_name, categories, sdp_handle,
      browse_supported, profile_version, cover_art_psm);
}
uint16_t AVRC_FindService(uint16_t service_uuid, const RawAddress& bd_addr,
                          tAVRC_SDP_DB_PARAMS* p_db,
                          const tAVRC_FIND_CBACK& find_cback) {
  inc_func_call_count(__func__);
  return test::mock::stack_avrc_sdp::AVRC_FindService(service_uuid, bd_addr,
                                                      p_db, find_cback);
}
void AVRC_Init(void) {
  inc_func_call_count(__func__);
  test::mock::stack_avrc_sdp::AVRC_Init();
}
uint16_t AVRC_RemoveRecord(uint32_t sdp_handle) {
  inc_func_call_count(__func__);
  return test::mock::stack_avrc_sdp::AVRC_RemoveRecord(sdp_handle);
}
// Mocked functions complete
// END mockcify generation
