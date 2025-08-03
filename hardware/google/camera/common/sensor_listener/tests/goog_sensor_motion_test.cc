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
#include "utils/SystemClock.h"

#include "goog_sensor_motion.h"

namespace android {
namespace camera_sensor_listener {
namespace {

void PrintMotionSensorEvent(const char* sensor_name,
                            const ExtendedSensorEvent& event) {
  std::cout << sensor_name << " event (" << event.sensor_event.u.vec3.x << " "
            << event.sensor_event.u.vec3.y << " " << event.sensor_event.u.vec3.z
            << ") with timestamp " << event.sensor_event.timestamp
            << " arrives at " << event.event_arrival_time_ns << " with latency "
            << event.event_arrival_time_ns - event.sensor_event.timestamp
            << std::endl;
}

void CheckLatestNMotionEvents(const sp<GoogSensorMotion>& sensor_ptr,
                              int num_events) {
  // Test GetLatestNTimestamps for 200 iterations with 33ms apart.
  for (int i = 0; i < 200; ++i) {
    const std::chrono::milliseconds kCheckPeriod(33 /*ms*/);
    std::this_thread::sleep_for(kCheckPeriod);

    std::vector<int64_t> timestamps;
    std::vector<float> motion_vector_x;
    std::vector<float> motion_vector_y;
    std::vector<float> motion_vector_z;
    std::vector<int64_t> arrival_timestamps;
    sensor_ptr->GetLatestNSensorEvents(num_events, &timestamps,
                                       &motion_vector_x, &motion_vector_y,
                                       &motion_vector_z, &arrival_timestamps);

    EXPECT_EQ(timestamps.size(), num_events);
    EXPECT_EQ(timestamps.size(), motion_vector_x.size());
    EXPECT_EQ(timestamps.size(), motion_vector_y.size());
    EXPECT_EQ(timestamps.size(), motion_vector_z.size());
    EXPECT_EQ(timestamps.size(), arrival_timestamps.size());

    for (int i = 1; i < timestamps.size(); ++i) {
      EXPECT_LT(timestamps[i - 1], timestamps[i]);
    }
  }
}

void CheckQuerySensorEventsBetweenTimestamps(
    const sp<GoogSensorMotion>& sensor_ptr) {
  // Test GetLatestNTimestamps for 200 iterations with 33ms apart.
  int64_t start_time = elapsedRealtimeNano();
  for (int i = 0; i < 200; ++i) {
    const std::chrono::milliseconds kCheckPeriod(33 /*ms*/);
    std::this_thread::sleep_for(kCheckPeriod);
    int64_t end_time = elapsedRealtimeNano();

    std::vector<int64_t> timestamps;
    std::vector<float> motion_vector_x;
    std::vector<float> motion_vector_y;
    std::vector<float> motion_vector_z;
    std::vector<int64_t> arrival_timestamps;
    sensor_ptr->QuerySensorEventsBetweenTimestamps(
        start_time, end_time, &timestamps, &motion_vector_x, &motion_vector_y,
        &motion_vector_z, &arrival_timestamps);

    EXPECT_EQ(timestamps.size(), motion_vector_x.size());
    EXPECT_EQ(timestamps.size(), motion_vector_y.size());
    EXPECT_EQ(timestamps.size(), motion_vector_z.size());
    EXPECT_EQ(timestamps.size(), arrival_timestamps.size());
    if (!timestamps.empty()) {
      EXPECT_GT(timestamps.front(), start_time);
      EXPECT_LE(timestamps.back(), end_time);
    }
    for (int i = 1; i < timestamps.size(); ++i) {
      EXPECT_LT(timestamps[i - 1], timestamps[i]);
    }

    start_time = end_time;
  }
}

}  // namespace

// Test ACCELEROMETER sensor.
TEST(GoogSensorMotionTest, TestAccelerometer) {
  ::android::sp<GoogSensorMotion> ptr;
  ptr = GoogSensorMotion::Create(MotionSensorType::ACCELEROMETER,
                                 2000 /*sampling_period_us*/);
  ASSERT_EQ(ptr, nullptr);

  ptr = GoogSensorMotion::Create(MotionSensorType::ACCELEROMETER,
                                 5000 /*sampling_period_us*/);
  ASSERT_NE(ptr, nullptr);
  ASSERT_TRUE(ptr->GetSensorEnablingStatus());

  // Test customized event arrival callback.
  const char* sensor_name = ptr->GetSensorName();
  std::function<void(const ExtendedSensorEvent& event)> print =
      std::bind(PrintMotionSensorEvent, sensor_name, std::placeholders::_1);
  ptr->SetEventProcessor(print);

  const std::chrono::milliseconds kStartupTime(500 /*ms*/);
  std::this_thread::sleep_for(kStartupTime);
  CheckLatestNMotionEvents(ptr, 6);
  CheckQuerySensorEventsBetweenTimestamps(ptr);
}

// Test GRAVITY sensor.
TEST(GoogSensorMotionTest, TestGravity) {
  ::android::sp<GoogSensorMotion> ptr;
  ptr = GoogSensorMotion::Create(MotionSensorType::GRAVITY,
                                 5000 /*sampling_period_us*/);
  ASSERT_NE(ptr, nullptr);
  ASSERT_TRUE(ptr->GetSensorEnablingStatus());

  // Test customized event arrival callback.
  const char* sensor_name = ptr->GetSensorName();
  std::function<void(const ExtendedSensorEvent& event)> print =
      std::bind(PrintMotionSensorEvent, sensor_name, std::placeholders::_1);
  ptr->SetEventProcessor(print);

  const std::chrono::milliseconds kStartupTime(500 /*ms*/);
  std::this_thread::sleep_for(kStartupTime);
  CheckLatestNMotionEvents(ptr, 6);
  CheckQuerySensorEventsBetweenTimestamps(ptr);
}

// Test GYROSCOPE sensor.
TEST(GoogSensorMotionTest, TestGyroscope) {
  ::android::sp<GoogSensorMotion> ptr;
  ptr = GoogSensorMotion::Create(MotionSensorType::GYROSCOPE,
                                 5000 /*sampling_period_us*/);
  ASSERT_NE(ptr, nullptr);
  ASSERT_TRUE(ptr->GetSensorEnablingStatus());

  // Test customized event arrival callback.
  const char* sensor_name = ptr->GetSensorName();
  std::function<void(const ExtendedSensorEvent& event)> print =
      std::bind(PrintMotionSensorEvent, sensor_name, std::placeholders::_1);
  ptr->SetEventProcessor(print);

  const std::chrono::milliseconds kStartupTime(500 /*ms*/);
  std::this_thread::sleep_for(kStartupTime);
  CheckLatestNMotionEvents(ptr, 7);
  CheckQuerySensorEventsBetweenTimestamps(ptr);
}

// Test LINEAR_ACCELERATION sensor.
TEST(GoogSensorMotionTest, TestLinearAcceleration) {
  ::android::sp<GoogSensorMotion> ptr;
  ptr = GoogSensorMotion::Create(MotionSensorType::LINEAR_ACCELERATION,
                                 5000 /*sampling_period_us*/);
  ASSERT_NE(ptr, nullptr);
  ASSERT_TRUE(ptr->GetSensorEnablingStatus());

  // Test customized event arrival callback.
  const char* sensor_name = ptr->GetSensorName();
  std::function<void(const ExtendedSensorEvent& event)> print =
      std::bind(PrintMotionSensorEvent, sensor_name, std::placeholders::_1);
  ptr->SetEventProcessor(print);

  const std::chrono::milliseconds kStartupTime(500 /*ms*/);
  std::this_thread::sleep_for(kStartupTime);
  CheckLatestNMotionEvents(ptr, 7);
  CheckQuerySensorEventsBetweenTimestamps(ptr);
}

// Test MAGNETIC_FIELD sensor.
TEST(GoogSensorMotionTest, TestMagneticField) {
  ::android::sp<GoogSensorMotion> ptr;
  ptr = GoogSensorMotion::Create(MotionSensorType::MAGNETIC_FIELD,
                                 5000 /*sampling_period_us*/);
  ASSERT_NE(ptr, nullptr);
  ASSERT_TRUE(ptr->GetSensorEnablingStatus());

  // Test customized event arrival callback.
  const char* sensor_name = ptr->GetSensorName();
  std::function<void(const ExtendedSensorEvent& event)> print =
      std::bind(PrintMotionSensorEvent, sensor_name, std::placeholders::_1);
  ptr->SetEventProcessor(print);

  const std::chrono::milliseconds kStartupTime(500 /*ms*/);
  std::this_thread::sleep_for(kStartupTime);
  CheckLatestNMotionEvents(ptr, 7);
  CheckQuerySensorEventsBetweenTimestamps(ptr);
}

}  // namespace camera_sensor_listener
}  // namespace android
