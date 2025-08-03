/*
 * Copyright (C) 2021 The Android Open Source Project
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

#ifdef NN_EXPERIMENTAL_FEATURE

#include "Densify.h"

#include "OperationsValidationUtils.h"

namespace android::nn {
namespace densify_op {

Result<Version> validate(const IOperationValidationContext* context) {
    // Checking number of inputs and outputs
    const uint32_t inputCount = context->getNumInputs();
    NN_RET_CHECK_GE(inputCount, kMinNumInputs);
    NN_RET_CHECK_EQ(inputCount,
                    kMinNumInputs + context->getInputShape(kInputTravOrder).dimensions.front() * 2);
    NN_RET_CHECK_EQ(context->getNumOutputs(), kNumOutputs);
    NN_RET_CHECK_EQ(context->getInputShape(kInputTensor).dimensions.size(), 1u);
    for (uint32_t i = 1; i < inputCount; i++) {
        NN_RET_CHECK_EQ(context->getInputShape(i).dimensions.size(), 1u);
        NN_RET_CHECK_EQ(context->getInputType(i), OperandType::TENSOR_INT32);
    }
    return kVersionFeatureLevelExperimental;
}

}  // namespace densify_op

NN_DEFINE_VALIDATION_FUNCTION(DENSIFY, densify_op::validate);

}  // namespace android::nn

#endif  // NN_EXPERIMENTAL_FEATURE