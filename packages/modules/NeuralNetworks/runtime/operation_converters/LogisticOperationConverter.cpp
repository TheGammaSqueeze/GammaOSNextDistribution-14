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

#include "LogisticOperationConverter.h"

#include <vector>

#include "OperationConverterResolver.h"
#include "SubGraphContext.h"

namespace android {
namespace nn {

Result<std::vector<int32_t>> LogisticOperationConverter::getLogisticInputs(
        const Operation& operation, SubGraphContext* context) const {
    NN_TRY(context->createTensorFlatbufferFromOperand(operation.inputs[kInputTensorIdx]));
    std::vector<int32_t> inputs{
            context->getTensorIdxFromOperandIdx(operation.inputs[kInputTensorIdx])};
    return inputs;
}

Result<std::vector<int32_t>> LogisticOperationConverter::getLogisticOutputs(
        const Operation& operation, SubGraphContext* context) const {
    NN_TRY(context->createTensorFlatbufferFromOperand(operation.outputs[kOutputTensorIdx]));
    std::vector<int32_t> outputs{
            context->getTensorIdxFromOperandIdx(operation.outputs[kOutputTensorIdx])};
    return outputs;
}

Result<void> LogisticOperationConverter::convert(const Operation& operation,
                                                 SubGraphContext* context) const {
    // add opcode for LOGISTIC if not added yet
    uint32_t opCodeIdx = context->addOpCode(OperationType::LOGISTIC);

    std::vector<int32_t> inputs = NN_TRY(getLogisticInputs(operation, context));
    std::vector<int32_t> outputs = NN_TRY(getLogisticOutputs(operation, context));

    auto optionsFlatbuffer = tflite::CreateLogSoftmaxOptions(context->getBuilder());
    auto operatorFlatbuffer = tflite::CreateOperatorDirect(
            context->getBuilder() /* builder */, opCodeIdx /* opcode_index */, &inputs /* inputs */,
            &outputs /* outputs */,
            tflite::BuiltinOptions::BuiltinOptions_LogSoftmaxOptions /* builtin_options_type */,
            optionsFlatbuffer.Union() /* builtin_options */);
    context->addOperatorFlatbuffer(operatorFlatbuffer);

    return {};
}

NN_REGISTER_OPERATION_CONVERTER(LOGISTIC, LogisticOperationConverter);

}  // namespace nn
}  // namespace android