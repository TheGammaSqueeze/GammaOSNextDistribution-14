/*
 * Copyright (c) 2014 The Android Open Source Project
 * Copyright (C) 2012 The Android Open Source Project
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

#define LOG_TAG "BluetoothHeadsetClientServiceJni"

#include <shared_mutex>

#include "com_android_bluetooth.h"
#include "hardware/bt_hf_client.h"
#include "os/logging/log_adapter.h"

namespace android {

static bthf_client_interface_t* sBluetoothHfpClientInterface = NULL;
static std::shared_mutex interface_mutex;

static jobject mCallbacksObj = NULL;
static std::shared_mutex callbacks_mutex;

static jmethodID method_onConnectionStateChanged;
static jmethodID method_onAudioStateChanged;
static jmethodID method_onVrStateChanged;
static jmethodID method_onNetworkState;
static jmethodID method_onNetworkRoaming;
static jmethodID method_onNetworkSignal;
static jmethodID method_onBatteryLevel;
static jmethodID method_onCurrentOperator;
static jmethodID method_onCall;
static jmethodID method_onCallSetup;
static jmethodID method_onCallHeld;
static jmethodID method_onRespAndHold;
static jmethodID method_onClip;
static jmethodID method_onCallWaiting;
static jmethodID method_onCurrentCalls;
static jmethodID method_onVolumeChange;
static jmethodID method_onCmdResult;
static jmethodID method_onSubscriberInfo;
static jmethodID method_onInBandRing;
static jmethodID method_onLastVoiceTagNumber;
static jmethodID method_onRingIndication;
static jmethodID method_onUnknownEvent;

static jbyteArray marshall_bda(const RawAddress* bd_addr) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return NULL;

  jbyteArray addr = sCallbackEnv->NewByteArray(sizeof(RawAddress));
  if (!addr) {
    log::error("Fail to new jbyteArray bd addr");
    return NULL;
  }
  sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(RawAddress),
                                   (jbyte*)bd_addr);
  return addr;
}

static void connection_state_cb(const RawAddress* bd_addr,
                                bthf_client_connection_state_t state,
                                unsigned int peer_feat,
                                unsigned int chld_feat) {
  std::shared_lock<std::shared_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;

  log::debug("state {} peer_feat {} chld_feat {}", state, peer_feat, chld_feat);
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onConnectionStateChanged,
                               (jint)state, (jint)peer_feat, (jint)chld_feat,
                               addr.get());
}

static void audio_state_cb(const RawAddress* bd_addr,
                           bthf_client_audio_state_t state) {
  std::shared_lock<std::shared_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onAudioStateChanged,
                               (jint)state, addr.get());
}

static void vr_cmd_cb(const RawAddress* bd_addr, bthf_client_vr_state_t state) {
  std::shared_lock<std::shared_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onVrStateChanged,
                               (jint)state, addr.get());
}

static void network_state_cb(const RawAddress* bd_addr,
                             bthf_client_network_state_t state) {
  std::shared_lock<std::shared_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onNetworkState,
                               (jint)state, addr.get());
}

static void network_roaming_cb(const RawAddress* bd_addr,
                               bthf_client_service_type_t type) {
  std::shared_lock<std::shared_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onNetworkRoaming,
                               (jint)type, addr.get());
}

static void network_signal_cb(const RawAddress* bd_addr, int signal) {
  std::shared_lock<std::shared_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onNetworkSignal,
                               (jint)signal, addr.get());
}

static void battery_level_cb(const RawAddress* bd_addr, int level) {
  std::shared_lock<std::shared_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onBatteryLevel,
                               (jint)level, addr.get());
}

static void current_operator_cb(const RawAddress* bd_addr, const char* name) {
  std::shared_lock<std::shared_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;

  const char null_str[] = "";
  if (!sCallbackEnv.isValidUtf(name)) {
    log::error("name is not a valid UTF string.");
    name = null_str;
  }

  ScopedLocalRef<jstring> js_name(sCallbackEnv.get(),
                                  sCallbackEnv->NewStringUTF(name));
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onCurrentOperator,
                               js_name.get(), addr.get());
}

static void call_cb(const RawAddress* bd_addr, bthf_client_call_t call) {
  std::shared_lock<std::shared_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onCall, (jint)call,
                               addr.get());
}

static void callsetup_cb(const RawAddress* bd_addr,
                         bthf_client_callsetup_t callsetup) {
  std::shared_lock<std::shared_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;

  log::debug("callsetup_cb bdaddr {}", ADDRESS_TO_LOGGABLE_STR(*bd_addr));

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onCallSetup,
                               (jint)callsetup, addr.get());
}

static void callheld_cb(const RawAddress* bd_addr,
                        bthf_client_callheld_t callheld) {
  std::shared_lock<std::shared_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onCallHeld, (jint)callheld,
                               addr.get());
}

static void resp_and_hold_cb(const RawAddress* bd_addr,
                             bthf_client_resp_and_hold_t resp_and_hold) {
  std::shared_lock<std::shared_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onRespAndHold,
                               (jint)resp_and_hold, addr.get());
}

static void clip_cb(const RawAddress* bd_addr, const char* number) {
  std::shared_lock<std::shared_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;

  const char null_str[] = "";
  if (!sCallbackEnv.isValidUtf(number)) {
    log::error("number is not a valid UTF string.");
    number = null_str;
  }

  ScopedLocalRef<jstring> js_number(sCallbackEnv.get(),
                                    sCallbackEnv->NewStringUTF(number));
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onClip, js_number.get(),
                               addr.get());
}

static void call_waiting_cb(const RawAddress* bd_addr, const char* number) {
  std::shared_lock<std::shared_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;

  const char null_str[] = "";
  if (!sCallbackEnv.isValidUtf(number)) {
    log::error("number is not a valid UTF string.");
    number = null_str;
  }

  ScopedLocalRef<jstring> js_number(sCallbackEnv.get(),
                                    sCallbackEnv->NewStringUTF(number));
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onCallWaiting,
                               js_number.get(), addr.get());
}

static void current_calls_cb(const RawAddress* bd_addr, int index,
                             bthf_client_call_direction_t dir,
                             bthf_client_call_state_t state,
                             bthf_client_call_mpty_type_t mpty,
                             const char* number) {
  std::shared_lock<std::shared_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;

  const char null_str[] = "";
  if (!sCallbackEnv.isValidUtf(number)) {
    log::error("number is not a valid UTF string.");
    number = null_str;
  }

  ScopedLocalRef<jstring> js_number(sCallbackEnv.get(),
                                    sCallbackEnv->NewStringUTF(number));
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onCurrentCalls, index, dir,
                               state, mpty, js_number.get(), addr.get());
}

static void volume_change_cb(const RawAddress* bd_addr,
                             bthf_client_volume_type_t type, int volume) {
  std::shared_lock<std::shared_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onVolumeChange, (jint)type,
                               (jint)volume, addr.get());
}

static void cmd_complete_cb(const RawAddress* bd_addr,
                            bthf_client_cmd_complete_t type, int cme) {
  std::shared_lock<std::shared_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onCmdResult, (jint)type,
                               (jint)cme, addr.get());
}

static void subscriber_info_cb(const RawAddress* bd_addr, const char* name,
                               bthf_client_subscriber_service_type_t type) {
  std::shared_lock<std::shared_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;

  const char null_str[] = "";
  if (!sCallbackEnv.isValidUtf(name)) {
    log::error("name is not a valid UTF string.");
    name = null_str;
  }

  ScopedLocalRef<jstring> js_name(sCallbackEnv.get(),
                                  sCallbackEnv->NewStringUTF(name));
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onSubscriberInfo,
                               js_name.get(), (jint)type, addr.get());
}

static void in_band_ring_cb(const RawAddress* bd_addr,
                            bthf_client_in_band_ring_state_t in_band) {
  std::shared_lock<std::shared_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onInBandRing,
                               (jint)in_band, addr.get());
}

static void last_voice_tag_number_cb(const RawAddress* bd_addr,
                                     const char* number) {
  std::shared_lock<std::shared_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;

  const char null_str[] = "";
  if (!sCallbackEnv.isValidUtf(number)) {
    log::error("number is not a valid UTF string.");
    number = null_str;
  }

  ScopedLocalRef<jstring> js_number(sCallbackEnv.get(),
                                    sCallbackEnv->NewStringUTF(number));
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onLastVoiceTagNumber,
                               js_number.get(), addr.get());
}

static void ring_indication_cb(const RawAddress* bd_addr) {
  std::shared_lock<std::shared_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onRingIndication,
                               addr.get());
}

static void unknown_event_cb(const RawAddress* bd_addr,
                             const char* eventString) {
  std::shared_lock<std::shared_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;

  ScopedLocalRef<jstring> js_event(sCallbackEnv.get(),
                                   sCallbackEnv->NewStringUTF(eventString));
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onUnknownEvent,
                               js_event.get(), addr.get());
}

static bthf_client_callbacks_t sBluetoothHfpClientCallbacks = {
    sizeof(sBluetoothHfpClientCallbacks),
    connection_state_cb,
    audio_state_cb,
    vr_cmd_cb,
    network_state_cb,
    network_roaming_cb,
    network_signal_cb,
    battery_level_cb,
    current_operator_cb,
    call_cb,
    callsetup_cb,
    callheld_cb,
    resp_and_hold_cb,
    clip_cb,
    call_waiting_cb,
    current_calls_cb,
    volume_change_cb,
    cmd_complete_cb,
    subscriber_info_cb,
    in_band_ring_cb,
    last_voice_tag_number_cb,
    ring_indication_cb,
    unknown_event_cb,
};

static void initializeNative(JNIEnv* env, jobject object) {
  log::debug("HfpClient");
  std::unique_lock<std::shared_mutex> interface_lock(interface_mutex);
  std::unique_lock<std::shared_mutex> callbacks_lock(callbacks_mutex);

  const bt_interface_t* btInf = getBluetoothInterface();
  if (btInf == NULL) {
    log::error("Bluetooth module is not loaded");
    return;
  }

  if (sBluetoothHfpClientInterface != NULL) {
    log::warn("Cleaning up Bluetooth HFP Client Interface before initializing");
    sBluetoothHfpClientInterface->cleanup();
    sBluetoothHfpClientInterface = NULL;
  }

  if (mCallbacksObj != NULL) {
    log::warn("Cleaning up Bluetooth HFP Client callback object");
    env->DeleteGlobalRef(mCallbacksObj);
    mCallbacksObj = NULL;
  }

  sBluetoothHfpClientInterface =
      (bthf_client_interface_t*)btInf->get_profile_interface(
          BT_PROFILE_HANDSFREE_CLIENT_ID);
  if (sBluetoothHfpClientInterface == NULL) {
    log::error("Failed to get Bluetooth HFP Client Interface");
    return;
  }

  bt_status_t status =
      sBluetoothHfpClientInterface->init(&sBluetoothHfpClientCallbacks);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed to initialize Bluetooth HFP Client, status: {}",
               bt_status_text(status));
    sBluetoothHfpClientInterface = NULL;
    return;
  }

  mCallbacksObj = env->NewGlobalRef(object);
}

static void cleanupNative(JNIEnv* env, jobject /* object */) {
  std::unique_lock<std::shared_mutex> interface_lock(interface_mutex);
  std::unique_lock<std::shared_mutex> callbacks_lock(callbacks_mutex);

  const bt_interface_t* btInf = getBluetoothInterface();
  if (btInf == NULL) {
    log::error("Bluetooth module is not loaded");
    return;
  }

  if (sBluetoothHfpClientInterface != NULL) {
    log::warn("Cleaning up Bluetooth HFP Client Interface...");
    sBluetoothHfpClientInterface->cleanup();
    sBluetoothHfpClientInterface = NULL;
  }

  if (mCallbacksObj != NULL) {
    log::warn("Cleaning up Bluetooth HFP Client callback object");
    env->DeleteGlobalRef(mCallbacksObj);
    mCallbacksObj = NULL;
  }
}

static jboolean connectNative(JNIEnv* env, jobject /* object */,
                              jbyteArray address) {
  std::shared_lock<std::shared_mutex> lock(interface_mutex);
  if (!sBluetoothHfpClientInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status =
      sBluetoothHfpClientInterface->connect((const RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed AG connection, status: {}", bt_status_text(status));
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean disconnectNative(JNIEnv* env, jobject /* object */,
                                 jbyteArray address) {
  std::shared_lock<std::shared_mutex> lock(interface_mutex);
  if (!sBluetoothHfpClientInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status =
      sBluetoothHfpClientInterface->disconnect((const RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed AG disconnection, status: {}", bt_status_text(status));
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean connectAudioNative(JNIEnv* env, jobject /* object */,
                                   jbyteArray address) {
  std::shared_lock<std::shared_mutex> lock(interface_mutex);
  if (!sBluetoothHfpClientInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status =
      sBluetoothHfpClientInterface->connect_audio((const RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed AG audio connection, status: {}",
               bt_status_text(status));
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean disconnectAudioNative(JNIEnv* env, jobject /* object */,
                                      jbyteArray address) {
  std::shared_lock<std::shared_mutex> lock(interface_mutex);
  if (!sBluetoothHfpClientInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status =
      sBluetoothHfpClientInterface->disconnect_audio((const RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed AG audio disconnection, status: {}",
               bt_status_text(status));
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean startVoiceRecognitionNative(JNIEnv* env, jobject /* object */,
                                            jbyteArray address) {
  std::shared_lock<std::shared_mutex> lock(interface_mutex);
  if (!sBluetoothHfpClientInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status = sBluetoothHfpClientInterface->start_voice_recognition(
      (const RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed to start voice recognition, status: {}",
               bt_status_text(status));
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean stopVoiceRecognitionNative(JNIEnv* env, jobject /* object */,
                                           jbyteArray address) {
  std::shared_lock<std::shared_mutex> lock(interface_mutex);
  if (!sBluetoothHfpClientInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status = sBluetoothHfpClientInterface->stop_voice_recognition(
      (const RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed to stop voice recognition, status: {}",
               bt_status_text(status));
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean setVolumeNative(JNIEnv* env, jobject /* object */,
                                jbyteArray address, jint volume_type,
                                jint volume) {
  std::shared_lock<std::shared_mutex> lock(interface_mutex);
  if (!sBluetoothHfpClientInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status = sBluetoothHfpClientInterface->volume_control(
      (const RawAddress*)addr, (bthf_client_volume_type_t)volume_type, volume);
  if (status != BT_STATUS_SUCCESS) {
    log::error("FAILED to control volume, status: {}", bt_status_text(status));
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean dialNative(JNIEnv* env, jobject /* object */,
                           jbyteArray address, jstring number_str) {
  std::shared_lock<std::shared_mutex> lock(interface_mutex);
  if (!sBluetoothHfpClientInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  const char* number = nullptr;
  if (number_str != nullptr) {
    number = env->GetStringUTFChars(number_str, nullptr);
  }
  bt_status_t status =
    sBluetoothHfpClientInterface->dial((const RawAddress*)addr,
                                       number == nullptr ? "" : number);

  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed to dial, status: {}", bt_status_text(status));
  }
  if (number != nullptr) {
    env->ReleaseStringUTFChars(number_str, number);
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean dialMemoryNative(JNIEnv* env, jobject /* object */,
                                 jbyteArray address, jint location) {
  std::shared_lock<std::shared_mutex> lock(interface_mutex);
  if (!sBluetoothHfpClientInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status = sBluetoothHfpClientInterface->dial_memory(
      (const RawAddress*)addr, (int)location);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed to dial from memory, status: {}",
               bt_status_text(status));
  }

  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean handleCallActionNative(JNIEnv* env, jobject /* object */,
                                       jbyteArray address, jint action,
                                       jint index) {
  std::shared_lock<std::shared_mutex> lock(interface_mutex);
  if (!sBluetoothHfpClientInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status = sBluetoothHfpClientInterface->handle_call_action(
      (const RawAddress*)addr, (bthf_client_call_action_t)action, (int)index);

  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed to enter private mode, status: {}",
               bt_status_text(status));
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean queryCurrentCallsNative(JNIEnv* env, jobject /* object */,
                                        jbyteArray address) {
  std::shared_lock<std::shared_mutex> lock(interface_mutex);
  if (!sBluetoothHfpClientInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status = sBluetoothHfpClientInterface->query_current_calls(
      (const RawAddress*)addr);

  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed to query current calls, status: {}",
               bt_status_text(status));
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean queryCurrentOperatorNameNative(JNIEnv* env,
                                               jobject /* object */,
                                               jbyteArray address) {
  std::shared_lock<std::shared_mutex> lock(interface_mutex);
  if (!sBluetoothHfpClientInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status =
      sBluetoothHfpClientInterface->query_current_operator_name(
          (const RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed to query current operator name, status: {}",
               bt_status_text(status));
  }

  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean retrieveSubscriberInfoNative(JNIEnv* env, jobject /* object */,
                                             jbyteArray address) {
  std::shared_lock<std::shared_mutex> lock(interface_mutex);
  if (!sBluetoothHfpClientInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status = sBluetoothHfpClientInterface->retrieve_subscriber_info(
      (const RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed to retrieve subscriber info, status: {}",
               bt_status_text(status));
  }

  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean sendDtmfNative(JNIEnv* env, jobject /* object */,
                               jbyteArray address, jbyte code) {
  std::shared_lock<std::shared_mutex> lock(interface_mutex);
  if (!sBluetoothHfpClientInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status = sBluetoothHfpClientInterface->send_dtmf(
      (const RawAddress*)addr, (char)code);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed to send DTMF, status: {}", bt_status_text(status));
  }

  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean requestLastVoiceTagNumberNative(JNIEnv* env,
                                                jobject /* object */,
                                                jbyteArray address) {
  std::shared_lock<std::shared_mutex> lock(interface_mutex);
  if (!sBluetoothHfpClientInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status =
      sBluetoothHfpClientInterface->request_last_voice_tag_number(
          (const RawAddress*)addr);

  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed to request last Voice Tag number, status: {}",
               bt_status_text(status));
  }

  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean sendATCmdNative(JNIEnv* env, jobject /* object */,
                                jbyteArray address, jint cmd, jint val1,
                                jint val2, jstring arg_str) {
  std::shared_lock<std::shared_mutex> lock(interface_mutex);
  if (!sBluetoothHfpClientInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }
  const char* arg = NULL;
  if (arg_str != NULL) {
    arg = env->GetStringUTFChars(arg_str, NULL);
  }

  bt_status_t status = sBluetoothHfpClientInterface->send_at_cmd(
      (const RawAddress*)addr, cmd, val1, val2, arg);

  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed to send cmd, status: {}", bt_status_text(status));
  }

  if (arg != NULL) {
    env->ReleaseStringUTFChars(arg_str, arg);
  }

  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean sendAndroidAtNative(JNIEnv* env, jobject /* object */,
                                    jbyteArray address, jstring arg_str) {
  std::shared_lock<std::shared_mutex> lock(interface_mutex);
  if (!sBluetoothHfpClientInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  const char* arg = NULL;
  if (arg_str != NULL) {
    arg = env->GetStringUTFChars(arg_str, NULL);
  }

  bt_status_t status = sBluetoothHfpClientInterface->send_android_at(
      (const RawAddress*)addr, arg);

  if (status != BT_STATUS_SUCCESS) {
    log::error("FAILED to control volume, status: {}", bt_status_text(status));
  }

  if (arg != NULL) {
    env->ReleaseStringUTFChars(arg_str, arg);
  }

  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

int register_com_android_bluetooth_hfpclient(JNIEnv* env) {
  const JNINativeMethod methods[] = {
      {"initializeNative", "()V", (void*)initializeNative},
      {"cleanupNative", "()V", (void*)cleanupNative},
      {"connectNative", "([B)Z", (void*)connectNative},
      {"disconnectNative", "([B)Z", (void*)disconnectNative},
      {"connectAudioNative", "([B)Z", (void*)connectAudioNative},
      {"disconnectAudioNative", "([B)Z", (void*)disconnectAudioNative},
      {"startVoiceRecognitionNative", "([B)Z",
       (void*)startVoiceRecognitionNative},
      {"stopVoiceRecognitionNative", "([B)Z",
       (void*)stopVoiceRecognitionNative},
      {"setVolumeNative", "([BII)Z", (void*)setVolumeNative},
      {"dialNative", "([BLjava/lang/String;)Z", (void*)dialNative},
      {"dialMemoryNative", "([BI)Z", (void*)dialMemoryNative},
      {"handleCallActionNative", "([BII)Z", (void*)handleCallActionNative},
      {"queryCurrentCallsNative", "([B)Z", (void*)queryCurrentCallsNative},
      {"queryCurrentOperatorNameNative", "([B)Z",
       (void*)queryCurrentOperatorNameNative},
      {"retrieveSubscriberInfoNative", "([B)Z",
       (void*)retrieveSubscriberInfoNative},
      {"sendDtmfNative", "([BB)Z", (void*)sendDtmfNative},
      {"requestLastVoiceTagNumberNative", "([B)Z",
       (void*)requestLastVoiceTagNumberNative},
      {"sendATCmdNative", "([BIIILjava/lang/String;)Z", (void*)sendATCmdNative},
      {"sendAndroidAtNative", "([BLjava/lang/String;)Z",
       (void*)sendAndroidAtNative},
  };
  const int result = REGISTER_NATIVE_METHODS(
      env, "com/android/bluetooth/hfpclient/NativeInterface", methods);
  if (result != 0) {
    return result;
  }

  const JNIJavaMethod javaMethods[] = {
      {"onConnectionStateChanged", "(III[B)V",
       &method_onConnectionStateChanged},
      {"onAudioStateChanged", "(I[B)V", &method_onAudioStateChanged},
      {"onVrStateChanged", "(I[B)V", &method_onVrStateChanged},
      {"onNetworkState", "(I[B)V", &method_onNetworkState},
      {"onNetworkRoaming", "(I[B)V", &method_onNetworkRoaming},
      {"onNetworkSignal", "(I[B)V", &method_onNetworkSignal},
      {"onBatteryLevel", "(I[B)V", &method_onBatteryLevel},
      {"onCurrentOperator", "(Ljava/lang/String;[B)V",
       &method_onCurrentOperator},
      {"onCall", "(I[B)V", &method_onCall},
      {"onCallSetup", "(I[B)V", &method_onCallSetup},
      {"onCallHeld", "(I[B)V", &method_onCallHeld},
      {"onRespAndHold", "(I[B)V", &method_onRespAndHold},
      {"onClip", "(Ljava/lang/String;[B)V", &method_onClip},
      {"onCallWaiting", "(Ljava/lang/String;[B)V", &method_onCallWaiting},
      {"onCurrentCalls", "(IIIILjava/lang/String;[B)V", &method_onCurrentCalls},
      {"onVolumeChange", "(II[B)V", &method_onVolumeChange},
      {"onCmdResult", "(II[B)V", &method_onCmdResult},
      {"onSubscriberInfo", "(Ljava/lang/String;I[B)V",
       &method_onSubscriberInfo},
      {"onInBandRing", "(I[B)V", &method_onInBandRing},
      {"onLastVoiceTagNumber", "(Ljava/lang/String;[B)V",
       &method_onLastVoiceTagNumber},
      {"onRingIndication", "([B)V", &method_onRingIndication},
      {"onUnknownEvent", "(Ljava/lang/String;[B)V", &method_onUnknownEvent},
  };
  GET_JAVA_METHODS(env, "com/android/bluetooth/hfpclient/NativeInterface",
                   javaMethods);

  return 0;
}

} /* namespace android */
