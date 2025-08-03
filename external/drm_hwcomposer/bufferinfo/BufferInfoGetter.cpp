/*
 * Copyright (C) 2020 The Android Open Source Project
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

#define LOG_TAG "hwc-buffer-info-getter"

#include "BufferInfoGetter.h"

#if __ANDROID_API__ >= 30
#include "BufferInfoMapperMetadata.h"
#endif

#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>
#include <xf86drm.h>
#include <xf86drmMode.h>

#include <mutex>

#include "utils/log.h"
#include "utils/properties.h"

namespace android {

BufferInfoGetter *BufferInfoGetter::GetInstance() {
  static std::unique_ptr<BufferInfoGetter> inst;
  if (!inst) {
#if __ANDROID_API__ >= 30 && defined(USE_IMAPPER4_METADATA_API)
    inst.reset(BufferInfoMapperMetadata::CreateInstance());
    if (!inst) {
      ALOGW(
          "Generic buffer getter is not available. Falling back to legacy...");
    }
#endif
    if (!inst) {
      inst = LegacyBufferInfoGetter::CreateInstance();
    }
  }

  return inst.get();
}

std::optional<BufferUniqueId> BufferInfoGetter::GetUniqueId(
    buffer_handle_t handle) {
  struct stat sb {};
  if (fstat(handle->data[0], &sb) != 0) {
    return {};
  }

  if (sb.st_size == 0) {
    return {};
  }

  return static_cast<BufferUniqueId>(sb.st_ino);
}

int LegacyBufferInfoGetter::Init() {
  const int ret = hw_get_module(
      GRALLOC_HARDWARE_MODULE_ID,
      // NOLINTNEXTLINE(cppcoreguidelines-pro-type-reinterpret-cast)
      reinterpret_cast<const hw_module_t **>(&gralloc_));
  if (ret != 0) {
    ALOGE("Failed to open gralloc module");
    return ret;
  }

  ALOGI("Using %s gralloc module: %s\n", gralloc_->common.name,
        gralloc_->common.author);

  return 0;
}

uint32_t LegacyBufferInfoGetter::ConvertHalFormatToDrm(uint32_t hal_format) {
  switch (hal_format) {
    case HAL_PIXEL_FORMAT_RGB_888:
      return DRM_FORMAT_BGR888;
    case HAL_PIXEL_FORMAT_BGRA_8888:
      return DRM_FORMAT_ARGB8888;
    case HAL_PIXEL_FORMAT_RGBX_8888:
      return DRM_FORMAT_XBGR8888;
    case HAL_PIXEL_FORMAT_RGBA_8888:
      return DRM_FORMAT_ABGR8888;
    case HAL_PIXEL_FORMAT_RGB_565:
      return DRM_FORMAT_BGR565;
    case HAL_PIXEL_FORMAT_YV12:
      return DRM_FORMAT_YVU420;
    case HAL_PIXEL_FORMAT_RGBA_1010102:
      return DRM_FORMAT_ABGR2101010;
    default:
      ALOGE("Cannot convert hal format to drm format %u", hal_format);
      return DRM_FORMAT_INVALID;
  }
}

bool BufferInfoGetter::IsDrmFormatRgb(uint32_t drm_format) {
  switch (drm_format) {
    case DRM_FORMAT_ARGB8888:
    case DRM_FORMAT_XBGR8888:
    case DRM_FORMAT_ABGR8888:
    case DRM_FORMAT_BGR888:
    case DRM_FORMAT_BGR565:
    case DRM_FORMAT_ABGR2101010:
      return true;
    default:
      return false;
  }
}

__attribute__((weak)) std::unique_ptr<LegacyBufferInfoGetter>
LegacyBufferInfoGetter::CreateInstance() {
  ALOGE("No legacy buffer info getters available");
  return nullptr;
}

}  // namespace android
