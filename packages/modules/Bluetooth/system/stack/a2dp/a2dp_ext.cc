/**
 * Copyright 2023 The Android Open Source Project
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

#include "a2dp_ext.h"

#include <base/logging.h>
#include <bluetooth/log.h>

#include "a2dp_codec_api.h"
#include "audio_hal_interface/a2dp_encoding.h"

using namespace bluetooth;

static uint64_t codec_id(btav_a2dp_codec_index_t codec_index) {
  uint64_t id = 0;
  auto result = ::bluetooth::audio::a2dp::provider::codec_info(
      codec_index, &id, nullptr, nullptr);
  LOG_ASSERT(result) << "provider::codec_info unexpectdly failed";
  return id;
}

A2dpCodecConfigExt::A2dpCodecConfigExt(btav_a2dp_codec_index_t codec_index,
                                       bool is_source)
    : A2dpCodecConfig(
          codec_index, codec_id(codec_index),
          bluetooth::audio::a2dp::provider::codec_index_str(codec_index)
              .value(),
          BTAV_A2DP_CODEC_PRIORITY_DEFAULT),
      is_source_(is_source) {
  // Load the local capabilities from the provider info.
  auto result = ::bluetooth::audio::a2dp::provider::codec_info(
      codec_index, nullptr, ota_codec_config_, &codec_capability_);
  LOG_ASSERT(result) << "provider::codec_info unexpectdly failed";
  codec_selectable_capability_ = codec_capability_;
}

bool A2dpCodecConfigExt::setCodecConfig(const uint8_t* p_peer_codec_info,
                                        bool is_capability,
                                        uint8_t* p_result_codec_config) {
  // Call get_a2dp_config to recompute best capabilities.
  // This method need to update codec_capability_, codec_config_,
  // and ota_codec_config_ using the local codec_user_config_, and input
  // peer_codec_info.
  using namespace bluetooth::audio::a2dp;
  provider::a2dp_remote_capabilities capabilities = {
      .seid = 0,  // the SEID does not matter here.
      .capabilities = p_peer_codec_info,
  };

  auto result = provider::get_a2dp_configuration(
      RawAddress::kEmpty,
      std::vector<provider::a2dp_remote_capabilities>{capabilities},
      codec_user_config_);
  if (!result.has_value()) {
    log::error("Failed to set a configuration for {}", name_);
    return false;
  }

  memcpy(ota_codec_config_, result->codec_config, sizeof(ota_codec_config_));
  codec_config_ = result->codec_parameters;
  codec_capability_ = result->codec_parameters;
  vendor_specific_parameters_ = result->vendor_specific_parameters;
  return true;
}

bool A2dpCodecConfigExt::setPeerCodecCapabilities(
    const uint8_t* p_peer_codec_capabilities) {
  // setPeerCodecCapabilities updates the selectable
  // capabilities in the codec config. It can be safely
  // ignored as providing a superset of the selectable
  // capabilities is safe.
  return true;
}

tA2DP_ENCODER_INTERFACE const a2dp_encoder_interface_ext = {
    .encoder_init = [](const tA2DP_ENCODER_INIT_PEER_PARAMS*, A2dpCodecConfig*,
                       a2dp_source_read_callback_t,
                       a2dp_source_enqueue_callback_t) {},
    .encoder_cleanup = []() {},
    .feeding_reset = []() {},
    .feeding_flush = []() {},
    .get_encoder_interval_ms = []() { return (uint64_t)20; },
    .get_effective_frame_size = []() { return 0; },
    .send_frames = [](uint64_t) {},
    .set_transmit_queue_length = [](size_t) {},
};

const tA2DP_ENCODER_INTERFACE* A2DP_GetEncoderInterfaceExt(const uint8_t*) {
  return &a2dp_encoder_interface_ext;
}
