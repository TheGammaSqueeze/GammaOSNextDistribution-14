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

#include "L2Normalization.h"

#include <vector>

#include "OperationsValidationUtils.h"

namespace android::nn {
namespace l2_norm {

Result<Version> validate(const IOperationValidationContext* context) {
    NN_RET_CHECK(context->getNumInputs() == kNumInputs ||
                 context->getNumInputs() == kNumInputs - 1);
    NN_RET_CHECK_EQ(context->getNumOutputs(), kNumOutputs);

    const OperandType inputType = context->getInputType(kInputTensor);
    std::vector<OperandType> inExpectedTypes = {inputType};
    auto minSupportedVersion = kVersionFeatureLevel1;
    if (inputType == OperandType::TENSOR_FLOAT16 || inputType == OperandType::TENSOR_QUANT8_ASYMM) {
        minSupportedVersion = kVersionFeatureLevel3;
    } else if (inputType == OperandType::TENSOR_FLOAT32) {
        minSupportedVersion = kVersionFeatureLevel1;
    } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
        minSupportedVersion = kVersionFeatureLevel4;
    } else {
        NN_RET_CHECK_FAIL() << "Unsupported tensor type for operation " << kOperationName;
    }
    if (context->getNumInputs() == kNumInputs) {
        inExpectedTypes.push_back(OperandType::INT32);
        minSupportedVersion = kVersionFeatureLevel3;
    } else if (context->getInputShape(kInputTensor).dimensions.size() != 4) {
        minSupportedVersion = kVersionFeatureLevel3;
    }
    const Shape& input = context->getInputShape(kInputTensor);
    if (hasKnownRank(input)) {
        NN_RET_CHECK_LE(getNumberOfDimensions(input), 4u);
    }
    NN_RET_CHECK(validateInputTypes(context, inExpectedTypes));
    NN_RET_CHECK(validateOutputTypes(context, {inputType}));
    return minSupportedVersion;
}

}  // namespace l2_norm

NN_DEFINE_VALIDATION_FUNCTION(L2_NORMALIZATION, l2_norm::validate);

}  // namespace android::nn
