/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include <array>
#include <climits>
#include <cmath>
#include <cstring>
#include <memory>
#include <vector>

#include <gtest/gtest.h>

#include <audio_utils/spdif/SPDIFDecoder.h>
#include <audio_utils/spdif/SPDIFEncoder.h>

using namespace android;

class MySPDIFEncoder : public SPDIFEncoder {
public:

    explicit MySPDIFEncoder(audio_format_t format)
            : SPDIFEncoder(format)
    {
    }
    // Defaults to AC3 format. Was in original API.
    MySPDIFEncoder() = default;

    ssize_t writeOutput( const void* /* buffer */, size_t numBytes ) override {
        mOutputSizeBytes = numBytes;
        return numBytes;
    }

    FrameScanner *getFramer() const { return mFramer; }
    size_t        getByteCursor() const { return mByteCursor; }
    size_t        getPayloadBytesPending() const { return mPayloadBytesPending; }
    size_t        getBurstBufferSizeBytes() const { return mBurstBufferSizeBytes; }

    size_t                     mOutputSizeBytes = 0;
};

class MySPDIFDecoder : public SPDIFDecoder {
 public:
    MySPDIFDecoder(audio_format_t format, const std::vector<uint8_t>&& inputData)
     : SPDIFDecoder(format)
     , mBurstGenerator(std::make_unique<BurstGenerator>(std::move(inputData),
            mFramer->getSampleFramesPerSyncFrame() * kSpdifEncodedChannelCount * sizeof(int16_t))) {
    }
    // This constructor creates an instance that will return error on reading input.
    explicit MySPDIFDecoder(audio_format_t format)
     : SPDIFDecoder(format)
     , mBurstGenerator(nullptr) {
    }

    ssize_t readInput(void* buffer, size_t numBytes) override {
        return mBurstGenerator == nullptr ? -1
                : mBurstGenerator->read(reinterpret_cast<uint8_t *>(buffer), numBytes);
    }

    FrameScanner &getFramer() const { return *mFramer; }

 private:
    // Generates data bursts of a given size with the provided input data.
    // If there are remaining bytes in the data burst after input data, they are filled with
    // incrementing values that wrap around.
    class BurstGenerator {
     public:
        BurstGenerator(const std::vector<uint8_t> &&inputData, size_t burstSizeBytes)
            : kBurstSizeBytes(burstSizeBytes)
            , mBurstBytesRead(0)
            , mInputData(inputData) {
        }

        ssize_t read(uint8_t* buffer, size_t numBytes) {
            auto bytesLeft = numBytes;
            while (bytesLeft > 0) {
                if (mBurstBytesRead < mInputData.size()) {
                    const auto bytesToRead = std::min(bytesLeft,
                            mInputData.size() - mBurstBytesRead);
                    memcpy(buffer, &mInputData[mBurstBytesRead], bytesToRead);
                    mBurstBytesRead += bytesToRead;
                    bytesLeft -= bytesToRead;
                    buffer += bytesToRead;
                } else if (mBurstBytesRead < kBurstSizeBytes) {
                    const auto bytesToRead = std::min(bytesLeft, kBurstSizeBytes - mBurstBytesRead);
                    // Pad remaining bytes with incrementing values that wrap around.
                    for (auto i = 0; i < bytesToRead; ++i) {
                        *buffer++ = (mBurstBytesRead++ - mInputData.size()) % 256;
                    }
                    bytesLeft -= bytesToRead;
                    buffer += bytesToRead;
                } else {
                    mBurstBytesRead = 0;
                }
            }
            return numBytes;
        }

     private:
        const size_t kBurstSizeBytes;
        size_t mBurstBytesRead;
        const std::vector<uint8_t> mInputData;
    };

    const std::unique_ptr<BurstGenerator> mBurstGenerator;
};

// This is the beginning of the file voice1-48k-64kbps-15s.ac3
static const uint8_t sVoice1ch48k_AC3[] = {
    0x0b, 0x77, 0x44, 0xcd, 0x08, 0x40, 0x2f, 0x84, 0x29, 0xca, 0x6e, 0x44, 0xa4, 0xfd, 0xce, 0xf7,
    0xc9, 0x9f, 0x3e, 0x74, 0xfa, 0x01, 0x0a, 0xda, 0xb3, 0x3e, 0xb0, 0x95, 0xf2, 0x5a, 0xef, 0x9e
};

// This is the beginning of the file channelcheck_48k6ch.eac3
static const uint8_t sChannel6ch48k_EAC3[] = {
    0x0b, 0x77, 0x01, 0xbf, 0x3f, 0x85, 0x7f, 0xe8, 0x1e, 0x40, 0x82, 0x10, 0x00, 0x00, 0x00, 0x01,
    0x00, 0x00, 0x00, 0x03, 0xfc, 0x60, 0x80, 0x7e, 0x59, 0x00, 0xfc, 0xf3, 0xcf, 0x01, 0xf9, 0xe7
};

// Size of the first frame of channelcheck_48k6ch.eac3, in number of bytes
static constexpr auto sChannel6ch48k_EAC3_frameSize = 896u;

// This is the beginning of the file channelcheck_48k6ch.eac3 after encapulating in IEC61937.
static const uint8_t sSpdif_Channel6ch48k_EAC3[] = {
    0x72, 0xf8, 0x1f, 0x4e, 0x15, 0x00, 0x80, 0x03, 0x77, 0x0b, 0xbf, 0x01, 0x85, 0x3f, 0xe8, 0x7f,
    0x40, 0x1e, 0x10, 0x82, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x03, 0x00, 0x60, 0xfc, 0x7e, 0x80
};

static const uint8_t sZeros[32] = { 0 };

static constexpr int kBytesPerOutputFrame = 2 * sizeof(int16_t); // stereo

static constexpr auto kIEC61937HeaderSize = 4 * sizeof(uint16_t);

TEST(audio_utils_spdif, SupportedFormats)
{
    ASSERT_FALSE(SPDIFEncoder::isFormatSupported(AUDIO_FORMAT_PCM_FLOAT));
    ASSERT_FALSE(SPDIFEncoder::isFormatSupported(AUDIO_FORMAT_PCM_16_BIT));
    ASSERT_FALSE(SPDIFEncoder::isFormatSupported(AUDIO_FORMAT_MP3));

    ASSERT_TRUE(SPDIFEncoder::isFormatSupported(AUDIO_FORMAT_AC3));
    ASSERT_TRUE(SPDIFEncoder::isFormatSupported(AUDIO_FORMAT_E_AC3));
    ASSERT_TRUE(SPDIFEncoder::isFormatSupported(AUDIO_FORMAT_DTS));
    ASSERT_TRUE(SPDIFEncoder::isFormatSupported(AUDIO_FORMAT_DTS_HD));
}

TEST(audio_utils_spdif, ScanAC3)
{
    MySPDIFEncoder encoder(AUDIO_FORMAT_AC3);
    FrameScanner *scanner = encoder.getFramer();
    // It should recognize the valid AC3 header.
    int i = 0;
    while (i < 5) {
        ASSERT_FALSE(scanner->scan(sVoice1ch48k_AC3[i++]));
    }
    ASSERT_TRUE(scanner->scan(sVoice1ch48k_AC3[i++]));
    ASSERT_FALSE(scanner->scan(sVoice1ch48k_AC3[i++]));
}

TEST(audio_utils_spdif, WriteAC3)
{
    MySPDIFEncoder encoder(AUDIO_FORMAT_AC3);
    encoder.write(sVoice1ch48k_AC3, sizeof(sVoice1ch48k_AC3));
    ASSERT_EQ(48000, encoder.getFramer()->getSampleRate());
    ASSERT_EQ(kBytesPerOutputFrame, encoder.getBytesPerOutputFrame());
    ASSERT_EQ(1, encoder.getRateMultiplier());

    // Check to make sure that the pending bytes calculation did not overflow.
    size_t burstBufferSizeBytes = encoder.getBurstBufferSizeBytes(); // allocated maximum size
    size_t pendingBytes = encoder.getPayloadBytesPending();
    ASSERT_GE(burstBufferSizeBytes, pendingBytes);

    // Write some fake compressed audio to force an output data burst.
    for (int i = 0; i < 7; i++) {
        auto result = encoder.write(sZeros, sizeof(sZeros));
        ASSERT_EQ(sizeof(sZeros), result);
    }
    // This value is calculated in SPDIFEncoder::sendZeroPad()
    //    size_t burstSize = mFramer->getSampleFramesPerSyncFrame() * sizeof(uint16_t)
    //        * kSpdifEncodedChannelCount;
    // If it changes then there is probably a regression.
    const int kExpectedBurstSize = 6144;
    ASSERT_EQ(kExpectedBurstSize, encoder.mOutputSizeBytes);
}

TEST(audio_utils_spdif, ValidEAC3)
{
    MySPDIFEncoder encoder(AUDIO_FORMAT_E_AC3);
    auto result = encoder.write(sChannel6ch48k_EAC3, sizeof(sChannel6ch48k_EAC3));
    ASSERT_EQ(sizeof(sChannel6ch48k_EAC3), result);
    ASSERT_EQ(kSpdifRateMultiplierEac3, encoder.getRateMultiplier());
    ASSERT_EQ(48000, encoder.getFramer()->getSampleRate());
    ASSERT_EQ(kBytesPerOutputFrame, encoder.getBytesPerOutputFrame());

    // Check to make sure that the pending bytes calculation did not overflow.
    size_t bufferSize = encoder.getBurstBufferSizeBytes();
    size_t pendingBytes = encoder.getPayloadBytesPending();
    ASSERT_GE(bufferSize, pendingBytes);
}

TEST(audio_utils_spdif, InvalidLengthEAC3)
{
    MySPDIFEncoder encoder(AUDIO_FORMAT_E_AC3);
    // Mangle a valid header and try to force a numeric overflow.
    uint8_t mangled[sizeof(sChannel6ch48k_EAC3)] = {0};
    memcpy(mangled, sChannel6ch48k_EAC3, sizeof(sChannel6ch48k_EAC3));

    // force frmsiz to zero!
    mangled[2] = mangled[2] & 0xF8;
    mangled[3] = 0;
    auto result = encoder.write(mangled, sizeof(mangled));
    ASSERT_EQ(sizeof(mangled), result);

    // Check to make sure that the pending bytes calculation did not overflow.
    size_t bufferSize = encoder.getBurstBufferSizeBytes();
    size_t pendingBytes = encoder.getPayloadBytesPending();
    ASSERT_GE(bufferSize, pendingBytes);

}

TEST(audio_utils_spdif, ScanSPDIF)
{
    std::vector<uint8_t> tmp;
    MySPDIFDecoder decoder(AUDIO_FORMAT_E_AC3, std::move(tmp));
    FrameScanner &scanner = decoder.getFramer();
    // It should recognize a valid IEC61937 header.
    int i = 0;
    while (i < kIEC61937HeaderSize - 1) {
        ASSERT_FALSE(scanner.scan(sSpdif_Channel6ch48k_EAC3[i++]));
    }
    ASSERT_TRUE(scanner.scan(sSpdif_Channel6ch48k_EAC3[i++]));
    ASSERT_FALSE(scanner.scan(sSpdif_Channel6ch48k_EAC3[i++]));
    ASSERT_EQ(kIEC61937HeaderSize, scanner.getHeaderSizeBytes());
    ASSERT_EQ(kSpdifDataTypeEac3, scanner.getDataType());
    ASSERT_EQ(kSpdifRateMultiplierEac3, scanner.getRateMultiplier());
    ASSERT_EQ(kSpdifRateMultiplierEac3 * 1536, scanner.getMaxSampleFramesPerSyncFrame());
    ASSERT_EQ(kSpdifRateMultiplierEac3 * 1536, scanner.getSampleFramesPerSyncFrame());
    ASSERT_EQ(sChannel6ch48k_EAC3_frameSize, scanner.getFrameSizeBytes());
}

TEST(audio_utils_spdif, ReadEAC3)
{
    constexpr auto kNumFrames = 2;  // Number of IEC61937 frames to read
    const std::vector<uint8_t> inputData(sSpdif_Channel6ch48k_EAC3,
            sSpdif_Channel6ch48k_EAC3 + sizeof(sSpdif_Channel6ch48k_EAC3));
    MySPDIFDecoder decoder(AUDIO_FORMAT_E_AC3, std::move(inputData));
    for (int n = 0; n < kNumFrames; ++n) {
        std::vector<uint8_t> buffer(sChannel6ch48k_EAC3_frameSize, 0xff);
        constexpr auto kChunkSize = 32u;
        for (int i = 0; i < sChannel6ch48k_EAC3_frameSize / kChunkSize; ++i) {
            ASSERT_EQ(kChunkSize, decoder.read(buffer.data() + i * kChunkSize, kChunkSize));
        }
        // Check the burst payload read from the decoder is correct
        constexpr auto kNumExtractedEac3Bytes = sizeof(sSpdif_Channel6ch48k_EAC3)
                - kIEC61937HeaderSize;
        ASSERT_EQ(0, memcmp(sChannel6ch48k_EAC3, buffer.data(), kNumExtractedEac3Bytes));
        uint16_t *p = reinterpret_cast<uint16_t *>(buffer.data());
        for (auto i = 0; i < (sChannel6ch48k_EAC3_frameSize - kNumExtractedEac3Bytes) / 2; ++i) {
            ASSERT_EQ(((i * 2) % 256) << 8 | ((i * 2 + 1) % 256),
                    p[kNumExtractedEac3Bytes / 2 + i]);
        }
    }
}

TEST(audio_utils_spdif, ReadErrorEAC3)
{
    MySPDIFDecoder decoder(AUDIO_FORMAT_E_AC3);
    std::vector<uint8_t> buffer(sChannel6ch48k_EAC3_frameSize, 0xff);
    constexpr auto kChunkSize = 32u;
    ASSERT_EQ(-1, decoder.read(buffer.data(), kChunkSize));
}

TEST(audio_utils_spdif, ReadAfterResetEAC3)
{
    const std::vector<uint8_t> inputData(sSpdif_Channel6ch48k_EAC3,
            sSpdif_Channel6ch48k_EAC3 + sizeof(sSpdif_Channel6ch48k_EAC3));
    MySPDIFDecoder decoder(AUDIO_FORMAT_E_AC3, std::move(inputData));
    constexpr auto kChunkSize = 32u;
    std::vector<uint8_t> buffer(sChannel6ch48k_EAC3_frameSize, 0xff);
    ASSERT_EQ(kChunkSize, decoder.read(buffer.data(), kChunkSize));
    constexpr auto kNumExtractedEac3Bytes = sizeof(sSpdif_Channel6ch48k_EAC3)
            - kIEC61937HeaderSize;
    ASSERT_EQ(0, memcmp(sChannel6ch48k_EAC3, buffer.data(), kNumExtractedEac3Bytes));

    // Reset after partial read and ensure decoder is able to resync to next data burst
    decoder.reset();
    for (int i = 0; i < sChannel6ch48k_EAC3_frameSize / kChunkSize; ++i) {
        ASSERT_EQ(kChunkSize, decoder.read(buffer.data() + i * kChunkSize, kChunkSize));
    }
    // Check the burst payload read from the decoder is correct
    ASSERT_EQ(0, memcmp(sChannel6ch48k_EAC3, buffer.data(), kNumExtractedEac3Bytes));
    uint16_t *p = reinterpret_cast<uint16_t *>(buffer.data());
    for (auto i = 0; i < (sChannel6ch48k_EAC3_frameSize - kNumExtractedEac3Bytes) / 2; ++i) {
        ASSERT_EQ(((i * 2) % 256) << 8 | ((i * 2 + 1) % 256), p[kNumExtractedEac3Bytes / 2 + i]);
    }
}
