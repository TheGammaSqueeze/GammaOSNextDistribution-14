/*
 * Copyright 2023, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "AudioSPDIF"

#include <log/log.h>
#include <audio_utils/spdif/FrameScanner.h>

#include <map>
#include <string>

#include "SPDIFFrameScanner.h"

namespace android {

// Burst Preamble defined in IEC61937-1
const uint8_t SPDIFFrameScanner::kSyncBytes[] = {
    (kSpdifSync1 & 0x00ff), (kSpdifSync1 & 0xff00) >> 8,
    (kSpdifSync2 & 0x00ff), (kSpdifSync2 & 0xff00) >> 8,
};

static int getDataTypeForAudioFormat(audio_format_t format) {
    static const std::map<audio_format_t, int> sMapAudioFormatToDataType = {
        { AUDIO_FORMAT_AC3, kSpdifDataTypeAc3 },
        { AUDIO_FORMAT_E_AC3, kSpdifDataTypeEac3 },
        { AUDIO_FORMAT_E_AC3_JOC, kSpdifDataTypeEac3 }
    };
    if (sMapAudioFormatToDataType.find(format) != sMapAudioFormatToDataType.end()) {
        return sMapAudioFormatToDataType.at(format);
    }
    return 0;
}

SPDIFFrameScanner::SPDIFFrameScanner(audio_format_t format)
    : FrameScanner(getDataTypeForAudioFormat(format), SPDIFFrameScanner::kSyncBytes,
            sizeof(SPDIFFrameScanner::kSyncBytes), 8) {
    mRateMultiplier = spdif_rate_multiplier(format);
}

// Parse IEC61937 header.
//
// @return true if valid
bool SPDIFFrameScanner::parseHeader() {
    // Parse Pc and Pd of IEC61937 preamble.
    const uint16_t pc = mHeaderBuffer[4] | (mHeaderBuffer[5] << 8);
    const uint16_t pd = mHeaderBuffer[6] | (mHeaderBuffer[7] << 8);
    const uint8_t dataType = 0x007f & pc;
    if (dataType != getDataType()) {
        return false;
    }
    mDataType = dataType;
    mErrorFlag = (pc >> 7) & 1;
    if (mErrorFlag) {
        return false;
    }
    mDataTypeInfo = (pc >> 8) & 0x1f;
    mFrameSizeBytes = pd / ((mDataType == kSpdifDataTypeAc3) ? 8 : 1);
    return true;
}

}  // namespace android
