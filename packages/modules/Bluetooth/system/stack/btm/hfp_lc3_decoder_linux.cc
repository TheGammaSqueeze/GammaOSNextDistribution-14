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

#include <bluetooth/log.h>

#include <algorithm>

#include "hfp_lc3_decoder.h"
#include "mmc/codec_client/codec_client.h"
#include "mmc/proto/mmc_config.pb.h"
#include "os/log.h"

using namespace bluetooth;

const int HFP_LC3_H2_HEADER_LEN = 2;
const int HFP_LC3_PKT_FRAME_LEN = 58;
const int HFP_LC3_PCM_BYTES = 480;

static mmc::CodecClient* client = nullptr;
static const uint8_t plc_buf[HFP_LC3_H2_HEADER_LEN + HFP_LC3_PKT_FRAME_LEN] = {
    0};

bool hfp_lc3_decoder_init() {
  hfp_lc3_decoder_cleanup();
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
  *config.mutable_hfp_lc3_decoder_param() = param;

  int ret = client->init(config);
  if (ret < 0) {
    log::error("Init failed with error message, {}", strerror(-ret));
    return false;
  }

  return true;
}

void hfp_lc3_decoder_cleanup() {
  if (client) {
    client->cleanup();
    delete client;
    client = nullptr;
  }
}

bool hfp_lc3_decoder_decode_packet(const uint8_t* i_buf, int16_t* o_buf,
                                   size_t out_len) {
  if (o_buf == nullptr || out_len < HFP_LC3_PCM_BYTES) {
    log::error("Output buffer size {} is less than LC3 frame size {}", out_len,
               HFP_LC3_PCM_BYTES);
    return false;
  }

  if (!client) {
    log::error("CodecClient has not been initialized");
    return false;
  }

  // Pass zeros to MMC when i_buf is nullptr.
  const uint8_t* frame = i_buf ? i_buf : plc_buf;

  // One extra byte in the beginning to indicate whether PLC was conducted.
  uint8_t* o_packet = new uint8_t[out_len + 1];

  int rc = client->transcode((uint8_t*)frame,
                             HFP_LC3_PKT_FRAME_LEN + HFP_LC3_H2_HEADER_LEN,
                             o_packet, out_len + 1);

  if (rc < 0) {
    log::warn("Decode failed with error message, {}", strerror(-rc));
    return false;
  }

  bool plc_conducted = o_packet[0];

  std::copy(o_packet + 1, o_packet + 1 + out_len, (uint8_t*)o_buf);

  delete[] o_packet;
  return !plc_conducted;
}
