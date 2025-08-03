/*
 * Copyright 2019 The Android Open Source Project
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

#include <android/hardware/bluetooth/audio/2.1/IBluetoothAudioProvider.h>
#include <android/hardware/bluetooth/audio/2.1/types.h>
#include <fmq/MessageQueue.h>
#include <hardware/audio.h>
#include <time.h>

#include <mutex>
#include <vector>

#include "common/message_loop_thread.h"

#define BLUETOOTH_AUDIO_HAL_PROP_DISABLED \
  "persist.bluetooth.bluetooth_audio_hal.disabled"

namespace bluetooth {
namespace audio {
namespace hidl {

using AudioCapabilities =
    ::android::hardware::bluetooth::audio::V2_0::AudioCapabilities;
using AudioCapabilities_2_1 =
    ::android::hardware::bluetooth::audio::V2_1::AudioCapabilities;
using AudioConfiguration =
    ::android::hardware::bluetooth::audio::V2_0::AudioConfiguration;
using AudioConfiguration_2_1 =
    ::android::hardware::bluetooth::audio::V2_1::AudioConfiguration;
using ::android::hardware::bluetooth::audio::V2_0::BitsPerSample;
using ::android::hardware::bluetooth::audio::V2_0::ChannelMode;
using IBluetoothAudioProvider =
    ::android::hardware::bluetooth::audio::V2_0::IBluetoothAudioProvider;
using IBluetoothAudioProvider_2_1 =
    ::android::hardware::bluetooth::audio::V2_1::IBluetoothAudioProvider;
using PcmParameters =
    ::android::hardware::bluetooth::audio::V2_0::PcmParameters;
using PcmParameters_2_1 =
    ::android::hardware::bluetooth::audio::V2_1::PcmParameters;
using SampleRate = ::android::hardware::bluetooth::audio::V2_0::SampleRate;
using SampleRate_2_1 = ::android::hardware::bluetooth::audio::V2_1::SampleRate;
using SessionType = ::android::hardware::bluetooth::audio::V2_0::SessionType;
using SessionType_2_1 =
    ::android::hardware::bluetooth::audio::V2_1::SessionType;
using ::android::hardware::bluetooth::audio::V2_0::TimeSpec;
using BluetoothAudioStatus =
    ::android::hardware::bluetooth::audio::V2_0::Status;

enum class BluetoothAudioCtrlAck : uint8_t {
  SUCCESS_FINISHED = 0,
  PENDING,
  FAILURE_UNSUPPORTED,
  FAILURE_BUSY,
  FAILURE_DISCONNECTING,
  FAILURE
};

std::ostream& operator<<(std::ostream& os, const BluetoothAudioCtrlAck& ack);

inline BluetoothAudioStatus BluetoothAudioCtrlAckToHalStatus(
    const BluetoothAudioCtrlAck& ack) {
  switch (ack) {
    case BluetoothAudioCtrlAck::SUCCESS_FINISHED:
      return BluetoothAudioStatus::SUCCESS;
    case BluetoothAudioCtrlAck::FAILURE_UNSUPPORTED:
      return BluetoothAudioStatus::UNSUPPORTED_CODEC_CONFIGURATION;
    case BluetoothAudioCtrlAck::PENDING:
      return BluetoothAudioStatus::FAILURE;
    case BluetoothAudioCtrlAck::FAILURE_BUSY:
      return BluetoothAudioStatus::FAILURE;
    case BluetoothAudioCtrlAck::FAILURE_DISCONNECTING:
      return BluetoothAudioStatus::FAILURE;
    default:
      return BluetoothAudioStatus::FAILURE;
  }
}

// An IBluetoothTransportInstance needs to be implemented by a Bluetooth
// audio transport, such as A2DP or Hearing Aid, to handle callbacks from Audio
// HAL.
class IBluetoothTransportInstance {
 public:
  IBluetoothTransportInstance(SessionType sessionType,
                              AudioConfiguration audioConfig)
      : session_type_(sessionType),
        session_type_2_1_(SessionType_2_1::UNKNOWN),
        audio_config_(std::move(audioConfig)),
        audio_config_2_1_({}){};
  IBluetoothTransportInstance(SessionType_2_1 sessionType_2_1,
                              AudioConfiguration_2_1 audioConfig_2_1)
      : session_type_(SessionType::UNKNOWN),
        session_type_2_1_(sessionType_2_1),
        audio_config_({}),
        audio_config_2_1_(std::move(audioConfig_2_1)){};
  virtual ~IBluetoothTransportInstance() = default;

  SessionType GetSessionType() const { return session_type_; }
  SessionType_2_1 GetSessionType_2_1() const { return session_type_2_1_; }

  AudioConfiguration GetAudioConfiguration() const { return audio_config_; }
  AudioConfiguration_2_1 GetAudioConfiguration_2_1() const {
    return audio_config_2_1_;
  }

  void UpdateAudioConfiguration(const AudioConfiguration& audio_config) {
    audio_config_ = audio_config;
  }
  void UpdateAudioConfiguration_2_1(
      const AudioConfiguration_2_1& audio_config_2_1) {
    audio_config_2_1_ = audio_config_2_1;
  }

  virtual BluetoothAudioCtrlAck StartRequest() = 0;

  virtual BluetoothAudioCtrlAck SuspendRequest() = 0;

  virtual void StopRequest() = 0;

  virtual bool GetPresentationPosition(uint64_t* remote_delay_report_ns,
                                       uint64_t* total_bytes_readed,
                                       timespec* data_position) = 0;

  virtual void MetadataChanged(const source_metadata_t& source_metadata) = 0;

  // Invoked when the transport is requested to reset presentation position
  virtual void ResetPresentationPosition() = 0;

 private:
  const SessionType session_type_;
  const SessionType_2_1 session_type_2_1_;
  AudioConfiguration audio_config_;
  AudioConfiguration_2_1 audio_config_2_1_;
};

// An IBluetoothSinkTransportInstance needs to be implemented by a Bluetooth
// audio transport, such as A2DP, Hearing Aid or LeAudio, to handle callbacks
// from Audio HAL.
class IBluetoothSinkTransportInstance : public IBluetoothTransportInstance {
 public:
  IBluetoothSinkTransportInstance(SessionType sessionType,
                                  AudioConfiguration audioConfig)
      : IBluetoothTransportInstance{sessionType, audioConfig} {}
  IBluetoothSinkTransportInstance(SessionType_2_1 sessionType_2_1,
                                  AudioConfiguration_2_1 audioConfig_2_1)
      : IBluetoothTransportInstance{sessionType_2_1, audioConfig_2_1} {}
  virtual ~IBluetoothSinkTransportInstance() = default;

  // Invoked when the transport is requested to log bytes read
  virtual void LogBytesRead(size_t bytes_readed) = 0;
};

class IBluetoothSourceTransportInstance : public IBluetoothTransportInstance {
 public:
  IBluetoothSourceTransportInstance(SessionType sessionType,
                                    AudioConfiguration audioConfig)
      : IBluetoothTransportInstance{sessionType, audioConfig} {}
  IBluetoothSourceTransportInstance(SessionType_2_1 sessionType_2_1,
                                    AudioConfiguration_2_1 audioConfig_2_1)
      : IBluetoothTransportInstance{sessionType_2_1, audioConfig_2_1} {}
  virtual ~IBluetoothSourceTransportInstance() = default;

  // Invoked when the transport is requested to log bytes written
  virtual void LogBytesWritten(size_t bytes_written) = 0;
};

// common object is shared between different kind of SessionType
class BluetoothAudioDeathRecipient;

// The client interface connects an IBluetoothTransportInstance to
// IBluetoothAudioProvider and helps to route callbacks to
// IBluetoothTransportInstance
class BluetoothAudioClientInterface {
 public:
  BluetoothAudioClientInterface(
      android::sp<BluetoothAudioDeathRecipient> death_recipient,
      IBluetoothTransportInstance* instance);
  virtual ~BluetoothAudioClientInterface() = default;

  bool IsValid() const {
    return provider_ != nullptr || provider_2_1_ != nullptr;
  }

  std::vector<AudioCapabilities> GetAudioCapabilities() const;
  std::vector<AudioCapabilities_2_1> GetAudioCapabilities_2_1() const;
  static std::vector<AudioCapabilities> GetAudioCapabilities(
      SessionType session_type);
  static std::vector<AudioCapabilities_2_1> GetAudioCapabilities_2_1(
      SessionType_2_1 session_type_2_1);

  void StreamStarted(const BluetoothAudioCtrlAck& ack);

  void StreamSuspended(const BluetoothAudioCtrlAck& ack);

  int StartSession();
  int StartSession_2_1();

  // Renew the connection and usually is used when HIDL restarted
  void RenewAudioProviderAndSession();

  int EndSession();

  bool UpdateAudioConfig(const AudioConfiguration& audioConfig);
  bool UpdateAudioConfig_2_1(const AudioConfiguration_2_1& audioConfig_2_1);

  void FlushAudioData();

  static constexpr PcmParameters kInvalidPcmConfiguration = {
      .sampleRate = SampleRate::RATE_UNKNOWN,
      .channelMode = ChannelMode::UNKNOWN,
      .bitsPerSample = BitsPerSample::BITS_UNKNOWN};

 protected:
  mutable std::mutex internal_mutex_;
  // Helper function to connect to an IBluetoothAudioProvider
  void FetchAudioProvider();
  // Helper function to connect to an IBluetoothAudioProvider 2.1
  void FetchAudioProvider_2_1();

  android::sp<IBluetoothAudioProvider> provider_;
  android::sp<IBluetoothAudioProvider_2_1> provider_2_1_;
  bool session_started_;
  std::unique_ptr<::android::hardware::MessageQueue<
      uint8_t, ::android::hardware::kSynchronizedReadWrite>>
      mDataMQ;
  android::sp<BluetoothAudioDeathRecipient> death_recipient_;

 private:
  IBluetoothTransportInstance* transport_;
  std::vector<AudioCapabilities> capabilities_;
  std::vector<AudioCapabilities_2_1> capabilities_2_1_;
};

// The client interface connects an IBluetoothTransportInstance to
// IBluetoothAudioProvider and helps to route callbacks to
// IBluetoothTransportInstance
class BluetoothAudioSinkClientInterface : public BluetoothAudioClientInterface {
 public:
  // Constructs an BluetoothAudioSinkClientInterface to communicate to
  // BluetoothAudio HAL. |sink| is the implementation for the transport, and
  // |message_loop| is the thread where callbacks are invoked.
  BluetoothAudioSinkClientInterface(
      IBluetoothSinkTransportInstance* sink,
      bluetooth::common::MessageLoopThread* message_loop);
  virtual ~BluetoothAudioSinkClientInterface();

  IBluetoothSinkTransportInstance* GetTransportInstance() const {
    return sink_;
  }

  // Read data from audio  HAL through fmq
  size_t ReadAudioData(uint8_t* p_buf, uint32_t len);

 private:
  IBluetoothSinkTransportInstance* sink_;
};

class BluetoothAudioSourceClientInterface
    : public BluetoothAudioClientInterface {
 public:
  // Constructs an BluetoothAudioSourceClientInterface to communicate to
  // BluetoothAudio HAL. |source| is the implementation for the transport, and
  // |message_loop| is the thread where callbacks are invoked.
  BluetoothAudioSourceClientInterface(
      IBluetoothSourceTransportInstance* source,
      bluetooth::common::MessageLoopThread* message_loop);
  virtual ~BluetoothAudioSourceClientInterface();

  IBluetoothSourceTransportInstance* GetTransportInstance() const {
    return source_;
  }

  // Write data to audio HAL through fmq
  size_t WriteAudioData(const uint8_t* p_buf, uint32_t len);

 private:
  IBluetoothSourceTransportInstance* source_;
};

}  // namespace hidl
}  // namespace audio
}  // namespace bluetooth
