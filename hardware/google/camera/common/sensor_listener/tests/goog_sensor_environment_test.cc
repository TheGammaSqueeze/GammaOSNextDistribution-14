/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include <functional>
#include <iostream>
#include <thread>

#include <gtest/gtest.h>
#include "utils/RefBase.h"

#include "goog_sensor_environment.h"

namespace android {
namespace camera_sensor_listener {
namespace {

void PrintSensorEvent(const char* sensor_name,
                      const ExtendedSensorEvent& event) {
  std::cout << sensor_name << " event " << event.sensor_event.u.scalar
            << " with timestamp " << event.sensor_event.timestamp
            << " arrives at " << event.event_arrival_time_ns << " with latency "
            << event.event_arrival_time_ns - event.sensor_event.timestamp
            << std::endl;
}

void CheckLatestNEvents(const sp<GoogSensorEnvironment>& sensor_ptr,
                        int num_events) {
  // Test GetLatestNTimestamps for 200 iterations with 33ms apart.
  for (int i = 0; i < 200; ++i) {
    const std::chrono::milliseconds kCheckPeriod(33 /*ms*/);
    std::this_thread::sleep_for(kCheckPeriod);

    std::vector<int64_t> timestamps;
    std::vector<float> event_data;
    std::vector<int64_t> arrival_timestamps;
    sensor_ptr->GetLatestNSensorEvents(num_events, &timestamps, &event_data,
                                       &arrival_timestamps);

    // On_change sensors may have less events than num_events if
    // not much environment change happens.
    EXPECT_NE(timestamps.size(), 0);
    EXPECT_EQ(timestamps.size(), event_data.size());
    EXPECT_EQ(timestamps.size(), arrival_timestamps.size());
    for (int i = 1; i < timestamps.size(); ++i) {
      EXPECT_LT(timestamps[i - 1], timestamps[i]);
    }
  }
}

}  // namespace

// Test DEVICE_ORIENTATION sensor.
// Try to rotate device during test to get on_change sensor events.
TEST(GoogSensorEnvironmentTest, TestDeviceOrientation) {
  ::android::sp<GoogSensorEnvironment> ptr;
  ptr = GoogSensorEnvironment::Create(EnvironmentSensorType::DEVICE_ORIENTATION);
  ASSERT_NE(ptr, nullptr);
  ASSERT_TRUE(ptr->GetSensorEnablingStatus());

  // Test customized event arrival callback.
  const char* sensor_name = ptr->GetSensorName();
  std::function<void(const ExtendedSensorEvent& event)> print =
      std::bind(PrintSensorEvent, sensor_name, std::placeholders::_1);
  ptr->SetEventProcessor(print);

  const std::chrono::milliseconds kStartupTime(5000 /*ms*/);
  std::this_thread::sleep_for(kStartupTime);
  CheckLatestNEvents(ptr, 2);
}

// Test LIGHT sensor.
// Try to put device under different lighting condition during test.
TEST(GoogSensorEnvironmentTest, TestLight) {
  ::android::sp<GoogSensorEnvironment> ptr;
  ptr = GoogSensorEnvironment::Create(EnvironmentSensorType::LIGHT);
  ASSERT_NE(ptr, nullptr);
  ASSERT_TRUE(ptr->GetSensorEnablingStatus());

  // Test customized event arrival callback.
  const char* sensor_name = ptr->GetSensorName();
  std::function<void(const ExtendedSensorEvent& event)> print =
      std::bind(PrintSensorEvent, sensor_name, std::placeholders::_1);
  ptr->SetEventProcessor(print);

  const std::chrono::milliseconds kStartupTime(5000 /*ms*/);
  std::this_thread::sleep_for(kStartupTime);
  CheckLatestNEvents(ptr, 3);
}

// Test PROXIMITY sensor.
// Try to move hands close/away from device during test.
TEST(GoogSensorEnvironmentTest, TestProximity) {
  ::android::sp<GoogSensorEnvironment> ptr;
  ptr = GoogSensorEnvironment::Create(EnvironmentSensorType::PROXIMITY);
  ASSERT_NE(ptr, nullptr);
  ASSERT_TRUE(ptr->GetSensorEnablingStatus());

  // Test customized event arrival callback.
  const char* sensor_name = ptr->GetSensorName();
  std::function<void(const ExtendedSensorEvent& event)> print =
      std::bind(PrintSensorEvent, sensor_name, std::placeholders::_1);
  ptr->SetEventProcessor(print);

  const std::chrono::milliseconds kStartupTime(5000 /*ms*/);
  std::this_thread::sleep_for(kStartupTime);
  CheckLatestNEvents(ptr, 5);
}

}  // namespace camera_sensor_listener
}  // namespace android
