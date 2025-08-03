/*
 * Copyright (C) 2023 The Android Open Source Project
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

#pragma once

#include <chrono>
#include <utility>
#include <system/camera_metadata.h>

namespace android {
namespace hardware {
namespace camera {
namespace provider {
namespace implementation {
namespace hw {

struct AFStateMachine {
    AFStateMachine(int afDurationMs, float focusedDistance, float unfocusedDistance);

    std::pair<camera_metadata_enum_android_control_af_state_t, float>
        operator()(camera_metadata_enum_android_control_af_mode_t,
                   camera_metadata_enum_android_control_af_trigger_t);

    std::pair<camera_metadata_enum_android_control_af_state_t, float> operator()();
    std::pair<camera_metadata_enum_android_control_af_state_t, float> doAF();

    camera_metadata_enum_android_control_af_state_t state = ANDROID_CONTROL_AF_STATE_INACTIVE;
    std::chrono::steady_clock::time_point afLockedT;
    std::chrono::steady_clock::duration afDuration;
    float focusedDistance;
    float unfocusedDistance;
};

}  // namespace hw
}  // namespace implementation
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android
