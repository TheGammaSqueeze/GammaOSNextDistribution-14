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

#include <log/log.h>

#include "AFStateMachine.h"

namespace android {
namespace hardware {
namespace camera {
namespace provider {
namespace implementation {
namespace hw {

constexpr char kClass[] = "AFStateMachine";

using namespace std::chrono_literals;

AFStateMachine::AFStateMachine(int afDurationMs, float focused, float unfocused)
    : afDuration(1ms * afDurationMs)
    , focusedDistance(focused)
    , unfocusedDistance(unfocused)
{}

std::pair<camera_metadata_enum_android_control_af_state_t, float>
AFStateMachine::operator()(const camera_metadata_enum_android_control_af_mode_t mode,
                           const camera_metadata_enum_android_control_af_trigger_t trigger) {
    switch (mode) {
    default:
        ALOGW("%s:%s:%d unexpected mode=%d", kClass, __func__, __LINE__, mode);
        [[fallthrough]];

    case ANDROID_CONTROL_AF_MODE_OFF:
        state = ANDROID_CONTROL_AF_STATE_INACTIVE;
        return {ANDROID_CONTROL_AF_STATE_INACTIVE, unfocusedDistance};

    case ANDROID_CONTROL_AF_MODE_AUTO:
        switch (trigger) {
        default:
            ALOGW("%s:%s:%d unexpected trigger=%d", kClass, __func__, __LINE__, trigger);
            [[fallthrough]];

        case ANDROID_CONTROL_AF_TRIGGER_IDLE:
            return doAF();

        case ANDROID_CONTROL_AF_TRIGGER_START:
            state = ANDROID_CONTROL_AF_STATE_ACTIVE_SCAN;
            afLockedT = std::chrono::steady_clock::now() + afDuration;
            return {ANDROID_CONTROL_AF_STATE_ACTIVE_SCAN, unfocusedDistance};

        case ANDROID_CONTROL_AF_TRIGGER_CANCEL:
            state = ANDROID_CONTROL_AF_STATE_INACTIVE;
            return {ANDROID_CONTROL_AF_STATE_INACTIVE, unfocusedDistance};
        }
    }
}

std::pair<camera_metadata_enum_android_control_af_state_t, float>
AFStateMachine::operator()() {
    return doAF();
}

std::pair<camera_metadata_enum_android_control_af_state_t, float>
AFStateMachine::doAF() {
    switch (state) {
    default:
        ALOGW("%s:%s:%d unexpected state=%d", kClass, __func__, __LINE__, state);
        [[fallthrough]];

    case ANDROID_CONTROL_AF_STATE_INACTIVE:
        return {ANDROID_CONTROL_AF_STATE_INACTIVE, unfocusedDistance};

    case ANDROID_CONTROL_AF_STATE_ACTIVE_SCAN:
        if (std::chrono::steady_clock::now() >= afLockedT) {
            state = ANDROID_CONTROL_AF_STATE_FOCUSED_LOCKED;
            return {ANDROID_CONTROL_AF_STATE_FOCUSED_LOCKED, focusedDistance};
        } else {
            return {ANDROID_CONTROL_AF_STATE_ACTIVE_SCAN, unfocusedDistance};
        }

    case ANDROID_CONTROL_AF_STATE_FOCUSED_LOCKED:
        return {ANDROID_CONTROL_AF_STATE_FOCUSED_LOCKED, focusedDistance};
    }
}

}  // namespace hw
}  // namespace implementation
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android
