/*
 * Copyright (C) 2018 The Android Open Source Project
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

#define LOG_TAG "AvrcpTargetJni"

#include <base/functional/bind.h>
#include <base/functional/callback.h>
#include <map>
#include <mutex>
#include <shared_mutex>
#include <vector>

#include "avrcp.h"
#include "com_android_bluetooth.h"
#include "utils/Log.h"

using namespace bluetooth::avrcp;

namespace android {

// Static Variables
static MediaCallbacks* mServiceCallbacks;
static ServiceInterface* sServiceInterface;
static jobject mJavaInterface;
static std::shared_timed_mutex interface_mutex;
static std::shared_timed_mutex callbacks_mutex;

// Forward Declarations
static void sendMediaKeyEvent(int, KeyState);
static std::string getCurrentMediaId();
static SongInfo getSongInfo();
static PlayStatus getCurrentPlayStatus();
static std::vector<SongInfo> getNowPlayingList();
static uint16_t getCurrentPlayerId();
static std::vector<MediaPlayerInfo> getMediaPlayerList();
using SetBrowsedPlayerCb = MediaInterface::SetBrowsedPlayerCallback;
static void setBrowsedPlayer(uint16_t player_id, SetBrowsedPlayerCb);
using GetFolderItemsCb = MediaInterface::FolderItemsCallback;
static void getFolderItems(uint16_t player_id, std::string media_id,
                           GetFolderItemsCb cb);
static void playItem(uint16_t player_id, bool now_playing,
                     std::string media_id);
static void setActiveDevice(const RawAddress& address);

static void volumeDeviceConnected(const RawAddress& address);
static void volumeDeviceConnected(
    const RawAddress& address,
    ::bluetooth::avrcp::VolumeInterface::VolumeChangedCb cb);
static void volumeDeviceDisconnected(const RawAddress& address);
static void setVolume(int8_t volume);

using ListPlayerSettingsCb =
    PlayerSettingsInterface::ListPlayerSettingsCallback;
static void listPlayerSettings(ListPlayerSettingsCb cb);
ListPlayerSettingsCb list_player_settings_cb;
using ListPlayerSettingValuesCb =
    PlayerSettingsInterface::ListPlayerSettingValuesCallback;
static void listPlayerSettingValues(PlayerAttribute setting,
                                    ListPlayerSettingValuesCb cb);
ListPlayerSettingValuesCb list_player_setting_values_cb;
using GetCurrentPlayerSettingValueCb =
    PlayerSettingsInterface::GetCurrentPlayerSettingValueCallback;
static void getPlayerSettings(std::vector<PlayerAttribute> attributes,
                              GetCurrentPlayerSettingValueCb cb);
GetCurrentPlayerSettingValueCb get_current_player_setting_value_cb;
using SetPlayerSettingValueCb =
    PlayerSettingsInterface::SetPlayerSettingValueCallback;
static void setPlayerSettings(std::vector<PlayerAttribute> attributes,
                              std::vector<uint8_t> values,
                              SetPlayerSettingValueCb cb);
SetPlayerSettingValueCb set_player_setting_value_cb;

// Local Variables
// TODO (apanicke): Use a map here to store the callback in order to
// support multi-browsing
SetBrowsedPlayerCb set_browsed_player_cb;
using map_entry = std::pair<std::string, GetFolderItemsCb>;
std::map<std::string, GetFolderItemsCb> get_folder_items_cb_map;
std::map<RawAddress, ::bluetooth::avrcp::VolumeInterface::VolumeChangedCb>
    volumeCallbackMap;

template <typename T>
void copyJavaArraytoCppVector(JNIEnv* env, const jbyteArray& jArray,
                              std::vector<T>& cVec) {
  size_t len = (size_t)env->GetArrayLength(jArray);
  if (len == 0) return;
  jbyte* elements = env->GetByteArrayElements(jArray, nullptr);
  T* array = reinterpret_cast<T*>(elements);
  cVec.reserve(len);
  std::copy(array, array + len, std::back_inserter(cVec));
  env->ReleaseByteArrayElements(jArray, elements, 0);
}

// TODO (apanicke): In the future, this interface should guarantee that
// all calls happen on the JNI Thread. Right now this is very difficult
// as it is hard to get a handle on the JNI thread from here.
class AvrcpMediaInterfaceImpl : public MediaInterface {
 public:
  void SendKeyEvent(uint8_t key, KeyState state) {
    sendMediaKeyEvent(key, state);
  }

  void GetSongInfo(SongInfoCallback cb) override {
    auto info = getSongInfo();
    cb.Run(info);
  }

  void GetPlayStatus(PlayStatusCallback cb) override {
    auto status = getCurrentPlayStatus();
    cb.Run(status);
  }

  void GetNowPlayingList(NowPlayingCallback cb) override {
    auto curr_song_id = getCurrentMediaId();
    auto now_playing_list = getNowPlayingList();
    cb.Run(curr_song_id, std::move(now_playing_list));
  }

  void GetMediaPlayerList(MediaListCallback cb) override {
    uint16_t current_player = getCurrentPlayerId();
    auto player_list = getMediaPlayerList();
    cb.Run(current_player, std::move(player_list));
  }

  void GetFolderItems(uint16_t player_id, std::string media_id,
                      FolderItemsCallback folder_cb) override {
    getFolderItems(player_id, media_id, folder_cb);
  }

  void SetBrowsedPlayer(uint16_t player_id,
                        SetBrowsedPlayerCallback browse_cb) override {
    setBrowsedPlayer(player_id, browse_cb);
  }

  void RegisterUpdateCallback(MediaCallbacks* callback) override {
    // TODO (apanicke): Allow multiple registrations in the future
    mServiceCallbacks = callback;
  }

  void UnregisterUpdateCallback(MediaCallbacks* /* callback */) override {
    mServiceCallbacks = nullptr;
  }

  void PlayItem(uint16_t player_id, bool now_playing,
                std::string media_id) override {
    playItem(player_id, now_playing, media_id);
  }

  void SetActiveDevice(const RawAddress& address) override {
    setActiveDevice(address);
  }
};
static AvrcpMediaInterfaceImpl mAvrcpInterface;

class VolumeInterfaceImpl : public VolumeInterface {
 public:
  void DeviceConnected(const RawAddress& bdaddr) override {
    volumeDeviceConnected(bdaddr);
  }

  void DeviceConnected(const RawAddress& bdaddr, VolumeChangedCb cb) override {
    volumeDeviceConnected(bdaddr, cb);
  }

  void DeviceDisconnected(const RawAddress& bdaddr) override {
    volumeDeviceDisconnected(bdaddr);
  }

  void SetVolume(int8_t volume) override { setVolume(volume); }
};
static VolumeInterfaceImpl mVolumeInterface;

class PlayerSettingsInterfaceImpl : public PlayerSettingsInterface {
 public:
  void ListPlayerSettings(ListPlayerSettingsCallback cb) {
    listPlayerSettings(cb);
  }

  void ListPlayerSettingValues(PlayerAttribute setting,
                               ListPlayerSettingValuesCallback cb) {
    listPlayerSettingValues(setting, cb);
  }

  void GetCurrentPlayerSettingValue(std::vector<PlayerAttribute> attributes,
                                    GetCurrentPlayerSettingValueCallback cb) {
    getPlayerSettings(attributes, cb);
  }

  void SetPlayerSettings(std::vector<PlayerAttribute> attributes,
                         std::vector<uint8_t> values,
                         SetPlayerSettingValueCallback cb) {
    setPlayerSettings(attributes, values, cb);
  }
};
static PlayerSettingsInterfaceImpl mPlayerSettingsInterface;

static jmethodID method_getCurrentSongInfo;
static jmethodID method_getPlaybackStatus;
static jmethodID method_sendMediaKeyEvent;

static jmethodID method_getCurrentMediaId;
static jmethodID method_getNowPlayingList;

static jmethodID method_setBrowsedPlayer;
static jmethodID method_getCurrentPlayerId;
static jmethodID method_getMediaPlayerList;
static jmethodID method_getFolderItemsRequest;
static jmethodID method_playItem;

static jmethodID method_setActiveDevice;

static jmethodID method_volumeDeviceConnected;
static jmethodID method_volumeDeviceDisconnected;

static jmethodID method_setVolume;

static jmethodID method_listPlayerSettings;
static jmethodID method_listPlayerSettingValues;
static jmethodID method_getPlayerSettings;
static jmethodID method_setPlayerSettings;

static void initNative(JNIEnv* env, jobject object) {
  log::debug("");
  std::unique_lock<std::shared_timed_mutex> interface_lock(interface_mutex);
  std::unique_lock<std::shared_timed_mutex> callbacks_lock(callbacks_mutex);
  mJavaInterface = env->NewGlobalRef(object);

  sServiceInterface = getBluetoothInterface()->get_avrcp_service();
  sServiceInterface->Init(&mAvrcpInterface, &mVolumeInterface,
                          &mPlayerSettingsInterface);
}

static void registerBipServerNative(JNIEnv* /* env */, jobject /* object */,
                                    jint l2cap_psm) {
  log::debug("l2cap_psm={}", (int)l2cap_psm);
  std::unique_lock<std::shared_timed_mutex> interface_lock(interface_mutex);
  if (sServiceInterface == nullptr) {
    log::warn("Service not loaded.");
    return;
  }
  sServiceInterface->RegisterBipServer((int)l2cap_psm);
}

static void unregisterBipServerNative(JNIEnv* /* env */, jobject /* object */) {
  log::debug("");
  std::unique_lock<std::shared_timed_mutex> interface_lock(interface_mutex);
  if (sServiceInterface == nullptr) {
    log::warn("Service not loaded.");
    return;
  }
  sServiceInterface->UnregisterBipServer();
}

static void sendMediaUpdateNative(JNIEnv* /* env */, jobject /* object */,
                                  jboolean metadata, jboolean state,
                                  jboolean queue) {
  log::debug("");
  std::unique_lock<std::shared_timed_mutex> interface_lock(interface_mutex);
  if (mServiceCallbacks == nullptr) {
    log::warn("Service not loaded.");
    return;
  }

  mServiceCallbacks->SendMediaUpdate(metadata == JNI_TRUE, state == JNI_TRUE,
                                     queue == JNI_TRUE);
}

static void sendFolderUpdateNative(JNIEnv* /* env */, jobject /* object */,
                                   jboolean available_players,
                                   jboolean addressed_player, jboolean uids) {
  log::debug("");
  std::unique_lock<std::shared_timed_mutex> interface_lock(interface_mutex);
  if (mServiceCallbacks == nullptr) {
    log::warn("Service not loaded.");
    return;
  }

  mServiceCallbacks->SendFolderUpdate(available_players == JNI_TRUE,
                                      addressed_player == JNI_TRUE,
                                      uids == JNI_TRUE);
}

static void cleanupNative(JNIEnv* env, jobject /* object */) {
  std::unique_lock<std::shared_timed_mutex> interface_lock(interface_mutex);
  std::unique_lock<std::shared_timed_mutex> callbacks_lock(callbacks_mutex);

  get_folder_items_cb_map.clear();
  volumeCallbackMap.clear();

  sServiceInterface->Cleanup();
  env->DeleteGlobalRef(mJavaInterface);
  mJavaInterface = nullptr;
  mServiceCallbacks = nullptr;
  sServiceInterface = nullptr;
}

jboolean connectDeviceNative(JNIEnv* env, jobject /* object */,
                             jstring address) {
  log::debug("");
  std::unique_lock<std::shared_timed_mutex> interface_lock(interface_mutex);
  if (mServiceCallbacks == nullptr) {
    log::warn("Service not loaded.");
    return JNI_FALSE;
  }

  const char* tmp_addr = env->GetStringUTFChars(address, 0);
  RawAddress bdaddr;
  bool success = RawAddress::FromString(tmp_addr, bdaddr);
  env->ReleaseStringUTFChars(address, tmp_addr);

  if (!success) return JNI_FALSE;

  return sServiceInterface->ConnectDevice(bdaddr) == true ? JNI_TRUE
                                                          : JNI_FALSE;
}

jboolean disconnectDeviceNative(JNIEnv* env, jobject /* object */,
                                jstring address) {
  log::debug("");
  std::unique_lock<std::shared_timed_mutex> interface_lock(interface_mutex);
  if (mServiceCallbacks == nullptr) {
    log::warn("Service not loaded.");
    return JNI_FALSE;
  }

  const char* tmp_addr = env->GetStringUTFChars(address, 0);
  RawAddress bdaddr;
  bool success = RawAddress::FromString(tmp_addr, bdaddr);
  env->ReleaseStringUTFChars(address, tmp_addr);

  if (!success) return JNI_FALSE;

  return sServiceInterface->DisconnectDevice(bdaddr) == true ? JNI_TRUE
                                                             : JNI_FALSE;
}

static void sendMediaKeyEvent(int key, KeyState state) {
  log::debug("");
  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || !mJavaInterface) return;
  sCallbackEnv->CallVoidMethod(
      mJavaInterface, method_sendMediaKeyEvent, key,
      state == KeyState::PUSHED ? JNI_TRUE : JNI_FALSE);
}

static std::string getImageHandleFromJavaObj(JNIEnv* env, jobject image) {
  std::string handle;

  if (image == nullptr) return handle;

  jclass class_image = env->GetObjectClass(image);
  jmethodID method_getImageHandle =
      env->GetMethodID(class_image, "getImageHandle", "()Ljava/lang/String;");
  jstring imageHandle = (jstring) env->CallObjectMethod(
      image, method_getImageHandle);
  if (imageHandle == nullptr) {
    return handle;
  }

  const char* value = env->GetStringUTFChars(imageHandle, nullptr);
  handle = std::string(value);
  env->ReleaseStringUTFChars(imageHandle, value);
  env->DeleteLocalRef(imageHandle);
  return handle;
}

static SongInfo getSongInfoFromJavaObj(JNIEnv* env, jobject metadata) {
  SongInfo info;

  if (metadata == nullptr) return info;

  jclass class_metadata = env->GetObjectClass(metadata);
  jfieldID field_mediaId =
      env->GetFieldID(class_metadata, "mediaId", "Ljava/lang/String;");
  jfieldID field_title =
      env->GetFieldID(class_metadata, "title", "Ljava/lang/String;");
  jfieldID field_artist =
      env->GetFieldID(class_metadata, "artist", "Ljava/lang/String;");
  jfieldID field_album =
      env->GetFieldID(class_metadata, "album", "Ljava/lang/String;");
  jfieldID field_trackNum =
      env->GetFieldID(class_metadata, "trackNum", "Ljava/lang/String;");
  jfieldID field_numTracks =
      env->GetFieldID(class_metadata, "numTracks", "Ljava/lang/String;");
  jfieldID field_genre =
      env->GetFieldID(class_metadata, "genre", "Ljava/lang/String;");
  jfieldID field_playingTime =
      env->GetFieldID(class_metadata, "duration", "Ljava/lang/String;");
  jfieldID field_image =
      env->GetFieldID(class_metadata, "image", "Lcom/android/bluetooth/audio_util/Image;");

  jstring jstr = (jstring)env->GetObjectField(metadata, field_mediaId);
  if (jstr != nullptr) {
    const char* value = env->GetStringUTFChars(jstr, nullptr);
    info.media_id = std::string(value);
    env->ReleaseStringUTFChars(jstr, value);
    env->DeleteLocalRef(jstr);
  }

  jstr = (jstring)env->GetObjectField(metadata, field_title);
  if (jstr != nullptr) {
    const char* value = env->GetStringUTFChars(jstr, nullptr);
    info.attributes.insert(
        AttributeEntry(Attribute::TITLE, std::string(value)));
    env->ReleaseStringUTFChars(jstr, value);
    env->DeleteLocalRef(jstr);
  }

  jstr = (jstring)env->GetObjectField(metadata, field_artist);
  if (jstr != nullptr) {
    const char* value = env->GetStringUTFChars(jstr, nullptr);
    info.attributes.insert(
        AttributeEntry(Attribute::ARTIST_NAME, std::string(value)));
    env->ReleaseStringUTFChars(jstr, value);
    env->DeleteLocalRef(jstr);
  }

  jstr = (jstring)env->GetObjectField(metadata, field_album);
  if (jstr != nullptr) {
    const char* value = env->GetStringUTFChars(jstr, nullptr);
    info.attributes.insert(
        AttributeEntry(Attribute::ALBUM_NAME, std::string(value)));
    env->ReleaseStringUTFChars(jstr, value);
    env->DeleteLocalRef(jstr);
  }

  jstr = (jstring)env->GetObjectField(metadata, field_trackNum);
  if (jstr != nullptr) {
    const char* value = env->GetStringUTFChars(jstr, nullptr);
    info.attributes.insert(
        AttributeEntry(Attribute::TRACK_NUMBER, std::string(value)));
    env->ReleaseStringUTFChars(jstr, value);
    env->DeleteLocalRef(jstr);
  }

  jstr = (jstring)env->GetObjectField(metadata, field_numTracks);
  if (jstr != nullptr) {
    const char* value = env->GetStringUTFChars(jstr, nullptr);
    info.attributes.insert(
        AttributeEntry(Attribute::TOTAL_NUMBER_OF_TRACKS, std::string(value)));
    env->ReleaseStringUTFChars(jstr, value);
    env->DeleteLocalRef(jstr);
  }

  jstr = (jstring)env->GetObjectField(metadata, field_genre);
  if (jstr != nullptr) {
    const char* value = env->GetStringUTFChars(jstr, nullptr);
    info.attributes.insert(
        AttributeEntry(Attribute::GENRE, std::string(value)));
    env->ReleaseStringUTFChars(jstr, value);
    env->DeleteLocalRef(jstr);
  }

  jstr = (jstring)env->GetObjectField(metadata, field_playingTime);
  if (jstr != nullptr) {
    const char* value = env->GetStringUTFChars(jstr, nullptr);
    info.attributes.insert(
        AttributeEntry(Attribute::PLAYING_TIME, std::string(value)));
    env->ReleaseStringUTFChars(jstr, value);
    env->DeleteLocalRef(jstr);
  }

  jobject object_image = env->GetObjectField(metadata, field_image);
  if (object_image != nullptr) {
    std::string imageHandle = getImageHandleFromJavaObj(env, object_image);
    if (!imageHandle.empty()) {
      info.attributes.insert(
          AttributeEntry(Attribute::DEFAULT_COVER_ART, imageHandle));
    }
    env->DeleteLocalRef(object_image);
  }

  return info;
}

static FolderInfo getFolderInfoFromJavaObj(JNIEnv* env, jobject folder) {
  FolderInfo info;

  jclass class_folder = env->GetObjectClass(folder);
  jfieldID field_mediaId =
      env->GetFieldID(class_folder, "mediaId", "Ljava/lang/String;");
  jfieldID field_isPlayable = env->GetFieldID(class_folder, "isPlayable", "Z");
  jfieldID field_name =
      env->GetFieldID(class_folder, "title", "Ljava/lang/String;");

  jstring jstr = (jstring)env->GetObjectField(folder, field_mediaId);
  if (jstr != nullptr) {
    const char* value = env->GetStringUTFChars(jstr, nullptr);
    info.media_id = std::string(value);
    env->ReleaseStringUTFChars(jstr, value);
    env->DeleteLocalRef(jstr);
  }

  info.is_playable = env->GetBooleanField(folder, field_isPlayable) == JNI_TRUE;

  jstr = (jstring)env->GetObjectField(folder, field_name);
  if (jstr != nullptr) {
    const char* value = env->GetStringUTFChars(jstr, nullptr);
    info.name = std::string(value);
    env->ReleaseStringUTFChars(jstr, value);
    env->DeleteLocalRef(jstr);
  }

  return info;
}

static SongInfo getSongInfo() {
  log::debug("");
  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || !mJavaInterface) return SongInfo();

  jobject metadata =
      sCallbackEnv->CallObjectMethod(mJavaInterface, method_getCurrentSongInfo);
  SongInfo info = getSongInfoFromJavaObj(sCallbackEnv.get(), metadata);
  sCallbackEnv->DeleteLocalRef(metadata);
  return info;
}

static PlayStatus getCurrentPlayStatus() {
  log::debug("");
  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || !mJavaInterface) return PlayStatus();

  PlayStatus status;
  jobject playStatus =
      sCallbackEnv->CallObjectMethod(mJavaInterface, method_getPlaybackStatus);

  if (playStatus == nullptr) {
    log::error("Got a null play status");
    return status;
  }

  jclass class_playStatus = sCallbackEnv->GetObjectClass(playStatus);
  jfieldID field_position =
      sCallbackEnv->GetFieldID(class_playStatus, "position", "J");
  jfieldID field_duration =
      sCallbackEnv->GetFieldID(class_playStatus, "duration", "J");
  jfieldID field_state =
      sCallbackEnv->GetFieldID(class_playStatus, "state", "B");

  status.position = sCallbackEnv->GetLongField(playStatus, field_position);
  status.duration = sCallbackEnv->GetLongField(playStatus, field_duration);
  status.state = (PlayState)sCallbackEnv->GetByteField(playStatus, field_state);

  sCallbackEnv->DeleteLocalRef(playStatus);

  return status;
}

static std::string getCurrentMediaId() {
  log::debug("");
  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || !mJavaInterface) return "";

  jstring media_id = (jstring)sCallbackEnv->CallObjectMethod(
      mJavaInterface, method_getCurrentMediaId);
  if (media_id == nullptr) {
    log::error("Got a null media ID");
    return "";
  }

  const char* value = sCallbackEnv->GetStringUTFChars(media_id, nullptr);
  std::string ret(value);
  sCallbackEnv->ReleaseStringUTFChars(media_id, value);
  sCallbackEnv->DeleteLocalRef(media_id);
  return ret;
}

static std::vector<SongInfo> getNowPlayingList() {
  log::debug("");
  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || !mJavaInterface) return std::vector<SongInfo>();

  jobject song_list =
      sCallbackEnv->CallObjectMethod(mJavaInterface, method_getNowPlayingList);
  if (song_list == nullptr) {
    log::error("Got a null now playing list");
    return std::vector<SongInfo>();
  }

  jclass class_list = sCallbackEnv->GetObjectClass(song_list);
  jmethodID method_get =
      sCallbackEnv->GetMethodID(class_list, "get", "(I)Ljava/lang/Object;");
  jmethodID method_size = sCallbackEnv->GetMethodID(class_list, "size", "()I");

  auto size = sCallbackEnv->CallIntMethod(song_list, method_size);
  if (size == 0) {
    sCallbackEnv->DeleteLocalRef(song_list);
    return std::vector<SongInfo>();
  }
  std::vector<SongInfo> ret;
  for (int i = 0; i < size; i++) {
    jobject song = sCallbackEnv->CallObjectMethod(song_list, method_get, i);
    ret.push_back(getSongInfoFromJavaObj(sCallbackEnv.get(), song));
    sCallbackEnv->DeleteLocalRef(song);
  }

  sCallbackEnv->DeleteLocalRef(song_list);

  return ret;
}

static uint16_t getCurrentPlayerId() {
  log::debug("");
  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || !mJavaInterface) return 0u;

  jint id =
      sCallbackEnv->CallIntMethod(mJavaInterface, method_getCurrentPlayerId);

  return (static_cast<int>(id) & 0xFFFF);
}

static std::vector<MediaPlayerInfo> getMediaPlayerList() {
  log::debug("");
  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || !mJavaInterface)
    return std::vector<MediaPlayerInfo>();

  jobject player_list = (jobject)sCallbackEnv->CallObjectMethod(
      mJavaInterface, method_getMediaPlayerList);

  if (player_list == nullptr) {
    log::error("Got a null media player list");
    return std::vector<MediaPlayerInfo>();
  }

  jclass class_list = sCallbackEnv->GetObjectClass(player_list);
  jmethodID method_get =
      sCallbackEnv->GetMethodID(class_list, "get", "(I)Ljava/lang/Object;");
  jmethodID method_size = sCallbackEnv->GetMethodID(class_list, "size", "()I");

  jint list_size = sCallbackEnv->CallIntMethod(player_list, method_size);
  if (list_size == 0) {
    sCallbackEnv->DeleteLocalRef(player_list);
    return std::vector<MediaPlayerInfo>();
  }

  jobject player_info =
      sCallbackEnv->CallObjectMethod(player_list, method_get, 0);
  jclass class_playerInfo = sCallbackEnv->GetObjectClass(player_info);
  jfieldID field_playerId =
      sCallbackEnv->GetFieldID(class_playerInfo, "id", "I");
  jfieldID field_name =
      sCallbackEnv->GetFieldID(class_playerInfo, "name", "Ljava/lang/String;");
  jfieldID field_browsable =
      sCallbackEnv->GetFieldID(class_playerInfo, "browsable", "Z");

  std::vector<MediaPlayerInfo> ret_list;
  for (jsize i = 0; i < list_size; i++) {
    jobject player = sCallbackEnv->CallObjectMethod(player_list, method_get, i);

    MediaPlayerInfo temp;
    temp.id = sCallbackEnv->GetIntField(player, field_playerId);

    jstring jstr = (jstring)sCallbackEnv->GetObjectField(player, field_name);
    if (jstr != nullptr) {
      const char* value = sCallbackEnv->GetStringUTFChars(jstr, nullptr);
      temp.name = std::string(value);
      sCallbackEnv->ReleaseStringUTFChars(jstr, value);
      sCallbackEnv->DeleteLocalRef(jstr);
    }

    temp.browsing_supported =
        sCallbackEnv->GetBooleanField(player, field_browsable) == JNI_TRUE
            ? true
            : false;

    ret_list.push_back(std::move(temp));
    sCallbackEnv->DeleteLocalRef(player);
  }

  sCallbackEnv->DeleteLocalRef(player_info);
  sCallbackEnv->DeleteLocalRef(player_list);

  return ret_list;
}

static void setBrowsedPlayer(uint16_t player_id, SetBrowsedPlayerCb cb) {
  log::debug("");
  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || !mJavaInterface) return;

  set_browsed_player_cb = cb;
  sCallbackEnv->CallVoidMethod(mJavaInterface, method_setBrowsedPlayer,
                               player_id);
}

static void setBrowsedPlayerResponseNative(JNIEnv* env, jobject /* object */,
                                           jint /* player_id */,
                                           jboolean success, jstring root_id,
                                           jint num_items) {
  log::debug("");

  std::string root;
  if (root_id != nullptr) {
    const char* value = env->GetStringUTFChars(root_id, nullptr);
    root = std::string(value);
    env->ReleaseStringUTFChars(root_id, value);
  }

  set_browsed_player_cb.Run(success == JNI_TRUE, root, num_items);
}

static void getFolderItemsResponseNative(JNIEnv* env, jobject /* object */,
                                         jstring parent_id, jobject list) {
  log::debug("");

  std::string id;
  if (parent_id != nullptr) {
    const char* value = env->GetStringUTFChars(parent_id, nullptr);
    id = std::string(value);
    env->ReleaseStringUTFChars(parent_id, value);
  }

  // TODO (apanicke): Right now browsing will fail on a second device if two
  // devices browse the same folder. Use a MultiMap to fix this behavior so
  // that both callbacks can be handled with one lookup if a request comes
  // for a folder that is already trying to be looked at.
  if (get_folder_items_cb_map.find(id) == get_folder_items_cb_map.end()) {
    log::error("Could not find response callback for the request of \"{}\"",
               id);
    return;
  }

  auto callback = get_folder_items_cb_map.find(id)->second;
  get_folder_items_cb_map.erase(id);

  if (list == nullptr) {
    log::error("Got a null get folder items response list");
    callback.Run(std::vector<ListItem>());
    return;
  }

  jclass class_list = env->GetObjectClass(list);
  jmethodID method_get =
      env->GetMethodID(class_list, "get", "(I)Ljava/lang/Object;");
  jmethodID method_size = env->GetMethodID(class_list, "size", "()I");

  jint list_size = env->CallIntMethod(list, method_size);
  if (list_size == 0) {
    callback.Run(std::vector<ListItem>());
    return;
  }

  jobject list_item = env->CallObjectMethod(list, method_get, 0);
  jclass class_listItem = env->GetObjectClass(list_item);
  jfieldID field_isFolder = env->GetFieldID(class_listItem, "isFolder", "Z");
  jfieldID field_folder = env->GetFieldID(
      class_listItem, "folder", "Lcom/android/bluetooth/audio_util/Folder;");
  jfieldID field_song = env->GetFieldID(
      class_listItem, "song", "Lcom/android/bluetooth/audio_util/Metadata;");

  std::vector<ListItem> ret_list;
  for (jsize i = 0; i < list_size; i++) {
    jobject item = env->CallObjectMethod(list, method_get, i);

    bool is_folder = env->GetBooleanField(item, field_isFolder) == JNI_TRUE;

    if (is_folder) {
      jobject folder = env->GetObjectField(item, field_folder);
      ListItem temp = {ListItem::FOLDER,
                       getFolderInfoFromJavaObj(env, folder),
                       SongInfo()};
      ret_list.push_back(temp);
      env->DeleteLocalRef(folder);
    } else {
      jobject song = env->GetObjectField(item, field_song);
      ListItem temp = {ListItem::SONG, FolderInfo(),
                       getSongInfoFromJavaObj(env, song)};
      ret_list.push_back(temp);
      env->DeleteLocalRef(song);
    }
    env->DeleteLocalRef(item);
  }

  env->DeleteLocalRef(list_item);

  callback.Run(std::move(ret_list));
}

static void getFolderItems(uint16_t player_id, std::string media_id,
                           GetFolderItemsCb cb) {
  log::debug("");
  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || !mJavaInterface) return;

  // TODO (apanicke): Fix a potential media_id collision if two media players
  // use the same media_id scheme or two devices browse the same content.
  get_folder_items_cb_map.insert(map_entry(media_id, cb));

  jstring j_media_id = sCallbackEnv->NewStringUTF(media_id.c_str());
  sCallbackEnv->CallVoidMethod(mJavaInterface, method_getFolderItemsRequest,
                               player_id, j_media_id);
}

static void playItem(uint16_t player_id, bool now_playing,
                     std::string media_id) {
  log::debug("");
  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || !mJavaInterface) return;

  jstring j_media_id = sCallbackEnv->NewStringUTF(media_id.c_str());
  sCallbackEnv->CallVoidMethod(mJavaInterface, method_playItem, player_id,
                               now_playing ? JNI_TRUE : JNI_FALSE, j_media_id);
}

static void setActiveDevice(const RawAddress& address) {
  log::debug("");
  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || !mJavaInterface) return;

  jstring j_bdaddr = sCallbackEnv->NewStringUTF(address.ToString().c_str());
  sCallbackEnv->CallVoidMethod(mJavaInterface, method_setActiveDevice,
                               j_bdaddr);
}

static void volumeDeviceConnected(const RawAddress& address) {
  log::debug("");
  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || !mJavaInterface) return;

  jstring j_bdaddr = sCallbackEnv->NewStringUTF(address.ToString().c_str());
  sCallbackEnv->CallVoidMethod(mJavaInterface, method_volumeDeviceConnected,
                               j_bdaddr, JNI_FALSE);
}

static void volumeDeviceConnected(
    const RawAddress& address,
    ::bluetooth::avrcp::VolumeInterface::VolumeChangedCb cb) {
  log::debug("");
  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || !mJavaInterface) return;

  volumeCallbackMap.emplace(address, cb);

  jstring j_bdaddr = sCallbackEnv->NewStringUTF(address.ToString().c_str());
  sCallbackEnv->CallVoidMethod(mJavaInterface, method_volumeDeviceConnected,
                               j_bdaddr, JNI_TRUE);
}

static void volumeDeviceDisconnected(const RawAddress& address) {
  log::debug("");
  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || !mJavaInterface) return;

  volumeCallbackMap.erase(address);

  jstring j_bdaddr = sCallbackEnv->NewStringUTF(address.ToString().c_str());
  sCallbackEnv->CallVoidMethod(mJavaInterface, method_volumeDeviceDisconnected,
                               j_bdaddr);
}

static void sendVolumeChangedNative(JNIEnv* env, jobject /* object */,
                                    jstring address, jint volume) {
  const char* tmp_addr = env->GetStringUTFChars(address, 0);
  RawAddress bdaddr;
  bool success = RawAddress::FromString(tmp_addr, bdaddr);
  env->ReleaseStringUTFChars(address, tmp_addr);

  if (!success) return;

  log::debug("");
  if (volumeCallbackMap.find(bdaddr) != volumeCallbackMap.end()) {
    volumeCallbackMap.find(bdaddr)->second.Run(volume & 0x7F);
  }
}

static void setVolume(int8_t volume) {
  log::debug("");
  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || !mJavaInterface) return;

  sCallbackEnv->CallVoidMethod(mJavaInterface, method_setVolume, volume);
}

static void setBipClientStatusNative(JNIEnv* env, jobject /* object */,
                                     jstring address, jboolean connected) {
  std::unique_lock<std::shared_timed_mutex> interface_lock(interface_mutex);
  if (mServiceCallbacks == nullptr) {
    log::warn("Service not loaded.");
    return;
  }

  const char* tmp_addr = env->GetStringUTFChars(address, 0);
  RawAddress bdaddr;
  bool success = RawAddress::FromString(tmp_addr, bdaddr);
  env->ReleaseStringUTFChars(address, tmp_addr);

  if (!success) return;

  bool status = (connected == JNI_TRUE);
  sServiceInterface->SetBipClientStatus(bdaddr, status);
}

// Called from native to list available player settings
static void listPlayerSettings(ListPlayerSettingsCb cb) {
  log::debug("");
  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || !mJavaInterface) return;

  list_player_settings_cb = std::move(cb);
  sCallbackEnv->CallVoidMethod(mJavaInterface, method_listPlayerSettings);
}

static void listPlayerSettingsResponseNative(JNIEnv* env, jobject /* object */,
                                             jbyteArray attributes) {
  log::debug("");

  std::vector<PlayerAttribute> attributes_vector;
  copyJavaArraytoCppVector(env, attributes, attributes_vector);

  list_player_settings_cb.Run(std::move(attributes_vector));
}

// Called from native to list available values for player setting
static void listPlayerSettingValues(PlayerAttribute attribute,
                                    ListPlayerSettingValuesCb cb) {
  log::debug("");
  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || !mJavaInterface) return;

  list_player_setting_values_cb = std::move(cb);
  sCallbackEnv->CallVoidMethod(mJavaInterface, method_listPlayerSettingValues,
                               (jbyte)attribute);
}

static void listPlayerSettingValuesResponseNative(JNIEnv* env,
                                                  jobject /* object */,
                                                  jbyte attribute,
                                                  jbyteArray values) {
  log::debug("");
  PlayerAttribute player_attribute = static_cast<PlayerAttribute>(attribute);
  std::vector<uint8_t> values_vector;
  copyJavaArraytoCppVector(env, values, values_vector);
  list_player_setting_values_cb.Run(player_attribute, std::move(values_vector));
}

// Called from native to get current player settings
static void getPlayerSettings(std::vector<PlayerAttribute> attributes,
                              GetCurrentPlayerSettingValueCb cb) {
  log::debug("");
  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || !mJavaInterface) return;

  jbyteArray attributes_array = sCallbackEnv->NewByteArray(attributes.size());
  sCallbackEnv->SetByteArrayRegion(
      attributes_array, 0, attributes.size(),
      reinterpret_cast<const jbyte*>(attributes.data()));

  get_current_player_setting_value_cb = std::move(cb);
  sCallbackEnv->CallVoidMethod(mJavaInterface, method_getPlayerSettings,
                               attributes_array);
}

static void getPlayerSettingsResponseNative(JNIEnv* env, jobject /* object */,
                                            jbyteArray attributes,
                                            jbyteArray values) {
  log::debug("");
  std::vector<PlayerAttribute> attributes_vector;
  std::vector<uint8_t> values_vector;
  copyJavaArraytoCppVector(env, attributes, attributes_vector);
  copyJavaArraytoCppVector(env, values, values_vector);
  get_current_player_setting_value_cb.Run(std::move(attributes_vector),
                                          std::move(values_vector));
}

// Called from native to set current player settings
static void setPlayerSettings(std::vector<PlayerAttribute> attributes,
                              std::vector<uint8_t> values,
                              SetPlayerSettingValueCb cb) {
  log::debug("");
  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || !mJavaInterface) return;

  jbyteArray attributes_array = sCallbackEnv->NewByteArray(attributes.size());
  sCallbackEnv->SetByteArrayRegion(
      attributes_array, 0, attributes.size(),
      reinterpret_cast<const jbyte*>(attributes.data()));

  jbyteArray values_array = sCallbackEnv->NewByteArray(values.size());
  sCallbackEnv->SetByteArrayRegion(
      values_array, 0, values.size(),
      reinterpret_cast<const jbyte*>(values.data()));

  set_player_setting_value_cb = std::move(cb);

  sCallbackEnv->CallVoidMethod(mJavaInterface, method_setPlayerSettings,
                               attributes_array, values_array);
}

static void setPlayerSettingsResponseNative(JNIEnv* /* env */,
                                            jobject /* object */,
                                            jboolean success) {
  log::debug("");
  set_player_setting_value_cb.Run(success);
}

static void sendPlayerSettingsNative(JNIEnv* env, jobject /* object */,
                                     jbyteArray attributes, jbyteArray values) {
  log::debug("");
  std::unique_lock<std::shared_timed_mutex> interface_lock(interface_mutex);
  if (mServiceCallbacks == nullptr) {
    log::warn("Service not loaded.");
    return;
  }
  std::vector<PlayerAttribute> attributes_vector;
  std::vector<uint8_t> values_vector;
  copyJavaArraytoCppVector(env, attributes, attributes_vector);
  copyJavaArraytoCppVector(env, values, values_vector);
  mServiceCallbacks->SendPlayerSettingsChanged(attributes_vector,
                                               values_vector);
}

int register_com_android_bluetooth_avrcp_target(JNIEnv* env) {
  const JNINativeMethod methods[] = {
      {"initNative", "()V", (void*)initNative},
      {"registerBipServerNative", "(I)V", (void*)registerBipServerNative},
      {"unregisterBipServerNative", "()V", (void*)unregisterBipServerNative},
      {"sendMediaUpdateNative", "(ZZZ)V", (void*)sendMediaUpdateNative},
      {"sendFolderUpdateNative", "(ZZZ)V", (void*)sendFolderUpdateNative},
      {"setBrowsedPlayerResponseNative", "(IZLjava/lang/String;I)V",
       (void*)setBrowsedPlayerResponseNative},
      {"getFolderItemsResponseNative", "(Ljava/lang/String;Ljava/util/List;)V",
       (void*)getFolderItemsResponseNative},
      {"cleanupNative", "()V", (void*)cleanupNative},
      {"connectDeviceNative", "(Ljava/lang/String;)Z",
       (void*)connectDeviceNative},
      {"disconnectDeviceNative", "(Ljava/lang/String;)Z",
       (void*)disconnectDeviceNative},
      {"sendVolumeChangedNative", "(Ljava/lang/String;I)V",
       (void*)sendVolumeChangedNative},
      {"setBipClientStatusNative", "(Ljava/lang/String;Z)V",
       (void*)setBipClientStatusNative},
      {"listPlayerSettingsResponseNative", "([B)V",
       (void*)listPlayerSettingsResponseNative},
      {"listPlayerSettingValuesResponseNative", "(B[B)V",
       (void*)listPlayerSettingValuesResponseNative},
      {"getPlayerSettingsResponseNative", "([B[B)V",
       (void*)getPlayerSettingsResponseNative},
      {"setPlayerSettingsResponseNative", "(Z)V",
       (void*)setPlayerSettingsResponseNative},
      {"sendPlayerSettingsNative", "([B[B)V", (void*)sendPlayerSettingsNative},
  };
  const int result = REGISTER_NATIVE_METHODS(
      env, "com/android/bluetooth/avrcp/AvrcpNativeInterface", methods);
  if (result != 0) {
    return result;
  }

  const JNIJavaMethod javaMethods[] = {
      {"getCurrentSongInfo", "()Lcom/android/bluetooth/audio_util/Metadata;",
       &method_getCurrentSongInfo},
      {"getPlayStatus", "()Lcom/android/bluetooth/audio_util/PlayStatus;",
       &method_getPlaybackStatus},
      {"sendMediaKeyEvent", "(IZ)V", &method_sendMediaKeyEvent},
      {"getCurrentMediaId", "()Ljava/lang/String;", &method_getCurrentMediaId},
      {"getNowPlayingList", "()Ljava/util/List;", &method_getNowPlayingList},
      {"getCurrentPlayerId", "()I", &method_getCurrentPlayerId},
      {"getMediaPlayerList", "()Ljava/util/List;", &method_getMediaPlayerList},
      {"setBrowsedPlayer", "(I)V", &method_setBrowsedPlayer},
      {"getFolderItemsRequest", "(ILjava/lang/String;)V",
       &method_getFolderItemsRequest},
      {"playItem", "(IZLjava/lang/String;)V", &method_playItem},
      {"setActiveDevice", "(Ljava/lang/String;)V", &method_setActiveDevice},
      {"deviceConnected", "(Ljava/lang/String;Z)V",
       &method_volumeDeviceConnected},
      {"deviceDisconnected", "(Ljava/lang/String;)V",
       &method_volumeDeviceDisconnected},
      {"setVolume", "(I)V", &method_setVolume},
      {"listPlayerSettingsRequest", "()V", &method_listPlayerSettings},
      {"listPlayerSettingValuesRequest", "(B)V",
       &method_listPlayerSettingValues},
      {"getCurrentPlayerSettingValuesRequest", "([B)V",
       &method_getPlayerSettings},
      {"setPlayerSettingsRequest", "([B[B)V", &method_setPlayerSettings},
  };
  GET_JAVA_METHODS(env, "com/android/bluetooth/avrcp/AvrcpNativeInterface",
                   javaMethods);

  return 0;
}

}  // namespace android
