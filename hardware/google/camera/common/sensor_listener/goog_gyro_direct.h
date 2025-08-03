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

#ifndef VENDOR_GOOGLE_CAMERA_SENSOR_LISTENER_GOOG_GYRO_DIRECT_H_
#define VENDOR_GOOGLE_CAMERA_SENSOR_LISTENER_GOOG_GYRO_DIRECT_H_

#include <android/frameworks/sensorservice/1.0/ISensorManager.h>
#include <android/hardware/sensors/1.0/types.h>

#include "goog_gralloc_wrapper.h"

namespace android {
namespace camera_sensor_listener {

// GoogGryoDirect class will create gyro direct channel listener.
// It fetches gyro events (timestamp, azimuth, pitch, roll, fetching timestamp)
// from a shared buffer.
// Sample usage:
//   std::unique_ptr<GoogGyroDirect> direct_ptr_ = GoogGyroDirect::Create(
//       /*gyro_sampling_period_us=*/5000,
//       /*event_queue_size=*/20);
//   if (direct_ptr->GetSensorEnablingStatus()) {
//     std::vector<int64_t> event_timestamps;
//     std::vector<float> motion_vector_x;
//     std::vector<float> motion_vector_y;
//     std::vector<float> motion_vector_z;
//     std::vector<int64_t> arrival_timestamps;
//     // Get all available gyro events.
//     direct_ptr->QueryGyroEventsBetweenTimestamps(
//         /*start_time=*/0,
//         /*end_time=*/LLONG_MAX,
//         &event_timestamps,
//         &motion_vector_x,
//         &motion_vector_y,
//         &motion_vector_z,
//         &arrival_timestamps);
//   }
class GoogGyroDirect {
 public:
  // Create a new instance of GoogGyroDirect.
  // Inputs:
  //   gyro_sampling_period_us: gyro sampling period in us (1e-6s).
  //     Direct channel only supports following rates:
  //       NORMAL,          // nominal 50Hz
  //       FAST,            // nominal 200Hz
  //       VERY_FAST,       // nominal 800Hz
  //     gyro_sampling_period_us will be converted to closest rate level, i.e.,
  //     sampling_frequency = 1e6 / gyro_sampling_period_us,
  //     sampling_frequency < 110Hz -> RateLevel:NORMAL,
  //     110Hz <= sampling_frequency < 440Hz -> RateLevel:FAST,
  //     sampling_frequency >= 440Hz -> RateLevel::VERY_FAST.
  //     Default sampling period is 5000us (200Hz -> RateLevel:FAST).
  //  event_queue_size: size of event queue to hold incoming sensor events.
  //    Default value is set to 20.
  static std::unique_ptr<GoogGyroDirect> Create(
      int64_t gyro_sampling_period_us = kDefaultSamplingPeriodUs,
      size_t event_queue_size = kDefaultEventQueueSize);

  // Destructor.
  // Destroy and free the resources of a GoogGyroDirect.
  ~GoogGyroDirect();

  // Get whether gyro direct channel is enabled.
  // Return true if gyro direct channel is enabled, false otherwise.
  bool GetSensorEnablingStatus() const {
    return gyro_direct_enabled_;
  }

  // Query gyro events between between the range (start_time, end_time].
  // Inputs:
  //   start_time: queried events' timestamps must be > start_time.
  //   end_time: queried events' timestamps must be <= end_time.
  // Outputs:
  //   event_timestamps: pointer of vector to hold queried events' timestamps.
  //   motion_vector_x: pointer of vector to hold queried events' x axis data.
  //   motion_vector_y: pointer of vector to hold queried events' y axis data.
  //   motion_vector_z: pointer of vector to hold queried events' z axis data.
  //   event_arrival_timestamps: pointer of vector to hold arrival times.
  // Event timestamps, data and arrival timestamps are listed in chronological
  // order, i.e., event_timestamps[0], motion_vector_x[0],
  // motion_vector_y[0], motion_vector_z[0], and event_arrival_timestamps[0]
  // hold the earliest data.
  // motion_vector_x, motion_vector_y, motion_vector_z are azimuth, pitch, roll
  // respectively.
  void QueryGyroEventsBetweenTimestamps(
      int64_t start_time, int64_t end_time,
      std::vector<int64_t>* event_timestamps,
      std::vector<float>* motion_vector_x, std::vector<float>* motion_vector_y,
      std::vector<float>* motion_vector_z,
      std::vector<int64_t>* event_arrival_timestamps) const;

  // Enable GoogGyroDirect to query events from direct channel.
  // Return 0 on success.
  status_t EnableDirectChannel();

  // Disable GoogGyroDirect.
  // Return 0 on success.
  status_t DisableDirectChannel();

 private:
  // Constructor.
  // Create and initialize GoogGyroDirect.
  // Inputs:
  //   rate_level: gyro sampling rate level.
  //     See definition at android/hardware/interfaces/sensors/1.0/types.hal.
  //   gyro_direct_buf_length: shared buffer length to hold gyro events.
  GoogGyroDirect(::android::hardware::sensors::V1_0::RateLevel rate_level,
                 size_t gyro_direct_buf_length);

  // Whether gyro direct channel buffer is initialized.
  bool gyro_direct_initialized_;

  // Whether gyro direct channel is enabled.
  bool gyro_direct_enabled_;

  // Gyro events sampling rate level.
  // See definition at android/hardware/interfaces/sensors/1.0/types.hal.
  ::android::hardware::sensors::V1_0::RateLevel gyro_direct_rate_level_;

  // Shared buffer length to hold gyro events.
  size_t gyro_direct_buf_length_;

  // Unique pointer to GoogGrallocWrapper.
  std::unique_ptr<GoogGrallocWrapper> goog_gralloc_wrapper_ptr_;

  // Strong pointer to IDirectReportChannel.
  sp<::android::frameworks::sensorservice::V1_0::IDirectReportChannel>
      gyro_direct_channel_;

  // Const pointer to direct channel native buffer handle.
  const native_handle_t* gyro_direct_channel_native_buf_handle_;

  // Gyro direct channel address.
  void* gyro_direct_channel_addr_;

  // Gyro sensor info.
  ::android::hardware::sensors::V1_0::SensorInfo sensor_info_;

  // Default sensor event queue size is set to 20.
  static constexpr size_t kDefaultEventQueueSize = 20;

  // Default sensor sampling period is set to 5000us(1e-6s), i.e. 200Hz.
  static constexpr int64_t kDefaultSamplingPeriodUs = 5000;
};

}  // namespace camera_sensor_listener
}  // namespace android

#endif  // VENDOR_GOOGLE_CAMERA_SENSOR_LISTENER_GOOG_GYRO_DIRECT_H_
