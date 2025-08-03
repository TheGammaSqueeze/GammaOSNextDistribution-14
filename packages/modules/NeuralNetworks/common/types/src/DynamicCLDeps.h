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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_DYNAMIC_CL_DEPS_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_DYNAMIC_CL_DEPS_H

#include <cstdint>

namespace android::nn {

struct CompatibilityLayerMemory {
    int (*create)(const char* name, size_t size);
    size_t (*getSize)(int fd);
};

const CompatibilityLayerMemory& loadCompatibilityLayerMemory();

}  // namespace android::nn

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_DYNAMIC_CL_DEPS_H
