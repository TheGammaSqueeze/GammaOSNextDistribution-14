// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
// Note: ported from Chromium commit head: 27c98933749f

#ifndef ANDROID_V4L2_CODEC2_COMMON_FOURCC_H
#define ANDROID_V4L2_CODEC2_COMMON_FOURCC_H

#include <stdint.h>
#include <optional>
#include <string>

#include <v4l2_codec2/common/VideoPixelFormat.h>

namespace android {

// Composes a Fourcc value.
constexpr uint32_t composeFourcc(char a, char b, char c, char d) {
    return static_cast<uint32_t>(a) | (static_cast<uint32_t>(b) << 8) |
           (static_cast<uint32_t>(c) << 16) | (static_cast<uint32_t>(d) << 24);
}

// Fourcc enum holder and converters.
// Usage:
// Fourcc f1(Fourcc::AR24);
// EXPECT_EQ("AR24", f1.ToString());
// Fourcc f2 = Fourcc::FromVideoPixelFormat(PIXEL_FORMAT_ARGB);
// EXPECT_EQ(f2, f1);
class Fourcc {
public:
    enum Value : uint32_t {
        // RGB formats.
        // https://linuxtv.org/downloads/v4l-dvb-apis/uapi/v4l/pixfmt-rgb.html
        // Maps to PIXEL_FORMAT_ARGB, V4L2_PIX_FMT_ABGR32, VA_FOURCC_BGRA.
        // 32bpp BGRA (byte-order), 1 plane.
        AR24 = composeFourcc('A', 'R', '2', '4'),

        // Maps to PIXEL_FORMAT_ABGR, V4L2_PIX_FMT_RGBA32, VA_FOURCC_RGBA.
        // 32bpp RGBA (byte-order), 1 plane
        AB24 = composeFourcc('A', 'B', '2', '4'),

        // Maps to PIXEL_FORMAT_XRGB, V4L2_PIX_FMT_XBGR32, VA_FOURCC_BGRX.
        // 32bpp BGRX (byte-order), 1 plane.
        XR24 = composeFourcc('X', 'R', '2', '4'),

        // Maps to PIXEL_FORMAT_XBGR, V4L2_PIX_FMT_RGBX32, VA_FOURCC_RGBX.
        // 32bpp RGBX (byte-order), 1 plane.
        XB24 = composeFourcc('X', 'B', '2', '4'),

        // Maps to PIXEL_FORMAT_BGRA, V4L2_PIX_FMT_RGB32, VA_FOURCC_ARGB.
        // 32bpp ARGB (byte-order), 1 plane.
        // Note that V4L2_PIX_FMT_RGB32("RGB4") is deprecated and replaced by
        // V4L2_PIX_FMT_ARGB32("BA24"), however, some board relies on the fourcc mapping so we keep
        // it as-is.
        RGB4 = composeFourcc('R', 'G', 'B', '4'),

        BGR4 = composeFourcc('B', 'G', 'R', '4'),

        // YUV420 single-planar formats.
        // https://linuxtv.org/downloads/v4l-dvb-apis/uapi/v4l/pixfmt-yuv420.html
        // Maps to PIXEL_FORMAT_I420, V4L2_PIX_FMT_YUV420, VA_FOURCC_I420.
        // 12bpp YUV planar 1x1 Y, 2x2 UV samples.
        YU12 = composeFourcc('Y', 'U', '1', '2'),
        // Maps to PIXEL_FORMAT_YV12, V4L2_PIX_FMT_YVU420, VA_FOURCC_YV12.
        // 12bpp YVU planar 1x1 Y, 2x2 VU samples.
        YV12 = composeFourcc('Y', 'V', '1', '2'),

        // YUV420 multi-planar format.
        // https://linuxtv.org/downloads/v4l-dvb-apis/uapi/v4l/pixfmt-yuv420m.htm
        // Maps to PIXEL_FORMAT_I420, V4L2_PIX_FMT_YUV420M.
        YM12 = composeFourcc('Y', 'M', '1', '2'),
        // Maps to PIXEL_FORMAT_YV12, V4L2_PIX_FMT_YVU420M.
        YM21 = composeFourcc('Y', 'M', '2', '1'),

        // YUYV format.
        // https://linuxtv.org/downloads/v4l-dvb-apis/uapi/v4l/pixfmt-yuyv.html
        // Maps to PIXEL_FORMAT_YUY2, V4L2_PIX_FMT_YUYV, VA_FOURCC_YUY2.
        // 16bpp YUV planar (YUV 4:2:2), YUYV (byte-order), 1 plane.
        YUYV = composeFourcc('Y', 'U', 'Y', 'V'),

        // NV12 single-planar format.
        // https://linuxtv.org/downloads/v4l-dvb-apis/uapi/v4l/pixfmt-nv12.html
        // Maps to PIXEL_FORMAT_NV12, V4L2_PIX_FMT_NV12, VA_FOURCC_NV12.
        // 12bpp with Y plane followed by a 2x2 interleaved UV plane.
        NV12 = composeFourcc('N', 'V', '1', '2'),
        // Maps to PIXEL_FORMAT_NV21, V4L2_PIX_FMT_NV21, VA_FOURCC_NV21.
        // 12bpp with Y plane followed by a 2x2 interleaved VU plane.
        NV21 = composeFourcc('N', 'V', '2', '1'),

        // NV12 multi-planar format.
        // https://linuxtv.org/downloads/v4l-dvb-apis/uapi/v4l/pixfmt-nv12m.html
        // Maps to PIXEL_FORMAT_NV12, V4L2_PIX_FMT_NV12M,
        NM12 = composeFourcc('N', 'M', '1', '2'),
        // Maps to PIXEL_FORMAT_NV21, V4L2_PIX_FMT_NV21M.
        NM21 = composeFourcc('N', 'M', '2', '1'),

        // YUV422 multi-planar format.
        // https://linuxtv.org/downloads/v4l-dvb-apis/uapi/v4l/pixfmt-yuv422m.html
        // Maps to PIXEL_FORMAT_I422, V4L2_PIX_FMT_YUV422M
        // 16bpp YUV planar 1x1 Y, 2x1 UV samples.
        YM16 = composeFourcc('Y', 'M', '1', '6'),

        // V4L2 proprietary format.
        // https://linuxtv.org/downloads/v4l-dvb-apis/uapi/v4l/pixfmt-reserved.html
        // Maps to V4L2_PIX_FMT_MT21C.
        // It is used for MT8173 hardware video decoder output and should be converted by MT8173 image
        // processor for compositor to render.
        MT21 = composeFourcc('M', 'T', '2', '1'),
        // Maps to V4L2_PIX_FMT_MM21.
        // It is used for MT8183 hardware video decoder.
        MM21 = composeFourcc('M', 'M', '2', '1'),
    };

    explicit Fourcc(Fourcc::Value fourcc);
    Fourcc& operator=(const Fourcc& fourcc);
    ~Fourcc();

    bool operator==(const Fourcc& rhs) const { return mValue == rhs.mValue; }

    // Factory methods:

    // Builds a Fourcc from a given fourcc code. This will return a valid Fourcc if the argument is
    // part of the |Value| enum, or nullopt otherwise.
    static std::optional<Fourcc> fromUint32(uint32_t fourcc);

    // Converts a VideoPixelFormat to Fourcc. Returns nullopt for invalid input. Note that a
    // VideoPixelFormat may have two Fourcc counterparts. Caller has to specify if it is for
    // single-planar or multi-planar format.
    static std::optional<Fourcc> fromVideoPixelFormat(VideoPixelFormat pixelFormat,
                                                      bool singlePlanar = true);
    // Converts a V4L2PixFmt to Fourcc. Returns nullopt for invalid input.
    static std::optional<Fourcc> fromV4L2PixFmt(uint32_t v4l2PixFmt);

    // Value getters:
    // Returns the VideoPixelFormat counterpart of the value. Returns PIXEL_FORMAT_UNKNOWN if no
    // mapping is found.
    VideoPixelFormat toVideoPixelFormat() const;
    // Returns the V4L2PixFmt counterpart of the value. Returns 0 if no mapping is found.
    uint32_t toV4L2PixFmt() const;

    // Returns the single-planar Fourcc of the value. If value is a single-planar, returns the same
    // Fourcc. Returns nullopt if no mapping is found.
    std::optional<Fourcc> toSinglePlanar() const;

    // Returns whether |value_| is multi planar format.
    bool isMultiPlanar() const;

    // Outputs human readable fourcc string, e.g. "NV12".
    std::string toString() const;

private:
    Value mValue;
};

bool operator!=(const Fourcc& lhs, const Fourcc& rhs);

}  // namespace android

#endif  // ANDROID_V4L2_CODEC2_COMMON_FOURCC_H
