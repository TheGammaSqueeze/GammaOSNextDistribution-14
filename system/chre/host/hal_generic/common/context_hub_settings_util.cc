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

#include "context_hub_settings_util.h"

#include <log/log.h>
#include <unistd.h>

namespace android {
namespace hardware {
namespace contexthub {
namespace common {
namespace implementation {

// Aliased for consistency with the way these symbols are referenced in
// CHRE-side code
namespace fbs = ::chre::fbs;

using ::android::hardware::contexthub::V1_1::SettingValue;
using ::android::hardware::contexthub::V1_2::Setting;

static_assert(sizeof(::android::hardware::contexthub::V1_1::Setting) ==
                  sizeof(Setting),
              "New and old Setting types must have the same size");

bool getFbsSetting(const Setting &setting, fbs::Setting *fbsSetting) {
  bool foundSetting = true;

  switch (setting) {
    case Setting::LOCATION:
      *fbsSetting = fbs::Setting::LOCATION;
      break;
    case Setting::WIFI_AVAILABLE:
      *fbsSetting = fbs::Setting::WIFI_AVAILABLE;
      break;
    case Setting::AIRPLANE_MODE:
      *fbsSetting = fbs::Setting::AIRPLANE_MODE;
      break;
    case Setting::MICROPHONE:
      *fbsSetting = fbs::Setting::MICROPHONE;
      break;
    default:
      foundSetting = false;
      ALOGE("Setting update with invalid enum value %hhu", setting);
      break;
  }

  return foundSetting;
}

bool getFbsSettingValue(const SettingValue &newValue,
                        fbs::SettingState *fbsState) {
  bool foundSettingValue = true;

  switch (newValue) {
    case SettingValue::ENABLED:
      *fbsState = fbs::SettingState::ENABLED;
      break;
    case SettingValue::DISABLED:
      *fbsState = fbs::SettingState::DISABLED;
      break;
    default:
      foundSettingValue = false;
      ALOGE("Setting value update with invalid enum value %hhu", newValue);
      break;
  }

  return foundSettingValue;
}

}  // namespace implementation
}  // namespace common
}  // namespace contexthub
}  // namespace hardware
}  // namespace android
