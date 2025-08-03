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

#include <fmq/AidlMessageQueue.h>
#include <hardware/audio.h>

#include <ctime>
#include <mutex>
#include <vector>

#include "audio_aidl_interfaces.h"
#include "audio_ctrl_ack.h"
#include "bluetooth_audio_port_impl.h"
#include "transport_instance.h"
#include "osi/include/properties.h"

#define BLUETOOTH_AUDIO_HAL_PROP_DISABLED \
  "persist.bluetooth.bluetooth_audio_hal.disabled"

namespace bluetooth {
namespace audio {
namespace aidl {

using ::aidl::android::hardware::bluetooth::audio::A2dpConfiguration;
using ::aidl::android::hardware::bluetooth::audio::A2dpConfigurationHint;
using ::aidl::android::hardware::bluetooth::audio::A2dpRemoteCapabilities;
using ::aidl::android::hardware::bluetooth::audio::A2dpStatus;
using ::aidl::android::hardware::bluetooth::audio::AudioCapabilities;
using ::aidl::android::hardware::bluetooth::audio::AudioConfiguration;
using ::aidl::android::hardware::bluetooth::audio::BluetoothAudioStatus;
using ::aidl::android::hardware::bluetooth::audio::CodecId;
using ::aidl::android::hardware::bluetooth::audio::CodecInfo;
using ::aidl::android::hardware::bluetooth::audio::CodecParameters;
using ::aidl::android::hardware::bluetooth::audio::CodecSpecificCapabilitiesLtv;
using ::aidl::android::hardware::bluetooth::audio::
    CodecSpecificConfigurationLtv;
using ::aidl::android::hardware::bluetooth::audio::IBluetoothAudioPort;
using ::aidl::android::hardware::bluetooth::audio::IBluetoothAudioProvider;
using ::aidl::android::hardware::bluetooth::audio::
    IBluetoothAudioProviderFactory;
using ::aidl::android::hardware::bluetooth::audio::LatencyMode;
using ::aidl::android::hardware::bluetooth::audio::MetadataLtv;
using ::aidl::android::hardware::bluetooth::audio::PcmConfiguration;

using ::aidl::android::hardware::common::fmq::MQDescriptor;
using ::aidl::android::hardware::common::fmq::SynchronizedReadWrite;
using ::android::AidlMessageQueue;

using MqDataType = int8_t;
using MqDataMode = SynchronizedReadWrite;
using DataMQ = AidlMessageQueue<MqDataType, MqDataMode>;
using DataMQDesc = MQDescriptor<MqDataType, MqDataMode>;

/***
 * The client interface connects an IBluetoothTransportInstance to
 * IBluetoothAudioProvider and helps to route callbacks to
 * IBluetoothTransportInstance
 ***/
class BluetoothAudioClientInterface {
 public:
  BluetoothAudioClientInterface(IBluetoothTransportInstance* instance);
  virtual ~BluetoothAudioClientInterface() = default;

  bool IsValid() const { return provider_ != nullptr; }

  std::vector<AudioCapabilities> GetAudioCapabilities() const;

  static std::vector<AudioCapabilities> GetAudioCapabilities(
      SessionType session_type);
  static std::optional<IBluetoothAudioProviderFactory::ProviderInfo>
  GetProviderInfo(SessionType session_type,
                  std::shared_ptr<IBluetoothAudioProviderFactory>
                      provider_factory = nullptr);

  std::optional<A2dpStatus> ParseA2dpConfiguration(
      const CodecId& codec_id, const std::vector<uint8_t>& configuration,
      CodecParameters* codec_parameters) const;

  std::optional<A2dpConfiguration> GetA2dpConfiguration(
      std::vector<A2dpRemoteCapabilities> const& remote_capabilities,
      A2dpConfigurationHint const& hint) const;

  void StreamStarted(const BluetoothAudioCtrlAck& ack);

  void StreamSuspended(const BluetoothAudioCtrlAck& ack);

  int StartSession();

  /***
   * Renew the connection and usually is used when aidl restarted
   ***/
  void RenewAudioProviderAndSession();

  int EndSession();

  bool UpdateAudioConfig(const AudioConfiguration& audioConfig);

  bool SetAllowedLatencyModes(std::vector<LatencyMode> latency_modes);

  void FlushAudioData();

  void SetCodecPriority(CodecId codec_id, int32_t priority);

  std::vector<IBluetoothAudioProvider::LeAudioAseConfigurationSetting>
  GetLeAudioAseConfiguration(
      std::optional<std::vector<
          std::optional<IBluetoothAudioProvider::LeAudioDeviceCapabilities>>>&
          remoteSinkAudioCapabilities,
      std::optional<std::vector<
          std::optional<IBluetoothAudioProvider::LeAudioDeviceCapabilities>>>&
          remoteSourceAudioCapabilities,
      std::vector<IBluetoothAudioProvider::LeAudioConfigurationRequirement>&
          requirements);

  IBluetoothAudioProvider::LeAudioAseQosConfigurationPair
  getLeAudioAseQosConfiguration(
      IBluetoothAudioProvider::LeAudioAseQosConfigurationRequirement&
          qosRequirement);

  void onSinkAseMetadataChanged(
      IBluetoothAudioProvider::AseState state, int32_t cigId, int32_t cisId,
      std::optional<std::vector<std::optional<MetadataLtv>>>& metadata);

  void onSourceAseMetadataChanged(
      IBluetoothAudioProvider::AseState state, int32_t cigId, int32_t cisId,
      std::optional<std::vector<std::optional<MetadataLtv>>>& metadata);

  IBluetoothAudioProvider::LeAudioBroadcastConfigurationSetting
  getLeAudioBroadcastConfiguration(
      const std::optional<std::vector<
          std::optional<IBluetoothAudioProvider::LeAudioDeviceCapabilities>>>&
          remoteSinkAudioCapabilities,
      const IBluetoothAudioProvider::LeAudioBroadcastConfigurationRequirement&
          requirement);

  static constexpr PcmConfiguration kInvalidPcmConfiguration = {};

  static bool is_aidl_available();

 protected:
  mutable std::mutex internal_mutex_;
  /***
   * Helper function to connect to an IBluetoothAudioProvider
   ***/
  void FetchAudioProvider();

  /***
   * Invoked when binder died
   ***/
  static void binderDiedCallbackAidl(void* cookie_ptr);

  std::shared_ptr<IBluetoothAudioProvider> provider_;

  std::shared_ptr<IBluetoothAudioProviderFactory> provider_factory_;

  bool session_started_;
  std::unique_ptr<DataMQ> data_mq_;

  ::ndk::ScopedAIBinder_DeathRecipient death_recipient_;
  // static constexpr const char* kDefaultAudioProviderFactoryInterface =
  //     "android.hardware.bluetooth.audio.IBluetoothAudioProviderFactory/default";
  static inline const std::string kDefaultAudioProviderFactoryInterface =
      std::string() + IBluetoothAudioProviderFactory::descriptor + "/default";
  static inline const std::string kSystemAudioProviderFactoryInterface =
      std::string() + IBluetoothAudioProviderFactory::descriptor + "/sysbta";
  static inline const std::string audioProviderFactoryInterface() {
   return osi_property_get_bool("persist.bluetooth.system_audio_hal.enabled", false)
    ? kSystemAudioProviderFactoryInterface : kDefaultAudioProviderFactoryInterface;
  }

 private:
  IBluetoothTransportInstance* transport_;
  std::vector<AudioCapabilities> capabilities_;
  std::vector<LatencyMode> latency_modes_;
};

/***
 * The client interface connects an IBluetoothTransportInstance to
 * IBluetoothAudioProvider and helps to route callbacks to
 * IBluetoothTransportInstance
 ***/
class BluetoothAudioSinkClientInterface : public BluetoothAudioClientInterface {
 public:
  /***
   * Constructs an BluetoothAudioSinkClientInterface to communicate to
   * BluetoothAudio HAL. |sink| is the implementation for the transport.
   ***/
  BluetoothAudioSinkClientInterface(IBluetoothSinkTransportInstance* sink);
  virtual ~BluetoothAudioSinkClientInterface();

  IBluetoothSinkTransportInstance* GetTransportInstance() const {
    return sink_;
  }

  /***
   * Read data from audio HAL through fmq
   ***/
  size_t ReadAudioData(uint8_t* p_buf, uint32_t len);

 private:
  IBluetoothSinkTransportInstance* sink_;

  static constexpr int kDefaultDataReadTimeoutMs = 10;
  static constexpr int kDefaultDataReadPollIntervalMs = 1;
};

class BluetoothAudioSourceClientInterface
    : public BluetoothAudioClientInterface {
 public:
  /***
   * Constructs an BluetoothAudioSourceClientInterface to communicate to
   * BluetoothAudio HAL. |source| is the implementation for the transport.
   ***/
  BluetoothAudioSourceClientInterface(
      IBluetoothSourceTransportInstance* source);
  virtual ~BluetoothAudioSourceClientInterface();

  IBluetoothSourceTransportInstance* GetTransportInstance() const {
    return source_;
  }

  /***
   * Write data to audio HAL through fmq
   ***/
  size_t WriteAudioData(const uint8_t* p_buf, uint32_t len);

 private:
  IBluetoothSourceTransportInstance* source_;

  static constexpr int kDefaultDataWriteTimeoutMs = 10;
  static constexpr int kDefaultDataWritePollIntervalMs = 1;
};

}  // namespace aidl
}  // namespace audio
}  // namespace bluetooth
