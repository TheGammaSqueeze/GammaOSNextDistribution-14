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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_OPERATION_CONVERTERS_ARITHMETIC_OPERATION_CONVERTER_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_OPERATION_CONVERTERS_ARITHMETIC_OPERATION_CONVERTER_H

#include <vector>

#include "OperationConverter.h"

namespace android {
namespace nn {

class ArithmeticOperationConverterBase : public IOperationConverter {
   protected:
    Result<std::vector<int32_t>> getArithmeticInputs(const Operation& operation,
                                                     SubGraphContext* context) const;
    Result<std::vector<int32_t>> getArithmeticOutputs(const Operation& operation,
                                                      SubGraphContext* context) const;

    // Offset locations of BuiltinOption parameters in NNAPI Operand inputs
    static constexpr int kActivationOffset = 0;

   private:
    // Locations of Operator inputs in a NNAPI Operation
    static constexpr int kInput1TensorIdx = 0;
    static constexpr int kInput2TensorIdx = 1;

    // Location of Operator outputs in a NNAPI Operation
    static constexpr int kOutputTensorIdx = 0;
};

}  // namespace nn
}  // namespace android

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_OPERATION_CONVERTERS_ARITHMETIC_OPERATION_CONVERTER_H