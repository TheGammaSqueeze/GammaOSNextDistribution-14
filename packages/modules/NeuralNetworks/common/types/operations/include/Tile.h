/*
 * Copyright (C) 2018 The Android Open Source Project
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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_TILE_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_TILE_H

#include "OperationsValidationUtils.h"

namespace android {
namespace nn {
namespace tile {

bool prepare(const Shape& input, const int32_t* multiples, const Shape& multiplesShape,
             Shape* output);

bool eval(const uint8_t* inputData, const Shape& inputShape, const int32_t* multiples,
          uint8_t* outputData, const Shape& outputShape);

}  // namespace tile
}  // namespace nn
}  // namespace android

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_TILE_H
