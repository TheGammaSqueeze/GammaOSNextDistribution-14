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

#define LOG_TAG "goog_sensor_environment"

#include "goog_sensor_environment.h"

#include "utils/Errors.h"
#include "utils/Log.h"
#include "utils/RefBase.h"

namespace android {
namespace camera_sensor_listener {
namespace {

using ::android::frameworks::sensorservice::V1_0::ISensorManager;
using ::android::frameworks::sensorservice::V1_0::Result;
using ::android::hardware::sensors::V1_0::SensorInfo;
using ::android::hardware::sensors::V1_0::SensorType;

SensorType GetHalSensorType(EnvironmentSensorType sensor_type) {
  switch (sensor_type) {
    case EnvironmentSensorType::DEVICE_ORIENTATION:
      return SensorType::DEVICE_ORIENTATION;
    case EnvironmentSensorType::LIGHT:
      return SensorType::LIGHT;
    case EnvironmentSensorType::PROXIMITY:
      return SensorType::PROXIMITY;
    default:
      LOG_FATAL("Unknown sensor type %d", static_cast<int>(sensor_type));
      return SensorType::DEVICE_ORIENTATION;
  }
}

}  // namespace

GoogSensorEnvironment::GoogSensorEnvironment(
    EnvironmentSensorType environment_sensor_type, size_t event_queue_size)
    : GoogSensorWrapper(event_queue_size),
      environment_sensor_type_(environment_sensor_type) {
  ALOGD("%s %d create sensor %s", __func__, __LINE__,
        GetSensorName(environment_sensor_type));
}

GoogSensorEnvironment::~GoogSensorEnvironment() {
  Disable();
  ALOGD("%s %d destroy sensor %s", __func__, __LINE__, GetSensorName());
}

sp<GoogSensorEnvironment> GoogSensorEnvironment::Create(
    EnvironmentSensorType environment_sensor_type, size_t event_queue_size) {
  // Check sensor_type validity.
  int environment_sensor_type_index = static_cast<int>(environment_sensor_type);
  if (environment_sensor_type_index >=
      static_cast<int>(EnvironmentSensorType::TOTAL_NUM)) {
    ALOGE("%s %d unsupported environment sensor type %d", __func__, __LINE__,
          environment_sensor_type_index);
    return nullptr;
  }

  // Create sensor.
  sp<GoogSensorEnvironment> sensor_ptr =
      new GoogSensorEnvironment(environment_sensor_type, event_queue_size);
  if (sensor_ptr == nullptr) {
    ALOGE("%s %d failed to create GoogSensorEnvironment for %s", __func__,
          __LINE__, GetSensorName(environment_sensor_type));
  } else {
    // Enable sensor.
    status_t result = sensor_ptr->Enable();
    if (result != 0) {
      ALOGE("%s %d failed to enable GoogSensorEnvironment for %s", __func__,
            __LINE__, sensor_ptr->GetSensorName());
    } else {
      ALOGD("%s %d successfully enabled GoogSensorEnvironment for %s", __func__,
            __LINE__, sensor_ptr->GetSensorName());
    }
  }
  return sensor_ptr;
}

void GoogSensorEnvironment::GetLatestNSensorEvents(
    int num_sample, std::vector<int64_t>* event_timestamps,
    std::vector<float>* event_data,
    std::vector<int64_t>* event_arrival_timestamps) const {
  event_timestamps->clear();
  event_data->clear();
  event_arrival_timestamps->clear();

  if (num_sample < 0) {
    return;
  }
  std::lock_guard<std::mutex> l(event_buffer_lock_);
  int start_index =
      std::max(0, static_cast<int>(event_buffer_.size()) - num_sample);
  auto event = event_buffer_.begin();
  std::advance(event, start_index);
  for (; event != event_buffer_.end(); ++event) {
    event_timestamps->push_back(event->sensor_event.timestamp);
    event_data->push_back(event->sensor_event.u.scalar);
    event_arrival_timestamps->push_back(event->event_arrival_time_ns);
    if (event_arrival_timestamps->size() >= num_sample) {
      break;
    }
  }
}

int32_t GoogSensorEnvironment::GetSensorHandle() {
  sp<ISensorManager> manager = ISensorManager::getService();
  if (manager == nullptr) {
    ALOGE("%s %d Cannot get ISensorManager for sensor %s", __func__, __LINE__,
          GetSensorName());
    return -1;
  }
  bool found = false;
  SensorInfo sensor;
  manager->getDefaultSensor(GetHalSensorType(environment_sensor_type_),
                            [&sensor, &found](const auto& info, auto result) {
                              if (result != Result::OK) {
                                ALOGE("%s %d Cannot find default sensor",
                                      __func__, __LINE__);
                                return;
                              }
                              sensor = info;
                              found = true;
                            });

  if (found) {
    ALOGD("%s %d handle for %s is found.", __func__, __LINE__, GetSensorName());
  } else {
    ALOGE("%s %d handle for %s is not found!", __func__, __LINE__,
          GetSensorName());
  }
  return found ? sensor.sensorHandle : -1;
}

const char* GoogSensorEnvironment::GetSensorName(
    EnvironmentSensorType sensor_type) {
  static constexpr const char* kSensorNames[] = {"device_orientation", "light",
                                                 "proximity"};
  return kSensorNames[static_cast<int>(sensor_type)];
}

}  // namespace camera_sensor_listener
}  // namespace android