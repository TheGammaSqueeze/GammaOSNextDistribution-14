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

#define LOG_TAG "goog_sensor_motion"

#include "goog_sensor_motion.h"

#include <cinttypes>

#include "utils/Errors.h"
#include "utils/Log.h"
#include "utils/Mutex.h"
#include "utils/RefBase.h"
#include "utils/String16.h"

namespace android {
namespace camera_sensor_listener {
namespace {

using ::android::frameworks::sensorservice::V1_0::ISensorManager;
using ::android::frameworks::sensorservice::V1_0::Result;
using ::android::hardware::sensors::V1_0::SensorInfo;
using ::android::hardware::sensors::V1_0::SensorType;

SensorType GetHalSensorType(MotionSensorType sensor_type) {
  switch (sensor_type) {
    case MotionSensorType::ACCELEROMETER:
      return SensorType::ACCELEROMETER;
    case MotionSensorType::GRAVITY:
      return SensorType::GRAVITY;
    case MotionSensorType::GYROSCOPE:
      return SensorType::GYROSCOPE;
    case MotionSensorType::LINEAR_ACCELERATION:
      return SensorType::LINEAR_ACCELERATION;
    case MotionSensorType::MAGNETIC_FIELD:
      return SensorType::MAGNETIC_FIELD;
    default:
      LOG_FATAL("Unknown sensor type %d", static_cast<int>(sensor_type));
      return SensorType::ACCELEROMETER;
  }
}

}  // namespace

GoogSensorMotion::GoogSensorMotion(MotionSensorType motion_sensor_type,
                                   int64_t sampling_period_us,
                                   size_t event_queue_size)
    : GoogSensorWrapper(event_queue_size, sampling_period_us),
      motion_sensor_type_(motion_sensor_type) {
  ALOGD("%s %d create sensor %s", __func__, __LINE__,
        GetSensorName(motion_sensor_type));
}

GoogSensorMotion::~GoogSensorMotion() {
  Disable();
  ALOGD("%s %d destroy sensor %s", __func__, __LINE__, GetSensorName());
}

sp<GoogSensorMotion> GoogSensorMotion::Create(MotionSensorType motion_sensor_type,
                                              int64_t sampling_period_us,
                                              size_t event_queue_size) {
  // Check sensor_type validity.
  int motion_sensor_type_index = static_cast<int>(motion_sensor_type);
  if (motion_sensor_type_index >= static_cast<int>(MotionSensorType::TOTAL_NUM)) {
    ALOGE("%s %d unsupported motion sensor type %d", __func__, __LINE__,
          motion_sensor_type_index);
    return nullptr;
  }
  // Check sampling period validity.
  if (sampling_period_us < kMinSamplingPeriodUs) {
    ALOGE("%s %d sampling period %" PRId64 "us is too small %s", __func__,
          __LINE__, sampling_period_us,
          "supported smallest sampling period is 2500us");
    return nullptr;
  }

  // Create sensor.
  sp<GoogSensorMotion> sensor_ptr = new GoogSensorMotion(
      motion_sensor_type, sampling_period_us, event_queue_size);
  if (sensor_ptr == nullptr) {
    ALOGE("%s %d failed to create GoogSensorMotion for %s", __func__, __LINE__,
          GetSensorName(motion_sensor_type));
  } else {
    // Enable sensor.
    status_t result = sensor_ptr->Enable();
    if (result != 0) {
      ALOGE("%s %d failed to enable GoogSensorMotion for %s", __func__,
            __LINE__, sensor_ptr->GetSensorName());
    } else {
      ALOGD("%s %d successfully enabled GoogSensorMotion for %s", __func__,
            __LINE__, sensor_ptr->GetSensorName());
    }
  }
  return sensor_ptr;
}

void GoogSensorMotion::GetLatestNSensorEvents(
    int num_sample, std::vector<int64_t>* event_timestamps,
    std::vector<float>* motion_vector_x, std::vector<float>* motion_vector_y,
    std::vector<float>* motion_vector_z,
    std::vector<int64_t>* event_arrival_timestamps) const {
  event_timestamps->clear();
  motion_vector_x->clear();
  motion_vector_y->clear();
  motion_vector_z->clear();
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
    motion_vector_x->push_back(event->sensor_event.u.vec3.x);
    motion_vector_y->push_back(event->sensor_event.u.vec3.y);
    motion_vector_z->push_back(event->sensor_event.u.vec3.z);
    event_arrival_timestamps->push_back(event->event_arrival_time_ns);
  }
}

void GoogSensorMotion::QuerySensorEventsBetweenTimestamps(
    int64_t start_time, int64_t end_time,
    std::vector<int64_t>* event_timestamps, std::vector<float>* motion_vector_x,
    std::vector<float>* motion_vector_y, std::vector<float>* motion_vector_z,
    std::vector<int64_t>* event_arrival_timestamps) const {
  event_timestamps->clear();
  motion_vector_x->clear();
  motion_vector_y->clear();
  motion_vector_z->clear();
  event_arrival_timestamps->clear();

  std::lock_guard<std::mutex> l(event_buffer_lock_);

  event_timestamps->reserve(event_buffer_.size());
  motion_vector_x->reserve(event_buffer_.size());
  motion_vector_y->reserve(event_buffer_.size());
  motion_vector_z->reserve(event_buffer_.size());
  event_arrival_timestamps->reserve(event_buffer_.size());

  for (const auto& event : event_buffer_) {
    int64_t event_time = event.sensor_event.timestamp;
    if (event_time <= start_time || event_time > end_time) {
      continue;
    }
    event_timestamps->push_back(event_time);
    motion_vector_x->push_back(event.sensor_event.u.vec3.x);
    motion_vector_y->push_back(event.sensor_event.u.vec3.y);
    motion_vector_z->push_back(event.sensor_event.u.vec3.z);
    event_arrival_timestamps->push_back(event.event_arrival_time_ns);
  }
}

int32_t GoogSensorMotion::GetSensorHandle() {
  sp<ISensorManager> manager = ISensorManager::getService();
  if (manager == nullptr) {
    ALOGE("%s %d Cannot get ISensorManager for sensor %s", __func__, __LINE__,
          GetSensorName());
    return -1;
  }
  bool found = false;
  SensorInfo sensor;
  manager->getDefaultSensor(GetHalSensorType(motion_sensor_type_),
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

const char* GoogSensorMotion::GetSensorName(MotionSensorType sensor_type) {
  static constexpr const char* kSensorNames[] = {
      "accelerometer", "gravity", "gyroscope", "linear_acceleration",
      "magnetic_field"};
  return kSensorNames[static_cast<int>(sensor_type)];
}

}  // namespace camera_sensor_listener
}  // namespace android