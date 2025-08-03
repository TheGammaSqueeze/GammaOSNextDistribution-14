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

#define LOG_TAG "BTAudioClientHIDL"

#include "client_interface_hidl.h"

#include <android/hardware/bluetooth/audio/2.0/IBluetoothAudioPort.h>
#include <base/logging.h>
#include <hidl/MQDescriptor.h>

#include <future>
#include <vector>

#include "common/stop_watch_legacy.h"
#include "hal_version_manager.h"
#include "include/check.h"

namespace bluetooth {
namespace audio {
namespace hidl {

using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::audio::common::V5_0::SourceMetadata;
using ::android::hardware::bluetooth::audio::V2_0::IBluetoothAudioPort;
using ::bluetooth::common::StopWatchLegacy;

using DataMQ = ::android::hardware::MessageQueue<
    uint8_t, ::android::hardware::kSynchronizedReadWrite>;

static constexpr int kDefaultDataReadTimeoutMs = 10;       // 10 ms
static constexpr int kDefaultDataWriteTimeoutMs = 10;      // 10 ms
static constexpr int kDefaultDataReadPollIntervalMs = 1;   // non-blocking poll
static constexpr int kDefaultDataWritePollIntervalMs = 1;  // non-blocking poll

std::ostream& operator<<(std::ostream& os, const BluetoothAudioCtrlAck& ack) {
  switch (ack) {
    case BluetoothAudioCtrlAck::SUCCESS_FINISHED:
      return os << "SUCCESS_FINISHED";
    case BluetoothAudioCtrlAck::PENDING:
      return os << "PENDING";
    case BluetoothAudioCtrlAck::FAILURE_UNSUPPORTED:
      return os << "FAILURE_UNSUPPORTED";
    case BluetoothAudioCtrlAck::FAILURE_BUSY:
      return os << "FAILURE_BUSY";
    case BluetoothAudioCtrlAck::FAILURE_DISCONNECTING:
      return os << "FAILURE_DISCONNECTING";
    case BluetoothAudioCtrlAck::FAILURE:
      return os << "FAILURE";
    default:
      return os << "UNDEFINED " << static_cast<int8_t>(ack);
  }
}

class BluetoothAudioPortImpl : public IBluetoothAudioPort {
 public:
  BluetoothAudioPortImpl(IBluetoothTransportInstance* transport_instance,
                         const android::sp<IBluetoothAudioProvider>& provider)
      : transport_instance_(transport_instance), provider_(provider) {}

  Return<void> startStream() override {
    StopWatchLegacy stop_watch(__func__);
    BluetoothAudioCtrlAck ack = transport_instance_->StartRequest();
    if (ack != BluetoothAudioCtrlAck::PENDING) {
      auto hidl_retval =
          provider_->streamStarted(BluetoothAudioCtrlAckToHalStatus(ack));
      if (!hidl_retval.isOk()) {
        LOG(ERROR) << __func__ << ": BluetoothAudioHal failure: "
                   << hidl_retval.description();
      }
    }
    return Void();
  }

  Return<void> suspendStream() override {
    StopWatchLegacy stop_watch(__func__);
    BluetoothAudioCtrlAck ack = transport_instance_->SuspendRequest();
    if (ack != BluetoothAudioCtrlAck::PENDING) {
      auto hidl_retval =
          provider_->streamSuspended(BluetoothAudioCtrlAckToHalStatus(ack));
      if (!hidl_retval.isOk()) {
        LOG(ERROR) << __func__ << ": BluetoothAudioHal failure: "
                   << hidl_retval.description();
      }
    }
    return Void();
  }

  Return<void> stopStream() override {
    StopWatchLegacy stop_watch(__func__);
    transport_instance_->StopRequest();
    return Void();
  }

  Return<void> getPresentationPosition(
      getPresentationPosition_cb _hidl_cb) override {
    StopWatchLegacy stop_watch(__func__);
    uint64_t remote_delay_report_ns;
    uint64_t total_bytes_read;
    timespec data_position;
    bool retval = transport_instance_->GetPresentationPosition(
        &remote_delay_report_ns, &total_bytes_read, &data_position);

    TimeSpec transmittedOctetsTimeStamp;
    if (retval) {
      transmittedOctetsTimeStamp = timespec_convert_to_hal(data_position);
    } else {
      remote_delay_report_ns = 0;
      total_bytes_read = 0;
      transmittedOctetsTimeStamp = {};
    }
    VLOG(2) << __func__ << ": result=" << retval
            << ", delay=" << remote_delay_report_ns
            << ", data=" << total_bytes_read
            << " byte(s), timestamp=" << toString(transmittedOctetsTimeStamp);
    _hidl_cb((retval ? BluetoothAudioStatus::SUCCESS
                     : BluetoothAudioStatus::FAILURE),
             remote_delay_report_ns, total_bytes_read,
             transmittedOctetsTimeStamp);
    return Void();
  }

  Return<void> updateMetadata(const SourceMetadata& sourceMetadata) override {
    StopWatchLegacy stop_watch(__func__);
    LOG(INFO) << __func__ << ": " << sourceMetadata.tracks.size()
              << " track(s)";
    // refer to StreamOut.impl.h within Audio HAL (AUDIO_HAL_VERSION_5_0)
    std::vector<playback_track_metadata> metadata_vec;
    metadata_vec.reserve(sourceMetadata.tracks.size());
    for (const auto& metadata : sourceMetadata.tracks) {
      metadata_vec.push_back({
          .usage = static_cast<audio_usage_t>(metadata.usage),
          .content_type =
              static_cast<audio_content_type_t>(metadata.contentType),
          .gain = metadata.gain,
      });
    }
    const source_metadata_t source_metadata = {
        .track_count = metadata_vec.size(), .tracks = metadata_vec.data()};
    transport_instance_->MetadataChanged(source_metadata);
    return Void();
  }

 private:
  IBluetoothTransportInstance* transport_instance_;
  const android::sp<IBluetoothAudioProvider> provider_;
  TimeSpec timespec_convert_to_hal(const timespec& ts) {
    return {.tvSec = static_cast<uint64_t>(ts.tv_sec),
            .tvNSec = static_cast<uint64_t>(ts.tv_nsec)};
  }
};

class BluetoothAudioDeathRecipient
    : public ::android::hardware::hidl_death_recipient {
 public:
  BluetoothAudioDeathRecipient(
      BluetoothAudioClientInterface* clientif,
      bluetooth::common::MessageLoopThread* message_loop)
      : bluetooth_audio_clientif_(clientif), message_loop_(message_loop) {}
  void serviceDied(
      uint64_t /*cookie*/,
      const ::android::wp<::android::hidl::base::V1_0::IBase>& /*who*/)
      override {
    LOG(WARNING) << __func__ << ": restarting connection with new Audio Hal";
    if (bluetooth_audio_clientif_ != nullptr && message_loop_ != nullptr) {
      // restart the session on the correct thread
      message_loop_->DoInThread(
          FROM_HERE,
          base::BindOnce(
              &BluetoothAudioClientInterface::RenewAudioProviderAndSession,
              base::Unretained(bluetooth_audio_clientif_)));
    } else {
      LOG(ERROR) << __func__ << ": BluetoothAudioClientInterface corrupted";
    }
  }

 private:
  BluetoothAudioClientInterface* bluetooth_audio_clientif_;
  bluetooth::common::MessageLoopThread* message_loop_;
};

// Constructs an BluetoothAudioClientInterface to communicate to
// BluetoothAudio HAL. |message_loop| is the thread where callbacks are
// invoked.
BluetoothAudioClientInterface::BluetoothAudioClientInterface(
    android::sp<BluetoothAudioDeathRecipient> death_recipient,
    IBluetoothTransportInstance* instance)
    : provider_(nullptr),
      provider_2_1_(nullptr),
      session_started_(false),
      mDataMQ(nullptr),
      transport_(instance) {
  death_recipient_ = death_recipient;
}

std::vector<AudioCapabilities>
BluetoothAudioClientInterface::GetAudioCapabilities() const {
  return capabilities_;
}

std::vector<AudioCapabilities_2_1>
BluetoothAudioClientInterface::GetAudioCapabilities_2_1() const {
  return capabilities_2_1_;
}

std::vector<AudioCapabilities>
BluetoothAudioClientInterface::GetAudioCapabilities(SessionType session_type) {
  std::vector<AudioCapabilities> capabilities(0);

  if (HalVersionManager::GetHalVersion() ==
      BluetoothAudioHalVersion::VERSION_UNAVAILABLE) {
    LOG(ERROR) << __func__ << ", can't get capability from unknown factory";
    return capabilities;
  }

  android::sp<IBluetoothAudioProvidersFactory_2_0> providersFactory =
      HalVersionManager::GetProvidersFactory_2_0();
  CHECK(providersFactory != nullptr)
      << "IBluetoothAudioProvidersFactory::getService() failed";

  auto getProviderCapabilities_cb =
      [&capabilities](const hidl_vec<AudioCapabilities>& audioCapabilities) {
        for (auto capability : audioCapabilities) {
          capabilities.push_back(capability);
        }
      };
  auto hidl_retval = providersFactory->getProviderCapabilities(
      session_type, getProviderCapabilities_cb);
  if (!hidl_retval.isOk()) {
    LOG(FATAL) << __func__
               << ": BluetoothAudioHal::getProviderCapabilities failure: "
               << hidl_retval.description();
  }
  return capabilities;
}

std::vector<AudioCapabilities_2_1>
BluetoothAudioClientInterface::GetAudioCapabilities_2_1(
    SessionType_2_1 session_type_2_1) {
  std::vector<AudioCapabilities_2_1> capabilities_2_1(0);
  if (HalVersionManager::GetHalVersion() !=
      BluetoothAudioHalVersion::VERSION_2_1) {
    LOG(ERROR) << __func__ << ", can't get capability for HAL 2.1";
    return capabilities_2_1;
  }

  android::sp<IBluetoothAudioProvidersFactory_2_1> providersFactory =
      HalVersionManager::GetProvidersFactory_2_1();
  CHECK(providersFactory != nullptr)
      << "IBluetoothAudioProvidersFactory::getService() failed";

  auto getProviderCapabilities_cb =
      [&capabilities_2_1](
          const hidl_vec<AudioCapabilities_2_1>& audioCapabilities_2_1) {
        for (auto capability_2_1 : audioCapabilities_2_1) {
          capabilities_2_1.push_back(capability_2_1);
        }
      };
  auto hidl_retval = providersFactory->getProviderCapabilities_2_1(
      session_type_2_1, getProviderCapabilities_cb);
  if (!hidl_retval.isOk()) {
    LOG(FATAL) << __func__
               << ": BluetoothAudioHal::getProviderCapabilities failure: "
               << hidl_retval.description();
  }
  return capabilities_2_1;
}

void BluetoothAudioClientInterface::FetchAudioProvider() {
  if (provider_ != nullptr) {
    LOG(WARNING) << __func__ << ": reflash";
  }

  android::sp<IBluetoothAudioProvidersFactory_2_0> providersFactory =
      HalVersionManager::GetProvidersFactory_2_0();
  CHECK(providersFactory != nullptr)
      << "IBluetoothAudioProvidersFactory::getService() failed";

  auto getProviderCapabilities_cb =
      [&capabilities = this->capabilities_](
          const hidl_vec<AudioCapabilities>& audioCapabilities) {
        capabilities.clear();
        for (auto capability : audioCapabilities) {
          capabilities.push_back(capability);
        }
      };
  auto hidl_retval = providersFactory->getProviderCapabilities(
      transport_->GetSessionType(), getProviderCapabilities_cb);
  if (!hidl_retval.isOk()) {
    LOG(FATAL) << __func__
               << ": BluetoothAudioHal::getProviderCapabilities failure: "
               << hidl_retval.description();
    return;
  }
  if (capabilities_.empty()) {
    LOG(WARNING) << __func__
                 << ": SessionType=" << toString(transport_->GetSessionType())
                 << " Not supported by BluetoothAudioHal";
    return;
  }
  LOG(INFO) << __func__ << ": BluetoothAudioHal SessionType="
            << toString(transport_->GetSessionType()) << " has "
            << capabilities_.size() << " AudioCapabilities";

  std::promise<void> openProvider_promise;
  auto openProvider_future = openProvider_promise.get_future();
  auto openProvider_cb =
      [&provider_ = this->provider_, &openProvider_promise](
          BluetoothAudioStatus status,
          const android::sp<IBluetoothAudioProvider>& provider) {
        LOG(INFO) << "openProvider_cb(" << toString(status) << ")";
        if (status == BluetoothAudioStatus::SUCCESS) {
          provider_ = provider;
        }
        ALOGE_IF(!provider_, "Failed to open BluetoothAudio provider");
        openProvider_promise.set_value();
      };
  hidl_retval = providersFactory->openProvider(transport_->GetSessionType(),
                                               openProvider_cb);
  openProvider_future.get();
  if (!hidl_retval.isOk()) {
    LOG(FATAL) << __func__ << ": BluetoothAudioHal::openProvider failure: "
               << hidl_retval.description();
  }
  CHECK(provider_ != nullptr);

  if (!provider_->linkToDeath(death_recipient_, 0).isOk()) {
    LOG(FATAL) << __func__ << ": BluetoothAudioDeathRecipient failure: "
               << hidl_retval.description();
  }

  LOG(INFO) << "IBluetoothAudioProvidersFactory::openProvider() returned "
            << provider_.get()
            << (provider_->isRemote() ? " (remote)" : " (local)");
}

void BluetoothAudioClientInterface::FetchAudioProvider_2_1() {
  if (provider_2_1_ != nullptr) {
    LOG(WARNING) << __func__ << ": reflash";
  }

  android::sp<IBluetoothAudioProvidersFactory_2_1> providersFactory =
      HalVersionManager::GetProvidersFactory_2_1();
  CHECK(providersFactory != nullptr)
      << "IBluetoothAudioProvidersFactory_2_1::getService() failed";

  auto getProviderCapabilities_cb =
      [&capabilities_2_1 = this->capabilities_2_1_](
          const hidl_vec<AudioCapabilities_2_1>& audioCapabilities_2_1) {
        capabilities_2_1.clear();
        for (auto capability_2_1 : audioCapabilities_2_1) {
          capabilities_2_1.push_back(capability_2_1);
        }
      };
  auto hidl_retval = providersFactory->getProviderCapabilities_2_1(
      transport_->GetSessionType_2_1(), getProviderCapabilities_cb);
  if (!hidl_retval.isOk()) {
    LOG(FATAL) << __func__
               << ": BluetoothAudioHal::getProviderCapabilities failure: "
               << hidl_retval.description();
    return;
  }
  if (capabilities_2_1_.empty()) {
    LOG(WARNING) << __func__ << ": SessionType="
                 << toString(transport_->GetSessionType_2_1())
                 << " Not supported by BluetoothAudioHal";
    return;
  }
  LOG(INFO) << __func__ << ": BluetoothAudioHal SessionType="
            << toString(transport_->GetSessionType_2_1()) << " has "
            << capabilities_2_1_.size() << " AudioCapabilities";

  std::promise<void> openProvider_promise;
  auto openProvider_future = openProvider_promise.get_future();
  auto openProvider_cb =
      [&provider_2_1_ = this->provider_2_1_, &openProvider_promise](
          BluetoothAudioStatus status,
          const android::sp<IBluetoothAudioProvider_2_1>& provider_2_1) {
        LOG(INFO) << "openProvider_cb(" << toString(status) << ")";
        if (status == BluetoothAudioStatus::SUCCESS) {
          provider_2_1_ = provider_2_1;
        }
        ALOGE_IF(!provider_2_1_, "Failed to open BluetoothAudio provider_2_1");
        openProvider_promise.set_value();
      };
  hidl_retval = providersFactory->openProvider_2_1(
      transport_->GetSessionType_2_1(), openProvider_cb);
  openProvider_future.get();
  if (!hidl_retval.isOk()) {
    LOG(FATAL) << __func__ << ": BluetoothAudioHal::openProvider failure: "
               << hidl_retval.description();
  }
  CHECK(provider_2_1_ != nullptr);

  if (!provider_2_1_->linkToDeath(death_recipient_, 0).isOk()) {
    LOG(FATAL) << __func__ << ": BluetoothAudioDeathRecipient failure: "
               << hidl_retval.description();
  }

  LOG(INFO) << "IBluetoothAudioProvidersFactory::openProvider() returned "
            << provider_2_1_.get()
            << (provider_2_1_->isRemote() ? " (remote)" : " (local)");
}

BluetoothAudioSinkClientInterface::BluetoothAudioSinkClientInterface(
    IBluetoothSinkTransportInstance* sink,
    bluetooth::common::MessageLoopThread* message_loop)
    : BluetoothAudioClientInterface{new BluetoothAudioDeathRecipient(
                                        this, message_loop),
                                    sink},
      sink_(sink) {
  if (HalVersionManager::GetHalVersion() ==
      BluetoothAudioHalVersion::VERSION_UNAVAILABLE) {
    return;
  }

  if ((HalVersionManager::GetHalVersion() ==
       BluetoothAudioHalVersion::VERSION_2_1) &&
      (sink_->GetSessionType_2_1() != SessionType_2_1::UNKNOWN)) {
    FetchAudioProvider_2_1();
    return;
  }
  FetchAudioProvider();
}

BluetoothAudioSinkClientInterface::~BluetoothAudioSinkClientInterface() {
  if (provider_ != nullptr) {
    auto hidl_retval = provider_->unlinkToDeath(death_recipient_);
    if (!hidl_retval.isOk()) {
      LOG(FATAL) << __func__ << ": BluetoothAudioDeathRecipient failure: "
                 << hidl_retval.description();
    }
  }
  if (provider_2_1_ != nullptr) {
    auto hidl_retval = provider_2_1_->unlinkToDeath(death_recipient_);
    if (!hidl_retval.isOk()) {
      LOG(FATAL) << __func__ << ": BluetoothAudioDeathRecipient failure: "
                 << hidl_retval.description();
    }
  }
}

BluetoothAudioSourceClientInterface::BluetoothAudioSourceClientInterface(
    IBluetoothSourceTransportInstance* source,
    bluetooth::common::MessageLoopThread* message_loop)
    : BluetoothAudioClientInterface{new BluetoothAudioDeathRecipient(
                                        this, message_loop),
                                    source},
      source_(source) {
  if (HalVersionManager::GetHalVersion() ==
      BluetoothAudioHalVersion::VERSION_UNAVAILABLE) {
    return;
  }

  if ((HalVersionManager::GetHalVersion() ==
       BluetoothAudioHalVersion::VERSION_2_1) &&
      (source_->GetSessionType_2_1() != SessionType_2_1::UNKNOWN)) {
    FetchAudioProvider_2_1();
    return;
  }
  FetchAudioProvider();
}

BluetoothAudioSourceClientInterface::~BluetoothAudioSourceClientInterface() {
  if (provider_ != nullptr) {
    auto hidl_retval = provider_->unlinkToDeath(death_recipient_);
    if (!hidl_retval.isOk()) {
      LOG(FATAL) << __func__ << ": BluetoothAudioDeathRecipient failure: "
                 << hidl_retval.description();
    }
  }
  if (provider_2_1_ != nullptr) {
    auto hidl_retval = provider_2_1_->unlinkToDeath(death_recipient_);
    if (!hidl_retval.isOk()) {
      LOG(FATAL) << __func__ << ": BluetoothAudioDeathRecipient failure: "
                 << hidl_retval.description();
    }
  }
}

bool BluetoothAudioClientInterface::UpdateAudioConfig(
    const AudioConfiguration& audio_config) {
  bool is_software_session =
      (transport_->GetSessionType() ==
           SessionType::A2DP_SOFTWARE_ENCODING_DATAPATH ||
       transport_->GetSessionType() ==
           SessionType::HEARING_AID_SOFTWARE_ENCODING_DATAPATH);
  bool is_offload_session = (transport_->GetSessionType() ==
                             SessionType::A2DP_HARDWARE_OFFLOAD_DATAPATH);
  auto audio_config_discriminator = audio_config.getDiscriminator();
  bool is_software_audio_config =
      (is_software_session &&
       audio_config_discriminator ==
           AudioConfiguration::hidl_discriminator::pcmConfig);
  bool is_offload_audio_config =
      (is_offload_session &&
       audio_config_discriminator ==
           AudioConfiguration::hidl_discriminator::codecConfig);
  if (!is_software_audio_config && !is_offload_audio_config) {
    return false;
  }
  transport_->UpdateAudioConfiguration(audio_config);
  return true;
}

bool BluetoothAudioClientInterface::UpdateAudioConfig_2_1(
    const AudioConfiguration_2_1& audio_config_2_1) {
  bool is_software_session =
      (transport_->GetSessionType_2_1() ==
           SessionType_2_1::A2DP_SOFTWARE_ENCODING_DATAPATH ||
       transport_->GetSessionType_2_1() ==
           SessionType_2_1::HEARING_AID_SOFTWARE_ENCODING_DATAPATH ||
       transport_->GetSessionType_2_1() ==
           SessionType_2_1::LE_AUDIO_SOFTWARE_ENCODING_DATAPATH ||
       transport_->GetSessionType_2_1() ==
           SessionType_2_1::LE_AUDIO_SOFTWARE_DECODED_DATAPATH);
  bool is_offload_session = (transport_->GetSessionType_2_1() ==
                             SessionType_2_1::A2DP_HARDWARE_OFFLOAD_DATAPATH);
  auto audio_config_discriminator = audio_config_2_1.getDiscriminator();
  bool is_software_audio_config =
      (is_software_session &&
       audio_config_discriminator ==
           AudioConfiguration_2_1::hidl_discriminator::pcmConfig);
  bool is_offload_audio_config =
      (is_offload_session &&
       audio_config_discriminator ==
           AudioConfiguration_2_1::hidl_discriminator::codecConfig);
  if (!is_software_audio_config && !is_offload_audio_config) {
    return false;
  }
  transport_->UpdateAudioConfiguration_2_1(audio_config_2_1);
  return true;
}

int BluetoothAudioClientInterface::StartSession() {
  std::lock_guard<std::mutex> guard(internal_mutex_);
  if (provider_ == nullptr) {
    LOG(ERROR) << __func__ << ": BluetoothAudioHal nullptr";
    session_started_ = false;
    return -EINVAL;
  }
  if (session_started_) {
    LOG(ERROR) << __func__ << ": session started already";
    return -EBUSY;
  }

  android::sp<IBluetoothAudioPort> stack_if =
      new BluetoothAudioPortImpl(transport_, provider_);

  std::unique_ptr<DataMQ> tempDataMQ;
  BluetoothAudioStatus session_status;

  std::promise<void> hidl_startSession_promise;
  auto hidl_startSession_future = hidl_startSession_promise.get_future();
  auto hidl_cb = [&session_status, &tempDataMQ, &hidl_startSession_promise](
                     BluetoothAudioStatus status,
                     const DataMQ::Descriptor& dataMQ) {
    LOG(INFO) << "startSession_cb(" << toString(status) << ")";
    session_status = status;
    if (status == BluetoothAudioStatus::SUCCESS && dataMQ.isHandleValid()) {
      tempDataMQ.reset(new DataMQ(dataMQ));
    }
    hidl_startSession_promise.set_value();
  };
  auto hidl_retval = provider_->startSession(
      stack_if, transport_->GetAudioConfiguration(), hidl_cb);
  hidl_startSession_future.get();
  if (!hidl_retval.isOk()) {
    LOG(FATAL) << __func__
               << ": BluetoothAudioHal failure: " << hidl_retval.description();
    return -EPROTO;
  }

  if (tempDataMQ && tempDataMQ->isValid()) {
    mDataMQ = std::move(tempDataMQ);
  } else if (transport_->GetSessionType() ==
                 SessionType::A2DP_HARDWARE_OFFLOAD_DATAPATH &&
             session_status == BluetoothAudioStatus::SUCCESS) {
    transport_->ResetPresentationPosition();
    session_started_ = true;
    return 0;
  }
  if (mDataMQ && mDataMQ->isValid()) {
    transport_->ResetPresentationPosition();
    session_started_ = true;
    return 0;
  } else {
    ALOGE_IF(!mDataMQ, "Failed to obtain audio data path");
    ALOGE_IF(mDataMQ && !mDataMQ->isValid(), "Audio data path is invalid");
    session_started_ = false;
    return -EIO;
  }
}

int BluetoothAudioClientInterface::StartSession_2_1() {
  std::lock_guard<std::mutex> guard(internal_mutex_);
  if (provider_2_1_ == nullptr) {
    LOG(ERROR) << __func__ << ": BluetoothAudioHal nullptr";
    session_started_ = false;
    return -EINVAL;
  }
  if (session_started_) {
    LOG(ERROR) << __func__ << ": session started already";
    return -EBUSY;
  }

  android::sp<IBluetoothAudioPort> stack_if =
      new BluetoothAudioPortImpl(transport_, provider_2_1_);

  std::unique_ptr<DataMQ> tempDataMQ;
  BluetoothAudioStatus session_status;

  std::promise<void> hidl_startSession_promise;
  auto hidl_startSession_future = hidl_startSession_promise.get_future();
  auto hidl_cb = [&session_status, &tempDataMQ, &hidl_startSession_promise](
                     BluetoothAudioStatus status,
                     const DataMQ::Descriptor& dataMQ) {
    LOG(INFO) << "startSession_cb(" << toString(status) << ")";
    session_status = status;
    if (status == BluetoothAudioStatus::SUCCESS && dataMQ.isHandleValid()) {
      tempDataMQ.reset(new DataMQ(dataMQ));
    }
    hidl_startSession_promise.set_value();
  };
  auto hidl_retval = provider_2_1_->startSession_2_1(
      stack_if, transport_->GetAudioConfiguration_2_1(), hidl_cb);
  hidl_startSession_future.get();
  if (!hidl_retval.isOk()) {
    LOG(FATAL) << __func__
               << ": BluetoothAudioHal failure: " << hidl_retval.description();
    return -EPROTO;
  }

  if (tempDataMQ && tempDataMQ->isValid()) {
    mDataMQ = std::move(tempDataMQ);
  } else if (transport_->GetSessionType_2_1() ==
                 SessionType_2_1::A2DP_HARDWARE_OFFLOAD_DATAPATH &&
             session_status == BluetoothAudioStatus::SUCCESS) {
    transport_->ResetPresentationPosition();
    session_started_ = true;
    return 0;
  }
  if (mDataMQ && mDataMQ->isValid()) {
    transport_->ResetPresentationPosition();
    session_started_ = true;
    return 0;
  } else {
    ALOGE_IF(!mDataMQ, "Failed to obtain audio data path");
    ALOGE_IF(mDataMQ && !mDataMQ->isValid(), "Audio data path is invalid");
    session_started_ = false;
    return -EIO;
  }
}

void BluetoothAudioClientInterface::StreamStarted(
    const BluetoothAudioCtrlAck& ack) {
  if (ack == BluetoothAudioCtrlAck::PENDING) {
    LOG(INFO) << __func__ << ": " << ack << " ignored";
    return;
  }
  BluetoothAudioStatus status = BluetoothAudioCtrlAckToHalStatus(ack);

  ::android::hardware::Return<void> hidl_retval;
  if (provider_2_1_ != nullptr) {
    hidl_retval = provider_2_1_->streamStarted(status);
  } else if (provider_ != nullptr) {
    hidl_retval = provider_->streamStarted(status);
  } else {
    LOG(ERROR) << __func__ << ": BluetoothAudioHal nullptr";
    return;
  }

  if (!hidl_retval.isOk()) {
    LOG(ERROR) << __func__
               << ": BluetoothAudioHal failure: " << hidl_retval.description();
  }
}

void BluetoothAudioClientInterface::StreamSuspended(
    const BluetoothAudioCtrlAck& ack) {
  if (ack == BluetoothAudioCtrlAck::PENDING) {
    LOG(INFO) << __func__ << ": " << ack << " ignored";
    return;
  }
  BluetoothAudioStatus status = BluetoothAudioCtrlAckToHalStatus(ack);

  ::android::hardware::Return<void> hidl_retval;
  if (provider_2_1_ != nullptr) {
    hidl_retval = provider_2_1_->streamSuspended(status);
  } else if (provider_ != nullptr) {
    hidl_retval = provider_->streamSuspended(status);
  } else {
    LOG(ERROR) << __func__ << ": BluetoothAudioHal nullptr";
    return;
  }

  if (!hidl_retval.isOk()) {
    LOG(ERROR) << __func__
               << ": BluetoothAudioHal failure: " << hidl_retval.description();
  }
}

int BluetoothAudioClientInterface::EndSession() {
  std::lock_guard<std::mutex> guard(internal_mutex_);
  if (!session_started_) {
    LOG(INFO) << __func__ << ": session ended already";
    return 0;
  }

  session_started_ = false;
  mDataMQ = nullptr;

  ::android::hardware::Return<void> hidl_retval;
  if (provider_2_1_ != nullptr) {
    hidl_retval = provider_2_1_->endSession();
  } else if (provider_ != nullptr) {
    hidl_retval = provider_->endSession();
  } else {
    LOG(ERROR) << __func__ << ": BluetoothAudioHal nullptr";
    return -EINVAL;
  }

  if (!hidl_retval.isOk()) {
    LOG(ERROR) << __func__
               << ": BluetoothAudioHal failure: " << hidl_retval.description();
    return -EPROTO;
  }
  return 0;
}

void BluetoothAudioClientInterface::FlushAudioData() {
  if (transport_->GetSessionType_2_1() ==
          SessionType_2_1::LE_AUDIO_HARDWARE_OFFLOAD_ENCODING_DATAPATH ||
      transport_->GetSessionType_2_1() ==
          SessionType_2_1::LE_AUDIO_HARDWARE_OFFLOAD_DECODING_DATAPATH)
    return;

  if (mDataMQ == nullptr || !mDataMQ->isValid()) {
    LOG(WARNING) << __func__ << ", mDataMQ invalid";
    return;
  }
  size_t size = mDataMQ->availableToRead();
  uint8_t p_buf[size];

  if (mDataMQ->read(p_buf, size) != size)
    LOG(WARNING) << __func__ << ", failed to flush data queue!";
}

size_t BluetoothAudioSinkClientInterface::ReadAudioData(uint8_t* p_buf,
                                                        uint32_t len) {
  if (!IsValid()) {
    LOG(ERROR) << __func__ << ": BluetoothAudioHal is not valid";
    return 0;
  }
  if (p_buf == nullptr || len == 0) return 0;

  std::lock_guard<std::mutex> guard(internal_mutex_);

  size_t total_read = 0;
  int timeout_ms = kDefaultDataReadTimeoutMs;
  do {
    if (mDataMQ == nullptr || !mDataMQ->isValid()) break;

    size_t avail_to_read = mDataMQ->availableToRead();
    if (avail_to_read) {
      if (avail_to_read > len - total_read) {
        avail_to_read = len - total_read;
      }
      if (mDataMQ->read(p_buf + total_read, avail_to_read) == 0) {
        LOG(WARNING) << __func__ << ": len=" << len
                     << " total_read=" << total_read << " failed";
        break;
      }
      total_read += avail_to_read;
    } else if (timeout_ms >= kDefaultDataReadPollIntervalMs) {
      std::this_thread::sleep_for(
          std::chrono::milliseconds(kDefaultDataReadPollIntervalMs));
      timeout_ms -= kDefaultDataReadPollIntervalMs;
      continue;
    } else {
      LOG(WARNING) << __func__ << ": " << (len - total_read) << "/" << len
                   << " no data " << (kDefaultDataReadTimeoutMs - timeout_ms)
                   << " ms";
      break;
    }
  } while (total_read < len);

  if (timeout_ms <
          (kDefaultDataReadTimeoutMs - kDefaultDataReadPollIntervalMs) &&
      timeout_ms >= kDefaultDataReadPollIntervalMs) {
    VLOG(1) << __func__ << ": underflow " << len << " -> " << total_read
            << " read " << (kDefaultDataReadTimeoutMs - timeout_ms) << " ms";
  } else {
    VLOG(2) << __func__ << ": " << len << " -> " << total_read << " read";
  }

  sink_->LogBytesRead(total_read);
  return total_read;
}

void BluetoothAudioClientInterface::RenewAudioProviderAndSession() {
  // NOTE: must be invoked on the same thread where this
  // BluetoothAudioClientInterface is running
  auto hal_version = HalVersionManager::GetHalVersion();
  if ((hal_version == BluetoothAudioHalVersion::VERSION_2_1) &&
      (transport_->GetSessionType_2_1() != SessionType_2_1::UNKNOWN)) {
    FetchAudioProvider_2_1();
  } else if (transport_->GetSessionType() != SessionType::UNKNOWN) {
    FetchAudioProvider();
  } else {
    LOG(FATAL) << __func__ << ", cannot renew audio provider";
    return;
  }

  if (session_started_) {
    LOG(INFO) << __func__ << ": Restart the session while audio HAL recovering";
    session_started_ = false;

    if (provider_2_1_ != nullptr)
      StartSession_2_1();
    else
      StartSession();
  }
}

size_t BluetoothAudioSourceClientInterface::WriteAudioData(const uint8_t* p_buf,
                                                           uint32_t len) {
  if (!IsValid()) {
    LOG(ERROR) << __func__ << ": BluetoothAudioHal is not valid";
    return 0;
  }
  if (p_buf == nullptr || len == 0) return 0;

  std::lock_guard<std::mutex> guard(internal_mutex_);

  size_t total_written = 0;
  int timeout_ms = kDefaultDataWriteTimeoutMs;
  do {
    if (mDataMQ == nullptr || !mDataMQ->isValid()) break;

    size_t avail_to_write = mDataMQ->availableToWrite();
    if (avail_to_write) {
      if (avail_to_write > len - total_written) {
        avail_to_write = len - total_written;
      }
      if (mDataMQ->write(p_buf + total_written, avail_to_write) == 0) {
        LOG(WARNING) << __func__ << ": len=" << len
                     << " total_written=" << total_written << " failed";
        break;
      }
      total_written += avail_to_write;
    } else if (timeout_ms >= kDefaultDataWritePollIntervalMs) {
      std::this_thread::sleep_for(
          std::chrono::milliseconds(kDefaultDataWritePollIntervalMs));
      timeout_ms -= kDefaultDataWritePollIntervalMs;
      continue;
    } else {
      LOG(WARNING) << __func__ << ": " << (len - total_written) << "/" << len
                   << " no data " << (kDefaultDataWriteTimeoutMs - timeout_ms)
                   << " ms";
      break;
    }
  } while (total_written < len);

  if (timeout_ms <
          (kDefaultDataWriteTimeoutMs - kDefaultDataWritePollIntervalMs) &&
      timeout_ms >= kDefaultDataWritePollIntervalMs) {
    VLOG(1) << __func__ << ": underflow " << len << " -> " << total_written
            << " read " << (kDefaultDataWriteTimeoutMs - timeout_ms) << " ms";
  } else {
    VLOG(2) << __func__ << ": " << len << " -> " << total_written << " written";
  }

  source_->LogBytesWritten(total_written);
  return total_written;
}

}  // namespace hidl
}  // namespace audio
}  // namespace bluetooth
