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

#include "Conv2DOperationConverter.h"

#include <vector>

#include "OperationConverterResolver.h"
#include "SubGraphContext.h"

namespace android {
namespace nn {

Result<std::vector<int32_t>> Conv2DOperationConverter::getConv2DInputs(
        const Operation& operation, SubGraphContext* context) const {
    NN_RET_CHECK(isOperandConstant(
            context->getSubgraph()->operands[operation.inputs[kFilterTensorIdx]]));

    NN_TRY(context->createTensorFlatbufferFromOperand(operation.inputs[kInputTensorIdx]));
    // TFLite does not support asymmetric tensors for convolution filters
    NN_TRY(context->createTensorFlatbufferFromOperand(operation.inputs[kFilterTensorIdx],
                                                      true /* makeSymmetric */));
    NN_TRY(context->createTensorFlatbufferFromOperand(operation.inputs[kBiasTensorIdx]));
    std::vector<int32_t> inputs{
            context->getTensorIdxFromOperandIdx(operation.inputs[kInputTensorIdx]),
            context->getTensorIdxFromOperandIdx(operation.inputs[kFilterTensorIdx]),
            context->getTensorIdxFromOperandIdx(operation.inputs[kBiasTensorIdx])};
    return inputs;
}

Result<std::vector<int32_t>> Conv2DOperationConverter::getConv2DOutputs(
        const Operation& operation, SubGraphContext* context) const {
    NN_TRY(context->createTensorFlatbufferFromOperand(operation.outputs[kOutputTensorIdx]));
    std::vector<int32_t> outputs{
            context->getTensorIdxFromOperandIdx(operation.outputs[kOutputTensorIdx])};
    return outputs;
}

Result<int> Conv2DOperationConverter::decomposeExplicitPadding(const Operation& operation,
                                                               SubGraphContext* context) const {
    const Model::Subgraph* subgraph = context->getSubgraph();
    const Operand& inputOperand = subgraph->operands[operation.inputs[0]];

    // add opcode for PAD if it does not exist yet
    uint32_t opCodeIdx = context->addOpCode(OperationType::PAD);

    // pad options
    auto padOptionsFlatbuffer = tflite::CreatePadOptions(context->getBuilder());

    // check to make sure padding Operands are constants
    const Operand& frontWidthPaddingOperand = subgraph->operands[operation.inputs[3]];
    const Operand& backWidthPaddingOperand = subgraph->operands[operation.inputs[4]];
    const Operand& frontHeightPaddingOperand = subgraph->operands[operation.inputs[5]];
    const Operand& backHeightPaddingOperand = subgraph->operands[operation.inputs[6]];
    NN_RET_CHECK(isOperandConstant(frontWidthPaddingOperand));
    NN_RET_CHECK(isOperandConstant(backWidthPaddingOperand));
    NN_RET_CHECK(isOperandConstant(frontHeightPaddingOperand));
    NN_RET_CHECK(isOperandConstant(backHeightPaddingOperand));

    // get padding params
    int32_t frontHeightPadding = context->getConstantScalar<int32_t>(frontHeightPaddingOperand);
    int32_t backHeightPadding = context->getConstantScalar<int32_t>(backHeightPaddingOperand);
    int32_t frontWidthPadding = context->getConstantScalar<int32_t>(frontWidthPaddingOperand);
    int32_t backWidthPadding = context->getConstantScalar<int32_t>(backWidthPaddingOperand);

    // build padding buffer
    const Dimensions& dims = inputOperand.dimensions;
    int numDimensionsInput = static_cast<int>(dims.size());
    std::vector<int32_t> paddingData(numDimensionsInput * 2, 0);
    paddingData[2] = frontHeightPadding;
    paddingData[3] = backHeightPadding;
    paddingData[4] = frontWidthPadding;
    paddingData[5] = backWidthPadding;
    uint32_t paddingBufferIdx = context->addBufferFromData(
            reinterpret_cast<uint8_t*>(paddingData.data()), paddingData.size() * sizeof(int32_t));

    // create new tensor for padding
    std::vector<int32_t> padShape{numDimensionsInput, 2};
    auto padTensor = tflite::CreateTensorDirect(context->getBuilder(), &padShape /* shape */,
                                                tflite::TensorType::TensorType_INT32 /* type */,
                                                paddingBufferIdx /* buffer */);
    int padTensorIdx = context->addTensorFlatbuffer(padTensor);

    // add inputs for padding operation
    std::vector<int32_t> padInputs = {context->getTensorIdxFromOperandIdx(operation.inputs[0]),
                                      padTensorIdx};

    // get dimensions of output of pad operation
    std::vector<int32_t> padToConv2dShape(dims.begin(), dims.end());
    // keep unknown height and width dimensions unknown
    padToConv2dShape[1] = padToConv2dShape[1] != 0
                                  ? frontHeightPadding + padToConv2dShape[1] + backHeightPadding
                                  : -1;
    padToConv2dShape[2] = padToConv2dShape[2] != 0
                                  ? frontWidthPadding + padToConv2dShape[2] + backWidthPadding
                                  : -1;
    replaceZeroDimensions(&padToConv2dShape);

    // build quantization parameters
    std::vector<float> scaleVector{inputOperand.scale};
    std::vector<int64_t> zeroPointVector{inputOperand.zeroPoint};
    // min and max used to convert TFLite models to TF models, so it is unused in this case and can
    // be set to 0
    std::vector<float> minVector{0};
    std::vector<float> maxVector{0};
    auto quantizationParams = tflite::CreateQuantizationParametersDirect(
            context->getBuilder(), &minVector /* min */, &maxVector /* max */,
            &scaleVector /* scale */, &zeroPointVector /* zero_point */,
            tflite::QuantizationDetails::QuantizationDetails_NONE /* details_type */);

    // create new tensor to be output of pad & input for conv2d
    auto padToConv2dTensor = tflite::CreateTensorDirect(
            context->getBuilder(), &padToConv2dShape /* shape */,
            NN_TRY(getTensorFlatbufferOperandType(inputOperand.type)) /* type */, 0 /* buffer */,
            0 /* name */, quantizationParams /* quantization */);
    int padToConv2dTensorIdx = context->addTensorFlatbuffer(padToConv2dTensor);

    // set output for padding operation and add to operators
    std::vector<int32_t> padOutputs{padToConv2dTensorIdx};

    OperatorFlatbuffer padOp = tflite::CreateOperatorDirect(
            context->getBuilder(), opCodeIdx, &padInputs, &padOutputs,
            tflite::BuiltinOptions::BuiltinOptions_PadOptions, padOptionsFlatbuffer.Union());
    context->addOperatorFlatbuffer(padOp);

    // Return tensor index of pad output created
    return padToConv2dTensorIdx;
}

Result<void> Conv2DOperationConverter::convert(const Operation& operation,
                                               SubGraphContext* context) const {
    const Model::Subgraph* subgraph = context->getSubgraph();

    // add opcode for CONV_2D if not added yet
    uint32_t opCodeIdx = context->addOpCode(OperationType::CONV_2D);

    // if there are less than 8 inputs or the input at the 7th index is a BOOL, there is implicit
    // padding
    bool isImplicitPadding = false;
    if (operation.inputs.size() < 8 ||
        subgraph->operands[operation.inputs[7]].type == OperandType::BOOL) {
        isImplicitPadding = true;
    }

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

    // check if stride and activation Operands are constant
    const Operand& strideWOperand =
            subgraph->operands[operation.inputs[baseOptionsIdx + kStrideWOffset]];
    const Operand& strideHOperand =
            subgraph->operands[operation.inputs[baseOptionsIdx + kStrideHOffset]];
    const Operand& activationOperand =
            subgraph->operands[operation.inputs[baseOptionsIdx + kActivationOffset]];
    NN_RET_CHECK(isOperandConstant(strideWOperand));
    NN_RET_CHECK(isOperandConstant(strideHOperand));
    NN_RET_CHECK(isOperandConstant(activationOperand));

    // get strides and activation
    int32_t strideW = context->getConstantScalar<int32_t>(strideWOperand);
    int32_t strideH = context->getConstantScalar<int32_t>(strideHOperand);
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

    flatbuffers::Offset<tflite::Conv2DOptions> optionsFlatbuffer = tflite::CreateConv2DOptions(
            context->getBuilder(), padding, strideW, strideH,
            NN_TRY(getTfliteActivation(activation)) /* fused_activation_function */, dilationW,
            dilationH);
    auto operatorFlatbuffer = tflite::CreateOperatorDirect(
            context->getBuilder() /* builder */, opCodeIdx /* opcode_index */, &inputs /* inputs */,
            &outputs /* outputs */,
            tflite::BuiltinOptions::BuiltinOptions_Conv2DOptions /* builtin_options_type */,
            optionsFlatbuffer.Union() /* builtin_options */);
    context->addOperatorFlatbuffer(operatorFlatbuffer);

    return {};
}

NN_REGISTER_OPERATION_CONVERTER(CONV_2D, Conv2DOperationConverter);

}  // namespace nn
}  // namespace android