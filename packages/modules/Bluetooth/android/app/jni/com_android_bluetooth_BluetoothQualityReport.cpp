/*
 * Copyright 2023 The Android Open Source Project
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

#define LOG_TAG "BluetoothQualityReportJni"

#include <string.h>

#include <shared_mutex>

#include "base/logging.h"
#include "com_android_bluetooth.h"
#include "common/init_flags.h"
#include "hardware/bt_bqr.h"

using bluetooth::bqr::BluetoothQualityReportCallbacks;
using bluetooth::bqr::BluetoothQualityReportInterface;

namespace android {
static jmethodID method_bqrDeliver;

static BluetoothQualityReportInterface* sBluetoothQualityReportInterface =
    nullptr;
static std::shared_timed_mutex interface_mutex;

static jobject mCallbacksObj = nullptr;
static std::shared_timed_mutex callbacks_mutex;

class BluetoothQualityReportCallbacksImpl
    : public bluetooth::bqr::BluetoothQualityReportCallbacks {
 public:
  ~BluetoothQualityReportCallbacksImpl() = default;

  void bqr_delivery_callback(const RawAddress bd_addr, uint8_t lmp_ver,
                             uint16_t lmp_subver, uint16_t manufacturer_id,
                             std::vector<uint8_t> bqr_raw_data) override {
    log::info("");
    std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);

    CallbackEnv sCallbackEnv(__func__);
    if (!sCallbackEnv.valid()) return;
    if (method_bqrDeliver == NULL) return;
    if (mCallbacksObj == nullptr) return;

    ScopedLocalRef<jbyteArray> addr(
        sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
    if (!addr.get()) {
      log::error("Error while allocation byte array for addr");
      return;
    }

    sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                     (jbyte*)bd_addr.address);

    ScopedLocalRef<jbyteArray> raw_data(
        sCallbackEnv.get(), sCallbackEnv->NewByteArray(bqr_raw_data.size()));
    if (!raw_data.get()) {
      log::error("Error while allocation byte array for bqr raw data");
      return;
    }
    sCallbackEnv->SetByteArrayRegion(raw_data.get(), 0, bqr_raw_data.size(),
                                     (jbyte*)bqr_raw_data.data());

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_bqrDeliver, addr.get(),
                                 (jint)lmp_ver, (jint)lmp_subver,
                                 (jint)manufacturer_id, raw_data.get());
  }
};

static BluetoothQualityReportCallbacksImpl sBluetoothQualityReportCallbacks;

static void initNative(JNIEnv* env, jobject object) {
  std::unique_lock<std::shared_timed_mutex> interface_lock(interface_mutex);
  std::unique_lock<std::shared_timed_mutex> callbacks_lock(callbacks_mutex);

  if (!bluetooth::common::InitFlags::
          IsBluetoothQualityReportCallbackEnabled()) {
    return;
  }

  const bt_interface_t* btInf = getBluetoothInterface();
  if (btInf == nullptr) {
    log::error("Bluetooth module is not loaded");
    return;
  }

  if (sBluetoothQualityReportInterface != nullptr) {
    log::info(
        "Cleaning up BluetoothQualityReport Interface before initializing...");
    sBluetoothQualityReportInterface = nullptr;
  }

  if (mCallbacksObj != nullptr) {
    log::info("Cleaning up BluetoothQualityReport callback object");
    env->DeleteGlobalRef(mCallbacksObj);
    mCallbacksObj = nullptr;
  }

  if ((mCallbacksObj = env->NewGlobalRef(object)) == nullptr) {
    log::error(
        "Failed to allocate Global Ref for BluetoothQualityReport Callbacks");
    return;
  }

  sBluetoothQualityReportInterface =
      (BluetoothQualityReportInterface*)btInf->get_profile_interface(BT_BQR_ID);
  if (sBluetoothQualityReportInterface == nullptr) {
    log::error("Failed to get BluetoothQualityReport Interface");
    return;
  }

  sBluetoothQualityReportInterface->init(&sBluetoothQualityReportCallbacks);
}

static void cleanupNative(JNIEnv* env, jobject /* object */) {
  std::unique_lock<std::shared_timed_mutex> interface_lock(interface_mutex);
  std::unique_lock<std::shared_timed_mutex> callbacks_lock(callbacks_mutex);

  const bt_interface_t* btInf = getBluetoothInterface();
  if (btInf == nullptr) {
    log::error("Bluetooth module is not loaded");
    return;
  }

  if (sBluetoothQualityReportInterface != nullptr) {
    sBluetoothQualityReportInterface = nullptr;
  }

  if (mCallbacksObj != nullptr) {
    env->DeleteGlobalRef(mCallbacksObj);
    mCallbacksObj = nullptr;
  }
}

int register_com_android_bluetooth_btservice_BluetoothQualityReport(
    JNIEnv* env) {
  const JNINativeMethod methods[] = {
      {"initNative", "()V", (void*)initNative},
      {"cleanupNative", "()V", (void*)cleanupNative},
  };
  const int result = REGISTER_NATIVE_METHODS(
      env,
      "com/android/bluetooth/btservice/BluetoothQualityReportNativeInterface",
      methods);
  if (result != 0) {
    return result;
  }

  const JNIJavaMethod javaMethods[] = {
      {"bqrDeliver", "([BIII[B)V", &method_bqrDeliver},
  };
  GET_JAVA_METHODS(
      env,
      "com/android/bluetooth/btservice/BluetoothQualityReportNativeInterface",
      javaMethods);

  return 0;
}
}  // namespace android
