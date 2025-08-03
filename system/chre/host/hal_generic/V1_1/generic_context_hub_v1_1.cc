/*
 * Copyright (C) 2020 The Android Open Source Project
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

#define LOG_TAG "ContextHubHal"
#define LOG_NDEBUG 0

#include "generic_context_hub_v1_1.h"

#include "context_hub_settings_util.h"

#include <chrono>
#include <cinttypes>
#include <vector>

#include <log/log.h>
#include <unistd.h>

namespace android {
namespace hardware {
namespace contexthub {
namespace V1_1 {
namespace implementation {

using ::android::chre::HostProtocolHost;
using ::android::hardware::Return;
using ::android::hardware::contexthub::common::implementation::getFbsSetting;
using ::android::hardware::contexthub::common::implementation::
    getFbsSettingValue;
using ::flatbuffers::FlatBufferBuilder;

// Aliased for consistency with the way these symbols are referenced in
// CHRE-side code
namespace fbs = ::chre::fbs;

Return<void> GenericContextHubV1_1::onSettingChanged(Setting setting,
                                                     SettingValue newValue) {
  fbs::Setting fbsSetting;
  fbs::SettingState fbsState;
  if (getFbsSetting(reinterpret_cast<V1_2::Setting &>(setting), &fbsSetting) &&
      getFbsSettingValue(newValue, &fbsState)) {
    mConnection.sendSettingChangedNotification(fbsSetting, fbsState);
  }

  return Void();
}

}  // namespace implementation
}  // namespace V1_1
}  // namespace contexthub
}  // namespace hardware
}  // namespace android
