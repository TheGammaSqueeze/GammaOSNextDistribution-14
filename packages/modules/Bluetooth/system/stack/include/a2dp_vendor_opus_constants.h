/*
 * Copyright 2021 The Android Open Source Project
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

//
// A2DP constants for Opus codec
//

#ifndef A2DP_VENDOR_OPUS_CONSTANTS_H
#define A2DP_VENDOR_OPUS_CONSTANTS_H

#define A2DP_OPUS_CODEC_LEN 9

#define A2DP_OPUS_CODEC_OUTPUT_CHS 2
#define A2DP_OPUS_CODEC_DEFAULT_SAMPLERATE 48000
#define A2DP_OPUS_CODEC_DEFAULT_FRAMESIZE 960
#define A2DP_OPUS_DECODE_BUFFER_LENGTH \
  (A2DP_OPUS_CODEC_OUTPUT_CHS * A2DP_OPUS_CODEC_DEFAULT_FRAMESIZE * 4)

// [Octet 0-3] Vendor ID
#define A2DP_OPUS_VENDOR_ID 0x000000E0
// [Octet 4-5] Vendor Specific Codec ID
#define A2DP_OPUS_CODEC_ID 0x0001
// [Octet 6], [Bits 0,1,2] Channel Mode
#define A2DP_OPUS_CHANNEL_MODE_MASK 0x07
#define A2DP_OPUS_CHANNEL_MODE_MONO 0x01
#define A2DP_OPUS_CHANNEL_MODE_STEREO 0x02
#define A2DP_OPUS_CHANNEL_MODE_DUAL_MONO 0x04
// [Octet 6], [Bits 3,4] Future 2, FrameSize
#define A2DP_OPUS_FRAMESIZE_MASK 0x18
#define A2DP_OPUS_10MS_FRAMESIZE 0x08
#define A2DP_OPUS_20MS_FRAMESIZE 0x10
// [Octet 6], [Bits 5] Sampling Frequency
#define A2DP_OPUS_SAMPLING_FREQ_MASK 0x80
#define A2DP_OPUS_SAMPLING_FREQ_48000 0x80
// [Octet 6], [Bits 6,7] Reserved
#define A2DP_OPUS_FUTURE_3 0x40
#define A2DP_OPUS_FUTURE_4 0x80

// Length of the Opus Media Payload header
#define A2DP_OPUS_MPL_HDR_LEN 1

#define A2DP_OPUS_OFFSET (AVDT_MEDIA_OFFSET + A2DP_OPUS_MPL_HDR_LEN)

#define A2DP_OPUS_HDR_F_MSK 0x80
#define A2DP_OPUS_HDR_S_MSK 0x40
#define A2DP_OPUS_HDR_L_MSK 0x20
#define A2DP_OPUS_HDR_NUM_MSK 0x0F

#endif
