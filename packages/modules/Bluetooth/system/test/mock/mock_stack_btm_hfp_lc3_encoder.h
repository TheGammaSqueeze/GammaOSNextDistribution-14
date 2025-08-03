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
 *   Functions generated:3
 *
 *  mockcify.pl ver 0.5.1
 */

#include <cstdint>
#include <functional>

// Original included files, if any

// Original usings

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace stack_btm_hfp_lc3_encoder {

// Shared state between mocked functions and tests
// Name: hfp_lc3_encode_frames
// Params: int16_t* input, uint8_t* output
// Return: uint32_t
struct hfp_lc3_encode_frames {
  static uint32_t return_value;
  std::function<uint32_t(int16_t* input, uint8_t* output)> body{
      [](int16_t* /* input */, uint8_t* /* output */) { return return_value; }};
  uint32_t operator()(int16_t* input, uint8_t* output) {
    return body(input, output);
  };
};
extern struct hfp_lc3_encode_frames hfp_lc3_encode_frames;

// Name: hfp_lc3_encoder_cleanup
// Params: void
// Return: void
struct hfp_lc3_encoder_cleanup {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct hfp_lc3_encoder_cleanup hfp_lc3_encoder_cleanup;

// Name: hfp_lc3_encoder_init
// Params: void
// Return: void
struct hfp_lc3_encoder_init {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct hfp_lc3_encoder_init hfp_lc3_encoder_init;

}  // namespace stack_btm_hfp_lc3_encoder
}  // namespace mock
}  // namespace test

// END mockcify generation
