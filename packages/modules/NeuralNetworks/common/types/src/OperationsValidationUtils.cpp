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

#define LOG_TAG "OperationValidationUtils"

#include "OperationsValidationUtils.h"

#include <android-base/logging.h>

#include <functional>
#include <vector>

#include "OperationsUtils.h"
#include "nnapi/Validation.h"

namespace android::nn {
namespace {

bool validateOperandTypes(const std::vector<OperandType>& expectedTypes, const char* tag,
                          uint32_t operandCount,
                          std::function<OperandType(uint32_t)> getOperandType) {
    NN_RET_CHECK_EQ(operandCount, expectedTypes.size());
    for (uint32_t i = 0; i < operandCount; ++i) {
        OperandType type = getOperandType(i);
        NN_RET_CHECK(type == expectedTypes[i])
                << "Invalid " << tag << " tensor type " << type << " for " << tag << " " << i
                << ", expected " << expectedTypes[i];
    }
    return true;
}

}  // namespace

std::string IOperationValidationContext::invalidInOutNumberMessage(int expIn, int expOut) const {
    std::ostringstream os;
    os << "Invalid number of input operands (" << getNumInputs() << ", expected " << expIn
       << ") or output operands (" << getNumOutputs() << ", expected " << expOut
       << ") for operation " << getOperationName();
    return os.str();
}

Result<void> IOperationValidationContext::validateOperationOperandTypes(
        const std::vector<OperandType>& inExpectedTypes,
        const std::vector<OperandType>& outExpectedInTypes) const {
    NN_RET_CHECK_EQ(getNumInputs(), inExpectedTypes.size())
            << "Wrong operand count: expected " << inExpectedTypes.size() << " inputs, got "
            << getNumInputs() << " inputs";
    NN_RET_CHECK_EQ(getNumOutputs(), outExpectedInTypes.size())
            << "Wrong operand count: expected " << outExpectedInTypes.size() << " outputs, got "
            << getNumOutputs() << " outputs";
    for (size_t i = 0; i < getNumInputs(); i++) {
        NN_RET_CHECK_EQ(getInputType(i), inExpectedTypes[i])
                << "Invalid input tensor type " << getInputType(i) << " for input " << i
                << ", expected " << inExpectedTypes[i];
    }
    for (size_t i = 0; i < getNumOutputs(); i++) {
        NN_RET_CHECK_EQ(getOutputType(i), outExpectedInTypes[i])
                << "Invalid output tensor type " << getOutputType(i) << " for input " << i
                << ", expected " << outExpectedInTypes[i];
    }

    return {};
}

bool validateInputTypes(const IOperationValidationContext* context,
                        const std::vector<OperandType>& expectedTypes) {
    return validateOperandTypes(expectedTypes, "input", context->getNumInputs(),
                                [context](uint32_t index) { return context->getInputType(index); });
}

bool validateOutputTypes(const IOperationValidationContext* context,
                         const std::vector<OperandType>& expectedTypes) {
    return validateOperandTypes(
            expectedTypes, "output", context->getNumOutputs(),
            [context](uint32_t index) { return context->getOutputType(index); });
}

bool validateVersion(const IOperationValidationContext* context, Version contextVersion,
                     Version minSupportedVersion) {
    if (!isCompliantVersion(minSupportedVersion, contextVersion)) {
        std::ostringstream message;
        message << "Operation " << context->getOperationName() << " with inputs {";
        for (uint32_t i = 0, n = context->getNumInputs(); i < n; ++i) {
            if (i != 0) {
                message << ", ";
            }
            message << context->getInputType(i);
        }
        message << "} and outputs {";
        for (uint32_t i = 0, n = context->getNumOutputs(); i < n; ++i) {
            if (i != 0) {
                message << ", ";
            }
            message << context->getOutputType(i);
        }
        message << "} is only supported since " << minSupportedVersion << " (validating using "
                << contextVersion << ")";
        NN_RET_CHECK_FAIL() << message.str();
    }
    return true;
}

}  // namespace android::nn
