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

#include "chre_api/chre/sensor.h"

#include <cstdint>

#include "chre/core/event_loop_manager.h"
#include "chre/core/settings.h"
#include "chre/platform/linux/pal_sensor.h"
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

TEST_F(TestBase, SensorCanSubscribeAndUnsubscribeToDataEvents) {
  CREATE_CHRE_TEST_EVENT(CONFIGURE, 0);

  struct Configuration {
    uint32_t sensorHandle;
    uint64_t interval;
    enum chreSensorConfigureMode mode;
  };

  class App : public TestNanoapp {
   public:
    void handleEvent(uint32_t, uint16_t eventType,
                     const void *eventData) override {
      switch (eventType) {
        case CHRE_EVENT_SENSOR_SAMPLING_CHANGE: {
          auto *event =
              static_cast<const struct chreSensorSamplingStatusEvent *>(
                  eventData);
          TestEventQueueSingleton::get()->pushEvent(
              CHRE_EVENT_SENSOR_SAMPLING_CHANGE, *event);
          break;
        }

        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case CONFIGURE: {
              auto config = static_cast<const Configuration *>(event->data);
              const bool success = chreSensorConfigure(
                  config->sensorHandle, config->mode, config->interval, 0);
              TestEventQueueSingleton::get()->pushEvent(CONFIGURE, success);
              break;
            }
          }
        }
      }
    }
  };

  uint64_t appId = loadNanoapp(MakeUnique<App>());

  bool success;

  EXPECT_FALSE(chrePalSensorIsSensor0Enabled());

  Configuration config{.sensorHandle = 0,
                       .interval = 100,
                       .mode = CHRE_SENSOR_CONFIGURE_MODE_CONTINUOUS};
  sendEventToNanoapp(appId, CONFIGURE, config);
  waitForEvent(CONFIGURE, &success);
  EXPECT_TRUE(success);
  struct chreSensorSamplingStatusEvent event;
  waitForEvent(CHRE_EVENT_SENSOR_SAMPLING_CHANGE, &event);
  EXPECT_EQ(event.sensorHandle, config.sensorHandle);
  EXPECT_EQ(event.status.interval, config.interval);
  EXPECT_TRUE(event.status.enabled);
  EXPECT_TRUE(chrePalSensorIsSensor0Enabled());

  config = {.sensorHandle = 0,
            .interval = 50,
            .mode = CHRE_SENSOR_CONFIGURE_MODE_DONE};
  sendEventToNanoapp(appId, CONFIGURE, config);
  waitForEvent(CONFIGURE, &success);
  EXPECT_TRUE(success);
  EXPECT_FALSE(chrePalSensorIsSensor0Enabled());
}

TEST_F(TestBase, SensorUnsubscribeToDataEventsOnUnload) {
  CREATE_CHRE_TEST_EVENT(CONFIGURE, 0);

  struct Configuration {
    uint32_t sensorHandle;
    uint64_t interval;
    enum chreSensorConfigureMode mode;
  };

  class App : public TestNanoapp {
   public:
    void handleEvent(uint32_t, uint16_t eventType,
                     const void *eventData) override {
      switch (eventType) {
        case CHRE_EVENT_SENSOR_SAMPLING_CHANGE: {
          auto *event =
              static_cast<const struct chreSensorSamplingStatusEvent *>(
                  eventData);
          TestEventQueueSingleton::get()->pushEvent(
              CHRE_EVENT_SENSOR_SAMPLING_CHANGE, *event);
          break;
        }

        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case CONFIGURE: {
              auto config = static_cast<const Configuration *>(event->data);
              const bool success = chreSensorConfigure(
                  config->sensorHandle, config->mode, config->interval, 0);
              TestEventQueueSingleton::get()->pushEvent(CONFIGURE, success);
              break;
            }
          }
        }
      }
    }
  };

  uint64_t appId = loadNanoapp(MakeUnique<App>());

  EXPECT_FALSE(chrePalSensorIsSensor0Enabled());

  Configuration config{.sensorHandle = 0,
                       .interval = 100,
                       .mode = CHRE_SENSOR_CONFIGURE_MODE_CONTINUOUS};
  sendEventToNanoapp(appId, CONFIGURE, config);
  bool success;
  waitForEvent(CONFIGURE, &success);
  EXPECT_TRUE(success);
  struct chreSensorSamplingStatusEvent event;
  waitForEvent(CHRE_EVENT_SENSOR_SAMPLING_CHANGE, &event);
  EXPECT_EQ(event.sensorHandle, config.sensorHandle);
  EXPECT_EQ(event.status.interval, config.interval);
  EXPECT_TRUE(event.status.enabled);
  EXPECT_TRUE(chrePalSensorIsSensor0Enabled());

  unloadNanoapp(appId);
  EXPECT_FALSE(chrePalSensorIsSensor0Enabled());
}

}  // namespace
}  // namespace chre