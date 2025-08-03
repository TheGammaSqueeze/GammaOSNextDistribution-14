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

#include "BidirectionalSequenceRNN.h"

#include <vector>

#include "OperationsValidationUtils.h"

namespace android::nn {
namespace bidirectional_sequence_rnn {

Result<Version> validate(const IOperationValidationContext* context) {
    NN_RET_CHECK_EQ(context->getNumInputs(), kNumInputs);
    // Exact number is dependent on the mergeOutputs parameter and checked
    // during preparation.
    const uint32_t numOutputs = context->getNumOutputs();
    NN_RET_CHECK(numOutputs == kNumOutputs || numOutputs == kNumOutputsMerged ||
                 numOutputs == kNumOutputsWithState || numOutputs == kNumOutputsMergedWithState);

    OperandType inputType = context->getInputType(kInputTensor);
    if (inputType != OperandType::TENSOR_FLOAT16 && inputType != OperandType::TENSOR_FLOAT32) {
        return NN_ERROR() << "Unsupported input operand type for UNIDIRECTIONAL_SEQUENCE_RNN op: "
                          << inputType;
    }
    NN_RET_CHECK(validateInputTypes(
            context, {inputType, inputType, inputType, inputType, inputType, inputType, inputType,
                      inputType, inputType, inputType, inputType, inputType, OperandType::INT32,
                      OperandType::BOOL, OperandType::BOOL}));

    std::vector<OperandType> outExpectedTypes(numOutputs, inputType);
    NN_RET_CHECK(validateOutputTypes(context, outExpectedTypes));

    Version minSupportedVersion = kVersionFeatureLevel3;
    if (numOutputs == kNumOutputsWithState || numOutputs == kNumOutputsMergedWithState) {
        minSupportedVersion = kVersionFeatureLevel4;
    }
    return minSupportedVersion;
}

}  // namespace bidirectional_sequence_rnn

NN_DEFINE_VALIDATION_FUNCTION(BIDIRECTIONAL_SEQUENCE_RNN, bidirectional_sequence_rnn::validate);

}  // namespace android::nn
