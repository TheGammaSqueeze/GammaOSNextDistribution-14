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

#define LOG_TAG "ShimDeviceManager"

#include "ShimDeviceManager.h"

#include <AndroidVersionUtil.h>
#include <aidl/android/hardware/neuralnetworks/IDevice.h>
#include <android-base/logging.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <nnapi/hal/aidl/InvalidDevice.h>

#include <algorithm>
#include <memory>
#include <string>
#include <unordered_map>
#include <utility>
#include <vector>

#include "NeuralNetworksShim.h"
#include "ShimDevice.h"
#include "ShimUtils.h"
#include "SupportLibrary.h"

namespace android::neuralnetworks::shim {
namespace {

using aidl::android::hardware::neuralnetworks::IDevice;
using aidl::android::hardware::neuralnetworks::InvalidDevice;
using aidl::android::hardware::neuralnetworks::ShimDevice;

ANeuralNetworksShimResultCode registerEagerService(const std::shared_ptr<IDevice>& device,
                                                   const std::string& name) {
    const binder_exception_t status =
            AServiceManager_addService(device->asBinder().get(), name.c_str());
    if (status != EX_NONE) {
        LOG(ERROR) << "AServiceManager_addService failed for " << name << ", error code " << status;
        return ANNSHIM_FAILED_TO_REGISTER_SERVICE;
    }
    return ANNSHIM_NO_ERROR;
}

ANeuralNetworksShimResultCode registerLazyService(const std::shared_ptr<IDevice>& device,
                                                  const std::string& name) {
    if (__builtin_available(android __NNAPI_AIDL_MIN_ANDROID_API__, *)) {
        const binder_status_t status =
                AServiceManager_registerLazyService(device->asBinder().get(), name.c_str());
        if (status != STATUS_OK) {
            LOG(ERROR) << "Service registration failed for " << name << ", error code " << status;
            return ANNSHIM_FAILED_TO_REGISTER_SERVICE;
        }
        return ANNSHIM_NO_ERROR;
    }
    LOG(ERROR) << "Service registration failed for " << name
               << " because AServiceManager_registerLazyService is not supported until API "
                  "level 31";
    return ANNSHIM_FAILED_TO_REGISTER_SERVICE;
}

ANeuralNetworksShimResultCode registerService(const std::shared_ptr<IDevice>& device,
                                              const std::string& name, bool registerAsLazy) {
    const std::string instance = std::string(ShimDevice::descriptor) + "/" + name;
    LOG(INFO) << "Attempting service registration for " << instance;
    return registerAsLazy ? registerLazyService(device, instance)
                          : registerEagerService(device, instance);
}

std::unordered_map<std::string, ANeuralNetworksDevice*> getNamedDevices(
        const std::shared_ptr<const NnApiSupportLibrary>& nnapi) {
    uint32_t numDevices;
    if (nnapi->getFL5()->ANeuralNetworks_getDeviceCount(&numDevices) != ANEURALNETWORKS_NO_ERROR) {
        LOG(ERROR) << "Failed ANeuralNetworks_getDeviceCount";
        return {};
    }

    std::unordered_map<std::string, ANeuralNetworksDevice*> nameToDevice;
    for (uint32_t i = 0; i < numDevices; ++i) {
        ANeuralNetworksDevice* device;
        if (nnapi->getFL5()->ANeuralNetworks_getDevice(i, &device) != ANEURALNETWORKS_NO_ERROR) {
            LOG(ERROR) << "Failed ANeuralNetworks_getDevice";
            return {};
        }

        const char* name = nullptr;
        if (nnapi->getFL5()->ANeuralNetworksDevice_getName(device, &name) !=
            ANEURALNETWORKS_NO_ERROR) {
            LOG(ERROR) << "Failed ANeuralNetworks_getName";
            return {};
        }

        nameToDevice.emplace(name, device);
    }

    return nameToDevice;
}

}  // namespace

ANeuralNetworksShimResultCode registerDevices(NnApiSLDriverImpl* nnapiSLImpl,
                                              const std::vector<ShimDeviceInfo>& devicesToRegister,
                                              uint32_t numberOfListenerThreads,
                                              bool registerAsLazyService,
                                              bool fallbackToMinimumSupportDevice) {
    if (nnapiSLImpl == nullptr) {
        LOG(ERROR) << "Invalid arguments, nnapiSLImpl == nullptr ";
        return ANNSHIM_INVALID_ARGUMENT;
    }
    if (devicesToRegister.empty()) {
        LOG(ERROR) << "Invalid arguments, devicesToRegister is empty";
        return ANNSHIM_INVALID_ARGUMENT;
    }

    if (nnapiSLImpl->implFeatureLevel < ANEURALNETWORKS_FEATURE_LEVEL_5) {
        LOG(ERROR) << "Invalid implStructFeatureLevel if NnApiSLDriverImpl, has to be at least "
                      "ANEURALNETWORKS_FEATURE_LEVEL_5";
        return ANNSHIM_FAILED_TO_LOAD_SL;
    }

    // NnApiSLDriverImplFL[5-6] are identical, hence we can just cast to latest one.
    std::shared_ptr<const NnApiSupportLibrary> nnapi;

    if (nnapiSLImpl->implFeatureLevel == ANEURALNETWORKS_FEATURE_LEVEL_5) {
        nnapi = std::make_unique<NnApiSupportLibrary>(
                *reinterpret_cast<NnApiSLDriverImplFL5*>(nnapiSLImpl), nullptr);
    }
    if (nnapiSLImpl->implFeatureLevel == ANEURALNETWORKS_FEATURE_LEVEL_6) {
        nnapi = std::make_unique<NnApiSupportLibrary>(
                *reinterpret_cast<NnApiSLDriverImplFL6*>(nnapiSLImpl), nullptr);
    }
    if (nnapiSLImpl->implFeatureLevel == ANEURALNETWORKS_FEATURE_LEVEL_7) {
        nnapi = std::make_unique<NnApiSupportLibrary>(
                *reinterpret_cast<NnApiSLDriverImplFL7*>(nnapiSLImpl), nullptr);
    }
    if (nnapiSLImpl->implFeatureLevel >= ANEURALNETWORKS_FEATURE_LEVEL_8) {
        nnapi = std::make_unique<NnApiSupportLibrary>(
                *reinterpret_cast<NnApiSLDriverImplFL8*>(nnapiSLImpl), nullptr);
    }

    CHECK_NE(nnapi, nullptr);

    ABinderProcess_setThreadPoolMaxThreadCount(numberOfListenerThreads);

    const auto nameToDevice = getNamedDevices(nnapi);
    std::vector<std::shared_ptr<IDevice>> devices;
    devices.reserve(devicesToRegister.size());

    // Convert all supplied devices to AIDL IDevice interfaces.
    for (const auto& info : devicesToRegister) {
        const auto& name = info.deviceName;

        if (const auto iter = nameToDevice.find(name); iter != nameToDevice.end()) {
            ANeuralNetworksDevice* device = iter->second;

            auto shimDevice = ndk::SharedRefBase::make<ShimDevice>(nnapi, device, info.serviceName);
            devices.push_back(std::move(shimDevice));
            continue;
        }

        if (!fallbackToMinimumSupportDevice) {
            LOG(ERROR) << "NNAPI device " << name
                       << " was not found in the support library package, and falling back to a "
                          "minimum support device was not specified";
            return ANNSHIM_FAILED_TO_REGISTER_SERVICE;
        }

        // If the device was not found in the support library package, and falling back on a
        // minimum support device is allowed, construct a minimum support device.
        LOG(INFO) << "NNAPI device " << name
                  << " was not found in the support library package, and falling back to a "
                     "minimal support device is allowed, so a minimal support device "
                     "is being registered instead.";
        devices.push_back(InvalidDevice::create());
    }

    CHECK_EQ(devices.size(), devicesToRegister.size());

    // Register all AIDL IDevice interfaces.
    for (size_t i = 0; i < devicesToRegister.size(); i++) {
        const auto& info = devicesToRegister[i];
        const auto& device = devices[i];

        const auto registrationResult =
                registerService(device, info.serviceName, registerAsLazyService);
        if (registrationResult != ANNSHIM_NO_ERROR) {
            // This will only fail if there is a problem with Binder or if there is a mismatch
            // between the service being registered and the service listed on the device manifest.
            // Falling back to a minimum support device would not help resolve this whatever
            // mismatch may exist, so there is no fallback path at this stage.
            return registrationResult;
        }
    }

    LOG(INFO) << devices.size() << " NNAPI Devices/services registered, blocking";
    ABinderProcess_joinThreadPool();

    // Shouldn't reach here.
    LOG(ERROR) << "ABinderProcess_joinThreadPool unexpected returned in registerDevices.";
    return ANNSHIM_GENERAL_ERROR;
}

}  // namespace android::neuralnetworks::shim
