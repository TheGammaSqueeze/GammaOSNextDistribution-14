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

#include "Fill.h"

#include "OperationResolver.h"
#include "OperationsExecutionUtils.h"

namespace android {
namespace nn {
namespace fill_op {
namespace {

template <typename T>
bool executeTyped(IOperationExecutionContext* context) {
    T* output = context->getOutputBuffer<T>(kOutputTensor);
    const int numElements = getNumberOfElements(context->getOutputShape(kOutputTensor));
    const T value = context->getInputValue<T>(kValueScalar);
    for (int i = 0; i < numElements; ++i) {
        output[i] = value;
    }
    return true;
}

}  // namespace

bool prepare(IOperationExecutionContext* context) {
    Shape dimsShape = context->getInputShape(kDimsTensor);
    NN_RET_CHECK_EQ(getNumberOfDimensions(dimsShape), 1u);

    Shape outputShape = context->getOutputShape(kOutputTensor);
    outputShape.dimensions.resize(dimsShape.dimensions[0]);
    const int32_t* dims = context->getInputBuffer<int32_t>(kDimsTensor);
    for (uint32_t i = 0; i < dimsShape.dimensions[0]; ++i) {
        outputShape.dimensions[i] = dims[i];
    }
    return context->setOutputShape(kOutputTensor, outputShape);
}

bool execute(IOperationExecutionContext* context) {
    switch (context->getInputType(kValueScalar)) {
        case OperandType::FLOAT16:
            return executeTyped<_Float16>(context);
        case OperandType::FLOAT32:
            return executeTyped<float>(context);
        case OperandType::INT32:
            return executeTyped<int32_t>(context);
        default:
            NN_RET_CHECK_FAIL() << "Unsupported value type for fill op.";
    }
}

}  // namespace fill_op

NN_REGISTER_OPERATION_DEFAULT_VALIDATION(FILL, fill_op::prepare, fill_op::execute);

}  // namespace nn
}  // namespace android
