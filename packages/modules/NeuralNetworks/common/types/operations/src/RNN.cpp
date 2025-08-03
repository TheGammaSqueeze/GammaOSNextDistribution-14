/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include "RNN.h"

#include <vector>

#include "OperationsValidationUtils.h"

namespace android::nn {
namespace rnn {

Result<Version> validate(const IOperationValidationContext* context) {
    NN_RET_CHECK(context->getNumInputs() == 6 && context->getNumOutputs() == 2)
            << context->invalidInOutNumberMessage(6, 2);
    OperandType inputType = context->getInputType(0);
    Version version;
    std::vector<OperandType> inExpectedTypes;
    std::vector<OperandType> outExpectedTypes;
    if (inputType == OperandType::TENSOR_FLOAT32) {
        version = kVersionFeatureLevel1;
        inExpectedTypes = {
                OperandType::TENSOR_FLOAT32, OperandType::TENSOR_FLOAT32,
                OperandType::TENSOR_FLOAT32, OperandType::TENSOR_FLOAT32,
                OperandType::TENSOR_FLOAT32, OperandType::INT32,
        };
        outExpectedTypes = {
                OperandType::TENSOR_FLOAT32,
                OperandType::TENSOR_FLOAT32,
        };
    } else if (inputType == OperandType::TENSOR_FLOAT16) {
        version = kVersionFeatureLevel3;
        inExpectedTypes = {
                OperandType::TENSOR_FLOAT16, OperandType::TENSOR_FLOAT16,
                OperandType::TENSOR_FLOAT16, OperandType::TENSOR_FLOAT16,
                OperandType::TENSOR_FLOAT16, OperandType::INT32,
        };
        outExpectedTypes = {
                OperandType::TENSOR_FLOAT16,
                OperandType::TENSOR_FLOAT16,
        };
    } else {
        NN_RET_CHECK_FAIL() << "Unsupported input tensor type for operation "
                            << context->getOperationName();
    }
    NN_TRY(context->validateOperationOperandTypes(inExpectedTypes, outExpectedTypes));
    return version;
}

}  // namespace rnn

NN_DEFINE_VALIDATION_FUNCTION(RNN, rnn::validate);

}  // namespace android::nn
