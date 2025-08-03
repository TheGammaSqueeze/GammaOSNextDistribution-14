/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include "BatchMatmul.h"

#include "OperationsValidationUtils.h"

namespace android::nn {
namespace batch_matmul_op {

Result<Version> validate(const IOperationValidationContext* context) {
    NN_RET_CHECK_EQ(context->getNumInputs(), kNumInputs);
    NN_RET_CHECK_EQ(context->getNumOutputs(), kNumOutputs);

    // Checks two input tensors have same input type and number of dimensions.
    OperandType inputLHSTensorType = context->getInputType(kInputLHSTensor);
    OperandType inputRHSTensorType = context->getInputType(kInputRHSTensor);
    NN_RET_CHECK_EQ(inputLHSTensorType, inputRHSTensorType)
            << "Input types do not match between two input tensors. InputLHSTensor: "
            << inputLHSTensorType << ", InputRHSTensor: " << inputRHSTensorType;
    NN_RET_CHECK(inputLHSTensorType == OperandType::TENSOR_FLOAT16 ||
                 inputLHSTensorType == OperandType::TENSOR_FLOAT32 ||
                 inputLHSTensorType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED ||
                 inputLHSTensorType == OperandType::TENSOR_INT32)
            << "Incorrect input tensor type for a BATCH_MATMUL op: " << inputLHSTensorType;

    OperandType inputLHSAdjType = context->getInputType(kInputLHSAdj);
    OperandType inputRHSAdjType = context->getInputType(kInputRHSAdj);
    NN_RET_CHECK(inputLHSAdjType == OperandType::BOOL && inputRHSAdjType == OperandType::BOOL)
            << "Incorrect input scalar type for a BATCH_MATMUL op: InputLHSAdj: " << inputLHSAdjType
            << ", InputRHSAdj: " << inputRHSAdjType;

    // Checks output type matches input type.
    OperandType outputType = context->getOutputType(kOutputTensor);
    NN_RET_CHECK_EQ(inputLHSTensorType, outputType)
            << "Output type " << outputType << " does not match input type " << inputLHSTensorType;

    return kVersionFeatureLevel6;
}

}  // namespace batch_matmul_op

NN_DEFINE_VALIDATION_FUNCTION(BATCH_MATMUL, batch_matmul_op::validate);

}  // namespace android::nn
