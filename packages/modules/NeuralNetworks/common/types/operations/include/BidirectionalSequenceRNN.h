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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_BIDIRECTIONAL_SEQUENCE_RNN_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_BIDIRECTIONAL_SEQUENCE_RNN_H

#include "OperationsValidationUtils.h"

namespace android::nn::bidirectional_sequence_rnn {

constexpr uint32_t kNumInputs = 15;
constexpr uint32_t kInputTensor = 0;
// Forward cell tensors
constexpr uint32_t kFwWeightsTensor = 1;
constexpr uint32_t kFwRecurrentWeightsTensor = 2;
constexpr uint32_t kFwBiasTensor = 3;
constexpr uint32_t kFwHiddenStateTensor = 4;
// Backward cell tensors
constexpr uint32_t kBwWeightsTensor = 5;
constexpr uint32_t kBwRecurrentWeightsTensor = 6;
constexpr uint32_t kBwBiasTensor = 7;
constexpr uint32_t kBwHiddenStateTensor = 8;
// Auxiliary inputs
constexpr uint32_t kAuxInputTensor = 9;       // optional
constexpr uint32_t kFwAuxWeightsTensor = 10;  // optional
constexpr uint32_t kBwAuxWeightsTensor = 11;  // optional
// Cell parameters
constexpr uint32_t kActivationParam = 12;
constexpr uint32_t kTimeMajorParam = 13;
constexpr uint32_t kMergeOutputsParam = 14;

constexpr uint32_t kNumOutputs = 2;
constexpr uint32_t kNumOutputsMerged = 1;
constexpr uint32_t kNumOutputsWithState = 4;
constexpr uint32_t kNumOutputsMergedWithState = 3;

constexpr uint32_t kFwOutputTensor = 0;
constexpr uint32_t kBwOutputTensor = 1;  // Only if mergeOutputs parameter is false
constexpr uint32_t kFwOutputHiddenStateTensor = 2;
constexpr uint32_t kBwOutputHiddenStateTensor = 3;

}  // namespace android::nn::bidirectional_sequence_rnn

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_BIDIRECTIONAL_SEQUENCE_RNN_H
