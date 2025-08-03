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

#define LOG_TAG "Operations"

#include "FullyConnected.h"

#include <vector>

#include "OperationResolver.h"
#include "Tracing.h"

#ifdef NN_INCLUDE_CPU_IMPLEMENTATION
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wunused-parameter"
#pragma clang diagnostic ignored "-Wsign-compare"
#pragma clang diagnostic ignored "-Winvalid-partial-specialization"
#include <tensorflow/lite/kernels/internal/optimized/legacy_optimized_ops.h>
#include <tensorflow/lite/kernels/internal/reference/integer_ops/fully_connected.h>
#include <tensorflow/lite/kernels/internal/reference/reference_ops.h>
#include <tensorflow/lite/kernels/internal/types.h>
#pragma clang diagnostic pop

#include "CpuOperationUtils.h"
#endif  // NN_INCLUDE_CPU_IMPLEMENTATION

namespace android {
namespace nn {
namespace fully_connected {

#ifdef NN_INCLUDE_CPU_IMPLEMENTATION
namespace {

// executionMutex is used to protect concurrent access of non-threadsafe resources
// like gemmlowp::GemmContext.
// std::mutex is safe for pthreads on Android.
static std::mutex executionMutex;

bool fullyConnectedFloat32(const float* inputData, const Shape& inputShape,
                           const float* weightsData, const Shape& weightsShape,
                           const float* biasData, const Shape& biasShape, int32_t activation,
                           float* outputData, const Shape& outputShape) {
    NNTRACE_TRANS("fullyConnectedFloat32");
    float output_activation_min, output_activation_max;
    CalculateActivationRangeFloat(activation, &output_activation_min, &output_activation_max);

    // b/80425683, optimized implementation produces incorrect results when the
    // number of input elements is the squre of batch_size.
    uint32_t batch_size = getSizeOfDimension(outputShape, 0);
    uint32_t input_n_elements = getNumberOfElements(inputShape);
    if (batch_size * batch_size == input_n_elements) {
        NNTRACE_COMP_SWITCH("reference_ops::FullyConnected");
        tflite::reference_ops::FullyConnected(inputData, convertShapeToDims(inputShape),
                                              weightsData, convertShapeToDims(weightsShape),
                                              biasData, convertShapeToDims(biasShape),
                                              output_activation_min, output_activation_max,
                                              outputData, convertShapeToDims(outputShape));
    } else {
        NNTRACE_COMP_SWITCH("optimized_ops::FullyConnected");
        tflite::optimized_ops::FullyConnected(inputData, convertShapeToDims(inputShape),
                                              weightsData, convertShapeToDims(weightsShape),
                                              biasData, convertShapeToDims(biasShape),
                                              output_activation_min, output_activation_max,
                                              outputData, convertShapeToDims(outputShape));
    }
    return true;
}

bool fullyConnectedFloat16(const _Float16* inputData, const Shape& inputShape,
                           const _Float16* weightsData, const Shape& weightsShape,
                           const _Float16* biasData, const Shape& biasShape, int32_t activation,
                           _Float16* outputData, const Shape& outputShape) {
    NNTRACE_TRANS("fullyConnectedFloat16");
    std::vector<float> inputDataFloat32(getNumberOfElements(inputShape));
    convertFloat16ToFloat32(inputData, &inputDataFloat32);
    std::vector<float> weightsDataFloat32(getNumberOfElements(weightsShape));
    convertFloat16ToFloat32(weightsData, &weightsDataFloat32);
    std::vector<float> biasDataFloat32(getNumberOfElements(biasShape));
    convertFloat16ToFloat32(biasData, &biasDataFloat32);

    std::vector<float> outputDataFloat32(getNumberOfElements(outputShape));
    fullyConnectedFloat32(inputDataFloat32.data(), inputShape, weightsDataFloat32.data(),
                          weightsShape, biasDataFloat32.data(), biasShape, activation,
                          outputDataFloat32.data(), outputShape);
    convertFloat32ToFloat16(outputDataFloat32, outputData);

    return true;
}

bool fullyConnectedQuant8(const uint8_t* inputData, const Shape& inputShape,
                          const uint8_t* weightsData, const Shape& weightsShape,
                          const int32_t* biasData, const Shape& biasShape, int32_t activation,
                          uint8_t* outputData, const Shape& outputShape) {
    NNTRACE_TRANS("fullyConnectedQuant8");
    int32_t inputOffset = -inputShape.offset;
    int32_t weightsOffset = -weightsShape.offset;
    int32_t outputOffset = outputShape.offset;

    double realMultiplier = 0.0;
    int32_t outputMultiplier = 0;
    int32_t outputShift = 0;
    int32_t outputActivationMin = 0;
    int32_t outputActivationMax = 0;

    NN_RET_CHECK(GetQuantizedConvolutionMultiplier(inputShape, weightsShape, biasShape, outputShape,
                                                   &realMultiplier));
    int exponent;
    NN_RET_CHECK(QuantizeMultiplier(realMultiplier, &outputMultiplier, &exponent));
    outputShift = -exponent;
    CalculateActivationRangeUint8(activation, outputShape, &outputActivationMin,
                                  &outputActivationMax);

    static gemmlowp::GemmContext gemmContext;

    // Prevent concurrent executions that access gemmContext.
    std::unique_lock<std::mutex> lock(executionMutex);
    // Alow gemmlowp automatically decide how many threads to use.
    gemmContext.set_max_num_threads(0);

    NNTRACE_COMP_SWITCH("optimized_ops::FullyConnected");
    tflite::optimized_ops::FullyConnected(inputData, convertShapeToDims(inputShape), inputOffset,
                                          weightsData, convertShapeToDims(weightsShape),
                                          weightsOffset, biasData, convertShapeToDims(biasShape),
                                          outputOffset, outputMultiplier, outputShift,
                                          outputActivationMin, outputActivationMax, outputData,
                                          convertShapeToDims(outputShape), &gemmContext);

    return true;
}

bool fullyConnectedQuant8(const int8_t* inputData, const Shape& inputShape,
                          const int8_t* weightsData, const Shape& weightsShape,
                          const int32_t* biasData, const Shape& biasShape, int32_t activation,
                          int8_t* outputData, const Shape& outputShape) {
    NNTRACE_TRANS("fullyConnectedQuant8Signed");

    double realMultiplier = 0.0;
    int32_t outputMultiplier = 0;
    int32_t outputShift = 0;
    int32_t outputActivationMin = 0;
    int32_t outputActivationMax = 0;

    NN_RET_CHECK(GetQuantizedConvolutionMultiplier(inputShape, weightsShape, biasShape, outputShape,
                                                   &realMultiplier));
    NN_RET_CHECK(QuantizeMultiplier(realMultiplier, &outputMultiplier, &outputShift));
    CalculateActivationRangeInt8(activation, outputShape, &outputActivationMin,
                                 &outputActivationMax);

    tflite::FullyConnectedParams params;
    params.input_offset = -inputShape.offset;
    params.weights_offset = -weightsShape.offset;
    params.output_offset = outputShape.offset;
    params.output_multiplier = outputMultiplier;
    params.output_shift = outputShift;
    params.quantized_activation_min = outputActivationMin;
    params.quantized_activation_max = outputActivationMax;

    NNTRACE_COMP_SWITCH("reference_integer_ops::FullyConnected");
    tflite::reference_integer_ops::FullyConnected(
            params, convertShapeToTflshape(inputShape), inputData,
            convertShapeToTflshape(weightsShape), weightsData, convertShapeToTflshape(biasShape),
            biasData, convertShapeToTflshape(outputShape), outputData);

    return true;
}

}  // namespace

bool prepare(IOperationExecutionContext* context) {
    Shape input = context->getInputShape(kInputTensor);
    Shape weights = context->getInputShape(kWeightsTensor);
    Shape bias = context->getInputShape(kBiasTensor);
    Shape output = context->getOutputShape(kOutputTensor);
    NN_RET_CHECK(validateShapes(input, weights, bias, &output));
    return context->setOutputShape(kOutputTensor, output);
}

bool execute(IOperationExecutionContext* context) {
    // Bypass execution in the case of zero-sized input.
    if (getNumberOfElements(context->getOutputShape(kOutputTensor)) == 0) return true;
    switch (context->getInputType(kInputTensor)) {
        case OperandType::TENSOR_FLOAT32:
            return fullyConnectedFloat32(context->getInputBuffer<float>(kInputTensor),
                                         context->getInputShape(kInputTensor),
                                         context->getInputBuffer<float>(kWeightsTensor),
                                         context->getInputShape(kWeightsTensor),
                                         context->getInputBuffer<float>(kBiasTensor),
                                         context->getInputShape(kBiasTensor),
                                         context->getInputValue<int32_t>(kActivationScalar),
                                         context->getOutputBuffer<float>(kOutputTensor),
                                         context->getOutputShape(kOutputTensor));
        case OperandType::TENSOR_FLOAT16:
            return fullyConnectedFloat16(context->getInputBuffer<_Float16>(kInputTensor),
                                         context->getInputShape(kInputTensor),
                                         context->getInputBuffer<_Float16>(kWeightsTensor),
                                         context->getInputShape(kWeightsTensor),
                                         context->getInputBuffer<_Float16>(kBiasTensor),
                                         context->getInputShape(kBiasTensor),
                                         context->getInputValue<int32_t>(kActivationScalar),
                                         context->getOutputBuffer<_Float16>(kOutputTensor),
                                         context->getOutputShape(kOutputTensor));
        case OperandType::TENSOR_QUANT8_ASYMM:
            return fullyConnectedQuant8(context->getInputBuffer<uint8_t>(kInputTensor),
                                        context->getInputShape(kInputTensor),
                                        context->getInputBuffer<uint8_t>(kWeightsTensor),
                                        context->getInputShape(kWeightsTensor),
                                        context->getInputBuffer<int32_t>(kBiasTensor),
                                        context->getInputShape(kBiasTensor),
                                        context->getInputValue<int32_t>(kActivationScalar),
                                        context->getOutputBuffer<uint8_t>(kOutputTensor),
                                        context->getOutputShape(kOutputTensor));
        case OperandType::TENSOR_QUANT8_ASYMM_SIGNED:
            return fullyConnectedQuant8(context->getInputBuffer<int8_t>(kInputTensor),
                                        context->getInputShape(kInputTensor),
                                        context->getInputBuffer<int8_t>(kWeightsTensor),
                                        context->getInputShape(kWeightsTensor),
                                        context->getInputBuffer<int32_t>(kBiasTensor),
                                        context->getInputShape(kBiasTensor),
                                        context->getInputValue<int32_t>(kActivationScalar),
                                        context->getOutputBuffer<int8_t>(kOutputTensor),
                                        context->getOutputShape(kOutputTensor));
        default:
            NN_RET_CHECK_FAIL() << "Unsupported tensor type for operation " << kOperationName;
    }
}
#endif  // NN_INCLUDE_CPU_IMPLEMENTATION

}  // namespace fully_connected

NN_REGISTER_OPERATION_DEFAULT_VALIDATION(FULLY_CONNECTED, fully_connected::prepare,
                                         fully_connected::execute, .allowZeroSizedInput = true);

}  // namespace nn
}  // namespace android
