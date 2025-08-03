// Copyright (C) 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "DeviceImpl.h"

#include <android-base/logging.h>
#include <android-base/strings.h>
#include <system/audio-hal-enums.h>
#include <utils/RefBase.h>

#include <optional>

#include "AidlTypes.h"
#include "AudioUtil.h"
#include "BusOutputStream.h"
#include "BusStreamProvider.h"
#include "ServiceConfig.h"
#include "StreamOutImpl.h"

using namespace ::android::hardware::audio::common::CPP_VERSION;
using namespace ::android::hardware::audio::CPP_VERSION;

using ::android::wp;

namespace audio_proxy {
namespace service {
namespace {
AudioPatchHandle gNextAudioPatchHandle = 1;

#if MAJOR_VERSION >= 7
std::optional<AidlAudioConfig> toAidlAudioConfig(
    const AudioConfigBase& hidl_config) {
  audio_format_t format = AUDIO_FORMAT_INVALID;
  if (!audio_format_from_string(hidl_config.format.c_str(), &format)) {
    return std::nullopt;
  }

  audio_channel_mask_t channelMask = AUDIO_CHANNEL_INVALID;
  if (!audio_channel_mask_from_string(hidl_config.channelMask.c_str(),
                                      &channelMask)) {
    return std::nullopt;
  }

  AidlAudioConfig aidlConfig = {
      .format = static_cast<AidlAudioFormat>(format),
      .sampleRateHz = static_cast<int32_t>(hidl_config.sampleRateHz),
      .channelMask = static_cast<AidlAudioChannelMask>(channelMask),
      .bufferSizeBytes = 0,
      .latencyMs = 0};

  return aidlConfig;
}

std::optional<int32_t> toAidlAudioOutputFlags(
    const hidl_vec<AudioInOutFlag>& flags) {
  int32_t outputFlags = static_cast<int32_t>(AUDIO_OUTPUT_FLAG_NONE);
  for (const auto& flag : flags) {
    audio_output_flags_t outputFlag = AUDIO_OUTPUT_FLAG_NONE;
    if (audio_output_flag_from_string(flag.c_str(), &outputFlag)) {
      outputFlags |= static_cast<int32_t>(outputFlag);
    } else {
      return std::nullopt;
    }
  }

  return outputFlags;
}

bool checkSourceMetadata(const SourceMetadata& metadata) {
  for (const auto& track : metadata.tracks) {
    audio_usage_t usage;
    if (!audio_usage_from_string(track.usage.c_str(), &usage)) {
      return false;
    }

    audio_content_type_t contentType;
    if (!audio_content_type_from_string(track.contentType.c_str(),
                                        &contentType)) {
      return false;
    }

    audio_channel_mask_t channelMask;
    if (!audio_channel_mask_from_string(track.channelMask.c_str(),
                                        &channelMask)) {
      return false;
    }

    // From types.hal:
    // Tags are set by vendor specific applications and must be prefixed by
    // "VX_". Vendor must namespace their tag names to avoid conflicts. See
    // 'vendorExtension' in audio_policy_configuration.xsd for a formal
    // definition.
    //
    // From audio_policy_configuration.xsd:
    // Vendor extension names must be prefixed by "VX_" to distinguish them from
    // AOSP values. Vendors must namespace their names to avoid conflicts. The
    // namespace part must only use capital latin characters and decimal digits
    // and consist of at least 3 characters.
    for (const auto& tag : track.tags) {
      if (!android::base::StartsWith(tag.c_str(), "VX_")) {
        return false;
      }
    }
  }

  return true;
}

bool checkAudioPortConfig(const AudioPortConfig& config) {
  if (config.base.format.getDiscriminator() ==
      AudioConfigBaseOptional::Format::hidl_discriminator::value) {
    audio_format_t format;
    if (!audio_format_from_string(config.base.format.value().c_str(),
                                  &format)) {
      return false;
    }
  }

  if (config.base.channelMask.getDiscriminator() ==
      AudioConfigBaseOptional::ChannelMask::hidl_discriminator::value) {
    audio_channel_mask_t channelMask;
    if (!audio_channel_mask_from_string(config.base.channelMask.value().c_str(),
                                        &channelMask)) {
      return false;
    }
  }

  if (config.gain.getDiscriminator() ==
      AudioPortConfig::OptionalGain::hidl_discriminator::config) {
    for (const auto& mode : config.gain.config().mode) {
      audio_gain_mode_t gainMode;
      if (!audio_gain_mode_from_string(mode.c_str(), &gainMode)) {
        return false;
      }
    }

    audio_channel_mask_t channelMask;
    if (!audio_channel_mask_from_string(
            config.gain.config().channelMask.c_str(), &channelMask)) {
      return false;
    }
  }

  if (config.ext.getDiscriminator() ==
      AudioPortExtendedInfo::hidl_discriminator::device) {
    audio_devices_t deviceType;
    if (!audio_device_from_string(config.ext.device().deviceType.c_str(),
                                  &deviceType)) {
      return false;
    }
  }

  if (config.ext.getDiscriminator() ==
      AudioPortExtendedInfo::hidl_discriminator::mix) {
    const auto& useCase = config.ext.mix().useCase;
    if (useCase.getDiscriminator() == AudioPortExtendedInfo::AudioPortMixExt::
                                          UseCase::hidl_discriminator::stream) {
      audio_stream_type_t audioStreamType;
      if (!audio_stream_type_from_string(useCase.stream().c_str(),
                                         &audioStreamType)) {
        return false;
      }
    } else {
      audio_source_t audioSource;
      if (!audio_source_from_string(useCase.source().c_str(), &audioSource)) {
        return false;
      }
    }
  }

  return true;
}
#else
AidlAudioConfig toAidlAudioConfig(const AudioConfig& hidl_config) {
  AidlAudioConfig aidlConfig = {
      .format = static_cast<AidlAudioFormat>(hidl_config.format),
      .sampleRateHz = static_cast<int32_t>(hidl_config.sampleRateHz),
      .channelMask = static_cast<AidlAudioChannelMask>(hidl_config.channelMask),
      .bufferSizeBytes = 0,
      .latencyMs = 0};

  return aidlConfig;
}

// Before 7.0, the fields are using enum instead of string. There's no need to
// validate them.
bool checkAudioPortConfig(const AudioPortConfig& config) { return true; }
#endif
}  // namespace

DeviceImpl::DeviceImpl(BusStreamProvider& busStreamProvider,
                       const ServiceConfig& serviceConfig)
    : mBusStreamProvider(busStreamProvider), mServiceConfig(serviceConfig) {}

// Methods from ::android::hardware::audio::V5_0::IDevice follow.
Return<Result> DeviceImpl::initCheck() { return Result::OK; }

Return<Result> DeviceImpl::setMasterVolume(float volume) {
  // software mixer will emulate this ability
  return Result::NOT_SUPPORTED;
}

Return<void> DeviceImpl::getMasterVolume(getMasterVolume_cb _hidl_cb) {
  _hidl_cb(Result::NOT_SUPPORTED, 0.f);
  return Void();
}

Return<Result> DeviceImpl::setMicMute(bool mute) {
  return Result::NOT_SUPPORTED;
}

Return<void> DeviceImpl::getMicMute(getMicMute_cb _hidl_cb) {
  _hidl_cb(Result::NOT_SUPPORTED, false);
  return Void();
}

Return<Result> DeviceImpl::setMasterMute(bool mute) {
  return Result::NOT_SUPPORTED;
}

Return<void> DeviceImpl::getMasterMute(getMasterMute_cb _hidl_cb) {
  _hidl_cb(Result::NOT_SUPPORTED, false);
  return Void();
}

Return<void> DeviceImpl::getInputBufferSize(const AudioConfig& config,
                                            getInputBufferSize_cb _hidl_cb) {
  _hidl_cb(Result::NOT_SUPPORTED, 0);
  return Void();
}

#if MAJOR_VERSION >= 7
template <typename CallbackType>
Return<void> DeviceImpl::openOutputStreamImpl(
    int32_t ioHandle, const DeviceAddress& device, const AudioConfig& config,
    const hidl_vec<AudioInOutFlag>& flags, const SourceMetadata& sourceMetadata,
    CallbackType _hidl_cb) {
  std::optional<AidlAudioConfig> aidlConfig = toAidlAudioConfig(config.base);
  if (!aidlConfig) {
    _hidl_cb(Result::INVALID_ARGUMENTS, nullptr, {});
    return Void();
  }

  std::optional<int32_t> outputFlags = toAidlAudioOutputFlags(flags);
  if (!outputFlags) {
    _hidl_cb(Result::INVALID_ARGUMENTS, nullptr, {});
    return Void();
  }

  if (!checkSourceMetadata(sourceMetadata)) {
    _hidl_cb(Result::INVALID_ARGUMENTS, nullptr, {});
    return Void();
  }

  std::string address;

  // Default device is used for VTS test.
  if (device.deviceType == "AUDIO_DEVICE_OUT_DEFAULT") {
    address = "default";
  } else if (device.deviceType == "AUDIO_DEVICE_OUT_BUS") {
    address = device.address.id();
  } else {
    _hidl_cb(Result::INVALID_ARGUMENTS, nullptr, {});
    return Void();
  }

  const auto configIt = mServiceConfig.streams.find(address);
  if (configIt == mServiceConfig.streams.end()) {
    _hidl_cb(Result::INVALID_ARGUMENTS, nullptr, {});
    return Void();
  }

  std::shared_ptr<BusOutputStream> busOutputStream =
      mBusStreamProvider.openOutputStream(
          address, *aidlConfig, *outputFlags,
          computeBufferSizeBytes(*aidlConfig, configIt->second.bufferSizeMs),
          configIt->second.latencyMs);
  DCHECK(busOutputStream);
  auto streamOut =
      sp<StreamOutImpl>::make(std::move(busOutputStream), config.base);
  mBusStreamProvider.onStreamOutCreated(streamOut);
  _hidl_cb(Result::OK, streamOut, config);
  return Void();
}

Return<void> DeviceImpl::openOutputStream(int32_t ioHandle,
                                          const DeviceAddress& device,
                                          const AudioConfig& config,
                                          const hidl_vec<AudioInOutFlag>& flags,
                                          const SourceMetadata& sourceMetadata,
                                          openOutputStream_cb _hidl_cb) {
  return openOutputStreamImpl(ioHandle, device, config, flags, sourceMetadata,
                              _hidl_cb);
}

Return<void> DeviceImpl::openInputStream(int32_t ioHandle,
                                         const DeviceAddress& device,
                                         const AudioConfig& config,
                                         const hidl_vec<AudioInOutFlag>& flags,
                                         const SinkMetadata& sinkMetadata,
                                         openInputStream_cb _hidl_cb) {
  _hidl_cb(Result::NOT_SUPPORTED, sp<IStreamIn>(), config);
  return Void();
}
#else
Return<void> DeviceImpl::openOutputStream(int32_t ioHandle,
                                          const DeviceAddress& device,
                                          const AudioConfig& config,
                                          hidl_bitfield<AudioOutputFlag> flags,
                                          const SourceMetadata& sourceMetadata,
                                          openOutputStream_cb _hidl_cb) {
  std::string address;
  if (device.device == AudioDevice::OUT_DEFAULT) {
    address = "default";
  } else if (device.device == AudioDevice::OUT_BUS) {
    address = device.busAddress;
  } else {
    _hidl_cb(Result::INVALID_ARGUMENTS, nullptr, {});
    return Void();
  }

  const auto configIt = mServiceConfig.streams.find(address);
  if (configIt == mServiceConfig.streams.end()) {
    _hidl_cb(Result::INVALID_ARGUMENTS, nullptr, {});
    return Void();
  }

  auto aidlConfig = toAidlAudioConfig(config);
  std::shared_ptr<BusOutputStream> busOutputStream =
      mBusStreamProvider.openOutputStream(
          address, aidlConfig, static_cast<int32_t>(flags),
          computeBufferSizeBytes(aidlConfig, configIt->second.bufferSizeMs),
          configIt->second.latencyMs);
  DCHECK(busOutputStream);
  auto streamOut = sp<StreamOutImpl>::make(std::move(busOutputStream), config);
  mBusStreamProvider.onStreamOutCreated(streamOut);
  _hidl_cb(Result::OK, streamOut, config);
  return Void();
}

Return<void> DeviceImpl::openInputStream(int32_t ioHandle,
                                         const DeviceAddress& device,
                                         const AudioConfig& config,
                                         hidl_bitfield<AudioInputFlag> flags,
                                         const SinkMetadata& sinkMetadata,
                                         openInputStream_cb _hidl_cb) {
  _hidl_cb(Result::NOT_SUPPORTED, sp<IStreamIn>(), config);
  return Void();
}
#endif

Return<bool> DeviceImpl::supportsAudioPatches() { return true; }

// Create a do-nothing audio patch.
Return<void> DeviceImpl::createAudioPatch(
    const hidl_vec<AudioPortConfig>& sources,
    const hidl_vec<AudioPortConfig>& sinks, createAudioPatch_cb _hidl_cb) {
  for (const auto& config : sources) {
    if (!checkAudioPortConfig(config)) {
      _hidl_cb(Result::INVALID_ARGUMENTS, 0);
      return Void();
    }
  }

  for (const auto& config : sinks) {
    if (!checkAudioPortConfig(config)) {
      _hidl_cb(Result::INVALID_ARGUMENTS, 0);
      return Void();
    }
  }

  AudioPatchHandle handle = gNextAudioPatchHandle++;
  mAudioPatchHandles.insert(handle);
  _hidl_cb(Result::OK, handle);
  return Void();
}

Return<Result> DeviceImpl::releaseAudioPatch(AudioPatchHandle patch) {
  size_t removed = mAudioPatchHandles.erase(patch);
  return removed > 0 ? Result::OK : Result::INVALID_ARGUMENTS;
}

Return<void> DeviceImpl::getAudioPort(const AudioPort& port,
                                      getAudioPort_cb _hidl_cb) {
  _hidl_cb(Result::NOT_SUPPORTED, port);
  return Void();
}

Return<Result> DeviceImpl::setAudioPortConfig(const AudioPortConfig& config) {
  return Result::NOT_SUPPORTED;
}

Return<void> DeviceImpl::getHwAvSync(getHwAvSync_cb _hidl_cb) {
  _hidl_cb(Result::NOT_SUPPORTED, 0);
  return Void();
}

Return<Result> DeviceImpl::setScreenState(bool turnedOn) {
  return Result::NOT_SUPPORTED;
}

Return<void> DeviceImpl::getParameters(const hidl_vec<ParameterValue>& context,
                                       const hidl_vec<hidl_string>& keys,
                                       getParameters_cb _hidl_cb) {
  _hidl_cb(Result::NOT_SUPPORTED, hidl_vec<ParameterValue>());
  return Void();
}

Return<Result> DeviceImpl::setParameters(
    const hidl_vec<ParameterValue>& context,
    const hidl_vec<ParameterValue>& parameters) {
  return Result::NOT_SUPPORTED;
}

Return<void> DeviceImpl::getMicrophones(getMicrophones_cb _hidl_cb) {
  _hidl_cb(Result::NOT_SUPPORTED, hidl_vec<MicrophoneInfo>());
  return Void();
}

Return<Result> DeviceImpl::setConnectedState(const DeviceAddress& address,
                                             bool connected) {
#if MAJOR_VERSION >= 7
  audio_devices_t deviceType = AUDIO_DEVICE_NONE;
  if (!audio_device_from_string(address.deviceType.c_str(), &deviceType)) {
    return Result::INVALID_ARGUMENTS;
  }

  if (deviceType != AUDIO_DEVICE_OUT_BUS) {
    return Result::NOT_SUPPORTED;
  }

  const auto& busAddress = address.address.id();
#else
  if (address.device != AudioDevice::OUT_BUS) {
    return Result::NOT_SUPPORTED;
  }

  const auto& busAddress = address.busAddress;
#endif

  return mServiceConfig.streams.count(busAddress) > 0 ? Result::OK
                                                      : Result::NOT_SUPPORTED;
}

#if MAJOR_VERSION >= 6
Return<void> DeviceImpl::updateAudioPatch(
    AudioPatchHandle previousPatch, const hidl_vec<AudioPortConfig>& sources,
    const hidl_vec<AudioPortConfig>& sinks, updateAudioPatch_cb _hidl_cb) {
  if (mAudioPatchHandles.erase(previousPatch) == 0) {
    _hidl_cb(Result::INVALID_ARGUMENTS, 0);
    return Void();
  }
  AudioPatchHandle newPatch = gNextAudioPatchHandle++;
  mAudioPatchHandles.insert(newPatch);
  _hidl_cb(Result::OK, newPatch);
  return Void();
}

Return<Result> DeviceImpl::close() {
  return mBusStreamProvider.cleanAndCountStreamOuts() == 0
             ? Result::OK
             : Result::INVALID_STATE;
}

Return<Result> DeviceImpl::addDeviceEffect(AudioPortHandle device,
                                           uint64_t effectId) {
  return Result::NOT_SUPPORTED;
}

Return<Result> DeviceImpl::removeDeviceEffect(AudioPortHandle device,
                                              uint64_t effectId) {
  return Result::NOT_SUPPORTED;
}
#endif

#if MAJOR_VERSION == 7 && MINOR_VERSION == 1
Return<void> DeviceImpl::openOutputStream_7_1(
    int32_t ioHandle, const DeviceAddress& device, const AudioConfig& config,
    const hidl_vec<AudioInOutFlag>& flags, const SourceMetadata& sourceMetadata,
    openOutputStream_7_1_cb _hidl_cb) {
  return openOutputStreamImpl(ioHandle, device, config, flags, sourceMetadata,
                              _hidl_cb);
}

Return<Result> DeviceImpl::setConnectedState_7_1(const AudioPort& devicePort,
                                                 bool connected) {
  return Result::OK;
}
#endif

}  // namespace service
}  // namespace audio_proxy
