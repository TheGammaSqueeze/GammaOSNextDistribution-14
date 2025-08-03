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

#include "MaximumMinimum.h"

#include <vector>

#include "OperationsValidationUtils.h"

namespace android::nn {
namespace maximum_minimum {

Result<Version> validate(const IOperationValidationContext* context) {
    NN_RET_CHECK(context->getNumInputs() == 2 && context->getNumOutputs() == 1)
            << context->invalidInOutNumberMessage(2, 1);
    std::vector<OperandType> inExpectedTypes;
    std::vector<OperandType> outExpectedTypes;
    OperandType inputType = context->getInputType(0);
    if (inputType == OperandType::TENSOR_FLOAT16 || inputType == OperandType::TENSOR_FLOAT32 ||
        inputType == OperandType::TENSOR_INT32 || inputType == OperandType::TENSOR_QUANT8_ASYMM ||
        inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
        inExpectedTypes = {inputType, inputType};
        outExpectedTypes = {inputType};
    } else {
        NN_RET_CHECK_FAIL() << "Unsupported input tensor type for operation "
                            << context->getOperationName();
    }
    Version version;
    if (inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
        version = kVersionFeatureLevel4;
    } else {
        version = kVersionFeatureLevel3;
    }
    NN_TRY(context->validateOperationOperandTypes(inExpectedTypes, outExpectedTypes));
    return version;
}

}  // namespace maximum_minimum

NN_DEFINE_VALIDATION_FUNCTION(MAXIMUM, maximum_minimum::validate);
NN_DEFINE_VALIDATION_FUNCTION(MINIMUM, maximum_minimum::validate);

}  // namespace android::nn
