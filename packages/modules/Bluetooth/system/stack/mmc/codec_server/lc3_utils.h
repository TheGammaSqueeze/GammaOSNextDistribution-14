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

#ifndef MMC_CODEC_SERVER_LC3_UTILS_H_
#define MMC_CODEC_SERVER_LC3_UTILS_H_

#include <base/logging.h>
#include <lc3.h>

#include "mmc/proto/mmc_config.pb.h"

namespace mmc {

// HFP LC3 constants.
const int HFP_LC3_H2_HEADER_LEN = 2;
const int HFP_LC3_PKT_FRAME_LEN = 58;
const int HFP_LC3_PCM_BYTES = 480;

// Helper that maps MMC pcm format to lc3 pcm format.
inline lc3_pcm_format MapLc3PcmFmt(Lc3Param_PcmFmt fmt) {
  switch (fmt) {
    case Lc3Param::kLc3PcmFormatS16:
      return LC3_PCM_FORMAT_S16;
    case Lc3Param::kLc3PcmFormatS24:
      return LC3_PCM_FORMAT_S24;
    default:
      LOG(INFO)
          << "No corresponding LC3 PCM format, return `LC3_PCM_FORMAT_S16`.";
      return LC3_PCM_FORMAT_S16;
  }
}

}  // namespace mmc
#endif  // MMC_CODEC_SERVER_LC3_UTILS_H_
