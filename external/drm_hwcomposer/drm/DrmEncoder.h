/*
 * Copyright (C) 2015 The Android Open Source Project
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

#pragma once

#include <xf86drmMode.h>

#include <cstdint>
#include <set>
#include <vector>

#include "DrmCrtc.h"
#include "DrmDisplayPipeline.h"

namespace android {

class DrmEncoder : public PipelineBindable<DrmEncoder> {
 public:
  static auto CreateInstance(DrmDevice &dev, uint32_t encoder_id,
                             uint32_t index) -> std::unique_ptr<DrmEncoder>;

  DrmEncoder(const DrmEncoder &) = delete;
  DrmEncoder &operator=(const DrmEncoder &) = delete;

  auto GetId() const {
    return enc_->encoder_id;
  };

  auto GetIndexInResArray() const {
    return index_in_res_array_;
  }

  auto CanClone(DrmEncoder &encoder) {
    return (enc_->possible_clones & (1 << encoder.GetIndexInResArray())) != 0;
  }

  auto SupportsCrtc(DrmCrtc &crtc) {
    return (enc_->possible_crtcs & (1 << crtc.GetIndexInResArray())) != 0;
  }

  auto GetCurrentCrtcId() {
    return enc_->crtc_id;
  }

 private:
  DrmEncoder(DrmModeEncoderUnique enc, uint32_t index)
      : enc_(std::move(enc)), index_in_res_array_(index){};

  DrmModeEncoderUnique enc_;

  const uint32_t index_in_res_array_;
};
}  // namespace android
