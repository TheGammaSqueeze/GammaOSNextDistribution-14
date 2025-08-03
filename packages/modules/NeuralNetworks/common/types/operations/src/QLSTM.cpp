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

#include "QLSTM.h"

#include <vector>

#include "OperationsValidationUtils.h"

namespace android::nn {
namespace qlstm {

Result<Version> validate(const IOperationValidationContext* context) {
    NN_RET_CHECK_EQ(context->getNumInputs(), kNumInputs);
    NN_RET_CHECK_EQ(context->getNumOutputs(), kNumOutputs);

    std::vector<OperandType> inExpectedTypes;
    // Input.
    inExpectedTypes.push_back(OperandType::TENSOR_QUANT8_ASYMM_SIGNED);
    // Input-to-* and recurrent-to-* weights.
    for (int i = 0; i < 8; ++i) {
        inExpectedTypes.push_back(OperandType::TENSOR_QUANT8_SYMM);
    }
    // Cell-to-* weights.
    for (int i = 0; i < 3; ++i) {
        inExpectedTypes.push_back(OperandType::TENSOR_QUANT16_SYMM);
    }
    // Gate biases.
    for (int i = 0; i < 4; ++i) {
        inExpectedTypes.push_back(OperandType::TENSOR_INT32);
    }
    // Projection.
    inExpectedTypes.push_back(OperandType::TENSOR_QUANT8_SYMM);
    inExpectedTypes.push_back(OperandType::TENSOR_INT32);
    // Previous output.
    inExpectedTypes.push_back(OperandType::TENSOR_QUANT8_ASYMM_SIGNED);
    // Previous cell state.
    inExpectedTypes.push_back(OperandType::TENSOR_QUANT16_SYMM);
    // Layer norm weights
    for (int i = 0; i < 4; ++i) {
        inExpectedTypes.push_back(OperandType::TENSOR_QUANT16_SYMM);
    }
    // Cell/projection clipping and scales of intermediate results at the 4 gates.
    for (int i = 0; i < 6; ++i) {
        inExpectedTypes.push_back(OperandType::FLOAT32);
    }
    // Zero point and scale of the hidden state.
    inExpectedTypes.push_back(OperandType::INT32);
    inExpectedTypes.push_back(OperandType::FLOAT32);
    NN_RET_CHECK(validateInputTypes(context, inExpectedTypes));

    std::vector<OperandType> outExpectedTypes;
    // Output state (out).
    outExpectedTypes.push_back(OperandType::TENSOR_QUANT8_ASYMM_SIGNED);
    // Cell state (out).
    outExpectedTypes.push_back(OperandType::TENSOR_QUANT16_SYMM);
    // Output.
    outExpectedTypes.push_back(OperandType::TENSOR_QUANT8_ASYMM_SIGNED);
    NN_RET_CHECK(validateOutputTypes(context, outExpectedTypes));

    return kVersionFeatureLevel4;
}

}  // namespace qlstm

NN_DEFINE_VALIDATION_FUNCTION(QUANTIZED_LSTM, qlstm::validate);

}  // namespace android::nn
