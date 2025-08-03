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

#include "Elu.h"

#include "OperationsValidationUtils.h"

namespace android::nn {
namespace elu {

Result<Version> validate(const IOperationValidationContext* context) {
    NN_RET_CHECK_EQ(context->getNumInputs(), kNumInputs);
    NN_RET_CHECK_EQ(context->getNumOutputs(), kNumOutputs);
    auto inputType = context->getInputType(kInputTensor);
    auto minSupportedVersion = kVersionFeatureLevel1;
    if (inputType == OperandType::TENSOR_FLOAT16 || inputType == OperandType::TENSOR_FLOAT32) {
        minSupportedVersion = kVersionFeatureLevel4;
    } else {
        NN_RET_CHECK_FAIL() << "Unsupported tensor type for operation ELU";
    }
    auto scalarType =
            inputType == OperandType::TENSOR_FLOAT16 ? OperandType::FLOAT16 : OperandType::FLOAT32;
    NN_RET_CHECK(validateInputTypes(context, {inputType, scalarType}));
    NN_RET_CHECK(validateOutputTypes(context, {inputType}));
    return minSupportedVersion;
}

}  // namespace elu

NN_DEFINE_VALIDATION_FUNCTION(ELU, elu::validate);

}  // namespace android::nn
