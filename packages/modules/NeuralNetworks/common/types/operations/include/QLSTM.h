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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_QLSTM_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_QLSTM_H

#include "OperationsValidationUtils.h"

namespace android::nn::qlstm {

// Inputs
constexpr uint32_t kNumInputs = 32;

constexpr uint32_t kInputTensor = 0;
// Input weight tensors of size: [numUnits, inputSize].
constexpr uint32_t kInputToInputWeightsTensor = 1;
constexpr uint32_t kInputToForgetWeightsTensor = 2;
constexpr uint32_t kInputToCellWeightsTensor = 3;
constexpr uint32_t kInputToOutputWeightsTensor = 4;

// Recurrent weight tensors of size [numUnits, outputSize].
constexpr uint32_t kRecurrentToInputWeightsTensor = 5;
constexpr uint32_t kRecurrentToForgetWeightsTensor = 6;
constexpr uint32_t kRecurrentToCellWeightsTensor = 7;
constexpr uint32_t kRecurrentToOutputWeightsTensor = 8;

// For peephole (optional).
// Cell to input/forget/output weights of size [numUnits].
constexpr uint32_t kCellToInputWeightsTensor = 9;
constexpr uint32_t kCellToForgetWeightsTensor = 10;
constexpr uint32_t kCellToOutputWeightsTensor = 11;

// Gates bias tensors of size [numUnits].
constexpr uint32_t kInputGateBiasTensor = 12;
constexpr uint32_t kForgetGateBiasTensor = 13;
constexpr uint32_t kCellGateBiasTensor = 14;
constexpr uint32_t kOutputGateBiasTensor = 15;

// Projection weight tensor of size [outputSize, numUnits].
constexpr uint32_t kProjectionWeightsTensor = 16;
// Projection bias tensor of size [outputSize].
constexpr uint32_t kProjectionBiasTensor = 17;

// Output from the previous time step, as tensor
// of size [numBatches, outputSize].
constexpr uint32_t kPrevOutputTensor = 18;

// Cell state from the previous time step, as tensor
// of size [numBatches, numUnits].
constexpr uint32_t kPrevCellStateTensor = 19;

// Layer normalization tensors of size [numUnits].
constexpr uint32_t kInputLayerNormTensor = 20;
constexpr uint32_t kForgetLayerNormTensor = 21;
constexpr uint32_t kCellLayerNormTensor = 22;
constexpr uint32_t kOutputLayerNormTensor = 23;

// Clipping.
constexpr uint32_t kCellClip = 24;
constexpr uint32_t kProjectionClip = 25;

// Scales of the result of matmul, i.e. input to layer normalization.
constexpr uint32_t kInputIntermediateScale = 26;
constexpr uint32_t kForgetIntermediateScale = 27;
constexpr uint32_t kCellIntermediateScale = 28;
constexpr uint32_t kOutputIntermediateScale = 29;

// Zero point and scale of hidden state.
constexpr uint32_t kHiddenStateZeroPoint = 30;
constexpr uint32_t kHiddenStateScale = 31;

// Outputs:
constexpr uint32_t kNumOutputs = 3;
constexpr uint32_t kOutputStateOutTensor = 0;
constexpr uint32_t kCellStateOutTensor = 1;
constexpr uint32_t kOutputTensor = 2;

}  // namespace android::nn::qlstm

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_QLSTM_H
