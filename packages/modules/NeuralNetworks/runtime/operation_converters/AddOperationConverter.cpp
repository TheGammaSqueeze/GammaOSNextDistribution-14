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

#include "AddOperationConverter.h"

#include <vector>

#include "OperationConverterResolver.h"
#include "SubGraphContext.h"

namespace android {
namespace nn {

Result<void> AddOperationConverter::convert(const Operation& operation,
                                            SubGraphContext* context) const {
    const Model::Subgraph* subgraph = context->getSubgraph();

    // add opcode for ADD if not added yet
    uint32_t opCodeIdx = context->addOpCode(OperationType::ADD);

    std::vector<int32_t> inputs = NN_TRY(getArithmeticInputs(operation, context));
    std::vector<int32_t> outputs = NN_TRY(getArithmeticOutputs(operation, context));

    int baseOptionsIdx = 2;

    // activation
    const Operand& activationOperand =
            subgraph->operands[operation.inputs[baseOptionsIdx + kActivationOffset]];
    NN_RET_CHECK(isOperandConstant(activationOperand));
    FusedActivationFunc activation = static_cast<FusedActivationFunc>(
            context->getConstantScalar<int32_t>(activationOperand));

    auto optionsFlatbuffer = tflite::CreateAddOptions(
            context->getBuilder(),
            NN_TRY(getTfliteActivation(activation)) /* fused_activation_function */);
    auto operatorFlatbuffer = tflite::CreateOperatorDirect(
            context->getBuilder() /* builder */, opCodeIdx /* opcode_index */, &inputs /* inputs */,
            &outputs /* outputs */,
            tflite::BuiltinOptions::BuiltinOptions_AddOptions /* builtin_options_type */,
            optionsFlatbuffer.Union() /* builtin_options */);
    context->addOperatorFlatbuffer(operatorFlatbuffer);

    return {};
}

NN_REGISTER_OPERATION_CONVERTER(ADD, AddOperationConverter);

}  // namespace nn
}  // namespace android