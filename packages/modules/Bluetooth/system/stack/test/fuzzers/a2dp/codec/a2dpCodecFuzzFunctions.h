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

#ifndef BT_STACK_FUZZ_A2DP_CODEC_FUNCTIONS_H_
#define BT_STACK_FUZZ_A2DP_CODEC_FUNCTIONS_H_

#include <fcntl.h>  // For fd
#include <fuzzer/FuzzedDataProvider.h>
#include <sys/stat.h>  // For fd
#include <vector>
#include "a2dp_codec_api.h"
#include "fuzzers/a2dp/codec/a2dpCodecHelperFunctions.h"
#include "fuzzers/a2dp/codec/a2dpCodecInfoFuzzFunctions.h"
#include "fuzzers/common/commonFuzzHelpers.h"

#include "fuzzers/a2dp/codec/a2dpCodecFuzzHelpers.h"

#define MAX_NUM_PROPERTIES 128
#define A2DP_MAX_INIT_RUNS 16

/* This is a vector of lambda functions the fuzzer will pull from.
 *  This is done so new functions can be added to the fuzzer easily
 *  without requiring modifications to the main fuzzer file. This also
 *  allows multiple fuzzers to include this file, if functionality is needed.
 */
std::vector<std::function<void(FuzzedDataProvider*)>> a2dp_codec_operations = {
    // A2dpCodecs Constructor
    [](FuzzedDataProvider* fdp) -> void {
      // Build out a vector of codec objects
      std::vector<btav_a2dp_codec_config_t> codec_priorities;
      size_t num_priorities =
          fdp->ConsumeIntegralInRange<size_t>(0, MAX_NUM_PROPERTIES);
      for (size_t i = 0; i < num_priorities; i++) {
        codec_priorities.push_back(getArbitraryBtavCodecConfig(fdp));
      }
      // Construct a const ref so we can pass to constructor
      const std::vector<btav_a2dp_codec_config_t>& codec_priorities_const =
          codec_priorities;
      std::shared_ptr<A2dpCodecs> codecs(
          new A2dpCodecs(codec_priorities_const));
      if (codecs) {
        a2dp_codecs_vect.push_back(codecs);
      }
    },

    // A2dpCodecs Destructor
    [](FuzzedDataProvider* fdp) -> void {
      if (a2dp_codecs_vect.empty()) {
        return;
      }
      // Get random vector index
      size_t index =
          fdp->ConsumeIntegralInRange<size_t>(0, a2dp_codecs_vect.size() - 1);
      // Remove from vector
      a2dp_codecs_vect.erase(a2dp_codecs_vect.begin() + index);
    },

    // init
    [](FuzzedDataProvider* fdp) -> void {
      // Limit the number of times we can call this function per iteration
      // (This is to prevent slow-units)
      if (a2dp_init_runs <= A2DP_MAX_INIT_RUNS) {
        std::shared_ptr<A2dpCodecs> codecs =
            getArbitraryVectorElement(fdp, a2dp_codecs_vect, false);
        if (codecs) {
          a2dp_init_runs++;
          codecs->init();
        }
      }
    },

    // findSourceCodecConfig
    [](FuzzedDataProvider* fdp) -> void {
      std::shared_ptr<A2dpCodecs> codecs =
          getArbitraryVectorElement(fdp, a2dp_codecs_vect, false);
      uint8_t* p_codec_info =
          getArbitraryVectorElement(fdp, a2dp_codec_info_vect, false);

      if (codecs && p_codec_info) {
        codecs->findSourceCodecConfig(p_codec_info);
      }
    },

    // findSinkCodecConfig
    [](FuzzedDataProvider* fdp) -> void {
      std::shared_ptr<A2dpCodecs> codecs =
          getArbitraryVectorElement(fdp, a2dp_codecs_vect, false);
      uint8_t* p_codec_info =
          getArbitraryVectorElement(fdp, a2dp_codec_info_vect, false);

      if (codecs && p_codec_info) {
        codecs->findSinkCodecConfig(p_codec_info);
      }
    },

    // isSupportedCodec
    [](FuzzedDataProvider* fdp) -> void {
      std::shared_ptr<A2dpCodecs> codecs =
          getArbitraryVectorElement(fdp, a2dp_codecs_vect, false);
      if (codecs) {
        codecs->isSupportedCodec(getArbitraryBtavCodecIndex(fdp));
      }
    },

    // getCurrentCodecConfig
    [](FuzzedDataProvider* fdp) -> void {
      std::shared_ptr<A2dpCodecs> codecs =
          getArbitraryVectorElement(fdp, a2dp_codecs_vect, false);
      if (codecs) {
        codecs->getCurrentCodecConfig();
      }
    },

    // orderedSourceCodecs
    [](FuzzedDataProvider* fdp) -> void {
      std::shared_ptr<A2dpCodecs> codecs =
          getArbitraryVectorElement(fdp, a2dp_codecs_vect, false);
      if (codecs) {
        codecs->orderedSourceCodecs();
      }
    },

    // orderedSinkCodecs
    [](FuzzedDataProvider* fdp) -> void {
      std::shared_ptr<A2dpCodecs> codecs =
          getArbitraryVectorElement(fdp, a2dp_codecs_vect, false);
      if (codecs) {
        codecs->orderedSinkCodecs();
      }
    },

    // setCodecConfig
    [](FuzzedDataProvider* fdp) -> void {
      std::shared_ptr<A2dpCodecs> codecs =
          getArbitraryVectorElement(fdp, a2dp_codecs_vect, false);
      if (codecs == nullptr) {
        return;
      }

      const uint8_t* peer_codec_info =
          getArbitraryVectorElement(fdp, a2dp_codec_info_vect, false);
      if (peer_codec_info == nullptr) {
        return;
      }

      // Codec_config is actually some buffer
      std::unique_ptr<uint8_t, void (*)(void*)> p_result_codec_config(
          reinterpret_cast<uint8_t*>(calloc(500, sizeof(uint8_t))), free);
      if (p_result_codec_config) {
        codecs->setCodecConfig(peer_codec_info, fdp->ConsumeBool(),
                               p_result_codec_config.get(), fdp->ConsumeBool());
      }
    },

    // setSinkCodecConfig
    [](FuzzedDataProvider* fdp) -> void {
      std::shared_ptr<A2dpCodecs> codecs =
          getArbitraryVectorElement(fdp, a2dp_codecs_vect, false);
      if (codecs == nullptr) {
        return;
      }

      const uint8_t* peer_codec_info =
          getArbitraryVectorElement(fdp, a2dp_codec_info_vect, false);
      if (peer_codec_info == nullptr) {
        return;
      }

      // Codec_config is actually some buffer
      std::unique_ptr<uint8_t, void (*)(void*)> p_result_codec_config(
          reinterpret_cast<uint8_t*>(calloc(500, sizeof(uint8_t))), free);
      if (p_result_codec_config) {
        codecs->setSinkCodecConfig(peer_codec_info, fdp->ConsumeBool(),
                                   p_result_codec_config.get(),
                                   fdp->ConsumeBool());
      }
    },

    // setCodecUserConfig
    [](FuzzedDataProvider* fdp) -> void {
      std::shared_ptr<A2dpCodecs> codecs =
          getArbitraryVectorElement(fdp, a2dp_codecs_vect, false);
      if (codecs == nullptr) {
        return;
      }

      const btav_a2dp_codec_config_t codec_user_config =
          getArbitraryBtavCodecConfig(fdp);
      const tA2DP_ENCODER_INIT_PEER_PARAMS p_peer_params =
          getArbitraryA2dpEncoderInitPeerParams(fdp);
      const uint8_t* p_peer_sink_capabilities =
          getArbitraryVectorElement(fdp, a2dp_codec_info_vect, false);
      if (p_peer_sink_capabilities == nullptr) {
        return;
      }

      // Craft our result variables (And possibly pass nullptrs)
      btav_a2dp_codec_config_t result_codec_config;
      bool restart_input, restart_output, config_updated;
      uint8_t* p_result_codec_config =
          reinterpret_cast<uint8_t*>(&result_codec_config);
      codecs->setCodecUserConfig(codec_user_config, &p_peer_params,
                                 p_peer_sink_capabilities,
                                 p_result_codec_config, &restart_input,
                                 &restart_output, &config_updated);
    },

    // setCodecAudioConfig
    [](FuzzedDataProvider* fdp) -> void {
      std::shared_ptr<A2dpCodecs> codecs =
          getArbitraryVectorElement(fdp, a2dp_codecs_vect, false);
      if (codecs == nullptr) {
        return;
      }

      const btav_a2dp_codec_config_t codec_audio_config =
          getArbitraryBtavCodecConfig(fdp);
      const tA2DP_ENCODER_INIT_PEER_PARAMS p_peer_params =
          getArbitraryA2dpEncoderInitPeerParams(fdp);
      const uint8_t* p_peer_sink_capabilities =
          getArbitraryVectorElement(fdp, a2dp_codec_info_vect, false);
      if (p_peer_sink_capabilities == nullptr) {
        return;
      }
      btav_a2dp_codec_config_t result_codec_config;
      uint8_t* p_result_codec_config =
          reinterpret_cast<uint8_t*>(&result_codec_config);
      bool p_restart_output, p_config_updated;
      codecs->setCodecAudioConfig(
          codec_audio_config, &p_peer_params, p_peer_sink_capabilities,
          p_result_codec_config, &p_restart_output, &p_config_updated);
    },

    // setCodecOtaConfig
    [](FuzzedDataProvider* fdp) -> void {
      std::shared_ptr<A2dpCodecs> codecs =
          getArbitraryVectorElement(fdp, a2dp_codecs_vect, false);
      if (codecs == nullptr) {
        return;
      }

      const uint8_t* p_ota_codec_config =
          getArbitraryVectorElement(fdp, a2dp_codec_info_vect, false);
      if (p_ota_codec_config == nullptr) {
        return;
      }

      const tA2DP_ENCODER_INIT_PEER_PARAMS p_peer_params =
          getArbitraryA2dpEncoderInitPeerParams(fdp);
      btav_a2dp_codec_config_t result_codec_config;
      uint8_t* p_result_codec_config =
          reinterpret_cast<uint8_t*>(&result_codec_config);
      bool p_restart_input, p_restart_output, p_config_updated;
      codecs->setCodecOtaConfig(p_ota_codec_config, &p_peer_params,
                                p_result_codec_config, &p_restart_input,
                                &p_restart_output, &p_config_updated);
    },

    // setPeerSinkCodecCapabilities
    [](FuzzedDataProvider* fdp) -> void {
      std::shared_ptr<A2dpCodecs> codecs =
          getArbitraryVectorElement(fdp, a2dp_codecs_vect, false);
      if (codecs == nullptr) {
        return;
      }

      const uint8_t* p_peer_codec_capabilities =
          getArbitraryVectorElement(fdp, a2dp_codec_info_vect, false);
      if (p_peer_codec_capabilities == nullptr) {
        return;
      }
      codecs->setPeerSinkCodecCapabilities(p_peer_codec_capabilities);
    },

    // setPeerSourceCodecCapabilities
    [](FuzzedDataProvider* fdp) -> void {
      std::shared_ptr<A2dpCodecs> codecs =
          getArbitraryVectorElement(fdp, a2dp_codecs_vect, false);
      if (codecs == nullptr) {
        return;
      }

      const uint8_t* p_peer_codec_capabilities =
          getArbitraryVectorElement(fdp, a2dp_codec_info_vect, false);
      if (p_peer_codec_capabilities == nullptr) {
        return;
      }
      codecs->setPeerSourceCodecCapabilities(p_peer_codec_capabilities);
    },

    // getCodecConfigAndCapabilities
    [](FuzzedDataProvider* fdp) -> void {
      std::shared_ptr<A2dpCodecs> codecs =
          getArbitraryVectorElement(fdp, a2dp_codecs_vect, false);
      if (codecs == nullptr) {
        return;
      }

      // Return objects
      std::vector<btav_a2dp_codec_config_t> codecs_local_capabilities;
      std::vector<btav_a2dp_codec_config_t> codecs_selectable_capabilities;
      btav_a2dp_codec_config_t codec_config;
      codecs->getCodecConfigAndCapabilities(&codec_config,
                                            &codecs_local_capabilities,
                                            &codecs_selectable_capabilities);
    },

    // debug_codec_dump
    [](FuzzedDataProvider* fdp) -> void {
      std::shared_ptr<A2dpCodecs> codecs =
          getArbitraryVectorElement(fdp, a2dp_codecs_vect, false);
      if (codecs == nullptr) {
        return;
      }

      // Dump this to /dev/null
      int fd = open("/dev/null", O_WRONLY);
      codecs->debug_codec_dump(fd);
      close(fd);
    },

    // Since we're dependent on having valid codec_info objects,
    // have a change to call fuzz functions for that
    [](FuzzedDataProvider* fdp) -> void {
      callArbitraryCodecInfoFunction(fdp, a2dp_codec_info_operations);
    }};

#endif  // BT_STACK_FUZZ_A2DP_CODEC_FUNCTIONS_H_
