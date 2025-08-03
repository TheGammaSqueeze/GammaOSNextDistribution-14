/*
 * Copyright (C) 2019 The Android Open Source Project
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

#define LOG_TAG "hwc-bufferinfo-mali-meson"

#include "BufferInfoMaliMeson.h"

#include <xf86drm.h>
#include <xf86drmMode.h>

#include <cinttypes>

#include "gralloc_priv.h"
#include "utils/log.h"

namespace android {

LEGACY_BUFFER_INFO_GETTER(BufferInfoMaliMeson);

#if defined(MALI_GRALLOC_INTFMT_AFBC_BASIC) && \
    defined(AFBC_FORMAT_MOD_BLOCK_SIZE_16x16)
uint64_t BufferInfoMaliMeson::ConvertGrallocFormatToDrmModifiers(
    uint64_t flags) {
  uint64_t features = 0UL;

  if (flags & MALI_GRALLOC_INTFMT_AFBC_BASIC) {
    if (flags & MALI_GRALLOC_INTFMT_AFBC_WIDEBLK)
      features |= AFBC_FORMAT_MOD_BLOCK_SIZE_32x8;
    else
      features |= AFBC_FORMAT_MOD_BLOCK_SIZE_16x16;
  }

  if (flags & MALI_GRALLOC_INTFMT_AFBC_SPLITBLK)
    features |= (AFBC_FORMAT_MOD_SPLIT | AFBC_FORMAT_MOD_SPARSE);

  if (flags & MALI_GRALLOC_INTFMT_AFBC_TILED_HEADERS)
    features |= AFBC_FORMAT_MOD_TILED;

  if (features)
    return DRM_FORMAT_MOD_ARM_AFBC(features | AFBC_FORMAT_MOD_YTR);

  return 0;
}
#else
uint64_t BufferInfoMaliMeson::ConvertGrallocFormatToDrmModifiers(
    uint64_t /* flags */) {
  return 0;
}
#endif

auto BufferInfoMaliMeson::GetBoInfo(buffer_handle_t handle)
    -> std::optional<BufferInfo> {
  const auto *hnd = (private_handle_t const *)handle;
  if (!hnd)
    return {};

  if (!(hnd->usage & GRALLOC_USAGE_HW_FB))
    return {};

  const uint32_t fmt = ConvertHalFormatToDrm(hnd->req_format);
  if (fmt == DRM_FORMAT_INVALID)
    return {};

  BufferInfo bi{};

  bi.modifiers[0] = BufferInfoMaliMeson::ConvertGrallocFormatToDrmModifiers(
      hnd->internal_format);

  bi.width = hnd->width;
  bi.height = hnd->height;
  bi.format = fmt;
  bi.prime_fds[0] = hnd->share_fd;
  bi.pitches[0] = hnd->byte_stride;
  bi.offsets[0] = 0;

  return bi;
}

}  // namespace android
