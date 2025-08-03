/*
 * Copyright (C) 2018 The Android Open Source Project
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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_STRIDED_SLICE_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_STRIDED_SLICE_H

#include "OperationsValidationUtils.h"

namespace android::nn::strided_slice {

constexpr uint32_t kNumInputs = 7;
constexpr uint32_t kInputTensor = 0;
constexpr uint32_t kBeginTensor = 1;
constexpr uint32_t kEndTensor = 2;
constexpr uint32_t kStridesTensor = 3;
constexpr uint32_t kBeginMask = 4;
constexpr uint32_t kEndMask = 5;
constexpr uint32_t kShrinkAxisMask = 6;

constexpr uint32_t kNumOutputs = 1;
constexpr uint32_t kOutputTensor = 0;

}  // namespace android::nn::strided_slice

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_STRIDED_SLICE_H
