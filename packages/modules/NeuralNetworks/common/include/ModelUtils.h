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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_MODEL_UTILS_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_MODEL_UTILS_H

#include "nnapi/Types.h"

namespace android::nn {

/**
 * @brief Removes all dead operands from the main subgraph.
 *
 * This function is intended as a cleanup after references to operands are removed from a valid
 * model (e.g., after an operation is removed), possibly causing the model to be invalid. Calling
 * removeDeadOperands will restore it as a valid model.
 *
 * @pre model != nullptr
 *
 * @param model The model to have dead operands removed.
 */
void removeDeadOperands(Model* model);

}  // namespace android::nn

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_MODEL_UTILS_H
