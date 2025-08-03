/*
 * Copyright 2022 The Android Open Source Project
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
 *   Functions generated:4
 *
 *  mockcify.pl ver 0.5.0
 */

#include <cstdint>
#include <functional>

// Original included files, if any
#include "stack/include/avrc_defs.h"

// Original usings

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace stack_avrc_apt {

// Shared state between mocked functions and tests
// Name: AVRC_SubCmd
// Params: uint8_t handle, uint8_t label, uint8_t page
// Return: uint16_t
struct AVRC_SubCmd {
  static uint16_t return_value;
  std::function<uint16_t(uint8_t handle, uint8_t label, uint8_t page)> body{
      [](uint8_t /* handle */, uint8_t /* label */, uint8_t /* page */) {
        return return_value;
      }};
  uint16_t operator()(uint8_t handle, uint8_t label, uint8_t page) {
    return body(handle, label, page);
  };
};
extern struct AVRC_SubCmd AVRC_SubCmd;

// Name: AVRC_UnitCmd
// Params: uint8_t handle, uint8_t label
// Return: uint16_t
struct AVRC_UnitCmd {
  static uint16_t return_value;
  std::function<uint16_t(uint8_t handle, uint8_t label)> body{
      [](uint8_t /* handle */, uint8_t /* label */) { return return_value; }};
  uint16_t operator()(uint8_t handle, uint8_t label) {
    return body(handle, label);
  };
};
extern struct AVRC_UnitCmd AVRC_UnitCmd;

// Name: AVRC_VendorCmd
// Params: uint8_t handle, uint8_t label, tAVRC_MSG_VENDOR* p_msg
// Return: uint16_t
struct AVRC_VendorCmd {
  static uint16_t return_value;
  std::function<uint16_t(uint8_t handle, uint8_t label,
                         tAVRC_MSG_VENDOR* p_msg)>
      body{[](uint8_t /* handle */, uint8_t /* label */,
              tAVRC_MSG_VENDOR* /* p_msg */) { return return_value; }};
  uint16_t operator()(uint8_t handle, uint8_t label, tAVRC_MSG_VENDOR* p_msg) {
    return body(handle, label, p_msg);
  };
};
extern struct AVRC_VendorCmd AVRC_VendorCmd;

// Name: AVRC_VendorRsp
// Params: uint8_t handle, uint8_t label, tAVRC_MSG_VENDOR* p_msg
// Return: uint16_t
struct AVRC_VendorRsp {
  static uint16_t return_value;
  std::function<uint16_t(uint8_t handle, uint8_t label,
                         tAVRC_MSG_VENDOR* p_msg)>
      body{[](uint8_t /* handle */, uint8_t /* label */,
              tAVRC_MSG_VENDOR* /* p_msg */) { return return_value; }};
  uint16_t operator()(uint8_t handle, uint8_t label, tAVRC_MSG_VENDOR* p_msg) {
    return body(handle, label, p_msg);
  };
};
extern struct AVRC_VendorRsp AVRC_VendorRsp;

}  // namespace stack_avrc_apt
}  // namespace mock
}  // namespace test

// END mockcify generation
