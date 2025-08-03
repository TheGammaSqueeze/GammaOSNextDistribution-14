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
#include <cstdio>
#include <string>

#include "DrmUnique.h"

namespace android {

class DrmDevice;

class DrmMode {
 public:
  DrmMode() = default;
  explicit DrmMode(drmModeModeInfoPtr m);

  bool operator==(const drmModeModeInfo &m) const;

  auto &GetRawMode() const {
    return mode_;
  }

  auto GetVRefresh() const {
    if (mode_.clock == 0) {
      return float(mode_.vrefresh);
    }
    // Always recalculate refresh to report correct float rate
    return static_cast<float>(mode_.clock) /
           (float)(mode_.vtotal * mode_.htotal) * 1000.0F;
  }

  auto GetName() const {
    return std::string(mode_.name) + "@" + std::to_string(GetVRefresh());
  }

  auto CreateModeBlob(const DrmDevice &drm) -> DrmModeUserPropertyBlobUnique;

 private:
  drmModeModeInfo mode_;
};
}  // namespace android
