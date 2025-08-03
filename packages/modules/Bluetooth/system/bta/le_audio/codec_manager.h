/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
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

#include <vector>

#include "hardware/bt_le_audio.h"
#include "le_audio_types.h"

namespace le_audio {

struct stream_map_info {
  stream_map_info(uint16_t stream_handle, uint32_t audio_channel_allocation,
                  bool is_stream_active)
      : stream_handle(stream_handle),
        audio_channel_allocation(audio_channel_allocation),
        is_stream_active(is_stream_active) {}
  uint16_t stream_handle;
  uint32_t audio_channel_allocation;
  bool is_stream_active;
};

struct offload_config {
  std::vector<stream_map_info> stream_map;
  uint8_t bits_per_sample;
  uint32_t sampling_rate;
  uint32_t frame_duration;
  uint16_t octets_per_frame;
  uint8_t blocks_per_sdu;
  uint16_t peer_delay_ms;
};

struct broadcast_offload_config {
  std::vector<std::pair<uint16_t, uint32_t>> stream_map;
  uint8_t bits_per_sample;
  uint32_t sampling_rate;
  uint32_t frame_duration;
  uint16_t octets_per_frame;
  uint8_t blocks_per_sdu;
  uint8_t retransmission_number;
  uint16_t max_transport_latency;
};

class CodecManager {
 public:
  virtual ~CodecManager() = default;
  static CodecManager* GetInstance(void) {
    static CodecManager* instance = new CodecManager();
    return instance;
  }
  void Start(const std::vector<bluetooth::le_audio::btle_audio_codec_config_t>&
                 offloading_preference);
  void Stop(void);
  virtual types::CodecLocation GetCodecLocation(void) const;
  virtual bool IsOffloadDualBiDirSwbSupported(void) const;
  virtual void UpdateCisConfiguration(
      const std::vector<struct types::cis>& cises,
      const stream_parameters& stream_params, uint8_t direction);
  virtual void ClearCisConfiguration(uint8_t direction);
  virtual void UpdateActiveAudioConfig(
      const types::BidirectionalPair<stream_parameters>& stream_params,
      types::BidirectionalPair<uint16_t> delays_ms,
      std::function<void(const offload_config& config, uint8_t direction)>
          update_receiver);
  virtual const ::le_audio::set_configurations::AudioSetConfigurations*
  GetOffloadCodecConfig(::le_audio::types::LeAudioContextType ctx_type);
  virtual const ::le_audio::broadcast_offload_config*
  GetBroadcastOffloadConfig();
  virtual void UpdateBroadcastConnHandle(
      const std::vector<uint16_t>& conn_handle,
      std::function<void(const ::le_audio::broadcast_offload_config& config)>
          update_receiver);
  virtual std::vector<bluetooth::le_audio::btle_audio_codec_config_t>
  GetLocalAudioOutputCodecCapa();
  virtual std::vector<bluetooth::le_audio::btle_audio_codec_config_t>
  GetLocalAudioInputCodecCapa();

 private:
  CodecManager();
  struct impl;
  std::unique_ptr<impl> pimpl_;
};
}  // namespace le_audio
