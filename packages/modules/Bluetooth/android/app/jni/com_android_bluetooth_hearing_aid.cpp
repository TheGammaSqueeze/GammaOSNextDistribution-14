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

#define LOG_TAG "BluetoothHearingAidServiceJni"

#include "base/logging.h"
#include "com_android_bluetooth.h"
#include "hardware/bt_hearing_aid.h"

#include <string.h>
#include <shared_mutex>

using bluetooth::hearing_aid::ConnectionState;
using bluetooth::hearing_aid::HearingAidInterface;
using bluetooth::hearing_aid::HearingAidCallbacks;

namespace android {
static jmethodID method_onConnectionStateChanged;
static jmethodID method_onDeviceAvailable;

static HearingAidInterface* sHearingAidInterface = nullptr;
static std::shared_timed_mutex interface_mutex;

static jobject mCallbacksObj = nullptr;
static std::shared_timed_mutex callbacks_mutex;

class HearingAidCallbacksImpl : public HearingAidCallbacks {
 public:
  ~HearingAidCallbacksImpl() = default;
  void OnConnectionState(ConnectionState state,
                         const RawAddress& bd_addr) override {
    log::info("");

    std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
    CallbackEnv sCallbackEnv(__func__);
    if (!sCallbackEnv.valid() || mCallbacksObj == nullptr) return;

    ScopedLocalRef<jbyteArray> addr(
        sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
    if (!addr.get()) {
      log::error("Failed to new jbyteArray bd addr for connection state");
      return;
    }

    sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                     (jbyte*)&bd_addr);
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onConnectionStateChanged,
                                 (jint)state, addr.get());
  }

  void OnDeviceAvailable(uint8_t capabilities, uint64_t hi_sync_id,
                         const RawAddress& bd_addr) override {
    log::info("capabilities={} hi_sync_id={}", +capabilities, hi_sync_id);

    std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
    CallbackEnv sCallbackEnv(__func__);
    if (!sCallbackEnv.valid() || mCallbacksObj == nullptr) return;

    ScopedLocalRef<jbyteArray> addr(
        sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
    if (!addr.get()) {
      log::error("Failed to new jbyteArray bd addr for connection state");
      return;
    }

    sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                     (jbyte*)&bd_addr);
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onDeviceAvailable,
                                 (jbyte)capabilities, (jlong)hi_sync_id,
                                 addr.get());
  }
};

static HearingAidCallbacksImpl sHearingAidCallbacks;

static void initNative(JNIEnv* env, jobject object) {
  std::unique_lock<std::shared_timed_mutex> interface_lock(interface_mutex);
  std::unique_lock<std::shared_timed_mutex> callbacks_lock(callbacks_mutex);

  const bt_interface_t* btInf = getBluetoothInterface();
  if (btInf == nullptr) {
    log::error("Bluetooth module is not loaded");
    return;
  }

  if (sHearingAidInterface != nullptr) {
    log::info("Cleaning up HearingAid Interface before initializing...");
    sHearingAidInterface->Cleanup();
    sHearingAidInterface = nullptr;
  }

  if (mCallbacksObj != nullptr) {
    log::info("Cleaning up HearingAid callback object");
    env->DeleteGlobalRef(mCallbacksObj);
    mCallbacksObj = nullptr;
  }

  if ((mCallbacksObj = env->NewGlobalRef(object)) == nullptr) {
    log::error("Failed to allocate Global Ref for Hearing Aid Callbacks");
    return;
  }

  sHearingAidInterface = (HearingAidInterface*)btInf->get_profile_interface(
      BT_PROFILE_HEARING_AID_ID);
  if (sHearingAidInterface == nullptr) {
    log::error("Failed to get Bluetooth Hearing Aid Interface");
    return;
  }

  sHearingAidInterface->Init(&sHearingAidCallbacks);
}

static void cleanupNative(JNIEnv* env, jobject /* object */) {
  std::unique_lock<std::shared_timed_mutex> interface_lock(interface_mutex);
  std::unique_lock<std::shared_timed_mutex> callbacks_lock(callbacks_mutex);

  const bt_interface_t* btInf = getBluetoothInterface();
  if (btInf == nullptr) {
    log::error("Bluetooth module is not loaded");
    return;
  }

  if (sHearingAidInterface != nullptr) {
    sHearingAidInterface->Cleanup();
    sHearingAidInterface = nullptr;
  }

  if (mCallbacksObj != nullptr) {
    env->DeleteGlobalRef(mCallbacksObj);
    mCallbacksObj = nullptr;
  }
}

static jboolean connectHearingAidNative(JNIEnv* env, jobject /* object */,
                                        jbyteArray address) {
  log::info("");
  std::shared_lock<std::shared_timed_mutex> lock(interface_mutex);
  if (!sHearingAidInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, nullptr);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  RawAddress* tmpraw = (RawAddress*)addr;
  sHearingAidInterface->Connect(*tmpraw);
  env->ReleaseByteArrayElements(address, addr, 0);
  return JNI_TRUE;
}

static jboolean disconnectHearingAidNative(JNIEnv* env, jobject /* object */,
                                           jbyteArray address) {
  log::info("");
  std::shared_lock<std::shared_timed_mutex> lock(interface_mutex);
  if (!sHearingAidInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, nullptr);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  RawAddress* tmpraw = (RawAddress*)addr;
  sHearingAidInterface->Disconnect(*tmpraw);
  env->ReleaseByteArrayElements(address, addr, 0);
  return JNI_TRUE;
}

static jboolean addToAcceptlistNative(JNIEnv* env, jobject /* object */,
                                      jbyteArray address) {
  std::shared_lock<std::shared_timed_mutex> lock(interface_mutex);
  if (!sHearingAidInterface) return JNI_FALSE;
  jbyte* addr = env->GetByteArrayElements(address, nullptr);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  RawAddress* tmpraw = (RawAddress*)addr;
  sHearingAidInterface->AddToAcceptlist(*tmpraw);
  env->ReleaseByteArrayElements(address, addr, 0);
  return JNI_TRUE;
}

static void setVolumeNative(JNIEnv* /* env */, jclass /* clazz */,
                            jint volume) {
  if (!sHearingAidInterface) {
    log::error("Failed to get the Bluetooth Hearing Aid Interface");
    return;
  }
  sHearingAidInterface->SetVolume(volume);
}

int register_com_android_bluetooth_hearing_aid(JNIEnv* env) {
  const JNINativeMethod methods[] = {
      {"initNative", "()V", (void*)initNative},
      {"cleanupNative", "()V", (void*)cleanupNative},
      {"connectHearingAidNative", "([B)Z", (void*)connectHearingAidNative},
      {"disconnectHearingAidNative", "([B)Z",
       (void*)disconnectHearingAidNative},
      {"addToAcceptlistNative", "([B)Z", (void*)addToAcceptlistNative},
      {"setVolumeNative", "(I)V", (void*)setVolumeNative},
  };
  const int result = REGISTER_NATIVE_METHODS(
      env, "com/android/bluetooth/hearingaid/HearingAidNativeInterface",
      methods);
  if (result != 0) {
    return result;
  }

  const JNIJavaMethod javaMethods[] = {
      {"onConnectionStateChanged", "(I[B)V", &method_onConnectionStateChanged},
      {"onDeviceAvailable", "(BJ[B)V", &method_onDeviceAvailable},
  };
  GET_JAVA_METHODS(env,
                   "com/android/bluetooth/hearingaid/HearingAidNativeInterface",
                   javaMethods);

  return 0;
}
}  // namespace android
