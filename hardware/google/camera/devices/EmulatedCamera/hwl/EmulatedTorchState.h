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

#ifndef HW_EMULATOR_TORCH_STATE_H
#define HW_EMULATOR_TORCH_STATE_H

#include <hwl_types.h>

#include <mutex>
#include <queue>

namespace android {

using android::google_camera_hal::HwlTorchModeStatusChangeFunc;
using android::google_camera_hal::TorchMode;
using android::google_camera_hal::TorchModeStatus;

class EmulatedTorchState {
 public:
  EmulatedTorchState(uint32_t camera_id, HwlTorchModeStatusChangeFunc torch_cb)
      : camera_id_(camera_id), torch_cb_(torch_cb) {
  }

  status_t SetTorchMode(TorchMode mode);
  status_t TurnOnTorchWithStrengthLevel(int32_t torch_strength);

  void AcquireFlashHw();
  void ReleaseFlashHw();
  int32_t GetTorchStrengthLevel();
  void InitializeTorchDefaultLevel(int32_t default_level);
  void InitializeSupportTorchStrengthLevel(bool is_torch_strength_control_supported);

 private:
  std::mutex mutex_;

  uint32_t camera_id_;
  HwlTorchModeStatusChangeFunc torch_cb_;
  bool camera_open_ = false;
  TorchModeStatus torch_status_;
  int32_t new_torch_strength_level_ = 0;
  bool support_torch_strength_control_ = false;
  int32_t default_level_ = 0;

  EmulatedTorchState(const EmulatedTorchState&) = delete;
  EmulatedTorchState& operator=(const EmulatedTorchState&) = delete;
};

}  // namespace android

#endif
