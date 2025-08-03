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

#define LOG_TAG "ShimServiceSample"

#include <android-base/logging.h>
#include <android-base/scopeguard.h>
#include <dlfcn.h>

#include "NeuralNetworksShim.h"
#include "SupportLibrarySymbols.h"

int main() {
    NnApiSLDriverImpl* impl = ANeuralNetworks_getSLDriverImpl();
    if (impl == nullptr) {
        LOG(ERROR) << "ANeuralNetworks_getSLDriverImpl returned nullptr";
        return EXIT_FAILURE;
    }

    ANeuralNetworksShimDeviceInfo* deviceInfo;
    ANeuralNetworksShimDeviceInfo_create(&deviceInfo,
                                         /*deviceName=*/"nnapi-sample_sl",
                                         /*serviceName=*/"nnapi-sample_sl_shim");
    const auto guardDeviceInfo = android::base::make_scope_guard(
            [deviceInfo] { ANeuralNetworksShimDeviceInfo_free(deviceInfo); });

    ANeuralNetworksShimRegistrationParams* params;
    ANeuralNetworksShimRegistrationParams_create(impl, &params);
    const auto guardParams = android::base::make_scope_guard(
            [params] { ANeuralNetworksShimRegistrationParams_free(params); });
    ANeuralNetworksShimRegistrationParams_addDeviceInfo(params, deviceInfo);
    // The default is 15, use more only if there's more devices exposed.
    ANeuralNetworksShimRegistrationParams_setNumberOfListenerThreads(params, 15);
    ANeuralNetworksShimRegistrationParams_registerAsLazyService(params, /*asLazy=*/false);
    ANeuralNetworksShimRegistrationParams_fallbackToMinimumSupportDevice(params,
                                                                         /*fallback=*/false);

    auto result = ANeuralNetworksShim_registerSupportLibraryService(params);

    LOG(ERROR) << "ANeuralNetworksShim_registerSupportLibraryService returned with error status: "
               << result;

    return EXIT_FAILURE;
}
