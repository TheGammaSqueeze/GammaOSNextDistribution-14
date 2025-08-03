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

#ifndef VENDOR_GOOGLE_CAMERA_SENSOR_LISTENER_GOOG_SENSOR_MOTION_H_
#define VENDOR_GOOGLE_CAMERA_SENSOR_LISTENER_GOOG_SENSOR_MOTION_H_

#include "goog_sensor_wrapper.h"

namespace android {
namespace camera_sensor_listener {

// Supported motion sensor types:
// ::android::hardware::sensors::V1_0::SensorType::ACCELEROMETER
// ::android::hardware::sensors::V1_0::SensorType::GRAVITY
// ::android::hardware::sensors::V1_0::SensorType::GYROSCOPE
// ::android::hardware::sensors::V1_0::SensorType::LINEAR_ACCELERATION
// ::android::hardware::sensors::V1_0::SensorType::MAGNETIC_FIELD
enum class MotionSensorType : int {
  ACCELEROMETER = 0,
  GRAVITY,
  GYROSCOPE,
  LINEAR_ACCELERATION,
  MAGNETIC_FIELD,
  TOTAL_NUM
};

// Motion sensor listener class.
// It will create a motion sensor listener whose event consists of three
// floating numbers corresponding to data in x, y, z axes. Please refer to
// Sensor Coorindate System section in
// https://developer.android.com/guide/topics/sensors/sensors_overview
// to check definition of x, y, z axis.
// ACCELEROMETER sensor event data's measure unit is m/s^2:
//   motion_vector_x: Acceleration force along the x axis (including gravity).
//   motion_vector_y: Acceleration force along the y axis (including gravity).
//   motion_vector_z: Acceleration force along the z axis (including gravity).
// GRAVITY sensor event data's measure unit is m/s^2:
//   motion_vector_x: Force of gravity along the x axis.
//   motion_vector_y: Force of gravity along the y axis.
//   motion_vector_z: Force of gravity along the z axis.
// GYROSCOPE sensor event data's measure unit is rad/s:
//   motion_vector_x: Rate of rotation around the x axis.
//   motion_vector_y: Rate of rotation around the y axis.
//   motion_vector_z: Rate of rotation around the z axis.
// LINEAR_ACCELERATION sensor event data's measure unit is m/s^2:
//   motion_vector_x: Acceleration force along the x axis (excluding gravity).
//   motion_vector_y: Acceleration force along the y axis (excluding gravity).
//   motion_vector_z: Acceleration force along the z axis (excluding gravity).
// MAGNETIC_FIELD sensor event data's measure unit is micro Tesla(uT):
//   motion_vector_x: Geomagnetic field strength along the x axis.
//   motion_vector_y: Geomagnetic field strength along the y axis.
//   motion_vector_z: Geomagnetic field strength along the z axis.
// Sample usage:
//   sp<GoogSensorMotion> sensor_ptr =
//       GoogSensorMotion::Create(MotionSensorType::GYROSCOPE,
//                                /*sampling_period_us=*/20000,
//                                /*event_queue_size=*/20);
//   std::function<void(const ExtendedSensorEvent& event)> callback =
//       [](const ExtendedSensorEvent& event) {
//         // customized operations.
//      };
//   //  Customized event callback is optional.
//   sensor_ptr->SetEventProcessor(callback);
//   if (sensor_ptr->GetSensorenablingStatus()) {
//     std::vector<int64_t> event_timestamps;
//     std::vector<float> motion_vector_x;
//     std::vector<float> motion_vector_y;
//     std::vector<float> motion_vector_z;
//     std::vector<int64_t> arrival_timestamps;
//     sensor_ptr->GetLatestNSensorEvents(
//         /*num_sample=*/5, &event_timestamps, &motion_vector_x,
//         &motion_vector_y, &motion_vector_z, &arrival_timestamps);
//   }
class GoogSensorMotion : public GoogSensorWrapper {
 public:
  // Return a StrongPointer pointing to newly created GoogSensorMotion
  // instance.
  // Inputs:
  //   motion_sensor_type: sensor type defined in enum class MotionSensorType.
  //   sampling_period_us: gyro sampling period in us (1e-6s). Sampling period
  //     should be >= 2500us as system can only support up to 400Hz frequency.
  //     If sampling_period_us < 2500us, a nullptr will be returned.
  //   event_queue_size: size of event queue to hold incoming sensor events.
  static sp<GoogSensorMotion> Create(
      MotionSensorType motion_sensor_type,
      int64_t sampling_period_us = kDefaultSamplingPeriodUs,
      size_t event_queue_size = kDefaultEventQueueSize);

  // Destructor.
  // Destroy and free the resources of a GoogSensorMotion.
  ~GoogSensorMotion();

  // Get whether sensor is enabled.
  // Return true if sensor is enabled, false otherwise.
  bool GetSensorEnablingStatus() const {
    return IsEnabled();
  }

  // Get latest n sensor events' timestamps, event data and arrival times.
  // If output vectors are not empty, they will be cleared first.
  // If total samples in event_deque_ is smaller than num_sample, size of
  // output vectors will be equal to event_deque_.size().
  // Input:
  //   num_sample: number of latest samples to query.
  // Outputs:
  //   latest_n_timestamps: pointer of vector to hold timestamps.
  //   motion_vector_x: pointer of vector to hold event data in x axis.
  //   motion_vector_y: pointer of vector to hold event data in y axis.
  //   motion_vector_z: pointer of vector to hold event data in z axis.
  //   latest_n_arrival_timestamps: pointer of vector to hold arrival times.
  // Event timestamps, data and arrival timestamps are listed in chronological
  // order, i.e., latest_n_timestamps[0], motion_vector_x[0],
  // motion_vector_y[0], motion_vector_z[0], and latest_n_arrival_timestamps[0]
  // hold the earliest data.
  void GetLatestNSensorEvents(
      int num_sample, std::vector<int64_t>* latest_n_timestamps,
      std::vector<float>* motion_vector_x, std::vector<float>* motion_vector_y,
      std::vector<float>* motion_vector_z,
      std::vector<int64_t>* latest_n_arrival_timestamps) const;

  // Query sensor events between between the range (start_time, end_time].
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
  void QuerySensorEventsBetweenTimestamps(
      int64_t start_time, int64_t end_time,
      std::vector<int64_t>* event_timestamps,
      std::vector<float>* motion_vector_x, std::vector<float>* motion_vector_y,
      std::vector<float>* motion_vector_z,
      std::vector<int64_t>* event_arrival_timestamps) const;

  const char* GetSensorName() const {
    return GetSensorName(motion_sensor_type_);
  }

 protected:
  // Get motion sensor handle.
  virtual int32_t GetSensorHandle() final;

 private:
  // Constructor.
  // Create and initialize a GoogSensorMotion.
  // Inputs:
  //   motion_sensor_type: sensor type defined in enum class MotionSensorType.
  //   sampling_period_us: gyro sampling period in us (1e-6s). Sampling period
  //     should be >= 2500us as system can only support up to 400Hz frequency.
  //   event_queue_size: size of event queue to hold incoming sensor events.
  GoogSensorMotion(MotionSensorType motion_sensor_type,
                   int64_t sampling_period_us, size_t event_queue_size);

  static const char* GetSensorName(MotionSensorType motion_sensor_type);

  MotionSensorType motion_sensor_type_;

  static constexpr size_t kDefaultEventQueueSize = 20;
  static constexpr int64_t kDefaultSamplingPeriodUs = 20000;  // = 50 Hz
  static constexpr int64_t kMinSamplingPeriodUs = 2500;       // = 400 Hz
};

}  // namespace camera_sensor_listener
}  // namespace android

#endif  // VENDOR_GOOGLE_CAMERA_SENSOR_LISTENER_GOOG_SENSOR_MOTION_H_
