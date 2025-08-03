
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
#define LOG_TAG "BluetoothCsipSetCoordinatorJni"
#include <string.h>

#include <shared_mutex>

#include "base/logging.h"
#include "com_android_bluetooth.h"
#include "hardware/bt_csis.h"

using bluetooth::csis::ConnectionState;
using bluetooth::csis::CsisClientCallbacks;
using bluetooth::csis::CsisClientInterface;
using bluetooth::csis::CsisGroupLockStatus;

namespace android {
static jmethodID method_onConnectionStateChanged;
static jmethodID method_onDeviceAvailable;
static jmethodID method_onSetMemberAvailable;
static jmethodID method_onGroupLockChanged;

static CsisClientInterface* sCsisClientInterface = nullptr;
static std::shared_timed_mutex interface_mutex;

static jobject mCallbacksObj = nullptr;
static std::shared_timed_mutex callbacks_mutex;

using bluetooth::Uuid;

#define UUID_PARAMS(uuid) uuid_lsb(uuid), uuid_msb(uuid)

static uint64_t uuid_lsb(const Uuid& uuid) {
  uint64_t lsb = 0;

  auto uu = uuid.To128BitBE();
  for (int i = 8; i <= 15; i++) {
    lsb <<= 8;
    lsb |= uu[i];
  }

  return lsb;
}

static uint64_t uuid_msb(const Uuid& uuid) {
  uint64_t msb = 0;

  auto uu = uuid.To128BitBE();
  for (int i = 0; i <= 7; i++) {
    msb <<= 8;
    msb |= uu[i];
  }

  return msb;
}

class CsisClientCallbacksImpl : public CsisClientCallbacks {
 public:
  ~CsisClientCallbacksImpl() = default;

  void OnConnectionState(const RawAddress& bd_addr,
                         ConnectionState state) override {
    log::info("state:{}, addr: {}", int(state),
              bd_addr.ToRedactedStringForLogging());

    std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
    CallbackEnv sCallbackEnv(__func__);
    if (!sCallbackEnv.valid() || mCallbacksObj == nullptr) return;

    ScopedLocalRef<jbyteArray> addr(
        sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
    if (!addr.get()) {
      log::error("Failed to new bd addr jbyteArray for connection state");
      return;
    }

    sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                     (jbyte*)&bd_addr);
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onConnectionStateChanged,
                                 addr.get(), (jint)state);
  }

  void OnDeviceAvailable(const RawAddress& bd_addr, int group_id,
                         int group_size, int rank,
                         const bluetooth::Uuid& uuid) override {
    std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
    CallbackEnv sCallbackEnv(__func__);
    if (!sCallbackEnv.valid() || mCallbacksObj == nullptr) return;

    ScopedLocalRef<jbyteArray> addr(
        sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
    if (!addr.get()) {
      log::error("Failed to new bd addr jbyteArray for device available");
      return;
    }
    sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                     (jbyte*)&bd_addr);

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onDeviceAvailable,
                                 addr.get(), (jint)group_id, (jint)group_size,
                                 (jint)rank, UUID_PARAMS(uuid));
  }

  void OnSetMemberAvailable(const RawAddress& bd_addr, int group_id) override {
    log::info("group id:{}", group_id);

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
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onSetMemberAvailable,
                                 addr.get(), (jint)group_id);
  }

  void OnGroupLockChanged(int group_id, bool locked,
                          CsisGroupLockStatus status) override {
    log::info("group_id: {}, locked: {}, status: {}", group_id, locked,
              (int)status);

    std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
    CallbackEnv sCallbackEnv(__func__);
    if (!sCallbackEnv.valid() || mCallbacksObj == nullptr) return;

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onGroupLockChanged,
                                 (jint)group_id, (jboolean)locked,
                                 (jint)status);
  }
};

static CsisClientCallbacksImpl sCsisClientCallbacks;

static void initNative(JNIEnv* env, jobject object) {
  std::unique_lock<std::shared_timed_mutex> interface_lock(interface_mutex);
  std::unique_lock<std::shared_timed_mutex> callbacks_lock(callbacks_mutex);

  const bt_interface_t* btInf = getBluetoothInterface();
  if (btInf == nullptr) {
    log::error("Bluetooth module is not loaded");
    return;
  }

  if (sCsisClientInterface != nullptr) {
    log::info("Cleaning up Csis Interface before initializing...");
    sCsisClientInterface->Cleanup();
    sCsisClientInterface = nullptr;
  }

  if (mCallbacksObj != nullptr) {
    log::info("Cleaning up Csis callback object");
    env->DeleteGlobalRef(mCallbacksObj);
    mCallbacksObj = nullptr;
  }

  if ((mCallbacksObj = env->NewGlobalRef(object)) == nullptr) {
    log::error("Failed to allocate Global Ref for Csis Client Callbacks");
    return;
  }

  sCsisClientInterface = (CsisClientInterface*)btInf->get_profile_interface(
      BT_PROFILE_CSIS_CLIENT_ID);
  if (sCsisClientInterface == nullptr) {
    log::error("Failed to get Csis Client Interface");
    return;
  }

  sCsisClientInterface->Init(&sCsisClientCallbacks);
}

static void cleanupNative(JNIEnv* env, jobject /* object */) {
  std::unique_lock<std::shared_timed_mutex> interface_lock(interface_mutex);
  std::unique_lock<std::shared_timed_mutex> callbacks_lock(callbacks_mutex);

  const bt_interface_t* btInf = getBluetoothInterface();
  if (btInf == nullptr) {
    log::error("Bluetooth module is not loaded");
    return;
  }

  if (sCsisClientInterface != nullptr) {
    sCsisClientInterface->Cleanup();
    sCsisClientInterface = nullptr;
  }

  if (mCallbacksObj != nullptr) {
    env->DeleteGlobalRef(mCallbacksObj);
    mCallbacksObj = nullptr;
  }
}

static jboolean connectNative(JNIEnv* env, jobject /* object */,
                              jbyteArray address) {
  std::shared_lock<std::shared_timed_mutex> lock(interface_mutex);
  if (!sCsisClientInterface) {
    log::error("Failed to get the Csis Client Interface Interface");
    return JNI_FALSE;
  }

  jbyte* addr = env->GetByteArrayElements(address, nullptr);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  RawAddress* tmpraw = (RawAddress*)addr;
  sCsisClientInterface->Connect(*tmpraw);
  env->ReleaseByteArrayElements(address, addr, 0);
  return JNI_TRUE;
}

static jboolean disconnectNative(JNIEnv* env, jobject /* object */,
                                 jbyteArray address) {
  std::shared_lock<std::shared_timed_mutex> lock(interface_mutex);
  if (!sCsisClientInterface) {
    log::error("Failed to get the Csis Client Interface");
    return JNI_FALSE;
  }

  jbyte* addr = env->GetByteArrayElements(address, nullptr);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  RawAddress* tmpraw = (RawAddress*)addr;
  sCsisClientInterface->Disconnect(*tmpraw);
  env->ReleaseByteArrayElements(address, addr, 0);
  return JNI_TRUE;
}

static void groupLockSetNative(JNIEnv* /* env */, jobject /* object */,
                               jint group_id, jboolean lock) {
  log::info("");

  if (!sCsisClientInterface) {
    log::error("Failed to get the Bluetooth Csis Client Interface");
    return;
  }

  sCsisClientInterface->LockGroup(group_id, lock);
}

int register_com_android_bluetooth_csip_set_coordinator(JNIEnv* env) {
  const JNINativeMethod methods[] = {
      {"initNative", "()V", (void*)initNative},
      {"cleanupNative", "()V", (void*)cleanupNative},
      {"connectNative", "([B)Z", (void*)connectNative},
      {"disconnectNative", "([B)Z", (void*)disconnectNative},
      {"groupLockSetNative", "(IZ)V", (void*)groupLockSetNative},
  };
  const int result = REGISTER_NATIVE_METHODS(
      env, "com/android/bluetooth/csip/CsipSetCoordinatorNativeInterface",
      methods);
  if (result != 0) {
    return result;
  }

  const JNIJavaMethod javaMethods[]{
      {"onConnectionStateChanged", "([BI)V", &method_onConnectionStateChanged},
      {"onDeviceAvailable", "([BIIIJJ)V", &method_onDeviceAvailable},
      {"onSetMemberAvailable", "([BI)V", &method_onSetMemberAvailable},
      {"onGroupLockChanged", "(IZI)V", &method_onGroupLockChanged},
  };
  GET_JAVA_METHODS(
      env, "com/android/bluetooth/csip/CsipSetCoordinatorNativeInterface",
      javaMethods);

  return 0;
}
}  // namespace android
