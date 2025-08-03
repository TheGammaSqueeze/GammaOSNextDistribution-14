/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include <hardware/hardware.h>
#include <hardware/hwcomposer.h>

#include <cmath>
#include <cstdbool>
#include <cstdint>
#include <optional>
#include <vector>

#include "bufferinfo/BufferInfo.h"
#include "drm/DrmFbImporter.h"
#include "utils/fd.h"

namespace android {

class DrmFbIdHandle;

enum LayerTransform : uint32_t {
  kIdentity = 0,
  kFlipH = 1 << 0,
  kFlipV = 1 << 1,
  kRotate90 = 1 << 2,
  kRotate180 = 1 << 3,
  kRotate270 = 1 << 4,
};

struct PresentInfo {
  LayerTransform transform{};
  uint16_t alpha = UINT16_MAX;
  hwc_frect_t source_crop{};
  hwc_rect_t display_frame{};

  bool RequireScalingOrPhasing() const {
    const float src_width = source_crop.right - source_crop.left;
    const float src_height = source_crop.bottom - source_crop.top;

    auto dest_width = float(display_frame.right - display_frame.left);
    auto dest_height = float(display_frame.bottom - display_frame.top);

    auto scaling = src_width != dest_width || src_height != dest_height;
    auto phasing = (source_crop.left - std::floor(source_crop.left) != 0) ||
                   (source_crop.top - std::floor(source_crop.top) != 0);
    return scaling || phasing;
  }
};

struct LayerData {
  std::optional<BufferInfo> bi;
  std::shared_ptr<DrmFbIdHandle> fb;
  PresentInfo pi;
  SharedFd acquire_fence;
};

}  // namespace android
