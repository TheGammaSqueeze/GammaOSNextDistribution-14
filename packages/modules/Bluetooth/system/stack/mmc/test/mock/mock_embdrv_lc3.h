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
 *   Functions generated:10
 *
 *  mockcify.pl ver 0.6.1
 */

#include <lc3.h>

#include <cstdint>
#include <functional>
#include <map>
#include <string>

#include "test/common/mock_functions.h"

namespace test {
namespace mock {
namespace embdrv_lc3 {
// Shared state between mocked functions and tests
// Name: lc3_decode
// Params: struct lc3_decoder* decoder, const void* in, int nbytes, enum
// lc3_pcm_format fmt, void* pcm, int stride Return: int
struct lc3_decode {
  static int return_value;
  std::function<int(struct lc3_decoder* decoder, const void* in, int nbytes,
                    enum lc3_pcm_format fmt, void* pcm, int stride)>
      body{[](struct lc3_decoder* decoder, const void* in, int nbytes,
              enum lc3_pcm_format fmt, void* pcm,
              int stride) { return return_value; }};
  int operator()(struct lc3_decoder* decoder, const void* in, int nbytes,
                 enum lc3_pcm_format fmt, void* pcm, int stride) {
    return body(decoder, in, nbytes, fmt, pcm, stride);
  };
};
extern struct lc3_decode lc3_decode;

// Name: lc3_decoder_size
// Params: int dt_us, int sr_hz
// Return: unsigned
struct lc3_decoder_size {
  static unsigned return_value;
  std::function<unsigned(int dt_us, int sr_hz)> body{
      [](int dt_us, int sr_hz) { return return_value; }};
  unsigned operator()(int dt_us, int sr_hz) { return body(dt_us, sr_hz); };
};
extern struct lc3_decoder_size lc3_decoder_size;

// Name: lc3_delay_samples
// Params: int dt_us, int sr_hz
// Return: int
struct lc3_delay_samples {
  static int return_value;
  std::function<int(int dt_us, int sr_hz)> body{
      [](int dt_us, int sr_hz) { return return_value; }};
  int operator()(int dt_us, int sr_hz) { return body(dt_us, sr_hz); };
};
extern struct lc3_delay_samples lc3_delay_samples;

// Name: lc3_encode
// Params: struct lc3_encoder* encoder, enum lc3_pcm_format fmt, const void*
// pcm, int stride, int nbytes, void* out Return: int
struct lc3_encode {
  static int return_value;
  std::function<int(struct lc3_encoder* encoder, enum lc3_pcm_format fmt,
                    const void* pcm, int stride, int nbytes, void* out)>
      body{[](struct lc3_encoder* encoder, enum lc3_pcm_format fmt,
              const void* pcm, int stride, int nbytes,
              void* out) { return return_value; }};
  int operator()(struct lc3_encoder* encoder, enum lc3_pcm_format fmt,
                 const void* pcm, int stride, int nbytes, void* out) {
    return body(encoder, fmt, pcm, stride, nbytes, out);
  };
};
extern struct lc3_encode lc3_encode;

// Name: lc3_encoder_size
// Params: int dt_us, int sr_hz
// Return: unsigned
struct lc3_encoder_size {
  static unsigned return_value;
  std::function<unsigned(int dt_us, int sr_hz)> body{
      [](int dt_us, int sr_hz) { return return_value; }};
  unsigned operator()(int dt_us, int sr_hz) { return body(dt_us, sr_hz); };
};
extern struct lc3_encoder_size lc3_encoder_size;

// Name: lc3_frame_bytes
// Params: int dt_us, int bitrate
// Return: int
struct lc3_frame_bytes {
  static int return_value;
  std::function<int(int dt_us, int bitrate)> body{
      [](int dt_us, int bitrate) { return return_value; }};
  int operator()(int dt_us, int bitrate) { return body(dt_us, bitrate); };
};
extern struct lc3_frame_bytes lc3_frame_bytes;

// Name: lc3_frame_samples
// Params: int dt_us, int sr_hz
// Return: int
struct lc3_frame_samples {
  static int return_value;
  std::function<int(int dt_us, int sr_hz)> body{
      [](int dt_us, int sr_hz) { return return_value; }};
  int operator()(int dt_us, int sr_hz) { return body(dt_us, sr_hz); };
};
extern struct lc3_frame_samples lc3_frame_samples;

// Name: lc3_resolve_bitrate
// Params: int dt_us, int nbytes
// Return: int
struct lc3_resolve_bitrate {
  static int return_value;
  std::function<int(int dt_us, int nbytes)> body{
      [](int dt_us, int nbytes) { return return_value; }};
  int operator()(int dt_us, int nbytes) { return body(dt_us, nbytes); };
};
extern struct lc3_resolve_bitrate lc3_resolve_bitrate;

// Name: lc3_setup_decoder
// Params: int dt_us, int sr_hz, int sr_pcm_hz, void* mem
// Return: struct lc3_decoder*
struct lc3_setup_decoder {
  static struct lc3_decoder* return_value;
  std::function<struct lc3_decoder*(int dt_us, int sr_hz, int sr_pcm_hz,
                                    void* mem)>
      body{[](int dt_us, int sr_hz, int sr_pcm_hz, void* mem) {
        return return_value;
      }};
  struct lc3_decoder* operator()(int dt_us, int sr_hz, int sr_pcm_hz,
                                 void* mem) {
    return body(dt_us, sr_hz, sr_pcm_hz, mem);
  };
};
extern struct lc3_setup_decoder lc3_setup_decoder;

// Name: lc3_setup_encoder
// Params: int dt_us, int sr_hz, int sr_pcm_hz, void* mem
// Return: struct lc3_encoder*
struct lc3_setup_encoder {
  static struct lc3_encoder* return_value;
  std::function<struct lc3_encoder*(int dt_us, int sr_hz, int sr_pcm_hz,
                                    void* mem)>
      body{[](int dt_us, int sr_hz, int sr_pcm_hz, void* mem) {
        return return_value;
      }};
  struct lc3_encoder* operator()(int dt_us, int sr_hz, int sr_pcm_hz,
                                 void* mem) {
    return body(dt_us, sr_hz, sr_pcm_hz, mem);
  };
};
extern struct lc3_setup_encoder lc3_setup_encoder;

}  // namespace embdrv_lc3
}  // namespace mock
}  // namespace test

// END mockcify generation
