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

#include "Fill.h"

#include "OperationsValidationUtils.h"

namespace android::nn {
namespace fill_op {
namespace {

bool getValueType(OperandType outputType, OperandType* valueType) {
    switch (outputType) {
        case OperandType::TENSOR_FLOAT16:
            *valueType = OperandType::FLOAT16;
            return true;
        case OperandType::TENSOR_FLOAT32:
            *valueType = OperandType::FLOAT32;
            return true;
        case OperandType::TENSOR_INT32:
            *valueType = OperandType::INT32;
            return true;
        default:
            NN_RET_CHECK_FAIL() << "Unsupported value type for fill op: " << outputType;
    }
}

}  // namespace

Result<Version> validate(const IOperationValidationContext* context) {
    NN_RET_CHECK_EQ(context->getNumInputs(), kNumInputs);
    NN_RET_CHECK_EQ(context->getNumOutputs(), kNumOutputs);
    // Check output type first because input value type is dependent on the
    // output type.
    OperandType outputType = context->getOutputType(kOutputTensor);
    NN_RET_CHECK(outputType == OperandType::TENSOR_FLOAT16 ||
                 outputType == OperandType::TENSOR_FLOAT32 ||
                 outputType == OperandType::TENSOR_INT32)
            << "Unsupported output type for fill op: " << outputType;
    NN_RET_CHECK(validateOutputTypes(context, {outputType}));

    OperandType valueType;
    NN_RET_CHECK(getValueType(outputType, &valueType));
    NN_RET_CHECK(validateInputTypes(context, {OperandType::TENSOR_INT32, valueType}));

    return kVersionFeatureLevel4;
}

}  // namespace fill_op

NN_DEFINE_VALIDATION_FUNCTION(FILL, fill_op::validate);

}  // namespace android::nn
