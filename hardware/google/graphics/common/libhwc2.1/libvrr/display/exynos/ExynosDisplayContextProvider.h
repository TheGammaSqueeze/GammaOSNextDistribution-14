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

#include "../common/CommonDisplayContextProvider.h"
#include "libdevice/ExynosDisplay.h"

namespace android::hardware::graphics::composer {

class ExynosDisplayContextProvider : public CommonDisplayContextProvider {
public:
    ExynosDisplayContextProvider(void* display,
                                 DisplayConfigurationsOwner* displayConfigurationOwner,
                                 std::unique_ptr<RefreshRateCalculator> videoFrameRateCalculator)
          : CommonDisplayContextProvider(displayConfigurationOwner,
                                         std::move(videoFrameRateCalculator)),
            mDisplay(static_cast<ExynosDisplay*>(display)),
            mDisplayFileNodePath(mDisplay->getPanelSysfsPath()){};

    BrightnessMode getBrightnessMode() const final;

    int getBrightnessNits() const final;

    const char* getDisplayFileNodePath() const final;

    int getAmbientLightSensorOutput() const final;

    bool isProximityThrottlingEnabled() const final;

private:
    ExynosDisplay* mDisplay;

    std::string mDisplayFileNodePath;
};

} // namespace android::hardware::graphics::composer
