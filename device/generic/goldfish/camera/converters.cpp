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

#include <libyuv/convert.h>
#include "converters.h"
#include "debug.h"

namespace android {
namespace hardware {
namespace camera {
namespace provider {
namespace implementation {
namespace conv {

// https://en.wikipedia.org/wiki/YCbCr#JPEG_conversion
constexpr size_t kFixedPointShift = 16;
constexpr size_t kFixedPointMul = size_t(1) << kFixedPointShift;
constexpr int32_t kY_R = 0.299 * kFixedPointMul;
constexpr int32_t kY_G = 0.587 * kFixedPointMul;
constexpr int32_t kY_B = 0.114 * kFixedPointMul;
constexpr int32_t kY_Add = 0 << kFixedPointShift;
constexpr int32_t kY_Shift = kFixedPointShift;
constexpr int32_t kY_Clamp = 255 << kY_Shift;
constexpr int32_t kCB_R = -0.168736 * kFixedPointMul;
constexpr int32_t kCB_G = -0.331264 * kFixedPointMul;
constexpr int32_t kCB_B = 0.5 * kFixedPointMul;
constexpr int32_t kCR_R = 0.5 * kFixedPointMul;
constexpr int32_t kCR_G = -0.418688 * kFixedPointMul;
constexpr int32_t kCR_B = -0.081312 * kFixedPointMul;
constexpr int32_t kCx_Shift = kFixedPointShift + 2;
constexpr int32_t kCx_Add = 128 << kCx_Shift;
constexpr int32_t kCx_Clamp = 255 << kCx_Shift;

#define CLAMP_SHIFT(X, MIN, MAX, S) \
    (((X) - (((X) > (MAX)) * ((X) - (MAX))) - (((X) < (MIN)) * ((MIN) - (X)))) >> (S))

#define RGB2Y(R, G, B) (kY_R * (R) + kY_G * (G) + kY_B * (B) + kY_Add)
#define RGB2CB(R, G, B) (kCB_R * (R) + kCB_G * (G) + kCB_B * (B) + kCx_Add)
#define RGB2CR(R, G, B) (kCR_R * (R) + kCR_G * (G) + kCR_B * (B) + kCx_Add)

bool rgba2yuv(const size_t width, size_t height,
              const uint32_t* rgba, const android_ycbcr& ycbcr) {
    if ((width & 1) || (height & 1)) {
        return FAILURE(false);
    }

    if (ycbcr.chroma_step == 1) {
        return (libyuv::ABGRToI420(
            reinterpret_cast<const uint8_t*>(rgba), (width * sizeof(*rgba)),
            static_cast<uint8_t*>(ycbcr.y), ycbcr.ystride,
            static_cast<uint8_t*>(ycbcr.cb), ycbcr.cstride,
            static_cast<uint8_t*>(ycbcr.cr), ycbcr.cstride,
            width, height) == 0) ? true : FAILURE(false);
    }

    const size_t width2 = width + width;
    const size_t ystride = ycbcr.ystride;
    const size_t ystride2 = ystride + ystride;
    const size_t cstride = ycbcr.cstride;
    const size_t chromaStep = ycbcr.chroma_step;
    uint8_t* y = static_cast<uint8_t*>(ycbcr.y);
    uint8_t* cb = static_cast<uint8_t*>(ycbcr.cb);
    uint8_t* cr = static_cast<uint8_t*>(ycbcr.cr);

    // The loops below go through the RGBA image 2rows X 2columns at once.
    // Each four RGBA pixels produce four Y values and one {Cb, Cr} pair.
    // R, G and B components of those 4 pixels are averaged (this is why
    // they are called R4, G4 and B4) before converting to the {Cb, Cr} pair.
    // The code does not have a separate divizion by 4 to average the color
    // components, instead `2` is added to the Cx shift.
    for (; height > 0; height -= 2, rgba += width2, y += ystride2,
                       cb += cstride, cr += cstride) {
        const uint32_t* r0 = rgba;
        const uint32_t* r1 = rgba + width;
        uint8_t* y0 = y;
        uint8_t* y1 = y + ystride;
        uint8_t* cb0 = cb;
        uint8_t* cr0 = cr;

        for (size_t col = width; col > 0; col -= 2, r0 += 2, r1 += 2, y0 += 2, y1 += 2,
                                          cb0 += chromaStep, cr0 += chromaStep) {
            int32_t r4;
            int32_t g4;
            int32_t b4;
            int32_t tmp0;
            int32_t tmp1;

            {
                const uint32_t p00 = r0[0];
                const uint32_t p01 = r0[1];
                const int32_t r00 = p00 & 0xFF;
                const int32_t r01 = p01 & 0xFF;
                const int32_t g00 = (p00 >> 8) & 0xFF;
                const int32_t g01 = (p01 >> 8) & 0xFF;
                const int32_t b00 = (p00 >> 16) & 0xFF;
                const int32_t b01 = (p01 >> 16) & 0xFF;
                r4 = r00 + r01;
                g4 = g00 + g01;
                b4 = b00 + b01;
                tmp0 = RGB2Y(r00, g00, b00);
                tmp1 = RGB2Y(r01, g01, b01);
                tmp0 = CLAMP_SHIFT(tmp0, 0, kY_Clamp, kY_Shift);
                tmp1 = CLAMP_SHIFT(tmp1, 0, kY_Clamp, kY_Shift);
                y0[0] = tmp0;
                y1[0] = tmp1;
            }
            {
                const uint32_t p10 = r1[0];
                const uint32_t p11 = r1[1];
                const int32_t r10 = p10 & 0xFF;
                const int32_t r11 = p11 & 0xFF;
                const int32_t g10 = (p10 >> 8) & 0xFF;
                const int32_t g11 = (p11 >> 8) & 0xFF;
                const int32_t b10 = (p10 >> 16) & 0xFF;
                const int32_t b11 = (p11 >> 16) & 0xFF;
                r4 += (r10 + r11);
                g4 += (g10 + g11);
                b4 += (b10 + b11);
                tmp0 = RGB2Y(r10, g10, b10);
                tmp1 = RGB2Y(r11, g11, b11);
                tmp0 = CLAMP_SHIFT(tmp0, 0, kY_Clamp, kY_Shift);
                tmp1 = CLAMP_SHIFT(tmp1, 0, kY_Clamp, kY_Shift);
                y1[0] = tmp0;
                y1[1] = tmp1;
            }

            tmp0 = RGB2CB(r4, g4, b4);
            tmp1 = RGB2CR(r4, g4, b4);
            tmp0 = CLAMP_SHIFT(tmp0, 0, kCx_Clamp, kCx_Shift);
            tmp1 = CLAMP_SHIFT(tmp1, 0, kCx_Clamp, kCx_Shift);
            *cb0 = tmp0;
            *cr0 = tmp1;
        }
    }

    return true;
}

}  // namespace conv
}  // namespace implementation
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android
