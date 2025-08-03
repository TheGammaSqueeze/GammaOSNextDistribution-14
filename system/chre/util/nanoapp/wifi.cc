/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include "chre/util/nanoapp/wifi.h"

#ifdef CHRE_IS_NANOAPP_BUILD
#include "chre/util/nanoapp/log.h"
#else
#include "chre/platform/log.h"
#endif  // CHRE_IS_NANOAPP_BUILD

#include <cctype>
#include <cinttypes>
#include <cstdio>
#include <cstring>

namespace chre {

void logChreWifiResult(const chreWifiScanResult &result, bool logSsidOnly) {
  const char *ssidStr = "<non-printable>";
  char ssidBuffer[kMaxSsidStrLen];
  if (result.ssidLen == 0) {
    ssidStr = "<empty>";
  } else if (parseSsidToStr(ssidBuffer, sizeof(ssidBuffer), result.ssid,
                            result.ssidLen)) {
    ssidStr = ssidBuffer;
  }

  LOGI("Found network with SSID: %s", ssidStr);
  if (!logSsidOnly) {
    const char *bssidStr = "<non-printable>";
    char bssidBuffer[kBssidStrLen];
    if (parseBssidToStr(result.bssid, bssidBuffer, sizeof(bssidBuffer))) {
      bssidStr = bssidBuffer;
    }

    LOGI("  age (ms): %" PRIu32, result.ageMs);
    LOGI("  capability info: %" PRIx16, result.capabilityInfo);
    LOGI("  bssid: %s", bssidStr);
    LOGI("  flags: %" PRIx8, result.flags);
    LOGI("  rssi: %" PRId8 "dBm", result.rssi);
    LOGI("  band: %s (%" PRIu8 ")", parseChreWifiBand(result.band),
         result.band);
    LOGI("  primary channel: %" PRIu32, result.primaryChannel);
    LOGI("  center frequency primary: %" PRIu32, result.centerFreqPrimary);
    LOGI("  center frequency secondary: %" PRIu32, result.centerFreqSecondary);
    LOGI("  channel width: %" PRIu8, result.channelWidth);
    LOGI("  security mode: %" PRIx8, result.securityMode);
  }
}

bool parseSsidToStr(char *buffer, size_t bufferLen, const uint8_t *ssid,
                    uint8_t ssidLen) {
  // Ensure that there is enough space in the buffer to copy the SSID and
  // null-terminate it.
  bool success = (bufferLen >= static_cast<size_t>(ssidLen + 1));

  if (success) {
    // Verify that the ssid is entirely printable characters and ASCII spaces.
    for (uint8_t i = 0; i < ssidLen; i++) {
      if (!isgraph(ssid[i]) && ssid[i] != ' ') {
        success = false;
        break;
      }
    }
  }

  if (success) {
    // Copy the SSID to the buffer and null-terminate.
    memcpy(buffer, ssid, ssidLen + 1);
    buffer[ssidLen] = '\0';
  }

  return success;
}

bool parseBssidToStr(const uint8_t bssid[CHRE_WIFI_BSSID_LEN], char *buffer,
                     size_t bufferLen) {
  const char *kFormat = "%02" PRIx8 ":%02" PRIx8 ":%02" PRIx8 ":%02" PRIx8
                        ":%02" PRIx8 ":%02" PRIx8;

  bool success = false;
  if (bufferLen >= kBssidStrLen) {
    success = true;
    snprintf(buffer, bufferLen, kFormat, bssid[0], bssid[1], bssid[2], bssid[3],
             bssid[4], bssid[5]);
  }

  return success;
}

const char *parseChreWifiBand(uint8_t band) {
  switch (band) {
    case CHRE_WIFI_BAND_2_4_GHZ:
      return "2.4GHz";
    case CHRE_WIFI_BAND_MASK_5_GHZ:
      return "5GHz";
    default:
      return "<invalid>";
  }
}

}  // namespace chre
