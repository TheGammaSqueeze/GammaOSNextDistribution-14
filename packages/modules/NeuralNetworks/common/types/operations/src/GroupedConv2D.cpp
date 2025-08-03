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

#include "GroupedConv2D.h"

#include <vector>

#include "OperationsValidationUtils.h"

namespace android::nn {
namespace grouped_conv2d {

Result<Version> validate(const IOperationValidationContext* context) {
    NN_RET_CHECK((context->getNumInputs() == 12 || context->getNumInputs() == 9) &&
                 context->getNumOutputs() == 1)
            << "Invalid number of input operands (" << context->getNumInputs()
            << ", expected 12 or 9) or output operands (" << context->getNumOutputs()
            << ", expected 1) for operation " << context->getOperationName();
    auto inputType = context->getInputType(0);
    auto filterType = context->getInputType(1);
    std::vector<OperandType> inExpectedTypes;
    std::vector<OperandType> outExpectedTypes;
    if (inputType == OperandType::TENSOR_FLOAT32) {
        inExpectedTypes = {OperandType::TENSOR_FLOAT32, OperandType::TENSOR_FLOAT32,
                           OperandType::TENSOR_FLOAT32, OperandType::INT32,
                           OperandType::INT32,          OperandType::INT32,
                           OperandType::INT32,          OperandType::INT32};
        outExpectedTypes = {OperandType::TENSOR_FLOAT32};
    } else if (inputType == OperandType::TENSOR_FLOAT16) {
        inExpectedTypes = {OperandType::TENSOR_FLOAT16, OperandType::TENSOR_FLOAT16,
                           OperandType::TENSOR_FLOAT16, OperandType::INT32,
                           OperandType::INT32,          OperandType::INT32,
                           OperandType::INT32,          OperandType::INT32};
        outExpectedTypes = {OperandType::TENSOR_FLOAT16};
    } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM ||
               inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
        NN_RET_CHECK(filterType == inputType ||
                     filterType == OperandType::TENSOR_QUANT8_SYMM_PER_CHANNEL)
                << "Unsupported filter tensor type for operation " << context->getOperationName();

        NN_RET_CHECK(filterType != OperandType::TENSOR_QUANT8_SYMM_PER_CHANNEL ||
                     std::get<Operand::SymmPerChannelQuantParams>(context->getInputExtraParams(1))
                                     .channelDim == 0)
                << "Unsupported filter tensor channel dimension for operation "
                << context->getOperationName();

        inExpectedTypes = {inputType,          filterType,         OperandType::TENSOR_INT32,
                           OperandType::INT32, OperandType::INT32, OperandType::INT32,
                           OperandType::INT32, OperandType::INT32};
        outExpectedTypes = {inputType};
    } else {
        NN_RET_CHECK_FAIL() << "Unsupported input tensor type for operation "
                            << context->getOperationName();
    }

    if (context->getNumInputs() == 12) {
        std::vector<OperandType> explicitScalarTypes(3, OperandType::INT32);
        inExpectedTypes.insert(inExpectedTypes.end(), explicitScalarTypes.begin(),
                               explicitScalarTypes.end());
    }
    inExpectedTypes.push_back(OperandType::BOOL);
    Version version;
    if (inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
        version = kVersionFeatureLevel4;
    } else {
        version = kVersionFeatureLevel3;
    }
    NN_TRY(context->validateOperationOperandTypes(inExpectedTypes, outExpectedTypes));
    return version;
}

}  // namespace grouped_conv2d

NN_DEFINE_VALIDATION_FUNCTION(GROUPED_CONV_2D, grouped_conv2d::validate);

}  // namespace android::nn
