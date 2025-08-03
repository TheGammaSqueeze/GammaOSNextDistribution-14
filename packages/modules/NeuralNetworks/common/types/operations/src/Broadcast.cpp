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

#include "Broadcast.h"

#include <nnapi/Validation.h>

#include "OperationsValidationUtils.h"

namespace android::nn {
namespace broadcast {

Result<Version> validate(OperationType opType, const IOperationValidationContext* context) {
    auto minSupportedVersion = (opType == OperationType::DIV || opType == OperationType::SUB)
                                       ? kVersionFeatureLevel2
                                       : kVersionFeatureLevel1;
    NN_RET_CHECK_EQ(context->getNumInputs(), kNumInputs);
    NN_RET_CHECK_EQ(context->getNumOutputs(), kNumOutputs);
    auto inputType = context->getInputType(kInputTensor1);
    const Shape& input1 = context->getInputShape(kInputTensor1);
    const Shape& input2 = context->getInputShape(kInputTensor2);
    const Shape& output = context->getOutputShape(kOutputTensor);
    if (inputType == OperandType::TENSOR_FLOAT32) {
        minSupportedVersion = combineVersions(minSupportedVersion, kVersionFeatureLevel1);
    } else if (inputType == OperandType::TENSOR_FLOAT16) {
        minSupportedVersion = combineVersions(minSupportedVersion, kVersionFeatureLevel3);
    } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM) {
        if (opType == OperationType::SUB) {
            minSupportedVersion = combineVersions(minSupportedVersion, kVersionFeatureLevel3);
        } else if (opType == OperationType::DIV) {
            NN_RET_CHECK_FAIL() << "Unsupported tensor type for operation DIV";
        } else if (opType == OperationType::MUL) {
            NN_RET_CHECK_GT(output.scale, input1.scale * input2.scale);
            minSupportedVersion = combineVersions(minSupportedVersion, kVersionFeatureLevel1);
        } else {
            minSupportedVersion = combineVersions(minSupportedVersion, kVersionFeatureLevel1);
        }
    } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
        if (opType == OperationType::MUL) {
            NN_RET_CHECK_GT(output.scale, input1.scale * input2.scale);
        }
        minSupportedVersion = combineVersions(minSupportedVersion, kVersionFeatureLevel4);
    } else if (inputType == OperandType::TENSOR_INT32) {
        minSupportedVersion = combineVersions(minSupportedVersion, kVersionFeatureLevel4);
    } else {
        NN_RET_CHECK_FAIL() << "Unsupported tensor type for operation " << opType;
    }

    if (hasKnownRank(input1) && hasKnownRank(input2)) {
        NN_RET_CHECK_LE(getNumberOfDimensions(input1), 4u);
        NN_RET_CHECK_LE(getNumberOfDimensions(input2), 4u);
    }
    NN_RET_CHECK(validateInputTypes(context, {inputType, inputType, OperandType::INT32}));
    NN_RET_CHECK(validateOutputTypes(context, {inputType}));
    return minSupportedVersion;
}

}  // namespace broadcast

NN_DEFINE_VALIDATION_FUNCTION(ADD, [](const IOperationValidationContext* context) {
    return broadcast::validate(OperationType::ADD, context);
});
NN_DEFINE_VALIDATION_FUNCTION(MUL, [](const IOperationValidationContext* context) {
    return broadcast::validate(OperationType::MUL, context);
});
NN_DEFINE_VALIDATION_FUNCTION(DIV, [](const IOperationValidationContext* context) {
    return broadcast::validate(OperationType::DIV, context);
});
NN_DEFINE_VALIDATION_FUNCTION(SUB, [](const IOperationValidationContext* context) {
    return broadcast::validate(OperationType::SUB, context);
});

}  // namespace android::nn
