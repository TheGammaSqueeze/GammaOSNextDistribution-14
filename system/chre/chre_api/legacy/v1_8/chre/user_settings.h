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

#ifndef _CHRE_USER_SETTINGS_H_
#define _CHRE_USER_SETTINGS_H_

/**
 * @file
 * The API for requesting notifications on changes in the settings of the
 * active user. If the device is set up with one or more secondary users
 * (see https://source.android.com/devices/tech/admin/multi-user), the user
 * settings in CHRE reflect that of the currently active user.
 */

#include <stdbool.h>
#include <stdint.h>

#include <chre/event.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * The user settings that nanoapps can request notifications for on a status
 * change.
 *
 * NOTE: The WIFI available setting indicates the overall availability
 * of WIFI related functionality. For example, if wifi is disabled for
 * connectivity but enabled for location, the WIFI available setting is
 * enabled.
 *
 * NOTE: The BLE available setting is the logical OR of the main Bluetooth
 * setting and the Bluetooth scanning setting found under Location settings.
 * Note that this indicates whether the user is allowing Bluetooth to be used,
 * however the system may still fully power down the BLE chip in some scenarios
 * if no request for it exists on the Android host side. See the
 * chreBleStartScanAsync() API documentation for more information.
 *
 * @defgroup CHRE_USER_SETTINGS
 * @{
 */
#define CHRE_USER_SETTING_LOCATION             UINT8_C(0)
#define CHRE_USER_SETTING_WIFI_AVAILABLE       UINT8_C(1)
#define CHRE_USER_SETTING_AIRPLANE_MODE        UINT8_C(2)
#define CHRE_USER_SETTING_MICROPHONE           UINT8_C(3)
#define CHRE_USER_SETTING_BLE_AVAILABLE        UINT8_C(4)

/** @} */

/**
 * Produce an event ID in the block of IDs reserved for settings notifications.
 *
 * @param offset Index into the event ID block, valid in the range [0,15]
 */
#define CHRE_SETTING_EVENT_ID(offset) (CHRE_EVENT_SETTING_CHANGED_FIRST_EVENT + (offset))

/**
 * nanoappHandleEvent argument: struct chreUserSettingChangedEvent
 *
 * Notify nanoapps of a change in the associated setting. Nanoapps must first
 * register (via chreUserSettingConfigureEvents) for events before they are
 * sent out.
 */
#define CHRE_EVENT_SETTING_CHANGED_LOCATION         CHRE_SETTING_EVENT_ID(0)
#define CHRE_EVENT_SETTING_CHANGED_WIFI_AVAILABLE   CHRE_SETTING_EVENT_ID(1)
#define CHRE_EVENT_SETTING_CHANGED_AIRPLANE_MODE    CHRE_SETTING_EVENT_ID(2)
#define CHRE_EVENT_SETTING_CHANGED_MICROPHONE       CHRE_SETTING_EVENT_ID(3)
#define CHRE_EVENT_SETTING_CHANGED_BLE_AVAILABLE    CHRE_SETTING_EVENT_ID(4)

#if CHRE_EVENT_SETTING_CHANGED_BLE_AVAILABLE > CHRE_EVENT_SETTING_CHANGED_LAST_EVENT
#error Too many setting changed events.
#endif

/**
 * Indicates the current state of a setting.
 * The setting state is 'unknown' only in the following scenarios:
 *  - CHRE hasn't received the initial state yet on a restart.
 *  - The nanoapp is running on CHRE v1.4 or older
 *  - Nanoapp provided in invalid setting ID to chreUserSettingGetStatus.
 */
enum chreUserSettingState {
  CHRE_USER_SETTING_STATE_UNKNOWN = -1,
  CHRE_USER_SETTING_STATE_DISABLED = 0,
  CHRE_USER_SETTING_STATE_ENABLED = 1
};

/**
 * The nanoappHandleEvent argument for CHRE settings changed notifications.
 */
struct chreUserSettingChangedEvent {
  //! Indicates the setting whose state has changed.
  uint8_t setting;

  //! A value that corresponds to a member in enum chreUserSettingState,
  // indicating the latest value of the setting.
  int8_t settingState;
};

/**
 * Get the current state of a given setting.
 *
 * @param setting The setting to get the current status of.
 *
 * @return The current state of the requested setting. The state is returned
 * as an int8_t to be consistent with the associated event data, but is
 * guaranteed to be a valid enum chreUserSettingState member.
 *
 * @since v1.5
 */
int8_t chreUserSettingGetState(uint8_t setting);

/**
 * Register or deregister for a notification on a status change for a given
 * setting. Note that registration does not produce an event with the initial
 * (or current) state, though nanoapps can use chreUserSettingGetState() for
 * this purpose.
 *
 * @param setting The setting on whose change a notification is desired.
 * @param enable The nanoapp is registered to receive notifications on a
 * change in the user settings if this parameter is true, otherwise the
 * nanoapp receives no further notifications for this setting.
 *
 * @since v1.5
 */
void chreUserSettingConfigureEvents(uint8_t setting, bool enable);

#ifdef __cplusplus
}
#endif

#endif  /* _CHRE_USER_SETTINGS_H_ */
