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

#ifndef BT_STACK_FUZZ_A2DP_CODECCONFIG_FUNCTIONS_H_
#define BT_STACK_FUZZ_A2DP_CODECCONFIG_FUNCTIONS_H_

#include <fuzzer/FuzzedDataProvider.h>
#include <vector>
#include "a2dp_codec_api.h"
#include "fuzzers/a2dp/codec/a2dpCodecHelperFunctions.h"
#include "fuzzers/a2dp/codec/a2dpCodecInfoFuzzFunctions.h"
#include "fuzzers/common/commonFuzzHelpers.h"

#include "fuzzers/a2dp/codec/a2dpCodecConfigFuzzHelpers.h"

/* This is a vector of lambda functions the fuzzer will pull from.
 *  This is done so new functions can be added to the fuzzer easily
 *  without requiring modifications to the main fuzzer file. This also
 *  allows multiple fuzzers to include this file, if functionality is needed.
 */
std::vector<std::function<void(FuzzedDataProvider*)>>
    a2dp_codec_config_operations = {
        // createCodec
        [](FuzzedDataProvider* fdp) -> void {
          // Generate our arguments
          btav_a2dp_codec_index_t codec_index = getArbitraryBtavCodecIndex(fdp);
          btav_a2dp_codec_priority_t codec_priority =
              getArbitraryBtavCodecPriority(fdp);
          // Create our new codec
          std::shared_ptr<A2dpCodecConfig> codec_config(
              A2dpCodecConfig::createCodec(codec_index, codec_priority));
          // Push it to our vector
          if (codec_config) {
            a2dp_codec_config_vect.push_back(codec_config);
          }
        },

        // A2dpCodecConfig Destructor
        [](FuzzedDataProvider* fdp) -> void {
          if (a2dp_codec_config_vect.empty()) {
            return;
          }
          // Get random vector index
          size_t index = fdp->ConsumeIntegralInRange<size_t>(
              0, a2dp_codec_config_vect.size() - 1);
          // Remove from vector
          a2dp_codec_config_vect.erase(a2dp_codec_config_vect.begin() + index);
        },

        // codecIndex
        [](FuzzedDataProvider* fdp) -> void {
          std::shared_ptr<A2dpCodecConfig> codec_config(
              getArbitraryVectorElement(fdp, a2dp_codec_config_vect, false));
          if (codec_config == nullptr) {
            return;
          }

          codec_config->codecIndex();
        },

        // name
        [](FuzzedDataProvider* fdp) -> void {
          std::shared_ptr<A2dpCodecConfig> codec_config(
              getArbitraryVectorElement(fdp, a2dp_codec_config_vect, false));
          if (codec_config == nullptr) {
            return;
          }

          codec_config->name();
        },

        // codecPriority
        [](FuzzedDataProvider* fdp) -> void {
          std::shared_ptr<A2dpCodecConfig> codec_config(
              getArbitraryVectorElement(fdp, a2dp_codec_config_vect, false));
          if (codec_config == nullptr) {
            return;
          }

          codec_config->codecPriority();
        },

        // getCodecSpecificConfig
        [](FuzzedDataProvider* fdp) -> void {
          std::shared_ptr<A2dpCodecConfig> codec_config(
              getArbitraryVectorElement(fdp, a2dp_codec_config_vect, false));
          if (codec_config == nullptr) {
            return;
          }

          tBT_A2DP_OFFLOAD a2dp_offload = generateArbitrarytA2dpOffload(fdp);
          codec_config->getCodecSpecificConfig(&a2dp_offload);
        },

        // getTrackBitRate
        [](FuzzedDataProvider* fdp) -> void {
          std::shared_ptr<A2dpCodecConfig> codec_config(
              getArbitraryVectorElement(fdp, a2dp_codec_config_vect, false));
          if (codec_config == nullptr) {
            return;
          }

          codec_config->getTrackBitRate();
        },

        // copyOutOtaCodecConfig
        [](FuzzedDataProvider* fdp) -> void {
          std::shared_ptr<A2dpCodecConfig> codec_config(
              getArbitraryVectorElement(fdp, a2dp_codec_config_vect, false));
          if (codec_config == nullptr) {
            return;
          }

          uint8_t* codec_info =
              getArbitraryVectorElement(fdp, a2dp_codec_info_vect, true);
          codec_config->copyOutOtaCodecConfig(codec_info);
        },

        // getCodecConfig
        [](FuzzedDataProvider* fdp) -> void {
          std::shared_ptr<A2dpCodecConfig> codec_config(
              getArbitraryVectorElement(fdp, a2dp_codec_config_vect, false));
          if (codec_config == nullptr) {
            return;
          }

          codec_config->getCodecConfig();
        },

        // getCodecCapability
        [](FuzzedDataProvider* fdp) -> void {
          std::shared_ptr<A2dpCodecConfig> codec_config(
              getArbitraryVectorElement(fdp, a2dp_codec_config_vect, false));
          if (codec_config == nullptr) {
            return;
          }

          codec_config->getCodecCapability();
        },

        // getCodecLocalCapability
        [](FuzzedDataProvider* fdp) -> void {
          std::shared_ptr<A2dpCodecConfig> codec_config(
              getArbitraryVectorElement(fdp, a2dp_codec_config_vect, false));
          if (codec_config == nullptr) {
            return;
          }

          codec_config->getCodecLocalCapability();
        },

        // getCodecSelectableCapability
        [](FuzzedDataProvider* fdp) -> void {
          std::shared_ptr<A2dpCodecConfig> codec_config(
              getArbitraryVectorElement(fdp, a2dp_codec_config_vect, false));
          if (codec_config == nullptr) {
            return;
          }

          codec_config->getCodecSelectableCapability();
        },

        // getCodecUserConfig
        [](FuzzedDataProvider* fdp) -> void {
          std::shared_ptr<A2dpCodecConfig> codec_config(
              getArbitraryVectorElement(fdp, a2dp_codec_config_vect, false));
          if (codec_config == nullptr) {
            return;
          }

          codec_config->getCodecUserConfig();
        },

        // getCodecAudioConfig
        [](FuzzedDataProvider* fdp) -> void {
          std::shared_ptr<A2dpCodecConfig> codec_config(
              getArbitraryVectorElement(fdp, a2dp_codec_config_vect, false));
          if (codec_config == nullptr) {
            return;
          }

          codec_config->getCodecAudioConfig();
        },

        // getAudioBitsPerSample
        [](FuzzedDataProvider* fdp) -> void {
          std::shared_ptr<A2dpCodecConfig> codec_config(
              getArbitraryVectorElement(fdp, a2dp_codec_config_vect, false));
          if (codec_config == nullptr) {
            return;
          }

          codec_config->getAudioBitsPerSample();
        },

        // getAudioBitsPerSample
        [](FuzzedDataProvider* fdp) -> void {
          std::shared_ptr<A2dpCodecConfig> codec_config(
              getArbitraryVectorElement(fdp, a2dp_codec_config_vect, false));
          if (codec_config == nullptr) {
            return;
          }

          const btav_a2dp_codec_config_t btav_codec_config =
              getArbitraryBtavCodecConfig(fdp);
          codec_config->isCodecConfigEmpty(btav_codec_config);
        },

        // Dependency calling: CodecInfo
        [](FuzzedDataProvider* fdp) -> void {
          callArbitraryCodecInfoFunction(fdp, a2dp_codec_info_operations);
        }};

#endif  // BT_STACK_FUZZ_A2DP_CODECCONFIG_FUNCTIONS_H_
