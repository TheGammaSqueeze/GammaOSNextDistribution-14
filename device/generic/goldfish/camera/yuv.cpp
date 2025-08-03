/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include <log/log.h>
#include "yuv.h"

namespace android {
namespace hardware {
namespace camera {
namespace provider {
namespace implementation {
namespace yuv {
namespace {

void copyCbCrPlane(uint8_t* dst, const size_t width, size_t height,
                   const void* src, const size_t srcStride, const size_t srcStep) {
    const uint8_t* src8 = static_cast<const uint8_t*>(src);
    for (; height > 0; --height, src8 += srcStride) {
        const uint8_t* p = src8;
        for (size_t rem = width & 15; rem; --rem, ++dst, p += srcStep) {
            *dst = *p;
        }

        for (size_t width16 = width >> 4; width16; --width16) {
            *dst = *p; ++dst; p += srcStep;
            *dst = *p; ++dst; p += srcStep;
            *dst = *p; ++dst; p += srcStep;
            *dst = *p; ++dst; p += srcStep;
            *dst = *p; ++dst; p += srcStep;
            *dst = *p; ++dst; p += srcStep;
            *dst = *p; ++dst; p += srcStep;
            *dst = *p; ++dst; p += srcStep;
            *dst = *p; ++dst; p += srcStep;
            *dst = *p; ++dst; p += srcStep;
            *dst = *p; ++dst; p += srcStep;
            *dst = *p; ++dst; p += srcStep;
            *dst = *p; ++dst; p += srcStep;
            *dst = *p; ++dst; p += srcStep;
            *dst = *p; ++dst; p += srcStep;
            *dst = *p; ++dst; p += srcStep;
        }
    }
}

}  // namespace

size_t NV21size(const size_t width, const size_t height) {
    LOG_ALWAYS_FATAL_IF((width & 1) || (height & 1));
    return width * height * 3 / 2;
}

android_ycbcr NV21init(const size_t width, const size_t height, void* data) {
    LOG_ALWAYS_FATAL_IF((width & 1) || (height & 1));
    uint8_t* data8 = static_cast<uint8_t*>(data);
    const size_t area = width * height;

    android_ycbcr nv21;
    nv21.y = data8;
    nv21.cb = data8 + area;
    nv21.cr = data8 + area + (area >> 2);
    nv21.ystride = width;
    nv21.cstride = width / 2;
    nv21.chroma_step = 1;

    return nv21;
}

android_ycbcr toNV21Shallow(const size_t width, const size_t height,
                            const android_ycbcr& ycbcr,
                            std::vector<uint8_t>* data) {
    LOG_ALWAYS_FATAL_IF((width & 1) || (height & 1));
    if (ycbcr.chroma_step == 1) {
        return ycbcr;
    }

    const size_t area = width * height;
    data->resize(area / 2);  // only for CbCr

    android_ycbcr nv21;
    nv21.y = ycbcr.y;  // don't copy Y
    nv21.ystride = ycbcr.ystride;
    nv21.cb = &data[0];
    nv21.cr = &data[area / 4];
    nv21.cstride = width / 2;
    nv21.chroma_step = 1;

    copyCbCrPlane(&((*data)[0]), width / 2, height / 2,
                  ycbcr.cb, ycbcr.cstride, ycbcr.chroma_step);
    copyCbCrPlane(&((*data)[area / 4]), width / 2, height / 2,
                  ycbcr.cr, ycbcr.cstride, ycbcr.chroma_step);

    return nv21;
}

}  // namespace yuv
}  // namespace implementation
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android
