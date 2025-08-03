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

#include <algorithm>
#include <cinttypes>
#include <cstring>

#include "chre/platform/log.h"
#include "chre/platform/shared/pal_system_api.h"
#include "chre/util/fixed_size_vector.h"
#include "chre/util/macros.h"
#include "chre/util/nanoapp/wifi.h"
#include "chre/util/optional.h"
#include "chre_api/chre/common.h"
#include "gtest/gtest.h"

namespace {

/************************************************
 *  Prototypes
 ***********************************************/
void chreWifiScanResponseCallback(bool pending, uint8_t errorCode);
void chreWifiScanEventCallback(struct chreWifiScanEvent *event);

struct WifiScanResponse {
  bool pending;
  uint8_t errorCode;
};

/************************************************
 *  Global variables
 ***********************************************/
const chrePalWifiCallbacks gChreWifiPalCallbacks = {
    .scanResponseCallback = chreWifiScanResponseCallback,
    .scanEventCallback = chreWifiScanEventCallback,
};

chre::Optional<WifiScanResponse> gWifiScanResponse;
chre::FixedSizeVector<chreWifiScanResult, CHRE_PAL_WIFI_SCAN_CACHE_CAPACITY>
    gWifiScanResultList;
chre::Optional<chreWifiScanEvent> gExpectedWifiScanEvent;
bool gWifiScanEventCompleted;

/************************************************
 *  Test class
 ***********************************************/
class WifiScanCacheTests : public ::testing::Test {
 protected:
  void SetUp() override {
    clearTestState();
    EXPECT_TRUE(chreWifiScanCacheInit(&chre::gChrePalSystemApi,
                                      &gChreWifiPalCallbacks));
  }

  void TearDown() override {
    chreWifiScanCacheDeinit();
  }

  void clearTestState() {
    gExpectedWifiScanEvent.reset();
    gWifiScanResponse.reset();
    while (!gWifiScanResultList.empty()) {
      gWifiScanResultList.pop_back();
    }
  }
};

/************************************************
 *  Private functions
 ***********************************************/
void chreWifiScanResponseCallback(bool pending, uint8_t errorCode) {
  WifiScanResponse response = {
      .pending = pending,
      .errorCode = errorCode,
  };
  gWifiScanResponse = response;
}

void chreWifiScanEventCallback(struct chreWifiScanEvent *event) {
  ASSERT_TRUE(gExpectedWifiScanEvent.has_value());
  EXPECT_EQ(event->version, gExpectedWifiScanEvent->version);
  EXPECT_EQ(event->scanType, gExpectedWifiScanEvent->scanType);
  EXPECT_EQ(event->ssidSetSize, gExpectedWifiScanEvent->ssidSetSize);
  ASSERT_EQ(event->scannedFreqListLen,
            gExpectedWifiScanEvent->scannedFreqListLen);
  if (event->scannedFreqListLen > 0) {
    ASSERT_NE(event->scannedFreqList, nullptr);
    EXPECT_EQ(
        memcmp(gExpectedWifiScanEvent->scannedFreqList, event->scannedFreqList,
               event->scannedFreqListLen * sizeof(uint32_t)),
        0);
  }

  EXPECT_EQ(event->radioChainPref, gExpectedWifiScanEvent->radioChainPref);
  EXPECT_EQ(event->eventIndex, gExpectedWifiScanEvent->eventIndex);
  gExpectedWifiScanEvent->eventIndex++;

  for (uint8_t i = 0; i < event->resultCount; i++) {
    const chreWifiScanResult &result = event->results[i];
    gWifiScanResultList.push_back(result);
  }

  if (gWifiScanResultList.size() == event->resultTotal) {
    gWifiScanEventCompleted = true;
  }

  chreWifiScanCacheReleaseScanEvent(event);
}

void beginDefaultWifiCache(const uint32_t *scannedFreqList,
                           uint16_t scannedFreqListLen,
                           bool activeScanResult = true) {
  chreWifiScanEvent event;
  memset(&event, 0, sizeof(chreWifiScanEvent));
  event.version = CHRE_WIFI_SCAN_EVENT_VERSION;
  event.scanType = CHRE_WIFI_SCAN_TYPE_ACTIVE;
  event.scannedFreqList = scannedFreqList;
  event.scannedFreqListLen = scannedFreqListLen;
  event.radioChainPref = CHRE_WIFI_RADIO_CHAIN_PREF_DEFAULT;
  gExpectedWifiScanEvent = event;

  chreWifiScanCacheScanEventBegin(
      static_cast<enum chreWifiScanType>(gExpectedWifiScanEvent->scanType),
      gExpectedWifiScanEvent->ssidSetSize,
      gExpectedWifiScanEvent->scannedFreqList,
      gExpectedWifiScanEvent->scannedFreqListLen,
      gExpectedWifiScanEvent->radioChainPref, activeScanResult);
}

void cacheDefaultWifiCacheTest(size_t numEvents,
                               const uint32_t *scannedFreqList,
                               uint16_t scannedFreqListLen,
                               bool activeScanResult = true,
                               bool scanMonitoringEnabled = false) {
  gWifiScanEventCompleted = false;
  beginDefaultWifiCache(scannedFreqList, scannedFreqListLen, activeScanResult);

  chreWifiScanResult result = {};
  for (size_t i = 0; i < numEvents; i++) {
    result.rssi = static_cast<int8_t>(i);
    memcpy(result.bssid, &i, sizeof(i));
    chreWifiScanCacheScanEventAdd(&result);
  }

  chreWifiScanCacheScanEventEnd(CHRE_ERROR_NONE);

  if (activeScanResult) {
    EXPECT_TRUE(gWifiScanResponse.has_value());
    EXPECT_EQ(gWifiScanResponse->pending, true);
    ASSERT_EQ(gWifiScanResponse->errorCode, CHRE_ERROR_NONE);
  } else {
    EXPECT_FALSE(gWifiScanResponse.has_value());
  }

  size_t numEventsExpected = 0;
  if (activeScanResult || scanMonitoringEnabled) {
    numEventsExpected = std::min(
        numEvents, static_cast<size_t>(CHRE_PAL_WIFI_SCAN_CACHE_CAPACITY));
    ASSERT_TRUE(gWifiScanEventCompleted);
  }

  ASSERT_EQ(gWifiScanResultList.size(), numEventsExpected);
  for (size_t i = 0; i < gWifiScanResultList.size(); i++) {
    // ageMs is not known apriori
    result.ageMs = gWifiScanResultList[i].ageMs;
    result.rssi = static_cast<int8_t>(i);
    memcpy(result.bssid, &i, sizeof(i));
    EXPECT_EQ(
        memcmp(&gWifiScanResultList[i], &result, sizeof(chreWifiScanResult)),
        0);
  }
}

void testCacheDispatch(size_t numEvents, uint32_t maxScanAgeMs,
                       bool expectSuccess) {
  cacheDefaultWifiCacheTest(numEvents, nullptr /* scannedFreqList */,
                            0 /* scannedFreqListLen */);

  gExpectedWifiScanEvent->eventIndex = 0;
  gWifiScanResponse.reset();
  while (!gWifiScanResultList.empty()) {
    gWifiScanResultList.pop_back();
  }

  struct chreWifiScanParams params = {
      .scanType = CHRE_WIFI_SCAN_TYPE_NO_PREFERENCE,
      .maxScanAgeMs = maxScanAgeMs,
      .frequencyListLen = 0,
      .frequencyList = nullptr,
      .ssidListLen = 0,
      .ssidList = nullptr,
      .radioChainPref = CHRE_WIFI_RADIO_CHAIN_PREF_DEFAULT,
      .channelSet = CHRE_WIFI_CHANNEL_SET_NON_DFS,
  };
  EXPECT_EQ(chreWifiScanCacheDispatchFromCache(&params), expectSuccess);

  EXPECT_EQ(gWifiScanResponse.has_value(), expectSuccess);
  if (expectSuccess) {
    EXPECT_TRUE(gWifiScanResponse->pending);
    EXPECT_EQ(gWifiScanResponse->errorCode, CHRE_ERROR_NONE);
  }

  EXPECT_EQ(gWifiScanResultList.size(), expectSuccess ? numEvents : 0);
}

}  // anonymous namespace

/************************************************
 *  Tests
 ***********************************************/
TEST_F(WifiScanCacheTests, SingleWifiResultTest) {
  cacheDefaultWifiCacheTest(1 /* numEvents */, nullptr /* scannedFreqList */,
                            0 /* scannedFreqListLen */);
}

TEST_F(WifiScanCacheTests, MultiWifiResultTest) {
  cacheDefaultWifiCacheTest(
      CHRE_PAL_WIFI_SCAN_CACHE_MAX_RESULT_COUNT + 1 /* numEvents */,
      nullptr /* scannedFreqList */, 0 /* scannedFreqListLen */);
}

TEST_F(WifiScanCacheTests, WifiResultOverflowTest) {
  cacheDefaultWifiCacheTest(
      CHRE_PAL_WIFI_SCAN_CACHE_CAPACITY + 1 /* numEvents */,
      nullptr /* scannedFreqList */, 0 /* scannedFreqListLen */);
}

TEST_F(WifiScanCacheTests, EmptyWifiResultTest) {
  cacheDefaultWifiCacheTest(0 /* numEvents */, nullptr /* scannedFreqList */,
                            0 /* scannedFreqListLen */);
}

TEST_F(WifiScanCacheTests, FailedWifiCacheTest) {
  beginDefaultWifiCache(nullptr /* scannedFreqList */,
                        0 /* scannedFreqListLen */);

  chreWifiScanCacheScanEventEnd(CHRE_ERROR);

  EXPECT_TRUE(gWifiScanResponse.has_value());
  EXPECT_FALSE(gWifiScanResponse->pending);
  EXPECT_EQ(gWifiScanResponse->errorCode, CHRE_ERROR);

  EXPECT_EQ(gWifiScanResultList.size(), 0);
}

TEST_F(WifiScanCacheTests, FrequencyListTest) {
  const uint32_t freqList[2] = {5210, 5240};
  cacheDefaultWifiCacheTest(1 /* numEvents */, freqList, ARRAY_SIZE(freqList));
}

TEST_F(WifiScanCacheTests, InvalidFrequencyListTest) {
  beginDefaultWifiCache(nullptr /* scannedFreqList */,
                        1 /* scannedFreqListLen */);

  EXPECT_TRUE(gWifiScanResponse.has_value());
  EXPECT_FALSE(gWifiScanResponse->pending);
  EXPECT_EQ(gWifiScanResponse->errorCode, CHRE_ERROR_INVALID_ARGUMENT);

  EXPECT_EQ(gWifiScanResultList.size(), 0);
}

TEST_F(WifiScanCacheTests, SequentialWifiResultTest) {
  cacheDefaultWifiCacheTest(1 /* numEvents */, nullptr /* scannedFreqList */,
                            0 /* scannedFreqListLen */);

  clearTestState();
  cacheDefaultWifiCacheTest(1 /* numEvents */, nullptr /* scannedFreqList */,
                            0 /* scannedFreqListLen */);
}

TEST_F(WifiScanCacheTests, ScanMonitorDisabledTest) {
  cacheDefaultWifiCacheTest(1 /* numEvents */, nullptr /* scannedFreqList */,
                            0 /* scannedFreqListLen */,
                            false /* activeScanResult */,
                            false /* scanMonitoringEnabled */);
}

TEST_F(WifiScanCacheTests, ScanMonitorEnabledTest) {
  chreWifiScanCacheConfigureScanMonitor(true /* enable */);
  cacheDefaultWifiCacheTest(1 /* numEvents */, nullptr /* scannedFreqList */,
                            0 /* scannedFreqListLen */,
                            false /* activeScanResult */,
                            true /* scanMonitoringEnabled */);
}

TEST_F(WifiScanCacheTests, ScanMonitorEnableDisableTest) {
  chreWifiScanCacheConfigureScanMonitor(true /* enable */);
  cacheDefaultWifiCacheTest(1 /* numEvents */, nullptr /* scannedFreqList */,
                            0 /* scannedFreqListLen */,
                            false /* activeScanResult */,
                            true /* scanMonitoringEnabled */);

  clearTestState();
  chreWifiScanCacheConfigureScanMonitor(false /* enable */);
  cacheDefaultWifiCacheTest(1 /* numEvents */, nullptr /* scannedFreqList */,
                            0 /* scannedFreqListLen */,
                            false /* activeScanResult */,
                            false /* scanMonitoringEnabled */);
}

TEST_F(WifiScanCacheTests, CacheDispatchTest) {
  testCacheDispatch(1 /* numEvents */, 5000 /* maxScanAgeMs */,
                    true /* expectSuccess */);
}

TEST_F(WifiScanCacheTests, ZeroMaxScanAgeCacheDispatchTest) {
  testCacheDispatch(1 /* numEvents */, 0 /* maxScanAgeMs */,
                    false /* expectSuccess */);
}

TEST_F(WifiScanCacheTests, DuplicateScanResultTest) {
  beginDefaultWifiCache(nullptr /* scannedFreqList */,
                        0 /* scannedFreqListLen */,
                        true /* activeScanResult */);

  chreWifiScanResult result = {};
  result.rssi = -98;
  result.primaryChannel = 5270;
  const char *dummySsid = "Test ssid";
  memcpy(result.ssid, dummySsid, strlen(dummySsid));
  result.ssidLen = strlen(dummySsid);
  const char *dummyBssid = "12:34:56:78:9a:bc";
  memcpy(result.bssid, dummyBssid, strlen(dummyBssid));
  chreWifiScanResult result2 = {};
  result2.rssi = -98;
  result2.primaryChannel = 5270;
  const char *dummySsid2 = "Test ssid 2";
  memcpy(result2.ssid, dummySsid2, strlen(dummySsid2));
  result2.ssidLen = strlen(dummySsid2);
  const char *dummyBssid2 = "34:56:78:9a:bc:de";
  memcpy(result2.bssid, dummyBssid2, strlen(dummyBssid2));

  chreWifiScanCacheScanEventAdd(&result);
  chreWifiScanCacheScanEventAdd(&result2);
  chreWifiScanCacheScanEventAdd(&result);

  chreWifiScanCacheScanEventEnd(CHRE_ERROR_NONE);

  EXPECT_TRUE(gWifiScanResponse.has_value());
  EXPECT_EQ(gWifiScanResponse->pending, true);
  ASSERT_EQ(gWifiScanResponse->errorCode, CHRE_ERROR_NONE);

  ASSERT_EQ(gWifiScanResultList.size(), 2);
  result.ageMs = gWifiScanResultList[0].ageMs;
  EXPECT_EQ(
      memcmp(&gWifiScanResultList[0], &result, sizeof(chreWifiScanResult)), 0);
  result2.ageMs = gWifiScanResultList[1].ageMs;
  EXPECT_EQ(
      memcmp(&gWifiScanResultList[1], &result2, sizeof(chreWifiScanResult)), 0);
}
