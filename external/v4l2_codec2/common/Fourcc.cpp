// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

//#define LOG_NDEBUG 0
#define LOG_TAG "Fourcc"

#include <v4l2_codec2/common/Fourcc.h>

#include <linux/videodev2.h>

#include <utils/Log.h>

namespace android {

Fourcc::Fourcc(Fourcc::Value fourcc) : mValue(fourcc) {}
Fourcc::~Fourcc() = default;
Fourcc& Fourcc::operator=(const Fourcc& other) = default;

// static
std::optional<Fourcc> Fourcc::fromUint32(uint32_t fourcc) {
    switch (fourcc) {
    case AR24:
    case AB24:
    case XR24:
    case XB24:
    case RGB4:
    case BGR4:
    case YU12:
    case YV12:
    case YM12:
    case YM21:
    case YUYV:
    case NV12:
    case NV21:
    case NM12:
    case NM21:
    case YM16:
    case MT21:
    case MM21:
        return Fourcc(static_cast<Value>(fourcc));
    }
    ALOGV("Unmapped fourcc: %s", fourccToString(fourcc).c_str());
    return std::nullopt;
}

// static
std::optional<Fourcc> Fourcc::fromVideoPixelFormat(VideoPixelFormat pixelFormat,
                                                   bool singlePlanar) {
    if (singlePlanar) {
        switch (pixelFormat) {
        case VideoPixelFormat::ARGB:
            return Fourcc(AR24);
        case VideoPixelFormat::ABGR:
            return Fourcc(AB24);
        case VideoPixelFormat::XRGB:
            return Fourcc(XR24);
        case VideoPixelFormat::XBGR:
            return Fourcc(XB24);
        case VideoPixelFormat::BGRA:
            return Fourcc(RGB4);
        case VideoPixelFormat::RGBA:
            return Fourcc(BGR4);
        case VideoPixelFormat::I420:
            return Fourcc(YU12);
        case VideoPixelFormat::YV12:
            return Fourcc(YV12);
        case VideoPixelFormat::YUY2:
            return Fourcc(YUYV);
        case VideoPixelFormat::NV12:
            return Fourcc(NV12);
        case VideoPixelFormat::NV21:
            return Fourcc(NV21);
        case VideoPixelFormat::I422:
        case VideoPixelFormat::I420A:
        case VideoPixelFormat::I444:
        case VideoPixelFormat::RGB24:
        case VideoPixelFormat::MJPEG:
        case VideoPixelFormat::YUV420P9:
        case VideoPixelFormat::YUV420P10:
        case VideoPixelFormat::YUV422P9:
        case VideoPixelFormat::YUV422P10:
        case VideoPixelFormat::YUV444P9:
        case VideoPixelFormat::YUV444P10:
        case VideoPixelFormat::YUV420P12:
        case VideoPixelFormat::YUV422P12:
        case VideoPixelFormat::YUV444P12:
        case VideoPixelFormat::Y16:
        case VideoPixelFormat::P016LE:
        case VideoPixelFormat::XR30:
        case VideoPixelFormat::XB30:
        case VideoPixelFormat::UNKNOWN:
            break;
        }
    } else {
        switch (pixelFormat) {
        case VideoPixelFormat::I420:
            return Fourcc(YM12);
        case VideoPixelFormat::YV12:
            return Fourcc(YM21);
        case VideoPixelFormat::NV12:
            return Fourcc(NM12);
        case VideoPixelFormat::I422:
            return Fourcc(YM16);
        case VideoPixelFormat::NV21:
            return Fourcc(NM21);
        case VideoPixelFormat::I420A:
        case VideoPixelFormat::I444:
        case VideoPixelFormat::YUY2:
        case VideoPixelFormat::ARGB:
        case VideoPixelFormat::XRGB:
        case VideoPixelFormat::RGB24:
        case VideoPixelFormat::MJPEG:
        case VideoPixelFormat::YUV420P9:
        case VideoPixelFormat::YUV420P10:
        case VideoPixelFormat::YUV422P9:
        case VideoPixelFormat::YUV422P10:
        case VideoPixelFormat::YUV444P9:
        case VideoPixelFormat::YUV444P10:
        case VideoPixelFormat::YUV420P12:
        case VideoPixelFormat::YUV422P12:
        case VideoPixelFormat::YUV444P12:
        case VideoPixelFormat::Y16:
        case VideoPixelFormat::ABGR:
        case VideoPixelFormat::XBGR:
        case VideoPixelFormat::P016LE:
        case VideoPixelFormat::XR30:
        case VideoPixelFormat::XB30:
        case VideoPixelFormat::BGRA:
        case VideoPixelFormat::RGBA:
        case VideoPixelFormat::UNKNOWN:
            break;
        }
    }
    ALOGE("Unmapped %s for %s", videoPixelFormatToString(pixelFormat).c_str(),
          singlePlanar ? "single-planar" : "multi-planar");
    return std::nullopt;
}

VideoPixelFormat Fourcc::toVideoPixelFormat() const {
    switch (mValue) {
    case AR24:
        return VideoPixelFormat::ARGB;
    case AB24:
        return VideoPixelFormat::ABGR;
    case XR24:
        return VideoPixelFormat::XRGB;
    case XB24:
        return VideoPixelFormat::XBGR;
    case RGB4:
        return VideoPixelFormat::BGRA;
    case BGR4:
        return VideoPixelFormat::RGBA;
    case YU12:
    case YM12:
        return VideoPixelFormat::I420;
    case YV12:
    case YM21:
        return VideoPixelFormat::YV12;
    case YUYV:
        return VideoPixelFormat::YUY2;
    case NV12:
    case NM12:
        return VideoPixelFormat::NV12;
    case NV21:
    case NM21:
        return VideoPixelFormat::NV21;
    case YM16:
        return VideoPixelFormat::I422;
    // V4L2_PIX_FMT_MT21C is only used for MT8173 hardware video decoder output
    // and should be converted by MT8173 image processor for compositor to
    // render. Since it is an intermediate format for video decoder,
    // VideoPixelFormat shall not have its mapping. However, we need to create a
    // VideoFrameLayout for the format to process the intermediate frame. Hence
    // we map V4L2_PIX_FMT_MT21C to PIXEL_FORMAT_NV12 as their layout are the
    // same.
    case MT21:
    // V4L2_PIX_FMT_MM21 is used for MT8183 hardware video decoder. It is
    // similar to V4L2_PIX_FMT_MT21C but is not compressed ; thus it can also
    // be mapped to PIXEL_FORMAT_NV12.
    case MM21:
        return VideoPixelFormat::NV12;
    }

    ALOGE("Unmapped Fourcc: %s", toString().c_str());
    return VideoPixelFormat::UNKNOWN;
}

// static
std::optional<Fourcc> Fourcc::fromV4L2PixFmt(uint32_t v4l2PixFmt) {
    // We can do that because we adopt the same internal definition of Fourcc as
    // V4L2.
    return fromUint32(v4l2PixFmt);
}

uint32_t Fourcc::toV4L2PixFmt() const {
    // Note that we can do that because we adopt the same internal definition of
    // Fourcc as V4L2.
    return static_cast<uint32_t>(mValue);
}

std::optional<Fourcc> Fourcc::toSinglePlanar() const {
    switch (mValue) {
    case AR24:
    case AB24:
    case XR24:
    case XB24:
    case RGB4:
    case BGR4:
    case YU12:
    case YV12:
    case YUYV:
    case NV12:
    case NV21:
        return Fourcc(mValue);
    case YM12:
        return Fourcc(YU12);
    case YM21:
        return Fourcc(YV12);
    case NM12:
        return Fourcc(NV12);
    case NM21:
        return Fourcc(NV21);
    case YM16:
    case MT21:
    case MM21:
        return std::nullopt;
    }
}

bool operator!=(const Fourcc& lhs, const Fourcc& rhs) {
    return !(lhs == rhs);
}

bool Fourcc::isMultiPlanar() const {
    switch (mValue) {
    case AR24:
    case AB24:
    case XR24:
    case XB24:
    case RGB4:
    case BGR4:
    case YU12:
    case YV12:
    case YUYV:
    case NV12:
    case NV21:
        return false;
    case YM12:
    case YM21:
    case NM12:
    case NM21:
    case YM16:
    case MT21:
    case MM21:
        return true;
    }
}

std::string Fourcc::toString() const {
    return fourccToString(static_cast<uint32_t>(mValue));
}

static_assert(Fourcc::AR24 == V4L2_PIX_FMT_ABGR32, "Mismatch Fourcc");
#ifdef V4L2_PIX_FMT_RGBA32
// V4L2_PIX_FMT_RGBA32 is defined since v5.2
static_assert(Fourcc::AB24 == V4L2_PIX_FMT_RGBA32, "Mismatch Fourcc");
#endif  // V4L2_PIX_FMT_RGBA32
static_assert(Fourcc::XR24 == V4L2_PIX_FMT_XBGR32, "Mismatch Fourcc");
#ifdef V4L2_PIX_FMT_RGBX32
// V4L2_PIX_FMT_RGBX32 is defined since v5.2
static_assert(Fourcc::XB24 == V4L2_PIX_FMT_RGBX32, "Mismatch Fourcc");
#endif  // V4L2_PIX_FMT_RGBX32
static_assert(Fourcc::RGB4 == V4L2_PIX_FMT_RGB32, "Mismatch Fourcc");
static_assert(Fourcc::BGR4 == V4L2_PIX_FMT_BGR32, "Mismatch Fourcc");
static_assert(Fourcc::YU12 == V4L2_PIX_FMT_YUV420, "Mismatch Fourcc");
static_assert(Fourcc::YV12 == V4L2_PIX_FMT_YVU420, "Mismatch Fourcc");
static_assert(Fourcc::YM12 == V4L2_PIX_FMT_YUV420M, "Mismatch Fourcc");
static_assert(Fourcc::YM21 == V4L2_PIX_FMT_YVU420M, "Mismatch Fourcc");
static_assert(Fourcc::YUYV == V4L2_PIX_FMT_YUYV, "Mismatch Fourcc");
static_assert(Fourcc::NV12 == V4L2_PIX_FMT_NV12, "Mismatch Fourcc");
static_assert(Fourcc::NV21 == V4L2_PIX_FMT_NV21, "Mismatch Fourcc");
static_assert(Fourcc::NM12 == V4L2_PIX_FMT_NV12M, "Mismatch Fourcc");
static_assert(Fourcc::NM21 == V4L2_PIX_FMT_NV21M, "Mismatch Fourcc");
static_assert(Fourcc::YM16 == V4L2_PIX_FMT_YUV422M, "Mismatch Fourcc");
static_assert(Fourcc::MT21 == V4L2_PIX_FMT_MT21C, "Mismatch Fourcc");
#ifdef V4L2_PIX_FMT_MM21
// V4L2_PIX_FMT_MM21 is not yet upstreamed.
static_assert(Fourcc::MM21 == V4L2_PIX_FMT_MM21, "Mismatch Fourcc");
#endif  // V4L2_PIX_FMT_MM21

}  // namespace android
