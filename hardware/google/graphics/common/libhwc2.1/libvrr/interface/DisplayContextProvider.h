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

namespace android::hardware::graphics::composer {

enum class OperationSpeedMode {
    kHighSpeedMode = 0,
    kNormalSpeedMode,
    kInvalidSpeedMode,
};

enum class BrightnessMode {
    kNormalBrightnessMode = 0,
    kHighBrightnessMode,
    kInvalidBrightnessMode,
};

class DisplayContextProvider {
public:
    virtual ~DisplayContextProvider() = default;

    virtual OperationSpeedMode getOperationSpeedMode() const = 0;

    virtual BrightnessMode getBrightnessMode() const = 0;

    virtual int getBrightnessNits() const = 0;

    virtual const char* getDisplayFileNodePath() const = 0;

    virtual int getEstimatedVideoFrameRate() const = 0;

    virtual int getAmbientLightSensorOutput() const = 0;

    virtual bool isProximityThrottlingEnabled() const = 0;
};

struct DisplayContextProviderInterface {
    OperationSpeedMode (*getOperationSpeedMode)(void* host);

    BrightnessMode (*getBrightnessMode)(void* host);

    int (*getBrightnessNits)(void* host);

    const char* (*getDisplayFileNodePath)(void* hosts);

    int (*getEstimatedVideoFrameRate)(void* host);

    int (*getAmbientLightSensorOutput)(void* hosts);

    bool (*isProximityThrottlingEnabled)(void* hosts);
};

} // namespace android::hardware::graphics::composer
