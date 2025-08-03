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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_OPERATION_CONVERTERS_DEPTHWISE_CONV2D_OPERATION_CONVERTER_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_OPERATION_CONVERTERS_DEPTHWISE_CONV2D_OPERATION_CONVERTER_H

#include <vector>

#include "Conv2DOperationConverter.h"

namespace android {
namespace nn {

class DepthwiseConv2DOperationConverter : public Conv2DOperationConverter {
   public:
    Result<void> convert(const Operation& operation, SubGraphContext* context) const override;

   private:
    // Offset locations of BuiltinOption parameters in NNAPI Operand inputs
    static constexpr int kStrideWOffset = 0;
    static constexpr int kStrideHOffset = 1;
    static constexpr int kDepthwiseMultiplier = 2;
    static constexpr int kActivationOffset = 3;
    static constexpr int kIsNchwOffset = 4;
    static constexpr int kDilationWOffset = 5;
    static constexpr int kDilationHOffset = 6;
};

}  // namespace nn
}  // namespace android

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_OPERATION_CONVERTERS_DEPTHWISE_CONV2D_OPERATION_CONVERTER_H