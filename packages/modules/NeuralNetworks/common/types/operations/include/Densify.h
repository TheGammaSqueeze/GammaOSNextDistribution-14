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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_DENSIFY_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_DENSIFY_H

#include <vector>

#include "OperationsValidationUtils.h"

namespace android {

namespace nn {
namespace densify_op {

constexpr uint32_t kMinNumInputs = 5;
constexpr uint32_t kInputTensor = 0;
constexpr uint32_t kInputTravOrder = 1;
constexpr uint32_t kInputBlockMap = 2;
constexpr uint32_t kInputDimFormat = 3;
constexpr uint32_t kInputDimensions = 4;
constexpr uint32_t kInputArrSeg = 5;
constexpr uint32_t kInputArrIdx = 6;
constexpr uint32_t kNumOutputs = 1;
constexpr uint32_t kOutputTensor = 0;
constexpr int32_t DENSE = 0;
constexpr int32_t SPARSE_CSR = 1;

}  // namespace densify_op
}  // namespace nn
}  // namespace android

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_DENSIFY_H
