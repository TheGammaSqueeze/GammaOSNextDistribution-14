/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include "Nfc.h"

#include <android-base/logging.h>

#include "StNfc_hal_api.h"

namespace aidl {
namespace android {
namespace hardware {
namespace nfc {

typedef struct {
  uint64_t mOpenCount;
  Nfc* nfc;
} stHalCookie;

std::shared_ptr<INfcClientCallback> Nfc::mCallback = nullptr;
AIBinder_DeathRecipient* clientDeathRecipient = nullptr;
pthread_mutex_t mLockOpenClose = PTHREAD_MUTEX_INITIALIZER;
uint64_t mOpenCount = 0;
stHalCookie mHalCookie;

void OnDeath(void* cookie) {
  pthread_mutex_lock(&mLockOpenClose);
  stHalCookie* mCookie = static_cast<stHalCookie*>(cookie);
  LOG(INFO) << __func__ << "cookie : " << mCookie->mOpenCount
            << " mOpenCount : " << mOpenCount;
  if (mCookie->mOpenCount == mOpenCount) {
    if (Nfc::mCallback != nullptr &&
        !AIBinder_isAlive(Nfc::mCallback->asBinder().get())) {
      LOG(INFO) << __func__ << " Nfc service has died";
      pthread_mutex_unlock(&mLockOpenClose);
      mCookie->nfc->close(NfcCloseType::DISABLE);
    }
  }
  pthread_mutex_unlock(&mLockOpenClose);
}

::ndk::ScopedAStatus Nfc::open(
    const std::shared_ptr<INfcClientCallback>& clientCallback) {
  LOG(INFO) << "open";
  if (clientCallback == nullptr) {
    LOG(INFO) << "Nfc::open null callback";
    return ndk::ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(NfcStatus::FAILED));
  } else {
    pthread_mutex_lock(&mLockOpenClose);
    mOpenCount++;
    Nfc::mCallback = clientCallback;
    mHalCookie.mOpenCount = mOpenCount;
    mHalCookie.nfc = this;

    clientDeathRecipient = AIBinder_DeathRecipient_new(OnDeath);
    auto linkRet =
        AIBinder_linkToDeath(clientCallback->asBinder().get(),
                             clientDeathRecipient, &mHalCookie /* cookie */);
    if (linkRet != STATUS_OK) {
      LOG(ERROR) << __func__ << ": linkToDeath failed: " << linkRet;
      // Just ignore the error.
    }

    int ret = StNfc_hal_open(eventCallback, dataCallback);
    LOG(INFO) << "Nfc::open Exit count " << mOpenCount;
    pthread_mutex_unlock(&mLockOpenClose);
    return ret == 0 ? ndk::ScopedAStatus::ok()
                    : ndk::ScopedAStatus::fromServiceSpecificError(
                          static_cast<int32_t>(NfcStatus::FAILED));
  }
}

::ndk::ScopedAStatus Nfc::close(NfcCloseType type) {
  LOG(INFO) << "close";
  if (Nfc::mCallback == nullptr) {
    LOG(ERROR) << __func__ << "mCallback null";
    return ndk::ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(NfcStatus::FAILED));
  }
  pthread_mutex_lock(&mLockOpenClose);
  int ret = 0;
  if (type == NfcCloseType::HOST_SWITCHED_OFF) {
    ret = StNfc_hal_closeForPowerOffCase();
  } else {
    ret = StNfc_hal_close(NFC_MODE_OFF);
  }
  Nfc::mCallback = nullptr;
  AIBinder_DeathRecipient_delete(clientDeathRecipient);
  clientDeathRecipient = nullptr;
  pthread_mutex_unlock(&mLockOpenClose);
  return ret == 0 ? ndk::ScopedAStatus::ok()
                  : ndk::ScopedAStatus::fromServiceSpecificError(
                        static_cast<int32_t>(NfcStatus::FAILED));
}

::ndk::ScopedAStatus Nfc::coreInitialized() {
  LOG(INFO) << "coreInitialized";
  if (Nfc::mCallback == nullptr) {
    LOG(ERROR) << __func__ << "mCallback null";
    return ndk::ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(NfcStatus::FAILED));
  }
  int ret = StNfc_hal_core_initialized();

  return ret == 0 ? ndk::ScopedAStatus::ok()
                  : ndk::ScopedAStatus::fromServiceSpecificError(
                        static_cast<int32_t>(NfcStatus::FAILED));
}

::ndk::ScopedAStatus Nfc::factoryReset() {
  LOG(INFO) << "factoryReset";
  StNfc_hal_factoryReset();
  return ndk::ScopedAStatus::ok();
}

::ndk::ScopedAStatus Nfc::getConfig(NfcConfig* _aidl_return) {
  LOG(INFO) << "getConfig";
  NfcConfig nfcVendorConfig;
  StNfc_hal_getConfig(nfcVendorConfig);

  *_aidl_return = nfcVendorConfig;
  return ndk::ScopedAStatus::ok();
}

::ndk::ScopedAStatus Nfc::powerCycle() {
  LOG(INFO) << "powerCycle";
  if (Nfc::mCallback == nullptr) {
    LOG(ERROR) << __func__ << "mCallback null";
    return ndk::ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(NfcStatus::FAILED));
  }
  return StNfc_hal_power_cycle() ? ndk::ScopedAStatus::fromServiceSpecificError(
                                       static_cast<int32_t>(NfcStatus::FAILED))
                                 : ndk::ScopedAStatus::ok();
}

::ndk::ScopedAStatus Nfc::preDiscover() {
  if (Nfc::mCallback == nullptr) {
    LOG(ERROR) << __func__ << "mCallback null";
    return ndk::ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(NfcStatus::FAILED));
  }
  return StNfc_hal_pre_discover()
             ? ndk::ScopedAStatus::fromServiceSpecificError(
                   static_cast<int32_t>(NfcStatus::FAILED))
             : ndk::ScopedAStatus::ok();
}

::ndk::ScopedAStatus Nfc::write(const std::vector<uint8_t>& data,
                                int32_t* _aidl_return) {
  if (Nfc::mCallback == nullptr) {
    LOG(ERROR) << __func__ << "mCallback null";
    return ndk::ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(NfcStatus::FAILED));
  }
  *_aidl_return = StNfc_hal_write(data.size(), &data[0]);
  return ndk::ScopedAStatus::ok();
}

::ndk::ScopedAStatus Nfc::setEnableVerboseLogging(bool enable) {
  LOG(INFO) << "setVerboseLogging";
  StNfc_hal_setLogging(enable);
  return ndk::ScopedAStatus::ok();
}

::ndk::ScopedAStatus Nfc::isVerboseLoggingEnabled(bool* _aidl_return) {
  *_aidl_return = StNfc_hal_isLoggingEnabled();
  return ndk::ScopedAStatus::ok();
}

}  // namespace nfc
}  // namespace hardware
}  // namespace android
}  // namespace aidl
