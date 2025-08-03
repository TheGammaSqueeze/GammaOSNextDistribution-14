/**
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

#include <stdbool.h>
#include <stdint.h>

#include "chpp/common/wifi.h"
#include "chpp/macros.h"
#include "chpp/services/wifi.h"
#include "chre/pal/wifi.h"

static const struct chrePalSystemApi *gSystemApi;
static const struct chrePalWifiCallbacks *gCallbacks;

static bool wifiPalOpen(const struct chrePalSystemApi *systemApi,
                        const struct chrePalWifiCallbacks *callbacks) {
  gSystemApi = systemApi;
  gCallbacks = callbacks;

  return true;
}

static void wifiPalClose(void) {}

static uint32_t wifiPalGetCapabilities(void) {
  return CHRE_WIFI_CAPABILITIES_SCAN_MONITORING |
         CHRE_WIFI_CAPABILITIES_ON_DEMAND_SCAN;
}

static bool wifiPalConfigureScanMonitor(bool enable) {
  // TODO
  UNUSED_VAR(enable);
  return true;  // If successful
}

static bool wifiPalRequestScan(const struct chreWifiScanParams *params) {
  // TODO
  UNUSED_VAR(params);
  return true;  // If successful
}

static void wifiPalReleaseScanEvent(struct chreWifiScanEvent *event) {
  gSystemApi->memoryFree(CHPP_CONST_CAST_POINTER(event->scannedFreqList));
  gSystemApi->memoryFree(CHPP_CONST_CAST_POINTER(event->results));
  gSystemApi->memoryFree(event);
}

static bool wifiPalRequestRanging(const struct chreWifiRangingParams *params) {
  // TODO
  UNUSED_VAR(params);
  return true;  // If successful
}

static void wifiPalReleaseRangingEvent(struct chreWifiRangingEvent *event) {
  gSystemApi->memoryFree(CHPP_CONST_CAST_POINTER(event->results));
  gSystemApi->memoryFree(event);
}

#ifdef CHPP_SERVICE_ENABLED_WIFI
const struct chrePalWifiApi *chrePalWifiGetApi(uint32_t requestedApiVersion) {
  static const struct chrePalWifiApi api = {
      .moduleVersion = CHPP_PAL_WIFI_API_VERSION,
      .open = wifiPalOpen,
      .close = wifiPalClose,
      .getCapabilities = wifiPalGetCapabilities,
      .configureScanMonitor = wifiPalConfigureScanMonitor,
      .requestScan = wifiPalRequestScan,
      .releaseScanEvent = wifiPalReleaseScanEvent,
      .requestRanging = wifiPalRequestRanging,
      .releaseRangingEvent = wifiPalReleaseRangingEvent,
  };

  CHPP_STATIC_ASSERT(
      CHRE_PAL_WIFI_API_CURRENT_VERSION == CHPP_PAL_WIFI_API_VERSION,
      "A newer CHRE PAL API version is available. Please update.");

  if (!CHRE_PAL_VERSIONS_ARE_COMPATIBLE(api.moduleVersion,
                                        requestedApiVersion)) {
    return NULL;
  } else {
    return &api;
  }
}
#endif
