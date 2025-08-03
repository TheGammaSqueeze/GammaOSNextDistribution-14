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

#define LOG_TAG "BluetoothA2dpSinkServiceJni"

#include <string.h>

#include <mutex>
#include <shared_mutex>

#include "com_android_bluetooth.h"
#include "hardware/bt_av.h"
#include "utils/Log.h"

namespace android {
static jmethodID method_onConnectionStateChanged;
static jmethodID method_onAudioStateChanged;
static jmethodID method_onAudioConfigChanged;

static const btav_sink_interface_t* sBluetoothA2dpInterface = NULL;
static jobject mCallbacksObj = NULL;
static std::shared_timed_mutex callbacks_mutex;

static void a2dp_sink_connection_state_callback(
    const RawAddress& bd_addr, btav_connection_state_t state,
    const btav_error_t& /* error */) {
  log::info("");
  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  if (!mCallbacksObj) return;

  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jbyteArray> addr(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
  if (!addr.get()) {
    log::error("Fail to new jbyteArray bd addr for connection state");
    return;
  }

  sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                   (const jbyte*)bd_addr.address);
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onConnectionStateChanged,
                               addr.get(), (jint)state);
}

static void a2dp_sink_audio_state_callback(const RawAddress& bd_addr,
                                           btav_audio_state_t state) {
  log::info("");
  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  if (!mCallbacksObj) return;

  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jbyteArray> addr(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
  if (!addr.get()) {
    log::error("Fail to new jbyteArray bd addr for connection state");
    return;
  }

  sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                   (const jbyte*)bd_addr.address);
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onAudioStateChanged,
                               addr.get(), (jint)state);
}

static void a2dp_sink_audio_config_callback(const RawAddress& bd_addr,
                                            uint32_t sample_rate,
                                            uint8_t channel_count) {
  log::info("");
  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  if (!mCallbacksObj) return;

  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jbyteArray> addr(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
  if (!addr.get()) {
    log::error("Fail to new jbyteArray bd addr for connection state");
    return;
  }

  sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                   (const jbyte*)bd_addr.address);
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onAudioConfigChanged,
                               addr.get(), (jint)sample_rate,
                               (jint)channel_count);
}

static btav_sink_callbacks_t sBluetoothA2dpCallbacks = {
    sizeof(sBluetoothA2dpCallbacks),
    a2dp_sink_connection_state_callback,
    a2dp_sink_audio_state_callback,
    a2dp_sink_audio_config_callback,
};

static void initNative(JNIEnv* env, jobject object,
                       jint maxConnectedAudioDevices) {
  std::unique_lock<std::shared_timed_mutex> lock(callbacks_mutex);

  const bt_interface_t* btInf = getBluetoothInterface();
  if (btInf == NULL) {
    log::error("Bluetooth module is not loaded");
    return;
  }

  if (sBluetoothA2dpInterface != NULL) {
    log::warn("Cleaning up A2DP Interface before initializing...");
    sBluetoothA2dpInterface->cleanup();
    sBluetoothA2dpInterface = NULL;
  }

  if (mCallbacksObj != NULL) {
    log::warn("Cleaning up A2DP callback object");
    env->DeleteGlobalRef(mCallbacksObj);
    mCallbacksObj = NULL;
  }

  sBluetoothA2dpInterface =
      (btav_sink_interface_t*)btInf->get_profile_interface(
          BT_PROFILE_ADVANCED_AUDIO_SINK_ID);
  if (sBluetoothA2dpInterface == NULL) {
    log::error("Failed to get Bluetooth A2DP Sink Interface");
    return;
  }

  bt_status_t status = sBluetoothA2dpInterface->init(&sBluetoothA2dpCallbacks,
                                                     maxConnectedAudioDevices);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed to initialize Bluetooth A2DP Sink, status: {}",
               bt_status_text(status));
    sBluetoothA2dpInterface = NULL;
    return;
  }

  mCallbacksObj = env->NewGlobalRef(object);
}

static void cleanupNative(JNIEnv* env, jobject /* object */) {
  std::unique_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  const bt_interface_t* btInf = getBluetoothInterface();

  if (btInf == NULL) {
    log::error("Bluetooth module is not loaded");
    return;
  }

  if (sBluetoothA2dpInterface != NULL) {
    sBluetoothA2dpInterface->cleanup();
    sBluetoothA2dpInterface = NULL;
  }

  if (mCallbacksObj != NULL) {
    env->DeleteGlobalRef(mCallbacksObj);
    mCallbacksObj = NULL;
  }
}

static jboolean connectA2dpNative(JNIEnv* env, jobject /* object */,
                                  jbyteArray address) {
  log::info("sBluetoothA2dpInterface: {}", fmt::ptr(sBluetoothA2dpInterface));
  if (!sBluetoothA2dpInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  RawAddress bd_addr;
  bd_addr.FromOctets(reinterpret_cast<const uint8_t*>(addr));
  bt_status_t status = sBluetoothA2dpInterface->connect(bd_addr);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed HF connection, status: {}", bt_status_text(status));
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean disconnectA2dpNative(JNIEnv* env, jobject /* object */,
                                     jbyteArray address) {
  if (!sBluetoothA2dpInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  RawAddress bd_addr;
  bd_addr.FromOctets(reinterpret_cast<const uint8_t*>(addr));
  bt_status_t status = sBluetoothA2dpInterface->disconnect(bd_addr);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed HF disconnection, status: {}", bt_status_text(status));
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static void informAudioFocusStateNative(JNIEnv* /* env */, jobject /* object */,
                                        jint focus_state) {
  if (!sBluetoothA2dpInterface) return;
  sBluetoothA2dpInterface->set_audio_focus_state((uint8_t)focus_state);
}

static void informAudioTrackGainNative(JNIEnv* /* env */, jobject /* object */,
                                       jfloat gain) {
  if (!sBluetoothA2dpInterface) return;
  sBluetoothA2dpInterface->set_audio_track_gain((float)gain);
}

static jboolean setActiveDeviceNative(JNIEnv* env, jobject /* object */,
                                      jbyteArray address) {
  if (!sBluetoothA2dpInterface) return JNI_FALSE;

  log::info("sBluetoothA2dpInterface: {}", fmt::ptr(sBluetoothA2dpInterface));

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  RawAddress rawAddress;
  rawAddress.FromOctets((uint8_t*)addr);
  bt_status_t status = sBluetoothA2dpInterface->set_active_device(rawAddress);
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed sending passthru command, status: {}",
               bt_status_text(status));
  }
  env->ReleaseByteArrayElements(address, addr, 0);

  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

int register_com_android_bluetooth_a2dp_sink(JNIEnv* env) {
  const JNINativeMethod methods[] = {
      {"initNative", "(I)V", (void*)initNative},
      {"cleanupNative", "()V", (void*)cleanupNative},
      {"connectA2dpNative", "([B)Z", (void*)connectA2dpNative},
      {"disconnectA2dpNative", "([B)Z", (void*)disconnectA2dpNative},
      {"informAudioFocusStateNative", "(I)V",
       (void*)informAudioFocusStateNative},
      {"informAudioTrackGainNative", "(F)V", (void*)informAudioTrackGainNative},
      {"setActiveDeviceNative", "([B)Z", (void*)setActiveDeviceNative},
  };
  const int result = REGISTER_NATIVE_METHODS(
      env, "com/android/bluetooth/a2dpsink/A2dpSinkNativeInterface", methods);
  if (result != 0) {
    return result;
  }

  const JNIJavaMethod javaMethods[] = {
      {"onConnectionStateChanged", "([BI)V", &method_onConnectionStateChanged},
      {"onAudioStateChanged", "([BI)V", &method_onAudioStateChanged},
      {"onAudioConfigChanged", "([BII)V", &method_onAudioConfigChanged},
  };
  GET_JAVA_METHODS(env,
                   "com/android/bluetooth/a2dpsink/A2dpSinkNativeInterface",
                   javaMethods);

  return 0;
}
}
