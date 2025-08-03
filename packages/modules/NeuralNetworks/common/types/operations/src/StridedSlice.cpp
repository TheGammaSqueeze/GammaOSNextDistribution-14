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

#include "StridedSlice.h"

#include "OperationsValidationUtils.h"

namespace android::nn {
namespace strided_slice {

Result<Version> validate(const IOperationValidationContext* context) {
    NN_RET_CHECK_EQ(context->getNumInputs(), kNumInputs);
    NN_RET_CHECK_EQ(context->getNumOutputs(), kNumOutputs);
    OperandType inputType = context->getInputType(kInputTensor);
    NN_RET_CHECK(inputType == OperandType::TENSOR_FLOAT16 ||
                 inputType == OperandType::TENSOR_FLOAT32 ||
                 inputType == OperandType::TENSOR_QUANT8_ASYMM ||
                 inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED)
            << "Unsupported input operand type for STRIDED_SLICE op: " << inputType;

    Version minSupportedVersion;
    if (inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
        minSupportedVersion = kVersionFeatureLevel4;
    } else if (inputType == OperandType::TENSOR_FLOAT16) {
        minSupportedVersion = kVersionFeatureLevel3;
    } else {
        minSupportedVersion = kVersionFeatureLevel2;
    }

    NN_RET_CHECK(validateInputTypes(context, {
                                                     inputType,
                                                     OperandType::TENSOR_INT32,
                                                     OperandType::TENSOR_INT32,
                                                     OperandType::TENSOR_INT32,
                                                     OperandType::INT32,
                                                     OperandType::INT32,
                                                     OperandType::INT32,
                                             }));
    NN_RET_CHECK(validateOutputTypes(context, {inputType}));
    const Shape& input = context->getInputShape(kInputTensor);
    if (hasKnownRank(input)) {
        NN_RET_CHECK_LE(getNumberOfDimensions(input), 4u);
    }
    return minSupportedVersion;
}

}  // namespace strided_slice

NN_DEFINE_VALIDATION_FUNCTION(STRIDED_SLICE, strided_slice::validate);

}  // namespace android::nn
