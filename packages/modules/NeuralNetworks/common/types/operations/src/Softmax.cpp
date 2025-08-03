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

#include "Softmax.h"

#include <vector>

#include "OperationsValidationUtils.h"
#include "nnapi/Validation.h"

namespace android::nn {
namespace softmax {

Result<Version> validate(const IOperationValidationContext* context) {
    NN_RET_CHECK(context->getNumInputs() == kNumInputs ||
                 context->getNumInputs() == kNumInputs - 1);
    NN_RET_CHECK_EQ(context->getNumOutputs(), kNumOutputs);
    auto inputType = context->getInputType(kInputTensor);
    std::vector<OperandType> inExpectedTypes;
    auto minSupportedVersion = kVersionFeatureLevel1;
    if (inputType == OperandType::TENSOR_FLOAT32 || inputType == OperandType::TENSOR_QUANT8_ASYMM) {
        minSupportedVersion = kVersionFeatureLevel1;
        inExpectedTypes = {inputType, OperandType::FLOAT32};
    } else if (inputType == OperandType::TENSOR_FLOAT16) {
        minSupportedVersion = kVersionFeatureLevel3;
        inExpectedTypes = {inputType, OperandType::FLOAT16};
    } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
        minSupportedVersion = kVersionFeatureLevel4;
        inExpectedTypes = {inputType, OperandType::FLOAT32};
    } else {
        NN_RET_CHECK_FAIL() << "Unsupported tensor type for operation " << kOperationName;
    }
    const auto inputRank = getNumberOfDimensions(context->getInputShape(kInputTensor));
    if (inputRank != 0) {
        NN_RET_CHECK_LE(inputRank, 4u);
    }
    if (context->getNumInputs() == kNumInputs) {
        minSupportedVersion = combineVersions(minSupportedVersion, kVersionFeatureLevel3);
        inExpectedTypes.push_back(OperandType::INT32);
    } else {
        if (inputRank != 2 && inputRank != 4 && inputRank != 0) {
            minSupportedVersion = combineVersions(minSupportedVersion, kVersionFeatureLevel3);
        }
    }
    NN_RET_CHECK(validateInputTypes(context, inExpectedTypes));
    NN_RET_CHECK(validateOutputTypes(context, {inputType}));
    return minSupportedVersion;
}

}  // namespace softmax

NN_DEFINE_VALIDATION_FUNCTION(SOFTMAX, softmax::validate);

}  // namespace android::nn
