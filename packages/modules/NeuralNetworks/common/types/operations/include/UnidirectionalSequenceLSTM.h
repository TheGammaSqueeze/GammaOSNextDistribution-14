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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_UNIDIRECTIONAL_SEQUENCE_LSTM_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_UNIDIRECTIONAL_SEQUENCE_LSTM_H

#include "OperationsValidationUtils.h"

namespace android::nn::unidirectional_sequence_lstm {

// Inputs
constexpr uint32_t kNumInputs = 28;

// Input tensor of size {max_time, n_batch, n_input}
constexpr uint32_t kInputTensor = 0;

// Input weight tensors of size: {n_cell, n_input}
constexpr uint32_t kInputToInputWeightsTensor = 1;  // Optional
constexpr uint32_t kInputToForgetWeightsTensor = 2;
constexpr uint32_t kInputToCellWeightsTensor = 3;
constexpr uint32_t kInputToOutputWeightsTensor = 4;

// Recurrent weight tensors of size {n_cell, n_output}
constexpr uint32_t kRecurrentToInputWeightsTensor = 5;  // Optional
constexpr uint32_t kRecurrentToForgetWeightsTensor = 6;
constexpr uint32_t kRecurrentToCellWeightsTensor = 7;
constexpr uint32_t kRecurrentToOutputWeightsTensor = 8;

// Peephole weights tensors of size {n_cell}, representing a diagonal matrix.
constexpr uint32_t kCellToInputWeightsTensor = 9;    // Optional
constexpr uint32_t kCellToForgetWeightsTensor = 10;  // Optional
constexpr uint32_t kCellToOutputWeightsTensor = 11;  // Optional

// Gates bias tensors of size {n_cell}
constexpr uint32_t kInputGateBiasTensor = 12;  // Optional
constexpr uint32_t kForgetGateBiasTensor = 13;
constexpr uint32_t kCellGateBiasTensor = 14;
constexpr uint32_t kOutputGateBiasTensor = 15;

// Projection weight tensor of size {n_output, n_cell}
constexpr uint32_t kProjectionWeightsTensor = 16;  // Optional
// Projection bias tensor of size {n_output}
constexpr uint32_t kProjectionBiasTensor = 17;  // Optional

// Input from the output of the previous step, tensor of size {batch_size, n_output}
constexpr uint32_t kOutputStateInTensor = 18;
// Input from the cell state of the previous step, tensor of size {batch_size, n_cell}
constexpr uint32_t kCellStateInTensor = 19;

constexpr uint32_t kActivationParam = 20;
constexpr uint32_t kCellClipParam = 21;
constexpr uint32_t kProjClipParam = 22;
constexpr uint32_t kTimeMajorParam = 23;

// Layer norm weights tensors of size {n_cell}, representing a diagonal matrix.
constexpr uint32_t kInputLayerNormWeightsTensor = 24;   // Optional
constexpr uint32_t kForgetLayerNormWeightsTensor = 25;  // Optional
constexpr uint32_t kCellLayerNormWeightsTensor = 26;    // Optional
constexpr uint32_t kOutputLayerNormWeightsTensor = 27;  // Optional

// Output tensors.
constexpr uint32_t kNumOutputs = 1;
constexpr uint32_t kNumOutputsWithState = 3;

constexpr uint32_t kOutputTensor = 0;
constexpr uint32_t kOutputStateOutTensor = 1;
constexpr uint32_t kCellStateOutTensor = 2;

}  // namespace android::nn::unidirectional_sequence_lstm

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_UNIDIRECTIONAL_SEQUENCE_LSTM_H
