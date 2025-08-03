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

#include "Elementwise.h"

#include "OperationsValidationUtils.h"

namespace android::nn {
namespace elementwise {

Result<Version> validate(const IOperationValidationContext* context) {
    NN_RET_CHECK_EQ(context->getNumInputs(), kNumInputs);
    NN_RET_CHECK_EQ(context->getNumOutputs(), kNumOutputs);
    OperandType inputType = context->getInputType(kInputTensor);
    NN_RET_CHECK(inputType == OperandType::TENSOR_FLOAT16 ||
                 inputType == OperandType::TENSOR_FLOAT32)
            << "Unsupported tensor type for elementwise operation";
    NN_RET_CHECK(validateInputTypes(context, {inputType}));
    NN_RET_CHECK(validateOutputTypes(context, {inputType}));
    return kVersionFeatureLevel3;
}

Result<Version> validateAbs(const IOperationValidationContext* context) {
    NN_RET_CHECK_EQ(context->getNumInputs(), kNumInputs);
    NN_RET_CHECK_EQ(context->getNumOutputs(), kNumOutputs);
    OperandType inputType = context->getInputType(kInputTensor);
    NN_RET_CHECK(inputType == OperandType::TENSOR_FLOAT16 ||
                 inputType == OperandType::TENSOR_FLOAT32 || inputType == OperandType::TENSOR_INT32)
            << "Unsupported tensor type for operation ABS";
    NN_RET_CHECK(validateInputTypes(context, {inputType}));
    NN_RET_CHECK(validateOutputTypes(context, {inputType}));
    return inputType == OperandType::TENSOR_INT32 ? kVersionFeatureLevel4 : kVersionFeatureLevel3;
}

Result<Version> validateFloor(const IOperationValidationContext* context) {
    NN_RET_CHECK_EQ(context->getNumInputs(), kNumInputs);
    NN_RET_CHECK_EQ(context->getNumOutputs(), kNumOutputs);

    OperandType inputType = context->getInputType(kInputTensor);
    NN_RET_CHECK(inputType == OperandType::TENSOR_FLOAT16 ||
                 inputType == OperandType::TENSOR_FLOAT32)
            << "Unsupported tensor type for operation FLOOR";
    NN_RET_CHECK(validateInputTypes(context, {inputType}));
    NN_RET_CHECK(validateOutputTypes(context, {inputType}));

    const Shape& input = context->getInputShape(kInputTensor);
    if (hasKnownRank(input)) {
        NN_RET_CHECK_LE(getNumberOfDimensions(input), 4u);
    }

    return inputType == OperandType::TENSOR_FLOAT16 ? kVersionFeatureLevel3 : kVersionFeatureLevel1;
}

Result<Version> validateRsqrt(const IOperationValidationContext* context) {
    NN_RET_CHECK_EQ(context->getNumInputs(), kNumInputs);
    NN_RET_CHECK_EQ(context->getNumOutputs(), kNumOutputs);
    OperandType inputType = context->getInputType(kInputTensor);
    NN_RET_CHECK(inputType == OperandType::TENSOR_FLOAT16 ||
                 inputType == OperandType::TENSOR_FLOAT32 ||
                 inputType == OperandType::TENSOR_QUANT8_ASYMM ||
                 inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED)
            << "Unsupported tensor type for operation RSQRT";
    NN_RET_CHECK(validateInputTypes(context, {inputType}));
    NN_RET_CHECK(validateOutputTypes(context, {inputType}));
    return (inputType == OperandType::TENSOR_QUANT8_ASYMM ||
            inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED)
                   ? kVersionFeatureLevel7
                   : kVersionFeatureLevel3;
}

}  // namespace elementwise

NN_DEFINE_VALIDATION_FUNCTION(FLOOR, elementwise::validateFloor);
NN_DEFINE_VALIDATION_FUNCTION(ABS, elementwise::validateAbs);
NN_DEFINE_VALIDATION_FUNCTION(EXP, elementwise::validate);
NN_DEFINE_VALIDATION_FUNCTION(LOG, elementwise::validate);
NN_DEFINE_VALIDATION_FUNCTION(RSQRT, elementwise::validateRsqrt);
NN_DEFINE_VALIDATION_FUNCTION(SIN, elementwise::validate);
NN_DEFINE_VALIDATION_FUNCTION(SQRT, elementwise::validate);

}  // namespace android::nn
