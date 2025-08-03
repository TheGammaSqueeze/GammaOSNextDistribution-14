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

#include "SimpleMath.h"

#include <vector>

#include "OperationsValidationUtils.h"

namespace android::nn {
namespace mean {

Result<Version> validate(const IOperationValidationContext* context) {
    NN_RET_CHECK(context->getNumInputs() == 3 && context->getNumOutputs() == 1)
            << context->invalidInOutNumberMessage(3, 1);
    const auto inputRank = context->getInputShape(0).dimensions.size();
    NN_RET_CHECK_LE(inputRank, 4u)
            << "Unsupported input tensor rank for operation " << context->getOperationName();
    auto inputType = context->getInputType(0);
    Version version;
    if (inputType == OperandType::TENSOR_FLOAT32 || inputType == OperandType::TENSOR_QUANT8_ASYMM) {
        version = kVersionFeatureLevel2;
    } else if (inputType == OperandType::TENSOR_FLOAT16) {
        version = kVersionFeatureLevel3;
    } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
        version = kVersionFeatureLevel4;
    } else {
        NN_RET_CHECK_FAIL() << "Unsupported input tensor type for operation "
                            << context->getOperationName();
    }
    std::vector<OperandType> inExpectedTypes = {inputType, OperandType::TENSOR_INT32,
                                                OperandType::INT32};
    std::vector<OperandType> outExpectedTypes = {inputType};
    NN_TRY(context->validateOperationOperandTypes(inExpectedTypes, outExpectedTypes));
    return version;
}

}  // namespace mean

NN_DEFINE_VALIDATION_FUNCTION(MEAN, mean::validate);

}  // namespace android::nn
