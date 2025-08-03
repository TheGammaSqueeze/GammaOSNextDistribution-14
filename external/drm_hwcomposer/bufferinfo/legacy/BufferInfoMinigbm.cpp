/*
 * Copyright (C) 2018 The Android Open Source Project
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

#define LOG_TAG "hwc-bufferinfo-minigbm"

#include "BufferInfoMinigbm.h"

#include <xf86drm.h>
#include <xf86drmMode.h>

#include <cerrno>
#include <cstring>

#include "utils/log.h"
namespace android {

LEGACY_BUFFER_INFO_GETTER(BufferInfoMinigbm);

constexpr int CROS_GRALLOC_DRM_GET_FORMAT = 1;
constexpr int CROS_GRALLOC_DRM_GET_DIMENSIONS = 2;
constexpr int CROS_GRALLOC_DRM_GET_BUFFER_INFO = 4;
constexpr int CROS_GRALLOC_DRM_GET_USAGE = 5;

struct cros_gralloc0_buffer_info {
  uint32_t drm_fourcc;
  int num_fds;
  int fds[4];
  uint64_t modifier;
  int offset[4];
  int stride[4];
};

auto BufferInfoMinigbm::GetBoInfo(buffer_handle_t handle)
    -> std::optional<BufferInfo> {
  if (handle == nullptr) {
    return {};
  }

  BufferInfo bi{};

  uint32_t width{};
  uint32_t height{};
  if (gralloc_->perform(gralloc_, CROS_GRALLOC_DRM_GET_DIMENSIONS, handle,
                        &width, &height) != 0) {
    ALOGE(
        "CROS_GRALLOC_DRM_GET_DIMENSIONS operation has failed. "
        "Please ensure you are using the latest minigbm.");
    return {};
  }

  int32_t droid_format{};
  if (gralloc_->perform(gralloc_, CROS_GRALLOC_DRM_GET_FORMAT, handle,
                        &droid_format) != 0) {
    ALOGE(
        "CROS_GRALLOC_DRM_GET_FORMAT operation has failed. "
        "Please ensure you are using the latest minigbm.");
    return {};
  }

  uint32_t usage{};
  if (gralloc_->perform(gralloc_, CROS_GRALLOC_DRM_GET_USAGE, handle, &usage) !=
      0) {
    ALOGE(
        "CROS_GRALLOC_DRM_GET_USAGE operation has failed. "
        "Please ensure you are using the latest minigbm.");
    return {};
  }

  struct cros_gralloc0_buffer_info info {};
  if (gralloc_->perform(gralloc_, CROS_GRALLOC_DRM_GET_BUFFER_INFO, handle,
                        &info) != 0) {
    ALOGE(
        "CROS_GRALLOC_DRM_GET_BUFFER_INFO operation has failed. "
        "Please ensure you are using the latest minigbm.");
    return {};
  }

  bi.width = width;
  bi.height = height;

  bi.format = info.drm_fourcc;

  for (int i = 0; i < info.num_fds; i++) {
    bi.modifiers[i] = info.modifier;
    bi.prime_fds[i] = info.fds[i];
    bi.pitches[i] = info.stride[i];
    bi.offsets[i] = info.offset[i];
  }

  return bi;
}

constexpr char cros_gralloc_module_name[] = "CrOS Gralloc";

int BufferInfoMinigbm::ValidateGralloc() {
  if (strcmp(gralloc_->common.name, cros_gralloc_module_name) != 0) {
    ALOGE("Gralloc name isn't valid: Expected: \"%s\", Actual: \"%s\"",
          cros_gralloc_module_name, gralloc_->common.name);
    return -EINVAL;
  }

  if (gralloc_->perform == nullptr) {
    ALOGE(
        "CrOS gralloc has no perform call implemented. Please upgrade your "
        "minigbm.");
    return -EINVAL;
  }

  return 0;
}

}  // namespace android
