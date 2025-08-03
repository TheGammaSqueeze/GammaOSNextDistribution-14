/*
 * Copyright (C) 2023 The Android Open Source Project
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

#define LOG_TAG "hfp_lc3_decoder"

#include "hfp_lc3_decoder.h"

#include <base/logging.h>
#include <bluetooth/log.h>
#include <lc3.h>

#include <cstring>

#include "os/log.h"
#include "osi/include/allocator.h"

using namespace bluetooth;

const int HFP_LC3_H2_HEADER_LEN = 2;
const int HFP_LC3_PKT_FRAME_LEN = 58;
const int HFP_LC3_PCM_BYTES = 480;

static void* hfp_lc3_decoder_mem;
static lc3_decoder_t hfp_lc3_decoder;

bool hfp_lc3_decoder_init() {
  if (hfp_lc3_decoder_mem) {
    log::warn("The decoder instance should have had been released.");
    osi_free(hfp_lc3_decoder_mem);
  }

  const int dt_us = 7500;
  const int sr_hz = 32000;
  const int sr_pcm_hz = 32000;
  const unsigned dec_size = lc3_decoder_size(dt_us, sr_pcm_hz);

  hfp_lc3_decoder_mem = osi_malloc(dec_size);
  hfp_lc3_decoder =
      lc3_setup_decoder(dt_us, sr_hz, sr_pcm_hz, hfp_lc3_decoder_mem);

  return true;
}

void hfp_lc3_decoder_cleanup() {
  if (hfp_lc3_decoder_mem) {
    osi_free_and_reset((void**)&hfp_lc3_decoder_mem);
  }
}

bool hfp_lc3_decoder_decode_packet(const uint8_t* i_buf, int16_t* o_buf,
                                   size_t out_len) {
  if (o_buf == nullptr || out_len < HFP_LC3_PCM_BYTES) {
    log::error("Output buffer size {} is less than LC3 frame size {}", out_len,
               HFP_LC3_PCM_BYTES);
    return false;
  }

  const uint8_t* frame = i_buf ? i_buf + HFP_LC3_H2_HEADER_LEN : nullptr;

  /* Note this only fails when wrong parameters are supplied. */
  int rc = lc3_decode(hfp_lc3_decoder, frame, HFP_LC3_PKT_FRAME_LEN,
                      LC3_PCM_FORMAT_S16, o_buf, 1);

  ASSERT(rc == 0 || rc == 1);

  return !rc;
}
