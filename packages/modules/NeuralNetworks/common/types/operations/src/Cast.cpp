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

#include "Cast.h"

#include <functional>
#include <numeric>
#include <vector>

#include "OperationsValidationUtils.h"

namespace android::nn {
namespace cast {

Result<Version> validate(const IOperationValidationContext* context) {
    NN_RET_CHECK(context->getNumInputs() == 1 && context->getNumOutputs() == 1)
            << context->invalidInOutNumberMessage(1, 1);
    auto inputShape = context->getInputShape(0);
    auto outputShape = context->getOutputShape(0);
    auto inputType = inputShape.type;
    auto outputType = outputShape.type;
    Version version;
    std::vector<OperandType> inExpectedTypes;
    std::vector<OperandType> outExpectedTypes;
    if ((inputType == OperandType::TENSOR_FLOAT16 || inputType == OperandType::TENSOR_FLOAT32 ||
         inputType == OperandType::TENSOR_INT32 || inputType == OperandType::TENSOR_QUANT8_ASYMM) &&
        (outputType == OperandType::TENSOR_FLOAT16 || outputType == OperandType::TENSOR_FLOAT32 ||
         outputType == OperandType::TENSOR_INT32 ||
         outputType == OperandType::TENSOR_QUANT8_ASYMM)) {
        version = kVersionFeatureLevel3;
        inExpectedTypes = {inputType};
        outExpectedTypes = {outputType};
    } else if (inputType == OperandType::TENSOR_BOOL8 ||
               inputType == OperandType::TENSOR_QUANT16_ASYMM ||
               inputType == OperandType::TENSOR_QUANT16_SYMM ||
               inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED ||
               inputType == OperandType::TENSOR_QUANT8_SYMM) {
        version = kVersionFeatureLevel4;
        inExpectedTypes = {inputType};
        outExpectedTypes = {inputType};  // Only identity CAST is supported.
    } else {
        NN_RET_CHECK_FAIL() << "Unsupported data type for operation "
                            << context->getOperationName();
    }
    // Validate that output shape is equal to input shape if dimensions
    // are already known.
    auto getNumberOfElements = [](const std::vector<uint32_t>& dims) {
        if (dims.empty()) {
            return 0;
        }
        return std::accumulate(dims.begin(), dims.end(), 1, std::multiplies<>());
    };
    NN_RET_CHECK(inputShape.dimensions.empty() || outputShape.dimensions.empty() ||
                 getNumberOfElements(outputShape.dimensions) == 0 ||
                 inputShape.dimensions == outputShape.dimensions);
    NN_TRY(context->validateOperationOperandTypes(inExpectedTypes, outExpectedTypes));
    return version;
}

}  // namespace cast

NN_DEFINE_VALIDATION_FUNCTION(CAST, cast::validate);

}  // namespace android::nn
