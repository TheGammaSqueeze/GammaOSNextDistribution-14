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

#include "Reduce.h"

#include "OperationsValidationUtils.h"

namespace android::nn {
namespace reduce {

Result<Version> validateProdSum(const IOperationValidationContext* context) {
    NN_RET_CHECK_EQ(context->getNumInputs(), kNumInputs);
    NN_RET_CHECK_EQ(context->getNumOutputs(), kNumOutputs);
    OperandType inputType = context->getInputType(kInputTensor);
    NN_RET_CHECK(inputType == OperandType::TENSOR_FLOAT16 ||
                 inputType == OperandType::TENSOR_FLOAT32)
            << "Unsupported tensor type for REDUCE_PROD or REDUCE_SUM";
    NN_RET_CHECK(
            validateInputTypes(context, {inputType, OperandType::TENSOR_INT32, OperandType::BOOL}));
    NN_RET_CHECK(validateOutputTypes(context, {inputType}));
    const Shape& input = context->getInputShape(kInputTensor);
    if (hasKnownRank(input)) {
        NN_RET_CHECK_LE(getNumberOfDimensions(input), 4u);
    }
    return kVersionFeatureLevel3;
}

Result<Version> validateMaxMin(const IOperationValidationContext* context) {
    NN_RET_CHECK_EQ(context->getNumInputs(), kNumInputs);
    NN_RET_CHECK_EQ(context->getNumOutputs(), kNumOutputs);
    OperandType inputType = context->getInputType(kInputTensor);
    NN_RET_CHECK(inputType == OperandType::TENSOR_FLOAT16 ||
                 inputType == OperandType::TENSOR_FLOAT32 ||
                 inputType == OperandType::TENSOR_QUANT8_ASYMM ||
                 inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED)
            << "Unsupported tensor type for REDUCE_MAX or REDUCE_MIN";
    NN_RET_CHECK(
            validateInputTypes(context, {inputType, OperandType::TENSOR_INT32, OperandType::BOOL}));
    NN_RET_CHECK(validateOutputTypes(context, {inputType}));
    auto minVersion = kVersionFeatureLevel3;
    if (inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
        minVersion = kVersionFeatureLevel4;
    }
    const Shape& input = context->getInputShape(kInputTensor);
    if (hasKnownRank(input)) {
        NN_RET_CHECK_LE(getNumberOfDimensions(input), 4u);
    }
    return minVersion;
}

Result<Version> validateLogical(const IOperationValidationContext* context) {
    NN_RET_CHECK_EQ(context->getNumInputs(), kNumInputs);
    NN_RET_CHECK_EQ(context->getNumOutputs(), kNumOutputs);
    OperandType inputType = context->getInputType(kInputTensor);
    NN_RET_CHECK(inputType == OperandType::TENSOR_BOOL8)
            << "Unsupported tensor type for REDUCE_ANY or REDUCE_ALL";
    NN_RET_CHECK(
            validateInputTypes(context, {inputType, OperandType::TENSOR_INT32, OperandType::BOOL}));
    NN_RET_CHECK(validateOutputTypes(context, {inputType}));
    const Shape& input = context->getInputShape(kInputTensor);
    if (hasKnownRank(input)) {
        NN_RET_CHECK_LE(getNumberOfDimensions(input), 4u);
    }
    return kVersionFeatureLevel3;
}

}  // namespace reduce

NN_DEFINE_VALIDATION_FUNCTION(REDUCE_ALL, reduce::validateLogical);
NN_DEFINE_VALIDATION_FUNCTION(REDUCE_ANY, reduce::validateLogical);
NN_DEFINE_VALIDATION_FUNCTION(REDUCE_MAX, reduce::validateMaxMin);
NN_DEFINE_VALIDATION_FUNCTION(REDUCE_MIN, reduce::validateMaxMin);
NN_DEFINE_VALIDATION_FUNCTION(REDUCE_PROD, reduce::validateProdSum);
NN_DEFINE_VALIDATION_FUNCTION(REDUCE_SUM, reduce::validateProdSum);

}  // namespace android::nn
