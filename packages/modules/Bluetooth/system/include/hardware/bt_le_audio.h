/*
 * Copyright 2019 HIMSA II K/S - www.himsa.com. Represented by EHIMA -
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

#pragma once

#include <bluetooth/log.h>

#include <array>
#include <map>
#include <optional>
#include <ostream>
#include <vector>

#include "raw_address.h"

namespace bluetooth {
namespace le_audio {

enum class LeAudioHealthBasedAction {
  NONE = 0,
  DISABLE,
  CONSIDER_DISABLING,
  INACTIVATE_GROUP,
};

inline std::ostream& operator<<(std::ostream& os,
                                const LeAudioHealthBasedAction action) {
  switch (action) {
    case LeAudioHealthBasedAction::NONE:
      os << "NONE";
      break;
    case LeAudioHealthBasedAction::DISABLE:
      os << "DISABLE";
      break;
    case LeAudioHealthBasedAction::CONSIDER_DISABLING:
      os << "CONSIDER_DISABLING";
      break;
    case LeAudioHealthBasedAction::INACTIVATE_GROUP:
      os << "INACTIVATE_GROUP";
      break;
    default:
      os << "UNKNOWN";
      break;
  }
  return os;
}

enum class ConnectionState {
  DISCONNECTED = 0,
  CONNECTING,
  CONNECTED,
  DISCONNECTING
};

enum class GroupStatus {
  INACTIVE = 0,
  ACTIVE,
  TURNED_IDLE_DURING_CALL,
};

enum class GroupStreamStatus {
  IDLE = 0,
  STREAMING,
  RELEASING,
  SUSPENDING,
  SUSPENDED,
  CONFIGURED_AUTONOMOUS,
  CONFIGURED_BY_USER,
  DESTROYED,
};

enum class GroupNodeStatus {
  ADDED = 1,
  REMOVED,
};

enum class UnicastMonitorModeStatus {
  STREAMING_REQUESTED = 0,
  STREAMING,
  STREAMING_SUSPENDED,
};

typedef enum {
  LE_AUDIO_CODEC_INDEX_SOURCE_LC3 = 0,
  LE_AUDIO_CODEC_INDEX_SOURCE_INVALID = 1000 * 1000,
} btle_audio_codec_index_t;

typedef enum { QUALITY_STANDARD = 0, QUALITY_HIGH } btle_audio_quality_t;

typedef enum {
  LE_AUDIO_SAMPLE_RATE_INDEX_NONE = 0,
  LE_AUDIO_SAMPLE_RATE_INDEX_8000HZ = 0x01 << 0,
  LE_AUDIO_SAMPLE_RATE_INDEX_11025HZ = 0x01 << 1,
  LE_AUDIO_SAMPLE_RATE_INDEX_16000HZ = 0x01 << 2,
  LE_AUDIO_SAMPLE_RATE_INDEX_22050HZ = 0x01 << 3,
  LE_AUDIO_SAMPLE_RATE_INDEX_24000HZ = 0x01 << 4,
  LE_AUDIO_SAMPLE_RATE_INDEX_32000HZ = 0x01 << 5,
  LE_AUDIO_SAMPLE_RATE_INDEX_44100HZ = 0x01 << 6,
  LE_AUDIO_SAMPLE_RATE_INDEX_48000HZ = 0x01 << 7,
  LE_AUDIO_SAMPLE_RATE_INDEX_88200HZ = 0x01 << 8,
  LE_AUDIO_SAMPLE_RATE_INDEX_96000HZ = 0x01 << 9,
  LE_AUDIO_SAMPLE_RATE_INDEX_176400HZ = 0x01 << 10,
  LE_AUDIO_SAMPLE_RATE_INDEX_192000HZ = 0x01 << 11,
  LE_AUDIO_SAMPLE_RATE_INDEX_384000HZ = 0x01 << 12
} btle_audio_sample_rate_index_t;

typedef enum {
  LE_AUDIO_BITS_PER_SAMPLE_INDEX_NONE = 0,
  LE_AUDIO_BITS_PER_SAMPLE_INDEX_16 = 0x01 << 0,
  LE_AUDIO_BITS_PER_SAMPLE_INDEX_24 = 0x01 << 1,
  LE_AUDIO_BITS_PER_SAMPLE_INDEX_32 = 0x01 << 3
} btle_audio_bits_per_sample_index_t;

typedef enum {
  LE_AUDIO_CHANNEL_COUNT_INDEX_NONE = 0,
  LE_AUDIO_CHANNEL_COUNT_INDEX_1 = 0x01 << 0,
  LE_AUDIO_CHANNEL_COUNT_INDEX_2 = 0x01 << 1
} btle_audio_channel_count_index_t;

typedef enum {
  LE_AUDIO_FRAME_DURATION_INDEX_NONE = 0,
  LE_AUDIO_FRAME_DURATION_INDEX_7500US = 0x01 << 0,
  LE_AUDIO_FRAME_DURATION_INDEX_10000US = 0x01 << 1
} btle_audio_frame_duration_index_t;

typedef struct {
  btle_audio_codec_index_t codec_type;
  btle_audio_sample_rate_index_t sample_rate;
  btle_audio_bits_per_sample_index_t bits_per_sample;
  btle_audio_channel_count_index_t channel_count;
  btle_audio_frame_duration_index_t frame_duration;
  uint16_t octets_per_frame;
  int32_t codec_priority;

  std::string ToString() const {
    std::string codec_name_str;
    std::string sample_rate_str;
    std::string bits_per_sample_str;
    std::string channel_count_str;
    std::string frame_duration_str;
    std::string octets_per_frame_str;
    std::string codec_priority_str;

    switch (codec_type) {
      case LE_AUDIO_CODEC_INDEX_SOURCE_LC3:
        codec_name_str = "LC3";
        break;
      default:
        codec_name_str = "Unknown LE codec " + std::to_string(codec_type);
        break;
    }

    switch (sample_rate) {
      case LE_AUDIO_SAMPLE_RATE_INDEX_NONE:
        sample_rate_str = "none";
        break;
      case LE_AUDIO_SAMPLE_RATE_INDEX_8000HZ:
        sample_rate_str = "8000 hz";
        break;
      case LE_AUDIO_SAMPLE_RATE_INDEX_11025HZ:
        sample_rate_str = "11025 hz";
        break;
      case LE_AUDIO_SAMPLE_RATE_INDEX_16000HZ:
        sample_rate_str = "16000 hz";
        break;
      case LE_AUDIO_SAMPLE_RATE_INDEX_22050HZ:
        sample_rate_str = "22050 hz";
        break;
      case LE_AUDIO_SAMPLE_RATE_INDEX_24000HZ:
        sample_rate_str = "24000 hz";
        break;
      case LE_AUDIO_SAMPLE_RATE_INDEX_32000HZ:
        sample_rate_str = "32000 hz";
        break;
      case LE_AUDIO_SAMPLE_RATE_INDEX_44100HZ:
        sample_rate_str = "44100 hz";
        break;
      case LE_AUDIO_SAMPLE_RATE_INDEX_48000HZ:
        sample_rate_str = "48000 hz";
        break;
      case LE_AUDIO_SAMPLE_RATE_INDEX_88200HZ:
        sample_rate_str = "88200 hz";
        break;
      case LE_AUDIO_SAMPLE_RATE_INDEX_96000HZ:
        sample_rate_str = "96000 hz";
        break;
      case LE_AUDIO_SAMPLE_RATE_INDEX_176400HZ:
        sample_rate_str = "176400 hz";
        break;
      case LE_AUDIO_SAMPLE_RATE_INDEX_192000HZ:
        sample_rate_str = "192000 hz";
        break;
      case LE_AUDIO_SAMPLE_RATE_INDEX_384000HZ:
        sample_rate_str = "384000 hz";
        break;
      default:
        sample_rate_str =
            "Unknown LE sample rate " + std::to_string(sample_rate);
        break;
    }

    switch (bits_per_sample) {
      case LE_AUDIO_BITS_PER_SAMPLE_INDEX_NONE:
        bits_per_sample_str = "none";
        break;
      case LE_AUDIO_BITS_PER_SAMPLE_INDEX_16:
        bits_per_sample_str = "16";
        break;
      case LE_AUDIO_BITS_PER_SAMPLE_INDEX_24:
        bits_per_sample_str = "24";
        break;
      case LE_AUDIO_BITS_PER_SAMPLE_INDEX_32:
        bits_per_sample_str = "32";
        break;
      default:
        bits_per_sample_str =
            "Unknown LE bits per sample " + std::to_string(bits_per_sample);
        break;
    }

    switch (channel_count) {
      case LE_AUDIO_CHANNEL_COUNT_INDEX_NONE:
        channel_count_str = "none";
        break;
      case LE_AUDIO_CHANNEL_COUNT_INDEX_1:
        channel_count_str = "1";
        break;
      case LE_AUDIO_CHANNEL_COUNT_INDEX_2:
        channel_count_str = "2";
        break;
      default:
        channel_count_str =
            "Unknown LE channel count " + std::to_string(channel_count);
        break;
    }

    switch (frame_duration) {
      case LE_AUDIO_FRAME_DURATION_INDEX_NONE:
        frame_duration_str = "none";
        break;
      case LE_AUDIO_FRAME_DURATION_INDEX_7500US:
        frame_duration_str = "7500 us";
        break;
      case LE_AUDIO_FRAME_DURATION_INDEX_10000US:
        frame_duration_str = "10000 us";
        break;
      default:
        frame_duration_str =
            "Unknown LE frame duration " + std::to_string(frame_duration);
        break;
    }

    if (octets_per_frame < 0) {
      octets_per_frame_str =
          "Unknown LE octets per frame " + std::to_string(octets_per_frame);
    } else {
      octets_per_frame_str = std::to_string(octets_per_frame);
    }

    if (codec_priority < -1) {
      codec_priority_str =
          "Unknown LE codec priority " + std::to_string(codec_priority);
    } else {
      codec_priority_str = std::to_string(codec_priority);
    }

    return "codec: " + codec_name_str + ", sample rate: " + sample_rate_str +
           ", bits per sample: " + bits_per_sample_str +
           ", channel count: " + channel_count_str +
           ", frame duration: " + frame_duration_str +
           ", octets per frame: " + octets_per_frame_str +
           ", codec priroty: " + codec_priority_str;
  }

} btle_audio_codec_config_t;

class LeAudioClientCallbacks {
 public:
  virtual ~LeAudioClientCallbacks() = default;

  /* Callback to notify Java that stack is ready */
  virtual void OnInitialized(void) = 0;

  /** Callback for profile connection state change */
  virtual void OnConnectionState(ConnectionState state,
                                 const RawAddress& address) = 0;

  /* Callback with group status update */
  virtual void OnGroupStatus(int group_id, GroupStatus group_status) = 0;

  /* Callback with node status update */
  virtual void OnGroupNodeStatus(const RawAddress& bd_addr, int group_id,
                                 GroupNodeStatus node_status) = 0;
  /* Callback for newly recognized or reconfigured existing le audio group */
  virtual void OnAudioConf(uint8_t direction, int group_id,
                           uint32_t snk_audio_location,
                           uint32_t src_audio_location,
                           uint16_t avail_cont) = 0;
  /* Callback for sink audio location recognized */
  virtual void OnSinkAudioLocationAvailable(const RawAddress& address,
                                            uint32_t snk_audio_locations) = 0;
  /* Callback with local codec capabilities */
  virtual void OnAudioLocalCodecCapabilities(
      std::vector<btle_audio_codec_config_t> local_input_capa_codec_conf,
      std::vector<btle_audio_codec_config_t> local_output_capa_codec_conf) = 0;
  /* Callback with current group codec configurations. Should change when PACs
   * changes */
  virtual void OnAudioGroupCurrentCodecConf(
      int group_id, btle_audio_codec_config_t input_codec_conf,
      btle_audio_codec_config_t output_codec_conf) = 0;
  /* Callback with selectable group codec configurations. Should change when
   * context changes */
  virtual void OnAudioGroupSelectableCodecConf(
      int group_id,
      std::vector<btle_audio_codec_config_t> input_selectable_codec_conf,
      std::vector<btle_audio_codec_config_t> output_selectable_codec_conf) = 0;
  virtual void OnHealthBasedRecommendationAction(
      const RawAddress& address, LeAudioHealthBasedAction action) = 0;
  virtual void OnHealthBasedGroupRecommendationAction(
      int group_id, LeAudioHealthBasedAction action) = 0;

  virtual void OnUnicastMonitorModeStatus(uint8_t direction,
                                          UnicastMonitorModeStatus status) = 0;

  /* Callback with group stream status update */
  virtual void OnGroupStreamStatus(int group_id,
                                   GroupStreamStatus group_stream_status) = 0;
};

class LeAudioClientInterface {
 public:
  virtual ~LeAudioClientInterface() = default;

  /* Register the LeAudio callbacks */
  virtual void Initialize(
      LeAudioClientCallbacks* callbacks,
      const std::vector<btle_audio_codec_config_t>& offloading_preference) = 0;

  /** Connect to LEAudio */
  virtual void Connect(const RawAddress& address) = 0;

  /** Disconnect from LEAudio */
  virtual void Disconnect(const RawAddress& address) = 0;

  /* Set enable/disable State for the LeAudio device */
  virtual void SetEnableState(const RawAddress& address, bool enabled) = 0;

  /* Cleanup the LeAudio */
  virtual void Cleanup(void) = 0;

  /* Called when LeAudio is unbonded. */
  virtual void RemoveDevice(const RawAddress& address) = 0;

  /* Attach le audio node to group */
  virtual void GroupAddNode(int group_id, const RawAddress& addr) = 0;

  /* Detach le audio node from a group */
  virtual void GroupRemoveNode(int group_id, const RawAddress& addr) = 0;

  /* Set active le audio group */
  virtual void GroupSetActive(int group_id) = 0;

  /* Set codec config preference */
  virtual void SetCodecConfigPreference(
      int group_id, btle_audio_codec_config_t input_codec_config,
      btle_audio_codec_config_t output_codec_config) = 0;

  /* Set Ccid for context type */
  virtual void SetCcidInformation(int ccid, int context_type) = 0;

  /* Set In call flag */
  virtual void SetInCall(bool in_call) = 0;

  /* Set Sink listening mode flag */
  virtual void SetUnicastMonitorMode(uint8_t direction, bool enable) = 0;

  /* Sends a preferred audio profiles change */
  virtual void SendAudioProfilePreferences(
      int group_id, bool is_output_preference_le_audio,
      bool is_duplex_preference_le_audio) = 0;
};

/* Represents the broadcast source state. */
enum class BroadcastState {
  STOPPED = 0,
  CONFIGURING,
  CONFIGURED,
  STOPPING,
  STREAMING,
};

using BroadcastId = uint32_t;
static constexpr BroadcastId kBroadcastIdInvalid = 0x00000000;
using BroadcastCode = std::array<uint8_t, 16>;

/* Content Metadata LTV Types */
constexpr uint8_t kLeAudioMetadataTypePreferredAudioContext = 0x01;
constexpr uint8_t kLeAudioMetadataTypeStreamingAudioContext = 0x02;
constexpr uint8_t kLeAudioMetadataTypeProgramInfo = 0x03;
constexpr uint8_t kLeAudioMetadataTypeLanguage = 0x04;
constexpr uint8_t kLeAudioMetadataTypeCcidList = 0x05;

/* Codec specific LTV Types */
constexpr uint8_t kLeAudioLtvTypeSamplingFreq = 0x01;
constexpr uint8_t kLeAudioLtvTypeFrameDuration = 0x02;
constexpr uint8_t kLeAudioLtvTypeAudioChannelAllocation = 0x03;
constexpr uint8_t kLeAudioLtvTypeOctetsPerCodecFrame = 0x04;
constexpr uint8_t kLeAudioLtvTypeCodecFrameBlocksPerSdu = 0x05;

/* Audio quality configuration in public broadcast announcement */
constexpr uint8_t kLeAudioQualityStandard = 0x1 << 1;
constexpr uint8_t kLeAudioQualityHigh = 0x1 << 2;

/* Unknown RSSI value 0x7F - 127 */
constexpr uint8_t kLeAudioSourceRssiUnknown = 0x7F;

struct BasicAudioAnnouncementCodecConfig {
  /* 5 octets for the Codec ID */
  uint8_t codec_id;
  uint16_t vendor_company_id;
  uint16_t vendor_codec_id;

  /* Codec params - series of LTV formatted triplets */
  std::map<uint8_t, std::vector<uint8_t>> codec_specific_params;
};

struct BasicAudioAnnouncementBisConfig {
  std::map<uint8_t, std::vector<uint8_t>> codec_specific_params;
  uint8_t bis_index;
};

struct BasicAudioAnnouncementSubgroup {
  /* Subgroup specific codec configuration and metadata */
  BasicAudioAnnouncementCodecConfig codec_config;
  // Content metadata
  std::map<uint8_t, std::vector<uint8_t>> metadata;
  // Broadcast channel configuration
  std::vector<BasicAudioAnnouncementBisConfig> bis_configs;
};

struct BasicAudioAnnouncementData {
  /* Announcement Header fields */
  uint32_t presentation_delay_us;

  /* Subgroup specific configurations */
  std::vector<BasicAudioAnnouncementSubgroup> subgroup_configs;
};

struct PublicBroadcastAnnouncementData {
  // Public Broadcast Announcement features bitmap
  uint8_t features;
  // Metadata
  std::map<uint8_t, std::vector<uint8_t>> metadata;
};

struct BroadcastMetadata {
  bool is_public;
  uint16_t pa_interval;
  RawAddress addr;
  uint8_t addr_type;
  uint8_t adv_sid;

  BroadcastId broadcast_id;
  std::string broadcast_name;
  std::optional<BroadcastCode> broadcast_code;

  PublicBroadcastAnnouncementData public_announcement;
  /* Presentation delay and subgroup configurations */
  BasicAudioAnnouncementData basic_audio_announcement;
};

class LeAudioBroadcasterCallbacks {
 public:
  virtual ~LeAudioBroadcasterCallbacks() = default;
  /* Callback for the newly created broadcast event. */
  virtual void OnBroadcastCreated(uint32_t broadcast_id, bool success) = 0;

  /* Callback for the destroyed broadcast event. */
  virtual void OnBroadcastDestroyed(uint32_t broadcast_id) = 0;
  /* Callback for the broadcast source state event. */
  virtual void OnBroadcastStateChanged(uint32_t broadcast_id,
                                       BroadcastState state) = 0;
  /* Callback for the broadcast metadata change. */
  virtual void OnBroadcastMetadataChanged(
      uint32_t broadcast_id, const BroadcastMetadata& broadcast_metadata) = 0;
};

class LeAudioBroadcasterInterface {
 public:
  virtual ~LeAudioBroadcasterInterface() = default;
  /* Register the LeAudio Broadcaster callbacks */
  virtual void Initialize(LeAudioBroadcasterCallbacks* callbacks) = 0;
  /* Stop the LeAudio Broadcaster and all active broadcasts */
  virtual void Stop(void) = 0;
  /* Cleanup the LeAudio Broadcaster */
  virtual void Cleanup(void) = 0;
  /* Create Broadcast instance */
  virtual void CreateBroadcast(
      bool is_public, std::string broadcast_name,
      std::optional<BroadcastCode> broadcast_code,
      std::vector<uint8_t> public_metadata,
      std::vector<uint8_t> subgroup_quality,
      std::vector<std::vector<uint8_t>> subgroup_metadata) = 0;
  /* Update the ongoing Broadcast metadata */
  virtual void UpdateMetadata(
      uint32_t broadcast_id, std::string broadcast_name,
      std::vector<uint8_t> public_metadata,
      std::vector<std::vector<uint8_t>> subgroup_metadata) = 0;

  /* Start the existing Broadcast stream */
  virtual void StartBroadcast(uint32_t broadcast_id) = 0;
  /* Pause the ongoing Broadcast stream */
  virtual void PauseBroadcast(uint32_t broadcast_id) = 0;
  /* Stop the Broadcast (no stream, no periodic advertisements */
  virtual void StopBroadcast(uint32_t broadcast_id) = 0;
  /* Destroy the existing Broadcast instance */
  virtual void DestroyBroadcast(uint32_t broadcast_id) = 0;
  /* Get Broadcast Metadata */
  virtual void GetBroadcastMetadata(uint32_t broadcast_id) = 0;
};

} /* namespace le_audio */
} /* namespace bluetooth */

namespace fmt {
template <>
struct formatter<bluetooth::le_audio::btle_audio_codec_index_t>
    : enum_formatter<bluetooth::le_audio::btle_audio_codec_index_t> {};
template <>
struct formatter<bluetooth::le_audio::btle_audio_sample_rate_index_t>
    : enum_formatter<bluetooth::le_audio::btle_audio_sample_rate_index_t> {};
template <>
struct formatter<bluetooth::le_audio::btle_audio_bits_per_sample_index_t>
    : enum_formatter<bluetooth::le_audio::btle_audio_bits_per_sample_index_t> {
};
template <>
struct formatter<bluetooth::le_audio::btle_audio_channel_count_index_t>
    : enum_formatter<bluetooth::le_audio::btle_audio_channel_count_index_t> {};
template <>
struct formatter<bluetooth::le_audio::btle_audio_frame_duration_index_t>
    : enum_formatter<bluetooth::le_audio::btle_audio_frame_duration_index_t> {};
}  // namespace fmt
