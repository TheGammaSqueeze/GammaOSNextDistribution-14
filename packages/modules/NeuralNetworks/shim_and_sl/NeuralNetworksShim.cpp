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

#define LOG_TAG "NeuralNetworksShim"

#include "NeuralNetworksShim.h"

#include <android-base/logging.h>
#include <nnapi/Types.h>

#include <limits>
#include <string>
#include <utility>
#include <vector>

#include "ShimDevice.h"
#include "ShimDeviceManager.h"

static_assert(offsetof(NnApiSLDriverImplFL5, base.implFeatureLevel) == 0,
              ".base.implFeatureLevel is not at offset 0 of a NnApiSLDriverImplFL5 struct");
static_assert(offsetof(NnApiSLDriverImplFL6, base.implFeatureLevel) == 0,
              ".base.implFeatureLevel is not at offset 0 of a NnApiSLDriverImplFL6 struct");
static_assert(offsetof(NnApiSLDriverImplFL7, base.implFeatureLevel) == 0,
              ".base.implFeatureLevel is not at offset 0 of a NnApiSLDriverImplFL7 struct");
static_assert(offsetof(NnApiSLDriverImplFL8, base) == 0,
              ".base is not at offset 0 of a NnApiSLDriverImplFL8 struct");
static_assert(offsetof(NnApiSLDriverImplFL8, base.base) == 0,
              ".base.base is not at offset 0 of a NnApiSLDriverImplFL8 struct");
static_assert(offsetof(NnApiSLDriverImplFL8, base.base.implFeatureLevel) == 0,
              ".base.base.implFeatureLevel is not at offset 0 of a NnApiSLDriverImplFL8 struct");
static_assert(offsetof(NnApiSLDriverImpl, implFeatureLevel) == 0,
              ".implFeatureLevel is not at offset 0 of a NnApiSLDriverImpl struct");

static_assert(sizeof(NnApiSLDriverImpl) == sizeof(int64_t), "NnApiSLDriverImpl size changed");

// NOTE: NnApiSLDriverImplFL5 is currently aligned to 8 bytes. In prior versions of the Support
// Library interface, we added a "reserved_placeholder" to force alignment on 32-bit platforms. This
// may need to be done in the future if this struct again becomes unaligned. This would look like:
//  /**
//   * Extra pointer required to align to 8 bytes on 32bit archs.
//   */
//  void (*reserved_placeholder1)();
static_assert(sizeof(NnApiSLDriverImplFL5) == sizeof(int64_t) + 104 * sizeof(void*),
              "NnApiSLDriverImplFL5 size changed");
static_assert(sizeof(NnApiSLDriverImplFL6) == sizeof(int64_t) + 104 * sizeof(void*),
              "NnApiSLDriverImplFL6 size changed");
static_assert(sizeof(NnApiSLDriverImplFL7) == sizeof(int64_t) + 104 * sizeof(void*),
              "NnApiSLDriverImplFL7 size changed");
static_assert(sizeof(NnApiSLDriverImplFL8) == sizeof(NnApiSLDriverImplFL7) + 2 * sizeof(void*),
              "NnApiSLDriverImplFL8 size changed");

static_assert(ANNSHIM_NO_ERROR == 0, "ANNSHIM_NO_ERROR has changed");
static_assert(ANNSHIM_FAILED_TO_LOAD_SL == 1, "ANNSHIM_FAILED_TO_LOAD_SL has changed");
static_assert(ANNSHIM_FAILED_TO_REGISTER_SERVICE == 2,
              "ANNSHIM_FAILED_TO_REGISTER_SERVICE has changed");
static_assert(ANNSHIM_GENERAL_ERROR == 3, "ANNSHIM_GENERAL_ERROR has changed");
static_assert(ANNSHIM_INVALID_ARGUMENT == 4, "ANNSHIM_INVALID_ARGUMENT has changed");

using android::neuralnetworks::shim::registerDevices;
using android::neuralnetworks::shim::RegistrationParams;
using android::neuralnetworks::shim::ShimDeviceInfo;

int ANeuralNetworksShim_registerSupportLibraryService(
        const ANeuralNetworksShimRegistrationParams* registrationParams) {
    if (registrationParams == nullptr) {
        LOG(ERROR) << "Invalid arguments, registrationParams == nullptr ";
        return ANNSHIM_INVALID_ARGUMENT;
    }
    const auto* params = reinterpret_cast<const RegistrationParams*>(registrationParams);

    NnApiSLDriverImpl* const nnapiImpl = params->nnapiSupportLibraryPackage;
    const auto& deviceInfos = params->deviceInfos;
    const uint32_t numberOfListenerThreads = params->numberOfListenerThreads;
    const bool registerAsLazyService = params->registerAsLazyService;
    const bool fallbackToMinimumSupportDevice = params->fallbackToMinimumSupportDevice;

    return static_cast<int>(registerDevices(nnapiImpl, deviceInfos, numberOfListenerThreads,
                                            registerAsLazyService, fallbackToMinimumSupportDevice));
}

int ANeuralNetworksShimDeviceInfo_create(ANeuralNetworksShimDeviceInfo** deviceInfo,
                                         const char* deviceName, const char* serviceName) {
    if (deviceInfo != nullptr) {
        *deviceInfo = nullptr;
    }

    if (deviceName == nullptr) {
        LOG(ERROR) << "Invalid arguments, deviceName passed a nullptr";
        return ANNSHIM_INVALID_ARGUMENT;
    }

    auto result = new (std::nothrow)
            ShimDeviceInfo{.deviceName = std::string(deviceName),
                           .serviceName = (serviceName == nullptr || strlen(serviceName) == 0)
                                                  ? std::string(deviceName)
                                                  : std::string(serviceName)};
    if (result == nullptr) {
        return ANNSHIM_GENERAL_ERROR;
    }
    *deviceInfo = reinterpret_cast<ANeuralNetworksShimDeviceInfo*>(result);
    return ANNSHIM_NO_ERROR;
}

void ANeuralNetworksShimDeviceInfo_free(ANeuralNetworksShimDeviceInfo* deviceInfo) {
    delete reinterpret_cast<ShimDeviceInfo*>(deviceInfo);
}

int ANeuralNetworksShimRegistrationParams_create(
        NnApiSLDriverImpl* nnapiSupportLibraryPackage,
        ANeuralNetworksShimRegistrationParams** outRegistrationParams) {
    if (outRegistrationParams != nullptr) {
        *outRegistrationParams = nullptr;
    }

    if (nnapiSupportLibraryPackage == nullptr) {
        LOG(ERROR) << "Invalid arguments, nnapiSupportLibraryPackage == nullptr ";
        return ANNSHIM_INVALID_ARGUMENT;
    }
    if (outRegistrationParams == nullptr) {
        LOG(ERROR) << "Invalid arguments, outRegistrationParams == nullptr ";
        return ANNSHIM_INVALID_ARGUMENT;
    }

    auto result = new (std::nothrow) RegistrationParams{
            .nnapiSupportLibraryPackage = nnapiSupportLibraryPackage,
            .registerAsLazyService = false,
            .fallbackToMinimumSupportDevice = false,
    };
    if (result == nullptr) {
        return ANNSHIM_GENERAL_ERROR;
    }
    *outRegistrationParams = reinterpret_cast<ANeuralNetworksShimRegistrationParams*>(result);
    return ANNSHIM_NO_ERROR;
}

void ANeuralNetworksShimRegistrationParams_free(
        ANeuralNetworksShimRegistrationParams* registrationParams) {
    delete reinterpret_cast<RegistrationParams*>(registrationParams);
}

int ANeuralNetworksShimRegistrationParams_addDeviceInfo(
        ANeuralNetworksShimRegistrationParams* registrationParams,
        const ANeuralNetworksShimDeviceInfo* deviceInfo) {
    if (registrationParams == nullptr) {
        LOG(ERROR) << "Invalid arguments, registrationParams == nullptr";
        return ANNSHIM_INVALID_ARGUMENT;
    }
    if (deviceInfo == nullptr) {
        LOG(ERROR) << "Invalid arguments, deviceInfo == nullptr";
        return ANNSHIM_INVALID_ARGUMENT;
    }

    auto params = reinterpret_cast<RegistrationParams*>(registrationParams);
    auto info = reinterpret_cast<const ShimDeviceInfo*>(deviceInfo);
    params->deviceInfos.push_back(*info);
    return ANNSHIM_NO_ERROR;
}

int ANeuralNetworksShimRegistrationParams_setNumberOfListenerThreads(
        ANeuralNetworksShimRegistrationParams* registrationParams,
        uint32_t numberOfListenerThreads) {
    if (registrationParams == nullptr) {
        LOG(ERROR) << "Invalid arguments, registrationParams == nullptr";
        return ANNSHIM_INVALID_ARGUMENT;
    }
    if (registrationParams == 0) {
        LOG(ERROR) << "Invalid arguments, numberOfListenerThreads == 0";
        return ANNSHIM_INVALID_ARGUMENT;
    }
    auto params = reinterpret_cast<RegistrationParams*>(registrationParams);
    params->numberOfListenerThreads = numberOfListenerThreads;
    return ANNSHIM_NO_ERROR;
}

int ANeuralNetworksShimRegistrationParams_registerAsLazyService(
        ANeuralNetworksShimRegistrationParams* registrationParams, bool asLazy) {
    if (registrationParams == nullptr) {
        LOG(ERROR) << "Invalid arguments, registrationParams == nullptr";
        return ANNSHIM_INVALID_ARGUMENT;
    }
    auto params = reinterpret_cast<RegistrationParams*>(registrationParams);
    params->registerAsLazyService = asLazy;
    return ANNSHIM_NO_ERROR;
}

int ANeuralNetworksShimRegistrationParams_fallbackToMinimumSupportDevice(
        ANeuralNetworksShimRegistrationParams* registrationParams, bool fallback) {
    if (registrationParams == nullptr) {
        LOG(ERROR) << "Invalid arguments, registrationParams == nullptr";
        return ANNSHIM_INVALID_ARGUMENT;
    }
    auto params = reinterpret_cast<RegistrationParams*>(registrationParams);
    params->fallbackToMinimumSupportDevice = fallback;
    return ANNSHIM_NO_ERROR;
}
