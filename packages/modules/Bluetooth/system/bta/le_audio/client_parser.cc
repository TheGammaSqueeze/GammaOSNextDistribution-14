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
 *  This module contains API of the audio stream control protocol.
 */

#include "client_parser.h"

#include <base/logging.h>
#include <base/strings/string_number_conversions.h>
#include <endian.h>
#include <hardware/bt_common_types.h>
#include <hardware/bt_gatt_types.h>

#include <map>
#include <numeric>

#include "internal_include/bt_trace.h"
#include "le_audio_types.h"
#include "os/log.h"
#include "stack/include/bt_types.h"

using le_audio::types::acs_ac_record;

namespace le_audio {
namespace client_parser {
namespace ascs {
static std::map<uint8_t, std::string> ase_state_map_string = {
    {kAseStateIdle, "Idle"},
    {kAseStateCodecConfigured, "Codec Configured"},
    {kAseStateQosConfigured, "QoS Configured"},
    {kAseStateEnabling, "Enabling"},
    {kAseStateStreaming, "Streaming"},
    {kAseStateDisabling, "Disabling"},
    {kAseStateReleasing, "Releasing"},
};

static std::map<uint8_t, std::string> ctp_opcode_map_string = {
    {kCtpOpcodeCodecConfiguration, "Config Codec"},
    {kCtpOpcodeQosConfiguration, "Config QoS"},
    {kCtpOpcodeEnable, "Enable"},
    {kCtpOpcodeReceiverStartReady, "Receiver Start Ready"},
    {kCtpOpcodeDisable, "Disable"},
    {kCtpOpcodeReceiverStopReady, "Receiver Stop Ready"},
    {kCtpOpcodeUpdateMetadata, "Update Metadata"},
    {kCtpOpcodeRelease, "Release"},
};

static std::map<uint8_t, std::string> ctp_configuration_reason_map_string = {
    {kCtpResponseNoReason, ""},
    {kCtpResponseCodecId, "Codec ID"},
    {kCtpResponseCodecSpecificConfiguration, "Codec specific configuration"},
    {kCtpResponseSduInterval, "SDU interval"},
    {kCtpResponseFraming, "Framing"},
    {kCtpResponsePhy, "PHY"},
    {kCtpResponseMaximumSduSize, "Maximum SDU size"},
    {kCtpResponseRetransmissionNumber, "Retransmission number"},
    {kCtpResponseMaxTransportLatency, "Max Transport latency"},
    {kCtpResponsePresentationDelay, "Presentation delay"},
    {kCtpResponseInvalidAseCisMapping, "Invalid ASE CIS mapping"},
};

static std::map<uint8_t, std::string> ctp_response_code_map_string = {
    {kCtpResponseCodeSuccess, "Success"},
    {kCtpResponseCodeUnsupportedOpcode, "Unsupported Opcode"},
    {kCtpResponseCodeInvalidLength, "Invalid Length"},
    {kCtpResponseCodeInvalidAseId, "Invalid ASE ID"},
    {kCtpResponseCodeInvalidAseStateMachineTransition,
     "Invalid ASE State Machine Transition"},
    {kCtpResponseCodeInvalidAseDirection, "Invalid ASE Direction"},
    {kCtpResponseCodeUnsupportedAudioCapabilities,
     "Unsupported Audio Capabilities"},
    {kCtpResponseCodeUnsupportedConfigurationParameterValue,
     "Unsupported Configuration Parameter Value"},
    {kCtpResponseCodeRejectedConfigurationParameterValue,
     "Rejected Configuration Parameter Value"},
    {kCtpResponseCodeInvalidConfigurationParameterValue,
     "Invalid Configuration Parameter Value"},
    {kCtpResponseCodeUnsupportedMetadata, "Unsupported Metadata"},
    {kCtpResponseCodeRejectedMetadata, "Rejected Metadata"},
    {kCtpResponseCodeInvalidMetadata, "Invalid Metadata"},
    {kCtpResponseCodeInsufficientResources, "Insufficient Resources"},
    {kCtpResponseCodeUnspecifiedError, "Unspecified Error"},
};

static std::map<uint8_t, std::string> ctp_metadata_reason_map_string = {
    {kCtpMetadataResponsePreferredAudioContexts, "Preferred Audio Contexts"},
    {kCtpMetadataResponseStreamingAudioContexts, "Streaming Audio Contexts"},
    {kCtpMetadataResponseProgramInfo, "Program Info"},
    {kCtpMetadataResponseLanguage, "Language"},
    {kCtpMetadataResponseCcidList, "CCID List"},
    {kCtpMetadataResponseParentalRating, "Parental Rating"},
    {kCtpMetadataResponseProgramInfoUri, "Program Info URI"},
    {kCtpMetadataResponseExtendedMetadata, "Extended Metadata"},
    {kCtpMetadataResponseVendorSpecific, "Vendor Specific"},
};

static std::map<uint8_t, std::map<uint8_t, std::string>*>
    ctp_response_code_map = {
        {kCtpResponseCodeUnsupportedConfigurationParameterValue,
         &ctp_configuration_reason_map_string},
        {kCtpResponseCodeRejectedConfigurationParameterValue,
         &ctp_configuration_reason_map_string},
        {kCtpResponseCodeInvalidConfigurationParameterValue,
         &ctp_configuration_reason_map_string},
        {kCtpResponseCodeUnsupportedMetadata, &ctp_metadata_reason_map_string},
        {kCtpResponseCodeRejectedMetadata, &ctp_metadata_reason_map_string},
        {kCtpResponseCodeInvalidMetadata, &ctp_metadata_reason_map_string},
};

bool ParseAseStatusHeader(ase_rsp_hdr& arh, uint16_t len,
                          const uint8_t* value) {
  if (len < kAseRspHdrMinLen) {
    LOG(ERROR) << __func__
               << ", wrong len of ASE char (header): " << static_cast<int>(len);

    return false;
  }

  STREAM_TO_UINT8(arh.id, value);
  STREAM_TO_UINT8(arh.state, value);

  LOG(INFO) << "ASE status: "
            << "\tASE id: " << loghex(arh.id)
            << "\tASE state: " << ase_state_map_string[arh.state] << " ("
            << loghex(arh.state) << ")";

  return true;
}

bool ParseAseStatusCodecConfiguredStateParams(
    struct ase_codec_configured_state_params& rsp, uint16_t len,
    const uint8_t* value) {
  uint8_t codec_spec_conf_len;

  if (len < kAseStatusCodecConfMinLen) {
    LOG(ERROR) << "Wrong len of codec conf status (Codec conf header)";
    return false;
  }

  STREAM_TO_UINT8(rsp.framing, value);
  STREAM_TO_UINT8(rsp.preferred_phy, value);
  STREAM_TO_UINT8(rsp.preferred_retrans_nb, value);
  STREAM_TO_UINT16(rsp.max_transport_latency, value);
  STREAM_TO_UINT24(rsp.pres_delay_min, value);
  STREAM_TO_UINT24(rsp.pres_delay_max, value);
  STREAM_TO_UINT24(rsp.preferred_pres_delay_min, value);
  STREAM_TO_UINT24(rsp.preferred_pres_delay_max, value);
  STREAM_TO_UINT8(rsp.codec_id.coding_format, value);
  STREAM_TO_UINT16(rsp.codec_id.vendor_company_id, value);
  STREAM_TO_UINT16(rsp.codec_id.vendor_codec_id, value);
  STREAM_TO_UINT8(codec_spec_conf_len, value);

  len -= kAseStatusCodecConfMinLen;

  if (len != codec_spec_conf_len) {
    LOG(ERROR) << "Wrong len of codec conf status (Codec spec conf)";
    return false;
  }
  if (codec_spec_conf_len)
    rsp.codec_spec_conf =
        std::vector<uint8_t>(value, value + codec_spec_conf_len);

  LOG(INFO) << __func__ << ", Codec configuration"
            << "\n\tFraming: " << loghex(rsp.framing)
            << "\n\tPreferred PHY: " << loghex(rsp.preferred_phy)
            << "\n\tPreferred retransmission number: "
            << loghex(rsp.preferred_retrans_nb) << "\n\tMax transport latency: "
            << loghex(rsp.max_transport_latency)
            << "\n\tPresence delay min: " << loghex(rsp.pres_delay_min)
            << "\n\tPresence delay max: " << loghex(rsp.pres_delay_max)
            << "\n\tPreferredPresentationDelayMin: "
            << loghex(rsp.preferred_pres_delay_min)
            << "\n\tPreferredPresentationDelayMax: "
            << loghex(rsp.preferred_pres_delay_max)
            << "\n\tCoding format: " << loghex(rsp.codec_id.coding_format)
            << "\n\tVendor codec company ID: "
            << loghex(rsp.codec_id.vendor_company_id)
            << "\n\tVendor codec ID: " << loghex(rsp.codec_id.vendor_codec_id)
            << "\n\tCodec specific conf len: " << (int)codec_spec_conf_len
            << "\n\tCodec specific conf: "
            << base::HexEncode(rsp.codec_spec_conf.data(),
                               rsp.codec_spec_conf.size());

  return true;
}

bool ParseAseStatusQosConfiguredStateParams(
    struct ase_qos_configured_state_params& rsp, uint16_t len,
    const uint8_t* value) {
  if (len != kAseStatusCodecQosConfMinLen) {
    LOG(ERROR) << "Wrong len of ASE characteristic (QOS conf header)";
    return false;
  }

  STREAM_TO_UINT8(rsp.cig_id, value);
  STREAM_TO_UINT8(rsp.cis_id, value);
  STREAM_TO_UINT24(rsp.sdu_interval, value);
  STREAM_TO_UINT8(rsp.framing, value);
  STREAM_TO_UINT8(rsp.phy, value);
  STREAM_TO_UINT16(rsp.max_sdu, value);
  STREAM_TO_UINT8(rsp.retrans_nb, value);
  STREAM_TO_UINT16(rsp.max_transport_latency, value);
  STREAM_TO_UINT24(rsp.pres_delay, value);

  LOG(INFO) << __func__ << ", Codec QoS Configured"
            << "\n\tCIG: " << loghex(rsp.cig_id)
            << "\n\tCIS: " << loghex(rsp.cis_id)
            << "\n\tSDU interval: " << loghex(rsp.sdu_interval)
            << "\n\tFraming: " << loghex(rsp.framing)
            << "\n\tPHY: " << loghex(rsp.phy)
            << "\n\tMax SDU: " << loghex(rsp.max_sdu)
            << "\n\tRetransmission number: " << loghex(rsp.retrans_nb)
            << "\n\tMax transport latency: "
            << loghex(rsp.max_transport_latency)
            << "\n\tPresentation delay: " << loghex(rsp.pres_delay);

  return true;
}

bool ParseAseStatusTransientStateParams(struct ase_transient_state_params& rsp,
                                        uint16_t len, const uint8_t* value) {
  uint8_t metadata_len;

  if (len < kAseStatusTransMinLen) {
    LOG(ERROR) << "Wrong len of ASE characteristic (metadata)";
    return false;
  }

  STREAM_TO_UINT8(rsp.cig_id, value);
  STREAM_TO_UINT8(rsp.cis_id, value);
  STREAM_TO_UINT8(metadata_len, value);
  len -= kAseStatusTransMinLen;

  if (len != metadata_len) {
    LOG(ERROR) << "Wrong len of ASE characteristic (metadata)";
    return false;
  }

  if (metadata_len > 0)
    rsp.metadata = std::vector<uint8_t>(value, value + metadata_len);

  LOG(INFO) << __func__ << ", Status enabling/streaming/disabling"
            << "\n\tCIG: " << loghex(rsp.cig_id)
            << "\n\tCIS: " << loghex(rsp.cis_id) << "\n\tMetadata: "
            << base::HexEncode(rsp.metadata.data(), rsp.metadata.size());

  return true;
}

bool ParseAseCtpNotification(struct ctp_ntf& ntf, uint16_t len,
                             const uint8_t* value) {
  uint8_t num_entries;

  if (len < kCtpNtfMinLen) {
    LOG(ERROR) << "Wrong len of ASE control point notification: " << (int)len;
    return false;
  }

  STREAM_TO_UINT8(ntf.op, value);
  STREAM_TO_UINT8(num_entries, value);

  if (len != kCtpNtfMinLen + (num_entries * kCtpAseEntryMinLen)) {
    LOG(ERROR) << "Wrong len of ASE control point notification (ASE IDs)";
    return false;
  }

  for (int i = 0; i < num_entries; i++) {
    struct ctp_ase_entry entry;

    STREAM_TO_UINT8(entry.ase_id, value);
    STREAM_TO_UINT8(entry.response_code, value);
    STREAM_TO_UINT8(entry.reason, value);

    ntf.entries.push_back(std::move(entry));
  }

  LOG(INFO) << __func__ << ", Control point notification"
            << "\n\tOpcode: " << ctp_opcode_map_string[ntf.op] << " ("
            << loghex(ntf.op) << ")"
            << "\n\tNum ASE IDs: " << (int)num_entries;
  for (size_t i = 0; i < num_entries; i++)
    LOG(INFO) << "\n\tASE ID[" << loghex(ntf.entries[i].ase_id)
              << "] response: "
              << ctp_response_code_map_string[ntf.entries[i].response_code]
              << " (" << loghex(ntf.entries[i].response_code) << ")"
              << " reason: "
              << ((ctp_response_code_map.count(ntf.entries[i].response_code) !=
                   0)
                      ? (*ctp_response_code_map[ntf.entries[i].response_code])
                            [ntf.entries[i].reason]
                      : "")
              << " (" << loghex(ntf.entries[i].reason) << ")";

  return true;
}

bool PrepareAseCtpCodecConfig(const std::vector<struct ctp_codec_conf>& confs,
                              std::vector<uint8_t>& value) {
  if (confs.size() == 0) return false;

  std::string conf_ents_str;
  size_t msg_len = std::accumulate(
      confs.begin(), confs.end(),
      confs.size() * kCtpCodecConfMinLen + kAseNumSize + kCtpOpSize,
      [&conf_ents_str](size_t cur_len, auto const& conf) {
        for (const auto& [type, value] : conf.codec_config.Values()) {
          conf_ents_str +=
              "\ttype: " + std::to_string(type) +
              "\tlen: " + std::to_string(value.size()) +
              "\tdata: " + base::HexEncode(value.data(), value.size()) + "\n";
        };

        return cur_len + conf.codec_config.RawPacketSize();
      });
  value.resize(msg_len);

  uint8_t* msg = value.data();
  UINT8_TO_STREAM(msg, kCtpOpcodeCodecConfiguration);

  UINT8_TO_STREAM(msg, confs.size());
  for (const struct ctp_codec_conf& conf : confs) {
    UINT8_TO_STREAM(msg, conf.ase_id);
    UINT8_TO_STREAM(msg, conf.target_latency);
    UINT8_TO_STREAM(msg, conf.target_phy);
    UINT8_TO_STREAM(msg, conf.codec_id.coding_format);
    UINT16_TO_STREAM(msg, conf.codec_id.vendor_company_id);
    UINT16_TO_STREAM(msg, conf.codec_id.vendor_codec_id);

    auto codec_spec_conf_len = conf.codec_config.RawPacketSize();

    UINT8_TO_STREAM(msg, codec_spec_conf_len);
    msg = conf.codec_config.RawPacket(msg);

    LOG(INFO) << __func__ << ", Codec configuration"
              << "\n\tAse id: " << loghex(conf.ase_id)
              << "\n\tTarget latency: " << loghex(conf.target_latency)
              << "\n\tTarget PHY: " << loghex(conf.target_phy)
              << "\n\tCoding format: " << loghex(conf.codec_id.coding_format)
              << "\n\tVendor codec company ID: "
              << loghex(conf.codec_id.vendor_company_id)
              << "\n\tVendor codec ID: "
              << loghex(conf.codec_id.vendor_codec_id)
              << "\n\tCodec config len: "
              << static_cast<int>(codec_spec_conf_len)
              << "\n\tCodec spec conf: "
              << "\n"
              << conf_ents_str;
  }

  return true;
}

bool PrepareAseCtpConfigQos(const std::vector<struct ctp_qos_conf>& confs,
                            std::vector<uint8_t>& value) {
  if (confs.size() == 0) return false;
  value.resize(confs.size() * kCtpQosConfMinLen + kAseNumSize + kCtpOpSize);

  uint8_t* msg = value.data();
  UINT8_TO_STREAM(msg, kCtpOpcodeQosConfiguration);
  UINT8_TO_STREAM(msg, confs.size());

  for (const struct ctp_qos_conf& conf : confs) {
    UINT8_TO_STREAM(msg, conf.ase_id);
    UINT8_TO_STREAM(msg, conf.cig);
    UINT8_TO_STREAM(msg, conf.cis);
    UINT24_TO_STREAM(msg, conf.sdu_interval);
    UINT8_TO_STREAM(msg, conf.framing);
    UINT8_TO_STREAM(msg, conf.phy);
    UINT16_TO_STREAM(msg, conf.max_sdu);
    UINT8_TO_STREAM(msg, conf.retrans_nb);
    UINT16_TO_STREAM(msg, conf.max_transport_latency);
    UINT24_TO_STREAM(msg, conf.pres_delay);

    LOG(INFO) << __func__ << ", QoS configuration"
              << "\n\tAse id: " << loghex(conf.ase_id)
              << "\n\tcig: " << loghex(conf.cig)
              << "\n\tCis: " << loghex(conf.cis)
              << "\n\tSDU interval: " << loghex(conf.sdu_interval)
              << "\n\tFraming: " << loghex(conf.framing)
              << "\n\tPhy: " << loghex(conf.phy)
              << "\n\tMax sdu size: " << loghex(conf.max_sdu)
              << "\n\tRetrans nb: " << loghex(conf.retrans_nb)
              << "\n\tMax Transport latency: "
              << loghex(conf.max_transport_latency)
              << "\n\tPres delay: " << loghex(conf.pres_delay);
  }

  return true;
}

bool PrepareAseCtpEnable(const std::vector<struct ctp_enable>& confs,
                         std::vector<uint8_t>& value) {
  if (confs.size() == 0) return false;

  if (confs.size() > UINT8_MAX) {
    LOG_ERROR(" To many ASEs to update metadata");
    return false;
  }

  uint16_t msg_len = confs.size() * kCtpEnableMinLen + kAseNumSize + kCtpOpSize;
  for (auto& conf : confs) {
    if (msg_len > GATT_MAX_ATTR_LEN) {
      LOG_ERROR(" Message length above GATT maximum");
      return false;
    }
    if (conf.metadata.size() > UINT8_MAX) {
      LOG_ERROR(" ase[%d] metadata length is invalid", conf.ase_id);
      return false;
    }

    msg_len += conf.metadata.size();
  }
  value.resize(msg_len);

  uint8_t* msg = value.data();
  UINT8_TO_STREAM(msg, kCtpOpcodeEnable);
  UINT8_TO_STREAM(msg, confs.size());

  for (const struct ctp_enable& conf : confs) {
    UINT8_TO_STREAM(msg, conf.ase_id);
    UINT8_TO_STREAM(msg, conf.metadata.size());
    ARRAY_TO_STREAM(msg, conf.metadata.data(),
                    static_cast<int>(conf.metadata.size()));

    LOG(INFO) << __func__ << ", Enable"
              << "\n\tAse id: " << loghex(conf.ase_id) << "\n\tMetadata: "
              << base::HexEncode(conf.metadata.data(), conf.metadata.size());
  }

  return true;
}

bool PrepareAseCtpAudioReceiverStartReady(const std::vector<uint8_t>& ase_ids,
                                          std::vector<uint8_t>& value) {
  if (ase_ids.size() == 0) return false;
  value.resize(ase_ids.size() * kAseIdSize + kAseNumSize + kCtpOpSize);

  uint8_t* msg = value.data();
  UINT8_TO_STREAM(msg, kCtpOpcodeReceiverStartReady);
  UINT8_TO_STREAM(msg, ase_ids.size());

  for (const uint8_t& id : ase_ids) {
    UINT8_TO_STREAM(msg, id);

    LOG(INFO) << __func__ << ", ReceiverStartReady"
              << "\n\tAse id: " << loghex(id);
  }

  return true;
}

bool PrepareAseCtpDisable(const std::vector<uint8_t>& ase_ids,
                          std::vector<uint8_t>& value) {
  if (ase_ids.size() == 0) return false;
  value.resize(ase_ids.size() * kAseIdSize + kAseNumSize + kCtpOpSize);

  uint8_t* msg = value.data();
  UINT8_TO_STREAM(msg, kCtpOpcodeDisable);
  UINT8_TO_STREAM(msg, ase_ids.size());

  for (const uint8_t& id : ase_ids) {
    UINT8_TO_STREAM(msg, id);

    LOG(INFO) << __func__ << ", Disable"
              << "\n\tAse id: " << loghex(id);
  }

  return true;
}

bool PrepareAseCtpAudioReceiverStopReady(const std::vector<uint8_t>& ase_ids,
                                         std::vector<uint8_t>& value) {
  if (ase_ids.size() == 0) return false;
  value.resize(ase_ids.size() * kAseIdSize + kAseNumSize + kCtpOpSize);

  uint8_t* msg = value.data();
  UINT8_TO_STREAM(msg, kCtpOpcodeReceiverStopReady);
  UINT8_TO_STREAM(msg, ase_ids.size());

  for (const uint8_t& ase_id : ase_ids) {
    UINT8_TO_STREAM(msg, ase_id);

    LOG(INFO) << __func__ << ", ReceiverStopReady"
              << "\n\tAse id: " << loghex(ase_id);
  }

  return true;
}

bool PrepareAseCtpUpdateMetadata(
    const std::vector<struct ctp_update_metadata>& confs,
    std::vector<uint8_t>& value) {
  if (confs.size() == 0) return false;

  if (confs.size() > UINT8_MAX) {
    LOG_ERROR(" To many ASEs to update metadata");
    return false;
  }

  uint16_t msg_len =
      confs.size() * kCtpUpdateMetadataMinLen + kAseNumSize + kCtpOpSize;
  for (auto& conf : confs) {
    if (msg_len > GATT_MAX_ATTR_LEN) {
      LOG_ERROR(" Message length above GATT maximum");
      return false;
    }
    if (conf.metadata.size() > UINT8_MAX) {
      LOG_ERROR(" ase[%d] metadata length is invalid", conf.ase_id);
      return false;
    }

    msg_len += conf.metadata.size();
  }
  value.resize(msg_len);

  uint8_t* msg = value.data();
  UINT8_TO_STREAM(msg, kCtpOpcodeUpdateMetadata);
  UINT8_TO_STREAM(msg, confs.size());

  for (const struct ctp_update_metadata& conf : confs) {
    UINT8_TO_STREAM(msg, conf.ase_id);
    UINT8_TO_STREAM(msg, conf.metadata.size());
    ARRAY_TO_STREAM(msg, conf.metadata.data(),
                    static_cast<int>(conf.metadata.size()));

    LOG(INFO) << __func__ << ", Update Metadata"
              << "\n\tAse id: " << loghex(conf.ase_id) << "\n\tMetadata: "
              << base::HexEncode(conf.metadata.data(), conf.metadata.size());
  }

  return true;
}

bool PrepareAseCtpRelease(const std::vector<uint8_t>& ase_ids,
                          std::vector<uint8_t>& value) {
  if (ase_ids.size() == 0) return true;
  value.resize(ase_ids.size() * kAseIdSize + kAseNumSize + kCtpOpSize);

  uint8_t* msg = value.data();
  UINT8_TO_STREAM(msg, kCtpOpcodeRelease);
  UINT8_TO_STREAM(msg, ase_ids.size());

  for (const uint8_t& ase_id : ase_ids) {
    UINT8_TO_STREAM(msg, ase_id);

    LOG(INFO) << __func__ << ", Release"
              << "\n\tAse id: " << loghex(ase_id);
  }

  return true;
}
}  // namespace ascs

namespace pacs {

int ParseSinglePac(std::vector<struct acs_ac_record>& pac_recs, uint16_t len,
                   const uint8_t* value) {
  struct acs_ac_record rec;
  uint8_t codec_spec_cap_len, metadata_len;

  if (len < kAcsPacRecordMinLen) {
    LOG_ERROR("Wrong len of PAC record (%d!=%d)", len, kAcsPacRecordMinLen);
    pac_recs.clear();
    return -1;
  }

  STREAM_TO_UINT8(rec.codec_id.coding_format, value);
  STREAM_TO_UINT16(rec.codec_id.vendor_company_id, value);
  STREAM_TO_UINT16(rec.codec_id.vendor_codec_id, value);
  STREAM_TO_UINT8(codec_spec_cap_len, value);
  len -= kAcsPacRecordMinLen - kAcsPacMetadataLenLen;

  if (len < codec_spec_cap_len + kAcsPacMetadataLenLen) {
    LOG_ERROR("Wrong len of PAC record (codec specific capabilities) (%d!=%d)",
              len, codec_spec_cap_len + kAcsPacMetadataLenLen);
    pac_recs.clear();
    return -1;
  }

  bool parsed;
  rec.codec_spec_caps =
      types::LeAudioLtvMap::Parse(value, codec_spec_cap_len, parsed);
  if (!parsed) return -1;

  value += codec_spec_cap_len;
  len -= codec_spec_cap_len;

  STREAM_TO_UINT8(metadata_len, value);
  len -= kAcsPacMetadataLenLen;

  if (len < metadata_len) {
    LOG_ERROR("Wrong len of PAC record (metadata) (%d!=%d)", len, metadata_len);
    pac_recs.clear();
    return -1;
  }

  rec.metadata = std::vector<uint8_t>(value, value + metadata_len);
  value += metadata_len;
  len -= metadata_len;

  pac_recs.push_back(std::move(rec));

  return len;
}

bool ParsePacs(std::vector<struct acs_ac_record>& pac_recs, uint16_t len,
               const uint8_t* value) {
  if (len < kAcsPacDiscoverRspMinLen) {
    LOG_ERROR("Wrong len of PAC characteristic (%d!=%d)", len,
              kAcsPacDiscoverRspMinLen);
    return false;
  }

  uint8_t pac_rec_nb;
  STREAM_TO_UINT8(pac_rec_nb, value);
  len -= kAcsPacDiscoverRspMinLen;

  pac_recs.reserve(pac_rec_nb);
  for (int i = 0; i < pac_rec_nb; i++) {
    int remaining_len = ParseSinglePac(pac_recs, len, value);
    if (remaining_len < 0) return false;

    value += (len - remaining_len);
    len = remaining_len;
  }

  return true;
}

bool ParseAudioLocations(types::AudioLocations& audio_locations, uint16_t len,
                         const uint8_t* value) {
  if (len != kAudioLocationsRspMinLen) {
    LOG(ERROR) << "Wrong len of Audio Location characteristic";
    return false;
  }

  STREAM_TO_UINT32(audio_locations, value);

  LOG(INFO) << "Audio locations: " << audio_locations.to_string();

  return true;
}

bool ParseSupportedAudioContexts(
    types::BidirectionalPair<types::AudioContexts>& contexts, uint16_t len,
    const uint8_t* value) {
  if (len != kAseAudioSuppContRspMinLen) {
    LOG(ERROR) << "Wrong len of Audio Supported Context characteristic";
    return false;
  }

  STREAM_TO_UINT16(contexts.sink.value_ref(), value);
  STREAM_TO_UINT16(contexts.source.value_ref(), value);

  LOG(INFO) << "Supported Audio Contexts: "
            << "\n\tSupported Sink Contexts: " << contexts.sink.to_string()
            << "\n\tSupported Source Contexts: " << contexts.source.to_string();

  return true;
}

bool ParseAvailableAudioContexts(
    types::BidirectionalPair<types::AudioContexts>& contexts, uint16_t len,
    const uint8_t* value) {
  if (len != kAseAudioAvailRspMinLen) {
    LOG(ERROR) << "Wrong len of Audio Availability characteristic";
    return false;
  }

  STREAM_TO_UINT16(contexts.sink.value_ref(), value);
  STREAM_TO_UINT16(contexts.source.value_ref(), value);

  LOG(INFO) << "Available Audio Contexts: "
            << "\n\tAvailable Sink Contexts: " << contexts.sink.to_string()
            << "\n\tAvailable Source Contexts: " << contexts.source.to_string();

  return true;
}
}  // namespace pacs

namespace tmap {

bool ParseTmapRole(std::bitset<16>& role, uint16_t len, const uint8_t* value) {
  if (len != kTmapRoleLen) {
    LOG_ERROR(
        ", Wrong len of Telephony Media Audio Profile Role, "
        "characteristic");
    return false;
  }

  STREAM_TO_UINT16(role, value);

  LOG_INFO(
      ", Telephony Media Audio Profile Role:"
      "\n\tRole: %s",
      role.to_string().c_str());

  return true;
}
}  // namespace tmap

}  // namespace client_parser
}  // namespace le_audio
