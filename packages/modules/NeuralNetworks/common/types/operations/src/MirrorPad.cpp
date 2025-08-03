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

#define LOG_TAG "Operations"

#include "MirrorPad.h"

#include "OperationsValidationUtils.h"

namespace android::nn {
namespace mirror_pad_op {

Result<Version> validate(const IOperationValidationContext* context) {
    NN_RET_CHECK_EQ(context->getNumInputs(), kNumInputs);
    NN_RET_CHECK_EQ(context->getNumOutputs(), kNumOutputs);

    // Validate the input tensor.
    const OperandType inputTensorType = context->getInputType(kInputTensor);
    NN_RET_CHECK(inputTensorType == OperandType::TENSOR_FLOAT16 ||
                 inputTensorType == OperandType::TENSOR_FLOAT32 ||
                 inputTensorType == OperandType::TENSOR_QUANT8_ASYMM ||
                 inputTensorType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED ||
                 inputTensorType == OperandType::TENSOR_INT32);

    // Validate the padding tensor.
    NN_RET_CHECK_EQ(context->getInputType(kInputPaddingTensor), OperandType::TENSOR_INT32);
    const Shape inputPaddingTensorShape = context->getInputShape(kInputPaddingTensor);
    if (hasKnownRank(inputPaddingTensorShape)) {
        NN_RET_CHECK_EQ(getNumberOfDimensions(inputPaddingTensorShape), 2U)
                << "Input tensor #" << kInputPaddingTensor << " must have 2 dimensions";
        auto dim1 = inputPaddingTensorShape.dimensions[1];
        NN_RET_CHECK(!dim1 || dim1 == 2) << "Input tensor #" << kInputPaddingTensor
                                         << " second dimension must be 2 but is " << dim1;
    }

    // Validate the mode scalar.
    NN_RET_CHECK_EQ(context->getInputType(kInputModeScalar), OperandType::INT32);

    // Validate the output tensor.
    NN_RET_CHECK_EQ(context->getOutputType(kOutputTensor), inputTensorType);

    // Consistency checks.
    const Shape inputTensorShape = context->getInputShape(kInputTensor);
    const Shape outputTensorShape = context->getOutputShape(kOutputTensor);
    if (inputTensorType == OperandType::TENSOR_QUANT8_ASYMM ||
        inputTensorType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
        NN_RET_CHECK_EQ(inputTensorShape.scale, outputTensorShape.scale)
                << "Input tensor #" << kInputTensor << " scale " << inputTensorShape.scale
                << " does not match output tensor scale " << outputTensorShape.scale;
        NN_RET_CHECK_EQ(inputTensorShape.offset, outputTensorShape.offset)
                << "Input tensor #" << kInputTensor << " offset " << inputTensorShape.offset
                << " does not match output tensor offset " << outputTensorShape.offset;
    }
    if (hasKnownRank(inputTensorShape)) {
        if (hasKnownRank(inputPaddingTensorShape)) {
            if (auto inputPaddingTensorDim0 = inputPaddingTensorShape.dimensions[0]) {
                auto inputTensorRank = getNumberOfDimensions(inputTensorShape);
                NN_RET_CHECK_EQ(inputTensorRank, inputPaddingTensorDim0)
                        << "Input tensor #" << kInputPaddingTensor << " first dimension "
                        << inputPaddingTensorDim0 << " does not match input tensor #"
                        << kInputTensor << " rank " << inputTensorRank;
            }
        }
        if (hasKnownRank(outputTensorShape)) {
            auto inputTensorRank = getNumberOfDimensions(inputTensorShape);
            auto outputTensorRank = getNumberOfDimensions(outputTensorShape);
            NN_RET_CHECK_EQ(inputTensorRank, outputTensorRank)
                    << "Input tensor #" << kInputTensor << " rank " << inputTensorRank
                    << " does not match "
                    << "output tensor rank " << outputTensorRank;
        }
    }

    return kVersionFeatureLevel7;
}

}  // namespace mirror_pad_op

NN_DEFINE_VALIDATION_FUNCTION(MIRROR_PAD, mirror_pad_op::validate);

}  // namespace android::nn
