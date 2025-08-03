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

#include "goog_sensor_sync.h"

#include <binder/ProcessState.h>
#include <gtest/gtest.h>
#include <stdlib.h>

#include <atomic>
#include <fstream>
#include <functional>
#include <iostream>
#include <sstream>
#include <thread>

#include "sensor_test_utils.h"
#include "utils/RefBase.h"

namespace android {
namespace camera_sensor_listener {
namespace {
// TODO(xuetu): add more camera id and test cases once verified.
enum class CameraId {
  kPixel4BackWide = 0,
  kPixel4Front,
  kPixel4BackTele,
  kSliderBackWide,
  kMaxNum
};
}  // namespace

// For Pixel 4 devices, camera sensor can only be streamed when the whole
// camera HAL is running. GoogSensorSyncTest is not a strict unit test, but
// more a integral test to test both functionality and performance of Vsync
// signals.
class GoogSensorSyncTest : public ::testing::Test {
 public:
  void SetUp() override {
    android::ProcessState::self()->startThreadPool();
  }

 protected:
  uint8_t CameraIdMapping(CameraId camera_id) {
    switch (camera_id) {
      case CameraId::kPixel4BackWide: {
        return 0;
      }
      case CameraId::kPixel4BackTele: {
        return 2;
      }
      case CameraId::kSliderBackWide: {
        return 1;  // slider back camera connects vsync 1
      }
      default: {
        return static_cast<uint8_t>(CameraId::kMaxNum);
      }
    }
  }

  void SetupTestEnvironment(CameraId camera_id) {
    const std::chrono::milliseconds kWaitTime(20000 /*ms*/);
    std::this_thread::sleep_for(kWaitTime);
    uint8_t camera_id_int = CameraIdMapping(camera_id);
    ASSERT_NE(camera_id_int, static_cast<uint8_t>(CameraId::kMaxNum));
    vsync_camera_id_ = static_cast<uint32_t>(camera_id_int);

    // Create vsync sensor listener.
    sensor_sync_ptr_ = GoogSensorSync::Create(camera_id_int, kEventQueueSize);
    ASSERT_NE(sensor_sync_ptr_, nullptr);
    ASSERT_TRUE(sensor_sync_ptr_->GetSensorEnablingStatus());

    // Create customized callback function to collect incoming event data.
    std::function<void(const ExtendedSensorEvent& event)> collect =
        [this](const ExtendedSensorEvent& event) {
          vsync_timestamps_.push_back(event.sensor_event.timestamp);
          vsync_arrival_timestamps_.push_back(event.event_arrival_time_ns);
          int64_t frame_id, boottime_timestamp;
          sensor_sync_ptr_->ExtractFrameIdAndBoottimeTimestamp(
              event, &frame_id, &boottime_timestamp);
          boottime_timestamps_.push_back(boottime_timestamp);
          frame_ids_.push_back(frame_id);
        };
    sensor_sync_ptr_->SetEventProcessor(collect);
    std::this_thread::sleep_for(kWaitTime);
  }

  void TeardownTestEnvironment() {
    const std::chrono::milliseconds kWaitTime(10000 /*ms*/);
    std::this_thread::sleep_for(kWaitTime);
  }

  void CheckSamplesValidy(int32_t num_query) {
    // Check total collected sample number.
    size_t num_vsync_timestamps = vsync_timestamps_.size();
    size_t num_vsync_arrival_timestamps = vsync_arrival_timestamps_.size();
    size_t num_boottime_timestamps = boottime_timestamps_.size();
    size_t num_frame_ids = frame_ids_.size();
    EXPECT_EQ(num_vsync_timestamps, num_vsync_arrival_timestamps)
        << "num_vsync_timestamps " << num_vsync_timestamps
        << " num_vsync_arrival_timestamps " << num_vsync_arrival_timestamps;
    EXPECT_EQ(num_boottime_timestamps, num_vsync_arrival_timestamps)
        << "num_boottime_timestamps " << num_vsync_timestamps
        << " num_vsync_arrival_timestamps " << num_vsync_arrival_timestamps;
    EXPECT_EQ(num_frame_ids, num_vsync_arrival_timestamps)
        << "num_frame_ids " << num_frame_ids << " num_vsync_arrival_timestamps "
        << num_vsync_arrival_timestamps;
    EXPECT_GT(num_vsync_timestamps, num_query * 0.9)
        << "only received " << num_vsync_timestamps << " vsync events";

    // Check boottime and vsync timestamp delta.
    if (num_vsync_timestamps > 0 &&
        num_vsync_timestamps == num_boottime_timestamps) {
      for (int i = 0; i < num_vsync_timestamps; ++i) {
        int64_t timestamp_delta = vsync_timestamps_[i] - boottime_timestamps_[i];
        EXPECT_LE(llabs(timestamp_delta), 10000000)
            << "delta vsync timestamp " << vsync_timestamps_[i]
            << " and boottime timestamp " << boottime_timestamps_[i] << " is "
            << timestamp_delta;
      }
    }

    // Check delivery latency
    if (num_vsync_timestamps > 0 &&
        num_vsync_timestamps == num_vsync_arrival_timestamps) {
      int64_t sum_latency = 0;
      int64_t max_latency = 0;
      int max_latency_sample_index = -1;
      for (int i = 0; i < num_vsync_timestamps; ++i) {
        int64_t latency = vsync_arrival_timestamps_[i] - vsync_timestamps_[i];
        sum_latency += latency;
        if (latency > max_latency) {
          max_latency = latency;
          max_latency_sample_index = i;
        }
      }
      double ave_latency = static_cast<double>(sum_latency) /
                           static_cast<double>(num_vsync_timestamps);
      EXPECT_LE(ave_latency, 8000000)
          << "average vsync timestamp latency " << ave_latency << "ns ("
          << ave_latency / 1e6 << "ms) exceeds 8000000ns (8ms)";
      EXPECT_LE(max_latency, 10000000)
          << "vsync timestamp " << vsync_timestamps_[max_latency_sample_index]
          << " arrives at "
          << vsync_arrival_timestamps_[max_latency_sample_index]
          << " with latency " << max_latency << "ns (" << max_latency / 1e6
          << "ms)";
    }

    // Check vsync timestamp interval
    for (int i = 1; i < num_vsync_timestamps; ++i) {
      int64_t interval = vsync_timestamps_[i] - vsync_timestamps_[i - 1];
      // assume fps is at least 60, sof interval should be > 12ms.
      EXPECT_GT(interval, 12000000)
          << "timestamp interval between " << vsync_timestamps_[i] << " and "
          << vsync_timestamps_[i - 1] << " has interval " << interval;
    }

    // Check frame id interval
    for (int i = 1; i < num_frame_ids; ++i) {
      int64_t interval = frame_ids_[i] - frame_ids_[i - 1];
      EXPECT_EQ(interval, 1)
          << "frame id " << frame_ids_[i] << " jumps from " << frame_ids_[i - 1]
          << " with interval " << interval;
    }
  }

  void TestGetLatestNSamples(int num_sample_to_query,
                             std::vector<int64_t>* vsync_timestamps,
                             std::vector<int64_t>* frame_ids,
                             std::vector<int64_t>* boottime_timestamps,
                             std::vector<int64_t>* arrival_timestamps) {
    sensor_sync_ptr_->GetLatestNSamples(num_sample_to_query, vsync_timestamps,
                                        frame_ids, boottime_timestamps,
                                        arrival_timestamps);
    size_t num_vsync_timestamps = vsync_timestamps->size();
    size_t num_frame_ids = frame_ids->size();
    size_t num_boottime_timestamps = boottime_timestamps->size();
    size_t num_arrival_timestamps = arrival_timestamps->size();
    int max_sample_number = num_sample_to_query < kEventQueueSize
                                ? num_sample_to_query
                                : kEventQueueSize;
    EXPECT_LE(num_vsync_timestamps, max_sample_number);
    EXPECT_LE(num_frame_ids, max_sample_number);
    EXPECT_LE(num_boottime_timestamps, max_sample_number);
    EXPECT_LE(num_arrival_timestamps, max_sample_number);
    EXPECT_EQ(num_vsync_timestamps, num_arrival_timestamps);
    EXPECT_EQ(num_frame_ids, num_arrival_timestamps);
    EXPECT_EQ(num_boottime_timestamps, num_arrival_timestamps);
    for (int i = 1; i < num_vsync_timestamps; ++i) {
      EXPECT_LT(vsync_timestamps->at(i - 1), vsync_timestamps->at(i));
    }
    for (int i = 1; i < num_frame_ids; ++i) {
      EXPECT_LT(frame_ids->at(i - 1), frame_ids->at(i));
    }
    for (int i = 1; i < num_boottime_timestamps; ++i) {
      EXPECT_LT(boottime_timestamps->at(i - 1), boottime_timestamps->at(i));
    }
    for (int i = 1; i < num_arrival_timestamps; ++i) {
      EXPECT_LT(arrival_timestamps->at(i - 1), arrival_timestamps->at(i));
    }
  }

  // Input boottime_timestamp and reference_vsync_timestamp must be valid.
  void TestSyncTimestamp(int64_t boottime_timestamp,
                         int64_t reference_vsync_timestamp) {
    // Test valid input.
    int64_t vsync_timestamp =
        sensor_sync_ptr_->SyncTimestamp(boottime_timestamp);
    EXPECT_NE(vsync_timestamp, boottime_timestamp);
    EXPECT_EQ(vsync_timestamp, reference_vsync_timestamp);
    // Test invalid input.
    int64_t invalid_boottime_timestamp = boottime_timestamp + 15000000;
    vsync_timestamp =
        sensor_sync_ptr_->SyncTimestamp(invalid_boottime_timestamp);
    EXPECT_EQ(vsync_timestamp, invalid_boottime_timestamp);
  }

  // Input boottime_timestamp, frame_id and reference_vsync_timestamp
  // must be valid.
  void TestMatchTimestamp(int64_t boottime_timestamp, int64_t frame_id,
                          int64_t reference_vsync_timestamp) {
    // Test valid input.
    int64_t vsync_timestamp =
        sensor_sync_ptr_->MatchTimestamp(boottime_timestamp, frame_id);
    EXPECT_NE(vsync_timestamp, boottime_timestamp);
    EXPECT_EQ(vsync_timestamp, reference_vsync_timestamp);
    // Test invalid input.
    int64_t invalid_boottime_timestamp = boottime_timestamp + 15000000;
    vsync_timestamp =
        sensor_sync_ptr_->MatchTimestamp(invalid_boottime_timestamp, frame_id);
    EXPECT_EQ(vsync_timestamp, invalid_boottime_timestamp);
    int64_t invalid_frame_id = frame_id + 1;
    vsync_timestamp =
        sensor_sync_ptr_->MatchTimestamp(boottime_timestamp, invalid_frame_id);
    EXPECT_EQ(vsync_timestamp, boottime_timestamp);
  }

  void RunTest(int64_t query_interval_ms, int32_t num_query) {
    const std::chrono::milliseconds kCheckPeriod(query_interval_ms);
    for (int i = 0; i < num_query; ++i) {
      // Test get Vsync timestamps.
      std::this_thread::sleep_for(kCheckPeriod);
      std::vector<int64_t> vsync_timestamps;
      std::vector<int64_t> arrival_timestamps;
      std::vector<int64_t> frame_ids;
      std::vector<int64_t> boottime_timestamps;
      TestGetLatestNSamples(/*num_sample_to_query=*/3, &vsync_timestamps,
                            &frame_ids, &boottime_timestamps,
                            &arrival_timestamps);
      if (vsync_timestamps.size() == frame_ids.size() &&
          vsync_timestamps.size() == boottime_timestamps.size() &&
          vsync_timestamps.size() != 0) {
        int64_t input_boottime_timestamp = boottime_timestamps.back();
        int64_t input_frame_id = frame_ids.back();
        int64_t reference_vsync_timestamp = vsync_timestamps.back();
        TestSyncTimestamp(input_boottime_timestamp, reference_vsync_timestamp);
        TestMatchTimestamp(input_boottime_timestamp, input_frame_id,
                           reference_vsync_timestamp);
      }
    }
  }

  void LogDataToFile() {
    std::stringstream filename;
    filename << "camera_" << vsync_camera_id_ << "_sensor_sync_log";
    std::string output_filename;
    bool result = GenerateLogFilename(filename.str(), &output_filename);
    ASSERT_EQ(result, true) << "fail to create goog_sensor_sync test log file";
    std::cout << "save sensor_sync log to " << output_filename << "\n";
    std::ofstream ofs(output_filename);
    for (int i = 0; i < vsync_timestamps_.size(); ++i) {
      ofs << "vsync: " << vsync_timestamps_[i]
          << " boottime: " << boottime_timestamps_[i]
          << " frame_id: " << frame_ids_[i]
          << " arrival_time: " << vsync_arrival_timestamps_[i] << "\n";
    }
  }

  ::android::sp<GoogSensorSync> sensor_sync_ptr_ = nullptr;
  uint32_t vsync_camera_id_;
  const int kEventQueueSize = 5;
  std::vector<int64_t> vsync_timestamps_;
  std::vector<int64_t> vsync_arrival_timestamps_;
  std::vector<int64_t> boottime_timestamps_;
  std::vector<int64_t> frame_ids_;
};

TEST_F(GoogSensorSyncTest, TestP4BackWideCamera) {
  SetupTestEnvironment(CameraId::kPixel4BackWide);
  const int kNumQuery = 200;
  RunTest(/*query_interval_ms=*/33, /*num_query=*/kNumQuery);
  TeardownTestEnvironment();
  CheckSamplesValidy(/*num_query=*/kNumQuery);
  LogDataToFile();
}

TEST_F(GoogSensorSyncTest, TestP4BackTeleCamera) {
  SetupTestEnvironment(CameraId::kPixel4BackTele);
  const int kNumQuery = 200;
  RunTest(/*query_interval_ms=*/33, /*num_query=*/kNumQuery);
  TeardownTestEnvironment();
  CheckSamplesValidy(/*num_query=*/kNumQuery);
  LogDataToFile();
}

// Event payload on Slider/P21 does not contain boottime timestamp as in
// Pixel 4. This test checks the event period and delay to be in reasonable
// ranges. Please manually open AOSP rear camera first and run this test.
TEST_F(GoogSensorSyncTest, TestSliderBackWideCamera) {
  uint8_t camera_id_int = CameraIdMapping(CameraId::kSliderBackWide);
  ASSERT_NE(camera_id_int, static_cast<uint8_t>(CameraId::kMaxNum));
  vsync_camera_id_ = static_cast<uint32_t>(camera_id_int);

  // Create vsync sensor listener.
  sensor_sync_ptr_ = GoogSensorSync::Create(camera_id_int, kEventQueueSize);
  ASSERT_NE(sensor_sync_ptr_, nullptr);
  ASSERT_TRUE(sensor_sync_ptr_->GetSensorEnablingStatus());

  vsync_timestamps_.clear();
  // Create customized callback function to collect incoming event data.
  std::function<void(const ExtendedSensorEvent& event)> collect =
      [this](const ExtendedSensorEvent& event) {
        if (vsync_timestamps_.size() > 1) {
          int period_ms =
              (event.sensor_event.timestamp - vsync_timestamps_.back()) /
              1000'000;
          const int kDefaultFrameDurationMs =
              34;  // Assume camera runs at 30 FPS.
          EXPECT_LE(period_ms, kDefaultFrameDurationMs);
          ALOGV("period %d", period_ms);
        }
        // Most delays are < 5 ms, but sometimes it can go up to > 15.
        const int kAllowedDelayMs = 20;
        int delay_ms =
            (event.event_arrival_time_ns - event.sensor_event.timestamp) /
            1000'000;
        EXPECT_LE(delay_ms, kAllowedDelayMs);

        vsync_timestamps_.push_back(event.sensor_event.timestamp);
        ALOGV("delay %d", delay_ms);
      };
  sensor_sync_ptr_->SetEventProcessor(collect);

  // Let the camera and sensor run for 10 sec.
  const std::chrono::milliseconds kTestPeriod(10000);
  std::this_thread::sleep_for(kTestPeriod);
}

}  // namespace camera_sensor_listener
}  // namespace android
