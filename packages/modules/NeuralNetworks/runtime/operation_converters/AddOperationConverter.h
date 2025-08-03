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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_OPERATION_CONVERTERS_ADD_OPERATION_CONVERTER_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_OPERATION_CONVERTERS_ADD_OPERATION_CONVERTER_H

#include <vector>

#include "ArithmeticOperationConverter.h"

namespace android {
namespace nn {

class AddOperationConverter : public ArithmeticOperationConverterBase {
   public:
    Result<void> convert(const Operation& operation, SubGraphContext* context) const override;
};

}  // namespace nn
}  // namespace android

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_OPERATION_CONVERTERS_ADD_OPERATION_CONVERTER_H