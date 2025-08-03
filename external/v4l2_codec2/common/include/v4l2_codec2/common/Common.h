// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef ANDROID_V4L2_CODEC2_COMMON_COMMON_H
#define ANDROID_V4L2_CODEC2_COMMON_COMMON_H

#include <inttypes.h>

#include <optional>
#include <string>
#include <vector>

#include <ui/Rect.h>
#include <ui/Size.h>

#include <v4l2_codec2/common/VideoPixelFormat.h>
#include <v4l2_codec2/common/VideoTypes.h>

namespace android {

// The stride, offset and size of a video frame plane.
struct VideoFramePlane {
    uint32_t mStride = 0;
    size_t mOffset = 0;
    size_t mSize = 0;
};

// A video frame's layout, containing pixel format, size and layout of individual planes.
struct VideoFrameLayout {
    VideoPixelFormat mFormat = VideoPixelFormat::UNKNOWN;
    android::ui::Size mCodedSize;
    std::vector<VideoFramePlane> mPlanes;
    bool mMultiPlanar = false;
};

// Specification of an encoding profile supported by an encoder or decoder.
struct SupportedProfile {
    C2Config::profile_t profile = C2Config::PROFILE_UNUSED;
    ui::Size min_resolution;
    ui::Size max_resolution;
    uint32_t max_framerate_numerator = 0;
    uint32_t max_framerate_denominator = 0;
    bool encrypted_only = false;
};
using SupportedProfiles = std::vector<SupportedProfile>;

// Contains the capabilites of the decoder or encoder.
struct SupportedCapabilities {
    VideoCodec codec;
    SupportedProfiles supportedProfiles;
    C2Config::profile_t defaultProfile = C2Config::PROFILE_UNUSED;
    std::vector<C2Config::level_t> supportedLevels;
    C2Config::level_t defaultLevel = C2Config::LEVEL_UNUSED;
};

// Check whether |rect1| completely contains |rect2|.
bool contains(const Rect& rect1, const Rect& rect2);

// Convert the specified |rect| to a string.
std::string toString(const Rect& rect);

// Get the area encapsulated by the |size|. Returns nullopt if multiplication causes overflow.
std::optional<int> getArea(const ui::Size& size);

// Check whether the specified |size| is empty
bool isEmpty(const ui::Size& size);

// Convert the specified |size| to a string.
std::string toString(const ui::Size& size);

// Check whether specified profile can be used with specified codec
bool isValidProfileForCodec(VideoCodec codec, C2Config::profile_t profile);

}  // namespace android

#endif  // ANDROID_V4L2_CODEC2_COMMON_COMMON_H
