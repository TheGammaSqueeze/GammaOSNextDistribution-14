/*
 * Copyright (C) 2024 The Android Open Source Project
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

#include "Utils.h"

#include <chrono>

#include "interface/Event.h"

namespace android::hardware::graphics::composer {

int64_t getNowMs() {
    const auto t = std::chrono::high_resolution_clock::now();
    return std::chrono::duration_cast<std::chrono::milliseconds>(t.time_since_epoch()).count();
}

int64_t getNowNs() {
    const auto t = std::chrono::high_resolution_clock::now();
    return std::chrono::duration_cast<std::chrono::nanoseconds>(t.time_since_epoch()).count();
}

bool hasPresentFrameFlag(int flag, PresentFrameFlag target) {
    return flag & static_cast<int>(target);
}

void setTimedEventWithAbsoluteTime(TimedEvent& event) {
    if (event.mIsRelativeTime) {
        event.mWhenNs += getNowNs();
    }
}

} // namespace android::hardware::graphics::composer
