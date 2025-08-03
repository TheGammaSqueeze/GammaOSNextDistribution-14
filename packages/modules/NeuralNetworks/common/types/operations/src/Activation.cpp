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

#include "Activation.h"

#include "OperationsValidationUtils.h"

namespace android::nn {
namespace activation {

Result<Version> validate(OperationType opType, const IOperationValidationContext* context) {
    NN_RET_CHECK_EQ(context->getNumInputs(), kNumInputs);
    NN_RET_CHECK_EQ(context->getNumOutputs(), kNumOutputs);
    auto inputType = context->getInputType(kInputTensor);
    auto minSupportedVersion = kVersionFeatureLevel1;
    if (inputType == OperandType::TENSOR_FLOAT32) {
        minSupportedVersion = kVersionFeatureLevel1;
    } else if (inputType == OperandType::TENSOR_FLOAT16) {
        minSupportedVersion = kVersionFeatureLevel3;
    } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM) {
        if (opType == OperationType::TANH) {
            minSupportedVersion = kVersionFeatureLevel3;
        } else {
            minSupportedVersion = kVersionFeatureLevel1;
        }
    } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
        minSupportedVersion = kVersionFeatureLevel4;
    } else {
        NN_RET_CHECK_FAIL() << "Unsupported tensor type for operation " << opType;
    }
    const Shape& input = context->getInputShape(kInputTensor);
    if (hasKnownRank(input)) {
        NN_RET_CHECK_LE(getNumberOfDimensions(input), 4u);
    }
    NN_RET_CHECK(validateInputTypes(context, {inputType}));
    NN_RET_CHECK(validateOutputTypes(context, {inputType}));
    return minSupportedVersion;
}

Result<Version> validateHardSwish(const IOperationValidationContext* context) {
    NN_RET_CHECK_EQ(context->getNumInputs(), kNumInputs);
    NN_RET_CHECK_EQ(context->getNumOutputs(), kNumOutputs);
    auto inputType = context->getInputType(kInputTensor);
    auto minSupportedVersion = kVersionFeatureLevel1;
    if (inputType == OperandType::TENSOR_FLOAT16 || inputType == OperandType::TENSOR_FLOAT32 ||
        inputType == OperandType::TENSOR_QUANT8_ASYMM ||
        inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
        minSupportedVersion = kVersionFeatureLevel4;
    } else {
        NN_RET_CHECK_FAIL() << "Unsupported tensor type for operation ELU";
    }
    NN_RET_CHECK(validateInputTypes(context, {inputType}));
    NN_RET_CHECK(validateOutputTypes(context, {inputType}));
    return minSupportedVersion;
}

}  // namespace activation

NN_DEFINE_VALIDATION_FUNCTION(RELU, [](const IOperationValidationContext* context) {
    return activation::validate(OperationType::RELU, context);
});
NN_DEFINE_VALIDATION_FUNCTION(RELU1, [](const IOperationValidationContext* context) {
    return activation::validate(OperationType::RELU1, context);
});
NN_DEFINE_VALIDATION_FUNCTION(RELU6, [](const IOperationValidationContext* context) {
    return activation::validate(OperationType::RELU6, context);
});
NN_DEFINE_VALIDATION_FUNCTION(LOGISTIC, [](const IOperationValidationContext* context) {
    return activation::validate(OperationType::LOGISTIC, context);
});
NN_DEFINE_VALIDATION_FUNCTION(TANH, [](const IOperationValidationContext* context) {
    return activation::validate(OperationType::TANH, context);
});
NN_DEFINE_VALIDATION_FUNCTION(HARD_SWISH, activation::validateHardSwish);

}  // namespace android::nn
