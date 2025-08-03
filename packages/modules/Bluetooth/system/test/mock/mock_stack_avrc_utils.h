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
 *   Functions generated:6
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
namespace stack_avrc_utils {

// Shared state between mocked functions and tests
// Name: AVRC_IsValidAvcType
// Params: uint8_t pdu_id, uint8_t avc_type
// Return: bool
struct AVRC_IsValidAvcType {
  static bool return_value;
  std::function<bool(uint8_t pdu_id, uint8_t avc_type)> body{
      [](uint8_t /* pdu_id */, uint8_t /* avc_type */) {
        return return_value;
      }};
  bool operator()(uint8_t pdu_id, uint8_t avc_type) {
    return body(pdu_id, avc_type);
  };
};
extern struct AVRC_IsValidAvcType AVRC_IsValidAvcType;

// Name: AVRC_IsValidPlayerAttr
// Params: uint8_t attr
// Return: bool
struct AVRC_IsValidPlayerAttr {
  static bool return_value;
  std::function<bool(uint8_t attr)> body{
      [](uint8_t /* attr */) { return return_value; }};
  bool operator()(uint8_t attr) { return body(attr); };
};
extern struct AVRC_IsValidPlayerAttr AVRC_IsValidPlayerAttr;

// Name: avrc_is_valid_opcode
// Params: uint8_t opcode
// Return: bool
struct avrc_is_valid_opcode {
  static bool return_value;
  std::function<bool(uint8_t opcode)> body{
      [](uint8_t /* opcode */) { return return_value; }};
  bool operator()(uint8_t opcode) { return body(opcode); };
};
extern struct avrc_is_valid_opcode avrc_is_valid_opcode;

// Name: avrc_is_valid_player_attrib_value
// Params: uint8_t attrib, uint8_t value
// Return: bool
struct avrc_is_valid_player_attrib_value {
  static bool return_value;
  std::function<bool(uint8_t attrib, uint8_t value)> body{
      [](uint8_t /* attrib */, uint8_t /* value */) { return return_value; }};
  bool operator()(uint8_t attrib, uint8_t value) {
    return body(attrib, value);
  };
};
extern struct avrc_is_valid_player_attrib_value
    avrc_is_valid_player_attrib_value;

// Name: avrc_opcode_from_pdu
// Params: uint8_t pdu
// Return: uint8_t
struct avrc_opcode_from_pdu {
  static uint8_t return_value;
  std::function<uint8_t(uint8_t pdu)> body{
      [](uint8_t /* pdu */) { return return_value; }};
  uint8_t operator()(uint8_t pdu) { return body(pdu); };
};
extern struct avrc_opcode_from_pdu avrc_opcode_from_pdu;

// Name: avrc_pars_pass_thru
// Params: tAVRC_MSG_PASS* p_msg, uint16_t* p_vendor_unique_id
// Return: tAVRC_STS
struct avrc_pars_pass_thru {
  static tAVRC_STS return_value;
  std::function<tAVRC_STS(tAVRC_MSG_PASS* p_msg, uint16_t* p_vendor_unique_id)>
      body{[](tAVRC_MSG_PASS* /* p_msg */, uint16_t* /* p_vendor_unique_id */) {
        return return_value;
      }};
  tAVRC_STS operator()(tAVRC_MSG_PASS* p_msg, uint16_t* p_vendor_unique_id) {
    return body(p_msg, p_vendor_unique_id);
  };
};
extern struct avrc_pars_pass_thru avrc_pars_pass_thru;

}  // namespace stack_avrc_utils
}  // namespace mock
}  // namespace test

// END mockcify generation
