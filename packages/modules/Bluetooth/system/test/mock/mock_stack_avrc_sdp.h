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
#pragma once

/*
 * Generated mock file from original source file
 *   Functions generated:5
 *
 *  mockcify.pl ver 0.6.3
 */

#include <cstdint>
#include <functional>

// Original included files, if any
// NOTE: Since this is a mock file with mock definitions some number of
//       include files may not be required.  The include-what-you-use
//       still applies, but crafting proper inclusion is out of scope
//       for this effort.  This compilation unit may compile as-is, or
//       may need attention to prune from (or add to ) the inclusion set.

#include "stack/include/avrc_api.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

// Original usings
using bluetooth::Uuid;
using namespace bluetooth::legacy::stack::sdp;

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace stack_avrc_sdp {

// Shared state between mocked functions and tests
// Name: AVRC_AddRecord
// Params: uint16_t service_uuid, const char* p_service_name, const char*
// p_provider_name, uint16_t categories, uint32_t sdp_handle, bool
// browse_supported, uint16_t profile_version, uint16_t cover_art_psm Return:
// uint16_t
struct AVRC_AddRecord {
  static uint16_t return_value;
  std::function<uint16_t(uint16_t service_uuid, const char* p_service_name,
                         const char* p_provider_name, uint16_t categories,
                         uint32_t sdp_handle, bool browse_supported,
                         uint16_t profile_version, uint16_t cover_art_psm)>
      body{[](uint16_t /* service_uuid */, const char* /* p_service_name */,
              const char* /* p_provider_name */, uint16_t /* categories */,
              uint32_t /* sdp_handle */, bool /* browse_supported */,
              uint16_t /* profile_version */,
              uint16_t /* cover_art_psm */) { return return_value; }};
  uint16_t operator()(uint16_t service_uuid, const char* p_service_name,
                      const char* p_provider_name, uint16_t categories,
                      uint32_t sdp_handle, bool browse_supported,
                      uint16_t profile_version, uint16_t cover_art_psm) {
    return body(service_uuid, p_service_name, p_provider_name, categories,
                sdp_handle, browse_supported, profile_version, cover_art_psm);
  };
};
extern struct AVRC_AddRecord AVRC_AddRecord;

// Name: AVRC_FindService
// Params: uint16_t service_uuid, const RawAddress& bd_addr,
// tAVRC_SDP_DB_PARAMS* p_db, const tAVRC_FIND_CBACK& find_cback Return:
// uint16_t
struct AVRC_FindService {
  static uint16_t return_value;
  std::function<uint16_t(uint16_t service_uuid, const RawAddress& bd_addr,
                         tAVRC_SDP_DB_PARAMS* p_db,
                         const tAVRC_FIND_CBACK& find_cback)>
      body{[](uint16_t /* service_uuid */, const RawAddress& /* bd_addr */,
              tAVRC_SDP_DB_PARAMS* /* p_db */,
              const tAVRC_FIND_CBACK& /* find_cback */) {
        return return_value;
      }};
  uint16_t operator()(uint16_t service_uuid, const RawAddress& bd_addr,
                      tAVRC_SDP_DB_PARAMS* p_db,
                      const tAVRC_FIND_CBACK& find_cback) {
    return body(service_uuid, bd_addr, p_db, find_cback);
  };
};
extern struct AVRC_FindService AVRC_FindService;

// Name: AVRC_Init
// Params: void
// Return: void
struct AVRC_Init {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct AVRC_Init AVRC_Init;

// Name: AVRC_RemoveRecord
// Params: uint32_t sdp_handle
// Return: uint16_t
struct AVRC_RemoveRecord {
  static uint16_t return_value;
  std::function<uint16_t(uint32_t sdp_handle)> body{
      [](uint32_t /* sdp_handle */) { return return_value; }};
  uint16_t operator()(uint32_t sdp_handle) { return body(sdp_handle); };
};
extern struct AVRC_RemoveRecord AVRC_RemoveRecord;

}  // namespace stack_avrc_sdp
}  // namespace mock
}  // namespace test

// END mockcify generation
