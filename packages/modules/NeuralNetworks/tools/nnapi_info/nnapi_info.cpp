/*
 * Copyright 2022 The Android Open Source Project
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

#define LOG_TAG "NnapiInfo"

#define CONTINUE_IF_ERR(expr)                                                                \
    {                                                                                        \
        int _errCode = (expr);                                                               \
        if (_errCode != ANEURALNETWORKS_NO_ERROR) {                                          \
            std::cerr << #expr << " failed at " << __FILE__ << ":" << __LINE__ << std::endl; \
            continue;                                                                        \
        }                                                                                    \
    }

#include <iostream>
#include <string>

#include "NeuralNetworks.h"
#include "NeuralNetworksTypes.h"

namespace {
std::string featureLevelString(int64_t featureLevel) {
    switch (featureLevel) {
        case ANEURALNETWORKS_FEATURE_LEVEL_1:
            return "Level 1";
        case ANEURALNETWORKS_FEATURE_LEVEL_2:
            return "Level 2";
        case ANEURALNETWORKS_FEATURE_LEVEL_3:
            return "Level 3";
        case ANEURALNETWORKS_FEATURE_LEVEL_4:
            return "Level 4";
        case ANEURALNETWORKS_FEATURE_LEVEL_5:
            return "Level 5";
        case ANEURALNETWORKS_FEATURE_LEVEL_6:
            return "Level 6";
        case ANEURALNETWORKS_FEATURE_LEVEL_7:
            return "Level 7";
        case ANEURALNETWORKS_FEATURE_LEVEL_8:
            return "Level 8";
        default:
            return "Undefined feature level code";
    }
}

std::string deviceTypeString(int32_t type) {
    switch (type) {
        case ANEURALNETWORKS_DEVICE_ACCELERATOR:
            return "Accelerator";
        case ANEURALNETWORKS_DEVICE_CPU:
            return "CPU";
        case ANEURALNETWORKS_DEVICE_GPU:
            return "GPU";
        case ANEURALNETWORKS_DEVICE_OTHER:
            return "Other";
        case ANEURALNETWORKS_DEVICE_UNKNOWN:
        default:
            return "Unknown";
    }
}
}  // namespace

int main() {
    uint32_t numDevices;
    int returnCode = ANeuralNetworks_getDeviceCount(&numDevices);
    if (returnCode != ANEURALNETWORKS_NO_ERROR) {
        std::cerr << "Error obtaining device count" << std::endl;
        return 1;
    }

    std::cout << "Number of devices: " << numDevices << std::endl << std::endl;

    ANeuralNetworksDevice* device = nullptr;
    int64_t featureLevel;
    const char* name;
    int32_t type;
    const char* version;
    for (uint32_t i = 0; i < numDevices; i++) {
        CONTINUE_IF_ERR(ANeuralNetworks_getDevice(i, &device));
        CONTINUE_IF_ERR(ANeuralNetworksDevice_getFeatureLevel(device, &featureLevel));
        CONTINUE_IF_ERR(ANeuralNetworksDevice_getName(device, &name));
        CONTINUE_IF_ERR(ANeuralNetworksDevice_getType(device, &type));
        CONTINUE_IF_ERR(ANeuralNetworksDevice_getVersion(device, &version));

        std::cout << "Device: " << name << std::endl;
        std::cout << "Feature Level: " << featureLevelString(featureLevel) << std::endl;
        std::cout << "Type: " << deviceTypeString(type) << std::endl;
        std::cout << "Version: " << version << std::endl;

        std::cout << std::endl;
    }

    return 0;
}