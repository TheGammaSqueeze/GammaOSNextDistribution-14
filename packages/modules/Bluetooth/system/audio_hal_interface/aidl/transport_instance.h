/*
 * Copyright 2022 The Android Open Source Project
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

#include <hardware/audio.h>

#include "audio_aidl_interfaces.h"
#include "audio_ctrl_ack.h"

namespace bluetooth {
namespace audio {
namespace aidl {

using ::aidl::android::hardware::bluetooth::audio::AudioConfiguration;
using ::aidl::android::hardware::bluetooth::audio::LatencyMode;
using ::aidl::android::hardware::bluetooth::audio::SessionType;

/***
 * An IBluetoothTransportInstance needs to be implemented by a Bluetooth
 * audio transport, such as A2DP or Hearing Aid, to handle callbacks from Audio
 * HAL.
 ***/
class IBluetoothTransportInstance {
 public:
  IBluetoothTransportInstance(SessionType sessionType,
                              AudioConfiguration audioConfig)
      : session_type_(sessionType), audio_config_(std::move(audioConfig)){};
  virtual ~IBluetoothTransportInstance() = default;

  SessionType GetSessionType() const { return session_type_; }

  AudioConfiguration GetAudioConfiguration() const { return audio_config_; }

  void UpdateAudioConfiguration(const AudioConfiguration& audio_config) {
    switch (audio_config.getTag()) {
      case AudioConfiguration::pcmConfig:
        audio_config_.set<AudioConfiguration::pcmConfig>(
            audio_config.get<AudioConfiguration::pcmConfig>());
        break;
      case AudioConfiguration::a2dpConfig:
        audio_config_.set<AudioConfiguration::a2dpConfig>(
            audio_config.get<AudioConfiguration::a2dpConfig>());
        break;
      case AudioConfiguration::hfpConfig:
        audio_config_.set<AudioConfiguration::hfpConfig>(
            audio_config.get<AudioConfiguration::hfpConfig>());
        break;
      case AudioConfiguration::leAudioConfig:
        audio_config_.set<AudioConfiguration::leAudioConfig>(
            audio_config.get<AudioConfiguration::leAudioConfig>());
        break;
      case AudioConfiguration::leAudioBroadcastConfig:
        audio_config_.set<AudioConfiguration::leAudioBroadcastConfig>(
            audio_config.get<AudioConfiguration::leAudioBroadcastConfig>());
        break;
      case AudioConfiguration::a2dp:
        audio_config_.set<AudioConfiguration::a2dp>(
            audio_config.get<AudioConfiguration::a2dp>());
        break;
    }
  }

  virtual BluetoothAudioCtrlAck StartRequest(bool is_low_latency) = 0;

  virtual BluetoothAudioCtrlAck SuspendRequest() = 0;

  virtual void StopRequest() = 0;

  virtual void SetLatencyMode(LatencyMode latency_mode) = 0;

  virtual bool GetPresentationPosition(uint64_t* remote_delay_report_ns,
                                       uint64_t* total_bytes_readed,
                                       timespec* data_position) = 0;

  virtual void SourceMetadataChanged(
      const source_metadata_v7_t& source_metadata) = 0;
  virtual void SinkMetadataChanged(const sink_metadata_v7_t& sink_metadata) = 0;

  /***
   * Invoked when the transport is requested to reset presentation position
   ***/
  virtual void ResetPresentationPosition() = 0;

 private:
  const SessionType session_type_;
  AudioConfiguration audio_config_;
};

/***
 * An IBluetoothSinkTransportInstance needs to be implemented by a Bluetooth
 * audio transport, such as A2DP, Hearing Aid or LeAudio, to handle callbacks
 * from Audio HAL.
 ***/
class IBluetoothSinkTransportInstance : public IBluetoothTransportInstance {
 public:
  IBluetoothSinkTransportInstance(SessionType sessionType,
                                  AudioConfiguration audioConfig)
      : IBluetoothTransportInstance{sessionType, audioConfig} {}
  virtual ~IBluetoothSinkTransportInstance() = default;

  /***
   * Invoked when the transport is requested to log bytes read
   ***/
  virtual void LogBytesRead(size_t bytes_readed) = 0;
};

class IBluetoothSourceTransportInstance : public IBluetoothTransportInstance {
 public:
  IBluetoothSourceTransportInstance(SessionType sessionType,
                                    AudioConfiguration audioConfig)
      : IBluetoothTransportInstance{sessionType, audioConfig} {}
  virtual ~IBluetoothSourceTransportInstance() = default;

  /***
   * Invoked when the transport is requested to log bytes written
   ***/
  virtual void LogBytesWritten(size_t bytes_written) = 0;
};

}  // namespace aidl
}  // namespace audio
}  // namespace bluetooth
