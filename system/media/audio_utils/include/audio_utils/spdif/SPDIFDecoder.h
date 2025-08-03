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

#include <system/audio.h>
#include <audio_utils/CircularBuffer.h>
#include <audio_utils/spdif/FrameScanner.h>
#include <audio_utils/spdif/SPDIF.h>

#include <algorithm>
#include <cstdint>
#include <vector>

namespace android {

using audio_utils::CircularBuffer;

/**
 * Scan the incoming SPDIF stream for a frame sync.
 * Then unwrap the burst payload from the data burst and send it off to the receiver.
 */
class SPDIFDecoder {
 public:
    explicit SPDIFDecoder(audio_format_t format);
    virtual ~SPDIFDecoder() = default;

    /**
     * Read burst payload data.
     * @return number of bytes read or negative error
     */
    ssize_t read(void* buffer, size_t numBytes);

    /**
     * Called by SPDIFDecoder for it to read in SPDIF stream data.
     * Must be implemented in the subclass.
     * @return number of bytes read or negative error
     */
    virtual ssize_t readInput(void* buffer, size_t numBytes) = 0;

    /**
     * Get ratio of the encoded data burst sample rate to the encoded rate.
     * For example, EAC3 data bursts are 4X the encoded rate.
     */
    uint32_t getRateMultiplier() const { return mFramer->getRateMultiplier(); }

    /**
     * @return true if we can unwrap this format from a SPDIF stream
     */
    static bool isFormatSupported(audio_format_t format);

    /**
     * Discard any data in the buffer. Reset frame scanners.
     * This should be called when seeking to a new position in the stream.
     */
    void reset();

 protected:
    ssize_t fillBurstDataBuffer();

    std::unique_ptr<FrameScanner> mFramer;

    audio_format_t mAudioFormat;
    CircularBuffer mBurstDataBuffer;  // Stores burst data
    size_t mPayloadBytesPending;  // number of bytes of burst payload remaining to be extracted
    size_t mPayloadBytesRead;  // number of bytes of burst payload already extracted
    bool mScanning;  // state variable, true if scanning for start of SPDIF frame
};

}  // namespace android
