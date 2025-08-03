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
 *   Functions generated:3
 *
 *  mockcify.pl ver 0.5.0
 */

#include <cstdint>
#include <functional>

// Original included files, if any
#include <base/logging.h>

#include <cstring>

// Original usings

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace stack_btm_hfp_msbc_decoder {

// Shared state between mocked functions and tests
// Name: hfp_msbc_decoder_cleanup
// Params: void
// Return: void
struct hfp_msbc_decoder_cleanup {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct hfp_msbc_decoder_cleanup hfp_msbc_decoder_cleanup;

// Name: hfp_msbc_decoder_decode_packet
// Params: const uint8_t* i_buf, int16_t* o_buf, size_t out_len
// Return: bool
struct hfp_msbc_decoder_decode_packet {
  static bool return_value;
  std::function<bool(const uint8_t* i_buf, int16_t* o_buf, size_t out_len)>
      body{[](const uint8_t* /* i_buf */, int16_t* /* o_buf */,
              size_t /* out_len */) { return return_value; }};
  bool operator()(const uint8_t* i_buf, int16_t* o_buf, size_t out_len) {
    return body(i_buf, o_buf, out_len);
  };
};
extern struct hfp_msbc_decoder_decode_packet hfp_msbc_decoder_decode_packet;

// Name: hfp_msbc_decoder_init
// Params:
// Return: bool
struct hfp_msbc_decoder_init {
  static bool return_value;
  std::function<bool()> body{[]() { return return_value; }};
  bool operator()() { return body(); };
};
extern struct hfp_msbc_decoder_init hfp_msbc_decoder_init;

}  // namespace stack_btm_hfp_msbc_decoder
}  // namespace mock
}  // namespace test

// END mockcify generation
