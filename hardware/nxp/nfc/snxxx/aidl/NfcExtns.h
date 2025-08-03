/******************************************************************************
 *
 *  Copyright 2023 NXP
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

#pragma once
#include <aidl/android/hardware/nfc/NfcConfig.h>
#include <aidl/android/hardware/nfc/PresenceCheckAlgorithm.h>
#include <aidl/android/hardware/nfc/ProtocolDiscoveryConfig.h>
#include <android-base/logging.h>
#include <log/log.h>

namespace aidl {
namespace android {
namespace hardware {
namespace nfc {

#define NXP_MAX_CONFIG_STRING_LEN 260
using ::aidl::android::hardware::nfc::NfcConfig;
using NfcConfig = aidl::android::hardware::nfc::NfcConfig;
using PresenceCheckAlgorithm =
    aidl::android::hardware::nfc::PresenceCheckAlgorithm;
using ProtocolDiscoveryConfig =
    aidl::android::hardware::nfc::ProtocolDiscoveryConfig;

struct NfcExtns {
 public:
  NfcExtns() = default;
  void getConfig(NfcConfig& config);
};

}  // namespace nfc
}  // namespace hardware
}  // namespace android
}  // namespace aidl
