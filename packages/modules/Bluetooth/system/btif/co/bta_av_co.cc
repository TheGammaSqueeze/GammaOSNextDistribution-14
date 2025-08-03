/******************************************************************************
 *
 *  Copyright 2004-2012 Broadcom Corporation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

/******************************************************************************
 *
 *  This is the advanced audio/video call-out function implementation for
 *  BTIF.
 *
 ******************************************************************************/

#include "btif/include/bta_av_co.h"

#include <bluetooth/log.h>

#include <mutex>
#include <optional>
#include <vector>

#include "audio_hal_interface/a2dp_encoding.h"
#include "bta/include/bta_av_api.h"
#include "bta/include/bta_av_ci.h"
#include "btif/include/bta_av_co_peer.h"
#include "btif/include/btif_a2dp_source.h"
#include "btif/include/btif_av.h"
#include "device/include/device_iot_config.h"
#include "include/check.h"
#include "include/hardware/bt_av.h"
#include "internal_include/bt_trace.h"
#include "osi/include/allocator.h"
#include "osi/include/osi.h"  // UNUSED_ATTR
#include "stack/include/a2dp_codec_api.h"
#include "stack/include/a2dp_error_codes.h"
#include "stack/include/a2dp_ext.h"
#include "stack/include/avdt_api.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/bt_types.h"
#include "stack/include/bt_uuid16.h"
#include "types/raw_address.h"

using namespace bluetooth;

// SCMS-T protect info
const uint8_t bta_av_co_cp_scmst[AVDT_CP_INFO_LEN] = {0x02, 0x02, 0x00};

// Control block instance
static const bool kContentProtectEnabled = false;
static BtaAvCo bta_av_co_cb(kContentProtectEnabled, new BtaAvCoPeerCache());

void BtaAvCoState::setActivePeer(BtaAvCoPeer* peer) { active_peer_ = peer; }

BtaAvCoPeer* BtaAvCoState::getActivePeer() const { return active_peer_; }

uint8_t* BtaAvCoState::getCodecConfig() { return codec_config_; }

void BtaAvCoState::setCodecConfig(const uint8_t* codec_config) {
  memcpy(codec_config_, codec_config, AVDT_CODEC_SIZE);
}

void BtaAvCoState::clearCodecConfig() {
  memset(codec_config_, 0, AVDT_CODEC_SIZE);
}

void BtaAvCoState::Reset() {
  active_peer_ = nullptr;
  clearCodecConfig();
}

void BtaAvCo::Init(
    const std::vector<btav_a2dp_codec_config_t>& codec_priorities,
    std::vector<btav_a2dp_codec_info_t>* supported_codecs) {
  log::verbose("");

  std::lock_guard<std::recursive_mutex> lock(peer_cache_->codec_lock_);

  // Reset the control block
  Reset();
  peer_cache_->Init(codec_priorities, supported_codecs);

  // Gather the supported codecs from the first peer context;
  // all contexes should be identical.
  supported_codecs->clear();
  for (auto* codec_config :
       peer_cache_->peers_[0].GetCodecs()->orderedSourceCodecs()) {
    auto& codec_info = supported_codecs->emplace_back();
    codec_info.codec_type = codec_config->codecIndex();
    codec_info.codec_id = codec_config->codecId();
    codec_info.codec_name = codec_config->name();
  }
}

void BtaAvCo::Reset() {
  bta_av_legacy_state_.Reset();
  content_protect_flag_ = 0;

  if (ContentProtectEnabled()) {
    SetContentProtectFlag(AVDT_CP_SCMS_COPY_NEVER);
  } else {
    SetContentProtectFlag(AVDT_CP_SCMS_COPY_FREE);
  }

  peer_cache_->Reset();
}

bool BtaAvCo::IsSupportedCodec(btav_a2dp_codec_index_t codec_index) {
  // All peer state is initialized with the same local codec config,
  // hence we check only the first peer.
  A2dpCodecs* codecs = peer_cache_->peers_[0].GetCodecs();
  if (codecs == nullptr) {
    log::error("Peer codecs is set to null");
    return false;
  }
  return codecs->isSupportedCodec(codec_index);
}

A2dpCodecConfig* BtaAvCo::GetActivePeerCurrentCodec() {
  std::lock_guard<std::recursive_mutex> lock(peer_cache_->codec_lock_);

  BtaAvCoPeer* active_peer = bta_av_legacy_state_.getActivePeer();
  if (active_peer == nullptr || active_peer->GetCodecs() == nullptr) {
    return nullptr;
  }
  return active_peer->GetCodecs()->getCurrentCodecConfig();
}

A2dpCodecConfig* BtaAvCo::GetPeerCurrentCodec(const RawAddress& peer_address) {
  std::lock_guard<std::recursive_mutex> lock(peer_cache_->codec_lock_);

  BtaAvCoPeer* peer = peer_cache_->FindPeer(peer_address);
  if (peer == nullptr || peer->GetCodecs() == nullptr) {
    return nullptr;
  }
  return peer->GetCodecs()->getCurrentCodecConfig();
}

void BtaAvCo::ProcessDiscoveryResult(tBTA_AV_HNDL bta_av_handle,
                                     const RawAddress& peer_address,
                                     uint8_t num_seps, uint8_t num_sinks,
                                     uint8_t num_sources, uint16_t uuid_local) {
  log::verbose(
      "peer {} bta_av_handle:0x{:x} num_seps:{} num_sinks:{} num_sources:{}",
      ADDRESS_TO_LOGGABLE_CSTR(peer_address), bta_av_handle, num_seps,
      num_sinks, num_sources);

  // Find the peer
  BtaAvCoPeer* p_peer =
      peer_cache_->FindPeerAndUpdate(bta_av_handle, peer_address);
  if (p_peer == nullptr) {
    log::error("could not find peer entry for bta_av_handle 0x{:x} peer {}",
               bta_av_handle, ADDRESS_TO_LOGGABLE_CSTR(peer_address));
    return;
  }

  /* Sanity check : this should never happen */
  if (p_peer->opened) {
    log::error("peer {} already opened",
               ADDRESS_TO_LOGGABLE_CSTR(peer_address));
  }

  /* Copy the discovery results */
  p_peer->addr = peer_address;
  p_peer->num_sinks = num_sinks;
  p_peer->num_sources = num_sources;
  p_peer->num_seps = num_seps;
  p_peer->num_rx_sinks = 0;
  p_peer->num_rx_sources = 0;
  p_peer->num_sup_sinks = 0;
  p_peer->num_sup_sources = 0;
  if (uuid_local == UUID_SERVCLASS_AUDIO_SINK) {
    p_peer->uuid_to_connect = UUID_SERVCLASS_AUDIO_SOURCE;
  } else if (uuid_local == UUID_SERVCLASS_AUDIO_SOURCE) {
    p_peer->uuid_to_connect = UUID_SERVCLASS_AUDIO_SINK;
  }
}

static void bta_av_co_store_peer_codectype(const BtaAvCoPeer* p_peer);
static bool bta_av_co_should_select_hardware_codec(
    const A2dpCodecConfig& software_config,
    const ::bluetooth::audio::a2dp::provider::a2dp_configuration&
        hardware_config);

tA2DP_STATUS BtaAvCo::ProcessSourceGetConfig(
    tBTA_AV_HNDL bta_av_handle, const RawAddress& peer_address,
    uint8_t* p_codec_info, uint8_t* p_sep_info_idx, uint8_t seid,
    uint8_t* p_num_protect, uint8_t* p_protect_info) {
  log::verbose("peer {} bta_av_handle:0x{:x} codec:{} seid:{}",
               ADDRESS_TO_LOGGABLE_CSTR(peer_address), bta_av_handle,
               A2DP_CodecName(p_codec_info), seid);
  log::verbose("num_protect:0x{:02x} protect_info:0x{:02x}{:02x}{:02x}",
               *p_num_protect, p_protect_info[0], p_protect_info[1],
               p_protect_info[2]);
  log::verbose("codec: {}", A2DP_CodecInfoString(p_codec_info));

  // Find the peer
  BtaAvCoPeer* p_peer =
      peer_cache_->FindPeerAndUpdate(bta_av_handle, peer_address);
  if (p_peer == nullptr) {
    log::error("could not find peer entry for bta_av_handle 0x{:x} peer {}",
               bta_av_handle, ADDRESS_TO_LOGGABLE_CSTR(peer_address));
    return A2DP_FAIL;
  }
  log::verbose("peer(o={}, n_sinks={}, n_rx_sinks={}, n_sup_sinks={})",
               p_peer->opened, p_peer->num_sinks, p_peer->num_rx_sinks,
               p_peer->num_sup_sinks);

  p_peer->num_rx_sinks++;

  // Bypass the validation for codecs that are offloaded:
  // the stack does not need to know about the peer capabilities,
  // since the validation and selection will be performed by the
  // bluetooth audio HAL for offloaded codecs.
  auto codec_index = A2DP_SourceCodecIndex(p_codec_info);
  bool is_offloaded_codec =
      ::bluetooth::audio::a2dp::provider::supports_codec(codec_index);

  // Check the peer's Sink codec
  if (is_offloaded_codec || A2DP_IsPeerSinkCodecValid(p_codec_info)) {
    // If there is room for a new one
    if (p_peer->num_sup_sinks < BTA_AV_CO_NUM_ELEMENTS(p_peer->sinks)) {
      BtaAvCoSep* p_sink = &p_peer->sinks[p_peer->num_sup_sinks++];

      log::verbose("saved caps[{:x}:{:x}:{:x}:{:x}:{:x}:{:x}]", p_codec_info[1],
                   p_codec_info[2], p_codec_info[3], p_codec_info[4],
                   p_codec_info[5], p_codec_info[6]);

      memcpy(p_sink->codec_caps, p_codec_info, AVDT_CODEC_SIZE);
      p_sink->sep_info_idx = *p_sep_info_idx;
      p_sink->seid = seid;
      p_sink->num_protect = *p_num_protect;
      memcpy(p_sink->protect_info, p_protect_info, AVDT_CP_INFO_LEN);
    } else {
      log::error("peer {} : no more room for Sink info",
                 ADDRESS_TO_LOGGABLE_CSTR(p_peer->addr));
    }
  }

  // Check if this is the last Sink get capabilities or all supported codec
  // capabilities are retrieved.
  if ((p_peer->num_rx_sinks != p_peer->num_sinks) &&
      (p_peer->num_sup_sinks != BTA_AV_CO_NUM_ELEMENTS(p_peer->sinks))) {
    return A2DP_FAIL;
  }
  log::verbose("last Sink codec reached for peer {} (local {})",
               ADDRESS_TO_LOGGABLE_CSTR(p_peer->addr),
               p_peer->acceptor ? "acceptor" : "initiator");

  bta_av_co_store_peer_codectype(p_peer);

  // Select the Source codec
  const BtaAvCoSep* p_sink = nullptr;
  if (p_peer->acceptor) {
    UpdateAllSelectableSourceCodecs(p_peer);
    if (p_peer->p_sink == nullptr) {
      // Update the selected codec
      p_peer->p_sink = peer_cache_->FindPeerSink(
          p_peer, A2DP_SourceCodecIndex(p_peer->codec_config),
          ContentProtectFlag());
    }
    p_sink = p_peer->p_sink;
    if (p_sink == nullptr) {
      log::error("cannot find the selected codec for peer {}",
                 ADDRESS_TO_LOGGABLE_CSTR(p_peer->addr));
      return A2DP_FAIL;
    }
  } else {
    if (btif_av_peer_prefers_mandatory_codec(p_peer->addr)) {
      // Apply user preferred codec directly before first codec selected.
      p_sink = peer_cache_->FindPeerSink(
          p_peer, BTAV_A2DP_CODEC_INDEX_SOURCE_SBC, ContentProtectFlag());
      if (p_sink != nullptr) {
        log::verbose("mandatory codec preferred for peer {}",
                     ADDRESS_TO_LOGGABLE_CSTR(p_peer->addr));
        btav_a2dp_codec_config_t high_priority_mandatory{
            .codec_type = BTAV_A2DP_CODEC_INDEX_SOURCE_SBC,
            .codec_priority = BTAV_A2DP_CODEC_PRIORITY_HIGHEST,
            // Using default settings for those untouched fields
        };
        uint8_t result_codec_config[AVDT_CODEC_SIZE];
        bool restart_input = false;
        bool restart_output = false;
        bool config_updated = false;
        tA2DP_ENCODER_INIT_PEER_PARAMS peer_params;
        GetPeerEncoderParameters(p_peer->addr, &peer_params);
        p_peer->GetCodecs()->setCodecUserConfig(
            high_priority_mandatory, &peer_params, p_sink->codec_caps,
            result_codec_config, &restart_input, &restart_output,
            &config_updated);
      } else {
        log::warn("mandatory codec not found for peer {}",
                  ADDRESS_TO_LOGGABLE_CSTR(p_peer->addr));
      }
    }
    p_sink = SelectSourceCodec(p_peer);
    if (p_sink == nullptr) {
      log::error("cannot set up codec for peer {}",
                 ADDRESS_TO_LOGGABLE_CSTR(p_peer->addr));
      return A2DP_FAIL;
    }
  }

  // By default, no content protection
  *p_num_protect = 0;
  if (ContentProtectEnabled() && p_peer->ContentProtectActive()) {
    *p_num_protect = AVDT_CP_INFO_LEN;
    memcpy(p_protect_info, bta_av_co_cp_scmst, AVDT_CP_INFO_LEN);
  }

  // If acceptor -> reconfig otherwise reply for configuration
  *p_sep_info_idx = p_sink->sep_info_idx;
  log::verbose("peer {} acceptor:{} reconfig_needed:{}",
               ADDRESS_TO_LOGGABLE_CSTR(p_peer->addr),
               (p_peer->acceptor) ? "true" : "false",
               (p_peer->reconfig_needed) ? "true" : "false");
  if (p_peer->acceptor) {
    if (p_peer->reconfig_needed) {
      log::verbose("call BTA_AvReconfig(0x{:x}) for peer {}", bta_av_handle,
                   ADDRESS_TO_LOGGABLE_CSTR(p_peer->addr));
      BTA_AvReconfig(bta_av_handle, true, p_sink->sep_info_idx,
                     p_peer->codec_config, *p_num_protect, bta_av_co_cp_scmst);
    }
  } else {
    memcpy(p_codec_info, p_peer->codec_config, AVDT_CODEC_SIZE);
  }

  // report this peer selectable codecs after retrieved all its capabilities.
  log::info("retrieved {} capabilities from peer {}", p_peer->num_rx_sinks,
            ADDRESS_TO_LOGGABLE_STR(p_peer->addr));
  ReportSourceCodecState(p_peer);

  return A2DP_SUCCESS;
}

tA2DP_STATUS BtaAvCo::ProcessSinkGetConfig(tBTA_AV_HNDL bta_av_handle,
                                           const RawAddress& peer_address,
                                           uint8_t* p_codec_info,
                                           uint8_t* p_sep_info_idx,
                                           uint8_t seid, uint8_t* p_num_protect,
                                           uint8_t* p_protect_info) {
  std::lock_guard<std::recursive_mutex> lock(peer_cache_->codec_lock_);

  log::verbose("peer {} bta_av_handle:0x{:x} codec:{} seid:{}",
               ADDRESS_TO_LOGGABLE_CSTR(peer_address), bta_av_handle,
               A2DP_CodecName(p_codec_info), seid);
  log::verbose("num_protect:0x{:02x} protect_info:0x{:02x}{:02x}{:02x}",
               *p_num_protect, p_protect_info[0], p_protect_info[1],
               p_protect_info[2]);
  log::verbose("codec: {}", A2DP_CodecInfoString(p_codec_info));

  // Find the peer
  BtaAvCoPeer* p_peer =
      peer_cache_->FindPeerAndUpdate(bta_av_handle, peer_address);
  if (p_peer == nullptr) {
    log::error("could not find peer entry for bta_av_handle 0x{:x} peer {}",
               bta_av_handle, ADDRESS_TO_LOGGABLE_CSTR(peer_address));
    return A2DP_FAIL;
  }
  log::verbose(
      "peer {} found (o={}, n_sources={}, n_rx_sources={}, n_sup_sources={})",
      ADDRESS_TO_LOGGABLE_CSTR(p_peer->addr), p_peer->opened,
      p_peer->num_sources, p_peer->num_rx_sources, p_peer->num_sup_sources);

  p_peer->num_rx_sources++;

  // Check the peer's Source codec
  if (A2DP_IsPeerSourceCodecValid(p_codec_info)) {
    // If there is room for a new one
    if (p_peer->num_sup_sources < BTA_AV_CO_NUM_ELEMENTS(p_peer->sources)) {
      BtaAvCoSep* p_source = &p_peer->sources[p_peer->num_sup_sources++];

      log::verbose("saved caps[{:x}:{:x}:{:x}:{:x}:{:x}:{:x}]", p_codec_info[1],
                   p_codec_info[2], p_codec_info[3], p_codec_info[4],
                   p_codec_info[5], p_codec_info[6]);

      memcpy(p_source->codec_caps, p_codec_info, AVDT_CODEC_SIZE);
      p_source->sep_info_idx = *p_sep_info_idx;
      p_source->seid = seid;
      p_source->num_protect = *p_num_protect;
      memcpy(p_source->protect_info, p_protect_info, AVDT_CP_INFO_LEN);
    } else {
      log::error("peer {} : no more room for Source info",
                 ADDRESS_TO_LOGGABLE_CSTR(p_peer->addr));
    }
  }

  // Check if this is the last Source get capabilities or all supported codec
  // capabilities are retrieved.
  if ((p_peer->num_rx_sources != p_peer->num_sources) &&
      (p_peer->num_sup_sources != BTA_AV_CO_NUM_ELEMENTS(p_peer->sources))) {
    return A2DP_FAIL;
  }
  log::verbose("last Source codec reached for peer {}",
               ADDRESS_TO_LOGGABLE_CSTR(p_peer->addr));

  // Select the Sink codec
  const BtaAvCoSep* p_source = nullptr;
  if (p_peer->acceptor) {
    UpdateAllSelectableSinkCodecs(p_peer);
    if (p_peer->p_source == nullptr) {
      // Update the selected codec
      p_peer->p_source = peer_cache_->FindPeerSource(
          p_peer, A2DP_SinkCodecIndex(p_peer->codec_config),
          ContentProtectFlag());
    }
    p_source = p_peer->p_source;
    if (p_source == nullptr) {
      log::error("cannot find the selected codec for peer {}",
                 ADDRESS_TO_LOGGABLE_CSTR(p_peer->addr));
      return A2DP_FAIL;
    }
  } else {
    p_source = SelectSinkCodec(p_peer);
    if (p_source == nullptr) {
      log::error("cannot set up codec for the peer {}",
                 ADDRESS_TO_LOGGABLE_CSTR(p_peer->addr));
      return A2DP_FAIL;
    }
  }

  // By default, no content protection
  *p_num_protect = 0;
  if (ContentProtectEnabled() && p_peer->ContentProtectActive()) {
    *p_num_protect = AVDT_CP_INFO_LEN;
    memcpy(p_protect_info, bta_av_co_cp_scmst, AVDT_CP_INFO_LEN);
  }

  // If acceptor -> reconfig otherwise reply for configuration
  *p_sep_info_idx = p_source->sep_info_idx;
  log::verbose("peer {} acceptor:{} reconfig_needed:{}",
               ADDRESS_TO_LOGGABLE_CSTR(p_peer->addr),
               (p_peer->acceptor) ? "true" : "false",
               (p_peer->reconfig_needed) ? "true" : "false");
  if (p_peer->acceptor) {
    if (p_peer->reconfig_needed) {
      log::verbose("call BTA_AvReconfig(0x{:x}) for peer {}", bta_av_handle,
                   ADDRESS_TO_LOGGABLE_CSTR(p_peer->addr));
      BTA_AvReconfig(bta_av_handle, true, p_source->sep_info_idx,
                     p_peer->codec_config, *p_num_protect, bta_av_co_cp_scmst);
    }
  } else {
    memcpy(p_codec_info, p_peer->codec_config, AVDT_CODEC_SIZE);
  }

  return A2DP_SUCCESS;
}

void BtaAvCo::ProcessSetConfig(tBTA_AV_HNDL bta_av_handle,
                               UNUSED_ATTR const RawAddress& peer_address,
                               const uint8_t* p_codec_info,
                               UNUSED_ATTR uint8_t seid, uint8_t num_protect,
                               const uint8_t* p_protect_info,
                               uint8_t t_local_sep, uint8_t avdt_handle) {
  tA2DP_STATUS status = A2DP_SUCCESS;
  uint8_t category = A2DP_SUCCESS;
  bool reconfig_needed = false;

  log::verbose(
      "bta_av_handle=0x{:x} peer_address={} seid={} num_protect={} "
      "t_local_sep={} avdt_handle={}",
      bta_av_handle, ADDRESS_TO_LOGGABLE_CSTR(peer_address), seid, num_protect,
      t_local_sep, avdt_handle);
  log::verbose("p_codec_info[{:x}:{:x}:{:x}:{:x}:{:x}:{:x}]", p_codec_info[1],
               p_codec_info[2], p_codec_info[3], p_codec_info[4],
               p_codec_info[5], p_codec_info[6]);
  log::verbose("num_protect:0x{:02x} protect_info:0x{:02x}{:02x}{:02x}",
               num_protect, p_protect_info[0], p_protect_info[1],
               p_protect_info[2]);
  log::verbose("codec: {}", A2DP_CodecInfoString(p_codec_info));

  // Find the peer
  BtaAvCoPeer* p_peer =
      peer_cache_->FindPeerAndUpdate(bta_av_handle, peer_address);
  if (p_peer == nullptr) {
    log::error("could not find peer entry for bta_av_handle 0x{:x} peer {}",
               bta_av_handle, ADDRESS_TO_LOGGABLE_CSTR(peer_address));
    // Call call-in rejecting the configuration
    bta_av_ci_setconfig(bta_av_handle, A2DP_BUSY, AVDT_ASC_CODEC, 0, nullptr,
                        false, avdt_handle);
    return;
  }

  log::verbose(
      "peer {} found (o={}, n_sinks={}, n_rx_sinks={}, n_sup_sinks={})",
      ADDRESS_TO_LOGGABLE_CSTR(p_peer->addr), p_peer->opened, p_peer->num_sinks,
      p_peer->num_rx_sinks, p_peer->num_sup_sinks);

  // Sanity check: should not be opened at this point
  if (p_peer->opened) {
    log::error("peer {} already in use",
               ADDRESS_TO_LOGGABLE_CSTR(p_peer->addr));
  }

  if (num_protect != 0) {
    if (ContentProtectEnabled()) {
      if ((num_protect != 1) || !ContentProtectIsScmst(p_protect_info)) {
        log::error("wrong CP configuration for peer {}",
                   ADDRESS_TO_LOGGABLE_CSTR(p_peer->addr));
        status = A2DP_BAD_CP_TYPE;
        category = AVDT_ASC_PROTECT;
      }
    } else {
      // Do not support content protection for the time being
      log::error("wrong CP configuration for peer {}",
                 ADDRESS_TO_LOGGABLE_CSTR(p_peer->addr));
      status = A2DP_BAD_CP_TYPE;
      category = AVDT_ASC_PROTECT;
    }
  }

  if (status == A2DP_SUCCESS) {
    bool codec_config_supported = false;

    if (t_local_sep == AVDT_TSEP_SNK) {
      log::verbose("peer {} is A2DP Source",
                   ADDRESS_TO_LOGGABLE_CSTR(p_peer->addr));
      codec_config_supported = A2DP_IsSinkCodecSupported(p_codec_info);
      if (codec_config_supported) {
        // If Peer is Source, and our config subset matches with what is
        // requested by peer, then just accept what peer wants.
        SaveNewCodecConfig(p_peer, p_codec_info, num_protect, p_protect_info);
      }
    }
    if (t_local_sep == AVDT_TSEP_SRC) {
      log::verbose("peer {} is A2DP SINK",
                   ADDRESS_TO_LOGGABLE_CSTR(p_peer->addr));
      // Ignore the restart_output flag: accepting the remote device's
      // codec selection should not trigger codec reconfiguration.
      bool dummy_restart_output = false;
      if ((p_peer->GetCodecs() == nullptr) ||
          !SetCodecOtaConfig(p_peer, p_codec_info, num_protect, p_protect_info,
                             &dummy_restart_output)) {
        log::error("cannot set source codec {} for peer {}",
                   A2DP_CodecName(p_codec_info),
                   ADDRESS_TO_LOGGABLE_CSTR(p_peer->addr));
      } else {
        codec_config_supported = true;
        // Check if reconfiguration is needed
        if (((num_protect == 1) && !p_peer->ContentProtectActive())) {
          reconfig_needed = true;
        }
      }
    }

    // Check if codec configuration is supported
    if (!codec_config_supported) {
      category = AVDT_ASC_CODEC;
      status = A2DP_WRONG_CODEC;
    }
  }

  if (status != A2DP_SUCCESS) {
    log::verbose("peer {} reject s={} c={}",
                 ADDRESS_TO_LOGGABLE_CSTR(p_peer->addr), status, category);
    // Call call-in rejecting the configuration
    bta_av_ci_setconfig(bta_av_handle, status, category, 0, nullptr, false,
                        avdt_handle);
    return;
  }

  // Mark that this is an acceptor peer
  p_peer->acceptor = true;
  p_peer->reconfig_needed = reconfig_needed;
  log::verbose("peer {} accept reconf={}",
               ADDRESS_TO_LOGGABLE_CSTR(p_peer->addr), reconfig_needed);
  // Call call-in accepting the configuration
  bta_av_ci_setconfig(bta_av_handle, A2DP_SUCCESS, A2DP_SUCCESS, 0, nullptr,
                      reconfig_needed, avdt_handle);
}

void BtaAvCo::ProcessOpen(tBTA_AV_HNDL bta_av_handle,
                          const RawAddress& peer_address, uint16_t mtu) {
  log::verbose("peer {} bta_av_handle: 0x{:x} mtu:{}",
               ADDRESS_TO_LOGGABLE_CSTR(peer_address), bta_av_handle, mtu);

  // Find the peer
  BtaAvCoPeer* p_peer =
      peer_cache_->FindPeerAndUpdate(bta_av_handle, peer_address);
  if (p_peer == nullptr) {
    log::error("could not find peer entry for bta_av_handle 0x{:x} peer {}",
               bta_av_handle, ADDRESS_TO_LOGGABLE_CSTR(peer_address));
    return;
  }
  p_peer->opened = true;
  p_peer->mtu = mtu;

  // The first connected peer becomes the active peer
  BtaAvCoPeer* active_peer = bta_av_legacy_state_.getActivePeer();
  if (active_peer == nullptr) {
    bta_av_legacy_state_.setActivePeer(p_peer);
  }
}

void BtaAvCo::ProcessClose(tBTA_AV_HNDL bta_av_handle,
                           const RawAddress& peer_address) {
  log::verbose("peer {} bta_av_handle: 0x{:x}",
               ADDRESS_TO_LOGGABLE_CSTR(peer_address), bta_av_handle);
  btif_av_reset_audio_delay();

  // Find the peer
  BtaAvCoPeer* p_peer =
      peer_cache_->FindPeerAndUpdate(bta_av_handle, peer_address);
  if (p_peer == nullptr) {
    log::error("could not find peer entry for bta_av_handle 0x{:x} peer {}",
               bta_av_handle, ADDRESS_TO_LOGGABLE_CSTR(peer_address));
    return;
  }
  // Reset the active peer
  BtaAvCoPeer* active_peer = bta_av_legacy_state_.getActivePeer();
  if (active_peer == p_peer) {
    bta_av_legacy_state_.setActivePeer(nullptr);
  }
  // Mark the peer closed and clean the peer info
  p_peer->Init(peer_cache_->codec_priorities_);
}

void BtaAvCo::ProcessStart(tBTA_AV_HNDL bta_av_handle,
                           const RawAddress& peer_address,
                           const uint8_t* p_codec_info, bool* p_no_rtp_header) {
  log::verbose("peer {} bta_av_handle: 0x{:x}",
               ADDRESS_TO_LOGGABLE_CSTR(peer_address), bta_av_handle);

  // Find the peer
  BtaAvCoPeer* p_peer =
      peer_cache_->FindPeerAndUpdate(bta_av_handle, peer_address);
  if (p_peer == nullptr) {
    log::error("could not find peer entry for bta_av_handle 0x{:x} peer {}",
               bta_av_handle, ADDRESS_TO_LOGGABLE_CSTR(peer_address));
    return;
  }

  bool add_rtp_header =
      A2DP_UsesRtpHeader(p_peer->ContentProtectActive(), p_codec_info);

  log::verbose("bta_av_handle: 0x{:x} add_rtp_header: {}", bta_av_handle,
               add_rtp_header ? "true" : "false");
  *p_no_rtp_header = !add_rtp_header;
}

void BtaAvCo::ProcessStop(tBTA_AV_HNDL bta_av_handle,
                          const RawAddress& peer_address) {
  log::verbose("peer {} bta_av_handle: 0x{:x}",
               ADDRESS_TO_LOGGABLE_CSTR(peer_address), bta_av_handle);
  // Nothing to do
}

BT_HDR* BtaAvCo::GetNextSourceDataPacket(const uint8_t* p_codec_info,
                                         uint32_t* p_timestamp) {
  BT_HDR* p_buf;

  log::verbose("codec: {}", A2DP_CodecName(p_codec_info));

  p_buf = btif_a2dp_source_audio_readbuf();
  if (p_buf == nullptr) return nullptr;

  if (p_buf->offset < 4) {
    osi_free(p_buf);
    log::error("No space for timestamp in packet, dropped");
    return nullptr;
  }
  /*
   * Retrieve the timestamp information from the media packet,
   * and set up the packet header.
   *
   * In media packet, the following information is available:
   * p_buf->layer_specific : number of audio frames in the packet
   * p_buf->word[0] : timestamp
   */
  if (!A2DP_GetPacketTimestamp(p_codec_info, (const uint8_t*)(p_buf + 1),
                               p_timestamp) ||
      !A2DP_BuildCodecHeader(p_codec_info, p_buf, p_buf->layer_specific)) {
    log::error("unsupported codec type ({})", A2DP_GetCodecType(p_codec_info));
    osi_free(p_buf);
    return nullptr;
  }

  BtaAvCoPeer* active_peer = bta_av_legacy_state_.getActivePeer();
  // if offset is 0, the decremental operation may result in
  // underflow and OOB access
  if (ContentProtectEnabled() && (active_peer != nullptr) &&
      active_peer->ContentProtectActive() && p_buf->offset > 0) {
    p_buf->len++;
    p_buf->offset--;
    uint8_t* p = (uint8_t*)(p_buf + 1) + p_buf->offset;
    *p = ContentProtectFlag();
  }

  return p_buf;
}

void BtaAvCo::DataPacketWasDropped(tBTA_AV_HNDL bta_av_handle,
                                   const RawAddress& peer_address) {
  log::error("peer {} dropped audio packet on handle 0x{:x}",
             ADDRESS_TO_LOGGABLE_CSTR(peer_address), bta_av_handle);
}

void BtaAvCo::ProcessAudioDelay(tBTA_AV_HNDL bta_av_handle,
                                const RawAddress& peer_address,
                                uint16_t delay) {
  log::verbose("peer {} bta_av_handle: 0x{:x} delay:0x{:x}",
               ADDRESS_TO_LOGGABLE_CSTR(peer_address), bta_av_handle, delay);

  btif_av_set_audio_delay(peer_address, delay);
}

void BtaAvCo::UpdateMtu(tBTA_AV_HNDL bta_av_handle,
                        const RawAddress& peer_address, uint16_t mtu) {
  log::info("peer {} bta_av_handle: {} mtu: {}",
            ADDRESS_TO_LOGGABLE_STR(peer_address), loghex(bta_av_handle), mtu);

  // Find the peer
  BtaAvCoPeer* p_peer =
      peer_cache_->FindPeerAndUpdate(bta_av_handle, peer_address);
  if (p_peer == nullptr) {
    log::error("could not find peer entry for bta_av_handle {} peer {}",
               loghex(bta_av_handle), ADDRESS_TO_LOGGABLE_STR(peer_address));
    return;
  }
  p_peer->mtu = mtu;
}

bool BtaAvCo::SetActivePeer(const RawAddress& peer_address) {
  log::info("peer_address={}", ADDRESS_TO_LOGGABLE_STR(peer_address));

  std::lock_guard<std::recursive_mutex> lock(peer_cache_->codec_lock_);

  BtaAvCoState* reference_state = &bta_av_legacy_state_;
  if (peer_address.IsEmpty()) {
    // Reset the active peer;
    reference_state->setActivePeer(nullptr);
    reference_state->clearCodecConfig();
    return true;
  }

  // Find the peer
  BtaAvCoPeer* p_peer = peer_cache_->FindPeer(peer_address);
  if (p_peer == nullptr) {
    return false;
  }

  reference_state->setActivePeer(p_peer);
  reference_state->setCodecConfig(p_peer->codec_config);
  log::info("codec = {}",
            A2DP_CodecInfoString(reference_state->getCodecConfig()));
  // report the selected codec configuration of this new active peer.
  ReportSourceCodecState(p_peer);
  return true;
}

void BtaAvCo::SaveCodec(const uint8_t* new_codec_config) {
  bta_av_legacy_state_.setCodecConfig(new_codec_config);
}

void BtaAvCo::GetPeerEncoderParameters(
    const RawAddress& peer_address,
    tA2DP_ENCODER_INIT_PEER_PARAMS* p_peer_params) {
  uint16_t min_mtu = 0xFFFF;
  CHECK(p_peer_params != nullptr) << "Peer address "
                                  << ADDRESS_TO_LOGGABLE_STR(peer_address);

  std::lock_guard<std::recursive_mutex> lock(peer_cache_->codec_lock_);

  // Compute the MTU
  for (size_t i = 0; i < BTA_AV_CO_NUM_ELEMENTS(peer_cache_->peers_); i++) {
    const BtaAvCoPeer* p_peer = &peer_cache_->peers_[i];
    if (!p_peer->opened) continue;
    if (p_peer->addr != peer_address) continue;
    if (p_peer->mtu < min_mtu) min_mtu = p_peer->mtu;
  }
  p_peer_params->peer_mtu = min_mtu;
  p_peer_params->is_peer_edr = btif_av_is_peer_edr(peer_address);
  p_peer_params->peer_supports_3mbps =
      btif_av_peer_supports_3mbps(peer_address);
  log::verbose(
      "peer_address={} peer_mtu={} is_peer_edr={} peer_supports_3mbps={}",
      ADDRESS_TO_LOGGABLE_CSTR(peer_address), p_peer_params->peer_mtu,
      logbool(p_peer_params->is_peer_edr),
      logbool(p_peer_params->peer_supports_3mbps));
}

const tA2DP_ENCODER_INTERFACE* BtaAvCo::GetSourceEncoderInterface() {
  std::lock_guard<std::recursive_mutex> lock(peer_cache_->codec_lock_);

  return A2DP_GetEncoderInterface(bta_av_legacy_state_.getCodecConfig());
}

bool BtaAvCo::SetCodecUserConfig(
    const RawAddress& peer_address,
    const btav_a2dp_codec_config_t& codec_user_config, bool* p_restart_output) {
  uint8_t result_codec_config[AVDT_CODEC_SIZE];
  const BtaAvCoSep* p_sink = nullptr;
  bool restart_input = false;
  bool restart_output = false;
  bool config_updated = false;
  bool success = true;

  log::verbose("peer_address={} codec_user_config={{}}",
               ADDRESS_TO_LOGGABLE_STR(peer_address),
               codec_user_config.ToString());

  *p_restart_output = false;

  BtaAvCoPeer* p_peer = peer_cache_->FindPeer(peer_address);
  if (p_peer == nullptr) {
    log::error("cannot find peer {} to configure",
               ADDRESS_TO_LOGGABLE_STR(peer_address));
    success = false;
    goto done;
  }

  // Don't call BTA_AvReconfig() prior to retrieving all peer's capabilities
  if ((p_peer->num_rx_sinks != p_peer->num_sinks) &&
      (p_peer->num_sup_sinks != BTA_AV_CO_NUM_ELEMENTS(p_peer->sinks))) {
    log::warn("peer {} : not all peer's capabilities have been retrieved",
              ADDRESS_TO_LOGGABLE_STR(p_peer->addr));
    success = false;
    goto done;
  }

  // Find the peer SEP codec to use
  if (codec_user_config.codec_type < BTAV_A2DP_CODEC_INDEX_MAX) {
    p_sink = peer_cache_->FindPeerSink(p_peer, codec_user_config.codec_type,
                                       ContentProtectFlag());
  } else {
    // Use the current sink codec
    p_sink = p_peer->p_sink;
  }
  if (p_sink == nullptr) {
    log::error("peer {} : cannot find peer SEP to configure for codec type {}",
               ADDRESS_TO_LOGGABLE_STR(p_peer->addr),
               codec_user_config.codec_type);
    success = false;
    goto done;
  }

  tA2DP_ENCODER_INIT_PEER_PARAMS peer_params;
  GetPeerEncoderParameters(p_peer->addr, &peer_params);
  if (!p_peer->GetCodecs()->setCodecUserConfig(
          codec_user_config, &peer_params, p_sink->codec_caps,
          result_codec_config, &restart_input, &restart_output,
          &config_updated)) {
    success = false;
    goto done;
  }

  if (restart_output) {
    uint8_t num_protect = 0;
    if (ContentProtectEnabled() && p_peer->ContentProtectActive()) {
      num_protect = AVDT_CP_INFO_LEN;
    }

    p_sink = SelectSourceCodec(p_peer);
    if (p_sink == nullptr) {
      log::error("peer {} : cannot set up codec for the peer SINK",
                 ADDRESS_TO_LOGGABLE_STR(p_peer->addr));
      success = false;
      goto done;
    }

    p_peer->acceptor = false;
    log::verbose("call BTA_AvReconfig({})", loghex(p_peer->BtaAvHandle()));
    BTA_AvReconfig(p_peer->BtaAvHandle(), true, p_sink->sep_info_idx,
                   p_peer->codec_config, num_protect, bta_av_co_cp_scmst);
    *p_restart_output = true;
  }

done:
  // We send the upcall if there is no change or the user config failed for
  // current active peer, so the caller would know it failed. If there is no
  // error, the new selected codec configuration would be sent after we are
  // ready to start a new session with the audio HAL.
  // For none active peer, we unconditionally send the upcall, so the caller
  // would always know the result.
  // NOTE: Currently, the input is restarted by sending an upcall
  // and informing the Media Framework about the change.

  // Find the peer that is currently open
  BtaAvCoPeer* active_peer = bta_av_legacy_state_.getActivePeer();
  if (p_peer != nullptr &&
      (!restart_output || !success || p_peer != active_peer)) {
    return ReportSourceCodecState(p_peer);
  }

  return success;
}

bool BtaAvCo::SetCodecAudioConfig(
    const btav_a2dp_codec_config_t& codec_audio_config) {
  uint8_t result_codec_config[AVDT_CODEC_SIZE];
  bool restart_output = false;
  bool config_updated = false;

  log::verbose("codec_audio_config: {}", codec_audio_config.ToString());

  // Find the peer that is currently open
  BtaAvCoPeer* p_peer = bta_av_legacy_state_.getActivePeer();
  if (p_peer == nullptr) {
    log::error("no active peer to configure");
    return false;
  }

  // Don't call BTA_AvReconfig() prior to retrieving all peer's capabilities
  if ((p_peer->num_rx_sinks != p_peer->num_sinks) &&
      (p_peer->num_sup_sinks != BTA_AV_CO_NUM_ELEMENTS(p_peer->sinks))) {
    log::warn("peer {} : not all peer's capabilities have been retrieved",
              ADDRESS_TO_LOGGABLE_STR(p_peer->addr));
    return false;
  }

  // Use the current sink codec
  const BtaAvCoSep* p_sink = p_peer->p_sink;
  if (p_sink == nullptr) {
    log::error("peer {} : cannot find peer SEP to configure",
               ADDRESS_TO_LOGGABLE_STR(p_peer->addr));
    return false;
  }

  tA2DP_ENCODER_INIT_PEER_PARAMS peer_params;
  GetPeerEncoderParameters(p_peer->addr, &peer_params);
  if (!p_peer->GetCodecs()->setCodecAudioConfig(
          codec_audio_config, &peer_params, p_sink->codec_caps,
          result_codec_config, &restart_output, &config_updated)) {
    return false;
  }

  if (restart_output) {
    uint8_t num_protect = 0;
    if (ContentProtectEnabled() && p_peer->ContentProtectActive()) {
      num_protect = AVDT_CP_INFO_LEN;
    }

    SaveNewCodecConfig(p_peer, result_codec_config, p_sink->num_protect,
                       p_sink->protect_info);

    p_peer->acceptor = false;
    log::verbose("call BTA_AvReconfig({})", loghex(p_peer->BtaAvHandle()));
    BTA_AvReconfig(p_peer->BtaAvHandle(), true, p_sink->sep_info_idx,
                   p_peer->codec_config, num_protect, bta_av_co_cp_scmst);
  }

  if (config_updated) {
    // NOTE: Currently, the input is restarted by sending an upcall
    // and informing the Media Framework about the change of selected codec.
    return ReportSourceCodecState(p_peer);
  }

  return true;
}

int BtaAvCo::GetSourceEncoderEffectiveFrameSize() {
  std::lock_guard<std::recursive_mutex> lock(peer_cache_->codec_lock_);

  return A2DP_GetEecoderEffectiveFrameSize(
      bta_av_legacy_state_.getCodecConfig());
}

bool BtaAvCo::ReportSourceCodecState(BtaAvCoPeer* p_peer) {
  btav_a2dp_codec_config_t codec_config = {
    .codec_type = BTAV_A2DP_CODEC_INDEX_SINK_MAX,
    .codec_priority = BTAV_A2DP_CODEC_PRIORITY_DISABLED,
    .sample_rate =    BTAV_A2DP_CODEC_SAMPLE_RATE_NONE,
    .bits_per_sample = BTAV_A2DP_CODEC_BITS_PER_SAMPLE_NONE,
    .channel_mode = BTAV_A2DP_CODEC_CHANNEL_MODE_NONE,
    .codec_specific_1 = 0,
    .codec_specific_2 = 0,
    .codec_specific_3 = 0,
    .codec_specific_4 = 0,
  };
  std::vector<btav_a2dp_codec_config_t> codecs_local_capabilities;
  std::vector<btav_a2dp_codec_config_t> codecs_selectable_capabilities;

  log::verbose("peer_address={}", ADDRESS_TO_LOGGABLE_STR(p_peer->addr));
  A2dpCodecs* codecs = p_peer->GetCodecs();
  if (codecs == nullptr) {
    log::error("Peer codecs is set to null");
    return false;
  }
  if (!codecs->getCodecConfigAndCapabilities(&codec_config,
                                             &codecs_local_capabilities,
                                             &codecs_selectable_capabilities)) {
    log::warn(
        "Peer {} : error reporting audio source codec state: cannot get codec "
        "config and capabilities",
        ADDRESS_TO_LOGGABLE_STR(p_peer->addr));
    return false;
  }
  log::info("peer {} codec_config={{}}", ADDRESS_TO_LOGGABLE_STR(p_peer->addr),
            codec_config.ToString());
  btif_av_report_source_codec_state(p_peer->addr, codec_config,
                                    codecs_local_capabilities,
                                    codecs_selectable_capabilities);
  return true;
}

bool BtaAvCo::ReportSinkCodecState(BtaAvCoPeer* p_peer) {
  log::verbose("peer_address={}", ADDRESS_TO_LOGGABLE_CSTR(p_peer->addr));
  // Nothing to do (for now)
  return true;
}

void BtaAvCo::DebugDump(int fd) {
  std::lock_guard<std::recursive_mutex> lock(peer_cache_->codec_lock_);

  //
  // Active peer codec-specific stats
  //
  if (bta_av_legacy_state_.getActivePeer() != nullptr) {
    A2dpCodecs* a2dp_codecs = bta_av_legacy_state_.getActivePeer()->GetCodecs();
    if (a2dp_codecs != nullptr) {
      a2dp_codecs->debug_codec_dump(fd);
    }
  }

  dprintf(fd, "\nA2DP Peers State:\n");
  dprintf(
      fd, "  Active peer: %s\n",
      (bta_av_legacy_state_.getActivePeer() != nullptr)
          ? ADDRESS_TO_LOGGABLE_CSTR(bta_av_legacy_state_.getActivePeer()->addr)
          : "null");

  for (size_t i = 0; i < BTA_AV_CO_NUM_ELEMENTS(peer_cache_->peers_); i++) {
    const BtaAvCoPeer& peer = peer_cache_->peers_[i];
    if (peer.addr.IsEmpty()) {
      continue;
    }
    dprintf(fd, "  Peer: %s\n", ADDRESS_TO_LOGGABLE_CSTR(peer.addr));
    dprintf(fd, "    Number of sinks: %u\n", peer.num_sinks);
    dprintf(fd, "    Number of sources: %u\n", peer.num_sources);
    dprintf(fd, "    Number of SEPs: %u\n", peer.num_seps);
    dprintf(fd, "    Number of received sinks: %u\n", peer.num_rx_sinks);
    dprintf(fd, "    Number of received sources: %u\n", peer.num_rx_sources);
    dprintf(fd, "    Number of supported sinks: %u\n", peer.num_sup_sinks);
    dprintf(fd, "    Number of supported sources: %u\n", peer.num_sup_sources);
    dprintf(fd, "    Acceptor: %s\n", (peer.acceptor) ? "true" : "false");
    dprintf(fd, "    Reconfig needed: %s\n",
            (peer.reconfig_needed) ? "true" : "false");
    dprintf(fd, "    Opened: %s\n", (peer.opened) ? "true" : "false");
    dprintf(fd, "    MTU: %u\n", peer.mtu);
    dprintf(fd, "    UUID to connect: 0x%x\n", peer.uuid_to_connect);
    dprintf(fd, "    BTA AV handle: %u\n", peer.BtaAvHandle());
  }
}

std::optional<::bluetooth::audio::a2dp::provider::a2dp_configuration>
BtaAvCo::GetProviderCodecConfiguration(BtaAvCoPeer* p_peer) {
  // Gather peer codec capabilities.
  std::vector<::bluetooth::audio::a2dp::provider::a2dp_remote_capabilities>
      a2dp_remote_caps;
  for (size_t index = 0; index < p_peer->num_sup_sinks; index++) {
    const BtaAvCoSep* p_sink = &p_peer->sinks[index];
    auto& capabilities = a2dp_remote_caps.emplace_back();
    capabilities.seid = p_sink->seid;
    capabilities.capabilities = p_sink->codec_caps;
  }

  // Get the configuration of the preferred codec as codec hint.
  btav_a2dp_codec_config_t codec_config =
      p_peer->GetCodecs()->orderedSourceCodecs().front()->getCodecUserConfig();

  // Pass all gathered codec capabilities to the provider
  return ::bluetooth::audio::a2dp::provider::get_a2dp_configuration(
      p_peer->addr, a2dp_remote_caps, codec_config);
}

BtaAvCoSep* BtaAvCo::SelectProviderCodecConfiguration(
    BtaAvCoPeer* p_peer,
    const ::bluetooth::audio::a2dp::provider::a2dp_configuration&
        provider_codec_config) {
  // Configure the selected offload codec for the active peer.
  // This function _must_ have the same external behaviour as
  // AttemptSourceCodecSelection, except the configuration
  // is provided by the HAL rather than derived locally.

  log::info("Configuration={}", provider_codec_config.toString());

  // Identify the selected sink.
  auto* p_sink = peer_cache_->FindPeerSink(
      p_peer, provider_codec_config.codec_parameters.codec_type,
      ContentProtectFlag());
  ASSERT_LOG(p_sink != nullptr, "Unable to find the selected codec config");

  // Identify the selected codec.
  auto* codec_config = reinterpret_cast<A2dpCodecConfigExt*>(
      p_peer->GetCodecs()->findSourceCodecConfig(
          provider_codec_config.codec_parameters.codec_type));
  ASSERT_LOG(codec_config != nullptr,
             "Unable to find the selected codec config");

  // Update the vendor codec parameters and codec configuration.
  codec_config->setCodecConfig(
      provider_codec_config.codec_parameters,
      provider_codec_config.codec_config,
      provider_codec_config.vendor_specific_parameters);

  // Select the codec config.
  p_peer->GetCodecs()->setCurrentCodecConfig(codec_config);
  p_peer->p_sink = p_sink;
  SaveNewCodecConfig(p_peer, provider_codec_config.codec_config,
                     p_sink->num_protect, p_sink->protect_info);

  return p_sink;
}

const BtaAvCoSep* BtaAvCo::SelectSourceCodec(BtaAvCoPeer* p_peer) {
  // Update all selectable codecs.
  // This is needed to update the selectable parameters for each codec.
  // NOTE: The selectable codec info is used only for informational purpose.
  UpdateAllSelectableSourceCodecs(p_peer);

  // Query the preferred codec configuration for offloaded codecs.
  auto provider_codec_config = GetProviderCodecConfiguration(p_peer);

  // Query the preferred codec configuration for software codecs.
  A2dpCodecConfig* software_codec_config = nullptr;
  for (const auto& iter : p_peer->GetCodecs()->orderedSourceCodecs()) {
    if (::bluetooth::audio::a2dp::provider::supports_codec(
            iter->codecIndex())) {
      continue;
    }

    // Find the peer Sink for the codec
    uint8_t new_codec_config[AVDT_CODEC_SIZE];
    const BtaAvCoSep* p_sink = peer_cache_->FindPeerSink(
        p_peer, iter->codecIndex(), ContentProtectFlag());

    if (p_sink == nullptr) {
      log::verbose("peer Sink for codec {} not found", iter->name());
      continue;
    }

    if (!p_peer->GetCodecs()->setCodecConfig(
            p_sink->codec_caps, true /* is_capability */, new_codec_config,
            false /* select_current_codec */)) {
      log::verbose("cannot set source codec {}", iter->name());
    } else {
      log::verbose("feasible to set source codec {}", iter->name());
      software_codec_config = iter;
      break;
    }
  }

  if (provider_codec_config.has_value() &&
      (software_codec_config == nullptr ||
       bta_av_co_should_select_hardware_codec(*software_codec_config,
                                              provider_codec_config.value()))) {
    // Select hardware offload codec configuration
    return SelectProviderCodecConfiguration(p_peer,
                                            provider_codec_config.value());
  }

  if (software_codec_config != nullptr) {
    // Select software codec configuration
    return AttemptSourceCodecSelection(*software_codec_config, p_peer);
  }

  return nullptr;
}

const BtaAvCoSep* BtaAvCo::SelectSinkCodec(BtaAvCoPeer* p_peer) {
  const BtaAvCoSep* p_source = nullptr;

  // Update all selectable codecs.
  // This is needed to update the selectable parameters for each codec.
  // NOTE: The selectable codec info is used only for informational purpose.
  UpdateAllSelectableSinkCodecs(p_peer);

  // Select the codec
  for (const auto& iter : p_peer->GetCodecs()->orderedSinkCodecs()) {
    log::verbose("trying codec {}", iter->name());
    p_source = AttemptSinkCodecSelection(*iter, p_peer);
    if (p_source != nullptr) {
      log::verbose("selected codec {}", iter->name());
      break;
    }
    log::verbose("cannot use codec {}", iter->name());
  }

  // NOTE: Unconditionally dispatch the event to make sure a callback with
  // the most recent codec info is generated.
  ReportSinkCodecState(p_peer);

  return p_source;
}

const BtaAvCoSep* BtaAvCo::AttemptSourceCodecSelection(
    const A2dpCodecConfig& codec_config, BtaAvCoPeer* p_peer) {
  uint8_t new_codec_config[AVDT_CODEC_SIZE];

  log::verbose("");

  // Find the peer Sink for the codec
  BtaAvCoSep* p_sink = peer_cache_->FindPeerSink(
      p_peer, codec_config.codecIndex(), ContentProtectFlag());
  if (p_sink == nullptr) {
    log::verbose("peer Sink for codec {} not found", codec_config.name());
    return nullptr;
  }
  if (!p_peer->GetCodecs()->setCodecConfig(
          p_sink->codec_caps, true /* is_capability */, new_codec_config,
          true /* select_current_codec */)) {
    log::verbose("cannot set source codec {}", codec_config.name());
    return nullptr;
  }
  p_peer->p_sink = p_sink;

  SaveNewCodecConfig(p_peer, new_codec_config, p_sink->num_protect,
                     p_sink->protect_info);

  return p_sink;
}

const BtaAvCoSep* BtaAvCo::AttemptSinkCodecSelection(
    const A2dpCodecConfig& codec_config, BtaAvCoPeer* p_peer) {
  uint8_t new_codec_config[AVDT_CODEC_SIZE];

  log::verbose("");

  // Find the peer Source for the codec
  BtaAvCoSep* p_source = peer_cache_->FindPeerSource(
      p_peer, codec_config.codecIndex(), ContentProtectFlag());
  if (p_source == nullptr) {
    log::verbose("peer Source for codec {} not found", codec_config.name());
    return nullptr;
  }
  if (!p_peer->GetCodecs()->setSinkCodecConfig(
          p_source->codec_caps, true /* is_capability */, new_codec_config,
          true /* select_current_codec */)) {
    log::verbose("cannot set sink codec {}", codec_config.name());
    return nullptr;
  }
  p_peer->p_source = p_source;

  SaveNewCodecConfig(p_peer, new_codec_config, p_source->num_protect,
                     p_source->protect_info);

  return p_source;
}

size_t BtaAvCo::UpdateAllSelectableSourceCodecs(BtaAvCoPeer* p_peer) {
  log::verbose("peer {}", ADDRESS_TO_LOGGABLE_CSTR(p_peer->addr));

  size_t updated_codecs = 0;
  for (const auto& iter : p_peer->GetCodecs()->orderedSourceCodecs()) {
    log::verbose("updating selectable codec {}", iter->name());
    if (UpdateSelectableSourceCodec(*iter, p_peer)) {
      updated_codecs++;
    }
  }
  return updated_codecs;
}

bool BtaAvCo::UpdateSelectableSourceCodec(const A2dpCodecConfig& codec_config,
                                          BtaAvCoPeer* p_peer) {
  log::verbose("peer {}", ADDRESS_TO_LOGGABLE_CSTR(p_peer->addr));

  // Find the peer Sink for the codec
  const BtaAvCoSep* p_sink = peer_cache_->FindPeerSink(
      p_peer, codec_config.codecIndex(), ContentProtectFlag());
  if (p_sink == nullptr) {
    // The peer Sink device does not support this codec
    return false;
  }
  if (!p_peer->GetCodecs()->setPeerSinkCodecCapabilities(p_sink->codec_caps)) {
    log::warn("cannot update peer {} codec capabilities for {}",
              ADDRESS_TO_LOGGABLE_CSTR(p_peer->addr),
              A2DP_CodecName(p_sink->codec_caps));
    return false;
  }
  return true;
}

size_t BtaAvCo::UpdateAllSelectableSinkCodecs(BtaAvCoPeer* p_peer) {
  log::verbose("peer {}", ADDRESS_TO_LOGGABLE_CSTR(p_peer->addr));

  size_t updated_codecs = 0;
  for (const auto& iter : p_peer->GetCodecs()->orderedSinkCodecs()) {
    log::verbose("updating selectable codec {}", iter->name());
    if (UpdateSelectableSinkCodec(*iter, p_peer)) {
      updated_codecs++;
    }
  }
  return updated_codecs;
}

bool BtaAvCo::UpdateSelectableSinkCodec(const A2dpCodecConfig& codec_config,
                                        BtaAvCoPeer* p_peer) {
  log::verbose("peer {}", ADDRESS_TO_LOGGABLE_CSTR(p_peer->addr));

  // Find the peer Source for the codec
  const BtaAvCoSep* p_source = peer_cache_->FindPeerSource(
      p_peer, codec_config.codecIndex(), ContentProtectFlag());
  if (p_source == nullptr) {
    // The peer Source device does not support this codec
    return false;
  }
  if (!p_peer->GetCodecs()->setPeerSourceCodecCapabilities(
          p_source->codec_caps)) {
    log::warn("cannot update peer {} codec capabilities for {}",
              ADDRESS_TO_LOGGABLE_CSTR(p_peer->addr),
              A2DP_CodecName(p_source->codec_caps));
    return false;
  }
  return true;
}

void BtaAvCo::SaveNewCodecConfig(BtaAvCoPeer* p_peer,
                                 const uint8_t* new_codec_config,
                                 uint8_t num_protect,
                                 const uint8_t* p_protect_info) {
  log::verbose("peer {}", ADDRESS_TO_LOGGABLE_CSTR(p_peer->addr));
  log::verbose("codec: {}", A2DP_CodecInfoString(new_codec_config));

  std::lock_guard<std::recursive_mutex> lock(peer_cache_->codec_lock_);
  bta_av_legacy_state_.setCodecConfig(new_codec_config);
  memcpy(p_peer->codec_config, new_codec_config, AVDT_CODEC_SIZE);

  if (ContentProtectEnabled()) {
    // Check if this Sink supports SCMS
    bool cp_active = AudioProtectHasScmst(num_protect, p_protect_info);
    p_peer->SetContentProtectActive(cp_active);
  }
}

bool BtaAvCo::SetCodecOtaConfig(BtaAvCoPeer* p_peer,
                                const uint8_t* p_ota_codec_config,
                                uint8_t num_protect,
                                const uint8_t* p_protect_info,
                                bool* p_restart_output) {
  uint8_t result_codec_config[AVDT_CODEC_SIZE];
  bool restart_input = false;
  bool restart_output = false;
  bool config_updated = false;

  log::info("peer_address={}, codec: {}", ADDRESS_TO_LOGGABLE_STR(p_peer->addr),
            A2DP_CodecInfoString(p_ota_codec_config));

  *p_restart_output = false;

  // Find the peer SEP codec to use
  const BtaAvCoSep* p_sink = peer_cache_->FindPeerSink(
      p_peer, A2DP_SourceCodecIndex(p_ota_codec_config), ContentProtectFlag());
  if ((p_peer->num_sup_sinks > 0) && (p_sink == nullptr)) {
    // There are no peer SEPs if we didn't do the discovery procedure yet.
    // We have all the information we need from the peer, so we can
    // proceed with the OTA codec configuration.
    log::error("peer {} : cannot find peer SEP to configure",
               ADDRESS_TO_LOGGABLE_STR(p_peer->addr));
    return false;
  }

  tA2DP_ENCODER_INIT_PEER_PARAMS peer_params;
  GetPeerEncoderParameters(p_peer->addr, &peer_params);
  if (!p_peer->GetCodecs()->setCodecOtaConfig(
          p_ota_codec_config, &peer_params, result_codec_config, &restart_input,
          &restart_output, &config_updated)) {
    log::error("peer {} : cannot set OTA config",
               ADDRESS_TO_LOGGABLE_STR(p_peer->addr));
    return false;
  }

  if (restart_output) {
    log::verbose("restart output for codec: {}",
                 A2DP_CodecInfoString(result_codec_config));

    *p_restart_output = true;
    p_peer->p_sink = p_sink;
    SaveNewCodecConfig(p_peer, result_codec_config, num_protect,
                       p_protect_info);
  }

  if (restart_input || config_updated) {
    // NOTE: Currently, the input is restarted by sending an upcall
    // and informing the Media Framework about the change of selected codec.
    ReportSourceCodecState(p_peer);
  }

  return true;
}

void bta_av_co_init(
    const std::vector<btav_a2dp_codec_config_t>& codec_priorities,
    std::vector<btav_a2dp_codec_info_t>* supported_codecs) {
  bta_av_co_cb.Init(codec_priorities, supported_codecs);
}

bool bta_av_co_is_supported_codec(btav_a2dp_codec_index_t codec_index) {
  return bta_av_co_cb.IsSupportedCodec(codec_index);
}

A2dpCodecConfig* bta_av_get_a2dp_current_codec(void) {
  return bta_av_co_cb.GetActivePeerCurrentCodec();
}

A2dpCodecConfig* bta_av_get_a2dp_peer_current_codec(
    const RawAddress& peer_address) {
  return bta_av_co_cb.GetPeerCurrentCodec(peer_address);
}

bool bta_av_co_audio_init(btav_a2dp_codec_index_t codec_index,
                          AvdtpSepConfig* p_cfg) {
  return A2DP_InitCodecConfig(codec_index, p_cfg);
}

void bta_av_co_audio_disc_res(tBTA_AV_HNDL bta_av_handle,
                              const RawAddress& peer_address, uint8_t num_seps,
                              uint8_t num_sinks, uint8_t num_sources,
                              uint16_t uuid_local) {
  bta_av_co_cb.ProcessDiscoveryResult(bta_av_handle, peer_address, num_seps,
                                      num_sinks, num_sources, uuid_local);
}

static void bta_av_co_store_peer_codectype(const BtaAvCoPeer* p_peer) {
  int index, peer_codec_type = 0;
  const BtaAvCoSep* p_sink;
  log::verbose("");
  for (index = 0; index < p_peer->num_sup_sinks; index++) {
    p_sink = &p_peer->sinks[index];
    peer_codec_type |= A2DP_IotGetPeerSinkCodecType(p_sink->codec_caps);
  }

  DEVICE_IOT_CONFIG_ADDR_SET_HEX(p_peer->addr, IOT_CONF_KEY_A2DP_CODECTYPE,
                                 peer_codec_type, IOT_CONF_BYTE_NUM_1);
}

static bool bta_av_co_should_select_hardware_codec(
    const A2dpCodecConfig& software_config,
    const ::bluetooth::audio::a2dp::provider::a2dp_configuration&
        hardware_config) {
  btav_a2dp_codec_index_t software_codec_index = software_config.codecIndex();
  btav_a2dp_codec_index_t hardware_offload_index =
      hardware_config.codec_parameters.codec_type;

  // Prioritize any offload codec except SBC and AAC
  if (A2DP_GetCodecType(hardware_config.codec_config) ==
      A2DP_MEDIA_CT_NON_A2DP) {
    log::verbose("select hardware codec: {}",
                 A2DP_CodecIndexStr(hardware_offload_index));
    return true;
  }
  // Prioritize LDAC, AptX HD and AptX over AAC and SBC offload codecs
  if (software_codec_index == BTAV_A2DP_CODEC_INDEX_SOURCE_LDAC ||
      software_codec_index == BTAV_A2DP_CODEC_INDEX_SOURCE_APTX_HD ||
      software_codec_index == BTAV_A2DP_CODEC_INDEX_SOURCE_APTX) {
    log::verbose("select software codec: {}",
                 A2DP_CodecIndexStr(software_codec_index));
    return false;
  }
  // Prioritize AAC offload
  if (hardware_offload_index == BTAV_A2DP_CODEC_INDEX_SOURCE_AAC) {
    log::verbose("select hardware codec: {}",
                 A2DP_CodecIndexStr(hardware_offload_index));
    return true;
  }
  // Prioritize AAC software
  if (software_codec_index == BTAV_A2DP_CODEC_INDEX_SOURCE_AAC) {
    log::verbose("select software codec: {}",
                 A2DP_CodecIndexStr(software_codec_index));
    return false;
  }
  // Prioritize SBC offload
  if (hardware_offload_index == BTAV_A2DP_CODEC_INDEX_SOURCE_SBC) {
    log::verbose("select hardware codec: {}",
                 A2DP_CodecIndexStr(hardware_offload_index));
    return true;
  }
  // Prioritize SBC software
  if (software_codec_index == BTAV_A2DP_CODEC_INDEX_SOURCE_SBC) {
    log::verbose("select software codec: {}",
                 A2DP_CodecIndexStr(software_codec_index));
    return false;
  }
  log::error("select unknown software codec: {}",
             A2DP_CodecIndexStr(software_codec_index));
  return false;
}

tA2DP_STATUS bta_av_co_audio_getconfig(tBTA_AV_HNDL bta_av_handle,
                                       const RawAddress& peer_address,
                                       uint8_t* p_codec_info,
                                       uint8_t* p_sep_info_idx, uint8_t seid,
                                       uint8_t* p_num_protect,
                                       uint8_t* p_protect_info) {
  uint16_t peer_uuid = bta_av_co_cb.peer_cache_->FindPeerUuid(bta_av_handle);

  log::verbose("peer {} bta_av_handle=0x{:x} peer_uuid=0x{:x}",
               ADDRESS_TO_LOGGABLE_CSTR(peer_address), bta_av_handle,
               peer_uuid);

  switch (peer_uuid) {
    case UUID_SERVCLASS_AUDIO_SOURCE:
      return bta_av_co_cb.ProcessSinkGetConfig(
          bta_av_handle, peer_address, p_codec_info, p_sep_info_idx, seid,
          p_num_protect, p_protect_info);
    case UUID_SERVCLASS_AUDIO_SINK:
      return bta_av_co_cb.ProcessSourceGetConfig(
          bta_av_handle, peer_address, p_codec_info, p_sep_info_idx, seid,
          p_num_protect, p_protect_info);
    default:
      break;
  }
  log::error("peer {} : Invalid peer UUID: 0x{:x} for bta_av_handle 0x{:x}",
             ADDRESS_TO_LOGGABLE_CSTR(peer_address), peer_uuid, bta_av_handle);
  return A2DP_FAIL;
}

void bta_av_co_audio_setconfig(tBTA_AV_HNDL bta_av_handle,
                               const RawAddress& peer_address,
                               const uint8_t* p_codec_info, uint8_t seid,
                               uint8_t num_protect,
                               const uint8_t* p_protect_info,
                               uint8_t t_local_sep, uint8_t avdt_handle) {
  bta_av_co_cb.ProcessSetConfig(bta_av_handle, peer_address, p_codec_info, seid,
                                num_protect, p_protect_info, t_local_sep,
                                avdt_handle);
}

void bta_av_co_audio_open(tBTA_AV_HNDL bta_av_handle,
                          const RawAddress& peer_address, uint16_t mtu) {
  bta_av_co_cb.ProcessOpen(bta_av_handle, peer_address, mtu);
}

void bta_av_co_audio_close(tBTA_AV_HNDL bta_av_handle,
                           const RawAddress& peer_address) {
  bta_av_co_cb.ProcessClose(bta_av_handle, peer_address);
}

void bta_av_co_audio_start(tBTA_AV_HNDL bta_av_handle,
                           const RawAddress& peer_address,
                           const uint8_t* p_codec_info, bool* p_no_rtp_header) {
  bta_av_co_cb.ProcessStart(bta_av_handle, peer_address, p_codec_info,
                            p_no_rtp_header);
}

void bta_av_co_audio_stop(tBTA_AV_HNDL bta_av_handle,
                          const RawAddress& peer_address) {
  bta_av_co_cb.ProcessStop(bta_av_handle, peer_address);
}

BT_HDR* bta_av_co_audio_source_data_path(const uint8_t* p_codec_info,
                                         uint32_t* p_timestamp) {
  return bta_av_co_cb.GetNextSourceDataPacket(p_codec_info, p_timestamp);
}

void bta_av_co_audio_drop(tBTA_AV_HNDL bta_av_handle,
                          const RawAddress& peer_address) {
  bta_av_co_cb.DataPacketWasDropped(bta_av_handle, peer_address);
}

void bta_av_co_audio_delay(tBTA_AV_HNDL bta_av_handle,
                           const RawAddress& peer_address, uint16_t delay) {
  bta_av_co_cb.ProcessAudioDelay(bta_av_handle, peer_address, delay);
}

void bta_av_co_audio_update_mtu(tBTA_AV_HNDL bta_av_handle,
                                const RawAddress& peer_address, uint16_t mtu) {
  bta_av_co_cb.UpdateMtu(bta_av_handle, peer_address, mtu);
}

bool bta_av_co_set_active_peer(const RawAddress& peer_address) {
  return bta_av_co_cb.SetActivePeer(peer_address);
}

void bta_av_co_save_codec(const uint8_t* new_codec_config) {
  return bta_av_co_cb.SaveCodec(new_codec_config);
}

void bta_av_co_get_peer_params(const RawAddress& peer_address,
                               tA2DP_ENCODER_INIT_PEER_PARAMS* p_peer_params) {
  bta_av_co_cb.GetPeerEncoderParameters(peer_address, p_peer_params);
}

const tA2DP_ENCODER_INTERFACE* bta_av_co_get_encoder_interface(void) {
  return bta_av_co_cb.GetSourceEncoderInterface();
}

bool bta_av_co_set_codec_user_config(
    const RawAddress& peer_address,
    const btav_a2dp_codec_config_t& codec_user_config, bool* p_restart_output) {
  return bta_av_co_cb.SetCodecUserConfig(peer_address, codec_user_config,
                                         p_restart_output);
}

bool bta_av_co_set_codec_audio_config(
    const btav_a2dp_codec_config_t& codec_audio_config) {
  return bta_av_co_cb.SetCodecAudioConfig(codec_audio_config);
}

int bta_av_co_get_encoder_effective_frame_size() {
  return bta_av_co_cb.GetSourceEncoderEffectiveFrameSize();
}

btav_a2dp_scmst_info_t bta_av_co_get_scmst_info(
    const RawAddress& peer_address) {
  BtaAvCoPeer* p_peer = bta_av_co_cb.peer_cache_->FindPeer(peer_address);
  CHECK(p_peer != nullptr);
  btav_a2dp_scmst_info_t scmst_info{};
  scmst_info.enable_status = BTAV_A2DP_SCMST_DISABLED;

  if (p_peer->ContentProtectActive()) {
    scmst_info.enable_status = BTAV_A2DP_SCMST_ENABLED;
    scmst_info.cp_header = bta_av_co_cb.ContentProtectFlag();
  }

  return scmst_info;
}

void btif_a2dp_codec_debug_dump(int fd) { bta_av_co_cb.DebugDump(fd); }
