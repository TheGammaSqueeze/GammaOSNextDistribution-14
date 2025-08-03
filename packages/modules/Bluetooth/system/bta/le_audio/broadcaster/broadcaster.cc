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

#include <base/functional/bind.h>
#include <base/logging.h>
#include <lc3.h>

#include <mutex>

#include "bta/include/bta_le_audio_broadcaster_api.h"
#include "bta/le_audio/broadcaster/state_machine.h"
#include "bta/le_audio/codec_interface.h"
#include "bta/le_audio/content_control_id_keeper.h"
#include "bta/le_audio/le_audio_types.h"
#include "bta/le_audio/le_audio_utils.h"
#include "bta/le_audio/metrics_collector.h"
#include "common/strings.h"
#include "device/include/controller.h"
#include "include/check.h"
#include "internal_include/stack_config.h"
#include "os/log.h"
#include "osi/include/properties.h"
#include "stack/include/bt_types.h"
#include "stack/include/btm_api_types.h"
#include "stack/include/btm_iso_api.h"

using bluetooth::common::ToString;
using bluetooth::hci::IsoManager;
using bluetooth::hci::iso_manager::big_create_cmpl_evt;
using bluetooth::hci::iso_manager::big_terminate_cmpl_evt;
using bluetooth::hci::iso_manager::BigCallbacks;
using bluetooth::le_audio::BasicAudioAnnouncementData;
using bluetooth::le_audio::BasicAudioAnnouncementSubgroup;
using bluetooth::le_audio::BroadcastId;
using bluetooth::le_audio::PublicBroadcastAnnouncementData;
using le_audio::CodecManager;
using le_audio::ContentControlIdKeeper;
using le_audio::DsaMode;
using le_audio::LeAudioCodecConfiguration;
using le_audio::LeAudioSourceAudioHalClient;
using le_audio::broadcaster::BigConfig;
using le_audio::broadcaster::BroadcastCodecWrapper;
using le_audio::broadcaster::BroadcastQosConfig;
using le_audio::broadcaster::BroadcastStateMachine;
using le_audio::broadcaster::BroadcastStateMachineConfig;
using le_audio::broadcaster::IBroadcastStateMachineCallbacks;
using le_audio::types::AudioContexts;
using le_audio::types::CodecLocation;
using le_audio::types::kLeAudioCodingFormatLC3;
using le_audio::types::LeAudioContextType;
using le_audio::types::LeAudioLtvMap;
using le_audio::utils::GetAudioContextsFromSourceMetadata;

namespace {
class LeAudioBroadcasterImpl;
LeAudioBroadcasterImpl* instance;
std::mutex instance_mutex;

/* Class definitions */

/* LeAudioBroadcasterImpl class represents main implementation class for le
 * audio broadcaster feature in the stack.
 *
 * This class may be bonded with Test socket which allows to drive an instance
 * for test purposes.
 */
class LeAudioBroadcasterImpl : public LeAudioBroadcaster, public BigCallbacks {
  enum class AudioDataPathState {
    INACTIVE,
    ACTIVE,
    SUSPENDED,
  };

 public:
  LeAudioBroadcasterImpl(
      bluetooth::le_audio::LeAudioBroadcasterCallbacks* callbacks_)
      : callbacks_(callbacks_),
        current_phy_(PHY_LE_2M),
        audio_data_path_state_(AudioDataPathState::INACTIVE),
        le_audio_source_hal_client_(nullptr) {
    LOG_INFO();

    /* Register State machine callbacks */
    BroadcastStateMachine::Initialize(&state_machine_callbacks_,
                                      &state_machine_adv_callbacks_);

    GenerateBroadcastIds();
  }

  ~LeAudioBroadcasterImpl() override = default;

  void GenerateBroadcastIds(void) {
    btsnd_hcic_ble_rand(base::Bind([](BT_OCTET8 rand) {
      if (!instance) return;

      /* LE Rand returns 8 octets. Lets' make 2 outstanding Broadcast Ids out
       * of it */
      for (int i = 0; i < 8; i += 4) {
        BroadcastId broadcast_id = 0;
        /* Broadcast ID should be 3 octets long (BAP v1.0 spec.) */
        STREAM_TO_UINT24(broadcast_id, rand);
        if (broadcast_id == bluetooth::le_audio::kBroadcastIdInvalid) continue;
        instance->available_broadcast_ids_.emplace_back(broadcast_id);
      }

      if (instance->available_broadcast_ids_.empty()) {
        LOG_ALWAYS_FATAL("Unable to generate proper broadcast identifiers.");
      }
    }));
  }

  void CleanUp() {
    LOG_INFO("Broadcaster");
    broadcasts_.clear();
    callbacks_ = nullptr;
    is_iso_running_ = false;
    queued_start_broadcast_request_ = std::nullopt;
    queued_create_broadcast_request_ = std::nullopt;

    if (le_audio_source_hal_client_) {
      le_audio_source_hal_client_->Stop();
      le_audio_source_hal_client_.reset();
    }
  }

  void Stop() {
    LOG_INFO("Broadcaster");

    for (auto& sm_pair : broadcasts_) {
      StopAudioBroadcast(sm_pair.first);
    }
  }

  static PublicBroadcastAnnouncementData preparePublicAnnouncement(
      uint8_t features, const LeAudioLtvMap& metadata) {
    PublicBroadcastAnnouncementData announcement;

    /* Prepare the announcement */
    announcement.features = features;
    announcement.metadata = metadata.Values();
    return announcement;
  }

  static BasicAudioAnnouncementData prepareBasicAnnouncement(
      const BroadcastCodecWrapper& codec_config,
      const std::vector<LeAudioLtvMap>& metadata_group) {
    BasicAudioAnnouncementData announcement;

    /* Prepare the announcement */
    announcement.presentation_delay_us = 40000; /* us */

    auto const& codec_id = codec_config.GetLeAudioCodecId();

    for (const LeAudioLtvMap& metadata : metadata_group) {
      /* Note: Currently we have a single audio source configured with a one
       *       set of codec/pcm parameters thus we can use a single subgroup
       *       for all the BISes. Configure common BIS codec params at the
       *       subgroup level.
       */
      BasicAudioAnnouncementSubgroup config = {
          .codec_config =
              {
                  .codec_id = codec_id.coding_format,
                  .vendor_company_id = codec_id.vendor_company_id,
                  .vendor_codec_id = codec_id.vendor_codec_id,
                  .codec_specific_params =
                      codec_config.GetSubgroupCodecSpecData().Values(),
              },
          .metadata = metadata.Values(),
          .bis_configs = {},
      };
      /* BIS indices range is [1-31] - BASS, Sec.3.2 Broadcast Receive State. */
      for (size_t i = 0; i < codec_config.GetNumChannels(); ++i) {
        config.bis_configs.push_back(
            {.codec_specific_params =
                 codec_config.GetBisCodecSpecData(i + 1).Values(),
             .bis_index = static_cast<uint8_t>(i + 1)});
      }

      announcement.subgroup_configs.push_back(config);
    }

    return announcement;
  }

  void UpdateStreamingContextTypeOnAllSubgroups(const AudioContexts& contexts) {
    LOG_DEBUG("%s context_type_map=%s", __func__, contexts.to_string().c_str());

    auto ccids = ContentControlIdKeeper::GetInstance()->GetAllCcids(contexts);
    if (ccids.empty()) {
      LOG_WARN("%s No content providers available for context_type_map=%s.",
               __func__, contexts.to_string().c_str());
    }

    std::vector<uint8_t> stream_context_vec(2);
    auto pp = stream_context_vec.data();
    UINT16_TO_STREAM(pp, contexts.value());

    for (auto const& kv_it : broadcasts_) {
      auto& broadcast = kv_it.second;
      if (broadcast->GetState() == BroadcastStateMachine::State::STREAMING) {
        auto announcement = broadcast->GetBroadcastAnnouncement();
        bool broadcast_update = false;

        // Replace context type and CCID list
        for (auto& subgroup : announcement.subgroup_configs) {
          auto subgroup_ltv = LeAudioLtvMap(subgroup.metadata);
          bool subgroup_update = false;

          auto existing_context = subgroup_ltv.Find(
              le_audio::types::kLeAudioMetadataTypeStreamingAudioContext);
          if (existing_context) {
            if (memcmp(stream_context_vec.data(), existing_context->data(),
                       existing_context->size()) != 0) {
              subgroup_ltv.Add(
                  le_audio::types::kLeAudioMetadataTypeStreamingAudioContext,
                  stream_context_vec);
              subgroup_update = true;
            }
          } else {
            subgroup_ltv.Add(
                le_audio::types::kLeAudioMetadataTypeStreamingAudioContext,
                stream_context_vec);
            subgroup_update = true;
          }

          auto existing_ccid_list =
              subgroup_ltv.Find(le_audio::types::kLeAudioMetadataTypeCcidList);
          if (existing_ccid_list) {
            if (ccids.empty()) {
              subgroup_ltv.Remove(
                  le_audio::types::kLeAudioMetadataTypeCcidList);
              subgroup_update = true;

            } else if (!std::is_permutation(ccids.begin(), ccids.end(),
                                            existing_ccid_list->begin())) {
              subgroup_ltv.Add(le_audio::types::kLeAudioMetadataTypeCcidList,
                               ccids);
              subgroup_update = true;
            }
          } else if (!ccids.empty()) {
            subgroup_ltv.Add(le_audio::types::kLeAudioMetadataTypeCcidList,
                             ccids);
            subgroup_update = true;
          }

          if (subgroup_update) {
            subgroup.metadata = subgroup_ltv.Values();
            broadcast_update = true;
          }
        }

        if (broadcast_update) {
          broadcast->UpdateBroadcastAnnouncement(std::move(announcement));
        }
      }
    }
  }

  void UpdateMetadata(
      uint32_t broadcast_id, const std::string& broadcast_name,
      const std::vector<uint8_t>& public_metadata,
      const std::vector<std::vector<uint8_t>>& subgroup_metadata) override {
    std::vector<LeAudioLtvMap> subgroup_ltvs;

    if (broadcasts_.count(broadcast_id) == 0) {
      LOG_ERROR("No such broadcast_id=%d", broadcast_id);
      return;
    }

    LOG_INFO("For broadcast_id=%d", broadcast_id);

    auto& codec_config = broadcasts_[broadcast_id]->GetCodecConfig();

    for (const std::vector<uint8_t>& metadata : subgroup_metadata) {
      /* Prepare the announcement format */
      bool is_metadata_valid;
      auto ltv = LeAudioLtvMap::Parse(metadata.data(), metadata.size(), is_metadata_valid);
      if (!is_metadata_valid) {
        LOG_ERROR("Invalid metadata provided.");
        return;
      }

      auto context_type = AudioContexts(LeAudioContextType::MEDIA);

      /* Adds multiple contexts and CCIDs regardless of the incoming audio
       * context. Android has only two CCIDs, one for Media and one for
       * Conversational context. Even though we are not broadcasting
       * Conversational streams, some PTS test cases wants multiple CCIDs.
       */
      if (stack_config_get_interface()
              ->get_pts_force_le_audio_multiple_contexts_metadata()) {
        context_type =
            LeAudioContextType::MEDIA | LeAudioContextType::CONVERSATIONAL;
        auto stream_context_vec = ltv.Find(
            le_audio::types::kLeAudioMetadataTypeStreamingAudioContext);
        if (stream_context_vec) {
          auto pp = stream_context_vec.value().data();
          if (stream_context_vec.value().size() < 2) {
            LOG_ERROR("stream_context_vec.value() size < 2");
            return;
          }
          UINT16_TO_STREAM(pp, context_type.value());
        }
      }

      auto stream_context_vec =
          ltv.Find(le_audio::types::kLeAudioMetadataTypeStreamingAudioContext);
      if (stream_context_vec) {
        auto pp = stream_context_vec.value().data();
        if (stream_context_vec.value().size() < 2) {
          LOG_ERROR("stream_context_vec.value() size < 2");
          return;
        }
        STREAM_TO_UINT16(context_type.value_ref(), pp);
      }

      // Append the CCID list
      auto ccid_vec =
          ContentControlIdKeeper::GetInstance()->GetAllCcids(context_type);
      if (!ccid_vec.empty()) {
        ltv.Add(le_audio::types::kLeAudioMetadataTypeCcidList, ccid_vec);
      }

      // Push to subgroup ltvs
      subgroup_ltvs.push_back(ltv);
    }

    if (broadcasts_[broadcast_id]->IsPublicBroadcast()) {
      // Only update broadcast name and public metadata if current broadcast is
      // public Otherwise ignore those fields
      bool is_public_metadata_valid;
      LeAudioLtvMap public_ltv =
          LeAudioLtvMap::Parse(public_metadata.data(), public_metadata.size(),
                               is_public_metadata_valid);
      if (!is_public_metadata_valid) {
        LOG_ERROR("Invalid public metadata provided.");
        return;
      }
      PublicBroadcastAnnouncementData pb_announcement =
          preparePublicAnnouncement(broadcasts_[broadcast_id]
                                        ->GetPublicBroadcastAnnouncement()
                                        .features,
                                    public_ltv);

      broadcasts_[broadcast_id]->UpdatePublicBroadcastAnnouncement(
          broadcast_id, broadcast_name, pb_announcement);
    }

    BasicAudioAnnouncementData announcement =
        prepareBasicAnnouncement(codec_config, subgroup_ltvs);

    broadcasts_[broadcast_id]->UpdateBroadcastAnnouncement(
        std::move(announcement));
  }

  void CreateAudioBroadcast(
      bool is_public, const std::string& broadcast_name,
      const std::optional<bluetooth::le_audio::BroadcastCode>& broadcast_code,
      const std::vector<uint8_t>& public_metadata,
      const std::vector<uint8_t>& subgroup_quality,
      const std::vector<std::vector<uint8_t>>& subgroup_metadata) override {
    uint8_t public_features = 0;
    LeAudioLtvMap public_ltv;
    std::vector<LeAudioLtvMap> subgroup_ltvs;

    if (queued_create_broadcast_request_) {
      LOG_ERROR("Not processed yet queued broadcast");
      callbacks_->OnBroadcastCreated(bluetooth::le_audio::kBroadcastIdInvalid,
                                     false);
      return;
    }

    if (is_public) {
      // Prepare public broadcast announcement format
      bool is_metadata_valid;
      public_ltv = LeAudioLtvMap::Parse(
          public_metadata.data(), public_metadata.size(), is_metadata_valid);
      if (!is_metadata_valid) {
        LOG_ERROR("Invalid metadata provided.");
        callbacks_->OnBroadcastCreated(bluetooth::le_audio::kBroadcastIdInvalid,
                                       false);
        return;
      }
      // Prepare public features byte
      // bit 0 Encryption broadcast stream encrypted or not
      // bit 1 Standard quality audio configuration present or not
      // bit 2 High quality audio configuration present or not
      // bit 3-7 RFU
      public_features = static_cast<uint8_t>(broadcast_code ? 1 : 0);
    }

    auto broadcast_id = available_broadcast_ids_.back();
    available_broadcast_ids_.pop_back();
    if (available_broadcast_ids_.size() == 0) GenerateBroadcastIds();

    auto context_type = AudioContexts(LeAudioContextType::MEDIA);

    /* Adds multiple contexts and CCIDs regardless of the incoming audio
     * context. Android has only two CCIDs, one for Media and one for
     * Conversational context. Even though we are not broadcasting
     * Conversational streams, some PTS test cases wants multiple CCIDs.
     */
    if (stack_config_get_interface()
            ->get_pts_force_le_audio_multiple_contexts_metadata()) {
      context_type =
          LeAudioContextType::MEDIA | LeAudioContextType::CONVERSATIONAL;
    }

    for (const uint8_t quality : subgroup_quality) {
      if (quality == bluetooth::le_audio::QUALITY_STANDARD) {
        public_features |= bluetooth::le_audio::kLeAudioQualityStandard;
      } else if (quality == bluetooth::le_audio::QUALITY_HIGH) {
        public_features |= bluetooth::le_audio::kLeAudioQualityHigh;
      }
    }

    for (const std::vector<uint8_t>& metadata : subgroup_metadata) {
      /* Prepare the announcement format */
      bool is_metadata_valid;
      auto ltv = LeAudioLtvMap::Parse(metadata.data(), metadata.size(), is_metadata_valid);
      if (!is_metadata_valid) {
        LOG_ERROR("Invalid metadata provided.");
        callbacks_->OnBroadcastCreated(bluetooth::le_audio::kBroadcastIdInvalid,
                                       false);
        return;
      }

      if (stack_config_get_interface()
              ->get_pts_force_le_audio_multiple_contexts_metadata()) {
        auto stream_context_vec = ltv.Find(
            le_audio::types::kLeAudioMetadataTypeStreamingAudioContext);
        if (stream_context_vec) {
          if (stream_context_vec.value().size() < 2) {
            LOG_ERROR("kLeAudioMetadataTypeStreamingAudioContext size < 2");
            callbacks_->OnBroadcastCreated(
                bluetooth::le_audio::kBroadcastIdInvalid, false);
            return;
          }
          auto pp = stream_context_vec.value().data();
          UINT16_TO_STREAM(pp, context_type.value());
        }
      }

      auto stream_context_vec =
          ltv.Find(le_audio::types::kLeAudioMetadataTypeStreamingAudioContext);
      if (stream_context_vec) {
        if (stream_context_vec.value().size() < 2) {
          LOG_ERROR("kLeAudioMetadataTypeStreamingAudioContext size < 2");
          callbacks_->OnBroadcastCreated(
              bluetooth::le_audio::kBroadcastIdInvalid, false);
          return;
        }

        auto pp = stream_context_vec.value().data();
        STREAM_TO_UINT16(context_type.value_ref(), pp);
      }

      // Append the CCID list
      auto ccid_vec =
          ContentControlIdKeeper::GetInstance()->GetAllCcids(context_type);
      if (!ccid_vec.empty()) {
        ltv.Add(le_audio::types::kLeAudioMetadataTypeCcidList, ccid_vec);
      }

      // Push to subgroup ltvs
      subgroup_ltvs.push_back(ltv);
    }

    auto codec_qos_pair = [](AudioContexts context_type)
        -> std::optional<
            std::pair<const BroadcastCodecWrapper, const BroadcastQosConfig>> {
      if (CodecManager::GetInstance()->GetCodecLocation() ==
          CodecLocation::ADSP) {
        auto offload_config =
            CodecManager::GetInstance()->GetBroadcastOffloadConfig();
        if (offload_config == nullptr) {
          return std::nullopt;
        }
        return std::make_pair(
            BroadcastCodecWrapper(
                {.coding_format = le_audio::types::kLeAudioCodingFormatLC3,
                 .vendor_company_id =
                     le_audio::types::kLeAudioVendorCompanyIdUndefined,
                 .vendor_codec_id =
                     le_audio::types::kLeAudioVendorCodecIdUndefined},
                {.num_channels =
                     static_cast<uint8_t>(offload_config->stream_map.size()),
                 .sample_rate = offload_config->sampling_rate,
                 .bits_per_sample = offload_config->bits_per_sample,
                 .data_interval_us = offload_config->frame_duration},
                offload_config->octets_per_frame),
            BroadcastQosConfig(offload_config->retransmission_number,
                               offload_config->max_transport_latency));
      } else {
        return le_audio::broadcaster::getStreamConfigForContext(context_type);
      }
    }(context_type);

    if (!codec_qos_pair) {
      LOG_ERROR("No valid broadcast offload config");
      return;
    }

    BroadcastStateMachineConfig msg = {
        .is_public = is_public,
        .broadcast_id = broadcast_id,
        .broadcast_name = broadcast_name,
        .streaming_phy = GetStreamingPhy(),
        .codec_wrapper = codec_qos_pair->first,
        .qos_config = codec_qos_pair->second,
        .announcement =
            prepareBasicAnnouncement(codec_qos_pair->first, subgroup_ltvs),
        .broadcast_code = std::move(broadcast_code)};
    if (is_public) {
      msg.public_announcement =
          preparePublicAnnouncement(public_features, public_ltv);
    }

    // If there is ongoing ISO traffic, it might be a unicast stream
    if (is_iso_running_) {
      LOG_INFO("Iso is still active. Queueing broadcast creation for later.");
      if (queued_create_broadcast_request_) {
        LOG_WARN(
            "Already queued. Updating queued broadcast creation with the new "
            "configuration.");
      }
      queued_create_broadcast_request_ = std::move(msg);
      return;
    }

    InstantiateBroadcast(std::move(msg));
  }

  void InstantiateBroadcast(BroadcastStateMachineConfig msg) {
    LOG_INFO("CreateAudioBroadcast");

    /* Put the new broadcast on the initialization queue, notify the error and
     * drop the pending broadcast data if init fails.
     */
    pending_broadcasts_.push_back(
        BroadcastStateMachine::CreateInstance(std::move(msg)));
    if (!pending_broadcasts_.back()->Initialize()) {
      pending_broadcasts_.pop_back();
      callbacks_->OnBroadcastCreated(bluetooth::le_audio::kBroadcastIdInvalid,
                                     false);
    }
  }

  void SuspendAudioBroadcast(uint32_t broadcast_id) override {
    LOG_INFO("broadcast_id=%d", broadcast_id);

    if (broadcasts_.count(broadcast_id) != 0) {
      LOG_INFO("Stopping AudioHalClient");
      if (le_audio_source_hal_client_) le_audio_source_hal_client_->Stop();
      broadcasts_[broadcast_id]->SetMuted(true);
      broadcasts_[broadcast_id]->ProcessMessage(
          BroadcastStateMachine::Message::SUSPEND, nullptr);
    } else {
      LOG_ERROR("No such broadcast_id=%d", broadcast_id);
    }
  }

  static bool IsAnyoneStreaming() {
    if (!instance) return false;

    auto const& iter =
        std::find_if(instance->broadcasts_.cbegin(),
                     instance->broadcasts_.cend(), [](auto const& sm) {
                       return sm.second->GetState() ==
                              BroadcastStateMachine::State::STREAMING;
                     });
    return (iter != instance->broadcasts_.cend());
  }

  void StartAudioBroadcast(uint32_t broadcast_id) override {
    LOG_INFO("Starting broadcast_id=%d", broadcast_id);

    if (queued_start_broadcast_request_) {
      LOG_ERROR("Not processed yet start broadcast request");
      return;
    }

    if (is_iso_running_) {
      queued_start_broadcast_request_ = broadcast_id;
      return;
    }

    if (IsAnyoneStreaming()) {
      LOG_ERROR("Stop the other broadcast first!");
      return;
    }

    if (broadcasts_.count(broadcast_id) != 0) {
      if (!le_audio_source_hal_client_) {
        le_audio_source_hal_client_ =
            LeAudioSourceAudioHalClient::AcquireBroadcast();
        if (!le_audio_source_hal_client_) {
          LOG_ERROR("Could not acquire le audio");
          return;
        }
      }

      broadcasts_[broadcast_id]->ProcessMessage(
          BroadcastStateMachine::Message::START, nullptr);
      le_audio::MetricsCollector::Get()->OnBroadcastStateChanged(true);
    } else {
      LOG_ERROR("No such broadcast_id=%d", broadcast_id);
    }
  }

  void StopAudioBroadcast(uint32_t broadcast_id) override {
    if (broadcasts_.count(broadcast_id) == 0) {
      LOG_ERROR("no such broadcast_id=%d", broadcast_id);
      return;
    }

    LOG_INFO("Stopping AudioHalClient, broadcast_id=%d", broadcast_id);

    if (le_audio_source_hal_client_) le_audio_source_hal_client_->Stop();
    broadcasts_[broadcast_id]->SetMuted(true);
    broadcasts_[broadcast_id]->ProcessMessage(
        BroadcastStateMachine::Message::STOP, nullptr);
    le_audio::MetricsCollector::Get()->OnBroadcastStateChanged(false);
  }

  void DestroyAudioBroadcast(uint32_t broadcast_id) override {
    LOG_INFO("Destroying broadcast_id=%d", broadcast_id);
    broadcasts_.erase(broadcast_id);
  }

  std::optional<bluetooth::le_audio::BroadcastMetadata> GetBroadcastMetadataOpt(
      bluetooth::le_audio::BroadcastId broadcast_id) {
    bluetooth::le_audio::BroadcastMetadata metadata;
    for (auto const& kv_it : broadcasts_) {
      if (kv_it.second->GetBroadcastId() == broadcast_id) {
        metadata.is_public = kv_it.second->IsPublicBroadcast();
        metadata.broadcast_id = kv_it.second->GetBroadcastId();
        metadata.broadcast_name = kv_it.second->GetBroadcastName();
        metadata.adv_sid = kv_it.second->GetAdvertisingSid();
        metadata.pa_interval = kv_it.second->GetPaInterval();
        metadata.addr = kv_it.second->GetOwnAddress();
        metadata.addr_type = kv_it.second->GetOwnAddressType();
        metadata.broadcast_code = kv_it.second->GetBroadcastCode();
        metadata.basic_audio_announcement =
            kv_it.second->GetBroadcastAnnouncement();
        metadata.public_announcement =
            kv_it.second->GetPublicBroadcastAnnouncement();
        return metadata;
      }
    }
    return std::nullopt;
  }

  void GetBroadcastMetadata(uint32_t broadcast_id) override {
    if (broadcasts_.count(broadcast_id) == 0) {
      LOG_ERROR("No such broadcast_id=%d", broadcast_id);
      return;
    }

    auto meta = GetBroadcastMetadataOpt(broadcast_id);
    if (!meta) {
      LOG_ERROR("No metadata for broadcast_id=%d", broadcast_id);
      return;
    }
    callbacks_->OnBroadcastMetadataChanged(broadcast_id,
                                           std::move(meta.value()));
  }

  void GetAllBroadcastStates(void) override {
    for (auto const& kv_it : broadcasts_) {
      callbacks_->OnBroadcastStateChanged(
          kv_it.second->GetBroadcastId(),
          static_cast<bluetooth::le_audio::BroadcastState>(
              kv_it.second->GetState()));
    }
  }

  void IsValidBroadcast(
      uint32_t broadcast_id, uint8_t addr_type, RawAddress addr,
      base::Callback<void(uint8_t /* broadcast_id */, uint8_t /* addr_type */,
                          RawAddress /* addr */, bool /* is_local */)>
          cb) override {
    if (broadcasts_.count(broadcast_id) == 0) {
      LOG_ERROR("No such broadcast_id=%d", broadcast_id);
      std::move(cb).Run(broadcast_id, addr_type, addr, false);
      return;
    }

    broadcasts_[broadcast_id]->RequestOwnAddress(base::Bind(
        [](uint32_t broadcast_id, uint8_t req_address_type,
           RawAddress req_address,
           base::Callback<void(uint8_t /* broadcast_id */,
                               uint8_t /* addr_type */, RawAddress /* addr */,
                               bool /* is_local */)>
               cb,
           uint8_t rcv_address_type, RawAddress rcv_address) {
          bool is_local = (req_address_type == rcv_address_type) &&
                          (req_address == rcv_address);
          std::move(cb).Run(broadcast_id, req_address_type, req_address,
                            is_local);
        },
        broadcast_id, addr_type, addr, std::move(cb)));
  }

  void SetStreamingPhy(uint8_t phy) override { current_phy_ = phy; }

  uint8_t GetStreamingPhy(void) const override { return current_phy_; }

  BroadcastId BroadcastIdFromBigHandle(uint8_t big_handle) const {
    auto pair_it =
        std::find_if(broadcasts_.begin(), broadcasts_.end(),
                     [big_handle](auto const& entry) {
                       return entry.second->GetAdvertisingSid() == big_handle;
                     });
    if (pair_it != broadcasts_.end()) {
      return pair_it->second->GetBroadcastId();
    }
    return bluetooth::le_audio::kBroadcastIdInvalid;
  }

  void OnSetupIsoDataPath(uint8_t status, uint16_t conn_handle,
                          uint8_t big_handle) override {
    auto broadcast_id = BroadcastIdFromBigHandle(big_handle);
    CHECK(broadcasts_.count(broadcast_id) != 0);
    broadcasts_[broadcast_id]->OnSetupIsoDataPath(status, conn_handle);
  }

  void OnRemoveIsoDataPath(uint8_t status, uint16_t conn_handle,
                           uint8_t big_handle) override {
    auto broadcast_id = BroadcastIdFromBigHandle(big_handle);
    CHECK(broadcasts_.count(broadcast_id) != 0);
    broadcasts_[broadcast_id]->OnRemoveIsoDataPath(status, conn_handle);
  }

  void OnBigEvent(uint8_t event, void* data) override {
    switch (event) {
      case bluetooth::hci::iso_manager::kIsoEventBigOnCreateCmpl: {
        auto* evt = static_cast<big_create_cmpl_evt*>(data);
        auto broadcast_id = BroadcastIdFromBigHandle(evt->big_id);
        CHECK(broadcasts_.count(broadcast_id) != 0);
        broadcasts_[broadcast_id]->HandleHciEvent(HCI_BLE_CREATE_BIG_CPL_EVT,
                                                  evt);

      } break;
      case bluetooth::hci::iso_manager::kIsoEventBigOnTerminateCmpl: {
        auto* evt = static_cast<big_terminate_cmpl_evt*>(data);
        auto broadcast_id = BroadcastIdFromBigHandle(evt->big_id);
        CHECK(broadcasts_.count(broadcast_id) != 0);
        broadcasts_[broadcast_id]->HandleHciEvent(HCI_BLE_TERM_BIG_CPL_EVT,
                                                  evt);
        le_audio_source_hal_client_.reset();
      } break;
      default:
        LOG_ERROR("Invalid event=%d", event);
    }
  }

  void IsoTrafficEventCb(bool is_active) {
    is_iso_running_ = is_active;
    LOG_INFO("is_iso_running: %d", is_iso_running_);
    if (!is_iso_running_) {
      if (queued_start_broadcast_request_) {
        auto broadcast_id = *queued_start_broadcast_request_;
        queued_start_broadcast_request_ = std::nullopt;

        LOG_INFO("Start queued broadcast.");
        StartAudioBroadcast(broadcast_id);
      }
      if (queued_create_broadcast_request_) {
        auto broadcast_msg = std::move(*queued_create_broadcast_request_);
        queued_create_broadcast_request_ = std::nullopt;

        LOG_INFO("Create queued broadcast.");
        InstantiateBroadcast(std::move(broadcast_msg));
      }
    }
  }

  void Dump(int fd) {
    std::stringstream stream;

    stream << "    Number of broadcasts: " << broadcasts_.size() << "\n";
    for (auto& broadcast_pair : broadcasts_) {
      auto& broadcast = broadcast_pair.second;
      if (broadcast) stream << *broadcast;
    }

    dprintf(fd, "%s", stream.str().c_str());
  }

 private:
  static class BroadcastStateMachineCallbacks
      : public IBroadcastStateMachineCallbacks {
    void OnStateMachineCreateStatus(uint32_t broadcast_id,
                                    bool initialized) override {
      auto pending_broadcast = std::find_if(
          instance->pending_broadcasts_.begin(),
          instance->pending_broadcasts_.end(), [broadcast_id](auto& sm) {
            return (sm->GetBroadcastId() == broadcast_id);
          });
      LOG_ASSERT(pending_broadcast != instance->pending_broadcasts_.end());
      LOG_ASSERT(instance->broadcasts_.count(broadcast_id) == 0);

      if (initialized) {
        const uint32_t broadcast_id = (*pending_broadcast)->GetBroadcastId();
        LOG_INFO("broadcast_id=%d state=%s", broadcast_id,
                 ToString((*pending_broadcast)->GetState()).c_str());

        instance->broadcasts_[broadcast_id] = std::move(*pending_broadcast);
      } else {
        LOG_ERROR("Failed creating broadcast!");
      }
      instance->pending_broadcasts_.erase(pending_broadcast);
      instance->callbacks_->OnBroadcastCreated(broadcast_id, initialized);
    }

    void OnStateMachineDestroyed(uint32_t broadcast_id) override {
      /* This is a special case when state machine destructor calls this
       * callback. It may happen during the Cleanup() call when all state
       * machines are erased and instance can already be set to null to avoid
       * unnecessary calls.
       */
      if (instance) instance->callbacks_->OnBroadcastDestroyed(broadcast_id);
    }

    static int getStreamerCount() {
      return std::count_if(instance->broadcasts_.begin(),
                           instance->broadcasts_.end(), [](auto const& sm) {
                             LOG_VERBOSE(
                                 "broadcast_id=%d, state=%s",
                                 sm.second->GetBroadcastId(),
                                 ToString(sm.second->GetState()).c_str());
                             return sm.second->GetState() ==
                                    BroadcastStateMachine::State::STREAMING;
                           });
    }

    void OnStateMachineEvent(uint32_t broadcast_id,
                             BroadcastStateMachine::State state,
                             const void* data) override {
      LOG_INFO("broadcast_id=%d state=%s", broadcast_id,
               ToString(state).c_str());

      switch (state) {
        case BroadcastStateMachine::State::STOPPED:
          /* Pass through */
        case BroadcastStateMachine::State::CONFIGURING:
          /* Pass through */
        case BroadcastStateMachine::State::CONFIGURED:
          /* Pass through */
        case BroadcastStateMachine::State::STOPPING:
          /* Nothing to do here? */
          break;
        case BroadcastStateMachine::State::STREAMING:
          if (getStreamerCount() == 1) {
            LOG_INFO("Starting AudioHalClient");

            if (instance->broadcasts_.count(broadcast_id) != 0) {
              const auto& broadcast = instance->broadcasts_.at(broadcast_id);

              // Reconfigure encoder instance for the new stream requirements
              audio_receiver_.setCurrentCodecConfig(
                  broadcast->GetCodecConfig());
              audio_receiver_.CheckAndReconfigureEncoders();

              broadcast->SetMuted(false);
              auto cfg = static_cast<const LeAudioCodecConfiguration*>(data);
              auto is_started = instance->le_audio_source_hal_client_->Start(
                  *cfg, &audio_receiver_);
              if (!is_started) {
                /* Audio Source setup failed - stop the broadcast */
                instance->StopAudioBroadcast(broadcast_id);
                return;
              }

              instance->audio_data_path_state_ = AudioDataPathState::ACTIVE;
            }
          }
          break;
      };

      instance->callbacks_->OnBroadcastStateChanged(
          broadcast_id,
          static_cast<bluetooth::le_audio::BroadcastState>(state));
    }

    void OnOwnAddressResponse(uint32_t broadcast_id, uint8_t addr_type,
                              RawAddress addr) override {
      /* Not used currently */
    }

    void OnBigCreated(const std::vector<uint16_t>& conn_handle) {
      CodecManager::GetInstance()->UpdateBroadcastConnHandle(
          conn_handle,
          std::bind(
              &LeAudioSourceAudioHalClient::UpdateBroadcastAudioConfigToHal,
              instance->le_audio_source_hal_client_.get(),
              std::placeholders::_1));
    }
  } state_machine_callbacks_;

  static class BroadcastAdvertisingCallbacks : public AdvertisingCallbacks {
    void OnAdvertisingSetStarted(int reg_id, uint8_t advertiser_id,
                                 int8_t tx_power, uint8_t status) {
      if (!instance) return;

      if (reg_id == BroadcastStateMachine::kLeAudioBroadcastRegId &&
          !instance->pending_broadcasts_.empty()) {
        instance->pending_broadcasts_.back()->OnCreateAnnouncement(
            advertiser_id, tx_power, status);
      } else {
        LOG_WARN(
            "Ignored OnAdvertisingSetStarted callback reg_id:%d "
            "advertiser_id:%d",
            reg_id, advertiser_id);
      }
    }

    void OnAdvertisingEnabled(uint8_t advertiser_id, bool enable,
                              uint8_t status) {
      if (!instance) return;

      auto const& iter = std::find_if(
          instance->broadcasts_.cbegin(), instance->broadcasts_.cend(),
          [advertiser_id](auto const& sm) {
            return sm.second->GetAdvertisingSid() == advertiser_id;
          });
      if (iter != instance->broadcasts_.cend()) {
        iter->second->OnEnableAnnouncement(enable, status);
      } else {
        LOG_WARN("Ignored OnAdvertisingEnabled callback advertiser_id:%d",
                 advertiser_id);
      }
    }

    void OnAdvertisingDataSet(uint8_t advertiser_id, uint8_t status) {
      LOG_WARN(
          "Not being used, ignored OnAdvertisingDataSet callback "
          "advertiser_id:%d",
          advertiser_id);
    }

    void OnScanResponseDataSet(uint8_t advertiser_id, uint8_t status) {
      LOG_WARN(
          "Not being used, ignored OnScanResponseDataSet callback "
          "advertiser_id:%d",
          advertiser_id);
    }

    void OnAdvertisingParametersUpdated(uint8_t advertiser_id, int8_t tx_power,
                                        uint8_t status) {
      LOG_WARN(
          "Not being used, ignored OnAdvertisingParametersUpdated callback "
          "advertiser_id:%d",
          advertiser_id);
    }

    void OnPeriodicAdvertisingParametersUpdated(uint8_t advertiser_id,
                                                uint8_t status) {
      LOG_WARN(
          "Not being used, ignored OnPeriodicAdvertisingParametersUpdated "
          "callback advertiser_id:%d",
          advertiser_id);
    }

    void OnPeriodicAdvertisingDataSet(uint8_t advertiser_id, uint8_t status) {
      LOG_WARN(
          "Not being used, ignored OnPeriodicAdvertisingDataSet callback "
          "advertiser_id:%d",
          advertiser_id);
    }

    void OnPeriodicAdvertisingEnabled(uint8_t advertiser_id, bool enable,
                                      uint8_t status) {
      LOG_WARN(
          "Not being used, ignored OnPeriodicAdvertisingEnabled callback "
          "advertiser_id:%d",
          advertiser_id);
    }

    void OnOwnAddressRead(uint8_t advertiser_id, uint8_t address_type,
                          RawAddress address) {
      LOG_WARN(
          "Not being used, ignored OnOwnAddressRead callback advertiser_id:%d",
          advertiser_id);
    }
  } state_machine_adv_callbacks_;

  static class LeAudioSourceCallbacksImpl
      : public LeAudioSourceAudioHalClient::Callbacks {
   public:
    LeAudioSourceCallbacksImpl()
        : codec_wrapper_(le_audio::broadcaster::getStreamConfigForContext(
                             AudioContexts(LeAudioContextType::UNSPECIFIED))
                             .first) {}

    void CheckAndReconfigureEncoders() {
      auto const& codec_id = codec_wrapper_.GetLeAudioCodecId();
      /* TODO: We should act smart and reuse current configurations */
      sw_enc_.clear();
      while (sw_enc_.size() != codec_wrapper_.GetNumChannels()) {
        auto codec = le_audio::CodecInterface::CreateInstance(codec_id);

        auto codec_status =
            codec->InitEncoder(codec_wrapper_.GetLeAudioCodecConfiguration(),
                               codec_wrapper_.GetLeAudioCodecConfiguration());
        if (codec_status != le_audio::CodecInterface::Status::STATUS_OK) {
          LOG_ERROR("Channel %d codec setup failed with err: %d",
                    (uint32_t)sw_enc_.size(), codec_status);
          return;
        }

        sw_enc_.emplace_back(std::move(codec));
      }
    }

    const BroadcastCodecWrapper& getCurrentCodecConfig(void) const {
      return codec_wrapper_;
    }

    void setCurrentCodecConfig(BroadcastCodecWrapper const& config) {
      codec_wrapper_ = config;
    }

    static void sendBroadcastData(
        const std::unique_ptr<BroadcastStateMachine>& broadcast,
        std::vector<std::unique_ptr<le_audio::CodecInterface>>& encoders) {
      auto const& config = broadcast->GetBigConfig();
      if (config == std::nullopt) {
        LOG_ERROR(
            "Broadcast broadcast_id=%d has no valid BIS configurations in "
            "state=%s",
            broadcast->GetBroadcastId(),
            ToString(broadcast->GetState()).c_str());
        return;
      }

      if (config->connection_handles.size() < encoders.size()) {
        LOG_ERROR("Not enough BIS'es to broadcast all channels!");
        return;
      }

      for (uint8_t chan = 0; chan < encoders.size(); ++chan) {
        IsoManager::GetInstance()->SendIsoData(
            config->connection_handles[chan],
            (const uint8_t*)encoders[chan]->GetDecodedSamples().data(),
            encoders[chan]->GetDecodedSamples().size() * 2);
      }
    }

    virtual void OnAudioDataReady(const std::vector<uint8_t>& data) override {
      if (!instance) return;

      LOG_VERBOSE("Received %zu bytes.", data.size());

      /* Constants for the channel data configuration */
      const auto num_channels = codec_wrapper_.GetNumChannels();
      const auto bytes_per_sample = (codec_wrapper_.GetBitsPerSample() / 8);

      /* Prepare encoded data for all channels */
      for (uint8_t chan = 0; chan < num_channels; ++chan) {
        auto initial_channel_offset = chan * bytes_per_sample;
        sw_enc_[chan]->Encode(data.data() + initial_channel_offset,
                              num_channels,
                              codec_wrapper_.GetOctetsPerCodecFrame());
      }

      /* Currently there is no way to broadcast multiple distinct streams.
       * We just receive all system sounds mixed into a one stream and each
       * broadcast gets the same data.
       */
      for (auto& broadcast_pair : instance->broadcasts_) {
        auto& broadcast = broadcast_pair.second;
        if ((broadcast->GetState() ==
             BroadcastStateMachine::State::STREAMING) &&
            !broadcast->IsMuted())
          sendBroadcastData(broadcast, sw_enc_);
      }
      LOG_VERBOSE("All data sent.");
    }

    virtual void OnAudioSuspend(void) override {
      LOG_INFO();
      /* TODO: Should we suspend all broadcasts - remove BIGs? */
      if (instance)
        instance->audio_data_path_state_ = AudioDataPathState::SUSPENDED;
    }

    virtual void OnAudioResume(void) override {
      LOG_INFO();
      if (!instance) return;

      /* TODO: Should we resume all broadcasts - recreate BIGs? */
      instance->audio_data_path_state_ = AudioDataPathState::ACTIVE;

      if (!IsAnyoneStreaming()) {
        instance->le_audio_source_hal_client_->CancelStreamingRequest();
        return;
      }

      instance->le_audio_source_hal_client_->ConfirmStreamingRequest();
    }

    virtual void OnAudioMetadataUpdate(
        const std::vector<struct playback_track_metadata_v7> source_metadata,
        DsaMode dsa_mode) override {
      LOG_INFO();
      if (!instance) return;

      /* TODO: Should we take supported contexts from ASCS? */
      auto contexts = GetAudioContextsFromSourceMetadata(source_metadata);
      if (contexts.any()) {
        /* NOTICE: We probably don't want to change the stream configuration
         * on each metadata change, so just update the context type metadata.
         * Since we are not able to identify individual track streams and
         * they are all mixed inside a single data stream, we will update
         * the metadata of all BIS subgroups with the same combined context.
         */
        instance->UpdateStreamingContextTypeOnAllSubgroups(contexts);
      }
    }

   private:
    BroadcastCodecWrapper codec_wrapper_;
    std::vector<std::unique_ptr<le_audio::CodecInterface>> sw_enc_;
  } audio_receiver_;

  bluetooth::le_audio::LeAudioBroadcasterCallbacks* callbacks_;
  std::map<uint32_t, std::unique_ptr<BroadcastStateMachine>> broadcasts_;
  std::vector<std::unique_ptr<BroadcastStateMachine>> pending_broadcasts_;
  std::optional<BroadcastStateMachineConfig> queued_create_broadcast_request_;
  std::optional<uint32_t> queued_start_broadcast_request_;

  /* Some BIG params are set globally */
  uint8_t current_phy_;
  AudioDataPathState audio_data_path_state_;
  std::unique_ptr<LeAudioSourceAudioHalClient> le_audio_source_hal_client_;
  std::vector<BroadcastId> available_broadcast_ids_;

  // Flag to track iso state
  bool is_iso_running_ = false;
};

/* Static members definitions */
LeAudioBroadcasterImpl::BroadcastStateMachineCallbacks
    LeAudioBroadcasterImpl::state_machine_callbacks_;
LeAudioBroadcasterImpl::LeAudioSourceCallbacksImpl
    LeAudioBroadcasterImpl::audio_receiver_;
LeAudioBroadcasterImpl::BroadcastAdvertisingCallbacks
    LeAudioBroadcasterImpl::state_machine_adv_callbacks_;
} /* namespace */

void LeAudioBroadcaster::Initialize(
    bluetooth::le_audio::LeAudioBroadcasterCallbacks* callbacks,
    base::Callback<bool()> audio_hal_verifier) {
  std::scoped_lock<std::mutex> lock(instance_mutex);
  LOG_INFO();
  if (instance) {
    LOG_ERROR("Already initialized");
    return;
  }

  if (!controller_get_interface()->SupportsBleIsochronousBroadcaster() &&
      !osi_property_get_bool("persist.bluetooth.fake_iso_support", false)) {
    LOG_WARN("Isochronous Broadcast not supported by the controller!");
    return;
  }

  if (!std::move(audio_hal_verifier).Run()) {
    LOG_ALWAYS_FATAL("HAL requirements not met. Init aborted.");
  }

  IsoManager::GetInstance()->Start();

  instance = new LeAudioBroadcasterImpl(callbacks);
  /* Register HCI event handlers */
  IsoManager::GetInstance()->RegisterBigCallbacks(instance);
  /* Register for active traffic */
  IsoManager::GetInstance()->RegisterOnIsoTrafficActiveCallback(
      [](bool is_active) {
        if (instance) instance->IsoTrafficEventCb(is_active);
      });
}

bool LeAudioBroadcaster::IsLeAudioBroadcasterRunning() { return instance; }

LeAudioBroadcaster* LeAudioBroadcaster::Get(void) {
  LOG_INFO();
  CHECK(instance);
  return instance;
}

void LeAudioBroadcaster::Stop(void) {
  LOG_INFO();

  if (instance) {
    instance->Stop();
  }
}

void LeAudioBroadcaster::Cleanup(void) {
  std::scoped_lock<std::mutex> lock(instance_mutex);
  LOG_INFO();

  if (instance == nullptr) return;

  LeAudioBroadcasterImpl* ptr = instance;
  instance = nullptr;

  ptr->CleanUp();
  delete ptr;
}

void LeAudioBroadcaster::DebugDump(int fd) {
  std::scoped_lock<std::mutex> lock(instance_mutex);
  dprintf(fd, "Le Audio Broadcaster:\n");
  if (instance) instance->Dump(fd);
  dprintf(fd, "\n");
}
