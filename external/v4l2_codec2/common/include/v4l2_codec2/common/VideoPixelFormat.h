// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
// Note: ported from Chromium commit head: 3b7ce92816e2
// Note: only necessary functions are ported from video_types.h

#ifndef ANDROID_V4L2_CODEC2_COMMON_VIDEO_PIXEL_FORMAT_H
#define ANDROID_V4L2_CODEC2_COMMON_VIDEO_PIXEL_FORMAT_H

#include <string>

#include "ui/Size.h"

namespace android {

// Pixel formats roughly based on FOURCC labels, see:
// http://www.fourcc.org/rgb.php and http://www.fourcc.org/yuv.php
enum class VideoPixelFormat {
    I420,    // 12bpp YUV planar 1x1 Y, 2x2 UV samples, a.k.a. YU12.
    YV12,    // 12bpp YVU planar 1x1 Y, 2x2 VU samples.
    I422,    // 16bpp YUV planar 1x1 Y, 2x1 UV samples.
    I420A,   // 20bpp YUVA planar 1x1 Y, 2x2 UV, 1x1 A samples.
    I444,    // 24bpp YUV planar, no subsampling.
    NV12,    // 12bpp with Y plane followed by a 2x2 interleaved UV plane.
    NV21,    // 12bpp with Y plane followed by a 2x2 interleaved VU plane.
    YUY2,    // 16bpp interleaved 1x1 Y, 2x1 U, 1x1 Y, 2x1 V samples.
    ARGB,    // 32bpp BGRA (byte-order), 1 plane.
    XRGB,    // 24bpp BGRX (byte-order), 1 plane.
    RGB24,   // 24bpp BGR (byte-order), 1 plane.
    MJPEG,   // MJPEG compressed.
    Y16,     // single 16bpp plane.
    ABGR,    // 32bpp RGBA (byte-order), 1 plane.
    XBGR,    // 24bpp RGBX (byte-order), 1 plane.
    P016LE,  // 24bpp NV12, 16 bits per channel
    XR30,    // 32bpp BGRX, 10 bits per channel, 2 bits ignored, 1 plane
    XB30,    // 32bpp RGBX, 10 bits per channel, 2 bits ignored, 1 plane
    BGRA,    // 32bpp ARGB (byte-order), 1 plane.
    RGBA,    // 32bpp ABGR (byte-order), 1 plane.
    // The P* in the formats below designates the number of bits per pixel component. I.e. P9 is
    // 9-bits per pixel component, P10 is 10-bits per pixel component, etc.
    YUV420P9,
    YUV420P10,
    YUV422P9,
    YUV422P10,
    YUV444P9,
    YUV444P10,
    YUV420P12,
    YUV422P12,
    YUV444P12,
    UNKNOWN,  // Unknown or unspecified format value.
};

// Returns the name of a Format as a string.
std::string videoPixelFormatToString(VideoPixelFormat format);

// Returns human readable fourcc string. If any of the four characters is non-printable, it outputs
// "0x<32-bit integer in hex>", e.g. FourccToString(0x66616b00) returns "0x66616b00".
std::string fourccToString(uint32_t fourcc);

// Returns the number of significant bits per channel.
size_t bitDepth(VideoPixelFormat format);

// Returns the number of planes for the |format|.
size_t numPlanes(VideoPixelFormat format);

// Returns required allocation size for a (tightly packed) frame of the given coded size and format.
size_t allocationSize(VideoPixelFormat format, const android::ui::Size& coded_size);

// Returns the plane Size (in bytes) for a plane of the given coded size and format.
android::ui::Size planeSize(VideoPixelFormat format, size_t plane,
                            const android::ui::Size& coded_size);

// Returns horizontal bits per pixel for given |plane| and |format|.
int planeHorizontalBitsPerPixel(VideoPixelFormat format, size_t plane);

// Returns bits per pixel for given |plane| and |format|.
int planeBitsPerPixel(VideoPixelFormat format, size_t plane);

// Returns the number of bytes per element for given |plane| and |format|.
int bytesPerElement(VideoPixelFormat format, size_t plane);

// Returns true if |plane| is a valid plane index for the given |format|.
bool isValidPlane(VideoPixelFormat format, size_t plane);

// Returns the pixel size of each subsample for a given |plane| and |format|.
// E.g. 2x2 for the U-plane in I420.
android::ui::Size SampleSize(VideoPixelFormat format, size_t plane);

}  // namespace android

#endif  // ANDROID_V4L2_CODEC2_COMMON_VIDEO_PIXEL_FORMAT_H
