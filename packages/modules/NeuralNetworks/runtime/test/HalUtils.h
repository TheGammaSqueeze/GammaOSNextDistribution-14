/*
 * Copyright (C) 2020 The Android Open Source Project
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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_TEST_HAL_UTILS_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_TEST_HAL_UTILS_H

#include <HalInterfaces.h>
#include <Utils.h>
#include <nnapi/hal/1.0/Device.h>
#include <nnapi/hal/1.1/Device.h>
#include <nnapi/hal/1.2/Device.h>
#include <nnapi/hal/1.3/Device.h>
#include <utils/StrongPointer.h>

#include <string>
#include <utility>

namespace android::nn {

// Creates valid V1_3::Capabilities.
inline V1_3::Capabilities makeCapabilities(float perf) {
    const V1_0::PerformanceInfo perfInfo = {.execTime = perf, .powerUsage = perf};
    return {.relaxedFloat32toFloat16PerformanceScalar = perfInfo,
            .relaxedFloat32toFloat16PerformanceTensor = perfInfo,
            .operandPerformance = nonExtensionOperandPerformance<HalVersion::V1_3>(perfInfo),
            .ifPerformance = perfInfo,
            .whilePerformance = perfInfo};
}

inline SharedDevice makeSharedDevice(std::string name, sp<V1_0::IDevice> driver) {
    auto handleError = [](GeneralResult<SharedDevice> result) -> SharedDevice {
        if (!result.has_value()) {
            LOG(ERROR) << "Failed to create Device (" << result.error().code
                       << "): " << result.error().message;
            return nullptr;
        }
        return std::move(result).value();
    };
    if (auto driver13 = V1_3::IDevice::castFrom(driver).withDefault(nullptr); driver13 != nullptr) {
        return handleError(V1_3::utils::Device::create(std::move(name), std::move(driver13)));
    }
    if (auto driver12 = V1_2::IDevice::castFrom(driver).withDefault(nullptr); driver12 != nullptr) {
        return handleError(V1_2::utils::Device::create(std::move(name), std::move(driver12)));
    }
    if (auto driver11 = V1_1::IDevice::castFrom(driver).withDefault(nullptr); driver11 != nullptr) {
        return handleError(V1_1::utils::Device::create(std::move(name), std::move(driver11)));
    }
    return handleError(V1_0::utils::Device::create(std::move(name), std::move(driver)));
}

}  // namespace android::nn

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_TEST_HAL_UTILS_H
