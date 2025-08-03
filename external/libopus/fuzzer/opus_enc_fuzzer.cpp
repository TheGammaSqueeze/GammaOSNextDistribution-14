/******************************************************************************
 *
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *****************************************************************************
 * Originally developed and contributed by Ittiam Systems Pvt. Ltd, Bangalore
 */

#include <algorithm>

#include "opus.h"

using namespace std;

constexpr int kFrameDuration = 50;
constexpr int kMaxPacket = 1500;
constexpr int kMinBitRate = 500;
constexpr int kMaxBitRate = 512000;

constexpr opus_int32 kSampleRates[] = {8000, 12000, 16000, 24000, 48000};
constexpr size_t kSampleRatesSize = size(kSampleRates);

#ifndef MULTISTREAM
constexpr int kChannels[] = {1, 2};
constexpr size_t kChannelsSize = size(kChannels);
#endif

constexpr int kApplications[] = {OPUS_APPLICATION_VOIP, OPUS_APPLICATION_AUDIO,
                                 OPUS_APPLICATION_RESTRICTED_LOWDELAY};
constexpr size_t kApplicationsSize = size(kApplications);

constexpr int kSignals[] = {OPUS_AUTO, OPUS_SIGNAL_VOICE, OPUS_SIGNAL_MUSIC};
constexpr size_t kSignalsSize = size(kSignals);

constexpr int kSetDTX[] = {0, 1};
constexpr size_t kSetDTXSize = size(kSetDTX);

constexpr int kSetVBR[] = {0, 1};
constexpr size_t kSetVBRSize = size(kSetVBR);

constexpr int kSetInbandFec[] = {0, 1};
constexpr size_t kSetInbandFecSize = size(kSetInbandFec);

constexpr int kSetVBRConstraint[] = {0, 1};
constexpr size_t kSetVBRConstraintSize = size(kSetVBRConstraint);

constexpr int kSetPredDisable[] = {0, 1};
constexpr size_t kSetPredDisableSize = size(kSetPredDisable);

constexpr int kComplexities[] = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
constexpr size_t kComplexitiesSize = size(kComplexities);

constexpr int kForceChannels[] = {OPUS_AUTO, 1, 2};
constexpr size_t kForceChannelsSize = size(kForceChannels);

constexpr int kMaxBandwidths[] = {OPUS_BANDWIDTH_NARROWBAND, OPUS_BANDWIDTH_MEDIUMBAND,
                                  OPUS_BANDWIDTH_WIDEBAND, OPUS_BANDWIDTH_SUPERWIDEBAND,
                                  OPUS_BANDWIDTH_FULLBAND};
constexpr size_t kMaxBandwidthsSize = size(kMaxBandwidths);

constexpr int kPacketLossPerc[] = {0, 1, 2, 5};
constexpr size_t kPacketLossPercSize = size(kPacketLossPerc);

constexpr int kLsbDepths[] = {8, 24};
constexpr size_t kLsbDepthsSize = size(kLsbDepths);

constexpr int kFrameDurations[] = {
    OPUS_FRAMESIZE_2_5_MS, OPUS_FRAMESIZE_5_MS,   OPUS_FRAMESIZE_10_MS,
    OPUS_FRAMESIZE_20_MS,  OPUS_FRAMESIZE_40_MS,  OPUS_FRAMESIZE_60_MS,
    OPUS_FRAMESIZE_80_MS,  OPUS_FRAMESIZE_100_MS, OPUS_FRAMESIZE_120_MS};
constexpr size_t kFrameDurationsSize = size(kFrameDurations);

#ifdef MULTISTREAM
#include "opus_multistream.h"
#define OPUS_ENC_DATA_TYPE OpusMSEncoder
#define OPUS_ENC_ENCODE_API opus_multistream_encode
#define OPUS_ENC_CTL_API opus_multistream_encoder_ctl
#define OPUS_ENC_CREATE_API ms_opus_encoder_create
#define OPUS_ENC_DESTROY_API opus_multistream_encoder_destroy
static OpusMSEncoder* ms_opus_encoder_create(opus_int32 sampleRate, int channels, int application,
                                             int* error) {
    unsigned char* mapping = (unsigned char*)malloc(sizeof(unsigned char) * channels);
    if (!mapping) {
        *error = 1;
        return nullptr;
    }
    for (unsigned char x = 0; x < channels; ++x) {
        mapping[x] = x;
    }
    OpusMSEncoder* enc = opus_multistream_encoder_create(sampleRate, channels, 1, channels - 1,
                                                         mapping, application, error);
    free(mapping);
    return enc;
}
#else
#define OPUS_ENC_DATA_TYPE OpusEncoder
#define OPUS_ENC_ENCODE_API opus_encode
#define OPUS_ENC_CTL_API opus_encoder_ctl
#define OPUS_ENC_CREATE_API opus_encoder_create
#define OPUS_ENC_DESTROY_API opus_encoder_destroy
#endif

enum {
    IDX_SAMPLE_RATE_INDEX = 0,
    IDX_CHANNEL,
    IDX_BIT_RATE_1,
    IDX_BIT_RATE_2,
    IDX_BIT_RATE_3,
    IDX_COMPLEXITY,
    IDX_APPLICATION,
    IDX_SET_DTX,
    IDX_SET_SIGNAL,
    IDX_SET_VBR,
    IDX_SET_VBR_CONSTRAINT,
    IDX_FORCE_CHANNEL_INDEX,
    IDX_SET_MAX_BANDWIDTH,
    IDX_SET_INBAND_FEC,
    IDX_SET_PACKET_LOSS_PERC,
    IDX_SET_LSB_DEPTH,
    IDX_SET_PREDICTION_DISABLED,
    IDX_FRAME_ENUM,
    IDX_LAST
};

template <typename type1, typename type2, typename type3>
auto generateNumberInRangeFromData(type1 data, type2 min, type3 max) -> decltype(max) {
    return (data % (1 + max - min)) + min;
}

class Codec {
   public:
    ~Codec() { deInitEncoder(); }
    bool initEncoder(uint8_t** dataPtr, size_t* sizePtr);
    void encodeFrames(const uint8_t* data, size_t size);
    void deInitEncoder();

   private:
    OPUS_ENC_DATA_TYPE* mEncoder = nullptr;
    int mChannels = 0;
    int mNumSamplesPerFrame = 0;
    size_t mFrameSize = 0;
    size_t mNumPcmBytesPerInputFrame = 0;
};

int get_frame_size(int frameSizeEnum, int samplingRate) {
    int frameSize = 0;
    switch (frameSizeEnum) {
        case OPUS_FRAMESIZE_2_5_MS:
            frameSize = samplingRate / 400;
            break;
        case OPUS_FRAMESIZE_5_MS:
            frameSize = samplingRate / 200;
            break;
        case OPUS_FRAMESIZE_10_MS:
            frameSize = samplingRate / 100;
            break;
        case OPUS_FRAMESIZE_20_MS:
            frameSize = samplingRate / 50;
            break;
        case OPUS_FRAMESIZE_40_MS:
            frameSize = samplingRate / 25;
            break;
        case OPUS_FRAMESIZE_60_MS:
            frameSize = 3 * samplingRate / 50;
            break;
        case OPUS_FRAMESIZE_80_MS:
            frameSize = 4 * samplingRate / 50;
            break;
        case OPUS_FRAMESIZE_100_MS:
            frameSize = 5 * samplingRate / 50;
            break;
        case OPUS_FRAMESIZE_120_MS:
            frameSize = 6 * samplingRate / 50;
            break;
        default:
            break;
    }
    return frameSize;
}

bool Codec::initEncoder(uint8_t** dataPtr, size_t* sizePtr) {
    uint8_t* data = *dataPtr;

    int sampleRateIndex = data[IDX_SAMPLE_RATE_INDEX] % kSampleRatesSize;
    opus_int32 sampleRate = kSampleRates[sampleRateIndex];

#ifdef MULTISTREAM
    mChannels = generateNumberInRangeFromData(data[IDX_CHANNEL], 1, 255);
#else
    int channelIndex = data[IDX_CHANNEL] % kChannelsSize;
    mChannels = kChannels[channelIndex];
#endif

    mNumSamplesPerFrame = sampleRate / kFrameDuration;
    mNumPcmBytesPerInputFrame = mChannels * mNumSamplesPerFrame * sizeof(int16_t);

    int application = kApplications[data[IDX_APPLICATION] % kApplicationsSize];
    int err = 0;
    mEncoder = OPUS_ENC_CREATE_API(sampleRate, mChannels, application, &err);
    if (err) {
        return false;
    }
    OPUS_ENC_CTL_API(mEncoder, OPUS_SET_APPLICATION(application));

    int complexityIndex = data[IDX_COMPLEXITY] % kComplexitiesSize;
    int complexity = kComplexities[complexityIndex];
    OPUS_ENC_CTL_API(mEncoder, OPUS_SET_COMPLEXITY(complexity));

    int setDTXIndex = data[IDX_SET_DTX] % kSetDTXSize;
    int setDTX = kSetDTX[setDTXIndex];
    OPUS_ENC_CTL_API(mEncoder, OPUS_SET_DTX(setDTX));

    int signalIndex = data[IDX_SET_SIGNAL] % kSignalsSize;
    int signal = kSignals[signalIndex];
    OPUS_ENC_CTL_API(mEncoder, OPUS_SET_SIGNAL(signal));

    int setVBRIndex = data[IDX_SET_VBR] % kSetVBRSize;
    int setVBR = kSetVBR[setVBRIndex];
    OPUS_ENC_CTL_API(mEncoder, OPUS_SET_VBR(setVBR));

    int setVBRConstraintIndex = data[IDX_SET_VBR_CONSTRAINT] % kSetVBRConstraintSize;
    int setVBRConstraint = kSetVBRConstraint[setVBRConstraintIndex];
    OPUS_ENC_CTL_API(mEncoder, OPUS_SET_VBR_CONSTRAINT(setVBRConstraint));

    // Clubbing 3 bytes of data to ensure bit rate in the range [kMinBitRate, kMaxBitRate]
    uint32_t tempValue =
        (data[IDX_BIT_RATE_1] << 16) | (data[IDX_BIT_RATE_2] << 8) | data[IDX_BIT_RATE_3];
    uint32_t bitRate = generateNumberInRangeFromData(tempValue, kMinBitRate, kMaxBitRate);
    OPUS_ENC_CTL_API(mEncoder, OPUS_SET_BITRATE(bitRate));

    int forceChanneIndex = data[IDX_FORCE_CHANNEL_INDEX] % kForceChannelsSize;
    int forceChannel = kForceChannels[forceChanneIndex];
    forceChannel = min(forceChannel, mChannels);
    OPUS_ENC_CTL_API(mEncoder, OPUS_SET_FORCE_CHANNELS(forceChannel));

    int maxBandwidthIndex = data[IDX_SET_MAX_BANDWIDTH] % kMaxBandwidthsSize;
    opus_int32 maxBandwidth = kMaxBandwidths[maxBandwidthIndex];
    OPUS_ENC_CTL_API(mEncoder, OPUS_SET_MAX_BANDWIDTH(maxBandwidth));

    int setInbandFecIndex = data[IDX_SET_INBAND_FEC] % kSetInbandFecSize;
    int setInbandFec = kSetInbandFec[setInbandFecIndex];
    OPUS_ENC_CTL_API(mEncoder, OPUS_SET_INBAND_FEC(setInbandFec));

    int pktLossIndex = data[IDX_SET_PACKET_LOSS_PERC] % kPacketLossPercSize;
    int pktLoss = kPacketLossPerc[pktLossIndex];
    OPUS_ENC_CTL_API(mEncoder, OPUS_SET_PACKET_LOSS_PERC(pktLoss));

    int lsbDepthIndex = data[IDX_SET_LSB_DEPTH] % kLsbDepthsSize;
    int lsbDepth = kLsbDepths[lsbDepthIndex];
    OPUS_ENC_CTL_API(mEncoder, OPUS_SET_LSB_DEPTH(lsbDepth));

    int setPredDisableIndex = data[IDX_SET_PREDICTION_DISABLED] % kSetPredDisableSize;
    int setPredDisable = kSetPredDisable[setPredDisableIndex];
    OPUS_ENC_CTL_API(mEncoder, OPUS_SET_PREDICTION_DISABLED(setPredDisable));

    int frameSizesEnumIndex = data[IDX_FRAME_ENUM] % kFrameDurationsSize;
    int frameSizeEnum = kFrameDurations[frameSizesEnumIndex];
    OPUS_ENC_CTL_API(mEncoder, OPUS_SET_EXPERT_FRAME_DURATION(frameSizeEnum));

    mFrameSize = get_frame_size(frameSizeEnum, sampleRate);
    if (mFrameSize == 0) {
        return false;
    }

    // Not re-using the data which was used for configuration for encoding
    *dataPtr += IDX_LAST;
    *sizePtr -= IDX_LAST;
    return true;
}

void Codec::encodeFrames(const uint8_t* data, size_t size) {
    opus_int16* inputBuffer = (opus_int16*)data;
    size = size / sizeof(opus_int16);
    size_t offset = 0;
    do {
        size_t frameSize = mFrameSize / mChannels;
        if (frameSize > (size - offset)) {
            frameSize = size - offset;
        }
        unsigned char packet[kMaxPacket];
        (void)OPUS_ENC_ENCODE_API(mEncoder, &inputBuffer[offset], frameSize, packet, kMaxPacket);
        offset += mFrameSize * mChannels;
    } while (offset < size);
}

void Codec::deInitEncoder() {
    if (mEncoder) {
        OPUS_ENC_DESTROY_API(mEncoder);
        mEncoder = nullptr;
    }
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
    if (size < IDX_LAST) {
        return 0;
    }
    Codec encoder;
    if (encoder.initEncoder(const_cast<uint8_t**>(&data), &size)) {
        encoder.encodeFrames(data, size);
    }
    return 0;
}
