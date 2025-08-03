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

#define LOG_TAG "BTAudioCodecStatusAIDL"

#include "codec_status_aidl.h"

#include <unordered_set>
#include <vector>

#include "a2dp_aac_constants.h"
#include "a2dp_sbc_constants.h"
#include "a2dp_vendor_aptx_constants.h"
#include "a2dp_vendor_aptx_hd_constants.h"
#include "a2dp_vendor_ldac_constants.h"
#include "bta/av/bta_av_int.h"
#include "client_interface_aidl.h"

namespace bluetooth {
namespace audio {
namespace aidl {
namespace codec {

using ::aidl::android::hardware::bluetooth::audio::AacCapabilities;
using ::aidl::android::hardware::bluetooth::audio::AacConfiguration;
using ::aidl::android::hardware::bluetooth::audio::AacObjectType;
using ::aidl::android::hardware::bluetooth::audio::AptxCapabilities;
using ::aidl::android::hardware::bluetooth::audio::AptxConfiguration;
using ::aidl::android::hardware::bluetooth::audio::AudioCapabilities;
using ::aidl::android::hardware::bluetooth::audio::ChannelMode;
using ::aidl::android::hardware::bluetooth::audio::CodecCapabilities;
using ::aidl::android::hardware::bluetooth::audio::CodecType;
using ::aidl::android::hardware::bluetooth::audio::LdacCapabilities;
using ::aidl::android::hardware::bluetooth::audio::LdacChannelMode;
using ::aidl::android::hardware::bluetooth::audio::LdacConfiguration;
using ::aidl::android::hardware::bluetooth::audio::LdacQualityIndex;
using ::aidl::android::hardware::bluetooth::audio::OpusCapabilities;
using ::aidl::android::hardware::bluetooth::audio::OpusConfiguration;
using ::aidl::android::hardware::bluetooth::audio::SbcAllocMethod;
using ::aidl::android::hardware::bluetooth::audio::SbcCapabilities;
using ::aidl::android::hardware::bluetooth::audio::SbcChannelMode;
using ::aidl::android::hardware::bluetooth::audio::SbcConfiguration;

namespace {

// capabilities from BluetoothAudioSinkClientInterface::GetAudioCapabilities()
std::vector<AudioCapabilities> audio_hal_capabilities(0);
// capabilities that audio HAL supports and frameworks / Bluetooth SoC / runtime
// preference would like to use.
std::vector<AudioCapabilities> offloading_preference(0);

template <typename T>
struct identity {
  typedef T type;
};

template <class T>
bool ContainedInVector(const std::vector<T>& vector,
                       const typename identity<T>::type& target) {
  return std::find(vector.begin(), vector.end(), target) != vector.end();
}

bool sbc_offloading_capability_match(const SbcCapabilities& sbc_capability,
                                     const SbcConfiguration& sbc_config) {
  if (!ContainedInVector(sbc_capability.channelMode, sbc_config.channelMode) ||
      !ContainedInVector(sbc_capability.allocMethod, sbc_config.allocMethod) ||
      !ContainedInVector(sbc_capability.blockLength, sbc_config.blockLength) ||
      !ContainedInVector(sbc_capability.numSubbands, sbc_config.numSubbands) ||
      !ContainedInVector(sbc_capability.bitsPerSample,
                         sbc_config.bitsPerSample) ||
      !ContainedInVector(sbc_capability.sampleRateHz,
                         sbc_config.sampleRateHz) ||
      (sbc_config.minBitpool < sbc_capability.minBitpool ||
       sbc_config.maxBitpool < sbc_config.minBitpool ||
       sbc_capability.maxBitpool < sbc_config.maxBitpool)) {
    LOG(WARNING) << __func__ << ": software codec=" << sbc_config.toString()
                 << " capability=" << sbc_capability.toString();
    return false;
  }
  LOG(INFO) << __func__ << ": offload codec=" << sbc_config.toString()
            << " capability=" << sbc_capability.toString();
  return true;
}

bool aac_offloading_capability_match(const AacCapabilities& aac_capability,
                                     const AacConfiguration& aac_config) {
  if (!ContainedInVector(aac_capability.channelMode, aac_config.channelMode) ||
      !ContainedInVector(aac_capability.objectType, aac_config.objectType) ||
      !ContainedInVector(aac_capability.bitsPerSample,
                         aac_config.bitsPerSample) ||
      !ContainedInVector(aac_capability.sampleRateHz,
                         aac_config.sampleRateHz) ||
      (!aac_capability.variableBitRateSupported &&
       aac_config.variableBitRateEnabled)) {
    LOG(WARNING) << __func__ << ": software codec=" << aac_config.toString()
                 << " capability=" << aac_capability.toString();
    return false;
  }
  LOG(INFO) << __func__ << ": offloading codec=" << aac_config.toString()
            << " capability=" << aac_capability.toString();
  return true;
}

bool aptx_offloading_capability_match(const AptxCapabilities& aptx_capability,
                                      const AptxConfiguration& aptx_config) {
  if (!ContainedInVector(aptx_capability.channelMode,
                         aptx_config.channelMode) ||
      !ContainedInVector(aptx_capability.bitsPerSample,
                         aptx_config.bitsPerSample) ||
      !ContainedInVector(aptx_capability.sampleRateHz,
                         aptx_config.sampleRateHz)) {
    LOG(WARNING) << __func__ << ": software codec=" << aptx_config.toString()
                 << " capability=" << aptx_capability.toString();
    return false;
  }
  LOG(INFO) << __func__ << ": offloading codec=" << aptx_config.toString()
            << " capability=" << aptx_capability.toString();
  return true;
}

bool ldac_offloading_capability_match(const LdacCapabilities& ldac_capability,
                                      const LdacConfiguration& ldac_config) {
  if (!ContainedInVector(ldac_capability.channelMode,
                         ldac_config.channelMode) ||
      !ContainedInVector(ldac_capability.bitsPerSample,
                         ldac_config.bitsPerSample) ||
      !ContainedInVector(ldac_capability.sampleRateHz,
                         ldac_config.sampleRateHz)) {
    LOG(WARNING) << __func__ << ": software codec=" << ldac_config.toString()
                 << " capability=" << ldac_capability.toString();
    return false;
  }
  LOG(INFO) << __func__ << ": offloading codec=" << ldac_config.toString()
            << " capability=" << ldac_capability.toString();
  return true;
}

bool opus_offloading_capability_match(
    const std::optional<OpusCapabilities>& opus_capability,
    const std::optional<OpusConfiguration>& opus_config) {
  if (!ContainedInVector(opus_capability->channelMode,
                         opus_config->channelMode) ||
      !ContainedInVector(opus_capability->frameDurationUs,
                         opus_config->frameDurationUs) ||
      !ContainedInVector(opus_capability->samplingFrequencyHz,
                         opus_config->samplingFrequencyHz)) {
    LOG(WARNING) << __func__ << ": software codec=" << opus_config->toString()
                 << " capability=" << opus_capability->toString();
    return false;
  }
  LOG(INFO) << __func__ << ": offloading codec=" << opus_config->toString()
            << " capability=" << opus_capability->toString();
  return true;
}

}  // namespace

const CodecConfiguration kInvalidCodecConfiguration = {};

int32_t A2dpCodecToHalSampleRate(
    const btav_a2dp_codec_config_t& a2dp_codec_config) {
  switch (a2dp_codec_config.sample_rate) {
    case BTAV_A2DP_CODEC_SAMPLE_RATE_44100:
      return 44100;
    case BTAV_A2DP_CODEC_SAMPLE_RATE_48000:
      return 48000;
    case BTAV_A2DP_CODEC_SAMPLE_RATE_88200:
      return 88200;
    case BTAV_A2DP_CODEC_SAMPLE_RATE_96000:
      return 96000;
    case BTAV_A2DP_CODEC_SAMPLE_RATE_176400:
      return 176400;
    case BTAV_A2DP_CODEC_SAMPLE_RATE_192000:
      return 192000;
    case BTAV_A2DP_CODEC_SAMPLE_RATE_16000:
      return 16000;
    case BTAV_A2DP_CODEC_SAMPLE_RATE_24000:
      return 24000;
    default:
      return 0;
  }
}

int8_t A2dpCodecToHalBitsPerSample(
    const btav_a2dp_codec_config_t& a2dp_codec_config) {
  switch (a2dp_codec_config.bits_per_sample) {
    case BTAV_A2DP_CODEC_BITS_PER_SAMPLE_16:
      return 16;
    case BTAV_A2DP_CODEC_BITS_PER_SAMPLE_24:
      return 24;
    case BTAV_A2DP_CODEC_BITS_PER_SAMPLE_32:
      return 32;
    default:
      return 0;
  }
}

ChannelMode A2dpCodecToHalChannelMode(
    const btav_a2dp_codec_config_t& a2dp_codec_config) {
  switch (a2dp_codec_config.channel_mode) {
    case BTAV_A2DP_CODEC_CHANNEL_MODE_MONO:
      return ChannelMode::MONO;
    case BTAV_A2DP_CODEC_CHANNEL_MODE_STEREO:
      return ChannelMode::STEREO;
    default:
      return ChannelMode::UNKNOWN;
  }
}

bool A2dpSbcToHalConfig(CodecConfiguration* codec_config,
                        A2dpCodecConfig* a2dp_config) {
  btav_a2dp_codec_config_t current_codec = a2dp_config->getCodecConfig();
  if (current_codec.codec_type != BTAV_A2DP_CODEC_INDEX_SOURCE_SBC &&
      current_codec.codec_type != BTAV_A2DP_CODEC_INDEX_SINK_SBC) {
    return false;
  }
  tBT_A2DP_OFFLOAD a2dp_offload;
  a2dp_config->getCodecSpecificConfig(&a2dp_offload);
  codec_config->codecType = CodecType::SBC;
  SbcConfiguration sbc_config = {};
  sbc_config.sampleRateHz = A2dpCodecToHalSampleRate(current_codec);
  if (sbc_config.sampleRateHz <= 0) {
    LOG(ERROR) << __func__
               << ": Unknown SBC sample_rate=" << current_codec.sample_rate;
    return false;
  }
  uint8_t channel_mode = a2dp_offload.codec_info[3] & A2DP_SBC_IE_CH_MD_MSK;
  switch (channel_mode) {
    case A2DP_SBC_IE_CH_MD_JOINT:
      sbc_config.channelMode = SbcChannelMode::JOINT_STEREO;
      break;
    case A2DP_SBC_IE_CH_MD_STEREO:
      sbc_config.channelMode = SbcChannelMode::STEREO;
      break;
    case A2DP_SBC_IE_CH_MD_DUAL:
      sbc_config.channelMode = SbcChannelMode::DUAL;
      break;
    case A2DP_SBC_IE_CH_MD_MONO:
      sbc_config.channelMode = SbcChannelMode::MONO;
      break;
    default:
      LOG(ERROR) << __func__ << ": Unknown SBC channel_mode=" << channel_mode;
      sbc_config.channelMode = SbcChannelMode::UNKNOWN;
      return false;
  }
  uint8_t block_length = a2dp_offload.codec_info[0] & A2DP_SBC_IE_BLOCKS_MSK;
  switch (block_length) {
    case A2DP_SBC_IE_BLOCKS_4:
      sbc_config.blockLength = 4;
      break;
    case A2DP_SBC_IE_BLOCKS_8:
      sbc_config.blockLength = 8;
      break;
    case A2DP_SBC_IE_BLOCKS_12:
      sbc_config.blockLength = 12;
      break;
    case A2DP_SBC_IE_BLOCKS_16:
      sbc_config.blockLength = 16;
      break;
    default:
      LOG(ERROR) << __func__ << ": Unknown SBC block_length=" << block_length;
      return false;
  }
  uint8_t sub_bands = a2dp_offload.codec_info[0] & A2DP_SBC_IE_SUBBAND_MSK;
  switch (sub_bands) {
    case A2DP_SBC_IE_SUBBAND_4:
      sbc_config.numSubbands = 4;
      break;
    case A2DP_SBC_IE_SUBBAND_8:
      sbc_config.numSubbands = 8;
      break;
    default:
      LOG(ERROR) << __func__ << ": Unknown SBC Subbands=" << sub_bands;
      return false;
  }
  uint8_t alloc_method = a2dp_offload.codec_info[0] & A2DP_SBC_IE_ALLOC_MD_MSK;
  switch (alloc_method) {
    case A2DP_SBC_IE_ALLOC_MD_S:
      sbc_config.allocMethod = SbcAllocMethod::ALLOC_MD_S;
      break;
    case A2DP_SBC_IE_ALLOC_MD_L:
      sbc_config.allocMethod = SbcAllocMethod::ALLOC_MD_L;
      break;
    default:
      LOG(ERROR) << __func__ << ": Unknown SBC alloc_method=" << alloc_method;
      return false;
  }
  sbc_config.minBitpool = a2dp_offload.codec_info[1];
  sbc_config.maxBitpool = a2dp_offload.codec_info[2];
  sbc_config.bitsPerSample = A2dpCodecToHalBitsPerSample(current_codec);
  if (sbc_config.bitsPerSample <= 0) {
    LOG(ERROR) << __func__ << ": Unknown SBC bits_per_sample="
               << current_codec.bits_per_sample;
    return false;
  }
  codec_config->config.set<CodecConfiguration::CodecSpecific::sbcConfig>(
      sbc_config);
  return true;
}

bool A2dpAacToHalConfig(CodecConfiguration* codec_config,
                        A2dpCodecConfig* a2dp_config) {
  btav_a2dp_codec_config_t current_codec = a2dp_config->getCodecConfig();
  if (current_codec.codec_type != BTAV_A2DP_CODEC_INDEX_SOURCE_AAC &&
      current_codec.codec_type != BTAV_A2DP_CODEC_INDEX_SINK_AAC) {
    return false;
  }
  tBT_A2DP_OFFLOAD a2dp_offload;
  a2dp_config->getCodecSpecificConfig(&a2dp_offload);
  codec_config->codecType = CodecType::AAC;
  AacConfiguration aac_config = {};
  uint8_t object_type = a2dp_offload.codec_info[0];
  switch (object_type) {
    case A2DP_AAC_OBJECT_TYPE_MPEG2_LC:
      aac_config.objectType = AacObjectType::MPEG2_LC;
      break;
    case A2DP_AAC_OBJECT_TYPE_MPEG4_LC:
      aac_config.objectType = AacObjectType::MPEG4_LC;
      break;
    case A2DP_AAC_OBJECT_TYPE_MPEG4_LTP:
      aac_config.objectType = AacObjectType::MPEG4_LTP;
      break;
    case A2DP_AAC_OBJECT_TYPE_MPEG4_SCALABLE:
      aac_config.objectType = AacObjectType::MPEG4_SCALABLE;
      break;
    default:
      LOG(ERROR) << __func__ << ": Unknown AAC object_type=" << +object_type;
      return false;
  }
  aac_config.sampleRateHz = A2dpCodecToHalSampleRate(current_codec);
  if (aac_config.sampleRateHz <= 0) {
    LOG(ERROR) << __func__
               << ": Unknown AAC sample_rate=" << current_codec.sample_rate;
    return false;
  }
  aac_config.channelMode = A2dpCodecToHalChannelMode(current_codec);
  if (aac_config.channelMode == ChannelMode::UNKNOWN) {
    LOG(ERROR) << __func__
               << ": Unknown AAC channel_mode=" << current_codec.channel_mode;
    return false;
  }
  uint8_t vbr_enabled =
      a2dp_offload.codec_info[1] & A2DP_AAC_VARIABLE_BIT_RATE_MASK;
  switch (vbr_enabled) {
    case A2DP_AAC_VARIABLE_BIT_RATE_ENABLED:
      aac_config.variableBitRateEnabled = true;
      break;
    case A2DP_AAC_VARIABLE_BIT_RATE_DISABLED:
      aac_config.variableBitRateEnabled = false;
      break;
    default:
      LOG(ERROR) << __func__ << ": Unknown AAC VBR=" << +vbr_enabled;
      return false;
  }
  aac_config.bitsPerSample = A2dpCodecToHalBitsPerSample(current_codec);
  if (aac_config.bitsPerSample <= 0) {
    LOG(ERROR) << __func__ << ": Unknown AAC bits_per_sample="
               << current_codec.bits_per_sample;
    return false;
  }
  codec_config->config.set<CodecConfiguration::CodecSpecific::aacConfig>(
      aac_config);
  return true;
}

bool A2dpAptxToHalConfig(CodecConfiguration* codec_config,
                         A2dpCodecConfig* a2dp_config) {
  btav_a2dp_codec_config_t current_codec = a2dp_config->getCodecConfig();
  if (current_codec.codec_type != BTAV_A2DP_CODEC_INDEX_SOURCE_APTX &&
      current_codec.codec_type != BTAV_A2DP_CODEC_INDEX_SOURCE_APTX_HD) {
    return false;
  }
  tBT_A2DP_OFFLOAD a2dp_offload;
  a2dp_config->getCodecSpecificConfig(&a2dp_offload);
  if (current_codec.codec_type == BTAV_A2DP_CODEC_INDEX_SOURCE_APTX) {
    codec_config->codecType = CodecType::APTX;
  } else {
    codec_config->codecType = CodecType::APTX_HD;
  }
  AptxConfiguration aptx_config = {};
  aptx_config.sampleRateHz = A2dpCodecToHalSampleRate(current_codec);
  if (aptx_config.sampleRateHz <= 0) {
    LOG(ERROR) << __func__
               << ": Unknown aptX sample_rate=" << current_codec.sample_rate;
    return false;
  }
  aptx_config.channelMode = A2dpCodecToHalChannelMode(current_codec);
  if (aptx_config.channelMode == ChannelMode::UNKNOWN) {
    LOG(ERROR) << __func__
               << ": Unknown aptX channel_mode=" << current_codec.channel_mode;
    return false;
  }
  aptx_config.bitsPerSample = A2dpCodecToHalBitsPerSample(current_codec);
  if (aptx_config.bitsPerSample <= 0) {
    LOG(ERROR) << __func__ << ": Unknown aptX bits_per_sample="
               << current_codec.bits_per_sample;
    return false;
  }
  codec_config->config.set<CodecConfiguration::CodecSpecific::aptxConfig>(
      aptx_config);
  return true;
}

bool A2dpLdacToHalConfig(CodecConfiguration* codec_config,
                         A2dpCodecConfig* a2dp_config) {
  btav_a2dp_codec_config_t current_codec = a2dp_config->getCodecConfig();
  if (current_codec.codec_type != BTAV_A2DP_CODEC_INDEX_SOURCE_LDAC) {
    return false;
  }
  tBT_A2DP_OFFLOAD a2dp_offload;
  a2dp_config->getCodecSpecificConfig(&a2dp_offload);
  codec_config->codecType = CodecType::LDAC;
  LdacConfiguration ldac_config = {};
  ldac_config.sampleRateHz = A2dpCodecToHalSampleRate(current_codec);
  if (ldac_config.sampleRateHz <= 0) {
    LOG(ERROR) << __func__
               << ": Unknown LDAC sample_rate=" << current_codec.sample_rate;
    return false;
  }
  switch (a2dp_offload.codec_info[7]) {
    case A2DP_LDAC_CHANNEL_MODE_STEREO:
      ldac_config.channelMode = LdacChannelMode::STEREO;
      break;
    case A2DP_LDAC_CHANNEL_MODE_DUAL:
      ldac_config.channelMode = LdacChannelMode::DUAL;
      break;
    case A2DP_LDAC_CHANNEL_MODE_MONO:
      ldac_config.channelMode = LdacChannelMode::MONO;
      break;
    default:
      LOG(ERROR) << __func__ << ": Unknown LDAC channel_mode="
                 << a2dp_offload.codec_info[7];
      ldac_config.channelMode = LdacChannelMode::UNKNOWN;
      return false;
  }
  switch (a2dp_offload.codec_info[6]) {
    case A2DP_LDAC_QUALITY_HIGH:
      ldac_config.qualityIndex = LdacQualityIndex::HIGH;
      break;
    case A2DP_LDAC_QUALITY_MID:
      ldac_config.qualityIndex = LdacQualityIndex::MID;
      break;
    case A2DP_LDAC_QUALITY_LOW:
      ldac_config.qualityIndex = LdacQualityIndex::LOW;
      break;
    case A2DP_LDAC_QUALITY_ABR_OFFLOAD:
      ldac_config.qualityIndex = LdacQualityIndex::ABR;
      break;
    default:
      LOG(ERROR) << __func__ << ": Unknown LDAC QualityIndex="
                 << a2dp_offload.codec_info[6];
      return false;
  }
  ldac_config.bitsPerSample = A2dpCodecToHalBitsPerSample(current_codec);
  if (ldac_config.bitsPerSample <= 0) {
    LOG(ERROR) << __func__ << ": Unknown LDAC bits_per_sample="
               << current_codec.bits_per_sample;
    return false;
  }
  codec_config->config.set<CodecConfiguration::CodecSpecific::ldacConfig>(
      ldac_config);
  return true;
}

bool A2dpOpusToHalConfig(CodecConfiguration* codec_config,
                         A2dpCodecConfig* a2dp_config) {
  btav_a2dp_codec_config_t current_codec = a2dp_config->getCodecConfig();
  if (current_codec.codec_type != BTAV_A2DP_CODEC_INDEX_SOURCE_OPUS) {
    codec_config = {};
    return false;
  }
  tBT_A2DP_OFFLOAD a2dp_offload;
  a2dp_config->getCodecSpecificConfig(&a2dp_offload);
  codec_config->codecType = CodecType::OPUS;
  OpusConfiguration opus_config = {};

  opus_config.pcmBitDepth = A2dpCodecToHalBitsPerSample(current_codec);
  if (opus_config.pcmBitDepth <= 0) {
    LOG(ERROR) << __func__ << ": Unknown Opus bits_per_sample="
               << current_codec.bits_per_sample;
    return false;
  }
  opus_config.samplingFrequencyHz = A2dpCodecToHalSampleRate(current_codec);
  if (opus_config.samplingFrequencyHz <= 0) {
    LOG(ERROR) << __func__
               << ": Unknown Opus sample_rate=" << current_codec.sample_rate;
    return false;
  }
  opus_config.channelMode = A2dpCodecToHalChannelMode(current_codec);
  if (opus_config.channelMode == ChannelMode::UNKNOWN) {
    LOG(ERROR) << __func__
               << ": Unknown Opus channel_mode=" << current_codec.channel_mode;
    return false;
  }

  opus_config.frameDurationUs = 20000;

  if (opus_config.channelMode == ChannelMode::STEREO) {
    opus_config.octetsPerFrame = 640;
  } else {
    opus_config.octetsPerFrame = 320;
  }

  codec_config->config.set<CodecConfiguration::CodecSpecific::opusConfig>(
      opus_config);
  return true;
}

bool UpdateOffloadingCapabilities(
    const std::vector<btav_a2dp_codec_config_t>& framework_preference) {
  audio_hal_capabilities =
      BluetoothAudioSinkClientInterface::GetAudioCapabilities(
          SessionType::A2DP_HARDWARE_OFFLOAD_ENCODING_DATAPATH);
  std::unordered_set<CodecType> codec_type_set;
  for (auto preference : framework_preference) {
    switch (preference.codec_type) {
      case BTAV_A2DP_CODEC_INDEX_SOURCE_SBC:
        codec_type_set.insert(CodecType::SBC);
        break;
      case BTAV_A2DP_CODEC_INDEX_SOURCE_AAC:
        codec_type_set.insert(CodecType::AAC);
        break;
      case BTAV_A2DP_CODEC_INDEX_SOURCE_APTX:
        codec_type_set.insert(CodecType::APTX);
        break;
      case BTAV_A2DP_CODEC_INDEX_SOURCE_APTX_HD:
        codec_type_set.insert(CodecType::APTX_HD);
        break;
      case BTAV_A2DP_CODEC_INDEX_SOURCE_LDAC:
        codec_type_set.insert(CodecType::LDAC);
        break;
      case BTAV_A2DP_CODEC_INDEX_SOURCE_LC3:
        LOG(WARNING) << __func__
                     << ": Ignore source codec_type=" << preference.codec_type
                     << ", not implemented";
        break;
      case BTAV_A2DP_CODEC_INDEX_SOURCE_OPUS:
        codec_type_set.insert(CodecType::OPUS);
        break;
      case BTAV_A2DP_CODEC_INDEX_SINK_SBC:
        [[fallthrough]];
      case BTAV_A2DP_CODEC_INDEX_SINK_AAC:
        [[fallthrough]];
      case BTAV_A2DP_CODEC_INDEX_SINK_LDAC:
        [[fallthrough]];
      case BTAV_A2DP_CODEC_INDEX_SINK_OPUS:
        LOG(WARNING) << __func__
                     << ": Ignore sink codec_type=" << preference.codec_type;
        break;
      case BTAV_A2DP_CODEC_INDEX_MAX:
        [[fallthrough]];
      default:
        LOG(ERROR) << __func__
                   << ": Unknown codec_type=" << preference.codec_type;
        return false;
    }
  }
  offloading_preference.clear();
  for (auto capability : audio_hal_capabilities) {
    auto codec_type =
        capability.get<AudioCapabilities::a2dpCapabilities>().codecType;
    if (codec_type_set.find(codec_type) != codec_type_set.end()) {
      LOG(INFO) << __func__
                << ": enabled offloading capability=" << capability.toString();
      offloading_preference.push_back(capability);
    } else {
      LOG(INFO) << __func__
                << ": disabled offloading capability=" << capability.toString();
    }
  }

  // TODO: Bluetooth SoC and runtime property
  return true;
}

/***
 * Check whether this codec is supported by the audio HAL and is allowed to
 * use by prefernece of framework / Bluetooth SoC / runtime property.
 ***/
bool IsCodecOffloadingEnabled(const CodecConfiguration& codec_config) {
  for (auto preference : offloading_preference) {
    if (codec_config.codecType !=
        preference.get<AudioCapabilities::a2dpCapabilities>().codecType) {
      continue;
    }
    auto codec_capability =
        preference.get<AudioCapabilities::a2dpCapabilities>();
    switch (codec_capability.codecType) {
      case CodecType::SBC: {
        auto sbc_capability =
            codec_capability.capabilities
                .get<CodecCapabilities::Capabilities::sbcCapabilities>();
        auto sbc_config =
            codec_config.config
                .get<CodecConfiguration::CodecSpecific::sbcConfig>();
        return sbc_offloading_capability_match(sbc_capability, sbc_config);
      }
      case CodecType::AAC: {
        auto aac_capability =
            codec_capability.capabilities
                .get<CodecCapabilities::Capabilities::aacCapabilities>();
        auto aac_config =
            codec_config.config
                .get<CodecConfiguration::CodecSpecific::aacConfig>();
        return aac_offloading_capability_match(aac_capability, aac_config);
      }
      case CodecType::APTX:
        [[fallthrough]];
      case CodecType::APTX_HD: {
        auto aptx_capability =
            codec_capability.capabilities
                .get<CodecCapabilities::Capabilities::aptxCapabilities>();
        auto aptx_config =
            codec_config.config
                .get<CodecConfiguration::CodecSpecific::aptxConfig>();
        return aptx_offloading_capability_match(aptx_capability, aptx_config);
      }
      case CodecType::LDAC: {
        auto ldac_capability =
            codec_capability.capabilities
                .get<CodecCapabilities::Capabilities::ldacCapabilities>();
        auto ldac_config =
            codec_config.config
                .get<CodecConfiguration::CodecSpecific::ldacConfig>();
        return ldac_offloading_capability_match(ldac_capability, ldac_config);
      }
      case CodecType::OPUS: {
        std::optional<OpusCapabilities> opus_capability =
            codec_capability.capabilities
                .get<CodecCapabilities::Capabilities::opusCapabilities>();
        std::optional<OpusConfiguration> opus_config =
            codec_config.config
                .get<CodecConfiguration::CodecSpecific::opusConfig>();
        return opus_offloading_capability_match(opus_capability, opus_config);
      }
      case CodecType::UNKNOWN:
        [[fallthrough]];
      default:
        LOG(ERROR) << __func__ << ": Unknown codecType="
                   << toString(codec_capability.codecType);
        return false;
    }
  }
  LOG(INFO) << __func__ << ": software codec=" << codec_config.toString();
  return false;
}

}  // namespace codec
}  // namespace aidl
}  // namespace audio
}  // namespace bluetooth
