/*
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

#define LOG_TAG "AIDLA2dpProviderInfo"

#include "a2dp_provider_info.h"

#include <android/binder_manager.h>
#include <android_bluetooth_flags.h>

#include <optional>
#include <vector>

#include "a2dp_codec_api.h"
#include "a2dp_constants.h"
#include "a2dp_vendor.h"
#include "a2dp_vendor_aptx_constants.h"
#include "a2dp_vendor_aptx_hd_constants.h"
#include "a2dp_vendor_ldac_constants.h"
#include "a2dp_vendor_opus_constants.h"
#include "client_interface_aidl.h"
#include "os/log.h"

namespace bluetooth::audio::aidl::a2dp {

using ::aidl::android::hardware::bluetooth::audio::ChannelMode;
using ::aidl::android::hardware::bluetooth::audio::CodecId;
using ::aidl::android::hardware::bluetooth::audio::CodecInfo;
using ::aidl::android::hardware::bluetooth::audio::
    IBluetoothAudioProviderFactory;
using ::aidl::android::hardware::bluetooth::audio::SessionType;

/***
 * Reads the provider information from the HAL.
 * May return nullptr if the HAL does not implement
 * getProviderInfo, or if the feature flag for codec
 * extensibility is disabled.
 ***/
std::unique_ptr<ProviderInfo> ProviderInfo::GetProviderInfo(
    bool supports_a2dp_hw_offload_v2) {
  if (!IS_FLAG_ENABLED(a2dp_offload_codec_extensibility)) {
    LOG(INFO) << "a2dp offload codec extensibility is disabled;"
              << " not going to load the ProviderInfo";
    return nullptr;
  }

  if (!supports_a2dp_hw_offload_v2) {
    LOG(INFO) << "a2dp hw offload v2 is not supported by the controller;"
              << " not going to load the ProviderInfo";
    return nullptr;
  }

  auto source_provider_info = BluetoothAudioClientInterface::GetProviderInfo(
      SessionType::A2DP_HARDWARE_OFFLOAD_ENCODING_DATAPATH, nullptr);

  auto sink_provider_info = BluetoothAudioClientInterface::GetProviderInfo(
      SessionType::A2DP_HARDWARE_OFFLOAD_DECODING_DATAPATH, nullptr);

  if (!source_provider_info.has_value() && !sink_provider_info.has_value()) {
    LOG(INFO) << "a2dp offload codec extensibility is enabled;"
              << " but the provider info is empty";
    return nullptr;
  }

  std::vector<CodecInfo> source_codecs;
  std::vector<CodecInfo> sink_codecs;

  if (source_provider_info.has_value()) {
    source_codecs = std::move(source_provider_info->codecInfos);
  }

  if (sink_provider_info.has_value()) {
    sink_codecs = std::move(sink_provider_info->codecInfos);
  }

  LOG(INFO) << "successfully loaded provider info";
  return std::make_unique<ProviderInfo>(std::move(source_codecs),
                                        std::move(sink_codecs));
}

/***
 * Returns the codec with the selected index if supported
 * by the provider.
 ***/
std::optional<CodecInfo const*> ProviderInfo::GetCodec(
    btav_a2dp_codec_index_t codec_index) const {
  auto it = assigned_codec_indexes.find(codec_index);
  return it == assigned_codec_indexes.end() ? std::nullopt
                                            : std::make_optional(it->second);
}

/***
 * Return the assigned source codec index if the codec
 * matches a known codec, or pick a new codec index starting from
 * ext_index.
 ***/
static std::optional<btav_a2dp_codec_index_t> assignSourceCodecIndex(
    CodecInfo const& codec, btav_a2dp_codec_index_t* ext_index) {
  switch (codec.id.getTag()) {
    case CodecId::core:
    default:
      return std::nullopt;
    case CodecId::a2dp:
      switch (codec.id.get<CodecId::a2dp>()) {
        case CodecId::A2dp::SBC:
          return BTAV_A2DP_CODEC_INDEX_SOURCE_SBC;
        case CodecId::A2dp::AAC:
          return BTAV_A2DP_CODEC_INDEX_SOURCE_AAC;
        default:
          return std::nullopt;
      }
      break;
    case CodecId::vendor: {
      int vendor_id = codec.id.get<CodecId::vendor>().id;
      int codec_id = codec.id.get<CodecId::vendor>().codecId;

      /* match know vendor codecs */
      if (vendor_id == A2DP_APTX_VENDOR_ID &&
          codec_id == A2DP_APTX_CODEC_ID_BLUETOOTH) {
        return BTAV_A2DP_CODEC_INDEX_SOURCE_APTX;
      }
      if (vendor_id == A2DP_APTX_HD_VENDOR_ID &&
          codec_id == A2DP_APTX_HD_CODEC_ID_BLUETOOTH) {
        return BTAV_A2DP_CODEC_INDEX_SOURCE_APTX_HD;
      }
      if (vendor_id == A2DP_LDAC_VENDOR_ID && codec_id == A2DP_LDAC_CODEC_ID) {
        return BTAV_A2DP_CODEC_INDEX_SOURCE_LDAC;
      }
      if (vendor_id == A2DP_OPUS_VENDOR_ID && codec_id == A2DP_OPUS_CODEC_ID) {
        return BTAV_A2DP_CODEC_INDEX_SOURCE_OPUS;
      }

      /* out of extension codec indexes */
      if (*ext_index >= BTAV_A2DP_CODEC_INDEX_SOURCE_EXT_MAX) {
        LOG(ERROR) << "unable to assign a source codec index for vendorId="
                   << vendor_id << ", codecId=" << codec_id;
      }

      /* assign a new codec index for the
         unknown vendor codec */
      return *(ext_index++);
    }
  }
}

/***
 * Return the assigned source codec index if the codec
 * matches a known codec, or pick a new codec index starting from
 * ext_index.
 ***/
static std::optional<btav_a2dp_codec_index_t> assignSinkCodecIndex(
    CodecInfo const& codec, btav_a2dp_codec_index_t* ext_index) {
  switch (codec.id.getTag()) {
    case CodecId::core:
    default:
      return std::nullopt;
    case CodecId::a2dp:
      switch (codec.id.get<CodecId::a2dp>()) {
        case CodecId::A2dp::SBC:
          return BTAV_A2DP_CODEC_INDEX_SINK_SBC;
        case CodecId::A2dp::AAC:
          return BTAV_A2DP_CODEC_INDEX_SINK_AAC;
        default:
          return std::nullopt;
      }
      break;
    case CodecId::vendor: {
      int vendor_id = codec.id.get<CodecId::vendor>().id;
      int codec_id = codec.id.get<CodecId::vendor>().codecId;

      /* match know vendor codecs */
      if (vendor_id == A2DP_LDAC_VENDOR_ID && codec_id == A2DP_LDAC_CODEC_ID) {
        return BTAV_A2DP_CODEC_INDEX_SINK_LDAC;
      }
      if (vendor_id == A2DP_OPUS_VENDOR_ID && codec_id == A2DP_OPUS_CODEC_ID) {
        return BTAV_A2DP_CODEC_INDEX_SINK_OPUS;
      }

      /* out of extension codec indexes */
      if (*ext_index >= BTAV_A2DP_CODEC_INDEX_SINK_EXT_MAX) {
        LOG(ERROR) << "unable to assign a sink codec index for vendorId="
                   << vendor_id << ", codecId=" << codec_id;
      }

      /* assign a new codec index for the
         unknown vendor codec */
      return *(ext_index++);
    }
  }
}

ProviderInfo::ProviderInfo(std::vector<CodecInfo> source_codecs,
                           std::vector<CodecInfo> sink_codecs)
    : source_codecs(std::move(source_codecs)),
      sink_codecs(std::move(sink_codecs)) {
  btav_a2dp_codec_index_t ext_source_index =
      BTAV_A2DP_CODEC_INDEX_SOURCE_EXT_MIN;
  for (size_t i = 0; i < this->source_codecs.size(); i++) {
    auto& codec = this->source_codecs[i];
    LOG(INFO) << "supported source codec " << codec.name;
    auto index = assignSourceCodecIndex(codec, &ext_source_index);
    if (index.has_value()) {
      assigned_codec_indexes[index.value()] = &codec;
    }
  }

  btav_a2dp_codec_index_t ext_sink_index = BTAV_A2DP_CODEC_INDEX_SINK_EXT_MIN;
  for (size_t i = 0; i < this->sink_codecs.size(); i++) {
    auto& codec = this->sink_codecs[i];
    LOG(INFO) << "supports sink codec " << codec.name;
    auto index = assignSinkCodecIndex(codec, &ext_sink_index);
    if (index.has_value()) {
      assigned_codec_indexes[index.value()] = &codec;
    }
  }
}

std::optional<btav_a2dp_codec_index_t> ProviderInfo::SourceCodecIndex(
    CodecId const& codec_id) const {
  for (auto const& [index, codec] : assigned_codec_indexes) {
    if (codec->id == codec_id && index >= BTAV_A2DP_CODEC_INDEX_SOURCE_MIN &&
        index < BTAV_A2DP_CODEC_INDEX_SOURCE_EXT_MAX) {
      return index;
    }
  }
  return std::nullopt;
}

std::optional<btav_a2dp_codec_index_t> ProviderInfo::SourceCodecIndex(
    uint32_t vendor_id, uint16_t codec_id) const {
  for (auto const& [index, codec] : assigned_codec_indexes) {
    if (codec->id.getTag() == CodecId::vendor &&
        codec->id.get<CodecId::vendor>().id == (int)vendor_id &&
        codec->id.get<CodecId::vendor>().codecId == codec_id &&
        index >= BTAV_A2DP_CODEC_INDEX_SOURCE_MIN &&
        index < BTAV_A2DP_CODEC_INDEX_SOURCE_EXT_MAX) {
      return index;
    }
  }
  return std::nullopt;
}

std::optional<btav_a2dp_codec_index_t> ProviderInfo::SourceCodecIndex(
    uint8_t const* codec_info) const {
  LOG_ASSERT(codec_info != nullptr) << "codec_info is unexpectedly null";
  auto codec_type = A2DP_GetCodecType(codec_info);
  switch (codec_type) {
    case A2DP_MEDIA_CT_SBC: {
      return SourceCodecIndex(CodecId::A2dp(CodecId::A2dp::SBC));
    }
    case A2DP_MEDIA_CT_AAC: {
      return SourceCodecIndex(CodecId::A2dp(CodecId::A2dp::AAC));
    }
    case A2DP_MEDIA_CT_NON_A2DP: {
      uint32_t vendor_id = A2DP_VendorCodecGetVendorId(codec_info);
      uint16_t codec_id = A2DP_VendorCodecGetCodecId(codec_info);
      return SourceCodecIndex(vendor_id, codec_id);
    }
    default: {
      return std::nullopt;
    }
  }
}

std::optional<btav_a2dp_codec_index_t> ProviderInfo::SinkCodecIndex(
    CodecId const& codec_id) const {
  for (auto const& [index, codec] : assigned_codec_indexes) {
    if (codec->id == codec_id && index >= BTAV_A2DP_CODEC_INDEX_SINK_MIN &&
        index < BTAV_A2DP_CODEC_INDEX_SINK_EXT_MAX) {
      return index;
    }
  }
  return std::nullopt;
}

std::optional<btav_a2dp_codec_index_t> ProviderInfo::SinkCodecIndex(
    uint32_t vendor_id, uint16_t codec_id) const {
  for (auto const& [index, codec] : assigned_codec_indexes) {
    if (codec->id.getTag() == CodecId::vendor &&
        codec->id.get<CodecId::vendor>().id == (int)vendor_id &&
        codec->id.get<CodecId::vendor>().codecId == codec_id &&
        index >= BTAV_A2DP_CODEC_INDEX_SINK_MIN &&
        index < BTAV_A2DP_CODEC_INDEX_SINK_EXT_MAX) {
      return index;
    }
  }
  return std::nullopt;
}

std::optional<btav_a2dp_codec_index_t> ProviderInfo::SinkCodecIndex(
    uint8_t const* codec_info) const {
  LOG_ASSERT(codec_info != nullptr) << "codec_info is unexpectedly null";
  auto codec_type = A2DP_GetCodecType(codec_info);
  switch (codec_type) {
    case A2DP_MEDIA_CT_SBC: {
      return SinkCodecIndex(CodecId::A2dp(CodecId::A2dp::SBC));
    }
    case A2DP_MEDIA_CT_AAC: {
      return SinkCodecIndex(CodecId::A2dp(CodecId::A2dp::AAC));
    }
    case A2DP_MEDIA_CT_NON_A2DP: {
      uint32_t vendor_id = A2DP_VendorCodecGetVendorId(codec_info);
      uint16_t codec_id = A2DP_VendorCodecGetCodecId(codec_info);
      return SinkCodecIndex(vendor_id, codec_id);
    }
    default: {
      return std::nullopt;
    }
  }
}

std::optional<const char*> ProviderInfo::CodecIndexStr(
    btav_a2dp_codec_index_t codec_index) const {
  auto it = assigned_codec_indexes.find(codec_index);
  return it != assigned_codec_indexes.end()
             ? std::make_optional(it->second->name.c_str())
             : std::nullopt;
}

bool ProviderInfo::SupportsCodec(btav_a2dp_codec_index_t codec_index) const {
  return assigned_codec_indexes.find(codec_index) !=
         assigned_codec_indexes.end();
}

bool ProviderInfo::BuildCodecCapabilities(
    CodecId const& codec_id, std::vector<uint8_t> const& capabilities,
    uint8_t* codec_info) {
  switch (codec_id.getTag()) {
    case CodecId::a2dp: {
      auto id = codec_id.get<CodecId::a2dp>();
      codec_info[0] = 2 + capabilities.size();
      codec_info[1] = AVDT_MEDIA_TYPE_AUDIO << 4;
      codec_info[2] = static_cast<uint8_t>(id);
      memcpy(codec_info + 3, capabilities.data(), capabilities.size());
      return true;
    }
    case CodecId::vendor: {
      auto id = codec_id.get<CodecId::vendor>();
      uint32_t vendor_id = static_cast<uint32_t>(id.id);
      uint16_t codec_id = static_cast<uint16_t>(id.codecId);
      codec_info[0] = 8 + capabilities.size();
      codec_info[1] = AVDT_MEDIA_TYPE_AUDIO << 4;
      codec_info[2] = A2DP_MEDIA_CT_NON_A2DP;
      codec_info[3] = static_cast<uint8_t>(vendor_id >> 0);
      codec_info[4] = static_cast<uint8_t>(vendor_id >> 8);
      codec_info[5] = static_cast<uint8_t>(vendor_id >> 16);
      codec_info[6] = static_cast<uint8_t>(vendor_id >> 24);
      codec_info[7] = static_cast<uint8_t>(codec_id >> 0);
      codec_info[8] = static_cast<uint8_t>(codec_id >> 8);
      memcpy(codec_info + 9, capabilities.data(), capabilities.size());
      return true;
    }
    case CodecId::core:
    default:
      break;
  }
  return false;
}

bool ProviderInfo::CodecCapabilities(
    btav_a2dp_codec_index_t codec_index, uint64_t* codec_id,
    uint8_t* codec_info, btav_a2dp_codec_config_t* codec_config) const {
  auto it = assigned_codec_indexes.find(codec_index);
  if (it == assigned_codec_indexes.end()) {
    return false;
  }

  CodecInfo const* codec = it->second;
  auto transport = codec->transport.get<CodecInfo::Transport::a2dp>();

  if (codec_id != nullptr) {
    switch (codec->id.getTag()) {
      case CodecId::a2dp: {
        auto id = codec->id.get<CodecId::a2dp>();
        *codec_id = static_cast<uint8_t>(id);
        break;
      }
      case CodecId::vendor: {
        auto id = codec->id.get<CodecId::vendor>();
        *codec_id = 0xff | (static_cast<uint64_t>(id.id) << 8) |
                    (static_cast<uint64_t>(id.codecId) << 24);
        break;
      }
      default:
        break;
    }
  }
  if (codec_config != nullptr) {
    memset(codec_config, 0, sizeof(*codec_config));
    for (auto const& channel_mode : transport.channelMode) {
      switch (channel_mode) {
        case ChannelMode::MONO:
          codec_config->channel_mode |= BTAV_A2DP_CODEC_CHANNEL_MODE_MONO;
          break;
        case ChannelMode::STEREO:
          codec_config->channel_mode |= BTAV_A2DP_CODEC_CHANNEL_MODE_STEREO;
          break;
        case ChannelMode::DUALMONO:
        case ChannelMode::UNKNOWN:
        default:
          break;
      }
    }
    for (auto const& sample_rate : transport.samplingFrequencyHz) {
      switch (sample_rate) {
        case 44100:
          codec_config->sample_rate |= BTAV_A2DP_CODEC_SAMPLE_RATE_44100;
          break;
        case 48000:
          codec_config->sample_rate |= BTAV_A2DP_CODEC_SAMPLE_RATE_48000;
          break;
        case 88200:
          codec_config->sample_rate |= BTAV_A2DP_CODEC_SAMPLE_RATE_88200;
          break;
        case 96000:
          codec_config->sample_rate |= BTAV_A2DP_CODEC_SAMPLE_RATE_96000;
          break;
        case 176400:
          codec_config->sample_rate |= BTAV_A2DP_CODEC_SAMPLE_RATE_176400;
          break;
        case 192000:
          codec_config->sample_rate |= BTAV_A2DP_CODEC_SAMPLE_RATE_192000;
          break;
        case 16000:
          codec_config->sample_rate |= BTAV_A2DP_CODEC_SAMPLE_RATE_16000;
          break;
        case 24000:
          codec_config->sample_rate |= BTAV_A2DP_CODEC_SAMPLE_RATE_24000;
          break;
        default:
          break;
      }
    }
    for (auto const& bitdepth : transport.bitdepth) {
      switch (bitdepth) {
        case 16:
          codec_config->bits_per_sample |= BTAV_A2DP_CODEC_BITS_PER_SAMPLE_16;
          break;
        case 24:
          codec_config->bits_per_sample |= BTAV_A2DP_CODEC_BITS_PER_SAMPLE_24;
          break;
        case 32:
          codec_config->bits_per_sample |= BTAV_A2DP_CODEC_BITS_PER_SAMPLE_32;
          break;
        default:
          break;
      }
    }
  }

  return codec_info == nullptr ||
         BuildCodecCapabilities(codec->id, transport.capabilities, codec_info);
}

}  // namespace bluetooth::audio::aidl::a2dp
