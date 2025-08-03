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

#include <math.h>
#include <stdlib.h>
#include <algorithm>

#include "FLAC/stream_encoder.h"
#include "audio_utils/primitives.h"
#include "share/compat.h"

constexpr int kMinSampleRate = 1;
constexpr int kFramesPerBlock = 1152;
constexpr int kMaxSampleRate = 655350;
constexpr uint8_t kMinNumChannels = 1;
constexpr uint8_t kMaxNumChannels = 2;
constexpr uint8_t kMinCompressionLevel = 0;
constexpr uint8_t kMaxCompressionLevel = 8;

enum Encoding { PCM_16, PCM_FLOAT };

enum {
    IDX_SAMPLE_RATE_INDEX_1 = 0,
    IDX_SAMPLE_RATE_INDEX_2,
    IDX_SAMPLE_RATE_INDEX_3,
    IDX_CHANNEL,
    IDX_COMPRESSION_LEVEL,
    IDX_PCM,
    IDX_SET_VERIFY,
    IDX_SET_STREAMABLE_SUBSET,
    IDX_SET_DO_MID_SIDE_STEREO,
    IDX_SET_LOOSE_MID_SIDE_STEREO,
    IDX_SET_MAX_LPC_ORDER,
    IDX_SET_COEFF_PRECISION,
    IDX_SET_COEFF_PREC_SEARCH,
    IDX_SET_DO_ESCAPE_CODING,
    IDX_SET_DO_EXHAUSTIVE_MODEL_SEARCH,
    IDX_SET_MIN_RESIDUAL_PARTITION_ORDER,
    IDX_SET_MAX_RESIDUAL_PARTITION_ORDER,
    IDX_SET_RICE_PARAMETER_SEARCH_DIST,
    IDX_SET_TOTAL_SAMPLES_ESTIMATE,
    IDX_LAST
};

class Codec {
   public:
    ~Codec() { deInitEncoder(); }
    bool initEncoder(uint8_t **dataPtr, size_t *sizePtr);
    void encodeFrames(const uint8_t *data, size_t size);
    void deInitEncoder();
    static FLAC__StreamEncoderWriteStatus flacEncoderWriteCallback(
        const FLAC__StreamEncoder *encoder, const FLAC__byte buffer[], size_t bytes,
        unsigned samples, unsigned current_frame, void *client_data);

   private:
    bool verifyStateAndReturn();
    FLAC__StreamEncoder *mFlacStreamEncoder = nullptr;
    uint32_t mChannels = 0;
    uint32_t mPcmEncodingInfo = 0;
    FLAC__int32 mInputBufferPcm32[kFramesPerBlock * kMaxNumChannels] = {};
};

FLAC__StreamEncoderWriteStatus Codec::flacEncoderWriteCallback(const FLAC__StreamEncoder *encoder,
                                                               const FLAC__byte buffer[],
                                                               size_t bytes, unsigned samples,
                                                               unsigned current_frame,
                                                               void *client_data) {
    (void)encoder;
    (void)buffer;
    (void)bytes;
    (void)samples;
    (void)current_frame;
    (void)client_data;
    return FLAC__STREAM_ENCODER_WRITE_STATUS_OK;
}

bool Codec::verifyStateAndReturn() {
    FLAC__StreamEncoderState state = FLAC__stream_encoder_get_state(mFlacStreamEncoder);
    if (state == FLAC__STREAM_ENCODER_VERIFY_DECODER_ERROR) {
        FLAC__stream_encoder_get_verify_decoder_state(mFlacStreamEncoder);
    }
    return false;
}

template <typename type1, typename type2, typename type3>
auto generateNumberInRangeFromData(type1 data, type2 min, type3 max) -> decltype(max) {
    return (data % (1 + max - min)) + min;
}

bool Codec::initEncoder(uint8_t **dataPtr, size_t *sizePtr) {
    uint8_t *data = *dataPtr;
    mFlacStreamEncoder = FLAC__stream_encoder_new();
    if (!mFlacStreamEncoder) {
        return false;
    }

    // Clubbing 3 bytes of data to ensure sample rate in the range [1, 655350]
    uint32_t tempValue = (data[IDX_SAMPLE_RATE_INDEX_1] << 16) |
                         (data[IDX_SAMPLE_RATE_INDEX_2] << 8) | data[IDX_SAMPLE_RATE_INDEX_3];
    uint32_t sampleRate = generateNumberInRangeFromData(tempValue, kMinSampleRate, kMaxSampleRate);
    FLAC__stream_encoder_set_sample_rate(mFlacStreamEncoder, sampleRate);

    mChannels = generateNumberInRangeFromData(data[IDX_CHANNEL], kMinNumChannels, kMaxNumChannels);
    FLAC__stream_encoder_set_channels(mFlacStreamEncoder, mChannels);

    int compression = generateNumberInRangeFromData(data[IDX_COMPRESSION_LEVEL],
                                                    kMinCompressionLevel, kMaxCompressionLevel);
    FLAC__stream_encoder_set_compression_level(mFlacStreamEncoder, compression);

    uint32_t pcmEncodingInfo =
        generateNumberInRangeFromData(data[IDX_PCM], (int)PCM_16, (int)PCM_FLOAT);
    mPcmEncodingInfo = pcmEncodingInfo;
    uint32_t bitsPerSample = (mPcmEncodingInfo == PCM_FLOAT) ? 24 : 16;
    FLAC__stream_encoder_set_bits_per_sample(mFlacStreamEncoder, bitsPerSample);

    int ver = data[IDX_SET_VERIFY] % 2;
    FLAC__stream_encoder_set_verify(mFlacStreamEncoder, ver);

    int streamableSubset = data[IDX_SET_STREAMABLE_SUBSET] % 2;
    FLAC__stream_encoder_set_streamable_subset(mFlacStreamEncoder, streamableSubset);

    int doMidSideStereo = data[IDX_SET_DO_MID_SIDE_STEREO] % 2;
    FLAC__stream_encoder_set_do_mid_side_stereo(mFlacStreamEncoder, doMidSideStereo);

    int looseMidSideStereo = data[IDX_SET_LOOSE_MID_SIDE_STEREO] % 2;
    FLAC__stream_encoder_set_loose_mid_side_stereo(mFlacStreamEncoder, looseMidSideStereo);

    int maxLpcOrder = data[IDX_SET_MAX_LPC_ORDER] % 2;
    FLAC__stream_encoder_set_max_lpc_order(mFlacStreamEncoder, maxLpcOrder);

    int coeffPrec = data[IDX_SET_COEFF_PRECISION] % 2;
    FLAC__stream_encoder_set_qlp_coeff_precision(mFlacStreamEncoder, coeffPrec);

    int coeffPrecSearch = data[IDX_SET_COEFF_PREC_SEARCH] % 2;
    FLAC__stream_encoder_set_do_qlp_coeff_prec_search(mFlacStreamEncoder, coeffPrecSearch);

    int escCoding = data[IDX_SET_DO_ESCAPE_CODING] % 2;
    FLAC__stream_encoder_set_do_escape_coding(mFlacStreamEncoder, escCoding);

    int exhaustiveModelSearch = data[IDX_SET_DO_EXHAUSTIVE_MODEL_SEARCH] % 2;
    FLAC__stream_encoder_set_do_exhaustive_model_search(mFlacStreamEncoder, exhaustiveModelSearch);

    int minResidualPartitionOrder = data[IDX_SET_MIN_RESIDUAL_PARTITION_ORDER] % 2;
    FLAC__stream_encoder_set_min_residual_partition_order(mFlacStreamEncoder,
                                                          minResidualPartitionOrder);

    int maxResidualPartitionOrder = data[IDX_SET_MAX_RESIDUAL_PARTITION_ORDER] % 2;
    FLAC__stream_encoder_set_max_residual_partition_order(mFlacStreamEncoder,
                                                          maxResidualPartitionOrder);

    int riceParam = data[IDX_SET_RICE_PARAMETER_SEARCH_DIST] % 2;
    FLAC__stream_encoder_set_rice_parameter_search_dist(mFlacStreamEncoder, riceParam);

    int totalSamplesEstimate = data[IDX_SET_TOTAL_SAMPLES_ESTIMATE] % 2;
    FLAC__stream_encoder_set_total_samples_estimate(mFlacStreamEncoder, totalSamplesEstimate);

    FLAC__StreamEncoderInitStatus status = FLAC__stream_encoder_init_stream(
        mFlacStreamEncoder, flacEncoderWriteCallback /*write_callback*/, nullptr /*seek_callback*/,
        nullptr /*tell_callback*/, nullptr /*metadata_callback*/, (void *)this /*client_data*/);

    if (status != FLAC__STREAM_ENCODER_INIT_STATUS_OK) {
        return verifyStateAndReturn();
    }

    // Not re-using the data which was used for configuration for encoding
    *dataPtr += IDX_LAST;
    *sizePtr -= IDX_LAST;
    return true;
}

void Codec::encodeFrames(const uint8_t *data, size_t size) {
    size_t sampleSize = (mPcmEncodingInfo == PCM_FLOAT) ? sizeof(float) : sizeof(int16_t);
    size_t frameSize = mChannels * sampleSize;
    do {
        const size_t bytesConsumed = std::min(kFramesPerBlock * frameSize, size);
        const unsigned inputFrames = bytesConsumed / frameSize;
        const unsigned inputSamples = inputFrames * mChannels;
        if (mPcmEncodingInfo == PCM_FLOAT) {
            const float *const pcmFloat = reinterpret_cast<const float *>(data);
            memcpy_to_q8_23_from_float_with_clamp(mInputBufferPcm32, pcmFloat, inputSamples);
        } else {
            const int16_t *const pcm16 = reinterpret_cast<const int16_t *>(data);
            for (unsigned i = 0; i < inputSamples; ++i) {
                mInputBufferPcm32[i] = (FLAC__int32)pcm16[i];
            }
        }
        FLAC__stream_encoder_process_interleaved(mFlacStreamEncoder, mInputBufferPcm32,
                                                 inputFrames);
        data += bytesConsumed;
        size -= bytesConsumed;
    } while (size > 0);
}

void Codec::deInitEncoder() {
    if (mFlacStreamEncoder) {
        FLAC__stream_encoder_finish(mFlacStreamEncoder);
        FLAC__stream_encoder_delete(mFlacStreamEncoder);
        mFlacStreamEncoder = nullptr;
    }
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t *data, size_t size) {
    if (size < IDX_LAST) {
        return 0;
    }
    Codec encoder;
    if (encoder.initEncoder(const_cast<uint8_t **>(&data), &size)) {
        encoder.encodeFrames(data, size);
    }
    return 0;
}
