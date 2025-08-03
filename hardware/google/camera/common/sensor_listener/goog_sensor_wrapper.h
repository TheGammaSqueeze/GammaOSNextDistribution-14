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

#ifndef VENDOR_GOOGLE_CAMERA_SENSOR_LISTENER_GOOG_SENSOR_WRAPPER_H_
#define VENDOR_GOOGLE_CAMERA_SENSOR_LISTENER_GOOG_SENSOR_WRAPPER_H_

#include <android-base/thread_annotations.h>
#include <android/frameworks/sensorservice/1.0/ISensorManager.h>
#include <android/frameworks/sensorservice/1.0/types.h>

#include <deque>
#include <functional>
#include <mutex>

#include "utils/Errors.h"
#include "utils/RefBase.h"

namespace android {
namespace camera_sensor_listener {

struct ExtendedSensorEvent {
  // Actual sensor event data.
  ::android::hardware::sensors::V1_0::Event sensor_event;
  // Event arrival time, i.e., time of callback being triggered.
  int64_t event_arrival_time_ns;
};

class GoogSensorWrapper : public virtual RefBase {
 public:
  virtual ~GoogSensorWrapper();

  // Set user-defined sensor event callback function.
  // When sensor event arrives, event_processor will be invoked to perform
  // user-defined callback operations.
  // It should be called before GoogleSensorWrapper::Enable, otherwise it
  // won't take effect.
  status_t SetEventProcessor(
      std::function<void(const ExtendedSensorEvent& event)> event_processor);

  // Enables the sensor. When object is created, sensor is disabled by default.
  // Returns 0 on success.
  status_t Enable();

  // Disables the sensor. Returns 0 on success.
  status_t Disable();

  // True if the sensor is currently enabled.
  bool IsEnabled() const { return enabled_; }

 protected:
  // Input:
  //   event_queue_size: sets size of the event queue.
  //   sensor_sampling_period_us: sets sensor sampling period in us (1e-6s).
  //     Default sampling rate is set to 5ms. However, sensor_sampling_period_us
  //     has no effect on on_change type sensors like Vsync or Light.
  GoogSensorWrapper(size_t event_queue_size,
                    int64_t sensor_sampling_period_us = 5000);

  // Virtual function to get different sensor handler, e.g., gyro handler.
  virtual int32_t GetSensorHandle() = 0;

  // Buffer of the most recent events. Oldest in the front.
  std::deque<ExtendedSensorEvent> event_buffer_ GUARDED_BY(event_buffer_lock_);

  // Lock protecting event_buffer_.
  mutable std::mutex event_buffer_lock_;

 private:
  // Event callback function.
  // When invoked, it will enqueue Event e to event_deque_, and further invoke
  // user-defined callback function event_processor_.
  int EventCallback(const ::android::hardware::sensors::V1_0::Event& e);

  // Initialize sensor handler and set event_queue_.
  status_t InitializeEventQueueLocked()
      EXCLUSIVE_LOCKS_REQUIRED(event_queue_lock_);

  // Strong pointer to IEventQueue allocated by sensor service.
  sp<::android::frameworks::sensorservice::V1_0::IEventQueue> event_queue_
      GUARDED_BY(event_queue_lock_);

  // User-defined callback functor invoked when sensor event arrives.
  std::function<void(const ExtendedSensorEvent& event)> event_processor_
      GUARDED_BY(event_processor_lock_);

  // Lock protecting event_queue_.
  mutable std::mutex event_queue_lock_;

  // Lock protecting event_processor_.
  mutable std::mutex event_processor_lock_;

  // Size limit for the event buffer.
  size_t event_buffer_size_limit_;

  // Sampling period to read sensor events.
  int64_t sensor_sampling_period_us_;

  // Sensor handler.
  int handle_;

  // Whether sensor is enabled.
  bool enabled_;

  friend class EventQueueCallback;
};

}  // namespace camera_sensor_listener
}  // namespace android

#endif  // VENDOR_GOOGLE_CAMERA_SENSOR_LISTENER_GOOG_SENSOR_WRAPPER_H_
