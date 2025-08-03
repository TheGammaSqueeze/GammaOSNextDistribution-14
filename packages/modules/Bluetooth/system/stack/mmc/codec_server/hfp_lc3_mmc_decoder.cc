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

#include "mmc/codec_server/hfp_lc3_mmc_decoder.h"

#include <base/logging.h>
#include <lc3.h>

#include "mmc/codec_server/lc3_utils.h"
#include "mmc/proto/mmc_config.pb.h"
#include "osi/include/allocator.h"

namespace mmc {

HfpLc3Decoder::HfpLc3Decoder() : hfp_lc3_decoder_mem_(nullptr) {}

HfpLc3Decoder::~HfpLc3Decoder() { cleanup(); }

int HfpLc3Decoder::init(ConfigParam config) {
  cleanup();

  if (!config.has_hfp_lc3_decoder_param()) {
    LOG(ERROR) << "HFP LC3 decoder params are not set";
    return -EINVAL;
  }

  param_ = config.hfp_lc3_decoder_param();
  int dt_us = param_.dt_us();
  int sr_hz = param_.sr_hz();
  int sr_pcm_hz = param_.sr_pcm_hz();
  const unsigned dec_size = lc3_decoder_size(dt_us, sr_pcm_hz);

  hfp_lc3_decoder_mem_ = osi_malloc(dec_size);

  hfp_lc3_decoder_ =
      lc3_setup_decoder(dt_us, sr_hz, sr_pcm_hz, hfp_lc3_decoder_mem_);

  if (hfp_lc3_decoder_ == nullptr) {
    LOG(ERROR) << "Wrong parameters provided";
    return -EINVAL;
  }

  return HFP_LC3_PKT_FRAME_LEN;
}

void HfpLc3Decoder::cleanup() {
  if (hfp_lc3_decoder_mem_) {
    osi_free_and_reset((void**)&hfp_lc3_decoder_mem_);
    LOG(INFO) << "Released the decoder instance";
  }
}

int HfpLc3Decoder::transcode(uint8_t* i_buf, int i_len, uint8_t* o_buf,
                             int o_len) {
  if (o_buf == nullptr || o_len < HFP_LC3_PCM_BYTES + 1) {
    LOG(ERROR) << "Output buffer size is less than LC3 frame size";
    return -EINVAL;
  }

  // Check header to decide whether it's PLC.
  uint8_t* in_frame =
      (i_buf[0] || i_buf[1]) ? i_buf + HFP_LC3_H2_HEADER_LEN : nullptr;

  // First byte is reserved to indicate PLC.
  uint8_t* out_frame = o_buf + 1;

  /* Note this only fails when wrong parameters are supplied. */
  int rc = lc3_decode(hfp_lc3_decoder_, in_frame, HFP_LC3_PKT_FRAME_LEN,
                      MapLc3PcmFmt(param_.fmt()), out_frame, param_.stride());

  if (rc != 0 && rc != 1) {
    LOG(WARNING) << "Wrong decode parameters";
    std::fill(o_buf, o_buf + o_len, 0);
  } else
    o_buf[0] = rc;
  return HFP_LC3_PCM_BYTES + 1;
}

}  // namespace mmc
