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

#include "TransposeConv2D.h"

#include <vector>

#include "OperationsValidationUtils.h"

namespace android::nn {
namespace transpose_conv_2d {

Result<Version> validate(const IOperationValidationContext* context) {
    const uint32_t inputCount = context->getNumInputs();
    NN_RET_CHECK(inputCount == kNumInputs1 || inputCount == kNumInputs2);
    NN_RET_CHECK_EQ(context->getNumOutputs(), kNumOutputs);
    const auto inputType = context->getInputType(kInputTensor);
    const auto filterType = context->getInputType(kFilterTensor);
    std::vector<OperandType> inExpectedTypes;
    Version minSupportedVersion = kVersionFeatureLevel3;
    if (inputType == OperandType::TENSOR_FLOAT32 || inputType == OperandType::TENSOR_FLOAT16) {
        inExpectedTypes = {inputType, inputType, inputType};
    } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM ||
               inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
        NN_RET_CHECK(filterType == OperandType::TENSOR_QUANT8_SYMM_PER_CHANNEL ||
                     filterType == inputType)
                << "Unsupported filter tensor type for operation " << kOperationName;
        if (filterType == OperandType::TENSOR_QUANT8_SYMM_PER_CHANNEL) {
            NN_RET_CHECK_EQ(std::get<Operand::SymmPerChannelQuantParams>(
                                    context->getInputExtraParams(kFilterTensor))
                                    .channelDim,
                            0u)
                    << "Unsupported filter tensor channel dimension for operation "
                    << kOperationName;
        }
        inExpectedTypes = {inputType, filterType, OperandType::TENSOR_INT32};
        if (inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
            minSupportedVersion = kVersionFeatureLevel4;
        }
    } else {
        NN_RET_CHECK_FAIL() << "Unsupported input tensor type for operation " << kOperationName;
    }

    std::vector<OperandType> argExpectedTypes;
    if (inputCount == 11) {
        argExpectedTypes = {OperandType::INT32, OperandType::INT32, OperandType::INT32,
                            OperandType::INT32, OperandType::INT32, OperandType::INT32,
                            OperandType::INT32, OperandType::BOOL};
    } else {
        argExpectedTypes = {OperandType::TENSOR_INT32, OperandType::INT32, OperandType::INT32,
                            OperandType::INT32,        OperandType::INT32, OperandType::BOOL};
    }
    inExpectedTypes.insert(inExpectedTypes.end(), argExpectedTypes.begin(), argExpectedTypes.end());
    NN_RET_CHECK(validateInputTypes(context, inExpectedTypes));
    NN_RET_CHECK(validateOutputTypes(context, {inputType}));
    return minSupportedVersion;
}

}  // namespace transpose_conv_2d

NN_DEFINE_VALIDATION_FUNCTION(TRANSPOSE_CONV_2D, transpose_conv_2d::validate);

}  // namespace android::nn
