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

#pragma once

#include <base/logging.h>

#include <variant>

#include "bta/le_audio/audio_hal_client/audio_hal_client.h"
#include "bta/le_audio/le_audio_types.h"
#include "bta_le_audio_api.h"
#include "bta_le_audio_broadcaster_api.h"

/* Types used internally by various modules of the broadcaster but not exposed
 * in the API.
 */

namespace le_audio {
namespace broadcaster {
static const uint16_t kBroadcastAudioAnnouncementServiceUuid = 0x1852;
static const uint16_t kBasicAudioAnnouncementServiceUuid = 0x1851;
static const uint16_t kPublicBroadcastAnnouncementServiceUuid = 0x1856;

static const uint8_t kBisIndexInvalid = 0;

bool ToRawPacket(bluetooth::le_audio::BasicAudioAnnouncementData const&,
                 std::vector<uint8_t>&);

void PrepareAdvertisingData(
    bool is_public, const std::string& broadcast_name,
    bluetooth::le_audio::BroadcastId& broadcast_id,
    const bluetooth::le_audio::PublicBroadcastAnnouncementData&
        public_announcement,
    std::vector<uint8_t>& adv_data);
void PreparePeriodicData(
    const bluetooth::le_audio::BasicAudioAnnouncementData& announcement,
    std::vector<uint8_t>& periodic_data);

struct BroadcastCodecWrapper {
  BroadcastCodecWrapper(types::LeAudioCodecId codec_id,
                        LeAudioCodecConfiguration source_codec_config,
                        uint32_t octets_per_codec_frame,
                        uint8_t blocks_per_sdu = 1)
      : codec_id(codec_id),
        source_codec_config(source_codec_config),
        octets_per_codec_frame(octets_per_codec_frame),
        blocks_per_sdu(blocks_per_sdu) {
    if (codec_id.coding_format != types::kLeAudioCodingFormatLC3)
      LOG(ERROR) << "Unsupported coding format!";
  }

  /* We need this copy-assignment operator as we currently use global copy of a
   * wrapper for the currently active Broadcast. Maybe we should consider using
   * shared pointer instead.
   */
  BroadcastCodecWrapper& operator=(const BroadcastCodecWrapper& other) {
    codec_id = other.codec_id;
    source_codec_config = other.source_codec_config;
    octets_per_codec_frame = other.octets_per_codec_frame;
    blocks_per_sdu = other.blocks_per_sdu;
    return *this;
  };

  types::LeAudioLtvMap GetSubgroupCodecSpecData() const;
  types::LeAudioLtvMap GetBisCodecSpecData(uint8_t bis_idx) const;

  uint16_t GetMaxSduSizePerChannel() const {
    if (codec_id.coding_format == types::kLeAudioCodingFormatLC3) {
      return GetOctetsPerCodecFrame() * blocks_per_sdu;
    }

    LOG(ERROR) << "Invalid codec ID: "
               << "[" << +codec_id.coding_format << ":"
               << +codec_id.vendor_company_id << ":"
               << +codec_id.vendor_codec_id << "]";
    return 0;
  }

  uint16_t GetMaxSduSize() const {
    return GetNumChannelsPerBis() * GetMaxSduSizePerChannel();
  }

  const LeAudioCodecConfiguration& GetLeAudioCodecConfiguration() const {
    return source_codec_config;
  }

  const types::LeAudioCodecId& GetLeAudioCodecId() const { return codec_id; }

  uint8_t GetNumChannels() const { return source_codec_config.num_channels; }

  uint32_t GetOctetsPerCodecFrame() const { return octets_per_codec_frame; }

  uint8_t GetBitsPerSample() const {
    return source_codec_config.bits_per_sample;
  }

  uint32_t GetSampleRate() const { return source_codec_config.sample_rate; }

  uint32_t GetDataIntervalUs() const {
    return source_codec_config.data_interval_us;
  }

  uint8_t GetNumChannelsPerBis() const {
    // TODO: Need to handle each BIS has more than one channel case
    return 1;
  }

 private:
  types::LeAudioCodecId codec_id;
  LeAudioCodecConfiguration source_codec_config;
  uint32_t octets_per_codec_frame;
  uint8_t blocks_per_sdu;
};

std::ostream& operator<<(
    std::ostream& os,
    const le_audio::broadcaster::BroadcastCodecWrapper& config);

struct BroadcastQosConfig {
  BroadcastQosConfig(uint8_t retransmission_number,
                     uint16_t max_transport_latency)
      : retransmission_number(retransmission_number),
        max_transport_latency(max_transport_latency) {}

  BroadcastQosConfig& operator=(const BroadcastQosConfig& other) {
    retransmission_number = other.retransmission_number;
    max_transport_latency = other.max_transport_latency;
    return *this;
  };

  uint8_t getRetransmissionNumber() const { return retransmission_number; }
  uint16_t getMaxTransportLatency() const { return max_transport_latency; }

 private:
  uint8_t retransmission_number;
  uint16_t max_transport_latency;
};

static const BroadcastQosConfig qos_config_2_10 = BroadcastQosConfig(2, 10);
static const BroadcastQosConfig qos_config_4_45 = BroadcastQosConfig(4, 45);
static const BroadcastQosConfig qos_config_4_50 = BroadcastQosConfig(4, 50);
static const BroadcastQosConfig qos_config_4_60 = BroadcastQosConfig(4, 60);
static const BroadcastQosConfig qos_config_4_65 = BroadcastQosConfig(4, 65);

std::ostream& operator<<(
    std::ostream& os, const le_audio::broadcaster::BroadcastQosConfig& config);

std::pair<const BroadcastCodecWrapper&, const BroadcastQosConfig&>
getStreamConfigForContext(types::AudioContexts context);

}  // namespace broadcaster
}  // namespace le_audio

/* BroadcastAnnouncements compare helper */
namespace bluetooth {
namespace le_audio {
bool operator==(const BasicAudioAnnouncementData& lhs,
                const BasicAudioAnnouncementData& rhs);
bool operator==(const PublicBroadcastAnnouncementData& lhs,
                const PublicBroadcastAnnouncementData& rhs);
}  // namespace le_audio
}  // namespace bluetooth
