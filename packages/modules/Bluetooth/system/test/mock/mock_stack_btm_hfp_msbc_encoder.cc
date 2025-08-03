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
 *   Functions generated:3
 *
 *  mockcify.pl ver 0.5.1
 */

// Mock include file to share data between tests and mock
#include "test/mock/mock_stack_btm_hfp_msbc_encoder.h"

#include <cstdint>

#ifndef __clang_analyzer__

#include "test/common/mock_functions.h"

// Original usings

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace stack_btm_hfp_msbc_encoder {

// Function state capture and return values, if needed
struct hfp_msbc_encode_frames hfp_msbc_encode_frames;
struct hfp_msbc_encoder_cleanup hfp_msbc_encoder_cleanup;
struct hfp_msbc_encoder_init hfp_msbc_encoder_init;

}  // namespace stack_btm_hfp_msbc_encoder
}  // namespace mock
}  // namespace test

// Mocked function return values, if any
namespace test {
namespace mock {
namespace stack_btm_hfp_msbc_encoder {

uint32_t hfp_msbc_encode_frames::return_value = 0;

}  // namespace stack_btm_hfp_msbc_encoder
}  // namespace mock
}  // namespace test

// Mocked functions, if any
uint32_t hfp_msbc_encode_frames(int16_t* input, uint8_t* output) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_hfp_msbc_encoder::hfp_msbc_encode_frames(input,
                                                                        output);
}
void hfp_msbc_encoder_cleanup(void) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_hfp_msbc_encoder::hfp_msbc_encoder_cleanup();
}
void hfp_msbc_encoder_init(void) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_hfp_msbc_encoder::hfp_msbc_encoder_init();
}
// Mocked functions complete
#endif  //  __clang_analyzer__
// END mockcify generation
