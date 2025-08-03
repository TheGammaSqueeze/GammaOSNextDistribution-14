// Copyright 2021 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef ANDROID_V4L2_CODEC2_COMMON_NALPARSER_H
#define ANDROID_V4L2_CODEC2_COMMON_NALPARSER_H

#include <stdint.h>

#include <media/stagefright/foundation/ABitReader.h>

namespace android {

// Helper class to parse NAL units from data.
class NalParser {
public:
    // Parameters related to a video's color aspects.
    struct ColorAspects {
        uint32_t primaries;
        uint32_t transfer;
        uint32_t coeffs;
        bool fullRange;
    };

    NalParser(const uint8_t* data, size_t length);
    virtual ~NalParser() = default;

    // Locates the next NAL after |mNextNalStartCodePos|. If there is one, updates |mCurrNalDataPos|
    // to the first byte of the NAL data (start code is not included), and |mNextNalStartCodePos| to
    // the position of the next start code, and returns true.
    // If there is no more NAL, returns false.
    //
    // Note: This method must be called prior to data() and length().
    bool locateNextNal();

    // Locate the sequence parameter set (SPS).
    virtual bool locateSPS() = 0;
    virtual bool locateIDR() = 0;

    // Gets current NAL data (start code is not included).
    const uint8_t* data() const;

    // Gets the byte length of current NAL data (start code is not included).
    size_t length() const;

    // Get the type of the current NAL unit.
    virtual uint8_t type() const = 0;

    // Find the video's color aspects in the current SPS NAL.
    virtual bool findCodedColorAspects(ColorAspects* colorAspects) = 0;

    // Read unsigned int encoded with exponential-golomb.
    static bool parseUE(ABitReader* br, uint32_t* val);

    // Read signed int encoded with exponential-golomb.
    static bool parseSE(ABitReader* br, int32_t* val);

protected:
    const uint8_t* findNextStartCodePos() const;

    // The byte pattern for the start of a NAL unit.
    const uint8_t kNalStartCode[3] = {0x00, 0x00, 0x01};
    // The length in bytes of the NAL-unit start pattern.
    const size_t kNalStartCodeLength = 3;

    const uint8_t* mCurrNalDataPos;
    const uint8_t* mDataEnd;
    const uint8_t* mNextNalStartCodePos;
};

}  // namespace android

#endif  // ANDROID_V4L2_CODEC2_COMMON_NALPARSER_H
