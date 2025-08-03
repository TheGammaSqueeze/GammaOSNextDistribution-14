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

#include <memory>

#include "../RefreshRateCalculator/VideoFrameRateCalculator.h"
#include "../interface/DisplayContextProvider.h"
#include "EventQueue.h"
#include "common/DisplayConfigurationOwner.h"
#include "display/DisplayContextProviderFactory.h"
#include "exynos/ExynosDisplayContextProvider.h"

namespace android::hardware::graphics::composer {

enum class DisplayContextProviderType {
    kExynos = 0,
    // For the subsequent new panel.
    kTotal,
};

class DisplayContextProviderFactory {
public:
    DisplayContextProviderFactory(void* display,
                                  DisplayConfigurationsOwner* displayConfigurationsOwner,
                                  EventQueue* eventQueue)
          : mDisplay(display),
            mDisplayConfigurationsOwner(displayConfigurationsOwner),
            mEventQueue(eventQueue) {}

    std::unique_ptr<DisplayContextProvider> buildDisplayContextProvider(
            DisplayContextProviderType type) {
        RefreshRateCalculatorFactory refreshRateCalculatorFactory;
        VideoFrameRateCalculatorParameters params;
        // Present timeout handling is currently necessary only when the frame rate of the playing
        // video is 30 frames per second or lower.
        params.mMinInterestedFrameRate = 10;
        params.mMaxInterestedFrameRate = 30;
        auto videoFrameRateCalculator =
                refreshRateCalculatorFactory.BuildRefreshRateCalculator(mEventQueue, params);

        if (type == DisplayContextProviderType::kExynos) {
            return std::make_unique<
                    ExynosDisplayContextProvider>(mDisplay, mDisplayConfigurationsOwner,
                                                  std::move(videoFrameRateCalculator));
        } else {
            return nullptr;
        }
    }

    // buildDisplayContextProvider for new HWC display context.

private:
    void* mDisplay;
    DisplayConfigurationsOwner* mDisplayConfigurationsOwner;
    EventQueue* mEventQueue;
};

} // namespace android::hardware::graphics::composer
