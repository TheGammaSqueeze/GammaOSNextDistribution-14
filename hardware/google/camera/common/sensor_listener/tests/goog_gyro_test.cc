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

#include <atomic>
#include <fstream>
#include <functional>
#include <iostream>
#include <memory>
#include <thread>

#include <gtest/gtest.h>
#include <log/log.h>
#include <utils/SystemClock.h>

#include "goog_gyro_direct.h"
#include "goog_sensor_motion.h"
#include "sensor_test_utils.h"

namespace android {
namespace camera_sensor_listener {

class GoogGyroTest : public ::testing::Test {
 protected:
  void CreateGyroDirectListener(int64_t gyro_sampling_period_us,
                                size_t event_queue_size) {
    gyro_direct_ptr_ =
        GoogGyroDirect::Create(gyro_sampling_period_us, event_queue_size);
    ASSERT_NE(gyro_direct_ptr_, nullptr);
    ASSERT_TRUE(gyro_direct_ptr_->GetSensorEnablingStatus());
  }

  void CreateGyroPollListener(int64_t gyro_sampling_period_us,
                              size_t event_queue_size) {
    gyro_poll_ptr_ = GoogSensorMotion::Create(
        MotionSensorType::GYROSCOPE, gyro_sampling_period_us, event_queue_size);
    ASSERT_NE(gyro_poll_ptr_, nullptr);
    ASSERT_TRUE(gyro_poll_ptr_->GetSensorEnablingStatus());
    // Set up gyro_poll_ptr_'s event callback.
    std::function<void(const ExtendedSensorEvent& event)> enqueue_gyro_poll =
        [&](const ExtendedSensorEvent& event) {
          gyro_poll_timestamp_ns_.push_back(event.sensor_event.timestamp);
          gyro_poll_azimuth_.push_back(event.sensor_event.u.vec3.x);
          gyro_poll_pitch_.push_back(event.sensor_event.u.vec3.y);
          gyro_poll_roll_.push_back(event.sensor_event.u.vec3.z);
          gyro_poll_arrival_time_ns_.push_back(event.event_arrival_time_ns);
        };
    gyro_poll_ptr_->SetEventProcessor(enqueue_gyro_poll);
  }

  void QueryGyroDirectEventsForDuration(int time_duration_ms,
                                        int check_period_ms) {
    const std::chrono::milliseconds kCheckPeriod(check_period_ms);
    int64_t check_time_duration_ns =
        static_cast<int64_t>(time_duration_ms) * static_cast<int64_t>(1e6);

    int64_t start_measure_time = android::elapsedRealtimeNano();
    int64_t gyro_start_time = start_measure_time;
    while (1) {
      std::this_thread::sleep_for(kCheckPeriod);

      std::vector<int64_t> gyro_timestamp_ns;
      std::vector<float> azimuth;
      std::vector<float> pitch;
      std::vector<float> roll;
      std::vector<int64_t> gyro_arrival_time_ns;

      int64_t gyro_end_time = LLONG_MAX;
      gyro_direct_ptr_->QueryGyroEventsBetweenTimestamps(
          gyro_start_time, gyro_end_time, &gyro_timestamp_ns, &azimuth, &pitch,
          &roll, &gyro_arrival_time_ns);

      if (!gyro_timestamp_ns.empty()) {
        EXPECT_GT(gyro_timestamp_ns.front(), gyro_start_time);
        EXPECT_LE(gyro_timestamp_ns.back(), gyro_end_time);

        gyro_direct_timestamp_ns_.insert(gyro_direct_timestamp_ns_.end(),
                                         gyro_timestamp_ns.begin(),
                                         gyro_timestamp_ns.end());
        gyro_direct_azimuth_.insert(gyro_direct_azimuth_.end(), azimuth.begin(),
                                    azimuth.end());
        gyro_direct_pitch_.insert(gyro_direct_pitch_.end(), pitch.begin(),
                                  pitch.end());
        gyro_direct_roll_.insert(gyro_direct_roll_.end(), roll.begin(),
                                 roll.end());
        gyro_direct_arrival_time_ns_.insert(gyro_direct_arrival_time_ns_.end(),
                                            gyro_arrival_time_ns.begin(),
                                            gyro_arrival_time_ns.end());

        gyro_start_time = gyro_timestamp_ns.back();
      }

      int64_t current_time = android::elapsedRealtimeNano();
      if (current_time - start_measure_time > check_time_duration_ns) {
        break;
      }
    }
  }

  void CheckTimestampInterval(const std::vector<int64_t>& gyro_timestamp_ns,
                              int64_t gyro_sampling_period_us) {
    // gyro samples' timestamp intervals need to be between
    // [sampling_period * 0.9, sampling_period * 1.1].
    int64_t delta_upper_ns =
        gyro_sampling_period_us * 1000 + gyro_sampling_period_us * 100;
    int64_t delta_lower_ns =
        gyro_sampling_period_us * 1000 - gyro_sampling_period_us * 100;
    size_t length = gyro_timestamp_ns.size();
    EXPECT_GT(length, 0) << "Get zero gyro events";
    for (int i = 1; i < length; ++i) {
      int64_t delta = gyro_timestamp_ns[i] - gyro_timestamp_ns[i - 1];
      EXPECT_LE(delta, delta_upper_ns)
          << " gyro timestamp i+1 " << gyro_timestamp_ns[i]
          << " gyro timestamp i " << gyro_timestamp_ns[i - 1] << " delta "
          << delta;
      EXPECT_GE(delta, delta_lower_ns)
          << " gyro timestamp i+1 " << gyro_timestamp_ns[i]
          << " gyro timestamp i " << gyro_timestamp_ns[i - 1] << " delta "
          << delta;
    }
  }

  void CheckGyroSampleLatency(const std::vector<int64_t>& gyro_timestamp_ns,
                              const std::vector<int64_t>& gyro_arrival_time_ns) {
    int num_latency_10ms = 0;
    int num_latency_15ms = 0;
    int num_sample = gyro_timestamp_ns.size();
    EXPECT_GT(num_sample, 0) << "Get zero gyro events";
    int64_t sum_latency = 0;
    for (int i = 0; i < num_sample; ++i) {
      int64_t arrival_latency = gyro_arrival_time_ns[i] - gyro_timestamp_ns[i];
      sum_latency += arrival_latency;
      if (arrival_latency >= 10e6) {
        num_latency_10ms++;
        if (arrival_latency >= 15e6) {
          num_latency_15ms++;
        }
      }
    }
    float average_latency = static_cast<float>(sum_latency) /
                            static_cast<float>(num_sample) / 1000000.f;
    std::cout << "average latency " << average_latency << "ms\n";
    float latency_rate_10ms =
        static_cast<float>(num_latency_10ms) / static_cast<float>(num_sample);
    float latency_rate_15ms =
        static_cast<float>(num_latency_15ms) / static_cast<float>(num_sample);
    EXPECT_LE(average_latency, 7.f) << "gyro average latency should be <= 7ms";
    EXPECT_LE(latency_rate_10ms, 0.01f)
        << "gyro latency 99 percentile should be <= 10ms";
    EXPECT_LE(latency_rate_15ms, 0.001f)
        << "gyro latency 99.9 percentile should be <= 15ms";
  }

  void CheckGyroTimestampOffset() {
    // find the first corresponding gyro sample.
    int poll_index = 0;
    int direct_index = 0;
    EXPECT_GT(gyro_poll_timestamp_ns_.size(), 0) << "Get zero gyro poll events";
    EXPECT_GT(gyro_direct_timestamp_ns_.size(), 0)
        << "Get zero gyro direct events";
    while (poll_index < gyro_poll_timestamp_ns_.size() &&
           direct_index < gyro_direct_timestamp_ns_.size()) {
      if (llabs(gyro_poll_timestamp_ns_[poll_index] -
                gyro_direct_timestamp_ns_[direct_index]) < 1e6 &&
          gyro_poll_azimuth_[poll_index] == gyro_direct_azimuth_[direct_index] &&
          gyro_poll_pitch_[poll_index] == gyro_direct_pitch_[direct_index] &&
          gyro_poll_roll_[poll_index] == gyro_direct_roll_[direct_index]) {
        break;
      } else {
        if (gyro_poll_timestamp_ns_[poll_index] <
            gyro_direct_timestamp_ns_[direct_index]) {
          ++poll_index;
        } else {
          ++direct_index;
        }
      }
    }
    int64_t max_offset = 0;
    int num_offset = 0;
    int num_samples = 0;

    for (int i = poll_index, j = direct_index;
         i < gyro_poll_timestamp_ns_.size() &&
         j < gyro_direct_timestamp_ns_.size();
         ++i, ++j) {
      int64_t offset = gyro_direct_timestamp_ns_[j] - gyro_poll_timestamp_ns_[i];
      if (offset != 0) {
        ++num_offset;
        if (llabs(offset) > max_offset) {
          max_offset = llabs(offset);
        }
      }
      ++num_samples;
    }
    EXPECT_LE(max_offset, 0) << "max timestamp drift " << max_offset << "ns\n";
    EXPECT_LE(num_offset, 0) << "drifted samples " << num_offset << " out of "
                             << num_samples << " samples\n";
  }

  void LogGyroDataToFile(const std::string& filename,
                         const std::vector<int64_t>& gyro_timestamp_ns,
                         const std::vector<float>& azimuth,
                         const std::vector<float>& roll,
                         const std::vector<float>& pitch,
                         const std::vector<int64_t>& gyro_arrival_time_ns) {
    std::string output_filename;
    bool result = GenerateLogFilename(filename, &output_filename);
    EXPECT_EQ(result, true) << "fail to create gyro test log file";
    std::cout << "save gyro log to " << output_filename << "\n";
    std::ofstream ofs(output_filename);
    for (int i = 0; i < gyro_timestamp_ns.size(); ++i) {
      ofs << gyro_timestamp_ns[i] << " " << azimuth[i] << " " << roll[i] << " "
          << pitch[i] << " " << gyro_arrival_time_ns[i] << "\n";
    }
  }

  sp<GoogSensorMotion> gyro_poll_ptr_ = nullptr;
  std::vector<int64_t> gyro_poll_timestamp_ns_;
  std::vector<float> gyro_poll_azimuth_;
  std::vector<float> gyro_poll_pitch_;
  std::vector<float> gyro_poll_roll_;
  std::vector<int64_t> gyro_poll_arrival_time_ns_;

  std::unique_ptr<GoogGyroDirect> gyro_direct_ptr_ = nullptr;
  std::vector<int64_t> gyro_direct_timestamp_ns_;
  std::vector<float> gyro_direct_azimuth_;
  std::vector<float> gyro_direct_pitch_;
  std::vector<float> gyro_direct_roll_;
  std::vector<int64_t> gyro_direct_arrival_time_ns_;
};

TEST_F(GoogGyroTest, TestDirectGyroLatency) {
  CreateGyroDirectListener(/*gyro_sampling_period_us=*/5000,
                           /*event_queue_size=*/60);
  // Test duration 5 minutes, check period 5ms.
  // Direct gyro arrival time is the poking time,
  // to effectively test latency, check period has to be
  // the same as sampling period.
  QueryGyroDirectEventsForDuration(/*gyro_check_duration_ms=*/300000,
                                   /*gyro_check_period_ms=*/5);
  CheckGyroSampleLatency(gyro_direct_timestamp_ns_,
                         gyro_direct_arrival_time_ns_);
}

TEST_F(GoogGyroTest, TestGyroDirectIntervalAndDrift) {
  CreateGyroDirectListener(/*gyro_sampling_period_us=*/5000,
                           /*event_queue_size=*/60);
  CreateGyroPollListener(/*gyro_sampling_period_us=*/5000,
                         /*event_queue_size=*/20);
  QueryGyroDirectEventsForDuration(/*time_duration_ms=*/300000,
                                   /*check_period_ms=*/33);
  gyro_poll_ptr_->Disable();
  CheckTimestampInterval(gyro_direct_timestamp_ns_,
                         /*gyro_sampling_period_us=*/5000);
  CheckTimestampInterval(gyro_poll_timestamp_ns_,
                         /*gyro_sampling_period_us=*/5000);
  CheckGyroTimestampOffset();

  // Save gyro poll data to txt file.
  LogGyroDataToFile("gyro_poll_log", gyro_poll_timestamp_ns_,
                    gyro_poll_azimuth_, gyro_poll_roll_, gyro_poll_pitch_,
                    gyro_poll_arrival_time_ns_);
  // Save gyro direct data to txt file.
  LogGyroDataToFile("gyro_direct_log", gyro_direct_timestamp_ns_,
                    gyro_direct_azimuth_, gyro_direct_roll_, gyro_direct_pitch_,
                    gyro_direct_arrival_time_ns_);
}

TEST_F(GoogGyroTest, TestGyroEnableDisable) {
  CreateGyroDirectListener(/*gyro_sampling_period_us=*/5000,
                           /*event_queue_size=*/20);
  QueryGyroDirectEventsForDuration(/*time_duration_ms=*/300,
                                   /*check_period_ms=*/33);
  size_t num_gyro_direct_samples = gyro_direct_timestamp_ns_.size();
  EXPECT_GT(gyro_direct_timestamp_ns_.size(), 0);
  gyro_direct_ptr_->DisableDirectChannel();
  EXPECT_FALSE(gyro_direct_ptr_->GetSensorEnablingStatus());
  const std::chrono::milliseconds kWaitPeriod(/*wait_period_ms=*/300);
  std::this_thread::sleep_for(kWaitPeriod);
  gyro_direct_ptr_->EnableDirectChannel();
  EXPECT_TRUE(gyro_direct_ptr_->GetSensorEnablingStatus());
  QueryGyroDirectEventsForDuration(/*time_duration_ms=*/300,
                                   /*check_period_ms=*/33);
  EXPECT_GT(gyro_direct_timestamp_ns_.size(), num_gyro_direct_samples);

  CreateGyroPollListener(/*gyro_sampling_period_us=*/5000,
                         /*event_queue_size=*/20);
  std::this_thread::sleep_for(kWaitPeriod);
  size_t num_gyro_poll_samples = gyro_poll_timestamp_ns_.size();
  EXPECT_GT(num_gyro_poll_samples, 0);
  gyro_poll_ptr_->Disable();
  EXPECT_FALSE(gyro_poll_ptr_->GetSensorEnablingStatus());
  std::this_thread::sleep_for(kWaitPeriod);
  EXPECT_EQ(gyro_poll_timestamp_ns_.size(), num_gyro_poll_samples);
  gyro_poll_ptr_->Enable();
  EXPECT_TRUE(gyro_poll_ptr_->GetSensorEnablingStatus());
  std::this_thread::sleep_for(kWaitPeriod);
  EXPECT_GT(gyro_poll_timestamp_ns_.size(), num_gyro_poll_samples);
}

}  // namespace camera_sensor_listener
}  // namespace android
