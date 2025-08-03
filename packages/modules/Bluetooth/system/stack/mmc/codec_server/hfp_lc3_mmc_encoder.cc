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

#include "mmc/codec_server/hfp_lc3_mmc_encoder.h"

#include <base/logging.h>
#include <lc3.h>

#include <algorithm>

#include "mmc/codec_server/lc3_utils.h"
#include "mmc/proto/mmc_config.pb.h"
#include "osi/include/allocator.h"

namespace mmc {

HfpLc3Encoder::HfpLc3Encoder() : hfp_lc3_encoder_mem_(nullptr) {}

HfpLc3Encoder::~HfpLc3Encoder() { cleanup(); }

int HfpLc3Encoder::init(ConfigParam config) {
  cleanup();

  if (!config.has_hfp_lc3_encoder_param()) {
    LOG(ERROR) << "HFP LC3 encoder params are not set";
    return -EINVAL;
  }

  param_ = config.hfp_lc3_encoder_param();
  int dt_us = param_.dt_us();
  int sr_hz = param_.sr_hz();
  int sr_pcm_hz = param_.sr_pcm_hz();
  const unsigned enc_size = lc3_encoder_size(dt_us, sr_pcm_hz);

  hfp_lc3_encoder_mem_ = osi_malloc(enc_size);

  hfp_lc3_encoder_ =
      lc3_setup_encoder(dt_us, sr_hz, sr_pcm_hz, hfp_lc3_encoder_mem_);

  if (hfp_lc3_encoder_ == nullptr) {
    LOG(ERROR) << "Wrong parameters provided";
    return -EINVAL;
  }

  return HFP_LC3_PCM_BYTES;
}

void HfpLc3Encoder::cleanup() {
  if (hfp_lc3_encoder_mem_) {
    osi_free_and_reset((void**)&hfp_lc3_encoder_mem_);
    LOG(INFO) << "Released the encoder instance";
  }
}

int HfpLc3Encoder::transcode(uint8_t* i_buf, int i_len, uint8_t* o_buf,
                             int o_len) {
  if (i_buf == nullptr || o_buf == nullptr) {
    LOG(ERROR) << "Buffer is null";
    return -EINVAL;
  }

  /* Note this only fails when wrong parameters are supplied. */
  int rc = lc3_encode(hfp_lc3_encoder_, MapLc3PcmFmt(param_.fmt()), i_buf,
                      param_.stride(), HFP_LC3_PKT_FRAME_LEN, o_buf);

  if (rc != 0) {
    LOG(WARNING) << "Wrong encode parameters";
    std::fill(o_buf, o_buf + o_len, 0);
  }

  return HFP_LC3_PKT_FRAME_LEN;
}

}  // namespace mmc
