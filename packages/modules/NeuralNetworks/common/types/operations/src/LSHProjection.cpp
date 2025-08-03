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

#include "LSHProjection.h"

#include <vector>

#include "OperationsValidationUtils.h"

namespace android::nn {
namespace lsh_projection {

Result<Version> validate(const IOperationValidationContext* context) {
    NN_RET_CHECK(context->getNumInputs() == 4 && context->getNumOutputs() == 1)
            << context->invalidInOutNumberMessage(4, 1);
    auto inputType = context->getInputType(1);
    NN_RET_CHECK(
            inputType == OperandType::TENSOR_FLOAT16 || inputType == OperandType::TENSOR_FLOAT32 ||
            inputType == OperandType::TENSOR_INT32 || inputType == OperandType::TENSOR_QUANT8_ASYMM)
            << "Unsupported input tensor type for operation " << context->getOperationName();
    auto hashType = context->getInputType(0);
    Version version;
    std::vector<OperandType> inExpectedTypes;
    if (hashType == OperandType::TENSOR_FLOAT16) {
        version = kVersionFeatureLevel3;
        inExpectedTypes = {
                OperandType::TENSOR_FLOAT16,
                inputType,
                OperandType::TENSOR_FLOAT16,
                OperandType::INT32,
        };
    } else if (hashType == OperandType::TENSOR_FLOAT32) {
        version = kVersionFeatureLevel1;
        inExpectedTypes = {
                OperandType::TENSOR_FLOAT32,
                inputType,
                OperandType::TENSOR_FLOAT32,
                OperandType::INT32,
        };
    } else {
        NN_RET_CHECK_FAIL() << "Unsupported hash tensor type for operation "
                            << context->getOperationName();
    }
    std::vector<OperandType> outExpectedTypes = {OperandType::TENSOR_INT32};
    NN_TRY(context->validateOperationOperandTypes(inExpectedTypes, outExpectedTypes));
    return version;
}

}  // namespace lsh_projection

NN_DEFINE_VALIDATION_FUNCTION(LSH_PROJECTION, lsh_projection::validate);

}  // namespace android::nn
