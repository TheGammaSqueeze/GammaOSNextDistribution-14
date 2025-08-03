/******************************************************************************
 *
 *  Copyright 2018 Samsung
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

#include "SecNfc.h"
#include <log/log.h>
#include "HalSecNfc.h"

typedef uint16_t NFCSTATUS; /* Return values */
#define NFCSTATUS_SUCCESS (0x0000)
#define NFCSTATUS_OK (NFCSTATUS_SUCCESS)

#define CHK_STATUS(x)                                 \
  ((x) == NFCSTATUS_SUCCESS)                          \
      ? (android::hardware::nfc::V1_0::NfcStatus::OK) \
      : (android::hardware::nfc::V1_0::NfcStatus::FAILED)

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
  ALOGD_IF(nfc_debug_enabled, "SecNfc::open Enter");
  if (clientCallback == nullptr) {
    ALOGD_IF(nfc_debug_enabled, "Nfc::open null callback");
    return V1_0::NfcStatus::FAILED;
  } else {
    mCallbackV1_1 = clientCallback;
    mCallbackV1_1->linkToDeath(this, 0 /*cookie*/);
  }
  ALOGD_IF(nfc_debug_enabled, "SecNfc::open Exit");
  return open(clientCallback);
}

Return<V1_0::NfcStatus> Nfc::open(
    const sp<V1_0::INfcClientCallback>& clientCallback) {
  ALOGD_IF(nfc_debug_enabled, "SecNfc::open Enter");
  if (clientCallback == nullptr) {
    ALOGD_IF(nfc_debug_enabled, "Nfc::open null callback");
    return V1_0::NfcStatus::FAILED;
  } else {
    mCallbackV1_0 = clientCallback;
    mCallbackV1_0->linkToDeath(this, 0 /*cookie*/);
  }
  NFCSTATUS status = nfc_hal_open(eventCallback, dataCallback);
  ALOGD_IF(nfc_debug_enabled, "SecNfc::open Exit");
  return CHK_STATUS(status);
}

Return<uint32_t> Nfc::write(const hidl_vec<uint8_t>& data) {
  hidl_vec<uint8_t> copy = data;
  return nfc_hal_write(copy.size(), &copy[0]);
}

Return<V1_0::NfcStatus> Nfc::coreInitialized(const hidl_vec<uint8_t>& data) {
  hidl_vec<uint8_t> copy = data;
  NFCSTATUS status = nfc_hal_core_initialized((unsigned char*)&copy[0]);
  return CHK_STATUS(status);
}

Return<::android::hardware::nfc::V1_0::NfcStatus> Nfc::prediscover() {
  NFCSTATUS status = nfc_hal_pre_discover();
  return CHK_STATUS(status);
}

Return<V1_0::NfcStatus> Nfc::close() {
  if (mCallbackV1_1 == nullptr && mCallbackV1_0 == nullptr) {
    return V1_0::NfcStatus::FAILED;
  }
  NFCSTATUS status = nfc_hal_close();  // To-do function Parameter

  if (mCallbackV1_1 != nullptr) {
    mCallbackV1_1->unlinkToDeath(this);
    mCallbackV1_1 = nullptr;
  }
  if (mCallbackV1_0 != nullptr) {
    mCallbackV1_0->unlinkToDeath(this);
    mCallbackV1_0 = nullptr;
  }
  return CHK_STATUS(status);
}

Return<V1_0::NfcStatus> Nfc::controlGranted() {
  NFCSTATUS status = nfc_hal_control_granted();
  return CHK_STATUS(status);
}

Return<V1_0::NfcStatus> Nfc::powerCycle() {
  NFCSTATUS status = nfc_hal_power_cycle();
  return CHK_STATUS(status);
}

// Methods from ::android::hardware::nfc::V1_1::INfc follow.
Return<void> Nfc::factoryReset() {
  nfc_hal_factory_reset();
  return Void();
}

Return<V1_0::NfcStatus> Nfc::closeForPowerOffCase() {
  if (mCallbackV1_1 == nullptr && mCallbackV1_0 == nullptr) {
    return V1_0::NfcStatus::FAILED;
  }

  NFCSTATUS status = nfc_hal_closeForPowerOffCase();

  if (mCallbackV1_1 != nullptr) {
    mCallbackV1_1->unlinkToDeath(this);
    mCallbackV1_1 = nullptr;
  }
  if (mCallbackV1_0 != nullptr) {
    mCallbackV1_0->unlinkToDeath(this);
    mCallbackV1_0 = nullptr;
  }
  return CHK_STATUS(status);
}

Return<void> Nfc::getConfig(getConfig_cb hidl_cb) {
  android::hardware::nfc::V1_1::NfcConfig nfcVendorConfig;
  nfc_hal_getVendorConfig(nfcVendorConfig);
  hidl_cb(nfcVendorConfig);
  return Void();
}

Return<void> Nfc::getConfig_1_2(getConfig_1_2_cb hidl_cb) {
  NfcConfig nfcVendorConfig;
  nfc_hal_getVendorConfig_1_2(nfcVendorConfig);
  hidl_cb(nfcVendorConfig);
  return Void();
}

}  // namespace implementation
}  // namespace V1_2
}  // namespace nfc
}  // namespace hardware
}  // namespace android
