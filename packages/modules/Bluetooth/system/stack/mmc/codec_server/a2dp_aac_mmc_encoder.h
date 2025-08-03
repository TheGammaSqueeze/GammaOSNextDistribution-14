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

#ifndef MMC_CODEC_SERVER_A2DP_AAC_MMC_ENCODER_LINUX_H_
#define MMC_CODEC_SERVER_A2DP_AAC_MMC_ENCODER_LINUX_H_

extern "C" {
#include <libavcodec/avcodec.h>
}

#include "mmc/mmc_interface/mmc_interface.h"
#include "mmc/proto/mmc_config.pb.h"

namespace mmc {

// Implementation of MmcInterface.
// A2dpAacEncoder wraps FFmpeg encode libraries.
class A2dpAacEncoder : public MmcInterface {
 public:
  explicit A2dpAacEncoder();
  ~A2dpAacEncoder();

  // A2dpAacEncoder is neither copyable nor movable.
  A2dpAacEncoder(const A2dpAacEncoder&) = delete;
  A2dpAacEncoder& operator=(const A2dpAacEncoder&) = delete;

  // Inits encoder instance.
  //
  // Returns:
  //   Input pcm frame size accepted by the encoder, if init succeeded.
  //   Negative errno on error, otherwise.
  int init(ConfigParam config) override;

  // Releases encoder instance.
  void cleanup() override;

  // Encodes data from |i_buf|, and stores the result to |o_buf|.
  //
  // Returns:
  //   Encoded data length, if encode succeeded.
  //   Negative errno on error, otherwise.
  int transcode(uint8_t* i_buf, int i_len, uint8_t* o_buf, int o_len) override;

 private:
  AVCodecContext* avctx_;
  AacEncoderParam param_;
};

}  // namespace mmc

#endif  // MMC_CODEC_SERVER_A2DP_AAC_MMC_ENCODER_LINUX_H_
