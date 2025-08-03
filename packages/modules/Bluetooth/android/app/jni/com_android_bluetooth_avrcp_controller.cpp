/*
 * Copyright (C) 2016 The Android Open Source Project
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

#define LOG_TAG "BluetoothAvrcpControllerJni"

#include "com_android_bluetooth.h"
#include "hardware/bt_rc.h"
#include "utils/Log.h"

#include <string.h>
#include <shared_mutex>

namespace android {
static jmethodID method_onConnectionStateChanged;
static jmethodID method_handleplayerappsetting;
static jmethodID method_handleplayerappsettingchanged;
static jmethodID method_handleSetAbsVolume;
static jmethodID method_handleRegisterNotificationAbsVol;
static jmethodID method_handletrackchanged;
static jmethodID method_handleplaypositionchanged;
static jmethodID method_handleplaystatuschanged;
static jmethodID method_handleGetFolderItemsRsp;
static jmethodID method_handleGetPlayerItemsRsp;
static jmethodID method_createFromNativeMediaItem;
static jmethodID method_createFromNativeFolderItem;
static jmethodID method_createFromNativePlayerItem;
static jmethodID method_handleChangeFolderRsp;
static jmethodID method_handleSetBrowsedPlayerRsp;
static jmethodID method_handleSetAddressedPlayerRsp;
static jmethodID method_handleAddressedPlayerChanged;
static jmethodID method_handleNowPlayingContentChanged;
static jmethodID method_onAvailablePlayerChanged;
static jmethodID method_getRcPsm;

static jclass class_AvrcpControllerNativeInterface;
static jclass class_AvrcpItem;
static jclass class_AvrcpPlayer;

static const btrc_ctrl_interface_t* sBluetoothAvrcpInterface = NULL;
static jobject sCallbacksObj = NULL;
static std::shared_timed_mutex sCallbacks_mutex;

static void btavrcp_passthrough_response_callback(
    const RawAddress& /* bd_addr */, int id, int pressed) {
  log::verbose("id: {}, pressed: {} --- Not implemented", id, pressed);
}

static void btavrcp_groupnavigation_response_callback(int id, int pressed) {
  log::verbose("id: {}, pressed: {} --- Not implemented", id, pressed);
}

static void btavrcp_connection_state_callback(bool rc_connect, bool br_connect,
                                              const RawAddress& bd_addr) {
  log::info("conn state: rc: {} br: {}", rc_connect, br_connect);
  std::shared_lock<std::shared_timed_mutex> lock(sCallbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  if (!sCallbacksObj) {
    log::error("sCallbacksObj is null");
    return;
  }

  ScopedLocalRef<jbyteArray> addr(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
  if (!addr.get()) {
    log::error("Failed to allocate a new byte array");
    return;
  }

  sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                   (jbyte*)bd_addr.address);
  sCallbackEnv->CallVoidMethod(sCallbacksObj, method_onConnectionStateChanged,
                               (jboolean)rc_connect, (jboolean)br_connect,
                               addr.get());
}

static void btavrcp_get_rcfeatures_callback(const RawAddress& /* bd_addr */,
                                            int /* features */) {
  log::verbose("--- Not implemented");
}
static void btavrcp_setplayerapplicationsetting_rsp_callback(
    const RawAddress& /* bd_addr */, uint8_t /* accepted */) {
  log::verbose("--- Not implemented");
}

static void btavrcp_playerapplicationsetting_callback(
    const RawAddress& bd_addr, uint8_t num_attr,
    btrc_player_app_attr_t* app_attrs, uint8_t /* num_ext_attr */,
    btrc_player_app_ext_attr_t* /* ext_attrs */) {
  log::info("");
  std::shared_lock<std::shared_timed_mutex> lock(sCallbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  if (!sCallbacksObj) {
    log::error("sCallbacksObj is null");
    return;
  }

  ScopedLocalRef<jbyteArray> addr(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
  if (!addr.get()) {
    log::error("Failed to allocate a new byte array");
    return;
  }
  sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                   (jbyte*)&bd_addr.address);
  /* TODO ext attrs
   * Flattening defined attributes: <id,num_values,values[]>
   */
  jint arraylen = 0;
  for (int i = 0; i < num_attr; i++) {
    /*2 bytes for id and num */
    arraylen += 2 + app_attrs[i].num_val;
  }
  log::verbose(" arraylen {}", arraylen);

  ScopedLocalRef<jbyteArray> playerattribs(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(arraylen));
  if (!playerattribs.get()) {
    log::error("Failed to allocate a new byte array");
    return;
  }

  for (int i = 0, k = 0; (i < num_attr) && (k < arraylen); i++) {
    sCallbackEnv->SetByteArrayRegion(playerattribs.get(), k, 1,
                                     (jbyte*)&(app_attrs[i].attr_id));
    k++;
    sCallbackEnv->SetByteArrayRegion(playerattribs.get(), k, 1,
                                     (jbyte*)&(app_attrs[i].num_val));
    k++;
    sCallbackEnv->SetByteArrayRegion(playerattribs.get(), k,
                                     app_attrs[i].num_val,
                                     (jbyte*)(app_attrs[i].attr_val));
    k = k + app_attrs[i].num_val;
  }
  sCallbackEnv->CallVoidMethod(sCallbacksObj, method_handleplayerappsetting,
                               addr.get(), playerattribs.get(), (jint)arraylen);
}

static void btavrcp_playerapplicationsetting_changed_callback(
    const RawAddress& bd_addr, const btrc_player_settings_t& vals) {
  log::info("");
  std::shared_lock<std::shared_timed_mutex> lock(sCallbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  if (!sCallbacksObj) {
    log::error("sCallbacksObj is null");
    return;
  }

  ScopedLocalRef<jbyteArray> addr(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
  if (!addr.get()) {
    log::error("Failed to allocate a new byte array");
    return;
  }
  sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                   (jbyte*)&bd_addr.address);

  int arraylen = vals.num_attr * 2;
  ScopedLocalRef<jbyteArray> playerattribs(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(arraylen));
  if (!playerattribs.get()) {
    log::error("Fail to new jbyteArray playerattribs ");
    return;
  }
  /*
   * Flatening format: <id,val>
   */
  for (int i = 0, k = 0; (i < vals.num_attr) && (k < arraylen); i++) {
    sCallbackEnv->SetByteArrayRegion(playerattribs.get(), k, 1,
                                     (jbyte*)&(vals.attr_ids[i]));
    k++;
    sCallbackEnv->SetByteArrayRegion(playerattribs.get(), k, 1,
                                     (jbyte*)&(vals.attr_values[i]));
    k++;
  }
  sCallbackEnv->CallVoidMethod(sCallbacksObj,
                               method_handleplayerappsettingchanged, addr.get(),
                               playerattribs.get(), (jint)arraylen);
}

static void btavrcp_set_abs_vol_cmd_callback(const RawAddress& bd_addr,
                                             uint8_t abs_vol, uint8_t label) {
  log::info("");
  std::shared_lock<std::shared_timed_mutex> lock(sCallbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  if (!sCallbacksObj) {
    log::error("sCallbacksObj is null");
    return;
  }

  ScopedLocalRef<jbyteArray> addr(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
  if (!addr.get()) {
    log::error("Failed to allocate a new byte array");
    return;
  }

  sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                   (jbyte*)&bd_addr.address);
  sCallbackEnv->CallVoidMethod(sCallbacksObj, method_handleSetAbsVolume,
                               addr.get(), (jbyte)abs_vol, (jbyte)label);
}

static void btavrcp_register_notification_absvol_callback(
    const RawAddress& bd_addr, uint8_t label) {
  log::info("");
  std::shared_lock<std::shared_timed_mutex> lock(sCallbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  if (!sCallbacksObj) {
    log::error("sCallbacksObj is null");
    return;
  }

  ScopedLocalRef<jbyteArray> addr(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
  if (!addr.get()) {
    log::error("Failed to allocate a new byte array");
    return;
  }

  sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                   (jbyte*)&bd_addr.address);
  sCallbackEnv->CallVoidMethod(sCallbacksObj,
                               method_handleRegisterNotificationAbsVol,
                               addr.get(), (jbyte)label);
}

static void btavrcp_track_changed_callback(const RawAddress& bd_addr,
                                           uint8_t num_attr,
                                           btrc_element_attr_val_t* p_attrs) {
  /*
   * byteArray will be formatted like this: id,len,string
   * Assuming text feild to be null terminated.
   */
  log::info("");
  std::shared_lock<std::shared_timed_mutex> lock(sCallbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  if (!sCallbacksObj) {
    log::error("sCallbacksObj is null");
    return;
  }

  ScopedLocalRef<jbyteArray> addr(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
  if (!addr.get()) {
    log::error("Failed to allocate a new byte array");
    return;
  }

  ScopedLocalRef<jintArray> attribIds(sCallbackEnv.get(),
                                      sCallbackEnv->NewIntArray(num_attr));
  if (!attribIds.get()) {
    log::error(" failed to set new array for attribIds");
    return;
  }
  sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                   (jbyte*)&bd_addr.address);

  jclass strclazz = sCallbackEnv->FindClass("java/lang/String");
  ScopedLocalRef<jobjectArray> stringArray(
      sCallbackEnv.get(),
      sCallbackEnv->NewObjectArray((jint)num_attr, strclazz, 0));
  if (!stringArray.get()) {
    log::error(" failed to get String array");
    return;
  }

  for (jint i = 0; i < num_attr; i++) {
    ScopedLocalRef<jstring> str(
        sCallbackEnv.get(),
        sCallbackEnv->NewStringUTF((char*)(p_attrs[i].text)));
    if (!str.get()) {
      log::error("Unable to get str");
      return;
    }
    sCallbackEnv->SetIntArrayRegion(attribIds.get(), i, 1,
                                    (jint*)&(p_attrs[i].attr_id));
    sCallbackEnv->SetObjectArrayElement(stringArray.get(), i, str.get());
  }

  sCallbackEnv->CallVoidMethod(sCallbacksObj, method_handletrackchanged,
                               addr.get(), (jbyte)(num_attr), attribIds.get(),
                               stringArray.get());
}

static void btavrcp_play_position_changed_callback(const RawAddress& bd_addr,
                                                   uint32_t song_len,
                                                   uint32_t song_pos) {
  log::info("");
  std::shared_lock<std::shared_timed_mutex> lock(sCallbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  if (!sCallbacksObj) {
    log::error("sCallbacksObj is null");
    return;
  }

  ScopedLocalRef<jbyteArray> addr(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
  if (!addr.get()) {
    log::error("Failed to allocate a new byte array");
    return;
  }
  sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                   (jbyte*)&bd_addr.address);
  sCallbackEnv->CallVoidMethod(sCallbacksObj, method_handleplaypositionchanged,
                               addr.get(), (jint)(song_len), (jint)song_pos);
}

static void btavrcp_play_status_changed_callback(
    const RawAddress& bd_addr, btrc_play_status_t play_status) {
  log::info("");
  std::shared_lock<std::shared_timed_mutex> lock(sCallbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  if (!sCallbacksObj) {
    log::error("sCallbacksObj is null");
    return;
  }

  ScopedLocalRef<jbyteArray> addr(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
  if (!addr.get()) {
    log::error("Failed to allocate a new byte array");
    return;
  }
  sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                   (jbyte*)&bd_addr.address);
  sCallbackEnv->CallVoidMethod(sCallbacksObj, method_handleplaystatuschanged,
                               addr.get(), (jbyte)play_status);
}

static void btavrcp_get_folder_items_callback(
    const RawAddress& bd_addr, btrc_status_t status,
    const btrc_folder_items_t* folder_items, uint8_t count) {
  /* Folder items are list of items that can be either BTRC_ITEM_PLAYER
   * BTRC_ITEM_MEDIA, BTRC_ITEM_FOLDER. Here we translate them to their java
   * counterparts by calling the java constructor for each of the items.
   */
  log::verbose("count {}", count);
  std::shared_lock<std::shared_timed_mutex> lock(sCallbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  if (!sCallbacksObj) {
    log::error("sCallbacksObj is null");
    return;
  }

  ScopedLocalRef<jbyteArray> addr(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
  if (!addr.get()) {
    log::error("Failed to allocate a new byte array");
    return;
  }

  sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                   (jbyte*)&bd_addr.address);

  // Inspect if the first element is a folder/item or player listing. They are
  // always exclusive.
  bool isPlayerListing =
      count > 0 && (folder_items[0].item_type == BTRC_ITEM_PLAYER);

  // Initialize arrays for Folder OR Player listing.
  ScopedLocalRef<jobjectArray> itemArray(sCallbackEnv.get(), NULL);
  if (isPlayerListing) {
    itemArray.reset(
        sCallbackEnv->NewObjectArray((jint)count, class_AvrcpPlayer, 0));
  } else {
    itemArray.reset(sCallbackEnv->NewObjectArray(
        (jint)count, class_AvrcpItem, 0));
  }
  if (!itemArray.get()) {
    log::error("itemArray allocation failed.");
    return;
  }
  for (int i = 0; i < count; i++) {
    const btrc_folder_items_t* item = &(folder_items[i]);
    log::verbose("item type {}", item->item_type);
    switch (item->item_type) {
      case BTRC_ITEM_MEDIA: {
        // Parse name
        ScopedLocalRef<jstring> mediaName(
            sCallbackEnv.get(),
            sCallbackEnv->NewStringUTF((const char*)item->media.name));
        if (!mediaName.get()) {
          log::error("can't allocate media name string!");
          return;
        }
        // Parse UID
        long long uid = *(long long*)item->media.uid;
        // Parse Attrs
        ScopedLocalRef<jintArray> attrIdArray(
            sCallbackEnv.get(),
            sCallbackEnv->NewIntArray(item->media.num_attrs));
        if (!attrIdArray.get()) {
          log::error("can't allocate attr id array!");
          return;
        }
        ScopedLocalRef<jobjectArray> attrValArray(
            sCallbackEnv.get(),
            sCallbackEnv->NewObjectArray(
                item->media.num_attrs,
                sCallbackEnv->FindClass("java/lang/String"), 0));
        if (!attrValArray.get()) {
          log::error("can't allocate attr val array!");
          return;
        }

        for (int j = 0; j < item->media.num_attrs; j++) {
          sCallbackEnv->SetIntArrayRegion(
              attrIdArray.get(), j, 1,
              (jint*)&(item->media.p_attrs[j].attr_id));
          ScopedLocalRef<jstring> attrValStr(
              sCallbackEnv.get(),
              sCallbackEnv->NewStringUTF((char*)(item->media.p_attrs[j].text)));
          sCallbackEnv->SetObjectArrayElement(attrValArray.get(), j,
                                              attrValStr.get());
        }

        ScopedLocalRef<jobject> mediaObj(
            sCallbackEnv.get(),
            (jobject)sCallbackEnv->CallStaticObjectMethod(
                class_AvrcpControllerNativeInterface,
                method_createFromNativeMediaItem, addr.get(), uid,
                (jint)item->media.type, mediaName.get(), attrIdArray.get(),
                attrValArray.get()));
        if (!mediaObj.get()) {
          log::error("failed to create AvrcpItem for type ITEM_MEDIA");
          return;
        }
        sCallbackEnv->SetObjectArrayElement(itemArray.get(), i, mediaObj.get());
        break;
      }

      case BTRC_ITEM_FOLDER: {
        // Parse name
        ScopedLocalRef<jstring> folderName(
            sCallbackEnv.get(),
            sCallbackEnv->NewStringUTF((const char*)item->folder.name));
        if (!folderName.get()) {
          log::error("can't allocate folder name string!");
          return;
        }
        // Parse UID
        long long uid = *(long long*)item->folder.uid;
        ScopedLocalRef<jobject> folderObj(
            sCallbackEnv.get(),
            (jobject)sCallbackEnv->CallStaticObjectMethod(
                class_AvrcpControllerNativeInterface,
                method_createFromNativeFolderItem, addr.get(), uid,
                (jint)item->folder.type, folderName.get(),
                (jint)item->folder.playable));
        if (!folderObj.get()) {
          log::error("failed to create AvrcpItem for type ITEM_FOLDER");
          return;
        }
        sCallbackEnv->SetObjectArrayElement(itemArray.get(), i,
                                            folderObj.get());
        break;
      }

      case BTRC_ITEM_PLAYER: {
        // Parse name
        isPlayerListing = true;
        jint id = (jint)item->player.player_id;
        jint playerType = (jint)item->player.major_type;
        jint playStatus = (jint)item->player.play_status;
        ScopedLocalRef<jbyteArray> featureBitArray(
            sCallbackEnv.get(),
            sCallbackEnv->NewByteArray(BTRC_FEATURE_BIT_MASK_SIZE *
                                       sizeof(uint8_t)));
        if (!featureBitArray.get()) {
          log::error("failed to allocate featureBitArray");
          return;
        }
        sCallbackEnv->SetByteArrayRegion(
            featureBitArray.get(), 0,
            sizeof(uint8_t) * BTRC_FEATURE_BIT_MASK_SIZE,
            (jbyte*)item->player.features);
        ScopedLocalRef<jstring> playerName(
            sCallbackEnv.get(),
            sCallbackEnv->NewStringUTF((const char*)item->player.name));
        if (!playerName.get()) {
          log::error("can't allocate player name string!");
          return;
        }
        ScopedLocalRef<jobject> playerObj(
            sCallbackEnv.get(),
            (jobject)sCallbackEnv->CallStaticObjectMethod(
                class_AvrcpControllerNativeInterface,
                method_createFromNativePlayerItem, addr.get(), id,
                playerName.get(), featureBitArray.get(), playStatus,
                playerType));
        if (!playerObj.get()) {
          log::error("failed to create AvrcpPlayer from ITEM_PLAYER");
          return;
        }
        sCallbackEnv->SetObjectArrayElement(itemArray.get(), i,
                                            playerObj.get());
        break;
      }

      default:
        log::error("cannot understand type {}", item->item_type);
    }
  }

  if (isPlayerListing) {
    sCallbackEnv->CallVoidMethod(sCallbacksObj, method_handleGetPlayerItemsRsp,
                                 addr.get(), itemArray.get());
  } else {
    sCallbackEnv->CallVoidMethod(sCallbacksObj, method_handleGetFolderItemsRsp,
                                 addr.get(), status, itemArray.get());
  }
}

static void btavrcp_change_path_callback(const RawAddress& bd_addr,
                                         uint32_t count) {
  log::info("count {}", count);
  std::shared_lock<std::shared_timed_mutex> lock(sCallbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  if (!sCallbacksObj) {
    log::error("sCallbacksObj is null");
    return;
  }
  ScopedLocalRef<jbyteArray> addr(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
  if (!addr.get()) {
    log::error("Failed to allocate a new byte array");
    return;
  }

  sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                   (jbyte*)&bd_addr.address);

  sCallbackEnv->CallVoidMethod(sCallbacksObj, method_handleChangeFolderRsp,
                               addr.get(), (jint)count);
}

static void btavrcp_set_browsed_player_callback(const RawAddress& bd_addr,
                                                uint8_t num_items,
                                                uint8_t depth) {
  log::info("items {} depth {}", num_items, depth);
  std::shared_lock<std::shared_timed_mutex> lock(sCallbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  if (!sCallbacksObj) {
    log::error("sCallbacksObj is null");
    return;
  }
  ScopedLocalRef<jbyteArray> addr(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
  if (!addr.get()) {
    log::error("Failed to allocate a new byte array");
    return;
  }

  sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                   (jbyte*)&bd_addr.address);

  sCallbackEnv->CallVoidMethod(sCallbacksObj, method_handleSetBrowsedPlayerRsp,
                               addr.get(), (jint)num_items, (jint)depth);
}

static void btavrcp_set_addressed_player_callback(const RawAddress& bd_addr,
                                                  uint8_t status) {
  log::info("status {}", status);
  std::shared_lock<std::shared_timed_mutex> lock(sCallbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  if (!sCallbacksObj) {
    log::error("sCallbacksObj is null");
    return;
  }
  ScopedLocalRef<jbyteArray> addr(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
  if (!addr.get()) {
    log::error("Failed to allocate a new byte array");
    return;
  }

  sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                   (jbyte*)&bd_addr.address);

  sCallbackEnv->CallVoidMethod(sCallbacksObj,
                               method_handleSetAddressedPlayerRsp, addr.get(),
                               (jint)status);
}

static void btavrcp_addressed_player_changed_callback(const RawAddress& bd_addr,
                                                      uint16_t id) {
  log::info("status {}", id);
  std::shared_lock<std::shared_timed_mutex> lock(sCallbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  if (!sCallbacksObj) {
    log::error("sCallbacksObj is null");
    return;
  }
  ScopedLocalRef<jbyteArray> addr(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
  if (!addr.get()) {
    log::error("Failed to allocate a new byte array");
    return;
  }

  sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                   (jbyte*)&bd_addr.address);

  sCallbackEnv->CallVoidMethod(
      sCallbacksObj, method_handleAddressedPlayerChanged, addr.get(), (jint)id);
}

static void btavrcp_now_playing_content_changed_callback(
    const RawAddress& bd_addr) {
  log::info("");

  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  ScopedLocalRef<jbyteArray> addr(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
  if (!addr.get()) {
    log::error("Failed to allocate a new byte array");
    return;
  }

  sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                   (jbyte*)&bd_addr.address);

  sCallbackEnv->CallVoidMethod(
      sCallbacksObj, method_handleNowPlayingContentChanged, addr.get());
}

static void btavrcp_available_player_changed_callback (
    const RawAddress& bd_addr) {
  log::info("");
  std::shared_lock<std::shared_timed_mutex> lock(sCallbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbacksObj) {
    log::error("sCallbacksObj is null");
    return;
  }
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jbyteArray> addr(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
  if (!addr.get()) {
    log::error("Failed to allocate a new byte array");
    return;
  }

  sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                    (jbyte*)&bd_addr);
  sCallbackEnv->CallVoidMethod(
      sCallbacksObj, method_onAvailablePlayerChanged, addr.get());
}

static void btavrcp_get_rcpsm_callback(const RawAddress& bd_addr,
                                       uint16_t psm) {
  log::error("-> psm received of {}", psm);
  std::shared_lock<std::shared_timed_mutex> lock(sCallbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbacksObj) {
    log::error("sCallbacksObj is null");
    return;
  }
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jbyteArray> addr(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
  if (!addr.get()) {
    log::error("Failed to allocate a new byte array");
    return;
  }

  sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                   (jbyte*)&bd_addr.address);
  sCallbackEnv->CallVoidMethod(sCallbacksObj, method_getRcPsm, addr.get(),
                               (jint)psm);
}

static btrc_ctrl_callbacks_t sBluetoothAvrcpCallbacks = {
    sizeof(sBluetoothAvrcpCallbacks),
    btavrcp_passthrough_response_callback,
    btavrcp_groupnavigation_response_callback,
    btavrcp_connection_state_callback,
    btavrcp_get_rcfeatures_callback,
    btavrcp_setplayerapplicationsetting_rsp_callback,
    btavrcp_playerapplicationsetting_callback,
    btavrcp_playerapplicationsetting_changed_callback,
    btavrcp_set_abs_vol_cmd_callback,
    btavrcp_register_notification_absvol_callback,
    btavrcp_track_changed_callback,
    btavrcp_play_position_changed_callback,
    btavrcp_play_status_changed_callback,
    btavrcp_get_folder_items_callback,
    btavrcp_change_path_callback,
    btavrcp_set_browsed_player_callback,
    btavrcp_set_addressed_player_callback,
    btavrcp_addressed_player_changed_callback,
    btavrcp_now_playing_content_changed_callback,
    btavrcp_available_player_changed_callback,
    btavrcp_get_rcpsm_callback,
};

static void initNative(JNIEnv* env, jobject object) {
  std::unique_lock<std::shared_timed_mutex> lock(sCallbacks_mutex);

  jclass tmpAvrcpItem =
      env->FindClass("com/android/bluetooth/avrcpcontroller/AvrcpItem");
  class_AvrcpItem = (jclass)env->NewGlobalRef(tmpAvrcpItem);

  jclass tmpBtPlayer =
      env->FindClass("com/android/bluetooth/avrcpcontroller/AvrcpPlayer");
  class_AvrcpPlayer = (jclass)env->NewGlobalRef(tmpBtPlayer);

  jclass tmpControllerInterface = env->FindClass(
      "com/android/bluetooth/avrcpcontroller/AvrcpControllerNativeInterface");
  class_AvrcpControllerNativeInterface =
      (jclass)env->NewGlobalRef(tmpControllerInterface);

  const bt_interface_t* btInf = getBluetoothInterface();
  if (btInf == NULL) {
    log::error("Bluetooth module is not loaded");
    return;
  }

  if (sBluetoothAvrcpInterface != NULL) {
    log::warn("Cleaning up Avrcp Interface before initializing...");
    sBluetoothAvrcpInterface->cleanup();
    sBluetoothAvrcpInterface = NULL;
  }

  if (sCallbacksObj != NULL) {
    log::warn("Cleaning up Avrcp callback object");
    env->DeleteGlobalRef(sCallbacksObj);
    sCallbacksObj = NULL;
  }

  sBluetoothAvrcpInterface =
      (btrc_ctrl_interface_t*)btInf->get_profile_interface(
          BT_PROFILE_AV_RC_CTRL_ID);
  if (sBluetoothAvrcpInterface == NULL) {
    log::error("Failed to get Bluetooth Avrcp Controller Interface");
    return;
  }

  bt_status_t status =
      sBluetoothAvrcpInterface->init(&sBluetoothAvrcpCallbacks);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed to initialize Bluetooth Avrcp Controller, status: {}",
               bt_status_text(status));
    sBluetoothAvrcpInterface = NULL;
    return;
  }

  sCallbacksObj = env->NewGlobalRef(object);
}

static void cleanupNative(JNIEnv* env, jobject /* object */) {
  std::unique_lock<std::shared_timed_mutex> lock(sCallbacks_mutex);

  const bt_interface_t* btInf = getBluetoothInterface();
  if (btInf == NULL) {
    log::error("Bluetooth module is not loaded");
    return;
  }

  if (sBluetoothAvrcpInterface != NULL) {
    sBluetoothAvrcpInterface->cleanup();
    sBluetoothAvrcpInterface = NULL;
  }

  if (sCallbacksObj != NULL) {
    env->DeleteGlobalRef(sCallbacksObj);
    sCallbacksObj = NULL;
  }
}

static jboolean sendPassThroughCommandNative(JNIEnv* env, jobject /* object */,
                                             jbyteArray address, jint key_code,
                                             jint key_state) {
  if (!sBluetoothAvrcpInterface) return JNI_FALSE;

  log::info("sBluetoothAvrcpInterface: {}", fmt::ptr(sBluetoothAvrcpInterface));

  log::info("key_code: {}, key_state: {}", key_code, key_state);

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  RawAddress rawAddress;
  rawAddress.FromOctets((uint8_t*)addr);
  bt_status_t status = sBluetoothAvrcpInterface->send_pass_through_cmd(
      rawAddress, (uint8_t)key_code, (uint8_t)key_state);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed sending passthru command, status: {}",
               bt_status_text(status));
  }
  env->ReleaseByteArrayElements(address, addr, 0);

  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean sendGroupNavigationCommandNative(JNIEnv* env,
                                                 jobject /* object */,
                                                 jbyteArray address,
                                                 jint key_code,
                                                 jint key_state) {
  if (!sBluetoothAvrcpInterface) return JNI_FALSE;

  log::info("sBluetoothAvrcpInterface: {}", fmt::ptr(sBluetoothAvrcpInterface));

  log::info("key_code: {}, key_state: {}", key_code, key_state);

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }
  RawAddress rawAddress;
  rawAddress.FromOctets((uint8_t*)addr);

  bt_status_t status = sBluetoothAvrcpInterface->send_group_navigation_cmd(
      rawAddress, (uint8_t)key_code, (uint8_t)key_state);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed sending Grp Navigation command, status: {}",
               bt_status_text(status));
  }
  env->ReleaseByteArrayElements(address, addr, 0);

  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static void setPlayerApplicationSettingValuesNative(
    JNIEnv* env, jobject /* object */, jbyteArray address, jbyte num_attrib,
    jbyteArray attrib_ids, jbyteArray attrib_val) {
  log::info("sBluetoothAvrcpInterface: {}", fmt::ptr(sBluetoothAvrcpInterface));
  if (!sBluetoothAvrcpInterface) return;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return;
  }

  uint8_t* pAttrs = new uint8_t[num_attrib];
  uint8_t* pAttrsVal = new uint8_t[num_attrib];
  if ((!pAttrs) || (!pAttrsVal)) {
    delete[] pAttrs;
    log::error(
        "setPlayerApplicationSettingValuesNative: not have enough memory");
    return;
  }

  jbyte* attr = env->GetByteArrayElements(attrib_ids, NULL);
  jbyte* attr_val = env->GetByteArrayElements(attrib_val, NULL);
  if ((!attr) || (!attr_val)) {
    delete[] pAttrs;
    delete[] pAttrsVal;
    jniThrowIOException(env, EINVAL);
    return;
  }

  int i;
  for (i = 0; i < num_attrib; ++i) {
    pAttrs[i] = (uint8_t)attr[i];
    pAttrsVal[i] = (uint8_t)attr_val[i];
  }
  RawAddress rawAddress;
  rawAddress.FromOctets((uint8_t*)addr);

  bt_status_t status = sBluetoothAvrcpInterface->set_player_app_setting_cmd(
      rawAddress, (uint8_t)num_attrib, pAttrs, pAttrsVal);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed sending setPlAppSettValNative command, status: {}",
               bt_status_text(status));
  }
  delete[] pAttrs;
  delete[] pAttrsVal;
  env->ReleaseByteArrayElements(attrib_ids, attr, 0);
  env->ReleaseByteArrayElements(attrib_val, attr_val, 0);
  env->ReleaseByteArrayElements(address, addr, 0);
}

static void sendAbsVolRspNative(JNIEnv* env, jobject /* object */,
                                jbyteArray address, jint abs_vol, jint label) {
  if (!sBluetoothAvrcpInterface) return;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return;
  }

  log::info("sBluetoothAvrcpInterface: {}", fmt::ptr(sBluetoothAvrcpInterface));
  RawAddress rawAddress;
  rawAddress.FromOctets((uint8_t*)addr);

  bt_status_t status = sBluetoothAvrcpInterface->set_volume_rsp(
      rawAddress, (uint8_t)abs_vol, (uint8_t)label);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed sending sendAbsVolRspNative command, status: {}",
               bt_status_text(status));
  }
  env->ReleaseByteArrayElements(address, addr, 0);
}

static void sendRegisterAbsVolRspNative(JNIEnv* env, jobject /* object */,
                                        jbyteArray address, jbyte rsp_type,
                                        jint abs_vol, jint label) {
  if (!sBluetoothAvrcpInterface) return;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return;
  }
  log::info("sBluetoothAvrcpInterface: {}", fmt::ptr(sBluetoothAvrcpInterface));
  RawAddress rawAddress;
  rawAddress.FromOctets((uint8_t*)addr);

  bt_status_t status = sBluetoothAvrcpInterface->register_abs_vol_rsp(
      rawAddress, (btrc_notification_type_t)rsp_type, (uint8_t)abs_vol,
      (uint8_t)label);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed sending sendRegisterAbsVolRspNative command, status: {}",
               bt_status_text(status));
  }
  env->ReleaseByteArrayElements(address, addr, 0);
}

static void getCurrentMetadataNative(JNIEnv* env, jobject /* object */,
                                     jbyteArray address) {
  if (!sBluetoothAvrcpInterface) return;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return;
  }
  log::verbose("sBluetoothAvrcpInterface: {}",
               fmt::ptr(sBluetoothAvrcpInterface));
  RawAddress rawAddress;
  rawAddress.FromOctets((uint8_t*)addr);

  bt_status_t status =
      sBluetoothAvrcpInterface->get_current_metadata_cmd(rawAddress);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed sending getCurrentMetadataNative command, status: {}",
               bt_status_text(status));
  }
  env->ReleaseByteArrayElements(address, addr, 0);
}

static void getPlaybackStateNative(JNIEnv* env, jobject /* object */,
                                   jbyteArray address) {
  if (!sBluetoothAvrcpInterface) return;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return;
  }
  log::verbose("sBluetoothAvrcpInterface: {}",
               fmt::ptr(sBluetoothAvrcpInterface));
  RawAddress rawAddress;
  rawAddress.FromOctets((uint8_t*)addr);

  bt_status_t status =
      sBluetoothAvrcpInterface->get_playback_state_cmd(rawAddress);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed sending getPlaybackStateNative command, status: {}",
               bt_status_text(status));
  }
  env->ReleaseByteArrayElements(address, addr, 0);
}

static void getNowPlayingListNative(JNIEnv* env, jobject /* object */,
                                    jbyteArray address, jint start, jint end) {
  if (!sBluetoothAvrcpInterface) return;
  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return;
  }
  log::verbose("sBluetoothAvrcpInterface: {}",
               fmt::ptr(sBluetoothAvrcpInterface));
  RawAddress rawAddress;
  rawAddress.FromOctets((uint8_t*)addr);

  bt_status_t status = sBluetoothAvrcpInterface->get_now_playing_list_cmd(
      rawAddress, start, end);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed sending getNowPlayingListNative command, status: {}",
               bt_status_text(status));
  }
  env->ReleaseByteArrayElements(address, addr, 0);
}

static void getFolderListNative(JNIEnv* env, jobject /* object */,
                                jbyteArray address, jint start, jint end) {
  if (!sBluetoothAvrcpInterface) return;
  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return;
  }
  log::verbose("sBluetoothAvrcpInterface: {}",
               fmt::ptr(sBluetoothAvrcpInterface));
  RawAddress rawAddress;
  rawAddress.FromOctets((uint8_t*)addr);

  bt_status_t status =
      sBluetoothAvrcpInterface->get_folder_list_cmd(rawAddress, start, end);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed sending getFolderListNative command, status: {}",
               bt_status_text(status));
  }
  env->ReleaseByteArrayElements(address, addr, 0);
}

static void getPlayerListNative(JNIEnv* env, jobject /* object */,
                                jbyteArray address, jint start, jint end) {
  if (!sBluetoothAvrcpInterface) return;
  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return;
  }
  log::info("sBluetoothAvrcpInterface: {}", fmt::ptr(sBluetoothAvrcpInterface));
  RawAddress rawAddress;
  rawAddress.FromOctets((uint8_t*)addr);

  bt_status_t status =
      sBluetoothAvrcpInterface->get_player_list_cmd(rawAddress, start, end);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed sending getPlayerListNative command, status: {}",
               bt_status_text(status));
  }
  env->ReleaseByteArrayElements(address, addr, 0);
}

static void changeFolderPathNative(JNIEnv* env, jobject /* object */,
                                   jbyteArray address, jbyte direction,
                                   jlong uid) {
  if (!sBluetoothAvrcpInterface) return;
  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return;
  }

  // jbyte* uid = env->GetByteArrayElements(uidarr, NULL);
  // if (!uid) {
  //  jniThrowIOException(env, EINVAL);
  //  return;
  //}

  log::info("sBluetoothAvrcpInterface: {}", fmt::ptr(sBluetoothAvrcpInterface));
  RawAddress rawAddress;
  rawAddress.FromOctets((uint8_t*)addr);

  bt_status_t status = sBluetoothAvrcpInterface->change_folder_path_cmd(
      rawAddress, (uint8_t)direction, (uint8_t*)&uid);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed sending changeFolderPathNative command, status: {}",
               bt_status_text(status));
  }
  // env->ReleaseByteArrayElements(address, addr, 0);
}

static void setBrowsedPlayerNative(JNIEnv* env, jobject /* object */,
                                   jbyteArray address, jint id) {
  if (!sBluetoothAvrcpInterface) return;
  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return;
  }
  RawAddress rawAddress;
  rawAddress.FromOctets((uint8_t*)addr);

  log::info("sBluetoothAvrcpInterface: {}", fmt::ptr(sBluetoothAvrcpInterface));
  bt_status_t status = sBluetoothAvrcpInterface->set_browsed_player_cmd(
      rawAddress, (uint16_t)id);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed sending setBrowsedPlayerNative command, status: {}",
               bt_status_text(status));
  }
  env->ReleaseByteArrayElements(address, addr, 0);
}

static void setAddressedPlayerNative(JNIEnv* env, jobject /* object */,
                                     jbyteArray address, jint id) {
  if (!sBluetoothAvrcpInterface) return;
  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return;
  }
  RawAddress rawAddress;
  rawAddress.FromOctets((uint8_t*)addr);

  log::info("sBluetoothAvrcpInterface: {}", fmt::ptr(sBluetoothAvrcpInterface));
  bt_status_t status = sBluetoothAvrcpInterface->set_addressed_player_cmd(
      rawAddress, (uint16_t)id);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed sending setAddressedPlayerNative command, status: {}",
               bt_status_text(status));
  }
  env->ReleaseByteArrayElements(address, addr, 0);
}

static void playItemNative(JNIEnv* env, jobject /* object */,
                           jbyteArray address, jbyte scope, jlong uid,
                           jint uidCounter) {
  if (!sBluetoothAvrcpInterface) return;
  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return;
  }

  //  jbyte* uid = env->GetByteArrayElements(uidArr, NULL);
  //  if (!uid) {
  //    jniThrowIOException(env, EINVAL);
  //    return;
  //  }
  RawAddress rawAddress;
  rawAddress.FromOctets((uint8_t*)addr);

  log::info("sBluetoothAvrcpInterface: {}", fmt::ptr(sBluetoothAvrcpInterface));
  bt_status_t status = sBluetoothAvrcpInterface->play_item_cmd(
      rawAddress, (uint8_t)scope, (uint8_t*)&uid, (uint16_t)uidCounter);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed sending playItemNative command, status: {}",
               bt_status_text(status));
  }
  env->ReleaseByteArrayElements(address, addr, 0);
}

int register_com_android_bluetooth_avrcp_controller(JNIEnv* env) {
  const JNINativeMethod methods[] = {
      {"initNative", "()V", (void*)initNative},
      {"cleanupNative", "()V", (void*)cleanupNative},
      {"sendPassThroughCommandNative", "([BII)Z",
       (void*)sendPassThroughCommandNative},
      {"sendGroupNavigationCommandNative", "([BII)Z",
       (void*)sendGroupNavigationCommandNative},
      {"setPlayerApplicationSettingValuesNative", "([BB[B[B)V",
       (void*)setPlayerApplicationSettingValuesNative},
      {"sendAbsVolRspNative", "([BII)V", (void*)sendAbsVolRspNative},
      {"sendRegisterAbsVolRspNative", "([BBII)V",
       (void*)sendRegisterAbsVolRspNative},
      {"getCurrentMetadataNative", "([B)V", (void*)getCurrentMetadataNative},
      {"getPlaybackStateNative", "([B)V", (void*)getPlaybackStateNative},
      {"getNowPlayingListNative", "([BII)V", (void*)getNowPlayingListNative},
      {"getFolderListNative", "([BII)V", (void*)getFolderListNative},
      {"getPlayerListNative", "([BII)V", (void*)getPlayerListNative},
      {"changeFolderPathNative", "([BBJ)V", (void*)changeFolderPathNative},
      {"playItemNative", "([BBJI)V", (void*)playItemNative},
      {"setBrowsedPlayerNative", "([BI)V", (void*)setBrowsedPlayerNative},
      {"setAddressedPlayerNative", "([BI)V", (void*)setAddressedPlayerNative},
  };
  const int result = REGISTER_NATIVE_METHODS(
      env,
      "com/android/bluetooth/avrcpcontroller/AvrcpControllerNativeInterface",
      methods);
  if (result != 0) {
    return result;
  }

  const JNIJavaMethod javaMethods[] = {
      {"onConnectionStateChanged", "(ZZ[B)V", &method_onConnectionStateChanged},
      {"getRcPsm", "([BI)V", &method_getRcPsm},
      {"handlePlayerAppSetting", "([B[BI)V", &method_handleplayerappsetting},
      {"onPlayerAppSettingChanged", "([B[BI)V",
       &method_handleplayerappsettingchanged},
      {"handleSetAbsVolume", "([BBB)V", &method_handleSetAbsVolume},
      {"handleRegisterNotificationAbsVol", "([BB)V",
       &method_handleRegisterNotificationAbsVol},
      {"onTrackChanged", "([BB[I[Ljava/lang/String;)V",
       &method_handletrackchanged},
      {"onPlayPositionChanged", "([BII)V", &method_handleplaypositionchanged},
      {"onPlayStatusChanged", "([BB)V", &method_handleplaystatuschanged},
      {"handleGetFolderItemsRsp",
       "([BI[Lcom/android/bluetooth/avrcpcontroller/AvrcpItem;)V",
       &method_handleGetFolderItemsRsp},
      {"handleGetPlayerItemsRsp",
       "([B[Lcom/android/bluetooth/avrcpcontroller/AvrcpPlayer;)V",
       &method_handleGetPlayerItemsRsp},
      {"handleChangeFolderRsp", "([BI)V", &method_handleChangeFolderRsp},
      {"handleSetBrowsedPlayerRsp", "([BII)V",
       &method_handleSetBrowsedPlayerRsp},
      {"handleSetAddressedPlayerRsp", "([BI)V",
       &method_handleSetAddressedPlayerRsp},
      {"handleAddressedPlayerChanged", "([BI)V",
       &method_handleAddressedPlayerChanged},
      {"handleNowPlayingContentChanged", "([B)V",
       &method_handleNowPlayingContentChanged},
      {"onAvailablePlayerChanged", "([B)V", &method_onAvailablePlayerChanged},
      // Fetch static method
      {"createFromNativeMediaItem",
       "([BJILjava/lang/String;[I[Ljava/lang/String;)"
       "Lcom/android/bluetooth/avrcpcontroller/AvrcpItem;",
       &method_createFromNativeMediaItem, true},
      {"createFromNativeFolderItem",
       "([BJILjava/lang/String;I)"
       "Lcom/android/bluetooth/avrcpcontroller/AvrcpItem;",
       &method_createFromNativeFolderItem, true},
      {"createFromNativePlayerItem",
       "([BILjava/lang/String;[BII)"
       "Lcom/android/bluetooth/avrcpcontroller/AvrcpPlayer;",
       &method_createFromNativePlayerItem, true},
  };
  GET_JAVA_METHODS(
      env,
      "com/android/bluetooth/avrcpcontroller/AvrcpControllerNativeInterface",
      javaMethods);
  return 0;
}
}  // namespace android
