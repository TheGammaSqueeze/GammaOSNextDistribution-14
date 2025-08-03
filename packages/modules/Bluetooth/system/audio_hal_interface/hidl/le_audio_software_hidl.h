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
#include "bta/le_audio/le_audio_types.h"
#include "client_interface_hidl.h"

namespace bluetooth {
namespace audio {
namespace hidl {
namespace le_audio {

using ::android::hardware::bluetooth::audio::V2_1::PcmParameters;
using ::bluetooth::audio::hidl::BluetoothAudioCtrlAck;
using ::le_audio::set_configurations::AudioSetConfiguration;
using ::le_audio::set_configurations::CodecConfigSetting;

using ::bluetooth::audio::le_audio::StartRequestState;

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

void flush_sink();
void flush_source();

bool is_source_hal_enabled();
bool is_sink_hal_enabled();

class LeAudioTransport {
 public:
  LeAudioTransport(void (*flush)(void), StreamCallbacks stream_cb,
                   PcmParameters pcm_config);

  BluetoothAudioCtrlAck StartRequest();
  BluetoothAudioCtrlAck StartRequestV2();

  BluetoothAudioCtrlAck SuspendRequest();

  void StopRequest();

  bool GetPresentationPosition(uint64_t* remote_delay_report_ns,
                               uint64_t* total_bytes_processed,
                               timespec* data_position);

  void MetadataChanged(const source_metadata_t& source_metadata);

  void ResetPresentationPosition();

  void LogBytesProcessed(size_t bytes_processed);

  void SetRemoteDelay(uint16_t delay_report_ms);

  const PcmParameters& LeAudioGetSelectedHalPcmConfig();

  void LeAudioSetSelectedHalPcmConfig(uint32_t sample_rate_hz, uint8_t bit_rate,
                                      uint8_t channels_count,
                                      uint32_t data_interval);

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
  PcmParameters pcm_config_;
  mutable std::mutex start_request_state_mutex_;
  std::atomic<StartRequestState> start_request_state_;
};

// Sink transport implementation for Le Audio
class LeAudioSinkTransport
    : public ::bluetooth::audio::hidl::IBluetoothSinkTransportInstance {
 public:
  LeAudioSinkTransport(SessionType_2_1 session_type, StreamCallbacks stream_cb);

  ~LeAudioSinkTransport();

  BluetoothAudioCtrlAck StartRequest() override;
  BluetoothAudioCtrlAck StartRequestV2();

  BluetoothAudioCtrlAck SuspendRequest() override;

  void StopRequest() override;

  bool GetPresentationPosition(uint64_t* remote_delay_report_ns,
                               uint64_t* total_bytes_read,
                               timespec* data_position) override;

  void MetadataChanged(const source_metadata_t& source_metadata) override;

  void ResetPresentationPosition() override;

  void LogBytesRead(size_t bytes_read) override;

  void SetRemoteDelay(uint16_t delay_report_ms);

  const PcmParameters& LeAudioGetSelectedHalPcmConfig();

  void LeAudioSetSelectedHalPcmConfig(uint32_t sample_rate_hz, uint8_t bit_rate,
                                      uint8_t channels_count,
                                      uint32_t data_interval);

  bool IsRequestCompletedAfterUpdate(
      const std::function<
          std::pair<StartRequestState, bool>(StartRequestState)>& lambda);

  StartRequestState GetStartRequestState(void);
  void ClearStartRequestState(void);
  void SetStartRequestState(StartRequestState state);

  static inline LeAudioSinkTransport* instance = nullptr;
  static inline BluetoothAudioSinkClientInterface* interface = nullptr;

 private:
  LeAudioTransport* transport_;
};

class LeAudioSourceTransport
    : public ::bluetooth::audio::hidl::IBluetoothSourceTransportInstance {
 public:
  LeAudioSourceTransport(SessionType_2_1 session_type,
                         StreamCallbacks stream_cb);

  ~LeAudioSourceTransport();

  BluetoothAudioCtrlAck StartRequest() override;

  BluetoothAudioCtrlAck SuspendRequest() override;

  void StopRequest() override;

  bool GetPresentationPosition(uint64_t* remote_delay_report_ns,
                               uint64_t* total_bytes_written,
                               timespec* data_position) override;

  void MetadataChanged(const source_metadata_t& source_metadata) override;

  void ResetPresentationPosition() override;

  void LogBytesWritten(size_t bytes_written) override;

  void SetRemoteDelay(uint16_t delay_report_ms);

  const PcmParameters& LeAudioGetSelectedHalPcmConfig();

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
}  // namespace hidl
}  // namespace audio
}  // namespace bluetooth
