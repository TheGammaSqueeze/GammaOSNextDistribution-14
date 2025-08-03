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

#ifndef CHRE_UTIL_SYSTEM_WIFI_UTIL_H_
#define CHRE_UTIL_SYSTEM_WIFI_UTIL_H_

#include "chre_api/chre.h"

namespace chre {

// Return a v1.4-compliant chreWifiScanParams from a v1.5+ one.
struct chreWifiScanParams translateToLegacyWifiScanParams(
    const struct chreWifiScanParams *params) {
  // Copy v1.4-compliant fields over.
  struct chreWifiScanParams legacyParams = {};
  legacyParams.scanType = params->scanType;
  legacyParams.maxScanAgeMs = params->maxScanAgeMs;
  legacyParams.frequencyListLen = params->frequencyListLen;
  legacyParams.frequencyList = params->frequencyList;
  legacyParams.ssidListLen = params->ssidListLen;
  legacyParams.ssidList = params->ssidList;
  legacyParams.radioChainPref = params->radioChainPref;

  // channelSet is ignored by v1.4- platforms.

  if (params->scanType == CHRE_WIFI_SCAN_TYPE_NO_PREFERENCE) {
    if (params->channelSet == CHRE_WIFI_CHANNEL_SET_NON_DFS) {
      legacyParams.scanType = CHRE_WIFI_SCAN_TYPE_ACTIVE;
    } else {
      legacyParams.scanType = CHRE_WIFI_SCAN_TYPE_ACTIVE_PLUS_PASSIVE_DFS;
    }
  }

  return legacyParams;
}

}  // namespace chre

#endif  // CHRE_UTIL_SYSTEM_WIFI_UTIL_H_
