/*
 * Copyright (C) 2017 The Android Open Source Project
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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_FULLY_CONNECTED_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_FULLY_CONNECTED_H

#include "OperationsValidationUtils.h"

namespace android::nn::fully_connected {

constexpr char kOperationName[] = "FULLY_CONNECTED";

constexpr uint32_t kNumInputs = 4;
constexpr uint32_t kInputTensor = 0;
constexpr uint32_t kWeightsTensor = 1;
constexpr uint32_t kBiasTensor = 2;
constexpr uint32_t kActivationScalar = 3;

constexpr uint32_t kNumOutputs = 1;
constexpr uint32_t kOutputTensor = 0;

bool validateShapes(const Shape& input, const Shape& weights, const Shape& bias,
                    Shape* output = nullptr);

}  // namespace android::nn::fully_connected

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_FULLY_CONNECTED_H
