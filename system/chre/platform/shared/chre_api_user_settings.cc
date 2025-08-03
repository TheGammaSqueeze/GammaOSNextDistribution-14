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

#include "chre_api/chre/user_settings.h"

#include "chre/core/event_loop_manager.h"
#include "chre/core/settings.h"
#include "chre/util/macros.h"

using chre::EventLoopManager;
using chre::Nanoapp;
using chre::Setting;

DLL_EXPORT int8_t chreUserSettingGetState(uint8_t setting) {
  return chre::EventLoopManagerSingleton::get()
      ->getSettingManager()
      .getSettingStateAsInt8(setting);
}

DLL_EXPORT void chreUserSettingConfigureEvents(uint8_t setting, bool enable) {
  if (setting < static_cast<uint8_t>(Setting::SETTING_MAX)) {
    Nanoapp *nanoapp = EventLoopManager::validateChreApiCall(__func__);
    nanoapp->configureUserSettingEvent(setting, enable);
  }
}
