/*
 * Copyright 2018 The Android Open Source Project
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

#define LOG_TAG "avrcp"

#include "device.h"

#include <bluetooth/log.h>

#include "abstract_message_loop.h"
#include "avrcp_common.h"
#include "include/check.h"
#include "internal_include/stack_config.h"
#include "packet/avrcp/avrcp_reject_packet.h"
#include "packet/avrcp/general_reject_packet.h"
#include "packet/avrcp/get_current_player_application_setting_value.h"
#include "packet/avrcp/get_play_status_packet.h"
#include "packet/avrcp/list_player_application_setting_attributes.h"
#include "packet/avrcp/list_player_application_setting_values.h"
#include "packet/avrcp/pass_through_packet.h"
#include "packet/avrcp/set_absolute_volume.h"
#include "packet/avrcp/set_addressed_player.h"
#include "packet/avrcp/set_player_application_setting_value.h"
#include "types/raw_address.h"

extern bool btif_av_peer_is_connected_sink(const RawAddress& peer_address);
extern bool btif_av_both_enable(void);
extern bool btif_av_src_sink_coexist_enabled(void);

template <>
struct fmt::formatter<bluetooth::avrcp::PlayState>
    : enum_formatter<bluetooth::avrcp::PlayState> {};

namespace bluetooth {
namespace avrcp {

#define VOL_NOT_SUPPORTED -1
#define VOL_REGISTRATION_FAILED -2

Device::Device(const RawAddress& bdaddr, bool avrcp13_compatibility,
               base::RepeatingCallback<
                   void(uint8_t label, bool browse,
                        std::unique_ptr<::bluetooth::PacketBuilder> message)>
                   send_msg_cb,
               uint16_t ctrl_mtu, uint16_t browse_mtu)
    : weak_ptr_factory_(this),
      address_(bdaddr),
      avrcp13_compatibility_(avrcp13_compatibility),
      send_message_cb_(send_msg_cb),
      ctrl_mtu_(ctrl_mtu),
      browse_mtu_(browse_mtu),
      has_bip_client_(false) {}

void Device::RegisterInterfaces(
    MediaInterface* media_interface, A2dpInterface* a2dp_interface,
    VolumeInterface* volume_interface,
    PlayerSettingsInterface* player_settings_interface) {
  CHECK(media_interface);
  CHECK(a2dp_interface);
  a2dp_interface_ = a2dp_interface;
  media_interface_ = media_interface;
  volume_interface_ = volume_interface;
  player_settings_interface_ = player_settings_interface;
}

base::WeakPtr<Device> Device::Get() { return weak_ptr_factory_.GetWeakPtr(); }

void Device::SetBrowseMtu(uint16_t browse_mtu) {
  log::info("{}: browse_mtu = {}", ADDRESS_TO_LOGGABLE_STR(address_),
            browse_mtu);
  browse_mtu_ = browse_mtu;
}

void Device::SetBipClientStatus(bool connected) {
  log::info("{}: connected = {}", ADDRESS_TO_LOGGABLE_STR(address_), connected);
  has_bip_client_ = connected;
}

bool Device::HasBipClient() const { return has_bip_client_; }

void filter_cover_art(SongInfo& s) {
  for (auto it = s.attributes.begin(); it != s.attributes.end(); it++) {
    if (it->attribute() == Attribute::DEFAULT_COVER_ART) {
      s.attributes.erase(it);
      break;
    }
  }
}

bool Device::IsActive() const {
  return address_ == a2dp_interface_->active_peer();
}

bool Device::IsInSilenceMode() const {
  return a2dp_interface_->is_peer_in_silence_mode(address_);
}

void Device::VendorPacketHandler(uint8_t label,
                                 std::shared_ptr<VendorPacket> pkt) {
  CHECK(media_interface_);
  log::verbose("pdu={}", pkt->GetCommandPdu());

  if (!pkt->IsValid()) {
    log::warn("{}: Request packet is not valid",
              ADDRESS_TO_LOGGABLE_STR(address_));
    auto response = RejectBuilder::MakeBuilder(static_cast<CommandPdu>(0),
                                               Status::INVALID_COMMAND);
    send_message(label, false, std::move(response));
    return;
  }

  // All CTypes at and above NOT_IMPLEMENTED are all response types.
  if (pkt->GetCType() == CType::NOT_IMPLEMENTED) {
    return;
  }

  if (pkt->GetCType() >= CType::ACCEPTED) {
    switch (pkt->GetCommandPdu()) {
      // VOLUME_CHANGED is the only notification we register for while target.
      case CommandPdu::REGISTER_NOTIFICATION: {
        auto register_notification =
            Packet::Specialize<RegisterNotificationResponse>(pkt);

        if ((!btif_av_src_sink_coexist_enabled() ||
             (btif_av_src_sink_coexist_enabled() &&
              register_notification->GetEvent() == Event::VOLUME_CHANGED)) &&
            !register_notification->IsValid()) {
          log::warn("{}: Request packet is not valid",
                    ADDRESS_TO_LOGGABLE_STR(address_));
          auto response = RejectBuilder::MakeBuilder(pkt->GetCommandPdu(),
                                                     Status::INVALID_PARAMETER);
          send_message(label, false, std::move(response));
          active_labels_.erase(label);
          volume_interface_ = nullptr;
          volume_ = VOL_REGISTRATION_FAILED;
          return;
        }

        if (register_notification->GetEvent() != Event::VOLUME_CHANGED) {
          log::warn("{}: Unhandled register notification received: {}",
                    ADDRESS_TO_LOGGABLE_STR(address_),
                    register_notification->GetEvent());
          return;
        }
        HandleVolumeChanged(label, register_notification);
        break;
      }
      case CommandPdu::SET_ABSOLUTE_VOLUME:
        // TODO (apanicke): Add a retry mechanism if the response has a
        // different volume than the one we set. For now, we don't care
        // about the response to this message.
        break;
      default:
        log::warn("{}: Unhandled Response: pdu={}",
                  ADDRESS_TO_LOGGABLE_STR(address_), pkt->GetCommandPdu());
        break;
    }
    return;
  }

  switch (pkt->GetCommandPdu()) {
    case CommandPdu::GET_CAPABILITIES: {
      HandleGetCapabilities(label,
                            Packet::Specialize<GetCapabilitiesRequest>(pkt));
    } break;

    case CommandPdu::REGISTER_NOTIFICATION: {
      HandleNotification(label,
                         Packet::Specialize<RegisterNotificationRequest>(pkt));
    } break;

    case CommandPdu::GET_ELEMENT_ATTRIBUTES: {
      auto get_element_attributes_request_pkt =
          Packet::Specialize<GetElementAttributesRequest>(pkt);

      if (!get_element_attributes_request_pkt->IsValid()) {
        log::warn("{}: Request packet is not valid",
                  ADDRESS_TO_LOGGABLE_STR(address_));
        auto response = RejectBuilder::MakeBuilder(pkt->GetCommandPdu(),
                                                   Status::INVALID_PARAMETER);
        send_message(label, false, std::move(response));
        return;
      }
      media_interface_->GetSongInfo(base::Bind(
          &Device::GetElementAttributesResponse, weak_ptr_factory_.GetWeakPtr(),
          label, get_element_attributes_request_pkt));
    } break;

    case CommandPdu::GET_PLAY_STATUS: {
      media_interface_->GetPlayStatus(base::Bind(&Device::GetPlayStatusResponse,
                                                 weak_ptr_factory_.GetWeakPtr(),
                                                 label));
    } break;

    case CommandPdu::PLAY_ITEM: {
      HandlePlayItem(label, Packet::Specialize<PlayItemRequest>(pkt));
    } break;

    case CommandPdu::SET_ADDRESSED_PLAYER: {
      // TODO (apanicke): Implement set addressed player. We don't need
      // this currently since the current implementation only has one
      // player and the player will never change, but we need it for a
      // more complete implementation.
      auto set_addressed_player_request =
          Packet::Specialize<SetAddressedPlayerRequest>(pkt);

      if (!set_addressed_player_request->IsValid()) {
        log::warn("{}: Request packet is not valid",
                  ADDRESS_TO_LOGGABLE_STR(address_));
        auto response = RejectBuilder::MakeBuilder(pkt->GetCommandPdu(),
                                                   Status::INVALID_PARAMETER);
        send_message(label, false, std::move(response));
        return;
      }

      media_interface_->GetMediaPlayerList(base::Bind(
          &Device::HandleSetAddressedPlayer, weak_ptr_factory_.GetWeakPtr(),
          label, set_addressed_player_request));
    } break;

    case CommandPdu::LIST_PLAYER_APPLICATION_SETTING_ATTRIBUTES: {
      if (player_settings_interface_ == nullptr) {
        log::error("Player Settings Interface not initialized.");
        auto response = RejectBuilder::MakeBuilder(pkt->GetCommandPdu(),
                                                   Status::INVALID_COMMAND);
        send_message(label, false, std::move(response));
        return;
      }

      player_settings_interface_->ListPlayerSettings(
          base::Bind(&Device::ListPlayerApplicationSettingAttributesResponse,
                     weak_ptr_factory_.GetWeakPtr(), label));
    } break;

    case CommandPdu::LIST_PLAYER_APPLICATION_SETTING_VALUES: {
      if (player_settings_interface_ == nullptr) {
        log::error("Player Settings Interface not initialized.");
        auto response = RejectBuilder::MakeBuilder(pkt->GetCommandPdu(),
                                                   Status::INVALID_COMMAND);
        send_message(label, false, std::move(response));
        return;
      }
      auto list_player_setting_values_request =
          Packet::Specialize<ListPlayerApplicationSettingValuesRequest>(pkt);

      if (!list_player_setting_values_request->IsValid()) {
        log::warn("{}: Request packet is not valid",
                  ADDRESS_TO_LOGGABLE_STR(address_));
        auto response = RejectBuilder::MakeBuilder(pkt->GetCommandPdu(),
                                                   Status::INVALID_PARAMETER);
        send_message(label, false, std::move(response));
        return;
      }

      PlayerAttribute attribute =
          list_player_setting_values_request->GetRequestedAttribute();
      if (attribute < PlayerAttribute::EQUALIZER ||
          attribute > PlayerAttribute::SCAN) {
        log::warn("{}: Player Setting Attribute is not valid",
                  ADDRESS_TO_LOGGABLE_STR(address_));
        auto response = RejectBuilder::MakeBuilder(pkt->GetCommandPdu(),
                                                   Status::INVALID_PARAMETER);
        send_message(label, false, std::move(response));
        return;
      }

      player_settings_interface_->ListPlayerSettingValues(
          attribute,
          base::Bind(&Device::ListPlayerApplicationSettingValuesResponse,
                     weak_ptr_factory_.GetWeakPtr(), label));
    } break;

    case CommandPdu::GET_CURRENT_PLAYER_APPLICATION_SETTING_VALUE: {
      if (player_settings_interface_ == nullptr) {
        log::error("Player Settings Interface not initialized.");
        auto response = RejectBuilder::MakeBuilder(pkt->GetCommandPdu(),
                                                   Status::INVALID_COMMAND);
        send_message(label, false, std::move(response));
        return;
      }
      auto get_current_player_setting_value_request =
          Packet::Specialize<GetCurrentPlayerApplicationSettingValueRequest>(
              pkt);

      if (!get_current_player_setting_value_request->IsValid()) {
        log::warn("{}: Request packet is not valid",
                  ADDRESS_TO_LOGGABLE_STR(address_));
        auto response = RejectBuilder::MakeBuilder(pkt->GetCommandPdu(),
                                                   Status::INVALID_PARAMETER);
        send_message(label, false, std::move(response));
        return;
      }

      std::vector<PlayerAttribute> attributes =
          get_current_player_setting_value_request->GetRequestedAttributes();
      for (auto attribute : attributes) {
        if (attribute < PlayerAttribute::EQUALIZER ||
            attribute > PlayerAttribute::SCAN) {
          log::warn("{}: Player Setting Attribute is not valid",
                    ADDRESS_TO_LOGGABLE_STR(address_));
          auto response = RejectBuilder::MakeBuilder(pkt->GetCommandPdu(),
                                                     Status::INVALID_PARAMETER);
          send_message(label, false, std::move(response));
          return;
        }
      }

      player_settings_interface_->GetCurrentPlayerSettingValue(
          attributes,
          base::Bind(&Device::GetPlayerApplicationSettingValueResponse,
                     weak_ptr_factory_.GetWeakPtr(), label));
    } break;

    case CommandPdu::SET_PLAYER_APPLICATION_SETTING_VALUE: {
      if (player_settings_interface_ == nullptr) {
        log::error("Player Settings Interface not initialized.");
        auto response = RejectBuilder::MakeBuilder(pkt->GetCommandPdu(),
                                                   Status::INVALID_COMMAND);
        send_message(label, false, std::move(response));
        return;
      }
      auto set_player_setting_value_request =
          Packet::Specialize<SetPlayerApplicationSettingValueRequest>(pkt);

      if (!set_player_setting_value_request->IsValid()) {
        log::warn("{} : Request packet is not valid",
                  ADDRESS_TO_LOGGABLE_STR(address_));
        auto response = RejectBuilder::MakeBuilder(pkt->GetCommandPdu(),
                                                   Status::INVALID_PARAMETER);
        send_message(label, false, std::move(response));
        return;
      }

      std::vector<PlayerAttribute> attributes =
          set_player_setting_value_request->GetRequestedAttributes();
      std::vector<uint8_t> values =
          set_player_setting_value_request->GetRequestedValues();

      bool invalid_request = false;
      for (size_t i = 0; i < attributes.size(); i++) {
        if (attributes[i] < PlayerAttribute::EQUALIZER ||
            attributes[i] > PlayerAttribute::SCAN) {
          log::warn("{}: Player Setting Attribute is not valid",
                    ADDRESS_TO_LOGGABLE_STR(address_));
          invalid_request = true;
          break;
        }

        if (attributes[i] == PlayerAttribute::REPEAT) {
          PlayerRepeatValue value = static_cast<PlayerRepeatValue>(values[i]);
          if (value < PlayerRepeatValue::OFF ||
              value > PlayerRepeatValue::GROUP) {
            log::warn("{}: Player Repeat Value is not valid",
                      ADDRESS_TO_LOGGABLE_STR(address_));
            invalid_request = true;
            break;
          }
        } else if (attributes[i] == PlayerAttribute::SHUFFLE) {
          PlayerShuffleValue value = static_cast<PlayerShuffleValue>(values[i]);
          if (value < PlayerShuffleValue::OFF ||
              value > PlayerShuffleValue::GROUP) {
            log::warn("{}: Player Shuffle Value is not valid",
                      ADDRESS_TO_LOGGABLE_STR(address_));
            invalid_request = true;
            break;
          }
        }
      }

      if (invalid_request) {
        auto response = RejectBuilder::MakeBuilder(pkt->GetCommandPdu(),
                                                   Status::INVALID_PARAMETER);
        send_message(label, false, std::move(response));
        return;
      }

      player_settings_interface_->SetPlayerSettings(
          attributes, values,
          base::Bind(&Device::SetPlayerApplicationSettingValueResponse,
                     weak_ptr_factory_.GetWeakPtr(), label,
                     pkt->GetCommandPdu()));
    } break;

    default: {
      log::error("{}: Unhandled Vendor Packet: {}",
                 ADDRESS_TO_LOGGABLE_STR(address_), pkt->ToString());
      auto response = RejectBuilder::MakeBuilder(
          (CommandPdu)pkt->GetCommandPdu(), Status::INVALID_COMMAND);
      send_message(label, false, std::move(response));
    } break;
  }
}

void Device::HandleGetCapabilities(
    uint8_t label, const std::shared_ptr<GetCapabilitiesRequest>& pkt) {
  if (!pkt->IsValid()) {
    log::warn("{}: Request packet is not valid",
              ADDRESS_TO_LOGGABLE_STR(address_));
    auto response = RejectBuilder::MakeBuilder(pkt->GetCommandPdu(),
                                               Status::INVALID_PARAMETER);
    send_message(label, false, std::move(response));
    return;
  }

  log::verbose("capability={}", pkt->GetCapabilityRequested());

  switch (pkt->GetCapabilityRequested()) {
    case Capability::COMPANY_ID: {
      auto response =
          GetCapabilitiesResponseBuilder::MakeCompanyIdBuilder(0x001958);
      response->AddCompanyId(0x002345);
      send_message_cb_.Run(label, false, std::move(response));
    } break;

    case Capability::EVENTS_SUPPORTED: {
      auto response =
          GetCapabilitiesResponseBuilder::MakeEventsSupportedBuilder(
              Event::PLAYBACK_STATUS_CHANGED);
      response->AddEvent(Event::TRACK_CHANGED);
      response->AddEvent(Event::PLAYBACK_POS_CHANGED);
      if (player_settings_interface_ != nullptr) {
        response->AddEvent(Event::PLAYER_APPLICATION_SETTING_CHANGED);
      }

      if (!avrcp13_compatibility_) {
        response->AddEvent(Event::AVAILABLE_PLAYERS_CHANGED);
        response->AddEvent(Event::ADDRESSED_PLAYER_CHANGED);
        response->AddEvent(Event::UIDS_CHANGED);
        response->AddEvent(Event::NOW_PLAYING_CONTENT_CHANGED);
      }

      send_message(label, false, std::move(response));
    } break;

    default: {
      log::warn("{}: Unhandled Capability: {}",
                ADDRESS_TO_LOGGABLE_STR(address_),
                pkt->GetCapabilityRequested());
      auto response = RejectBuilder::MakeBuilder(CommandPdu::GET_CAPABILITIES,
                                                 Status::INVALID_PARAMETER);
      send_message(label, false, std::move(response));
    } break;
  }
}

void Device::HandleNotification(
    uint8_t label, const std::shared_ptr<RegisterNotificationRequest>& pkt) {
  if (!pkt->IsValid()) {
    log::warn("{}: Request packet is not valid",
              ADDRESS_TO_LOGGABLE_STR(address_));
    auto response = RejectBuilder::MakeBuilder(pkt->GetCommandPdu(),
                                               Status::INVALID_PARAMETER);
    send_message(label, false, std::move(response));
    return;
  }

  log::verbose("event={}", pkt->GetEventRegistered());

  switch (pkt->GetEventRegistered()) {
    case Event::TRACK_CHANGED: {
      media_interface_->GetNowPlayingList(
          base::Bind(&Device::TrackChangedNotificationResponse,
                     weak_ptr_factory_.GetWeakPtr(), label, true));
    } break;

    case Event::PLAYBACK_STATUS_CHANGED: {
      media_interface_->GetPlayStatus(
          base::Bind(&Device::PlaybackStatusNotificationResponse,
                     weak_ptr_factory_.GetWeakPtr(), label, true));
    } break;

    case Event::PLAYBACK_POS_CHANGED: {
      play_pos_interval_ = pkt->GetInterval();
      media_interface_->GetPlayStatus(
          base::Bind(&Device::PlaybackPosNotificationResponse,
                     weak_ptr_factory_.GetWeakPtr(), label, true));
    } break;

    case Event::PLAYER_APPLICATION_SETTING_CHANGED: {
      if (player_settings_interface_ == nullptr) {
        log::error("Player Settings Interface not initialized.");
        auto response = RejectBuilder::MakeBuilder(pkt->GetCommandPdu(),
                                                   Status::INVALID_COMMAND);
        send_message(label, false, std::move(response));
        return;
      }
      std::vector<PlayerAttribute> attributes = {
          PlayerAttribute::EQUALIZER, PlayerAttribute::REPEAT,
          PlayerAttribute::SHUFFLE, PlayerAttribute::SCAN};
      player_settings_interface_->GetCurrentPlayerSettingValue(
          attributes,
          base::Bind(&Device::PlayerSettingChangedNotificationResponse,
                     weak_ptr_factory_.GetWeakPtr(), label, true));
    } break;

    case Event::NOW_PLAYING_CONTENT_CHANGED: {
      media_interface_->GetNowPlayingList(
          base::Bind(&Device::HandleNowPlayingNotificationResponse,
                     weak_ptr_factory_.GetWeakPtr(), label, true));
    } break;

    case Event::AVAILABLE_PLAYERS_CHANGED: {
      // TODO (apanicke): If we make a separate handler function for this, make
      // sure to register the notification in the interim response.

      // Respond immediately since this notification doesn't require any info
      avail_players_changed_ = Notification(true, label);
      auto response =
          RegisterNotificationResponseBuilder::MakeAvailablePlayersBuilder(
              true);
      send_message(label, false, std::move(response));
    } break;

    case Event::ADDRESSED_PLAYER_CHANGED: {
      media_interface_->GetMediaPlayerList(
          base::Bind(&Device::AddressedPlayerNotificationResponse,
                     weak_ptr_factory_.GetWeakPtr(), label, true));
    } break;

    case Event::UIDS_CHANGED: {
      // TODO (apanicke): If we make a separate handler function for this, make
      // sure to register the notification in the interim response.

      // Respond immediately since this notification doesn't require any info
      uids_changed_ = Notification(true, label);
      auto response =
          RegisterNotificationResponseBuilder::MakeUidsChangedBuilder(true, 0);
      send_message(label, false, std::move(response));
    } break;

    default: {
      log::error("{}: Unknown event registered. Event ID={}",
                 ADDRESS_TO_LOGGABLE_STR(address_), pkt->GetEventRegistered());
      auto response = RejectBuilder::MakeBuilder(
          (CommandPdu)pkt->GetCommandPdu(), Status::INVALID_PARAMETER);
      send_message(label, false, std::move(response));
    } break;
  }
}

void Device::RegisterVolumeChanged() {
  log::verbose("");
  if (volume_interface_ == nullptr) return;

  auto request =
      RegisterNotificationRequestBuilder::MakeBuilder(Event::VOLUME_CHANGED, 0);

  // Find an open transaction label to prevent conflicts with other commands
  // that are in flight. We can not use the reserved label while the
  // notification hasn't been completed.
  uint8_t label = MAX_TRANSACTION_LABEL;
  for (uint8_t i = 0; i < MAX_TRANSACTION_LABEL; i++) {
    if (active_labels_.find(i) == active_labels_.end()) {
      active_labels_.insert(i);
      label = i;
      break;
    }
  }

  if (label == MAX_TRANSACTION_LABEL) {
    log::fatal("{}: Abandon all hope, something went catastrophically wrong",
               ADDRESS_TO_LOGGABLE_STR(address_));
  }

  send_message_cb_.Run(label, false, std::move(request));
}

void Device::HandleVolumeChanged(
    uint8_t label, const std::shared_ptr<RegisterNotificationResponse>& pkt) {
  log::verbose("interim={}", pkt->IsInterim());

  if (volume_interface_ == nullptr) return;

  if (pkt->GetCType() == CType::REJECTED) {
    // Disable Absolute Volume
    active_labels_.erase(label);
    volume_ = VOL_REGISTRATION_FAILED;
    volume_interface_->DeviceConnected(GetAddress());
    return;
  }

  // We only update on interim and just re-register on changes.
  if (!pkt->IsInterim()) {
    active_labels_.erase(label);
    RegisterVolumeChanged();
    return;
  }

  // Handle the first volume update.
  if (volume_ == VOL_NOT_SUPPORTED) {
    volume_ = pkt->GetVolume();
    volume_ &= ~0x80;  // remove RFA bit
    volume_interface_->DeviceConnected(
        GetAddress(),
        base::Bind(&Device::SetVolume, weak_ptr_factory_.GetWeakPtr()));

    // Ignore the returned volume in favor of the volume returned
    // by the volume interface.
    return;
  }

  if (!IsActive()) {
    log::verbose("Ignoring volume changes from non active device");
    return;
  }

  volume_ = pkt->GetVolume();
  volume_ &= ~0x80;  // remove RFA bit
  log::verbose("Volume has changed to {}", (uint32_t)volume_);
  volume_interface_->SetVolume(volume_);
}

void Device::SetVolume(int8_t volume) {
  // TODO (apanicke): Implement logic for Multi-AVRCP
  log::verbose("volume={}", (int)volume);
  if (volume == volume_) {
    log::warn("{}: Ignoring volume change same as current volume level",
              ADDRESS_TO_LOGGABLE_STR(address_));
    return;
  }
  auto request = SetAbsoluteVolumeRequestBuilder::MakeBuilder(volume);

  uint8_t label = MAX_TRANSACTION_LABEL;
  for (uint8_t i = 0; i < MAX_TRANSACTION_LABEL; i++) {
    if (active_labels_.find(i) == active_labels_.end()) {
      active_labels_.insert(i);
      label = i;
      break;
    }
  }

  volume_ = volume;
  send_message_cb_.Run(label, false, std::move(request));
}

void Device::TrackChangedNotificationResponse(uint8_t label, bool interim,
                                              std::string curr_song_id,
                                              std::vector<SongInfo> song_list) {
  log::verbose("");

  if (interim) {
    track_changed_ = Notification(true, label);
  } else if (!track_changed_.first) {
    log::verbose("Device not registered for update");
    return;
  }

  if (!interim) {
    if (curr_song_id.empty()) {
      // CHANGED response is only defined when there is media selected
      // for playing.
      return;
    }
    active_labels_.erase(label);
    track_changed_ = Notification(false, 0);
  }

  // Case for browsing not supported;
  // PTS BV-04-C and BV-5-C assume browsing not supported
  if (stack_config_get_interface()->get_pts_avrcp_test()) {
    log::warn("{}: pts test mode", ADDRESS_TO_LOGGABLE_STR(address_));
    uint64_t uid = curr_song_id.empty() ? 0xffffffffffffffff : 0;
    auto response =
        RegisterNotificationResponseBuilder::MakeTrackChangedBuilder(interim,
                                                                     uid);
    send_message_cb_.Run(label, false, std::move(response));
    return;
  }

  // Anytime we use the now playing list, update our map so that its always
  // current
  now_playing_ids_.clear();
  uint64_t uid = 0;
  for (const SongInfo& song : song_list) {
    now_playing_ids_.insert(song.media_id);
    if (curr_song_id == song.media_id) {
      log::verbose("Found media ID match for {}", song.media_id);
      uid = now_playing_ids_.get_uid(curr_song_id);
    }
  }

  if (uid == 0) {
    // uid 0 is not valid here when browsing is supported
    log::error("{}: No match for media ID found",
               ADDRESS_TO_LOGGABLE_STR(address_));
  }

  auto response = RegisterNotificationResponseBuilder::MakeTrackChangedBuilder(
      interim, uid);
  send_message_cb_.Run(label, false, std::move(response));
}

void Device::PlaybackStatusNotificationResponse(uint8_t label, bool interim,
                                                PlayStatus status) {
  log::verbose("");
  if (status.state == PlayState::PAUSED) play_pos_update_cb_.Cancel();

  if (interim) {
    play_status_changed_ = Notification(true, label);
  } else if (!play_status_changed_.first) {
    log::verbose("Device not registered for update");
    return;
  }

  auto state_to_send = status.state;
  if (!IsActive()) state_to_send = PlayState::PAUSED;
  if (!interim && state_to_send == last_play_status_.state) {
    log::verbose("Not sending notification due to no state update {}",
                 ADDRESS_TO_LOGGABLE_STR(address_));
    return;
  }

  last_play_status_.state = state_to_send;

  auto response =
      RegisterNotificationResponseBuilder::MakePlaybackStatusBuilder(
          interim, IsActive() ? status.state : PlayState::PAUSED);
  send_message_cb_.Run(label, false, std::move(response));

  if (!interim) {
    active_labels_.erase(label);
    play_status_changed_ = Notification(false, 0);
  }
}

void Device::PlaybackPosNotificationResponse(uint8_t label, bool interim,
                                             PlayStatus status) {
  log::verbose("");

  if (interim) {
    play_pos_changed_ = Notification(true, label);
  } else if (!play_pos_changed_.first) {
    log::verbose("Device not registered for update");
    return;
  }

  if (!interim && last_play_status_.position == status.position) {
    log::warn("{}: No update to play position",
              ADDRESS_TO_LOGGABLE_STR(address_));
    return;
  }

  auto response =
      RegisterNotificationResponseBuilder::MakePlaybackPositionBuilder(
          interim, status.position);
  send_message_cb_.Run(label, false, std::move(response));

  last_play_status_.position = status.position;

  if (!interim) {
    active_labels_.erase(label);
    play_pos_changed_ = Notification(false, 0);
  }

  // We still try to send updates while music is playing to the non active
  // device even though the device thinks the music is paused. This makes
  // the status bar on the remote device move.
  if (status.state == PlayState::PLAYING && !IsInSilenceMode()) {
    log::verbose("Queue next play position update");
    play_pos_update_cb_.Reset(base::Bind(&Device::HandlePlayPosUpdate,
                                         weak_ptr_factory_.GetWeakPtr()));
    btbase::AbstractMessageLoop::current_task_runner()->PostDelayedTask(
        FROM_HERE, play_pos_update_cb_.callback(),
#if BASE_VER < 931007
        base::TimeDelta::FromSeconds(play_pos_interval_));
#else
        base::Seconds(play_pos_interval_));
#endif
  }
}

// TODO (apanicke): Finish implementing when we add support for more than one
// player
void Device::AddressedPlayerNotificationResponse(
    uint8_t label, bool interim, uint16_t curr_player,
    std::vector<MediaPlayerInfo> /* unused */) {
  log::verbose("curr_player_id={}", (unsigned int)curr_player);

  if (interim) {
    addr_player_changed_ = Notification(true, label);
  } else if (!addr_player_changed_.first) {
    log::verbose("Device not registered for update");
    return;
  }

  // If there is no set browsed player, use the current addressed player as the
  // default NOTE: Using any browsing commands before the browsed player is set
  // is a violation of the AVRCP Spec but there are some carkits that try too
  // anyways
  if (curr_browsed_player_id_ == -1) curr_browsed_player_id_ = curr_player;

  auto response =
      RegisterNotificationResponseBuilder::MakeAddressedPlayerBuilder(
          interim, curr_player, 0x0000);
  send_message_cb_.Run(label, false, std::move(response));

  if (!interim) {
    active_labels_.erase(label);
    addr_player_changed_ = Notification(false, 0);
    RejectNotification();
  }
}

void Device::RejectNotification() {
  log::verbose("");
  Notification* rejectNotification[] = {&play_status_changed_, &track_changed_,
                                        &play_pos_changed_,
                                        &now_playing_changed_};
  for (int i = 0; i < 4; i++) {
    uint8_t label = rejectNotification[i]->second;
    auto response = RejectBuilder::MakeBuilder(
        CommandPdu::REGISTER_NOTIFICATION, Status::ADDRESSED_PLAYER_CHANGED);
    send_message_cb_.Run(label, false, std::move(response));
    active_labels_.erase(label);
    rejectNotification[i] = new Notification(false, 0);
  }
}

void Device::GetPlayStatusResponse(uint8_t label, PlayStatus status) {
  log::verbose("position={} duration={} state={}", status.position,
               status.duration, status.state);
  auto response = GetPlayStatusResponseBuilder::MakeBuilder(
      status.duration, status.position,
      IsActive() ? status.state : PlayState::PAUSED);
  send_message(label, false, std::move(response));
}

void Device::GetElementAttributesResponse(
    uint8_t label, std::shared_ptr<GetElementAttributesRequest> pkt,
    SongInfo info) {
  auto get_element_attributes_pkt = pkt;
  auto attributes_requested =
      get_element_attributes_pkt->GetAttributesRequested();

  auto response = GetElementAttributesResponseBuilder::MakeBuilder(ctrl_mtu_);

  // Filter out DEFAULT_COVER_ART handle if this device has no client
  if (!HasBipClient()) {
    filter_cover_art(info);
  }

  last_song_info_ = info;

  if (attributes_requested.size() != 0) {
    for (const auto& attribute : attributes_requested) {
      if (info.attributes.find(attribute) != info.attributes.end()) {
        response->AddAttributeEntry(*info.attributes.find(attribute));
      }
    }
  } else {  // zero attributes requested which means all attributes requested
    for (const auto& attribute : info.attributes) {
      response->AddAttributeEntry(attribute);
    }
  }

  send_message(label, false, std::move(response));
}

void Device::MessageReceived(uint8_t label, std::shared_ptr<Packet> pkt) {
  if (!pkt->IsValid()) {
    log::warn("{}: Request packet is not valid",
              ADDRESS_TO_LOGGABLE_STR(address_));
    auto response = RejectBuilder::MakeBuilder(static_cast<CommandPdu>(0),
                                               Status::INVALID_COMMAND);
    send_message(label, false, std::move(response));
    return;
  }

  log::verbose("opcode={}", pkt->GetOpcode());
  active_labels_.insert(label);
  switch (pkt->GetOpcode()) {
    // TODO (apanicke): Remove handling of UNIT_INFO and SUBUNIT_INFO from
    // the AVRC_API and instead handle it here to reduce fragmentation.
    case Opcode::UNIT_INFO: {
    } break;
    case Opcode::SUBUNIT_INFO: {
    } break;
    case Opcode::PASS_THROUGH: {
      /** Newavrcp not passthrough response pkt. @{ */
      if (pkt->GetCType() == CType::ACCEPTED ||
          pkt->GetCType() == CType::REJECTED ||
          pkt->GetCType() == CType::NOT_IMPLEMENTED)
        break;
      /** @} */
      auto pass_through_packet = Packet::Specialize<PassThroughPacket>(pkt);

      if (!pass_through_packet->IsValid()) {
        log::warn("{}: Request packet is not valid",
                  ADDRESS_TO_LOGGABLE_STR(address_));
        auto response = RejectBuilder::MakeBuilder(static_cast<CommandPdu>(0),
                                                   Status::INVALID_COMMAND);
        send_message(label, false, std::move(response));
        return;
      }

      auto response = PassThroughPacketBuilder::MakeBuilder(
          true, pass_through_packet->GetKeyState() == KeyState::PUSHED,
          pass_through_packet->GetOperationId());
      send_message(label, false, std::move(response));

      // TODO (apanicke): Use an enum for media key ID's
      if (pass_through_packet->GetOperationId() == 0x44 &&
          pass_through_packet->GetKeyState() == KeyState::PUSHED) {
        // We need to get the play status since we need to know
        // what the actual playstate is without being modified
        // by whether the device is active.
        media_interface_->GetPlayStatus(base::Bind(
            [](base::WeakPtr<Device> d, PlayStatus s) {
              if (!d) return;

              if (!d->IsActive()) {
                log::info("Setting {} to be the active device",
                          ADDRESS_TO_LOGGABLE_STR(d->address_));
                d->media_interface_->SetActiveDevice(d->address_);

                if (s.state == PlayState::PLAYING) {
                  log::info(
                      "Skipping sendKeyEvent since music is already playing");
                  return;
                }
              }

              d->media_interface_->SendKeyEvent(0x44, KeyState::PUSHED);
            },
            weak_ptr_factory_.GetWeakPtr()));
        return;
      }

      if (IsActive()) {
        media_interface_->SendKeyEvent(pass_through_packet->GetOperationId(),
                                       pass_through_packet->GetKeyState());
      }
    } break;
    case Opcode::VENDOR: {
      auto vendor_pkt = Packet::Specialize<VendorPacket>(pkt);
      VendorPacketHandler(label, vendor_pkt);
    } break;
  }
}

void Device::HandlePlayItem(uint8_t label,
                            std::shared_ptr<PlayItemRequest> pkt) {
  if (!pkt->IsValid()) {
    log::warn("{}: Request packet is not valid",
              ADDRESS_TO_LOGGABLE_STR(address_));
    auto response = RejectBuilder::MakeBuilder(pkt->GetCommandPdu(),
                                               Status::INVALID_PARAMETER);
    send_message(label, false, std::move(response));
    return;
  }

  log::verbose("scope={} uid={}", pkt->GetScope(), pkt->GetUid());

  std::string media_id = "";
  switch (pkt->GetScope()) {
    case Scope::NOW_PLAYING:
      media_id = now_playing_ids_.get_media_id(pkt->GetUid());
      break;
    case Scope::VFS:
      media_id = vfs_ids_.get_media_id(pkt->GetUid());
      break;
    default:
      log::warn("{}: Unknown scope for play item",
                ADDRESS_TO_LOGGABLE_STR(address_));
  }

  if (media_id == "") {
    log::verbose("Could not find item");
    auto response = RejectBuilder::MakeBuilder(CommandPdu::PLAY_ITEM,
                                               Status::DOES_NOT_EXIST);
    send_message(label, false, std::move(response));
    return;
  }

  media_interface_->PlayItem(curr_browsed_player_id_,
                             pkt->GetScope() == Scope::NOW_PLAYING, media_id);

  auto response = PlayItemResponseBuilder::MakeBuilder(Status::NO_ERROR);
  send_message(label, false, std::move(response));
}

void Device::HandleSetAddressedPlayer(
    uint8_t label, std::shared_ptr<SetAddressedPlayerRequest> pkt,
    uint16_t curr_player, std::vector<MediaPlayerInfo> players) {
  log::verbose("PlayerId={}", pkt->GetPlayerId());

  if (curr_player != pkt->GetPlayerId()) {
    log::verbose("Reject invalid addressed player ID");
    auto response = RejectBuilder::MakeBuilder(CommandPdu::SET_ADDRESSED_PLAYER,
                                               Status::INVALID_PLAYER_ID);
    send_message(label, false, std::move(response));
    return;
  }

  auto response =
      SetAddressedPlayerResponseBuilder::MakeBuilder(Status::NO_ERROR);
  send_message(label, false, std::move(response));
}

void Device::ListPlayerApplicationSettingAttributesResponse(
    uint8_t label, std::vector<PlayerAttribute> attributes) {
  uint8_t num_of_attributes = attributes.size();
  log::verbose("num_of_attributes={}", num_of_attributes);
  if (num_of_attributes > 0) {
    for (auto attribute : attributes) {
      log::verbose("attribute={}", attribute);
    }
  }
  auto response =
      ListPlayerApplicationSettingAttributesResponseBuilder::MakeBuilder(
          std::move(attributes));
  send_message(label, false, std::move(response));
}

void Device::ListPlayerApplicationSettingValuesResponse(
    uint8_t label, PlayerAttribute attribute, std::vector<uint8_t> values) {
  uint8_t number_of_values = values.size();
  log::verbose("attribute={}, number_of_values={}", attribute,
               number_of_values);

  if (number_of_values > 0) {
    if (attribute == PlayerAttribute::REPEAT) {
      for (auto value : values) {
        log::verbose("value={}", static_cast<PlayerRepeatValue>(value));
      }
    } else if (attribute == PlayerAttribute::SHUFFLE) {
      for (auto value : values) {
        log::verbose("value={}", static_cast<PlayerShuffleValue>(value));
      }
    } else {
      log::verbose("value={}", loghex(values.at(0)));
    }
  }

  auto response =
      ListPlayerApplicationSettingValuesResponseBuilder::MakeBuilder(
          std::move(values));
  send_message(label, false, std::move(response));
}

void Device::GetPlayerApplicationSettingValueResponse(
    uint8_t label, std::vector<PlayerAttribute> attributes,
    std::vector<uint8_t> values) {
  for (size_t i = 0; i < attributes.size(); i++) {
    log::verbose("attribute={}", static_cast<PlayerAttribute>(attributes[i]));
    if (attributes[i] == PlayerAttribute::REPEAT) {
      log::verbose("value={}", static_cast<PlayerRepeatValue>(values[i]));
    } else if (attributes[i] == PlayerAttribute::SHUFFLE) {
      log::verbose("value={}", static_cast<PlayerShuffleValue>(values[i]));
    } else {
      log::verbose("value={}", loghex(values.at(0)));
    }
  }

  auto response =
      GetCurrentPlayerApplicationSettingValueResponseBuilder::MakeBuilder(
          std::move(attributes), std::move(values));
  send_message(label, false, std::move(response));
}

void Device::SetPlayerApplicationSettingValueResponse(uint8_t label,
                                                      CommandPdu pdu,
                                                      bool success) {
  if (!success) {
    log::error("{}: Set Player Application Setting Value failed",
               ADDRESS_TO_LOGGABLE_STR(address_));
    auto response = RejectBuilder::MakeBuilder(pdu, Status::INVALID_PARAMETER);
    send_message(label, false, std::move(response));
    return;
  }

  auto response =
      SetPlayerApplicationSettingValueResponseBuilder::MakeBuilder();
  send_message(label, false, std::move(response));
}

void Device::BrowseMessageReceived(uint8_t label,
                                   std::shared_ptr<BrowsePacket> pkt) {
  if (!pkt->IsValid()) {
    log::warn("{}: Request packet is not valid",
              ADDRESS_TO_LOGGABLE_STR(address_));
    auto response = GeneralRejectBuilder::MakeBuilder(Status::INVALID_COMMAND);
    send_message(label, false, std::move(response));
    return;
  }

  log::verbose("pdu={}", pkt->GetPdu());

  switch (pkt->GetPdu()) {
    case BrowsePdu::SET_BROWSED_PLAYER:
      HandleSetBrowsedPlayer(label,
                             Packet::Specialize<SetBrowsedPlayerRequest>(pkt));
      break;
    case BrowsePdu::GET_FOLDER_ITEMS:
      HandleGetFolderItems(label,
                           Packet::Specialize<GetFolderItemsRequest>(pkt));
      break;
    case BrowsePdu::CHANGE_PATH:
      HandleChangePath(label, Packet::Specialize<ChangePathRequest>(pkt));
      break;
    case BrowsePdu::GET_ITEM_ATTRIBUTES:
      HandleGetItemAttributes(
          label, Packet::Specialize<GetItemAttributesRequest>(pkt));
      break;
    case BrowsePdu::GET_TOTAL_NUMBER_OF_ITEMS:
      HandleGetTotalNumberOfItems(
          label, Packet::Specialize<GetTotalNumberOfItemsRequest>(pkt));
      break;
    default:
      log::warn("{}: pdu={}", ADDRESS_TO_LOGGABLE_STR(address_), pkt->GetPdu());
      auto response =
          GeneralRejectBuilder::MakeBuilder(Status::INVALID_COMMAND);
      send_message(label, true, std::move(response));

      break;
  }
}

void Device::HandleGetFolderItems(uint8_t label,
                                  std::shared_ptr<GetFolderItemsRequest> pkt) {
  if (!pkt->IsValid()) {
    // The specific get folder items builder is unimportant on failure.
    log::warn("{}: Get folder items request packet is not valid",
              ADDRESS_TO_LOGGABLE_STR(address_));
    auto response = GetFolderItemsResponseBuilder::MakePlayerListBuilder(
        Status::INVALID_PARAMETER, 0x0000, browse_mtu_);
    send_message(label, true, std::move(response));
    return;
  }

  log::verbose("scope={}", pkt->GetScope());

  switch (pkt->GetScope()) {
    case Scope::MEDIA_PLAYER_LIST:
      media_interface_->GetMediaPlayerList(
          base::Bind(&Device::GetMediaPlayerListResponse,
                     weak_ptr_factory_.GetWeakPtr(), label, pkt));
      break;
    case Scope::VFS:
      media_interface_->GetFolderItems(
          curr_browsed_player_id_, CurrentFolder(),
          base::Bind(&Device::GetVFSListResponse,
                     weak_ptr_factory_.GetWeakPtr(), label, pkt));
      break;
    case Scope::NOW_PLAYING:
      media_interface_->GetNowPlayingList(
          base::Bind(&Device::GetNowPlayingListResponse,
                     weak_ptr_factory_.GetWeakPtr(), label, pkt));
      break;
    default:
      log::error("{}: scope={}", ADDRESS_TO_LOGGABLE_STR(address_),
                 pkt->GetScope());
      auto response = GetFolderItemsResponseBuilder::MakePlayerListBuilder(
          Status::INVALID_PARAMETER, 0, browse_mtu_);
      send_message(label, true, std::move(response));
      break;
  }
}

void Device::HandleGetTotalNumberOfItems(
    uint8_t label, std::shared_ptr<GetTotalNumberOfItemsRequest> pkt) {
  if (!pkt->IsValid()) {
    log::warn("{}: Request packet is not valid",
              ADDRESS_TO_LOGGABLE_STR(address_));
    auto response = GetTotalNumberOfItemsResponseBuilder::MakeBuilder(
        Status::INVALID_PARAMETER, 0x0000, 0);
    send_message(label, true, std::move(response));
    return;
  }

  log::verbose("scope={}", pkt->GetScope());

  switch (pkt->GetScope()) {
    case Scope::MEDIA_PLAYER_LIST: {
      media_interface_->GetMediaPlayerList(
          base::Bind(&Device::GetTotalNumberOfItemsMediaPlayersResponse,
                     weak_ptr_factory_.GetWeakPtr(), label));
      break;
    }
    case Scope::VFS:
      media_interface_->GetFolderItems(
          curr_browsed_player_id_, CurrentFolder(),
          base::Bind(&Device::GetTotalNumberOfItemsVFSResponse,
                     weak_ptr_factory_.GetWeakPtr(), label));
      break;
    case Scope::NOW_PLAYING:
      media_interface_->GetNowPlayingList(
          base::Bind(&Device::GetTotalNumberOfItemsNowPlayingResponse,
                     weak_ptr_factory_.GetWeakPtr(), label));
      break;
    default:
      log::error("{}: scope={}", ADDRESS_TO_LOGGABLE_STR(address_),
                 pkt->GetScope());
      break;
  }
}

void Device::GetTotalNumberOfItemsMediaPlayersResponse(
    uint8_t label, uint16_t curr_player, std::vector<MediaPlayerInfo> list) {
  log::verbose("num_items={}", list.size());

  auto builder = GetTotalNumberOfItemsResponseBuilder::MakeBuilder(
      Status::NO_ERROR, 0x0000, list.size());
  send_message(label, true, std::move(builder));
}

void Device::GetTotalNumberOfItemsVFSResponse(uint8_t label,
                                              std::vector<ListItem> list) {
  log::verbose("num_items={}", list.size());

  auto builder = GetTotalNumberOfItemsResponseBuilder::MakeBuilder(
      Status::NO_ERROR, 0x0000, list.size());
  send_message(label, true, std::move(builder));
}

void Device::GetTotalNumberOfItemsNowPlayingResponse(
    uint8_t label, std::string curr_song_id, std::vector<SongInfo> list) {
  log::verbose("num_items={}", list.size());

  auto builder = GetTotalNumberOfItemsResponseBuilder::MakeBuilder(
      Status::NO_ERROR, 0x0000, list.size());
  send_message(label, true, std::move(builder));
}

void Device::HandleChangePath(uint8_t label,
                              std::shared_ptr<ChangePathRequest> pkt) {
  if (!pkt->IsValid()) {
    log::warn("{}: Request packet is not valid",
              ADDRESS_TO_LOGGABLE_STR(address_));
    auto response =
        ChangePathResponseBuilder::MakeBuilder(Status::INVALID_PARAMETER, 0);
    send_message(label, true, std::move(response));
    return;
  }

  log::verbose("direction={} uid={}", pkt->GetDirection(),
               loghex(pkt->GetUid()));

  if (pkt->GetDirection() == Direction::DOWN &&
      vfs_ids_.get_media_id(pkt->GetUid()) == "") {
    log::error("{}: No item found for UID={}",
               ADDRESS_TO_LOGGABLE_STR(address_), pkt->GetUid());
    auto builder =
        ChangePathResponseBuilder::MakeBuilder(Status::DOES_NOT_EXIST, 0);
    send_message(label, true, std::move(builder));
    return;
  }

  if (pkt->GetDirection() == Direction::DOWN) {
    current_path_.push(vfs_ids_.get_media_id(pkt->GetUid()));
    log::verbose("Pushing Path to stack: \"{}\"", CurrentFolder());
  } else {
    // Don't pop the root id off the stack
    if (current_path_.size() > 1) {
      current_path_.pop();
    } else {
      log::error("{}: Trying to change directory up past root.",
                 ADDRESS_TO_LOGGABLE_STR(address_));
      auto builder =
          ChangePathResponseBuilder::MakeBuilder(Status::DOES_NOT_EXIST, 0);
      send_message(label, true, std::move(builder));
      return;
    }

    log::verbose("Popping Path from stack: new path=\"{}\"", CurrentFolder());
  }

  media_interface_->GetFolderItems(
      curr_browsed_player_id_, CurrentFolder(),
      base::Bind(&Device::ChangePathResponse, weak_ptr_factory_.GetWeakPtr(),
                 label, pkt));
}

void Device::ChangePathResponse(uint8_t label,
                                std::shared_ptr<ChangePathRequest> pkt,
                                std::vector<ListItem> list) {
  // TODO (apanicke): Reconstruct the VFS ID's here. Right now it gets
  // reconstructed in GetFolderItemsVFS
  auto builder =
      ChangePathResponseBuilder::MakeBuilder(Status::NO_ERROR, list.size());
  send_message(label, true, std::move(builder));
}

void Device::HandleGetItemAttributes(
    uint8_t label, std::shared_ptr<GetItemAttributesRequest> pkt) {
  if (!pkt->IsValid()) {
    log::warn("{}: Request packet is not valid",
              ADDRESS_TO_LOGGABLE_STR(address_));
    auto builder = GetItemAttributesResponseBuilder::MakeBuilder(
        Status::INVALID_PARAMETER, browse_mtu_);
    send_message(label, true, std::move(builder));
    return;
  }

  log::verbose("scope={} uid={} uid counter={}", pkt->GetScope(),
               loghex(pkt->GetUid()), loghex(pkt->GetUidCounter()));
  if (pkt->GetUidCounter() != 0x0000) {  // For database unaware player, use 0
    log::warn("{}: UidCounter is invalid", ADDRESS_TO_LOGGABLE_STR(address_));
    auto builder = GetItemAttributesResponseBuilder::MakeBuilder(
        Status::UIDS_CHANGED, browse_mtu_);
    send_message(label, true, std::move(builder));
    return;
  }

  switch (pkt->GetScope()) {
    case Scope::NOW_PLAYING: {
      media_interface_->GetNowPlayingList(
          base::Bind(&Device::GetItemAttributesNowPlayingResponse,
                     weak_ptr_factory_.GetWeakPtr(), label, pkt));
    } break;
    case Scope::VFS:
      // TODO (apanicke): Check the vfs_ids_ here. If the item doesn't exist
      // then we can auto send the error without calling up. We do this check
      // later right now though in order to prevent race conditions with updates
      // on the media layer.
      media_interface_->GetFolderItems(
          curr_browsed_player_id_, CurrentFolder(),
          base::Bind(&Device::GetItemAttributesVFSResponse,
                     weak_ptr_factory_.GetWeakPtr(), label, pkt));
      break;
    default:
      log::error("{}: UNKNOWN SCOPE FOR HANDLE GET ITEM ATTRIBUTES",
                 ADDRESS_TO_LOGGABLE_STR(address_));
      break;
  }
}

void Device::GetItemAttributesNowPlayingResponse(
    uint8_t label, std::shared_ptr<GetItemAttributesRequest> pkt,
    std::string curr_media_id, std::vector<SongInfo> song_list) {
  log::verbose("uid={}", loghex(pkt->GetUid()));
  auto builder = GetItemAttributesResponseBuilder::MakeBuilder(Status::NO_ERROR,
                                                               browse_mtu_);

  auto media_id = now_playing_ids_.get_media_id(pkt->GetUid());
  if (media_id == "") {
    media_id = curr_media_id;
  }

  log::verbose("media_id=\"{}\"", media_id);

  SongInfo info;
  if (song_list.size() == 1) {
    log::verbose("Send out the only song in the queue as now playing song.");
    info = song_list.front();
  } else {
    for (const auto& temp : song_list) {
      if (temp.media_id == media_id) {
        info = temp;
      }
    }
  }

  // Filter out DEFAULT_COVER_ART handle if this device has no client
  if (!HasBipClient()) {
    filter_cover_art(info);
  }

  auto attributes_requested = pkt->GetAttributesRequested();
  if (attributes_requested.size() != 0) {
    for (const auto& attribute : attributes_requested) {
      if (info.attributes.find(attribute) != info.attributes.end()) {
        builder->AddAttributeEntry(*info.attributes.find(attribute));
      }
    }
  } else {
    // If zero attributes were requested, that means all attributes were
    // requested
    for (const auto& attribute : info.attributes) {
      builder->AddAttributeEntry(attribute);
    }
  }

  send_message(label, true, std::move(builder));
}

void Device::GetItemAttributesVFSResponse(
    uint8_t label, std::shared_ptr<GetItemAttributesRequest> pkt,
    std::vector<ListItem> item_list) {
  log::verbose("uid={}", loghex(pkt->GetUid()));

  auto media_id = vfs_ids_.get_media_id(pkt->GetUid());
  if (media_id == "") {
    log::warn("Item not found");
    auto builder = GetItemAttributesResponseBuilder::MakeBuilder(
        Status::DOES_NOT_EXIST, browse_mtu_);
    send_message(label, true, std::move(builder));
    return;
  }

  auto builder = GetItemAttributesResponseBuilder::MakeBuilder(Status::NO_ERROR,
                                                               browse_mtu_);

  ListItem item_requested;
  item_requested.type = ListItem::SONG;

  for (const auto& temp : item_list) {
    if ((temp.type == ListItem::FOLDER && temp.folder.media_id == media_id) ||
        (temp.type == ListItem::SONG && temp.song.media_id == media_id)) {
      item_requested = temp;
    }
  }

  // Filter out DEFAULT_COVER_ART handle if this device has no client
  if (item_requested.type == ListItem::SONG && !HasBipClient()) {
    filter_cover_art(item_requested.song);
  }

  // TODO (apanicke): Add a helper function or allow adding a map
  // of attributes to GetItemAttributesResponseBuilder
  auto attributes_requested = pkt->GetAttributesRequested();
  if (item_requested.type == ListItem::FOLDER) {
    if (attributes_requested.size() == 0) {
      builder->AddAttributeEntry(Attribute::TITLE, item_requested.folder.name);
    } else {
      for (auto& attr : attributes_requested) {
        if (attr == Attribute::TITLE) {
          builder->AddAttributeEntry(Attribute::TITLE,
                                     item_requested.folder.name);
        }
      }
    }
  } else {
    if (attributes_requested.size() != 0) {
      for (const auto& attribute : attributes_requested) {
        if (item_requested.song.attributes.find(attribute) !=
            item_requested.song.attributes.end()) {
          builder->AddAttributeEntry(
              *item_requested.song.attributes.find(attribute));
        }
      }
    } else {
      // If zero attributes were requested, that means all attributes were
      // requested
      for (const auto& attribute : item_requested.song.attributes) {
        builder->AddAttributeEntry(attribute);
      }
    }
  }

  send_message(label, true, std::move(builder));
}

void Device::GetMediaPlayerListResponse(
    uint8_t label, std::shared_ptr<GetFolderItemsRequest> pkt,
    uint16_t curr_player, std::vector<MediaPlayerInfo> players) {
  log::verbose("");

  if (players.size() == 0) {
    auto no_items_rsp = GetFolderItemsResponseBuilder::MakePlayerListBuilder(
        Status::RANGE_OUT_OF_BOUNDS, 0x0000, browse_mtu_);
    send_message(label, true, std::move(no_items_rsp));
  }

  auto builder = GetFolderItemsResponseBuilder::MakePlayerListBuilder(
      Status::NO_ERROR, 0x0000, browse_mtu_);

  // Move the current player to the first slot due to some carkits always
  // connecting to the first listed player rather than using the ID
  // returned by Addressed Player Changed
  for (auto it = players.begin(); it != players.end(); it++) {
    if (it->id == curr_player) {
      log::verbose(" Adding player to first spot: {}", it->name);
      auto temp_player = *it;
      players.erase(it);
      players.insert(players.begin(), temp_player);
      break;
    }
  }

  for (size_t i = pkt->GetStartItem();
       i <= pkt->GetEndItem() && i < players.size(); i++) {
    MediaPlayerItem item(players[i].id, players[i].name,
                         players[i].browsing_supported);
    builder->AddMediaPlayer(item);
  }

  send_message(label, true, std::move(builder));
}

std::set<AttributeEntry> filter_attributes_requested(
    const SongInfo& song, const std::vector<Attribute>& attrs) {
  std::set<AttributeEntry> result;
  for (const auto& attr : attrs) {
    if (song.attributes.find(attr) != song.attributes.end()) {
      result.insert(*song.attributes.find(attr));
    }
  }

  return result;
}

void Device::GetVFSListResponse(uint8_t label,
                                std::shared_ptr<GetFolderItemsRequest> pkt,
                                std::vector<ListItem> items) {
  log::verbose("start_item={} end_item={}", pkt->GetStartItem(),
               pkt->GetEndItem());

  // The builder will automatically correct the status if there are zero items
  auto builder = GetFolderItemsResponseBuilder::MakeVFSBuilder(
      Status::NO_ERROR, 0x0000, browse_mtu_);

  // TODO (apanicke): Add test that checks if vfs_ids_ is the correct size after
  // an operation.
  for (const auto& item : items) {
    if (item.type == ListItem::FOLDER) {
      vfs_ids_.insert(item.folder.media_id);
    } else if (item.type == ListItem::SONG) {
      vfs_ids_.insert(item.song.media_id);
    }
  }

  // Add the elements retrieved in the last get folder items request and map
  // them to UIDs The maps will be cleared every time a directory change
  // happens. These items do not need to correspond with the now playing list as
  // the UID's only need to be unique in the context of the current scope and
  // the current folder
  for (auto i = pkt->GetStartItem(); i <= pkt->GetEndItem() && i < items.size();
       i++) {
    if (items[i].type == ListItem::FOLDER) {
      auto folder = items[i].folder;
      // right now we always use folders of mixed type
      FolderItem folder_item(vfs_ids_.get_uid(folder.media_id), 0x00,
                             folder.is_playable, folder.name);
      if (!builder->AddFolder(folder_item)) break;
    } else if (items[i].type == ListItem::SONG) {
      auto song = items[i].song;

      // Filter out DEFAULT_COVER_ART handle if this device has no client
      if (!HasBipClient()) {
        filter_cover_art(song);
      }

      auto title =
          song.attributes.find(Attribute::TITLE) != song.attributes.end()
              ? song.attributes.find(Attribute::TITLE)->value()
              : "No Song Info";
      MediaElementItem song_item(vfs_ids_.get_uid(song.media_id), title,
                                 std::set<AttributeEntry>());

      if (pkt->GetNumAttributes() == 0x00) {  // All attributes requested
        song_item.attributes_ = std::move(song.attributes);
      } else {
        song_item.attributes_ =
            filter_attributes_requested(song, pkt->GetAttributesRequested());
      }

      // If we fail to add a song, don't accidentally add one later that might
      // fit.
      if (!builder->AddSong(song_item)) break;
    }
  }

  send_message(label, true, std::move(builder));
}

void Device::GetNowPlayingListResponse(
    uint8_t label, std::shared_ptr<GetFolderItemsRequest> pkt,
    std::string /* unused curr_song_id */, std::vector<SongInfo> song_list) {
  log::verbose("");
  auto builder = GetFolderItemsResponseBuilder::MakeNowPlayingBuilder(
      Status::NO_ERROR, 0x0000, browse_mtu_);

  now_playing_ids_.clear();
  for (const SongInfo& song : song_list) {
    now_playing_ids_.insert(song.media_id);
  }

  for (size_t i = pkt->GetStartItem();
       i <= pkt->GetEndItem() && i < song_list.size(); i++) {
    auto song = song_list[i];

    // Filter out DEFAULT_COVER_ART handle if this device has no client
    if (!HasBipClient()) {
      filter_cover_art(song);
    }

    auto title = song.attributes.find(Attribute::TITLE) != song.attributes.end()
                     ? song.attributes.find(Attribute::TITLE)->value()
                     : "No Song Info";

    MediaElementItem item(i + 1, title, std::set<AttributeEntry>());
    if (pkt->GetNumAttributes() == 0x00) {
      item.attributes_ = std::move(song.attributes);
    } else {
      item.attributes_ =
          filter_attributes_requested(song, pkt->GetAttributesRequested());
    }

    // If we fail to add a song, don't accidentally add one later that might
    // fit.
    if (!builder->AddSong(item)) break;
  }

  send_message(label, true, std::move(builder));
}

void Device::HandleSetBrowsedPlayer(
    uint8_t label, std::shared_ptr<SetBrowsedPlayerRequest> pkt) {
  if (!pkt->IsValid()) {
    log::warn("{}: Request packet is not valid",
              ADDRESS_TO_LOGGABLE_STR(address_));
    auto response = SetBrowsedPlayerResponseBuilder::MakeBuilder(
        Status::INVALID_PARAMETER, 0x0000, 0, 0, "");
    send_message(label, true, std::move(response));
    return;
  }

  log::verbose("player_id={}", pkt->GetPlayerId());
  media_interface_->SetBrowsedPlayer(
      pkt->GetPlayerId(),
      base::Bind(&Device::SetBrowsedPlayerResponse,
                 weak_ptr_factory_.GetWeakPtr(), label, pkt));
}

void Device::SetBrowsedPlayerResponse(
    uint8_t label, std::shared_ptr<SetBrowsedPlayerRequest> pkt, bool success,
    std::string root_id, uint32_t num_items) {
  log::verbose("success={} root_id=\"{}\" num_items={}", success, root_id,
               num_items);

  if (!success) {
    auto response = SetBrowsedPlayerResponseBuilder::MakeBuilder(
        Status::INVALID_PLAYER_ID, 0x0000, num_items, 0, "");
    send_message(label, true, std::move(response));
    return;
  }

  if (pkt->GetPlayerId() == 0 && num_items == 0) {
    // Response fail if no browsable player in Bluetooth Player
    auto response = SetBrowsedPlayerResponseBuilder::MakeBuilder(
        Status::PLAYER_NOT_BROWSABLE, 0x0000, num_items, 0, "");
    send_message(label, true, std::move(response));
    return;
  }

  curr_browsed_player_id_ = pkt->GetPlayerId();

  // Clear the path and push the new root.
  current_path_ = std::stack<std::string>();
  current_path_.push(root_id);

  auto response = SetBrowsedPlayerResponseBuilder::MakeBuilder(
      Status::NO_ERROR, 0x0000, num_items, 0, "");
  send_message(label, true, std::move(response));
}

void Device::SendMediaUpdate(bool metadata, bool play_status, bool queue) {
  bool is_silence = IsInSilenceMode();

  CHECK(media_interface_);
  log::verbose("Metadata={} : play_status= {} : queue={} : is_silence={}",
               metadata, play_status, queue, is_silence);

  if (queue) {
    HandleNowPlayingUpdate();
  }

  if (play_status) {
    HandlePlayStatusUpdate();
    if (!is_silence) {
      HandlePlayPosUpdate();
    }
  }

  if (metadata) HandleTrackUpdate();
}

void Device::SendFolderUpdate(bool available_players, bool addressed_player,
                              bool uids) {
  CHECK(media_interface_);
  log::verbose("");

  if (available_players) {
    HandleAvailablePlayerUpdate();
  }

  if (addressed_player) {
    HandleAddressedPlayerUpdate();
  }
}

void Device::HandleTrackUpdate() {
  log::verbose("");
  if (!track_changed_.first) {
    log::warn("Device is not registered for track changed updates");
    return;
  }

  media_interface_->GetNowPlayingList(
      base::Bind(&Device::TrackChangedNotificationResponse,
                 weak_ptr_factory_.GetWeakPtr(), track_changed_.second, false));
}

void Device::HandlePlayStatusUpdate() {
  log::verbose("");
  if (!play_status_changed_.first) {
    log::warn("Device is not registered for play status updates");
    return;
  }

  media_interface_->GetPlayStatus(base::Bind(
      &Device::PlaybackStatusNotificationResponse,
      weak_ptr_factory_.GetWeakPtr(), play_status_changed_.second, false));
}

void Device::HandleNowPlayingUpdate() {
  log::verbose("");

  if (!now_playing_changed_.first) {
    log::warn("Device is not registered for now playing updates");
    return;
  }

  media_interface_->GetNowPlayingList(base::Bind(
      &Device::HandleNowPlayingNotificationResponse,
      weak_ptr_factory_.GetWeakPtr(), now_playing_changed_.second, false));
}

void Device::HandlePlayerSettingChanged(std::vector<PlayerAttribute> attributes,
                                        std::vector<uint8_t> values) {
  log::verbose("");
  if (!player_setting_changed_.first) {
    log::warn("Device is not registered for player settings updates");
    return;
  }

  for (size_t i = 0; i < attributes.size(); i++) {
    log::verbose(" attribute: {}", attributes[i]);
    if (attributes[i] == PlayerAttribute::SHUFFLE) {
      log::verbose(" value: {}", (PlayerShuffleValue)values[i]);
    } else if (attributes[i] == PlayerAttribute::REPEAT) {
      log::verbose(" value: {}", (PlayerRepeatValue)values[i]);
    } else {
      log::verbose(" value: {}", values[i]);
    }
  }

  auto response =
      RegisterNotificationResponseBuilder::MakePlayerSettingChangedBuilder(
          false, attributes, values);
  send_message(player_setting_changed_.second, false, std::move(response));
}

void Device::PlayerSettingChangedNotificationResponse(
    uint8_t label, bool interim, std::vector<PlayerAttribute> attributes,
    std::vector<uint8_t> values) {
  log::verbose("interim: {}", interim);
  for (size_t i = 0; i < attributes.size(); i++) {
    log::verbose(" attribute: {}", attributes[i]);
    if (attributes[i] == PlayerAttribute::SHUFFLE) {
      log::verbose(" value: {}", (PlayerShuffleValue)values[i]);
    } else if (attributes[i] == PlayerAttribute::REPEAT) {
      log::verbose(" value: {}", (PlayerRepeatValue)values[i]);
    } else {
      log::verbose(" value: {}", values[i]);
    }
  }

  if (interim) {
    player_setting_changed_ = Notification(true, label);
  } else if (!player_setting_changed_.first) {
    log::warn("Device is not registered for now playing updates");
    return;
  }

  auto response =
      RegisterNotificationResponseBuilder::MakePlayerSettingChangedBuilder(
          interim, attributes, values);
  send_message(player_setting_changed_.second, false, std::move(response));

  if (!interim) {
    active_labels_.erase(label);
    player_setting_changed_ = Notification(false, 0);
  }
}

void Device::HandleNowPlayingNotificationResponse(
    uint8_t label, bool interim, std::string curr_song_id,
    std::vector<SongInfo> song_list) {
  if (interim) {
    now_playing_changed_ = Notification(true, label);
  } else if (!now_playing_changed_.first) {
    log::warn("Device is not registered for now playing updates");
    return;
  }

  now_playing_ids_.clear();
  for (const SongInfo& song : song_list) {
    now_playing_ids_.insert(song.media_id);
  }

  auto response =
      RegisterNotificationResponseBuilder::MakeNowPlayingBuilder(interim);
  send_message(now_playing_changed_.second, false, std::move(response));

  if (!interim) {
    active_labels_.erase(label);
    now_playing_changed_ = Notification(false, 0);
  }
}

void Device::HandlePlayPosUpdate() {
  log::verbose("");
  if (!play_pos_changed_.first) {
    log::warn("Device is not registered for play position updates");
    return;
  }

  media_interface_->GetPlayStatus(base::Bind(
      &Device::PlaybackPosNotificationResponse, weak_ptr_factory_.GetWeakPtr(),
      play_pos_changed_.second, false));
}

void Device::HandleAvailablePlayerUpdate() {
  log::verbose("");

  if (!avail_players_changed_.first) {
    log::warn("Device is not registered for available player updates");
    return;
  }

  auto response =
      RegisterNotificationResponseBuilder::MakeAvailablePlayersBuilder(false);
  send_message_cb_.Run(avail_players_changed_.second, false,
                       std::move(response));

  if (!avail_players_changed_.first) {
    active_labels_.erase(avail_players_changed_.second);
    avail_players_changed_ = Notification(false, 0);
  }
}

void Device::HandleAddressedPlayerUpdate() {
  log::verbose("");
  if (!addr_player_changed_.first) {
    log::warn("{}: Device is not registered for addressed player updates",
              ADDRESS_TO_LOGGABLE_STR(address_));
    return;
  }
  media_interface_->GetMediaPlayerList(base::Bind(
      &Device::AddressedPlayerNotificationResponse,
      weak_ptr_factory_.GetWeakPtr(), addr_player_changed_.second, false));
}

void Device::DeviceDisconnected() {
  log::info("{} : Device was disconnected", ADDRESS_TO_LOGGABLE_STR(address_));
  play_pos_update_cb_.Cancel();

  // TODO (apanicke): Once the interfaces are set in the Device construction,
  // remove these conditionals.
  if (volume_interface_ != nullptr)
    volume_interface_->DeviceDisconnected(GetAddress());
  // The volume at connection is set by the remote device when indicating
  // that it supports absolute volume, in case it's not, we need
  // to reset the local volume var to be sure we send the correct value
  // to the remote device on the next connection.
  volume_ = VOL_NOT_SUPPORTED;
}

static std::string volumeToStr(int8_t volume) {
  if (volume == VOL_NOT_SUPPORTED) return "Absolute Volume not supported";
  if (volume == VOL_REGISTRATION_FAILED)
    return "Volume changed notification was rejected";
  return std::to_string(volume);
}

std::ostream& operator<<(std::ostream& out, const Device& d) {
  // TODO: whether this should be turned into LOGGABLE STRING?
  out << "  " << ADDRESS_TO_LOGGABLE_STR(d.address_);
  if (d.IsActive()) out << " <Active>";
  out << std::endl;
  out << "    Current Volume: " << volumeToStr(d.volume_) << std::endl;
  out << "    Current Browsed Player ID: " << d.curr_browsed_player_id_
      << std::endl;
  out << "    Registered Notifications:\n";
  if (d.track_changed_.first) out << "        Track Changed\n";
  if (d.play_status_changed_.first) out << "        Play Status\n";
  if (d.play_pos_changed_.first) out << "        Play Position\n";
  if (d.player_setting_changed_.first) out << "        Player Setting Changed\n";
  if (d.now_playing_changed_.first) out << "        Now Playing\n";
  if (d.addr_player_changed_.first) out << "        Addressed Player\n";
  if (d.avail_players_changed_.first) out << "        Available Players\n";
  if (d.uids_changed_.first) out << "        UIDs Changed\n";
  out << "    Last Play State: " << d.last_play_status_.state << std::endl;
  out << "    Last Song Sent ID: \"" << d.last_song_info_.media_id << "\"\n";
  out << "    Current Folder: \"" << d.CurrentFolder() << "\"\n";
  out << "    MTU Sizes: CTRL=" << d.ctrl_mtu_ << " BROWSE=" << d.browse_mtu_
      << std::endl;
  // TODO (apanicke): Add supported features as well as media keys
  return out;
}

}  // namespace avrcp
}  // namespace bluetooth
