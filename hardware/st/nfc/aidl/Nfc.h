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

#pragma once

#include <aidl/android/hardware/nfc/BnNfc.h>
#include <aidl/android/hardware/nfc/INfcClientCallback.h>
#include <android-base/logging.h>

#include "hardware_nfc.h"

namespace aidl {
namespace android {
namespace hardware {
namespace nfc {

using ::aidl::android::hardware::nfc::NfcCloseType;
using ::aidl::android::hardware::nfc::NfcConfig;
using ::aidl::android::hardware::nfc::NfcEvent;
using ::aidl::android::hardware::nfc::NfcStatus;

// Default implementation that reports no support NFC.
struct Nfc : public BnNfc {
 public:
  Nfc() = default;

  ::ndk::ScopedAStatus open(
      const std::shared_ptr<INfcClientCallback>& clientCallback) override;
  ::ndk::ScopedAStatus close(NfcCloseType type) override;
  ::ndk::ScopedAStatus coreInitialized() override;
  ::ndk::ScopedAStatus factoryReset() override;
  ::ndk::ScopedAStatus getConfig(NfcConfig* _aidl_return) override;
  ::ndk::ScopedAStatus powerCycle() override;
  ::ndk::ScopedAStatus preDiscover() override;
  ::ndk::ScopedAStatus write(const std::vector<uint8_t>& data,
                             int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus setEnableVerboseLogging(bool enable) override;
  ::ndk::ScopedAStatus isVerboseLoggingEnabled(bool* _aidl_return) override;

  static void eventCallback(uint8_t event, uint8_t status) {
    if (mCallback != nullptr) {
      NfcEvent mEvent;
      NfcStatus mStatus;
      switch (event) {
        case HAL_NFC_OPEN_CPLT_EVT:
          mEvent = NfcEvent::OPEN_CPLT;
          break;
        case HAL_NFC_CLOSE_CPLT_EVT:
          mEvent = NfcEvent::CLOSE_CPLT;
          break;
        case HAL_NFC_POST_INIT_CPLT_EVT:
          mEvent = NfcEvent::POST_INIT_CPLT;
          break;
        case HAL_NFC_PRE_DISCOVER_CPLT_EVT:
          mEvent = NfcEvent::PRE_DISCOVER_CPLT;
          break;
        case HAL_HCI_NETWORK_RESET:
          mEvent = NfcEvent::HCI_NETWORK_RESET;
          break;
        case HAL_NFC_REQUEST_CONTROL_EVT:
        case HAL_NFC_RELEASE_CONTROL_EVT:
        case HAL_NFC_ERROR_EVT:
        default:
          mEvent = NfcEvent::ERROR;
      }
      switch (status) {
        case HAL_NFC_STATUS_OK:
          mStatus = NfcStatus::OK;
          break;
        case HAL_NFC_STATUS_FAILED:
          mStatus = NfcStatus::FAILED;
          break;
        case HAL_NFC_STATUS_ERR_TRANSPORT:
          mStatus = NfcStatus::ERR_TRANSPORT;
          break;
        case HAL_NFC_STATUS_ERR_CMD_TIMEOUT:
          mStatus = NfcStatus::ERR_CMD_TIMEOUT;
          break;
        case HAL_NFC_STATUS_REFUSED:
          mStatus = NfcStatus::REFUSED;
          break;
        default:
          mStatus = NfcStatus::FAILED;
      }
      auto ret = mCallback->sendEvent(mEvent, mStatus);
      if (!ret.isOk()) {
        LOG(ERROR) << "Failed to send event!";
      }
    }
  }

  static void dataCallback(uint16_t data_len, uint8_t* p_data) {
    std::vector<uint8_t> data(p_data, p_data + data_len);
    if (mCallback != nullptr) {
      auto ret = mCallback->sendData(data);
      if (!ret.isOk()) {
        LOG(ERROR) << "Failed to send data!";
      }
    }
  }

  static std::shared_ptr<INfcClientCallback> mCallback;
};

}  // namespace nfc
}  // namespace hardware
}  // namespace android
}  // namespace aidl
