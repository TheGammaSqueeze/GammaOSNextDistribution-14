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

#define LOG_TAG "goog_sensor_sync"

#include "goog_sensor_sync.h"

#include <inttypes.h>
#include <stdint.h>
#include <string.h>

#include <algorithm>
#include <cmath>
#include <deque>

#include "utils/Errors.h"
#include "utils/Log.h"
#include "utils/RefBase.h"
#include "utils/String16.h"

namespace android {
namespace camera_sensor_listener {

using ::android::frameworks::sensorservice::V1_0::ISensorManager;
using ::android::frameworks::sensorservice::V1_0::Result;
using ::android::hardware::sensors::V1_0::SensorInfo;

GoogSensorSync::GoogSensorSync(uint8_t cam_id, size_t event_queue_size)
    : GoogSensorWrapper(event_queue_size), cam_id_(cam_id) {
  ALOGI("%s %d Sensor sync camera ID: %d", __func__, __LINE__,
        static_cast<int>(cam_id_));
}

GoogSensorSync::~GoogSensorSync() {
  Disable();
  if (sync_cnt_ != 0) {
    ALOGI("%s %d Total failure/sync for camera %d: %d/%d", __func__, __LINE__,
          static_cast<int>(cam_id_), sync_failure_cnt_, sync_cnt_);
  }
  if (match_cnt_ != 0) {
    ALOGI("%s %d Total failure/match for camera %d: %d/%d", __func__, __LINE__,
          static_cast<int>(cam_id_), match_failure_cnt_, match_cnt_);
  }
}

sp<GoogSensorSync> GoogSensorSync::Create(uint8_t cam_id,
                                          size_t event_queue_size) {
  sp<GoogSensorSync> sensor_sync_ptr =
      new GoogSensorSync(cam_id, event_queue_size);
  if (sensor_sync_ptr == nullptr) {
    ALOGE("%s %d failed to create GoogSensorSync.", __func__, __LINE__);
  } else {
    status_t result = sensor_sync_ptr->Enable();
    if (result != 0) {
      ALOGE("%s %d failed to enable GoogSensorSync.", __func__, __LINE__);
    } else {
      ALOGI("%s %d successfully enabled GoogSensorSync.", __func__, __LINE__);
    }
  }
  return sensor_sync_ptr;
}

void GoogSensorSync::ExtractFrameIdAndBoottimeTimestamp(
    const ExtendedSensorEvent& event, int64_t* frame_id,
    int64_t* timestamp_boottime) const {
  // Cast away const-ness.
  float* data = const_cast<float*>(&event.sensor_event.u.data[0]);
  // Reinterpret_cast to uint64_t pointer, since sensor hal concatenates
  // two 32-bit floating point to pass 1 64-bit uint64_t data.
  uint64_t* vsync_data = reinterpret_cast<uint64_t*>(data);
  // Static_cast from uint64_t to int64_t to align with vsync timestamp format.
  *frame_id = static_cast<int64_t>(vsync_data[1]);
  *timestamp_boottime = static_cast<int64_t>(vsync_data[2]);
}

uint64_t GoogSensorSync::ExtractAocEventCounter(const ExtendedSensorEvent& event) {
  // AoC sensor Event is one uint64_t data. It can be accessed with stepCount
  // from the data union.
  return event.sensor_event.u.stepCount;
}

void GoogSensorSync::GetLatestNSamples(
    int num_sample, std::vector<int64_t>* latest_n_vsync_timestamps,
    std::vector<int64_t>* latest_n_frame_ids,
    std::vector<int64_t>* latest_n_boottime_timestamps,
    std::vector<int64_t>* latest_n_arrival_timestamps) const {
  if (latest_n_vsync_timestamps == nullptr || latest_n_frame_ids == nullptr ||
      latest_n_boottime_timestamps == nullptr ||
      latest_n_arrival_timestamps == nullptr) {
    return;
  }
  latest_n_vsync_timestamps->clear();
  latest_n_frame_ids->clear();
  latest_n_boottime_timestamps->clear();
  latest_n_arrival_timestamps->clear();

  if (num_sample < 0) {
    return;
  }
  std::lock_guard<std::mutex> el(event_buffer_lock_);
  int start_index =
      std::max(0, static_cast<int>(event_buffer_.size()) - num_sample);
  auto event = event_buffer_.begin();
  std::advance(event, start_index);
  for (; event != event_buffer_.end(); ++event) {
    latest_n_vsync_timestamps->push_back(event->sensor_event.timestamp);
    latest_n_arrival_timestamps->push_back(event->event_arrival_time_ns);
    int64_t frame_id, boottime_timestamp;
    ExtractFrameIdAndBoottimeTimestamp(*event, &frame_id, &boottime_timestamp);
    latest_n_frame_ids->push_back(frame_id);
    latest_n_boottime_timestamps->push_back(boottime_timestamp);
  }
}

int32_t GoogSensorSync::GetSensorHandle() {
  sp<ISensorManager> manager = ISensorManager::getService();
  if (manager == nullptr) {
    ALOGE("%s %d Cannot get ISensorManager", __func__, __LINE__);
    return -1;
  }
  SensorInfo info;
  bool find = false;
  static constexpr size_t kMaxVsyncNameSize = 16;
  char sensor_for_camera[kMaxVsyncNameSize];
  snprintf(sensor_for_camera, sizeof(sensor_for_camera), "camera v-sync %d",
           cam_id_);

  manager->getSensorList(
      [&info, &find, &sensor_for_camera](const auto& list, auto result) {
        if (result != Result::OK) {
          return;
        }
        for (const SensorInfo& item : list) {
          if (item.typeAsString == "com.google.sensor.camera_vsync") {
            ALOGV("%s %d Enumerating sensor %s %s %zu %zu", __func__, __LINE__,
                  item.name.c_str(), sensor_for_camera,
                  strlen(item.name.c_str()), strlen(sensor_for_camera));
            if (!strncasecmp(item.name.c_str(), sensor_for_camera,
                             strlen(sensor_for_camera))) {
              info = item;
              find = true;
              break;
            }
          }
        }
      });

  if (find) {
    ALOGI("%s %d handle for %d is found. Sensor name %s", __func__, __LINE__,
          static_cast<int>(cam_id_), info.name.c_str());
  } else {
    ALOGE("%s %d handle for %d is not found!", __func__, __LINE__,
          static_cast<int>(cam_id_));
  }
  return find ? info.sensorHandle : -1;
}

int64_t GoogSensorSync::SyncTimestamp(int64_t timestamp) {
  status_t ret;

  if (!IsEnabled()) {
    ALOGE("%s %d sensor_sync sensor is not enabled", __func__, __LINE__);
    return timestamp;
  }

  std::lock_guard<std::mutex> el(event_buffer_lock_);
  int64_t min_delta = kMaxTimeDriftNs;
  int64_t nearest_sync = timestamp;
  for (auto event : event_buffer_) {
    if (llabs(event.sensor_event.timestamp - timestamp) < min_delta) {
      min_delta = llabs(event.sensor_event.timestamp - timestamp);
      nearest_sync = event.sensor_event.timestamp;
    }
  }

  sync_cnt_++;
  if (min_delta == kMaxTimeDriftNs) {
    struct timespec res;
    clock_gettime(CLOCK_BOOTTIME, &res);
    int64_t curr_time = (int64_t)res.tv_sec * 1000000000LL + res.tv_nsec;

    ALOGV("%s %d Cannot sync timestamp for input timestamp %" PRId64
          "at CPU time %" PRId64,
          __func__, __LINE__, timestamp, curr_time);
    sync_failure_cnt_++;
    if (sync_failure_cnt_ >= kFailureThreshold) {
      ALOGW("%s %d Camera %d: out of %d camera timestamps, %d failed to sync",
            __func__, __LINE__, static_cast<int>(cam_id_), sync_cnt_,
            sync_failure_cnt_);
      sync_cnt_ = 0;
      sync_failure_cnt_ = 0;
    }
  }

  return nearest_sync;
}

std::optional<ExtendedSensorEvent> GoogSensorSync::FindNearestEvent(
    int64_t timestamp) {
  if (!IsEnabled()) {
    ALOGE("%s %d sensor_sync sensor is not enabled", __func__, __LINE__);
    return std::nullopt;
  }
  std::lock_guard<std::mutex> el(event_buffer_lock_);
  int64_t min_delta = kMaxTimeDriftNs;
  std::optional<ExtendedSensorEvent> nearest_event;
  for (auto event : event_buffer_) {
    int64_t delta = llabs(event.sensor_event.timestamp - timestamp);
    if (delta < min_delta) {
      min_delta = delta;
      nearest_event = event;
    }
  }
  return nearest_event;
}

int64_t GoogSensorSync::MatchTimestamp(int64_t timestamp, int64_t frame_id) {
  if (!IsEnabled()) {
    ALOGE("%s %d sensor_sync sensor is not enabled", __func__, __LINE__);
    return timestamp;
  }

  std::lock_guard<std::mutex> el(event_buffer_lock_);
  for (auto event : event_buffer_) {
    int64_t event_frame_id, event_timestamp;
    ExtractFrameIdAndBoottimeTimestamp(event, &event_frame_id, &event_timestamp);
    if (frame_id == event_frame_id && timestamp == event_timestamp) {
      match_cnt_++;
      return event.sensor_event.timestamp;
    }
  }
  match_cnt_++;
  match_failure_cnt_++;
  if (match_failure_cnt_ >= kFailureThreshold) {
    ALOGW("%s %d Camera %d: out of %d camera timestamps, %d failed to match",
          __func__, __LINE__, static_cast<int>(cam_id_), match_cnt_,
          match_failure_cnt_);
    match_cnt_ = 0;
    match_failure_cnt_ = 0;
  }
  return timestamp;
}

}  // namespace camera_sensor_listener
}  // namespace android
