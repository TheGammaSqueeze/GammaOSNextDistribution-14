/*
 * Copyright 2016 The Android Open Source Project
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

/**
 * A2DP Codecs Configuration
 */

#define LOG_TAG "a2dp_codec"

#include <bluetooth/log.h>

#include "a2dp_aac.h"
#include "a2dp_codec_api.h"
#include "a2dp_ext.h"
#include "a2dp_sbc.h"
#include "a2dp_vendor.h"
#include "include/check.h"

#if !defined(EXCLUDE_NONSTANDARD_CODECS)
#include "a2dp_vendor_aptx.h"
#include "a2dp_vendor_aptx_hd.h"
#include "a2dp_vendor_ldac.h"
#include "a2dp_vendor_opus.h"
#endif

#include "audio_hal_interface/a2dp_encoding.h"
#include "bta/av/bta_av_int.h"
#include "device/include/device_iot_config.h"
#include "internal_include/bt_trace.h"
#include "osi/include/properties.h"
#include "stack/include/bt_hdr.h"

/* The Media Type offset within the codec info byte array */
#define A2DP_MEDIA_TYPE_OFFSET 1

// Initializes the codec config.
// |codec_config| is the codec config to initialize.
// |codec_index| and |codec_priority| are the codec type and priority to use
// for the initialization.

using namespace bluetooth;

static void init_btav_a2dp_codec_config(
    btav_a2dp_codec_config_t* codec_config, btav_a2dp_codec_index_t codec_index,
    btav_a2dp_codec_priority_t codec_priority) {
  memset(codec_config, 0, sizeof(btav_a2dp_codec_config_t));
  codec_config->codec_type = codec_index;
  codec_config->codec_priority = codec_priority;
}

A2dpCodecConfig::A2dpCodecConfig(btav_a2dp_codec_index_t codec_index,
                                 uint64_t codec_id, const std::string& name,
                                 btav_a2dp_codec_priority_t codec_priority)
    : codec_index_(codec_index),
      codec_id_(codec_id),
      name_(name),
      default_codec_priority_(codec_priority) {
  setCodecPriority(codec_priority);

  init_btav_a2dp_codec_config(&codec_config_, codec_index_, codecPriority());
  init_btav_a2dp_codec_config(&codec_capability_, codec_index_,
                              codecPriority());
  init_btav_a2dp_codec_config(&codec_local_capability_, codec_index_,
                              codecPriority());
  init_btav_a2dp_codec_config(&codec_selectable_capability_, codec_index_,
                              codecPriority());
  init_btav_a2dp_codec_config(&codec_user_config_, codec_index_,
                              BTAV_A2DP_CODEC_PRIORITY_DEFAULT);
  init_btav_a2dp_codec_config(&codec_audio_config_, codec_index_,
                              BTAV_A2DP_CODEC_PRIORITY_DEFAULT);

  memset(ota_codec_config_, 0, sizeof(ota_codec_config_));
  memset(ota_codec_peer_capability_, 0, sizeof(ota_codec_peer_capability_));
  memset(ota_codec_peer_config_, 0, sizeof(ota_codec_peer_config_));
}

A2dpCodecConfig::~A2dpCodecConfig() {}

void A2dpCodecConfig::setCodecPriority(
    btav_a2dp_codec_priority_t codec_priority) {
  if (codec_priority == BTAV_A2DP_CODEC_PRIORITY_DEFAULT) {
    // Compute the default codec priority
    setDefaultCodecPriority();
  } else {
    codec_priority_ = codec_priority;
  }
  codec_config_.codec_priority = codec_priority_;
}

void A2dpCodecConfig::setDefaultCodecPriority() {
  if (default_codec_priority_ != BTAV_A2DP_CODEC_PRIORITY_DEFAULT) {
    codec_priority_ = default_codec_priority_;
  } else {
    // Compute the default codec priority
    uint32_t priority = 1000 * (codec_index_ + 1) + 1;
    codec_priority_ = static_cast<btav_a2dp_codec_priority_t>(priority);
  }
  codec_config_.codec_priority = codec_priority_;
}

A2dpCodecConfig* A2dpCodecConfig::createCodec(
    btav_a2dp_codec_index_t codec_index,
    btav_a2dp_codec_priority_t codec_priority) {
  log::info("{}", A2DP_CodecIndexStr(codec_index));

  // Hardware offload codec extensibility:
  // management of the codec is moved under the ProviderInfo
  // class of the aidl audio HAL client.
  if (::bluetooth::audio::a2dp::provider::supports_codec(codec_index)) {
    return new A2dpCodecConfigExt(codec_index, true);
  }

  A2dpCodecConfig* codec_config = nullptr;
  switch (codec_index) {
    case BTAV_A2DP_CODEC_INDEX_SOURCE_SBC:
      codec_config = new A2dpCodecConfigSbcSource(codec_priority);
      break;
    case BTAV_A2DP_CODEC_INDEX_SINK_SBC:
      codec_config = new A2dpCodecConfigSbcSink(codec_priority);
      break;
#if !defined(EXCLUDE_NONSTANDARD_CODECS)
    case BTAV_A2DP_CODEC_INDEX_SOURCE_AAC:
      codec_config = new A2dpCodecConfigAacSource(codec_priority);
      break;
    case BTAV_A2DP_CODEC_INDEX_SINK_AAC:
      codec_config = new A2dpCodecConfigAacSink(codec_priority);
      break;
    case BTAV_A2DP_CODEC_INDEX_SOURCE_APTX:
      codec_config = new A2dpCodecConfigAptx(codec_priority);
      break;
    case BTAV_A2DP_CODEC_INDEX_SOURCE_APTX_HD:
      codec_config = new A2dpCodecConfigAptxHd(codec_priority);
      break;
    case BTAV_A2DP_CODEC_INDEX_SOURCE_LDAC:
      codec_config = new A2dpCodecConfigLdacSource(codec_priority);
      break;
    case BTAV_A2DP_CODEC_INDEX_SINK_LDAC:
      codec_config = new A2dpCodecConfigLdacSink(codec_priority);
      break;
    case BTAV_A2DP_CODEC_INDEX_SOURCE_OPUS:
      codec_config = new A2dpCodecConfigOpusSource(codec_priority);
      break;
    case BTAV_A2DP_CODEC_INDEX_SINK_OPUS:
      codec_config = new A2dpCodecConfigOpusSink(codec_priority);
      break;
#endif
    case BTAV_A2DP_CODEC_INDEX_MAX:
    default:
      break;
  }

  if (codec_config != nullptr) {
    if (!codec_config->init()) {
      delete codec_config;
      codec_config = nullptr;
    }
  }

  return codec_config;
}

int A2dpCodecConfig::getTrackBitRate() const {
  uint8_t p_codec_info[AVDT_CODEC_SIZE];
  memcpy(p_codec_info, ota_codec_config_, sizeof(ota_codec_config_));
  tA2DP_CODEC_TYPE codec_type = A2DP_GetCodecType(p_codec_info);

  log::verbose("codec_type = 0x{:x}", codec_type);

  switch (codec_type) {
    case A2DP_MEDIA_CT_SBC:
      return A2DP_GetBitrateSbc();
#if !defined(EXCLUDE_NONSTANDARD_CODECS)
    case A2DP_MEDIA_CT_AAC:
      return A2DP_GetBitRateAac(p_codec_info);
    case A2DP_MEDIA_CT_NON_A2DP:
      return A2DP_VendorGetBitRate(p_codec_info);
#endif
    default:
      break;
  }

  log::error("unsupported codec type 0x{:x}", codec_type);
  return -1;
}

bool A2dpCodecConfig::getCodecSpecificConfig(tBT_A2DP_OFFLOAD* p_a2dp_offload) {
  std::lock_guard<std::recursive_mutex> lock(codec_mutex_);

  uint8_t codec_config[AVDT_CODEC_SIZE];
  uint32_t vendor_id;
  uint16_t codec_id;

  memset(p_a2dp_offload->codec_info, 0, sizeof(p_a2dp_offload->codec_info));

  if (!A2DP_IsSourceCodecValid(ota_codec_config_)) {
    return false;
  }

  memcpy(codec_config, ota_codec_config_, sizeof(ota_codec_config_));
  tA2DP_CODEC_TYPE codec_type = A2DP_GetCodecType(codec_config);
  switch (codec_type) {
    case A2DP_MEDIA_CT_SBC:
      p_a2dp_offload->codec_info[0] =
          codec_config[4];  // blk_len | subbands | Alloc Method
      p_a2dp_offload->codec_info[1] = codec_config[5];  // Min bit pool
      p_a2dp_offload->codec_info[2] = codec_config[6];  // Max bit pool
      p_a2dp_offload->codec_info[3] =
          codec_config[3];  // Sample freq | channel mode
      break;
#if !defined(EXCLUDE_NONSTANDARD_CODECS)
    case A2DP_MEDIA_CT_AAC:
      p_a2dp_offload->codec_info[0] = codec_config[3];  // object type
      p_a2dp_offload->codec_info[1] = codec_config[6];  // VBR | BR
      break;
    case A2DP_MEDIA_CT_NON_A2DP:
      vendor_id = A2DP_VendorCodecGetVendorId(codec_config);
      codec_id = A2DP_VendorCodecGetCodecId(codec_config);
      p_a2dp_offload->codec_info[0] = (vendor_id & 0x000000FF);
      p_a2dp_offload->codec_info[1] = (vendor_id & 0x0000FF00) >> 8;
      p_a2dp_offload->codec_info[2] = (vendor_id & 0x00FF0000) >> 16;
      p_a2dp_offload->codec_info[3] = (vendor_id & 0xFF000000) >> 24;
      p_a2dp_offload->codec_info[4] = (codec_id & 0x000000FF);
      p_a2dp_offload->codec_info[5] = (codec_id & 0x0000FF00) >> 8;
      if (vendor_id == A2DP_LDAC_VENDOR_ID && codec_id == A2DP_LDAC_CODEC_ID) {
        if (codec_config_.codec_specific_1 == 0) {  // default is 0, ABR
          p_a2dp_offload->codec_info[6] =
              A2DP_LDAC_QUALITY_ABR_OFFLOAD;  // ABR in offload
        } else {
          switch (codec_config_.codec_specific_1 % 10) {
            case 0:
              p_a2dp_offload->codec_info[6] =
                  A2DP_LDAC_QUALITY_HIGH;  // High bitrate
              break;
            case 1:
              p_a2dp_offload->codec_info[6] =
                  A2DP_LDAC_QUALITY_MID;  // Mid birate
              break;
            case 2:
              p_a2dp_offload->codec_info[6] =
                  A2DP_LDAC_QUALITY_LOW;  // Low birate
              break;
            case 3:
              FALLTHROUGH_INTENDED; /* FALLTHROUGH */
            default:
              p_a2dp_offload->codec_info[6] =
                  A2DP_LDAC_QUALITY_ABR_OFFLOAD;  // ABR in offload
              break;
          }
        }
        p_a2dp_offload->codec_info[7] =
            codec_config[10];  // LDAC specific channel mode
        log::verbose("Ldac specific channelmode ={}",
                     p_a2dp_offload->codec_info[7]);
      }
      break;
#endif
    default:
      break;
  }
  return true;
}

bool A2dpCodecConfig::isValid() const { return true; }

bool A2dpCodecConfig::copyOutOtaCodecConfig(uint8_t* p_codec_info) {
  std::lock_guard<std::recursive_mutex> lock(codec_mutex_);

  // TODO: We should use a mechanism to verify codec config,
  // not codec capability.
  if (!A2DP_IsSourceCodecValid(ota_codec_config_)) {
    return false;
  }
  memcpy(p_codec_info, ota_codec_config_, sizeof(ota_codec_config_));
  return true;
}

btav_a2dp_codec_config_t A2dpCodecConfig::getCodecConfig() {
  std::lock_guard<std::recursive_mutex> lock(codec_mutex_);

  // TODO: We should check whether the codec config is valid
  return codec_config_;
}

btav_a2dp_codec_config_t A2dpCodecConfig::getCodecCapability() {
  std::lock_guard<std::recursive_mutex> lock(codec_mutex_);

  // TODO: We should check whether the codec capability is valid
  return codec_capability_;
}

btav_a2dp_codec_config_t A2dpCodecConfig::getCodecLocalCapability() {
  std::lock_guard<std::recursive_mutex> lock(codec_mutex_);

  // TODO: We should check whether the codec capability is valid
  return codec_local_capability_;
}

btav_a2dp_codec_config_t A2dpCodecConfig::getCodecSelectableCapability() {
  std::lock_guard<std::recursive_mutex> lock(codec_mutex_);

  // TODO: We should check whether the codec capability is valid
  return codec_selectable_capability_;
}

btav_a2dp_codec_config_t A2dpCodecConfig::getCodecUserConfig() {
  std::lock_guard<std::recursive_mutex> lock(codec_mutex_);

  return codec_user_config_;
}

btav_a2dp_codec_config_t A2dpCodecConfig::getCodecAudioConfig() {
  std::lock_guard<std::recursive_mutex> lock(codec_mutex_);

  return codec_audio_config_;
}

uint8_t A2dpCodecConfig::getAudioBitsPerSample() {
  std::lock_guard<std::recursive_mutex> lock(codec_mutex_);

  switch (codec_config_.bits_per_sample) {
    case BTAV_A2DP_CODEC_BITS_PER_SAMPLE_16:
      return 16;
    case BTAV_A2DP_CODEC_BITS_PER_SAMPLE_24:
      return 24;
    case BTAV_A2DP_CODEC_BITS_PER_SAMPLE_32:
      return 32;
    case BTAV_A2DP_CODEC_BITS_PER_SAMPLE_NONE:
      break;
  }
  return 0;
}

bool A2dpCodecConfig::isCodecConfigEmpty(
    const btav_a2dp_codec_config_t& codec_config) {
  return (
      (codec_config.codec_priority == BTAV_A2DP_CODEC_PRIORITY_DEFAULT) &&
      (codec_config.sample_rate == BTAV_A2DP_CODEC_SAMPLE_RATE_NONE) &&
      (codec_config.bits_per_sample == BTAV_A2DP_CODEC_BITS_PER_SAMPLE_NONE) &&
      (codec_config.channel_mode == BTAV_A2DP_CODEC_CHANNEL_MODE_NONE) &&
      (codec_config.codec_specific_1 == 0) &&
      (codec_config.codec_specific_2 == 0) &&
      (codec_config.codec_specific_3 == 0) &&
      (codec_config.codec_specific_4 == 0));
}

bool A2dpCodecConfig::setCodecUserConfig(
    const btav_a2dp_codec_config_t& codec_user_config,
    const btav_a2dp_codec_config_t& codec_audio_config,
    const tA2DP_ENCODER_INIT_PEER_PARAMS* p_peer_params,
    const uint8_t* p_peer_codec_info, bool is_capability,
    uint8_t* p_result_codec_config, bool* p_restart_input,
    bool* p_restart_output, bool* p_config_updated) {
  std::lock_guard<std::recursive_mutex> lock(codec_mutex_);
  *p_restart_input = false;
  *p_restart_output = false;
  *p_config_updated = false;

  // Save copies of the current codec config, and the OTA codec config, so they
  // can be compared for changes.
  btav_a2dp_codec_config_t saved_codec_config = getCodecConfig();
  uint8_t saved_ota_codec_config[AVDT_CODEC_SIZE];
  memcpy(saved_ota_codec_config, ota_codec_config_, sizeof(ota_codec_config_));

  btav_a2dp_codec_config_t saved_codec_user_config = codec_user_config_;
  codec_user_config_ = codec_user_config;
  btav_a2dp_codec_config_t saved_codec_audio_config = codec_audio_config_;
  codec_audio_config_ = codec_audio_config;
  bool success =
      setCodecConfig(p_peer_codec_info, is_capability, p_result_codec_config);
  if (!success) {
    // Restore the local copy of the user and audio config
    codec_user_config_ = saved_codec_user_config;
    codec_audio_config_ = saved_codec_audio_config;
    return false;
  }

  //
  // The input (audio data) should be restarted if the audio format has changed
  //
  btav_a2dp_codec_config_t new_codec_config = getCodecConfig();
  if ((saved_codec_config.sample_rate != new_codec_config.sample_rate) ||
      (saved_codec_config.bits_per_sample !=
       new_codec_config.bits_per_sample) ||
      (saved_codec_config.channel_mode != new_codec_config.channel_mode)) {
    *p_restart_input = true;
  }

  //
  // The output (the connection) should be restarted if OTA codec config
  // has changed.
  //
  if (!A2DP_CodecEquals(saved_ota_codec_config, p_result_codec_config)) {
    *p_restart_output = true;
  }

  if (*p_restart_input || *p_restart_output) *p_config_updated = true;

  return true;
}

bool A2dpCodecConfig::codecConfigIsValid(
    const btav_a2dp_codec_config_t& codec_config) {
  return (codec_config.codec_type < BTAV_A2DP_CODEC_INDEX_MAX) &&
         (codec_config.sample_rate != BTAV_A2DP_CODEC_SAMPLE_RATE_NONE) &&
         (codec_config.bits_per_sample !=
          BTAV_A2DP_CODEC_BITS_PER_SAMPLE_NONE) &&
         (codec_config.channel_mode != BTAV_A2DP_CODEC_CHANNEL_MODE_NONE);
}

std::string A2dpCodecConfig::codecConfig2Str(
    const btav_a2dp_codec_config_t& codec_config) {
  std::string result;

  if (!codecConfigIsValid(codec_config)) return "Invalid";

  result.append("Rate=");
  result.append(codecSampleRate2Str(codec_config.sample_rate));
  result.append(" Bits=");
  result.append(codecBitsPerSample2Str(codec_config.bits_per_sample));
  result.append(" Mode=");
  result.append(codecChannelMode2Str(codec_config.channel_mode));

  return result;
}

std::string A2dpCodecConfig::codecSampleRate2Str(
    btav_a2dp_codec_sample_rate_t codec_sample_rate) {
  std::string result;

  if (codec_sample_rate & BTAV_A2DP_CODEC_SAMPLE_RATE_44100) {
    if (!result.empty()) result += "|";
    result += "44100";
  }
  if (codec_sample_rate & BTAV_A2DP_CODEC_SAMPLE_RATE_48000) {
    if (!result.empty()) result += "|";
    result += "48000";
  }
  if (codec_sample_rate & BTAV_A2DP_CODEC_SAMPLE_RATE_88200) {
    if (!result.empty()) result += "|";
    result += "88200";
  }
  if (codec_sample_rate & BTAV_A2DP_CODEC_SAMPLE_RATE_96000) {
    if (!result.empty()) result += "|";
    result += "96000";
  }
  if (codec_sample_rate & BTAV_A2DP_CODEC_SAMPLE_RATE_176400) {
    if (!result.empty()) result += "|";
    result += "176400";
  }
  if (codec_sample_rate & BTAV_A2DP_CODEC_SAMPLE_RATE_192000) {
    if (!result.empty()) result += "|";
    result += "192000";
  }
  if (result.empty()) {
    std::stringstream ss;
    ss << "UnknownSampleRate(0x" << std::hex << codec_sample_rate << ")";
    ss >> result;
  }

  return result;
}

std::string A2dpCodecConfig::codecBitsPerSample2Str(
    btav_a2dp_codec_bits_per_sample_t codec_bits_per_sample) {
  std::string result;

  if (codec_bits_per_sample & BTAV_A2DP_CODEC_BITS_PER_SAMPLE_16) {
    if (!result.empty()) result += "|";
    result += "16";
  }
  if (codec_bits_per_sample & BTAV_A2DP_CODEC_BITS_PER_SAMPLE_24) {
    if (!result.empty()) result += "|";
    result += "24";
  }
  if (codec_bits_per_sample & BTAV_A2DP_CODEC_BITS_PER_SAMPLE_32) {
    if (!result.empty()) result += "|";
    result += "32";
  }
  if (result.empty()) {
    std::stringstream ss;
    ss << "UnknownBitsPerSample(0x" << std::hex << codec_bits_per_sample << ")";
    ss >> result;
  }

  return result;
}

std::string A2dpCodecConfig::codecChannelMode2Str(
    btav_a2dp_codec_channel_mode_t codec_channel_mode) {
  std::string result;

  if (codec_channel_mode & BTAV_A2DP_CODEC_CHANNEL_MODE_MONO) {
    if (!result.empty()) result += "|";
    result += "MONO";
  }
  if (codec_channel_mode & BTAV_A2DP_CODEC_CHANNEL_MODE_STEREO) {
    if (!result.empty()) result += "|";
    result += "STEREO";
  }
  if (result.empty()) {
    std::stringstream ss;
    ss << "UnknownChannelMode(0x" << std::hex << codec_channel_mode << ")";
    ss >> result;
  }

  return result;
}

void A2dpCodecConfig::debug_codec_dump(int fd) {
  std::string result;
  dprintf(fd, "\nA2DP %s State:\n", name().c_str());
  dprintf(fd, "  Priority: %d\n", codecPriority());

  result = codecConfig2Str(getCodecConfig());
  dprintf(fd, "  Config: %s\n", result.c_str());

  result = codecConfig2Str(getCodecSelectableCapability());
  dprintf(fd, "  Selectable: %s\n", result.c_str());

  result = codecConfig2Str(getCodecLocalCapability());
  dprintf(fd, "  Local capability: %s\n", result.c_str());
}

int A2DP_IotGetPeerSinkCodecType(const uint8_t* p_codec_info) {
  int peer_codec_type = 0;
  tA2DP_CODEC_TYPE codec_type = A2DP_GetCodecType(p_codec_info);
  log::verbose("codec_type = 0x{:x}", codec_type);
  switch (codec_type) {
    case A2DP_MEDIA_CT_SBC:
      peer_codec_type = IOT_CONF_VAL_A2DP_CODECTYPE_SBC;
      break;
#if !defined(EXCLUDE_NONSTANDARD_CODECS)
    case A2DP_MEDIA_CT_NON_A2DP: {
      uint16_t codec_id = A2DP_VendorCodecGetCodecId(p_codec_info);
      uint32_t vendor_id = A2DP_VendorCodecGetVendorId(p_codec_info);

      log::verbose("codec_id = {}", codec_id);
      log::verbose("vendor_id = {:x}", vendor_id);

      if (codec_id == A2DP_APTX_CODEC_ID_BLUETOOTH &&
          vendor_id == A2DP_APTX_VENDOR_ID) {
        peer_codec_type = IOT_CONF_VAL_A2DP_CODECTYPE_APTX;
      } else if (codec_id == A2DP_APTX_HD_CODEC_ID_BLUETOOTH &&
                 vendor_id == A2DP_APTX_HD_VENDOR_ID) {
        peer_codec_type = IOT_CONF_VAL_A2DP_CODECTYPE_APTXHD;
      } else if (codec_id == A2DP_LDAC_CODEC_ID &&
                 vendor_id == A2DP_LDAC_VENDOR_ID) {
        peer_codec_type = IOT_CONF_VAL_A2DP_CODECTYPE_LDAC;
      }
      break;
    }
    case A2DP_MEDIA_CT_AAC:
      peer_codec_type = IOT_CONF_VAL_A2DP_CODECTYPE_AAC;
      break;
#endif
    default:
      break;
  }
  return peer_codec_type;
}

//
// Compares two codecs |lhs| and |rhs| based on their priority.
// Returns true if |lhs| has higher priority (larger priority value).
// If |lhs| and |rhs| have same priority, the unique codec index is used
// as a tie-breaker: larger codec index value means higher priority.
//
static bool compare_codec_priority(const A2dpCodecConfig* lhs,
                                   const A2dpCodecConfig* rhs) {
  if (lhs->codecPriority() > rhs->codecPriority()) return true;
  if (lhs->codecPriority() < rhs->codecPriority()) return false;
  return (lhs->codecIndex() > rhs->codecIndex());
}

A2dpCodecs::A2dpCodecs(
    const std::vector<btav_a2dp_codec_config_t>& codec_priorities)
    : current_codec_config_(nullptr) {
  for (auto config : codec_priorities) {
    codec_priorities_.insert(
        std::make_pair(config.codec_type, config.codec_priority));
  }
}

A2dpCodecs::~A2dpCodecs() {
  std::unique_lock<std::recursive_mutex> lock(codec_mutex_);
  for (const auto& iter : indexed_codecs_) {
    delete iter.second;
  }
  for (const auto& iter : disabled_codecs_) {
    delete iter.second;
  }
  lock.unlock();
}

bool A2dpCodecs::init() {
  log::info("");
  std::lock_guard<std::recursive_mutex> lock(codec_mutex_);

  bool opus_enabled =
      osi_property_get_bool("persist.bluetooth.opus.enabled", false);

  for (int i = BTAV_A2DP_CODEC_INDEX_MIN; i < BTAV_A2DP_CODEC_INDEX_MAX; i++) {
    btav_a2dp_codec_index_t codec_index =
        static_cast<btav_a2dp_codec_index_t>(i);

    // Select the codec priority if explicitly configured
    btav_a2dp_codec_priority_t codec_priority =
        BTAV_A2DP_CODEC_PRIORITY_DEFAULT;
    auto cp_iter = codec_priorities_.find(codec_index);
    if (cp_iter != codec_priorities_.end()) {
      codec_priority = cp_iter->second;
    }

#if !defined(UNIT_TESTS)
    if (codec_index == BTAV_A2DP_CODEC_INDEX_SOURCE_OPUS) {
      if (!bluetooth::audio::a2dp::is_opus_supported()) {
        // We are using HIDL HAL which does not support OPUS codec
        // Mark OPUS as disabled
        opus_enabled = false;
      }
    }
#endif

    // If OPUS is not supported it is disabled
    if (codec_index == BTAV_A2DP_CODEC_INDEX_SOURCE_OPUS && !opus_enabled) {
      codec_priority = BTAV_A2DP_CODEC_PRIORITY_DISABLED;
      log::info("OPUS codec disabled, updated priority to {}", codec_priority);
    }

    A2dpCodecConfig* codec_config =
        A2dpCodecConfig::createCodec(codec_index, codec_priority);
    if (codec_config == nullptr) continue;

    if (codec_priority != BTAV_A2DP_CODEC_PRIORITY_DEFAULT) {
      log::info("updated {} codec priority to {}", codec_config->name().c_str(),
                codec_priority);
    }

    // Test if the codec is disabled
    if (codec_config->codecPriority() == BTAV_A2DP_CODEC_PRIORITY_DISABLED) {
      disabled_codecs_.insert(std::make_pair(codec_index, codec_config));
      continue;
    }

    indexed_codecs_.insert(std::make_pair(codec_index, codec_config));

    if (codec_index < BTAV_A2DP_CODEC_INDEX_SOURCE_MAX) {
      ordered_source_codecs_.push_back(codec_config);
      ordered_source_codecs_.sort(compare_codec_priority);
    } else {
      ordered_sink_codecs_.push_back(codec_config);
      ordered_sink_codecs_.sort(compare_codec_priority);
    }
  }

  if (ordered_source_codecs_.empty()) {
    log::error("no Source codecs were initialized");
  } else {
    for (auto iter : ordered_source_codecs_) {
      log::info("initialized Source codec {}, idx {}", iter->name().c_str(),
                iter->codecIndex());
    }
  }
  if (ordered_sink_codecs_.empty()) {
    log::error("no Sink codecs were initialized");
  } else {
    for (auto iter : ordered_sink_codecs_) {
      log::info("initialized Sink codec {}, idx {}", iter->name().c_str(),
                iter->codecIndex());
    }
  }

  return (!ordered_source_codecs_.empty() && !ordered_sink_codecs_.empty());
}

A2dpCodecConfig* A2dpCodecs::findSourceCodecConfig(
    const uint8_t* p_codec_info) {
  std::lock_guard<std::recursive_mutex> lock(codec_mutex_);
  btav_a2dp_codec_index_t codec_index = A2DP_SourceCodecIndex(p_codec_info);
  if (codec_index == BTAV_A2DP_CODEC_INDEX_MAX) return nullptr;

  auto iter = indexed_codecs_.find(codec_index);
  if (iter == indexed_codecs_.end()) return nullptr;
  return iter->second;
}

A2dpCodecConfig* A2dpCodecs::findSourceCodecConfig(
    btav_a2dp_codec_index_t codec_index) {
  std::lock_guard<std::recursive_mutex> lock(codec_mutex_);

  auto iter = indexed_codecs_.find(codec_index);
  if (iter == indexed_codecs_.end()) return nullptr;
  return iter->second;
}

A2dpCodecConfig* A2dpCodecs::findSinkCodecConfig(const uint8_t* p_codec_info) {
  std::lock_guard<std::recursive_mutex> lock(codec_mutex_);
  btav_a2dp_codec_index_t codec_index = A2DP_SinkCodecIndex(p_codec_info);
  if (codec_index == BTAV_A2DP_CODEC_INDEX_MAX) return nullptr;

  auto iter = indexed_codecs_.find(codec_index);
  if (iter == indexed_codecs_.end()) return nullptr;
  return iter->second;
}

bool A2dpCodecs::isSupportedCodec(btav_a2dp_codec_index_t codec_index) {
  std::lock_guard<std::recursive_mutex> lock(codec_mutex_);
  return indexed_codecs_.find(codec_index) != indexed_codecs_.end();
}

bool A2dpCodecs::setCodecConfig(const uint8_t* p_peer_codec_info,
                                bool is_capability,
                                uint8_t* p_result_codec_config,
                                bool select_current_codec) {
  std::lock_guard<std::recursive_mutex> lock(codec_mutex_);
  A2dpCodecConfig* a2dp_codec_config = findSourceCodecConfig(p_peer_codec_info);
  if (a2dp_codec_config == nullptr) return false;
  if (!a2dp_codec_config->setCodecConfig(p_peer_codec_info, is_capability,
                                         p_result_codec_config)) {
    return false;
  }
  if (select_current_codec) {
    current_codec_config_ = a2dp_codec_config;
  }
  return true;
}

bool A2dpCodecs::setSinkCodecConfig(const uint8_t* p_peer_codec_info,
                                    bool is_capability,
                                    uint8_t* p_result_codec_config,
                                    bool select_current_codec) {
  std::lock_guard<std::recursive_mutex> lock(codec_mutex_);
  A2dpCodecConfig* a2dp_codec_config = findSinkCodecConfig(p_peer_codec_info);
  if (a2dp_codec_config == nullptr) return false;
  if (!a2dp_codec_config->setCodecConfig(p_peer_codec_info, is_capability,
                                         p_result_codec_config)) {
    return false;
  }
  if (select_current_codec) {
    current_codec_config_ = a2dp_codec_config;
  }
  return true;
}

bool A2dpCodecs::setCodecUserConfig(
    const btav_a2dp_codec_config_t& codec_user_config,
    const tA2DP_ENCODER_INIT_PEER_PARAMS* p_peer_params,
    const uint8_t* p_peer_sink_capabilities, uint8_t* p_result_codec_config,
    bool* p_restart_input, bool* p_restart_output, bool* p_config_updated) {
  std::lock_guard<std::recursive_mutex> lock(codec_mutex_);
  btav_a2dp_codec_config_t codec_audio_config;
  A2dpCodecConfig* a2dp_codec_config = nullptr;
  A2dpCodecConfig* last_codec_config = current_codec_config_;
  *p_restart_input = false;
  *p_restart_output = false;
  *p_config_updated = false;

  log::info("Configuring: {}", codec_user_config.ToString().c_str());

  if (codec_user_config.codec_type < BTAV_A2DP_CODEC_INDEX_MAX) {
    auto iter = indexed_codecs_.find(codec_user_config.codec_type);
    if (iter == indexed_codecs_.end()) goto fail;
    a2dp_codec_config = iter->second;
  } else {
    // Update the default codec
    a2dp_codec_config = current_codec_config_;
  }
  if (a2dp_codec_config == nullptr) goto fail;

  // Reuse the existing codec audio config
  codec_audio_config = a2dp_codec_config->getCodecAudioConfig();
  if (!a2dp_codec_config->setCodecUserConfig(
          codec_user_config, codec_audio_config, p_peer_params,
          p_peer_sink_capabilities, true, p_result_codec_config,
          p_restart_input, p_restart_output, p_config_updated)) {
    goto fail;
  }

  // Update the codec priorities, and eventually restart the connection
  // if a new codec needs to be selected.
  do {
    // Update the codec priority
    btav_a2dp_codec_priority_t old_priority =
        a2dp_codec_config->codecPriority();
    btav_a2dp_codec_priority_t new_priority = codec_user_config.codec_priority;
    a2dp_codec_config->setCodecPriority(new_priority);
    // Get the actual (recomputed) priority
    new_priority = a2dp_codec_config->codecPriority();

    // Check if there was no previous codec
    if (last_codec_config == nullptr) {
      current_codec_config_ = a2dp_codec_config;
      *p_restart_input = true;
      *p_restart_output = true;
      break;
    }

    // Check if the priority of the current codec was updated
    if (a2dp_codec_config == last_codec_config) {
      if (old_priority == new_priority) break;  // No change in priority

      *p_config_updated = true;
      if (new_priority < old_priority) {
        // The priority has become lower - restart the connection to
        // select a new codec.
        *p_restart_output = true;
      }
      break;
    }

    if (new_priority <= old_priority) {
      // No change in priority, or the priority has become lower.
      // This wasn't the current codec, so we shouldn't select a new codec.
      if (*p_restart_input || *p_restart_output ||
          (old_priority != new_priority)) {
        *p_config_updated = true;
      }
      *p_restart_input = false;
      *p_restart_output = false;
      break;
    }

    *p_config_updated = true;
    if (new_priority >= last_codec_config->codecPriority()) {
      // The new priority is higher than the current codec. Restart the
      // connection to select a new codec.
      current_codec_config_ = a2dp_codec_config;
      last_codec_config->setDefaultCodecPriority();
      *p_restart_input = true;
      *p_restart_output = true;
    }
  } while (false);
  ordered_source_codecs_.sort(compare_codec_priority);

  if (*p_restart_input || *p_restart_output) *p_config_updated = true;

  log::info(
      "Configured: restart_input = {} restart_output = {} config_updated = {}",
      *p_restart_input, *p_restart_output, *p_config_updated);

  return true;

fail:
  current_codec_config_ = last_codec_config;
  return false;
}

bool A2dpCodecs::setCodecAudioConfig(
    const btav_a2dp_codec_config_t& codec_audio_config,
    const tA2DP_ENCODER_INIT_PEER_PARAMS* p_peer_params,
    const uint8_t* p_peer_sink_capabilities, uint8_t* p_result_codec_config,
    bool* p_restart_output, bool* p_config_updated) {
  std::lock_guard<std::recursive_mutex> lock(codec_mutex_);
  btav_a2dp_codec_config_t codec_user_config;
  A2dpCodecConfig* a2dp_codec_config = current_codec_config_;
  *p_restart_output = false;
  *p_config_updated = false;

  if (a2dp_codec_config == nullptr) return false;

  // Reuse the existing codec user config
  codec_user_config = a2dp_codec_config->getCodecUserConfig();
  bool restart_input = false;  // Flag ignored - input was just restarted
  if (!a2dp_codec_config->setCodecUserConfig(
          codec_user_config, codec_audio_config, p_peer_params,
          p_peer_sink_capabilities, true, p_result_codec_config, &restart_input,
          p_restart_output, p_config_updated)) {
    return false;
  }

  return true;
}

bool A2dpCodecs::setCodecOtaConfig(
    const uint8_t* p_ota_codec_config,
    const tA2DP_ENCODER_INIT_PEER_PARAMS* p_peer_params,
    uint8_t* p_result_codec_config, bool* p_restart_input,
    bool* p_restart_output, bool* p_config_updated) {
  std::lock_guard<std::recursive_mutex> lock(codec_mutex_);
  btav_a2dp_codec_index_t codec_type;
  btav_a2dp_codec_config_t codec_user_config;
  btav_a2dp_codec_config_t codec_audio_config;
  A2dpCodecConfig* a2dp_codec_config = nullptr;
  A2dpCodecConfig* last_codec_config = current_codec_config_;
  *p_restart_input = false;
  *p_restart_output = false;
  *p_config_updated = false;

  // Check whether the current codec config is explicitly configured by
  // user configuration. If yes, then the OTA codec configuration is ignored.
  if (current_codec_config_ != nullptr) {
    codec_user_config = current_codec_config_->getCodecUserConfig();
    if (!A2dpCodecConfig::isCodecConfigEmpty(codec_user_config)) {
      log::warn(
          "ignoring peer OTA configuration for codec {}: existing user "
          "configuration for current codec {}",
          A2DP_CodecName(p_ota_codec_config),
          current_codec_config_->name().c_str());
      goto fail;
    }
  }

  // Check whether the codec config for the same codec is explicitly configured
  // by user configuration. If yes, then the OTA codec configuration is
  // ignored.
  codec_type = A2DP_SourceCodecIndex(p_ota_codec_config);
  if (codec_type == BTAV_A2DP_CODEC_INDEX_MAX) {
    log::warn("ignoring peer OTA codec configuration: invalid codec");
    goto fail;  // Invalid codec
  } else {
    auto iter = indexed_codecs_.find(codec_type);
    if (iter == indexed_codecs_.end()) {
      log::warn("cannot find codec configuration for peer OTA codec {}",
                A2DP_CodecName(p_ota_codec_config));
      goto fail;
    }
    a2dp_codec_config = iter->second;
  }
  if (a2dp_codec_config == nullptr) goto fail;
  codec_user_config = a2dp_codec_config->getCodecUserConfig();
  if (!A2dpCodecConfig::isCodecConfigEmpty(codec_user_config)) {
    log::warn(
        "ignoring peer OTA configuration for codec {}: existing user "
        "configuration for same codec",
        A2DP_CodecName(p_ota_codec_config));
    goto fail;
  }
  current_codec_config_ = a2dp_codec_config;

  // Reuse the existing codec user config and codec audio config
  codec_audio_config = a2dp_codec_config->getCodecAudioConfig();
  if (!a2dp_codec_config->setCodecUserConfig(
          codec_user_config, codec_audio_config, p_peer_params,
          p_ota_codec_config, false, p_result_codec_config, p_restart_input,
          p_restart_output, p_config_updated)) {
    log::warn("cannot set codec configuration for peer OTA codec {}",
              A2DP_CodecName(p_ota_codec_config));
    goto fail;
  }
  CHECK(current_codec_config_ != nullptr);

  if (*p_restart_input || *p_restart_output) *p_config_updated = true;

  return true;

fail:
  current_codec_config_ = last_codec_config;
  return false;
}

bool A2dpCodecs::setPeerSinkCodecCapabilities(
    const uint8_t* p_peer_codec_capabilities) {
  std::lock_guard<std::recursive_mutex> lock(codec_mutex_);

  A2dpCodecConfig* a2dp_codec_config =
      findSourceCodecConfig(p_peer_codec_capabilities);
  if (a2dp_codec_config == nullptr) return false;

  // Bypass the validation for codecs that are offloaded:
  // the stack does not need to know about the peer capabilities,
  // since the validation and selection will be performed by the
  // bluetooth audio HAL for offloaded codecs.
  if (!::bluetooth::audio::a2dp::provider::supports_codec(
          a2dp_codec_config->codecIndex()) &&
      !A2DP_IsPeerSinkCodecValid(p_peer_codec_capabilities))
    return false;

  return a2dp_codec_config->setPeerCodecCapabilities(p_peer_codec_capabilities);
}

bool A2dpCodecs::setPeerSourceCodecCapabilities(
    const uint8_t* p_peer_codec_capabilities) {
  std::lock_guard<std::recursive_mutex> lock(codec_mutex_);

  if (!A2DP_IsPeerSourceCodecValid(p_peer_codec_capabilities)) return false;
  A2dpCodecConfig* a2dp_codec_config =
      findSinkCodecConfig(p_peer_codec_capabilities);
  if (a2dp_codec_config == nullptr) return false;
  return a2dp_codec_config->setPeerCodecCapabilities(p_peer_codec_capabilities);
}

bool A2dpCodecs::getCodecConfigAndCapabilities(
    btav_a2dp_codec_config_t* p_codec_config,
    std::vector<btav_a2dp_codec_config_t>* p_codecs_local_capabilities,
    std::vector<btav_a2dp_codec_config_t>* p_codecs_selectable_capabilities) {
  std::lock_guard<std::recursive_mutex> lock(codec_mutex_);

  if (current_codec_config_ != nullptr) {
    *p_codec_config = current_codec_config_->getCodecConfig();
  } else {
    btav_a2dp_codec_config_t codec_config;
    memset(&codec_config, 0, sizeof(codec_config));
    *p_codec_config = codec_config;
  }

  std::vector<btav_a2dp_codec_config_t> codecs_capabilities;
  for (auto codec : orderedSourceCodecs()) {
    codecs_capabilities.push_back(codec->getCodecLocalCapability());
  }
  *p_codecs_local_capabilities = codecs_capabilities;

  codecs_capabilities.clear();
  for (auto codec : orderedSourceCodecs()) {
    btav_a2dp_codec_config_t codec_capability =
        codec->getCodecSelectableCapability();
    // Don't add entries that cannot be used
    if ((codec_capability.sample_rate == BTAV_A2DP_CODEC_SAMPLE_RATE_NONE) ||
        (codec_capability.bits_per_sample ==
         BTAV_A2DP_CODEC_BITS_PER_SAMPLE_NONE) ||
        (codec_capability.channel_mode == BTAV_A2DP_CODEC_CHANNEL_MODE_NONE)) {
      continue;
    }
    codecs_capabilities.push_back(codec_capability);
  }
  *p_codecs_selectable_capabilities = codecs_capabilities;

  return true;
}

void A2dpCodecs::debug_codec_dump(int fd) {
  std::lock_guard<std::recursive_mutex> lock(codec_mutex_);
  dprintf(fd, "\nA2DP Codecs State:\n");

  // Print the current codec name
  if (current_codec_config_ != nullptr) {
    dprintf(fd, "  Current Codec: %s\n", current_codec_config_->name().c_str());
  } else {
    dprintf(fd, "  Current Codec: None\n");
  }

  // Print the codec-specific state
  for (auto codec_config : ordered_source_codecs_) {
    codec_config->debug_codec_dump(fd);
  }
}

tA2DP_CODEC_TYPE A2DP_GetCodecType(const uint8_t* p_codec_info) {
  return (tA2DP_CODEC_TYPE)(p_codec_info[AVDT_CODEC_TYPE_INDEX]);
}

bool A2DP_IsSourceCodecValid(const uint8_t* p_codec_info) {
  tA2DP_CODEC_TYPE codec_type = A2DP_GetCodecType(p_codec_info);

  log::verbose("codec_type = 0x{:x}", codec_type);

  switch (codec_type) {
    case A2DP_MEDIA_CT_SBC:
      return A2DP_IsSourceCodecValidSbc(p_codec_info);
#if !defined(EXCLUDE_NONSTANDARD_CODECS)
    case A2DP_MEDIA_CT_AAC:
      return A2DP_IsSourceCodecValidAac(p_codec_info);
    case A2DP_MEDIA_CT_NON_A2DP:
      return A2DP_IsVendorSourceCodecValid(p_codec_info);
#endif
    default:
      break;
  }

  return false;
}

bool A2DP_IsSinkCodecValid(const uint8_t* p_codec_info) {
  tA2DP_CODEC_TYPE codec_type = A2DP_GetCodecType(p_codec_info);

  log::verbose("codec_type = 0x{:x}", codec_type);

  switch (codec_type) {
    case A2DP_MEDIA_CT_SBC:
      return A2DP_IsSinkCodecValidSbc(p_codec_info);
#if !defined(EXCLUDE_NONSTANDARD_CODECS)
    case A2DP_MEDIA_CT_AAC:
      return A2DP_IsSinkCodecValidAac(p_codec_info);
    case A2DP_MEDIA_CT_NON_A2DP:
      return A2DP_IsVendorSinkCodecValid(p_codec_info);
#endif
    default:
      break;
  }

  return false;
}

bool A2DP_IsPeerSourceCodecValid(const uint8_t* p_codec_info) {
  tA2DP_CODEC_TYPE codec_type = A2DP_GetCodecType(p_codec_info);

  log::verbose("codec_type = 0x{:x}", codec_type);

  switch (codec_type) {
    case A2DP_MEDIA_CT_SBC:
      return A2DP_IsPeerSourceCodecValidSbc(p_codec_info);
#if !defined(EXCLUDE_NONSTANDARD_CODECS)
    case A2DP_MEDIA_CT_AAC:
      return A2DP_IsPeerSourceCodecValidAac(p_codec_info);
    case A2DP_MEDIA_CT_NON_A2DP:
      return A2DP_IsVendorPeerSourceCodecValid(p_codec_info);
#endif
    default:
      break;
  }

  return false;
}

bool A2DP_IsPeerSinkCodecValid(const uint8_t* p_codec_info) {
  tA2DP_CODEC_TYPE codec_type = A2DP_GetCodecType(p_codec_info);

  log::verbose("codec_type = 0x{:x}", codec_type);

  switch (codec_type) {
    case A2DP_MEDIA_CT_SBC:
      return A2DP_IsPeerSinkCodecValidSbc(p_codec_info);
#if !defined(EXCLUDE_NONSTANDARD_CODECS)
    case A2DP_MEDIA_CT_AAC:
      return A2DP_IsPeerSinkCodecValidAac(p_codec_info);
    case A2DP_MEDIA_CT_NON_A2DP:
      return A2DP_IsVendorPeerSinkCodecValid(p_codec_info);
#endif
    default:
      break;
  }

  return false;
}

bool A2DP_IsSinkCodecSupported(const uint8_t* p_codec_info) {
  tA2DP_CODEC_TYPE codec_type = A2DP_GetCodecType(p_codec_info);

  log::verbose("codec_type = 0x{:x}", codec_type);

  switch (codec_type) {
    case A2DP_MEDIA_CT_SBC:
      return A2DP_IsSinkCodecSupportedSbc(p_codec_info);
#if !defined(EXCLUDE_NONSTANDARD_CODECS)
    case A2DP_MEDIA_CT_AAC:
      return A2DP_IsSinkCodecSupportedAac(p_codec_info);
    case A2DP_MEDIA_CT_NON_A2DP:
      return A2DP_IsVendorSinkCodecSupported(p_codec_info);
#endif
    default:
      break;
  }

  log::error("unsupported codec type 0x{:x}", codec_type);
  return false;
}

bool A2DP_IsPeerSourceCodecSupported(const uint8_t* p_codec_info) {
  tA2DP_CODEC_TYPE codec_type = A2DP_GetCodecType(p_codec_info);

  log::verbose("codec_type = 0x{:x}", codec_type);

  switch (codec_type) {
    case A2DP_MEDIA_CT_SBC:
      return A2DP_IsPeerSourceCodecSupportedSbc(p_codec_info);
#if !defined(EXCLUDE_NONSTANDARD_CODECS)
    case A2DP_MEDIA_CT_AAC:
      return A2DP_IsPeerSourceCodecSupportedAac(p_codec_info);
    case A2DP_MEDIA_CT_NON_A2DP:
      return A2DP_IsVendorPeerSourceCodecSupported(p_codec_info);
#endif
    default:
      break;
  }

  log::error("unsupported codec type 0x{:x}", codec_type);
  return false;
}

void A2DP_InitDefaultCodec(uint8_t* p_codec_info) {
  A2DP_InitDefaultCodecSbc(p_codec_info);
}

bool A2DP_UsesRtpHeader(bool content_protection_enabled,
                        const uint8_t* p_codec_info) {
  tA2DP_CODEC_TYPE codec_type = A2DP_GetCodecType(p_codec_info);

  if (codec_type != A2DP_MEDIA_CT_NON_A2DP) return true;

#if !defined(EXCLUDE_NONSTANDARD_CODECS)
  return A2DP_VendorUsesRtpHeader(content_protection_enabled, p_codec_info);
#else
  return true;
#endif
}

uint8_t A2DP_GetMediaType(const uint8_t* p_codec_info) {
  uint8_t media_type = (p_codec_info[A2DP_MEDIA_TYPE_OFFSET] >> 4) & 0x0f;
  return media_type;
}

const char* A2DP_CodecName(const uint8_t* p_codec_info) {
  tA2DP_CODEC_TYPE codec_type = A2DP_GetCodecType(p_codec_info);

  log::verbose("codec_type = 0x{:x}", codec_type);

  switch (codec_type) {
    case A2DP_MEDIA_CT_SBC:
      return A2DP_CodecNameSbc(p_codec_info);
#if !defined(EXCLUDE_NONSTANDARD_CODECS)
    case A2DP_MEDIA_CT_AAC:
      return A2DP_CodecNameAac(p_codec_info);
    case A2DP_MEDIA_CT_NON_A2DP:
      return A2DP_VendorCodecName(p_codec_info);
#endif
    default:
      break;
  }

  log::error("unsupported codec type 0x{:x}", codec_type);
  return "UNKNOWN CODEC";
}

bool A2DP_CodecTypeEquals(const uint8_t* p_codec_info_a,
                          const uint8_t* p_codec_info_b) {
  tA2DP_CODEC_TYPE codec_type_a = A2DP_GetCodecType(p_codec_info_a);
  tA2DP_CODEC_TYPE codec_type_b = A2DP_GetCodecType(p_codec_info_b);

  if (codec_type_a != codec_type_b) return false;

  switch (codec_type_a) {
    case A2DP_MEDIA_CT_SBC:
      return A2DP_CodecTypeEqualsSbc(p_codec_info_a, p_codec_info_b);
#if !defined(EXCLUDE_NONSTANDARD_CODECS)
    case A2DP_MEDIA_CT_AAC:
      return A2DP_CodecTypeEqualsAac(p_codec_info_a, p_codec_info_b);
    case A2DP_MEDIA_CT_NON_A2DP:
      return A2DP_VendorCodecTypeEquals(p_codec_info_a, p_codec_info_b);
#endif
    default:
      break;
  }

  log::error("unsupported codec type 0x{:x}", codec_type_a);
  return false;
}

bool A2DP_CodecEquals(const uint8_t* p_codec_info_a,
                      const uint8_t* p_codec_info_b) {
  tA2DP_CODEC_TYPE codec_type_a = A2DP_GetCodecType(p_codec_info_a);
  tA2DP_CODEC_TYPE codec_type_b = A2DP_GetCodecType(p_codec_info_b);

  if (codec_type_a != codec_type_b) return false;

  switch (codec_type_a) {
    case A2DP_MEDIA_CT_SBC:
      return A2DP_CodecEqualsSbc(p_codec_info_a, p_codec_info_b);
#if !defined(EXCLUDE_NONSTANDARD_CODECS)
    case A2DP_MEDIA_CT_AAC:
      return A2DP_CodecEqualsAac(p_codec_info_a, p_codec_info_b);
    case A2DP_MEDIA_CT_NON_A2DP:
      return A2DP_VendorCodecEquals(p_codec_info_a, p_codec_info_b);
#endif
    default:
      break;
  }

  log::error("unsupported codec type 0x{:x}", codec_type_a);
  return false;
}

int A2DP_GetTrackSampleRate(const uint8_t* p_codec_info) {
  tA2DP_CODEC_TYPE codec_type = A2DP_GetCodecType(p_codec_info);

  log::verbose("codec_type = 0x{:x}", codec_type);

  switch (codec_type) {
    case A2DP_MEDIA_CT_SBC:
      return A2DP_GetTrackSampleRateSbc(p_codec_info);
#if !defined(EXCLUDE_NONSTANDARD_CODECS)
    case A2DP_MEDIA_CT_AAC:
      return A2DP_GetTrackSampleRateAac(p_codec_info);
    case A2DP_MEDIA_CT_NON_A2DP:
      return A2DP_VendorGetTrackSampleRate(p_codec_info);
#endif
    default:
      break;
  }

  log::error("unsupported codec type 0x{:x}", codec_type);
  return -1;
}

int A2DP_GetTrackBitsPerSample(const uint8_t* p_codec_info) {
  tA2DP_CODEC_TYPE codec_type = A2DP_GetCodecType(p_codec_info);

  log::verbose("codec_type = 0x{:x}", codec_type);

  switch (codec_type) {
    case A2DP_MEDIA_CT_SBC:
      return A2DP_GetTrackBitsPerSampleSbc(p_codec_info);
#if !defined(EXCLUDE_NONSTANDARD_CODECS)
    case A2DP_MEDIA_CT_AAC:
      return A2DP_GetTrackBitsPerSampleAac(p_codec_info);
    case A2DP_MEDIA_CT_NON_A2DP:
      return A2DP_VendorGetTrackBitsPerSample(p_codec_info);
#endif
    default:
      break;
  }

  log::error("unsupported codec type 0x{:x}", codec_type);
  return -1;
}

int A2DP_GetTrackChannelCount(const uint8_t* p_codec_info) {
  tA2DP_CODEC_TYPE codec_type = A2DP_GetCodecType(p_codec_info);

  log::verbose("codec_type = 0x{:x}", codec_type);

  switch (codec_type) {
    case A2DP_MEDIA_CT_SBC:
      return A2DP_GetTrackChannelCountSbc(p_codec_info);
#if !defined(EXCLUDE_NONSTANDARD_CODECS)
    case A2DP_MEDIA_CT_AAC:
      return A2DP_GetTrackChannelCountAac(p_codec_info);
    case A2DP_MEDIA_CT_NON_A2DP:
      return A2DP_VendorGetTrackChannelCount(p_codec_info);
#endif
    default:
      break;
  }

  log::error("unsupported codec type 0x{:x}", codec_type);
  return -1;
}

int A2DP_GetSinkTrackChannelType(const uint8_t* p_codec_info) {
  tA2DP_CODEC_TYPE codec_type = A2DP_GetCodecType(p_codec_info);

  log::verbose("codec_type = 0x{:x}", codec_type);

  switch (codec_type) {
    case A2DP_MEDIA_CT_SBC:
      return A2DP_GetSinkTrackChannelTypeSbc(p_codec_info);
#if !defined(EXCLUDE_NONSTANDARD_CODECS)
    case A2DP_MEDIA_CT_AAC:
      return A2DP_GetSinkTrackChannelTypeAac(p_codec_info);
    case A2DP_MEDIA_CT_NON_A2DP:
      return A2DP_VendorGetSinkTrackChannelType(p_codec_info);
#endif
    default:
      break;
  }

  log::error("unsupported codec type 0x{:x}", codec_type);
  return -1;
}

bool A2DP_GetPacketTimestamp(const uint8_t* p_codec_info, const uint8_t* p_data,
                             uint32_t* p_timestamp) {
  tA2DP_CODEC_TYPE codec_type = A2DP_GetCodecType(p_codec_info);

  switch (codec_type) {
    case A2DP_MEDIA_CT_SBC:
      return A2DP_GetPacketTimestampSbc(p_codec_info, p_data, p_timestamp);
#if !defined(EXCLUDE_NONSTANDARD_CODECS)
    case A2DP_MEDIA_CT_AAC:
      return A2DP_GetPacketTimestampAac(p_codec_info, p_data, p_timestamp);
    case A2DP_MEDIA_CT_NON_A2DP:
      return A2DP_VendorGetPacketTimestamp(p_codec_info, p_data, p_timestamp);
#endif
    default:
      break;
  }

  log::error("unsupported codec type 0x{:x}", codec_type);
  return false;
}

bool A2DP_BuildCodecHeader(const uint8_t* p_codec_info, BT_HDR* p_buf,
                           uint16_t frames_per_packet) {
  tA2DP_CODEC_TYPE codec_type = A2DP_GetCodecType(p_codec_info);

  switch (codec_type) {
    case A2DP_MEDIA_CT_SBC:
      return A2DP_BuildCodecHeaderSbc(p_codec_info, p_buf, frames_per_packet);
#if !defined(EXCLUDE_NONSTANDARD_CODECS)
    case A2DP_MEDIA_CT_AAC:
      return A2DP_BuildCodecHeaderAac(p_codec_info, p_buf, frames_per_packet);
    case A2DP_MEDIA_CT_NON_A2DP:
      return A2DP_VendorBuildCodecHeader(p_codec_info, p_buf,
                                         frames_per_packet);
#endif
    default:
      break;
  }

  log::error("unsupported codec type 0x{:x}", codec_type);
  return false;
}

const tA2DP_ENCODER_INTERFACE* A2DP_GetEncoderInterface(
    const uint8_t* p_codec_info) {
  tA2DP_CODEC_TYPE codec_type = A2DP_GetCodecType(p_codec_info);

  log::verbose("codec_type = 0x{:x}", codec_type);

  if (::bluetooth::audio::a2dp::provider::supports_codec(
          A2DP_SourceCodecIndex(p_codec_info))) {
    return A2DP_GetEncoderInterfaceExt(p_codec_info);
  }

  switch (codec_type) {
    case A2DP_MEDIA_CT_SBC:
      return A2DP_GetEncoderInterfaceSbc(p_codec_info);
#if !defined(EXCLUDE_NONSTANDARD_CODECS)
    case A2DP_MEDIA_CT_AAC:
      return A2DP_GetEncoderInterfaceAac(p_codec_info);
    case A2DP_MEDIA_CT_NON_A2DP:
      return A2DP_VendorGetEncoderInterface(p_codec_info);
#endif
    default:
      break;
  }

  log::error("unsupported codec type 0x{:x}", codec_type);
  return NULL;
}

const tA2DP_DECODER_INTERFACE* A2DP_GetDecoderInterface(
    const uint8_t* p_codec_info) {
  tA2DP_CODEC_TYPE codec_type = A2DP_GetCodecType(p_codec_info);

  log::verbose("codec_type = 0x{:x}", codec_type);

  switch (codec_type) {
    case A2DP_MEDIA_CT_SBC:
      return A2DP_GetDecoderInterfaceSbc(p_codec_info);
#if !defined(EXCLUDE_NONSTANDARD_CODECS)
    case A2DP_MEDIA_CT_AAC:
      return A2DP_GetDecoderInterfaceAac(p_codec_info);
    case A2DP_MEDIA_CT_NON_A2DP:
      return A2DP_VendorGetDecoderInterface(p_codec_info);
#endif
    default:
      break;
  }

  log::error("unsupported codec type 0x{:x}", codec_type);
  return NULL;
}

bool A2DP_AdjustCodec(uint8_t* p_codec_info) {
  tA2DP_CODEC_TYPE codec_type = A2DP_GetCodecType(p_codec_info);

  switch (codec_type) {
    case A2DP_MEDIA_CT_SBC:
      return A2DP_AdjustCodecSbc(p_codec_info);
#if !defined(EXCLUDE_NONSTANDARD_CODECS)
    case A2DP_MEDIA_CT_AAC:
      return A2DP_AdjustCodecAac(p_codec_info);
    case A2DP_MEDIA_CT_NON_A2DP:
      return A2DP_VendorAdjustCodec(p_codec_info);
#endif
    default:
      break;
  }

  log::error("unsupported codec type 0x{:x}", codec_type);
  return false;
}

btav_a2dp_codec_index_t A2DP_SourceCodecIndex(const uint8_t* p_codec_info) {
  tA2DP_CODEC_TYPE codec_type = A2DP_GetCodecType(p_codec_info);

  log::verbose("codec_type = 0x{:x}", codec_type);

  auto ext_codec_index =
      bluetooth::audio::a2dp::provider::source_codec_index(p_codec_info);
  if (ext_codec_index.has_value()) {
    return ext_codec_index.value();
  }

  switch (codec_type) {
    case A2DP_MEDIA_CT_SBC:
      return A2DP_SourceCodecIndexSbc(p_codec_info);
#if !defined(EXCLUDE_NONSTANDARD_CODECS)
    case A2DP_MEDIA_CT_AAC:
      return A2DP_SourceCodecIndexAac(p_codec_info);
    case A2DP_MEDIA_CT_NON_A2DP:
      return A2DP_VendorSourceCodecIndex(p_codec_info);
#endif
    default:
      break;
  }

  log::error("unsupported codec type 0x{:x}", codec_type);
  return BTAV_A2DP_CODEC_INDEX_MAX;
}

btav_a2dp_codec_index_t A2DP_SinkCodecIndex(const uint8_t* p_codec_info) {
  tA2DP_CODEC_TYPE codec_type = A2DP_GetCodecType(p_codec_info);

  log::verbose("codec_type = 0x{:x}", codec_type);

  auto ext_codec_index =
      bluetooth::audio::a2dp::provider::sink_codec_index(p_codec_info);
  if (ext_codec_index.has_value()) {
    return ext_codec_index.value();
  }

  switch (codec_type) {
    case A2DP_MEDIA_CT_SBC:
      return A2DP_SinkCodecIndexSbc(p_codec_info);
#if !defined(EXCLUDE_NONSTANDARD_CODECS)
    case A2DP_MEDIA_CT_AAC:
      return A2DP_SinkCodecIndexAac(p_codec_info);
    case A2DP_MEDIA_CT_NON_A2DP:
      return A2DP_VendorSinkCodecIndex(p_codec_info);
#endif
    default:
      break;
  }

  log::error("unsupported codec type 0x{:x}", codec_type);
  return BTAV_A2DP_CODEC_INDEX_MAX;
}

const char* A2DP_CodecIndexStr(btav_a2dp_codec_index_t codec_index) {
  if ((codec_index >= BTAV_A2DP_CODEC_INDEX_SOURCE_EXT_MIN &&
       codec_index < BTAV_A2DP_CODEC_INDEX_SOURCE_EXT_MAX) ||
      (codec_index >= BTAV_A2DP_CODEC_INDEX_SINK_EXT_MIN &&
       codec_index < BTAV_A2DP_CODEC_INDEX_SINK_EXT_MAX)) {
    auto codec_index_str =
        bluetooth::audio::a2dp::provider::codec_index_str(codec_index);
    if (codec_index_str.has_value()) {
      return codec_index_str.value();
    }
  }

  switch (codec_index) {
    case BTAV_A2DP_CODEC_INDEX_SOURCE_SBC:
      return A2DP_CodecIndexStrSbc();
    case BTAV_A2DP_CODEC_INDEX_SINK_SBC:
      return A2DP_CodecIndexStrSbcSink();
#if !defined(EXCLUDE_NONSTANDARD_CODECS)
    case BTAV_A2DP_CODEC_INDEX_SOURCE_AAC:
      return A2DP_CodecIndexStrAac();
    case BTAV_A2DP_CODEC_INDEX_SINK_AAC:
      return A2DP_CodecIndexStrAacSink();
#endif
    default:
      break;
  }

#if !defined(EXCLUDE_NONSTANDARD_CODECS)
  if (codec_index < BTAV_A2DP_CODEC_INDEX_MAX)
    return A2DP_VendorCodecIndexStr(codec_index);
#endif

  return "UNKNOWN CODEC INDEX";
}

bool A2DP_InitCodecConfig(btav_a2dp_codec_index_t codec_index,
                          AvdtpSepConfig* p_cfg) {
  log::verbose("codec {}", A2DP_CodecIndexStr(codec_index));

  /* Default: no content protection info */
  p_cfg->num_protect = 0;
  p_cfg->protect_info[0] = 0;

  if (::bluetooth::audio::a2dp::provider::supports_codec(codec_index)) {
    return ::bluetooth::audio::a2dp::provider::codec_info(
        codec_index, nullptr, p_cfg->codec_info, nullptr);
  }

  switch (codec_index) {
    case BTAV_A2DP_CODEC_INDEX_SOURCE_SBC:
      return A2DP_InitCodecConfigSbc(p_cfg);
    case BTAV_A2DP_CODEC_INDEX_SINK_SBC:
      return A2DP_InitCodecConfigSbcSink(p_cfg);
#if !defined(EXCLUDE_NONSTANDARD_CODECS)
    case BTAV_A2DP_CODEC_INDEX_SOURCE_AAC:
      return A2DP_InitCodecConfigAac(p_cfg);
    case BTAV_A2DP_CODEC_INDEX_SINK_AAC:
      return A2DP_InitCodecConfigAacSink(p_cfg);
#endif
    default:
      break;
  }

#if !defined(EXCLUDE_NONSTANDARD_CODECS)
  if (codec_index < BTAV_A2DP_CODEC_INDEX_MAX)
    return A2DP_VendorInitCodecConfig(codec_index, p_cfg);
#endif

  return false;
}

std::string A2DP_CodecInfoString(const uint8_t* p_codec_info) {
  tA2DP_CODEC_TYPE codec_type = A2DP_GetCodecType(p_codec_info);

  switch (codec_type) {
    case A2DP_MEDIA_CT_SBC:
      return A2DP_CodecInfoStringSbc(p_codec_info);
#if !defined(EXCLUDE_NONSTANDARD_CODECS)
    case A2DP_MEDIA_CT_AAC:
      return A2DP_CodecInfoStringAac(p_codec_info);
    case A2DP_MEDIA_CT_NON_A2DP:
      return A2DP_VendorCodecInfoString(p_codec_info);
#endif
    default:
      break;
  }

  return "Unsupported codec type: " + loghex(codec_type);
}

int A2DP_GetEecoderEffectiveFrameSize(const uint8_t* p_codec_info) {
  const tA2DP_ENCODER_INTERFACE* a2dp_encoder_interface =
      A2DP_GetEncoderInterface(p_codec_info);
  return a2dp_encoder_interface
             ? a2dp_encoder_interface->get_effective_frame_size()
             : 0;
}
