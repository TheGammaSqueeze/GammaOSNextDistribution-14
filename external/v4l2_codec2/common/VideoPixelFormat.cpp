// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
// Note: ported from Chromium commit head: 3b7ce92816e2
// Note: only necessary functions are ported from video_types.cc

#include <v4l2_codec2/common/VideoPixelFormat.h>

#include <base/bits.h>
#include <utils/Log.h>

namespace android {

namespace {

enum {
    kMaxPlanes = 4,
    kYPlane = 0,
    kARGBPlane = kYPlane,
    kUPlane = 1,
    kUVPlane = kUPlane,
    kVPlane = 2,
    kAPlane = 3,
};
}

std::string videoPixelFormatToString(VideoPixelFormat format) {
    switch (format) {
    case VideoPixelFormat::I420:
        return "I420";
    case VideoPixelFormat::YV12:
        return "YV12";
    case VideoPixelFormat::I422:
        return "I422";
    case VideoPixelFormat::I420A:
        return "I420A";
    case VideoPixelFormat::I444:
        return "I444";
    case VideoPixelFormat::NV12:
        return "NV12";
    case VideoPixelFormat::NV21:
        return "NV21";
    case VideoPixelFormat::YUY2:
        return "YUY2";
    case VideoPixelFormat::ARGB:
        return "ARGB";
    case VideoPixelFormat::XRGB:
        return "XRGB";
    case VideoPixelFormat::RGB24:
        return "RGB24";
    case VideoPixelFormat::MJPEG:
        return "MJPEG";
    case VideoPixelFormat::YUV420P9:
        return "YUV420P9";
    case VideoPixelFormat::YUV420P10:
        return "YUV420P10";
    case VideoPixelFormat::YUV422P9:
        return "YUV422P9";
    case VideoPixelFormat::YUV422P10:
        return "YUV422P10";
    case VideoPixelFormat::YUV444P9:
        return "YUV444P9";
    case VideoPixelFormat::YUV444P10:
        return "YUV444P10";
    case VideoPixelFormat::YUV420P12:
        return "YUV420P12";
    case VideoPixelFormat::YUV422P12:
        return "YUV422P12";
    case VideoPixelFormat::YUV444P12:
        return "YUV444P12";
    case VideoPixelFormat::Y16:
        return "Y16";
    case VideoPixelFormat::ABGR:
        return "ABGR";
    case VideoPixelFormat::XBGR:
        return "XBGR";
    case VideoPixelFormat::P016LE:
        return "P016LE";
    case VideoPixelFormat::XR30:
        return "XR30";
    case VideoPixelFormat::XB30:
        return "XB30";
    case VideoPixelFormat::BGRA:
        return "BGRA";
    case VideoPixelFormat::RGBA:
        return "BGRA";
    case VideoPixelFormat::UNKNOWN:
        return "UNKNOWN";
    }
}

std::string fourccToString(uint32_t fourcc) {
    std::string result = "0000";
    for (size_t i = 0; i < 4; ++i, fourcc >>= 8) {
        const char c = static_cast<char>(fourcc & 0xFF);
        if (c <= 0x1f || c >= 0x7f) {
            return (std::stringstream("0x") << std::hex << fourcc).str();
        }
        result[i] = c;
    }
    return result;
}

size_t bitDepth(VideoPixelFormat format) {
    switch (format) {
    case VideoPixelFormat::I420:
    case VideoPixelFormat::YV12:
    case VideoPixelFormat::I422:
    case VideoPixelFormat::I420A:
    case VideoPixelFormat::I444:
    case VideoPixelFormat::NV12:
    case VideoPixelFormat::NV21:
    case VideoPixelFormat::YUY2:
    case VideoPixelFormat::ARGB:
    case VideoPixelFormat::XRGB:
    case VideoPixelFormat::RGB24:
    case VideoPixelFormat::MJPEG:
    case VideoPixelFormat::ABGR:
    case VideoPixelFormat::XBGR:
    case VideoPixelFormat::BGRA:
    case VideoPixelFormat::RGBA:
        return 8;
    case VideoPixelFormat::YUV420P9:
    case VideoPixelFormat::YUV422P9:
    case VideoPixelFormat::YUV444P9:
        return 9;
    case VideoPixelFormat::YUV420P10:
    case VideoPixelFormat::YUV422P10:
    case VideoPixelFormat::YUV444P10:
    case VideoPixelFormat::XR30:
    case VideoPixelFormat::XB30:
        return 10;
    case VideoPixelFormat::YUV420P12:
    case VideoPixelFormat::YUV422P12:
    case VideoPixelFormat::YUV444P12:
        return 12;
    case VideoPixelFormat::Y16:
    case VideoPixelFormat::P016LE:
        return 16;
    case VideoPixelFormat::UNKNOWN:
        ALOGE("Invalid pixel format");
        return 0;
    }
}

// If it is required to allocate aligned to multiple-of-two size overall for the
// frame of pixel |format|.
static bool RequiresEvenSizeAllocation(VideoPixelFormat format) {
    switch (format) {
    case VideoPixelFormat::ARGB:
    case VideoPixelFormat::XRGB:
    case VideoPixelFormat::RGB24:
    case VideoPixelFormat::Y16:
    case VideoPixelFormat::ABGR:
    case VideoPixelFormat::XBGR:
    case VideoPixelFormat::XR30:
    case VideoPixelFormat::XB30:
    case VideoPixelFormat::BGRA:
    case VideoPixelFormat::RGBA:
        return false;
    case VideoPixelFormat::NV12:
    case VideoPixelFormat::NV21:
    case VideoPixelFormat::I420:
    case VideoPixelFormat::MJPEG:
    case VideoPixelFormat::YUY2:
    case VideoPixelFormat::YV12:
    case VideoPixelFormat::I422:
    case VideoPixelFormat::I444:
    case VideoPixelFormat::YUV420P9:
    case VideoPixelFormat::YUV422P9:
    case VideoPixelFormat::YUV444P9:
    case VideoPixelFormat::YUV420P10:
    case VideoPixelFormat::YUV422P10:
    case VideoPixelFormat::YUV444P10:
    case VideoPixelFormat::YUV420P12:
    case VideoPixelFormat::YUV422P12:
    case VideoPixelFormat::YUV444P12:
    case VideoPixelFormat::I420A:
    case VideoPixelFormat::P016LE:
        return true;
    case VideoPixelFormat::UNKNOWN:
        ALOGE("Invalid pixel format");
        return false;
    }
}

size_t numPlanes(VideoPixelFormat format) {
    switch (format) {
    case VideoPixelFormat::YUY2:
    case VideoPixelFormat::ARGB:
    case VideoPixelFormat::BGRA:
    case VideoPixelFormat::RGBA:
    case VideoPixelFormat::XRGB:
    case VideoPixelFormat::RGB24:
    case VideoPixelFormat::MJPEG:
    case VideoPixelFormat::Y16:
    case VideoPixelFormat::ABGR:
    case VideoPixelFormat::XBGR:
    case VideoPixelFormat::XR30:
    case VideoPixelFormat::XB30:
        return 1;
    case VideoPixelFormat::NV12:
    case VideoPixelFormat::NV21:
    case VideoPixelFormat::P016LE:
        return 2;
    case VideoPixelFormat::I420:
    case VideoPixelFormat::YV12:
    case VideoPixelFormat::I422:
    case VideoPixelFormat::I444:
    case VideoPixelFormat::YUV420P9:
    case VideoPixelFormat::YUV422P9:
    case VideoPixelFormat::YUV444P9:
    case VideoPixelFormat::YUV420P10:
    case VideoPixelFormat::YUV422P10:
    case VideoPixelFormat::YUV444P10:
    case VideoPixelFormat::YUV420P12:
    case VideoPixelFormat::YUV422P12:
    case VideoPixelFormat::YUV444P12:
        return 3;
    case VideoPixelFormat::I420A:
        return 4;
    case VideoPixelFormat::UNKNOWN:
        // Note: VideoPixelFormat::UNKNOWN is used for end-of-stream frame.
        return 0;
    }
}

size_t allocationSize(VideoPixelFormat format, const android::ui::Size& coded_size) {
    size_t total = 0;
    for (size_t i = 0; i < numPlanes(format); ++i) {
        android::ui::Size plane_size = planeSize(format, i, coded_size);
        total += (plane_size.width * plane_size.height);
    }

    return total;
}

android::ui::Size planeSize(VideoPixelFormat format, size_t plane,
                            const android::ui::Size& coded_size) {
    ALOG_ASSERT(isValidPlane(plane, format));

    int width = coded_size.width;
    int height = coded_size.height;
    if (RequiresEvenSizeAllocation(format)) {
        // Align to multiple-of-two size overall. This ensures that non-subsampled
        // planes can be addressed by pixel with the same scaling as the subsampled
        // planes.
        width = base::bits::Align(width, 2);
        height = base::bits::Align(height, 2);
    }

    const android::ui::Size subsample = SampleSize(format, plane);
    ALOG_ASSERT(width % subsample.width == 0);
    ALOG_ASSERT(height % subsample.height == 0);
    return android::ui::Size(bytesPerElement(format, plane) * width / subsample.width,
                             height / subsample.height);
}

int planeHorizontalBitsPerPixel(VideoPixelFormat format, size_t plane) {
    ALOG_ASSERT(isValidPlane(plane, format));
    const int bitsPerElement = 8 * bytesPerElement(format, plane);
    const int horizPixelsPerElement = SampleSize(format, plane).width;
    ALOG_ASSERT(bitsPerElement % horizPixelsPerElement == 0);
    return bitsPerElement / horizPixelsPerElement;
}

int planeBitsPerPixel(VideoPixelFormat format, size_t plane) {
    ALOG_ASSERT(isValidPlane(plane, format));
    return planeHorizontalBitsPerPixel(format, plane) / SampleSize(format, plane).height;
}

int bytesPerElement(VideoPixelFormat format, size_t plane) {
    ALOG_ASSERT(isValidPlane(format, plane));
    switch (format) {
    case VideoPixelFormat::ARGB:
    case VideoPixelFormat::BGRA:
    case VideoPixelFormat::RGBA:
    case VideoPixelFormat::XRGB:
    case VideoPixelFormat::ABGR:
    case VideoPixelFormat::XBGR:
    case VideoPixelFormat::XR30:
    case VideoPixelFormat::XB30:
        return 4;
    case VideoPixelFormat::RGB24:
        return 3;
    case VideoPixelFormat::Y16:
    case VideoPixelFormat::YUY2:
    case VideoPixelFormat::YUV420P9:
    case VideoPixelFormat::YUV422P9:
    case VideoPixelFormat::YUV444P9:
    case VideoPixelFormat::YUV420P10:
    case VideoPixelFormat::YUV422P10:
    case VideoPixelFormat::YUV444P10:
    case VideoPixelFormat::YUV420P12:
    case VideoPixelFormat::YUV422P12:
    case VideoPixelFormat::YUV444P12:
    case VideoPixelFormat::P016LE:
        return 2;
    case VideoPixelFormat::NV12:
    case VideoPixelFormat::NV21: {
        static const int bytes_per_element[] = {1, 2};
        ALOG_ASSERT(plane < base::size(bytes_per_element));
        return bytes_per_element[plane];
    }
    case VideoPixelFormat::YV12:
    case VideoPixelFormat::I420:
    case VideoPixelFormat::I422:
    case VideoPixelFormat::I420A:
    case VideoPixelFormat::I444:
        return 1;
    case VideoPixelFormat::MJPEG:
        return 0;
    case VideoPixelFormat::UNKNOWN:
        ALOGE("Invalid pixel format");
        return 0;
    }
}

bool isValidPlane(VideoPixelFormat format, size_t plane) {
    ALOG_ASSERT(numPlanes(format) <= static_cast<size_t>(kMaxPlanes));
    return plane < numPlanes(format);
}

android::ui::Size SampleSize(VideoPixelFormat format, size_t plane) {
    ALOG_ASSERT(isValidPlane(format, plane));

    switch (plane) {
    case kYPlane:  // and kARGBPlane:
    case kAPlane:
        return android::ui::Size(1, 1);

    case kUPlane:  // and kUVPlane:
    case kVPlane:
        switch (format) {
        case VideoPixelFormat::I444:
        case VideoPixelFormat::YUV444P9:
        case VideoPixelFormat::YUV444P10:
        case VideoPixelFormat::YUV444P12:
        case VideoPixelFormat::Y16:
            return android::ui::Size(1, 1);

        case VideoPixelFormat::I422:
        case VideoPixelFormat::YUV422P9:
        case VideoPixelFormat::YUV422P10:
        case VideoPixelFormat::YUV422P12:
            return android::ui::Size(2, 1);

        case VideoPixelFormat::YV12:
        case VideoPixelFormat::I420:
        case VideoPixelFormat::I420A:
        case VideoPixelFormat::NV12:
        case VideoPixelFormat::NV21:
        case VideoPixelFormat::YUV420P9:
        case VideoPixelFormat::YUV420P10:
        case VideoPixelFormat::YUV420P12:
        case VideoPixelFormat::P016LE:
            return android::ui::Size(2, 2);

        case VideoPixelFormat::UNKNOWN:
        case VideoPixelFormat::YUY2:
        case VideoPixelFormat::ARGB:
        case VideoPixelFormat::XRGB:
        case VideoPixelFormat::RGB24:
        case VideoPixelFormat::MJPEG:
        case VideoPixelFormat::ABGR:
        case VideoPixelFormat::XBGR:
        case VideoPixelFormat::XR30:
        case VideoPixelFormat::XB30:
        case VideoPixelFormat::BGRA:
        case VideoPixelFormat::RGBA:
            ALOGE("Invalid pixel format");
        }
    }

    return android::ui::Size();
}

}  // namespace android
