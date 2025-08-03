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

#include <bluetooth/log.h>

#include "hfp_lc3_encoder.h"
#include "mmc/codec_client/codec_client.h"
#include "mmc/proto/mmc_config.pb.h"
#include "os/log.h"

using namespace bluetooth;

const int HFP_LC3_PCM_BYTES = 480;
const int HFP_LC3_PKT_FRAME_LEN = 58;

static mmc::CodecClient* client = nullptr;

void hfp_lc3_encoder_init() {
  hfp_lc3_encoder_cleanup();
  client = new mmc::CodecClient;

  const int dt_us = 7500;
  const int sr_hz = 32000;
  const int sr_pcm_hz = 32000;

  mmc::Lc3Param param;
  param.set_dt_us(dt_us);
  param.set_sr_hz(sr_hz);
  param.set_sr_pcm_hz(sr_pcm_hz);
  param.set_stride(1);
  param.set_fmt(mmc::Lc3Param::kLc3PcmFormatS16);

  mmc::ConfigParam config;
  *config.mutable_hfp_lc3_encoder_param() = param;

  int ret = client->init(config);
  if (ret < 0) {
    log::error("Init failed with error message, {}", strerror(-ret));
  }
  return;
}

void hfp_lc3_encoder_cleanup() {
  if (client) {
    client->cleanup();
    delete client;
    client = nullptr;
  }
}

uint32_t hfp_lc3_encode_frames(int16_t* input, uint8_t* output) {
  if (input == nullptr || output == nullptr) {
    log::error("Buffer is null");
    return 0;
  }

  if (!client) {
    log::error("CodecClient has not been initialized");
    return 0;
  }

  int rc = client->transcode((uint8_t*)input, HFP_LC3_PCM_BYTES, output,
                             HFP_LC3_PKT_FRAME_LEN);

  if (rc < 0) {
    log::warn("Encode failed with error message, {}", strerror(-rc));
    return 0;
  }

  return HFP_LC3_PCM_BYTES;
}
