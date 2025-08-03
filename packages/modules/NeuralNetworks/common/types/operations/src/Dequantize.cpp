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

#include "Dequantize.h"

#include "OperationsValidationUtils.h"

namespace android::nn {
namespace dequantize {

Result<Version> validate(const IOperationValidationContext* context) {
    NN_RET_CHECK_EQ(context->getNumInputs(), kNumInputs);
    NN_RET_CHECK_EQ(context->getNumOutputs(), kNumOutputs);

    const OperandType inputType = context->getInputType(kInputTensor);
    const OperandType outputType = context->getOutputType(kOutputTensor);

    const Shape& input = context->getInputShape(kInputTensor);
    if (hasKnownRank(input)) {
        NN_RET_CHECK_LE(getNumberOfDimensions(input), 4u);
    }

    if (inputType == OperandType::TENSOR_QUANT8_ASYMM &&
        outputType == OperandType::TENSOR_FLOAT32) {
        return kVersionFeatureLevel1;
    }

    NN_RET_CHECK(inputType == OperandType::TENSOR_QUANT8_ASYMM ||
                 inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED ||
                 inputType == OperandType::TENSOR_QUANT8_SYMM ||
                 inputType == OperandType::TENSOR_QUANT8_SYMM_PER_CHANNEL)
            << "Unsupported input operand type for DEQUANTIZE op: " << inputType;
    NN_RET_CHECK(outputType == OperandType::TENSOR_FLOAT16 ||
                 outputType == OperandType::TENSOR_FLOAT32)
            << "Unsupported output operand type for DEQUANTIZE op: " << outputType;
    return kVersionFeatureLevel3;
}

}  // namespace dequantize

NN_DEFINE_VALIDATION_FUNCTION(DEQUANTIZE, dequantize::validate);

}  // namespace android::nn
