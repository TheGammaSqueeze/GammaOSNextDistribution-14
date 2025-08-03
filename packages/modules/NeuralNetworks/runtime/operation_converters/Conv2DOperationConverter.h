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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_OPERATION_CONVERTERS_CONV2D_OPERATION_CONVERTER_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_OPERATION_CONVERTERS_CONV2D_OPERATION_CONVERTER_H

#include <vector>

#include "OperationConverter.h"

namespace android {
namespace nn {

class Conv2DOperationConverter : public IOperationConverter {
   public:
    Result<void> convert(const Operation& operation, SubGraphContext* context) const override;

   protected:
    Result<std::vector<int32_t>> getConv2DInputs(const Operation& operation,
                                                 SubGraphContext* context) const;
    Result<std::vector<int32_t>> getConv2DOutputs(const Operation& operation,
                                                  SubGraphContext* context) const;

    // Returns the output Tensor index of created Padding Operator if successful
    Result<int> decomposeExplicitPadding(const Operation& operation,
                                         SubGraphContext* context) const;

   private:
    // Offset locations of BuiltinOption parameters in NNAPI Operand inputs
    static constexpr int kStrideWOffset = 0;
    static constexpr int kStrideHOffset = 1;
    static constexpr int kActivationOffset = 2;
    static constexpr int kIsNchwOffset = 3;
    static constexpr int kDilationWOffset = 4;
    static constexpr int kDilationHOffset = 5;

    // Locations of Operator inputs in a NNAPI Operation
    static constexpr int kInputTensorIdx = 0;
    static constexpr int kFilterTensorIdx = 1;
    static constexpr int kBiasTensorIdx = 2;

    // Location of Operator outputs in a NNAPI Operation
    static constexpr int kOutputTensorIdx = 0;
};

}  // namespace nn
}  // namespace android

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_OPERATION_CONVERTERS_CONV2D_OPERATION_CONVERTER_H