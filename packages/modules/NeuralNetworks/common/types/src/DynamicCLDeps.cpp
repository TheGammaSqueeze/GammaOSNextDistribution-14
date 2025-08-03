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

#define LOG_TAG "DynamicCLDeps"

#include "DynamicCLDeps.h"

#include <android-base/logging.h>
#include <dlfcn.h>

namespace android::nn {
namespace {

void* loadFunction(void* handle, const char* name) {
    CHECK(handle != nullptr);
    void* fn = dlsym(handle, name);
    CHECK(fn != nullptr) << "Unable to open function " << name << ": " << dlerror();
    return fn;
}

#define NN_LOAD_FUNCTION(name, symbol) \
    impl.name = reinterpret_cast<decltype(impl.name)>(loadFunction(handle, #symbol));

const CompatibilityLayerMemory loadCompatibilityLayerMemoryHelper() {
    CompatibilityLayerMemory impl = {};

    // libandroid.so is NOT accessible for non-NDK apps and
    // libcutils.so  is NOT accessible for NDK apps.
    // Hence we try to load one or the other in order to cover both cases.
    void* handle = dlopen("libandroid.so", RTLD_LAZY | RTLD_LOCAL);
    if (handle != nullptr) {
        NN_LOAD_FUNCTION(create, ASharedMemory_create);
        NN_LOAD_FUNCTION(getSize, ASharedMemory_getSize);
    } else {
        handle = dlopen("libcutils.so", RTLD_LAZY | RTLD_LOCAL);
        CHECK(handle != nullptr) << "Unable to open either libandroid.so or libcutils.so: "
                                 << dlerror();
        NN_LOAD_FUNCTION(create, ashmem_create_region);
        NN_LOAD_FUNCTION(getSize, ashmem_get_size_region);
    }

    return impl;
}

#undef NN_LOAD_FUNCTION

}  // namespace

const CompatibilityLayerMemory& loadCompatibilityLayerMemory() {
    static const CompatibilityLayerMemory impl = loadCompatibilityLayerMemoryHelper();
    return impl;
}

}  // namespace android::nn
