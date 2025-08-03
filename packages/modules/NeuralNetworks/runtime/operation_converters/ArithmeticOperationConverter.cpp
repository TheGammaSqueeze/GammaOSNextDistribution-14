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

#include "ArithmeticOperationConverter.h"

#include <vector>

#include "OperationConverterResolver.h"
#include "SubGraphContext.h"

namespace android {
namespace nn {

Result<std::vector<int32_t>> ArithmeticOperationConverterBase::getArithmeticInputs(
        const Operation& operation, SubGraphContext* context) const {
    NN_TRY(context->createTensorFlatbufferFromOperand(operation.inputs[kInput1TensorIdx]));
    NN_TRY(context->createTensorFlatbufferFromOperand(operation.inputs[kInput2TensorIdx]));
    std::vector<int32_t> inputs{
            context->getTensorIdxFromOperandIdx(operation.inputs[kInput1TensorIdx]),
            context->getTensorIdxFromOperandIdx(operation.inputs[kInput2TensorIdx])};
    return inputs;
}

Result<std::vector<int32_t>> ArithmeticOperationConverterBase::getArithmeticOutputs(
        const Operation& operation, SubGraphContext* context) const {
    NN_TRY(context->createTensorFlatbufferFromOperand(operation.outputs[kOutputTensorIdx]));
    std::vector<int32_t> outputs{
            context->getTensorIdxFromOperandIdx(operation.outputs[kOutputTensorIdx])};
    return outputs;
}

}  // namespace nn
}  // namespace android