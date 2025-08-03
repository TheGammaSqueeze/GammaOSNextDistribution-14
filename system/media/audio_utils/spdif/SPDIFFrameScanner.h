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

#pragma once

#include <stdint.h>
#include <system/audio.h>
#include <audio_utils/spdif/FrameScanner.h>
#include <audio_utils/spdif/SPDIF.h>

namespace android {

inline constexpr uint32_t kEac3PcmFramesPerBlock = 256u;
inline constexpr uint32_t kEac3MaxBlocksPerSyncFrame = 6u;

// Scanner for IEC61937 streams.
class SPDIFFrameScanner : public FrameScanner {
 public:
    explicit SPDIFFrameScanner(audio_format_t format);

    virtual int getMaxChannels() const { return kSpdifEncodedChannelCount; }  // IEC61937

    virtual int getMaxSampleFramesPerSyncFrame() const {
        return kSpdifRateMultiplierEac3 * kEac3MaxBlocksPerSyncFrame
                * kEac3PcmFramesPerBlock;
    }

    virtual int getSampleFramesPerSyncFrame() const {
        return mRateMultiplier * kEac3MaxBlocksPerSyncFrame * kEac3PcmFramesPerBlock;
    }

    virtual bool isFirstInBurst() { return true; }
    virtual bool isLastInBurst() { return true; }
    virtual void resetBurst() { }

 protected:
    // used to recognize the start of an IEC61937 frame
    static const uint8_t kSyncBytes[];

    virtual bool parseHeader();
};

}  // namespace android
