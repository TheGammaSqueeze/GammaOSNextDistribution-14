/*
 * Copyright (C) 2022 The Android Open Source Project
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

#define LOG_TAG "EmulatedVehicleService"

#include <DefaultVehicleHal.h>
#include <EmulatedVehicleHardware.h>

#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <utils/Log.h>

using ::android::hardware::automotive::vehicle::DefaultVehicleHal;
using ::android::hardware::automotive::vehicle::fake::EmulatedVehicleHardware;

int main(int /* argc */, char* /* argv */[]) {
    ALOGI("Starting thread pool...");
    if (!ABinderProcess_setThreadPoolMaxThreadCount(4)) {
        ALOGE("%s", "failed to set thread pool max thread count");
        return 1;
    }
    ABinderProcess_startThreadPool();

    std::unique_ptr<EmulatedVehicleHardware> hardware = std::make_unique<EmulatedVehicleHardware>();
    std::shared_ptr<DefaultVehicleHal> vhal =
            ::ndk::SharedRefBase::make<DefaultVehicleHal>(std::move(hardware));

    ALOGI("Emulator Vehicle Service: Registering as service...");
    binder_exception_t err = AServiceManager_addService(
            vhal->asBinder().get(), "android.hardware.automotive.vehicle.IVehicle/default");
    if (err != EX_NONE) {
        ALOGE("failed to register android.hardware.automotive.vehicle service, exception: %d", err);
        return 1;
    }

    ALOGI("Emulator Vehicle Service Ready");

    ABinderProcess_joinThreadPool();

    ALOGI("Emulator Vehicle Service Exiting");

    return 0;
}
