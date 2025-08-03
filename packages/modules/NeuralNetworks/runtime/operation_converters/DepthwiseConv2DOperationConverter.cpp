/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include "DepthwiseConv2DOperationConverter.h"

#include <vector>

#include "OperationConverterResolver.h"
#include "SubGraphContext.h"

namespace android {
namespace nn {

Result<void> DepthwiseConv2DOperationConverter::convert(const Operation& operation,
                                                        SubGraphContext* context) const {
    const Model::Subgraph* subgraph = context->getSubgraph();

    // add opcode for DEPTHWISE_CONV_2D if not added yet
    uint32_t opCodeIdx = context->addOpCode(OperationType::DEPTHWISE_CONV_2D);

    // if there are less than 9 inputs or the input at the 8th index is a BOOL, there is implicit
    // padding
    const bool isImplicitPadding =
            (operation.inputs.size() < 9 ||
             subgraph->operands[operation.inputs[8]].type == OperandType::BOOL);

    std::vector<int32_t> inputs = NN_TRY(getConv2DInputs(operation, context));
    std::vector<int32_t> outputs = NN_TRY(getConv2DOutputs(operation, context));

    // if explicit padding, we need to decompose the operation to a separate padding op and a conv2d
    // op
    if (!isImplicitPadding) {
        auto padOpIdx = NN_TRY(decomposeExplicitPadding(operation, context));
        inputs[0] = padOpIdx;
    }

    int baseOptionsIdx = 4;
    tflite::Padding padding;
    if (isImplicitPadding) {
        const Operand& paddingTypeOperand = subgraph->operands[operation.inputs[3]];
        NN_RET_CHECK(isOperandConstant(paddingTypeOperand));

        int32_t paddingType = context->getConstantScalar<int32_t>(paddingTypeOperand);
        padding = getTFLitePadding(paddingType);
    } else {
        padding = tflite::Padding::Padding_VALID;
        baseOptionsIdx = 7;
    }

    // check if stride, depthwise multiplier, and activation Operands are constant
    const Operand& strideWOperand =
            subgraph->operands[operation.inputs[baseOptionsIdx + kStrideWOffset]];
    const Operand& strideHOperand =
            subgraph->operands[operation.inputs[baseOptionsIdx + kStrideHOffset]];
    const Operand& activationOperand =
            subgraph->operands[operation.inputs[baseOptionsIdx + kActivationOffset]];
    const Operand& depthwiseMultiplierOperand =
            subgraph->operands[operation.inputs[baseOptionsIdx + kDepthwiseMultiplier]];
    NN_RET_CHECK(isOperandConstant(strideWOperand));
    NN_RET_CHECK(isOperandConstant(strideHOperand));
    NN_RET_CHECK(isOperandConstant(activationOperand));
    NN_RET_CHECK(isOperandConstant(depthwiseMultiplierOperand));

    // get strides and activation
    int32_t strideW = context->getConstantScalar<int32_t>(strideWOperand);
    int32_t strideH = context->getConstantScalar<int32_t>(strideHOperand);
    int32_t depthwiseMultiplier = context->getConstantScalar<int32_t>(depthwiseMultiplierOperand);
    FusedActivationFunc activation = static_cast<FusedActivationFunc>(
            context->getConstantScalar<int32_t>(activationOperand));

    // check for nchw
    int isNchwIdx = baseOptionsIdx + kIsNchwOffset;
    if (operation.inputs.size() > static_cast<uint32_t>(isNchwIdx)) {
        const Operand& isNchwOperand = subgraph->operands[operation.inputs[isNchwIdx]];
        NN_RET_CHECK(isOperandConstant(isNchwOperand));

        bool isNchw = context->getConstantScalar<bool>(isNchwOperand);
        NN_RET_CHECK(!isNchw) << "TFLite does not support NCHW formatted input tensors";
    }

    // dilations
    int dilationWIdx = baseOptionsIdx + kDilationWOffset;
    int dilationHIdx = baseOptionsIdx + kDilationHOffset;
    // default dilation factors are 1
    int32_t dilationW = 1;
    int32_t dilationH = 1;
    if (operation.inputs.size() > static_cast<uint32_t>(dilationWIdx)) {
        const Operand& dilationWOperand = subgraph->operands[operation.inputs[dilationWIdx]];
        NN_RET_CHECK(isOperandConstant(dilationWOperand));

        dilationW = context->getConstantScalar<int32_t>(dilationWOperand);
    }
    if (operation.inputs.size() > static_cast<uint32_t>(dilationHIdx)) {
        const Operand& dilationHOperand = subgraph->operands[operation.inputs[dilationHIdx]];
        NN_RET_CHECK(isOperandConstant(dilationHOperand));

        dilationH = context->getConstantScalar<int32_t>(dilationHOperand);
    }

    flatbuffers::Offset<tflite::DepthwiseConv2DOptions> optionsFlatbuffer =
            tflite::CreateDepthwiseConv2DOptions(
                    context->getBuilder(), padding, strideW, strideH, depthwiseMultiplier,
                    NN_TRY(getTfliteActivation(activation)) /* fused_activation_function */,
                    dilationW, dilationH);
    auto operatorFlatbuffer = tflite::CreateOperatorDirect(
            context->getBuilder() /* builder */, opCodeIdx /* opcode_index */, &inputs /* inputs */,
            &outputs /* outputs */,
            tflite::BuiltinOptions::
                    BuiltinOptions_DepthwiseConv2DOptions /* builtin_options_type */,
            optionsFlatbuffer.Union() /* builtin_options */);
    context->addOperatorFlatbuffer(operatorFlatbuffer);

    return {};
}

NN_REGISTER_OPERATION_CONVERTER(DEPTHWISE_CONV_2D, DepthwiseConv2DOperationConverter);

}  // namespace nn
}  // namespace android