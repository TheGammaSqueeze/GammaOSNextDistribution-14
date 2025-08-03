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

#define LOG_TAG "hwc-bufferinfo-libdrm"

#include "BufferInfoLibdrm.h"

#include <gralloc_handle.h>
#include <hardware/gralloc.h>
#include <xf86drm.h>
#include <xf86drmMode.h>

#include <mutex>

#include "utils/log.h"
#include "utils/properties.h"

namespace android {

LEGACY_BUFFER_INFO_GETTER(BufferInfoLibdrm);

enum chroma_order {
  kYCbCr,
  kYCrCb,
};

struct DroidYuvFormat {
  /* Lookup keys */
  uint32_t native;                /* HAL_PIXEL_FORMAT_ */
  enum chroma_order chroma_order; /* chroma order is {Cb, Cr} or {Cr, Cb} */
  size_t chroma_step; /* Distance in bytes between subsequent chroma pixels. */

  /* Result */
  int fourcc; /* DRM_FORMAT_ */
};

#ifndef DRM_FORMAT_XYUV8888
#define DRM_FORMAT_XYUV8888 \
  fourcc_code('X', 'Y', 'U', 'V') /* [31:0] X:Y:Cb:Cr 8:8:8:8 little endian */
#endif

/* The following table is used to look up a DRI image FourCC based
 * on native format and information contained in android_ycbcr struct. */
static const struct DroidYuvFormat kDroidYuvFormats[] = {
    /* Native format, YCrCb, Chroma step, DRI image FourCC */
    {HAL_PIXEL_FORMAT_YCbCr_420_888, kYCbCr, 2, DRM_FORMAT_NV12},
    {HAL_PIXEL_FORMAT_YCbCr_420_888, kYCbCr, 1, DRM_FORMAT_YUV420},
    {HAL_PIXEL_FORMAT_YCbCr_420_888, kYCrCb, 1, DRM_FORMAT_YVU420},
    {HAL_PIXEL_FORMAT_YV12, kYCrCb, 1, DRM_FORMAT_YVU420},
    /* HACK: See droid_create_image_from_prime_fds() and
     * https://issuetracker.google.com/32077885. */
    {HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED, kYCbCr, 2, DRM_FORMAT_NV12},
    {HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED, kYCbCr, 1, DRM_FORMAT_YUV420},
    {HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED, kYCrCb, 1, DRM_FORMAT_YVU420},
    {HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED, kYCrCb, 1, DRM_FORMAT_AYUV},
    {HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED, kYCrCb, 1, DRM_FORMAT_XYUV8888},
};

#define ARRAY_SIZE(a) (sizeof(a) / sizeof((a)[0]))

static uint32_t get_fourcc_yuv(uint32_t native, enum chroma_order chroma_order,
                               size_t chroma_step) {
  for (auto droid_yuv_format : kDroidYuvFormats)
    if (droid_yuv_format.native == native &&
        droid_yuv_format.chroma_order == chroma_order &&
        droid_yuv_format.chroma_step == chroma_step)
      return droid_yuv_format.fourcc;

  return UINT32_MAX;
}

static bool is_yuv(uint32_t native) {
  // NOLINTNEXTLINE(readability-use-anyofallof)
  for (auto droid_yuv_format : kDroidYuvFormats)
    if (droid_yuv_format.native == native)
      return true;

  return false;
}

bool BufferInfoLibdrm::GetYuvPlaneInfo(uint32_t hal_format, int num_fds,
                                       buffer_handle_t handle, BufferInfo *bo) {
  struct android_ycbcr ycbcr {};
  enum chroma_order chroma_order {};
  int ret = 0;

  if (!gralloc_->lock_ycbcr) {
    static std::once_flag once;
    std::call_once(once,
                   []() { ALOGW("Gralloc does not support lock_ycbcr()"); });
    return false;
  }

  memset(&ycbcr, 0, sizeof(ycbcr));
  ret = gralloc_->lock_ycbcr(gralloc_, handle, 0, 0, 0, 0, 0, &ycbcr);
  if (ret) {
    ALOGW("gralloc->lock_ycbcr failed: %d", ret);
    return false;
  }
  gralloc_->unlock(gralloc_, handle);

  /* When lock_ycbcr's usage argument contains no SW_READ/WRITE flags
   * it will return the .y/.cb/.cr pointers based on a NULL pointer,
   * so they can be interpreted as offsets. */
  bo->offsets[0] = (size_t)ycbcr.y;
  /* We assume here that all the planes are located in one DMA-buf. */
  if ((size_t)ycbcr.cr < (size_t)ycbcr.cb) {
    chroma_order = kYCrCb;
    bo->offsets[1] = (size_t)ycbcr.cr;
    bo->offsets[2] = (size_t)ycbcr.cb;
  } else {
    chroma_order = kYCbCr;
    bo->offsets[1] = (size_t)ycbcr.cb;
    bo->offsets[2] = (size_t)ycbcr.cr;
  }

  /* .ystride is the line length (in bytes) of the Y plane,
   * .cstride is the line length (in bytes) of any of the remaining
   * Cb/Cr/CbCr planes, assumed to be the same for Cb and Cr for fully
   * planar formats. */
  bo->pitches[0] = ycbcr.ystride;
  bo->pitches[1] = bo->pitches[2] = ycbcr.cstride;

  /* .chroma_step is the byte distance between the same chroma channel
   * values of subsequent pixels, assumed to be the same for Cb and Cr. */
  bo->format = get_fourcc_yuv(hal_format, chroma_order, ycbcr.chroma_step);
  if (bo->format == UINT32_MAX) {
    ALOGW(
        "unsupported YUV format, native = %x, chroma_order = %s, chroma_step = "
        "%d",
        hal_format, chroma_order == kYCbCr ? "YCbCr" : "YCrCb",
        (int)ycbcr.chroma_step);
    return false;
  }

  /*
   * Since this is EGL_NATIVE_BUFFER_ANDROID don't assume that
   * the single-fd case cannot happen.  So handle eithe single
   * fd or fd-per-plane case:
   */
  if (num_fds == 1) {
    bo->prime_fds[2] = bo->prime_fds[1] = bo->prime_fds[0];
  } else {
    const int expected_planes = (ycbcr.chroma_step == 2) ? 2 : 3;
    if (num_fds != expected_planes)
      return false;
  }

  return true;
}

auto BufferInfoLibdrm::GetBoInfo(buffer_handle_t handle)
    -> std::optional<BufferInfo> {
  gralloc_handle_t *gr_handle = gralloc_handle(handle);
  if (!gr_handle)
    return {};

  BufferInfo bi{};

  bi.width = gr_handle->width;
  bi.height = gr_handle->height;

#if GRALLOC_HANDLE_VERSION < 4
  static std::once_flag once;
  std::call_once(once, []() {
    ALOGE(
        "libdrm < v2.4.97 has broken gralloc_handle structure. Please update.");
  });
#endif
#if GRALLOC_HANDLE_VERSION == 4
  bi.modifiers[0] = gr_handle->modifier;
#endif

  bi.prime_fds[0] = gr_handle->prime_fd;

  if (is_yuv(gr_handle->format)) {
    if (!GetYuvPlaneInfo(gr_handle->format, handle->numFds, handle, &bi))
      return {};
  } else {
    bi.pitches[0] = gr_handle->stride;
    bi.offsets[0] = 0;

    /* FOSS graphic components (gbm_gralloc, mesa3d) are translating
     * HAL_PIXEL_FORMAT_RGB_565 to DRM_FORMAT_RGB565 without swapping
     * the R and B components. Same must be done here. */
    switch (gr_handle->format) {
      case HAL_PIXEL_FORMAT_RGB_565:
        bi.format = DRM_FORMAT_RGB565;
        break;
      default:
        bi.format = ConvertHalFormatToDrm(gr_handle->format);
    }

    if (bi.format == DRM_FORMAT_INVALID)
      return {};
  }

  return bi;
}

constexpr char gbm_gralloc_module_name[] = "GBM Memory Allocator";
constexpr char drm_gralloc_module_name[] = "DRM Memory Allocator";

int BufferInfoLibdrm::ValidateGralloc() {
  if (strcmp(gralloc_->common.name, drm_gralloc_module_name) != 0 &&
      strcmp(gralloc_->common.name, gbm_gralloc_module_name) != 0) {
    ALOGE(
        "Gralloc name isn't valid: Expected: \"%s\" or \"%s\", Actual: \"%s\"",
        gbm_gralloc_module_name, drm_gralloc_module_name,
        gralloc_->common.name);
    return -EINVAL;
  }

  return 0;
}

}  // namespace android
