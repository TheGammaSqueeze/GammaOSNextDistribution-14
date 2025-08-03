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
 *  mockcify.pl ver 0.6.1
 */

#include <functional>

// Original included files, if any
#include "bta/include/bta_sdp_api.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

// Original usings

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace bta_sdp_api {

// Shared state between mocked functions and tests
// Name: BTA_SdpCreateRecordByUser
// Params: void* user_data
// Return: tBTA_SDP_STATUS
struct BTA_SdpCreateRecordByUser {
  static tBTA_SDP_STATUS return_value;
  std::function<tBTA_SDP_STATUS(void* user_data)> body{
      [](void* /* user_data */) { return return_value; }};
  tBTA_SDP_STATUS operator()(void* user_data) { return body(user_data); };
};
extern struct BTA_SdpCreateRecordByUser BTA_SdpCreateRecordByUser;

// Name: BTA_SdpDumpsys
// Params: int fd
// Return: void
struct BTA_SdpDumpsys {
  std::function<void(int fd)> body{[](int /* fd */) {}};
  void operator()(int fd) { body(fd); };
};
extern struct BTA_SdpDumpsys BTA_SdpDumpsys;

// Name: BTA_SdpEnable
// Params: tBTA_SDP_DM_CBACK* p_cback
// Return: tBTA_SDP_STATUS
struct BTA_SdpEnable {
  static tBTA_SDP_STATUS return_value;
  std::function<tBTA_SDP_STATUS(tBTA_SDP_DM_CBACK* p_cback)> body{
      [](tBTA_SDP_DM_CBACK* /* p_cback */) { return return_value; }};
  tBTA_SDP_STATUS operator()(tBTA_SDP_DM_CBACK* p_cback) {
    return body(p_cback);
  };
};
extern struct BTA_SdpEnable BTA_SdpEnable;

// Name: BTA_SdpRemoveRecordByUser
// Params: void* user_data
// Return: tBTA_SDP_STATUS
struct BTA_SdpRemoveRecordByUser {
  static tBTA_SDP_STATUS return_value;
  std::function<tBTA_SDP_STATUS(void* user_data)> body{
      [](void* /* user_data */) { return return_value; }};
  tBTA_SDP_STATUS operator()(void* user_data) { return body(user_data); };
};
extern struct BTA_SdpRemoveRecordByUser BTA_SdpRemoveRecordByUser;

// Name: BTA_SdpSearch
// Params: const RawAddress& bd_addr, const bluetooth::Uuid& uuid
// Return: tBTA_SDP_STATUS
struct BTA_SdpSearch {
  static tBTA_SDP_STATUS return_value;
  std::function<tBTA_SDP_STATUS(const RawAddress& bd_addr,
                                const bluetooth::Uuid& uuid)>
      body{[](const RawAddress& /* bd_addr */,
              const bluetooth::Uuid& /* uuid */) { return return_value; }};
  tBTA_SDP_STATUS operator()(const RawAddress& bd_addr,
                             const bluetooth::Uuid& uuid) {
    return body(bd_addr, uuid);
  };
};
extern struct BTA_SdpSearch BTA_SdpSearch;

}  // namespace bta_sdp_api
}  // namespace mock
}  // namespace test

// END mockcify generation
