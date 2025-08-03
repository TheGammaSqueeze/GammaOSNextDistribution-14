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

#pragma once

#include <chrono>
#include <cmath>
#include <cstdint>

#include "interface/Event.h"

namespace android::hardware::graphics::composer {

struct TimedEvent;

enum PresentFrameFlag {
    kHasRefreshRateIndicatorLayer = (1 << 0),
    kIsYuv = (1 << 1),
    kPresentingWhenDoze = (1 << 2),
};

template <typename T, typename = std::enable_if_t<std::is_integral_v<T>>>
T roundDivide(T divident, T divisor) {
    if (divident < 0 || divisor <= 0) {
        return 0;
    }
    return (divident + (divisor / 2)) / divisor;
}

template <typename T, typename = std::enable_if_t<std::is_integral_v<T>>>
T durationNsToFreq(T durationNs) {
    auto res = roundDivide(std::nano::den, static_cast<int64_t>(durationNs));
    return static_cast<T>(res);
}

template <typename T, typename = std::enable_if_t<std::is_integral_v<T>>>
T freqToDurationNs(T freq) {
    auto res = roundDivide(std::nano::den, static_cast<int64_t>(freq));
    return static_cast<T>(res);
}

int64_t getNowMs();
int64_t getNowNs();

bool hasPresentFrameFlag(int flag, PresentFrameFlag target);

void setTimedEventWithAbsoluteTime(TimedEvent& event);

} // namespace android::hardware::graphics::composer
