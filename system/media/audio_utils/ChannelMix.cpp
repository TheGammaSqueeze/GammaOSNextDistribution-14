/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include <audio_utils/ChannelMix.h>

namespace android::audio_utils::channels {

/**
 * The ChannelMix code relies on sparse matrix optimization for speed.
 *
 * This requires -ffast-math to be specified.
 */


/*
 Implementation detail:

 We "compute" the channel mix matrix by constexpr computation,
 but alternatively we could use a straight 2D array initialization.

 The thought is that the channel mix matrix by computation is easier
 to keep consistent with modifications.
 */

namespace {
// A container for the channel matrix
template <audio_channel_mask_t INPUT_CHANNEL_MASK, audio_channel_mask_t OUTPUT_CHANNEL_MASK>
struct ChannelMatrixContainer {
    static inline constexpr size_t INPUT_CHANNEL_COUNT =
            audio_channel_count_from_out_mask(INPUT_CHANNEL_MASK);
    static inline constexpr size_t OUTPUT_CHANNEL_COUNT =
            audio_channel_count_from_out_mask(OUTPUT_CHANNEL_MASK);
    float f[INPUT_CHANNEL_COUNT][OUTPUT_CHANNEL_COUNT];
};

template <audio_channel_mask_t INPUT_CHANNEL_MASK, audio_channel_mask_t OUTPUT_CHANNEL_MASK>
constexpr ChannelMatrixContainer<INPUT_CHANNEL_MASK, OUTPUT_CHANNEL_MASK> computeMatrix() {
    ChannelMatrixContainer<INPUT_CHANNEL_MASK, OUTPUT_CHANNEL_MASK> channelMatrix{};
    // Compiler bug: cannot check result of this through static_assert.
    (void)fillChannelMatrix<OUTPUT_CHANNEL_MASK>(INPUT_CHANNEL_MASK, channelMatrix.f);
    return channelMatrix;
}

} // namespace

/**
 * Remixes a multichannel signal of specified number of channels
 *
 * INPUT_CHANNEL_MASK the src input.
 * OUTPUT_CHANNEL_MASK the dst output.
 * ACCUMULATE is true if the remix is added to the destination or
 *               false if the remix replaces the destination.
 *
 * \param src          multichannel audio buffer to remix
 * \param dst          remixed stereo audio samples
 * \param frameCount   number of multichannel frames to remix
 *
 * \return false if the CHANNEL_COUNT is not supported.
 */
template <audio_channel_mask_t INPUT_CHANNEL_MASK,
        audio_channel_mask_t OUTPUT_CHANNEL_MASK, bool ACCUMULATE>
bool sparseChannelMatrixMultiply(const float *src, float *dst, size_t frameCount) {
    static constexpr auto s = computeMatrix<INPUT_CHANNEL_MASK, OUTPUT_CHANNEL_MASK>();

    // matrix multiply
    if (INPUT_CHANNEL_MASK == AUDIO_CHANNEL_NONE) return false;
    for (;frameCount > 0; --frameCount) {
        float ch[s.OUTPUT_CHANNEL_COUNT]{};
        #pragma unroll
        for (size_t i = 0; i < s.INPUT_CHANNEL_COUNT; ++i) {
            const float (&array)[s.OUTPUT_CHANNEL_COUNT] = s.f[i];
            #pragma unroll
            for (size_t j = 0; j < s.OUTPUT_CHANNEL_COUNT; ++j) {
                ch[j] += array[j] * src[i];
            }
        }
        if constexpr (ACCUMULATE) {
            #pragma unroll
            for (size_t j = 0; j < s.OUTPUT_CHANNEL_COUNT; ++j) {
                ch[j] += dst[j];
            }
        }
        #pragma unroll
        for (size_t j = 0; j < s.OUTPUT_CHANNEL_COUNT; ++j) {
            dst[j] = clamp(ch[j]);
        }
        src += s.INPUT_CHANNEL_COUNT;
        dst += s.OUTPUT_CHANNEL_COUNT;
    }
    return true;
}

// Create accelerated instances

#define INSTANTIATE(INPUT_MASK, OUTPUT_MASK) \
template bool \
sparseChannelMatrixMultiply<INPUT_MASK, OUTPUT_MASK, true>( \
        const float *src, float *dst, size_t frameCount); \
template bool \
sparseChannelMatrixMultiply<INPUT_MASK, OUTPUT_MASK, false>( \
        const float *src, float *dst, size_t frameCount); \

#define INSTANTIATE_MASKS(CHANNEL) \
INSTANTIATE(AUDIO_CHANNEL_OUT_STEREO, (CHANNEL)) \
INSTANTIATE(AUDIO_CHANNEL_OUT_QUAD_BACK, (CHANNEL)) \
INSTANTIATE(AUDIO_CHANNEL_OUT_5POINT1_BACK, (CHANNEL)) \
INSTANTIATE(AUDIO_CHANNEL_OUT_7POINT1, (CHANNEL)) \
INSTANTIATE(AUDIO_CHANNEL_OUT_5POINT1POINT2, (CHANNEL)) \
INSTANTIATE(AUDIO_CHANNEL_OUT_5POINT1POINT4, (CHANNEL)) \
INSTANTIATE(AUDIO_CHANNEL_OUT_7POINT1POINT2, (CHANNEL)) \
INSTANTIATE(AUDIO_CHANNEL_OUT_7POINT1POINT4, (CHANNEL)) \
INSTANTIATE(AUDIO_CHANNEL_OUT_9POINT1POINT6, (CHANNEL)) \
INSTANTIATE(AUDIO_CHANNEL_OUT_22POINT2, (CHANNEL))

INSTANTIATE_MASKS(AUDIO_CHANNEL_OUT_STEREO)
INSTANTIATE_MASKS(AUDIO_CHANNEL_OUT_5POINT1)
INSTANTIATE_MASKS(AUDIO_CHANNEL_OUT_7POINT1)
INSTANTIATE_MASKS(AUDIO_CHANNEL_OUT_7POINT1POINT4)
INSTANTIATE_MASKS(AUDIO_CHANNEL_OUT_9POINT1POINT6)

/* static */
std::shared_ptr<IChannelMix> IChannelMix::create(audio_channel_mask_t outputChannelMask) {
     switch (outputChannelMask) {
     case AUDIO_CHANNEL_OUT_STEREO:
         return std::make_shared<ChannelMix<AUDIO_CHANNEL_OUT_STEREO>>();
     case AUDIO_CHANNEL_OUT_5POINT1:
         return std::make_shared<ChannelMix<AUDIO_CHANNEL_OUT_5POINT1>>();
     case AUDIO_CHANNEL_OUT_7POINT1:
         return std::make_shared<ChannelMix<AUDIO_CHANNEL_OUT_7POINT1>>();
     case AUDIO_CHANNEL_OUT_7POINT1POINT4:
         return std::make_shared<ChannelMix<AUDIO_CHANNEL_OUT_7POINT1POINT4>>();
     case AUDIO_CHANNEL_OUT_9POINT1POINT6:
         return std::make_shared<ChannelMix<AUDIO_CHANNEL_OUT_9POINT1POINT6>>();
     default:
         return {};
     }
}

/* static */
bool IChannelMix::isOutputChannelMaskSupported(audio_channel_mask_t outputChannelMask) {
    switch (outputChannelMask) {
    case AUDIO_CHANNEL_OUT_STEREO:
    case AUDIO_CHANNEL_OUT_5POINT1:
    case AUDIO_CHANNEL_OUT_7POINT1:
    case AUDIO_CHANNEL_OUT_7POINT1POINT4:
    case AUDIO_CHANNEL_OUT_9POINT1POINT6:
        return true;
    default:
        return false;
    }
}

} // android::audio_utils::channels
