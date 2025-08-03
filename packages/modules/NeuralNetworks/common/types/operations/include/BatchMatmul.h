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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_BATCH_MATMUL_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_BATCH_MATMUL_H

#include "OperationsValidationUtils.h"

namespace android::nn::batch_matmul_op {

constexpr char kOperationName[] = "BATCH_MATMUL";

constexpr uint32_t kNumInputs = 4;
constexpr uint32_t kInputLHSTensor = 0;
constexpr uint32_t kInputRHSTensor = 1;
constexpr uint32_t kInputLHSAdj = 2;
constexpr uint32_t kInputRHSAdj = 3;

constexpr uint32_t kNumOutputs = 1;
constexpr uint32_t kOutputTensor = 0;

}  // namespace android::nn::batch_matmul_op

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_BATCH_MATMUL_H
