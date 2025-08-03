/*
 * Copyright (C) 2020 The Android Open Source Project
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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_MODEL_ARCH_HASHER_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_MODEL_ARCH_HASHER_H

#include <nnapi/Types.h>

namespace android::nn {

// Generated hash from canonical model operations and operands.
// Weights do not affect this hash.
bool calcModelArchHash(const Model& model, uint8_t* data);

static const int BYTE_SIZE_OF_MODEL_ARCH_HASH = 32;

}  // namespace android::nn

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_MODEL_ARCH_HASHER_H
