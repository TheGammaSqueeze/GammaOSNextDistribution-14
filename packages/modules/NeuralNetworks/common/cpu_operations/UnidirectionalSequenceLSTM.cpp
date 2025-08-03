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

#define LOG_TAG "Operations"

#include "UnidirectionalSequenceLSTM.h"

#include <vector>

#include "IndexedShapeWrapper.h"
#include "OperationResolver.h"
#include "OperationsExecutionUtils.h"

#ifdef NN_INCLUDE_CPU_IMPLEMENTATION
#include <tensorflow/lite/kernels/internal/tensor_utils.h>

#include "LSTM.h"
#endif  // NN_INCLUDE_CPU_IMPLEMENTATION

namespace android {
namespace nn {
namespace unidirectional_sequence_lstm {

#ifdef NN_INCLUDE_CPU_IMPLEMENTATION
namespace {

inline bool hasTensor(IOperationExecutionContext* context, const uint32_t tensor) {
    return context->getInputBuffer(tensor) != nullptr;
}

inline bool isTimeMajor(IOperationExecutionContext* context) {
    return context->getInputValue<bool>(kTimeMajorParam);
}

template <typename T>
inline LSTMParams getLSTMParams(IOperationExecutionContext* context) {
    LSTMParams params;
    params.activation =
            static_cast<ActivationFn>(context->getInputValue<int32_t>(kActivationParam));
    params.cell_clip = static_cast<float>(context->getInputValue<T>(kCellClipParam));
    params.proj_clip = static_cast<float>(context->getInputValue<T>(kProjClipParam));
    params.use_cifg = !hasTensor(context, kInputToInputWeightsTensor);
    params.use_peephole = hasTensor(context, kCellToOutputWeightsTensor);
    params.use_layer_norm = hasTensor(context, kOutputLayerNormWeightsTensor);
    params.use_projection_weight = hasTensor(context, kProjectionWeightsTensor);
    params.use_projection_bias = hasTensor(context, kProjectionBiasTensor);
    return params;
}

}  // namespace

bool prepare(IOperationExecutionContext* context) {
    // Check that none of the required inputs are omitted
    const std::vector<int> requiredInputs = {
            kInputTensor,
            kInputToForgetWeightsTensor,
            kInputToCellWeightsTensor,
            kInputToOutputWeightsTensor,
            kRecurrentToForgetWeightsTensor,
            kRecurrentToCellWeightsTensor,
            kRecurrentToOutputWeightsTensor,
            kForgetGateBiasTensor,
            kCellGateBiasTensor,
            kOutputGateBiasTensor,
            kOutputStateInTensor,
            kCellStateInTensor,
            kActivationParam,
            kCellClipParam,
            kProjClipParam,
            kTimeMajorParam,
    };
    for (const int requiredInput : requiredInputs) {
        NN_RET_CHECK(!context->isOmittedInput(requiredInput))
                << "required input " << requiredInput << " is omitted";
    }

    const Shape inputShape = context->getInputShape(kInputTensor);
    const uint32_t inputRank = getNumberOfDimensions(inputShape);
    NN_RET_CHECK_EQ(inputRank, 3u) << "Invalid input tensor rank: " << inputRank;

    [[maybe_unused]] const uint32_t maxTime =
            getSizeOfDimension(inputShape, isTimeMajor(context) ? 0 : 1);
    const uint32_t batchSize = getSizeOfDimension(inputShape, isTimeMajor(context) ? 1 : 0);
    const uint32_t inputSize = getSizeOfDimension(inputShape, inputRank - 1);

    const Shape inputToOutputShape = context->getInputShape(kInputToOutputWeightsTensor);
    NN_RET_CHECK_EQ(getNumberOfDimensions(inputToOutputShape), 2u);
    NN_RET_CHECK_EQ(getSizeOfDimension(inputToOutputShape, 1), inputSize);
    const uint32_t numCells = getSizeOfDimension(inputToOutputShape, 0);

    const Shape recurrentToOutputShape = context->getInputShape(kRecurrentToOutputWeightsTensor);
    NN_RET_CHECK_EQ(getNumberOfDimensions(recurrentToOutputShape), 2u);
    NN_RET_CHECK_EQ(getSizeOfDimension(recurrentToOutputShape, 0), numCells);
    const uint32_t outputSize = getSizeOfDimension(recurrentToOutputShape, 1);

    if (hasTensor(context, kInputToInputWeightsTensor)) {
        const Shape inputToInputShape = context->getInputShape(kInputToInputWeightsTensor);
        NN_RET_CHECK_EQ(getNumberOfDimensions(inputToInputShape), 2u);
        NN_RET_CHECK_EQ(getSizeOfDimension(inputToInputShape, 0), numCells);
        NN_RET_CHECK_EQ(getSizeOfDimension(inputToInputShape, 1), inputSize);
    }

    const Shape inputToForgetShape = context->getInputShape(kInputToForgetWeightsTensor);
    NN_RET_CHECK_EQ(getNumberOfDimensions(inputToForgetShape), 2u);
    NN_RET_CHECK_EQ(getSizeOfDimension(inputToForgetShape, 0), numCells);
    NN_RET_CHECK_EQ(getSizeOfDimension(inputToForgetShape, 1), inputSize);
    const Shape inputToCellShape = context->getInputShape(kInputToCellWeightsTensor);
    NN_RET_CHECK_EQ(getNumberOfDimensions(inputToCellShape), 2u);
    NN_RET_CHECK_EQ(getSizeOfDimension(inputToCellShape, 0), numCells);
    NN_RET_CHECK_EQ(getSizeOfDimension(inputToCellShape, 1), inputSize);

    if (hasTensor(context, kRecurrentToInputWeightsTensor)) {
        const Shape recurrentToInputShape = context->getInputShape(kRecurrentToInputWeightsTensor);
        NN_RET_CHECK_EQ(getNumberOfDimensions(recurrentToInputShape), 2u);
        NN_RET_CHECK_EQ(getSizeOfDimension(recurrentToInputShape, 0), numCells);
        NN_RET_CHECK_EQ(getSizeOfDimension(recurrentToInputShape, 1), outputSize);
    }

    const Shape recurrentToForgetShape = context->getInputShape(kRecurrentToForgetWeightsTensor);
    NN_RET_CHECK_EQ(getNumberOfDimensions(recurrentToForgetShape), 2u);
    NN_RET_CHECK_EQ(getSizeOfDimension(recurrentToForgetShape, 0), numCells);
    NN_RET_CHECK_EQ(getSizeOfDimension(recurrentToForgetShape, 1), outputSize);
    const Shape recurrentToCellShape = context->getInputShape(kRecurrentToCellWeightsTensor);
    NN_RET_CHECK_EQ(getNumberOfDimensions(recurrentToCellShape), 2u);
    NN_RET_CHECK_EQ(getSizeOfDimension(recurrentToCellShape, 0), numCells);
    NN_RET_CHECK_EQ(getSizeOfDimension(recurrentToCellShape, 1), outputSize);

    // We make sure the input-gate's parameters are either both present (regular
    // LSTM) or not at all (CIFG-LSTM).
    const bool cifgWeightsAllOrNone = (hasTensor(context, kInputToInputWeightsTensor) &&
                                       hasTensor(context, kRecurrentToInputWeightsTensor)) ||
                                      (!hasTensor(context, kInputToInputWeightsTensor) &&
                                       !hasTensor(context, kRecurrentToInputWeightsTensor));
    NN_RET_CHECK(cifgWeightsAllOrNone);

    if (hasTensor(context, kCellToInputWeightsTensor)) {
        const Shape cellToInputShape = context->getInputShape(kCellToInputWeightsTensor);
        NN_RET_CHECK_EQ(getNumberOfDimensions(cellToInputShape), 1u);
        NN_RET_CHECK_EQ(getSizeOfDimension(cellToInputShape, 0), numCells);
    }

    if (hasTensor(context, kCellToForgetWeightsTensor)) {
        const Shape cellToForgetShape = context->getInputShape(kCellToForgetWeightsTensor);
        NN_RET_CHECK_EQ(getNumberOfDimensions(cellToForgetShape), 1u);
        NN_RET_CHECK_EQ(getSizeOfDimension(cellToForgetShape, 0), numCells);
    }

    if (hasTensor(context, kCellToOutputWeightsTensor)) {
        const Shape cellToOutputShape = context->getInputShape(kCellToOutputWeightsTensor);
        NN_RET_CHECK_EQ(getNumberOfDimensions(cellToOutputShape), 1u);
        NN_RET_CHECK_EQ(getSizeOfDimension(cellToOutputShape, 0), numCells);
    }

    // Making sure the peephole weights are there all or none.
    const bool cifgUsed = !hasTensor(context, kInputToInputWeightsTensor);
    const bool peepholeWeightsAllOrNone =
            ((hasTensor(context, kCellToInputWeightsTensor) || cifgUsed) &&
             hasTensor(context, kCellToForgetWeightsTensor) &&
             hasTensor(context, kCellToOutputWeightsTensor)) ||
            (!hasTensor(context, kCellToInputWeightsTensor) &&
             !hasTensor(context, kCellToForgetWeightsTensor) &&
             !hasTensor(context, kCellToOutputWeightsTensor));
    NN_RET_CHECK(peepholeWeightsAllOrNone);

    if (!cifgUsed) {
        NN_RET_CHECK(hasTensor(context, kInputGateBiasTensor));
        const Shape inputGateBiasShape = context->getInputShape(kInputGateBiasTensor);
        NN_RET_CHECK_EQ(getNumberOfDimensions(inputGateBiasShape), 1u);
        NN_RET_CHECK_EQ(getSizeOfDimension(inputGateBiasShape, 0), numCells);
    } else {
        NN_RET_CHECK(!hasTensor(context, kInputGateBiasTensor))
                << "Input gate bias tensor is present when CIFG is used";
    }

    const Shape forgetGateBiasShape = context->getInputShape(kForgetGateBiasTensor);
    NN_RET_CHECK_EQ(getNumberOfDimensions(forgetGateBiasShape), 1u);
    NN_RET_CHECK_EQ(getSizeOfDimension(forgetGateBiasShape, 0), numCells);
    const Shape cellGateBiasShape = context->getInputShape(kCellGateBiasTensor);
    NN_RET_CHECK_EQ(getNumberOfDimensions(cellGateBiasShape), 1u);
    NN_RET_CHECK_EQ(getSizeOfDimension(cellGateBiasShape, 0), numCells);
    const Shape outputGateBiasShape = context->getInputShape(kOutputGateBiasTensor);
    NN_RET_CHECK_EQ(getNumberOfDimensions(outputGateBiasShape), 1u);
    NN_RET_CHECK_EQ(getSizeOfDimension(outputGateBiasShape, 0), numCells);

    if (hasTensor(context, kProjectionWeightsTensor)) {
        const Shape projectionShape = context->getInputShape(kProjectionWeightsTensor);
        NN_RET_CHECK_EQ(getNumberOfDimensions(projectionShape), 2u);
        NN_RET_CHECK_EQ(getSizeOfDimension(projectionShape, 0), outputSize);
        NN_RET_CHECK_EQ(getSizeOfDimension(projectionShape, 1), numCells);
    }

    if (hasTensor(context, kProjectionBiasTensor)) {
        const Shape projectionBiasShape = context->getInputShape(kProjectionBiasTensor);
        NN_RET_CHECK_EQ(getNumberOfDimensions(projectionBiasShape), 1u);
        NN_RET_CHECK_EQ(getSizeOfDimension(projectionBiasShape, 0), outputSize);
    }

    const Shape outputStateShape = context->getInputShape(kOutputStateInTensor);
    NN_RET_CHECK_EQ(getNumberOfDimensions(outputStateShape), 2u);
    NN_RET_CHECK_EQ(getSizeOfDimension(outputStateShape, 0), batchSize);
    NN_RET_CHECK_EQ(getSizeOfDimension(outputStateShape, 1), outputSize);
    const Shape cellStateShape = context->getInputShape(kCellStateInTensor);
    NN_RET_CHECK_EQ(getNumberOfDimensions(cellStateShape), 2u);
    NN_RET_CHECK_EQ(getSizeOfDimension(cellStateShape, 0), batchSize);
    NN_RET_CHECK_EQ(getSizeOfDimension(cellStateShape, 1), numCells);

    if (hasTensor(context, kInputLayerNormWeightsTensor)) {
        const Shape inputLayerNormShape = context->getInputShape(kInputLayerNormWeightsTensor);
        NN_RET_CHECK_EQ(getNumberOfDimensions(inputLayerNormShape), 1u);
        NN_RET_CHECK_EQ(getSizeOfDimension(inputLayerNormShape, 0), numCells);
    }

    if (hasTensor(context, kForgetLayerNormWeightsTensor)) {
        const Shape forgetLayerNormShape = context->getInputShape(kForgetLayerNormWeightsTensor);
        NN_RET_CHECK_EQ(getNumberOfDimensions(forgetLayerNormShape), 1u);
        NN_RET_CHECK_EQ(getSizeOfDimension(forgetLayerNormShape, 0), numCells);
    }

    if (hasTensor(context, kCellLayerNormWeightsTensor)) {
        const Shape cellLayerNormShape = context->getInputShape(kCellLayerNormWeightsTensor);
        NN_RET_CHECK_EQ(getNumberOfDimensions(cellLayerNormShape), 1u);
        NN_RET_CHECK_EQ(getSizeOfDimension(cellLayerNormShape, 0), numCells);
    }

    if (hasTensor(context, kOutputLayerNormWeightsTensor)) {
        const Shape outputLayerNormShape = context->getInputShape(kOutputLayerNormWeightsTensor);
        NN_RET_CHECK_EQ(getNumberOfDimensions(outputLayerNormShape), 1u);
        NN_RET_CHECK_EQ(getSizeOfDimension(outputLayerNormShape, 0), numCells);
    }

    if (cifgUsed) {
        NN_RET_CHECK(!hasTensor(context, kInputLayerNormWeightsTensor))
                << "Input layer norm weights tensor is present when CIFG is used";
        const bool layerNormWeightsAllOrNoneCifg =
                (hasTensor(context, kForgetLayerNormWeightsTensor) &&
                 hasTensor(context, kCellLayerNormWeightsTensor) &&
                 hasTensor(context, kOutputLayerNormWeightsTensor)) ||
                (!hasTensor(context, kForgetLayerNormWeightsTensor) &&
                 !hasTensor(context, kCellLayerNormWeightsTensor) &&
                 !hasTensor(context, kOutputLayerNormWeightsTensor));
        NN_RET_CHECK(layerNormWeightsAllOrNoneCifg);
    } else {
        const bool layerNormWeightsAllOrNone =
                (hasTensor(context, kInputLayerNormWeightsTensor) &&
                 hasTensor(context, kForgetLayerNormWeightsTensor) &&
                 hasTensor(context, kCellLayerNormWeightsTensor) &&
                 hasTensor(context, kOutputLayerNormWeightsTensor)) ||
                (!hasTensor(context, kInputLayerNormWeightsTensor) &&
                 !hasTensor(context, kForgetLayerNormWeightsTensor) &&
                 !hasTensor(context, kCellLayerNormWeightsTensor) &&
                 !hasTensor(context, kOutputLayerNormWeightsTensor));
        NN_RET_CHECK(layerNormWeightsAllOrNone);
    }

    Shape outputShape = context->getInputShape(kInputTensor);
    outputShape.dimensions[2] = outputSize;

    if (context->getNumOutputs() == kNumOutputsWithState) {
        NN_RET_CHECK(!context->isOmittedOutput(kOutputStateOutTensor));
        NN_RET_CHECK(!context->isOmittedOutput(kCellStateOutTensor));

        Shape outputStateOutTensor = context->getInputShape(kOutputStateInTensor);
        outputStateOutTensor.dimensions.resize(2);
        outputStateOutTensor.dimensions[0] = batchSize;
        outputStateOutTensor.dimensions[1] = outputSize;
        NN_RET_CHECK(context->setOutputShape(kOutputStateOutTensor, outputStateOutTensor));

        Shape cellStateOutTensor = context->getInputShape(kCellStateInTensor);
        cellStateOutTensor.dimensions.resize(2);
        cellStateOutTensor.dimensions[0] = batchSize;
        cellStateOutTensor.dimensions[1] = numCells;
        NN_RET_CHECK(context->setOutputShape(kCellStateOutTensor, cellStateOutTensor));
    }

    return context->setOutputShape(kOutputTensor, outputShape);
}

bool execute(IOperationExecutionContext* context) {
    const auto outputStateSize = getNumberOfElements(context->getInputShape(kOutputStateInTensor));
    const auto cellStateSize = getNumberOfElements(context->getInputShape(kCellStateInTensor));
    const bool use_cifg = !hasTensor(context, kInputToInputWeightsTensor);
    const auto scratchSize = use_cifg ? 3 * cellStateSize : 4 * cellStateSize;
    const bool useStateOutTensors = (context->getNumOutputs() == kNumOutputsWithState);

    const OperandType inputType = context->getInputType(kInputTensor);
    switch (inputType) {
        case OperandType::TENSOR_FLOAT32: {
            // Initialize empty vectors and resize below only if needed
            std::vector<float> outputStateOutBuffer;
            std::vector<float> cellStateOutBuffer;
            float* outputStateOut;
            float* cellStateOut;
            if (useStateOutTensors) {
                outputStateOut = context->getOutputBuffer<float>(kOutputStateOutTensor);
                cellStateOut = context->getOutputBuffer<float>(kCellStateOutTensor);
            } else {
                outputStateOutBuffer.resize(outputStateSize);
                cellStateOutBuffer.resize(cellStateSize);
                outputStateOut = outputStateOutBuffer.data();
                cellStateOut = cellStateOutBuffer.data();
            }
            std::vector<float> scratchBuffer(scratchSize);
            LSTMCell::LSTMEvalFloat32(
                    getLSTMParams<float>(context), context->getInputBuffer<float>(kInputTensor),
                    context->getInputShape(kInputTensor),
                    context->getInputBuffer<float>(kInputToInputWeightsTensor),
                    context->getInputBuffer<float>(kInputToForgetWeightsTensor),
                    context->getInputBuffer<float>(kInputToCellWeightsTensor),
                    context->getInputBuffer<float>(kInputToOutputWeightsTensor),
                    context->getInputShape(kInputToOutputWeightsTensor),
                    context->getInputBuffer<float>(kRecurrentToInputWeightsTensor),
                    context->getInputBuffer<float>(kRecurrentToForgetWeightsTensor),
                    context->getInputBuffer<float>(kRecurrentToCellWeightsTensor),
                    context->getInputBuffer<float>(kRecurrentToOutputWeightsTensor),
                    context->getInputShape(kRecurrentToOutputWeightsTensor),
                    context->getInputBuffer<float>(kCellToInputWeightsTensor),
                    context->getInputBuffer<float>(kCellToForgetWeightsTensor),
                    context->getInputBuffer<float>(kCellToOutputWeightsTensor),
                    /*aux_input_buffer=*/nullptr,
                    /*aux_input_to_input_weights_buffer=*/nullptr,
                    /*aux_input_to_forget_weights_buffer=*/nullptr,
                    /*aux_input_to_cell_weights_buffer=*/nullptr,
                    /*aux_input_to_output_weights_buffer=*/nullptr,
                    context->getInputBuffer<float>(kInputGateBiasTensor),
                    context->getInputBuffer<float>(kForgetGateBiasTensor),
                    context->getInputBuffer<float>(kCellGateBiasTensor),
                    context->getInputBuffer<float>(kOutputGateBiasTensor),
                    context->getInputBuffer<float>(kProjectionWeightsTensor),
                    context->getInputBuffer<float>(kProjectionBiasTensor),
                    context->getInputBuffer<float>(kOutputStateInTensor),
                    context->getInputBuffer<float>(kCellStateInTensor),
                    context->getInputBuffer<float>(kInputLayerNormWeightsTensor),
                    context->getInputBuffer<float>(kForgetLayerNormWeightsTensor),
                    context->getInputBuffer<float>(kCellLayerNormWeightsTensor),
                    context->getInputBuffer<float>(kOutputLayerNormWeightsTensor), outputStateOut,
                    cellStateOut, context->getOutputBuffer<float>(kOutputTensor),
                    scratchBuffer.data(), isTimeMajor(context));
        } break;
        case OperandType::TENSOR_FLOAT16: {
            // Initialize empty vectors and resize below only if needed
            std::vector<_Float16> outputStateOutBuffer;
            std::vector<_Float16> cellStateOutBuffer;
            _Float16* outputStateOut;
            _Float16* cellStateOut;
            if (useStateOutTensors) {
                outputStateOut = context->getOutputBuffer<_Float16>(kOutputStateOutTensor);
                cellStateOut = context->getOutputBuffer<_Float16>(kCellStateOutTensor);
            } else {
                outputStateOutBuffer.resize(outputStateSize);
                cellStateOutBuffer.resize(cellStateSize);
                outputStateOut = outputStateOutBuffer.data();
                cellStateOut = cellStateOutBuffer.data();
            }
            std::vector<_Float16> scratchBuffer(scratchSize);
            LSTMCell::LSTMEvalFloat16(
                    getLSTMParams<_Float16>(context),
                    context->getInputBuffer<_Float16>(kInputTensor),
                    context->getInputShape(kInputTensor),
                    context->getInputBuffer<_Float16>(kInputToInputWeightsTensor),
                    context->getInputBuffer<_Float16>(kInputToForgetWeightsTensor),
                    context->getInputBuffer<_Float16>(kInputToCellWeightsTensor),
                    context->getInputBuffer<_Float16>(kInputToOutputWeightsTensor),
                    context->getInputShape(kInputToOutputWeightsTensor),
                    context->getInputBuffer<_Float16>(kRecurrentToInputWeightsTensor),
                    context->getInputBuffer<_Float16>(kRecurrentToForgetWeightsTensor),
                    context->getInputBuffer<_Float16>(kRecurrentToCellWeightsTensor),
                    context->getInputBuffer<_Float16>(kRecurrentToOutputWeightsTensor),
                    context->getInputShape(kRecurrentToOutputWeightsTensor),
                    context->getInputBuffer<_Float16>(kCellToInputWeightsTensor),
                    context->getInputBuffer<_Float16>(kCellToForgetWeightsTensor),
                    context->getInputBuffer<_Float16>(kCellToOutputWeightsTensor),
                    /*aux_input_buffer=*/nullptr,
                    /*aux_input_to_input_weights_buffer=*/nullptr,
                    /*aux_input_to_forget_weights_buffer=*/nullptr,
                    /*aux_input_to_cell_weights_buffer=*/nullptr,
                    /*aux_input_to_output_weights_buffer=*/nullptr,
                    context->getInputBuffer<_Float16>(kInputGateBiasTensor),
                    context->getInputBuffer<_Float16>(kForgetGateBiasTensor),
                    context->getInputBuffer<_Float16>(kCellGateBiasTensor),
                    context->getInputBuffer<_Float16>(kOutputGateBiasTensor),
                    context->getInputBuffer<_Float16>(kProjectionWeightsTensor),
                    context->getInputBuffer<_Float16>(kProjectionBiasTensor),
                    context->getInputBuffer<_Float16>(kOutputStateInTensor),
                    context->getInputBuffer<_Float16>(kCellStateInTensor),
                    context->getInputBuffer<_Float16>(kInputLayerNormWeightsTensor),
                    context->getInputBuffer<_Float16>(kForgetLayerNormWeightsTensor),
                    context->getInputBuffer<_Float16>(kCellLayerNormWeightsTensor),
                    context->getInputBuffer<_Float16>(kOutputLayerNormWeightsTensor),
                    outputStateOut, cellStateOut, context->getOutputBuffer<_Float16>(kOutputTensor),
                    scratchBuffer.data(), isTimeMajor(context));
        } break;
        default: {
            LOG(ERROR) << "Unsupported data type: " << static_cast<int>(inputType);
            return false;
        }
    }
    return true;
}
#endif  // NN_INCLUDE_CPU_IMPLEMENTATION

}  // namespace unidirectional_sequence_lstm

NN_REGISTER_OPERATION_DEFAULT_VALIDATION(UNIDIRECTIONAL_SEQUENCE_LSTM,
                                         unidirectional_sequence_lstm::prepare,
                                         unidirectional_sequence_lstm::execute,
                                         .allowOmittedOperand = true);

}  // namespace nn
}  // namespace android
