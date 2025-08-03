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

#include "Rank.h"

#include "OperationsValidationUtils.h"

namespace android::nn {
namespace rank_op {

Result<Version> validate(const IOperationValidationContext* context) {
    NN_RET_CHECK_EQ(context->getNumInputs(), kNumInputs);
    NN_RET_CHECK_EQ(context->getNumOutputs(), kNumOutputs);
    OperandType inputType = context->getInputType(kInputTensor);
    NN_RET_CHECK(inputType == OperandType::TENSOR_FLOAT16 ||
                 inputType == OperandType::TENSOR_FLOAT32 ||
                 inputType == OperandType::TENSOR_INT32 ||
                 inputType == OperandType::TENSOR_QUANT8_ASYMM ||
                 inputType == OperandType::TENSOR_QUANT16_SYMM ||
                 inputType == OperandType::TENSOR_BOOL8 ||
                 inputType == OperandType::TENSOR_QUANT8_SYMM_PER_CHANNEL ||
                 inputType == OperandType::TENSOR_QUANT16_ASYMM ||
                 inputType == OperandType::TENSOR_QUANT8_SYMM ||
                 inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED)
            << "Incorrect input type for a RANK op: " << inputType;
    NN_RET_CHECK(validateOutputTypes(context, {OperandType::INT32}));
    return kVersionFeatureLevel4;
}

}  // namespace rank_op

NN_DEFINE_VALIDATION_FUNCTION(RANK, rank_op::validate);

}  // namespace android::nn
