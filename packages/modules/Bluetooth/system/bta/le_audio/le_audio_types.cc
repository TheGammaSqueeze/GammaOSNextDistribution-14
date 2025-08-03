/*
 * Copyright 2020 HIMSA II K/S - www.himsa.com. Represented by EHIMA -
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

/*
 * This file contains definitions for Basic Audio Profile / Audio Stream Control
 * and Published Audio Capabilities definitions, structures etc.
 */

#include "le_audio_types.h"

#include <base/logging.h>
#include <base/strings/string_number_conversions.h>

#include "audio_hal_client/audio_hal_client.h"
#include "common/strings.h"
#include "internal_include/bt_trace.h"
#include "stack/include/bt_types.h"

namespace le_audio {
using types::acs_ac_record;
using types::LeAudioContextType;

namespace set_configurations {
using set_configurations::CodecConfigSetting;
using types::CodecLocation;
using types::kLeAudioCodingFormatLC3;
using types::kLeAudioDirectionSink;
using types::kLeAudioDirectionSource;
using types::LeAudioCoreCodecConfig;

static uint8_t min_req_devices_cnt(
    const AudioSetConfiguration* audio_set_conf) {
  std::pair<uint8_t /* sink */, uint8_t /* source */> snk_src_pair(0, 0);

  for (auto ent : (*audio_set_conf).confs) {
    if (ent.direction == kLeAudioDirectionSink)
      snk_src_pair.first += ent.device_cnt;
    if (ent.direction == kLeAudioDirectionSource)
      snk_src_pair.second += ent.device_cnt;
  }

  return std::max(snk_src_pair.first, snk_src_pair.second);
}

static uint8_t min_req_devices_cnt(
    const AudioSetConfigurations* audio_set_confs) {
  uint8_t curr_min_req_devices_cnt = 0xff;

  for (auto ent : *audio_set_confs) {
    uint8_t req_devices_cnt = min_req_devices_cnt(ent);
    if (req_devices_cnt < curr_min_req_devices_cnt)
      curr_min_req_devices_cnt = req_devices_cnt;
  }

  return curr_min_req_devices_cnt;
}

void get_cis_count(LeAudioContextType context_type, int expected_device_cnt,
                   types::LeAudioConfigurationStrategy strategy,
                   int avail_group_ase_snk_cnt, int avail_group_ase_src_count,
                   uint8_t& out_cis_count_bidir,
                   uint8_t& out_cis_count_unidir_sink,
                   uint8_t& out_cis_count_unidir_source) {
  LOG_INFO(
      " %s strategy %d, group avail sink ases: %d, group avail source ases %d "
      "expected_device_count %d",
      bluetooth::common::ToString(context_type).c_str(),
      static_cast<int>(strategy), avail_group_ase_snk_cnt,
      avail_group_ase_src_count, expected_device_cnt);

  bool is_bidirectional = types::kLeAudioContextAllBidir.test(context_type);

  switch (strategy) {
    case types::LeAudioConfigurationStrategy::MONO_ONE_CIS_PER_DEVICE:
    /* This strategy is for the CSIS topology, e.g. two earbuds which are both
     * connected with a Phone
     */
    case types::LeAudioConfigurationStrategy::STEREO_ONE_CIS_PER_DEVICE:
      /* This strategy is for e.g. the banded headphones */
      if (is_bidirectional) {
        if ((avail_group_ase_snk_cnt > 0) && (avail_group_ase_src_count) > 0) {
          /* Prepare CIG to enable all microphones */
          out_cis_count_bidir = expected_device_cnt;
        } else {
          if (avail_group_ase_snk_cnt > 0) {
            out_cis_count_unidir_sink = expected_device_cnt;
          } else if (avail_group_ase_src_count > 0) {
            out_cis_count_unidir_source = expected_device_cnt;
          }
        }
      } else {
        out_cis_count_unidir_sink = expected_device_cnt;
      }

      break;
    case types::LeAudioConfigurationStrategy::STEREO_TWO_CISES_PER_DEVICE:
      /* This strategy is for the old TWS topology. e.g. one earbud connected to
       * the Phone but each channel is carried in separate CIS
       */
      if (is_bidirectional) {
        if ((avail_group_ase_snk_cnt > 0) && (avail_group_ase_src_count) > 0) {
          /* Prepare CIG to enable all microphones per device */
          out_cis_count_bidir = expected_device_cnt;
          out_cis_count_unidir_sink = expected_device_cnt;
        } else {
          if (avail_group_ase_snk_cnt > 0) {
            out_cis_count_unidir_sink = 2 * expected_device_cnt;
          } else if (avail_group_ase_src_count > 0) {
            out_cis_count_unidir_source = 2 * expected_device_cnt;
          }
        }
      } else {
        out_cis_count_unidir_sink = 2 * expected_device_cnt;
      }
      break;
    case types::LeAudioConfigurationStrategy::RFU:
      LOG_ERROR("Should not happen;");
      break;
  }

  LOG_INFO(
      "Required cis count: Bi-Directional: %d, Uni-Directional Sink: %d, "
      "Uni-Directional Source: %d",
      out_cis_count_bidir, out_cis_count_unidir_sink,
      out_cis_count_unidir_source);
}

bool check_if_may_cover_scenario(const AudioSetConfigurations* audio_set_confs,
                                 uint8_t group_size) {
  if (!audio_set_confs) {
    LOG(ERROR) << __func__ << ", no audio requirements for group";
    return false;
  }

  return group_size >= min_req_devices_cnt(audio_set_confs);
}

bool check_if_may_cover_scenario(const AudioSetConfiguration* audio_set_conf,
                                 uint8_t group_size) {
  if (!audio_set_conf) {
    LOG(ERROR) << __func__ << ", no audio requirement for group";
    return false;
  }

  return group_size >= min_req_devices_cnt(audio_set_conf);
}

uint8_t get_num_of_devices_in_configuration(
    const AudioSetConfiguration* audio_set_conf) {
  return min_req_devices_cnt(audio_set_conf);
}

static bool IsCodecConfigCoreSupported(const types::LeAudioLtvMap& pacs,
                                       const types::LeAudioLtvMap& reqs) {
  auto caps = pacs.GetAsCoreCodecCapabilities();
  auto config = reqs.GetAsCoreCodecConfig();

  /* Sampling frequency */
  if (!caps.HasSupportedSamplingFrequencies() || !config.sampling_frequency) {
    LOG_DEBUG("Missing supported sampling frequencies capability");
    return false;
  }
  if (!caps.IsSamplingFrequencyConfigSupported(
          config.sampling_frequency.value())) {
    LOG_DEBUG("Cfg: SamplingFrequency= 0x%04x",
              config.sampling_frequency.value());
    LOG_DEBUG("Cap: SupportedSamplingFrequencies= 0x%04x",
              caps.supported_sampling_frequencies.value());
    LOG_DEBUG("Sampling frequency not supported");
    return false;
  }

  /* Channel counts */
  if (!caps.IsAudioChannelCountsSupported(
          config.GetChannelCountPerIsoStream())) {
    LOG_DEBUG("Cfg: Allocated channel count= 0x%04x",
              config.GetChannelCountPerIsoStream());
    LOG_DEBUG("Cap: Supported channel counts= 0x%04x",
              caps.supported_audio_channel_counts.value_or(1));
    LOG_DEBUG("Channel count not supported");
    return false;
  }

  /* Frame duration */
  if (!caps.HasSupportedFrameDurations() || !config.frame_duration) {
    LOG_DEBUG("Missing supported frame durations capability");
    return false;
  }
  if (!caps.IsFrameDurationConfigSupported(config.frame_duration.value())) {
    LOG_DEBUG("Cfg: FrameDuration= 0x%04x", config.frame_duration.value());
    LOG_DEBUG("Cap: SupportedFrameDurations= 0x%04x",
              caps.supported_frame_durations.value());
    LOG_DEBUG("Frame duration not supported");
    return false;
  }

  /* Octets per frame */
  if (!caps.HasSupportedOctetsPerCodecFrame() ||
      !config.octets_per_codec_frame) {
    LOG_DEBUG("Missing supported octets per codec frame");
    return false;
  }
  if (!caps.IsOctetsPerCodecFrameConfigSupported(
          config.octets_per_codec_frame.value())) {
    LOG_DEBUG("Cfg: Octets per frame=%d",
              config.octets_per_codec_frame.value());
    LOG_DEBUG("Cap: Min octets per frame=%d",
              caps.supported_min_octets_per_codec_frame.value());
    LOG_DEBUG("Cap: Max octets per frame=%d",
              caps.supported_max_octets_per_codec_frame.value());
    LOG_DEBUG("Octets per codec frame outside the capabilities");
    return false;
  }

  return true;
}

bool IsCodecConfigSettingSupported(
    const acs_ac_record& pac, const CodecConfigSetting& codec_config_setting) {
  const auto& codec_id = codec_config_setting.id;

  if (codec_id != pac.codec_id) return false;

  LOG_DEBUG(": Settings for format: 0x%02x ", codec_id.coding_format);

  switch (codec_id.coding_format) {
    case kLeAudioCodingFormatLC3:
      return IsCodecConfigCoreSupported(pac.codec_spec_caps,
                                        codec_config_setting.params);
    default:
      return false;
  }
}

uint32_t CodecConfigSetting::GetSamplingFrequencyHz() const {
  switch (id.coding_format) {
    case kLeAudioCodingFormatLC3:
      return params.GetAsCoreCodecConfig().GetSamplingFrequencyHz();
    default:
      LOG_WARN(", invalid codec id: 0x%02x", id.coding_format);
      return 0;
  }
};

uint32_t CodecConfigSetting::GetDataIntervalUs() const {
  switch (id.coding_format) {
    case kLeAudioCodingFormatLC3:
      return params.GetAsCoreCodecConfig().GetFrameDurationUs();
    default:
      LOG_WARN(", invalid codec id: 0x%02x", id.coding_format);
      return 0;
  }
};

uint8_t CodecConfigSetting::GetBitsPerSample() const {
  switch (id.coding_format) {
    case kLeAudioCodingFormatLC3:
      /* XXX LC3 supports 16, 24, 32 */
      return 16;
    default:
      LOG_WARN(", invalid codec id: 0x%02x", id.coding_format);
      return 0;
  }
};
}  // namespace set_configurations

namespace types {
/* Helper map for matching various frequency notations */
const std::map<uint8_t, uint32_t> LeAudioCoreCodecConfig::sampling_freq_map = {
    {codec_spec_conf::kLeAudioSamplingFreq8000Hz,
     LeAudioCodecConfiguration::kSampleRate8000},
    {codec_spec_conf::kLeAudioSamplingFreq16000Hz,
     LeAudioCodecConfiguration::kSampleRate16000},
    {codec_spec_conf::kLeAudioSamplingFreq24000Hz,
     LeAudioCodecConfiguration::kSampleRate24000},
    {codec_spec_conf::kLeAudioSamplingFreq32000Hz,
     LeAudioCodecConfiguration::kSampleRate32000},
    {codec_spec_conf::kLeAudioSamplingFreq44100Hz,
     LeAudioCodecConfiguration::kSampleRate44100},
    {codec_spec_conf::kLeAudioSamplingFreq48000Hz,
     LeAudioCodecConfiguration::kSampleRate48000}};

/* Helper map for matching various frame durations notations */
const std::map<uint8_t, uint32_t> LeAudioCoreCodecConfig::frame_duration_map = {
    {codec_spec_conf::kLeAudioCodecFrameDur7500us,
     LeAudioCodecConfiguration::kInterval7500Us},
    {codec_spec_conf::kLeAudioCodecFrameDur10000us,
     LeAudioCodecConfiguration::kInterval10000Us}};

std::string CapabilityTypeToStr(const uint8_t& type) {
  switch (type) {
    case codec_spec_caps::kLeAudioLtvTypeSupportedSamplingFrequencies:
      return "Supported Sampling Frequencies";
    case codec_spec_caps::kLeAudioLtvTypeSupportedFrameDurations:
      return "Supported Frame Durations";
    case codec_spec_caps::kLeAudioLtvTypeSupportedAudioChannelCounts:
      return "Supported Audio Channel Count";
    case codec_spec_caps::kLeAudioLtvTypeSupportedOctetsPerCodecFrame:
      return "Supported Octets Per Codec Frame";
    case codec_spec_caps::kLeAudioLtvTypeSupportedMaxCodecFramesPerSdu:
      return "Supported Max Codec Frames Per SDU";
    default:
      return "Unknown";
  }
}

std::string CapabilityValueToStr(const uint8_t& type,
                                 const std::vector<uint8_t>& value) {
  std::string string = "";

  switch (type) {
    case codec_spec_conf::kLeAudioLtvTypeSamplingFreq: {
      if (value.size() != 2) {
        return "Invalid size";
      }

      uint16_t u16_val = VEC_UINT8_TO_UINT16(value);

      if (u16_val & codec_spec_caps::kLeAudioSamplingFreq8000Hz) {
        string += "8";
      }
      if (u16_val & codec_spec_caps::kLeAudioSamplingFreq11025Hz) {
        string += std::string((string.empty() ? "" : "|")) + "11.025";
      }
      if (u16_val & codec_spec_caps::kLeAudioSamplingFreq16000Hz) {
        string += std::string((string.empty() ? "" : "|")) + "16";
      }
      if (u16_val & codec_spec_caps::kLeAudioSamplingFreq22050Hz) {
        string += std::string((string.empty() ? "" : "|")) + "22.050";
      }
      if (u16_val & codec_spec_caps::kLeAudioSamplingFreq24000Hz) {
        string += std::string((string.empty() ? "" : "|")) + "24";
      }
      if (u16_val & codec_spec_caps::kLeAudioSamplingFreq32000Hz) {
        string += std::string((string.empty() ? "" : "|")) + "32";
      }
      if (u16_val & codec_spec_caps::kLeAudioSamplingFreq44100Hz) {
        string += std::string((string.empty() ? "" : "|")) + "44.1";
      }
      if (u16_val & codec_spec_caps::kLeAudioSamplingFreq48000Hz) {
        string += std::string((string.empty() ? "" : "|")) + "48";
      }
      if (u16_val & codec_spec_caps::kLeAudioSamplingFreq88200Hz) {
        string += std::string((string.empty() ? "" : "|")) + "88.2";
      }
      if (u16_val & codec_spec_caps::kLeAudioSamplingFreq96000Hz) {
        string += std::string((string.empty() ? "" : "|")) + "96";
      }
      if (u16_val & codec_spec_caps::kLeAudioSamplingFreq176400Hz) {
        string += std::string((string.empty() ? "" : "|")) + "176.4";
      }
      if (u16_val & codec_spec_caps::kLeAudioSamplingFreq192000Hz) {
        string += std::string((string.empty() ? "" : "|")) + "192";
      }
      if (u16_val & codec_spec_caps::kLeAudioSamplingFreq384000Hz) {
        string += std::string((string.empty() ? "" : "|")) + "384";
      }

      return string += " [kHz]\n";
    }
    case codec_spec_conf::kLeAudioLtvTypeFrameDuration: {
      if (value.size() != 1) {
        return "Invalid size";
      }

      uint8_t u8_val = VEC_UINT8_TO_UINT8(value);

      if (u8_val & codec_spec_caps::kLeAudioCodecFrameDur7500us) {
        string += "7.5";
      }
      if (u8_val & codec_spec_caps::kLeAudioCodecFrameDur10000us) {
        string += std::string((string.empty() ? "" : "|")) + "10";
      }
      if (u8_val & codec_spec_caps::kLeAudioCodecFrameDurPrefer7500us) {
        string += std::string((string.empty() ? "" : "|")) + "7.5 preferred";
      }
      if (u8_val & codec_spec_caps::kLeAudioCodecFrameDurPrefer10000us) {
        string += std::string((string.empty() ? "" : "|")) + "10 preferred";
      }

      return string += " [ms]\n";
    }
    case codec_spec_conf::kLeAudioLtvTypeAudioChannelAllocation: {
      if (value.size() != 1) {
        return "Invalid size";
      }

      uint8_t u8_val = VEC_UINT8_TO_UINT8(value);

      if (u8_val & codec_spec_caps::kLeAudioCodecChannelCountNone) {
        string += "0";
      }
      if (u8_val & codec_spec_caps::kLeAudioCodecChannelCountSingleChannel) {
        string += std::string((string.empty() ? "" : "|")) + "1";
      }
      if (u8_val & codec_spec_caps::kLeAudioCodecChannelCountTwoChannel) {
        string += std::string((string.empty() ? "" : "|")) + "2";
      }
      if (u8_val & codec_spec_caps::kLeAudioCodecChannelCountThreeChannel) {
        string += std::string((string.empty() ? "" : "|")) + "3";
      }
      if (u8_val & codec_spec_caps::kLeAudioCodecChannelCountFourChannel) {
        string += std::string((string.empty() ? "" : "|")) + "4";
      }
      if (u8_val & codec_spec_caps::kLeAudioCodecChannelCountFiveChannel) {
        string += std::string((string.empty() ? "" : "|")) + "5";
      }
      if (u8_val & codec_spec_caps::kLeAudioCodecChannelCountSixChannel) {
        string += std::string((string.empty() ? "" : "|")) + "6";
      }
      if (u8_val & codec_spec_caps::kLeAudioCodecChannelCountSevenChannel) {
        string += std::string((string.empty() ? "" : "|")) + "7";
      }
      if (u8_val & codec_spec_caps::kLeAudioCodecChannelCountEightChannel) {
        string += std::string((string.empty() ? "" : "|")) + "8";
      }

      return string += " channel/s\n";
    }
    case codec_spec_conf::kLeAudioLtvTypeOctetsPerCodecFrame: {
      if (value.size() != 4) {
        return "Invalid size";
      }

      uint16_t u16_min_number_of_octets = VEC_UINT8_TO_UINT16(value);
      uint16_t u16_max_number_of_octets =
          OFF_VEC_UINT8_TO_UINT16(value, sizeof(u16_min_number_of_octets));

      string += "Minimum: " + std::to_string(u16_min_number_of_octets);
      string += ", Maximum: " + std::to_string(u16_max_number_of_octets) + "\n";

      return string;
    }
    case codec_spec_conf::kLeAudioLtvTypeCodecFrameBlocksPerSdu: {
      if (value.size() != 1) {
        return "Invalid size";
      }

      uint8_t u8_val = VEC_UINT8_TO_UINT8(value);

      string += std::to_string(u8_val) + " frame/s\n";

      return string;
    }
    default:
      return base::HexEncode(value.data(), value.size()) + "\n";
  }
}

std::string CodecCapabilitiesLtvFormat(const uint8_t& type,
                                       const std::vector<uint8_t>& value) {
  std::string string = "";

  string += CapabilityTypeToStr(type) + ": ";
  string += CapabilityValueToStr(type, value);

  return string;
}

std::optional<std::vector<uint8_t>> LeAudioLtvMap::Find(uint8_t type) const {
  auto iter =
      std::find_if(values.cbegin(), values.cend(),
                   [type](const auto& value) { return value.first == type; });

  if (iter == values.cend()) return std::nullopt;

  return iter->second;
}

uint8_t* LeAudioLtvMap::RawPacket(uint8_t* p_buf) const {
  for (auto const& value : values) {
    UINT8_TO_STREAM(p_buf, value.second.size() + 1);
    UINT8_TO_STREAM(p_buf, value.first);
    ARRAY_TO_STREAM(p_buf, value.second.data(),
                    static_cast<int>(value.second.size()));
  }

  return p_buf;
}

std::vector<uint8_t> LeAudioLtvMap::RawPacket() const {
  std::vector<uint8_t> data(RawPacketSize());
  RawPacket(data.data());
  return data;
}

void LeAudioLtvMap::Append(const LeAudioLtvMap& other) {
  /* This will override values for the already existing keys */
  for (auto& el : other.values) {
    values[el.first] = el.second;
  }
}

LeAudioLtvMap LeAudioLtvMap::Parse(const uint8_t* p_value, uint8_t len,
                                   bool& success) {
  LeAudioLtvMap ltv_map;

  if (len > 0) {
    const auto p_value_end = p_value + len;

    while ((p_value_end - p_value) > 0) {
      uint8_t ltv_len;
      STREAM_TO_UINT8(ltv_len, p_value);

      // Unusual, but possible case
      if (ltv_len == 0) continue;

      if (p_value_end < (p_value + ltv_len)) {
        LOG(ERROR) << __func__
                   << " Invalid ltv_len: " << static_cast<int>(ltv_len);
        success = false;
        return LeAudioLtvMap();
      }

      uint8_t ltv_type;
      STREAM_TO_UINT8(ltv_type, p_value);
      ltv_len -= sizeof(ltv_type);

      const auto p_temp = p_value;
      p_value += ltv_len;

      std::vector<uint8_t> ltv_value(p_temp, p_value);
      ltv_map.values.emplace(ltv_type, std::move(ltv_value));
    }
  }

  success = true;
  return ltv_map;
}

size_t LeAudioLtvMap::RawPacketSize() const {
  size_t bytes = 0;

  for (auto const& value : values) {
    bytes += (/* ltv_len + ltv_type */ 2 + value.second.size());
  }

  return bytes;
}

std::string LeAudioLtvMap::ToString(
    const std::string& indent_string,
    std::string (*format)(const uint8_t&, const std::vector<uint8_t>&)) const {
  std::string debug_str;

  for (const auto& value : values) {
    std::stringstream sstream;

    if (format == nullptr) {
      sstream << indent_string + "type: " << std::to_string(value.first)
              << "\tlen: " << std::to_string(value.second.size()) << "\tdata: "
              << base::HexEncode(value.second.data(), value.second.size()) +
                     "\n";
    } else {
      sstream << indent_string + format(value.first, value.second);
    }

    debug_str += sstream.str();
  }

  return debug_str;
}

const struct LeAudioCoreCodecConfig& LeAudioLtvMap::GetAsCoreCodecConfig()
    const {
  ASSERT_LOG(!core_capabilities, "LTVs were already parsed for capabilities!");

  if (!core_config) {
    core_config = LtvMapToCoreCodecConfig(*this);
  }
  return *core_config;
}

const struct LeAudioCoreCodecCapabilities&
LeAudioLtvMap::GetAsCoreCodecCapabilities() const {
  ASSERT_LOG(!core_config, "LTVs were already parsed for configurations!");

  if (!core_capabilities) {
    core_capabilities = LtvMapToCoreCodecCapabilities(*this);
  }
  return *core_capabilities;
}

}  // namespace types

void AppendMetadataLtvEntryForCcidList(std::vector<uint8_t>& metadata,
                                       const std::vector<uint8_t>& ccid_list) {
  if (ccid_list.size() == 0) {
    LOG_WARN("Empty CCID list.");
    return;
  }

  metadata.push_back(
      static_cast<uint8_t>(types::kLeAudioMetadataTypeLen + ccid_list.size()));
  metadata.push_back(static_cast<uint8_t>(types::kLeAudioMetadataTypeCcidList));

  metadata.insert(metadata.end(), ccid_list.begin(), ccid_list.end());
}

void AppendMetadataLtvEntryForStreamingContext(
    std::vector<uint8_t>& metadata, types::AudioContexts context_type) {
  std::vector<uint8_t> streaming_context_ltv_entry;

  streaming_context_ltv_entry.resize(
      types::kLeAudioMetadataTypeLen + types::kLeAudioMetadataLenLen +
      types::kLeAudioMetadataStreamingAudioContextLen);
  uint8_t* streaming_context_ltv_entry_buf = streaming_context_ltv_entry.data();

  UINT8_TO_STREAM(streaming_context_ltv_entry_buf,
                  types::kLeAudioMetadataTypeLen +
                      types::kLeAudioMetadataStreamingAudioContextLen);
  UINT8_TO_STREAM(streaming_context_ltv_entry_buf,
                  types::kLeAudioMetadataTypeStreamingAudioContext);
  UINT16_TO_STREAM(streaming_context_ltv_entry_buf, context_type.value());

  metadata.insert(metadata.end(), streaming_context_ltv_entry.begin(),
                  streaming_context_ltv_entry.end());
}

uint8_t GetMaxCodecFramesPerSduFromPac(const acs_ac_record* pac) {
  auto tlv_ent = pac->codec_spec_caps.Find(
      codec_spec_caps::kLeAudioLtvTypeSupportedMaxCodecFramesPerSdu);

  if (tlv_ent) return VEC_UINT8_TO_UINT8(tlv_ent.value());

  return 1;
}

namespace types {
std::ostream& operator<<(std::ostream& os, const CisState& state) {
  static const char* char_value_[5] = {"IDLE", "ASSIGNED", "CONNECTING",
                                       "CONNECTED", "DISCONNECTING"};

  os << char_value_[static_cast<uint8_t>(state)] << " ("
     << "0x" << std::setfill('0') << std::setw(2) << static_cast<int>(state)
     << ")";
  return os;
}
std::ostream& operator<<(std::ostream& os, const DataPathState& state) {
  static const char* char_value_[4] = {"IDLE", "CONFIGURING", "CONFIGURED",
                                       "REMOVING"};

  os << char_value_[static_cast<uint8_t>(state)] << " ("
     << "0x" << std::setfill('0') << std::setw(2) << static_cast<int>(state)
     << ")";
  return os;
}
std::ostream& operator<<(std::ostream& os, const types::CigState& state) {
  static const char* char_value_[5] = {"NONE", "CREATING", "CREATED",
                                       "REMOVING", "RECOVERING"};

  os << char_value_[static_cast<uint8_t>(state)] << " ("
     << "0x" << std::setfill('0') << std::setw(2) << static_cast<int>(state)
     << ")";
  return os;
}
std::ostream& operator<<(std::ostream& os, const types::AseState& state) {
  static const char* char_value_[7] = {
      "IDLE",      "CODEC_CONFIGURED", "QOS_CONFIGURED", "ENABLING",
      "STREAMING", "DISABLING",        "RELEASING",
  };

  os << char_value_[static_cast<uint8_t>(state)] << " ("
     << "0x" << std::setfill('0') << std::setw(2) << static_cast<int>(state)
     << ")";
  return os;
}

std::ostream& operator<<(std::ostream& os,
                         const types::LeAudioCoreCodecConfig& config) {
  os << " LeAudioCoreCodecConfig(SamplFreq="
     << loghex(*config.sampling_frequency)
     << ", FrameDur=" << loghex(*config.frame_duration)
     << ", OctetsPerFrame=" << int(*config.octets_per_codec_frame)
     << ", CodecFramesBlocksPerSDU=" << int(*config.codec_frames_blocks_per_sdu)
     << ", AudioChanLoc=" << loghex(*config.audio_channel_allocation) << ")";
  return os;
}

std::string contextTypeToStr(const LeAudioContextType& context) {
  switch (context) {
    case LeAudioContextType::UNINITIALIZED:
      return "UNINITIALIZED";
    case LeAudioContextType::UNSPECIFIED:
      return "UNSPECIFIED";
    case LeAudioContextType::CONVERSATIONAL:
      return "CONVERSATIONAL";
    case LeAudioContextType::MEDIA:
      return "MEDIA";
    case LeAudioContextType::GAME:
      return "GAME";
    case LeAudioContextType::INSTRUCTIONAL:
      return "INSTRUCTIONAL";
    case LeAudioContextType::VOICEASSISTANTS:
      return "VOICEASSISTANTS";
    case LeAudioContextType::LIVE:
      return "LIVE";
    case LeAudioContextType::SOUNDEFFECTS:
      return "SOUNDEFFECTS";
    case LeAudioContextType::NOTIFICATIONS:
      return "NOTIFICATIONS";
    case LeAudioContextType::RINGTONE:
      return "RINGTONE";
    case LeAudioContextType::ALERTS:
      return "ALERTS";
    case LeAudioContextType::EMERGENCYALARM:
      return "EMERGENCYALARM";
    default:
      return "UNKNOWN";
  }
}

std::ostream& operator<<(std::ostream& os, const LeAudioContextType& context) {
  os << contextTypeToStr(context);
  return os;
}

AudioContexts operator|(std::underlying_type<LeAudioContextType>::type lhs,
                        const LeAudioContextType rhs) {
  using T = std::underlying_type<LeAudioContextType>::type;
  return AudioContexts(lhs | static_cast<T>(rhs));
}

AudioContexts& operator|=(AudioContexts& lhs, AudioContexts const& rhs) {
  lhs = AudioContexts(lhs.value() | rhs.value());
  return lhs;
}

AudioContexts& operator&=(AudioContexts& lhs, AudioContexts const& rhs) {
  lhs = AudioContexts(lhs.value() & rhs.value());
  return lhs;
}

std::string ToHexString(const LeAudioContextType& value) {
  using T = std::underlying_type<LeAudioContextType>::type;
  return bluetooth::common::ToHexString(static_cast<T>(value));
}

std::string AudioContexts::to_string() const {
  std::stringstream s;
  for (auto ctx : le_audio::types::kLeAudioContextAllTypesArray) {
    if (test(ctx)) {
      if (s.tellp() != 0) s << " | ";
      s << ctx;
    }
  }
  s << " (" << bluetooth::common::ToHexString(mValue) << ")";
  return s.str();
}

std::ostream& operator<<(std::ostream& os, const AudioContexts& contexts) {
  os << contexts.to_string();
  return os;
}

template <typename T>
const T& BidirectionalPair<T>::get(uint8_t direction) const {
  ASSERT_LOG(
      direction < types::kLeAudioDirectionBoth,
      "Unsupported complex direction. Consider using get_bidirectional<>() "
      "instead.");
  return (direction == types::kLeAudioDirectionSink) ? sink : source;
}

template <typename T>
T& BidirectionalPair<T>::get(uint8_t direction) {
  ASSERT_LOG(direction < types::kLeAudioDirectionBoth,
             "Unsupported complex direction. Reference to a single complex"
             " direction value is not supported.");
  return (direction == types::kLeAudioDirectionSink) ? sink : source;
}

/* Bidirectional getter trait for AudioContexts bidirectional pair */
template <>
AudioContexts get_bidirectional(BidirectionalPair<AudioContexts> p) {
  return p.sink | p.source;
}

template <>
std::vector<uint8_t> get_bidirectional(
    BidirectionalPair<std::vector<uint8_t>> bidir) {
  std::vector<uint8_t> res = bidir.sink;
  res.insert(std::end(res), std::begin(bidir.source), std::end(bidir.source));
  return res;
}

template <>
AudioLocations get_bidirectional(BidirectionalPair<AudioLocations> bidir) {
  return bidir.sink | bidir.source;
}

template struct BidirectionalPair<AudioContexts>;
template struct BidirectionalPair<AudioLocations>;
template struct BidirectionalPair<CisType>;
template struct BidirectionalPair<ase*>;
template struct BidirectionalPair<std::string>;
template struct BidirectionalPair<std::vector<uint8_t>>;
template struct BidirectionalPair<stream_configuration>;
template struct BidirectionalPair<stream_parameters>;
template struct BidirectionalPair<uint16_t>;

}  // namespace types
}  // namespace le_audio
