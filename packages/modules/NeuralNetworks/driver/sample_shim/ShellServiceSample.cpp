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

#define LOG_TAG "ShellServiceSample"

#include <NeuralNetworksShim.h>
#include <aidl/android/hardware/neuralnetworks/BnDevice.h>
#include <android-base/logging.h>
#include <android-base/scopeguard.h>
#include <android/binder_enums.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <dlfcn.h>
#include <nnapi/hal/aidl/InvalidDevice.h>

#include <algorithm>
#include <limits>
#include <string>
#include <type_traits>
#include <unordered_map>
#include <utility>
#include <vector>

typedef struct NnApiSLDriverImpl NnApiSLDriverImpl;

namespace aidl::android::hardware::neuralnetworks {
namespace {

struct Names {
    std::string driverName;
    std::string serviceName;
};

void registerInvalidDevices(const std::vector<Names>& names) {
    for (const auto& [_, name] : names) {
        const auto invalidDevice = InvalidDevice::create();
        const std::string instance = std::string(IDevice::descriptor) + "/" + name;
        LOG(INFO) << "Attempting service registration for " << instance;
        const auto status = AServiceManager_registerLazyService(invalidDevice->asBinder().get(),
                                                                instance.c_str());
        if (status != STATUS_OK) {
            LOG(ERROR) << "AServiceManager_registerLazyService failed for " << name
                       << ", error code " << status;
            return;
        }
    }
    ABinderProcess_setThreadPoolMaxThreadCount(15);
    ABinderProcess_joinThreadPool();
}

int registerDevices(const std::string& driverPath, const std::vector<Names>& devices) {
    // Load support library.
    void* libHandle = dlopen(driverPath.c_str(), RTLD_LAZY | RTLD_LOCAL | RTLD_NODELETE);
    if (libHandle == nullptr) {
        LOG(ERROR) << "Failed to load sample SL driver: " << driverPath;
        registerInvalidDevices(devices);
        return EXIT_FAILURE;
    }

    // Load support library entry point.
    using GetSlDriverImplFn = std::add_pointer_t<NnApiSLDriverImpl*()>;
    GetSlDriverImplFn getSlDriverImpl = reinterpret_cast<GetSlDriverImplFn>(
            dlsym(libHandle, "ANeuralNetworks_getSLDriverImpl"));
    if (getSlDriverImpl == nullptr) {
        LOG(ERROR) << "Failed to find ANeuralNetworks_getSLDriverImpl symbol in: " << driverPath;
        registerInvalidDevices(devices);
        return EXIT_FAILURE;
    }

    // Call support library entry point to obtain functionality.
    NnApiSLDriverImpl* impl = getSlDriverImpl();
    if (impl == nullptr) {
        LOG(ERROR) << "ANeuralNetworks_getSLDriverImpl returned nullptr: " << driverPath;
        registerInvalidDevices(devices);
        return EXIT_FAILURE;
    }

    ANeuralNetworksShimRegistrationParams* params;
    ANeuralNetworksShimRegistrationParams_create(impl, &params);
    const auto guardParams = ::android::base::make_scope_guard(
            [params] { ANeuralNetworksShimRegistrationParams_free(params); });

    // The default is 15, use more only if there's more devices exposed.
    ANeuralNetworksShimRegistrationParams_setNumberOfListenerThreads(params, 15);
    ANeuralNetworksShimRegistrationParams_registerAsLazyService(params, /*asLazy=*/true);
    ANeuralNetworksShimRegistrationParams_fallbackToMinimumSupportDevice(params, /*fallback=*/true);

    for (const auto& device : devices) {
        ANeuralNetworksShimDeviceInfo* deviceInfo;
        ANeuralNetworksShimDeviceInfo_create(&deviceInfo, device.driverName.c_str(),
                                             device.serviceName.c_str());
        const auto guardDeviceInfo = ::android::base::make_scope_guard(
                [deviceInfo] { ANeuralNetworksShimDeviceInfo_free(deviceInfo); });

        ANeuralNetworksShimRegistrationParams_addDeviceInfo(params, deviceInfo);
    }

    // Register the support library as a binderized AIDL service.
    auto result = ANeuralNetworksShim_registerSupportLibraryService(params);
    LOG(ERROR) << "ANeuralNetworksShim_registerSupportLibraryService returned with error status: "
               << result;

    return EXIT_FAILURE;
}

}  // namespace
}  // namespace aidl::android::hardware::neuralnetworks

using aidl::android::hardware::neuralnetworks::Names;
using aidl::android::hardware::neuralnetworks::registerDevices;

int main() {
    const std::string driverPath = "/vendor/lib64/neuralnetworks_sample_sl_driver_prebuilt.so";

    const std::vector<Names> devicesToRegister = {
            {.driverName = "nnapi-sample_sl", .serviceName = "nnapi-sample_sl_updatable"},
    };

    return registerDevices(driverPath, devicesToRegister);
}
