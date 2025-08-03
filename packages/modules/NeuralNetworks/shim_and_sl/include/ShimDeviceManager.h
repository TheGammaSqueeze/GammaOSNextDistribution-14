/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <string>
#include <vector>

#include "NeuralNetworksShim.h"
#include "ShimDevice.h"

namespace android::neuralnetworks::shim {

/**
 * Information about an NNAPI Device to register.
 */
struct ShimDeviceInfo {
    /**
     * Name of the target device, as returned by SL ANeuralNetworksDevice_getName
     */
    std::string deviceName;

    /**
     * Name of HAL AIDL service backed by this SL NNAPI Driver device.
     */
    std::string serviceName;
};

struct RegistrationParams {
    NnApiSLDriverImpl* nnapiSupportLibraryPackage = nullptr;
    std::vector<ShimDeviceInfo> deviceInfos;
    uint32_t numberOfListenerThreads = 15;
    bool registerAsLazyService = false;
    bool fallbackToMinimumSupportDevice = false;
};

ANeuralNetworksShimResultCode registerDevices(NnApiSLDriverImpl* nnapiSLImpl,
                                              const std::vector<ShimDeviceInfo>& devicesToRegister,
                                              uint32_t numberOfListenerThreads,
                                              bool registerAsLazyService,
                                              bool fallbackToMinimumSupportDevice);

}  // namespace android::neuralnetworks::shim
