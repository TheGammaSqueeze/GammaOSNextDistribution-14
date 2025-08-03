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

#include "RefreshRateCalculatorFactory.h"

namespace android::hardware::graphics::composer {

// Build InstantRefreshRateCalculator.
std::unique_ptr<RefreshRateCalculator> RefreshRateCalculatorFactory::BuildRefreshRateCalculator(
        EventQueue* eventQueue, int64_t maxValidPeriodNs) {
    return std::make_unique<InstantRefreshRateCalculator>(eventQueue, maxValidPeriodNs);
}

// Build VideoFrameRateCalculator
std::unique_ptr<RefreshRateCalculator> RefreshRateCalculatorFactory::BuildRefreshRateCalculator(
        EventQueue* eventQueue, const VideoFrameRateCalculatorParameters& params) {
    return std::make_unique<VideoFrameRateCalculator>(eventQueue, params);
}

// Build PeriodRefreshRateCalculator.
std::unique_ptr<RefreshRateCalculator> RefreshRateCalculatorFactory::BuildRefreshRateCalculator(
        EventQueue* eventQueue, const PeriodRefreshRateCalculatorParameters& params) {
    return std::make_unique<PeriodRefreshRateCalculator>(eventQueue, params);
}

// Build CombinedRefreshRateCalculator.
std::unique_ptr<RefreshRateCalculator> RefreshRateCalculatorFactory::BuildRefreshRateCalculator(
        EventQueue* eventQueue, const std::vector<RefreshRateCalculatorType>& types) {
    std::vector<std::unique_ptr<RefreshRateCalculator>> refreshRateCalculators;
    for (const auto& type : types) {
        refreshRateCalculators.emplace_back(BuildRefreshRateCalculator(eventQueue, type));
    }
    return std::make_unique<CombinedRefreshRateCalculator>(refreshRateCalculators);
}

// Build CombinedRefreshRateCalculator.
std::unique_ptr<RefreshRateCalculator> RefreshRateCalculatorFactory::BuildRefreshRateCalculator(
        std::vector<std::unique_ptr<RefreshRateCalculator>>& refreshRateCalculators,
        int minValidRefreshRate, int maxValidRefreshRate) {
    return std::make_unique<CombinedRefreshRateCalculator>(refreshRateCalculators,
                                                           minValidRefreshRate,
                                                           maxValidRefreshRate);
}

// Build various RefreshRateCalculator with default settings.
std::unique_ptr<RefreshRateCalculator> RefreshRateCalculatorFactory::BuildRefreshRateCalculator(
        EventQueue* eventQueue, RefreshRateCalculatorType type) {
    switch (type) {
        case RefreshRateCalculatorType::kAod: {
            return std::make_unique<AODRefreshRateCalculator>(eventQueue);
        }
        case RefreshRateCalculatorType::kInstant: {
            return std::make_unique<InstantRefreshRateCalculator>(eventQueue);
        }
        case RefreshRateCalculatorType::kPeriodical: {
            return std::make_unique<PeriodRefreshRateCalculator>(eventQueue);
        }
        case RefreshRateCalculatorType::kVideoPlayback: {
            return std::make_unique<VideoFrameRateCalculator>(eventQueue);
        }
        case RefreshRateCalculatorType::kCombined: {
            std::vector<RefreshRateCalculatorType> types{RefreshRateCalculatorType::kVideoPlayback,
                                                         RefreshRateCalculatorType::kPeriodical};
            return BuildRefreshRateCalculator(eventQueue, types);
        }
        default:
            return nullptr;
    };
}

} // namespace android::hardware::graphics::composer
