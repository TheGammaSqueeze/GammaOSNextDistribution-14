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

#include "android-base/thread_annotations.h"
#define LOG_TAG "goog_sensor_wrapper"

#include <assert.h>
#include <inttypes.h>
#include <stdint.h>
#include <sys/time.h>
#include <utils/Log.h>
#include <utils/String16.h>
#include <utils/SystemClock.h>

#include <algorithm>
#include <cmath>

#include "goog_sensor_wrapper.h"

namespace android {
namespace camera_sensor_listener {

using ::android::frameworks::sensorservice::V1_0::IEventQueue;
using ::android::frameworks::sensorservice::V1_0::IEventQueueCallback;
using ::android::frameworks::sensorservice::V1_0::ISensorManager;
using ::android::frameworks::sensorservice::V1_0::Result;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::sensors::V1_0::Event;
using ::android::hardware::sensors::V1_0::SensorType;

// Derived IEventQueueCallbak class needed by sensor service.
class EventQueueCallback : public IEventQueueCallback {
 public:
  EventQueueCallback(wp<GoogSensorWrapper> master) {
    assert(master);
    master_ = master;
  }

  Return<void> onEvent(const Event& e) {
    sp<GoogSensorWrapper> master = master_.promote();
    if (master != nullptr) {
      master->EventCallback(e);
    }
    return Void();
  }

 private:
  wp<GoogSensorWrapper> master_;
};

GoogSensorWrapper::GoogSensorWrapper(size_t event_buffer_size,
                                     int64_t sensor_sampling_period_us)
    : event_queue_(nullptr),
      event_buffer_size_limit_(event_buffer_size),
      sensor_sampling_period_us_(sensor_sampling_period_us),
      handle_(-1),
      enabled_(false) {
  ALOGV("%s %d", __func__, __LINE__);
}

GoogSensorWrapper::~GoogSensorWrapper() { ALOGV("%s %d", __func__, __LINE__); }

status_t GoogSensorWrapper::SetEventProcessor(
    std::function<void(const ExtendedSensorEvent& event)> event_processor) {
  std::lock_guard<std::mutex> l(event_processor_lock_);
  event_processor_ = std::move(event_processor);
  return OK;
}

status_t GoogSensorWrapper::Enable() {
  status_t res = OK;
  std::lock_guard<std::mutex> l(event_queue_lock_);

  if (event_queue_ == nullptr) {
    res = InitializeEventQueueLocked();
    if (res != OK) {
      ALOGE("%s %d initializing event queue failed: %d(%s)", __func__, __LINE__,
            res, strerror(-res));
      return res;
    }
  }
  Return<Result> result =
      event_queue_->enableSensor(handle_, sensor_sampling_period_us_, 0);
  if (!result.isOk()) {
    ALOGE("%s %d enable sensor Hidl call failed: %s", __func__, __LINE__,
          result.description().c_str());
    return -1;
  }
  if (result != Result::OK) {
    ALOGE("%s %d enable sensor %s failed.", __func__, __LINE__,
          toString(result).c_str());
    return -1;
  }

  enabled_ = true;
  return res;
}

status_t GoogSensorWrapper::Disable() {
  std::lock_guard<std::mutex> l(event_queue_lock_);

  if (enabled_) {
    if (event_queue_ != nullptr) {
      Return<Result> result = event_queue_->disableSensor(handle_);
      if (!result.isOk()) {
        ALOGE("%s %d disable sensor Hidl call failed: %s", __func__, __LINE__,
              result.description().c_str());
        return -1;
      }
      if (result != Result::OK) {
        ALOGE("%s %d disable sensor %s failed.", __func__, __LINE__,
              toString(result).c_str());
        return -1;
      }
    }
    enabled_ = false;
  }
  return OK;
}

status_t GoogSensorWrapper::InitializeEventQueueLocked() {
  ALOGV("%s %d", __func__, __LINE__);

  handle_ = GetSensorHandle();
  if (handle_ < 0) {
    ALOGE("%s %d Getting sensor from Sensor Manager failed.", __func__,
          __LINE__);
    return INVALID_OPERATION;
  }

  sp<ISensorManager> manager = ISensorManager::getService();
  if (manager == nullptr) {
    ALOGE("%s %d Cannot get ISensorManager", __func__, __LINE__);
    return INVALID_OPERATION;
  }

  manager->createEventQueue(
      new EventQueueCallback(this), [this](const auto& q, auto result) {
        // The lock will be held when this synchronous callback executes
        base::ScopedLockAssertion assertion(event_queue_lock_);
        if (result != Result::OK) {
          ALOGE("%s %d Cannot create event queue", __func__, __LINE__);
          return;
        }
        event_queue_ = q;
      });

  if (event_queue_ == nullptr) {
    return INVALID_OPERATION;
  }

  return OK;
}

int GoogSensorWrapper::EventCallback(const Event& e) {
  ExtendedSensorEvent event;
  memset(&event, 0, sizeof(event));
  std::lock_guard<std::mutex> l(event_queue_lock_);
  event.sensor_event = e;
  if (event.sensor_event.sensorHandle == handle_ &&
      event.sensor_event.sensorType != SensorType::ADDITIONAL_INFO) {
    {
      std::lock_guard<std::mutex> l(event_buffer_lock_);
      if (event_buffer_.size() >= event_buffer_size_limit_) {
        event_buffer_.pop_front();
      }
      event.event_arrival_time_ns = elapsedRealtimeNano();
      event_buffer_.push_back(event);
    }

    std::lock_guard<std::mutex> el(event_processor_lock_);
    if (event_processor_ != nullptr) {
      event_processor_(event);
    }
  }

  // Return 1 to continue receiving callbacks.
  return 1;
}

}  // namespace camera_sensor_listener
}  // namespace android
