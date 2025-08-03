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

#include "ResizeImageOps.h"

#include <vector>

#include "OperationsValidationUtils.h"
#include "nnapi/Validation.h"

namespace android::nn {
namespace resize_image {

Result<Version> validate(OperationType opType, const IOperationValidationContext* context) {
    const auto numInputs = context->getNumInputs();
    if (opType == OperationType::RESIZE_BILINEAR) {
        NN_RET_CHECK(numInputs >= kNumInputs - 1 && numInputs <= kNumInputs + kNumOptionalInputs);
    } else if (opType == OperationType::RESIZE_NEAREST_NEIGHBOR) {
        NN_RET_CHECK(numInputs >= kNumInputs && numInputs <= kNumInputs + kNumOptionalInputs);
    } else {
        NN_RET_CHECK_FAIL() << "Unsupported operation " << opType;
    }
    NN_RET_CHECK_EQ(context->getNumOutputs(), kNumOutputs);
    auto inputType = context->getInputType(kInputTensor);
    auto scalarType = context->getInputType(kOutputHeightParamScalar);
    std::vector<OperandType> inExpectedTypes = {inputType, scalarType, scalarType};
    auto minSupportedVersion = kVersionFeatureLevel1;
    NN_RET_CHECK(inputType == OperandType::TENSOR_FLOAT16 ||
                 inputType == OperandType::TENSOR_FLOAT32 ||
                 inputType == OperandType::TENSOR_QUANT8_ASYMM ||
                 inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED)
            << "Unsupported tensor type for operation " << opType;
    if (inputType == OperandType::TENSOR_FLOAT16 || inputType == OperandType::TENSOR_QUANT8_ASYMM) {
        minSupportedVersion = combineVersions(minSupportedVersion, kVersionFeatureLevel3);
    }
    if (inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
        minSupportedVersion = combineVersions(minSupportedVersion, kVersionFeatureLevel4);
    }
    if (scalarType != OperandType::INT32) {
        minSupportedVersion = combineVersions(minSupportedVersion, kVersionFeatureLevel3);
        if (inputType == OperandType::TENSOR_FLOAT32) {
            NN_RET_CHECK(scalarType == OperandType::FLOAT32);
        } else if (inputType == OperandType::TENSOR_FLOAT16) {
            NN_RET_CHECK(scalarType == OperandType::FLOAT16);
        } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM ||
                   inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
            NN_RET_CHECK(scalarType == OperandType::FLOAT32);
        }
    }
    if (numInputs < kNumInputs) {
        minSupportedVersion = combineVersions(minSupportedVersion, kVersionFeatureLevel1);
    } else if (numInputs == kNumInputs) {
        inExpectedTypes.push_back(OperandType::BOOL);
        minSupportedVersion = combineVersions(minSupportedVersion, kVersionFeatureLevel3);
    } else {
        while (inExpectedTypes.size() < numInputs) {
            inExpectedTypes.push_back(OperandType::BOOL);
        }
        minSupportedVersion = combineVersions(minSupportedVersion, kVersionFeatureLevel4);
    }
    NN_RET_CHECK(validateInputTypes(context, inExpectedTypes));
    NN_RET_CHECK(validateOutputTypes(context, {inputType}));
    return minSupportedVersion;
}

}  // namespace resize_image

NN_DEFINE_VALIDATION_FUNCTION(RESIZE_BILINEAR, [](const IOperationValidationContext* context) {
    return resize_image::validate(OperationType::RESIZE_BILINEAR, context);
});
NN_DEFINE_VALIDATION_FUNCTION(RESIZE_NEAREST_NEIGHBOR,
                              [](const IOperationValidationContext* context) {
                                  return resize_image::validate(
                                          OperationType::RESIZE_NEAREST_NEIGHBOR, context);
                              });

}  // namespace android::nn
