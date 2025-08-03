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

#define LOG_TAG "hwc-bufferinfo-mali-hisi"

#include "BufferInfoMaliHisi.h"

#include <xf86drm.h>
#include <xf86drmMode.h>

#include <cinttypes>

#include "gralloc_priv.h"
#include "utils/log.h"

#define MALI_ALIGN(value, base) (((value) + ((base)-1)) & ~((base)-1))

namespace android {

LEGACY_BUFFER_INFO_GETTER(BufferInfoMaliHisi);

#if defined(MALI_GRALLOC_INTFMT_AFBC_BASIC) && \
    defined(AFBC_FORMAT_MOD_BLOCK_SIZE_16x16)
uint64_t BufferInfoMaliHisi::ConvertGrallocFormatToDrmModifiers(uint64_t flags,
                                                                bool is_rgb) {
  uint64_t features = 0UL;

  if (flags & MALI_GRALLOC_INTFMT_AFBC_BASIC)
    features |= AFBC_FORMAT_MOD_BLOCK_SIZE_16x16;

  if (flags & MALI_GRALLOC_INTFMT_AFBC_SPLITBLK)
    features |= (AFBC_FORMAT_MOD_SPLIT | AFBC_FORMAT_MOD_SPARSE);

  if (flags & MALI_GRALLOC_INTFMT_AFBC_WIDEBLK)
    features |= AFBC_FORMAT_MOD_BLOCK_SIZE_32x8;

  if (flags & MALI_GRALLOC_INTFMT_AFBC_TILED_HEADERS)
    features |= AFBC_FORMAT_MOD_TILED;

  if (features) {
    if (is_rgb)
      features |= AFBC_FORMAT_MOD_YTR;

    return DRM_FORMAT_MOD_ARM_AFBC(features);
  }

  return 0;
}
#else
uint64_t BufferInfoMaliHisi::ConvertGrallocFormatToDrmModifiers(
    uint64_t /* flags */, bool /* is_rgb */) {
  return 0;
}
#endif

auto BufferInfoMaliHisi::GetBoInfo(buffer_handle_t handle)
    -> std::optional<BufferInfo> {
  bool is_rgb = false;

  const auto *hnd = (private_handle_t const *)handle;
  if (!hnd)
    return {};

  if (!(hnd->usage & GRALLOC_USAGE_HW_FB))
    return {};

  const uint32_t fmt = ConvertHalFormatToDrm(hnd->req_format);
  if (fmt == DRM_FORMAT_INVALID)
    return {};

  BufferInfo bi{};

  is_rgb = IsDrmFormatRgb(fmt);
  bi.modifiers[0] = ConvertGrallocFormatToDrmModifiers(hnd->internal_format,
                                                       is_rgb);

  bi.width = hnd->width;
  bi.height = hnd->height;
  bi.format = fmt;
  bi.pitches[0] = hnd->byte_stride;
  bi.prime_fds[0] = hnd->share_fd;
  bi.offsets[0] = 0;

  switch (fmt) {
    case DRM_FORMAT_YVU420: {
      int align = 128;
      if (hnd->usage &
          (GRALLOC_USAGE_SW_READ_MASK | GRALLOC_USAGE_SW_WRITE_MASK))
        align = 16;
      const int adjusted_height = MALI_ALIGN(hnd->height, 2);
      const int y_size = adjusted_height * hnd->byte_stride;
      const int vu_stride = MALI_ALIGN(hnd->byte_stride / 2, align);
      const int v_size = vu_stride * (adjusted_height / 2);

      /* V plane*/
      bi.prime_fds[1] = hnd->share_fd;
      bi.pitches[1] = vu_stride;
      bi.offsets[1] = y_size;
      /* U plane */
      bi.prime_fds[2] = hnd->share_fd;
      bi.pitches[2] = vu_stride;
      bi.offsets[2] = y_size + v_size;
      break;
    }
    default:
      break;
  }

  return bi;
}

}  // namespace android
