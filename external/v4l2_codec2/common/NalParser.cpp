// Copyright 2021 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

//#define LOG_NDEBUG 0
#define LOG_TAG "NalParser"

#include <algorithm>

#include <v4l2_codec2/common/NalParser.h>

#include <media/stagefright/foundation/ABitReader.h>

namespace android {

NalParser::NalParser(const uint8_t* data, size_t length)
      : mCurrNalDataPos(data), mDataEnd(data + length) {
    mNextNalStartCodePos = findNextStartCodePos();
}

bool NalParser::locateNextNal() {
    if (mNextNalStartCodePos == mDataEnd) return false;
    mCurrNalDataPos = mNextNalStartCodePos + kNalStartCodeLength;  // skip start code.
    mNextNalStartCodePos = findNextStartCodePos();
    return true;
}

const uint8_t* NalParser::data() const {
    return mCurrNalDataPos;
}

size_t NalParser::length() const {
    if (mNextNalStartCodePos == mDataEnd) return mDataEnd - mCurrNalDataPos;
    size_t length = mNextNalStartCodePos - mCurrNalDataPos;
    // The start code could be 3 or 4 bytes, i.e., 0x000001 or 0x00000001.
    return *(mNextNalStartCodePos - 1) == 0x00 ? length - 1 : length;
}

const uint8_t* NalParser::findNextStartCodePos() const {
    return std::search(mCurrNalDataPos, mDataEnd, kNalStartCode,
                       kNalStartCode + kNalStartCodeLength);
}

// Read unsigned int encoded with exponential-golomb.
bool NalParser::parseUE(ABitReader* br, uint32_t* val) {
    uint32_t numZeroes = 0;
    uint32_t bit;
    if (!br->getBitsGraceful(1, &bit)) return false;
    while (bit == 0) {
        ++numZeroes;
        if (!br->getBitsGraceful(1, &bit)) return false;
    }
    if (!br->getBitsGraceful(numZeroes, val)) return false;
    *val += (1u << numZeroes) - 1;
    return true;
}

// Read signed int encoded with exponential-golomb.
bool NalParser::parseSE(ABitReader* br, int32_t* val) {
    uint32_t codeNum;
    if (!parseUE(br, &codeNum)) return false;
    *val = (codeNum & 1) ? (codeNum + 1) >> 1 : -static_cast<int32_t>(codeNum >> 1);
    return true;
}

}  // namespace android
