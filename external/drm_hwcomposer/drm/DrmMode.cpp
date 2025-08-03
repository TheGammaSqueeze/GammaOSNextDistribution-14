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

#include "DrmMode.h"

#include <cstring>

#include "DrmDevice.h"

namespace android {

DrmMode::DrmMode(drmModeModeInfoPtr m) : mode_(*m){};

bool DrmMode::operator==(const drmModeModeInfo &m) const {
  return memcmp(&m, &mode_, offsetof(drmModeModeInfo, name)) == 0;
}

auto DrmMode::CreateModeBlob(const DrmDevice &drm)
    -> DrmModeUserPropertyBlobUnique {
  struct drm_mode_modeinfo drm_mode = {};
  /* drm_mode_modeinfo and drmModeModeInfo should be identical
   * At least libdrm does the same memcpy in drmModeAttachMode();
   */
  memcpy(&drm_mode, &mode_, sizeof(struct drm_mode_modeinfo));

  return drm.RegisterUserPropertyBlob(&drm_mode,
                                      sizeof(struct drm_mode_modeinfo));
}

}  // namespace android
