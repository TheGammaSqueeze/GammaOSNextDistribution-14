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

#ifdef NN_INCLUDE_CPU_IMPLEMENTATION
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wunused-parameter"
#pragma clang diagnostic ignored "-Wsign-compare"
#pragma clang diagnostic ignored "-Winvalid-partial-specialization"
#include <tensorflow/lite/kernels/internal/reference/reference_ops.h>
#include <tensorflow/lite/kernels/internal/runtime_shape.h>
#pragma clang diagnostic pop

#include <limits>
#include <memory>
#include <vector>

#include "CpuOperationUtils.h"
#endif  // NN_INCLUDE_CPU_IMPLEMENTATION

#include "BatchMatmul.h"
#include "OperationResolver.h"
#include "OperationsExecutionUtils.h"
#include "Tracing.h"

namespace android {
namespace nn {
namespace batch_matmul_op {

#ifdef NN_INCLUDE_CPU_IMPLEMENTATION
namespace {

// Checks if two matrices can be multiplied.
bool canMatrixMul(uint32_t LHSRow, uint32_t LHSCol, uint32_t RHSRow, uint32_t RHSCol, bool adjX,
                  bool adjY) {
    if (LHSRow == 0 || LHSCol == 0 || RHSRow == 0 || RHSCol == 0) {
        return false;
    }
    if (adjX) {
        LHSCol = LHSRow;
    }
    if (adjY) {
        RHSRow = RHSCol;
    }
    return LHSCol == RHSRow;
}

// Computes the dimensions of output tensor.
std::vector<uint32_t> computeOutputDimensions(const Shape& LHSTensorShape,
                                              const Shape& RHSTensorShape, bool adjX, bool adjY) {
    uint32_t numDims = getNumberOfDimensions(LHSTensorShape);
    auto outputTensorDimensions = LHSTensorShape.dimensions;
    outputTensorDimensions[numDims - 2] =
            adjX ? LHSTensorShape.dimensions[numDims - 1] : LHSTensorShape.dimensions[numDims - 2];
    outputTensorDimensions[numDims - 1] =
            adjY ? RHSTensorShape.dimensions[numDims - 2] : RHSTensorShape.dimensions[numDims - 1];
    return outputTensorDimensions;
}

// Swaps row and column dimensions for a shape.
Shape swapRowColumnDims(const Shape& shape) {
    Shape swappedShape = shape;
    uint32_t numDims = getNumberOfDimensions(shape);
    swappedShape.dimensions[numDims - 2] = shape.dimensions[numDims - 1];
    swappedShape.dimensions[numDims - 1] = shape.dimensions[numDims - 2];
    return swappedShape;
}

// Transposes a matrix.
template <typename T>
void transposeRowsColumns(const T* inputData, const Shape& inputShape, T* outputData) {
    Shape transposedShape = swapRowColumnDims(inputShape);
    tflite::TransposeParams params;
    int rank = getNumberOfDimensions(inputShape);
    params.perm_count = rank;
    for (int i = 0; i < rank - 2; ++i) {
        params.perm[i] = i;
    }
    params.perm[rank - 2] = rank - 1;
    params.perm[rank - 1] = rank - 2;
    tflite::reference_ops::Transpose(params, convertShapeToTflshape(inputShape), inputData,
                                     convertShapeToTflshape(transposedShape), outputData);
}

// Creates a temporary space in heap.
// Note that it is caller's responsibility to free the memory.
template <typename T>
std::unique_ptr<T[]> getTempData(uint32_t numElems) {
    return std::unique_ptr<T[]>(new (std::nothrow) T[numElems]);
}

// Performs batch matmul.
// LHS <..., A, B>  X  RHS<..., B, C>
// We assume that LHS and RHS are both row oriented (adjacent values in memory
// are in the same row) and will output in the same memory layout. However,
// TFLite's fast GEMM libraries assume RCC layout (LHS row oriented,
// RHS column oriented, output column oriented). Therefore, we perform
// RHS <..., C, B> X LHS <..., B, A>
// where output is a C X A column-oriented, which is equivalent to
// A X C row-oriented.
template <typename T>
bool batchMatMulGeneric(const T* inputLHSData, const Shape& inputLHSShape, const T* inputRHSData,
                        const Shape& inputRHSShape, const bool adjX, const bool adjY, T* outputData,
                        const Shape& outputShape) {
    NNTRACE_TRANS("batchMatMulGeneric");
    // Only performs transpose without conjugation for adjoint since complex number is not
    // supported.
    NNTRACE_COMP_SWITCH("reference_ops::Transpose");
    const T* realInputLHSData = inputLHSData;
    const T* realInputRHSData = inputRHSData;
    auto tempInputLHSData = getTempData<T>(getNumberOfElements(inputLHSShape));
    auto tempInputRHSData = getTempData<T>(getNumberOfElements(inputRHSShape));
    // For LHS, it's passed as RHS and column-oriented.
    // If adjX is false, needs to swap shape but no need to do data transpose.
    // If adjX is true, no need to swap shape but needs to do data transpose.
    // For RHS, it's passed as LHS and row-oriented.
    // If adjY is false, needs to swap shape also needs to do data transpose.
    // If adjY is true, no need to swap shape also no need to do data transpose.
    if (adjX) {
        transposeRowsColumns(inputLHSData, inputLHSShape, tempInputLHSData.get());
        realInputLHSData = tempInputLHSData.get();
    }
    if (!adjY) {
        transposeRowsColumns(inputRHSData, inputRHSShape, tempInputRHSData.get());
        realInputRHSData = tempInputRHSData.get();
    }
    Shape realInputLHSShape = adjX ? inputLHSShape : swapRowColumnDims(inputLHSShape);
    Shape realInputRHSShape = adjY ? inputRHSShape : swapRowColumnDims(inputRHSShape);
    NNTRACE_COMP_SWITCH("reference_ops::BatchMatMul");
    tflite::reference_ops::BatchMatMul(convertShapeToTflshape(realInputRHSShape), realInputRHSData,
                                       convertShapeToTflshape(realInputLHSShape), realInputLHSData,
                                       convertShapeToTflshape(outputShape), outputData);
    return true;
}

// Performs batch matmul for quantized types.
template <typename T>
bool batchMatMulQuantized(const T* inputLHSData, const Shape& inputLHSShape, const T* inputRHSData,
                          const Shape& inputRHSShape, const bool adjX, const bool adjY,
                          T* outputData, const Shape& outputShape) {
    NNTRACE_TRANS("batchMatMulQuantized");
    NNTRACE_COMP_SWITCH("reference_ops::Transpose");
    const T* realInputLHSData = inputLHSData;
    const T* realInputRHSData = inputRHSData;
    auto tempInputLHSData = getTempData<T>(getNumberOfElements(inputLHSShape));
    auto tempInputRHSData = getTempData<T>(getNumberOfElements(inputRHSShape));
    if (adjX) {
        transposeRowsColumns(inputLHSData, inputLHSShape, tempInputLHSData.get());
        realInputLHSData = tempInputLHSData.get();
    }
    if (!adjY) {
        transposeRowsColumns(inputRHSData, inputRHSShape, tempInputRHSData.get());
        realInputRHSData = tempInputRHSData.get();
    }
    Shape realInputLHSShape = adjX ? inputLHSShape : swapRowColumnDims(inputLHSShape);
    Shape realInputRHSShape = adjY ? inputRHSShape : swapRowColumnDims(inputRHSShape);

    NNTRACE_COMP_SWITCH("reference_ops::BatchMatMul");

    double realMultiplier = 0.0;
    int32_t outputMultiplier = 0;
    int32_t outputShift = 0;
    NN_RET_CHECK(GetQuantizedConvolutionMultiplier(realInputLHSShape, realInputRHSShape,
                                                   outputShape, &realMultiplier));
    NN_RET_CHECK(QuantizeMultiplier(realMultiplier, &outputMultiplier, &outputShift));
    tflite::FullyConnectedParams params;
    params.input_offset = -realInputLHSShape.offset;
    params.weights_offset = -realInputRHSShape.offset;
    params.output_offset = outputShape.offset;
    params.output_multiplier = outputMultiplier;
    params.output_shift = outputShift;
    // BatchMatMul has no fused activation functions. Therefore, sets
    // output activation min and max to min and max of int8_t.
    params.quantized_activation_min = std::numeric_limits<int8_t>::min();
    params.quantized_activation_max = std::numeric_limits<int8_t>::max();
    params.lhs_cacheable = false;
    params.rhs_cacheable = false;

    tflite::reference_ops::BatchMatMul<T, int32_t>(
            params, convertShapeToTflshape(realInputRHSShape), realInputRHSData,
            convertShapeToTflshape(realInputLHSShape), realInputLHSData,
            convertShapeToTflshape(outputShape), outputData);
    return true;
}

}  // namespace

bool prepare(IOperationExecutionContext* context) {
    Shape inputLHSTensorShape = context->getInputShape(kInputLHSTensor);
    Shape inputRHSTensorShape = context->getInputShape(kInputRHSTensor);
    // Checks two input tensors have same number of dimensions.
    NN_RET_CHECK_EQ(getNumberOfDimensions(inputLHSTensorShape),
                    getNumberOfDimensions(inputRHSTensorShape))
            << "Input tensor ranks do not match with each other.";
    NN_RET_CHECK_GE(getNumberOfDimensions(inputLHSTensorShape), 2u)
            << "Input tensor rank should be at least 2.";
    NN_RET_CHECK_LE(getNumberOfDimensions(inputLHSTensorShape), 4u)
            << "Input tensor rank should be at most 4.";
    uint32_t numDims = getNumberOfDimensions(inputLHSTensorShape);
    const bool adjX = context->getInputValue<bool>(kInputLHSAdj);
    const bool adjY = context->getInputValue<bool>(kInputRHSAdj);
    // Checks dimensions work for matrix multiplication.
    NN_RET_CHECK(canMatrixMul(getSizeOfDimension(inputLHSTensorShape, numDims - 2),
                              getSizeOfDimension(inputLHSTensorShape, numDims - 1),
                              getSizeOfDimension(inputRHSTensorShape, numDims - 2),
                              getSizeOfDimension(inputRHSTensorShape, numDims - 1), adjX, adjY))
            << "Input tensors are not able to perform matrix multiplication.";

    Shape outputTensorShape = context->getOutputShape(kOutputTensor);
    outputTensorShape.dimensions =
            computeOutputDimensions(inputLHSTensorShape, inputRHSTensorShape, adjX, adjY);
    return context->setOutputShape(kOutputTensor, outputTensorShape);
}

bool execute(IOperationExecutionContext* context) {
    switch (context->getInputType(kInputLHSTensor)) {
        case OperandType::TENSOR_FLOAT32:
            return batchMatMulGeneric(context->getInputBuffer<float>(kInputLHSTensor),
                                      context->getInputShape(kInputLHSTensor),
                                      context->getInputBuffer<float>(kInputRHSTensor),
                                      context->getInputShape(kInputRHSTensor),
                                      context->getInputValue<bool>(kInputLHSAdj),
                                      context->getInputValue<bool>(kInputRHSAdj),
                                      context->getOutputBuffer<float>(kOutputTensor),
                                      context->getOutputShape(kOutputTensor));
        case OperandType::TENSOR_FLOAT16:
            return batchMatMulGeneric(context->getInputBuffer<_Float16>(kInputLHSTensor),
                                      context->getInputShape(kInputLHSTensor),
                                      context->getInputBuffer<_Float16>(kInputRHSTensor),
                                      context->getInputShape(kInputRHSTensor),
                                      context->getInputValue<bool>(kInputLHSAdj),
                                      context->getInputValue<bool>(kInputRHSAdj),
                                      context->getOutputBuffer<_Float16>(kOutputTensor),
                                      context->getOutputShape(kOutputTensor));
        case OperandType::TENSOR_INT32:
            return batchMatMulGeneric(context->getInputBuffer<int32_t>(kInputLHSTensor),
                                      context->getInputShape(kInputLHSTensor),
                                      context->getInputBuffer<int32_t>(kInputRHSTensor),
                                      context->getInputShape(kInputRHSTensor),
                                      context->getInputValue<bool>(kInputLHSAdj),
                                      context->getInputValue<bool>(kInputRHSAdj),
                                      context->getOutputBuffer<int32_t>(kOutputTensor),
                                      context->getOutputShape(kOutputTensor));
        case OperandType::TENSOR_QUANT8_ASYMM_SIGNED:
            return batchMatMulQuantized(context->getInputBuffer<int8_t>(kInputLHSTensor),
                                        context->getInputShape(kInputLHSTensor),
                                        context->getInputBuffer<int8_t>(kInputRHSTensor),
                                        context->getInputShape(kInputRHSTensor),
                                        context->getInputValue<bool>(kInputLHSAdj),
                                        context->getInputValue<bool>(kInputRHSAdj),
                                        context->getOutputBuffer<int8_t>(kOutputTensor),
                                        context->getOutputShape(kOutputTensor));
        default:
            NN_RET_CHECK_FAIL() << "Unsupported tensor type for operation " << kOperationName;
    }
    return true;
}
#endif  // NN_INCLUDE_CPU_IMPLEMENTATION

}  // namespace batch_matmul_op

NN_REGISTER_OPERATION_DEFAULT_VALIDATION(BATCH_MATMUL, batch_matmul_op::prepare,
                                         batch_matmul_op::execute);

}  // namespace nn
}  // namespace android
