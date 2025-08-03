/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include "Elementwise.h"

#include <algorithm>
#include <cmath>
#include <functional>
#include <limits>

#include "OperationResolver.h"
#include "OperationsExecutionUtils.h"
#include "Tracing.h"

namespace android {
namespace nn {
namespace elementwise {
namespace {

template <typename IntermediateType, typename T>
inline bool compute(const std::function<IntermediateType(IntermediateType)>& func, const T* input,
                    const Shape& shape, T* output) {
    const auto size = getNumberOfElements(shape);
    for (uint32_t i = 0; i < size; ++i) {
        output[i] = static_cast<T>(func(static_cast<IntermediateType>(input[i])));
    }
    return true;
}

template <typename IntermediateType, typename T>
inline bool compute(IntermediateType func(IntermediateType), const T* input, const Shape& shape,
                    T* output) {
    return compute(std::function<IntermediateType(IntermediateType)>(func), input, shape, output);
}

template <typename IntermediateType, typename T>
auto makeQuantized(const std::function<IntermediateType(IntermediateType)>& func, float inScale,
                   T inZeroPoint, float outScale, T outZeroPoint) {
    return [func, inScale, inZeroPoint, outScale, outZeroPoint](T val) -> T {
        // For dequantization formula, see Dequantize.cpp.
        using WideT = int32_t;
        static_assert(sizeof(T) < sizeof(WideT));
        IntermediateType dequantizedVal =
                (static_cast<WideT>(val) - static_cast<WideT>(inZeroPoint)) * inScale;

        IntermediateType res = func(dequantizedVal);

        // For quantization formula, see Quantize.cpp.
        T quantizedRes = static_cast<T>(std::max<float>(
                static_cast<IntermediateType>(std::numeric_limits<T>::min()),
                std::min<float>(static_cast<IntermediateType>(std::numeric_limits<T>::max()),
                                outZeroPoint + std::round(res / outScale))));

        return quantizedRes;
    };
}

bool execute(IOperationExecutionContext* context, float func(float)) {
    switch (context->getInputType(kInputTensor)) {
        case OperandType::TENSOR_FLOAT16:
            return compute<float, _Float16>(func, context->getInputBuffer<_Float16>(kInputTensor),
                                            context->getInputShape(kInputTensor),
                                            context->getOutputBuffer<_Float16>(kOutputTensor));
        case OperandType::TENSOR_FLOAT32:
            return compute<float, float>(func, context->getInputBuffer<float>(kInputTensor),
                                         context->getInputShape(kInputTensor),
                                         context->getOutputBuffer<float>(kOutputTensor));
        default:
            NN_RET_CHECK_FAIL() << "Unsupported tensor type for elementwise operation";
    }
}

}  // namespace

bool executeAbs(IOperationExecutionContext* context) {
    switch (context->getInputType(kInputTensor)) {
        case OperandType::TENSOR_FLOAT16:
            return compute<float, _Float16>(std::abs,
                                            context->getInputBuffer<_Float16>(kInputTensor),
                                            context->getInputShape(kInputTensor),
                                            context->getOutputBuffer<_Float16>(kOutputTensor));
        case OperandType::TENSOR_FLOAT32:
            return compute<float, float>(std::abs, context->getInputBuffer<float>(kInputTensor),
                                         context->getInputShape(kInputTensor),
                                         context->getOutputBuffer<float>(kOutputTensor));
        case OperandType::TENSOR_INT32:
            return compute<int32_t, int32_t>(std::abs,
                                             context->getInputBuffer<int32_t>(kInputTensor),
                                             context->getInputShape(kInputTensor),
                                             context->getOutputBuffer<int32_t>(kOutputTensor));
        default:
            NN_RET_CHECK_FAIL() << "Unsupported tensor type for operation ABS";
    }
}

bool executeRsqrt(IOperationExecutionContext* context) {
    const std::function<float(float)> frsqrt = [](float x) { return 1.f / std::sqrt(x); };
    const auto tensorType = context->getInputType(kInputTensor);
    switch (tensorType) {
        case OperandType::TENSOR_FLOAT16:
            return compute<float, _Float16>(frsqrt, context->getInputBuffer<_Float16>(kInputTensor),
                                            context->getInputShape(kInputTensor),
                                            context->getOutputBuffer<_Float16>(kOutputTensor));
        case OperandType::TENSOR_FLOAT32:
            return compute<float, float>(frsqrt, context->getInputBuffer<float>(kInputTensor),
                                         context->getInputShape(kInputTensor),
                                         context->getOutputBuffer<float>(kOutputTensor));
        case OperandType::TENSOR_QUANT8_ASYMM: {
            const Shape inShape = context->getInputShape(kInputTensor);
            const Shape outShape = context->getOutputShape(kOutputTensor);
            return compute<uint8_t, uint8_t>(
                    makeQuantized(frsqrt, inShape.scale, static_cast<uint8_t>(inShape.offset),
                                  outShape.scale, static_cast<uint8_t>(outShape.offset)),
                    context->getInputBuffer<uint8_t>(kInputTensor),
                    context->getInputShape(kInputTensor),
                    context->getOutputBuffer<uint8_t>(kOutputTensor));
        }
        case OperandType::TENSOR_QUANT8_ASYMM_SIGNED: {
            const Shape inShape = context->getInputShape(kInputTensor);
            const Shape outShape = context->getOutputShape(kOutputTensor);
            return compute<int8_t, int8_t>(
                    makeQuantized(frsqrt, inShape.scale, static_cast<int8_t>(inShape.offset),
                                  outShape.scale, static_cast<int8_t>(outShape.offset)),
                    context->getInputBuffer<int8_t>(kInputTensor),
                    context->getInputShape(kInputTensor),
                    context->getOutputBuffer<int8_t>(kOutputTensor));
        }
        default:
            NN_RET_CHECK_FAIL() << "Unsupported tensor type " << tensorType
                                << " for operation RSQRT";
    }
}

bool prepare(IOperationExecutionContext* context) {
    Shape input = context->getInputShape(kInputTensor);
    Shape output = context->getOutputShape(kOutputTensor);
    NN_RET_CHECK(SetShape(input, &output));
    return context->setOutputShape(kOutputTensor, output);
}

bool prepareFloor(IOperationExecutionContext* context) {
    Shape input = context->getInputShape(kInputTensor);
    Shape output = context->getOutputShape(kOutputTensor);
    NN_RET_CHECK_LE(getNumberOfDimensions(input), 4u);
    NN_RET_CHECK(SetShape(input, &output));
    return context->setOutputShape(kOutputTensor, output);
}

bool executeExp(IOperationExecutionContext* context) {
    return execute(context, std::exp);
}

bool executeFloor(IOperationExecutionContext* context) {
    return execute(context, std::floor);
}

bool executeLog(IOperationExecutionContext* context) {
    return execute(context, std::log);
}

bool executeSin(IOperationExecutionContext* context) {
    return execute(context, std::sin);
}

bool executeSqrt(IOperationExecutionContext* context) {
    return execute(context, std::sqrt);
}

}  // namespace elementwise

NN_REGISTER_OPERATION_DEFAULT_VALIDATION(ABS, elementwise::prepare, elementwise::executeAbs);
NN_REGISTER_OPERATION_DEFAULT_VALIDATION(EXP, elementwise::prepare, elementwise::executeExp);
NN_REGISTER_OPERATION_DEFAULT_VALIDATION(FLOOR, elementwise::prepareFloor,
                                         elementwise::executeFloor);
NN_REGISTER_OPERATION_DEFAULT_VALIDATION(LOG, elementwise::prepare, elementwise::executeLog);
NN_REGISTER_OPERATION_DEFAULT_VALIDATION(RSQRT, elementwise::prepare, elementwise::executeRsqrt);
NN_REGISTER_OPERATION_DEFAULT_VALIDATION(SIN, elementwise::prepare, elementwise::executeSin);
NN_REGISTER_OPERATION_DEFAULT_VALIDATION(SQRT, elementwise::prepare, elementwise::executeSqrt);

}  // namespace nn
}  // namespace android
