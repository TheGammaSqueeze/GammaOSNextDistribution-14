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

#include "../EventQueue.h"
#include "AODRefreshRateCalculator.h"
#include "CombinedRefreshRateCalculator.h"
#include "InstantRefreshRateCalculator.h"
#include "PeriodRefreshRateCalculator.h"
#include "RefreshRateCalculator.h"
#include "VideoFrameRateCalculator.h"

namespace android::hardware::graphics::composer {

class RefreshRateCalculatorFactory {
public:
    RefreshRateCalculatorFactory() = default;
    ~RefreshRateCalculatorFactory() = default;

    RefreshRateCalculatorFactory(const RefreshRateCalculatorFactory&) = delete;
    RefreshRateCalculatorFactory& operator=(const RefreshRateCalculatorFactory&) = delete;

    // Build InstantRefreshRateCalculator.
    std::unique_ptr<RefreshRateCalculator> BuildRefreshRateCalculator(EventQueue* eventQueue,
                                                                      int64_t maxValidPeriodNs);

    // Build VideoFrameRateCalculator
    std::unique_ptr<RefreshRateCalculator> BuildRefreshRateCalculator(
            EventQueue* eventQueue, const VideoFrameRateCalculatorParameters& params);

    // Build PeriodRefreshRateCalculator.
    std::unique_ptr<RefreshRateCalculator> BuildRefreshRateCalculator(
            EventQueue* eventQueue, const PeriodRefreshRateCalculatorParameters& params);

    // Build CombinedRefreshRateCalculator.
    std::unique_ptr<RefreshRateCalculator> BuildRefreshRateCalculator(
            EventQueue* eventQueue, const std::vector<RefreshRateCalculatorType>& types);

    // Build CombinedRefreshRateCalculator.
    std::unique_ptr<RefreshRateCalculator> BuildRefreshRateCalculator(
            std::vector<std::unique_ptr<RefreshRateCalculator>>& refreshRateCalculators,
            int minValidRefreshRate = 1, int maxValidRefreshRate = 120);

    // Build various RefreshRateCalculator with default settings.
    std::unique_ptr<RefreshRateCalculator> BuildRefreshRateCalculator(
            EventQueue* eventQueue, RefreshRateCalculatorType type);
};

} // namespace android::hardware::graphics::composer
