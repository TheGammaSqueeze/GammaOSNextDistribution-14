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

#define LOG_TAG "SampleDriverAidlPartial"

#include "SampleDriverAidlPartial.h"

#include <android-base/logging.h>
#include <android/binder_auto_utils.h>
#include <nnapi/Validation.h>
#include <nnapi/hal/aidl/Conversions.h>
#include <nnapi/hal/aidl/Utils.h>

#include <memory>
#include <thread>
#include <utility>
#include <vector>

#include "SampleDriverAidlUtils.h"

namespace android {
namespace nn {
namespace sample_driver_aidl {

ndk::ScopedAStatus SampleDriverPartial::getSupportedOperations(
        const aidl_hal::Model& model, std::vector<bool>* supportedOperations) {
    VLOG(DRIVER) << "getSupportedOperations()";
    const auto canonicalModel = convert(model);
    if (!canonicalModel.has_value()) {
        return toAStatus(aidl_hal::ErrorStatus::INVALID_ARGUMENT, canonicalModel.error().message);
    }
    *supportedOperations = getSupportedOperationsImpl(canonicalModel.value());
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus SampleDriverPartial::prepareModel(
        const aidl_hal::Model& model, aidl_hal::ExecutionPreference preference,
        aidl_hal::Priority priority, int64_t deadline,
        const std::vector<ndk::ScopedFileDescriptor>&,
        const std::vector<ndk::ScopedFileDescriptor>&, const std::vector<uint8_t>&,
        const std::shared_ptr<aidl_hal::IPreparedModelCallback>& callback) {
    const auto canonicalModel = convert(model);
    if (!canonicalModel.has_value()) {
        notify(callback, aidl_hal::ErrorStatus::INVALID_ARGUMENT, nullptr);
        return toAStatus(aidl_hal::ErrorStatus::INVALID_ARGUMENT, canonicalModel.error().message);
    }
    std::vector<bool> supported = getSupportedOperationsImpl(canonicalModel.value());
    bool isModelFullySupported =
            std::all_of(supported.begin(), supported.end(), [](bool v) { return v; });
    auto copiedModel = aidl_hal::utils::clone(model);
    if (!copiedModel.has_value()) {
        return toAStatus(aidl_hal::ErrorStatus::GENERAL_FAILURE, copiedModel.error().message);
    }
    return prepareModelBase(std::move(copiedModel).value(), this, preference, priority, deadline,
                            callback, isModelFullySupported);
}

}  // namespace sample_driver_aidl
}  // namespace nn
}  // namespace android
