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

#define LOG_TAG "hwc-bufferinfo-mali-mediatek"

#include "BufferInfoMaliMediatek.h"

#include <hardware/gralloc.h>
#include <stdatomic.h>
#include <xf86drm.h>
#include <xf86drmMode.h>

#include <cinttypes>

#include "gralloc_priv.h"
#include "utils/log.h"

namespace android {

LEGACY_BUFFER_INFO_GETTER(BufferInfoMaliMediatek);

auto BufferInfoMaliMediatek::GetBoInfo(buffer_handle_t handle)
    -> std::optional<BufferInfo> {
  const auto *hnd = (private_handle_t const *)handle;
  if (!hnd)
    return {};

  const uint32_t fmt = ConvertHalFormatToDrm(hnd->req_format);
  if (fmt == DRM_FORMAT_INVALID)
    return {};

  BufferInfo bi{};

  bi.width = hnd->width;
  bi.height = hnd->height;
  bi.format = fmt;
  bi.prime_fds[0] = hnd->share_fd;
  bi.pitches[0] = hnd->byte_stride;
  bi.offsets[0] = 0;

  return bi;
}

}  // namespace android
