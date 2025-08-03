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

#include "EmbeddingLookup.h"

#include <vector>

#include "OperationsValidationUtils.h"

namespace android::nn {
namespace embedding_lookup {

Result<Version> validate(const IOperationValidationContext* context) {
    NN_RET_CHECK(context->getNumInputs() == 2 && context->getNumOutputs() == 1)
            << context->invalidInOutNumberMessage(2, 1);
    auto inputType = context->getInputType(1);
    NN_RET_CHECK(inputType == OperandType::TENSOR_FLOAT16 ||
                 inputType == OperandType::TENSOR_FLOAT32 ||
                 inputType == OperandType::TENSOR_INT32 ||
                 inputType == OperandType::TENSOR_QUANT8_ASYMM ||
                 inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED)
            << "Unsupported input tensor type for operation " << context->getOperationName();
    Version version;
    std::vector<OperandType> inExpectedTypes = {OperandType::TENSOR_INT32, inputType};
    std::vector<OperandType> outExpectedTypes = {inputType};
    if (inputType == OperandType::TENSOR_FLOAT16 ||
        inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
        version = kVersionFeatureLevel4;
    } else if (inputType == OperandType::TENSOR_INT32 ||
               inputType == OperandType::TENSOR_QUANT8_ASYMM) {
        version = kVersionFeatureLevel3;
    } else {
        version = kVersionFeatureLevel1;
    }
    NN_TRY(context->validateOperationOperandTypes(inExpectedTypes, outExpectedTypes));
    return version;
}

}  // namespace embedding_lookup

NN_DEFINE_VALIDATION_FUNCTION(EMBEDDING_LOOKUP, embedding_lookup::validate);

}  // namespace android::nn
