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

#include "Slice.h"

#include <vector>

#include "IndexedShapeWrapper.h"
#include "OperationResolver.h"

#ifdef NN_INCLUDE_CPU_IMPLEMENTATION
#include "CpuOperationUtils.h"
#endif  // NN_INCLUDE_CPU_IMPLEMENTATION

namespace android {
namespace nn {
namespace slice {

#ifdef NN_INCLUDE_CPU_IMPLEMENTATION
namespace {

template <typename T>
void addVectors(const std::vector<T>& a, const std::vector<T>& b, std::vector<T>* res) {
    for (size_t i = 0; i < res->size(); ++i) {
        res->at(i) = a[i] + b[i];
    }
}

template <typename T>
bool evalGeneric(const T* inputData, const Shape& inputShape, const int32_t* beginData,
                 const Shape& beginShape, const int32_t* /*sizeData*/, const Shape& /*sizeShape*/,
                 T* outputData, const Shape& outputShape) {
    [[maybe_unused]] const int outputSize = getNumberOfElements(outputShape);
    const IndexedShapeWrapper indexedOutput = IndexedShapeWrapper(outputShape);
    const IndexedShapeWrapper indexedInput = IndexedShapeWrapper(inputShape);
    std::vector<uint32_t> outputIndex(getNumberOfDimensions(outputShape), 0);
    std::vector<uint32_t> beginIndex(getSizeOfDimension(beginShape, 0));
    std::vector<uint32_t> inputIndex(getNumberOfDimensions(inputShape));

    for (size_t i = 0; i < beginIndex.size(); ++i) {
        beginIndex[i] = static_cast<uint32_t>(beginData[i]);
    }

    bool lastIndex = false;
    uint32_t outputOffset;
    uint32_t inputOffset;

    do {
        addVectors(outputIndex, beginIndex, &inputIndex);

        NN_RET_CHECK(indexedOutput.indexToFlatIndex(outputIndex, &outputOffset));
        NN_RET_CHECK(indexedInput.indexToFlatIndex(inputIndex, &inputOffset));

        outputData[outputOffset] = inputData[inputOffset];
        NN_RET_CHECK(indexedOutput.nextIndexInplace(&outputIndex, &lastIndex));
    } while (!lastIndex);
    return true;
}

}  // namespace

bool prepare(IOperationExecutionContext* context) {
    const Shape& inputShape = context->getInputShape(kInputTensor);
    const uint32_t n_dims = getNumberOfDimensions(inputShape);
    NN_RET_CHECK(n_dims > 0);

    const Shape& beginShape = context->getInputShape(kBeginTensor);
    NN_RET_CHECK_EQ(getNumberOfDimensions(beginShape), 1u);
    NN_RET_CHECK_EQ(getSizeOfDimension(beginShape, 0), n_dims);

    const Shape& sizeShape = context->getInputShape(kSizeTensor);
    NN_RET_CHECK_EQ(getNumberOfDimensions(sizeShape), 1u);
    NN_RET_CHECK_EQ(getSizeOfDimension(sizeShape, 0), n_dims);

    const int32_t* beginData = context->getInputBuffer<int32_t>(kBeginTensor);
    const int32_t* sizeData = context->getInputBuffer<int32_t>(kSizeTensor);

    Shape outputShape = context->getOutputShape(kOutputTensor);
    outputShape.dimensions.resize(n_dims);
    for (uint32_t i = 0; i < n_dims; ++i) {
        const int32_t sliceBegin = beginData[i];
        int32_t sliceSize = sizeData[i];
        if (sliceSize == -1) {
            sliceSize = getSizeOfDimension(inputShape, i) - sliceBegin;
        }
        NN_RET_CHECK_LE(static_cast<uint32_t>(beginData[i]), getSizeOfDimension(inputShape, i));
        NN_RET_CHECK_GE(sliceSize, 0);
        NN_RET_CHECK_LE(static_cast<uint32_t>(sliceBegin + sliceSize),
                        getSizeOfDimension(inputShape, i));
        outputShape.dimensions[i] = sliceSize;
    }
    return context->setOutputShape(kOutputTensor, outputShape);
}

bool execute(IOperationExecutionContext* context) {
    // Bypass execution in the case of zero-sized input.
    if (getNumberOfElements(context->getOutputShape(kOutputTensor)) == 0) return true;
    switch (context->getInputType(kInputTensor)) {
        case OperandType::TENSOR_FLOAT16:
            return evalGeneric(context->getInputBuffer<_Float16>(kInputTensor),
                               context->getInputShape(kInputTensor),
                               context->getInputBuffer<int32_t>(kBeginTensor),
                               context->getInputShape(kBeginTensor),
                               context->getInputBuffer<int32_t>(kSizeTensor),
                               context->getInputShape(kSizeTensor),
                               context->getOutputBuffer<_Float16>(kOutputTensor),
                               context->getOutputShape(kOutputTensor));
        case OperandType::TENSOR_FLOAT32:
            return evalGeneric(context->getInputBuffer<float>(kInputTensor),
                               context->getInputShape(kInputTensor),
                               context->getInputBuffer<int32_t>(kBeginTensor),
                               context->getInputShape(kBeginTensor),
                               context->getInputBuffer<int32_t>(kSizeTensor),
                               context->getInputShape(kSizeTensor),
                               context->getOutputBuffer<float>(kOutputTensor),
                               context->getOutputShape(kOutputTensor));
        case OperandType::TENSOR_INT32:
            return evalGeneric(context->getInputBuffer<int32_t>(kInputTensor),
                               context->getInputShape(kInputTensor),
                               context->getInputBuffer<int32_t>(kBeginTensor),
                               context->getInputShape(kBeginTensor),
                               context->getInputBuffer<int32_t>(kSizeTensor),
                               context->getInputShape(kSizeTensor),
                               context->getOutputBuffer<int32_t>(kOutputTensor),
                               context->getOutputShape(kOutputTensor));
        case OperandType::TENSOR_QUANT8_ASYMM:
            return evalGeneric(context->getInputBuffer<uint8_t>(kInputTensor),
                               context->getInputShape(kInputTensor),
                               context->getInputBuffer<int32_t>(kBeginTensor),
                               context->getInputShape(kBeginTensor),
                               context->getInputBuffer<int32_t>(kSizeTensor),
                               context->getInputShape(kSizeTensor),
                               context->getOutputBuffer<uint8_t>(kOutputTensor),
                               context->getOutputShape(kOutputTensor));
        case OperandType::TENSOR_QUANT8_ASYMM_SIGNED:
            return evalGeneric(context->getInputBuffer<int8_t>(kInputTensor),
                               context->getInputShape(kInputTensor),
                               context->getInputBuffer<int32_t>(kBeginTensor),
                               context->getInputShape(kBeginTensor),
                               context->getInputBuffer<int32_t>(kSizeTensor),
                               context->getInputShape(kSizeTensor),
                               context->getOutputBuffer<int8_t>(kOutputTensor),
                               context->getOutputShape(kOutputTensor));
        default:
            NN_RET_CHECK_FAIL() << "Unsupported tensor type for operation " << kOperationName;
    }
}
#endif  // NN_INCLUDE_CPU_IMPLEMENTATION

}  // namespace slice

NN_REGISTER_OPERATION_DEFAULT_VALIDATION(SLICE, slice::prepare, slice::execute,
                                         .allowZeroSizedInput = true);

}  // namespace nn
}  // namespace android
