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

#pragma once

#include "../le_audio_software.h"
#include "audio_aidl_interfaces.h"
#include "bta/le_audio/le_audio_types.h"
#include "client_interface_aidl.h"

namespace bluetooth {
namespace audio {
namespace aidl {
namespace le_audio {

using ::aidl::android::hardware::bluetooth::audio::BroadcastCapability;
using ::aidl::android::hardware::bluetooth::audio::
    LeAudioBroadcastConfiguration;
using ::aidl::android::hardware::bluetooth::audio::LeAudioConfiguration;
using ::aidl::android::hardware::bluetooth::audio::PcmConfiguration;
using ::aidl::android::hardware::bluetooth::audio::SessionType;
using ::aidl::android::hardware::bluetooth::audio::UnicastCapability;
using ::bluetooth::audio::aidl::BluetoothAudioCtrlAck;
using ::bluetooth::audio::aidl::LatencyMode;
using ::bluetooth::audio::le_audio::StartRequestState;
using ::le_audio::DsaMode;
using ::le_audio::set_configurations::AudioSetConfiguration;
using ::le_audio::set_configurations::CodecConfigSetting;

constexpr uint8_t kChannelNumberMono = 1;
constexpr uint8_t kChannelNumberStereo = 2;

constexpr uint32_t kSampleRate48000 = 48000;
constexpr uint32_t kSampleRate44100 = 44100;
constexpr uint32_t kSampleRate32000 = 32000;
constexpr uint32_t kSampleRate24000 = 24000;
constexpr uint32_t kSampleRate16000 = 16000;
constexpr uint32_t kSampleRate8000 = 8000;

constexpr uint8_t kBitsPerSample16 = 16;
constexpr uint8_t kBitsPerSample24 = 24;
constexpr uint8_t kBitsPerSample32 = 32;

using ::bluetooth::audio::le_audio::StreamCallbacks;

void flush_source();
bool hal_ucast_capability_to_stack_format(
    const UnicastCapability& ucast_capability,
    CodecConfigSetting& stack_capability);
AudioConfiguration offload_config_to_hal_audio_config(
    const ::le_audio::offload_config& offload_config);

std::vector<AudioSetConfiguration> get_offload_capabilities();

class LeAudioTransport {
 public:
  LeAudioTransport(void (*flush)(void), StreamCallbacks stream_cb,
                   PcmConfiguration pcm_config);

  BluetoothAudioCtrlAck StartRequest(bool is_low_latency);
  BluetoothAudioCtrlAck StartRequestV2(bool is_low_latency);

  BluetoothAudioCtrlAck SuspendRequest();

  void StopRequest();

  void SetLatencyMode(LatencyMode latency_mode);

  bool GetPresentationPosition(uint64_t* remote_delay_report_ns,
                               uint64_t* total_bytes_processed,
                               timespec* data_position);

  void SourceMetadataChanged(const source_metadata_v7_t& source_metadata);

  void SinkMetadataChanged(const sink_metadata_v7_t& sink_metadata);

  void ResetPresentationPosition();

  void LogBytesProcessed(size_t bytes_processed);

  void SetRemoteDelay(uint16_t delay_report_ms);

  const PcmConfiguration& LeAudioGetSelectedHalPcmConfig();

  void LeAudioSetSelectedHalPcmConfig(uint32_t sample_rate_hz, uint8_t bit_rate,
                                      uint8_t channels_count,
                                      uint32_t data_interval);

  void LeAudioSetBroadcastConfig(
      const ::le_audio::broadcast_offload_config& offload_config);

  const LeAudioBroadcastConfiguration& LeAudioGetBroadcastConfig();

  bool IsRequestCompletedAfterUpdate(
      const std::function<
          std::pair<StartRequestState, bool>(StartRequestState)>& lambda);
  StartRequestState GetStartRequestState(void);
  void ClearStartRequestState(void);
  void SetStartRequestState(StartRequestState state);

 private:
  void (*flush_)(void);
  StreamCallbacks stream_cb_;
  uint16_t remote_delay_report_ms_;
  uint64_t total_bytes_processed_;
  timespec data_position_;
  PcmConfiguration pcm_config_;
  LeAudioBroadcastConfiguration broadcast_config_;
  mutable std::mutex start_request_state_mutex_;
  std::atomic<StartRequestState> start_request_state_;
  DsaMode dsa_mode_;
};

// Sink transport implementation for Le Audio
class LeAudioSinkTransport
    : public ::bluetooth::audio::aidl::IBluetoothSinkTransportInstance {
 public:
  LeAudioSinkTransport(SessionType session_type, StreamCallbacks stream_cb);

  ~LeAudioSinkTransport();

  BluetoothAudioCtrlAck StartRequest(bool is_low_latency) override;
  BluetoothAudioCtrlAck StartRequestV2(bool is_low_latency);

  BluetoothAudioCtrlAck SuspendRequest() override;

  void StopRequest() override;

  void SetLatencyMode(LatencyMode latency_mode) override;

  bool GetPresentationPosition(uint64_t* remote_delay_report_ns,
                               uint64_t* total_bytes_read,
                               timespec* data_position) override;

  void SourceMetadataChanged(
      const source_metadata_v7_t& source_metadata) override;

  void SinkMetadataChanged(const sink_metadata_v7_t& sink_metadata) override;

  void ResetPresentationPosition() override;

  void LogBytesRead(size_t bytes_read) override;

  void SetRemoteDelay(uint16_t delay_report_ms);

  const PcmConfiguration& LeAudioGetSelectedHalPcmConfig();

  void LeAudioSetSelectedHalPcmConfig(uint32_t sample_rate_hz, uint8_t bit_rate,
                                      uint8_t channels_count,
                                      uint32_t data_interval);

  void LeAudioSetBroadcastConfig(
      const ::le_audio::broadcast_offload_config& offload_config);

  const LeAudioBroadcastConfiguration& LeAudioGetBroadcastConfig();

  bool IsRequestCompletedAfterUpdate(
      const std::function<
          std::pair<StartRequestState, bool>(StartRequestState)>& lambda);
  StartRequestState GetStartRequestState(void);
  void ClearStartRequestState(void);
  void SetStartRequestState(StartRequestState state);

  static inline LeAudioSinkTransport* instance_unicast_ = nullptr;
  static inline LeAudioSinkTransport* instance_broadcast_ = nullptr;

  static inline BluetoothAudioSinkClientInterface* interface_unicast_ = nullptr;
  static inline BluetoothAudioSinkClientInterface* interface_broadcast_ =
      nullptr;

 private:
  LeAudioTransport* transport_;
};

class LeAudioSourceTransport
    : public ::bluetooth::audio::aidl::IBluetoothSourceTransportInstance {
 public:
  LeAudioSourceTransport(SessionType session_type, StreamCallbacks stream_cb);

  ~LeAudioSourceTransport();

  BluetoothAudioCtrlAck StartRequest(bool is_low_latency) override;
  BluetoothAudioCtrlAck StartRequestV2(bool is_low_latency);

  BluetoothAudioCtrlAck SuspendRequest() override;

  void StopRequest() override;

  void SetLatencyMode(LatencyMode latency_mode) override;

  bool GetPresentationPosition(uint64_t* remote_delay_report_ns,
                               uint64_t* total_bytes_written,
                               timespec* data_position) override;

  void SourceMetadataChanged(
      const source_metadata_v7_t& source_metadata) override;

  void SinkMetadataChanged(const sink_metadata_v7_t& sink_metadata) override;

  void ResetPresentationPosition() override;

  void LogBytesWritten(size_t bytes_written) override;

  void SetRemoteDelay(uint16_t delay_report_ms);

  const PcmConfiguration& LeAudioGetSelectedHalPcmConfig();

  void LeAudioSetSelectedHalPcmConfig(uint32_t sample_rate_hz, uint8_t bit_rate,
                                      uint8_t channels_count,
                                      uint32_t data_interval);

  bool IsRequestCompletedAfterUpdate(
      const std::function<
          std::pair<StartRequestState, bool>(StartRequestState)>& lambda);

  StartRequestState GetStartRequestState(void);
  void ClearStartRequestState(void);
  void SetStartRequestState(StartRequestState state);

  static inline LeAudioSourceTransport* instance = nullptr;
  static inline BluetoothAudioSourceClientInterface* interface = nullptr;

 private:
  LeAudioTransport* transport_;
};

}  // namespace le_audio
}  // namespace aidl
}  // namespace audio
}  // namespace bluetooth
