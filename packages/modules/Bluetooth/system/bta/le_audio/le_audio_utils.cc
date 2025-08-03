/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "le_audio_utils.h"

#include "bta/le_audio/content_control_id_keeper.h"
#include "common/strings.h"
#include "le_audio_types.h"
#include "os/log.h"

using bluetooth::common::ToString;
using le_audio::types::AudioContexts;
using le_audio::types::LeAudioContextType;

namespace le_audio {
namespace utils {

/* The returned LeAudioContextType should have its entry in the
 * AudioSetConfigurationProvider's ContextTypeToScenario mapping table.
 * Otherwise the AudioSetConfigurationProvider will fall back
 * to default scenario.
 */
LeAudioContextType AudioContentToLeAudioContext(
    audio_content_type_t content_type, audio_usage_t usage) {
  /* Check audio attribute usage of stream */
  switch (usage) {
    case AUDIO_USAGE_MEDIA:
      return LeAudioContextType::MEDIA;
    case AUDIO_USAGE_ASSISTANT:
      return LeAudioContextType::VOICEASSISTANTS;
    case AUDIO_USAGE_VOICE_COMMUNICATION:
    case AUDIO_USAGE_CALL_ASSISTANT:
      return LeAudioContextType::CONVERSATIONAL;
    case AUDIO_USAGE_VOICE_COMMUNICATION_SIGNALLING:
      if (content_type == AUDIO_CONTENT_TYPE_SPEECH) {
        return LeAudioContextType::CONVERSATIONAL;
      }

      if (content_type == AUDIO_CONTENT_TYPE_SONIFICATION) {
        return LeAudioContextType::RINGTONE;
      }

      return LeAudioContextType::MEDIA;
    case AUDIO_USAGE_GAME:
      return LeAudioContextType::GAME;
    case AUDIO_USAGE_NOTIFICATION:
      return LeAudioContextType::NOTIFICATIONS;
    case AUDIO_USAGE_NOTIFICATION_TELEPHONY_RINGTONE:
      return LeAudioContextType::RINGTONE;
    case AUDIO_USAGE_ALARM:
      return LeAudioContextType::ALERTS;
    case AUDIO_USAGE_EMERGENCY:
      return LeAudioContextType::EMERGENCYALARM;
    case AUDIO_USAGE_ASSISTANCE_NAVIGATION_GUIDANCE:
      return LeAudioContextType::INSTRUCTIONAL;
    case AUDIO_USAGE_ASSISTANCE_SONIFICATION:
      return LeAudioContextType::SOUNDEFFECTS;
    default:
      break;
  }

  return LeAudioContextType::MEDIA;
}

static std::string usageToString(audio_usage_t usage) {
  switch (usage) {
    case AUDIO_USAGE_UNKNOWN:
      return "USAGE_UNKNOWN";
    case AUDIO_USAGE_MEDIA:
      return "USAGE_MEDIA";
    case AUDIO_USAGE_VOICE_COMMUNICATION:
      return "USAGE_VOICE_COMMUNICATION";
    case AUDIO_USAGE_VOICE_COMMUNICATION_SIGNALLING:
      return "USAGE_VOICE_COMMUNICATION_SIGNALLING";
    case AUDIO_USAGE_ALARM:
      return "USAGE_ALARM";
    case AUDIO_USAGE_NOTIFICATION:
      return "USAGE_NOTIFICATION";
    case AUDIO_USAGE_NOTIFICATION_TELEPHONY_RINGTONE:
      return "USAGE_NOTIFICATION_TELEPHONY_RINGTONE";
    case AUDIO_USAGE_NOTIFICATION_COMMUNICATION_REQUEST:
      return "USAGE_NOTIFICATION_COMMUNICATION_REQUEST";
    case AUDIO_USAGE_NOTIFICATION_COMMUNICATION_INSTANT:
      return "USAGE_NOTIFICATION_COMMUNICATION_INSTANT";
    case AUDIO_USAGE_NOTIFICATION_COMMUNICATION_DELAYED:
      return "USAGE_NOTIFICATION_COMMUNICATION_DELAYED";
    case AUDIO_USAGE_NOTIFICATION_EVENT:
      return "USAGE_NOTIFICATION_EVENT";
    case AUDIO_USAGE_ASSISTANCE_ACCESSIBILITY:
      return "USAGE_ASSISTANCE_ACCESSIBILITY";
    case AUDIO_USAGE_ASSISTANCE_NAVIGATION_GUIDANCE:
      return "USAGE_ASSISTANCE_NAVIGATION_GUIDANCE";
    case AUDIO_USAGE_ASSISTANCE_SONIFICATION:
      return "USAGE_ASSISTANCE_SONIFICATION";
    case AUDIO_USAGE_GAME:
      return "USAGE_GAME";
    case AUDIO_USAGE_ASSISTANT:
      return "USAGE_ASSISTANT";
    case AUDIO_USAGE_CALL_ASSISTANT:
      return "USAGE_CALL_ASSISTANT";
    case AUDIO_USAGE_EMERGENCY:
      return "USAGE_EMERGENCY";
    case AUDIO_USAGE_SAFETY:
      return "USAGE_SAFETY";
    case AUDIO_USAGE_VEHICLE_STATUS:
      return "USAGE_VEHICLE_STATUS";
    case AUDIO_USAGE_ANNOUNCEMENT:
      return "USAGE_ANNOUNCEMENT";
    default:
      return "unknown usage ";
  }
}

static std::string contentTypeToString(audio_content_type_t content_type) {
  switch (content_type) {
    case AUDIO_CONTENT_TYPE_UNKNOWN:
      return "CONTENT_TYPE_UNKNOWN";
    case AUDIO_CONTENT_TYPE_SPEECH:
      return "CONTENT_TYPE_SPEECH";
    case AUDIO_CONTENT_TYPE_MUSIC:
      return "CONTENT_TYPE_MUSIC";
    case AUDIO_CONTENT_TYPE_MOVIE:
      return "CONTENT_TYPE_MOVIE";
    case AUDIO_CONTENT_TYPE_SONIFICATION:
      return "CONTENT_TYPE_SONIFICATION";
    default:
      return "unknown content type ";
  }
}

static const char* audioSourceToStr(audio_source_t source) {
  const char* strArr[] = {
      "AUDIO_SOURCE_DEFAULT",           "AUDIO_SOURCE_MIC",
      "AUDIO_SOURCE_VOICE_UPLINK",      "AUDIO_SOURCE_VOICE_DOWNLINK",
      "AUDIO_SOURCE_VOICE_CALL",        "AUDIO_SOURCE_CAMCORDER",
      "AUDIO_SOURCE_VOICE_RECOGNITION", "AUDIO_SOURCE_VOICE_COMMUNICATION",
      "AUDIO_SOURCE_REMOTE_SUBMIX",     "AUDIO_SOURCE_UNPROCESSED",
      "AUDIO_SOURCE_VOICE_PERFORMANCE"};

  if (static_cast<uint32_t>(source) < (sizeof(strArr) / sizeof(strArr[0])))
    return strArr[source];
  return "UNKNOWN";
}

static bool isMetadataTagPresent(const char* tags, const char* tag) {
  std::istringstream iss(tags);
  std::string t;
  while (std::getline(iss, t, AUDIO_ATTRIBUTES_TAGS_SEPARATOR)) {
    LOG_VERBOSE("Tag %s", t.c_str());
    if (t.compare(tag) == 0) {
      return true;
    }
  }
  return false;
}

AudioContexts GetAudioContextsFromSourceMetadata(
    const std::vector<struct playback_track_metadata_v7>& source_metadata) {
  AudioContexts track_contexts;
  for (const auto& entry : source_metadata) {
    auto track = entry.base;
    if (track.content_type == 0 && track.usage == 0) continue;

    LOG_INFO("%s: usage=%s(%d), content_type=%s(%d), gain=%f, tag:%s", __func__,
             usageToString(track.usage).c_str(), track.usage,
             contentTypeToString(track.content_type).c_str(),
             track.content_type, track.gain, entry.tags);

    if (isMetadataTagPresent(entry.tags, "VX_AOSP_SAMPLESOUND")) {
      track_contexts.set(LeAudioContextType::SOUNDEFFECTS);
    } else {
      track_contexts.set(
          AudioContentToLeAudioContext(track.content_type, track.usage));
    }
  }
  return track_contexts;
}

AudioContexts GetAudioContextsFromSinkMetadata(
    const std::vector<struct record_track_metadata_v7>& sink_metadata) {
  AudioContexts all_track_contexts;

  for (const auto& entry : sink_metadata) {
    auto track = entry.base;
    if (track.source == AUDIO_SOURCE_INVALID) continue;
    LeAudioContextType track_context;

    LOG_DEBUG(
        "source=%s(0x%02x), gain=%f, destination device=0x%08x, destination "
        "device address=%.32s",
        audioSourceToStr(track.source), track.source, track.gain,
        track.dest_device, track.dest_device_address);

    if (track.source == AUDIO_SOURCE_MIC) {
      track_context = LeAudioContextType::LIVE;

    } else if (track.source == AUDIO_SOURCE_VOICE_COMMUNICATION) {
      track_context = LeAudioContextType::CONVERSATIONAL;

    } else {
      /* Fallback to voice assistant
       * This will handle also a case when the device is
       * AUDIO_SOURCE_VOICE_RECOGNITION
       */
      track_context = LeAudioContextType::VOICEASSISTANTS;
      LOG_WARN(
          "Could not match the recording track type to group available "
          "context. Using context %s.",
          ToString(track_context).c_str());
    }

    all_track_contexts.set(track_context);
  }

  if (all_track_contexts.none()) {
    all_track_contexts = AudioContexts(
        static_cast<std::underlying_type<LeAudioContextType>::type>(
            LeAudioContextType::UNSPECIFIED));
    LOG_DEBUG(
        "Unable to find supported audio source context for the remote audio "
        "sink device. This may result in voice back channel malfunction.");
  }

  LOG_INFO("Allowed contexts from sink metadata: %s (0x%08hx)",
           bluetooth::common::ToString(all_track_contexts).c_str(),
           all_track_contexts.value());
  return all_track_contexts;
}

bluetooth::le_audio::btle_audio_codec_index_t
translateBluetoothCodecFormatToCodecType(uint8_t codec_format) {
  switch (codec_format) {
    case types::kLeAudioCodingFormatLC3:
      return bluetooth::le_audio::LE_AUDIO_CODEC_INDEX_SOURCE_LC3;
  }
  return bluetooth::le_audio::LE_AUDIO_CODEC_INDEX_SOURCE_INVALID;
}

bluetooth::le_audio::btle_audio_sample_rate_index_t
translateToBtLeAudioCodecConfigSampleRate(uint32_t sample_rate_capa) {
  LOG_INFO("%d", sample_rate_capa);
  return (bluetooth::le_audio::btle_audio_sample_rate_index_t)(
      sample_rate_capa);
}

bluetooth::le_audio::btle_audio_bits_per_sample_index_t
translateToBtLeAudioCodecConfigBitPerSample(uint8_t bits_per_sample) {
  switch (bits_per_sample) {
    case 16:
      return bluetooth::le_audio::LE_AUDIO_BITS_PER_SAMPLE_INDEX_16;
    case 24:
      return bluetooth::le_audio::LE_AUDIO_BITS_PER_SAMPLE_INDEX_24;
    case 32:
      return bluetooth::le_audio::LE_AUDIO_BITS_PER_SAMPLE_INDEX_32;
  }
  return bluetooth::le_audio::LE_AUDIO_BITS_PER_SAMPLE_INDEX_NONE;
}

bluetooth::le_audio::btle_audio_channel_count_index_t
translateToBtLeAudioCodecConfigChannelCount(uint8_t channel_count) {
  switch (channel_count) {
    case 1:
      return bluetooth::le_audio::LE_AUDIO_CHANNEL_COUNT_INDEX_1;
    case 2:
      return bluetooth::le_audio::LE_AUDIO_CHANNEL_COUNT_INDEX_2;
  }
  return bluetooth::le_audio::LE_AUDIO_CHANNEL_COUNT_INDEX_NONE;
}

bluetooth::le_audio::btle_audio_frame_duration_index_t
translateToBtLeAudioCodecConfigFrameDuration(int frame_duration) {
  switch (frame_duration) {
    case 7500:
      return bluetooth::le_audio::LE_AUDIO_FRAME_DURATION_INDEX_7500US;
    case 10000:
      return bluetooth::le_audio::LE_AUDIO_FRAME_DURATION_INDEX_10000US;
  }
  return bluetooth::le_audio::LE_AUDIO_FRAME_DURATION_INDEX_NONE;
}

void fillStreamParamsToBtLeAudioCodecConfig(
    types::LeAudioCodecId codec_id, const stream_parameters* stream_params,
    bluetooth::le_audio::btle_audio_codec_config_t& out_config) {
  if (stream_params == nullptr) {
    LOG_WARN("Stream params are null");
    return;
  }

  out_config.codec_type =
      translateBluetoothCodecFormatToCodecType(codec_id.coding_format);
  if (out_config.codec_type !=
      bluetooth::le_audio::LE_AUDIO_CODEC_INDEX_SOURCE_LC3) {
    return;
  }

  out_config.sample_rate = translateToBtLeAudioCodecConfigSampleRate(
      stream_params->sample_frequency_hz);
  out_config.channel_count = translateToBtLeAudioCodecConfigChannelCount(
      stream_params->num_of_channels);
  out_config.bits_per_sample = translateToBtLeAudioCodecConfigBitPerSample(16);
  out_config.frame_duration = translateToBtLeAudioCodecConfigFrameDuration(
      stream_params->frame_duration_us);
  out_config.octets_per_frame = stream_params->octets_per_codec_frame;
}

static bool is_known_codec(const types::LeAudioCodecId& codec_id) {
  switch (codec_id.coding_format) {
    case types::kLeAudioCodingFormatLC3:
      return true;
  }
  return false;
}

static void fillRemotePacsCapabitiliesToBtLeAudioCodecConfig(
    const struct types::acs_ac_record& record,
    std::vector<bluetooth::le_audio::btle_audio_codec_config_t>& vec) {
  const struct types::LeAudioCoreCodecCapabilities capa =
      record.codec_spec_caps.GetAsCoreCodecCapabilities();
  for (uint8_t freq_bit = codec_spec_conf::kLeAudioSamplingFreq8000Hz;
       freq_bit <= codec_spec_conf::kLeAudioSamplingFreq384000Hz; freq_bit++) {
    if (!capa.IsSamplingFrequencyConfigSupported(freq_bit)) continue;
    for (uint8_t fd_bit = codec_spec_conf::kLeAudioCodecFrameDur7500us;
         fd_bit <= codec_spec_conf::kLeAudioCodecFrameDur10000us; fd_bit++) {
      if (!capa.IsFrameDurationConfigSupported(fd_bit)) continue;
      if (!capa.HasSupportedAudioChannelCounts()) {
        bluetooth::le_audio::btle_audio_codec_config_t config = {
            .sample_rate = utils::translateToBtLeAudioCodecConfigSampleRate(
                types::LeAudioCoreCodecConfig::GetSamplingFrequencyHz(
                    freq_bit)),
            .bits_per_sample =
                utils::translateToBtLeAudioCodecConfigBitPerSample(16),
            .channel_count =
                utils::translateToBtLeAudioCodecConfigChannelCount(1),
            .frame_duration =
                utils::translateToBtLeAudioCodecConfigFrameDuration(
                    types::LeAudioCoreCodecConfig::GetFrameDurationUs(fd_bit)),
        };
        vec.push_back(config);
      } else {
        for (int chan_bit = 1; chan_bit <= 2; chan_bit++) {
          if (!capa.IsAudioChannelCountsSupported(chan_bit)) continue;

          bluetooth::le_audio::btle_audio_codec_config_t config = {
              .sample_rate = utils::translateToBtLeAudioCodecConfigSampleRate(
                  types::LeAudioCoreCodecConfig::GetSamplingFrequencyHz(
                      freq_bit)),
              .bits_per_sample =
                  utils::translateToBtLeAudioCodecConfigBitPerSample(16),
              .channel_count =
                  utils::translateToBtLeAudioCodecConfigChannelCount(chan_bit),
              .frame_duration =
                  utils::translateToBtLeAudioCodecConfigFrameDuration(
                      types::LeAudioCoreCodecConfig::GetFrameDurationUs(
                          fd_bit)),
          };
          vec.push_back(config);
        }
      }
    }
  }
}

std::vector<bluetooth::le_audio::btle_audio_codec_config_t>
GetRemoteBtLeAudioCodecConfigFromPac(
    const types::PublishedAudioCapabilities& group_pacs) {
  std::vector<bluetooth::le_audio::btle_audio_codec_config_t> vec;

  for (auto& [handles, pacs_record] : group_pacs) {
    for (auto& pac : pacs_record) {
      if (!is_known_codec(pac.codec_id)) continue;

      fillRemotePacsCapabitiliesToBtLeAudioCodecConfig(pac, vec);
    }
  }
  return vec;
}

}  // namespace utils
}  // namespace le_audio
