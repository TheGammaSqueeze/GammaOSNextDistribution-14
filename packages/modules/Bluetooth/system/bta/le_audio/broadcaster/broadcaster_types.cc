/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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

#include "broadcaster_types.h"

#include <lc3.h>

#include <vector>

#include "bta_le_audio_broadcaster_api.h"
#include "btm_ble_api_types.h"
#include "internal_include/stack_config.h"
#include "osi/include/properties.h"
#include "stack/include/bt_types.h"

using bluetooth::le_audio::BasicAudioAnnouncementBisConfig;
using bluetooth::le_audio::BasicAudioAnnouncementCodecConfig;
using bluetooth::le_audio::BasicAudioAnnouncementData;
using bluetooth::le_audio::BasicAudioAnnouncementSubgroup;
using le_audio::types::LeAudioContextType;

namespace le_audio {
namespace broadcaster {

static void EmitHeader(const BasicAudioAnnouncementData& announcement_data,
                       std::vector<uint8_t>& data) {
  size_t old_size = data.size();
  data.resize(old_size + 3);

  // Set the cursor behind the old data
  uint8_t* p_value = data.data() + old_size;

  UINT24_TO_STREAM(p_value, announcement_data.presentation_delay_us);
}

static void EmitCodecConfiguration(
    const BasicAudioAnnouncementCodecConfig& config, std::vector<uint8_t>& data,
    const BasicAudioAnnouncementCodecConfig* lower_lvl_config) {
  size_t old_size = data.size();

  // Add 5 for full, or 1 for short Codec ID
  uint8_t codec_config_length = 5;

  auto ltv = types::LeAudioLtvMap(config.codec_specific_params);
  auto ltv_raw_sz = ltv.RawPacketSize();

  // Add 1 for the codec spec. config length + config spec. data itself
  codec_config_length += 1 + ltv_raw_sz;

  // Resize and set the cursor behind the old data
  data.resize(old_size + codec_config_length);
  uint8_t* p_value = data.data() + old_size;

  // Codec ID
  UINT8_TO_STREAM(p_value, config.codec_id);
  UINT16_TO_STREAM(p_value, config.vendor_company_id);
  UINT16_TO_STREAM(p_value, config.vendor_codec_id);

  // Codec specific config length and data
  UINT8_TO_STREAM(p_value, ltv_raw_sz);
  p_value = ltv.RawPacket(p_value);
}

static void EmitMetadata(
    const std::map<uint8_t, std::vector<uint8_t>>& metadata,
    std::vector<uint8_t>& data) {
  auto ltv = types::LeAudioLtvMap(metadata);
  auto ltv_raw_sz = ltv.RawPacketSize();

  size_t old_size = data.size();
  data.resize(old_size + ltv_raw_sz + 1);

  // Set the cursor behind the old data
  uint8_t* p_value = data.data() + old_size;

  UINT8_TO_STREAM(p_value, ltv_raw_sz);
  if (ltv_raw_sz > 0) {
    p_value = ltv.RawPacket(p_value);
  }
}

static void EmitBroadcastName(const std::string& name,
                              std::vector<uint8_t>& data) {
  int name_len = name.length();
  size_t old_size = data.size();
  data.resize(old_size + name_len + 2);

  // Set the cursor behind the old data
  uint8_t* p_value = data.data() + old_size;
  UINT8_TO_STREAM(p_value, name_len + 1);
  UINT8_TO_STREAM(p_value, BTM_BLE_AD_TYPE_BROADCAST_NAME);

  std::vector<uint8_t> vec(name.begin(), name.end());
  ARRAY_TO_STREAM(p_value, vec.data(), name_len);
}

static void EmitBisConfigs(
    const std::vector<BasicAudioAnnouncementBisConfig>& bis_configs,
    std::vector<uint8_t>& data) {
  // Emit each BIS config - that's the level 3 data
  for (auto const& bis_config : bis_configs) {
    auto ltv = types::LeAudioLtvMap(bis_config.codec_specific_params);
    auto ltv_raw_sz = ltv.RawPacketSize();

    size_t old_size = data.size();
    data.resize(old_size + ltv_raw_sz + 2);

    // Set the cursor behind the old data
    auto* p_value = data.data() + old_size;

    // BIS_index[i[k]]
    UINT8_TO_STREAM(p_value, bis_config.bis_index);

    // Per BIS Codec Specific Params[i[k]]
    UINT8_TO_STREAM(p_value, ltv_raw_sz);
    if (ltv_raw_sz > 0) {
      p_value = ltv.RawPacket(p_value);
    }
  }
}

static void EmitSubgroup(const BasicAudioAnnouncementSubgroup& subgroup_config,
                         std::vector<uint8_t>& data) {
  // That's the level 2 data

  // Resize for the num_bis
  size_t initial_offset = data.size();
  data.resize(initial_offset + 1);

  // Set the cursor behind the old data and adds the level 2 Num_BIS[i]
  uint8_t* p_value = data.data() + initial_offset;
  UINT8_TO_STREAM(p_value, subgroup_config.bis_configs.size());

  EmitCodecConfiguration(subgroup_config.codec_config, data, nullptr);
  EmitMetadata(subgroup_config.metadata, data);

  // This adds the level 3 data
  EmitBisConfigs(subgroup_config.bis_configs, data);
}

bool ToRawPacket(BasicAudioAnnouncementData const& in,
                 std::vector<uint8_t>& data) {
  EmitHeader(in, data);

  // Set the cursor behind the old data and resize
  size_t old_size = data.size();
  data.resize(old_size + 1);
  uint8_t* p_value = data.data() + old_size;

  // Emit the subgroup size and each subgroup
  // That's the level 1 Num_Subgroups
  UINT8_TO_STREAM(p_value, in.subgroup_configs.size());
  for (const auto& subgroup_config : in.subgroup_configs) {
    // That's the level 2 and higher level data
    EmitSubgroup(subgroup_config, data);
  }

  return true;
}

void PrepareAdvertisingData(
    bool is_public, const std::string& broadcast_name,
    bluetooth::le_audio::BroadcastId& broadcast_id,
    const bluetooth::le_audio::PublicBroadcastAnnouncementData&
        public_announcement,
    std::vector<uint8_t>& adv_data) {
  adv_data.resize(7);
  uint8_t* data_ptr = adv_data.data();
  UINT8_TO_STREAM(data_ptr, 6);
  UINT8_TO_STREAM(data_ptr, BTM_BLE_AD_TYPE_SERVICE_DATA_TYPE);
  UINT16_TO_STREAM(data_ptr, kBroadcastAudioAnnouncementServiceUuid);
  UINT24_TO_STREAM(data_ptr, broadcast_id);

  // Prepare public broadcast announcement data
  if (is_public) {
    size_t old_size = adv_data.size();
    // 5: datalen(1) + adtype(1) + serviceuuid(2) + features(1)
    adv_data.resize(old_size + 5);
    // Skip the data length field until the full content is generated
    data_ptr = adv_data.data() + old_size + 1;
    UINT8_TO_STREAM(data_ptr, BTM_BLE_AD_TYPE_SERVICE_DATA_TYPE);
    UINT16_TO_STREAM(data_ptr, kPublicBroadcastAnnouncementServiceUuid);
    UINT8_TO_STREAM(data_ptr, public_announcement.features);
    // Set metadata length to 0 if no meta data present
    EmitMetadata(public_announcement.metadata, adv_data);

    // Update the length field accordingly
    data_ptr = adv_data.data() + old_size;
    UINT8_TO_STREAM(data_ptr, adv_data.size() - old_size - 1);

    // Prepare broadcast name
    if (!broadcast_name.empty()) {
      EmitBroadcastName(broadcast_name, adv_data);
    }
  }
}

void PreparePeriodicData(const BasicAudioAnnouncementData& announcement,
                         std::vector<uint8_t>& periodic_data) {
  /* Account for AD Type + Service UUID */
  periodic_data.resize(4);
  /* Skip the data length field until the full content is generated */
  uint8_t* data_ptr = periodic_data.data() + 1;
  UINT8_TO_STREAM(data_ptr, BTM_BLE_AD_TYPE_SERVICE_DATA_TYPE);
  UINT16_TO_STREAM(data_ptr, kBasicAudioAnnouncementServiceUuid);

  /* Append the announcement */
  ToRawPacket(announcement, periodic_data);

  /* Update the length field accordingly */
  data_ptr = periodic_data.data();
  UINT8_TO_STREAM(data_ptr, periodic_data.size() - 1);
}

constexpr types::LeAudioCodecId kLeAudioCodecIdLc3 = {
    .coding_format = types::kLeAudioCodingFormatLC3,
    .vendor_company_id = types::kLeAudioVendorCompanyIdUndefined,
    .vendor_codec_id = types::kLeAudioVendorCodecIdUndefined};

static const BroadcastCodecWrapper lc3_mono_16_2 = BroadcastCodecWrapper(
    kLeAudioCodecIdLc3,
    // LeAudioCodecConfiguration
    {.num_channels = LeAudioCodecConfiguration::kChannelNumberMono,
     .sample_rate = LeAudioCodecConfiguration::kSampleRate16000,
     .bits_per_sample = LeAudioCodecConfiguration::kBitsPerSample16,
     .data_interval_us = LeAudioCodecConfiguration::kInterval10000Us},
    // Frame len.
    40);

static const BroadcastCodecWrapper lc3_stereo_16_2 = BroadcastCodecWrapper(
    kLeAudioCodecIdLc3,
    // LeAudioCodecConfiguration
    {.num_channels = LeAudioCodecConfiguration::kChannelNumberStereo,
     .sample_rate = LeAudioCodecConfiguration::kSampleRate16000,
     .bits_per_sample = LeAudioCodecConfiguration::kBitsPerSample16,
     .data_interval_us = LeAudioCodecConfiguration::kInterval10000Us},
    // Frame len.
    40);

static const BroadcastCodecWrapper lc3_stereo_24_2 = BroadcastCodecWrapper(
    kLeAudioCodecIdLc3,
    // LeAudioCodecConfiguration
    {.num_channels = LeAudioCodecConfiguration::kChannelNumberStereo,
     .sample_rate = LeAudioCodecConfiguration::kSampleRate24000,
     .bits_per_sample = LeAudioCodecConfiguration::kBitsPerSample16,
     .data_interval_us = LeAudioCodecConfiguration::kInterval10000Us},
    // Frame len.
    60);

static const BroadcastCodecWrapper lc3_stereo_48_1 = BroadcastCodecWrapper(
    kLeAudioCodecIdLc3,
    // LeAudioCodecConfiguration
    {.num_channels = LeAudioCodecConfiguration::kChannelNumberStereo,
     .sample_rate = LeAudioCodecConfiguration::kSampleRate48000,
     .bits_per_sample = LeAudioCodecConfiguration::kBitsPerSample16,
     .data_interval_us = LeAudioCodecConfiguration::kInterval7500Us},
    // Frame len.
    75);

static const BroadcastCodecWrapper lc3_stereo_48_2 = BroadcastCodecWrapper(
    kLeAudioCodecIdLc3,
    // LeAudioCodecConfiguration
    {.num_channels = LeAudioCodecConfiguration::kChannelNumberStereo,
     .sample_rate = LeAudioCodecConfiguration::kSampleRate48000,
     .bits_per_sample = LeAudioCodecConfiguration::kBitsPerSample16,
     .data_interval_us = LeAudioCodecConfiguration::kInterval10000Us},
    // Frame len.
    100);

static const BroadcastCodecWrapper lc3_stereo_48_3 = BroadcastCodecWrapper(
    kLeAudioCodecIdLc3,
    // LeAudioCodecConfiguration
    {.num_channels = LeAudioCodecConfiguration::kChannelNumberStereo,
     .sample_rate = LeAudioCodecConfiguration::kSampleRate48000,
     .bits_per_sample = LeAudioCodecConfiguration::kBitsPerSample16,
     .data_interval_us = LeAudioCodecConfiguration::kInterval7500Us},
    // Frame len.
    90);

static const BroadcastCodecWrapper lc3_stereo_48_4 = BroadcastCodecWrapper(
    kLeAudioCodecIdLc3,
    // LeAudioCodecConfiguration
    {.num_channels = LeAudioCodecConfiguration::kChannelNumberStereo,
     .sample_rate = LeAudioCodecConfiguration::kSampleRate48000,
     .bits_per_sample = LeAudioCodecConfiguration::kBitsPerSample16,
     .data_interval_us = LeAudioCodecConfiguration::kInterval10000Us},
    // Frame len.
    120);

const std::map<uint32_t, uint8_t> sample_rate_to_sampling_freq_map = {
    {LeAudioCodecConfiguration::kSampleRate8000,
     codec_spec_conf::kLeAudioSamplingFreq8000Hz},
    {LeAudioCodecConfiguration::kSampleRate16000,
     codec_spec_conf::kLeAudioSamplingFreq16000Hz},
    {LeAudioCodecConfiguration::kSampleRate24000,
     codec_spec_conf::kLeAudioSamplingFreq24000Hz},
    {LeAudioCodecConfiguration::kSampleRate32000,
     codec_spec_conf::kLeAudioSamplingFreq32000Hz},
    {LeAudioCodecConfiguration::kSampleRate44100,
     codec_spec_conf::kLeAudioSamplingFreq44100Hz},
    {LeAudioCodecConfiguration::kSampleRate48000,
     codec_spec_conf::kLeAudioSamplingFreq48000Hz},
};

const std::map<uint32_t, uint8_t> data_interval_ms_to_frame_duration = {
    {LeAudioCodecConfiguration::kInterval7500Us,
     codec_spec_conf::kLeAudioCodecFrameDur7500us},
    {LeAudioCodecConfiguration::kInterval10000Us,
     codec_spec_conf::kLeAudioCodecFrameDur10000us},
};

types::LeAudioLtvMap BroadcastCodecWrapper::GetBisCodecSpecData(
    uint8_t bis_idx) const {
  /* For a single channel this will be set at the subgroup lvl. */
  if (source_codec_config.num_channels == 1) return {};

  switch (bis_idx) {
    case 1:
      return types::LeAudioLtvMap(
          {{codec_spec_conf::kLeAudioLtvTypeAudioChannelAllocation,
            UINT32_TO_VEC_UINT8(codec_spec_conf::kLeAudioLocationFrontLeft)}});
    case 2:
      return types::LeAudioLtvMap(
          {{codec_spec_conf::kLeAudioLtvTypeAudioChannelAllocation,
            UINT32_TO_VEC_UINT8(codec_spec_conf::kLeAudioLocationFrontRight)}});
      break;
    default:
      return {};
  }
}

types::LeAudioLtvMap BroadcastCodecWrapper::GetSubgroupCodecSpecData() const {
  LOG_ASSERT(
      sample_rate_to_sampling_freq_map.count(source_codec_config.sample_rate))
      << "Invalid sample_rate";
  LOG_ASSERT(data_interval_ms_to_frame_duration.count(
      source_codec_config.data_interval_us))
      << "Invalid data_interval";

  std::map<uint8_t, std::vector<uint8_t>> codec_spec_ltvs = {
      {codec_spec_conf::kLeAudioLtvTypeSamplingFreq,
       UINT8_TO_VEC_UINT8(sample_rate_to_sampling_freq_map.at(
           source_codec_config.sample_rate))},
      {codec_spec_conf::kLeAudioLtvTypeFrameDuration,
       UINT8_TO_VEC_UINT8(data_interval_ms_to_frame_duration.at(
           source_codec_config.data_interval_us))},
  };

  if (codec_id.coding_format == kLeAudioCodecIdLc3.coding_format) {
    codec_spec_ltvs[codec_spec_conf::kLeAudioLtvTypeOctetsPerCodecFrame] =
        UINT16_TO_VEC_UINT8(octets_per_codec_frame);
  }

  if (source_codec_config.num_channels == 1) {
    codec_spec_ltvs[codec_spec_conf::kLeAudioLtvTypeAudioChannelAllocation] =
        UINT32_TO_VEC_UINT8(codec_spec_conf::kLeAudioLocationFrontCenter);
  }

  return types::LeAudioLtvMap(codec_spec_ltvs);
}

std::ostream& operator<<(
    std::ostream& os,
    const le_audio::broadcaster::BroadcastCodecWrapper& config) {
  os << " BroadcastCodecWrapper=[";
  os << "CodecID="
     << "{" << +config.GetLeAudioCodecId().coding_format << ":"
     << +config.GetLeAudioCodecId().vendor_company_id << ":"
     << +config.GetLeAudioCodecId().vendor_codec_id << "}";
  os << ", LeAudioCodecConfiguration="
     << "{NumChannels=" << +config.GetNumChannels()
     << ", SampleRate=" << +config.GetSampleRate()
     << ", BitsPerSample=" << +config.GetBitsPerSample()
     << ", DataIntervalUs=" << +config.GetDataIntervalUs() << "}";
  os << "]";
  return os;
}

std::ostream& operator<<(
    std::ostream& os, const le_audio::broadcaster::BroadcastQosConfig& config) {
  os << " BroadcastQosConfig=[";
  os << "RTN=" << +config.getRetransmissionNumber();
  os << ", MaxTransportLatency=" << config.getMaxTransportLatency();
  os << "]";
  return os;
}

static const std::pair<const BroadcastCodecWrapper&, const BroadcastQosConfig&>
    lc3_mono_16_2_1 = {lc3_mono_16_2, qos_config_2_10};

static const std::pair<const BroadcastCodecWrapper&, const BroadcastQosConfig&>
    lc3_mono_16_2_2 = {lc3_mono_16_2, qos_config_4_60};

static const std::pair<const BroadcastCodecWrapper&, const BroadcastQosConfig&>
    lc3_stereo_16_2_2 = {lc3_stereo_16_2, qos_config_4_60};

static const std::pair<const BroadcastCodecWrapper&, const BroadcastQosConfig&>
    lc3_stereo_24_2_1 = {lc3_stereo_24_2, qos_config_2_10};

static const std::pair<const BroadcastCodecWrapper&, const BroadcastQosConfig&>
    lc3_stereo_24_2_2 = {lc3_stereo_24_2, qos_config_4_60};

static const std::pair<const BroadcastCodecWrapper&, const BroadcastQosConfig&>
    lc3_stereo_48_1_2 = {lc3_stereo_48_1, qos_config_4_50};

static const std::pair<const BroadcastCodecWrapper&, const BroadcastQosConfig&>
    lc3_stereo_48_2_2 = {lc3_stereo_48_2, qos_config_4_65};

static const std::pair<const BroadcastCodecWrapper&, const BroadcastQosConfig&>
    lc3_stereo_48_3_2 = {lc3_stereo_48_3, qos_config_4_50};

static const std::pair<const BroadcastCodecWrapper&, const BroadcastQosConfig&>
    lc3_stereo_48_4_2 = {lc3_stereo_48_4, qos_config_4_65};

std::pair<const BroadcastCodecWrapper&, const BroadcastQosConfig&>
getStreamConfigForContext(types::AudioContexts context) {
  const std::string* options =
      stack_config_get_interface()->get_pts_broadcast_audio_config_options();
  if (options) {
    if (!options->compare("lc3_stereo_48_1_2")) return lc3_stereo_48_1_2;
    if (!options->compare("lc3_stereo_48_2_2")) return lc3_stereo_48_2_2;
    if (!options->compare("lc3_stereo_48_3_2")) return lc3_stereo_48_3_2;
    if (!options->compare("lc3_stereo_48_4_2")) return lc3_stereo_48_4_2;
  }
  // High quality, Low Latency
  if (context.test_any(LeAudioContextType::GAME | LeAudioContextType::LIVE))
    return lc3_stereo_24_2_1;

  // Low quality, Low Latency
  if (context.test(LeAudioContextType::INSTRUCTIONAL)) return lc3_mono_16_2_1;

  // Low quality, High Reliability
  if (context.test_any(LeAudioContextType::SOUNDEFFECTS |
                       LeAudioContextType::UNSPECIFIED))
    return lc3_stereo_16_2_2;

  if (context.test_any(LeAudioContextType::ALERTS |
                       LeAudioContextType::NOTIFICATIONS |
                       LeAudioContextType::EMERGENCYALARM))
    return lc3_mono_16_2_2;

  // High quality, High Reliability
  if (context.test(LeAudioContextType::MEDIA)) return lc3_stereo_24_2_2;

  // Defaults: Low quality, High Reliability
  return lc3_mono_16_2_2;
}

} /* namespace broadcaster */
} /* namespace le_audio */

/* Helper functions for comparing BroadcastAnnouncements */
namespace bluetooth {
namespace le_audio {

static bool isMetadataSame(std::map<uint8_t, std::vector<uint8_t>> m1,
                           std::map<uint8_t, std::vector<uint8_t>> m2) {
  if (m1.size() != m2.size()) return false;

  for (auto& m1pair : m1) {
    if (m2.count(m1pair.first) == 0) return false;

    auto& m2val = m2.at(m1pair.first);
    if (m1pair.second.size() != m2val.size()) return false;

    if (m1pair.second.size() != 0) {
      if (memcmp(m1pair.second.data(), m2val.data(), m2val.size()) != 0)
        return false;
    }
  }
  return true;
}

bool operator==(const BasicAudioAnnouncementData& lhs,
                const BasicAudioAnnouncementData& rhs) {
  if (lhs.presentation_delay_us != rhs.presentation_delay_us) return false;

  if (lhs.subgroup_configs.size() != rhs.subgroup_configs.size()) return false;

  for (auto i = 0lu; i < lhs.subgroup_configs.size(); ++i) {
    auto& lhs_subgroup = lhs.subgroup_configs[i];
    auto& rhs_subgroup = rhs.subgroup_configs[i];

    if (lhs_subgroup.codec_config.codec_id !=
        rhs_subgroup.codec_config.codec_id)
      return false;

    if (lhs_subgroup.codec_config.vendor_company_id !=
        rhs_subgroup.codec_config.vendor_company_id)
      return false;

    if (lhs_subgroup.codec_config.vendor_codec_id !=
        rhs_subgroup.codec_config.vendor_codec_id)
      return false;

    if (!isMetadataSame(lhs_subgroup.codec_config.codec_specific_params,
                        rhs_subgroup.codec_config.codec_specific_params))
      return false;

    if (!isMetadataSame(lhs_subgroup.metadata, rhs_subgroup.metadata))
      return false;

    for (auto j = 0lu; j < lhs_subgroup.bis_configs.size(); ++j) {
      auto& lhs_bis_config = lhs_subgroup.bis_configs[i];
      auto& rhs_bis_config = rhs_subgroup.bis_configs[i];
      if (lhs_bis_config.bis_index != rhs_bis_config.bis_index) return false;

      if (!isMetadataSame(lhs_bis_config.codec_specific_params,
                          rhs_bis_config.codec_specific_params))
        return false;
    }
  }

  return true;
}

bool operator==(const PublicBroadcastAnnouncementData& lhs,
                const PublicBroadcastAnnouncementData& rhs) {
  if (lhs.features != rhs.features) return false;
  if (!isMetadataSame(lhs.metadata, rhs.metadata)) return false;

  return true;
}
}  // namespace le_audio
}  // namespace bluetooth
