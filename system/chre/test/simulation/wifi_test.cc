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

#include "chre_api/chre/wifi.h"
#include <cstdint>
#include "chre/core/event_loop_manager.h"
#include "chre/core/settings.h"
#include "chre/platform/linux/pal_nan.h"
#include "chre/platform/linux/pal_wifi.h"
#include "chre/platform/log.h"
#include "chre/util/system/napp_permissions.h"
#include "chre_api/chre/event.h"

#include "gtest/gtest.h"
#include "test_base.h"
#include "test_event.h"
#include "test_event_queue.h"
#include "test_util.h"

namespace chre {
namespace {

TEST_F(TestBase, WifiCanSubscribeAndUnsubscribeToScanMonitoring) {
  CREATE_CHRE_TEST_EVENT(MONITORING_REQUEST, 0);

  struct MonitoringRequest {
    bool enable;
    uint32_t cookie;
  };

  class App : public TestNanoapp {
   public:
    App()
        : TestNanoapp(
              TestNanoappInfo{.perms = NanoappPermissions::CHRE_PERMS_WIFI}) {}

    void handleEvent(uint32_t, uint16_t eventType,
                     const void *eventData) override {
      switch (eventType) {
        case CHRE_EVENT_WIFI_ASYNC_RESULT: {
          auto *event = static_cast<const chreAsyncResult *>(eventData);
          if (event->success) {
            TestEventQueueSingleton::get()->pushEvent(
                CHRE_EVENT_WIFI_ASYNC_RESULT,
                *(static_cast<const uint32_t *>(event->cookie)));
          }
          break;
        }

        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case MONITORING_REQUEST:
              auto request =
                  static_cast<const MonitoringRequest *>(event->data);
              mCookie = request->cookie;
              bool success =
                  chreWifiConfigureScanMonitorAsync(request->enable, &mCookie);
              TestEventQueueSingleton::get()->pushEvent(MONITORING_REQUEST,
                                                        success);
          }
        }
      }
    }

   protected:
    uint32_t mCookie;
  };

  uint64_t appId = loadNanoapp(MakeUnique<App>());

  EXPECT_FALSE(chrePalWifiIsScanMonitoringActive());

  MonitoringRequest request{.enable = true, .cookie = 0x123};
  sendEventToNanoapp(appId, MONITORING_REQUEST, request);
  uint32_t cookie;
  waitForEvent(CHRE_EVENT_WIFI_ASYNC_RESULT, &cookie);
  EXPECT_EQ(cookie, request.cookie);
  EXPECT_TRUE(chrePalWifiIsScanMonitoringActive());

  request = {.enable = false, .cookie = 0x456};
  sendEventToNanoapp(appId, MONITORING_REQUEST, request);
  bool success;
  waitForEvent(MONITORING_REQUEST, &success);
  EXPECT_TRUE(success);
  waitForEvent(CHRE_EVENT_WIFI_ASYNC_RESULT, &cookie);
  EXPECT_EQ(cookie, request.cookie);
  EXPECT_FALSE(chrePalWifiIsScanMonitoringActive());
}

TEST_F(TestBase, WifiScanMonitoringDisabledOnUnload) {
  CREATE_CHRE_TEST_EVENT(MONITORING_REQUEST, 1);

  struct MonitoringRequest {
    bool enable;
    uint32_t cookie;
  };

  class App : public TestNanoapp {
   public:
    App()
        : TestNanoapp(
              TestNanoappInfo{.perms = NanoappPermissions::CHRE_PERMS_WIFI}) {}

    void handleEvent(uint32_t, uint16_t eventType,
                     const void *eventData) override {
      switch (eventType) {
        case CHRE_EVENT_WIFI_ASYNC_RESULT: {
          auto *event = static_cast<const chreAsyncResult *>(eventData);
          if (event->success) {
            TestEventQueueSingleton::get()->pushEvent(
                CHRE_EVENT_WIFI_ASYNC_RESULT,
                *(static_cast<const uint32_t *>(event->cookie)));
          }
          break;
        }

        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case MONITORING_REQUEST:
              auto request =
                  static_cast<const MonitoringRequest *>(event->data);
              mCookie = request->cookie;
              bool success =
                  chreWifiConfigureScanMonitorAsync(request->enable, &mCookie);
              TestEventQueueSingleton::get()->pushEvent(MONITORING_REQUEST,
                                                        success);
          }
        }
      }
    }

   protected:
    uint32_t mCookie;
  };

  uint64_t appId = loadNanoapp(MakeUnique<App>());

  EXPECT_FALSE(chrePalWifiIsScanMonitoringActive());

  MonitoringRequest request{.enable = true, .cookie = 0x123};
  sendEventToNanoapp(appId, MONITORING_REQUEST, request);
  bool success;
  waitForEvent(MONITORING_REQUEST, &success);
  EXPECT_TRUE(success);
  uint32_t cookie;
  waitForEvent(CHRE_EVENT_WIFI_ASYNC_RESULT, &cookie);
  EXPECT_EQ(cookie, request.cookie);
  EXPECT_TRUE(chrePalWifiIsScanMonitoringActive());

  unloadNanoapp(appId);
  EXPECT_FALSE(chrePalWifiIsScanMonitoringActive());
}

TEST_F(TestBase, WifiScanMonitoringDisabledOnUnloadAndCanBeReEnabled) {
  CREATE_CHRE_TEST_EVENT(MONITORING_REQUEST, 1);

  struct MonitoringRequest {
    bool enable;
    uint32_t cookie;
  };

  class App : public TestNanoapp {
   public:
    App()
        : TestNanoapp(
              TestNanoappInfo{.perms = NanoappPermissions::CHRE_PERMS_WIFI}) {}

    void handleEvent(uint32_t, uint16_t eventType,
                     const void *eventData) override {
      switch (eventType) {
        case CHRE_EVENT_WIFI_ASYNC_RESULT: {
          auto *event = static_cast<const chreAsyncResult *>(eventData);
          if (event->success) {
            TestEventQueueSingleton::get()->pushEvent(
                CHRE_EVENT_WIFI_ASYNC_RESULT,
                *(static_cast<const uint32_t *>(event->cookie)));
          }
          break;
        }

        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case MONITORING_REQUEST:
              auto request =
                  static_cast<const MonitoringRequest *>(event->data);
              mCookie = request->cookie;
              bool success =
                  chreWifiConfigureScanMonitorAsync(request->enable, &mCookie);
              TestEventQueueSingleton::get()->pushEvent(MONITORING_REQUEST,
                                                        success);
          }
        }
      }
    }

   protected:
    uint32_t mCookie;
  };

  uint64_t appId = loadNanoapp(MakeUnique<App>());

  EXPECT_FALSE(chrePalWifiIsScanMonitoringActive());

  MonitoringRequest request{.enable = true, .cookie = 0x123};
  sendEventToNanoapp(appId, MONITORING_REQUEST, request);
  bool success;
  waitForEvent(MONITORING_REQUEST, &success);
  EXPECT_TRUE(success);
  uint32_t cookie;
  waitForEvent(CHRE_EVENT_WIFI_ASYNC_RESULT, &cookie);
  EXPECT_EQ(cookie, request.cookie);
  EXPECT_TRUE(chrePalWifiIsScanMonitoringActive());

  unloadNanoapp(appId);
  EXPECT_FALSE(chrePalWifiIsScanMonitoringActive());

  appId = loadNanoapp(MakeUnique<App>());
  EXPECT_FALSE(chrePalWifiIsScanMonitoringActive());

  request = {.enable = true, .cookie = 0x456};
  sendEventToNanoapp(appId, MONITORING_REQUEST, request);
  waitForEvent(MONITORING_REQUEST, &success);
  EXPECT_TRUE(success);
  waitForEvent(CHRE_EVENT_WIFI_ASYNC_RESULT, &cookie);
  EXPECT_EQ(cookie, request.cookie);
  EXPECT_TRUE(chrePalWifiIsScanMonitoringActive());
}

}  // namespace
}  // namespace chre