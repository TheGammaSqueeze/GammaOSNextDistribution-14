/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include "test_base.h"

#include <gtest/gtest.h>

#include <cstdint>
#include <optional>
#include <thread>

#include "chre/core/event_loop_manager.h"
#include "chre/core/host_endpoint_manager.h"
#include "chre/platform/log.h"
#include "chre_api/chre/event.h"
#include "test_event_queue.h"
#include "test_util.h"

namespace chre {

namespace {

//! The host endpoint ID to use for this test.
constexpr uint16_t kHostEndpointId = 123;

//! Helper function to get Host Endpoint Manager from Event Loop Manager
HostEndpointManager &getHostEndpointManager() {
  return EventLoopManagerSingleton::get()->getHostEndpointManager();
}

/**
 * Verifies basic functionality of chreConfigureHostEndpointNotifications.
 */
TEST_F(TestBase, HostEndpointDisconnectedTest) {
  CREATE_CHRE_TEST_EVENT(SETUP_NOTIFICATION, 0);

  struct Config {
    bool enable;
    uint16_t endpointId;
  };

  class App : public TestNanoapp {
   public:
    void handleEvent(uint32_t, uint16_t eventType,
                     const void *eventData) override {
      switch (eventType) {
        case CHRE_EVENT_HOST_ENDPOINT_NOTIFICATION: {
          auto notification = *(struct chreHostEndpointNotification *)eventData;
          TestEventQueueSingleton::get()->pushEvent(
              CHRE_EVENT_HOST_ENDPOINT_NOTIFICATION, notification);
        } break;

        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case SETUP_NOTIFICATION: {
              auto config = static_cast<const Config *>(event->data);
              const bool success = chreConfigureHostEndpointNotifications(
                  config->endpointId, config->enable);
              TestEventQueueSingleton::get()->pushEvent(SETUP_NOTIFICATION,
                                                        success);
            }
          }
        }
      }
    }
  };

  struct chreHostEndpointInfo info;
  info.hostEndpointId = kHostEndpointId;
  info.hostEndpointType = CHRE_HOST_ENDPOINT_TYPE_FRAMEWORK;
  info.isNameValid = true;
  strcpy(&info.endpointName[0], "Test endpoint name");
  info.isTagValid = true;
  strcpy(&info.endpointTag[0], "Test tag");
  getHostEndpointManager().postHostEndpointConnected(info);

  uint64_t appId = loadNanoapp(MakeUnique<App>());

  Config config = {.enable = true, .endpointId = kHostEndpointId};

  sendEventToNanoapp(appId, SETUP_NOTIFICATION, config);
  bool success;
  waitForEvent(SETUP_NOTIFICATION, &success);
  EXPECT_TRUE(success);

  struct chreHostEndpointInfo retrievedInfo;
  ASSERT_TRUE(getHostEndpointManager().getHostEndpointInfo(kHostEndpointId,
                                                           &retrievedInfo));
  ASSERT_EQ(retrievedInfo.hostEndpointId, info.hostEndpointId);
  ASSERT_EQ(retrievedInfo.hostEndpointType, info.hostEndpointType);
  ASSERT_EQ(retrievedInfo.isNameValid, info.isNameValid);
  ASSERT_EQ(strcmp(&retrievedInfo.endpointName[0], &info.endpointName[0]), 0);
  ASSERT_EQ(retrievedInfo.isTagValid, info.isTagValid);
  ASSERT_EQ(strcmp(&retrievedInfo.endpointTag[0], &info.endpointTag[0]), 0);

  struct chreHostEndpointNotification notification;
  getHostEndpointManager().postHostEndpointDisconnected(kHostEndpointId);
  waitForEvent(CHRE_EVENT_HOST_ENDPOINT_NOTIFICATION, &notification);

  ASSERT_EQ(notification.hostEndpointId, kHostEndpointId);
  ASSERT_EQ(notification.notificationType,
            HOST_ENDPOINT_NOTIFICATION_TYPE_DISCONNECT);
  ASSERT_EQ(notification.reserved, 0);

  ASSERT_FALSE(EventLoopManagerSingleton::get()
                   ->getHostEndpointManager()
                   .getHostEndpointInfo(kHostEndpointId, &retrievedInfo));
}

TEST_F(TestBase, HostEndpointNotRegisteredTest) {
  struct chreHostEndpointInfo retrievedInfo;
  ASSERT_FALSE(getHostEndpointManager().getHostEndpointInfo(kHostEndpointId,
                                                            &retrievedInfo));
}

TEST_F(TestBase, HostEndpointDisconnectedTwiceTest) {
  struct chreHostEndpointInfo info;
  info.hostEndpointId = kHostEndpointId;
  info.hostEndpointType = CHRE_HOST_ENDPOINT_TYPE_FRAMEWORK;
  info.isNameValid = false;
  info.isTagValid = false;
  getHostEndpointManager().postHostEndpointConnected(info);

  getHostEndpointManager().postHostEndpointDisconnected(kHostEndpointId);
  // The second invocation should be a silent no-op.
  getHostEndpointManager().postHostEndpointDisconnected(kHostEndpointId);
}

}  // anonymous namespace
}  // namespace chre
