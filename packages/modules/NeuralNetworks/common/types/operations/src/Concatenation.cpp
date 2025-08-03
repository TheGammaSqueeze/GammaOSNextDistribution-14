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

#include "Concatenation.h"

#include <vector>

#include "OperationsValidationUtils.h"
#include "nnapi/Validation.h"

namespace android::nn {
namespace concatenation {

Result<Version> validate(const IOperationValidationContext* context) {
    uint32_t inputCount = context->getNumInputs();
    NN_RET_CHECK_GE(inputCount, 2u);
    NN_RET_CHECK_EQ(context->getNumOutputs(), kNumOutputs);
    const OperandType inputType = context->getInputType(0);
    auto minSupportedVersion = kVersionFeatureLevel1;
    if (inputType == OperandType::TENSOR_FLOAT32 || inputType == OperandType::TENSOR_QUANT8_ASYMM) {
        minSupportedVersion = kVersionFeatureLevel1;
    } else if (inputType == OperandType::TENSOR_FLOAT16) {
        minSupportedVersion = kVersionFeatureLevel3;
    } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
        minSupportedVersion = kVersionFeatureLevel4;
    } else {
        NN_RET_CHECK_FAIL() << "Unsupported tensor type for operation " << kOperationName;
    }
    std::vector<OperandType> inExpectedTypes(inputCount - 1, inputType);
    inExpectedTypes.push_back(OperandType::INT32);
    if (inputType == OperandType::TENSOR_QUANT8_ASYMM) {
        const Shape& output = context->getOutputShape(kOutputTensor);
        for (uint32_t i = 0; i < inputCount - 1; ++i) {
            const Shape& input = context->getInputShape(i);
            if (input.scale != output.scale || input.offset != output.offset) {
                minSupportedVersion = combineVersions(minSupportedVersion, kVersionFeatureLevel3);
            }
        }
    }
    for (uint32_t i = 0; i < inputCount - 1; ++i) {
        const uint32_t inputRank = getNumberOfDimensions(context->getInputShape(i));
        if (inputRank != 0) {
            NN_RET_CHECK_LE(inputRank, 4u);
        }
    }
    NN_RET_CHECK(validateInputTypes(context, inExpectedTypes));
    NN_RET_CHECK(validateOutputTypes(context, {inputType}));
    return minSupportedVersion;
}

}  // namespace concatenation

NN_DEFINE_VALIDATION_FUNCTION(CONCATENATION, concatenation::validate);

}  // namespace android::nn
