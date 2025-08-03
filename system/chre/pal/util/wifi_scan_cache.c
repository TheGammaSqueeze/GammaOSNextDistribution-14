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

#include "chre/pal/util/wifi_scan_cache.h"

#include <inttypes.h>

#include "chre/util/macros.h"

/************************************************
 *  Prototypes
 ***********************************************/

struct chreWifiScanCacheState {
  //! true if the scan cache has started, i.e. chreWifiScanCacheScanEventBegin
  //! was invoked and has not yet ended.
  bool started;

  //! true if the current scan cache is a result of a CHRE active scan request.
  bool activeScanResult;

  //! The number of chreWifiScanResults dropped due to OOM.
  uint16_t numWifiScanResultsDropped;

  //! Stores the WiFi cache elements
  struct chreWifiScanEvent event;
  struct chreWifiScanResult resultList[CHRE_PAL_WIFI_SCAN_CACHE_CAPACITY];

  //! The number of chreWifiScanEvent data pending release via
  //! chreWifiScanCacheReleaseScanEvent().
  uint8_t numWifiEventsPendingRelease;

  bool scanMonitoringEnabled;

  uint32_t scannedFreqList[CHRE_WIFI_FREQUENCY_LIST_MAX_LEN];
};

/************************************************
 *  Global variables
 ***********************************************/
static const struct chrePalSystemApi *gSystemApi = NULL;
static const struct chrePalWifiCallbacks *gCallbacks = NULL;

static struct chreWifiScanCacheState gWifiCacheState;

//! true if scan monitoring is enabled via
//! chreWifiScanCacheConfigureScanMonitor().
static bool gScanMonitoringEnabled;

static const uint64_t kOneMillisecondInNanoseconds = UINT64_C(1000000);

/************************************************
 *  Private functions
 ***********************************************/
static bool chreWifiScanCacheIsInitialized(void) {
  return (gSystemApi != NULL && gCallbacks != NULL);
}

static bool areAllScanEventsReleased(void) {
  return gWifiCacheState.numWifiEventsPendingRelease == 0;
}

static bool isFrequencyListValid(const uint32_t *frequencyList,
                                 uint16_t frequencyListLen) {
  return (frequencyListLen == 0) || (frequencyList != NULL);
}

static bool paramsMatchScanCache(const struct chreWifiScanParams *params) {
  uint64_t timeNs = gWifiCacheState.event.referenceTime;
  bool scan_within_age =
      (timeNs >= gSystemApi->getCurrentTime() -
                     (params->maxScanAgeMs * kOneMillisecondInNanoseconds));

  // Perform a conservative check for the params and scan cache.
  // TODO(b/174510035): Consider optimizing for the case for channelSet ==
  // CHRE_WIFI_CHANNEL_SET_ALL.
  bool params_non_dfs =
      (params->scanType == CHRE_WIFI_SCAN_TYPE_ACTIVE) ||
      ((params->scanType == CHRE_WIFI_SCAN_TYPE_NO_PREFERENCE) &&
       (params->channelSet == CHRE_WIFI_CHANNEL_SET_NON_DFS));
  bool cache_non_dfs =
      (gWifiCacheState.event.scanType == CHRE_WIFI_SCAN_TYPE_ACTIVE) ||
      (gWifiCacheState.event.scanType == CHRE_WIFI_SCAN_TYPE_PASSIVE);

  bool cache_all_freq = (gWifiCacheState.event.scannedFreqListLen == 0);
  bool cache_all_ssid = (gWifiCacheState.event.ssidSetSize == 0);

  return scan_within_age && (params_non_dfs || !cache_non_dfs) &&
         cache_all_freq && cache_all_ssid;
}

static bool isWifiScanCacheBusy(bool logOnBusy) {
  bool busy = true;
  if (gWifiCacheState.started) {
    if (logOnBusy) {
      gSystemApi->log(CHRE_LOG_ERROR, "Scan cache already started");
    }
  } else if (!areAllScanEventsReleased()) {
    if (logOnBusy) {
      gSystemApi->log(CHRE_LOG_ERROR, "Scan cache events pending release");
    }
  } else {
    busy = false;
  }

  return busy;
}

static void chreWifiScanCacheDispatchAll(void) {
  gSystemApi->log(CHRE_LOG_DEBUG, "Dispatching %" PRIu8 " events",
                  gWifiCacheState.event.resultTotal);
  if (gWifiCacheState.event.resultTotal == 0) {
    gWifiCacheState.event.eventIndex = 0;
    gWifiCacheState.event.resultCount = 0;
    gWifiCacheState.event.results = NULL;
    gCallbacks->scanEventCallback(&gWifiCacheState.event);
  } else {
    uint8_t eventIndex = 0;
    for (uint16_t i = 0; i < gWifiCacheState.event.resultTotal;
         i += CHRE_PAL_WIFI_SCAN_CACHE_MAX_RESULT_COUNT) {
      gWifiCacheState.event.resultCount =
          MIN(CHRE_PAL_WIFI_SCAN_CACHE_MAX_RESULT_COUNT,
              (uint8_t)(gWifiCacheState.event.resultTotal - i));
      gWifiCacheState.event.eventIndex = eventIndex++;
      gWifiCacheState.event.results = &gWifiCacheState.resultList[i];

      // TODO(b/174511061): The current approach only works for situations where
      // the event is released immediately. Add a way to handle this scenario
      // (e.g. an array of chreWifiScanEvent's).
      gWifiCacheState.numWifiEventsPendingRelease++;
      gCallbacks->scanEventCallback(&gWifiCacheState.event);
    }
  }
}

static bool isWifiScanResultInCache(const struct chreWifiScanResult *result,
                                    size_t *index) {
  for (uint8_t i = 0; i < gWifiCacheState.event.resultTotal; i++) {
    const struct chreWifiScanResult *cacheResult =
        &gWifiCacheState.resultList[i];
    // Filtering based on BSSID + SSID + frequency based on Linux cfg80211.
    // https://github.com/torvalds/linux/blob/master/net/wireless/scan.c
    if ((result->primaryChannel == cacheResult->primaryChannel) &&
        (memcmp(result->bssid, cacheResult->bssid, CHRE_WIFI_BSSID_LEN) == 0) &&
        (result->ssidLen == cacheResult->ssidLen) &&
        (memcmp(result->ssid, cacheResult->ssid, result->ssidLen) == 0)) {
      *index = i;
      return true;
    }
  }

  return false;
}

/************************************************
 *  Public functions
 ***********************************************/
bool chreWifiScanCacheInit(const struct chrePalSystemApi *systemApi,
                           const struct chrePalWifiCallbacks *callbacks) {
  if (systemApi == NULL || callbacks == NULL) {
    return false;
  }

  gSystemApi = systemApi;
  gCallbacks = callbacks;
  memset(&gWifiCacheState, 0, sizeof(gWifiCacheState));
  gScanMonitoringEnabled = false;

  return true;
}

void chreWifiScanCacheDeinit(void) {
  gSystemApi = NULL;
  gCallbacks = NULL;
}

bool chreWifiScanCacheScanEventBegin(enum chreWifiScanType scanType,
                                     uint8_t ssidSetSize,
                                     const uint32_t *scannedFreqList,
                                     uint16_t scannedFreqListLength,
                                     uint8_t radioChainPref,
                                     bool activeScanResult) {
  bool success = false;
  if (chreWifiScanCacheIsInitialized()) {
    enum chreError error = CHRE_ERROR_NONE;
    if (!isFrequencyListValid(scannedFreqList, scannedFreqListLength)) {
      gSystemApi->log(CHRE_LOG_ERROR, "Invalid frequency argument");
      error = CHRE_ERROR_INVALID_ARGUMENT;
    } else if (isWifiScanCacheBusy(true /* logOnBusy */)) {
      error = CHRE_ERROR_BUSY;
    } else {
      success = true;
      memset(&gWifiCacheState, 0, sizeof(gWifiCacheState));

      gWifiCacheState.event.version = CHRE_WIFI_SCAN_EVENT_VERSION;
      gWifiCacheState.event.scanType = scanType;
      gWifiCacheState.event.ssidSetSize = ssidSetSize;

      scannedFreqListLength =
          MIN(scannedFreqListLength, CHRE_WIFI_FREQUENCY_LIST_MAX_LEN);
      if (scannedFreqList != NULL) {
        memcpy(gWifiCacheState.scannedFreqList, scannedFreqList,
               scannedFreqListLength * sizeof(uint32_t));
      }
      gWifiCacheState.event.scannedFreqListLen = scannedFreqListLength;
      gWifiCacheState.event.radioChainPref = radioChainPref;

      gWifiCacheState.activeScanResult = activeScanResult;
      gWifiCacheState.started = true;
    }

    if (activeScanResult && !success) {
      gCallbacks->scanResponseCallback(false /* pending */, error);
    }
  }

  return success;
}

void chreWifiScanCacheScanEventAdd(const struct chreWifiScanResult *result) {
  if (!gWifiCacheState.started) {
    gSystemApi->log(CHRE_LOG_ERROR, "Cannot add to cache before starting it");
  } else {
    size_t index;
    bool exists = isWifiScanResultInCache(result, &index);
    if (!exists && gWifiCacheState.event.resultTotal >=
                       CHRE_PAL_WIFI_SCAN_CACHE_CAPACITY) {
      // TODO(b/174510884): Filter based on e.g. RSSI if full
      gWifiCacheState.numWifiScanResultsDropped++;
    } else {
      if (!exists) {
        // Only add a new entry if the result was not already cached.
        index = gWifiCacheState.event.resultTotal;
        gWifiCacheState.event.resultTotal++;
      }

      memcpy(&gWifiCacheState.resultList[index], result,
             sizeof(const struct chreWifiScanResult));

      // ageMs will be properly populated in chreWifiScanCacheScanEventEnd
      gWifiCacheState.resultList[index].ageMs =
          (uint32_t)gSystemApi->getCurrentTime() /
          (uint32_t)kOneMillisecondInNanoseconds;
    }
  }
}

void chreWifiScanCacheScanEventEnd(enum chreError errorCode) {
  if (gWifiCacheState.started) {
    if (gWifiCacheState.numWifiScanResultsDropped > 0) {
      gSystemApi->log(CHRE_LOG_WARN,
                      "Dropped total of %" PRIu32 " access points",
                      gWifiCacheState.numWifiScanResultsDropped);
    }
    if (gWifiCacheState.activeScanResult) {
      gCallbacks->scanResponseCallback(
          errorCode == CHRE_ERROR_NONE /* pending */, errorCode);
    }

    if (errorCode == CHRE_ERROR_NONE &&
        (gWifiCacheState.activeScanResult || gScanMonitoringEnabled)) {
      gWifiCacheState.event.referenceTime = gSystemApi->getCurrentTime();
      gWifiCacheState.event.scannedFreqList = gWifiCacheState.scannedFreqList;

      uint32_t referenceTimeMs = (uint32_t)gWifiCacheState.event.referenceTime /
                                 (uint32_t)kOneMillisecondInNanoseconds;
      for (uint16_t i = 0; i < gWifiCacheState.event.resultTotal; i++) {
        gWifiCacheState.resultList[i].ageMs =
            referenceTimeMs - gWifiCacheState.resultList[i].ageMs;
      }

      chreWifiScanCacheDispatchAll();
    }

    gWifiCacheState.started = false;
    gWifiCacheState.activeScanResult = false;
  }
}

bool chreWifiScanCacheDispatchFromCache(
    const struct chreWifiScanParams *params) {
  if (!chreWifiScanCacheIsInitialized()) {
    return false;
  }

  if (paramsMatchScanCache(params) &&
      !isWifiScanCacheBusy(false /* logOnBusy */)) {
    // TODO(b/174511061): Handle scenario where cache is working on delivering
    // a scan event. Ideally the library will wait until it is complete to
    // dispatch from the cache if it meets the criteria, rather than scheduling
    // a fresh scan.
    gCallbacks->scanResponseCallback(true /* pending */, CHRE_ERROR_NONE);
    chreWifiScanCacheDispatchAll();
    return true;
  } else {
    return false;
  }
}

void chreWifiScanCacheReleaseScanEvent(struct chreWifiScanEvent *event) {
  if (!chreWifiScanCacheIsInitialized()) {
    return;
  }

  if (event != &gWifiCacheState.event) {
    gSystemApi->log(CHRE_LOG_ERROR, "Invalid event pointer %p", event);
  } else if (gWifiCacheState.numWifiEventsPendingRelease > 0) {
    gWifiCacheState.numWifiEventsPendingRelease--;
  }
}

void chreWifiScanCacheConfigureScanMonitor(bool enable) {
  if (!chreWifiScanCacheIsInitialized()) {
    return;
  }

  gScanMonitoringEnabled = enable;
}
