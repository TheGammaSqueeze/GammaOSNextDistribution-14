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

#include "avrcp_service.h"

#include <base/functional/bind.h>
#include <base/logging.h>
#include <base/task/cancelable_task_tracker.h>
#include <base/threading/thread.h>
#include <bluetooth/log.h>

#include <mutex>
#include <sstream>

#include "bta/sys/bta_sys.h"
#include "btif_av.h"
#include "btif_common.h"
#include "device.h"
#include "include/check.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/bt_uuid16.h"
#include "stack/include/main_thread.h"
#include "stack/include/sdp_api.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

using namespace bluetooth::legacy::stack::sdp;

namespace bluetooth {
namespace avrcp {
// Static variables and interface definitions
AvrcpService* AvrcpService::instance_ = nullptr;
AvrcpService::ServiceInterfaceImpl* AvrcpService::service_interface_ = nullptr;

void do_in_avrcp_jni(const base::Closure& task) {
  do_in_jni_thread(FROM_HERE, task);
}

class A2dpInterfaceImpl : public A2dpInterface {
  RawAddress active_peer() override { return btif_av_source_active_peer(); }

  bool is_peer_in_silence_mode(const RawAddress& peer_address) override {
    return btif_av_is_peer_silenced(peer_address);
  }
} a2dp_interface_;

class AvrcpInterfaceImpl : public AvrcpInterface {
 public:
  uint16_t GetAvrcpControlVersion() { return AVRC_GetControlProfileVersion(); }

  uint16_t GetAvrcpVersion() {
    return AVRC_GetProfileVersion();
  }

  uint16_t AddRecord(uint16_t service_uuid, const char* p_service_name,
                     const char* p_provider_name, uint16_t categories,
                     uint32_t sdp_handle, bool browse_supported,
                     uint16_t profile_version,
                     uint16_t cover_art_psm) override {
    return AVRC_AddRecord(service_uuid, p_service_name, p_provider_name,
                          categories, sdp_handle, browse_supported,
                          profile_version, cover_art_psm);
  }

  uint16_t RemoveRecord(uint32_t sdp_handle) {
    return AVRC_RemoveRecord(sdp_handle);
  }

  uint16_t FindService(uint16_t service_uuid, const RawAddress& bd_addr,
                       tAVRC_SDP_DB_PARAMS* p_db,
                       tAVRC_FIND_CBACK p_cback) override {
    return AVRC_FindService(service_uuid, bd_addr, p_db, p_cback);
  }

  uint16_t Open(uint8_t* p_handle, tAVRC_CONN_CB* p_ccb,
                const RawAddress& bd_addr) override {
    return AVRC_Open(p_handle, p_ccb, bd_addr);
  }

  uint16_t OpenBrowse(uint8_t handle, uint8_t conn_role) override {
    return AVRC_OpenBrowse(handle, conn_role);
  }

  uint16_t GetPeerMtu(uint8_t handle) override {
    return AVCT_GetPeerMtu(handle);
  }

  uint16_t GetBrowseMtu(uint8_t handle) override {
    return AVCT_GetBrowseMtu(handle);
  }

  uint16_t Close(uint8_t handle) override { return AVRC_Close(handle); }

  uint16_t CloseBrowse(uint8_t handle) override {
    return AVRC_CloseBrowse(handle);
  }

  uint16_t MsgReq(uint8_t handle, uint8_t label, uint8_t ctype,
                  BT_HDR* p_pkt) override {
    return AVRC_MsgReq(handle, label, ctype, p_pkt, true);
  }

  void SaveControllerVersion(const RawAddress& bdaddr,
                             uint16_t version) override {
    AVRC_SaveControllerVersion(bdaddr, version);
  }

} avrcp_interface_;

class SdpInterfaceImpl : public SdpInterface {
 public:
  bool InitDiscoveryDb(tSDP_DISCOVERY_DB* a, uint32_t b, uint16_t c,
                       const bluetooth::Uuid* d, uint16_t e,
                       uint16_t* f) override {
    return get_legacy_stack_sdp_api()->service.SDP_InitDiscoveryDb(a, b, c, d,
                                                                   e, f);
  }

  bool ServiceSearchAttributeRequest(const RawAddress& a, tSDP_DISCOVERY_DB* b,
                                     tSDP_DISC_CMPL_CB* c) override {
    return get_legacy_stack_sdp_api()
        ->service.SDP_ServiceSearchAttributeRequest(a, b, c);
  }

  tSDP_DISC_REC* FindServiceInDb(tSDP_DISCOVERY_DB* a, uint16_t b,
                                 t_sdp_disc_rec* c) override {
    return get_legacy_stack_sdp_api()->db.SDP_FindServiceInDb(a, b, c);
  }

  tSDP_DISC_ATTR* FindAttributeInRec(t_sdp_disc_rec* a, uint16_t b) override {
    return get_legacy_stack_sdp_api()->record.SDP_FindAttributeInRec(a, b);
  }

  bool FindProfileVersionInRec(t_sdp_disc_rec* a, uint16_t b,
                               uint16_t* c) override {
    return get_legacy_stack_sdp_api()->record.SDP_FindProfileVersionInRec(a, b,
                                                                          c);
  }
} sdp_interface_;

// A wrapper class for the media callbacks that handles thread
// switching/synchronization so the devices don't have to worry about it.
class MediaInterfaceWrapper : public MediaInterface {
 public:
  MediaInterfaceWrapper(MediaInterface* cb) : wrapped_(cb){};

  void SendKeyEvent(uint8_t key, KeyState state) override {
    do_in_avrcp_jni(base::Bind(&MediaInterface::SendKeyEvent,
                               base::Unretained(wrapped_), key, state));
  }

  void GetSongInfo(SongInfoCallback info_cb) override {
    auto cb_lambda = [](SongInfoCallback cb, SongInfo data) {
      do_in_main_thread(FROM_HERE, base::BindOnce(cb, data));
    };

    auto bound_cb = base::Bind(cb_lambda, info_cb);

    do_in_avrcp_jni(base::Bind(&MediaInterface::GetSongInfo,
                               base::Unretained(wrapped_), bound_cb));
  }

  void GetPlayStatus(PlayStatusCallback status_cb) override {
    auto cb_lambda = [](PlayStatusCallback cb, PlayStatus status) {
      do_in_main_thread(FROM_HERE, base::BindOnce(cb, status));
    };

    auto bound_cb = base::Bind(cb_lambda, status_cb);

    do_in_avrcp_jni(base::Bind(&MediaInterface::GetPlayStatus,
                               base::Unretained(wrapped_), bound_cb));
  }

  void GetNowPlayingList(NowPlayingCallback now_playing_cb) override {
    auto cb_lambda = [](NowPlayingCallback cb, std::string curr_media_id,
                        std::vector<SongInfo> song_list) {
      do_in_main_thread(
          FROM_HERE, base::BindOnce(cb, curr_media_id, std::move(song_list)));
    };

    auto bound_cb = base::Bind(cb_lambda, now_playing_cb);

    do_in_avrcp_jni(base::Bind(&MediaInterface::GetNowPlayingList,
                               base::Unretained(wrapped_), bound_cb));
  }

  void GetMediaPlayerList(MediaListCallback list_cb) override {
    auto cb_lambda = [](MediaListCallback cb, uint16_t curr_player,
                        std::vector<MediaPlayerInfo> player_list) {
      do_in_main_thread(
          FROM_HERE, base::BindOnce(cb, curr_player, std::move(player_list)));
    };

    auto bound_cb = base::Bind(cb_lambda, list_cb);

    do_in_avrcp_jni(base::Bind(&MediaInterface::GetMediaPlayerList,
                               base::Unretained(wrapped_), bound_cb));
  }

  void GetFolderItems(uint16_t player_id, std::string media_id,
                      FolderItemsCallback folder_cb) override {
    auto cb_lambda = [](FolderItemsCallback cb,
                        std::vector<ListItem> item_list) {
      do_in_main_thread(FROM_HERE, base::BindOnce(cb, std::move(item_list)));
    };

    auto bound_cb = base::Bind(cb_lambda, folder_cb);

    do_in_avrcp_jni(base::Bind(&MediaInterface::GetFolderItems,
                               base::Unretained(wrapped_), player_id, media_id,
                               bound_cb));
  }

  void SetBrowsedPlayer(uint16_t player_id,
                        SetBrowsedPlayerCallback browse_cb) override {
    auto cb_lambda = [](SetBrowsedPlayerCallback cb, bool success,
                        std::string root_id, uint32_t num_items) {
      do_in_main_thread(FROM_HERE,
                        base::BindOnce(cb, success, root_id, num_items));
    };

    auto bound_cb = base::Bind(cb_lambda, browse_cb);

    do_in_avrcp_jni(base::Bind(&MediaInterface::SetBrowsedPlayer,
                               base::Unretained(wrapped_), player_id,
                               bound_cb));
  }

  void PlayItem(uint16_t player_id, bool now_playing,
                std::string media_id) override {
    do_in_avrcp_jni(base::Bind(&MediaInterface::PlayItem,
                               base::Unretained(wrapped_), player_id,
                               now_playing, media_id));
  }

  void SetActiveDevice(const RawAddress& address) override {
    do_in_avrcp_jni(base::Bind(&MediaInterface::SetActiveDevice,
                               base::Unretained(wrapped_), address));
  }

  void RegisterUpdateCallback(MediaCallbacks* callback) override {
    wrapped_->RegisterUpdateCallback(callback);
  }

  void UnregisterUpdateCallback(MediaCallbacks* callback) override {
    wrapped_->UnregisterUpdateCallback(callback);
  }

 private:
  MediaInterface* wrapped_;
};

// A wrapper class for the media callbacks that handles thread
// switching/synchronization so the devices don't have to worry about it.
class VolumeInterfaceWrapper : public VolumeInterface {
 public:
  VolumeInterfaceWrapper(VolumeInterface* interface) : wrapped_(interface){};

  void DeviceConnected(const RawAddress& bdaddr) override {
    do_in_avrcp_jni(
        base::Bind(static_cast<void (VolumeInterface::*)(const RawAddress&)>(
                       &VolumeInterface::DeviceConnected),
                   base::Unretained(wrapped_), bdaddr));
  }

  void DeviceConnected(const RawAddress& bdaddr, VolumeChangedCb cb) override {
    auto cb_lambda = [](VolumeChangedCb cb, int8_t volume) {
      do_in_main_thread(FROM_HERE, base::BindOnce(cb, volume));
    };

    auto bound_cb = base::Bind(cb_lambda, cb);

    do_in_avrcp_jni(base::Bind(static_cast<void (VolumeInterface::*)(
                                   const RawAddress&, VolumeChangedCb)>(
                                   &VolumeInterface::DeviceConnected),
                               base::Unretained(wrapped_), bdaddr, bound_cb));
  }

  void DeviceDisconnected(const RawAddress& bdaddr) override {
    do_in_avrcp_jni(base::Bind(&VolumeInterface::DeviceDisconnected,
                               base::Unretained(wrapped_), bdaddr));
  }

  void SetVolume(int8_t volume) override {
    do_in_avrcp_jni(base::Bind(&VolumeInterface::SetVolume,
                               base::Unretained(wrapped_), volume));
  }

 private:
  VolumeInterface* wrapped_;
};

// A wrapper class for the media callbacks that handles thread
// switching/synchronization so the devices don't have to worry about it.
class PlayerSettingsInterfaceWrapper : public PlayerSettingsInterface {
 public:
  PlayerSettingsInterfaceWrapper(PlayerSettingsInterface* interface)
      : wrapped_(interface){};

  void ListPlayerSettings(ListPlayerSettingsCallback cb) override {
    auto cb_lambda = [](const ListPlayerSettingsCallback& cb,
                        std::vector<PlayerAttribute> attributes) {
      do_in_main_thread(FROM_HERE, base::BindOnce(cb, std::move(attributes)));
    };

    auto bound_cb = base::Bind(cb_lambda, cb);

    do_in_avrcp_jni(base::Bind(&PlayerSettingsInterface::ListPlayerSettings,
                               base::Unretained(wrapped_), bound_cb));
  }

  void ListPlayerSettingValues(PlayerAttribute setting,
                               ListPlayerSettingValuesCallback cb) override {
    auto cb_lambda = [](const ListPlayerSettingValuesCallback& cb,
                        PlayerAttribute setting, std::vector<uint8_t> values) {
      do_in_main_thread(FROM_HERE,
                        base::BindOnce(cb, setting, std::move(values)));
    };

    auto bound_cb = base::Bind(cb_lambda, cb);

    do_in_avrcp_jni(
        base::Bind(&PlayerSettingsInterface::ListPlayerSettingValues,
                   base::Unretained(wrapped_), setting, bound_cb));
  }

  void GetCurrentPlayerSettingValue(
      std::vector<PlayerAttribute> attributes,
      GetCurrentPlayerSettingValueCallback cb) override {
    auto cb_lambda = [](const GetCurrentPlayerSettingValueCallback& cb,
                        std::vector<PlayerAttribute> attributes,
                        std::vector<uint8_t> values) {
      do_in_main_thread(FROM_HERE, base::BindOnce(cb, std::move(attributes),
                                                  std::move(values)));
    };

    auto bound_cb = base::Bind(cb_lambda, cb);

    do_in_avrcp_jni(base::Bind(
        &PlayerSettingsInterface::GetCurrentPlayerSettingValue,
        base::Unretained(wrapped_), std::move(attributes), bound_cb));
  }

  void SetPlayerSettings(std::vector<PlayerAttribute> attributes,
                         std::vector<uint8_t> values,
                         SetPlayerSettingValueCallback cb) override {
    auto cb_lambda = [](const SetPlayerSettingValueCallback& cb, bool success) {
      do_in_main_thread(FROM_HERE, base::BindOnce(cb, success));
    };

    auto bound_cb = base::Bind(cb_lambda, cb);

    do_in_avrcp_jni(base::Bind(
        &PlayerSettingsInterface::SetPlayerSettings, base::Unretained(wrapped_),
        std::move(attributes), std::move(values), bound_cb));
  }

 private:
  PlayerSettingsInterface* wrapped_;
};

void AvrcpService::Init(MediaInterface* media_interface,
                        VolumeInterface* volume_interface,
                        PlayerSettingsInterface* player_settings_interface) {
  log::info("AVRCP Target Service started");

  profile_version = avrcp_interface_.GetAvrcpVersion();

  uint16_t supported_features = GetSupportedFeatures(profile_version);
  sdp_record_handle = get_legacy_stack_sdp_api()->handle.SDP_CreateRecord();

  avrcp_interface_.AddRecord(UUID_SERVCLASS_AV_REM_CTRL_TARGET,
                             "AV Remote Control Target", NULL,
                             supported_features, sdp_record_handle, true,
                             profile_version, 0);
  bta_sys_add_uuid(UUID_SERVCLASS_AV_REM_CTRL_TARGET);

  ct_sdp_record_handle = get_legacy_stack_sdp_api()->handle.SDP_CreateRecord();

  avrcp_interface_.AddRecord(UUID_SERVCLASS_AV_REMOTE_CONTROL,
                             "AV Remote Control", NULL, AVRCP_SUPF_TG_CT,
                             ct_sdp_record_handle, false,
                             avrcp_interface_.GetAvrcpControlVersion(), 0);
  bta_sys_add_uuid(UUID_SERVCLASS_AV_REMOTE_CONTROL);

  media_interface_ = new MediaInterfaceWrapper(media_interface);
  media_interface->RegisterUpdateCallback(instance_);

  VolumeInterfaceWrapper* wrapped_volume_interface = nullptr;
  if (volume_interface != nullptr) {
    wrapped_volume_interface = new VolumeInterfaceWrapper(volume_interface);
  }

  volume_interface_ = wrapped_volume_interface;

  PlayerSettingsInterfaceWrapper* wrapped_player_settings_interface = nullptr;
  if (player_settings_interface != nullptr) {
    wrapped_player_settings_interface =
        new PlayerSettingsInterfaceWrapper(player_settings_interface);
  }

  player_settings_interface_ = wrapped_player_settings_interface;

  ConnectionHandler::Initialize(
      base::BindRepeating(&AvrcpService::DeviceCallback,
                          base::Unretained(instance_)),
      &avrcp_interface_, &sdp_interface_, wrapped_volume_interface);
  connection_handler_ = ConnectionHandler::Get();
}

uint16_t AvrcpService::GetSupportedFeatures(uint16_t profile_version) {
  switch (profile_version) {
    case AVRC_REV_1_6:
      return AVRCP_SUPF_TG_1_6;
    case AVRC_REV_1_5:
      return AVRCP_SUPF_TG_1_5;
    case AVRC_REV_1_4:
      return AVRCP_SUPF_TG_1_4;
    case AVRC_REV_1_3:
      return AVRCP_SUPF_TG_1_3;
  }
  return AVRCP_SUPF_TG_DEFAULT;
}

void AvrcpService::Cleanup() {
  log::info("AVRCP Target Service stopped");

  avrcp_interface_.RemoveRecord(sdp_record_handle);
  bta_sys_remove_uuid(UUID_SERVCLASS_AV_REM_CTRL_TARGET);
  sdp_record_handle = -1;
  avrcp_interface_.RemoveRecord(ct_sdp_record_handle);
  bta_sys_remove_uuid(UUID_SERVCLASS_AV_REMOTE_CONTROL);
  ct_sdp_record_handle = -1;

  connection_handler_->CleanUp();
  connection_handler_ = nullptr;
  if (player_settings_interface_ != nullptr) {
    delete player_settings_interface_;
  }
  if (volume_interface_ != nullptr) {
    delete volume_interface_;
  }
  delete media_interface_;
}

void AvrcpService::RegisterBipServer(int psm) {
  log::info("AVRCP Target Service has registered a BIP OBEX server, psm={}",
            psm);
  avrcp_interface_.RemoveRecord(sdp_record_handle);
  uint16_t supported_features
      = GetSupportedFeatures(profile_version) | AVRC_SUPF_TG_PLAYER_COVER_ART;
  sdp_record_handle = get_legacy_stack_sdp_api()->handle.SDP_CreateRecord();
  avrcp_interface_.AddRecord(UUID_SERVCLASS_AV_REM_CTRL_TARGET,
                             "AV Remote Control Target", NULL,
                             supported_features, sdp_record_handle, true,
                             profile_version, psm);
}

void AvrcpService::UnregisterBipServer() {
  log::info("AVRCP Target Service has unregistered a BIP OBEX server");
  avrcp_interface_.RemoveRecord(sdp_record_handle);
  uint16_t supported_features = GetSupportedFeatures(profile_version);
  sdp_record_handle = get_legacy_stack_sdp_api()->handle.SDP_CreateRecord();
  avrcp_interface_.AddRecord(UUID_SERVCLASS_AV_REM_CTRL_TARGET,
                             "AV Remote Control Target", NULL,
                             supported_features, sdp_record_handle, true,
                             profile_version, 0);
}

AvrcpService* AvrcpService::Get() {
  CHECK(instance_);
  return instance_;
}

ServiceInterface* AvrcpService::GetServiceInterface() {
  if (service_interface_ == nullptr) {
    service_interface_ = new ServiceInterfaceImpl();
  }

  return service_interface_;
}

void AvrcpService::ConnectDevice(const RawAddress& bdaddr) {
  log::info("address={}", ADDRESS_TO_LOGGABLE_STR(bdaddr));

  connection_handler_->ConnectDevice(bdaddr);
}

void AvrcpService::DisconnectDevice(const RawAddress& bdaddr) {
  log::info("address={}", ADDRESS_TO_LOGGABLE_STR(bdaddr));
  connection_handler_->DisconnectDevice(bdaddr);
}

void AvrcpService::SetBipClientStatus(const RawAddress& bdaddr,
                                      bool connected) {
  log::info("address={}, connected={}", ADDRESS_TO_LOGGABLE_STR(bdaddr),
            connected);
  connection_handler_->SetBipClientStatus(bdaddr, connected);
}

void AvrcpService::SendMediaUpdate(bool track_changed, bool play_state,
                                   bool queue) {
  log::info("track_changed={} :  play_state={} :  queue={}", track_changed,
            play_state, queue);

  // This function may be called on any thread, we need to make sure that the
  // device update happens on the main thread.
  for (const auto& device :
       instance_->connection_handler_->GetListOfDevices()) {
    do_in_main_thread(
        FROM_HERE, base::BindOnce(&Device::SendMediaUpdate, device.get()->Get(),
                                  track_changed, play_state, queue));
  }
}

void AvrcpService::SendFolderUpdate(bool available_players,
                                    bool addressed_players, bool uids) {
  log::info("available_players={} :  addressed_players={} :  uids={}",
            available_players, addressed_players, uids);

  // Ensure that the update is posted to the correct thread
  for (const auto& device :
       instance_->connection_handler_->GetListOfDevices()) {
    do_in_main_thread(
        FROM_HERE,
        base::BindOnce(&Device::SendFolderUpdate, device.get()->Get(),
                       available_players, addressed_players, uids));
  }
}

// Send out the track changed info to update the playback state for each device
void AvrcpService::SendActiveDeviceChanged(const RawAddress& address) {
  SendMediaUpdate(false, true, false);
}

void AvrcpService::SendPlayerSettingsChanged(
    std::vector<PlayerAttribute> attributes, std::vector<uint8_t> values) {
  log::info("");
  std::stringstream ss;
  for (size_t i = 0; i < attributes.size(); i++) {
    ss << "attribute=" << attributes.at(i) << " : ";
    if (attributes.at(i) == PlayerAttribute::REPEAT) {
      ss << "value=" << (PlayerRepeatValue)values.at(i);
    } else if (attributes.at(i) == PlayerAttribute::SHUFFLE) {
      ss << "value=" << (PlayerShuffleValue)values.at(i);
    } else {
      ss << "value=" << std::to_string(values.at(i));
    }
    ss << std::endl;
  }

  log::info("{}", ss.str());

  // Ensure that the update is posted to the correct thread
  for (const auto& device :
       instance_->connection_handler_->GetListOfDevices()) {
    do_in_main_thread(FROM_HERE,
                      base::BindOnce(&Device::HandlePlayerSettingChanged,
                                     device.get()->Get(), attributes, values));
  }
}

void AvrcpService::DeviceCallback(std::shared_ptr<Device> new_device) {
  if (new_device == nullptr) return;

  // TODO (apanicke): Pass the interfaces into the connection handler
  // so that the devices can be created with any interfaces they need.
  new_device->RegisterInterfaces(media_interface_, &a2dp_interface_,
                                 volume_interface_, player_settings_interface_);
}

// Service Interface
void AvrcpService::ServiceInterfaceImpl::Init(
    MediaInterface* media_interface, VolumeInterface* volume_interface,
    PlayerSettingsInterface* player_settings_interface) {
  std::lock_guard<std::mutex> lock(service_interface_lock_);

  // TODO: This function should block until the service is completely up so
  // that its possible to call Get() on the service immediately after calling
  // init without issues.

  CHECK(instance_ == nullptr);
  instance_ = new AvrcpService();

  do_in_main_thread(
      FROM_HERE, base::BindOnce(&AvrcpService::Init,
                                base::Unretained(instance_), media_interface,
                                volume_interface, player_settings_interface));
}

void AvrcpService::ServiceInterfaceImpl::RegisterBipServer(int psm) {
  std::lock_guard<std::mutex> lock(service_interface_lock_);
  CHECK(instance_ != nullptr);
  do_in_main_thread(FROM_HERE,
                    base::BindOnce(&AvrcpService::RegisterBipServer,
                                   base::Unretained(instance_), psm));
}

void AvrcpService::ServiceInterfaceImpl::UnregisterBipServer() {
  std::lock_guard<std::mutex> lock(service_interface_lock_);
  CHECK(instance_ != nullptr);
  do_in_main_thread(FROM_HERE,
                    base::BindOnce(&AvrcpService::UnregisterBipServer,
                                   base::Unretained(instance_)));
}

bool AvrcpService::ServiceInterfaceImpl::ConnectDevice(
    const RawAddress& bdaddr) {
  std::lock_guard<std::mutex> lock(service_interface_lock_);
  CHECK(instance_ != nullptr);
  do_in_main_thread(FROM_HERE,
                    base::BindOnce(&AvrcpService::ConnectDevice,
                                   base::Unretained(instance_), bdaddr));
  return true;
}

bool AvrcpService::ServiceInterfaceImpl::DisconnectDevice(
    const RawAddress& bdaddr) {
  std::lock_guard<std::mutex> lock(service_interface_lock_);
  CHECK(instance_ != nullptr);
  do_in_main_thread(FROM_HERE,
                    base::BindOnce(&AvrcpService::DisconnectDevice,
                                   base::Unretained(instance_), bdaddr));
  return true;
}

void AvrcpService::ServiceInterfaceImpl::SetBipClientStatus(
    const RawAddress& bdaddr, bool connected) {
  std::lock_guard<std::mutex> lock(service_interface_lock_);
  CHECK(instance_ != nullptr);
  do_in_main_thread(FROM_HERE, base::BindOnce(&AvrcpService::SetBipClientStatus,
                                              base::Unretained(instance_),
                                              bdaddr, connected));
}

bool AvrcpService::ServiceInterfaceImpl::Cleanup() {
  std::lock_guard<std::mutex> lock(service_interface_lock_);

  if (instance_ == nullptr) return false;

  do_in_main_thread(FROM_HERE, base::BindOnce(&AvrcpService::Cleanup,
                                              base::Owned(instance_)));

  // Setting instance to nullptr here is fine since it will be deleted on the
  // other thread.
  instance_ = nullptr;

  return true;
}

void AvrcpService::DebugDump(int fd) {
  if (instance_ == nullptr) {
    dprintf(fd, "\nAVRCP Target Service not started\n");
    return;
  }

  auto handler = instance_->connection_handler_;
  if (handler == nullptr) {
    dprintf(fd, "\nAVRCP connection handler is null\n");
    return;
  }

  auto device_list = handler->GetListOfDevices();
  dprintf(fd, "\nAVRCP Target Native Service: %zu devices\n",
          device_list.size());

  std::stringstream stream;
  for (const auto& device : device_list) {
    stream << "  " << *device << std::endl;
  }

  dprintf(fd, "%s", stream.str().c_str());
}

/** when a2dp connected, btif will start register vol changed, so we need a
 * interface for it. */
void AvrcpService::RegisterVolChanged(const RawAddress& bdaddr) {
  log::info(": address={}", ADDRESS_TO_LOGGABLE_STR(bdaddr));

  connection_handler_->RegisterVolChanged(bdaddr);
}

}  // namespace avrcp
}  // namespace bluetooth
