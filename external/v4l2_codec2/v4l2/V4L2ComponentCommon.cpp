// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

//#define LOG_NDEBUG 0
#define LOG_TAG "V4L2ComponentCommon"

#include <v4l2_codec2/v4l2/V4L2ComponentCommon.h>

#include <log/log.h>
#include <set>

namespace android {

const std::string V4L2ComponentName::kH264Encoder = "c2.v4l2.avc.encoder";
const std::string V4L2ComponentName::kVP8Encoder = "c2.v4l2.vp8.encoder";
const std::string V4L2ComponentName::kVP9Encoder = "c2.v4l2.vp9.encoder";

const std::string V4L2ComponentName::kH264Decoder = "c2.v4l2.avc.decoder";
const std::string V4L2ComponentName::kVP8Decoder = "c2.v4l2.vp8.decoder";
const std::string V4L2ComponentName::kVP9Decoder = "c2.v4l2.vp9.decoder";
const std::string V4L2ComponentName::kHEVCDecoder = "c2.v4l2.hevc.decoder";
const std::string V4L2ComponentName::kH264SecureDecoder = "c2.v4l2.avc.decoder.secure";
const std::string V4L2ComponentName::kVP8SecureDecoder = "c2.v4l2.vp8.decoder.secure";
const std::string V4L2ComponentName::kVP9SecureDecoder = "c2.v4l2.vp9.decoder.secure";
const std::string V4L2ComponentName::kHEVCSecureDecoder = "c2.v4l2.hevc.decoder.secure";

// static
bool V4L2ComponentName::isValid(const std::string& name) {
    return name == kH264Encoder || name == kVP8Encoder || name == kVP9Encoder ||
           name == kH264Decoder || name == kVP8Decoder || name == kVP9Decoder ||
           name == kHEVCDecoder || name == kH264SecureDecoder || name == kVP8SecureDecoder ||
           name == kVP9SecureDecoder || name == kHEVCSecureDecoder;
}

// static
bool V4L2ComponentName::isEncoder(const std::string& name) {
    ALOG_ASSERT(isValid(name));

    return name == kH264Encoder || name == kVP8Encoder || name == kVP9Encoder;
}

// static
bool V4L2ComponentName::isDecoder(const std::string& name) {
    ALOG_ASSERT(isValid(name));
    static const std::set<std::string> kValidDecoders = {
            kH264Decoder, kH264SecureDecoder, kVP8Decoder,  kVP8SecureDecoder,
            kVP9Decoder,  kVP9SecureDecoder,  kHEVCDecoder, kHEVCSecureDecoder,
    };

    return kValidDecoders.find(name) != kValidDecoders.end();
}

// static
std::optional<VideoCodec> V4L2ComponentName::getCodec(const std::string& name) {
    ALOG_ASSERT(isValid(name));
    static const std::map<std::string, VideoCodec> kNameToCodecs = {
            {kH264Decoder, VideoCodec::H264}, {kH264SecureDecoder, VideoCodec::H264},
            {kH264Encoder, VideoCodec::H264},

            {kVP8Decoder, VideoCodec::VP8},   {kVP8SecureDecoder, VideoCodec::VP8},
            {kVP8Encoder, VideoCodec::VP8},

            {kVP9Decoder, VideoCodec::VP9},   {kVP9SecureDecoder, VideoCodec::VP9},
            {kVP9Encoder, VideoCodec::VP9},

            {kHEVCDecoder, VideoCodec::HEVC}, {kHEVCSecureDecoder, VideoCodec::HEVC},
    };

    auto iter = kNameToCodecs.find(name);
    if (iter == kNameToCodecs.end()) {
        return std::nullopt;
    }
    return iter->second;
}

}  // namespace android
