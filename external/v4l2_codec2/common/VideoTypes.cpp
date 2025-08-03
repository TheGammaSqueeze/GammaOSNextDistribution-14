// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

//#define LOG_NDEBUG 0
#define LOG_TAG "VideoTypes"

#include <v4l2_codec2/common/VideoTypes.h>

#include <log/log.h>

namespace android {

const char* VideoCodecToString(VideoCodec codec) {
    switch (codec) {
    case VideoCodec::H264:
        return "H264";
    case VideoCodec::VP8:
        return "VP8";
    case VideoCodec::VP9:
        return "VP9";
    case VideoCodec::HEVC:
        return "HEVC";
    }
}

const char* profileToString(C2Config::profile_t profile) {
    switch (profile) {
    case C2Config::PROFILE_UNUSED:
        return "unused";
    case C2Config::PROFILE_AVC_BASELINE:
        return "h264 baseline";
    case C2Config::PROFILE_AVC_CONSTRAINED_BASELINE:
        return "h264 constrained baseline";
    case C2Config::PROFILE_AVC_MAIN:
        return "h264 main";
    case C2Config::PROFILE_AVC_EXTENDED:
        return "h264 extended";
    case C2Config::PROFILE_AVC_HIGH:
        return "h264 high";
    case C2Config::PROFILE_AVC_HIGH_10:
        return "h264 high 10";
    case C2Config::PROFILE_AVC_HIGH_422:
        return "h264 high 4:2:2";
    case C2Config::PROFILE_AVC_HIGH_444_PREDICTIVE:
        return "h264 high 4:4:4 predictive";
    case C2Config::PROFILE_AVC_SCALABLE_BASELINE:
        return "h264 scalable baseline";
    case C2Config::PROFILE_AVC_SCALABLE_HIGH:
        return "h264 scalable high";
    case C2Config::PROFILE_AVC_STEREO_HIGH:
        return "h264 stereo high";
    case C2Config::PROFILE_AVC_MULTIVIEW_HIGH:
        return "h264 multiview high";
    case C2Config::PROFILE_HEVC_MAIN:
        return "hevc main";
    case C2Config::PROFILE_HEVC_MAIN_10:
        return "hevc main 10";
    case C2Config::PROFILE_HEVC_MAIN_STILL:
        return "hevc main still-picture";
    case C2Config::PROFILE_VP8_0:
        return "vp8 profile0";
    case C2Config::PROFILE_VP8_1:
        return "vp8 profile1";
    case C2Config::PROFILE_VP8_2:
        return "vp8 profile2";
    case C2Config::PROFILE_VP8_3:
        return "vp8 profile3";
    case C2Config::PROFILE_VP9_0:
        return "vp9 profile0";
    case C2Config::PROFILE_VP9_1:
        return "vp9 profile1";
    case C2Config::PROFILE_VP9_2:
        return "vp9 profile2";
    case C2Config::PROFILE_VP9_3:
        return "vp9 profile3";
    case C2Config::PROFILE_AV1_0:
        return "av1 profile 0";
    case C2Config::PROFILE_AV1_1:
        return "av1 profile 1";
    case C2Config::PROFILE_AV1_2:
        return "av1 profile 2";
    default:
        return "unknown";
    }
}

const char* HalPixelFormatToString(HalPixelFormat format) {
    switch (format) {
    case HalPixelFormat::UNKNOWN:
        return "Unknown";
    case HalPixelFormat::YCBCR_420_888:
        return "YCBCR_420_888";
    case HalPixelFormat::YV12:
        return "YV12";
    case HalPixelFormat::NV12:
        return "NV12";
    }
}

}  // namespace android
