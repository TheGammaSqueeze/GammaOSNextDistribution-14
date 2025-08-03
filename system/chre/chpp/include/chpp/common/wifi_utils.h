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

#ifndef CHPP_WIFI_UTILS_H_
#define CHPP_WIFI_UTILS_H_

#include <stdbool.h>

#include "chre_api/chre/wifi.h"

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Resets the internal state to use chppCheckWifiScanEventNotification(). Should
 * be used whenever the CHPP WiFi link resets.
 */
void chppCheckWifiScanEventNotificationReset(void);

/**
 * Helper function to validate incoming WiFi scan events. This method will
 * retain history of the previous invocations to verify if the sequence
 * of WiFi scan events are valid.
 *
 * @param chre A non-null pointer to the event.
 *
 * @return false if the check failed.
 */
bool chppCheckWifiScanEventNotification(const struct chreWifiScanEvent *chre);

#ifdef __cplusplus
}
#endif

#endif  // CHPP_WIFI_UTILS_H_
