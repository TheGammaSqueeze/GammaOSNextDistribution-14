/*
 * Copyright (C) 2019 The Android Open Source Project
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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_RESIZE_IMAGE_OPS_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_RESIZE_IMAGE_OPS_H

#include "OperationsValidationUtils.h"

namespace android::nn::resize_image {

constexpr uint32_t kNumInputs = 4;
constexpr uint32_t kInputTensor = 0;
// The following two scalars represent output shape if INT32, scale if floating point.
constexpr uint32_t kOutputWidthParamScalar = 1;
constexpr uint32_t kOutputHeightParamScalar = 2;
constexpr uint32_t kLayoutScalar = 3;
constexpr uint32_t kNumOptionalInputs = 2;
constexpr uint32_t kAlignCornersScalar = 4;
constexpr uint32_t kHalfPixelCentersScalar = 5;

constexpr uint32_t kNumOutputs = 1;
constexpr uint32_t kOutputTensor = 0;

}  // namespace android::nn::resize_image

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_RESIZE_IMAGE_OPS_H
