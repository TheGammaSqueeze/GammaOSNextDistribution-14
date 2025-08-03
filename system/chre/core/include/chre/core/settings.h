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

#ifndef CHRE_CORE_SETTINGS_H_
#define CHRE_CORE_SETTINGS_H_

#include <cinttypes>

#include "chre/util/system/debug_dump.h"

namespace chre {

enum class Setting : uint8_t {
  LOCATION = 0,
  WIFI_AVAILABLE,
  AIRPLANE_MODE,
  MICROPHONE,
  BLE_AVAILABLE,
  SETTING_MAX,
};

/**
 * Stores latest setting state and is responsible for sending setting updates
 * to nanoapps.
 */
class SettingManager {
 public:
  SettingManager();

  /**
   * Updates the state of a given setting.
   *
   * @param setting The setting to update.
   * @param state The state of the setting.
   */
  void postSettingChange(Setting setting, bool enabled);

  /**
   * Gets the current state of a given setting. Must be called from the context
   * of the main CHRE thread.
   *
   * @param setting The setting to check the current state of.
   *
   * @return True if the setting is enabled.
   */
  bool getSettingEnabled(Setting setting);

  /**
   * Gets the current state of a given setting, but returns the state as an
   * int8_t. The state is guaranteed to be a member of enum
   * chreUserSettingState.
   *
   * @param setting The setting to check the current state of (see
   * CHRE_USER_SETTINGS).
   *
   * @return The current state of the setting (see enum chreUserSettingState)
   */
  int8_t getSettingStateAsInt8(uint8_t setting);

  /**
   * Logs the settings related stats in the debug dump. Must be called from the
   * context of the main CHRE thread.
   *
   * @param debugDump The object that is printed into for debug dump logs.
   */
  void logStateToBuffer(DebugDumpWrapper &debugDump);

 private:
  static constexpr size_t kNumSettings =
      static_cast<size_t>(Setting::SETTING_MAX);

  //! The current state for each setting.
  bool mSettingStateList[kNumSettings];

  void setSettingState(Setting setting, bool enabled);

  const char *getSettingEnabledString(Setting setting);

  static void settingChangedCallback(uint16_t type, void *data,
                                     void *extraData);
};

}  // namespace chre

#endif  // CHRE_CORE_SETTINGS_H_
