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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_MIRROR_PAD_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_MIRROR_PAD_H

#include "OperationsValidationUtils.h"

namespace android::nn::mirror_pad_op {

constexpr char kOperationName[] = "MIRROR_PAD";

// inputs consist of an n-D tensor to be padded, a 2-D tensor specifying the padding, and a scalar
// specifying the mode
constexpr uint32_t kNumInputs = 3;
constexpr uint32_t kInputTensor = 0;
constexpr uint32_t kInputPaddingTensor = 1;
constexpr uint32_t kInputModeScalar = 2;

constexpr uint32_t kNumOutputs = 1;
constexpr uint32_t kOutputTensor = 0;

constexpr int kModeReflect = 0;
constexpr int kModeSymmetric = 1;

}  // namespace android::nn::mirror_pad_op

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_MIRROR_PAD_H
