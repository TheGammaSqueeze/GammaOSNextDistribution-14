/*
 * Copyright (C) 2021 The Android Open Source Project
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
#include "channels.h"
#include <math.h>

namespace android::audio_utils::channels {

// sparseChannelMatrixMultiply must be compiled with specific compiler flags
// for optimization.  The body is in ChannelMix.cpp.
template <audio_channel_mask_t INPUT_CHANNEL_MASK,
        audio_channel_mask_t OUTPUT_CHANNEL_MASK, bool ACCUMULATE>
bool sparseChannelMatrixMultiply(const float *src, float *dst, size_t frameCount);

inline float clamp(float value) {
    constexpr float LIMIT_AMPLITUDE = M_SQRT2;       // 3dB = 1.41421356
    return fmin(fmax(value, -LIMIT_AMPLITUDE), LIMIT_AMPLITUDE);
}

// This method can be evaluated constexpr.
template <audio_channel_mask_t OUTPUT_CHANNEL_MASK, size_t M>
constexpr bool fillChannelMatrix(audio_channel_mask_t INPUT_CHANNEL_MASK,
        float (&matrix)[M][audio_channel_count_from_out_mask(OUTPUT_CHANNEL_MASK)]) {

    // This is a bit long since there is no functional partial template specialization.
    if constexpr (OUTPUT_CHANNEL_MASK == AUDIO_CHANNEL_OUT_STEREO) {
        // Compute at what index each channel is: samples will be in the following order:
        //   FL  FR  FC    LFE   BL  BR  BC    SL  SR
        //
        // Prior to API 32, use of downmix resulted in channels being scaled in half amplitude.
        // We now use a compliant downmix matrix for 5.1 with the following standards:
        // ITU-R 775-2, ATSC A/52, ETSI TS 101 154, IEC 14496-3, which is unity gain for the
        // front left and front right channel contribution.
        //
        // For 7.1 to 5.1 we set equal contributions for the side and back channels
        // which follow Dolby downmix recommendations.
        //
        // We add contributions from the LFE into the L and R channels
        // at a weight of 0.5 (rather than the power preserving 0.707)
        // which is to ensure that headphones can still experience LFE
        // with lesser risk of speaker overload.
        //
        // Note: geometrically left and right channels contribute only to the corresponding
        // left and right outputs respectively.  Geometrically center channels contribute
        // to both left and right outputs, so they are scaled by 0.707 to preserve power.
        //
        //  (transfer matrix)
        //   FL  FR  FC    LFE  BL  BR     BC  SL    SR
        //   1.0     0.707 0.5  0.707      0.5 0.707
        //       1.0 0.707 0.5       0.707 0.5       0.707
        size_t index = 0;
        constexpr float COEF_25 = 0.2508909536f;
        constexpr float COEF_35 = 0.3543928915f;
        constexpr float COEF_36 = 0.3552343859f;
        constexpr float COEF_61 = 0.6057043428f;
        constexpr float MINUS_3_DB_IN_FLOAT = M_SQRT1_2; // -3dB = 0.70710678

        constexpr size_t FL = 0;
        constexpr size_t FR = 1;
        for (unsigned tmp = INPUT_CHANNEL_MASK; tmp != 0; ++index) {
            if (index >= M) return false;
            const unsigned lowestBit = tmp & -(signed)tmp;
            switch (lowestBit) {
                case AUDIO_CHANNEL_OUT_FRONT_LEFT:
                case AUDIO_CHANNEL_OUT_TOP_FRONT_LEFT:
                case AUDIO_CHANNEL_OUT_BOTTOM_FRONT_LEFT:
                    matrix[index][FL] = 1.f;
                    matrix[index][FR] = 0.f;
                    break;
                case AUDIO_CHANNEL_OUT_SIDE_LEFT:
                case AUDIO_CHANNEL_OUT_BACK_LEFT:
                case AUDIO_CHANNEL_OUT_TOP_BACK_LEFT:
                case AUDIO_CHANNEL_OUT_FRONT_WIDE_LEFT: // FRONT_WIDE closer to SIDE.
                    matrix[index][FL] = MINUS_3_DB_IN_FLOAT;
                    matrix[index][FR] = 0.f;
                    break;
                case AUDIO_CHANNEL_OUT_FRONT_RIGHT:
                case AUDIO_CHANNEL_OUT_TOP_FRONT_RIGHT:
                case AUDIO_CHANNEL_OUT_BOTTOM_FRONT_RIGHT:
                    matrix[index][FL] = 0.f;
                    matrix[index][FR] = 1.f;
                    break;
                case AUDIO_CHANNEL_OUT_SIDE_RIGHT:
                case AUDIO_CHANNEL_OUT_BACK_RIGHT:
                case AUDIO_CHANNEL_OUT_TOP_BACK_RIGHT:
                case AUDIO_CHANNEL_OUT_FRONT_WIDE_RIGHT: // FRONT_WIDE closer to SIDE.
                    matrix[index][FL] = 0.f;
                    matrix[index][FR] = MINUS_3_DB_IN_FLOAT;
                    break;
                case AUDIO_CHANNEL_OUT_FRONT_CENTER:
                case AUDIO_CHANNEL_OUT_TOP_FRONT_CENTER:
                case AUDIO_CHANNEL_OUT_BOTTOM_FRONT_CENTER:
                    matrix[index][FL] = matrix[index][FR] = MINUS_3_DB_IN_FLOAT;
                    break;
                case AUDIO_CHANNEL_OUT_TOP_SIDE_LEFT:
                    matrix[index][FL] = COEF_61;
                    matrix[index][FR] = 0.f;
                    break;
                case AUDIO_CHANNEL_OUT_TOP_SIDE_RIGHT:
                    matrix[index][FL] = 0.f;
                    matrix[index][FR] = COEF_61;
                    break;
                case AUDIO_CHANNEL_OUT_FRONT_LEFT_OF_CENTER:
                    matrix[index][FL] = COEF_61;
                    matrix[index][FR] = COEF_25;
                    break;
                case AUDIO_CHANNEL_OUT_FRONT_RIGHT_OF_CENTER:
                    matrix[index][FL] = COEF_25;
                    matrix[index][FR] = COEF_61;
                    break;
                case AUDIO_CHANNEL_OUT_TOP_CENTER:
                    matrix[index][FL] = matrix[index][FR] = COEF_36;
                    break;
                case AUDIO_CHANNEL_OUT_TOP_BACK_CENTER:
                    matrix[index][FL] = matrix[index][FR] = COEF_35;
                    break;
                case AUDIO_CHANNEL_OUT_LOW_FREQUENCY_2:
                    matrix[index][FL] = 0.f;
                    matrix[index][FR] = MINUS_3_DB_IN_FLOAT;
                    break;
                case AUDIO_CHANNEL_OUT_LOW_FREQUENCY:
                    if (INPUT_CHANNEL_MASK & AUDIO_CHANNEL_OUT_LOW_FREQUENCY_2) {
                        matrix[index][FL] = MINUS_3_DB_IN_FLOAT;
                        matrix[index][FR] = 0.f;
                        break;
                    }
                    FALLTHROUGH_INTENDED;
                case AUDIO_CHANNEL_OUT_BACK_CENTER:
                    matrix[index][FL] = matrix[index][FR] = 0.5f;
                    break;
            }
            tmp ^= lowestBit;
        }
        return true;
    } else if constexpr (OUTPUT_CHANNEL_MASK == AUDIO_CHANNEL_OUT_5POINT1) {
        //   FL  FR  FC  LFE  BL  BR
        size_t index = 0;
        constexpr float MINUS_3_DB_IN_FLOAT = M_SQRT1_2; // -3dB = 0.70710678
        constexpr float MINUS_4_5_DB_IN_FLOAT = 0.5946035575f;

        constexpr size_t FL = 0;
        constexpr size_t FR = 1;
        constexpr size_t FC = 2;
        constexpr size_t LFE = 3;
        constexpr size_t BL = 4;
        constexpr size_t BR = 5;
        for (unsigned tmp = INPUT_CHANNEL_MASK; tmp != 0; ++index) {
            if (index >= M) return false;
            const unsigned lowestBit = tmp & -(signed)tmp;
            matrix[index][FL] = matrix[index][FR] = matrix[index][FC] = 0.f;
            matrix[index][LFE] = matrix[index][BL] = matrix[index][BR] = 0.f;
            switch (lowestBit) {
                case AUDIO_CHANNEL_OUT_FRONT_LEFT:
                case AUDIO_CHANNEL_OUT_TOP_FRONT_LEFT:
                case AUDIO_CHANNEL_OUT_BOTTOM_FRONT_LEFT:
                    matrix[index][FL] = 1.f;
                    break;
                case AUDIO_CHANNEL_OUT_FRONT_RIGHT:
                case AUDIO_CHANNEL_OUT_TOP_FRONT_RIGHT:
                case AUDIO_CHANNEL_OUT_BOTTOM_FRONT_RIGHT:
                    matrix[index][FR] = 1.f;
                    break;

                case AUDIO_CHANNEL_OUT_FRONT_CENTER:
                case AUDIO_CHANNEL_OUT_TOP_FRONT_CENTER:
                case AUDIO_CHANNEL_OUT_BOTTOM_FRONT_CENTER:
                    matrix[index][FC] = 1.f;
                    break;

                // ADJUST
                case AUDIO_CHANNEL_OUT_FRONT_WIDE_LEFT: // FRONT_WIDE closer to SIDE.
                    matrix[index][FL] = MINUS_3_DB_IN_FLOAT;
                    matrix[index][BL] = MINUS_4_5_DB_IN_FLOAT;
                    break;
                case AUDIO_CHANNEL_OUT_FRONT_WIDE_RIGHT: // FRONT_WIDE closer to SIDE.
                    matrix[index][FR] = MINUS_3_DB_IN_FLOAT;
                    matrix[index][BR] = MINUS_4_5_DB_IN_FLOAT;
                    break;

                case AUDIO_CHANNEL_OUT_FRONT_LEFT_OF_CENTER:
                    matrix[index][FL] = MINUS_4_5_DB_IN_FLOAT;
                    matrix[index][FC] = MINUS_3_DB_IN_FLOAT;
                    break;
                case AUDIO_CHANNEL_OUT_FRONT_RIGHT_OF_CENTER:
                    matrix[index][FR] = MINUS_4_5_DB_IN_FLOAT;
                    matrix[index][FC] = MINUS_3_DB_IN_FLOAT;
                    break;

                case AUDIO_CHANNEL_OUT_SIDE_LEFT:
                case AUDIO_CHANNEL_OUT_BACK_LEFT:
                case AUDIO_CHANNEL_OUT_TOP_BACK_LEFT:
                    matrix[index][BL] = 1.f;
                    break;
                case AUDIO_CHANNEL_OUT_SIDE_RIGHT:
                case AUDIO_CHANNEL_OUT_BACK_RIGHT:
                case AUDIO_CHANNEL_OUT_TOP_BACK_RIGHT:
                    matrix[index][BR] = 1.f;
                    break;

                case AUDIO_CHANNEL_OUT_TOP_SIDE_LEFT:
                    matrix[index][BL] = 1.f;
                    break;
                case AUDIO_CHANNEL_OUT_TOP_SIDE_RIGHT:
                    matrix[index][BR] = 1.f;
                    break;

                case AUDIO_CHANNEL_OUT_TOP_BACK_CENTER:
                case AUDIO_CHANNEL_OUT_BACK_CENTER:
                    matrix[index][BL] = matrix[index][BR] = MINUS_3_DB_IN_FLOAT;
                    break;

                case AUDIO_CHANNEL_OUT_TOP_CENTER:
                    matrix[index][FC] = matrix[index][BL] = matrix[index][BR] = 0.5f;
                    break;

                case AUDIO_CHANNEL_OUT_LOW_FREQUENCY:
                case AUDIO_CHANNEL_OUT_LOW_FREQUENCY_2:
                    matrix[index][LFE] = 1.f;
                    break;
            }
            tmp ^= lowestBit;
        }
        return true;
    } else if constexpr (OUTPUT_CHANNEL_MASK == AUDIO_CHANNEL_OUT_7POINT1) {
        //   FL  FR  FC  LFE  BL  BR  SL  SR
        size_t index = 0;
        constexpr float MINUS_3_DB_IN_FLOAT = M_SQRT1_2; // -3dB = 0.70710678
        constexpr float MINUS_4_5_DB_IN_FLOAT = 0.5946035575f;

        constexpr size_t FL = 0;
        constexpr size_t FR = 1;
        constexpr size_t FC = 2;
        constexpr size_t LFE = 3;
        constexpr size_t BL = 4;
        constexpr size_t BR = 5;
        constexpr size_t SL = 6;
        constexpr size_t SR = 7;
        for (unsigned tmp = INPUT_CHANNEL_MASK; tmp != 0; ++index) {
            if (index >= M) return false;
            const unsigned lowestBit = tmp & -(signed)tmp;
            matrix[index][FL] = matrix[index][FR] = matrix[index][FC] = 0.f;
            matrix[index][LFE] = matrix[index][BL] = matrix[index][BR] = 0.f;
            switch (lowestBit) {
                case AUDIO_CHANNEL_OUT_FRONT_LEFT:
                case AUDIO_CHANNEL_OUT_TOP_FRONT_LEFT:
                case AUDIO_CHANNEL_OUT_BOTTOM_FRONT_LEFT:
                    matrix[index][FL] = 1.f;
                    break;
                case AUDIO_CHANNEL_OUT_FRONT_RIGHT:
                case AUDIO_CHANNEL_OUT_TOP_FRONT_RIGHT:
                case AUDIO_CHANNEL_OUT_BOTTOM_FRONT_RIGHT:
                    matrix[index][FR] = 1.f;
                    break;

                case AUDIO_CHANNEL_OUT_FRONT_CENTER:
                case AUDIO_CHANNEL_OUT_TOP_FRONT_CENTER:
                case AUDIO_CHANNEL_OUT_BOTTOM_FRONT_CENTER:
                    matrix[index][FC] = 1.f;
                    break;

                case AUDIO_CHANNEL_OUT_FRONT_WIDE_LEFT: // FRONT_WIDE closer to SIDE.
                    matrix[index][FL] = MINUS_4_5_DB_IN_FLOAT;
                    matrix[index][SL] = MINUS_3_DB_IN_FLOAT;
                    break;
                case AUDIO_CHANNEL_OUT_FRONT_WIDE_RIGHT: // FRONT_WIDE closer to SIDE.
                    matrix[index][FR] = MINUS_4_5_DB_IN_FLOAT;
                    matrix[index][SR] = MINUS_3_DB_IN_FLOAT;
                    break;

                case AUDIO_CHANNEL_OUT_FRONT_LEFT_OF_CENTER:
                    matrix[index][FL] = MINUS_4_5_DB_IN_FLOAT;
                    matrix[index][FC] = MINUS_3_DB_IN_FLOAT;
                    break;
                case AUDIO_CHANNEL_OUT_FRONT_RIGHT_OF_CENTER:
                    matrix[index][FR] = MINUS_4_5_DB_IN_FLOAT;
                    matrix[index][FC] = MINUS_3_DB_IN_FLOAT;
                    break;

                case AUDIO_CHANNEL_OUT_BACK_LEFT:
                case AUDIO_CHANNEL_OUT_TOP_BACK_LEFT:
                    matrix[index][BL] = 1.f;
                    break;
                case AUDIO_CHANNEL_OUT_BACK_RIGHT:
                case AUDIO_CHANNEL_OUT_TOP_BACK_RIGHT:
                    matrix[index][BR] = 1.f;
                    break;

                case AUDIO_CHANNEL_OUT_SIDE_LEFT:
                case AUDIO_CHANNEL_OUT_TOP_SIDE_LEFT:
                    matrix[index][SL] = 1.f;
                    break;
                case AUDIO_CHANNEL_OUT_SIDE_RIGHT:
                case AUDIO_CHANNEL_OUT_TOP_SIDE_RIGHT:
                    matrix[index][SR] = 1.f;
                    break;

                case AUDIO_CHANNEL_OUT_TOP_BACK_CENTER:
                case AUDIO_CHANNEL_OUT_BACK_CENTER:
                    matrix[index][BL] = matrix[index][BR] = MINUS_3_DB_IN_FLOAT;
                    break;

                case AUDIO_CHANNEL_OUT_TOP_CENTER:
                    matrix[index][FC] = matrix[index][BL] = matrix[index][BR] = 0.5f;
                    break;

                case AUDIO_CHANNEL_OUT_LOW_FREQUENCY:
                case AUDIO_CHANNEL_OUT_LOW_FREQUENCY_2:
                    matrix[index][LFE] = 1.f;
                    break;
            }
            tmp ^= lowestBit;
        }
        return true;
    } else if constexpr (OUTPUT_CHANNEL_MASK == AUDIO_CHANNEL_OUT_7POINT1POINT4) {
        //   FL  FR  FC  LFE  BL  BR  SL  SR  TFL  TFR  TBL  TBR
        size_t index = 0;
        constexpr float MINUS_3_DB_IN_FLOAT = M_SQRT1_2; // -3dB = 0.70710678
        constexpr float MINUS_4_5_DB_IN_FLOAT = 0.5946035575f;

        constexpr size_t FL = 0;
        constexpr size_t FR = 1;
        constexpr size_t FC = 2;
        constexpr size_t LFE = 3;
        constexpr size_t BL = 4;
        constexpr size_t BR = 5;
        constexpr size_t SL = 6;
        constexpr size_t SR = 7;
        constexpr size_t TFL = 8;
        constexpr size_t TFR = 9;
        constexpr size_t TBL = 10;
        constexpr size_t TBR = 11;
        for (unsigned tmp = INPUT_CHANNEL_MASK; tmp != 0; ++index) {
            if (index >= M) return false;
            const unsigned lowestBit = tmp & -(signed)tmp;
            matrix[index][FL] = matrix[index][FR] = matrix[index][FC] = 0.f;
            matrix[index][LFE] = matrix[index][BL] = matrix[index][BR] = 0.f;
            switch (lowestBit) {
                case AUDIO_CHANNEL_OUT_TOP_FRONT_LEFT:
                    matrix[index][TFL] = 1.f;
                    break;
                case AUDIO_CHANNEL_OUT_TOP_FRONT_RIGHT:
                    matrix[index][TFR] = 1.f;
                    break;
                case AUDIO_CHANNEL_OUT_TOP_FRONT_CENTER:
                    matrix[index][TFL] = matrix[index][TFR] = MINUS_3_DB_IN_FLOAT;
                    break;

                case AUDIO_CHANNEL_OUT_FRONT_LEFT:
                case AUDIO_CHANNEL_OUT_BOTTOM_FRONT_LEFT:
                    matrix[index][FL] = 1.f;
                    break;
                case AUDIO_CHANNEL_OUT_FRONT_RIGHT:
                case AUDIO_CHANNEL_OUT_BOTTOM_FRONT_RIGHT:
                    matrix[index][FR] = 1.f;
                    break;

                case AUDIO_CHANNEL_OUT_FRONT_CENTER:
                case AUDIO_CHANNEL_OUT_BOTTOM_FRONT_CENTER:
                    matrix[index][FC] = 1.f;
                    break;

                case AUDIO_CHANNEL_OUT_FRONT_WIDE_LEFT: // FRONT_WIDE closer to SIDE.
                    matrix[index][FL] = MINUS_4_5_DB_IN_FLOAT;
                    matrix[index][SL] = MINUS_3_DB_IN_FLOAT;
                    break;
                case AUDIO_CHANNEL_OUT_FRONT_WIDE_RIGHT: // FRONT_WIDE closer to SIDE.
                    matrix[index][FR] = MINUS_4_5_DB_IN_FLOAT;
                    matrix[index][SR] = MINUS_3_DB_IN_FLOAT;
                    break;

                case AUDIO_CHANNEL_OUT_FRONT_LEFT_OF_CENTER:
                    matrix[index][FL] = MINUS_4_5_DB_IN_FLOAT;
                    matrix[index][FC] = MINUS_3_DB_IN_FLOAT;
                    break;
                case AUDIO_CHANNEL_OUT_FRONT_RIGHT_OF_CENTER:
                    matrix[index][FR] = MINUS_4_5_DB_IN_FLOAT;
                    matrix[index][FC] = MINUS_3_DB_IN_FLOAT;
                    break;

                case AUDIO_CHANNEL_OUT_BACK_LEFT:
                case AUDIO_CHANNEL_OUT_TOP_BACK_LEFT:
                    matrix[index][BL] = 1.f;
                    break;
                case AUDIO_CHANNEL_OUT_BACK_RIGHT:
                case AUDIO_CHANNEL_OUT_TOP_BACK_RIGHT:
                    matrix[index][BR] = 1.f;
                    break;

                case AUDIO_CHANNEL_OUT_SIDE_LEFT:
                case AUDIO_CHANNEL_OUT_TOP_SIDE_LEFT:
                    matrix[index][SL] = 1.f;
                    break;
                case AUDIO_CHANNEL_OUT_SIDE_RIGHT:
                case AUDIO_CHANNEL_OUT_TOP_SIDE_RIGHT:
                    matrix[index][SR] = 1.f;
                    break;

                case AUDIO_CHANNEL_OUT_TOP_BACK_CENTER:
                    matrix[index][TBL] = matrix[index][TBR] = MINUS_3_DB_IN_FLOAT;
                    break;

                case AUDIO_CHANNEL_OUT_BACK_CENTER:
                    matrix[index][BL] = matrix[index][BR] = MINUS_3_DB_IN_FLOAT;
                    break;

                case AUDIO_CHANNEL_OUT_TOP_CENTER:
                    matrix[index][TFL] = matrix[index][TFR] = 0.5f;
                    matrix[index][TBL] = matrix[index][TBR] = 0.5f;
                    break;

                case AUDIO_CHANNEL_OUT_LOW_FREQUENCY:
                case AUDIO_CHANNEL_OUT_LOW_FREQUENCY_2:
                    matrix[index][LFE] = 1.f;
                    break;
            }
            tmp ^= lowestBit;
        }
        return true;
    } else /* constexpr */ {
        // We only accept NONE here as we don't do anything in that case.
        static_assert(OUTPUT_CHANNEL_MASK==AUDIO_CHANNEL_NONE);
        return true;
    }
    return false;
}

class IChannelMix {
public:
    virtual ~IChannelMix() = default;

    /**
     * Set the input channel mask.
     *
     * \param inputChannelMask channel position mask for input data.
     *
     * \return false if the channel mask is not supported.
     */
    virtual bool setInputChannelMask(audio_channel_mask_t inputChannelMask) = 0;

    /**
     * Returns the input channel mask.
     */
    virtual audio_channel_mask_t getInputChannelMask() const = 0;

    /**
     * Remixes audio data in src to dst.
     *
     * \param src          input audio buffer to remix
     * \param dst          remixed audio samples
     * \param frameCount   number of frames to remix
     * \param accumulate   is true if the remix is added to the destination or
     *                     false if the remix replaces the destination.
     *
     * \return false if the channel mask set is not supported.
     */
    virtual bool process(
            const float *src, float *dst, size_t frameCount, bool accumulate) const = 0;

    /**
     * Remixes audio data in src to dst.
     *
     * \param src          input audio buffer to remix
     * \param dst          remixed audio samples
     * \param frameCount   number of frames to remix
     * \param accumulate   is true if the remix is added to the destination or
     *                     false if the remix replaces the destination.
     * \param inputChannelMask channel position mask for input data.
     *
     * \return false if the channel mask set is not supported.
     */
    virtual bool process(const float *src, float *dst, size_t frameCount, bool accumulate,
            audio_channel_mask_t inputChannelMask) = 0;

    /** Built in ChannelMix factory. */
    static std::shared_ptr<IChannelMix> create(audio_channel_mask_t outputChannelMask);

    /** Returns true if the Built-in factory supports the outputChannelMask */
    static bool isOutputChannelMaskSupported(audio_channel_mask_t outputChannelMask);
};

/**
 * ChannelMix
 *
 * Converts audio streams with different positional channel configurations.
 *
 */
template <audio_channel_mask_t OUTPUT_CHANNEL_MASK>
class ChannelMix : public IChannelMix {
public:
    /**
     * Creates a ChannelMix object
     *
     * Note: If construction is unsuccessful then getInputChannelMask will return
     * AUDIO_CHANNEL_NONE.
     *
     * \param inputChannelMask   channel position mask for input audio data.
     */
    explicit ChannelMix(audio_channel_mask_t inputChannelMask) {
        setInputChannelMask(inputChannelMask);
    }

    ChannelMix() = default;

    bool setInputChannelMask(audio_channel_mask_t inputChannelMask) override {
        if (mInputChannelMask != inputChannelMask) {
            if (inputChannelMask & ~((1 << MAX_INPUT_CHANNELS_SUPPORTED) - 1)) {
                return false;  // not channel position mask, or has unknown channels.
            }
            if (!fillChannelMatrix<OUTPUT_CHANNEL_MASK>(inputChannelMask, mMatrix)) {
                return false;  // missized matrix.
            }
            mInputChannelMask = inputChannelMask;
            mInputChannelCount = audio_channel_count_from_out_mask(inputChannelMask);
        }
        return true;
    }

    audio_channel_mask_t getInputChannelMask() const override {
        return mInputChannelMask;
    }

    bool process(const float *src, float *dst, size_t frameCount,
            bool accumulate) const override {
        return accumulate ? processSwitch<true>(src, dst, frameCount)
                : processSwitch<false>(src, dst, frameCount);
    }

    bool process(const float *src, float *dst, size_t frameCount,
            bool accumulate, audio_channel_mask_t inputChannelMask) override {
        return setInputChannelMask(inputChannelMask) && process(src, dst, frameCount, accumulate);
    }

    // The maximum channels supported (bits in the channel mask).
    static constexpr size_t MAX_INPUT_CHANNELS_SUPPORTED = FCC_26;

private:
    // Static/const parameters.
    static inline constexpr audio_channel_mask_t mOutputChannelMask = OUTPUT_CHANNEL_MASK;
    static inline constexpr size_t mOutputChannelCount =
            audio_channel_count_from_out_mask(OUTPUT_CHANNEL_MASK);
    static inline constexpr float MINUS_3_DB_IN_FLOAT = M_SQRT1_2; // -3dB = 0.70710678

    // These values are modified only when the input channel mask changes.
    // Keep alignment for matrix for more stable benchmarking.
    //
    // DO NOT change the order of these variables without running
    // atest channelmix_benchmark
    alignas(128) float mMatrix[MAX_INPUT_CHANNELS_SUPPORTED][mOutputChannelCount];
    audio_channel_mask_t mInputChannelMask = AUDIO_CHANNEL_NONE;
    size_t mInputChannelCount = 0;

    /**
     * Remixes audio data in src to dst.
     *
     * ACCUMULATE is true if the remix is added to the destination or
     *               false if the remix replaces the destination.
     *
     * \param src          multichannel audio buffer to remix
     * \param dst          remixed audio samples
     * \param frameCount   number of multichannel frames to remix
     *
     * \return false if the CHANNEL_COUNT is not supported.
     */
    template <bool ACCUMULATE>
    bool processSwitch(const float *src, float *dst, size_t frameCount) const {
        constexpr bool ANDROID_SPECIFIC = true;  // change for testing.
        if constexpr (ANDROID_SPECIFIC) {
            if constexpr (OUTPUT_CHANNEL_MASK == AUDIO_CHANNEL_OUT_STEREO
                    || OUTPUT_CHANNEL_MASK == AUDIO_CHANNEL_OUT_5POINT1) {
                switch (mInputChannelMask) {
                case AUDIO_CHANNEL_OUT_STEREO:
                    return sparseChannelMatrixMultiply<AUDIO_CHANNEL_OUT_STEREO,
                            OUTPUT_CHANNEL_MASK, ACCUMULATE>(src, dst, frameCount);
                case AUDIO_CHANNEL_OUT_QUAD_BACK:
                case AUDIO_CHANNEL_OUT_QUAD_SIDE:
                    return sparseChannelMatrixMultiply<AUDIO_CHANNEL_OUT_QUAD_BACK,
                            OUTPUT_CHANNEL_MASK, ACCUMULATE>(src, dst, frameCount);
                case AUDIO_CHANNEL_OUT_5POINT1_BACK:
                case AUDIO_CHANNEL_OUT_5POINT1_SIDE:
                    return sparseChannelMatrixMultiply<AUDIO_CHANNEL_OUT_5POINT1_BACK,
                            OUTPUT_CHANNEL_MASK, ACCUMULATE>(src, dst, frameCount);
                case AUDIO_CHANNEL_OUT_5POINT1POINT2:
                    return sparseChannelMatrixMultiply<AUDIO_CHANNEL_OUT_5POINT1POINT2,
                            OUTPUT_CHANNEL_MASK, ACCUMULATE>(src, dst, frameCount);
                case AUDIO_CHANNEL_OUT_5POINT1POINT4:
                     return sparseChannelMatrixMultiply<AUDIO_CHANNEL_OUT_5POINT1POINT4,
                            OUTPUT_CHANNEL_MASK, ACCUMULATE>(src, dst, frameCount);
                case AUDIO_CHANNEL_OUT_7POINT1:
                     return sparseChannelMatrixMultiply<AUDIO_CHANNEL_OUT_7POINT1,
                            OUTPUT_CHANNEL_MASK, ACCUMULATE>(src, dst, frameCount);
                case AUDIO_CHANNEL_OUT_7POINT1POINT2:
                    return sparseChannelMatrixMultiply<AUDIO_CHANNEL_OUT_7POINT1POINT2,
                            OUTPUT_CHANNEL_MASK, ACCUMULATE>(src, dst, frameCount);
                case AUDIO_CHANNEL_OUT_7POINT1POINT4:
                     return sparseChannelMatrixMultiply<AUDIO_CHANNEL_OUT_7POINT1POINT4,
                            OUTPUT_CHANNEL_MASK, ACCUMULATE>(src, dst, frameCount);
                case AUDIO_CHANNEL_OUT_22POINT2:
                     return sparseChannelMatrixMultiply<AUDIO_CHANNEL_OUT_22POINT2,
                            OUTPUT_CHANNEL_MASK, ACCUMULATE>(src, dst, frameCount);
                default:
                    break; // handled below.
                }
            } else if constexpr (OUTPUT_CHANNEL_MASK == AUDIO_CHANNEL_OUT_7POINT1
                    || OUTPUT_CHANNEL_MASK == AUDIO_CHANNEL_OUT_7POINT1POINT4) {
                switch (mInputChannelMask) {
                case AUDIO_CHANNEL_OUT_STEREO:
                    return sparseChannelMatrixMultiply<AUDIO_CHANNEL_OUT_STEREO,
                            OUTPUT_CHANNEL_MASK, ACCUMULATE>(src, dst, frameCount);
                case AUDIO_CHANNEL_OUT_QUAD_BACK:
                // Note: case AUDIO_CHANNEL_OUT_QUAD_SIDE is not equivalent.
                    return sparseChannelMatrixMultiply<AUDIO_CHANNEL_OUT_QUAD_BACK,
                            OUTPUT_CHANNEL_MASK, ACCUMULATE>(src, dst, frameCount);
                case AUDIO_CHANNEL_OUT_5POINT1_BACK:
                // Note: case AUDIO_CHANNEL_OUT_5POINT1_SIDE is not equivalent.
                    return sparseChannelMatrixMultiply<AUDIO_CHANNEL_OUT_5POINT1_BACK,
                            OUTPUT_CHANNEL_MASK, ACCUMULATE>(src, dst, frameCount);
                case AUDIO_CHANNEL_OUT_5POINT1POINT2:
                    return sparseChannelMatrixMultiply<AUDIO_CHANNEL_OUT_5POINT1POINT2,
                            OUTPUT_CHANNEL_MASK, ACCUMULATE>(src, dst, frameCount);
                case AUDIO_CHANNEL_OUT_5POINT1POINT4:
                     return sparseChannelMatrixMultiply<AUDIO_CHANNEL_OUT_5POINT1POINT4,
                            OUTPUT_CHANNEL_MASK, ACCUMULATE>(src, dst, frameCount);
                case AUDIO_CHANNEL_OUT_7POINT1:
                     return sparseChannelMatrixMultiply<AUDIO_CHANNEL_OUT_7POINT1,
                            OUTPUT_CHANNEL_MASK, ACCUMULATE>(src, dst, frameCount);
                case AUDIO_CHANNEL_OUT_7POINT1POINT2:
                    return sparseChannelMatrixMultiply<AUDIO_CHANNEL_OUT_7POINT1POINT2,
                            OUTPUT_CHANNEL_MASK, ACCUMULATE>(src, dst, frameCount);
                case AUDIO_CHANNEL_OUT_7POINT1POINT4:
                     return sparseChannelMatrixMultiply<AUDIO_CHANNEL_OUT_7POINT1POINT4,
                            OUTPUT_CHANNEL_MASK, ACCUMULATE>(src, dst, frameCount);
                case AUDIO_CHANNEL_OUT_22POINT2:
                     return sparseChannelMatrixMultiply<AUDIO_CHANNEL_OUT_22POINT2,
                            OUTPUT_CHANNEL_MASK, ACCUMULATE>(src, dst, frameCount);
                default:
                    break; // handled below.
                }
            }
        }
        return matrixProcess(src, dst, frameCount, ACCUMULATE);
    }

    /**
     * Converts a source audio stream to destination audio stream with a matrix
     * channel conversion.
     *
     * \param src          multichannel audio buffer to remix
     * \param dst          remixed audio samples
     * \param frameCount   number of multichannel frames to remix
     * \param accumulate   is true if the remix is added to the destination or
     *                     false if the remix replaces the destination.
     *
     * \return false if the CHANNEL_COUNT is not supported.
     */
    bool matrixProcess(const float *src, float *dst, size_t frameCount, bool accumulate) const {
        // matrix multiply
        if (mInputChannelMask == AUDIO_CHANNEL_NONE) return false;
        while (frameCount) {
            float ch[mOutputChannelCount]{};
            for (size_t i = 0; i < mInputChannelCount; ++i) {
                const float (&array)[mOutputChannelCount] = mMatrix[i];
                for (size_t j = 0; j < mOutputChannelCount; ++j) {
                    ch[j] += array[j] * src[i];
                }
            }
            if (accumulate) {
                for (size_t j = 0; j < mOutputChannelCount; ++j) {
                    ch[j] += dst[j];
                }
            }
            for (size_t j = 0; j < mOutputChannelCount; ++j) {
                dst[j] = clamp(ch[j]);
            }
            src += mInputChannelCount;
            dst += mOutputChannelCount;
            --frameCount;
        }
        return true;
    }
};

} // android::audio_utils::channels
