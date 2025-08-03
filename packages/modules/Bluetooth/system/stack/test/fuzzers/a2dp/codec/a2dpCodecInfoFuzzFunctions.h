/*
 * Copyright 2020 The Android Open Source Project
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

#ifndef BT_STACK_FUZZ_A2DP_CODECINFO_FUNCTIONS_H_
#define BT_STACK_FUZZ_A2DP_CODECINFO_FUNCTIONS_H_

#include <fuzzer/FuzzedDataProvider.h>

#include <vector>

#include "a2dp_codec_api.h"
#include "fuzzers/a2dp/codec/a2dpCodecHelperFunctions.h"
#include "fuzzers/a2dp/codec/a2dpCodecInfoFuzzHelpers.h"
#include "fuzzers/common/commonFuzzHelpers.h"
#include "stack/include/bt_hdr.h"

#define MAX_PACKET_SIZE 2048

/* This is a vector of lambda functions the fuzzer will pull from.
 *  This is done so new functions can be added to the fuzzer easily
 *  without requiring modifications to the main fuzzer file. This also
 *  allows multiple fuzzers to include this file, if functionality is needed.
 */
std::vector<std::function<void(FuzzedDataProvider*, uint8_t*)>>
    a2dp_codec_info_operations = {
        // A2DP_InitDefaultCodec
        [](FuzzedDataProvider* fdp, uint8_t*) -> void {
          // Allocate space for a new codec & add it to our tracking vector
          uint8_t* codec_info = new uint8_t[AVDT_CODEC_SIZE];
          a2dp_codec_info_vect.push_back(codec_info);

          A2DP_InitDefaultCodec(codec_info);
        },

        // Delete a codec_info object
        [](FuzzedDataProvider* fdp, uint8_t*) -> void {
          if (a2dp_codec_info_vect.empty()) {
            return;
          }
          // Get random vector index
          size_t index = fdp->ConsumeIntegralInRange<size_t>(
              0, a2dp_codec_info_vect.size() - 1);
          // delete codec
          delete a2dp_codec_info_vect.at(index);
          // Remove from vector
          a2dp_codec_info_vect.erase(a2dp_codec_info_vect.begin() + index);
        },

        // A2DP_GetCodecType
        [](FuzzedDataProvider* fdp, uint8_t* codec_info) -> void {
          A2DP_GetCodecType(codec_info);
        },

        // A2DP_IsSourceCodecValid
        [](FuzzedDataProvider* fdp, uint8_t* codec_info) -> void {
          A2DP_IsSourceCodecValid(codec_info);
        },

        // A2DP_IsSinkCodecValid
        [](FuzzedDataProvider* fdp, uint8_t* codec_info) -> void {
          A2DP_IsSinkCodecValid(codec_info);
        },

        // A2DP_IsPeerSourceCodecValid
        [](FuzzedDataProvider* fdp, uint8_t* codec_info) -> void {
          A2DP_IsPeerSourceCodecValid(codec_info);
        },

        // A2DP_IsPeerSinkCodecValid
        [](FuzzedDataProvider* fdp, uint8_t* codec_info) -> void {
          A2DP_IsPeerSinkCodecValid(codec_info);
        },

        // A2DP_IsSinkCodecSupported
        [](FuzzedDataProvider* fdp, uint8_t* codec_info) -> void {
          A2DP_IsSinkCodecSupported(codec_info);
        },

        // A2DP_IsPeerSourceCodecSupported
        [](FuzzedDataProvider* fdp, uint8_t* codec_info) -> void {
          A2DP_IsPeerSourceCodecSupported(codec_info);
        },

        // A2DP_UsesRtpHeader
        [](FuzzedDataProvider* fdp, uint8_t* codec_info) -> void {
          A2DP_UsesRtpHeader(fdp->ConsumeBool(), codec_info);
        },

        // A2DP_GetMediaType
        [](FuzzedDataProvider* fdp, uint8_t* codec_info) -> void {
          A2DP_GetMediaType(codec_info);
        },

        // A2DP_CodecName
        [](FuzzedDataProvider* fdp, uint8_t* codec_info) -> void {
          A2DP_CodecName(codec_info);
        },

        // A2DP_CodecTypeEquals
        [](FuzzedDataProvider* fdp, uint8_t* codec_info) -> void {
          uint8_t* codec_info_2 =
              getArbitraryVectorElement(fdp, a2dp_codec_info_vect, false);
          if (codec_info_2) {
            A2DP_CodecTypeEquals(codec_info, codec_info_2);
          }
        },

        // A2DP_CodecEquals
        [](FuzzedDataProvider* fdp, uint8_t* codec_info) -> void {
          uint8_t* codec_info_2 =
              getArbitraryVectorElement(fdp, a2dp_codec_info_vect, false);
          if (codec_info_2) {
            A2DP_CodecEquals(codec_info, codec_info_2);
          }
        },

        // A2DP_GetTrackSampleRate
        [](FuzzedDataProvider* fdp, uint8_t* codec_info) -> void {
          A2DP_GetTrackSampleRate(codec_info);
        },

        // A2DP_GetTrackBitsPerSample
        [](FuzzedDataProvider* fdp, uint8_t* codec_info) -> void {
          A2DP_GetTrackBitsPerSample(codec_info);
        },

        // A2DP_GetTrackChannelCount
        [](FuzzedDataProvider* fdp, uint8_t* codec_info) -> void {
          A2DP_GetTrackChannelCount(codec_info);
        },

        // A2DP_GetSinkTrackChannelType
        [](FuzzedDataProvider* fdp, uint8_t* codec_info) -> void {
          A2DP_GetSinkTrackChannelType(codec_info);
        },

        // A2DP_GetPacketTimestamp
        [](FuzzedDataProvider* fdp, uint8_t* codec_info) -> void {
          uint32_t timestamp_retval;
          size_t packet_size =
              fdp->ConsumeIntegralInRange<size_t>(0, MAX_PACKET_SIZE);
          std::vector<uint8_t> bytes = fdp->ConsumeBytes<uint8_t>(packet_size);
          // Timestamp will fail if p_data is < 4 bytes, due to a cast & deref
          // to a uint32_t*
          if (bytes.size() < 4) {
            return;
          }
          const uint8_t* p_data = bytes.data();

          A2DP_GetPacketTimestamp(codec_info, p_data, &timestamp_retval);
        },

        // A2DP_BuildCodecHeader
        [](FuzzedDataProvider* fdp, uint8_t* codec_info) -> void {
          std::shared_ptr<BT_HDR> p_buf = getArbitraryBtHdr(fdp);
          if (p_buf) {
            uint16_t frames_per_packet = fdp->ConsumeIntegral<uint16_t>();
            A2DP_BuildCodecHeader(codec_info, p_buf.get(), frames_per_packet);
          }
        },

        // A2DP_GetEncoderInterface
        [](FuzzedDataProvider* fdp, uint8_t* codec_info) -> void {
          A2DP_GetEncoderInterface(codec_info);
        },

        // A2DP_GetDecoderInterface
        [](FuzzedDataProvider* fdp, uint8_t* codec_info) -> void {
          A2DP_GetDecoderInterface(codec_info);
        },

        // A2DP_AdjustCodec
        [](FuzzedDataProvider* fdp, uint8_t* codec_info) -> void {
          A2DP_AdjustCodec(codec_info);
        },

        // A2DP_SourceCodecIndex
        [](FuzzedDataProvider* fdp, uint8_t* codec_info) -> void {
          A2DP_SourceCodecIndex(codec_info);
        },

        // A2DP_SinkCodecIndex
        [](FuzzedDataProvider* fdp, uint8_t* codec_info) -> void {
          A2DP_SinkCodecIndex(codec_info);
        },

        // A2DP_CodecIndexStr
        [](FuzzedDataProvider* fdp, uint8_t*) -> void {
          A2DP_CodecIndexStr(getArbitraryBtavCodecIndex(fdp));
        },

        // A2DP_InitCodecConfig
        [](FuzzedDataProvider* fdp, uint8_t*) -> void {
          AvdtpSepConfig cfg_retval;
          A2DP_InitCodecConfig(getArbitraryBtavCodecIndex(fdp), &cfg_retval);
        },

        // A2DP_GetEecoderEffectiveFrameSize
        [](FuzzedDataProvider* fdp, uint8_t* codec_info) -> void {
          A2DP_GetEecoderEffectiveFrameSize(codec_info);
        },

        // A2DP_CodecInfoString
        [](FuzzedDataProvider* fdp, uint8_t* codec_info) -> void {
          A2DP_CodecInfoString(codec_info);
        }};

#endif  // BT_STACK_FUZZ_A2DP_CODECINFO_FUNCTIONS_H_
