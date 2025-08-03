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

#include "chre_api/chre/gnss.h"

#include <cstdint>
#include <functional>

#include "chre/core/event_loop_manager.h"
#include "chre/core/settings.h"
#include "chre/platform/linux/pal_gnss.h"
#include "chre/platform/log.h"
#include "chre/util/system/napp_permissions.h"
#include "chre_api/chre/event.h"
#include "gtest/gtest.h"
#include "inc/test_util.h"
#include "test_base.h"
#include "test_event.h"
#include "test_event_queue.h"
#include "test_util.h"

namespace chre {
namespace {

/**
 * Wait for the predicate to become true with a timeout.
 *
 * @return the last value of the predicate.
 */
bool waitForCondition(const std::function<bool()> &predicate,
                      std::chrono::milliseconds timeout) {
  constexpr std::chrono::milliseconds kSleepDuration(100);
  bool result;
  std::chrono::milliseconds time;
  while (!(result = predicate()) && time < timeout) {
    std::this_thread::sleep_for(kSleepDuration);
    time += kSleepDuration;
  }
  return result;
}

// ref b/228669574
TEST_F(TestBase, GnssSubscriptionWithSettingChange) {
  CREATE_CHRE_TEST_EVENT(LOCATION_REQUEST, 0);

  struct LocationRequest {
    bool enable;
    uint32_t cookie;
  };

  class App : public TestNanoapp {
   public:
    App()
        : TestNanoapp(
              TestNanoappInfo{.perms = NanoappPermissions::CHRE_PERMS_GNSS}) {}

    bool start() override {
      chreUserSettingConfigureEvents(CHRE_USER_SETTING_LOCATION,
                                     true /*enabled*/);
      return true;
    }

    void handleEvent(uint32_t, uint16_t eventType,
                     const void *eventData) override {
      switch (eventType) {
        case CHRE_EVENT_GNSS_ASYNC_RESULT: {
          auto *event = static_cast<const chreAsyncResult *>(eventData);
          if (event->success) {
            TestEventQueueSingleton::get()->pushEvent(
                CHRE_EVENT_GNSS_ASYNC_RESULT,
                *(static_cast<const uint32_t *>(event->cookie)));
          }
          break;
        }

        case CHRE_EVENT_SETTING_CHANGED_LOCATION: {
          TestEventQueueSingleton::get()->pushEvent(
              CHRE_EVENT_SETTING_CHANGED_LOCATION);
          break;
        }

        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case LOCATION_REQUEST: {
              auto request = static_cast<const LocationRequest *>(event->data);
              bool success;
              mCookie = request->cookie;
              if (request->enable) {
                success = chreGnssLocationSessionStartAsync(
                    1000 /*minIntervalMs*/, 1000 /*minTimeToNextFixMs*/,
                    &mCookie);
              } else {
                success = chreGnssLocationSessionStopAsync(&mCookie);
              }
              TestEventQueueSingleton::get()->pushEvent(LOCATION_REQUEST,
                                                        success);
              break;
            }
          }
        }
      }
    }

    void end() override {
      chreUserSettingConfigureEvents(CHRE_USER_SETTING_LOCATION,
                                     false /*enabled*/);
    }

   protected:
    uint32_t mCookie;
  };

  uint64_t appId = loadNanoapp(MakeUnique<App>());

  bool success;
  EXPECT_FALSE(chrePalGnssIsLocationEnabled());
  chrePalGnssDelaySendingLocationEvents(true);

  LocationRequest request{.enable = true, .cookie = 0x123};
  sendEventToNanoapp(appId, LOCATION_REQUEST, request);
  waitForEvent(LOCATION_REQUEST, &success);
  EXPECT_TRUE(success);
  chrePalGnssStartSendingLocationEvents();
  uint32_t cookie;
  waitForEvent(CHRE_EVENT_GNSS_ASYNC_RESULT, &cookie);
  EXPECT_EQ(cookie, request.cookie);
  EXPECT_TRUE(chrePalGnssIsLocationEnabled());

  EventLoopManagerSingleton::get()->getSettingManager().postSettingChange(
      Setting::LOCATION, false /* enabled */);

  waitForEvent(CHRE_EVENT_SETTING_CHANGED_LOCATION);

  // Wait for the setting change to propagate to GNSS.
  EXPECT_TRUE(waitForCondition([]() { return !chrePalGnssIsLocationEnabled(); },
                               std::chrono::milliseconds(1000)));

  EventLoopManagerSingleton::get()->getSettingManager().postSettingChange(
      Setting::LOCATION, true /* enabled */);

  waitForEvent(CHRE_EVENT_SETTING_CHANGED_LOCATION);

  // Wait for the setting change to propagate to GNSS.
  EXPECT_TRUE(waitForCondition([]() { return chrePalGnssIsLocationEnabled(); },
                               std::chrono::milliseconds(1000)));

  request.enable = false;
  sendEventToNanoapp(appId, LOCATION_REQUEST, request);
  waitForEvent(LOCATION_REQUEST, &success);
  EXPECT_TRUE(success);
  chrePalGnssStartSendingLocationEvents();
  waitForEvent(CHRE_EVENT_GNSS_ASYNC_RESULT, &cookie);
  EXPECT_EQ(cookie, request.cookie);
  EXPECT_FALSE(chrePalGnssIsLocationEnabled());
  chrePalGnssDelaySendingLocationEvents(false);
}

TEST_F(TestBase, GnssCanSubscribeAndUnsubscribeToLocation) {
  CREATE_CHRE_TEST_EVENT(LOCATION_REQUEST, 0);

  struct LocationRequest {
    bool enable;
    uint32_t cookie;
  };

  class App : public TestNanoapp {
   public:
    App()
        : TestNanoapp(
              TestNanoappInfo{.perms = NanoappPermissions::CHRE_PERMS_GNSS}) {}

    void handleEvent(uint32_t, uint16_t eventType,
                     const void *eventData) override {
      switch (eventType) {
        case CHRE_EVENT_GNSS_ASYNC_RESULT: {
          auto *event = static_cast<const chreAsyncResult *>(eventData);
          if (event->success) {
            TestEventQueueSingleton::get()->pushEvent(
                CHRE_EVENT_GNSS_ASYNC_RESULT,
                *(static_cast<const uint32_t *>(event->cookie)));
          }
          break;
        }

        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case LOCATION_REQUEST: {
              auto request = static_cast<const LocationRequest *>(event->data);
              bool success;
              mCookie = request->cookie;
              if (request->enable) {
                success = chreGnssLocationSessionStartAsync(
                    1000 /*minIntervalMs*/, 1000 /*minTimeToNextFixMs*/,
                    &mCookie);
              } else {
                success = chreGnssLocationSessionStopAsync(&mCookie);
              }
              TestEventQueueSingleton::get()->pushEvent(LOCATION_REQUEST,
                                                        success);
              break;
            }
          }
        }
      }
    }

   protected:
    uint32_t mCookie;
  };

  uint64_t appId = loadNanoapp(MakeUnique<App>());

  bool success;
  EXPECT_FALSE(chrePalGnssIsLocationEnabled());

  LocationRequest request{.enable = true, .cookie = 0x123};
  sendEventToNanoapp(appId, LOCATION_REQUEST, request);
  waitForEvent(LOCATION_REQUEST, &success);
  EXPECT_TRUE(success);
  uint32_t cookie;
  waitForEvent(CHRE_EVENT_GNSS_ASYNC_RESULT, &cookie);
  EXPECT_EQ(cookie, request.cookie);
  EXPECT_TRUE(chrePalGnssIsLocationEnabled());

  request.enable = false;
  sendEventToNanoapp(appId, LOCATION_REQUEST, request);
  waitForEvent(LOCATION_REQUEST, &success);
  EXPECT_TRUE(success);
  waitForEvent(CHRE_EVENT_GNSS_ASYNC_RESULT, &cookie);
  EXPECT_EQ(cookie, request.cookie);
  EXPECT_FALSE(chrePalGnssIsLocationEnabled());
}

TEST_F(TestBase, GnssUnsubscribeToLocationOnUnload) {
  CREATE_CHRE_TEST_EVENT(LOCATION_REQUEST, 0);

  struct LocationRequest {
    bool enable;
    uint32_t cookie;
  };

  class App : public TestNanoapp {
   public:
    App()
        : TestNanoapp(
              TestNanoappInfo{.perms = NanoappPermissions::CHRE_PERMS_GNSS}) {}

    void handleEvent(uint32_t, uint16_t eventType,
                     const void *eventData) override {
      switch (eventType) {
        case CHRE_EVENT_GNSS_ASYNC_RESULT: {
          auto *event = static_cast<const chreAsyncResult *>(eventData);
          if (event->success) {
            TestEventQueueSingleton::get()->pushEvent(
                CHRE_EVENT_GNSS_ASYNC_RESULT,
                *(static_cast<const uint32_t *>(event->cookie)));
          }
          break;
        }

        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case LOCATION_REQUEST: {
              auto request = static_cast<const LocationRequest *>(event->data);
              if (request->enable) {
                mCookie = request->cookie;
                const bool success = chreGnssLocationSessionStartAsync(
                    1000 /*minIntervalMs*/, 1000 /*minTimeToNextFixMs*/,
                    &mCookie);
                TestEventQueueSingleton::get()->pushEvent(LOCATION_REQUEST,
                                                          success);
              }
              break;
            }
          }
        }
      }
    }

   protected:
    uint32_t mCookie;
  };

  uint64_t appId = loadNanoapp(MakeUnique<App>());

  EXPECT_FALSE(chrePalGnssIsLocationEnabled());

  LocationRequest request{.enable = true, .cookie = 0x123};
  sendEventToNanoapp(appId, LOCATION_REQUEST, request);
  bool success;
  waitForEvent(LOCATION_REQUEST, &success);
  EXPECT_TRUE(success);
  uint32_t cookie;
  waitForEvent(CHRE_EVENT_GNSS_ASYNC_RESULT, &cookie);
  EXPECT_EQ(cookie, request.cookie);
  EXPECT_TRUE(chrePalGnssIsLocationEnabled());

  unloadNanoapp(appId);
  EXPECT_FALSE(chrePalGnssIsLocationEnabled());
}

TEST_F(TestBase, GnssCanSubscribeAndUnsubscribeToMeasurement) {
  CREATE_CHRE_TEST_EVENT(MEASUREMENT_REQUEST, 0);

  struct MeasurementRequest {
    bool enable;
    uint32_t cookie;
  };

  class App : public TestNanoapp {
   public:
    App()
        : TestNanoapp(
              TestNanoappInfo{.perms = NanoappPermissions::CHRE_PERMS_GNSS}) {}

    void handleEvent(uint32_t, uint16_t eventType,
                     const void *eventData) override {
      static uint32_t cookie;
      switch (eventType) {
        case CHRE_EVENT_GNSS_ASYNC_RESULT: {
          auto *event = static_cast<const chreAsyncResult *>(eventData);
          if (event->success) {
            TestEventQueueSingleton::get()->pushEvent(
                CHRE_EVENT_GNSS_ASYNC_RESULT,
                *(static_cast<const uint32_t *>(event->cookie)));
          }
          break;
        }

        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case MEASUREMENT_REQUEST: {
              auto request =
                  static_cast<const MeasurementRequest *>(event->data);
              bool success;
              mCookie = request->cookie;
              if (request->enable) {
                success = chreGnssMeasurementSessionStartAsync(
                    1000 /*minIntervalMs*/, &mCookie);
              } else {
                cookie = request->cookie;
                success = chreGnssMeasurementSessionStopAsync(&mCookie);
              }
              TestEventQueueSingleton::get()->pushEvent(MEASUREMENT_REQUEST,
                                                        success);
              break;
            }
          }
        }
      }
    }

   protected:
    uint32_t mCookie;
  };

  uint64_t appId = loadNanoapp(MakeUnique<App>());

  bool success;
  EXPECT_FALSE(chrePalGnssIsLocationEnabled());

  MeasurementRequest request{.enable = true, .cookie = 0x123};
  sendEventToNanoapp(appId, MEASUREMENT_REQUEST, request);
  waitForEvent(MEASUREMENT_REQUEST, &success);
  EXPECT_TRUE(success);
  uint32_t cookie;
  waitForEvent(CHRE_EVENT_GNSS_ASYNC_RESULT, &cookie);
  EXPECT_EQ(cookie, request.cookie);
  EXPECT_TRUE(chrePalGnssIsMeasurementEnabled());

  request.enable = false;
  sendEventToNanoapp(appId, MEASUREMENT_REQUEST, request);
  waitForEvent(MEASUREMENT_REQUEST, &success);
  EXPECT_TRUE(success);
  waitForEvent(CHRE_EVENT_GNSS_ASYNC_RESULT, &cookie);
  EXPECT_EQ(cookie, request.cookie);
  EXPECT_FALSE(chrePalGnssIsMeasurementEnabled());
}

TEST_F(TestBase, GnssUnsubscribeToMeasurementOnUnload) {
  CREATE_CHRE_TEST_EVENT(MEASUREMENT_REQUEST, 0);

  struct MeasurementRequest {
    bool enable;
    uint32_t cookie;
  };

  class App : public TestNanoapp {
   public:
    App()
        : TestNanoapp(
              TestNanoappInfo{.perms = NanoappPermissions::CHRE_PERMS_GNSS}) {}

    void handleEvent(uint32_t, uint16_t eventType,
                     const void *eventData) override {
      switch (eventType) {
        case CHRE_EVENT_GNSS_ASYNC_RESULT: {
          auto *event = static_cast<const chreAsyncResult *>(eventData);
          if (event->success) {
            TestEventQueueSingleton::get()->pushEvent(
                CHRE_EVENT_GNSS_ASYNC_RESULT,
                *(static_cast<const uint32_t *>(event->cookie)));
          }
          break;
        }

        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case MEASUREMENT_REQUEST: {
              auto request =
                  static_cast<const MeasurementRequest *>(event->data);
              if (request->enable) {
                mCookie = request->cookie;
                const bool success = chreGnssMeasurementSessionStartAsync(
                    1000 /*minIntervalMs*/, &mCookie);
                TestEventQueueSingleton::get()->pushEvent(MEASUREMENT_REQUEST,
                                                          success);
              }
              break;
            }
          }
        }
      }
    }

   protected:
    uint32_t mCookie;
  };

  uint64_t appId = loadNanoapp(MakeUnique<App>());

  EXPECT_FALSE(chrePalGnssIsLocationEnabled());

  MeasurementRequest request{.enable = true, .cookie = 0x123};
  sendEventToNanoapp(appId, MEASUREMENT_REQUEST, request);
  bool success;
  waitForEvent(MEASUREMENT_REQUEST, &success);
  EXPECT_TRUE(success);
  uint32_t cookie;
  waitForEvent(CHRE_EVENT_GNSS_ASYNC_RESULT, &cookie);
  EXPECT_EQ(cookie, request.cookie);
  EXPECT_TRUE(chrePalGnssIsMeasurementEnabled());

  unloadNanoapp(appId);
  EXPECT_FALSE(chrePalGnssIsMeasurementEnabled());
}

TEST_F(TestBase, GnssCanSubscribeAndUnsubscribeToPassiveListener) {
  CREATE_CHRE_TEST_EVENT(LISTENER_REQUEST, 0);

  class App : public TestNanoapp {
   public:
    App()
        : TestNanoapp(
              TestNanoappInfo{.perms = NanoappPermissions::CHRE_PERMS_GNSS}) {}

    void handleEvent(uint32_t, uint16_t eventType,
                     const void *eventData) override {
      switch (eventType) {
        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case LISTENER_REQUEST: {
              auto enable = *(static_cast<const bool *>(event->data));
              const bool success =
                  chreGnssConfigurePassiveLocationListener(enable);
              TestEventQueueSingleton::get()->pushEvent(LISTENER_REQUEST,
                                                        success);
              break;
            }
          }
        }
      }
    }
  };

  uint64_t appId = loadNanoapp(MakeUnique<App>());

  bool success;
  EXPECT_FALSE(chrePalGnssIsPassiveLocationListenerEnabled());

  sendEventToNanoapp(appId, LISTENER_REQUEST, true);
  waitForEvent(LISTENER_REQUEST, &success);
  EXPECT_TRUE(success);
  EXPECT_TRUE(chrePalGnssIsPassiveLocationListenerEnabled());

  sendEventToNanoapp(appId, LISTENER_REQUEST, false);
  waitForEvent(LISTENER_REQUEST, &success);
  EXPECT_TRUE(success);
  EXPECT_FALSE(chrePalGnssIsPassiveLocationListenerEnabled());
}

TEST_F(TestBase, GnssUnsubscribeToPassiveListenerOnUnload) {
  CREATE_CHRE_TEST_EVENT(LISTENER_REQUEST, 0);

  class App : public TestNanoapp {
   public:
    App()
        : TestNanoapp(
              TestNanoappInfo{.perms = NanoappPermissions::CHRE_PERMS_GNSS}) {}

    void handleEvent(uint32_t, uint16_t eventType,
                     const void *eventData) override {
      switch (eventType) {
        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case LISTENER_REQUEST: {
              auto enable = *(static_cast<const bool *>(event->data));
              const bool success =
                  chreGnssConfigurePassiveLocationListener(enable);
              TestEventQueueSingleton::get()->pushEvent(LISTENER_REQUEST,
                                                        success);
            }
          }
        }
      }
    }
  };

  uint64_t appId = loadNanoapp(MakeUnique<App>());

  EXPECT_FALSE(chrePalGnssIsPassiveLocationListenerEnabled());

  sendEventToNanoapp(appId, LISTENER_REQUEST, true);
  bool success;
  waitForEvent(LISTENER_REQUEST, &success);
  EXPECT_TRUE(success);
  EXPECT_TRUE(chrePalGnssIsPassiveLocationListenerEnabled());

  unloadNanoapp(appId);
  EXPECT_FALSE(chrePalGnssIsPassiveLocationListenerEnabled());
}

}  // namespace
}  // namespace chre