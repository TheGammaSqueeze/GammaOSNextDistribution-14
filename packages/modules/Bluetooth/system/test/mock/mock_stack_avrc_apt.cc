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
 *   Functions generated:4
 *
 *  mockcify.pl ver 0.5.0
 */
// Mock include file to share data between tests and mock
#include "test/mock/mock_stack_avrc_apt.h"

#include <cstdint>

#include "test/common/mock_functions.h"

// Original usings

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace stack_avrc_apt {

// Function state capture and return values, if needed
struct AVRC_SubCmd AVRC_SubCmd;
struct AVRC_UnitCmd AVRC_UnitCmd;
struct AVRC_VendorCmd AVRC_VendorCmd;
struct AVRC_VendorRsp AVRC_VendorRsp;

}  // namespace stack_avrc_apt
}  // namespace mock
}  // namespace test

// Mocked function return values, if any
namespace test {
namespace mock {
namespace stack_avrc_apt {

uint16_t AVRC_SubCmd::return_value = 0;
uint16_t AVRC_UnitCmd::return_value = 0;
uint16_t AVRC_VendorCmd::return_value = 0;
uint16_t AVRC_VendorRsp::return_value = 0;

}  // namespace stack_avrc_apt
}  // namespace mock
}  // namespace test

// Mocked functions, if any
uint16_t AVRC_SubCmd(uint8_t handle, uint8_t label, uint8_t page) {
  inc_func_call_count(__func__);
  return test::mock::stack_avrc_apt::AVRC_SubCmd(handle, label, page);
}
uint16_t AVRC_UnitCmd(uint8_t handle, uint8_t label) {
  inc_func_call_count(__func__);
  return test::mock::stack_avrc_apt::AVRC_UnitCmd(handle, label);
}
uint16_t AVRC_VendorCmd(uint8_t handle, uint8_t label,
                        tAVRC_MSG_VENDOR* p_msg) {
  inc_func_call_count(__func__);
  return test::mock::stack_avrc_apt::AVRC_VendorCmd(handle, label, p_msg);
}
uint16_t AVRC_VendorRsp(uint8_t handle, uint8_t label,
                        tAVRC_MSG_VENDOR* p_msg) {
  inc_func_call_count(__func__);
  return test::mock::stack_avrc_apt::AVRC_VendorRsp(handle, label, p_msg);
}
// Mocked functions complete
// END mockcify generation
