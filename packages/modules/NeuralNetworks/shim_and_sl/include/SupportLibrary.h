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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_SL_SUPPORT_LIBRARY_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_SL_SUPPORT_LIBRARY_H

#include <android-base/logging.h>
#include <dlfcn.h>

#include <cassert>
#include <memory>
#include <string>
#include <variant>

#include "NeuralNetworksSupportLibraryImpl.h"
#include "NeuralNetworksTypes.h"

#ifndef __NNAPI_FL5_MIN_ANDROID_API__
#define __NNAPI_FL5_MIN_ANDROID_API__ __ANDROID_API_S__
#endif

/**
 * Helper struct, wraps different versions of NnApiSLDriverImpl.
 *
 * Owns the .so handle, and will close it in destructor.
 * Sets proper implStructFeatureLevel in constructor.
 *
 * There's expectation that for M>N, NnApiSLDriverImplFL(M) is
 * a strict superset of NnApiSLDriverImplFL(N), and *NnApiSLDriverImplFL(M) can
 * be reinterpret_cast to *NnApiSLDriverImplFL(N) safely.
 *
 * The base->implFeatureLevel is set to the actual Feature Level
 * implemented by the SLDriverImpl,
 */
struct NnApiSupportLibrary {
    NnApiSupportLibrary(const NnApiSLDriverImplFL5& impl, void* libHandle)
        : libHandle(libHandle), impl(impl) {}
    // No need for ctor below since FL6&7 are typedefs of FL5
    // NnApiSupportLibrary(const NnApiSLDriverImplFL6& impl, void* libHandle): impl(impl),
    // NnApiSupportLibrary(const NnApiSLDriverImplFL7& impl, void* libHandle): impl(impl),
    // libHandle(libHandle) {}
    NnApiSupportLibrary(const NnApiSLDriverImplFL8& impl, void* libHandle)
        : libHandle(libHandle), impl(impl) {}
    ~NnApiSupportLibrary() {
        if (libHandle != nullptr) {
            dlclose(libHandle);
            libHandle = nullptr;
        }
    }

    int64_t getFeatureLevel() const { return getFL5()->base.implFeatureLevel; }
    const NnApiSLDriverImplFL5* getFL5() const {
        return std::visit(
                [](auto&& impl) { return reinterpret_cast<const NnApiSLDriverImplFL5*>(&impl); },
                impl);
    }
    const NnApiSLDriverImplFL6* getFL6() const {
        CHECK_GE(getFeatureLevel(), ANEURALNETWORKS_FEATURE_LEVEL_6);
        return std::visit(
                [](auto&& impl) { return reinterpret_cast<const NnApiSLDriverImplFL6*>(&impl); },
                impl);
    }
    const NnApiSLDriverImplFL7* getFL7() const {
        assert(getFeatureLevel() >= ANEURALNETWORKS_FEATURE_LEVEL_7);
        return std::visit(
                [](auto&& impl) { return reinterpret_cast<const NnApiSLDriverImplFL7*>(&impl); },
                impl);
    }
    const NnApiSLDriverImplFL8* getFL8() const {
        assert(getFeatureLevel() >= ANEURALNETWORKS_FEATURE_LEVEL_8);
        return std::visit(
                [](auto&& impl) { return reinterpret_cast<const NnApiSLDriverImplFL8*>(&impl); },
                impl);
    }

    void* libHandle = nullptr;
    // NnApiSLDriverImplFL[6-7] is a typedef of FL5, can't be explicitly specified.
    std::variant<NnApiSLDriverImplFL5,
                 /*NnApiSLDriverImplFL6, NnApiSLDriverImplFL7,*/ NnApiSLDriverImplFL8>
            impl;
};

/**
 * Loads the NNAPI support library.
 * The NnApiSupportLibrary structure is filled with all the pointers. If one
 * function doesn't exist, a null pointer is stored.
 */
std::unique_ptr<const NnApiSupportLibrary> loadNnApiSupportLibrary(const std::string& libName);
std::unique_ptr<const NnApiSupportLibrary> loadNnApiSupportLibrary(void* libHandle);

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_SL_SUPPORT_LIBRARY_H
