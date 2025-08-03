/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include "rust/topshim/btav/btav_shim.h"

#include <cstdio>
#include <memory>

#include "base/functional/callback.h"
#include "include/hardware/avrcp/avrcp.h"
#include "include/hardware/bluetooth.h"
#include "rust/cxx.h"
#include "src/profiles/a2dp.rs.h"
#include "src/profiles/avrcp.rs.h"
#include "types/raw_address.h"

namespace rusty = ::bluetooth::topshim::rust;

namespace bluetooth::avrcp {
class AvrcpMediaInterfaceImpl : public MediaInterface {
 public:
  void SendKeyEvent(uint8_t key, KeyState state) {
    rusty::avrcp_send_key_event(key, state == KeyState::PUSHED);
  }

  void GetSongInfo(SongInfoCallback cb) override {
    cb.Run(songInfo_);
  }

  void GetPlayStatus(PlayStatusCallback cb) override {
    cb.Run(playStatus_);
  }

  void GetNowPlayingList(NowPlayingCallback cb) override {
    cb.Run(currentSongId_, nowPlayingList_);
  }

  void GetMediaPlayerList(MediaListCallback cb) override {
    cb.Run(currentPlayer_, playerList_);
  }

  void GetFolderItems(
      [[maybe_unused]] uint16_t player_id,
      [[maybe_unused]] std::string media_id,
      [[maybe_unused]] FolderItemsCallback folder_cb) override {}

  void SetBrowsedPlayer(
      [[maybe_unused]] uint16_t player_id, [[maybe_unused]] SetBrowsedPlayerCallback browse_cb) override {}

  void RegisterUpdateCallback(MediaCallbacks* callback) override {
    mediaCb_ = callback;
  }

  void UnregisterUpdateCallback([[maybe_unused]] MediaCallbacks* callback) override {
    mediaCb_ = nullptr;
  }

  void PlayItem(
      [[maybe_unused]] uint16_t player_id,
      [[maybe_unused]] bool now_playing,
      [[maybe_unused]] std::string media_id) override {}

  void SetActiveDevice(const RawAddress& addr) override {
    rusty::avrcp_set_active_device(addr);
  }

  void SetPlaybackStatus(const PlayState& state) {
    playStatus_.state = state;
    if (mediaCb_) mediaCb_->SendMediaUpdate(/*track_changed*/ false, /*play_state*/ true, /*queuefalse*/ false);
  }

  void SetPosition(int64_t position_us) {
    // Unit conversion from microsecond to millisecond.
    playStatus_.position = position_us / 1000;
    if (mediaCb_) mediaCb_->SendMediaUpdate(/*track_changed*/ false, /*play_state*/ true, /*queuefalse*/ false);
  }

  void SetMetadata(const std::string& title, const std::string& artist, const std::string& album, int64_t length_us) {
    if (title.length() || artist.length() || album.length()) {
      songInfo_.attributes.clear();
      // Reuse title for media_id, ideally this should be a shorter UID.
      songInfo_.media_id = title;
      if (title.length()) songInfo_.attributes.emplace(avrcp::AttributeEntry(avrcp::Attribute::TITLE, title));
      if (artist.length()) songInfo_.attributes.emplace(avrcp::AttributeEntry(avrcp::Attribute::ARTIST_NAME, artist));
      if (album.length()) songInfo_.attributes.emplace(avrcp::AttributeEntry(avrcp::Attribute::ALBUM_NAME, album));
      // Floss's media implementation does not fully support the "Now Playing List," as Floss does not receive
      // additional media information other than the current playing one. However, not all peripherals request metadata
      // through the "Get Element Attributes" request. Instead, many get such information through the "Track Changed
      // Notification." Hence, fill the playlist with one item here and have the Current Song ID always point to the
      // only entry to support the track changed notification.
      nowPlayingList_.clear();
      nowPlayingList_.emplace_back(songInfo_);
      currentSongId_ = songInfo_.media_id;
      if (mediaCb_) mediaCb_->SendMediaUpdate(/*track_changed*/ true, /*play_state*/ false, /*queuefalse*/ false);
    }

    if (length_us) {
      // Unit conversion from microsecond to millisecond.
      playStatus_.duration = length_us / 1000;
      if (mediaCb_) mediaCb_->SendMediaUpdate(/*track_changed*/ false, /*play_state*/ true, /*queuefalse*/ false);
    }
  }

  uint16_t AddPlayer(std::string name, bool browsing_supported) {
    playerList_.push_back(MediaPlayerInfo{player_id_, name, browsing_supported});

    if (mediaCb_)
      mediaCb_->SendFolderUpdate(
          /*available_players*/ true, /*addressed_players*/ true, /*uids_changed*/ false);

    return player_id_++;
  }

 private:
  MediaCallbacks* mediaCb_;

  PlayStatus playStatus_;
  SongInfo songInfo_;
  std::string currentSongId_;
  std::vector<MediaPlayerInfo> playerList_;
  std::vector<SongInfo> nowPlayingList_;
  uint16_t currentPlayer_;

  uint16_t player_id_ = 0;
};

class VolumeInterfaceImpl : public VolumeInterface {
 public:
  void DeviceConnected(const RawAddress& addr) override {
    rusty::avrcp_device_connected(addr, /*absolute_volume_enabled=*/false);
  }

  void DeviceConnected(const RawAddress& addr, VolumeChangedCb cb) override {
    volumeCb = std::move(cb);
    rusty::avrcp_device_connected(addr, /*absolute_volume_enabled=*/true);
  }

  void DeviceDisconnected(const RawAddress& addr) override {
    volumeCb.Reset();
    rusty::avrcp_device_disconnected(addr);
  }

  // Set TG's (Android, ChromeOS) volume.
  void SetVolume(int8_t volume) override {
    if (volume < 0) return;

    rusty::avrcp_absolute_volume_update(volume);
  }

  // Set CT's (headsets, speakers) volume.
  void SetDeviceVolume(int8_t volume) {
    if (!volumeCb || volume < 0) return;

    volumeCb.Run(volume);
  }

 private:
  VolumeInterface::VolumeChangedCb volumeCb;
};

}  // namespace bluetooth::avrcp

namespace bluetooth {
namespace topshim {
namespace rust {
namespace internal {
static A2dpIntf* g_a2dpif;
static AvrcpIntf* g_avrcpif;

static A2dpCodecConfig to_rust_codec_config(const btav_a2dp_codec_config_t& config) {
  A2dpCodecConfig rconfig = {
      .codec_type = static_cast<uint8_t>(config.codec_type),
      .codec_priority = config.codec_priority,
      .sample_rate = static_cast<uint8_t>(config.sample_rate),
      .bits_per_sample = static_cast<uint8_t>(config.bits_per_sample),
      .channel_mode = static_cast<uint8_t>(config.channel_mode),
      .codec_specific_1 = config.codec_specific_1,
      .codec_specific_2 = config.codec_specific_2,
      .codec_specific_3 = config.codec_specific_3,
      .codec_specific_4 = config.codec_specific_4};
  return rconfig;
}

static btav_a2dp_codec_config_t from_rust_codec_config(const A2dpCodecConfig& rconfig) {
  btav_a2dp_codec_config_t config = {
      .codec_type = static_cast<btav_a2dp_codec_index_t>(rconfig.codec_type),
      .codec_priority = static_cast<btav_a2dp_codec_priority_t>(rconfig.codec_priority),
      .sample_rate = static_cast<btav_a2dp_codec_sample_rate_t>(rconfig.sample_rate),
      .bits_per_sample = static_cast<btav_a2dp_codec_bits_per_sample_t>(rconfig.bits_per_sample),
      .channel_mode = static_cast<btav_a2dp_codec_channel_mode_t>(rconfig.channel_mode),
      .codec_specific_1 = rconfig.codec_specific_1,
      .codec_specific_2 = rconfig.codec_specific_2,
      .codec_specific_3 = rconfig.codec_specific_3,
      .codec_specific_4 = rconfig.codec_specific_4,
  };
  return config;
}

static ::rust::Vec<A2dpCodecConfig> to_rust_codec_config_vec(const std::vector<btav_a2dp_codec_config_t>& configs) {
  ::rust::Vec<A2dpCodecConfig> rconfigs;

  for (btav_a2dp_codec_config_t c : configs) {
    rconfigs.push_back(to_rust_codec_config(c));
  }
  return rconfigs;
}

static A2dpError to_rust_error(const btav_error_t& error) {
  A2dpError a2dp_error = {
      .status = error.status,
      .error_code = error.error_code,
      .error_msg = error.error_msg.value_or(""),
  };
  return a2dp_error;
}

static void connection_state_cb(const RawAddress& addr, btav_connection_state_t state, const btav_error_t& error) {
  A2dpError a2dp_error = to_rust_error(error);
  rusty::connection_state_callback(addr, state, a2dp_error);
}
static void audio_state_cb(const RawAddress& addr, btav_audio_state_t state) {
  rusty::audio_state_callback(addr, state);
}
static void audio_config_cb(
    const RawAddress& addr,
    btav_a2dp_codec_config_t codec_config,
    std::vector<btav_a2dp_codec_config_t> codecs_local_capabilities,
    std::vector<btav_a2dp_codec_config_t> codecs_selectable_capabilities) {
  A2dpCodecConfig cfg = to_rust_codec_config(codec_config);
  ::rust::Vec<A2dpCodecConfig> lcaps = to_rust_codec_config_vec(codecs_local_capabilities);
  ::rust::Vec<A2dpCodecConfig> scaps = to_rust_codec_config_vec(codecs_selectable_capabilities);
  rusty::audio_config_callback(addr, cfg, lcaps, scaps);
}
static bool mandatory_codec_preferred_cb(const RawAddress& addr) {
  rusty::mandatory_codec_preferred_callback(addr);
  return false;
}

btav_source_callbacks_t g_callbacks = {
    sizeof(btav_source_callbacks_t),
    connection_state_cb,
    audio_state_cb,
    audio_config_cb,
    mandatory_codec_preferred_cb,
};
}  // namespace internal

A2dpIntf::~A2dpIntf() {
  // TODO
}

std::unique_ptr<A2dpIntf> GetA2dpProfile(const unsigned char* btif) {
  if (internal::g_a2dpif) std::abort();

  const bt_interface_t* btif_ = reinterpret_cast<const bt_interface_t*>(btif);

  auto a2dpif = std::make_unique<A2dpIntf>(
      reinterpret_cast<const btav_source_interface_t*>(btif_->get_profile_interface("a2dp")));
  internal::g_a2dpif = a2dpif.get();
  return a2dpif;
}

int A2dpIntf::init() const {
  std::vector<btav_a2dp_codec_config_t> a;
  std::vector<btav_a2dp_codec_config_t> b;
  std::vector<btav_a2dp_codec_info_t> c;
  return intf_->init(&internal::g_callbacks, 1, a, b, &c);
}

uint32_t A2dpIntf::connect(RawAddress addr) const {
  return intf_->connect(addr);
}
uint32_t A2dpIntf::disconnect(RawAddress addr) const {
  return intf_->disconnect(addr);
}
int A2dpIntf::set_silence_device(RawAddress addr, bool silent) const {
  return intf_->set_silence_device(addr, silent);
}
int A2dpIntf::set_active_device(RawAddress addr) const {
  return intf_->set_active_device(addr);
}
int A2dpIntf::config_codec(RawAddress addr, ::rust::Vec<A2dpCodecConfig> codec_preferences) const {
  std::vector<btav_a2dp_codec_config_t> prefs;
  for (size_t i = 0; i < codec_preferences.size(); ++i) {
    prefs.push_back(internal::from_rust_codec_config(codec_preferences[i]));
  }
  return intf_->config_codec(addr, prefs);
}

void A2dpIntf::cleanup() const {
  intf_->cleanup();
}
bool A2dpIntf::set_audio_config(A2dpCodecConfig rconfig) const {
  bluetooth::audio::a2dp::AudioConfig config = {
      .sample_rate = static_cast<btav_a2dp_codec_sample_rate_t>(rconfig.sample_rate),
      .bits_per_sample = static_cast<btav_a2dp_codec_bits_per_sample_t>(rconfig.bits_per_sample),
      .channel_mode = static_cast<btav_a2dp_codec_channel_mode_t>(rconfig.channel_mode),
  };
  return bluetooth::audio::a2dp::SetAudioConfig(config);
}
bool A2dpIntf::start_audio_request() const {
  return bluetooth::audio::a2dp::StartRequest();
}
bool A2dpIntf::stop_audio_request() const {
  return bluetooth::audio::a2dp::StopRequest();
}
bool A2dpIntf::suspend_audio_request() const {
  return bluetooth::audio::a2dp::SuspendRequest();
}
RustPresentationPosition A2dpIntf::get_presentation_position() const {
  bluetooth::audio::a2dp::PresentationPosition p = bluetooth::audio::a2dp::GetPresentationPosition();
  RustPresentationPosition rposition = {
      .remote_delay_report_ns = p.remote_delay_report_ns,
      .total_bytes_read = p.total_bytes_read,
      .data_position_sec = p.data_position.tv_sec,
      .data_position_nsec = static_cast<int32_t>(p.data_position.tv_nsec),
  };
  return rposition;
}

// AVRCP

static bluetooth::avrcp::AvrcpMediaInterfaceImpl mAvrcpInterface;
static bluetooth::avrcp::VolumeInterfaceImpl mVolumeInterface;

std::unique_ptr<AvrcpIntf> GetAvrcpProfile(const unsigned char* btif) {
  if (internal::g_avrcpif) std::abort();

  const bt_interface_t* btif_ = reinterpret_cast<const bt_interface_t*>(btif);

  auto avrcpif = std::make_unique<AvrcpIntf>(reinterpret_cast<avrcp::ServiceInterface*>(btif_->get_avrcp_service()));
  internal::g_avrcpif = avrcpif.get();
  return avrcpif;
}

AvrcpIntf::~AvrcpIntf() {}

void AvrcpIntf::init() {
  intf_->Init(&mAvrcpInterface, &mVolumeInterface, nullptr);
}

void AvrcpIntf::cleanup() {
  intf_->Cleanup();
}

uint32_t AvrcpIntf::connect(RawAddress addr) {
  return intf_->ConnectDevice(addr);
}
uint32_t AvrcpIntf::disconnect(RawAddress addr) {
  return intf_->DisconnectDevice(addr);
}

void AvrcpIntf::set_volume(int8_t volume) {
  return mVolumeInterface.SetDeviceVolume(volume);
}

void AvrcpIntf::set_playback_status(const ::rust::String& status) {
  avrcp::PlayState state = avrcp::PlayState::STOPPED;

  if (status == "stopped") state = avrcp::PlayState::STOPPED;
  if (status == "playing") state = avrcp::PlayState::PLAYING;
  if (status == "paused") state = avrcp::PlayState::PAUSED;

  mAvrcpInterface.SetPlaybackStatus(state);
}

void AvrcpIntf::set_position(int64_t position) {
  mAvrcpInterface.SetPosition(position);
}

void AvrcpIntf::set_metadata(
    const ::rust::String& title, const ::rust::String& artist, const ::rust::String& album, int64_t length_us) {
  mAvrcpInterface.SetMetadata(std::string(title), std::string(artist), std::string(album), length_us);
}

uint16_t AvrcpIntf::add_player(const ::rust::String& name, bool browsing_supported) {
  return mAvrcpInterface.AddPlayer(std::string(name), browsing_supported);
}

}  // namespace rust
}  // namespace topshim
}  // namespace bluetooth
