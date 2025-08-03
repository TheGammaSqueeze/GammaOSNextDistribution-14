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

#include "LogSoftmax.h"

#include <vector>

#include "OperationsValidationUtils.h"

namespace android::nn {
namespace log_softmax {

Result<Version> validate(const IOperationValidationContext* context) {
    NN_RET_CHECK_EQ(context->getNumInputs(), kNumInputs);
    NN_RET_CHECK_EQ(context->getNumOutputs(), kNumOutputs);
    OperandType inputType = context->getInputType(kInputTensor);
    std::vector<OperandType> inExpectedTypes;
    std::vector<OperandType> outExpectedTypes;
    if (inputType == OperandType::TENSOR_FLOAT32) {
        inExpectedTypes = {OperandType::TENSOR_FLOAT32, OperandType::FLOAT32, OperandType::INT32};
        outExpectedTypes = {OperandType::TENSOR_FLOAT32};
    } else if (inputType == OperandType::TENSOR_FLOAT16) {
        inExpectedTypes = {OperandType::TENSOR_FLOAT16, OperandType::FLOAT16, OperandType::INT32};
        outExpectedTypes = {OperandType::TENSOR_FLOAT16};
    } else {
        return NN_ERROR() << "Unsupported input tensor type for operation " << kOperationName;
    }
    NN_RET_CHECK(validateInputTypes(context, inExpectedTypes));
    NN_RET_CHECK(validateOutputTypes(context, outExpectedTypes));
    return kVersionFeatureLevel3;
}

}  // namespace log_softmax

NN_DEFINE_VALIDATION_FUNCTION(LOG_SOFTMAX, log_softmax::validate);

}  // namespace android::nn
