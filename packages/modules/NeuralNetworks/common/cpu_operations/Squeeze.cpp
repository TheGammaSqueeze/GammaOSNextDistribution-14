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

// Contains the implementation of the operations.

#define LOG_TAG "Operations"

#include "Squeeze.h"

#include <vector>

#include "OperationResolver.h"
#include "Operations.h"
#include "Tracing.h"

namespace android {
namespace nn {
namespace squeeze {

#ifdef NN_INCLUDE_CPU_IMPLEMENTATION
bool prepare(IOperationExecutionContext* context) {
    // Only the squeeze dims tensor can be omitted.
    NN_RET_CHECK(!context->isOmittedInput(kInputTensor));
    NN_RET_CHECK(!context->isOmittedOutput(kOutputTensor));

    const int32_t* squeezeDims = context->getInputBuffer<int32_t>(kSqueezeDims);
    const Shape inputShape = context->getInputShape(kInputTensor);
    const Shape squeezeDimsShape = context->getInputShape(kSqueezeDims);
    int32_t numInputDims = static_cast<int32_t>(getNumberOfDimensions(inputShape));

    NN_RET_CHECK_LE(getNumberOfDimensions(inputShape), 4u);

    // squeezeDims need to be provided as a 1-D int32 tensor.
    NN_OPS_CHECK(squeezeDimsShape.type == OperandType::TENSOR_INT32);
    NN_OPS_CHECK(getNumberOfDimensions(squeezeDimsShape) == 1);

    std::vector<bool> shouldSqueeze(numInputDims, false);
    int32_t numDimsSqueezed = 0;

    if (context->isOmittedInput(kSqueezeDims)) {
        // If squeezeDims is omitted, all dims with value 1 will be squeezed.
        for (int32_t idx = 0; idx < numInputDims; ++idx) {
            if (getSizeOfDimension(inputShape, idx) == 1) {
                shouldSqueeze[idx] = true;
                ++numDimsSqueezed;
            }
        }
    } else {
        int32_t squeezeDimsSize = static_cast<int32_t>(getSizeOfDimension(squeezeDimsShape, 0));
        for (int32_t idx = 0; idx < squeezeDimsSize; ++idx) {
            int32_t current =
                    squeezeDims[idx] < 0 ? squeezeDims[idx] + numInputDims : squeezeDims[idx];
            NN_OPS_CHECK(current >= 0 && current < numInputDims &&
                         getSizeOfDimension(inputShape, current) == 1);
            if (!shouldSqueeze[current]) ++numDimsSqueezed;
            shouldSqueeze[current] = true;
        }
    }

    // Sets output dimensions.
    std::vector<uint32_t> outDims(numInputDims - numDimsSqueezed);
    if (numInputDims == numDimsSqueezed) {
        // Handle edge case where squeeze removes all dimensions.
        outDims.push_back(1);
    } else {
        for (int32_t inIdx = 0, outIdx = 0; inIdx < numInputDims; ++inIdx) {
            if (!shouldSqueeze[inIdx]) {
                outDims[outIdx++] = getSizeOfDimension(inputShape, inIdx);
            }
        }
    }
    Shape outputShape(inputShape);
    outputShape.dimensions = outDims;

    return context->setOutputShape(kOutputTensor, outputShape);
}

bool execute(IOperationExecutionContext* context) {
    switch (context->getInputType(kInputTensor)) {
        case OperandType::TENSOR_FLOAT16:
        case OperandType::TENSOR_FLOAT32:
        case OperandType::TENSOR_QUANT8_ASYMM:
        case OperandType::TENSOR_QUANT8_ASYMM_SIGNED:
            return copyData(context->getInputBuffer(kInputTensor),
                            context->getInputShape(kInputTensor),
                            context->getOutputBuffer(kOutputTensor),
                            context->getOutputShape(kOutputTensor));
        default:
            NN_RET_CHECK_FAIL() << "Unsupported tensor type for SQUEEZE op.";
    }
}
#endif  // NN_INCLUDE_CPU_IMPLEMENTATION

}  // namespace squeeze

NN_REGISTER_OPERATION_DEFAULT_VALIDATION(SQUEEZE, squeeze::prepare, squeeze::execute,
                                         .allowOmittedOperand = true);

}  // namespace nn
}  // namespace android
