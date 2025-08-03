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

#include "../../interface/DisplayContextProvider.h"
#include "../RefreshRateCalculator/VideoFrameRateCalculator.h"
#include "DisplayConfigurationOwner.h"

namespace android::hardware::graphics::composer {

class CommonDisplayContextProvider : public DisplayContextProvider {
public:
    CommonDisplayContextProvider(DisplayConfigurationsOwner* displayConfigurationOwner,
                                 std::unique_ptr<RefreshRateCalculator> videoFrameRateCalculator)
          : mDisplayConfigurationOwner(displayConfigurationOwner),
            mVideoFrameRateCalculator(std::move(videoFrameRateCalculator)){};

    OperationSpeedMode getOperationSpeedMode() const override;

    virtual BrightnessMode getBrightnessMode() const = 0;

    virtual int getBrightnessNits() const = 0;

    virtual const char* getDisplayFileNodePath() const = 0;

    int getEstimatedVideoFrameRate() const override;

    virtual int getAmbientLightSensorOutput() const = 0;

    virtual bool isProximityThrottlingEnabled() const = 0;

private:
    DisplayConfigurationsOwner* mDisplayConfigurationOwner;

    std::unique_ptr<RefreshRateCalculator> mVideoFrameRateCalculator;
};

} // namespace android::hardware::graphics::composer
