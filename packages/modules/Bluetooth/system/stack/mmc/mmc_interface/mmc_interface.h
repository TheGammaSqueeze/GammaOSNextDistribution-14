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

#ifndef MMC_MMC_INTERFACE_MMC_INTERFACE_H_
#define MMC_MMC_INTERFACE_MMC_INTERFACE_H_

#include <stdint.h>

#include "mmc/proto/mmc_config.pb.h"

namespace mmc {

// An abstract interface representing either an encoder or a decoder.
class MmcInterface {
 public:
  virtual ~MmcInterface() = default;

  // Builds and configures the encoder/decoder instance.
  //
  // Returns:
  //   Input frame size accepted by the transcoder, if init succeeded.
  //   Negative errno on error, otherwise.
  virtual int init(ConfigParam config) = 0;

  // Resets the encoder/decoder instance.
  virtual void cleanup() = 0;

  // Transcodes data in |i_buf|, and stores the result in |o_buf|.
  //
  // Returns:
  //   Transcoded data length, if transcode succeeded.
  //   Negative errno on error, otherwise.
  virtual int transcode(uint8_t* i_buf, int i_len, uint8_t* o_buf,
                        int o_len) = 0;
};

}  // namespace mmc

#endif  // MMC_MMC_INTERFACE_MMC_INTERFACE_H_
