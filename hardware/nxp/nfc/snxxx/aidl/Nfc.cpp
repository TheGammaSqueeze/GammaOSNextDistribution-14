/******************************************************************************
 *
 *  Copyright 2022-2023 NXP
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

#include "Nfc.h"

#include <android-base/logging.h>

#include "NfcExtns.h"
#include "phNfcStatus.h"
#include "phNxpConfig.h"
#include "phNxpNciHal_Adaptation.h"
#include "phNxpNciHal_ext.h"

namespace aidl {
namespace android {
namespace hardware {
namespace nfc {

std::shared_ptr<INfcClientCallback> Nfc::mCallback = nullptr;
AIBinder_DeathRecipient* clientDeathRecipient = nullptr;

void OnDeath(void* cookie) {
  if (Nfc::mCallback != nullptr &&
      !AIBinder_isAlive(Nfc::mCallback->asBinder().get())) {
    LOG(INFO) << __func__ << " Nfc service has died";
    Nfc* nfc = static_cast<Nfc*>(cookie);
    nfc->close(NfcCloseType::DISABLE);
  }
}

::ndk::ScopedAStatus Nfc::open(
    const std::shared_ptr<INfcClientCallback>& clientCallback) {
  LOG(INFO) << "Nfc::open";
  if (clientCallback == nullptr) {
    LOG(INFO) << "Nfc::open null callback";
    return ndk::ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(NfcStatus::FAILED));
  }
  Nfc::mCallback = clientCallback;

  clientDeathRecipient = AIBinder_DeathRecipient_new(OnDeath);
  auto linkRet = AIBinder_linkToDeath(clientCallback->asBinder().get(),
                                      clientDeathRecipient, this /* cookie */);
  if (linkRet != STATUS_OK) {
    LOG(ERROR) << __func__ << ": linkToDeath failed: " << linkRet;
    // Just ignore the error.
  }

  printNfcMwVersion();
  int ret = phNxpNciHal_open(eventCallback, dataCallback);
  LOG(INFO) << "Nfc::open Exit";
  return ret == NFCSTATUS_SUCCESS
             ? ndk::ScopedAStatus::ok()
             : ndk::ScopedAStatus::fromServiceSpecificError(
                   static_cast<int32_t>(NfcStatus::FAILED));
}

::ndk::ScopedAStatus Nfc::close(NfcCloseType type) {
  LOG(INFO) << "Nfc::close";
  if (Nfc::mCallback == nullptr) {
    LOG(ERROR) << __func__ << "mCallback null";
    return ndk::ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(NfcStatus::FAILED));
  }
  int ret = 0;
  if (type == NfcCloseType::HOST_SWITCHED_OFF) {
    ret = phNxpNciHal_configDiscShutdown();
  } else {
    ret = phNxpNciHal_close(false);
  }
  Nfc::mCallback = nullptr;
  AIBinder_DeathRecipient_delete(clientDeathRecipient);
  clientDeathRecipient = nullptr;
  return ret == NFCSTATUS_SUCCESS
             ? ndk::ScopedAStatus::ok()
             : ndk::ScopedAStatus::fromServiceSpecificError(
                   static_cast<int32_t>(NfcStatus::FAILED));
}

::ndk::ScopedAStatus Nfc::coreInitialized() {
  LOG(INFO) << "Nfc::coreInitialized";
  if (Nfc::mCallback == nullptr) {
    LOG(ERROR) << __func__ << "mCallback null";
    return ndk::ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(NfcStatus::FAILED));
  }
  int ret = phNxpNciHal_core_initialized();

  return ret == NFCSTATUS_SUCCESS
             ? ndk::ScopedAStatus::ok()
             : ndk::ScopedAStatus::fromServiceSpecificError(
                   static_cast<int32_t>(NfcStatus::FAILED));
}

::ndk::ScopedAStatus Nfc::factoryReset() {
  LOG(INFO) << "Nfc::factoryReset";
  phNxpNciHal_do_factory_reset();
  return ndk::ScopedAStatus::ok();
}

::ndk::ScopedAStatus Nfc::getConfig(NfcConfig* _aidl_return) {
  LOG(INFO) << "Nfc::getConfig";
  NfcConfig config;
  NfcExtns nfcExtns;
  nfcExtns.getConfig(config);
  *_aidl_return = std::move(config);
  return ndk::ScopedAStatus::ok();
}

::ndk::ScopedAStatus Nfc::powerCycle() {
  LOG(INFO) << "powerCycle";
  if (Nfc::mCallback == nullptr) {
    LOG(ERROR) << __func__ << "mCallback null";
    return ndk::ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(NfcStatus::FAILED));
  }
  int ret = phNxpNciHal_power_cycle();
  return ret == NFCSTATUS_SUCCESS
             ? ndk::ScopedAStatus::ok()
             : ndk::ScopedAStatus::fromServiceSpecificError(
                   static_cast<int32_t>(NfcStatus::FAILED));
}

::ndk::ScopedAStatus Nfc::preDiscover() {
  LOG(INFO) << "preDiscover";
  if (Nfc::mCallback == nullptr) {
    LOG(ERROR) << __func__ << "mCallback null";
    return ndk::ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(NfcStatus::FAILED));
  }
  int ret = phNxpNciHal_pre_discover();
  return ret == NFCSTATUS_SUCCESS
             ? ndk::ScopedAStatus::ok()
             : ndk::ScopedAStatus::fromServiceSpecificError(
                   static_cast<int32_t>(NfcStatus::FAILED));
}

::ndk::ScopedAStatus Nfc::write(const std::vector<uint8_t>& data,
                                int32_t* _aidl_return) {
  LOG(INFO) << "write";
  if (Nfc::mCallback == nullptr) {
    LOG(ERROR) << __func__ << "mCallback null";
    return ndk::ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(NfcStatus::FAILED));
  }
  *_aidl_return = phNxpNciHal_write(data.size(), &data[0]);
  return ndk::ScopedAStatus::ok();
}
::ndk::ScopedAStatus Nfc::setEnableVerboseLogging(bool enable) {
  LOG(INFO) << "setVerboseLogging";
  phNxpNciHal_setVerboseLogging(enable);
  return ndk::ScopedAStatus::ok();
}

::ndk::ScopedAStatus Nfc::isVerboseLoggingEnabled(bool* _aidl_return) {
  *_aidl_return = phNxpNciHal_getVerboseLogging();
  return ndk::ScopedAStatus::ok();
}

}  // namespace nfc
}  // namespace hardware
}  // namespace android
}  // namespace aidl
