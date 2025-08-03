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

#define LOG_TAG "hwc-drm-crtc"

#include "DrmCrtc.h"

#include <utils/log.h>
#include <xf86drmMode.h>

#include <cstdint>

#include "DrmDevice.h"

namespace android {

static int GetCrtcProperty(const DrmDevice &dev, const DrmCrtc &crtc,
                           const char *prop_name, DrmProperty *property) {
  return dev.GetProperty(crtc.GetId(), DRM_MODE_OBJECT_CRTC, prop_name,
                         property);
}

auto DrmCrtc::CreateInstance(DrmDevice &dev, uint32_t crtc_id, uint32_t index)
    -> std::unique_ptr<DrmCrtc> {
  auto crtc = MakeDrmModeCrtcUnique(*dev.GetFd(), crtc_id);
  if (!crtc) {
    ALOGE("Failed to get CRTC %d", crtc_id);
    return {};
  }

  auto c = std::unique_ptr<DrmCrtc>(new DrmCrtc(std::move(crtc), index));

  int ret = GetCrtcProperty(dev, *c, "ACTIVE", &c->active_property_);
  if (ret != 0) {
    ALOGE("Failed to get ACTIVE property");
    return {};
  }

  ret = GetCrtcProperty(dev, *c, "MODE_ID", &c->mode_property_);
  if (ret != 0) {
    ALOGE("Failed to get MODE_ID property");
    return {};
  }

  ret = GetCrtcProperty(dev, *c, "OUT_FENCE_PTR", &c->out_fence_ptr_property_);
  if (ret != 0) {
    ALOGE("Failed to get OUT_FENCE_PTR property");
    return {};
  }

  ret = GetCrtcProperty(dev, *c, "CTM", &c->ctm_property_);
  if (ret != 0) {
    ALOGV("Missing optional CTM property");
  }

  return c;
}

}  // namespace android
