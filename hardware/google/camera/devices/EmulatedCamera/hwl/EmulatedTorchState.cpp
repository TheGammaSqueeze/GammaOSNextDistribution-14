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

//#define LOG_NDEBUG 0
#define LOG_TAG "EmulatedTorchState"
#include "EmulatedTorchState.h"

#include <log/log.h>

namespace android {

using android::google_camera_hal::TorchModeStatus;

status_t EmulatedTorchState::SetTorchMode(TorchMode mode) {
  std::lock_guard<std::mutex> lock(mutex_);
  if (camera_open_) {
    ALOGE("%s: Camera device open, torch cannot be controlled using this API!",
          __FUNCTION__);
    return UNKNOWN_ERROR;
  }

  torch_status_ = (mode == TorchMode::kOn) ? TorchModeStatus::kAvailableOn : TorchModeStatus::kAvailableOff;
  if (mode == TorchMode::kOff && support_torch_strength_control_) {
    new_torch_strength_level_ = default_level_;
    ALOGV("%s: Turning torch OFF so reset the torch strength to default level:%d",
          __FUNCTION__, default_level_);
  }

  torch_cb_(camera_id_, (mode == TorchMode::kOn)
                            ? TorchModeStatus::kAvailableOn
                            : TorchModeStatus::kAvailableOff);
  return OK;
}

status_t EmulatedTorchState::TurnOnTorchWithStrengthLevel(int32_t torch_strength) {
  std::lock_guard<std::mutex> lock(mutex_);
  if (camera_open_) {
    ALOGE("%s: Camera device open, torch cannot be controlled using this API!",
          __FUNCTION__);
    return UNKNOWN_ERROR;
  }
  new_torch_strength_level_ = torch_strength;
  // If the torch mode is OFF and device is available, torch will be turned ON.
  // torch_strength value should be greater than 1.
  if (torch_status_ != TorchModeStatus::kAvailableOn && torch_strength > 1) {
    torch_status_ = TorchModeStatus::kAvailableOn;
    ALOGV("Changed the torch status to: %d", torch_status_);
    torch_cb_(camera_id_, TorchModeStatus::kAvailableOn);
  }

  ALOGV("%s: Torch strength level successfully set to %d", __FUNCTION__, torch_strength);

  return OK;
}

void EmulatedTorchState::AcquireFlashHw() {
  std::lock_guard<std::mutex> lock(mutex_);
  camera_open_ = true;
  torch_cb_(camera_id_, TorchModeStatus::kNotAvailable);
}

void EmulatedTorchState::ReleaseFlashHw() {
  std::lock_guard<std::mutex> lock(mutex_);
  camera_open_ = false;
  torch_cb_(camera_id_, TorchModeStatus::kAvailableOff);
}

int32_t EmulatedTorchState::GetTorchStrengthLevel() {
  std::lock_guard<std::mutex> lock(mutex_);
  return new_torch_strength_level_;
}

void EmulatedTorchState::InitializeTorchDefaultLevel(int32_t default_level) {
  std::lock_guard<std::mutex> lock(mutex_);
  default_level_ = default_level;
}

void EmulatedTorchState::InitializeSupportTorchStrengthLevel(bool is_torch_strength_control_supported) {
  std::lock_guard<std::mutex> lock(mutex_);
  support_torch_strength_control_ = is_torch_strength_control_supported;
}


}  // namespace android
