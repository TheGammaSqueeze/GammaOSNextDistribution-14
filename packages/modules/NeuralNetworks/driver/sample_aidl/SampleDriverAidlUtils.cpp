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

#include "SampleDriverAidlUtils.h"

#include <aidl/android/hardware/common/NativeHandle.h>
#include <android/binder_auto_utils.h>
#include <android/binder_ibinder.h>
#include <android/binder_manager.h>
#include <nnapi/Validation.h>
#include <nnapi/hal/aidl/Conversions.h>
#include <nnapi/hal/aidl/Utils.h>
#include <utils/NativeHandle.h>

#include <memory>
#include <string>
#include <thread>
#include <utility>
#include <vector>

#include "SampleDriverAidl.h"
#include "android/binder_process.h"

namespace android {
namespace nn {
namespace sample_driver_aidl {

int run(const std::shared_ptr<aidl_hal::BnDevice>& device, const std::string& name) {
    constexpr size_t kNumberOfThreads = 4;
    ABinderProcess_setThreadPoolMaxThreadCount(kNumberOfThreads);

    const std::string fqName = std::string(SampleDriver::descriptor) + "/" + name;
    const binder_status_t status =
            AServiceManager_addService(device->asBinder().get(), fqName.c_str());
    if (status != STATUS_OK) {
        LOG(ERROR) << "Could not register service " << name;
        return 1;
    }

    ABinderProcess_joinThreadPool();
    LOG(ERROR) << "Service exited!";
    return 1;
}

void notify(const std::shared_ptr<aidl_hal::IPreparedModelCallback>& callback,
            const aidl_hal::ErrorStatus& status,
            const std::shared_ptr<aidl_hal::IPreparedModel>& preparedModel) {
    const auto ret = callback->notify(status, preparedModel);
    if (!ret.isOk()) {
        LOG(ERROR) << "Error when calling IPreparedModelCallback::notify: " << ret.getDescription()
                   << " " << ret.getMessage();
    }
}

ndk::ScopedAStatus toAStatus(aidl_hal::ErrorStatus errorStatus) {
    if (errorStatus == aidl_hal::ErrorStatus::NONE) {
        return ndk::ScopedAStatus::ok();
    }
    return ndk::ScopedAStatus::fromServiceSpecificError(static_cast<int32_t>(errorStatus));
}

ndk::ScopedAStatus toAStatus(aidl_hal::ErrorStatus errorStatus, const std::string& errorMessage) {
    if (errorStatus == aidl_hal::ErrorStatus::NONE) {
        return ndk::ScopedAStatus::ok();
    }
    return ndk::ScopedAStatus::fromServiceSpecificErrorWithMessage(
            static_cast<int32_t>(errorStatus), errorMessage.c_str());
}

ndk::ScopedAStatus prepareModelBase(
        aidl_hal::Model&& model, const SampleDriver* driver,
        aidl_hal::ExecutionPreference preference, aidl_hal::Priority priority, int64_t halDeadline,
        const std::shared_ptr<aidl_hal::IPreparedModelCallback>& callback,
        bool isFullModelSupported) {
    const uid_t userId = AIBinder_getCallingUid();
    if (callback.get() == nullptr) {
        LOG(ERROR) << "invalid callback passed to prepareModelBase";
        return toAStatus(aidl_hal::ErrorStatus::INVALID_ARGUMENT,
                         "invalid callback passed to prepareModelBase");
    }
    const auto canonicalModel = convert(model);
    if (!canonicalModel.has_value()) {
        VLOG(DRIVER) << "invalid model passed to prepareModelBase";
        notify(callback, aidl_hal::ErrorStatus::INVALID_ARGUMENT, nullptr);
        return toAStatus(aidl_hal::ErrorStatus::INVALID_ARGUMENT,
                         "invalid model passed to prepareModelBase");
    }
    if (VLOG_IS_ON(DRIVER)) {
        VLOG(DRIVER) << "prepareModelBase";
        logModelToInfo(canonicalModel.value());
    }
    if (!aidl_hal::utils::valid(preference)) {
        const std::string log_message =
                "invalid execution preference passed to prepareModelBase: " + toString(preference);
        VLOG(DRIVER) << log_message;
        notify(callback, aidl_hal::ErrorStatus::INVALID_ARGUMENT, nullptr);
        return toAStatus(aidl_hal::ErrorStatus::INVALID_ARGUMENT, log_message);
    }
    if (!aidl_hal::utils::valid(priority)) {
        const std::string log_message =
                "invalid priority passed to prepareModelBase: " + toString(priority);
        VLOG(DRIVER) << log_message;
        notify(callback, aidl_hal::ErrorStatus::INVALID_ARGUMENT, nullptr);
        return toAStatus(aidl_hal::ErrorStatus::INVALID_ARGUMENT, log_message);
    }

    if (!isFullModelSupported) {
        VLOG(DRIVER) << "model is not fully supported";
        notify(callback, aidl_hal::ErrorStatus::INVALID_ARGUMENT, nullptr);
        return ndk::ScopedAStatus::ok();
    }

    if (halDeadline < -1) {
        notify(callback, aidl_hal::ErrorStatus::INVALID_ARGUMENT, nullptr);
        return toAStatus(aidl_hal::ErrorStatus::INVALID_ARGUMENT,
                         "Invalid deadline: " + toString(halDeadline));
    }
    const auto deadline = makeDeadline(halDeadline);
    if (hasDeadlinePassed(deadline)) {
        notify(callback, aidl_hal::ErrorStatus::MISSED_DEADLINE_PERSISTENT, nullptr);
        return ndk::ScopedAStatus::ok();
    }

    // asynchronously prepare the model from a new, detached thread
    std::thread(
            [driver, preference, userId, priority, callback](aidl_hal::Model&& model) {
                std::shared_ptr<SamplePreparedModel> preparedModel =
                        ndk::SharedRefBase::make<SamplePreparedModel>(std::move(model), driver,
                                                                      preference, userId, priority);
                if (!preparedModel->initialize()) {
                    notify(callback, aidl_hal::ErrorStatus::INVALID_ARGUMENT, nullptr);
                    return;
                }
                notify(callback, aidl_hal::ErrorStatus::NONE, preparedModel);
            },
            std::move(model))
            .detach();

    return ndk::ScopedAStatus::ok();
}

}  // namespace sample_driver_aidl
}  // namespace nn
}  // namespace android
