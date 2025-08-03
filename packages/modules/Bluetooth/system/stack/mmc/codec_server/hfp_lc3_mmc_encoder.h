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

#ifndef MMC_CODEC_SERVER_HFP_LC3_MMC_ENCODER_H_
#define MMC_CODEC_SERVER_HFP_LC3_MMC_ENCODER_H_

#include <lc3.h>

#include "mmc/mmc_interface/mmc_interface.h"
#include "mmc/proto/mmc_config.pb.h"

namespace mmc {

// Implementation of MmcInterface.
// HfpLc3Encoder wraps lc3 encode libraries.
class HfpLc3Encoder : public MmcInterface {
 public:
  explicit HfpLc3Encoder();
  ~HfpLc3Encoder();

  // HfpLc3Encoder is neither copyable nor movable.
  HfpLc3Encoder(const HfpLc3Encoder&) = delete;
  HfpLc3Encoder& operator=(const HfpLc3Encoder&) = delete;

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
  void* hfp_lc3_encoder_mem_;
  lc3_encoder_t hfp_lc3_encoder_;
  Lc3Param param_;
};

}  // namespace mmc

#endif  // MMC_CODEC_SERVER_HFP_LC3_MMC_ENCODER_H_
