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

#include "Reverse.h"

#include "OperationResolver.h"
#include "OperationsExecutionUtils.h"

#ifdef NN_INCLUDE_CPU_IMPLEMENTATION

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wunused-parameter"
#pragma clang diagnostic ignored "-Wsign-compare"
#include <tensorflow/lite/kernels/internal/reference/reference_ops.h>
#pragma clang diagnostic pop

#include "CpuOperationUtils.h"
#endif  // NN_INCLUDE_CPU_IMPLEMENTATION

namespace android {
namespace nn {
namespace reverse_op {

#ifdef NN_INCLUDE_CPU_IMPLEMENTATION
bool prepare(IOperationExecutionContext* context) {
    const Shape inputShape = context->getInputShape(kInputTensor);

    // Input tensor must be of rank 1..8.
    const auto inputTensorRank = getNumberOfDimensions(inputShape);
    NN_RET_CHECK_GE(inputTensorRank, 1U);
    NN_RET_CHECK_LE(inputTensorRank, 8U);

    // Check the axis dimension value.
    const Shape axisShape = context->getInputShape(kInputAxisTensor);
    NN_RET_CHECK_EQ(getNumberOfDimensions(axisShape), 1U);
    NN_RET_CHECK_EQ(getNumberOfElements(axisShape), 1U);
    const int32_t axisDimension = (context->getInputBuffer<int32_t>(kInputAxisTensor))[0];
    NN_RET_CHECK_GE(axisDimension, 0);
    NN_RET_CHECK_LT(uint32_t(axisDimension), inputTensorRank);

    Shape outputShape = context->getOutputShape(kOutputTensor);
    NN_RET_CHECK(SetShape(inputShape, &outputShape));
    return context->setOutputShape(kOutputTensor, outputShape);
}

template <typename T>
bool reverse(IOperationExecutionContext* context) {
    // Note that the NNAPI REVERSE operation requires input and output tensor to
    // have the same dimensions.
    const tflite::RuntimeShape tensorShape =
            convertShapeToTflshape(context->getInputShape(kInputTensor));

    tflite::reference_ops::Reverse((context->getInputBuffer<int32_t>(kInputAxisTensor))[0],
                                   tensorShape, context->getInputBuffer<T>(kInputTensor),
                                   tensorShape, context->getOutputBuffer<T>(kOutputTensor));
    return true;
}

bool execute(IOperationExecutionContext* context) {
    switch (context->getInputType(kInputTensor)) {
        case OperandType::TENSOR_FLOAT16:
            return reverse<_Float16>(context);
        case OperandType::TENSOR_FLOAT32:
            return reverse<float>(context);
        case OperandType::TENSOR_QUANT8_ASYMM:
            return reverse<uint8_t>(context);
        case OperandType::TENSOR_QUANT8_ASYMM_SIGNED:
            return reverse<int8_t>(context);
        case OperandType::TENSOR_INT32:
            return reverse<int32_t>(context);
        default:
            NN_RET_CHECK_FAIL() << "Unsupported tensor type for operation " << kOperationName;
    }
}
#endif  // NN_INCLUDE_CPU_IMPLEMENTATION

}  // namespace reverse_op

NN_REGISTER_OPERATION_DEFAULT_VALIDATION(REVERSE, reverse_op::prepare, reverse_op::execute);

}  // namespace nn
}  // namespace android
