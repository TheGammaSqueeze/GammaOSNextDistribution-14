/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com. Represented by EHIMA -
 * www.ehima.com
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

#define LOG_TAG "BTAudioClientLeAudioStub"

#include "le_audio_software.h"

#include <android_bluetooth_flags.h>

#include <unordered_map>
#include <vector>

#include "aidl/le_audio_software_aidl.h"
#include "bta/le_audio/codec_manager.h"
#include "hal_version_manager.h"
#include "hidl/le_audio_software_hidl.h"
#include "os/log.h"
#include "osi/include/properties.h"

namespace bluetooth {
namespace audio {
namespace le_audio {

namespace {

using ::android::hardware::bluetooth::audio::V2_1::PcmParameters;
using AudioConfiguration_2_1 =
    ::android::hardware::bluetooth::audio::V2_1::AudioConfiguration;
using AudioConfigurationAIDL =
    ::aidl::android::hardware::bluetooth::audio::AudioConfiguration;
using ::aidl::android::hardware::bluetooth::audio::LatencyMode;
using ::aidl::android::hardware::bluetooth::audio::LeAudioCodecConfiguration;

using ::le_audio::CodecManager;
using ::le_audio::set_configurations::AudioSetConfiguration;
using ::le_audio::types::CodecLocation;
}  // namespace

std::vector<AudioSetConfiguration> get_offload_capabilities() {
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    return std::vector<AudioSetConfiguration>(0);
  }
  return aidl::le_audio::get_offload_capabilities();
}

aidl::BluetoothAudioSinkClientInterface* get_aidl_client_interface(
    bool is_broadcaster) {
  if (is_broadcaster)
    return aidl::le_audio::LeAudioSinkTransport::interface_broadcast_;

  return aidl::le_audio::LeAudioSinkTransport::interface_unicast_;
}

aidl::le_audio::LeAudioSinkTransport* get_aidl_transport_instance(
    bool is_broadcaster) {
  if (is_broadcaster)
    return aidl::le_audio::LeAudioSinkTransport::instance_broadcast_;

  return aidl::le_audio::LeAudioSinkTransport::instance_unicast_;
}

bool is_aidl_offload_encoding_session(bool is_broadcaster) {
  return get_aidl_client_interface(is_broadcaster)
                 ->GetTransportInstance()
                 ->GetSessionType() ==
             aidl::SessionType::LE_AUDIO_HARDWARE_OFFLOAD_ENCODING_DATAPATH ||
         get_aidl_client_interface(is_broadcaster)
                 ->GetTransportInstance()
                 ->GetSessionType() ==
             aidl::SessionType::
                 LE_AUDIO_BROADCAST_HARDWARE_OFFLOAD_ENCODING_DATAPATH;
}

LeAudioClientInterface* LeAudioClientInterface::interface = nullptr;
LeAudioClientInterface* LeAudioClientInterface::Get() {
  if (osi_property_get_bool(BLUETOOTH_AUDIO_HAL_PROP_DISABLED, false)) {
    LOG(ERROR) << __func__ << ": BluetoothAudio HAL is disabled";
    return nullptr;
  }

  if (LeAudioClientInterface::interface == nullptr)
    LeAudioClientInterface::interface = new LeAudioClientInterface();

  return LeAudioClientInterface::interface;
}

void LeAudioClientInterface::Sink::Cleanup() {
  LOG_INFO("HAL transport: 0x%02x, is broadcast: %d",
            static_cast<int>(HalVersionManager::GetHalTransport()),
            is_broadcaster_);

  StopSession();

  /* Cleanup transport interface and instance according to type and role */
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    if (hidl::le_audio::LeAudioSinkTransport::interface) {
      delete hidl::le_audio::LeAudioSinkTransport::interface;
      hidl::le_audio::LeAudioSinkTransport::interface = nullptr;
    }
    if (hidl::le_audio::LeAudioSinkTransport::instance) {
      delete hidl::le_audio::LeAudioSinkTransport::instance;
      hidl::le_audio::LeAudioSinkTransport::instance = nullptr;
    }
  } else if (HalVersionManager::GetHalTransport() ==
             BluetoothAudioHalTransport::AIDL) {
    if (IsBroadcaster()) {
      if (aidl::le_audio::LeAudioSinkTransport::interface_broadcast_) {
        delete aidl::le_audio::LeAudioSinkTransport::interface_broadcast_;
        aidl::le_audio::LeAudioSinkTransport::interface_broadcast_ = nullptr;
      }
      if (aidl::le_audio::LeAudioSinkTransport::instance_broadcast_) {
        delete aidl::le_audio::LeAudioSinkTransport::instance_broadcast_;
        aidl::le_audio::LeAudioSinkTransport::instance_broadcast_ = nullptr;
      }
    } else {
      if (aidl::le_audio::LeAudioSinkTransport::interface_unicast_) {
        delete aidl::le_audio::LeAudioSinkTransport::interface_unicast_;
        aidl::le_audio::LeAudioSinkTransport::interface_unicast_ = nullptr;
      }
      if (aidl::le_audio::LeAudioSinkTransport::instance_unicast_) {
        delete aidl::le_audio::LeAudioSinkTransport::instance_unicast_;
        aidl::le_audio::LeAudioSinkTransport::instance_unicast_ = nullptr;
      }
    }
  } else {
    LOG_ERROR("Invalid HAL transport: 0x%02x",
              static_cast<int>(HalVersionManager::GetHalTransport()));
  }
}

void LeAudioClientInterface::Sink::SetPcmParameters(
    const PcmParameters& params) {
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    return hidl::le_audio::LeAudioSinkTransport::instance
        ->LeAudioSetSelectedHalPcmConfig(
            params.sample_rate, params.bits_per_sample, params.channels_count,
            params.data_interval_us);
  }
  return get_aidl_transport_instance(is_broadcaster_)
      ->LeAudioSetSelectedHalPcmConfig(
          params.sample_rate, params.bits_per_sample, params.channels_count,
          params.data_interval_us);
}

// Update Le Audio delay report to BluetoothAudio HAL
void LeAudioClientInterface::Sink::SetRemoteDelay(uint16_t delay_report_ms) {
  LOG(INFO) << __func__ << ": delay_report_ms=" << delay_report_ms << " ms";
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    hidl::le_audio::LeAudioSinkTransport::instance->SetRemoteDelay(
        delay_report_ms);
    return;
  }
  get_aidl_transport_instance(is_broadcaster_)->SetRemoteDelay(delay_report_ms);
}

void LeAudioClientInterface::Sink::StartSession() {
  LOG(INFO) << __func__;
  if (HalVersionManager::GetHalVersion() ==
      BluetoothAudioHalVersion::VERSION_2_1) {
    AudioConfiguration_2_1 audio_config;
    audio_config.pcmConfig(hidl::le_audio::LeAudioSinkTransport::instance
                               ->LeAudioGetSelectedHalPcmConfig());
    if (!hidl::le_audio::LeAudioSinkTransport::interface->UpdateAudioConfig_2_1(
            audio_config)) {
      LOG(ERROR) << __func__ << ": cannot update audio config to HAL";
      return;
    }
    hidl::le_audio::LeAudioSinkTransport::interface->StartSession_2_1();
    return;
  } else if (HalVersionManager::GetHalTransport() ==
             BluetoothAudioHalTransport::AIDL) {
    AudioConfigurationAIDL audio_config;
    if (is_aidl_offload_encoding_session(is_broadcaster_)) {
      if (is_broadcaster_) {
        audio_config.set<AudioConfigurationAIDL::leAudioBroadcastConfig>(
            get_aidl_transport_instance(is_broadcaster_)
                ->LeAudioGetBroadcastConfig());
      } else {
        aidl::le_audio::LeAudioConfiguration le_audio_config = {};
        audio_config.set<AudioConfigurationAIDL::leAudioConfig>(
            le_audio_config);
      }
    } else {
      audio_config.set<AudioConfigurationAIDL::pcmConfig>(
          get_aidl_transport_instance(is_broadcaster_)
              ->LeAudioGetSelectedHalPcmConfig());
    }
    if (!get_aidl_client_interface(is_broadcaster_)
             ->UpdateAudioConfig(audio_config)) {
      LOG(ERROR) << __func__ << ": cannot update audio config to HAL";
      return;
    }
    get_aidl_client_interface(is_broadcaster_)->StartSession();
  }
}

void LeAudioClientInterface::Sink::ConfirmStreamingRequest() {
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    auto hidl_instance = hidl::le_audio::LeAudioSinkTransport::instance;
    auto start_request_state = hidl_instance->GetStartRequestState();

    switch (start_request_state) {
      case StartRequestState::IDLE:
        LOG_WARN(", no pending start stream request");
        return;
      case StartRequestState::PENDING_BEFORE_RESUME:
        LOG_INFO("Response before sending PENDING to audio HAL");
        hidl_instance->SetStartRequestState(StartRequestState::CONFIRMED);
        return;
      case StartRequestState::PENDING_AFTER_RESUME:
        LOG_INFO("Response after sending PENDING to audio HAL");
        hidl_instance->ClearStartRequestState();
        hidl::le_audio::LeAudioSinkTransport::interface->StreamStarted(
            hidl::BluetoothAudioCtrlAck::SUCCESS_FINISHED);
        return;
      case StartRequestState::CONFIRMED:
      case StartRequestState::CANCELED:
        LOG_ERROR("Invalid state, start stream already confirmed");
        return;
    }
  }

  auto aidl_instance = get_aidl_transport_instance(is_broadcaster_);
  auto start_request_state = aidl_instance->GetStartRequestState();
  switch (start_request_state) {
    case StartRequestState::IDLE:
      LOG_WARN(", no pending start stream request");
      return;
    case StartRequestState::PENDING_BEFORE_RESUME:
      LOG_INFO("Response before sending PENDING to audio HAL");
      aidl_instance->SetStartRequestState(StartRequestState::CONFIRMED);
      return;
    case StartRequestState::PENDING_AFTER_RESUME:
      LOG_INFO("Response after sending PENDING to audio HAL");
      aidl_instance->ClearStartRequestState();
      get_aidl_client_interface(is_broadcaster_)
          ->StreamStarted(aidl::BluetoothAudioCtrlAck::SUCCESS_FINISHED);
      return;
    case StartRequestState::CONFIRMED:
    case StartRequestState::CANCELED:
      LOG_ERROR("Invalid state, start stream already confirmed");
      return;
  }
}

void LeAudioClientInterface::Sink::ConfirmStreamingRequestV2() {
  auto lambda = [&](StartRequestState currect_start_request_state)
      -> std::pair<StartRequestState, bool> {
    switch (currect_start_request_state) {
      case StartRequestState::IDLE:
        LOG_WARN(", no pending start stream request");
        return std::make_pair(StartRequestState::IDLE, false);
      case StartRequestState::PENDING_BEFORE_RESUME:
        LOG_INFO("Response before sending PENDING to audio HAL");
        return std::make_pair(StartRequestState::CONFIRMED, false);
      case StartRequestState::PENDING_AFTER_RESUME:
        LOG_INFO("Response after sending PENDING to audio HAL");
        return std::make_pair(StartRequestState::IDLE, true);
      case StartRequestState::CONFIRMED:
      case StartRequestState::CANCELED:
        LOG_ERROR("Invalid state, start stream already confirmed");
        return std::make_pair(currect_start_request_state, false);
    }
  };

  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    auto hidl_instance = hidl::le_audio::LeAudioSinkTransport::instance;
    if (hidl_instance->IsRequestCompletedAfterUpdate(lambda)) {
      hidl::le_audio::LeAudioSinkTransport::interface->StreamStarted(
          hidl::BluetoothAudioCtrlAck::SUCCESS_FINISHED);
    }

    return;
  }

  auto aidl_instance = get_aidl_transport_instance(is_broadcaster_);
  if (aidl_instance->IsRequestCompletedAfterUpdate(lambda)) {
    get_aidl_client_interface(is_broadcaster_)
        ->StreamStarted(aidl::BluetoothAudioCtrlAck::SUCCESS_FINISHED);
  }
}

void LeAudioClientInterface::Sink::CancelStreamingRequest() {
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    auto hidl_instance = hidl::le_audio::LeAudioSinkTransport::instance;
    auto start_request_state = hidl_instance->GetStartRequestState();
    switch (start_request_state) {
      case StartRequestState::IDLE:
        LOG_WARN(", no pending start stream request");
        return;
      case StartRequestState::PENDING_BEFORE_RESUME:
        LOG_INFO("Response before sending PENDING to audio HAL");
        hidl_instance->SetStartRequestState(StartRequestState::CANCELED);
        return;
      case StartRequestState::PENDING_AFTER_RESUME:
        LOG_INFO("Response after sending PENDING to audio HAL");
        hidl_instance->ClearStartRequestState();
        hidl::le_audio::LeAudioSinkTransport::interface->StreamStarted(
            hidl::BluetoothAudioCtrlAck::FAILURE);
        return;
      case StartRequestState::CONFIRMED:
      case StartRequestState::CANCELED:
        LOG_ERROR("Invalid state, start stream already confirmed");
        break;
    }
  }

  auto aidl_instance = get_aidl_transport_instance(is_broadcaster_);
  auto start_request_state = aidl_instance->GetStartRequestState();
  switch (start_request_state) {
    case StartRequestState::IDLE:
      LOG_WARN(", no pending start stream request");
      return;
    case StartRequestState::PENDING_BEFORE_RESUME:
      LOG_INFO("Response before sending PENDING to audio HAL");
      aidl_instance->SetStartRequestState(StartRequestState::CANCELED);
      return;
    case StartRequestState::PENDING_AFTER_RESUME:
      LOG_INFO("Response after sending PENDING to audio HAL");
      aidl_instance->ClearStartRequestState();
      get_aidl_client_interface(is_broadcaster_)
          ->StreamStarted(aidl::BluetoothAudioCtrlAck::FAILURE);
      return;
    case StartRequestState::CONFIRMED:
    case StartRequestState::CANCELED:
      LOG_ERROR("Invalid state, start stream already confirmed");
      break;
  }
}

void LeAudioClientInterface::Sink::CancelStreamingRequestV2() {
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    auto hidl_instance = hidl::le_audio::LeAudioSinkTransport::instance;
    auto start_request_state = hidl_instance->GetStartRequestState();
    switch (start_request_state) {
      case StartRequestState::IDLE:
        LOG_WARN(", no pending start stream request");
        return;
      case StartRequestState::PENDING_BEFORE_RESUME:
        LOG_INFO("Response before sending PENDING to audio HAL");
        hidl_instance->SetStartRequestState(StartRequestState::CANCELED);
        return;
      case StartRequestState::PENDING_AFTER_RESUME:
        LOG_INFO("Response after sending PENDING to audio HAL");
        hidl_instance->ClearStartRequestState();
        hidl::le_audio::LeAudioSinkTransport::interface->StreamStarted(
            hidl::BluetoothAudioCtrlAck::FAILURE);
        return;
      case StartRequestState::CONFIRMED:
      case StartRequestState::CANCELED:
        LOG_ERROR("Invalid state, start stream already confirmed");
        break;
    }
  }

  auto aidl_instance = get_aidl_transport_instance(is_broadcaster_);
  auto start_request_state = aidl_instance->GetStartRequestState();
  switch (start_request_state) {
    case StartRequestState::IDLE:
      LOG_WARN(", no pending start stream request");
      return;
    case StartRequestState::PENDING_BEFORE_RESUME:
      LOG_INFO("Response before sending PENDING to audio HAL");
      aidl_instance->SetStartRequestState(StartRequestState::CANCELED);
      return;
    case StartRequestState::PENDING_AFTER_RESUME:
      LOG_INFO("Response after sending PENDING to audio HAL");
      aidl_instance->ClearStartRequestState();
      get_aidl_client_interface(is_broadcaster_)
          ->StreamStarted(aidl::BluetoothAudioCtrlAck::FAILURE);
      return;
    case StartRequestState::CONFIRMED:
    case StartRequestState::CANCELED:
      LOG_ERROR("Invalid state, start stream already confirmed");
      break;
  }
}

void LeAudioClientInterface::Sink::StopSession() {
  LOG(INFO) << __func__ << " sink";
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    hidl::le_audio::LeAudioSinkTransport::instance->ClearStartRequestState();
    hidl::le_audio::LeAudioSinkTransport::interface->EndSession();
    return;
  }
  get_aidl_transport_instance(is_broadcaster_)->ClearStartRequestState();
  get_aidl_client_interface(is_broadcaster_)->EndSession();
}

void LeAudioClientInterface::Sink::UpdateAudioConfigToHal(
    const ::le_audio::offload_config& offload_config) {
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    return;
  }

  if (is_broadcaster_ || !is_aidl_offload_encoding_session(is_broadcaster_)) {
    return;
  }

  get_aidl_client_interface(is_broadcaster_)
      ->UpdateAudioConfig(
          aidl::le_audio::offload_config_to_hal_audio_config(offload_config));
}

void LeAudioClientInterface::Sink::UpdateBroadcastAudioConfigToHal(
    const ::le_audio::broadcast_offload_config& offload_config) {
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    return;
  }

  if (!is_broadcaster_ || !is_aidl_offload_encoding_session(is_broadcaster_)) {
    return;
  }

  get_aidl_transport_instance(is_broadcaster_)
      ->LeAudioSetBroadcastConfig(offload_config);
}

void LeAudioClientInterface::Sink::SuspendedForReconfiguration() {
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    hidl::le_audio::LeAudioSinkTransport::interface->StreamSuspended(
        hidl::BluetoothAudioCtrlAck::SUCCESS_FINISHED);
    return;
  }

  get_aidl_client_interface(is_broadcaster_)
      ->StreamSuspended(aidl::BluetoothAudioCtrlAck::SUCCESS_RECONFIGURATION);
}

void LeAudioClientInterface::Sink::ReconfigurationComplete() {
  // This is needed only for AIDL since SuspendedForReconfiguration()
  // already calls StreamSuspended(SUCCESS_FINISHED) for HIDL
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::AIDL) {
    // FIXME: For now we have to workaround the missing API and use
    //        StreamSuspended() with SUCCESS_FINISHED ack code.
    get_aidl_client_interface(is_broadcaster_)
        ->StreamSuspended(aidl::BluetoothAudioCtrlAck::SUCCESS_FINISHED);
  }
}

size_t LeAudioClientInterface::Sink::Read(uint8_t* p_buf, uint32_t len) {
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    return hidl::le_audio::LeAudioSinkTransport::interface->ReadAudioData(p_buf,
                                                                          len);
  }
  return get_aidl_client_interface(is_broadcaster_)->ReadAudioData(p_buf, len);
}

void LeAudioClientInterface::Source::Cleanup() {
  LOG(INFO) << __func__ << " source";
  StopSession();
  if (hidl::le_audio::LeAudioSourceTransport::interface) {
    delete hidl::le_audio::LeAudioSourceTransport::interface;
    hidl::le_audio::LeAudioSourceTransport::interface = nullptr;
  }
  if (hidl::le_audio::LeAudioSourceTransport::instance) {
    delete hidl::le_audio::LeAudioSourceTransport::instance;
    hidl::le_audio::LeAudioSourceTransport::instance = nullptr;
  }
  if (aidl::le_audio::LeAudioSourceTransport::interface) {
    delete aidl::le_audio::LeAudioSourceTransport::interface;
    aidl::le_audio::LeAudioSourceTransport::interface = nullptr;
  }
  if (aidl::le_audio::LeAudioSourceTransport::instance) {
    delete aidl::le_audio::LeAudioSourceTransport::instance;
    aidl::le_audio::LeAudioSourceTransport::instance = nullptr;
  }
}

void LeAudioClientInterface::Source::SetPcmParameters(
    const PcmParameters& params) {
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    hidl::le_audio::LeAudioSourceTransport::instance
        ->LeAudioSetSelectedHalPcmConfig(
            params.sample_rate, params.bits_per_sample, params.channels_count,
            params.data_interval_us);
    return;
  }
  return aidl::le_audio::LeAudioSourceTransport::instance
      ->LeAudioSetSelectedHalPcmConfig(
          params.sample_rate, params.bits_per_sample, params.channels_count,
          params.data_interval_us);
}

void LeAudioClientInterface::Source::SetRemoteDelay(uint16_t delay_report_ms) {
  LOG(INFO) << __func__ << ": delay_report_ms=" << delay_report_ms << " ms";
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    hidl::le_audio::LeAudioSourceTransport::instance->SetRemoteDelay(
        delay_report_ms);
    return;
  }
  return aidl::le_audio::LeAudioSourceTransport::instance->SetRemoteDelay(
      delay_report_ms);
}

void LeAudioClientInterface::Source::StartSession() {
  LOG(INFO) << __func__;
  if (HalVersionManager::GetHalVersion() ==
      BluetoothAudioHalVersion::VERSION_2_1) {
    AudioConfiguration_2_1 audio_config;
    audio_config.pcmConfig(hidl::le_audio::LeAudioSourceTransport::instance
                               ->LeAudioGetSelectedHalPcmConfig());
    if (!hidl::le_audio::LeAudioSourceTransport::
             interface->UpdateAudioConfig_2_1(audio_config)) {
      LOG(ERROR) << __func__ << ": cannot update audio config to HAL";
      return;
    }
    hidl::le_audio::LeAudioSourceTransport::interface->StartSession_2_1();
    return;
  } else if (HalVersionManager::GetHalTransport() ==
             BluetoothAudioHalTransport::AIDL) {
    AudioConfigurationAIDL audio_config;
    if (aidl::le_audio::LeAudioSourceTransport::
            interface->GetTransportInstance()
                ->GetSessionType() ==
        aidl::SessionType::LE_AUDIO_HARDWARE_OFFLOAD_DECODING_DATAPATH) {
      aidl::le_audio::LeAudioConfiguration le_audio_config;
      audio_config.set<AudioConfigurationAIDL::leAudioConfig>(
          aidl::le_audio::LeAudioConfiguration{});
    } else {
      audio_config.set<AudioConfigurationAIDL::pcmConfig>(
          aidl::le_audio::LeAudioSourceTransport::instance
              ->LeAudioGetSelectedHalPcmConfig());
    }

    if (!aidl::le_audio::LeAudioSourceTransport::interface->UpdateAudioConfig(
            audio_config)) {
      LOG(ERROR) << __func__ << ": cannot update audio config to HAL";
      return;
    }
    aidl::le_audio::LeAudioSourceTransport::interface->StartSession();
  }
}

void LeAudioClientInterface::Source::SuspendedForReconfiguration() {
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    hidl::le_audio::LeAudioSourceTransport::interface->StreamSuspended(
        hidl::BluetoothAudioCtrlAck::SUCCESS_FINISHED);
    return;
  }

  aidl::le_audio::LeAudioSourceTransport::interface->StreamSuspended(
      aidl::BluetoothAudioCtrlAck::SUCCESS_RECONFIGURATION);
}

void LeAudioClientInterface::Source::ReconfigurationComplete() {
  // This is needed only for AIDL since SuspendedForReconfiguration()
  // already calls StreamSuspended(SUCCESS_FINISHED) for HIDL
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::AIDL) {
    // FIXME: For now we have to workaround the missing API and use
    //        StreamSuspended() with SUCCESS_FINISHED ack code.
    aidl::le_audio::LeAudioSourceTransport::interface->StreamSuspended(
        aidl::BluetoothAudioCtrlAck::SUCCESS_FINISHED);
  }
}

void LeAudioClientInterface::Source::ConfirmStreamingRequest() {
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    auto hidl_instance = hidl::le_audio::LeAudioSourceTransport::instance;
    auto start_request_state = hidl_instance->GetStartRequestState();

    switch (start_request_state) {
      case StartRequestState::IDLE:
        LOG_WARN(", no pending start stream request");
        return;
      case StartRequestState::PENDING_BEFORE_RESUME:
        LOG_INFO("Response before sending PENDING to audio HAL");
        hidl_instance->SetStartRequestState(StartRequestState::CONFIRMED);
        return;
      case StartRequestState::PENDING_AFTER_RESUME:
        LOG_INFO("Response after sending PENDING to audio HAL");
        hidl_instance->ClearStartRequestState();
        hidl::le_audio::LeAudioSourceTransport::interface->StreamStarted(
            hidl::BluetoothAudioCtrlAck::SUCCESS_FINISHED);
        return;
      case StartRequestState::CONFIRMED:
      case StartRequestState::CANCELED:
        LOG_ERROR("Invalid state, start stream already confirmed");
        return;
    }
  }

  auto aidl_instance = aidl::le_audio::LeAudioSourceTransport::instance;
  auto start_request_state = aidl_instance->GetStartRequestState();
  switch (start_request_state) {
    case StartRequestState::IDLE:
      LOG_WARN(", no pending start stream request");
      return;
    case StartRequestState::PENDING_BEFORE_RESUME:
      LOG_INFO("Response before sending PENDING to audio HAL");
      aidl_instance->SetStartRequestState(StartRequestState::CONFIRMED);
      return;
    case StartRequestState::PENDING_AFTER_RESUME:
      LOG_INFO("Response after sending PENDING to audio HAL");
      aidl_instance->ClearStartRequestState();
      aidl::le_audio::LeAudioSourceTransport::interface->StreamStarted(
          aidl::BluetoothAudioCtrlAck::SUCCESS_FINISHED);
      return;
    case StartRequestState::CONFIRMED:
    case StartRequestState::CANCELED:
      LOG_ERROR("Invalid state, start stream already confirmed");
      return;
  }
}

void LeAudioClientInterface::Source::ConfirmStreamingRequestV2() {
  auto lambda = [&](StartRequestState currect_start_request_state)
      -> std::pair<StartRequestState, bool> {
    switch (currect_start_request_state) {
      case StartRequestState::IDLE:
        LOG_WARN(", no pending start stream request");
        return std::make_pair(StartRequestState::IDLE, false);
      case StartRequestState::PENDING_BEFORE_RESUME:
        LOG_INFO("Response before sending PENDING to audio HAL");
        return std::make_pair(StartRequestState::CONFIRMED, false);
      case StartRequestState::PENDING_AFTER_RESUME:
        LOG_INFO("Response after sending PENDING to audio HAL");
        return std::make_pair(StartRequestState::IDLE, true);
      case StartRequestState::CONFIRMED:
      case StartRequestState::CANCELED:
        LOG_ERROR("Invalid state, start stream already confirmed");
        return std::make_pair(currect_start_request_state, false);
    }
  };

  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    auto hidl_instance = hidl::le_audio::LeAudioSourceTransport::instance;

    if (hidl_instance->IsRequestCompletedAfterUpdate(lambda)) {
      hidl::le_audio::LeAudioSourceTransport::interface->StreamStarted(
          hidl::BluetoothAudioCtrlAck::SUCCESS_FINISHED);
    }
    return;
  }

  auto aidl_instance = aidl::le_audio::LeAudioSourceTransport::instance;
  if (aidl_instance->IsRequestCompletedAfterUpdate(lambda)) {
    aidl::le_audio::LeAudioSourceTransport::interface->StreamStarted(
        aidl::BluetoothAudioCtrlAck::SUCCESS_FINISHED);
  }
}

void LeAudioClientInterface::Source::CancelStreamingRequest() {
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    auto hidl_instance = hidl::le_audio::LeAudioSourceTransport::instance;
    auto start_request_state = hidl_instance->GetStartRequestState();
    switch (start_request_state) {
      case StartRequestState::IDLE:
        LOG_WARN(", no pending start stream request");
        return;
      case StartRequestState::PENDING_BEFORE_RESUME:
        LOG_INFO("Response before sending PENDING to audio HAL");
        hidl_instance->SetStartRequestState(StartRequestState::CANCELED);
        return;
      case StartRequestState::PENDING_AFTER_RESUME:
        LOG_INFO("Response after sending PENDING to audio HAL");
        hidl_instance->ClearStartRequestState();
        hidl::le_audio::LeAudioSourceTransport::interface->StreamStarted(
            hidl::BluetoothAudioCtrlAck::FAILURE);
        return;
      case StartRequestState::CONFIRMED:
      case StartRequestState::CANCELED:
        LOG_ERROR("Invalid state, start stream already confirmed");
        break;
    }
  }

  auto aidl_instance = aidl::le_audio::LeAudioSourceTransport::instance;
  auto start_request_state = aidl_instance->GetStartRequestState();
  switch (start_request_state) {
    case StartRequestState::IDLE:
      LOG_WARN(", no pending start stream request");
      return;
    case StartRequestState::PENDING_BEFORE_RESUME:
      LOG_INFO("Response before sending PENDING to audio HAL");
      aidl_instance->SetStartRequestState(StartRequestState::CANCELED);
      return;
    case StartRequestState::PENDING_AFTER_RESUME:
      LOG_INFO("Response after sending PENDING to audio HAL");
      aidl_instance->ClearStartRequestState();
      aidl::le_audio::LeAudioSourceTransport::interface->StreamStarted(
          aidl::BluetoothAudioCtrlAck::FAILURE);
      return;
    case StartRequestState::CONFIRMED:
    case StartRequestState::CANCELED:
      LOG_ERROR("Invalid state, start stream already confirmed");
      break;
  }
}

void LeAudioClientInterface::Source::CancelStreamingRequestV2() {
  auto lambda = [&](StartRequestState currect_start_request_state)
      -> std::pair<StartRequestState, bool> {
    switch (currect_start_request_state) {
      case StartRequestState::IDLE:
        LOG_WARN(", no pending start stream request");
        return std::make_pair(StartRequestState::IDLE, false);
      case StartRequestState::PENDING_BEFORE_RESUME:
        LOG_INFO("Response before sending PENDING to audio HAL");
        return std::make_pair(StartRequestState::CANCELED, false);
      case StartRequestState::PENDING_AFTER_RESUME:
        LOG_INFO("Response after sending PENDING to audio HAL");
        return std::make_pair(StartRequestState::IDLE, true);
      case StartRequestState::CONFIRMED:
      case StartRequestState::CANCELED:
        LOG_ERROR("Invalid state, start stream already confirmed");
        return std::make_pair(currect_start_request_state, false);
    }
  };

  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    auto hidl_instance = hidl::le_audio::LeAudioSourceTransport::instance;
    if (hidl_instance->IsRequestCompletedAfterUpdate(lambda)) {
      hidl::le_audio::LeAudioSourceTransport::interface->StreamStarted(
          hidl::BluetoothAudioCtrlAck::FAILURE);
    }
    return;
  }

  auto aidl_instance = aidl::le_audio::LeAudioSourceTransport::instance;
  if (aidl_instance->IsRequestCompletedAfterUpdate(lambda)) {
    aidl::le_audio::LeAudioSourceTransport::interface->StreamStarted(
        aidl::BluetoothAudioCtrlAck::FAILURE);
  }
}

void LeAudioClientInterface::Source::StopSession() {
  LOG(INFO) << __func__ << " source";
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    hidl::le_audio::LeAudioSourceTransport::instance->ClearStartRequestState();
    hidl::le_audio::LeAudioSourceTransport::interface->EndSession();
    return;
  }
  aidl::le_audio::LeAudioSourceTransport::instance->ClearStartRequestState();
  aidl::le_audio::LeAudioSourceTransport::interface->EndSession();
}

void LeAudioClientInterface::Source::UpdateAudioConfigToHal(
    const ::le_audio::offload_config& offload_config) {
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    return;
  }

  if (aidl::le_audio::LeAudioSourceTransport::interface->GetTransportInstance()
          ->GetSessionType() !=
      aidl::SessionType::LE_AUDIO_HARDWARE_OFFLOAD_DECODING_DATAPATH) {
    return;
  }
  aidl::le_audio::LeAudioSourceTransport::interface->UpdateAudioConfig(
      aidl::le_audio::offload_config_to_hal_audio_config(offload_config));
}

size_t LeAudioClientInterface::Source::Write(const uint8_t* p_buf,
                                             uint32_t len) {
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    return hidl::le_audio::LeAudioSourceTransport::interface->WriteAudioData(
        p_buf, len);
  }
  return aidl::le_audio::LeAudioSourceTransport::interface->WriteAudioData(
      p_buf, len);
}

LeAudioClientInterface::Sink* LeAudioClientInterface::GetSink(
    StreamCallbacks stream_cb,
    bluetooth::common::MessageLoopThread* message_loop,
    bool is_broadcasting_session_type) {
  if (is_broadcasting_session_type && HalVersionManager::GetHalTransport() ==
                                          BluetoothAudioHalTransport::HIDL) {
    LOG(WARNING) << __func__
                 << ", No support for broadcasting Le Audio on HIDL";
    return nullptr;
  }

  Sink* sink = is_broadcasting_session_type ? broadcast_sink_ : unicast_sink_;
  if (sink == nullptr) {
    sink = new Sink(is_broadcasting_session_type);
  } else {
    LOG(WARNING) << __func__ << ", Sink is already acquired";
    return nullptr;
  }

  LOG(INFO) << __func__;

  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    hidl::SessionType_2_1 session_type =
        hidl::SessionType_2_1::LE_AUDIO_SOFTWARE_ENCODING_DATAPATH;

    hidl::le_audio::LeAudioSinkTransport::instance =
        new hidl::le_audio::LeAudioSinkTransport(session_type,
                                                 std::move(stream_cb));
    hidl::le_audio::LeAudioSinkTransport::interface =
        new hidl::BluetoothAudioSinkClientInterface(
            hidl::le_audio::LeAudioSinkTransport::instance, message_loop);
    if (!hidl::le_audio::LeAudioSinkTransport::interface->IsValid()) {
      LOG(WARNING) << __func__
                   << ": BluetoothAudio HAL for Le Audio is invalid?!";
      delete hidl::le_audio::LeAudioSinkTransport::interface;
      hidl::le_audio::LeAudioSinkTransport::interface = nullptr;
      delete hidl::le_audio::LeAudioSinkTransport::instance;
      hidl::le_audio::LeAudioSinkTransport::instance = nullptr;
      delete sink;
      sink = nullptr;

      return nullptr;
    }
  } else {
    aidl::SessionType session_type =
        is_broadcasting_session_type
            ? aidl::SessionType::LE_AUDIO_BROADCAST_SOFTWARE_ENCODING_DATAPATH
            : aidl::SessionType::LE_AUDIO_SOFTWARE_ENCODING_DATAPATH;
    if (CodecManager::GetInstance()->GetCodecLocation() !=
        CodecLocation::HOST) {
      session_type =
          is_broadcasting_session_type
              ? aidl::SessionType::
                    LE_AUDIO_BROADCAST_HARDWARE_OFFLOAD_ENCODING_DATAPATH
              : aidl::SessionType::LE_AUDIO_HARDWARE_OFFLOAD_ENCODING_DATAPATH;
    }

    if (session_type ==
            aidl::SessionType::LE_AUDIO_HARDWARE_OFFLOAD_ENCODING_DATAPATH ||
        session_type ==
            aidl::SessionType::LE_AUDIO_SOFTWARE_ENCODING_DATAPATH) {
      aidl::le_audio::LeAudioSinkTransport::instance_unicast_ =
          new aidl::le_audio::LeAudioSinkTransport(session_type,
                                                   std::move(stream_cb));
      aidl::le_audio::LeAudioSinkTransport::interface_unicast_ =
          new aidl::BluetoothAudioSinkClientInterface(
              aidl::le_audio::LeAudioSinkTransport::instance_unicast_);
      if (!aidl::le_audio::LeAudioSinkTransport::interface_unicast_
               ->IsValid()) {
        LOG(WARNING) << __func__
                     << ": BluetoothAudio HAL for Le Audio is invalid?!";
        delete aidl::le_audio::LeAudioSinkTransport::interface_unicast_;
        aidl::le_audio::LeAudioSinkTransport::interface_unicast_ = nullptr;
        delete aidl::le_audio::LeAudioSinkTransport::instance_unicast_;
        aidl::le_audio::LeAudioSinkTransport::instance_unicast_ = nullptr;
        delete sink;
        sink = nullptr;

        return nullptr;
      }
    } else {
      aidl::le_audio::LeAudioSinkTransport::instance_broadcast_ =
          new aidl::le_audio::LeAudioSinkTransport(session_type,
                                                   std::move(stream_cb));
      aidl::le_audio::LeAudioSinkTransport::interface_broadcast_ =
          new aidl::BluetoothAudioSinkClientInterface(
              aidl::le_audio::LeAudioSinkTransport::instance_broadcast_);
      if (!aidl::le_audio::LeAudioSinkTransport::interface_broadcast_
               ->IsValid()) {
        LOG(WARNING) << __func__
                     << ": BluetoothAudio HAL for Le Audio is invalid?!";
        delete aidl::le_audio::LeAudioSinkTransport::interface_broadcast_;
        aidl::le_audio::LeAudioSinkTransport::interface_broadcast_ = nullptr;
        delete aidl::le_audio::LeAudioSinkTransport::instance_broadcast_;
        aidl::le_audio::LeAudioSinkTransport::instance_broadcast_ = nullptr;
        delete sink;
        sink = nullptr;

        return nullptr;
      }
    }
  }

  return sink;
}

bool LeAudioClientInterface::IsUnicastSinkAcquired() {
  return unicast_sink_ != nullptr;
}
bool LeAudioClientInterface::IsBroadcastSinkAcquired() {
  return broadcast_sink_ != nullptr;
}

bool LeAudioClientInterface::ReleaseSink(LeAudioClientInterface::Sink* sink) {
  if (sink != unicast_sink_ && sink != broadcast_sink_) {
    LOG(WARNING) << __func__ << ", can't release not acquired sink";
    return false;
  }

  if ((hidl::le_audio::LeAudioSinkTransport::interface &&
       hidl::le_audio::LeAudioSinkTransport::instance) ||
      (aidl::le_audio::LeAudioSinkTransport::interface_unicast_ &&
       aidl::le_audio::LeAudioSinkTransport::instance_unicast_) ||
      (aidl::le_audio::LeAudioSinkTransport::interface_broadcast_ &&
       aidl::le_audio::LeAudioSinkTransport::instance_broadcast_))
    sink->Cleanup();

  if (sink == unicast_sink_) {
    delete (unicast_sink_);
    unicast_sink_ = nullptr;
  } else if (sink == broadcast_sink_) {
    delete (broadcast_sink_);
    broadcast_sink_ = nullptr;
  }

  return true;
}

LeAudioClientInterface::Source* LeAudioClientInterface::GetSource(
    StreamCallbacks stream_cb,
    bluetooth::common::MessageLoopThread* message_loop) {
  if (source_ == nullptr) {
    source_ = new Source();
  } else {
    LOG(WARNING) << __func__ << ", Source is already acquired";
    return nullptr;
  }

  LOG(INFO) << __func__;

  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    hidl::SessionType_2_1 session_type =
        hidl::SessionType_2_1::LE_AUDIO_SOFTWARE_DECODED_DATAPATH;
    if (CodecManager::GetInstance()->GetCodecLocation() !=
        CodecLocation::HOST) {
      session_type =
          hidl::SessionType_2_1::LE_AUDIO_HARDWARE_OFFLOAD_DECODING_DATAPATH;
    }

    hidl::le_audio::LeAudioSourceTransport::instance =
        new hidl::le_audio::LeAudioSourceTransport(session_type,
                                                   std::move(stream_cb));
    hidl::le_audio::LeAudioSourceTransport::interface =
        new hidl::BluetoothAudioSourceClientInterface(
            hidl::le_audio::LeAudioSourceTransport::instance, message_loop);
    if (!hidl::le_audio::LeAudioSourceTransport::interface->IsValid()) {
      LOG(WARNING) << __func__
                   << ": BluetoothAudio HAL for Le Audio is invalid?!";
      delete hidl::le_audio::LeAudioSourceTransport::interface;
      hidl::le_audio::LeAudioSourceTransport::interface = nullptr;
      delete hidl::le_audio::LeAudioSourceTransport::instance;
      hidl::le_audio::LeAudioSourceTransport::instance = nullptr;
      delete source_;
      source_ = nullptr;

      return nullptr;
    }
  } else {
    aidl::SessionType session_type =
        aidl::SessionType::LE_AUDIO_SOFTWARE_DECODING_DATAPATH;
    if (CodecManager::GetInstance()->GetCodecLocation() !=
        CodecLocation::HOST) {
      session_type =
          aidl::SessionType::LE_AUDIO_HARDWARE_OFFLOAD_DECODING_DATAPATH;
    }

    aidl::le_audio::LeAudioSourceTransport::instance =
        new aidl::le_audio::LeAudioSourceTransport(session_type,
                                                   std::move(stream_cb));
    aidl::le_audio::LeAudioSourceTransport::interface =
        new aidl::BluetoothAudioSourceClientInterface(
            aidl::le_audio::LeAudioSourceTransport::instance);
    if (!aidl::le_audio::LeAudioSourceTransport::interface->IsValid()) {
      LOG(WARNING) << __func__
                   << ": BluetoothAudio HAL for Le Audio is invalid?!";
      delete aidl::le_audio::LeAudioSourceTransport::interface;
      aidl::le_audio::LeAudioSourceTransport::interface = nullptr;
      delete aidl::le_audio::LeAudioSourceTransport::instance;
      aidl::le_audio::LeAudioSourceTransport::instance = nullptr;
      delete source_;
      source_ = nullptr;

      return nullptr;
    }
  }

  return source_;
}

bool LeAudioClientInterface::IsSourceAcquired() { return source_ != nullptr; }

bool LeAudioClientInterface::ReleaseSource(
    LeAudioClientInterface::Source* source) {
  if (source != source_) {
    LOG(WARNING) << __func__ << ", can't release not acquired source";
    return false;
  }

  if ((hidl::le_audio::LeAudioSourceTransport::interface &&
       hidl::le_audio::LeAudioSourceTransport::instance) ||
      (aidl::le_audio::LeAudioSourceTransport::interface &&
       aidl::le_audio::LeAudioSourceTransport::instance))
    source->Cleanup();

  delete (source_);
  source_ = nullptr;

  return true;
}

void LeAudioClientInterface::SetAllowedDsaModes(DsaModes dsa_modes) {
  if (!IS_FLAG_ENABLED(leaudio_dynamic_spatial_audio)) {
    return;
  }

  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::AIDL) {
    if (aidl::le_audio::LeAudioSinkTransport::interface_unicast_ == nullptr ||
        aidl::le_audio::LeAudioSinkTransport::instance_unicast_ == nullptr) {
      LOG(WARNING) << __func__ << ": LeAudioSourceTransport::interface is null";
      return;
    }

    std::vector<LatencyMode> latency_modes = {LatencyMode::FREE};
    for (auto dsa_mode : dsa_modes) {
      switch (dsa_mode) {
        case DsaMode::DISABLED:
        // Already added
          break;
        case DsaMode::ACL:
          latency_modes.push_back(LatencyMode::LOW_LATENCY);
          break;
        case DsaMode::ISO_SW:
          latency_modes.push_back(LatencyMode::DYNAMIC_SPATIAL_AUDIO_SOFTWARE);
          break;
        case DsaMode::ISO_HW:
          latency_modes.push_back(LatencyMode::DYNAMIC_SPATIAL_AUDIO_HARDWARE);
          break;
        default:
          LOG(WARNING) << "Unsupported latency mode ignored: " << (int)dsa_mode;
          break;
      }
    }
    aidl::le_audio::LeAudioSinkTransport::interface_unicast_
        ->SetAllowedLatencyModes(latency_modes);
  }
}

}  // namespace le_audio
}  // namespace audio
}  // namespace bluetooth
