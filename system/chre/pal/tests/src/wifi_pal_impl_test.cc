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

#include "wifi_pal_impl_test.h"

#include <cinttypes>

#include "chre/platform/linux/task_util/task_manager.h"
#include "chre/platform/log.h"
#include "chre/platform/shared/pal_system_api.h"
#include "chre/platform/system_time.h"
#include "chre/util/lock_guard.h"
#include "chre/util/macros.h"
#include "chre/util/nanoapp/wifi.h"

// Flag to require on-demand WiFi scanning capability to be enabled for the test
// to pass. Set to false to allow tests to pass on disabled platforms.
#ifndef PAL_IMPL_TEST_WIFI_ON_DEMAND_SCAN_REQUIRED
#define PAL_IMPL_TEST_WIFI_ON_DEMAND_SCAN_REQUIRED true
#endif

// Same as above for scan monitoring.
#ifndef PAL_IMPL_TEST_WIFI_SCAN_MONITORING_REQUIRED
#define PAL_IMPL_TEST_WIFI_SCAN_MONITORING_REQUIRED true
#endif

namespace wifi_pal_impl_test {

namespace {

using ::chre::Nanoseconds;
using ::chre::Seconds;
using ::chre::SystemTime;

//! A pointer to the current test running
wifi_pal_impl_test::PalWifiTest *gTest = nullptr;

//! Timeout as specified by the CHRE API
const Nanoseconds kAsyncResultTimeoutNs =
    Nanoseconds(CHRE_ASYNC_RESULT_TIMEOUT_NS);
const Nanoseconds kScanResultTimeoutNs =
    Nanoseconds(CHRE_WIFI_SCAN_RESULT_TIMEOUT_NS);

void chrePalScanMonitorStatusChangeCallback(bool enabled, uint8_t errorCode) {
  if (gTest != nullptr) {
    gTest->scanMonitorStatusChangeCallback(enabled, errorCode);
  }
}

void chrePalScanResponseCallback(bool pending, uint8_t errorCode) {
  if (gTest != nullptr) {
    gTest->scanResponseCallback(pending, errorCode);
  }
}

void chrePalScanEventCallback(struct chreWifiScanEvent *event) {
  if (gTest != nullptr) {
    gTest->scanEventCallback(event);
  }
}

void chrePalRangingEventCallback(uint8_t errorCode,
                                 struct chreWifiRangingEvent *event) {
  if (gTest != nullptr) {
    gTest->rangingEventCallback(errorCode, event);
  }
}

void chrePalNanServiceIdentifierCallback(uint8_t errorCode,
                                         uint32_t subscriptionId) {
  if (gTest != nullptr) {
    gTest->nanServiceIdentifierCallback(errorCode, subscriptionId);
  }
}

void chrePalNanServiceDiscoveryCallback(
    struct chreWifiNanDiscoveryEvent *event) {
  if (gTest != nullptr) {
    gTest->nanServiceDiscoveryCallback(event);
  }
}

void chrePalNanServiceLostCallback(uint32_t subscriptionId,
                                   uint32_t publisherId) {
  if (gTest != nullptr) {
    gTest->nanServiceLostCallback(subscriptionId, publisherId);
  }
}

void chrePalNanServiceTerminatedCallback(uint32_t reason,
                                         uint32_t subscriptionId) {
  if (gTest != nullptr) {
    gTest->nanServiceTerminatedCallback(reason, subscriptionId);
  }
}

void chrePalNanSubscriptionCanceledCallback(uint8_t reason,
                                            uint32_t subscriptionId) {
  if (gTest != nullptr) {
    gTest->nanSubscriptionCanceledCallback(reason, subscriptionId);
  }
}

}  // anonymous namespace

void PalWifiTest::SetUp() {
  chre::TaskManagerSingleton::deinit();
  chre::TaskManagerSingleton::init();
  api_ = chrePalWifiGetApi(CHRE_PAL_WIFI_API_CURRENT_VERSION);
  ASSERT_NE(api_, nullptr);
  EXPECT_EQ(api_->moduleVersion, CHRE_PAL_WIFI_API_CURRENT_VERSION);

  // Open the PAL API
  static const struct chrePalWifiCallbacks kCallbacks = {
      .scanMonitorStatusChangeCallback = chrePalScanMonitorStatusChangeCallback,
      .scanResponseCallback = chrePalScanResponseCallback,
      .scanEventCallback = chrePalScanEventCallback,
      .rangingEventCallback = chrePalRangingEventCallback,
      .nanServiceIdentifierCallback = chrePalNanServiceIdentifierCallback,
      .nanServiceDiscoveryCallback = chrePalNanServiceDiscoveryCallback,
      .nanServiceLostCallback = chrePalNanServiceLostCallback,
      .nanServiceTerminatedCallback = chrePalNanServiceTerminatedCallback,
      .nanSubscriptionCanceledCallback = chrePalNanSubscriptionCanceledCallback,
  };
  ASSERT_TRUE(api_->open(&chre::gChrePalSystemApi, &kCallbacks));
  gTest = this;

  errorCode_ = CHRE_ERROR_LAST;
  numScanResultCount_ = 0;
  lastScanEventReceived_ = false;
  scanEventList_.clear();
  scanParams_.reset();
  lastEventIndex_ = UINT8_MAX;
  scanMonitorEnabled_ = false;
}

void PalWifiTest::TearDown() {
  gTest = nullptr;
  if (api_ != nullptr) {
    api_->close();
  }
  chre::TaskManagerSingleton::deinit();
}

void PalWifiTest::scanMonitorStatusChangeCallback(bool enabled,
                                                  uint8_t errorCode) {
  LOGI("Received scan monitor response with enabled %d error %" PRIu8, enabled,
       errorCode);
  if (errorCode == CHRE_ERROR_LAST) {
    LOGE("Received CHRE_ERROR_LAST");
    errorCode = CHRE_ERROR;
  }
  chre::LockGuard<chre::Mutex> lock(mutex_);
  scanMonitorEnabled_ = enabled;
  errorCode_ = errorCode;
  condVar_.notify_one();
}

void PalWifiTest::scanResponseCallback(bool pending, uint8_t errorCode) {
  LOGI("Received scan response with pending %d error %" PRIu8, pending,
       errorCode);
  if (errorCode == CHRE_ERROR_LAST) {
    LOGE("Received CHRE_ERROR_LAST");
    errorCode = CHRE_ERROR;
  }
  chre::LockGuard<chre::Mutex> lock(mutex_);
  errorCode_ = errorCode;
  condVar_.notify_one();
}

void PalWifiTest::scanEventCallback(struct chreWifiScanEvent *event) {
  if (event == nullptr) {
    LOGE("Got null scan event");
  } else {
    {
      chre::LockGuard<chre::Mutex> lock(mutex_);
      scanEventList_.push_back(event);
      numScanResultCount_ += event->resultCount;
      lastScanEventReceived_ = (numScanResultCount_ == event->resultTotal);
    }

    condVar_.notify_one();
  }
}

void PalWifiTest::rangingEventCallback(uint8_t errorCode,
                                       struct chreWifiRangingEvent *event) {
  // TODO:
  UNUSED_VAR(errorCode);
  UNUSED_VAR(event);
}

void PalWifiTest::nanServiceIdentifierCallback(uint8_t errorCode,
                                               uint32_t subscriptionId) {
  chre::LockGuard<chre::Mutex> lock(mutex_);
  subscriptionId_ = subscriptionId;
  errorCode_ = errorCode;
  condVar_.notify_one();
}

void PalWifiTest::nanServiceDiscoveryCallback(
    struct chreWifiNanDiscoveryEvent *event) {
  if (event == nullptr) {
    LOGE("Got null discovery event");
  } else {
    errorCode_ = CHRE_ERROR_LAST;
    publishId_ = event->publishId;
  }
}

void PalWifiTest::nanServiceLostCallback(uint32_t subscriptionId,
                                         uint32_t publisherId) {
  subscriptionId_ = subscriptionId;
  publishId_ = publisherId;
}

void PalWifiTest::nanServiceTerminatedCallback(uint32_t reason,
                                               uint32_t subscriptionId) {
  subscriptionId_ = subscriptionId;
  errorCode_ = reason;
}

void PalWifiTest::nanSubscriptionCanceledCallback(uint8_t errorCode,
                                                  uint32_t subscriptionId) {
  errorCode_ = errorCode;
  subscriptionId_ = subscriptionId;
}

void PalWifiTest::validateWifiScanEvent(const chreWifiScanEvent &event) {
  if (scanParams_.has_value()) {
    EXPECT_EQ(event.scanType, scanParams_->scanType);
    EXPECT_GE(event.referenceTime,
              chreGetTime() - (scanParams_->maxScanAgeMs *
                               chre::kOneMillisecondInNanoseconds));
    EXPECT_EQ(event.radioChainPref, scanParams_->radioChainPref);
    EXPECT_EQ(event.eventIndex, static_cast<uint8_t>(lastEventIndex_ + 1));
  }
}

void PalWifiTest::waitForAsyncResponseAssertSuccess(
    chre::Nanoseconds timeoutNs) {
  bool waitSuccess = true;
  while (errorCode_ == CHRE_ERROR_LAST && waitSuccess) {
    waitSuccess = condVar_.wait_for(mutex_, timeoutNs);
  }
  ASSERT_TRUE(waitSuccess);
  ASSERT_EQ(errorCode_, CHRE_ERROR_NONE);
}

TEST_F(PalWifiTest, ScanAsyncTest) {
  bool hasOnDemandScanCapability =
      (api_->getCapabilities() & CHRE_WIFI_CAPABILITIES_ON_DEMAND_SCAN) ==
      CHRE_WIFI_CAPABILITIES_ON_DEMAND_SCAN;
#if PAL_IMPL_TEST_WIFI_ON_DEMAND_SCAN_REQUIRED
  ASSERT_TRUE(hasOnDemandScanCapability);
#else
  if (!hasOnDemandScanCapability) {
    GTEST_SKIP();
  }
#endif

  // Request a WiFi scan
  chre::LockGuard<chre::Mutex> lock(mutex_);

  struct chreWifiScanParams params = {};
  params.scanType = CHRE_WIFI_SCAN_TYPE_ACTIVE;
  params.maxScanAgeMs = 5000;  // 5 seconds
  params.frequencyListLen = 0;
  params.ssidListLen = 0;
  params.radioChainPref = CHRE_WIFI_RADIO_CHAIN_PREF_DEFAULT;
  scanParams_ = params;

  prepareForAsyncResponse();
  ASSERT_TRUE(api_->requestScan(&scanParams_.value()));
  waitForAsyncResponseAssertSuccess(kScanResultTimeoutNs);

  // The CHRE API only poses timeout requirements on the async response. Use
  // the same timeout to receive the scan results to avoid blocking forever.
  bool waitSuccess = true;
  while (!lastScanEventReceived_ && waitSuccess) {
    waitSuccess = condVar_.wait_for(mutex_, kScanResultTimeoutNs);
  }

  for (auto *event : scanEventList_) {
    for (uint8_t i = 0; i < event->resultCount; i++) {
      const chreWifiScanResult &result = event->results[i];
      chre::logChreWifiResult(result);
    }
    validateWifiScanEvent(*event);

    lastEventIndex_ = event->eventIndex;
    api_->releaseScanEvent(event);
  }

  EXPECT_TRUE(lastScanEventReceived_);
  EXPECT_GT(numScanResultCount_, 0u);
}

// Note: This test only verifies that the scan monitor succeeds according
// to the async response.
TEST_F(PalWifiTest, ScanMonitorTest) {
  bool hasScanMonitoringCapability =
      (api_->getCapabilities() & CHRE_WIFI_CAPABILITIES_SCAN_MONITORING) ==
      CHRE_WIFI_CAPABILITIES_SCAN_MONITORING;
#if PAL_IMPL_TEST_WIFI_SCAN_MONITORING_REQUIRED
  ASSERT_TRUE(hasScanMonitoringCapability);
#else
  if (!hasScanMonitoringCapability) {
    GTEST_SKIP();
  }
#endif

  chre::LockGuard<chre::Mutex> lock(mutex_);

  prepareForAsyncResponse();
  ASSERT_TRUE(api_->configureScanMonitor(true /* enable */));
  waitForAsyncResponseAssertSuccess(kAsyncResultTimeoutNs);
  ASSERT_TRUE(scanMonitorEnabled_);

  prepareForAsyncResponse();
  ASSERT_TRUE(api_->configureScanMonitor(false /* enable */));
  waitForAsyncResponseAssertSuccess(kAsyncResultTimeoutNs);
  ASSERT_FALSE(scanMonitorEnabled_);
}

/*
 * Tests if NAN capabilities are available and requests a service
 * subscription. The test is terminated if NAN capabilities are not
 * available.
 *
 * NOTE: The subscription config currently is mostly a placeholder -
 * please modify it appropriately when testing and/or validating service
 * discovery.
 */
TEST_F(PalWifiTest, NanSubscribeTest) {
  bool hasNanCapabilities =
      (api_->getCapabilities() & CHRE_WIFI_CAPABILITIES_NAN_SUB);
  if (!hasNanCapabilities) {
    GTEST_SKIP();
  }

  prepareForAsyncResponse();
  struct chreWifiNanSubscribeConfig config = {
      .subscribeType = CHRE_WIFI_NAN_SUBSCRIBE_TYPE_PASSIVE,
      .service = "SomeService",
  };
  ASSERT_TRUE(api_->nanSubscribe(&config));
  waitForAsyncResponseAssertSuccess(kAsyncResultTimeoutNs);
  ASSERT_TRUE(subscriptionId_.has_value());
}

/*
 * Tests if NAN capabilities are available and requests a service
 * subscription, then requests a cancelation of the subscription.
 * The test is terminated if NAN capabilities are not
 * available.
 *
 * NOTE: The subscription config currently is mostly a placeholder -
 * please modify it appropriately when testing and/or validating service
 * discovery.
 */
TEST_F(PalWifiTest, NanSubscribeCancelTest) {
  bool hasNanCapabilities =
      (api_->getCapabilities() & CHRE_WIFI_CAPABILITIES_NAN_SUB);
  if (!hasNanCapabilities) {
    GTEST_SKIP();
  }

  prepareForAsyncResponse();
  struct chreWifiNanSubscribeConfig config = {
      .subscribeType = CHRE_WIFI_NAN_SUBSCRIBE_TYPE_PASSIVE,
      .service = "SomeService",
  };
  ASSERT_TRUE(api_->nanSubscribe(&config));
  waitForAsyncResponseAssertSuccess(kAsyncResultTimeoutNs);
  ASSERT_TRUE(subscriptionId_.has_value());

  prepareForAsyncResponse();
  uint32_t cachedSubscriptionId = subscriptionId_.value();
  subscriptionId_.reset();

  ASSERT_TRUE(api_->nanSubscribeCancel(cachedSubscriptionId));
  waitForAsyncResponseAssertSuccess(kAsyncResultTimeoutNs);
  ASSERT_TRUE(subscriptionId_.has_value());
  ASSERT_EQ(subscriptionId_.value(), cachedSubscriptionId);
}

}  // namespace wifi_pal_impl_test
