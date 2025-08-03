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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_DRIVER_SAMPLE_SAMPLE_DRIVER_UTILS_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_DRIVER_SAMPLE_SAMPLE_DRIVER_UTILS_H

#include <HalInterfaces.h>
#include <hwbinder/IPCThreadState.h>

#include <string>
#include <thread>
#include <utility>
#include <vector>

#include "SampleDriver.h"

namespace android {
namespace nn {
namespace sample_driver {

// Starts and runs the driver service.  Typically called from main().
// This will return only once the service shuts down.
int run(const sp<V1_3::IDevice>& device, const std::string& name);

void notify(const sp<V1_0::IPreparedModelCallback>& callback, const V1_3::ErrorStatus& status,
            const sp<SamplePreparedModel>& preparedModel);

void notify(const sp<V1_2::IPreparedModelCallback>& callback, const V1_3::ErrorStatus& status,
            const sp<SamplePreparedModel>& preparedModel);

void notify(const sp<V1_3::IPreparedModelCallback>& callback, const V1_3::ErrorStatus& status,
            const sp<SamplePreparedModel>& preparedModel);

void notify(const sp<V1_0::IExecutionCallback>& callback, const V1_3::ErrorStatus& status,
            const hardware::hidl_vec<V1_2::OutputShape>&, V1_2::Timing);

void notify(const sp<V1_2::IExecutionCallback>& callback, const V1_3::ErrorStatus& status,
            const hardware::hidl_vec<V1_2::OutputShape>& outputShapes, V1_2::Timing timing);

void notify(const sp<V1_3::IExecutionCallback>& callback, const V1_3::ErrorStatus& status,
            const hardware::hidl_vec<V1_2::OutputShape>& outputShapes, V1_2::Timing timing);

template <typename T_Model, typename T_IPreparedModelCallback>
V1_3::ErrorStatus prepareModelBase(const T_Model& model, const SampleDriver* driver,
                                   V1_1::ExecutionPreference preference, V1_3::Priority priority,
                                   const V1_3::OptionalTimePoint& halDeadline,
                                   const sp<T_IPreparedModelCallback>& callback,
                                   bool isFullModelSupported = true) {
    const uid_t userId = hardware::IPCThreadState::self()->getCallingUid();
    if (callback.get() == nullptr) {
        LOG(ERROR) << "invalid callback passed to prepareModelBase";
        return V1_3::ErrorStatus::INVALID_ARGUMENT;
    }
    if (VLOG_IS_ON(DRIVER)) {
        VLOG(DRIVER) << "prepareModelBase";
        logModelToInfo(model);
    }
    if (!validateModel(model) || !validateExecutionPreference(preference) ||
        !validatePriority(priority)) {
        notify(callback, V1_3::ErrorStatus::INVALID_ARGUMENT, nullptr);
        return V1_3::ErrorStatus::INVALID_ARGUMENT;
    }
    if (!isFullModelSupported) {
        notify(callback, V1_3::ErrorStatus::INVALID_ARGUMENT, nullptr);
        return V1_3::ErrorStatus::NONE;
    }
    const auto deadline = makeDeadline(halDeadline);
    if (hasDeadlinePassed(deadline)) {
        notify(callback, V1_3::ErrorStatus::MISSED_DEADLINE_PERSISTENT, nullptr);
        return V1_3::ErrorStatus::NONE;
    }

    // asynchronously prepare the model from a new, detached thread
    std::thread([model, driver, preference, userId, priority, callback] {
        sp<SamplePreparedModel> preparedModel =
                new SamplePreparedModel(convertToV1_3(model), driver, preference, userId, priority);
        if (!preparedModel->initialize()) {
            notify(callback, V1_3::ErrorStatus::INVALID_ARGUMENT, nullptr);
            return;
        }
        notify(callback, V1_3::ErrorStatus::NONE, preparedModel);
    }).detach();

    return V1_3::ErrorStatus::NONE;
}

}  // namespace sample_driver
}  // namespace nn
}  // namespace android

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_DRIVER_SAMPLE_SAMPLE_DRIVER_UTILS_H
