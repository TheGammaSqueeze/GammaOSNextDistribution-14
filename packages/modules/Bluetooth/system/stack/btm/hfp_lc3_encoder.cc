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

#define LOG_TAG "hfp_lc3_encoder"

#include "hfp_lc3_encoder.h"

#include <bluetooth/log.h>
#include <lc3.h>

#include <cstring>

#include "os/log.h"
#include "osi/include/allocator.h"

using namespace bluetooth;

const int HFP_LC3_PCM_BYTES = 480;
const int HFP_LC3_PKT_FRAME_LEN = 58;

static void* hfp_lc3_encoder_mem;
static lc3_encoder_t hfp_lc3_encoder;

void hfp_lc3_encoder_init() {
  if (hfp_lc3_encoder_mem) {
    log::warn("The encoder instance should have had been released.");
    osi_free(hfp_lc3_encoder_mem);
  }

  const int dt_us = 7500;
  const int sr_hz = 32000;
  const int sr_pcm_hz = 32000;
  const unsigned enc_size = lc3_encoder_size(dt_us, sr_pcm_hz);

  hfp_lc3_encoder_mem = osi_malloc(enc_size);
  hfp_lc3_encoder =
      lc3_setup_encoder(dt_us, sr_hz, sr_pcm_hz, hfp_lc3_encoder_mem);
}

void hfp_lc3_encoder_cleanup() {
  if (hfp_lc3_encoder_mem) {
    osi_free_and_reset((void**)&hfp_lc3_encoder_mem);
  }
}

uint32_t hfp_lc3_encode_frames(int16_t* input, uint8_t* output) {
  if (input == nullptr || output == nullptr) {
    log::error("Buffer is null.");
    return 0;
  }

  /* Note this only fails when wrong parameters are supplied. */
  int rc = lc3_encode(hfp_lc3_encoder, LC3_PCM_FORMAT_S16, input, 1,
                      HFP_LC3_PKT_FRAME_LEN, output);

  ASSERT(rc == 0);

  return HFP_LC3_PCM_BYTES;
}
