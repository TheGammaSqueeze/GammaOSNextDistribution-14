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
/*
 * Generated mock file from original source file
 *   Functions generated:6
 *
 *  mockcify.pl ver 0.5.0
 */
// Mock include file to share data between tests and mock
#include "test/mock/mock_stack_avrc_utils.h"

#include <cstdint>

#include "test/common/mock_functions.h"

// Original usings

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace stack_avrc_utils {

// Function state capture and return values, if needed
struct AVRC_IsValidAvcType AVRC_IsValidAvcType;
struct AVRC_IsValidPlayerAttr AVRC_IsValidPlayerAttr;
struct avrc_is_valid_opcode avrc_is_valid_opcode;
struct avrc_is_valid_player_attrib_value avrc_is_valid_player_attrib_value;
struct avrc_opcode_from_pdu avrc_opcode_from_pdu;
struct avrc_pars_pass_thru avrc_pars_pass_thru;

}  // namespace stack_avrc_utils
}  // namespace mock
}  // namespace test

// Mocked function return values, if any
namespace test {
namespace mock {
namespace stack_avrc_utils {

bool AVRC_IsValidAvcType::return_value = false;
bool AVRC_IsValidPlayerAttr::return_value = false;
bool avrc_is_valid_opcode::return_value = false;
bool avrc_is_valid_player_attrib_value::return_value = false;
uint8_t avrc_opcode_from_pdu::return_value = 0;
tAVRC_STS avrc_pars_pass_thru::return_value = 0;

}  // namespace stack_avrc_utils
}  // namespace mock
}  // namespace test

// Mocked functions, if any
bool AVRC_IsValidAvcType(uint8_t pdu_id, uint8_t avc_type) {
  inc_func_call_count(__func__);
  return test::mock::stack_avrc_utils::AVRC_IsValidAvcType(pdu_id, avc_type);
}
bool AVRC_IsValidPlayerAttr(uint8_t attr) {
  inc_func_call_count(__func__);
  return test::mock::stack_avrc_utils::AVRC_IsValidPlayerAttr(attr);
}
bool avrc_is_valid_opcode(uint8_t opcode) {
  inc_func_call_count(__func__);
  return test::mock::stack_avrc_utils::avrc_is_valid_opcode(opcode);
}
bool avrc_is_valid_player_attrib_value(uint8_t attrib, uint8_t value) {
  inc_func_call_count(__func__);
  return test::mock::stack_avrc_utils::avrc_is_valid_player_attrib_value(attrib,
                                                                         value);
}
uint8_t avrc_opcode_from_pdu(uint8_t pdu) {
  inc_func_call_count(__func__);
  return test::mock::stack_avrc_utils::avrc_opcode_from_pdu(pdu);
}
tAVRC_STS avrc_pars_pass_thru(tAVRC_MSG_PASS* p_msg,
                              uint16_t* p_vendor_unique_id) {
  inc_func_call_count(__func__);
  return test::mock::stack_avrc_utils::avrc_pars_pass_thru(p_msg,
                                                           p_vendor_unique_id);
}
// Mocked functions complete
// END mockcify generation
