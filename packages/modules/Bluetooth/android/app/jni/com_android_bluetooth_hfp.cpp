/*
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

#define LOG_TAG "BluetoothHeadsetServiceJni"

#include <mutex>
#include <shared_mutex>

#include "com_android_bluetooth.h"
#include "hardware/bluetooth_headset_callbacks.h"
#include "hardware/bluetooth_headset_interface.h"
#include "hardware/bt_hf.h"
#include "os/log.h"

namespace android {

static jmethodID method_onConnectionStateChanged;
static jmethodID method_onAudioStateChanged;
static jmethodID method_onVrStateChanged;
static jmethodID method_onAnswerCall;
static jmethodID method_onHangupCall;
static jmethodID method_onVolumeChanged;
static jmethodID method_onDialCall;
static jmethodID method_onSendDtmf;
static jmethodID method_onNoiseReductionEnable;
static jmethodID method_onWBS;
static jmethodID method_onSWB;
static jmethodID method_onAtChld;
static jmethodID method_onAtCnum;
static jmethodID method_onAtCind;
static jmethodID method_onAtCops;
static jmethodID method_onAtClcc;
static jmethodID method_onUnknownAt;
static jmethodID method_onKeyPressed;
static jmethodID method_onAtBind;
static jmethodID method_onAtBiev;
static jmethodID method_onAtBia;

static bluetooth::headset::Interface* sBluetoothHfpInterface = nullptr;
static std::shared_timed_mutex interface_mutex;

static jobject mCallbacksObj = nullptr;
static std::shared_timed_mutex callbacks_mutex;

static jbyteArray marshall_bda(RawAddress* bd_addr) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return nullptr;

  jbyteArray addr = sCallbackEnv->NewByteArray(sizeof(RawAddress));
  if (!addr) {
    log::error("Fail to new jbyteArray bd addr");
    return nullptr;
  }
  sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(RawAddress),
                                   (jbyte*)bd_addr);
  return addr;
}

class JniHeadsetCallbacks : bluetooth::headset::Callbacks {
 public:
  static bluetooth::headset::Callbacks* GetInstance() {
    static bluetooth::headset::Callbacks* instance = new JniHeadsetCallbacks();
    return instance;
  }

  void ConnectionStateCallback(
      bluetooth::headset::bthf_connection_state_t state,
      RawAddress* bd_addr) override {
    log::info("{} for {}", state, ADDRESS_TO_LOGGABLE_CSTR(*bd_addr));

    std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
    CallbackEnv sCallbackEnv(__func__);
    if (!sCallbackEnv.valid() || !mCallbacksObj) return;

    ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
    if (!addr.get()) return;

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onConnectionStateChanged,
                                 (jint)state, addr.get());
  }

  void AudioStateCallback(bluetooth::headset::bthf_audio_state_t state,
                          RawAddress* bd_addr) override {
    log::info("{} for {}", state, ADDRESS_TO_LOGGABLE_CSTR(*bd_addr));

    std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
    CallbackEnv sCallbackEnv(__func__);
    if (!sCallbackEnv.valid() || !mCallbacksObj) return;

    ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
    if (!addr.get()) return;

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onAudioStateChanged,
                                 (jint)state, addr.get());
  }

  void VoiceRecognitionCallback(bluetooth::headset::bthf_vr_state_t state,
                                RawAddress* bd_addr) override {
    std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
    CallbackEnv sCallbackEnv(__func__);
    if (!sCallbackEnv.valid() || !mCallbacksObj) return;

    ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
    if (!addr.get()) {
      log::error("Fail to new jbyteArray bd addr for audio state");
      return;
    }

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onVrStateChanged,
                                 (jint)state, addr.get());
  }

  void AnswerCallCallback(RawAddress* bd_addr) override {
    std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
    CallbackEnv sCallbackEnv(__func__);
    if (!sCallbackEnv.valid() || !mCallbacksObj) return;

    ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
    if (!addr.get()) {
      log::error("Fail to new jbyteArray bd addr for audio state");
      return;
    }

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onAnswerCall,
                                 addr.get());
  }

  void HangupCallCallback(RawAddress* bd_addr) override {
    std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
    CallbackEnv sCallbackEnv(__func__);
    if (!sCallbackEnv.valid() || !mCallbacksObj) return;

    ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
    if (!addr.get()) {
      log::error("Fail to new jbyteArray bd addr for audio state");
      return;
    }

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onHangupCall,
                                 addr.get());
  }

  void VolumeControlCallback(bluetooth::headset::bthf_volume_type_t type,
                             int volume, RawAddress* bd_addr) override {
    std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
    CallbackEnv sCallbackEnv(__func__);
    if (!sCallbackEnv.valid() || !mCallbacksObj) return;

    ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
    if (!addr.get()) {
      log::error("Fail to new jbyteArray bd addr for audio state");
      return;
    }

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onVolumeChanged,
                                 (jint)type, (jint)volume, addr.get());
  }

  void DialCallCallback(char* number, RawAddress* bd_addr) override {
    std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
    CallbackEnv sCallbackEnv(__func__);
    if (!sCallbackEnv.valid() || !mCallbacksObj) return;

    ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
    if (!addr.get()) {
      log::error("Fail to new jbyteArray bd addr for audio state");
      return;
    }

    char null_str[] = "";
    if (!sCallbackEnv.isValidUtf(number)) {
      log::error("number is not a valid UTF string.");
      number = null_str;
    }

    ScopedLocalRef<jstring> js_number(sCallbackEnv.get(),
                                      sCallbackEnv->NewStringUTF(number));
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onDialCall,
                                 js_number.get(), addr.get());
  }

  void DtmfCmdCallback(char dtmf, RawAddress* bd_addr) override {
    std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
    CallbackEnv sCallbackEnv(__func__);
    if (!sCallbackEnv.valid() || !mCallbacksObj) return;

    ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
    if (!addr.get()) {
      log::error("Fail to new jbyteArray bd addr for audio state");
      return;
    }

    // TBD dtmf has changed from int to char
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onSendDtmf, dtmf,
                                 addr.get());
  }

  void NoiseReductionCallback(bluetooth::headset::bthf_nrec_t nrec,
                              RawAddress* bd_addr) override {
    std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
    CallbackEnv sCallbackEnv(__func__);
    if (!sCallbackEnv.valid() || !mCallbacksObj) return;

    ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
    if (!addr.get()) {
      log::error("Fail to new jbyteArray bd addr for audio state");
      return;
    }
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onNoiseReductionEnable,
                                 nrec == bluetooth::headset::BTHF_NREC_START,
                                 addr.get());
  }

  void WbsCallback(bluetooth::headset::bthf_wbs_config_t wbs_config,
                   RawAddress* bd_addr) override {
    std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
    CallbackEnv sCallbackEnv(__func__);
    if (!sCallbackEnv.valid() || !mCallbacksObj) return;

    ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
    if (addr.get() == nullptr) return;

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onWBS, wbs_config,
                                 addr.get());
  }

  void SwbCallback(bluetooth::headset::bthf_swb_codec_t swb_codec,
                   bluetooth::headset::bthf_swb_config_t swb_config,
                   RawAddress* bd_addr) override {
    std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
    CallbackEnv sCallbackEnv(__func__);
    if (!sCallbackEnv.valid() || !mCallbacksObj) return;

    ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
    if (addr.get() == nullptr) return;

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onSWB, swb_codec,
                                 swb_config, addr.get());
  }

  void AtChldCallback(bluetooth::headset::bthf_chld_type_t chld,
                      RawAddress* bd_addr) override {
    std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
    CallbackEnv sCallbackEnv(__func__);
    if (!sCallbackEnv.valid() || !mCallbacksObj) return;

    ScopedLocalRef<jbyteArray> addr(
        sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
    if (!addr.get()) {
      log::error("Fail to new jbyteArray bd addr for audio state");
      return;
    }

    sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                     (jbyte*)bd_addr);
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onAtChld, chld,
                                 addr.get());
  }

  void AtCnumCallback(RawAddress* bd_addr) override {
    std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
    CallbackEnv sCallbackEnv(__func__);
    if (!sCallbackEnv.valid() || !mCallbacksObj) return;

    ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
    if (!addr.get()) {
      log::error("Fail to new jbyteArray bd addr for audio state");
      return;
    }

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onAtCnum, addr.get());
  }

  void AtCindCallback(RawAddress* bd_addr) override {
    std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
    CallbackEnv sCallbackEnv(__func__);
    if (!sCallbackEnv.valid() || !mCallbacksObj) return;

    ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
    if (!addr.get()) {
      log::error("Fail to new jbyteArray bd addr for audio state");
      return;
    }

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onAtCind, addr.get());
  }

  void AtCopsCallback(RawAddress* bd_addr) override {
    std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
    CallbackEnv sCallbackEnv(__func__);
    if (!sCallbackEnv.valid() || !mCallbacksObj) return;

    ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
    if (!addr.get()) {
      log::error("Fail to new jbyteArray bd addr for audio state");
      return;
    }

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onAtCops, addr.get());
  }

  void AtClccCallback(RawAddress* bd_addr) override {
    std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
    CallbackEnv sCallbackEnv(__func__);
    if (!sCallbackEnv.valid() || !mCallbacksObj) return;

    ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
    if (!addr.get()) {
      log::error("Fail to new jbyteArray bd addr for audio state");
      return;
    }

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onAtClcc, addr.get());
  }

  void UnknownAtCallback(char* at_string, RawAddress* bd_addr) override {
    std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
    CallbackEnv sCallbackEnv(__func__);
    if (!sCallbackEnv.valid() || !mCallbacksObj) return;

    ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
    if (!addr.get()) {
      log::error("Fail to new jbyteArray bd addr for audio state");
      return;
    }

    char null_str[] = "";
    if (!sCallbackEnv.isValidUtf(at_string)) {
      log::error("at_string is not a valid UTF string.");
      at_string = null_str;
    }

    ScopedLocalRef<jstring> js_at_string(sCallbackEnv.get(),
                                         sCallbackEnv->NewStringUTF(at_string));
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onUnknownAt,
                                 js_at_string.get(), addr.get());
  }

  void KeyPressedCallback(RawAddress* bd_addr) override {
    std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
    CallbackEnv sCallbackEnv(__func__);
    if (!sCallbackEnv.valid() || !mCallbacksObj) return;

    ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
    if (!addr.get()) {
      log::error("Fail to new jbyteArray bd addr for audio state");
      return;
    }

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onKeyPressed,
                                 addr.get());
  }

  void AtBindCallback(char* at_string, RawAddress* bd_addr) override {
    std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
    CallbackEnv sCallbackEnv(__func__);
    if (!sCallbackEnv.valid() || !mCallbacksObj) return;

    ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
    if (addr.get() == nullptr) return;

    char null_str[] = "";
    if (!sCallbackEnv.isValidUtf(at_string)) {
      log::error("at_string is not a valid UTF string.");
      at_string = null_str;
    }

    ScopedLocalRef<jstring> js_at_string(sCallbackEnv.get(),
                                         sCallbackEnv->NewStringUTF(at_string));

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onAtBind,
                                 js_at_string.get(), addr.get());
  }

  void AtBievCallback(bluetooth::headset::bthf_hf_ind_type_t ind_id,
                      int ind_value, RawAddress* bd_addr) override {
    std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
    CallbackEnv sCallbackEnv(__func__);
    if (!sCallbackEnv.valid() || !mCallbacksObj) return;

    ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
    if (addr.get() == nullptr) return;

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onAtBiev, ind_id,
                                 (jint)ind_value, addr.get());
  }

  void AtBiaCallback(bool service, bool roam, bool signal, bool battery,
                     RawAddress* bd_addr) override {
    std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
    CallbackEnv sCallbackEnv(__func__);
    if (!sCallbackEnv.valid() || !mCallbacksObj) return;

    ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
    if (addr.get() == nullptr) return;

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onAtBia, service, roam,
                                 signal, battery, addr.get());
  }

  void DebugDumpCallback(bool /* active */, uint16_t /* codec_id */,
                         int /* total_num_decoded_frames */,
                         double /* pkt_loss_ratio */, uint64_t /* begin_ts */,
                         uint64_t /* end_ts */,
                         const char* /* pkt_status_in_hex */,
                         const char* /* pkt_status_in_binary */) override {
    log::error("Not implemented and shouldn't be called");
  }
};

static void initializeNative(JNIEnv* env, jobject object, jint max_hf_clients,
                             jboolean inband_ringing_enabled) {
  std::unique_lock<std::shared_timed_mutex> interface_lock(interface_mutex);
  std::unique_lock<std::shared_timed_mutex> callbacks_lock(callbacks_mutex);

  const bt_interface_t* btInf = getBluetoothInterface();
  if (!btInf) {
    log::error("Bluetooth module is not loaded");
    jniThrowIOException(env, EINVAL);
    return;
  }

  if (sBluetoothHfpInterface) {
    log::info("Cleaning up Bluetooth Handsfree Interface before initializing");
    sBluetoothHfpInterface->Cleanup();
    sBluetoothHfpInterface = nullptr;
  }

  if (mCallbacksObj) {
    log::info("Cleaning up Bluetooth Handsfree callback object");
    env->DeleteGlobalRef(mCallbacksObj);
    mCallbacksObj = nullptr;
  }

  sBluetoothHfpInterface =
      (bluetooth::headset::Interface*)btInf->get_profile_interface(
          BT_PROFILE_HANDSFREE_ID);
  if (!sBluetoothHfpInterface) {
    log::warn("Failed to get Bluetooth Handsfree Interface");
    jniThrowIOException(env, EINVAL);
    return;
  }
  bt_status_t status =
      sBluetoothHfpInterface->Init(JniHeadsetCallbacks::GetInstance(),
                                   max_hf_clients, inband_ringing_enabled);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed to initialize Bluetooth Handsfree Interface, status: {}",
               bt_status_text(status));
    sBluetoothHfpInterface = nullptr;
    return;
  }

  mCallbacksObj = env->NewGlobalRef(object);
}

static void cleanupNative(JNIEnv* env, jobject /* object */) {
  std::unique_lock<std::shared_timed_mutex> interface_lock(interface_mutex);
  std::unique_lock<std::shared_timed_mutex> callbacks_lock(callbacks_mutex);

  const bt_interface_t* btInf = getBluetoothInterface();
  if (!btInf) {
    log::warn("Bluetooth module is not loaded");
    return;
  }

  if (sBluetoothHfpInterface) {
    log::info("Cleaning up Bluetooth Handsfree Interface");
    sBluetoothHfpInterface->Cleanup();
    sBluetoothHfpInterface = nullptr;
  }

  if (mCallbacksObj) {
    log::info("Cleaning up Bluetooth Handsfree callback object");
    env->DeleteGlobalRef(mCallbacksObj);
    mCallbacksObj = nullptr;
  }
}

static jboolean connectHfpNative(JNIEnv* env, jobject /* object */,
                                 jbyteArray address) {
  std::shared_lock<std::shared_timed_mutex> lock(interface_mutex);
  if (!sBluetoothHfpInterface) {
    log::warn("sBluetoothHfpInterface is null");
    return JNI_FALSE;
  }
  jbyte* addr = env->GetByteArrayElements(address, nullptr);
  if (!addr) {
    log::error("failed to get device address");
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }
  log::info("device {}", ADDRESS_TO_LOGGABLE_CSTR(*((RawAddress*)addr)));
  bt_status_t status = sBluetoothHfpInterface->Connect((RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed HF connection, status: {}", bt_status_text(status));
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean disconnectHfpNative(JNIEnv* env, jobject /* object */,
                                    jbyteArray address) {
  std::shared_lock<std::shared_timed_mutex> lock(interface_mutex);
  if (!sBluetoothHfpInterface) {
    log::warn("sBluetoothHfpInterface is null");
    return JNI_FALSE;
  }
  jbyte* addr = env->GetByteArrayElements(address, nullptr);
  if (!addr) {
    log::error("failed to get device address");
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }
  log::info("device {}", ADDRESS_TO_LOGGABLE_CSTR(*((RawAddress*)addr)));
  bt_status_t status = sBluetoothHfpInterface->Disconnect((RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed HF disconnection, status: {}", bt_status_text(status));
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean connectAudioNative(JNIEnv* env, jobject /* object */,
                                   jbyteArray address) {
  std::shared_lock<std::shared_timed_mutex> lock(interface_mutex);
  if (!sBluetoothHfpInterface) {
    log::warn("sBluetoothHfpInterface is null");
    return JNI_FALSE;
  }
  jbyte* addr = env->GetByteArrayElements(address, nullptr);
  if (!addr) {
    log::error("failed to get device address");
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }
  log::info("device {}", ADDRESS_TO_LOGGABLE_CSTR(*((RawAddress*)addr)));
  bt_status_t status =
      sBluetoothHfpInterface->ConnectAudio((RawAddress*)addr, 0);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed HF audio connection, status: {}",
               bt_status_text(status));
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean disconnectAudioNative(JNIEnv* env, jobject /* object */,
                                      jbyteArray address) {
  std::shared_lock<std::shared_timed_mutex> lock(interface_mutex);
  if (!sBluetoothHfpInterface) {
    log::warn("sBluetoothHfpInterface is null");
    return JNI_FALSE;
  }
  jbyte* addr = env->GetByteArrayElements(address, nullptr);
  if (!addr) {
    log::error("failed to get device address");
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }
  log::info("device {}", ADDRESS_TO_LOGGABLE_CSTR(*((RawAddress*)addr)));
  bt_status_t status =
      sBluetoothHfpInterface->DisconnectAudio((RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed HF audio disconnection, status: {}",
               bt_status_text(status));
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean isNoiseReductionSupportedNative(JNIEnv* env,
                                                jobject /* object */,
                                                jbyteArray address) {
  std::shared_lock<std::shared_timed_mutex> lock(interface_mutex);
  if (!sBluetoothHfpInterface) {
    log::warn("sBluetoothHfpInterface is null");
    return JNI_FALSE;
  }
  jbyte* addr = env->GetByteArrayElements(address, nullptr);
  if (!addr) {
    log::error("failed to get device address");
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }
  bt_status_t status =
      sBluetoothHfpInterface->isNoiseReductionSupported((RawAddress*)addr);
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean isVoiceRecognitionSupportedNative(JNIEnv* env,
                                                  jobject /* object */,
                                                  jbyteArray address) {
  std::shared_lock<std::shared_timed_mutex> lock(interface_mutex);
  if (!sBluetoothHfpInterface) {
    log::warn("sBluetoothHfpInterface is null");
    return JNI_FALSE;
  }
  jbyte* addr = env->GetByteArrayElements(address, nullptr);
  if (!addr) {
    log::error("failed to get device address");
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }
  bt_status_t status =
      sBluetoothHfpInterface->isVoiceRecognitionSupported((RawAddress*)addr);
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean startVoiceRecognitionNative(JNIEnv* env, jobject /* object */,
                                            jbyteArray address) {
  std::shared_lock<std::shared_timed_mutex> lock(interface_mutex);
  if (!sBluetoothHfpInterface) {
    log::warn("sBluetoothHfpInterface is null");
    return JNI_FALSE;
  }
  jbyte* addr = env->GetByteArrayElements(address, nullptr);
  if (!addr) {
    log::error("failed to get device address");
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }
  bt_status_t status =
      sBluetoothHfpInterface->StartVoiceRecognition((RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed to start voice recognition, status: {}",
               bt_status_text(status));
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean stopVoiceRecognitionNative(JNIEnv* env, jobject /* object */,
                                           jbyteArray address) {
  std::shared_lock<std::shared_timed_mutex> lock(interface_mutex);
  if (!sBluetoothHfpInterface) {
    log::warn("sBluetoothHfpInterface is null");
    return JNI_FALSE;
  }
  jbyte* addr = env->GetByteArrayElements(address, nullptr);
  if (!addr) {
    log::error("failed to get device address");
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }
  bt_status_t status =
      sBluetoothHfpInterface->StopVoiceRecognition((RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed to stop voice recognition, status: {}",
               bt_status_text(status));
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean setVolumeNative(JNIEnv* env, jobject /* object */,
                                jint volume_type, jint volume,
                                jbyteArray address) {
  std::shared_lock<std::shared_timed_mutex> lock(interface_mutex);
  if (!sBluetoothHfpInterface) {
    log::warn("sBluetoothHfpInterface is null");
    return JNI_FALSE;
  }
  jbyte* addr = env->GetByteArrayElements(address, nullptr);
  if (!addr) {
    log::error("failed to get device address");
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }
  bt_status_t status = sBluetoothHfpInterface->VolumeControl(
      (bluetooth::headset::bthf_volume_type_t)volume_type, volume,
      (RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    log::error("FAILED to control volume, status: {}", bt_status_text(status));
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean notifyDeviceStatusNative(JNIEnv* env, jobject /* object */,
                                         jint network_state, jint service_type,
                                         jint signal, jint battery_charge,
                                         jbyteArray address) {
  std::shared_lock<std::shared_timed_mutex> lock(interface_mutex);
  if (!sBluetoothHfpInterface) {
    log::warn("sBluetoothHfpInterface is null");
    return JNI_FALSE;
  }
  jbyte* addr = env->GetByteArrayElements(address, nullptr);
  if (!addr) {
    log::error("failed to get device address");
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }
  bt_status_t status = sBluetoothHfpInterface->DeviceStatusNotification(
      (bluetooth::headset::bthf_network_state_t)network_state,
      (bluetooth::headset::bthf_service_type_t)service_type, signal,
      battery_charge, (RawAddress*)addr);
  env->ReleaseByteArrayElements(address, addr, 0);
  if (status != BT_STATUS_SUCCESS) {
    log::error("FAILED to notify device status, status: {}",
               bt_status_text(status));
  }
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean copsResponseNative(JNIEnv* env, jobject /* object */,
                                   jstring operator_str, jbyteArray address) {
  std::shared_lock<std::shared_timed_mutex> lock(interface_mutex);
  if (!sBluetoothHfpInterface) {
    log::warn("sBluetoothHfpInterface is null");
    return JNI_FALSE;
  }
  jbyte* addr = env->GetByteArrayElements(address, nullptr);
  if (!addr) {
    log::error("failed to get device address");
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }
  const char* operator_name = env->GetStringUTFChars(operator_str, nullptr);
  bt_status_t status =
      sBluetoothHfpInterface->CopsResponse(operator_name, (RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed sending cops response, status: {}",
               bt_status_text(status));
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  env->ReleaseStringUTFChars(operator_str, operator_name);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean cindResponseNative(JNIEnv* env, jobject /* object */,
                                   jint service, jint num_active, jint num_held,
                                   jint call_state, jint signal, jint roam,
                                   jint battery_charge, jbyteArray address) {
  std::shared_lock<std::shared_timed_mutex> lock(interface_mutex);
  if (!sBluetoothHfpInterface) {
    log::warn("sBluetoothHfpInterface is null");
    return JNI_FALSE;
  }
  jbyte* addr = env->GetByteArrayElements(address, nullptr);
  if (!addr) {
    log::error("failed to get device address");
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }
  bt_status_t status = sBluetoothHfpInterface->CindResponse(
      service, num_active, num_held,
      (bluetooth::headset::bthf_call_state_t)call_state, signal, roam,
      battery_charge, (RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    log::error("failed, status: {}", bt_status_text(status));
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean atResponseStringNative(JNIEnv* env, jobject /* object */,
                                       jstring response_str,
                                       jbyteArray address) {
  std::shared_lock<std::shared_timed_mutex> lock(interface_mutex);
  if (!sBluetoothHfpInterface) {
    log::warn("sBluetoothHfpInterface is null");
    return JNI_FALSE;
  }
  jbyte* addr = env->GetByteArrayElements(address, nullptr);
  if (!addr) {
    log::error("failed to get device address");
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }
  const char* response = env->GetStringUTFChars(response_str, nullptr);
  bt_status_t status =
      sBluetoothHfpInterface->FormattedAtResponse(response, (RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed formatted AT response, status: {}",
               bt_status_text(status));
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  env->ReleaseStringUTFChars(response_str, response);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean atResponseCodeNative(JNIEnv* env, jobject /* object */,
                                     jint response_code, jint cmee_code,
                                     jbyteArray address) {
  std::shared_lock<std::shared_timed_mutex> lock(interface_mutex);
  if (!sBluetoothHfpInterface) {
    log::warn("sBluetoothHfpInterface is null");
    return JNI_FALSE;
  }
  jbyte* addr = env->GetByteArrayElements(address, nullptr);
  if (!addr) {
    log::error("failed to get device address");
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }
  bt_status_t status = sBluetoothHfpInterface->AtResponse(
      (bluetooth::headset::bthf_at_response_t)response_code, cmee_code,
      (RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed AT response, status: {}", bt_status_text(status));
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean clccResponseNative(JNIEnv* env, jobject /* object */,
                                   jint index, jint dir, jint callStatus,
                                   jint mode, jboolean mpty, jstring number_str,
                                   jint type, jbyteArray address) {
  std::shared_lock<std::shared_timed_mutex> lock(interface_mutex);
  if (!sBluetoothHfpInterface) {
    log::warn("sBluetoothHfpInterface is null");
    return JNI_FALSE;
  }
  jbyte* addr = env->GetByteArrayElements(address, nullptr);
  if (!addr) {
    log::error("failed to get device address");
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }
  const char* number = nullptr;
  if (number_str) {
    number = env->GetStringUTFChars(number_str, nullptr);
  }
  bt_status_t status = sBluetoothHfpInterface->ClccResponse(
      index, (bluetooth::headset::bthf_call_direction_t)dir,
      (bluetooth::headset::bthf_call_state_t)callStatus,
      (bluetooth::headset::bthf_call_mode_t)mode,
      mpty ? bluetooth::headset::BTHF_CALL_MPTY_TYPE_MULTI
           : bluetooth::headset::BTHF_CALL_MPTY_TYPE_SINGLE,
      number, (bluetooth::headset::bthf_call_addrtype_t)type,
      (RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed sending CLCC response, status: {}",
               bt_status_text(status));
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  if (number) {
    env->ReleaseStringUTFChars(number_str, number);
  }
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean phoneStateChangeNative(JNIEnv* env, jobject /* object */,
                                       jint num_active, jint num_held,
                                       jint call_state, jstring number_str,
                                       jint type, jstring name_str,
                                       jbyteArray address) {
  std::shared_lock<std::shared_timed_mutex> lock(interface_mutex);
  if (!sBluetoothHfpInterface) {
    log::warn("sBluetoothHfpInterface is null");
    return JNI_FALSE;
  }
  jbyte* addr = env->GetByteArrayElements(address, nullptr);
  if (!addr) {
    log::error("failed to get device address");
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }
  const char* number = env->GetStringUTFChars(number_str, nullptr);
  const char* name = nullptr;
  if (name_str != nullptr) {
    name = env->GetStringUTFChars(name_str, nullptr);
  }
  bt_status_t status = sBluetoothHfpInterface->PhoneStateChange(
      num_active, num_held, (bluetooth::headset::bthf_call_state_t)call_state,
      number, (bluetooth::headset::bthf_call_addrtype_t)type, name,
      (RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed report phone state change, status: {}",
               bt_status_text(status));
  }
  env->ReleaseStringUTFChars(number_str, number);
  if (name != nullptr) {
    env->ReleaseStringUTFChars(name_str, name);
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean setScoAllowedNative(JNIEnv* /* env */, jobject /* object */,
                                    jboolean value) {
  std::shared_lock<std::shared_timed_mutex> lock(interface_mutex);
  if (!sBluetoothHfpInterface) {
    log::warn("sBluetoothHfpInterface is null");
    return JNI_FALSE;
  }
  bt_status_t status = sBluetoothHfpInterface->SetScoAllowed(value == JNI_TRUE);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed HF set sco allowed, status: {}", bt_status_text(status));
  }
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean sendBsirNative(JNIEnv* env, jobject /* object */,
                               jboolean value, jbyteArray address) {
  std::shared_lock<std::shared_timed_mutex> lock(interface_mutex);
  if (!sBluetoothHfpInterface) {
    log::warn("sBluetoothHfpInterface is null");
    return JNI_FALSE;
  }
  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    log::error("failed to get device address");
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }
  bt_status_t status =
      sBluetoothHfpInterface->SendBsir(value == JNI_TRUE, (RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed sending BSIR, value={}, status={}", value,
               bt_status_text(status));
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean setActiveDeviceNative(JNIEnv* env, jobject /* object */,
                                      jbyteArray address) {
  std::shared_lock<std::shared_timed_mutex> lock(interface_mutex);
  if (!sBluetoothHfpInterface) {
    log::warn("sBluetoothHfpInterface is null");
    return JNI_FALSE;
  }
  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    log::error("failed to get device address");
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }
  bt_status_t status =
      sBluetoothHfpInterface->SetActiveDevice((RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed to set active device, status: {}",
               bt_status_text(status));
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

int register_com_android_bluetooth_hfp(JNIEnv* env) {
  const JNINativeMethod methods[] = {
      {"initializeNative", "(IZ)V", (void*)initializeNative},
      {"cleanupNative", "()V", (void*)cleanupNative},
      {"connectHfpNative", "([B)Z", (void*)connectHfpNative},
      {"disconnectHfpNative", "([B)Z", (void*)disconnectHfpNative},
      {"connectAudioNative", "([B)Z", (void*)connectAudioNative},
      {"disconnectAudioNative", "([B)Z", (void*)disconnectAudioNative},
      {"isNoiseReductionSupportedNative", "([B)Z",
       (void*)isNoiseReductionSupportedNative},
      {"isVoiceRecognitionSupportedNative", "([B)Z",
       (void*)isVoiceRecognitionSupportedNative},
      {"startVoiceRecognitionNative", "([B)Z",
       (void*)startVoiceRecognitionNative},
      {"stopVoiceRecognitionNative", "([B)Z",
       (void*)stopVoiceRecognitionNative},
      {"setVolumeNative", "(II[B)Z", (void*)setVolumeNative},
      {"notifyDeviceStatusNative", "(IIII[B)Z",
       (void*)notifyDeviceStatusNative},
      {"copsResponseNative", "(Ljava/lang/String;[B)Z",
       (void*)copsResponseNative},
      {"cindResponseNative", "(IIIIIII[B)Z", (void*)cindResponseNative},
      {"atResponseStringNative", "(Ljava/lang/String;[B)Z",
       (void*)atResponseStringNative},
      {"atResponseCodeNative", "(II[B)Z", (void*)atResponseCodeNative},
      {"clccResponseNative", "(IIIIZLjava/lang/String;I[B)Z",
       (void*)clccResponseNative},
      {"phoneStateChangeNative",
       "(IIILjava/lang/String;ILjava/lang/String;[B)Z",
       (void*)phoneStateChangeNative},
      {"setScoAllowedNative", "(Z)Z", (void*)setScoAllowedNative},
      {"sendBsirNative", "(Z[B)Z", (void*)sendBsirNative},
      {"setActiveDeviceNative", "([B)Z", (void*)setActiveDeviceNative},
  };
  const int result = REGISTER_NATIVE_METHODS(
      env, "com/android/bluetooth/hfp/HeadsetNativeInterface", methods);
  if (result != 0) {
    return result;
  }

  const JNIJavaMethod javaMethods[] = {
      {"onConnectionStateChanged", "(I[B)V", &method_onConnectionStateChanged},
      {"onAudioStateChanged", "(I[B)V", &method_onAudioStateChanged},
      {"onVrStateChanged", "(I[B)V", &method_onVrStateChanged},
      {"onAnswerCall", "([B)V", &method_onAnswerCall},
      {"onHangupCall", "([B)V", &method_onHangupCall},
      {"onVolumeChanged", "(II[B)V", &method_onVolumeChanged},
      {"onDialCall", "(Ljava/lang/String;[B)V", &method_onDialCall},
      {"onSendDtmf", "(I[B)V", &method_onSendDtmf},
      {"onNoiseReductionEnable", "(Z[B)V", &method_onNoiseReductionEnable},
      {"onWBS", "(I[B)V", &method_onWBS},
      {"onSWB", "(II[B)V", &method_onSWB},
      {"onAtChld", "(I[B)V", &method_onAtChld},
      {"onAtCnum", "([B)V", &method_onAtCnum},
      {"onAtCind", "([B)V", &method_onAtCind},
      {"onAtCops", "([B)V", &method_onAtCops},
      {"onAtClcc", "([B)V", &method_onAtClcc},
      {"onUnknownAt", "(Ljava/lang/String;[B)V", &method_onUnknownAt},
      {"onKeyPressed", "([B)V", &method_onKeyPressed},
      {"onATBind", "(Ljava/lang/String;[B)V", &method_onAtBind},
      {"onATBiev", "(II[B)V", &method_onAtBiev},
      {"onAtBia", "(ZZZZ[B)V", &method_onAtBia},
  };
  GET_JAVA_METHODS(env, "com/android/bluetooth/hfp/HeadsetNativeInterface",
                   javaMethods);

  return 0;
}

} /* namespace android */
