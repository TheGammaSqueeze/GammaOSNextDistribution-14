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

#include <stdint.h>
#include <string.h>

#define LOG_TAG "AudioSPDIF"
// #define LOG_NDEBUG 0
#include <log/log.h>
#include <audio_utils/spdif/SPDIFDecoder.h>

#include "SPDIFFrameScanner.h"

namespace android {

SPDIFDecoder::SPDIFDecoder(audio_format_t format)
  : mFramer(std::make_unique<SPDIFFrameScanner>(format))
  , mBurstDataBuffer(sizeof(uint16_t) * kSpdifEncodedChannelCount
            * mFramer->getMaxSampleFramesPerSyncFrame())
  , mPayloadBytesPending(0)
  , mPayloadBytesRead(0)
  , mScanning(true) {
}

bool SPDIFDecoder::isFormatSupported(audio_format_t format) {
    switch (format) {
        case AUDIO_FORMAT_AC3:
        case AUDIO_FORMAT_E_AC3:
        case AUDIO_FORMAT_E_AC3_JOC:
            return true;
        default:
            return false;
    }
}

void SPDIFDecoder::reset() {
    ALOGV("SPDIFDecoder: reset()");
    if (mFramer != NULL) {
        mFramer->resetBurst();
    }
    mPayloadBytesPending = 0;
    mPayloadBytesRead = 0;
    mScanning = true;
}

ssize_t SPDIFDecoder::fillBurstDataBuffer() {
    const auto bytesToFill = mBurstDataBuffer.availableToWrite();
    std::vector<uint8_t> tmpBuffer(bytesToFill);
    auto bytesRead = readInput(&tmpBuffer[0], bytesToFill);
    if (bytesRead > 0) {
        ALOGV("SPDIFDecoder: read %zd burst data bytes", bytesRead);
        auto written = mBurstDataBuffer.write(tmpBuffer.data(), bytesRead);
        LOG_ALWAYS_FATAL_IF(written != bytesRead);
    }
    return bytesRead;
}

// Read burst payload data.
ssize_t SPDIFDecoder::read(void *buffer, size_t numBytes) {
    size_t bytesRead = 0;
    uint16_t *buf = reinterpret_cast<uint16_t *>(buffer);
    ALOGV("SPDIFDecoder: mScanning = %d numBytes = %zu", mScanning, numBytes);
    while (bytesRead < numBytes) {
        if (mBurstDataBuffer.empty()) {
            if (fillBurstDataBuffer() <= 0) {
              return -1;
            }
            if (bytesRead > 0) {
                return bytesRead;
            }
        }
        if (mScanning) {
            // Look for beginning of next IEC61937 frame.
            if (!mBurstDataBuffer.empty() && mFramer->scan(mBurstDataBuffer.readByte())) {
                mPayloadBytesPending = mFramer->getFrameSizeBytes();
                mPayloadBytesRead = 0;
                mScanning = false;
            }
        } else {
            // Read until we hit end of burst payload.
            size_t bytesToRead = std::min(numBytes, mBurstDataBuffer.availableToRead());
            // Only read as many as we need to finish the frame.
            if (bytesToRead > mPayloadBytesPending) {
                bytesToRead = mPayloadBytesPending;
            }
            // Unpack the bytes from short buffer into byte array in the order:
            //   short[0] MSB -> byte[0]
            //   short[0] LSB -> byte[1]
            //   short[1] MSB -> byte[2]
            //   short[1] LSB -> byte[3]
            //   ...
            // This way the payload should be extracted correctly on both
            // Big and Little Endian CPUs.
            uint16_t pad = 0;
            size_t actualBytesRead = 0;
            for (; !mBurstDataBuffer.empty() && actualBytesRead < bytesToRead; ++actualBytesRead) {
                if (mPayloadBytesRead & 1) {
                    pad |= mBurstDataBuffer.readByte();  // Read second byte from LSB
                    buf[bytesRead >> 1] = pad;
                    pad = 0;
                } else {
                    pad |= mBurstDataBuffer.readByte() << 8;  // Read first byte from MSB
                }
                mPayloadBytesRead++;
                bytesRead++;
            }
            // Read out last byte from partially filled short.
            if (mPayloadBytesRead & 1) {
                reinterpret_cast<uint8_t *>(buffer)[bytesRead] = pad >> 8;
            }

            buf += (bytesRead >> 1);
            mPayloadBytesPending -= actualBytesRead;
            mPayloadBytesRead += actualBytesRead;

            // If we have read the entire payload, prepare to look for the next IEC61937 frame.
            if (mPayloadBytesPending == 0) {
                reset();
            }
        }
    }
    return numBytes;
}

}  // namespace android
