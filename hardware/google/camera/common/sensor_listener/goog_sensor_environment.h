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

#ifndef VENDOR_GOOGLE_CAMERA_SENSOR_LISTENER_GOOG_SENSOR_ENVIRONMENT_H_
#define VENDOR_GOOGLE_CAMERA_SENSOR_LISTENER_GOOG_SENSOR_ENVIRONMENT_H_

#include "goog_sensor_wrapper.h"

namespace android {
namespace camera_sensor_listener {

// Supported environment sensor types:
// ::android::hardware::sensors::V1_0::SensorType::DEVICE_ORIENTATION
// ::android::hardware::sensors::V1_0::SensorType::LIGHT
// ::android::hardware::sensors::V1_0::SensorType::PROXIMITY
enum class EnvironmentSensorType {
  DEVICE_ORIENTATION = 0,
  LIGHT,
  PROXIMITY,
  TOTAL_NUM
};

// Environement sensor listener class.
// It will create a environment sensor listener whose event data type is float.
// DEVICE_ORIENTATION sensor event data is 0, 1, 2, 3:
//   0: device is in default orientation (Y axis is vertical and points up).
//   1: device is rotated 90 degrees counter-clockwise from default
//      orientation (X axis is vertical and points up).
//   2: device is rotated 180 degrees from default orientation (Y axis is
//      vertical and points down).
//   3: device is rotated 90 degrees clockwise from default orientation
//      (X axis is vertical and points down).
// LIGHT sensor event data is in SI lux units.
// PROXIMITY sensor event data is 0 or 5:
//   0 means closest object is nearby, 5 means closest object is far.
// Current supported sensor types are listed in EnvironmentSensorType.
// Sample usage:
//   sp<GoogSensorEnvironment> sensor_ptr =
//       GoogSensorEnvironment::Create(EnvironmentSensorType::LIGHT,
//                                     /*event_queue_size=*/20);
//   std::function<void(const ExtendedSensorEvent& event)> callback =
//       [](const ExtendedSensorEvent& event) {
//         // customized operations.
//      };
//   sensor_ptr->SetEventProcessor(callback);
//   if (sensor_ptr->GetSensorenablingStatus()) {
//     std::vector<int64_t> event_timestamps;
//     std::vector<float> event_data;
//     std::vector<int64_t> arrival_timestamps;
//     sensor_ptr->GetLatestNSensorEvents(
//         /*num_sample=*/5, &event_timestamps, &event_data,
//         &arrival_timestamps);
//   }
class GoogSensorEnvironment : public GoogSensorWrapper {
 public:
  // Return a StrongPointer pointing to newly created GoogSensorEnvironment
  // instance.
  // Input:
  //   environment_sensor_type: sensor type defined in enum class
  //     EnvironmentSensorType.
  //   event_queue_size: size of event queue to hold incoming sensor events.
  static sp<GoogSensorEnvironment> Create(
      EnvironmentSensorType environment_sensor_type,
      size_t event_queue_size = kDefaultEventQueueSize);

  // Destructor.
  // Destroy and free the resources of a GoogSensorEnvironment.
  ~GoogSensorEnvironment();

  // Get whether sensor is enabled.
  // Return true if sensor is enabled, false otherwise.
  bool GetSensorEnablingStatus() const {
    return IsEnabled();
  }

  // Get latest n sensor events' timestamps, event data and arrival times.
  // If output vectors are not empty, latest_n_timestamps, event_data and
  // latest_n_arrival_timestamps will be cleared first.
  // If total samples in event_deque_ is smaller than num_sample,
  // size of latest_n_timestamps, event_data and latest_n_arrival_timestamps
  // will be equal to event_deque_.size().
  // Input:
  //   num_sample: number of latest samples to query.
  // Outputs:
  //   latest_n_timestamps: pointer of vector to hold timestamps.
  //   event_data: pointer of vector to hold float type event data.
  //   latest_n_arrival_timestamps: pointer of vector to hold arrival times.
  // Event timestamps, data and arrival timestamps are listed in chronological
  // order, i.e., latest_n_timestamps[0], event_data[0] and
  // latest_n_arrival_timestamps[0] hold the earliest data.
  void GetLatestNSensorEvents(
      int num_sample, std::vector<int64_t>* latest_n_timestamps,
      std::vector<float>* event_data,
      std::vector<int64_t>* latest_n_arrival_timestamps) const;

  // Get sensor name.
  const char* GetSensorName() const {
    return GetSensorName(environment_sensor_type_);
  }

 protected:
  // Get environment sensor handle.
  virtual int32_t GetSensorHandle() final;

 private:
  // Constructor.
  // Create and initialize a GoogSensorEnvironment.
  // Inputs:
  //   environment_sensor_type: sensor type defined in enum class
  //     EnvironmentSensorType.
  //   event_queue_size: size of event queue to hold incoming sensor events.
  GoogSensorEnvironment(EnvironmentSensorType environment_sensor_type,
                        size_t event_queue_size);

  static const char* GetSensorName(EnvironmentSensorType motion_sensor_type);

  EnvironmentSensorType environment_sensor_type_;

  // Default sensor event queue size is set to 20.
  static constexpr size_t kDefaultEventQueueSize = 20;
};

}  // namespace camera_sensor_listener
}  // namespace android

#endif  // VENDOR_GOOGLE_CAMERA_SENSOR_LISTENER_GOOG_SENSOR_ENVIRONMENT_H_
