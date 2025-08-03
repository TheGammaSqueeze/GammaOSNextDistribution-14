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

#include "Quantize.h"

#include <algorithm>
#include <cmath>

#include "IndexedShapeWrapper.h"
#include "OperationResolver.h"
#include "OperationsExecutionUtils.h"
#include "Tracing.h"

namespace android {
namespace nn {
namespace quantize {
namespace {

// The quantization formula also appears in Elementwise.cpp.
template <typename T>
bool quantizeToQuant8(const T* inputData, uint8_t* outputData, const Shape& outputShape) {
    NNTRACE_COMP("quantizeToQuant8");
    uint32_t size = getNumberOfElements(outputShape);
    for (uint32_t i = 0; i < size; ++i) {
        outputData[i] = static_cast<uint8_t>(std::max<float>(
                0.0f, std::min<float>(255.0f, outputShape.offset + std::round(inputData[i] /
                                                                              outputShape.scale))));
    }
    return true;
}

// The quantization formula also appears in Elementwise.cpp.
template <typename T>
bool quantizeToQuant8Signed(const T* inputData, int8_t* outputData, const Shape& outputShape) {
    NNTRACE_COMP("quantizeToQuant8Signed");
    uint32_t size = getNumberOfElements(outputShape);
    for (uint32_t i = 0; i < size; ++i) {
        outputData[i] = static_cast<int8_t>(std::max<float>(
                -128.0f,
                std::min<float>(127.0f, outputShape.offset +
                                                std::round(inputData[i] / outputShape.scale))));
    }
    return true;
}

}  // namespace

bool prepare(IOperationExecutionContext* context) {
    const Shape& input = context->getInputShape(kInputTensor);
    Shape output = context->getOutputShape(kOutputTensor);
    output.dimensions = input.dimensions;
    return context->setOutputShape(kOutputTensor, output);
}

bool execute(IOperationExecutionContext* context) {
    // Bypass execution in the case of zero-sized input.
    if (getNumberOfElements(context->getOutputShape(kOutputTensor)) == 0) return true;

    const OperandType inputType = context->getInputType(kInputTensor);
    const OperandType outputType = context->getOutputType(kOutputTensor);
    if (inputType == OperandType::TENSOR_FLOAT32) {
        if (outputType == OperandType::TENSOR_QUANT8_ASYMM) {
            return quantizeToQuant8<float>(context->getInputBuffer<float>(kInputTensor),
                                           context->getOutputBuffer<uint8_t>(kOutputTensor),
                                           context->getOutputShape(kOutputTensor));
        } else if (outputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
            return quantizeToQuant8Signed<float>(context->getInputBuffer<float>(kInputTensor),
                                                 context->getOutputBuffer<int8_t>(kOutputTensor),
                                                 context->getOutputShape(kOutputTensor));
        }
    } else if (inputType == OperandType::TENSOR_FLOAT16) {
        if (outputType == OperandType::TENSOR_QUANT8_ASYMM) {
            return quantizeToQuant8<_Float16>(context->getInputBuffer<_Float16>(kInputTensor),
                                              context->getOutputBuffer<uint8_t>(kOutputTensor),
                                              context->getOutputShape(kOutputTensor));
        } else if (outputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
            return quantizeToQuant8Signed<_Float16>(context->getInputBuffer<_Float16>(kInputTensor),
                                                    context->getOutputBuffer<int8_t>(kOutputTensor),
                                                    context->getOutputShape(kOutputTensor));
        }
    }
    NN_RET_CHECK_FAIL() << "Unsupported tensor types combination for QUANTIZE op. (input type: "
                        << inputType << " output type: " << context->getOutputType(kOutputTensor)
                        << ")";
}

}  // namespace quantize

NN_REGISTER_OPERATION_DEFAULT_VALIDATION(QUANTIZE, quantize::prepare, quantize::execute,
                                         .allowZeroSizedInput = true);

}  // namespace nn
}  // namespace android
