/******************************************************************************
 *
 *  Copyright 2018 NXP
 *  Copyright 2019 ST Microelectronics S.A.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

#define LOG_TAG "android.hardware.nfc@1.2-impl"
#include <log/log.h>
#include "Nfc.h"
#include "StNfc_hal_api.h"



#define CHK_STATUS(x) ((x) == NFCSTATUS_SUCCESS) \
      ? (V1_0::NfcStatus::OK) : (V1_0::NfcStatus::FAILED)

bool nfc_debug_enabled = true;

namespace android {
namespace hardware {
namespace nfc {
namespace V1_2 {
namespace implementation {

sp<V1_1::INfcClientCallback> Nfc::mCallbackV1_1 = nullptr;
sp<V1_0::INfcClientCallback> Nfc::mCallbackV1_0 = nullptr;

Return<V1_0::NfcStatus> Nfc::open_1_1(
    const sp<V1_1::INfcClientCallback>& clientCallback) {
  if (clientCallback == nullptr) {
    ALOGD_IF(nfc_debug_enabled,"Nfc::open null callback");
    return V1_0::NfcStatus::FAILED;
  } else {
    pthread_mutex_lock(&mLockOpenClose);
    mOpenCount++;
    mCallbackV1_1 = clientCallback;
    mCallbackV1_1->linkToDeath(this, mOpenCount /*cookie*/);
    pthread_mutex_unlock(&mLockOpenClose);
  }
  return open(clientCallback);
}

// Methods from ::android::hardware::nfc::V1_0::INfc follow.
Return<V1_0::NfcStatus> Nfc::open(
    const sp<V1_0::INfcClientCallback>& clientCallback) {
  ALOGD_IF(nfc_debug_enabled, "Nfc::open Enter");
  pthread_mutex_lock(&mLockOpenClose);
  if (mCallbackV1_1 == nullptr) mOpenCount++;
  if (clientCallback == nullptr) {
    ALOGD_IF(nfc_debug_enabled, "Nfc::open null callback");
    pthread_mutex_unlock(&mLockOpenClose);
    return V1_0::NfcStatus::FAILED;
  } else {
    mCallbackV1_0 = clientCallback;
    mCallbackV1_0->linkToDeath(this, mOpenCount /*cookie*/);
  }

  int ret = StNfc_hal_open(eventCallback, dataCallback);
  ALOGD_IF(nfc_debug_enabled, "Nfc::open Exit (count:%llu)", (unsigned long long)mOpenCount);
  pthread_mutex_unlock(&mLockOpenClose);
  return ret == 0 ? V1_0::NfcStatus::OK : V1_0::NfcStatus::FAILED;
}

Return<uint32_t> Nfc::write(const hidl_vec<uint8_t>& data) {
  hidl_vec<uint8_t> copy = data;

  return StNfc_hal_write(data.size(), &data[0]);
}

Return<V1_0::NfcStatus> Nfc::coreInitialized(const hidl_vec<uint8_t>& data) {
  hidl_vec<uint8_t> copy = data;
  int ret;

  if (copy.size() == 0)
    ret = 1;
  else
    ret = StNfc_hal_core_initialized(&copy[0]);
  return ret == 0 ? V1_0::NfcStatus::OK : V1_0::NfcStatus::FAILED;
}

Return<V1_0::NfcStatus> Nfc::prediscover() {
  return StNfc_hal_pre_discover() ? V1_0::NfcStatus::FAILED
                                  : V1_0::NfcStatus::OK;
}

Return<V1_0::NfcStatus> Nfc::close() {
  pthread_mutex_lock(&mLockOpenClose);
  int ret = StNfc_hal_close(NFC_MODE_OFF);

  if (mCallbackV1_1 != nullptr) {
    mCallbackV1_1->unlinkToDeath(this);
    mCallbackV1_1 = nullptr;
  }
  if (mCallbackV1_0 != nullptr) {
    mCallbackV1_0->unlinkToDeath(this);
    mCallbackV1_0 = nullptr;
  }
  pthread_mutex_unlock(&mLockOpenClose);
  return ret == 0 ? V1_0::NfcStatus::OK : V1_0::NfcStatus::FAILED;
}

Return<V1_0::NfcStatus> Nfc::controlGranted() {
  return StNfc_hal_control_granted() ? V1_0::NfcStatus::FAILED
                                     : V1_0::NfcStatus::OK;
}

Return<V1_0::NfcStatus> Nfc::powerCycle() {
  return StNfc_hal_power_cycle() ? V1_0::NfcStatus::FAILED
                                 : V1_0::NfcStatus::OK;
}

// Methods from ::android::hardware::nfc::V1_1::INfc follow.
Return<void> Nfc::factoryReset() {
  StNfc_hal_factoryReset();
  return Void();
}

Return<V1_0::NfcStatus> Nfc::closeForPowerOffCase() {
  if (mCallbackV1_1 == nullptr && mCallbackV1_0 == nullptr) {
    return V1_0::NfcStatus::FAILED;
  }

  pthread_mutex_lock(&mLockOpenClose);
  int ret = StNfc_hal_closeForPowerOffCase();

  if (mCallbackV1_1 != nullptr) {
    mCallbackV1_1->unlinkToDeath(this);
    mCallbackV1_1 = nullptr;
  }
  if (mCallbackV1_0 != nullptr) {
    mCallbackV1_0->unlinkToDeath(this);
    mCallbackV1_0 = nullptr;
  }
  pthread_mutex_unlock(&mLockOpenClose);
  return ret == 0 ? V1_0::NfcStatus::OK : V1_0::NfcStatus::FAILED;
}

Return<void> Nfc::getConfig(getConfig_cb hidl_cb) {
  android::hardware::nfc::V1_1::NfcConfig nfcVendorConfig;
  StNfc_hal_getConfig(nfcVendorConfig);
  hidl_cb(nfcVendorConfig);
  return Void();
}

Return<void> Nfc::getConfig_1_2(getConfig_1_2_cb hidl_cb) {
  NfcConfig nfcVendorConfig;
  StNfc_hal_getConfig_1_2(nfcVendorConfig);
  hidl_cb(nfcVendorConfig);
  return Void();
}

}  // namespace implementation
}  // namespace V1_2
}  // namespace nfc
}  // namespace hardware
}  // namespace android
