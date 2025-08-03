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

#include "SupportLibrary.h"

#include <android-base/logging.h>

#include <dlfcn.h>

#include <cstring>
#include <memory>
#include <string>

std::unique_ptr<const NnApiSupportLibrary> loadNnApiSupportLibrary(const std::string& libName) {
    void* libHandle = dlopen(libName.c_str(), RTLD_LAZY | RTLD_LOCAL | RTLD_NODELETE);
    if (libHandle == nullptr) {
        LOG(ERROR) << "nnapi error: unable to open library " << libName.c_str() << " " << dlerror();
        return nullptr;
    }

    auto result = loadNnApiSupportLibrary(libHandle);
    if (!result) {
        dlclose(libHandle);
    }
    return result;
}

std::unique_ptr<const NnApiSupportLibrary> loadNnApiSupportLibrary(void* libHandle) {
    NnApiSLDriverImpl* (*getSlDriverImpl)();
    getSlDriverImpl = reinterpret_cast<decltype(getSlDriverImpl)>(
            dlsym(libHandle, "ANeuralNetworks_getSLDriverImpl"));
    if (getSlDriverImpl == nullptr) {
        LOG(ERROR) << "Failed to find ANeuralNetworks_getSLDriverImpl symbol";
        return nullptr;
    }

    NnApiSLDriverImpl* impl = getSlDriverImpl();
    if (impl == nullptr) {
        LOG(ERROR) << "ANeuralNetworks_getSLDriverImpl returned nullptr";
        return nullptr;
    }

    if (impl->implFeatureLevel < ANEURALNETWORKS_FEATURE_LEVEL_5) {
        LOG(ERROR) << "Unsupported NnApiSLDriverImpl->implFeatureLevel: " << impl->implFeatureLevel;
        return nullptr;
    }

    if (impl->implFeatureLevel == ANEURALNETWORKS_FEATURE_LEVEL_5) {
        return std::make_unique<NnApiSupportLibrary>(*reinterpret_cast<NnApiSLDriverImplFL5*>(impl),
                                                     libHandle);
    }
    if (impl->implFeatureLevel == ANEURALNETWORKS_FEATURE_LEVEL_6) {
        return std::make_unique<NnApiSupportLibrary>(*reinterpret_cast<NnApiSLDriverImplFL6*>(impl),
                                                     libHandle);
    }
    if (impl->implFeatureLevel == ANEURALNETWORKS_FEATURE_LEVEL_7) {
        return std::make_unique<NnApiSupportLibrary>(*reinterpret_cast<NnApiSLDriverImplFL7*>(impl),
                                                     libHandle);
    }
    if (impl->implFeatureLevel >= ANEURALNETWORKS_FEATURE_LEVEL_8) {
        return std::make_unique<NnApiSupportLibrary>(*reinterpret_cast<NnApiSLDriverImplFL8*>(impl),
                                                     libHandle);
    }

    return nullptr;
}
