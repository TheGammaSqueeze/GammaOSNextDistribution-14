/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include <cstdint>

#include "chre/core/event_loop_manager.h"
#include "chre/core/settings.h"
#include "chre/platform/linux/pal_wifi.h"
#include "chre/platform/log.h"
#include "chre/util/nanoapp/app_id.h"
#include "chre/util/system/napp_permissions.h"
#include "chre_api/chre/event.h"
#include "chre_api/chre/re.h"
#include "chre_api/chre/wifi.h"
#include "gtest/gtest.h"
#include "test_base.h"
#include "test_event.h"
#include "test_event_queue.h"
#include "test_util.h"

namespace chre {
namespace {
// WifiTimeoutTestBase needs to set timeout more than the max waitForEvent()
// should process (Currently it is
// WifiCanDispatchSecondScanRequestInQueueAfterFirstTimeout). If not,
// waitForEvent will timeout before actual timeout happens in CHRE, making us
// unable to observe how system handles timeout.
class WifiTimeoutTestBase : public TestBase {
 protected:
  uint64_t getTimeoutNs() const override {
    return 3 * CHRE_TEST_WIFI_SCAN_RESULT_TIMEOUT_NS;
  }
};

CREATE_CHRE_TEST_EVENT(SCAN_REQUEST, 20);
CREATE_CHRE_TEST_EVENT(REQUEST_TIMED_OUT, 21);

TEST_F(WifiTimeoutTestBase, WifiScanRequestTimeoutTest) {
  class ScanTestNanoapp : public TestNanoapp {
   public:
    explicit ScanTestNanoapp()
        : TestNanoapp(
              TestNanoappInfo{.perms = NanoappPermissions::CHRE_PERMS_WIFI}) {}

    bool start() override {
      mRequestTimer = CHRE_TIMER_INVALID;
      return true;
    }

    void handleEvent(uint32_t, uint16_t eventType,
                     const void *eventData) override {
      switch (eventType) {
        case CHRE_EVENT_WIFI_ASYNC_RESULT: {
          auto *event = static_cast<const chreAsyncResult *>(eventData);
          if (mRequestTimer != CHRE_TIMER_INVALID) {
            chreTimerCancel(mRequestTimer);
            mRequestTimer = CHRE_TIMER_INVALID;
          }
          if (event->success) {
            TestEventQueueSingleton::get()->pushEvent(
                CHRE_EVENT_WIFI_ASYNC_RESULT,
                *(static_cast<const uint32_t *>(event->cookie)));
          }
          break;
        }

        case CHRE_EVENT_WIFI_SCAN_RESULT: {
          TestEventQueueSingleton::get()->pushEvent(
              CHRE_EVENT_WIFI_SCAN_RESULT);
          break;
        }

        case CHRE_EVENT_TIMER: {
          TestEventQueueSingleton::get()->pushEvent(REQUEST_TIMED_OUT);
          mRequestTimer = CHRE_TIMER_INVALID;
          break;
        }

        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case SCAN_REQUEST:
              bool success = false;
              mCookie = *static_cast<uint32_t *>(event->data);
              if (chreWifiRequestScanAsyncDefault(&mCookie)) {
                mRequestTimer =
                    chreTimerSet(CHRE_TEST_WIFI_SCAN_RESULT_TIMEOUT_NS, nullptr,
                                 true /* oneShot */);
                success = mRequestTimer != CHRE_TIMER_INVALID;
              }
              TestEventQueueSingleton::get()->pushEvent(SCAN_REQUEST, success);
              break;
          }
          break;
        }
      }
    }

   protected:
    uint32_t mCookie;
    uint32_t mRequestTimer;
  };

  uint64_t appId = loadNanoapp(MakeUnique<ScanTestNanoapp>());

  constexpr uint32_t timeOutCookie = 0xdead;
  chrePalWifiEnableResponse(PalWifiAsyncRequestTypes::SCAN,
                            false /* enableResponse */);
  sendEventToNanoapp(appId, SCAN_REQUEST, timeOutCookie);
  bool success;
  waitForEvent(SCAN_REQUEST, &success);
  EXPECT_TRUE(success);

  waitForEvent(REQUEST_TIMED_OUT);

  // Make sure that we can still request scan after a timedout
  // request.
  constexpr uint32_t successCookie = 0x0101;
  chrePalWifiEnableResponse(PalWifiAsyncRequestTypes::SCAN,
                            true /* enableResponse */);
  sendEventToNanoapp(appId, SCAN_REQUEST, successCookie);
  waitForEvent(SCAN_REQUEST, &success);
  EXPECT_TRUE(success);
  waitForEvent(CHRE_EVENT_WIFI_SCAN_RESULT);

  unloadNanoapp(appId);
}

TEST_F(WifiTimeoutTestBase, WifiCanDispatchQueuedRequestAfterOneTimeout) {
  constexpr uint8_t kNanoappNum = 2;
  // receivedTimeout is shared across apps and must be static.
  // But we want it initialized each time the test is executed.
  static uint8_t receivedTimeout;
  receivedTimeout = 0;

  class ScanTestNanoapp : public TestNanoapp {
   public:
    explicit ScanTestNanoapp(uint64_t id = kDefaultTestNanoappId)
        : TestNanoapp(TestNanoappInfo{
              .id = id, .perms = NanoappPermissions::CHRE_PERMS_WIFI}) {}

    bool start() override {
      for (uint8_t i = 0; i < kNanoappNum; ++i) {
        mRequestTimers[i] = CHRE_TIMER_INVALID;
      }
      return true;
    }

    void handleEvent(uint32_t, uint16_t eventType,
                     const void *eventData) override {
      size_t index = id() - CHRE_VENDOR_ID_EXAMPLE - 1;
      switch (eventType) {
        case CHRE_EVENT_WIFI_ASYNC_RESULT: {
          auto *event = static_cast<const chreAsyncResult *>(eventData);
          if (mRequestTimers[index] != CHRE_TIMER_INVALID) {
            chreTimerCancel(mRequestTimers[index]);
            mRequestTimers[index] = CHRE_TIMER_INVALID;
          }
          if (event->success) {
            TestEventQueueSingleton::get()->pushEvent(
                CHRE_EVENT_WIFI_ASYNC_RESULT,
                *(static_cast<const uint32_t *>(event->cookie)));
          }
          break;
        }

        case CHRE_EVENT_WIFI_SCAN_RESULT: {
          TestEventQueueSingleton::get()->pushEvent(
              CHRE_EVENT_WIFI_SCAN_RESULT);
          break;
        }

        case CHRE_EVENT_TIMER: {
          if (eventData == &mCookie[index]) {
            receivedTimeout++;
            mRequestTimers[index] = CHRE_TIMER_INVALID;
          }
          if (receivedTimeout == 2) {
            TestEventQueueSingleton::get()->pushEvent(REQUEST_TIMED_OUT);
          }
          break;
        }

        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case SCAN_REQUEST:
              bool success = false;
              mCookie[index] = *static_cast<uint32_t *>(event->data);
              if (chreWifiRequestScanAsyncDefault(&mCookie[index])) {
                mRequestTimers[index] =
                    chreTimerSet(CHRE_TEST_WIFI_SCAN_RESULT_TIMEOUT_NS,
                                 &mCookie[index], true /* oneShot */);
                success = mRequestTimers[index] != CHRE_TIMER_INVALID;
              }
              TestEventQueueSingleton::get()->pushEvent(SCAN_REQUEST, success);
              break;
          }
          break;
        }
      }
    }

   protected:
    uint32_t mCookie[kNanoappNum];
    uint32_t mRequestTimers[kNanoappNum];
  };
  constexpr uint64_t kAppOneId = makeExampleNanoappId(1);
  constexpr uint64_t kAppTwoId = makeExampleNanoappId(2);

  uint64_t firstAppId = loadNanoapp(MakeUnique<ScanTestNanoapp>(kAppOneId));
  uint64_t secondAppId = loadNanoapp(MakeUnique<ScanTestNanoapp>(kAppTwoId));

  constexpr uint32_t timeOutCookie = 0xdead;
  chrePalWifiEnableResponse(PalWifiAsyncRequestTypes::SCAN,
                            false /* enableResponse */);
  bool success;
  sendEventToNanoapp(firstAppId, SCAN_REQUEST, timeOutCookie);
  waitForEvent(SCAN_REQUEST, &success);
  EXPECT_TRUE(success);
  sendEventToNanoapp(secondAppId, SCAN_REQUEST, timeOutCookie);
  waitForEvent(SCAN_REQUEST, &success);
  EXPECT_TRUE(success);

  waitForEvent(REQUEST_TIMED_OUT);

  // Make sure that we can still request scan for both nanoapps after a timedout
  // request.
  constexpr uint32_t successCookie = 0x0101;
  chrePalWifiEnableResponse(PalWifiAsyncRequestTypes::SCAN,
                            true /* enableResponse */);
  sendEventToNanoapp(firstAppId, SCAN_REQUEST, successCookie);
  waitForEvent(SCAN_REQUEST, &success);
  EXPECT_TRUE(success);
  waitForEvent(CHRE_EVENT_WIFI_SCAN_RESULT);
  sendEventToNanoapp(secondAppId, SCAN_REQUEST, successCookie);
  waitForEvent(SCAN_REQUEST, &success);
  EXPECT_TRUE(success);
  waitForEvent(CHRE_EVENT_WIFI_SCAN_RESULT);

  unloadNanoapp(firstAppId);
  unloadNanoapp(secondAppId);
}

TEST_F(WifiTimeoutTestBase, WifiScanMonitorTimeoutTest) {
  CREATE_CHRE_TEST_EVENT(SCAN_MONITOR_REQUEST, 1);

  struct MonitoringRequest {
    bool enable;
    uint32_t cookie;
  };

  class App : public TestNanoapp {
   public:
    App()
        : TestNanoapp(
              TestNanoappInfo{.perms = NanoappPermissions::CHRE_PERMS_WIFI}) {}

    bool start() override {
      mRequestTimer = CHRE_TIMER_INVALID;
      return true;
    }

    void handleEvent(uint32_t, uint16_t eventType,
                     const void *eventData) override {
      switch (eventType) {
        case CHRE_EVENT_WIFI_ASYNC_RESULT: {
          auto *event = static_cast<const chreAsyncResult *>(eventData);
          if (event->success) {
            if (mRequestTimer != CHRE_TIMER_INVALID) {
              chreTimerCancel(mRequestTimer);
              mRequestTimer = CHRE_TIMER_INVALID;
            }
            TestEventQueueSingleton::get()->pushEvent(
                CHRE_EVENT_WIFI_ASYNC_RESULT,
                *(static_cast<const uint32_t *>(event->cookie)));
          }
          break;
        }

        case CHRE_EVENT_TIMER: {
          mRequestTimer = CHRE_TIMER_INVALID;
          TestEventQueueSingleton::get()->pushEvent(REQUEST_TIMED_OUT);
          break;
        }

        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case SCAN_MONITOR_REQUEST:
              bool success = false;
              auto request =
                  static_cast<const MonitoringRequest *>(event->data);
              if (chreWifiConfigureScanMonitorAsync(request->enable,
                                                    &mCookie)) {
                mCookie = request->cookie;
                mRequestTimer = chreTimerSet(CHRE_TEST_ASYNC_RESULT_TIMEOUT_NS,
                                             nullptr, true /* oneShot */);
                success = mRequestTimer != CHRE_TIMER_INVALID;
              }

              TestEventQueueSingleton::get()->pushEvent(SCAN_MONITOR_REQUEST,
                                                        success);
          }
        }
      }
    }

   protected:
    uint32_t mCookie;
    uint32_t mRequestTimer;
  };

  uint64_t appId = loadNanoapp(MakeUnique<App>());

  MonitoringRequest timeoutRequest{.enable = true, .cookie = 0xdead};
  chrePalWifiEnableResponse(PalWifiAsyncRequestTypes::SCAN_MONITORING, false);
  sendEventToNanoapp(appId, SCAN_MONITOR_REQUEST, timeoutRequest);
  bool success;
  waitForEvent(SCAN_MONITOR_REQUEST, &success);
  EXPECT_TRUE(success);

  waitForEvent(REQUEST_TIMED_OUT);

  // Make sure that we can still request to change scan monitor after a timedout
  // request.
  MonitoringRequest enableRequest{.enable = true, .cookie = 0x1010};
  chrePalWifiEnableResponse(PalWifiAsyncRequestTypes::SCAN_MONITORING, true);
  sendEventToNanoapp(appId, SCAN_MONITOR_REQUEST, enableRequest);
  waitForEvent(SCAN_MONITOR_REQUEST, &success);
  EXPECT_TRUE(success);

  uint32_t cookie;
  waitForEvent(CHRE_EVENT_WIFI_ASYNC_RESULT, &cookie);
  EXPECT_EQ(cookie, enableRequest.cookie);
  EXPECT_TRUE(chrePalWifiIsScanMonitoringActive());

  MonitoringRequest disableRequest{.enable = false, .cookie = 0x0101};
  sendEventToNanoapp(appId, SCAN_MONITOR_REQUEST, disableRequest);
  waitForEvent(SCAN_MONITOR_REQUEST, &success);
  EXPECT_TRUE(success);

  waitForEvent(CHRE_EVENT_WIFI_ASYNC_RESULT, &cookie);
  EXPECT_EQ(cookie, disableRequest.cookie);
  EXPECT_FALSE(chrePalWifiIsScanMonitoringActive());

  unloadNanoapp(appId);
}

TEST_F(WifiTimeoutTestBase, WifiRequestRangingTimeoutTest) {
  CREATE_CHRE_TEST_EVENT(RANGING_REQUEST, 0);

  class App : public TestNanoapp {
   public:
    App()
        : TestNanoapp(
              TestNanoappInfo{.perms = NanoappPermissions::CHRE_PERMS_WIFI}) {}

    bool start() override {
      mRequestTimer = CHRE_TIMER_INVALID;
      return true;
    }

    void handleEvent(uint32_t, uint16_t eventType,
                     const void *eventData) override {
      switch (eventType) {
        case CHRE_EVENT_WIFI_ASYNC_RESULT: {
          if (mRequestTimer != CHRE_TIMER_INVALID) {
            chreTimerCancel(mRequestTimer);
            mRequestTimer = CHRE_TIMER_INVALID;
          }

          auto *event = static_cast<const chreAsyncResult *>(eventData);
          if (event->success) {
            if (event->errorCode == 0) {
              TestEventQueueSingleton::get()->pushEvent(
                  CHRE_EVENT_WIFI_ASYNC_RESULT,
                  *(static_cast<const uint32_t *>(event->cookie)));
            }
          }
          break;
        }

        case CHRE_EVENT_TIMER: {
          mRequestTimer = CHRE_TIMER_INVALID;
          TestEventQueueSingleton::get()->pushEvent(REQUEST_TIMED_OUT);
          break;
        }

        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case RANGING_REQUEST:
              bool success = false;
              mCookie = *static_cast<uint32_t *>(event->data);

              // Placeholder parameters since linux PAL does not use this to
              // generate response
              struct chreWifiRangingTarget dummyRangingTarget = {
                  .macAddress = {0x12, 0x34, 0x56, 0x78, 0x9a, 0xbc},
                  .primaryChannel = 0xdef02468,
                  .centerFreqPrimary = 0xace13579,
                  .centerFreqSecondary = 0xbdf369cf,
                  .channelWidth = 0x48,
              };

              struct chreWifiRangingParams dummyRangingParams = {
                  .targetListLen = 1,
                  .targetList = &dummyRangingTarget,
              };

              if (chreWifiRequestRangingAsync(&dummyRangingParams, &mCookie)) {
                mRequestTimer =
                    chreTimerSet(CHRE_TEST_WIFI_RANGING_RESULT_TIMEOUT_NS,
                                 nullptr, true /* oneShot */);
                success = mRequestTimer != CHRE_TIMER_INVALID;
              }
              TestEventQueueSingleton::get()->pushEvent(RANGING_REQUEST,
                                                        success);
          }
        }
      }
    }

   protected:
    uint32_t mCookie;
    uint32_t mRequestTimer;
  };

  uint64_t appId = loadNanoapp(MakeUnique<App>());

  uint32_t timeOutCookie = 0xdead;

  chrePalWifiEnableResponse(PalWifiAsyncRequestTypes::RANGING, false);
  sendEventToNanoapp(appId, RANGING_REQUEST, timeOutCookie);
  bool success;
  waitForEvent(RANGING_REQUEST, &success);
  EXPECT_TRUE(success);

  waitForEvent(REQUEST_TIMED_OUT);

  // Make sure that we can still request ranging after a timedout request
  uint32_t successCookie = 0x0101;
  chrePalWifiEnableResponse(PalWifiAsyncRequestTypes::RANGING, true);
  sendEventToNanoapp(appId, RANGING_REQUEST, successCookie);
  waitForEvent(RANGING_REQUEST, &success);
  EXPECT_TRUE(success);

  uint32_t cookie;
  waitForEvent(CHRE_EVENT_WIFI_ASYNC_RESULT, &cookie);
  EXPECT_EQ(cookie, successCookie);

  unloadNanoapp(appId);
}

}  // namespace
}  // namespace chre
