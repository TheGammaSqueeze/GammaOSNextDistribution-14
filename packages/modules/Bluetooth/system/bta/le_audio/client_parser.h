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

/*
 * This file contains the Audio Stream Control (LE_AUDIO) API function external
 * definitions.
 */

#pragma once

#include <vector>

#include "le_audio_types.h"

namespace le_audio {
namespace client_parser {
namespace ascs {
/*
 * All structures and defines are described in Audio Stream Control Service
 * specification
 */

constexpr uint8_t kCtpResponseCodeSuccess = 0x00;
constexpr uint8_t kCtpResponseCodeUnsupportedOpcode = 0x01;
constexpr uint8_t kCtpResponseCodeInvalidLength = 0x02;
constexpr uint8_t kCtpResponseCodeInvalidAseId = 0x03;
constexpr uint8_t kCtpResponseCodeInvalidAseStateMachineTransition = 0x04;
constexpr uint8_t kCtpResponseCodeInvalidAseDirection = 0x05;
constexpr uint8_t kCtpResponseCodeUnsupportedAudioCapabilities = 0x06;
constexpr uint8_t kCtpResponseCodeUnsupportedConfigurationParameterValue = 0x07;
constexpr uint8_t kCtpResponseCodeRejectedConfigurationParameterValue = 0x08;
constexpr uint8_t kCtpResponseCodeInvalidConfigurationParameterValue = 0x09;
constexpr uint8_t kCtpResponseCodeUnsupportedMetadata = 0x0A;
constexpr uint8_t kCtpResponseCodeRejectedMetadata = 0x0B;
constexpr uint8_t kCtpResponseCodeInvalidMetadata = 0x0C;
constexpr uint8_t kCtpResponseCodeInsufficientResources = 0x0D;
constexpr uint8_t kCtpResponseCodeUnspecifiedError = 0x0E;

constexpr uint8_t kCtpResponseNoReason = 0x00;
constexpr uint8_t kCtpResponseCodecId = 0x01;
constexpr uint8_t kCtpResponseCodecSpecificConfiguration = 0x02;
constexpr uint8_t kCtpResponseSduInterval = 0x03;
constexpr uint8_t kCtpResponseFraming = 0x04;
constexpr uint8_t kCtpResponsePhy = 0x05;
constexpr uint8_t kCtpResponseMaximumSduSize = 0x06;
constexpr uint8_t kCtpResponseRetransmissionNumber = 0x07;
constexpr uint8_t kCtpResponseMaxTransportLatency = 0x08;
constexpr uint8_t kCtpResponsePresentationDelay = 0x09;
constexpr uint8_t kCtpResponseInvalidAseCisMapping = 0x0A;

constexpr uint8_t kCtpMetadataResponsePreferredAudioContexts = 0x01;
constexpr uint8_t kCtpMetadataResponseStreamingAudioContexts = 0x02;
constexpr uint8_t kCtpMetadataResponseProgramInfo = 0x03;
constexpr uint8_t kCtpMetadataResponseLanguage = 0x04;
constexpr uint8_t kCtpMetadataResponseCcidList = 0x05;
constexpr uint8_t kCtpMetadataResponseParentalRating = 0x06;
constexpr uint8_t kCtpMetadataResponseProgramInfoUri = 0x07;
constexpr uint8_t kCtpMetadataResponseExtendedMetadata = 0xFE;
constexpr uint8_t kCtpMetadataResponseVendorSpecific = 0xFF;

constexpr uint8_t kLeAudioErrorCtpUnsupporterdOpcode = 0xFF;
constexpr uint8_t kLeAudioErrorCtpTruncatedOperation = 0xFE;
constexpr uint8_t kLeAudioErrorCtpCtpErr = 0xFD;

/* ASE states */
constexpr uint8_t kAseStateIdle = 0x00;
constexpr uint8_t kAseStateCodecConfigured = 0x01;
constexpr uint8_t kAseStateQosConfigured = 0x02;
constexpr uint8_t kAseStateEnabling = 0x03;
constexpr uint8_t kAseStateStreaming = 0x04;
constexpr uint8_t kAseStateDisabling = 0x05;
constexpr uint8_t kAseStateReleasing = 0x06;

/* Control point opcodes */
constexpr uint8_t kCtpOpcodeCodecConfiguration = 0x01;
constexpr uint8_t kCtpOpcodeQosConfiguration = 0x02;
constexpr uint8_t kCtpOpcodeEnable = 0x03;
constexpr uint8_t kCtpOpcodeReceiverStartReady = 0x04;
constexpr uint8_t kCtpOpcodeDisable = 0x05;
constexpr uint8_t kCtpOpcodeReceiverStopReady = 0x06;
constexpr uint8_t kCtpOpcodeUpdateMetadata = 0x07;
constexpr uint8_t kCtpOpcodeRelease = 0x08;

/* ASE status masks */
static constexpr uint32_t kAseRspHeaderMaskCtrlStatusFailureOpcode = 0x00FF0000;
static constexpr uint32_t kAseRspHeaderMaskCtrlStatusErrorCode = 0x0000FF00;
static constexpr uint32_t kAseRspHeaderMaskCtrlStatusErrorReason = 0x000000FF;

constexpr uint16_t kAseStatusCodecConfMinLen = 23;
struct ase_codec_configured_state_params {
  uint8_t framing;
  uint8_t preferred_phy;
  uint8_t preferred_retrans_nb;
  uint16_t max_transport_latency;
  uint32_t pres_delay_min;
  uint32_t pres_delay_max;
  uint32_t preferred_pres_delay_min;
  uint32_t preferred_pres_delay_max;
  types::LeAudioCodecId codec_id;
  std::vector<uint8_t> codec_spec_conf;
};

constexpr uint16_t kAseStatusCodecQosConfMinLen = 15;
struct ase_qos_configured_state_params {
  uint8_t cig_id;
  uint8_t cis_id;
  uint32_t sdu_interval;
  uint8_t framing;
  uint8_t phy;
  uint16_t max_sdu;
  uint8_t retrans_nb;
  uint16_t max_transport_latency;
  uint32_t pres_delay;
};

constexpr uint16_t kAseStatusTransMinLen = 3;
struct ase_transient_state_params {
  uint8_t cig_id;
  uint8_t cis_id;
  std::vector<uint8_t> metadata;
};

constexpr uint16_t kCtpAseEntryMinLen = 3;
struct ctp_ase_entry {
  uint8_t ase_id;
  uint8_t response_code;
  uint8_t reason;
};

constexpr uint16_t kCtpNtfMinLen = 2;
struct ctp_ntf {
  uint8_t op;
  std::vector<struct ctp_ase_entry> entries;
};

constexpr uint16_t kAseRspHdrMinLen = 2;
struct ase_rsp_hdr {
  uint8_t id;
  uint8_t state;
};

constexpr uint8_t kCtpOpSize = 1;
constexpr uint8_t kAseNumSize = 1;
constexpr uint8_t kAseIdSize = 1;

constexpr uint16_t kCtpCodecConfMinLen = 9;
struct ctp_codec_conf {
  uint8_t ase_id;
  uint8_t target_latency;
  uint8_t target_phy;
  types::LeAudioCodecId codec_id;
  types::LeAudioLtvMap codec_config;
};

constexpr uint16_t kCtpQosConfMinLen = 16;
struct ctp_qos_conf {
  uint8_t ase_id;
  uint8_t cig;
  uint8_t cis;
  uint32_t sdu_interval;
  uint8_t framing;
  uint8_t phy;
  uint16_t max_sdu;
  uint8_t retrans_nb;
  uint16_t max_transport_latency;
  uint32_t pres_delay;
};

constexpr uint16_t kCtpEnableMinLen = 2;
struct ctp_enable {
  uint8_t ase_id;
  std::vector<uint8_t> metadata;
};

constexpr uint16_t kCtpUpdateMetadataMinLen = 2;
struct ctp_update_metadata {
  uint8_t ase_id;
  std::vector<uint8_t> metadata;
};

/* Device control and common functions */
bool ParseAseStatusHeader(ase_rsp_hdr& rsp, uint16_t len, const uint8_t* value);
bool ParseAseStatusCodecConfiguredStateParams(
    struct ase_codec_configured_state_params& rsp, uint16_t len,
    const uint8_t* value);
bool ParseAseStatusQosConfiguredStateParams(
    struct ase_qos_configured_state_params& rsp, uint16_t len,
    const uint8_t* value);
bool ParseAseStatusTransientStateParams(struct ase_transient_state_params& rsp,
                                        uint16_t len, const uint8_t* value);
bool ParseAseCtpNotification(struct ctp_ntf& ntf, uint16_t len,
                             const uint8_t* value);
bool PrepareAseCtpCodecConfig(const std::vector<struct ctp_codec_conf>& confs,
                              std::vector<uint8_t>& value);
bool PrepareAseCtpConfigQos(const std::vector<struct ctp_qos_conf>& confs,
                            std::vector<uint8_t>& value);
bool PrepareAseCtpEnable(const std::vector<struct ctp_enable>& confs,
                         std::vector<uint8_t>& value);
bool PrepareAseCtpAudioReceiverStartReady(const std::vector<uint8_t>& ids,
                                          std::vector<uint8_t>& value);
bool PrepareAseCtpDisable(const std::vector<uint8_t>& ids,
                          std::vector<uint8_t>& value);
bool PrepareAseCtpAudioReceiverStopReady(const std::vector<uint8_t>& ids,
                                         std::vector<uint8_t>& value);
bool PrepareAseCtpUpdateMetadata(
    const std::vector<struct ctp_update_metadata>& confs,
    std::vector<uint8_t>& value);
bool PrepareAseCtpRelease(const std::vector<uint8_t>& ids,
                          std::vector<uint8_t>& value);
}  // namespace ascs

namespace pacs {

constexpr uint16_t kAcsPacRecordMinLen = 7;
constexpr uint8_t kAcsPacMetadataLenLen = 1;
constexpr uint16_t kAcsPacDiscoverRspMinLen = 1;

constexpr uint16_t kAudioLocationsRspMinLen = 4;

constexpr uint16_t kAseAudioAvailRspMinLen = 4;

constexpr uint16_t kAseAudioSuppContRspMinLen = 4;

int ParseSinglePac(std::vector<struct types::acs_ac_record>& pac_recs,
                   uint16_t len, const uint8_t* value);
bool ParsePacs(std::vector<struct types::acs_ac_record>& pac_recs, uint16_t len,
               const uint8_t* value);
bool ParseAudioLocations(types::AudioLocations& audio_locations, uint16_t len,
                         const uint8_t* value);
bool ParseAvailableAudioContexts(
    types::BidirectionalPair<types::AudioContexts>& rsp, uint16_t len,
    const uint8_t* value);
bool ParseSupportedAudioContexts(
    types::BidirectionalPair<types::AudioContexts>& rsp, uint16_t len,
    const uint8_t* value);
}  // namespace pacs

namespace tmap {

constexpr uint16_t kTmapRoleLen = 2;

bool ParseTmapRole(std::bitset<16>& role, uint16_t len, const uint8_t* value);

}  // namespace tmap
}  // namespace client_parser
}  // namespace le_audio
