/*
 * Copyright (C) 2014 The Android Open Source Project
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

#ifndef ANDROID_AUDIO_CHANNELS_H
#define ANDROID_AUDIO_CHANNELS_H

#include <system/audio.h>

#ifdef __cplusplus

// New development in channels namespace.
namespace android::audio_utils::channels {

/**
 * Returns a particular side (left, right, center) associated
 * with a channel position mask bit index.
 * This is a fixed geometrical constant for a given channel mask.
 *
 * For the channel mask spec, see system/media/audio/include/system/audio*.h.
 *
 * Special Note: if there are two LFE speakers and bass management is used,
 * then AUDIO_CHANNEL_OUT_LOW_FREQUENCY speaker is on the left side and receives
 * all bass from left side speakers that they cannot reproduce,
 * likewise AUDIO_CHANNEL_OUT_LOW_FREQUENCY_2 is used for the right side
 * (https://www.itu.int/dms_pub/itu-r/opb/rep/R-REP-BS.2159-4-2012-PDF-E.pdf#page=15).
 *
 * For simplicity, both AUDIO_CHANNEL_OUT_LOW_FREQUENCY and
 * AUDIO_CHANNEL_OUT_LOW_FREQUENCY_2 are assigned to the center channel,
 * which is a safe approximation given the lack of directionality of LFE.
 * Specific handling for the presence of two LFE speakers must be handled
 * elsewhere.
 *
 * \param idx index of bit in the channel position mask.
 * \return    side constant.
 */
enum AUDIO_GEOMETRY_SIDE {
    AUDIO_GEOMETRY_SIDE_LEFT,
    AUDIO_GEOMETRY_SIDE_CENTER,
    AUDIO_GEOMETRY_SIDE_RIGHT,
};
// static constexpr arrays cannot be declared in block scope.
// inline allows multiple definition, single object address.
constexpr inline AUDIO_GEOMETRY_SIDE kSideFromChannelIdx[] = {
    AUDIO_GEOMETRY_SIDE_LEFT,   // AUDIO_CHANNEL_OUT_FRONT_LEFT            = 0x1u,
    AUDIO_GEOMETRY_SIDE_RIGHT,  // AUDIO_CHANNEL_OUT_FRONT_RIGHT           = 0x2u,
    AUDIO_GEOMETRY_SIDE_CENTER, // AUDIO_CHANNEL_OUT_FRONT_CENTER          = 0x4u,
    AUDIO_GEOMETRY_SIDE_CENTER, // AUDIO_CHANNEL_OUT_LOW_FREQUENCY         = 0x8u,
    AUDIO_GEOMETRY_SIDE_LEFT,   // AUDIO_CHANNEL_OUT_BACK_LEFT             = 0x10u,
    AUDIO_GEOMETRY_SIDE_RIGHT,  // AUDIO_CHANNEL_OUT_BACK_RIGHT            = 0x20u,
    AUDIO_GEOMETRY_SIDE_LEFT,   // AUDIO_CHANNEL_OUT_FRONT_LEFT_OF_CENTER  = 0x40u,
    AUDIO_GEOMETRY_SIDE_RIGHT,  // AUDIO_CHANNEL_OUT_FRONT_RIGHT_OF_CENTER = 0x80u,
    AUDIO_GEOMETRY_SIDE_CENTER, // AUDIO_CHANNEL_OUT_BACK_CENTER           = 0x100u,
    AUDIO_GEOMETRY_SIDE_LEFT,   // AUDIO_CHANNEL_OUT_SIDE_LEFT             = 0x200u,
    AUDIO_GEOMETRY_SIDE_RIGHT,  // AUDIO_CHANNEL_OUT_SIDE_RIGHT            = 0x400u,
    AUDIO_GEOMETRY_SIDE_CENTER, // AUDIO_CHANNEL_OUT_TOP_CENTER            = 0x800u,
    AUDIO_GEOMETRY_SIDE_LEFT,   // AUDIO_CHANNEL_OUT_TOP_FRONT_LEFT        = 0x1000u,
    AUDIO_GEOMETRY_SIDE_CENTER, // AUDIO_CHANNEL_OUT_TOP_FRONT_CENTER      = 0x2000u,
    AUDIO_GEOMETRY_SIDE_RIGHT,  // AUDIO_CHANNEL_OUT_TOP_FRONT_RIGHT       = 0x4000u,
    AUDIO_GEOMETRY_SIDE_LEFT,   // AUDIO_CHANNEL_OUT_TOP_BACK_LEFT         = 0x8000u,
    AUDIO_GEOMETRY_SIDE_CENTER, // AUDIO_CHANNEL_OUT_TOP_BACK_CENTER       = 0x10000u,
    AUDIO_GEOMETRY_SIDE_RIGHT,  // AUDIO_CHANNEL_OUT_TOP_BACK_RIGHT        = 0x20000u,
    AUDIO_GEOMETRY_SIDE_LEFT,   // AUDIO_CHANNEL_OUT_TOP_SIDE_LEFT         = 0x40000u,
    AUDIO_GEOMETRY_SIDE_RIGHT,  // AUDIO_CHANNEL_OUT_TOP_SIDE_RIGHT        = 0x80000u,
    AUDIO_GEOMETRY_SIDE_LEFT,   // AUDIO_CHANNEL_OUT_BOTTOM_FRONT_LEFT     = 0x100000u,
    AUDIO_GEOMETRY_SIDE_CENTER, // AUDIO_CHANNEL_OUT_BOTTOM_FRONT_CENTER   = 0x200000u,
    AUDIO_GEOMETRY_SIDE_RIGHT,  // AUDIO_CHANNEL_OUT_BOTTOM_FRONT_RIGHT    = 0x400000u,
    AUDIO_GEOMETRY_SIDE_CENTER, // AUDIO_CHANNEL_OUT_LOW_FREQUENCY_2       = 0x800000u,
    AUDIO_GEOMETRY_SIDE_LEFT,   // AUDIO_CHANNEL_OUT_FRONT_WIDE_LEFT       = 0x1000000u
    AUDIO_GEOMETRY_SIDE_RIGHT,  // AUDIO_CHANNEL_OUT_FRONT_WIDE_RIGHT      = 0x2000000u
};
constexpr inline AUDIO_GEOMETRY_SIDE sideFromChannelIdx(size_t idx) {
    static_assert(std::size(kSideFromChannelIdx) == FCC_26);
    if (idx < std::size(kSideFromChannelIdx)) return kSideFromChannelIdx[idx];
    return AUDIO_GEOMETRY_SIDE_CENTER;
}

/**
 * Returns a particular height (bottom, middle, top) associated
 * with a channel position mask bit index.
 * This is a fixed geometrical constant for a given channel mask.
 *
 * For the channel mask spec, see system/media/audio/include/system/audio*.h.
 *
 * \param idx index of bit in the channel position mask.
 * \return    height constant.
 */
enum AUDIO_GEOMETRY_HEIGHT {
    AUDIO_GEOMETRY_HEIGHT_BOTTOM,
    AUDIO_GEOMETRY_HEIGHT_MIDDLE,
    AUDIO_GEOMETRY_HEIGHT_TOP,
};
// static constexpr arrays cannot be declared in block scope.
// inline allows multiple definition, single object address.
constexpr inline AUDIO_GEOMETRY_HEIGHT kHeightFromChannelIdx [] = {
    AUDIO_GEOMETRY_HEIGHT_MIDDLE, // AUDIO_CHANNEL_OUT_FRONT_LEFT            = 0x1u,
    AUDIO_GEOMETRY_HEIGHT_MIDDLE, // AUDIO_CHANNEL_OUT_FRONT_RIGHT           = 0x2u,
    AUDIO_GEOMETRY_HEIGHT_MIDDLE, // AUDIO_CHANNEL_OUT_FRONT_CENTER          = 0x4u,
    AUDIO_GEOMETRY_HEIGHT_BOTTOM, // AUDIO_CHANNEL_OUT_LOW_FREQUENCY         = 0x8u,
    AUDIO_GEOMETRY_HEIGHT_MIDDLE, // AUDIO_CHANNEL_OUT_BACK_LEFT             = 0x10u,
    AUDIO_GEOMETRY_HEIGHT_MIDDLE, // AUDIO_CHANNEL_OUT_BACK_RIGHT            = 0x20u,
    AUDIO_GEOMETRY_HEIGHT_MIDDLE, // AUDIO_CHANNEL_OUT_FRONT_LEFT_OF_CENTER  = 0x40u,
    AUDIO_GEOMETRY_HEIGHT_MIDDLE, // AUDIO_CHANNEL_OUT_FRONT_RIGHT_OF_CENTER = 0x80u,
    AUDIO_GEOMETRY_HEIGHT_MIDDLE, // AUDIO_CHANNEL_OUT_BACK_CENTER           = 0x100u,
    AUDIO_GEOMETRY_HEIGHT_MIDDLE, // AUDIO_CHANNEL_OUT_SIDE_LEFT             = 0x200u,
    AUDIO_GEOMETRY_HEIGHT_MIDDLE, // AUDIO_CHANNEL_OUT_SIDE_RIGHT            = 0x400u,
    AUDIO_GEOMETRY_HEIGHT_TOP,    // AUDIO_CHANNEL_OUT_TOP_CENTER            = 0x800u,
    AUDIO_GEOMETRY_HEIGHT_TOP,    // AUDIO_CHANNEL_OUT_TOP_FRONT_LEFT        = 0x1000u,
    AUDIO_GEOMETRY_HEIGHT_TOP,    // AUDIO_CHANNEL_OUT_TOP_FRONT_CENTER      = 0x2000u,
    AUDIO_GEOMETRY_HEIGHT_TOP,    // AUDIO_CHANNEL_OUT_TOP_FRONT_RIGHT       = 0x4000u,
    AUDIO_GEOMETRY_HEIGHT_TOP,    // AUDIO_CHANNEL_OUT_TOP_BACK_LEFT         = 0x8000u,
    AUDIO_GEOMETRY_HEIGHT_TOP,    // AUDIO_CHANNEL_OUT_TOP_BACK_CENTER       = 0x10000u,
    AUDIO_GEOMETRY_HEIGHT_TOP,    // AUDIO_CHANNEL_OUT_TOP_BACK_RIGHT        = 0x20000u,
    AUDIO_GEOMETRY_HEIGHT_TOP,    // AUDIO_CHANNEL_OUT_TOP_SIDE_LEFT         = 0x40000u,
    AUDIO_GEOMETRY_HEIGHT_TOP,    // AUDIO_CHANNEL_OUT_TOP_SIDE_RIGHT        = 0x80000u,
    AUDIO_GEOMETRY_HEIGHT_BOTTOM, // AUDIO_CHANNEL_OUT_BOTTOM_FRONT_LEFT     = 0x100000u,
    AUDIO_GEOMETRY_HEIGHT_BOTTOM, // AUDIO_CHANNEL_OUT_BOTTOM_FRONT_CENTER   = 0x200000u,
    AUDIO_GEOMETRY_HEIGHT_BOTTOM, // AUDIO_CHANNEL_OUT_BOTTOM_FRONT_RIGHT    = 0x400000u,
    AUDIO_GEOMETRY_HEIGHT_BOTTOM, // AUDIO_CHANNEL_OUT_LOW_FREQUENCY_2       = 0x800000u,
    AUDIO_GEOMETRY_HEIGHT_MIDDLE, // AUDIO_CHANNEL_OUT_FRONT_WIDE_LEFT       = 0x1000000u
    AUDIO_GEOMETRY_HEIGHT_MIDDLE, // AUDIO_CHANNEL_OUT_FRONT_WIDE_RIGHT      = 0x2000000u
};
constexpr inline AUDIO_GEOMETRY_HEIGHT heightFromChannelIdx(size_t idx) {
    static_assert(std::size(kHeightFromChannelIdx) == FCC_26);
    if (idx < std::size(kHeightFromChannelIdx)) return kHeightFromChannelIdx[idx];
    return AUDIO_GEOMETRY_HEIGHT_MIDDLE;
}

/**
 * Returns a particular depth (front, middle (aka side), back) associated
 * with a channel position mask bit index.
 * This is a fixed geometrical constant for a given channel mask.
 *
 * For the channel mask spec, see system/media/audio/include/system/audio*.h.
 *
 * \param idx index of bit in the channel position mask.
 * \return    depth constant.
 */
enum AUDIO_GEOMETRY_DEPTH {
    AUDIO_GEOMETRY_DEPTH_FRONT,
    AUDIO_GEOMETRY_DEPTH_MIDDLE,
    AUDIO_GEOMETRY_DEPTH_BACK,
};
// static constexpr arrays cannot be declared in block scope.
// inline allows multiple definition, single object address.
constexpr inline AUDIO_GEOMETRY_DEPTH kDepthFromChannelIdx[] = {
    AUDIO_GEOMETRY_DEPTH_FRONT,  // AUDIO_CHANNEL_OUT_FRONT_LEFT            = 0x1u,
    AUDIO_GEOMETRY_DEPTH_FRONT,  // AUDIO_CHANNEL_OUT_FRONT_RIGHT           = 0x2u,
    AUDIO_GEOMETRY_DEPTH_FRONT,  // AUDIO_CHANNEL_OUT_FRONT_CENTER          = 0x4u,
    AUDIO_GEOMETRY_DEPTH_FRONT,  // AUDIO_CHANNEL_OUT_LOW_FREQUENCY         = 0x8u,
    AUDIO_GEOMETRY_DEPTH_BACK,   // AUDIO_CHANNEL_OUT_BACK_LEFT             = 0x10u,
    AUDIO_GEOMETRY_DEPTH_BACK,   // AUDIO_CHANNEL_OUT_BACK_RIGHT            = 0x20u,
    AUDIO_GEOMETRY_DEPTH_FRONT,  // AUDIO_CHANNEL_OUT_FRONT_LEFT_OF_CENTER  = 0x40u,
    AUDIO_GEOMETRY_DEPTH_FRONT,  // AUDIO_CHANNEL_OUT_FRONT_RIGHT_OF_CENTER = 0x80u,
    AUDIO_GEOMETRY_DEPTH_BACK,   // AUDIO_CHANNEL_OUT_BACK_CENTER           = 0x100u,
    AUDIO_GEOMETRY_DEPTH_MIDDLE, // AUDIO_CHANNEL_OUT_SIDE_LEFT             = 0x200u,
    AUDIO_GEOMETRY_DEPTH_MIDDLE, // AUDIO_CHANNEL_OUT_SIDE_RIGHT            = 0x400u,
    AUDIO_GEOMETRY_DEPTH_MIDDLE, // AUDIO_CHANNEL_OUT_TOP_CENTER            = 0x800u,
    AUDIO_GEOMETRY_DEPTH_FRONT,  // AUDIO_CHANNEL_OUT_TOP_FRONT_LEFT        = 0x1000u,
    AUDIO_GEOMETRY_DEPTH_FRONT,  // AUDIO_CHANNEL_OUT_TOP_FRONT_CENTER      = 0x2000u,
    AUDIO_GEOMETRY_DEPTH_FRONT,  // AUDIO_CHANNEL_OUT_TOP_FRONT_RIGHT       = 0x4000u,
    AUDIO_GEOMETRY_DEPTH_BACK,   // AUDIO_CHANNEL_OUT_TOP_BACK_LEFT         = 0x8000u,
    AUDIO_GEOMETRY_DEPTH_BACK,   // AUDIO_CHANNEL_OUT_TOP_BACK_CENTER       = 0x10000u,
    AUDIO_GEOMETRY_DEPTH_BACK,   // AUDIO_CHANNEL_OUT_TOP_BACK_RIGHT        = 0x20000u,
    AUDIO_GEOMETRY_DEPTH_MIDDLE, // AUDIO_CHANNEL_OUT_TOP_SIDE_LEFT         = 0x40000u,
    AUDIO_GEOMETRY_DEPTH_MIDDLE, // AUDIO_CHANNEL_OUT_TOP_SIDE_RIGHT        = 0x80000u,
    AUDIO_GEOMETRY_DEPTH_FRONT,  // AUDIO_CHANNEL_OUT_BOTTOM_FRONT_LEFT     = 0x100000u,
    AUDIO_GEOMETRY_DEPTH_FRONT,  // AUDIO_CHANNEL_OUT_BOTTOM_FRONT_CENTER   = 0x200000u,
    AUDIO_GEOMETRY_DEPTH_FRONT,  // AUDIO_CHANNEL_OUT_BOTTOM_FRONT_RIGHT    = 0x400000u,
    AUDIO_GEOMETRY_DEPTH_FRONT,  // AUDIO_CHANNEL_OUT_LOW_FREQUENCY_2       = 0x800000u,
    AUDIO_GEOMETRY_DEPTH_MIDDLE, // AUDIO_CHANNEL_OUT_FRONT_WIDE_LEFT       = 0x1000000u
    AUDIO_GEOMETRY_DEPTH_MIDDLE, // AUDIO_CHANNEL_OUT_FRONT_WIDE_RIGHT      = 0x2000000u
};
constexpr inline AUDIO_GEOMETRY_DEPTH depthFromChannelIdx(size_t idx) {
    static_assert(std::size(kDepthFromChannelIdx) == FCC_26);
    if (idx < std::size(kDepthFromChannelIdx)) return kDepthFromChannelIdx[idx];
    return AUDIO_GEOMETRY_DEPTH_FRONT;
}

/**
 * Returns the pair channel position mask bit index as determined by
 * AUDIO_GEOMETRY_SIDE_LEFT and AUDIO_GEOMETRY_SIDE_RIGHT characteristics.
 *
 * For example a bit index of 0 (AUDIO_CHANNEL_OUT_FRONT_LEFT) returns
 * a pair bit index of 1 (AUDIO_CHANNEL_OUT_FRONT_RIGHT).
 *
 * If there is no left/right characteristic, then -1 is returned.
 * For example, a bit index of 2 (AUDIO_CHANNEL_OUT_FRONT_CENTER) returns
 * a pair bit index of -1 (doesn't exist).
 *
 * For the channel mask spec, see system/media/audio/include/system/audio*.h.
 *
 * \param idx index of bit in the channel position mask.
 * \return    index of bit of the pair if non-negative, or -1 if it doesn't exist.
 */

#pragma push_macro("CHANNEL_ASSOCIATE")
#undef CHANNEL_ASSOCIATE
#define CHANNEL_ASSOCIATE(x, y) \
 [__builtin_ctz(x)] = __builtin_ctz(y), [__builtin_ctz(y)] = __builtin_ctz(x),

#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Winitializer-overrides"  // we use override array assignment

constexpr inline int kPairIdxFromChannelIdx[FCC_26] = {
    [ 0 ... 25 ] = -1,  // everything defaults to -1 unless overridden below.
    CHANNEL_ASSOCIATE(AUDIO_CHANNEL_OUT_FRONT_LEFT, AUDIO_CHANNEL_OUT_FRONT_RIGHT)
    // AUDIO_CHANNEL_OUT_FRONT_CENTER          = 0x4u,
    // AUDIO_CHANNEL_OUT_LOW_FREQUENCY         = 0x8u,
    CHANNEL_ASSOCIATE(AUDIO_CHANNEL_OUT_BACK_LEFT, AUDIO_CHANNEL_OUT_BACK_RIGHT)
    CHANNEL_ASSOCIATE(
            AUDIO_CHANNEL_OUT_FRONT_LEFT_OF_CENTER, AUDIO_CHANNEL_OUT_FRONT_RIGHT_OF_CENTER)
    // AUDIO_CHANNEL_OUT_BACK_CENTER           = 0x100u,
    CHANNEL_ASSOCIATE(AUDIO_CHANNEL_OUT_SIDE_LEFT, AUDIO_CHANNEL_OUT_SIDE_RIGHT)
    // AUDIO_CHANNEL_OUT_TOP_CENTER            = 0x800u,
    CHANNEL_ASSOCIATE(AUDIO_CHANNEL_OUT_TOP_FRONT_LEFT, AUDIO_CHANNEL_OUT_TOP_FRONT_RIGHT)
    // AUDIO_CHANNEL_OUT_TOP_FRONT_CENTER      = 0x2000u,
    CHANNEL_ASSOCIATE(AUDIO_CHANNEL_OUT_TOP_BACK_LEFT, AUDIO_CHANNEL_OUT_TOP_BACK_RIGHT)
    // AUDIO_CHANNEL_OUT_TOP_BACK_CENTER       = 0x10000u,
    CHANNEL_ASSOCIATE(AUDIO_CHANNEL_OUT_TOP_SIDE_LEFT, AUDIO_CHANNEL_OUT_TOP_SIDE_RIGHT)
    CHANNEL_ASSOCIATE(AUDIO_CHANNEL_OUT_BOTTOM_FRONT_LEFT, AUDIO_CHANNEL_OUT_BOTTOM_FRONT_RIGHT)
    // AUDIO_CHANNEL_OUT_BOTTOM_FRONT_CENTER   = 0x200000u,
    // AUDIO_CHANNEL_OUT_LOW_FREQUENCY_2       = 0x800000u,
    CHANNEL_ASSOCIATE(AUDIO_CHANNEL_OUT_FRONT_WIDE_LEFT, AUDIO_CHANNEL_OUT_FRONT_WIDE_RIGHT)
};
#pragma GCC diagnostic pop
#pragma pop_macro("CHANNEL_ASSOCIATE")

constexpr inline ssize_t pairIdxFromChannelIdx(size_t idx) {
    static_assert(std::size(kPairIdxFromChannelIdx) == FCC_26);
    if (idx < std::size(kPairIdxFromChannelIdx)) return kPairIdxFromChannelIdx[idx];
    return -1;
}

} // android::audio_utils::channels

#endif // __cplusplus

/** \cond */
__BEGIN_DECLS
/** \endcond */

/**
 * Expands or contracts sample data from one interleaved channel format to another.
 * Expanded channels are filled with zeros and put at the end of each audio frame.
 * Contracted channels are omitted from the end of each audio frame.
 *
 *   \param in_buff              points to the buffer of samples
 *   \param in_buff_chans        Specifies the number of channels in the input buffer.
 *   \param out_buff             points to the buffer to receive converted samples.
 *   \param out_buff_chans       Specifies the number of channels in the output buffer.
 *   \param sample_size_in_bytes Specifies the number of bytes per sample. 1, 2, 3, 4 are
 *     currently valid.
 *   \param num_in_bytes         size of input buffer in bytes
 *
 * \return
 *   the number of bytes of output data or 0 if an error occurs.
 *
 * \note
 *   The out and sums buffers must either be completely separate (non-overlapping), or
 *   they must both start at the same address. Partially overlapping buffers are not supported.
 */
size_t adjust_channels(const void* in_buff, size_t in_buff_chans,
                       void* out_buff, size_t out_buff_chans,
                       unsigned sample_size_in_bytes, size_t num_in_bytes);

/**
 * Expands or contracts sample data from one interleaved channel format to another.
 * Extra expanded channels are left alone in the output buffer.
 * Contracted channels are omitted from the end of each audio frame.
 *
 *   \param in_buff              points to the buffer of samples
 *   \param in_buff_chans        Specifies the number of channels in the input buffer.
 *   \param out_buff             points to the buffer to receive converted samples.
 *   \param out_buff_chans       Specifies the number of channels in the output buffer.
 *   \param sample_size_in_bytes Specifies the number of bytes per sample. 1, 2, 3, 4 are
 *     currently valid.
 *   \param num_in_bytes         size of input buffer in bytes
 *
 * \return
 *   the number of bytes of output data or 0 if an error occurs.
 *
 * \note
 *   The out and in buffers must either be completely separate (non-overlapping), or
 *   they must both start at the same address. Partially overlapping buffers are not supported.
 */
size_t adjust_selected_channels(const void* in_buff, size_t in_buff_chans,
                       void* out_buff, size_t out_buff_chans,
                       unsigned sample_size_in_bytes, size_t num_in_bytes);

/**
 * Expands or contracts sample data from one interleaved channel format to another.
 * Extra expanded channels are interleaved in from the end of the input buffer.
 * Contracted channels are copied to the end of the output buffer.
 *
 *   \param in_buff              points to the buffer of samples.
 *   \param in_buff_chans        Specifies the number of channels in the input buffer.
 *   \param out_buff             points to the buffer to receive converted samples.
 *   \param out_buff_chans       Specifies the number of channels in the output buffer.
 *   \param sample_size_in_bytes Specifies the number of bytes per sample. 1, 2, 3, 4 are
 *     currently valid.
 *   \param num_in_bytes         size of input buffer in bytes.
 *
 * \return
 *   the number of bytes of output data or 0 if an error occurs.
 *
 * \note
 *   The out and in buffers must be the same length.
 *   The out and in buffers must either be completely separate (non-overlapping), or
 *   they must both start at the same address. Partially overlapping buffers are not supported.
 */
size_t adjust_channels_non_destructive(const void* in_buff, size_t in_buff_chans,
                       void* out_buff, size_t out_buff_chans,
                       unsigned sample_size_in_bytes, size_t num_in_bytes);

/** \cond */
__END_DECLS
/** \endcond */

#endif  // !ANDROID_AUDIO_CHANNELS_H
