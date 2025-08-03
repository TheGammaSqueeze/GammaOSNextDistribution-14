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

#define LOG_TAG "SampleDriverAidlFull"

#include "SampleDriverAidlFull.h"

#include <nnapi/Validation.h>
#include <nnapi/hal/aidl/Conversions.h>
#include <nnapi/hal/aidl/HalUtils.h>

#include <string>
#include <vector>

#include "LegacyUtils.h"
#include "SampleDriverAidlUtils.h"

namespace android {
namespace nn {
namespace sample_driver_aidl {

ndk::ScopedAStatus SampleDriverFull::getCapabilities(aidl_hal::Capabilities* capabilities) {
    android::nn::initVLogMask();
    VLOG(DRIVER) << "getCapabilities()";
    *capabilities = {.relaxedFloat32toFloat16PerformanceScalar = mPerf,
                     .relaxedFloat32toFloat16PerformanceTensor = mPerf,
                     .operandPerformance = nonExtensionOperandPerformance(mPerf),
                     .ifPerformance = mPerf,
                     .whilePerformance = mPerf};
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus SampleDriverFull::getSupportedOperations(
        const aidl_hal::Model& model, std::vector<bool>* supportedOperations) {
    VLOG(DRIVER) << "getSupportedOperations()";
    const auto canonicalModel = convert(model);
    if (!canonicalModel.has_value()) {
        return toAStatus(aidl_hal::ErrorStatus::INVALID_ARGUMENT, canonicalModel.error().message);
    }
    const size_t count = canonicalModel.value().main.operations.size();
    *supportedOperations = std::vector<bool>(count, true);
    for (size_t i = 0; i < count; i++) {
        const Operation& operation = canonicalModel.value().main.operations[i];
        supportedOperations->at(i) = !isExtensionOperationType(operation.type);
    }
    return ndk::ScopedAStatus::ok();
}

}  // namespace sample_driver_aidl
}  // namespace nn
}  // namespace android
