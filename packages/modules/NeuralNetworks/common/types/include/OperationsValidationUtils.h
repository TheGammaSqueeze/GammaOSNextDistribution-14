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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_VALIDATION_UTILS_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_VALIDATION_UTILS_H

#include <string>
#include <vector>

#include "OperationsUtils.h"
#include "nnapi/TypeUtils.h"
#include "nnapi/Types.h"

namespace android::nn {

#define NN_VALIDATION_FUNCTION_NAME(opType) validate_##opType

#define NN_VALIDATION_FUNCTION_SIGNATURE(opType) \
    Result<Version> NN_VALIDATION_FUNCTION_NAME(opType)(const IOperationValidationContext* context)

#define NN_DEFINE_VALIDATION_FUNCTION(opType, validate) \
    NN_VALIDATION_FUNCTION_SIGNATURE(opType) { return validate(context); }

// Provides information available during graph creation to validate an operation.
class IOperationValidationContext {
   public:
    virtual ~IOperationValidationContext() {}

    virtual const char* getOperationName() const = 0;

    virtual uint32_t getNumInputs() const = 0;
    virtual OperandType getInputType(uint32_t index) const = 0;
    virtual Shape getInputShape(uint32_t index) const = 0;
    virtual const Operand::ExtraParams& getInputExtraParams(uint32_t index) const = 0;

    virtual uint32_t getNumOutputs() const = 0;
    virtual OperandType getOutputType(uint32_t index) const = 0;
    virtual Shape getOutputShape(uint32_t index) const = 0;

    std::string invalidInOutNumberMessage(int expIn, int expOut) const;

    Result<void> validateOperationOperandTypes(
            const std::vector<OperandType>& inExpectedTypes,
            const std::vector<OperandType>& outExpectedInTypes) const;
};

// Verifies that the number and types of operation inputs are as expected.
bool validateInputTypes(const IOperationValidationContext* context,
                        const std::vector<OperandType>& expectedTypes);

// Verifies that the number and types of operation outputs are as expected.
bool validateOutputTypes(const IOperationValidationContext* context,
                         const std::vector<OperandType>& expectedTypes);

// Verifies that the HAL version specified in the context is greater or equal
// than the minimal supported HAL version.
bool validateVersion(const IOperationValidationContext* context, Version contextVersion,
                     Version minSupportedVersion);

}  // namespace android::nn

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_VALIDATION_UTILS_H
