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

#include "RoiPooling.h"

#include <vector>

#include "OperationsValidationUtils.h"

namespace android::nn {
namespace roi_pooling {

Result<Version> validate(const IOperationValidationContext* context) {
    NN_RET_CHECK_EQ(context->getNumInputs(), kNumInputs);
    NN_RET_CHECK_EQ(context->getNumOutputs(), kNumOutputs);
    std::vector<OperandType> inExpectedTypes;
    auto inputType = context->getInputType(kInputTensor);
    if (inputType == OperandType::TENSOR_FLOAT32) {
        inExpectedTypes = {OperandType::TENSOR_FLOAT32, OperandType::TENSOR_FLOAT32,
                           OperandType::TENSOR_INT32,   OperandType::INT32,
                           OperandType::INT32,          OperandType::FLOAT32,
                           OperandType::FLOAT32,        OperandType::BOOL};
    } else if (inputType == OperandType::TENSOR_FLOAT16) {
        inExpectedTypes = {OperandType::TENSOR_FLOAT16, OperandType::TENSOR_FLOAT16,
                           OperandType::TENSOR_INT32,   OperandType::INT32,
                           OperandType::INT32,          OperandType::FLOAT16,
                           OperandType::FLOAT16,        OperandType::BOOL};
    } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM ||
               inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
        inExpectedTypes = {inputType,
                           OperandType::TENSOR_QUANT16_ASYMM,
                           OperandType::TENSOR_INT32,
                           OperandType::INT32,
                           OperandType::INT32,
                           OperandType::FLOAT32,
                           OperandType::FLOAT32,
                           OperandType::BOOL};
    } else {
        return NN_ERROR() << "Unsupported input tensor type for operation " << kOperationName;
    }
    NN_RET_CHECK(validateInputTypes(context, inExpectedTypes));
    NN_RET_CHECK(validateOutputTypes(context, {inputType}));
    if (inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
        return kVersionFeatureLevel4;
    } else {
        return kVersionFeatureLevel3;
    }
}

}  // namespace roi_pooling

NN_DEFINE_VALIDATION_FUNCTION(ROI_POOLING, roi_pooling::validate);

}  // namespace android::nn
