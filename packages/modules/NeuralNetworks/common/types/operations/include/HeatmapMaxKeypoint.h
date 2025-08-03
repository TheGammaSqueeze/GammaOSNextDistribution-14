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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_HEATMAP_MAX_KEYPOINT_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_HEATMAP_MAX_KEYPOINT_H

#include "OperationsValidationUtils.h"

namespace android::nn::heatmap_max_keypoint {

constexpr char kOperationName[] = "HEATMAP_MAX_KEYPOINT";

constexpr uint32_t kNumInputs = 3;
constexpr uint32_t kHeatmapTensor = 0;
constexpr uint32_t kBoxesTensor = 1;
constexpr uint32_t kLayoutScalar = 2;

constexpr uint32_t kNumOutputs = 2;
constexpr uint32_t kOutputScoreTensor = 0;
constexpr uint32_t kOutputKeypointTensor = 1;

}  // namespace android::nn::heatmap_max_keypoint

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_HEATMAP_MAX_KEYPOINT_H
