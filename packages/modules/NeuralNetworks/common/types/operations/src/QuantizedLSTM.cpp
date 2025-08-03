/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include "QuantizedLSTM.h"

#include <vector>

#include "OperationsValidationUtils.h"

namespace android::nn {
namespace quantized_16bit_lstm {

Result<Version> validate(const IOperationValidationContext* context) {
    NN_RET_CHECK(context->getNumInputs() == 15 && context->getNumOutputs() == 2)
            << context->invalidInOutNumberMessage(15, 2);
    std::vector<OperandType> inExpectedTypes = {
            OperandType::TENSOR_QUANT8_ASYMM, OperandType::TENSOR_QUANT8_ASYMM,
            OperandType::TENSOR_QUANT8_ASYMM, OperandType::TENSOR_QUANT8_ASYMM,
            OperandType::TENSOR_QUANT8_ASYMM, OperandType::TENSOR_QUANT8_ASYMM,
            OperandType::TENSOR_QUANT8_ASYMM, OperandType::TENSOR_QUANT8_ASYMM,
            OperandType::TENSOR_QUANT8_ASYMM, OperandType::TENSOR_INT32,
            OperandType::TENSOR_INT32,        OperandType::TENSOR_INT32,
            OperandType::TENSOR_INT32,        OperandType::TENSOR_QUANT16_SYMM,
            OperandType::TENSOR_QUANT8_ASYMM};
    std::vector<OperandType> outExpectedTypes = {OperandType::TENSOR_QUANT16_SYMM,
                                                 OperandType::TENSOR_QUANT8_ASYMM};
    NN_TRY(context->validateOperationOperandTypes(inExpectedTypes, outExpectedTypes));
    return kVersionFeatureLevel3;
}

}  // namespace quantized_16bit_lstm

NN_DEFINE_VALIDATION_FUNCTION(QUANTIZED_16BIT_LSTM, quantized_16bit_lstm::validate);

}  // namespace android::nn
