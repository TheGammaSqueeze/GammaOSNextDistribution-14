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

#include "BidirectionalSequenceLSTM.h"

#include <vector>

#include "OperationsValidationUtils.h"

namespace android::nn {
namespace bidirectional_sequence_lstm {

Result<Version> validate(const IOperationValidationContext* context) {
    const uint32_t kNumOutputs = 2;
    const uint32_t kNumOutputsMerged = 1;
    const uint32_t kNumOutputsWithState = 6;
    const uint32_t kNumOutputsMergedWithState = 5;
    NN_RET_CHECK(context->getNumInputs() == 61 &&
                 (context->getNumOutputs() == kNumOutputs ||
                  context->getNumOutputs() == kNumOutputsMerged ||
                  context->getNumOutputs() == kNumOutputsWithState ||
                  context->getNumOutputs() == kNumOutputsMergedWithState))
            << "Invalid number of input operands (" << context->getNumInputs()
            << ", expected 61) or output operands (" << context->getNumOutputs()
            << ", expected 1, 2, 5 or 6) for operation " << context->getOperationName();

    std::vector<OperandType> inExpectedTypes;
    auto inputType = context->getInputType(0);
    NN_RET_CHECK(inputType == OperandType::TENSOR_FLOAT32 ||
                 inputType == OperandType::TENSOR_FLOAT16)
            << "Unsupported input tensor type for operation " << context->getOperationName();

    inExpectedTypes = {};
    for (int i = 0; i < 48; ++i) {
        inExpectedTypes.push_back(inputType);
    }
    inExpectedTypes.push_back(OperandType::INT32);
    inExpectedTypes.push_back(inputType == OperandType::TENSOR_FLOAT32 ? OperandType::FLOAT32
                                                                       : OperandType::FLOAT16);
    inExpectedTypes.push_back(inputType == OperandType::TENSOR_FLOAT32 ? OperandType::FLOAT32
                                                                       : OperandType::FLOAT16);
    inExpectedTypes.push_back(OperandType::BOOL);
    inExpectedTypes.push_back(OperandType::BOOL);
    for (int i = 0; i < 8; ++i) {
        inExpectedTypes.push_back(inputType);
    }

    Version version = kVersionFeatureLevel3;
    if (context->getNumOutputs() == kNumOutputsWithState ||
        context->getNumOutputs() == kNumOutputsMergedWithState) {
        version = kVersionFeatureLevel4;
    }
    std::vector<OperandType> outExpectedTypes(context->getNumOutputs(), inputType);
    NN_TRY(context->validateOperationOperandTypes(inExpectedTypes, outExpectedTypes));
    return version;
}

}  // namespace bidirectional_sequence_lstm

NN_DEFINE_VALIDATION_FUNCTION(BIDIRECTIONAL_SEQUENCE_LSTM, bidirectional_sequence_lstm::validate);

}  // namespace android::nn
