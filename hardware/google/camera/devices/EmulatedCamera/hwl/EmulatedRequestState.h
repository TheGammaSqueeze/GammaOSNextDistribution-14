/*
 * Copyright (C) 2019 The Android Open Source Project
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

#ifndef EMULATOR_CAMERA_HAL_HWL_REQUEST_STATE_H
#define EMULATOR_CAMERA_HAL_HWL_REQUEST_STATE_H

#include <mutex>
#include <unordered_map>

#include "EmulatedCameraDeviceInfo.h"
#include "EmulatedSensor.h"
#include "hwl_types.h"

namespace android {

using google_camera_hal::HalCameraMetadata;
using google_camera_hal::HalStream;
using google_camera_hal::HwlPipelineCallback;
using google_camera_hal::HwlPipelineRequest;
using google_camera_hal::kTemplateCount;
using google_camera_hal::RequestTemplate;

struct PendingRequest;

class EmulatedRequestState {
 public:
  EmulatedRequestState(uint32_t camera_id) : camera_id_(camera_id) {
  }
  virtual ~EmulatedRequestState() {
  }

  status_t Initialize(std::unique_ptr<EmulatedCameraDeviceInfo> static_meta);

  status_t GetDefaultRequest(
      RequestTemplate type,
      std::unique_ptr<HalCameraMetadata>* default_settings /*out*/);

  std::unique_ptr<HwlPipelineResult> InitializeResult(uint32_t pipeline_id,
                                                      uint32_t frame_number);
  std::unique_ptr<HwlPipelineResult> InitializePartialResult(
      uint32_t pipeline_id, uint32_t frame_number);

  status_t InitializeSensorSettings(
      std::unique_ptr<HalCameraMetadata> request_settings,
      uint32_t override_frame_number,
      EmulatedSensor::SensorSettings* sensor_settings /*out*/);

  uint32_t GetPartialResultCount(bool is_partial_result);

 private:
  status_t ProcessAE();
  status_t ProcessAF();
  status_t ProcessAWB();
  status_t DoFakeAE();
  status_t CompensateAE();
  status_t Update3AMeteringRegion(uint32_t tag,
                                  const HalCameraMetadata& settings,
                                  int32_t* region /*out*/);

  std::mutex request_state_mutex_;
  std::unique_ptr<HalCameraMetadata> request_settings_;

  // Supported capabilities and features
  std::unique_ptr<EmulatedCameraDeviceInfo> device_info_;

  size_t ae_frame_counter_ = 0;
  const size_t kAEPrecaptureMinFrames = 10;
  // Fake AE related constants
  const float kExposureTrackRate = .2f;  // This is the rate at which the fake
                                         // AE will reach the calculated target
  const size_t kStableAeMaxFrames =
      100;  // The number of frames the fake AE will stay in converged state
  // After fake AE switches to state searching the exposure
  // time will wander randomly in region defined by min/max below.
  const float kExposureWanderMin = -2;
  const float kExposureWanderMax = 1;
  const uint32_t kAETargetThreshold =
      10;  // Defines a threshold for reaching the AE target
  nsecs_t ae_target_exposure_time_ = EmulatedSensor::kDefaultExposureTime;
  nsecs_t current_exposure_time_ = EmulatedSensor::kDefaultExposureTime;
  bool af_mode_changed_ = false;
  uint32_t settings_overriding_frame_number_ = 0;

  unsigned int rand_seed_ = 1;

  uint32_t camera_id_;

  EmulatedRequestState(const EmulatedRequestState&) = delete;
  EmulatedRequestState& operator=(const EmulatedRequestState&) = delete;
};

}  // namespace android

#endif  // EMULATOR_CAMERA_HAL_HWL_REQUEST_STATE_H
