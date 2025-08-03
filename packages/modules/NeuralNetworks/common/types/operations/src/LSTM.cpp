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

#include "LSTM.h"

#include <vector>

#include "OperationsValidationUtils.h"
#include "nnapi/Validation.h"

namespace android::nn {
namespace lstm {

Result<Version> validate(const IOperationValidationContext* context) {
    NN_RET_CHECK((context->getNumInputs() == 23 || context->getNumInputs() == 27) &&
                 context->getNumOutputs() == 4)
            << "Invalid number of input operands (" << context->getNumInputs()
            << ", expected 23 or 27) or output operands (" << context->getNumOutputs()
            << ", expected 4) for operation " << context->getOperationName();
    std::vector<OperandType> inExpectedTypes;
    std::vector<OperandType> outExpectedTypes;
    auto inputType = context->getInputType(0);
    NN_RET_CHECK(inputType == OperandType::TENSOR_FLOAT32 ||
                 inputType == OperandType::TENSOR_FLOAT16)
            << "Unsupported input tensor type for operation " << context->getOperationName();

    Version version = kVersionFeatureLevel1;
    inExpectedTypes = {inputType, inputType, inputType,         inputType, inputType, inputType,
                       inputType, inputType, inputType,         inputType, inputType, inputType,
                       inputType, inputType, inputType,         inputType, inputType, inputType,
                       inputType, inputType, OperandType::INT32};
    if (inputType == OperandType::TENSOR_FLOAT32) {
        inExpectedTypes.push_back(OperandType::FLOAT32);
        inExpectedTypes.push_back(OperandType::FLOAT32);
    } else {
        version = kVersionFeatureLevel3;
        inExpectedTypes.push_back(OperandType::FLOAT16);
        inExpectedTypes.push_back(OperandType::FLOAT16);
    }

    outExpectedTypes = {inputType, inputType, inputType, inputType};
    if (context->getNumInputs() == 23) {
        version = combineVersions(version, kVersionFeatureLevel1);
    } else {
        version = combineVersions(version, kVersionFeatureLevel3);
        for (int i = 0; i < 4; ++i) {
            inExpectedTypes.push_back(inputType);
        }
    }
    NN_TRY(context->validateOperationOperandTypes(inExpectedTypes, outExpectedTypes));
    return version;
}

}  // namespace lstm

NN_DEFINE_VALIDATION_FUNCTION(LSTM, lstm::validate);

}  // namespace android::nn
