/*
 * Copyright 2020 HIMSA II K/S - www.himsa.com. Represented by EHIMA
 * - www.ehima.com
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

#include "devices.h"

#include <android_bluetooth_flags.h>
#include <base/strings/string_number_conversions.h>

#include "acl_api.h"
#include "bta_gatt_queue.h"
#include "btif/include/btif_storage.h"
#include "internal_include/bt_trace.h"

using bluetooth::hci::kIsoCigPhy1M;
using bluetooth::hci::kIsoCigPhy2M;
using le_audio::DeviceConnectState;
using le_audio::set_configurations::CodecConfigSetting;
using le_audio::types::ase;
using le_audio::types::AseState;
using le_audio::types::AudioContexts;
using le_audio::types::AudioLocations;
using le_audio::types::BidirectionalPair;
using le_audio::types::CisState;
using le_audio::types::DataPathState;
using le_audio::types::LeAudioContextType;
using le_audio::types::LeAudioCoreCodecConfig;

namespace le_audio {
std::ostream& operator<<(std::ostream& os, const DeviceConnectState& state) {
  const char* char_value_ = "UNKNOWN";

  switch (state) {
    case DeviceConnectState::CONNECTED:
      char_value_ = "CONNECTED";
      break;
    case DeviceConnectState::DISCONNECTED:
      char_value_ = "DISCONNECTED";
      break;
    case DeviceConnectState::REMOVING:
      char_value_ = "REMOVING";
      break;
    case DeviceConnectState::DISCONNECTING:
      char_value_ = "DISCONNECTING";
      break;
    case DeviceConnectState::DISCONNECTING_AND_RECOVER:
      char_value_ = "DISCONNECTING_AND_RECOVER";
      break;
    case DeviceConnectState::CONNECTING_BY_USER:
      char_value_ = "CONNECTING_BY_USER";
      break;
    case DeviceConnectState::CONNECTED_BY_USER_GETTING_READY:
      char_value_ = "CONNECTED_BY_USER_GETTING_READY";
      break;
    case DeviceConnectState::CONNECTING_AUTOCONNECT:
      char_value_ = "CONNECTING_AUTOCONNECT";
      break;
    case DeviceConnectState::CONNECTED_AUTOCONNECT_GETTING_READY:
      char_value_ = "CONNECTED_AUTOCONNECT_GETTING_READY";
      break;
  }

  os << char_value_ << " ("
     << "0x" << std::setfill('0') << std::setw(2) << static_cast<int>(state)
     << ")";
  return os;
}

static uint32_t GetFirstLeft(const AudioLocations& audio_locations) {
  uint32_t audio_location_ulong = audio_locations.to_ulong();

  if (audio_location_ulong & codec_spec_conf::kLeAudioLocationFrontLeft)
    return codec_spec_conf::kLeAudioLocationFrontLeft;

  if (audio_location_ulong & codec_spec_conf::kLeAudioLocationBackLeft)
    return codec_spec_conf::kLeAudioLocationBackLeft;

  if (audio_location_ulong & codec_spec_conf::kLeAudioLocationFrontLeftOfCenter)
    return codec_spec_conf::kLeAudioLocationFrontLeftOfCenter;

  if (audio_location_ulong & codec_spec_conf::kLeAudioLocationSideLeft)
    return codec_spec_conf::kLeAudioLocationSideLeft;

  if (audio_location_ulong & codec_spec_conf::kLeAudioLocationTopFrontLeft)
    return codec_spec_conf::kLeAudioLocationTopFrontLeft;

  if (audio_location_ulong & codec_spec_conf::kLeAudioLocationTopBackLeft)
    return codec_spec_conf::kLeAudioLocationTopBackLeft;

  if (audio_location_ulong & codec_spec_conf::kLeAudioLocationTopSideLeft)
    return codec_spec_conf::kLeAudioLocationTopSideLeft;

  if (audio_location_ulong & codec_spec_conf::kLeAudioLocationBottomFrontLeft)
    return codec_spec_conf::kLeAudioLocationBottomFrontLeft;

  if (audio_location_ulong & codec_spec_conf::kLeAudioLocationFrontLeftWide)
    return codec_spec_conf::kLeAudioLocationFrontLeftWide;

  if (audio_location_ulong & codec_spec_conf::kLeAudioLocationLeftSurround)
    return codec_spec_conf::kLeAudioLocationLeftSurround;

  return 0;
}

static uint32_t GetFirstRight(const AudioLocations& audio_locations) {
  uint32_t audio_location_ulong = audio_locations.to_ulong();

  if (audio_location_ulong & codec_spec_conf::kLeAudioLocationFrontRight)
    return codec_spec_conf::kLeAudioLocationFrontRight;

  if (audio_location_ulong & codec_spec_conf::kLeAudioLocationBackRight)
    return codec_spec_conf::kLeAudioLocationBackRight;

  if (audio_location_ulong &
      codec_spec_conf::kLeAudioLocationFrontRightOfCenter)
    return codec_spec_conf::kLeAudioLocationFrontRightOfCenter;

  if (audio_location_ulong & codec_spec_conf::kLeAudioLocationSideRight)
    return codec_spec_conf::kLeAudioLocationSideRight;

  if (audio_location_ulong & codec_spec_conf::kLeAudioLocationTopFrontRight)
    return codec_spec_conf::kLeAudioLocationTopFrontRight;

  if (audio_location_ulong & codec_spec_conf::kLeAudioLocationTopBackRight)
    return codec_spec_conf::kLeAudioLocationTopBackRight;

  if (audio_location_ulong & codec_spec_conf::kLeAudioLocationTopSideRight)
    return codec_spec_conf::kLeAudioLocationTopSideRight;

  if (audio_location_ulong & codec_spec_conf::kLeAudioLocationBottomFrontRight)
    return codec_spec_conf::kLeAudioLocationBottomFrontRight;

  if (audio_location_ulong & codec_spec_conf::kLeAudioLocationFrontRightWide)
    return codec_spec_conf::kLeAudioLocationFrontRightWide;

  if (audio_location_ulong & codec_spec_conf::kLeAudioLocationRightSurround)
    return codec_spec_conf::kLeAudioLocationRightSurround;

  return 0;
}

uint32_t PickAudioLocation(types::LeAudioConfigurationStrategy strategy,
                           const AudioLocations& device_locations,
                           AudioLocations& group_locations) {
  LOG_DEBUG("strategy: %d, locations: 0x%lx, input group locations: 0x%lx",
            (int)strategy, device_locations.to_ulong(),
            group_locations.to_ulong());

  auto is_left_not_yet_assigned =
      !(group_locations.to_ulong() & codec_spec_conf::kLeAudioLocationAnyLeft);
  auto is_right_not_yet_assigned =
      !(group_locations.to_ulong() & codec_spec_conf::kLeAudioLocationAnyRight);
  uint32_t left_device_loc = GetFirstLeft(device_locations);
  uint32_t right_device_loc = GetFirstRight(device_locations);

  if (left_device_loc == 0 && right_device_loc == 0) {
    LOG_WARN("Can't find device able to render left  and right audio channel");
  }

  switch (strategy) {
    case types::LeAudioConfigurationStrategy::MONO_ONE_CIS_PER_DEVICE:
    case types::LeAudioConfigurationStrategy::STEREO_TWO_CISES_PER_DEVICE:
      if (left_device_loc && is_left_not_yet_assigned) {
        group_locations |= left_device_loc;
        return left_device_loc;
      }

      if (right_device_loc && is_right_not_yet_assigned) {
        group_locations |= right_device_loc;
        return right_device_loc;
      }
      break;

    case types::LeAudioConfigurationStrategy::STEREO_ONE_CIS_PER_DEVICE:
      if (left_device_loc && right_device_loc) {
        group_locations |= left_device_loc | right_device_loc;
        return left_device_loc | right_device_loc;
      }
      break;
    default:
      LOG_ALWAYS_FATAL("%s: Unknown strategy: %hhu", __func__, strategy);
      return 0;
  }

  LOG_ERROR(
      "Can't find device for left/right channel. Strategy: %hhu, "
      "device_locations: %lx, output group_locations: %lx.",
      strategy, device_locations.to_ulong(), group_locations.to_ulong());

  /* Return either any left or any right audio location. It might result with
   * multiple devices within the group having the same location.
   */
  return left_device_loc ? left_device_loc : right_device_loc;
}

bool LeAudioDevice::ConfigureAses(
    const le_audio::set_configurations::SetConfiguration& ent,
    LeAudioContextType context_type,
    uint8_t* number_of_already_active_group_ase,
    BidirectionalPair<AudioLocations>& group_audio_locations_memo,
    const BidirectionalPair<AudioContexts>& metadata_context_types,
    const BidirectionalPair<std::vector<uint8_t>>& ccid_lists,
    bool reuse_cis_id) {
  /* First try to use the already configured ASE */
  auto ase = GetFirstActiveAseByDirection(ent.direction);
  if (ase) {
    LOG_INFO("Using an already active ASE id=%d", ase->id);
  } else {
    ase = GetFirstInactiveAse(ent.direction, reuse_cis_id);
  }

  if (!ase) {
    LOG_ERROR("Unable to find an ASE to configure");
    return false;
  }

  /* The number_of_already_active_group_ase keeps all the active ases
   * in other devices in the group.
   * This function counts active ases only for this device, and we count here
   * new active ases and already active ases which we want to reuse in the
   * scenario
   */
  uint8_t active_ases = *number_of_already_active_group_ase;
  uint8_t max_required_ase_per_dev =
      ent.ase_cnt / ent.device_cnt + (ent.ase_cnt % ent.device_cnt);
  le_audio::types::LeAudioConfigurationStrategy strategy = ent.strategy;

  auto pac = GetCodecConfigurationSupportedPac(ent.direction, ent.codec);
  if (!pac) return false;

  int needed_ase = std::min((int)(max_required_ase_per_dev),
                            (int)(ent.ase_cnt - active_ases));

  AudioLocations audio_locations = 0;

  /* Check direction and if audio location allows to create more cise */
  if (ent.direction == types::kLeAudioDirectionSink) {
    audio_locations = snk_audio_locations_;
  } else {
    audio_locations = src_audio_locations_;
  }

  for (; needed_ase && ase; needed_ase--) {
    ase->active = true;
    ase->configured_for_context_type = context_type;
    ase->is_codec_in_controller = ent.is_codec_in_controller;
    ase->data_path_id = ent.data_path_id;
    active_ases++;

    /* In case of late connect, we could be here for STREAMING ase.
     * in such case, it is needed to mark ase as known active ase which
     * is important to validate scenario and is done already few lines above.
     * Nothing more to do is needed here.
     */
    if (ase->state != AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING) {
      if (ase->state == AseState::BTA_LE_AUDIO_ASE_STATE_CODEC_CONFIGURED)
        ase->reconfigure = true;

      ase->target_latency = ent.qos.target_latency;
      ase->codec_id = ent.codec.id;
      ase->codec_config = ent.codec.params;

      /* Let's choose audio channel allocation if not set */
      ase->codec_config.Add(
          codec_spec_conf::kLeAudioLtvTypeAudioChannelAllocation,
          PickAudioLocation(strategy, audio_locations,
                            group_audio_locations_memo.get(ent.direction)));

      /* Get default value if no requirement for specific frame blocks per sdu
       */
      if (!ase->codec_config.Find(
              codec_spec_conf::kLeAudioLtvTypeCodecFrameBlocksPerSdu)) {
        ase->codec_config.Add(
            codec_spec_conf::kLeAudioLtvTypeCodecFrameBlocksPerSdu,
            GetMaxCodecFramesPerSduFromPac(pac));
      }

      /* Recalculate Max SDU size */
      auto core_config = ent.codec.params.GetAsCoreCodecConfig();
      ase->max_sdu_size = core_config.GetChannelCountPerIsoStream() *
                          core_config.octets_per_codec_frame.value_or(0) *
                          core_config.codec_frames_blocks_per_sdu.value_or(1);

      ase->retrans_nb = ent.qos.retransmission_number;
      ase->max_transport_latency = ent.qos.max_transport_latency;

      SetMetadataToAse(ase, metadata_context_types, ccid_lists);
    }

    LOG_DEBUG(
        "device=%s, activated ASE id=%d, direction=%s, max_sdu_size=%d, "
        "cis_id=%d, target_latency=%d",
        ADDRESS_TO_LOGGABLE_CSTR(address_), ase->id,
        (ent.direction == 1 ? "snk" : "src"), ase->max_sdu_size, ase->cis_id,
        ent.qos.target_latency);

    /* Try to use the already active ASE */
    ase = GetNextActiveAseWithSameDirection(ase);
    if (ase == nullptr) {
      ase = GetFirstInactiveAse(ent.direction, reuse_cis_id);
    }
  }

  *number_of_already_active_group_ase = active_ases;
  return true;
}

/* LeAudioDevice Class methods implementation */
void LeAudioDevice::SetConnectionState(DeviceConnectState state) {
  LOG_DEBUG("%s, %s --> %s", ADDRESS_TO_LOGGABLE_CSTR(address_),
            bluetooth::common::ToString(connection_state_).c_str(),
            bluetooth::common::ToString(state).c_str());
  LeAudioLogHistory::Get()->AddLogHistory(
      kLogConnectionTag, group_id_, address_,
      bluetooth::common::ToString(connection_state_) + " -> ",
      "->" + bluetooth::common::ToString(state));
  connection_state_ = state;
}

DeviceConnectState LeAudioDevice::GetConnectionState(void) {
  return connection_state_;
}

void LeAudioDevice::ClearPACs(void) {
  snk_pacs_.clear();
  src_pacs_.clear();
}

LeAudioDevice::~LeAudioDevice(void) {
  alarm_free(link_quality_timer);
  for (auto& ase : ases_) {
    alarm_free(ase.autonomous_operation_timer_);
  }
  this->ClearPACs();
}

void LeAudioDevice::RegisterPACs(
    std::vector<struct types::acs_ac_record>* pac_db,
    std::vector<struct types::acs_ac_record>* pac_recs) {
  /* Clear PAC database for characteristic in case if re-read, indicated */
  if (!pac_db->empty()) {
    DLOG(INFO) << __func__ << ", upgrade PACs for characteristic";
    pac_db->clear();
  }

  dsa_.modes = {DsaMode::DISABLED};

  /* TODO wrap this logging part with debug flag */
  for (const struct types::acs_ac_record& pac : *pac_recs) {
    LOG(INFO) << "Registering PAC"
              << "\n\tCoding format: " << loghex(pac.codec_id.coding_format)
              << "\n\tVendor codec company ID: "
              << loghex(pac.codec_id.vendor_company_id)
              << "\n\tVendor codec ID: " << loghex(pac.codec_id.vendor_codec_id)
              << "\n\tCodec spec caps:\n"
              << pac.codec_spec_caps.ToString("",
                                              types::CodecCapabilitiesLtvFormat)
              << "\n\tMetadata: "
              << base::HexEncode(pac.metadata.data(), pac.metadata.size());

    if (IS_FLAG_ENABLED(leaudio_dynamic_spatial_audio)) {
      if (pac.codec_id == types::kLeAudioCodecHeadtracking) {
        LOG(INFO) << __func__ << ": Headtracking supported";
        /* Todo: Set DSA modes according to the codec configuration */
        dsa_.modes = {
            DsaMode::DISABLED,
            DsaMode::ISO_SW,
            DsaMode::ISO_HW,
        };
        /* Todo: Remove the headtracking codec from the list */
      }
    }
  }

  pac_db->insert(pac_db->begin(), pac_recs->begin(), pac_recs->end());
}

struct ase* LeAudioDevice::GetAseByValHandle(uint16_t val_hdl) {
  auto iter = std::find_if(
      ases_.begin(), ases_.end(),
      [&val_hdl](const auto& ase) { return ase.hdls.val_hdl == val_hdl; });

  return (iter == ases_.end()) ? nullptr : &(*iter);
}

int LeAudioDevice::GetAseCount(uint8_t direction) {
  return std::count_if(ases_.begin(), ases_.end(), [direction](const auto& a) {
    return a.direction == direction;
  });
}

struct ase* LeAudioDevice::GetFirstAseWithState(uint8_t direction,
                                                AseState state) {
  auto iter = std::find_if(
      ases_.begin(), ases_.end(), [direction, state](const auto& ase) {
        return ((ase.direction == direction) && (ase.state == state));
      });

  return (iter == ases_.end()) ? nullptr : &(*iter);
}

struct ase* LeAudioDevice::GetFirstActiveAse(void) {
  auto iter = std::find_if(ases_.begin(), ases_.end(),
                           [](const auto& ase) { return ase.active; });

  return (iter == ases_.end()) ? nullptr : &(*iter);
}

struct ase* LeAudioDevice::GetFirstActiveAseByDirection(uint8_t direction) {
  auto iter =
      std::find_if(ases_.begin(), ases_.end(), [direction](const auto& ase) {
        return (ase.active && (ase.direction == direction));
      });

  return (iter == ases_.end()) ? nullptr : &(*iter);
}

struct ase* LeAudioDevice::GetNextActiveAseWithSameDirection(
    struct ase* base_ase) {
  auto iter = std::find_if(ases_.begin(), ases_.end(),
                           [&base_ase](auto& ase) { return base_ase == &ase; });

  /* Invalid ase given */
  if (iter == ases_.end() || std::distance(iter, ases_.end()) < 1)
    return nullptr;

  iter =
      std::find_if(std::next(iter, 1), ases_.end(), [&iter](const auto& ase) {
        return ase.active && (*iter).direction == ase.direction;
      });

  return (iter == ases_.end()) ? nullptr : &(*iter);
}

struct ase* LeAudioDevice::GetNextActiveAseWithDifferentDirection(
    struct ase* base_ase) {
  auto iter = std::find_if(ases_.begin(), ases_.end(),
                           [&base_ase](auto& ase) { return base_ase == &ase; });

  /* Invalid ase given */
  if (std::distance(iter, ases_.end()) < 1) {
    LOG_DEBUG("ASE %d does not use bidirectional CIS", base_ase->id);
    return nullptr;
  }

  iter =
      std::find_if(std::next(iter, 1), ases_.end(), [&iter](const auto& ase) {
        return ase.active && iter->direction != ase.direction;
      });

  if (iter == ases_.end()) {
    return nullptr;
  }

  return &(*iter);
}

struct ase* LeAudioDevice::GetFirstActiveAseByCisAndDataPathState(
    CisState cis_state, DataPathState data_path_state) {
  auto iter = std::find_if(ases_.begin(), ases_.end(),
                           [cis_state, data_path_state](const auto& ase) {
                             return (ase.active &&
                                     (ase.data_path_state == data_path_state) &&
                                     (ase.cis_state == cis_state));
                           });

  return (iter == ases_.end()) ? nullptr : &(*iter);
}

struct ase* LeAudioDevice::GetFirstInactiveAse(uint8_t direction,
                                               bool reuse_cis_id) {
  auto iter = std::find_if(ases_.begin(), ases_.end(),
                           [direction, reuse_cis_id](const auto& ase) {
                             if (ase.active || (ase.direction != direction))
                               return false;

                             if (!reuse_cis_id) return true;

                             return (ase.cis_id != kInvalidCisId);
                           });
  /* If ASE is found, return it */
  if (iter != ases_.end()) return &(*iter);

  /* If reuse was not set, that means there is no inactive ASE available. */
  if (!reuse_cis_id) return nullptr;

  /* Since there is no ASE with assigned CIS ID, it means new configuration
   * needs more ASEs then it was configured before.
   * Let's find just inactive one */
  iter = std::find_if(ases_.begin(), ases_.end(),
                      [direction](const auto& ase) {
                        if (ase.active || (ase.direction != direction))
                          return false;
                        return true;
                      });

  return (iter == ases_.end()) ? nullptr : &(*iter);
}

struct ase* LeAudioDevice::GetNextActiveAse(struct ase* base_ase) {
  auto iter = std::find_if(ases_.begin(), ases_.end(),
                           [&base_ase](auto& ase) { return base_ase == &ase; });

  /* Invalid ase given */
  if (iter == ases_.end() || std::distance(iter, ases_.end()) < 1)
    return nullptr;

  iter = std::find_if(std::next(iter, 1), ases_.end(),
                      [](const auto& ase) { return ase.active; });

  return (iter == ases_.end()) ? nullptr : &(*iter);
}

struct ase* LeAudioDevice::GetAseToMatchBidirectionCis(struct ase* base_ase) {
  auto iter = std::find_if(ases_.begin(), ases_.end(), [&base_ase](auto& ase) {
    return (base_ase->cis_conn_hdl == ase.cis_conn_hdl) &&
           (base_ase->direction != ase.direction);
  });
  return (iter == ases_.end()) ? nullptr : &(*iter);
}

BidirectionalPair<struct ase*> LeAudioDevice::GetAsesByCisConnHdl(
    uint16_t conn_hdl) {
  BidirectionalPair<struct ase*> ases = {nullptr, nullptr};

  for (auto& ase : ases_) {
    if (ase.cis_conn_hdl == conn_hdl) {
      if (ase.direction == types::kLeAudioDirectionSink) {
        ases.sink = &ase;
      } else {
        ases.source = &ase;
      }
    }
  }

  return ases;
}

BidirectionalPair<struct ase*> LeAudioDevice::GetAsesByCisId(uint8_t cis_id) {
  BidirectionalPair<struct ase*> ases = {nullptr, nullptr};

  for (auto& ase : ases_) {
    if (ase.cis_id == cis_id) {
      if (ase.direction == types::kLeAudioDirectionSink) {
        ases.sink = &ase;
      } else {
        ases.source = &ase;
      }
    }
  }

  return ases;
}

bool LeAudioDevice::HaveActiveAse(void) {
  auto iter = std::find_if(ases_.begin(), ases_.end(),
                           [](const auto& ase) { return ase.active; });

  return iter != ases_.end();
}

bool LeAudioDevice::HaveAnyUnconfiguredAses(void) {
  /* In configuring state when active in Idle or Configured and reconfigure */
  auto iter = std::find_if(ases_.begin(), ases_.end(), [](const auto& ase) {
    if (!ase.active) return false;

    if (ase.state == AseState::BTA_LE_AUDIO_ASE_STATE_IDLE ||
        ((ase.state == AseState::BTA_LE_AUDIO_ASE_STATE_CODEC_CONFIGURED) &&
         ase.reconfigure))
      return true;

    return false;
  });

  return iter != ases_.end();
}

bool LeAudioDevice::HaveAllActiveAsesSameState(AseState state) {
  auto iter =
      std::find_if(ases_.begin(), ases_.end(), [&state](const auto& ase) {
        LOG_VERBOSE("ASE id: %d, active: %d, state: %s", ase.id, ase.active,
                    bluetooth::common::ToString(ase.state).c_str());
        return ase.active && (ase.state != state);
      });

  return iter == ases_.end();
}

bool LeAudioDevice::HaveAllActiveAsesSameDataPathState(
    types::DataPathState state) const {
  auto iter =
      std::find_if(ases_.begin(), ases_.end(), [&state](const auto& ase) {
        LOG_VERBOSE("ASE id: %d, active: %d, state: %s", ase.id, ase.active,
                    bluetooth::common::ToString(ase.data_path_state).c_str());
        return ase.active && (ase.data_path_state != state);
      });

  return iter == ases_.end();
}

bool LeAudioDevice::IsReadyToCreateStream(void) {
  auto iter = std::find_if(ases_.begin(), ases_.end(), [](const auto& ase) {
    if (!ase.active) return false;

    LOG_VERBOSE("ASE id: %d, state: %s, direction: %d", ase.id,
                bluetooth::common::ToString(ase.state).c_str(), ase.direction);
    if (ase.direction == types::kLeAudioDirectionSink &&
        (ase.state != AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING &&
         ase.state != AseState::BTA_LE_AUDIO_ASE_STATE_ENABLING))
      return true;

    if (ase.direction == types::kLeAudioDirectionSource &&
        ase.state != AseState::BTA_LE_AUDIO_ASE_STATE_ENABLING)
      return true;

    return false;
  });

  return iter == ases_.end();
}

bool LeAudioDevice::IsReadyToSuspendStream(void) {
  auto iter = std::find_if(ases_.begin(), ases_.end(), [](const auto& ase) {
    if (!ase.active) return false;

    if (ase.direction == types::kLeAudioDirectionSink &&
        ase.state != AseState::BTA_LE_AUDIO_ASE_STATE_QOS_CONFIGURED)
      return true;

    if (ase.direction == types::kLeAudioDirectionSource &&
        ase.state != AseState::BTA_LE_AUDIO_ASE_STATE_DISABLING)
      return true;

    return false;
  });

  return iter == ases_.end();
}

bool LeAudioDevice::HaveAllActiveAsesCisEst(void) const {
  if (ases_.empty()) {
    LOG_WARN("No ases for device %s", ADDRESS_TO_LOGGABLE_CSTR(address_));
    /* If there is no ASEs at all, it means we are good here - meaning, it is
     * not waiting for any CIS to be established.
     */
    return true;
  }

  bool has_active_ase = false;
  auto iter = std::find_if(ases_.begin(), ases_.end(), [&](const auto& ase) {
    if (!has_active_ase && ase.active) {
      has_active_ase = true;
    }
    LOG_VERBOSE("ASE id: %d, cis_state: %s, direction: %d", ase.id,
                bluetooth::common::ToString(ase.cis_state).c_str(),
                ase.direction);

    return ase.active && (ase.cis_state != CisState::CONNECTED);
  });

  return iter == ases_.end() && has_active_ase;
}

bool LeAudioDevice::HaveAnyCisConnected(void) {
  /* Pending and Disconnecting is considered as connected in this function */
  for (auto const ase : ases_) {
    if (ase.cis_state == CisState::CONNECTED ||
        ase.cis_state == CisState::CONNECTING ||
        ase.cis_state == CisState::DISCONNECTING) {
      return true;
    }
  }
  return false;
}

uint8_t LeAudioDevice::GetSupportedAudioChannelCounts(uint8_t direction) const {
  auto& pacs =
      direction == types::kLeAudioDirectionSink ? snk_pacs_ : src_pacs_;

  if (pacs.size() == 0) {
    LOG(ERROR) << __func__ << " missing PAC for direction " << +direction;
    return 0;
  }

  for (const auto& pac_tuple : pacs) {
    /* Get PAC records from tuple as second element from tuple */
    auto& pac_recs = std::get<1>(pac_tuple);

    for (const auto pac : pac_recs) {
      if (pac.codec_id.coding_format != types::kLeAudioCodingFormatLC3)
        continue;

      auto supported_channel_count_ltv = pac.codec_spec_caps.Find(
          codec_spec_caps::kLeAudioLtvTypeSupportedAudioChannelCounts);

      if (supported_channel_count_ltv == std::nullopt ||
          supported_channel_count_ltv->size() == 0L) {
        return 1;
      }

      return VEC_UINT8_TO_UINT8(supported_channel_count_ltv.value());
    };
  }

  return 0;
}

const struct types::acs_ac_record*
LeAudioDevice::GetCodecConfigurationSupportedPac(
    uint8_t direction, const CodecConfigSetting& codec_capability_setting) {
  auto& pacs =
      direction == types::kLeAudioDirectionSink ? snk_pacs_ : src_pacs_;

  if (pacs.size() == 0) {
    LOG_ERROR("missing PAC for direction %d", direction);
    return nullptr;
  }

  /* TODO: Validate channel locations */

  for (const auto& pac_tuple : pacs) {
    /* Get PAC records from tuple as second element from tuple */
    auto& pac_recs = std::get<1>(pac_tuple);

    for (const auto& pac : pac_recs) {
      if (!IsCodecConfigSettingSupported(pac, codec_capability_setting))
        continue;

      return &pac;
    };
  }

  /* Doesn't match required configuration with any PAC */
  return nullptr;
}

/**
 * Returns supported PHY's bitfield
 */
uint8_t LeAudioDevice::GetPhyBitmask(void) {
  uint8_t phy_bitfield = kIsoCigPhy1M;

  if (BTM_IsPhy2mSupported(address_, BT_TRANSPORT_LE))
    phy_bitfield |= kIsoCigPhy2M;

  return phy_bitfield;
}

void LeAudioDevice::PrintDebugState(void) {
  std::stringstream debug_str;

  debug_str << " address: " << address_ << ", "
            << bluetooth::common::ToString(connection_state_)
            << ", conn_id: " << +conn_id_ << ", mtu: " << +mtu_
            << ", num_of_ase: " << static_cast<int>(ases_.size());

  if (ases_.size() > 0) {
    debug_str << "\n  == ASEs == ";
    for (auto& ase : ases_) {
      debug_str << "\n  id: " << +ase.id << ", active: " << ase.active
                << ", dir: "
                << (ase.direction == types::kLeAudioDirectionSink ? "sink"
                                                                  : "source")
                << ", cis_id: " << +ase.cis_id
                << ", cis_handle: " << +ase.cis_conn_hdl
                << ", state: " << bluetooth::common::ToString(ase.cis_state)
                << ", data_path_state: "
                << bluetooth::common::ToString(ase.data_path_state)
                << "\n ase max_latency: " << +ase.max_transport_latency
                << ", rtn: " << +ase.retrans_nb
                << ", max_sdu: " << +ase.max_sdu_size
                << ", target latency: " << +ase.target_latency;
    }
  }

  LOG_INFO("%s", debug_str.str().c_str());
}

void LeAudioDevice::DumpPacsDebugState(std::stringstream& stream,
                                       types::PublishedAudioCapabilities pacs) {
  if (pacs.size() > 0) {
    for (auto& pac : pacs) {
      stream << "\n\t\tvalue handle: " << loghex(std::get<0>(pac).val_hdl)
             << " / CCC handle: " << loghex(std::get<0>(pac).ccc_hdl);

      for (auto& record : std::get<1>(pac)) {
        stream << "\n\n\t\tCodecId(Coding format: "
               << static_cast<int>(record.codec_id.coding_format)
               << ", Vendor company ID: "
               << static_cast<int>(record.codec_id.vendor_company_id)
               << ", Vendor codec ID: "
               << static_cast<int>(record.codec_id.vendor_codec_id) << ")";
        stream << "\n\t\tCodec specific capabilities:\n"
               << record.codec_spec_caps.ToString(
                      "\t\t\t", types::CodecCapabilitiesLtvFormat);
        stream << "\t\tMetadata: "
               << base::HexEncode(record.metadata.data(),
                                  record.metadata.size());
      }
    }
  }
}

void LeAudioDevice::DumpPacsDebugState(std::stringstream& stream) {
  stream << "\n\tSink PACs";
  DumpPacsDebugState(stream, snk_pacs_);
  stream << "\n\tSource PACs";
  DumpPacsDebugState(stream, src_pacs_);
}

void LeAudioDevice::Dump(int fd) {
  uint16_t acl_handle = BTM_GetHCIConnHandle(address_, BT_TRANSPORT_LE);
  std::string location = "unknown location";

  if (snk_audio_locations_.to_ulong() &
          codec_spec_conf::kLeAudioLocationAnyLeft &&
      snk_audio_locations_.to_ulong() &
          codec_spec_conf::kLeAudioLocationAnyRight) {
    std::string location_left_right = "left/right";
    location.swap(location_left_right);
  } else if (snk_audio_locations_.to_ulong() &
             codec_spec_conf::kLeAudioLocationAnyLeft) {
    std::string location_left = "left";
    location.swap(location_left);
  } else if (snk_audio_locations_.to_ulong() &
             codec_spec_conf::kLeAudioLocationAnyRight) {
    std::string location_right = "right";
    location.swap(location_right);
  }

  std::stringstream stream;
  stream << "\n\taddress: " << ADDRESS_TO_LOGGABLE_STR(address_) << ": "
         << connection_state_ << ": "
         << (conn_id_ == GATT_INVALID_CONN_ID ? "" : std::to_string(conn_id_))
         << ", acl_handle: " << std::to_string(acl_handle) << ", " << location
         << ",\t" << (encrypted_ ? "Encrypted" : "Unecrypted")
         << ",mtu: " << std::to_string(mtu_)
         << "\n\tnumber of ases_: " << static_cast<int>(ases_.size());

  if (ases_.size() > 0) {
    stream << "\n\t== ASEs == \n\t";
    stream << "id  active dir     cis_id  cis_handle  sdu  latency rtn  "
              "cis_state data_path_state";
    for (auto& ase : ases_) {
      stream << std::setfill('\x20') << "\n\t" << std::left << std::setw(4)
             << static_cast<int>(ase.id) << std::left << std::setw(7)
             << (ase.active ? "true" : "false") << std::left << std::setw(8)
             << (ase.direction == types::kLeAudioDirectionSink ? "sink"
                                                               : "source")
             << std::left << std::setw(8) << static_cast<int>(ase.cis_id)
             << std::left << std::setw(12) << ase.cis_conn_hdl << std::left
             << std::setw(5) << ase.max_sdu_size << std::left << std::setw(8)
             << ase.max_transport_latency << std::left << std::setw(5)
             << static_cast<int>(ase.retrans_nb) << std::left << std::setw(10)
             << bluetooth::common::ToString(ase.cis_state) << std::setw(12)
             << bluetooth::common::ToString(ase.data_path_state);
    }
  }

  stream << "\n\t====";

  dprintf(fd, "%s", stream.str().c_str());
}

void LeAudioDevice::DisconnectAcl(void) {
  if (conn_id_ == GATT_INVALID_CONN_ID) return;

  uint16_t acl_handle =
      BTM_GetHCIConnHandle(address_, BT_TRANSPORT_LE);
  if (acl_handle != HCI_INVALID_HANDLE) {
    acl_disconnect_from_handle(acl_handle, HCI_ERR_PEER_USER,
                               "bta::le_audio::client disconnect");
  }
}

void LeAudioDevice::SetAvailableContexts(
    BidirectionalPair<AudioContexts> contexts) {
  LOG_DEBUG(
      "\n\t previous_contexts_.sink: %s \n\t previous_contexts_.source: %s  "
      "\n\t "
      "new_contexts.sink: %s \n\t new_contexts.source: %s \n\t ",
      avail_contexts_.sink.to_string().c_str(),
      avail_contexts_.source.to_string().c_str(),
      contexts.sink.to_string().c_str(), contexts.source.to_string().c_str());

  avail_contexts_.sink = contexts.sink;
  avail_contexts_.source = contexts.source;
}

void LeAudioDevice::SetMetadataToAse(
    struct types::ase* ase,
    const BidirectionalPair<AudioContexts>& metadata_context_types,
    BidirectionalPair<std::vector<uint8_t>> ccid_lists) {
  /* Filter multidirectional audio context for each ase direction */
  auto directional_audio_context = metadata_context_types.get(ase->direction) &
                                   GetAvailableContexts(ase->direction);
  if (directional_audio_context.any()) {
    ase->metadata =
        GetMetadata(directional_audio_context, ccid_lists.get(ase->direction));
  } else {
    ase->metadata = GetMetadata(AudioContexts(LeAudioContextType::UNSPECIFIED),
                                std::vector<uint8_t>());
  }
}

bool LeAudioDevice::ActivateConfiguredAses(
    LeAudioContextType context_type,
    const BidirectionalPair<AudioContexts>& metadata_context_types,
    BidirectionalPair<std::vector<uint8_t>> ccid_lists) {
  if (conn_id_ == GATT_INVALID_CONN_ID) {
    LOG_WARN(" Device %s is not connected ",
             ADDRESS_TO_LOGGABLE_CSTR(address_));
    return false;
  }

  bool ret = false;

  LOG_INFO(" Configuring device %s", ADDRESS_TO_LOGGABLE_CSTR(address_));
  for (auto& ase : ases_) {
    if (ase.state == AseState::BTA_LE_AUDIO_ASE_STATE_CODEC_CONFIGURED &&
        ase.configured_for_context_type == context_type) {
      LOG_INFO(
          " conn_id: %d, ase id %d, cis id %d, cis_handle 0x%04x is activated.",
          conn_id_, ase.id, ase.cis_id, ase.cis_conn_hdl);
      ase.active = true;
      ret = true;
      /* update metadata */
      SetMetadataToAse(&ase, metadata_context_types, ccid_lists);
    }
  }

  return ret;
}

void LeAudioDevice::DeactivateAllAses(void) {
  for (auto& ase : ases_) {
    if (ase.active == false && ase.cis_state != CisState::IDLE &&
        ase.data_path_state != DataPathState::IDLE) {
      LOG_WARN(
          " %s, ase_id: %d, ase.cis_id: %d, cis_handle: 0x%02x, "
          "ase.cis_state=%s, ase.data_path_state=%s",
          ADDRESS_TO_LOGGABLE_CSTR(address_), ase.id, ase.cis_id,
          ase.cis_conn_hdl, bluetooth::common::ToString(ase.cis_state).c_str(),
          bluetooth::common::ToString(ase.data_path_state).c_str());
    }
    if (alarm_is_scheduled(ase.autonomous_operation_timer_)) {
      alarm_free(ase.autonomous_operation_timer_);
      ase.autonomous_operation_timer_ = NULL;
      ase.autonomous_target_state_ = AseState::BTA_LE_AUDIO_ASE_STATE_IDLE;
    }
    ase.state = AseState::BTA_LE_AUDIO_ASE_STATE_IDLE;
    ase.cis_state = CisState::IDLE;
    ase.data_path_state = DataPathState::IDLE;
    ase.active = false;
    ase.cis_id = le_audio::kInvalidCisId;
    ase.cis_conn_hdl = 0;
  }
}

std::vector<uint8_t> LeAudioDevice::GetMetadata(
    AudioContexts context_type, const std::vector<uint8_t>& ccid_list) {
  std::vector<uint8_t> metadata;

  AppendMetadataLtvEntryForStreamingContext(metadata, context_type);
  AppendMetadataLtvEntryForCcidList(metadata, ccid_list);

  return std::move(metadata);
}

bool LeAudioDevice::IsMetadataChanged(
    const BidirectionalPair<AudioContexts>& context_types,
    const BidirectionalPair<std::vector<uint8_t>>& ccid_lists) {
  for (auto* ase = this->GetFirstActiveAse(); ase;
       ase = this->GetNextActiveAse(ase)) {
    if (this->GetMetadata(context_types.get(ase->direction),
                          ccid_lists.get(ase->direction)) != ase->metadata)
      return true;
  }

  return false;
}

void LeAudioDevice::GetDeviceModelName(void) {
  bt_property_t prop_name;
  bt_bdname_t prop_value = {0};
  // Retrieve model name from storage
  BTIF_STORAGE_FILL_PROPERTY(&prop_name, BT_PROPERTY_REMOTE_MODEL_NUM,
                             sizeof(bt_bdname_t), &prop_value);
  if (btif_storage_get_remote_device_property(&address_, &prop_name) ==
      BT_STATUS_SUCCESS) {
    model_name_.assign((char*)prop_value.name);
  }
}

void LeAudioDevice::UpdateDeviceAllowlistFlag(void) {
  char allow_list[PROPERTY_VALUE_MAX] = {0};
  GetDeviceModelName();
  osi_property_get(kLeAudioDeviceAllowListProp, allow_list, "");
  if (allow_list[0] == '\0' || model_name_ == "") {
    // if device allow list is empty or no remote model name available
    // return allowlist_flag_ as default false
    return;
  }

  std::istringstream stream(allow_list);
  std::string token;
  while (std::getline(stream, token, ',')) {
    if (token.compare(model_name_) == 0) {
      allowlist_flag_ = true;
      return;
    }
  }
}

DsaModes LeAudioDevice::GetDsaModes(void) { return dsa_.modes; }

types::DataPathState LeAudioDevice::GetDsaDataPathState(void) {
  return dsa_.state;
}

void LeAudioDevice::SetDsaDataPathState(types::DataPathState state) {
  dsa_.state = state;
}

uint16_t LeAudioDevice::GetDsaCisHandle(void) { return dsa_.cis_handle; }

void LeAudioDevice::SetDsaCisHandle(uint16_t cis_handle) {
  dsa_.cis_handle = cis_handle;
}

/* LeAudioDevices Class methods implementation */
void LeAudioDevices::Add(const RawAddress& address, DeviceConnectState state,
                         int group_id) {
  auto device = FindByAddress(address);
  if (device != nullptr) {
    LOG(ERROR) << __func__ << ", address: " << ADDRESS_TO_LOGGABLE_STR(address)
               << " is already assigned to group: " << device->group_id_;
    return;
  }

  leAudioDevices_.emplace_back(
      std::make_shared<LeAudioDevice>(address, state, group_id));
}

void LeAudioDevices::Remove(const RawAddress& address) {
  auto iter = std::find_if(leAudioDevices_.begin(), leAudioDevices_.end(),
                           [&address](auto const& leAudioDevice) {
                             return leAudioDevice->address_ == address;
                           });

  if (iter == leAudioDevices_.end()) {
    LOG(ERROR) << __func__ << ", no such address: "
               << ADDRESS_TO_LOGGABLE_STR(address);
    return;
  }

  leAudioDevices_.erase(iter);
}

LeAudioDevice* LeAudioDevices::FindByAddress(const RawAddress& address) const {
  auto iter = std::find_if(leAudioDevices_.begin(), leAudioDevices_.end(),
                           [&address](auto const& leAudioDevice) {
                             return leAudioDevice->address_ == address;
                           });

  return (iter == leAudioDevices_.end()) ? nullptr : iter->get();
}

std::shared_ptr<LeAudioDevice> LeAudioDevices::GetByAddress(
    const RawAddress& address) const {
  auto iter = std::find_if(leAudioDevices_.begin(), leAudioDevices_.end(),
                           [&address](auto const& leAudioDevice) {
                             return leAudioDevice->address_ == address;
                           });

  return (iter == leAudioDevices_.end()) ? nullptr : *iter;
}

LeAudioDevice* LeAudioDevices::FindByConnId(uint16_t conn_id) const {
  auto iter = std::find_if(leAudioDevices_.begin(), leAudioDevices_.end(),
                           [&conn_id](auto const& leAudioDevice) {
                             return leAudioDevice->conn_id_ == conn_id;
                           });

  return (iter == leAudioDevices_.end()) ? nullptr : iter->get();
}

LeAudioDevice* LeAudioDevices::FindByCisConnHdl(uint8_t cig_id,
                                                uint16_t conn_hdl) const {
  auto iter = std::find_if(leAudioDevices_.begin(), leAudioDevices_.end(),
                           [&conn_hdl, &cig_id](auto& d) {
                             LeAudioDevice* dev;
                             BidirectionalPair<struct ase*> ases;

                             dev = d.get();
                             if (dev->group_id_ != cig_id) {
                               return false;
                             }

                             ases = dev->GetAsesByCisConnHdl(conn_hdl);
                             if (ases.sink || ases.source)
                               return true;
                             else
                               return false;
                           });

  if (iter == leAudioDevices_.end()) return nullptr;

  return iter->get();
}

void LeAudioDevices::SetInitialGroupAutoconnectState(
    int group_id, int gatt_if, tBTM_BLE_CONN_TYPE reconnection_mode,
    bool current_dev_autoconnect_flag) {
  if (!current_dev_autoconnect_flag) {
    /* If current device autoconnect flag is false, check if there is other
     * device in the group which is in autoconnect mode.
     * If yes, assume whole group is in autoconnect.
     */
    auto iter = std::find_if(leAudioDevices_.begin(), leAudioDevices_.end(),
                             [&group_id](auto& d) {
                               LeAudioDevice* dev;
                               dev = d.get();
                               if (dev->group_id_ != group_id) {
                                 return false;
                               }
                               return dev->autoconnect_flag_;
                             });

    current_dev_autoconnect_flag = !(iter == leAudioDevices_.end());
  }

  if (!current_dev_autoconnect_flag) {
    return;
  }

  /* This function is called when bluetooth started, therefore here we will
   * try direct connection, if that failes, we fallback to background connection
   */
  for (auto dev : leAudioDevices_) {
    if ((dev->group_id_ == group_id) &&
        (dev->GetConnectionState() == DeviceConnectState::DISCONNECTED)) {
      dev->SetConnectionState(DeviceConnectState::CONNECTING_AUTOCONNECT);
      dev->autoconnect_flag_ = true;
      btif_storage_set_leaudio_autoconnect(dev->address_, true);
      BTA_GATTC_Open(gatt_if, dev->address_, BTM_BLE_DIRECT_CONNECTION, false);
    }
  }
}

size_t LeAudioDevices::Size() const { return (leAudioDevices_.size()); }

void LeAudioDevices::Dump(int fd, int group_id) const {
  std::stringstream stream, stream_pacs;

  for (auto const& device : leAudioDevices_) {
    if (device->group_id_ == group_id) {
      device->Dump(fd);

      stream_pacs << "\n\taddress: " << device->address_;
      device->DumpPacsDebugState(stream_pacs);
      dprintf(fd, "%s", stream_pacs.str().c_str());
    }
  }
}

void LeAudioDevices::Cleanup(tGATT_IF client_if) {
  for (auto const& device : leAudioDevices_) {
    auto connection_state = device->GetConnectionState();
    if (connection_state == DeviceConnectState::DISCONNECTED ||
        connection_state == DeviceConnectState::DISCONNECTING) {
      continue;
    }

    if (connection_state == DeviceConnectState::CONNECTING_AUTOCONNECT) {
      BTA_GATTC_CancelOpen(client_if, device->address_, false);
    } else {
      BtaGattQueue::Clean(device->conn_id_);
      BTA_GATTC_Close(device->conn_id_);
      device->DisconnectAcl();
    }
  }
  leAudioDevices_.clear();
}

}  // namespace le_audio
