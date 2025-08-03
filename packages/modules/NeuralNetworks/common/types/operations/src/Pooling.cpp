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

#include "Pooling.h"

#include <vector>

#include "OperationsValidationUtils.h"
#include "nnapi/Validation.h"

namespace android::nn {
namespace pooling {

Result<Version> validate(OperationType opType, const IOperationValidationContext* context) {
    NN_RET_CHECK_EQ(context->getNumOutputs(), kNumOutputs);
    auto inputCount = context->getNumInputs();
    NN_RET_CHECK(inputCount == 11 || inputCount == 10 || inputCount == 8 || inputCount == 7);
    auto inputType = context->getInputType(kInputTensor);
    std::vector<OperandType> inExpectedTypes;
    auto minSupportedVersion = kVersionFeatureLevel1;
    if (inputType == OperandType::TENSOR_FLOAT32) {
        minSupportedVersion = kVersionFeatureLevel1;
        inExpectedTypes = {
                inputType,          OperandType::INT32, OperandType::INT32, OperandType::INT32,
                OperandType::INT32, OperandType::INT32, OperandType::INT32,
        };
    } else if (inputType == OperandType::TENSOR_FLOAT16) {
        minSupportedVersion = kVersionFeatureLevel3;
        inExpectedTypes = {
                OperandType::TENSOR_FLOAT16, OperandType::INT32, OperandType::INT32,
                OperandType::INT32,          OperandType::INT32, OperandType::INT32,
                OperandType::INT32,
        };
    } else if (opType != OperationType::L2_POOL_2D &&
               inputType == OperandType::TENSOR_QUANT8_ASYMM) {
        minSupportedVersion = kVersionFeatureLevel1;
        inExpectedTypes = {
                OperandType::TENSOR_QUANT8_ASYMM,
                OperandType::INT32,
                OperandType::INT32,
                OperandType::INT32,
                OperandType::INT32,
                OperandType::INT32,
                OperandType::INT32,
        };
    } else if (opType != OperationType::L2_POOL_2D &&
               inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
        minSupportedVersion = kVersionFeatureLevel4;
        inExpectedTypes = {
                OperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                OperandType::INT32,
                OperandType::INT32,
                OperandType::INT32,
                OperandType::INT32,
                OperandType::INT32,
                OperandType::INT32,
        };
    } else {
        NN_RET_CHECK_FAIL() << "Unsupported input tensor type for operation " << opType;
    }

    if (inputCount >= 10) {
        std::vector<OperandType> explicitScalarTypes(3, OperandType::INT32);
        inExpectedTypes.insert(inExpectedTypes.end(), explicitScalarTypes.begin(),
                               explicitScalarTypes.end());
    }
    if (inputCount == 11 || inputCount == 8) {
        inExpectedTypes.push_back(OperandType::BOOL);
        minSupportedVersion = combineVersions(minSupportedVersion, kVersionFeatureLevel3);
    } else {
        minSupportedVersion = combineVersions(minSupportedVersion, kVersionFeatureLevel1);
    }
    NN_RET_CHECK(validateInputTypes(context, inExpectedTypes));
    NN_RET_CHECK(validateOutputTypes(context, {inputType}));
    return minSupportedVersion;
}

}  // namespace pooling

NN_DEFINE_VALIDATION_FUNCTION(AVERAGE_POOL_2D, [](const IOperationValidationContext* context) {
    return pooling::validate(OperationType::AVERAGE_POOL_2D, context);
});
NN_DEFINE_VALIDATION_FUNCTION(L2_POOL_2D, [](const IOperationValidationContext* context) {
    return pooling::validate(OperationType::L2_POOL_2D, context);
});
NN_DEFINE_VALIDATION_FUNCTION(MAX_POOL_2D, [](const IOperationValidationContext* context) {
    return pooling::validate(OperationType::MAX_POOL_2D, context);
});

}  // namespace android::nn
