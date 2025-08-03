// Copyright 2021 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef ANDROID_V4L2_CODEC2_COMMON_H264NALPARSER_H
#define ANDROID_V4L2_CODEC2_COMMON_H264NALPARSER_H

#include <stdint.h>

#include <v4l2_codec2/common/NalParser.h>

namespace android {

// Helper class to parse H264 NAL units from data.
class H264NalParser : public NalParser {
public:
    // Type of a IDR Slice NAL unit.
    static constexpr uint8_t kIDRType = 5;
    // Type of a SPS NAL unit.
    static constexpr uint8_t kSPSType = 7;
    // Type of a PPS NAL unit.
    static constexpr uint8_t kPPSType = 8;

    H264NalParser(const uint8_t* data, size_t length);
    ~H264NalParser() = default;

    // Locate the sequence parameter set (SPS).
    bool locateSPS() override;
    bool locateIDR() override;

    // Get the type of the current NAL unit.
    uint8_t type() const;

    // Find the H.264 video's color aspects in the current SPS NAL.
    bool findCodedColorAspects(ColorAspects* colorAspects) override;
};

}  // namespace android

#endif  // ANDROID_V4L2_CODEC2_COMMON_H264NALPARSER_H
