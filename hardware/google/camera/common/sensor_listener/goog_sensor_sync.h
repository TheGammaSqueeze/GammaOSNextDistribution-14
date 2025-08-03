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

#ifndef VENDOR_GOOGLE_CAMERA_SENSOR_LISTENER_GOOG_SENSOR_SYNC_H_
#define VENDOR_GOOGLE_CAMERA_SENSOR_LISTENER_GOOG_SENSOR_SYNC_H_

#include "goog_sensor_wrapper.h"

namespace android {
namespace camera_sensor_listener {

// Vsync sensor listener class.
// It will create a Vsync listener for specific physical camera to receive
// Vsync timestamps.
// Sample usage:
//   sp<GoogSensorSync> sync_ptr = GoogSensorSync::Create(0);
//   if (sync_ptr->GetSensorenablingStatus()) {
//     int64_t sof_vsync = sync_ptr->SyncTimestamp(sof_boottime);
//   }
class GoogSensorSync : public GoogSensorWrapper {
 public:
  // Return a StrongPointer pointing to newly created GoogSensorSync instance.
  // Input:
  //   cam_id: physical camera id associated with Vsync sensor.
  //   event_queue_size: size of event queue to hold incoming Vsync events.
  static sp<GoogSensorSync> Create(
      uint8_t cam_id, size_t event_queue_size = kDefaultEventQueueSize);

  // Destructor.
  // Destroy and free the resources of a GoogSensorSync.
  ~GoogSensorSync();

  // Get whether sensor is enabled.
  // Return true if sensor is enabled, false otherwise.
  bool GetSensorEnablingStatus() const {
    return IsEnabled();
  }

  // Get latest n vsync timestamps, boottime_timestamps, frame_ids and
  // their arrival times.
  // If output vectors are not empty, latest_n_vsync_timestamps,
  // latest_n_frame_ids, latest_n_boottime_timestamps and
  // latest_n_arrival_timestamps will be cleared first.
  // If total samples in event_deque_ is smaller than num_sample,
  // latest_n_vsync_timestamps->size(), latest_n_frame_ids->size(),
  // latest_n_boottime_timestamps->size() and
  // latest_n_arrival_timestamps->size() will be
  // equal to event_deque_.size().
  // Input:
  //   num_sample: number of latest samples to query.
  // Outputs:
  //   latest_n_vsync_timestamps: pointer of vector to hold vsync timestamps.
  //   latest_n_frame_ids: pointer of vector to hold frame ids.
  //   latest_n_boottime_timestamps: pointer of vector to hold boottime
  //     timestamps.
  //   latest_n_arrival_timestamps: pointer of vector to hold arrival times.
  //   First element of output vectors hold earliest samples.
  void GetLatestNSamples(int num_sample,
                         std::vector<int64_t>* latest_n_vsync_timestamps,
                         std::vector<int64_t>* latest_n_frame_ids,
                         std::vector<int64_t>* latest_n_boottime_timestamps,
                         std::vector<int64_t>* latest_n_arrival_timestamps) const;

  // Synchronize timestamp with sensor_sync. Vsync must be enabled when this
  // is called.
  // Inputs:
  //   timestamp: sof timestamp from clock_boottime to be synced.
  // Return:
  //   synced timestamp from Vsync sensor if successfully synced,
  //   original input timestamp if unable to sync.
  int64_t SyncTimestamp(int64_t timestamp);

  // Find event with nearing timestamp that is less than kMaxTimeDriftNs.
  // Input:
  //   timestamp: target boottime timestamp.
  // Return:
  //   ExtendedSensorEvent if found; std::nullopt if not found.
  std::optional<ExtendedSensorEvent> FindNearestEvent(int64_t timestamp);

  // Utility function, extract sof boottime timestamp and frame id from vsync
  // sensor event's data field.
  // Input:
  //   event: received vsync sensor event.
  // Outputs:
  //   frame_id: pointer to data field holding frame id.
  //   timestamp_boottime: pointer to data field holding sof timestamp from
  //   clock_boottime.
  void ExtractFrameIdAndBoottimeTimestamp(const ExtendedSensorEvent& event,
                                          int64_t* frame_id,
                                          int64_t* timestamp_boottime) const;

  // For AoC sensor service, VSYNC sensor event payload only contains an event
  // counter. The function only applies to slider, P21 or other devices with
  // AoC sensor service.
  // Input:
  //   event: received vsync sensor event.
  // Return:
  //   event counter.
  uint64_t ExtractAocEventCounter(const ExtendedSensorEvent& event);

  // Find corresponding vsync sof timestamp through exact matching with input
  // boottime sof timestamp and frame id.
  // Inputs:
  //   timestamp: sof timestamp from clock_boottime to be matched.
  //   frame_id: frame id that sof timestamp is associated with.
  // Return:
  //   matched sof timestamp from clock_vsync if both timestamp and frame_id are
  //   the same with those carried by vsync events, otherwise return the input
  //   timestamp.
  int64_t MatchTimestamp(int64_t timestamp, int64_t frame_id);

 protected:
  // Get Vsync sensor handle.
  virtual int32_t GetSensorHandle() final;

 private:
  // Constructor.
  // Create and initialize a GoogSensorSync.
  // Inputs:
  //   cam_id: Id to identify different cameras (front/back, etc.).
  //     Usually starts from 0, but need to check camera id definitions with
  //     individual device.
  //   event_queue_size: size of event queue to hold incoming Vsync events.
  GoogSensorSync(uint8_t cam_id, size_t event_queue_size);

  // Default sensor event queue size is set to 5.
  static constexpr size_t kDefaultEventQueueSize = 5;

  // Maximum tolerated delta between camera time and sensor time (in ns).
  static constexpr int64_t kMaxTimeDriftNs = 10000000;

  // Threshold in logging failed SyncTimestamp.
  static constexpr int32_t kFailureThreshold = 100;

  // Counter for number of SyncTimeStamp() called.
  int32_t sync_cnt_ = 0;

  // Out of all SyncTimeStamp(), number of timestamps that failed to sync.
  int32_t sync_failure_cnt_ = 0;

  int32_t match_cnt_ = 0;

  int32_t match_failure_cnt_ = 0;

  // The id of the camera linked to this vsync signal.
  uint8_t cam_id_;
};

}  // namespace camera_sensor_listener
}  // namespace android

#endif  // VENDOR_GOOGLE_CAMERA_SENSOR_LISTENER_GOOG_SENSOR_SYNC_H_
