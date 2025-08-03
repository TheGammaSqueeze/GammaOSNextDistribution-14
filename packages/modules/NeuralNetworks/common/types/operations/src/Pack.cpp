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

#include "Pack.h"

#include "OperationsValidationUtils.h"

namespace android::nn {
namespace pack_op {

Result<Version> validate(const IOperationValidationContext* context) {
    NN_RET_CHECK_GE(context->getNumInputs(), kMinNumInputs);
    NN_RET_CHECK_EQ(context->getNumOutputs(), kNumOutputs);

    // Validate the axis scalar.
    const OperandType axisScalarType = context->getInputType(kInputAxisScalar);
    NN_RET_CHECK_EQ(axisScalarType, OperandType::INT32)
            << "Unsupported axis scalar type for pack op";

    // Validate the output tensor.
    const OperandType outputType = context->getOutputType(kOutputTensor);
    NN_RET_CHECK(outputType == OperandType::TENSOR_FLOAT16 ||
                 outputType == OperandType::TENSOR_FLOAT32 ||
                 outputType == OperandType::TENSOR_QUANT8_ASYMM ||
                 outputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED ||
                 outputType == OperandType::TENSOR_INT32);

    // All input tensors must agree with the output tensor in type, scale, and zeroPoint (offset).
    // All input tensors must agree in rank, which must be one less than rank of the output tensor.
    const Shape outputShape = context->getOutputShape(kOutputTensor);
    //     Either a rank we must match, or zero if we haven't determined the rank.
    size_t requiredInputRank = [outputRank = getNumberOfDimensions(outputShape)] {
        return (outputRank ? outputRank - 1 : 0);
    }();
    for (uint32_t inputTensorNum = 0, inputTensorCount = context->getNumInputs() - 1;
         inputTensorNum < inputTensorCount; ++inputTensorNum) {
        const Shape inputShape = context->getInputShape(kInputFirstTensor + inputTensorNum);
        NN_RET_CHECK_EQ(inputShape.type, outputShape.type)
                << "Input tensor #" << inputTensorNum << " type " << inputShape.type
                << " does not match output tensor type " << outputShape.type;
        if (outputType == OperandType::TENSOR_QUANT8_ASYMM ||
            outputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
            NN_RET_CHECK_EQ(inputShape.scale, outputShape.scale)
                    << "Input tensor #" << inputTensorNum << " scale " << inputShape.scale
                    << " does not match output tensor scale " << outputShape.scale;
            NN_RET_CHECK_EQ(inputShape.offset, outputShape.offset)
                    << "Input tensor #" << inputTensorNum << " offset " << inputShape.offset
                    << " does not match output tensor offset " << outputShape.offset;
        }
        if (const size_t inputRank = inputShape.dimensions.size()) {
            if (requiredInputRank) {
                NN_RET_CHECK_EQ(requiredInputRank, inputRank)
                        << "Input tensor #" << inputTensorNum << " has inconsistent rank";
            } else {
                requiredInputRank = inputRank;
            }
        }
    }

    return kVersionFeatureLevel6;
}

}  // namespace pack_op

NN_DEFINE_VALIDATION_FUNCTION(PACK, pack_op::validate);

}  // namespace android::nn
