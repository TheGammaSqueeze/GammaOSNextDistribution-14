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
#include "chre/common.h"
#include "chre/core/event_loop_manager.h"
#include "chre/core/settings.h"
#include "chre/platform/linux/pal_nan.h"
#include "chre/platform/log.h"
#include "chre/util/system/napp_permissions.h"
#include "chre_api/chre/event.h"
#include "chre_api/chre/wifi.h"

#include "gtest/gtest.h"
#include "test_base.h"
#include "test_event_queue.h"
#include "test_util.h"

/**
 * Simulation to test WiFi NAN functionality in CHRE.
 *
 * The test works as follows:
 * - A test nanoapp starts by requesting NAN subscriptions, with random
 *   service specific information. It also requests NAN ranging measurements
 *   if the test desires it. The Linux WiFi PAL has hooks and flags that
 *   instruct it to cover various test cases (fail subscribe, terminate
 *   service, etc.), to enable testing of all NAN events that CHRE is
 *   expected to propagate. These flags should be set before startTestNanoapping
 * the test nanoapp.
 *
 * - The test fails (times out) if any of the events are not sent by CHRE.
 */

namespace chre {
namespace {

/**
 * Common settings for test nanoapps.
 *
 * - Grant WiFi permissions,
 * - Initialize the WiFi state in start.
 */
class NanTestNanoapp : public TestNanoapp {
 public:
  NanTestNanoapp()
      : TestNanoapp(
            TestNanoappInfo{.perms = NanoappPermissions::CHRE_PERMS_WIFI}) {}

  bool start() override {
    EventLoopManagerSingleton::get()->getSettingManager().postSettingChange(
        Setting::WIFI_AVAILABLE, true /* enabled */);
    PalNanEngineSingleton::get()->setFlags(PalNanEngine::Flags::NONE);
    return true;
  }
};

/**
 * Test that an async error is received if NAN operations are attempted when
 * the WiFi setting is disabled.
 */
TEST_F(TestBase, WifiNanDisabledViaSettings) {
  CREATE_CHRE_TEST_EVENT(NAN_SUBSCRIBE, 0);

  class App : public NanTestNanoapp {
   public:
    void handleEvent(uint32_t, uint16_t eventType,
                     const void *eventData) override {
      constexpr uint32_t kSubscribeCookie = 0x10aded;

      switch (eventType) {
        case CHRE_EVENT_WIFI_ASYNC_RESULT: {
          auto *event = static_cast<const chreAsyncResult *>(eventData);
          if (event->requestType == CHRE_WIFI_REQUEST_TYPE_NAN_SUBSCRIBE) {
            ASSERT_EQ(event->errorCode, CHRE_ERROR_FUNCTION_DISABLED);
            TestEventQueueSingleton::get()->pushEvent(
                CHRE_EVENT_WIFI_ASYNC_RESULT);
          }
          break;
        }

        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case NAN_SUBSCRIBE: {
              auto config = (chreWifiNanSubscribeConfig *)(event->data);
              chreWifiNanSubscribe(config, &kSubscribeCookie);
              break;
            }
          }
        }
      }
    }
  };

  uint64_t appId = loadNanoapp(MakeUnique<App>());

  EventLoopManagerSingleton::get()->getSettingManager().postSettingChange(
      Setting::WIFI_AVAILABLE, false /* enabled */);

  chreWifiNanSubscribeConfig config = {
      .subscribeType = CHRE_WIFI_NAN_SUBSCRIBE_TYPE_PASSIVE,
      .service = "SomeServiceName",
  };
  sendEventToNanoapp(appId, NAN_SUBSCRIBE, config);
  waitForEvent(CHRE_EVENT_WIFI_ASYNC_RESULT);
}

/**
 * Test that a subscription request succeeds, and an identifier event is
 * received with a matching cookie. Also test that a discovery event is later
 * received, marking the completion of the subscription process.
 */
TEST_F(TestBase, WifiNanSuccessfulSubscribe) {
  CREATE_CHRE_TEST_EVENT(NAN_SUBSCRIBE, 0);

  class App : public NanTestNanoapp {
   public:
    void handleEvent(uint32_t, uint16_t eventType,
                     const void *eventData) override {
      const uint32_t kSubscribeCookie = 0x10aded;

      switch (eventType) {
        case CHRE_EVENT_WIFI_NAN_IDENTIFIER_RESULT: {
          auto event =
              static_cast<const chreWifiNanIdentifierEvent *>(eventData);
          if (event->result.errorCode == CHRE_ERROR_NONE) {
            TestEventQueueSingleton::get()->pushEvent(
                CHRE_EVENT_WIFI_NAN_IDENTIFIER_RESULT, event->id);
          }
          break;
        }

        case CHRE_EVENT_WIFI_NAN_DISCOVERY_RESULT: {
          auto event =
              static_cast<const chreWifiNanDiscoveryEvent *>(eventData);
          TestEventQueueSingleton::get()->pushEvent(
              CHRE_EVENT_WIFI_NAN_DISCOVERY_RESULT, event->subscribeId);
          break;
        }

        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case NAN_SUBSCRIBE: {
              auto config = (chreWifiNanSubscribeConfig *)(event->data);
              const bool success =
                  chreWifiNanSubscribe(config, &kSubscribeCookie);
              TestEventQueueSingleton::get()->pushEvent(NAN_SUBSCRIBE, success);
              break;
            }
          }
        }
      }
    }
  };

  uint64_t appId = loadNanoapp(MakeUnique<App>());

  chreWifiNanSubscribeConfig config = {
      .subscribeType = CHRE_WIFI_NAN_SUBSCRIBE_TYPE_PASSIVE,
      .service = "SomeServiceName",
  };
  sendEventToNanoapp(appId, NAN_SUBSCRIBE, config);
  bool success;
  waitForEvent(NAN_SUBSCRIBE, &success);
  EXPECT_TRUE(success);

  uint32_t id;
  waitForEvent(CHRE_EVENT_WIFI_NAN_IDENTIFIER_RESULT, &id);
  EXPECT_TRUE(PalNanEngineSingleton::get()->isSubscriptionActive(id));

  PalNanEngineSingleton::get()->sendDiscoveryEvent(id);
  uint32_t subscribeId;
  waitForEvent(CHRE_EVENT_WIFI_NAN_DISCOVERY_RESULT, &subscribeId);

  EXPECT_EQ(id, subscribeId);
}

TEST_F(TestBase, WifiNanUnsSubscribeOnNanoappUnload) {
  CREATE_CHRE_TEST_EVENT(NAN_SUBSCRIBE, 0);

  class App : public NanTestNanoapp {
   public:
    void handleEvent(uint32_t, uint16_t eventType,
                     const void *eventData) override {
      const uint32_t kSubscribeCookie = 0x10aded;

      switch (eventType) {
        case CHRE_EVENT_WIFI_NAN_IDENTIFIER_RESULT: {
          auto event =
              static_cast<const chreWifiNanIdentifierEvent *>(eventData);
          if (event->result.errorCode == CHRE_ERROR_NONE) {
            TestEventQueueSingleton::get()->pushEvent(
                CHRE_EVENT_WIFI_NAN_IDENTIFIER_RESULT, event->id);
          }
          break;
        }

        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case NAN_SUBSCRIBE: {
              auto config = (chreWifiNanSubscribeConfig *)(event->data);
              const bool success =
                  chreWifiNanSubscribe(config, &kSubscribeCookie);
              TestEventQueueSingleton::get()->pushEvent(NAN_SUBSCRIBE, success);
              break;
            }
          }
        }
      }
    }
  };

  uint64_t appId = loadNanoapp(MakeUnique<App>());

  chreWifiNanSubscribeConfig config = {
      .subscribeType = CHRE_WIFI_NAN_SUBSCRIBE_TYPE_PASSIVE,
      .service = "SomeServiceName",
  };
  sendEventToNanoapp(appId, NAN_SUBSCRIBE, config);
  bool success;
  waitForEvent(NAN_SUBSCRIBE, &success);
  EXPECT_TRUE(success);

  uint32_t id;
  waitForEvent(CHRE_EVENT_WIFI_NAN_IDENTIFIER_RESULT, &id);
  EXPECT_TRUE(PalNanEngineSingleton::get()->isSubscriptionActive(id));

  unloadNanoapp(appId);
  EXPECT_FALSE(PalNanEngineSingleton::get()->isSubscriptionActive(id));
}

/**
 * Test that a subscription request fails, and an identifier event is received
 * with a matching cookie, indicating the reason for the error (Note that the
 * fake PAL engine always returns the generic CHRE_ERROR as the error code,
 * but this may vary in unsimulated scenarios).
 */
TEST_F(TestBase, WifiNanUnuccessfulSubscribeTest) {
  CREATE_CHRE_TEST_EVENT(NAN_SUBSCRIBE, 0);

  class App : public NanTestNanoapp {
   public:
    void handleEvent(uint32_t, uint16_t eventType,
                     const void *eventData) override {
      const uint32_t kSubscribeCookie = 0x10aded;

      switch (eventType) {
        case CHRE_EVENT_WIFI_NAN_IDENTIFIER_RESULT: {
          auto event =
              static_cast<const chreWifiNanIdentifierEvent *>(eventData);
          if (event->result.errorCode != CHRE_ERROR_NONE) {
            TestEventQueueSingleton::get()->pushEvent(
                CHRE_EVENT_WIFI_NAN_IDENTIFIER_RESULT);
          }
          break;
        }

        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case NAN_SUBSCRIBE: {
              auto config = (chreWifiNanSubscribeConfig *)(event->data);
              const bool success =
                  chreWifiNanSubscribe(config, &kSubscribeCookie);
              TestEventQueueSingleton::get()->pushEvent(NAN_SUBSCRIBE, success);
              break;
            }
          }
        }
      }
    }
  };

  uint64_t appId = loadNanoapp(MakeUnique<App>());

  PalNanEngineSingleton::get()->setFlags(PalNanEngine::Flags::FAIL_SUBSCRIBE);

  chreWifiNanSubscribeConfig config = {
      .subscribeType = CHRE_WIFI_NAN_SUBSCRIBE_TYPE_PASSIVE,
      .service = "SomeServiceName",
  };
  sendEventToNanoapp(appId, NAN_SUBSCRIBE, config);
  bool success;
  waitForEvent(NAN_SUBSCRIBE, &success);
  EXPECT_TRUE(success);

  waitForEvent(CHRE_EVENT_WIFI_NAN_IDENTIFIER_RESULT);
}

/**
 * Test that a terminated event is received upon the Pal NAN engine
 * terminating a discovered service.
 */
TEST_F(TestBase, WifiNanServiceTerminatedTest) {
  CREATE_CHRE_TEST_EVENT(NAN_SUBSCRIBE, 0);

  class App : public NanTestNanoapp {
   public:
    void handleEvent(uint32_t, uint16_t eventType,
                     const void *eventData) override {
      const uint32_t kSubscribeCookie = 0x10aded;

      switch (eventType) {
        case CHRE_EVENT_WIFI_NAN_IDENTIFIER_RESULT: {
          auto event =
              static_cast<const chreWifiNanIdentifierEvent *>(eventData);
          if (event->result.errorCode == CHRE_ERROR_NONE) {
            TestEventQueueSingleton::get()->pushEvent(
                CHRE_EVENT_WIFI_NAN_IDENTIFIER_RESULT, event->id);
          }
          break;
        }

        case CHRE_EVENT_WIFI_NAN_DISCOVERY_RESULT: {
          auto event =
              static_cast<const chreWifiNanDiscoveryEvent *>(eventData);
          TestEventQueueSingleton::get()->pushEvent(
              CHRE_EVENT_WIFI_NAN_DISCOVERY_RESULT, event->subscribeId);
          break;
        }

        case CHRE_EVENT_WIFI_NAN_SESSION_TERMINATED: {
          auto event =
              static_cast<const chreWifiNanSessionTerminatedEvent *>(eventData);
          TestEventQueueSingleton::get()->pushEvent(
              CHRE_EVENT_WIFI_NAN_SESSION_TERMINATED, event->id);
          break;
        }

        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case NAN_SUBSCRIBE: {
              auto config = (chreWifiNanSubscribeConfig *)(event->data);
              const bool success =
                  chreWifiNanSubscribe(config, &kSubscribeCookie);
              TestEventQueueSingleton::get()->pushEvent(NAN_SUBSCRIBE, success);
              break;
            }
          }
        }
      }
    }
  };

  uint64_t appId = loadNanoapp(MakeUnique<App>());

  chreWifiNanSubscribeConfig config = {
      .subscribeType = CHRE_WIFI_NAN_SUBSCRIBE_TYPE_PASSIVE,
      .service = "SomeServiceName",
  };
  sendEventToNanoapp(appId, NAN_SUBSCRIBE, config);
  bool success;
  waitForEvent(NAN_SUBSCRIBE, &success);
  EXPECT_TRUE(success);

  uint32_t id;
  waitForEvent(CHRE_EVENT_WIFI_NAN_IDENTIFIER_RESULT, &id);

  PalNanEngineSingleton::get()->sendDiscoveryEvent(id);
  uint32_t subscribeId;
  waitForEvent(CHRE_EVENT_WIFI_NAN_DISCOVERY_RESULT, &subscribeId);
  EXPECT_EQ(subscribeId, id);

  PalNanEngineSingleton::get()->onServiceTerminated(id);
  uint32_t terminatedId;
  waitForEvent(CHRE_EVENT_WIFI_NAN_SESSION_TERMINATED, &terminatedId);
  EXPECT_EQ(terminatedId, id);
}

/**
 * Test that a service lost event is received upon the Pal NAN engine 'losing'
 * a discovered service.
 */
TEST_F(TestBase, WifiNanServiceLostTest) {
  CREATE_CHRE_TEST_EVENT(NAN_SUBSCRIBE, 0);

  struct Ids {
    uint32_t subscribe;
    uint32_t publish;
  };

  class App : public NanTestNanoapp {
   public:
    void handleEvent(uint32_t, uint16_t eventType,
                     const void *eventData) override {
      const uint32_t kSubscribeCookie = 0x10aded;

      switch (eventType) {
        case CHRE_EVENT_WIFI_NAN_IDENTIFIER_RESULT: {
          auto event =
              static_cast<const chreWifiNanIdentifierEvent *>(eventData);
          if (event->result.errorCode == CHRE_ERROR_NONE) {
            TestEventQueueSingleton::get()->pushEvent(
                CHRE_EVENT_WIFI_NAN_IDENTIFIER_RESULT, event->id);
          }
          break;
        }

        case CHRE_EVENT_WIFI_NAN_DISCOVERY_RESULT: {
          auto event =
              static_cast<const chreWifiNanDiscoveryEvent *>(eventData);
          TestEventQueueSingleton::get()->pushEvent(
              CHRE_EVENT_WIFI_NAN_DISCOVERY_RESULT, event->subscribeId);
          break;
        }

        case CHRE_EVENT_WIFI_NAN_SESSION_LOST: {
          auto event =
              static_cast<const chreWifiNanSessionLostEvent *>(eventData);
          Ids ids = {.subscribe = event->id, .publish = event->peerId};
          TestEventQueueSingleton::get()->pushEvent(
              CHRE_EVENT_WIFI_NAN_SESSION_LOST, ids);
          break;
        }

        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case NAN_SUBSCRIBE: {
              auto config = (chreWifiNanSubscribeConfig *)(event->data);
              const bool success =
                  chreWifiNanSubscribe(config, &kSubscribeCookie);
              TestEventQueueSingleton::get()->pushEvent(NAN_SUBSCRIBE, success);
              break;
            }
          }
        }
      }
    }
  };

  uint64_t appId = loadNanoapp(MakeUnique<App>());

  chreWifiNanSubscribeConfig config = {
      .subscribeType = CHRE_WIFI_NAN_SUBSCRIBE_TYPE_PASSIVE,
      .service = "SomeServiceName",
  };
  sendEventToNanoapp(appId, NAN_SUBSCRIBE, config);
  bool success;
  waitForEvent(NAN_SUBSCRIBE, &success);
  EXPECT_TRUE(success);

  uint32_t id;
  waitForEvent(CHRE_EVENT_WIFI_NAN_IDENTIFIER_RESULT, &id);

  PalNanEngineSingleton::get()->sendDiscoveryEvent(id);
  uint32_t subscribeId;
  waitForEvent(CHRE_EVENT_WIFI_NAN_DISCOVERY_RESULT, &subscribeId);
  EXPECT_EQ(subscribeId, id);

  PalNanEngineSingleton::get()->onServiceLost(subscribeId, id);
  Ids ids;
  waitForEvent(CHRE_EVENT_WIFI_NAN_SESSION_LOST, &ids);
  EXPECT_EQ(ids.subscribe, id);
  EXPECT_EQ(ids.publish, id);
}

/**
 * Test that a ranging event is received upon requesting NAN range
 * measurements.
 */
TEST_F(TestBase, WifiNanRangingTest) {
  CREATE_CHRE_TEST_EVENT(NAN_SUBSCRIBE, 0);
  CREATE_CHRE_TEST_EVENT(REQUEST_RANGING, 1);

  class App : public NanTestNanoapp {
   public:
    void handleEvent(uint32_t, uint16_t eventType,
                     const void *eventData) override {
      const uint32_t kRangingCookie = 0xfa11;
      const uint32_t kSubscribeCookie = 0x10aded;

      switch (eventType) {
        case CHRE_EVENT_WIFI_ASYNC_RESULT: {
          auto *event = static_cast<const chreAsyncResult *>(eventData);
          if (event->requestType == CHRE_WIFI_REQUEST_TYPE_RANGING) {
            TestEventQueueSingleton::get()->pushEvent(
                CHRE_EVENT_WIFI_ASYNC_RESULT);
          }
          break;
        }

        case CHRE_EVENT_WIFI_RANGING_RESULT: {
          TestEventQueueSingleton::get()->pushEvent(
              CHRE_EVENT_WIFI_RANGING_RESULT);
          break;
        }

        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case NAN_SUBSCRIBE: {
              auto config = (chreWifiNanSubscribeConfig *)(event->data);
              const bool success =
                  chreWifiNanSubscribe(config, &kSubscribeCookie);
              TestEventQueueSingleton::get()->pushEvent(NAN_SUBSCRIBE, success);
              break;
            }

            case REQUEST_RANGING: {
              uint8_t fakeMacAddress[CHRE_WIFI_BSSID_LEN] = {0x1, 0x2, 0x3,
                                                             0x4, 0x5, 0x6};
              struct chreWifiNanRangingParams fakeRangingParams;
              std::memcpy(fakeRangingParams.macAddress, fakeMacAddress,
                          CHRE_WIFI_BSSID_LEN);
              const bool success = chreWifiNanRequestRangingAsync(
                  &fakeRangingParams, &kRangingCookie);
              TestEventQueueSingleton::get()->pushEvent(REQUEST_RANGING,
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

  chreWifiNanSubscribeConfig config = {
      .subscribeType = CHRE_WIFI_NAN_SUBSCRIBE_TYPE_PASSIVE,
      .service = "SomeServiceName",
  };
  sendEventToNanoapp(appId, NAN_SUBSCRIBE, config);
  waitForEvent(NAN_SUBSCRIBE, &success);
  EXPECT_TRUE(success);

  sendEventToNanoapp(appId, REQUEST_RANGING, config);
  waitForEvent(REQUEST_RANGING, &success);
  EXPECT_TRUE(success);
  waitForEvent(CHRE_EVENT_WIFI_ASYNC_RESULT);
  waitForEvent(CHRE_EVENT_WIFI_RANGING_RESULT);
}

TEST_F(TestBase, WifiNanSubscribeCancelTest) {
  CREATE_CHRE_TEST_EVENT(NAN_SUBSCRIBE, 0);
  CREATE_CHRE_TEST_EVENT(NAN_SUBSCRIBE_DONE, 1);
  CREATE_CHRE_TEST_EVENT(NAN_UNSUBSCRIBE, 2);
  CREATE_CHRE_TEST_EVENT(NAN_UNSUBSCRIBE_DONE, 3);

  class App : public NanTestNanoapp {
   public:
    void handleEvent(uint32_t, uint16_t eventType,
                     const void *eventData) override {
      const uint32_t kSubscribeCookie = 0x10aded;

      switch (eventType) {
        case CHRE_EVENT_WIFI_NAN_IDENTIFIER_RESULT: {
          auto event =
              static_cast<const chreWifiNanIdentifierEvent *>(eventData);
          if (event->result.errorCode == CHRE_ERROR_NONE) {
            TestEventQueueSingleton::get()->pushEvent(
                CHRE_EVENT_WIFI_NAN_IDENTIFIER_RESULT, event->id);
          }
          break;
        }

        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case NAN_SUBSCRIBE: {
              auto config = (chreWifiNanSubscribeConfig *)(event->data);
              bool success = chreWifiNanSubscribe(config, &kSubscribeCookie);
              TestEventQueueSingleton::get()->pushEvent(NAN_SUBSCRIBE_DONE,
                                                        success);
              break;
            }
            case NAN_UNSUBSCRIBE: {
              auto *id = static_cast<uint32_t *>(event->data);
              bool success = chreWifiNanSubscribeCancel(*id);
              // Note that since we're 'simulating' NAN functionality here,
              // the async subscribe cancel event will be handled before
              // the return event below is posted. For a real on-device (or
              // non-simulated) test, this won't be the case, and care must
              // be taken to handle the asynchronicity appropriately.
              TestEventQueueSingleton::get()->pushEvent(NAN_UNSUBSCRIBE_DONE,
                                                        success);
              break;
            }
          }
        }
      }
    }
  };

  uint64_t appId = loadNanoapp(MakeUnique<App>());

  chreWifiNanSubscribeConfig config = {
      .subscribeType = CHRE_WIFI_NAN_SUBSCRIBE_TYPE_PASSIVE,
      .service = "SomeServiceName",
  };

  bool success = false;
  sendEventToNanoapp(appId, NAN_SUBSCRIBE, config);
  waitForEvent(NAN_SUBSCRIBE_DONE, &success);
  ASSERT_TRUE(success);

  uint32_t id;
  waitForEvent(CHRE_EVENT_WIFI_NAN_IDENTIFIER_RESULT, &id);

  auto &wifiRequestManager =
      EventLoopManagerSingleton::get()->getWifiRequestManager();
  EXPECT_EQ(wifiRequestManager.getNumNanSubscriptions(), 1);

  success = false;
  sendEventToNanoapp(appId, NAN_UNSUBSCRIBE, id);
  waitForEvent(NAN_UNSUBSCRIBE_DONE, &success);
  ASSERT_TRUE(success);
  // TODO(b/272351526): consider adding an async result event to catch when the
  //                     unsubscribe has finished
  // EXPECT_EQ(wifiRequestManager.getNumNanSubscriptions(), 0);
}

}  // anonymous namespace
}  // namespace chre
