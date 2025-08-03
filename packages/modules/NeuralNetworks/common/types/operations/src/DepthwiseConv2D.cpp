/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include "DepthwiseConv2D.h"

#include <vector>

#include "OperationsValidationUtils.h"

namespace android::nn {
namespace depthwise_conv_2d {

Result<Version> validate(const IOperationValidationContext* context) {
    const uint32_t numInputs = context->getNumInputs();
    NN_RET_CHECK(
            std::binary_search(std::begin(kNumInputsArray), std::end(kNumInputsArray), numInputs));
    NN_RET_CHECK_EQ(context->getNumOutputs(), kNumOutputs);
    auto inputType = context->getInputType(kInputTensor);
    auto filterType = context->getInputType(kFilterTensor);
    std::vector<OperandType> inExpectedTypes;
    if (inputType == OperandType::TENSOR_FLOAT32) {
        inExpectedTypes = {
                OperandType::TENSOR_FLOAT32, OperandType::TENSOR_FLOAT32,
                OperandType::TENSOR_FLOAT32, OperandType::INT32,
                OperandType::INT32,          OperandType::INT32,
                OperandType::INT32,          OperandType::INT32,
        };
    } else if (inputType == OperandType::TENSOR_FLOAT16) {
        inExpectedTypes = {
                OperandType::TENSOR_FLOAT16, OperandType::TENSOR_FLOAT16,
                OperandType::TENSOR_FLOAT16, OperandType::INT32,
                OperandType::INT32,          OperandType::INT32,
                OperandType::INT32,          OperandType::INT32,
        };
    } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM ||
               inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
        NN_RET_CHECK(filterType == OperandType::TENSOR_QUANT8_SYMM_PER_CHANNEL ||
                     filterType == inputType)
                << "Unsupported filter tensor type for operation " << kOperationName;
        if (filterType == OperandType::TENSOR_QUANT8_SYMM_PER_CHANNEL) {
            NN_RET_CHECK_EQ(std::get<Operand::SymmPerChannelQuantParams>(
                                    context->getInputExtraParams(kFilterTensor))
                                    .channelDim,
                            3u)
                    << "Unsupported filter tensor channel dimension for operation "
                    << kOperationName;
        }
        inExpectedTypes = {
                inputType,          filterType,         OperandType::TENSOR_INT32,
                OperandType::INT32, OperandType::INT32, OperandType::INT32,
                OperandType::INT32, OperandType::INT32,
        };
    } else {
        NN_RET_CHECK_FAIL() << "Unsupported input tensor type for operation " << kOperationName;
    }

    // NeuralNetworks.h specifies that ANEURALNETWORKS_DEPTHWISE_CONV_2D's output must
    // meet "outputScale > inputScale * filterScale" for the operand type
    // ANEURALNETWORKS_TENSOR_QUANT8_ASYMM before API level 29. For other
    // operand types (e.g., ANEURALNETWORKS_TENSOR_FLOAT32), this constraint
    // does not apply, so by default the constraint is met.
    bool meetsQuantizedScaleConstraintBeforeV1_2 = true;
    if (inputType == OperandType::TENSOR_QUANT8_ASYMM) {
        const float inputScale = context->getInputShape(kInputTensor).scale;
        const float filterScale = context->getInputShape(kFilterTensor).scale;
        const float outputScale = context->getInputShape(kOutputTensor).scale;
        meetsQuantizedScaleConstraintBeforeV1_2 = (outputScale > inputScale * filterScale);
    }

    bool withExplicitPadding = false;
    bool withLayout = false;
    bool withDilation = false;
    if (numInputs >= 9) {
        if (context->getInputType(8) == OperandType::INT32 && numInputs >= 11) {
            std::vector<OperandType> explicitScalarTypes(3, OperandType::INT32);
            inExpectedTypes.insert(inExpectedTypes.end(), explicitScalarTypes.begin(),
                                   explicitScalarTypes.end());
            withExplicitPadding = true;
        }
        int inputOffset = withExplicitPadding ? 3 : 0;
        if (numInputs >= 9u + inputOffset) {
            inExpectedTypes.push_back(OperandType::BOOL);
            withLayout = true;
        }
        NN_RET_CHECK_NE(numInputs, 10u + inputOffset)
                << "Provided only one dilation factor value, two values are required for operation "
                << kOperationName;
        if (numInputs == 11u + inputOffset) {
            inExpectedTypes.push_back(OperandType::INT32);
            inExpectedTypes.push_back(OperandType::INT32);
            withDilation = true;
        }
    }

    auto minSupportedVersion = kVersionFeatureLevel1;
    if (inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
        minSupportedVersion = kVersionFeatureLevel4;
    } else if (inputType == OperandType::TENSOR_FLOAT16 ||
               filterType == OperandType::TENSOR_QUANT8_SYMM_PER_CHANNEL || withLayout ||
               withDilation || !meetsQuantizedScaleConstraintBeforeV1_2) {
        minSupportedVersion = kVersionFeatureLevel3;
    } else {
        minSupportedVersion = kVersionFeatureLevel1;
    }
    NN_RET_CHECK(validateInputTypes(context, inExpectedTypes));
    NN_RET_CHECK(validateOutputTypes(context, {inputType}));
    return minSupportedVersion;
}

}  // namespace depthwise_conv_2d

NN_DEFINE_VALIDATION_FUNCTION(DEPTHWISE_CONV_2D, depthwise_conv_2d::validate);

}  // namespace android::nn
