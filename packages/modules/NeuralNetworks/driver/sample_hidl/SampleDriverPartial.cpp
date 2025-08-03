/*
 * Copyright (C) 2019 The Android Open Source Project
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

#define LOG_TAG "SampleDriverPartial"

#include "SampleDriverPartial.h"

#include <HalInterfaces.h>
#include <Utils.h>
#include <ValidateHal.h>
#include <android-base/logging.h>

#include <thread>
#include <vector>

#include "SampleDriverUtils.h"

namespace android {
namespace nn {
namespace sample_driver {

hardware::Return<void> SampleDriverPartial::getSupportedOperations_1_3(
        const V1_3::Model& model, getSupportedOperations_1_3_cb cb) {
    VLOG(DRIVER) << "getSupportedOperations()";
    if (validateModel(model)) {
        std::vector<bool> supported = getSupportedOperationsImpl(model);
        cb(V1_3::ErrorStatus::NONE, supported);
    } else {
        std::vector<bool> supported;
        cb(V1_3::ErrorStatus::INVALID_ARGUMENT, supported);
    }
    return hardware::Void();
}

hardware::Return<V1_3::ErrorStatus> SampleDriverPartial::prepareModel_1_3(
        const V1_3::Model& model, V1_1::ExecutionPreference preference, V1_3::Priority priority,
        const V1_3::OptionalTimePoint& deadline, const hardware::hidl_vec<hardware::hidl_handle>&,
        const hardware::hidl_vec<hardware::hidl_handle>&, const HalCacheToken&,
        const sp<V1_3::IPreparedModelCallback>& callback) {
    std::vector<bool> supported = getSupportedOperationsImpl(model);
    bool isModelFullySupported =
            std::all_of(supported.begin(), supported.end(), [](bool v) { return v; });
    return prepareModelBase(model, this, preference, priority, deadline, callback,
                            isModelFullySupported);
}

}  // namespace sample_driver
}  // namespace nn
}  // namespace android
