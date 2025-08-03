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

#include "ChannelShuffle.h"

#include "OperationsValidationUtils.h"

namespace android::nn {
namespace channel_shuffle {

Result<Version> validate(const IOperationValidationContext* context) {
    NN_RET_CHECK_EQ(context->getNumInputs(), kNumInputs);
    NN_RET_CHECK_EQ(context->getNumOutputs(), kNumOutputs);
    auto inputType = context->getInputType(kInputTensor);
    NN_RET_CHECK(inputType == OperandType::TENSOR_FLOAT16 ||
                 inputType == OperandType::TENSOR_FLOAT32 ||
                 inputType == OperandType::TENSOR_QUANT8_ASYMM ||
                 inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED)
            << "Unsupported tensor type for operation " << kOperationName;
    const Shape& inputShape = context->getInputShape(kInputTensor);
    if (hasKnownRank(inputShape)) {
        NN_RET_CHECK_LE(getNumberOfDimensions(inputShape), 4u);
    }
    NN_RET_CHECK(validateInputTypes(context, {inputType, OperandType::INT32, OperandType::INT32}));
    NN_RET_CHECK(validateOutputTypes(context, {inputType}));
    if (inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
        return kVersionFeatureLevel4;
    } else {
        return kVersionFeatureLevel3;
    }
}

}  // namespace channel_shuffle

NN_DEFINE_VALIDATION_FUNCTION(CHANNEL_SHUFFLE, channel_shuffle::validate);

}  // namespace android::nn
