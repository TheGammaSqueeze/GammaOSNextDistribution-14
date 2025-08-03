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

#pragma once

#include <drm/drm_fourcc.h>
#include <hardware/gralloc.h>

#include <optional>

#include "BufferInfo.h"
#include "drm/DrmDevice.h"

#ifndef DRM_FORMAT_INVALID
#define DRM_FORMAT_INVALID 0
#endif

namespace android {

using BufferUniqueId = uint64_t;

class BufferInfoGetter {
 public:
  virtual ~BufferInfoGetter() = default;

  virtual auto GetBoInfo(buffer_handle_t handle)
      -> std::optional<BufferInfo> = 0;

  virtual std::optional<BufferUniqueId> GetUniqueId(buffer_handle_t handle);

  static BufferInfoGetter *GetInstance();

  static bool IsDrmFormatRgb(uint32_t drm_format);
};

class LegacyBufferInfoGetter : public BufferInfoGetter {
 public:
  using BufferInfoGetter::BufferInfoGetter;

  int Init();

  virtual int ValidateGralloc() {
    return 0;
  }

  static std::unique_ptr<LegacyBufferInfoGetter> CreateInstance();

  static uint32_t ConvertHalFormatToDrm(uint32_t hal_format);

  // NOLINTNEXTLINE:(readability-identifier-naming)
  const gralloc_module_t *gralloc_;
};

#ifdef DISABLE_LEGACY_GETTERS
#define LEGACY_BUFFER_INFO_GETTER(getter_)
#else
// NOLINTNEXTLINE(cppcoreguidelines-macro-usage)
#define LEGACY_BUFFER_INFO_GETTER(getter_)                             \
  std::unique_ptr<LegacyBufferInfoGetter>                              \
  LegacyBufferInfoGetter::CreateInstance() {                           \
    auto instance = std::make_unique<getter_>();                       \
    if (instance) {                                                    \
      int err = instance->Init();                                      \
      if (err) {                                                       \
        ALOGE("Failed to initialize the " #getter_ " getter %d", err); \
        instance.reset();                                              \
      }                                                                \
      err = instance->ValidateGralloc();                               \
      if (err) {                                                       \
        instance.reset();                                              \
      }                                                                \
    }                                                                  \
    return std::move(instance);                                        \
  }
#endif

}  // namespace android
