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

#include "ArgMinMax.h"

#include "OperationsValidationUtils.h"

namespace android::nn {
namespace arg_min_max {

Result<Version> validate(const IOperationValidationContext* context) {
    NN_RET_CHECK(context->getNumInputs() == 2 && context->getNumOutputs() == 1)
            << context->invalidInOutNumberMessage(2, 1);
    auto inputType = context->getInputType(0);
    std::vector<OperandType> inExpectedTypes;
    std::vector<OperandType> outExpectedTypes;
    if (inputType == OperandType::TENSOR_FLOAT16 || inputType == OperandType::TENSOR_FLOAT32 ||
        inputType == OperandType::TENSOR_INT32 || inputType == OperandType::TENSOR_QUANT8_ASYMM ||
        inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
        inExpectedTypes = {inputType, OperandType::INT32};
        outExpectedTypes = {OperandType::TENSOR_INT32};
    } else {
        NN_RET_CHECK_FAIL() << "Unsupported input tensor type for operation "
                            << context->getOperationName();
    }
    NN_TRY(context->validateOperationOperandTypes(inExpectedTypes, outExpectedTypes));
    return kVersionFeatureLevel3;
}

}  // namespace arg_min_max

NN_DEFINE_VALIDATION_FUNCTION(ARGMIN, arg_min_max::validate);
NN_DEFINE_VALIDATION_FUNCTION(ARGMAX, arg_min_max::validate);

}  // namespace android::nn
