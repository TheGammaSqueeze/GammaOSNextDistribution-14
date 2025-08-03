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

#include "chre/core/settings.h"

#include <cstddef>

#include "chre/core/event_loop_manager.h"
#include "chre/platform/log.h"
#include "chre/util/macros.h"
#include "chre/util/nested_data_ptr.h"
#include "chre/util/system/event_callbacks.h"

#include "chre_api/chre/user_settings.h"

namespace chre {

namespace {

/**
 * @param setting The setting to get the index for.
 * @param index A non-null pointer to store the index.
 *
 * @return false if the setting was invalid.
 */
bool getIndexForSetting(Setting setting, size_t *index) {
  if (setting < Setting::SETTING_MAX) {
    *index = static_cast<size_t>(setting);
    return true;
  }

  return false;
}

void sendSettingChangedNotification(Setting setting, bool enabled) {
  auto *eventData = memoryAlloc<struct chreUserSettingChangedEvent>();
  auto settingAsInt = static_cast<uint8_t>(setting);
  uint16_t eventType = CHRE_EVENT_SETTING_CHANGED_FIRST_EVENT + settingAsInt;

  if (eventData != nullptr) {
    eventData->setting = settingAsInt;
    eventData->settingState = enabled ? CHRE_USER_SETTING_STATE_ENABLED
                                      : CHRE_USER_SETTING_STATE_DISABLED;

    EventLoopManagerSingleton::get()->getEventLoop().postEventOrDie(
        eventType, eventData, freeEventDataCallback, kBroadcastInstanceId);
  } else {
    LOG_OOM();
  }
}

}  // anonymous namespace

SettingManager::SettingManager() {
  // Default most settings to true until the host tells us otherwise so
  // nanoapps can begin work during boot which will get canceled if the user has
  // disabled the feature.
  for (size_t i = 0; i < ARRAY_SIZE(mSettingStateList); ++i) {
    mSettingStateList[i] = true;
  }

  // Airplane mode should be disabled since it being enabled causes API usage
  // restrictions.
  auto airplaneIndex = static_cast<uint8_t>(Setting::AIRPLANE_MODE);
  mSettingStateList[airplaneIndex] = false;
}

void SettingManager::postSettingChange(Setting setting, bool enabled) {
  LOGD("Posting setting change: setting type %" PRIu8 " enabled %d",
       static_cast<uint8_t>(setting), enabled);

  EventLoopManagerSingleton::get()->deferCallback(
      SystemCallbackType::SettingChangeEvent, NestedDataPtr<Setting>(setting),
      settingChangedCallback, NestedDataPtr<bool>(enabled));
}

bool SettingManager::getSettingEnabled(Setting setting) {
  size_t index;
  if (getIndexForSetting(setting, &index)) {
    return mSettingStateList[index];
  }

  LOGE("Unknown setting %" PRIu8, static_cast<uint8_t>(setting));
  return false;
}

int8_t SettingManager::getSettingStateAsInt8(uint8_t setting) {
  int8_t state = CHRE_USER_SETTING_STATE_UNKNOWN;
  if (setting < static_cast<uint8_t>(Setting::SETTING_MAX)) {
    auto settingEnum = static_cast<Setting>(setting);
    state = static_cast<int8_t>(getSettingEnabled(settingEnum));
  }
  return state;
}

void SettingManager::logStateToBuffer(DebugDumpWrapper &debugDump) {
  debugDump.print("\nSettings:");
  debugDump.print("\n Location %s", getSettingEnabledString(Setting::LOCATION));
  debugDump.print("\n WiFi available %s",
                  getSettingEnabledString(Setting::WIFI_AVAILABLE));
  debugDump.print("\n Airplane mode %s",
                  getSettingEnabledString(Setting::AIRPLANE_MODE));
  debugDump.print("\n Microphone Access %s",
                  getSettingEnabledString(Setting::MICROPHONE));
  debugDump.print("\n BLE available %s",
                  getSettingEnabledString(Setting::BLE_AVAILABLE));
}

void SettingManager::settingChangedCallback(uint16_t /* type */, void *data,
                                            void *extraData) {
  Setting setting = NestedDataPtr<Setting>(data);
  bool settingEnabled = NestedDataPtr<bool>(extraData);

  EventLoopManagerSingleton::get()->getSettingManager().setSettingState(
      setting, settingEnabled);

  LOGD("Setting changed callback called for setting %u enabled %d",
       static_cast<uint8_t>(setting), settingEnabled);

#ifdef CHRE_GNSS_SUPPORT_ENABLED
  EventLoopManagerSingleton::get()->getGnssManager().onSettingChanged(
      setting, settingEnabled);
#endif  // CHRE_GNSS_SUPPORT_ENABLED

#ifdef CHRE_AUDIO_SUPPORT_ENABLED
  EventLoopManagerSingleton::get()->getAudioRequestManager().onSettingChanged(
      setting, settingEnabled);
#endif  // CHRE_AUDIO_SUPPORT_ENABLED

#ifdef CHRE_BLE_SUPPORT_ENABLED
  EventLoopManagerSingleton::get()->getBleRequestManager().onSettingChanged(
      setting, settingEnabled);
#endif  // CHRE_BLE_SUPPORT_ENABLED

#ifdef CHRE_WIFI_SUPPORT_ENABLED
  EventLoopManagerSingleton::get()->getWifiRequestManager().onSettingChanged(
      setting, settingEnabled);
#endif  // CHRE_WIFI_SUPPORT_ENABLED

  sendSettingChangedNotification(setting, settingEnabled);
}

void SettingManager::setSettingState(Setting setting, bool enabled) {
  size_t index;
  if (!getIndexForSetting(setting, &index)) {
    LOGE("Unknown setting %" PRId8, static_cast<int8_t>(setting));
  } else {
    mSettingStateList[index] = enabled;
  }
}

const char *SettingManager::getSettingEnabledString(Setting setting) {
  if (getSettingEnabled(setting)) {
    return "enabled";
  } else {
    return "disabled";
  }
}

}  // namespace chre
