/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <log/log.h>

#include "CameraProvider.h"
#include "service_entry.h"
#include "utils.h"

namespace android {
namespace hardware {
namespace camera {
namespace provider {
namespace implementation {

int serviceEntry(const int deviceIdBase,
                 const Span<const hw::HwCameraFactory> availableCameras,
                 const unsigned binderMaxThreads) {
    ABinderProcess_setThreadPoolMaxThreadCount(binderMaxThreads);
    ABinderProcess_startThreadPool();

    std::shared_ptr<CameraProvider> hal = ndk::SharedRefBase::make<CameraProvider>(
        deviceIdBase, availableCameras);

    const std::string instance = std::string(CameraProvider::descriptor) + "/internal/1";

    if (AServiceManager_addService(hal->asBinder().get(),
                                   instance.c_str()) == STATUS_OK) {
        ABinderProcess_joinThreadPool();
        return EXIT_FAILURE;
    } else {
        ALOGE("Failed to register '%s'", instance.c_str());
        return android::NO_INIT;
    }
}

}  // namespace implementation
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android
