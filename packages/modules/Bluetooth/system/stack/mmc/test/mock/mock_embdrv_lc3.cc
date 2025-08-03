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
/*
 * Generated mock file from original source file
 *   Functions generated:10
 *
 *  mockcify.pl ver 0.6.1
 */

#include <cstdint>
#include <functional>
#include <map>
#include <string>

// Mock include file to share data between tests and mock
#include "mmc/test/mock/mock_embdrv_lc3.h"

// Original usings

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace embdrv_lc3 {

// Function state capture and return values, if needed
struct lc3_decode lc3_decode;
struct lc3_decoder_size lc3_decoder_size;
struct lc3_delay_samples lc3_delay_samples;
struct lc3_encode lc3_encode;
struct lc3_encoder_size lc3_encoder_size;
struct lc3_frame_bytes lc3_frame_bytes;
struct lc3_frame_samples lc3_frame_samples;
struct lc3_resolve_bitrate lc3_resolve_bitrate;
struct lc3_setup_decoder lc3_setup_decoder;
struct lc3_setup_encoder lc3_setup_encoder;

}  // namespace embdrv_lc3
}  // namespace mock
}  // namespace test

// Mocked function return values, if any
namespace test {
namespace mock {
namespace embdrv_lc3 {

int lc3_decode::return_value = 0;
unsigned lc3_decoder_size::return_value = 0;
int lc3_delay_samples::return_value = 0;
int lc3_encode::return_value = 0;
unsigned lc3_encoder_size::return_value = 0;
int lc3_frame_bytes::return_value = 0;
int lc3_frame_samples::return_value = 0;
int lc3_resolve_bitrate::return_value = 0;
struct lc3_decoder* lc3_setup_decoder::return_value = nullptr;
struct lc3_encoder* lc3_setup_encoder::return_value = nullptr;

}  // namespace embdrv_lc3
}  // namespace mock
}  // namespace test

// Mocked functions, if any
int lc3_decode(struct lc3_decoder* decoder, const void* in, int nbytes,
               enum lc3_pcm_format fmt, void* pcm, int stride) {
  inc_func_call_count(__func__);
  return test::mock::embdrv_lc3::lc3_decode(decoder, in, nbytes, fmt, pcm,
                                            stride);
}
unsigned lc3_decoder_size(int dt_us, int sr_hz) {
  inc_func_call_count(__func__);
  return test::mock::embdrv_lc3::lc3_decoder_size(dt_us, sr_hz);
}
int lc3_delay_samples(int dt_us, int sr_hz) {
  inc_func_call_count(__func__);
  return test::mock::embdrv_lc3::lc3_delay_samples(dt_us, sr_hz);
}
int lc3_encode(struct lc3_encoder* encoder, enum lc3_pcm_format fmt,
               const void* pcm, int stride, int nbytes, void* out) {
  inc_func_call_count(__func__);
  return test::mock::embdrv_lc3::lc3_encode(encoder, fmt, pcm, stride, nbytes,
                                            out);
}
unsigned lc3_encoder_size(int dt_us, int sr_hz) {
  inc_func_call_count(__func__);
  return test::mock::embdrv_lc3::lc3_encoder_size(dt_us, sr_hz);
}
int lc3_frame_bytes(int dt_us, int bitrate) {
  inc_func_call_count(__func__);
  return test::mock::embdrv_lc3::lc3_frame_bytes(dt_us, bitrate);
}
int lc3_frame_samples(int dt_us, int sr_hz) {
  inc_func_call_count(__func__);
  return test::mock::embdrv_lc3::lc3_frame_samples(dt_us, sr_hz);
}
int lc3_resolve_bitrate(int dt_us, int nbytes) {
  inc_func_call_count(__func__);
  return test::mock::embdrv_lc3::lc3_resolve_bitrate(dt_us, nbytes);
}
struct lc3_decoder* lc3_setup_decoder(int dt_us, int sr_hz, int sr_pcm_hz,
                                      void* mem) {
  inc_func_call_count(__func__);
  return test::mock::embdrv_lc3::lc3_setup_decoder(dt_us, sr_hz, sr_pcm_hz,
                                                   mem);
}
struct lc3_encoder* lc3_setup_encoder(int dt_us, int sr_hz, int sr_pcm_hz,
                                      void* mem) {
  inc_func_call_count(__func__);
  return test::mock::embdrv_lc3::lc3_setup_encoder(dt_us, sr_hz, sr_pcm_hz,
                                                   mem);
}
// Mocked functions complete
// END mockcify generation
