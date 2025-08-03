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

#include "HeatmapMaxKeypoint.h"

#include <vector>

#include "OperationsValidationUtils.h"

namespace android::nn {
namespace heatmap_max_keypoint {

Result<Version> validate(const IOperationValidationContext* context) {
    NN_RET_CHECK_EQ(context->getNumInputs(), kNumInputs);
    NN_RET_CHECK_EQ(context->getNumOutputs(), kNumOutputs);
    std::vector<OperandType> inExpectedTypes;
    std::vector<OperandType> outExpectedTypes;
    auto inputType = context->getInputType(kHeatmapTensor);
    auto minSupportedVersion = kVersionFeatureLevel3;
    if (inputType == OperandType::TENSOR_FLOAT32 || inputType == OperandType::TENSOR_FLOAT16) {
        inExpectedTypes = {inputType, inputType, OperandType::BOOL};
        outExpectedTypes = {inputType, inputType};
    } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM) {
        inExpectedTypes = {OperandType::TENSOR_QUANT8_ASYMM, OperandType::TENSOR_QUANT16_ASYMM,
                           OperandType::BOOL};
        outExpectedTypes = {OperandType::TENSOR_QUANT8_ASYMM, OperandType::TENSOR_QUANT16_ASYMM};
    } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
        inExpectedTypes = {OperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                           OperandType::TENSOR_QUANT16_ASYMM, OperandType::BOOL};
        outExpectedTypes = {OperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            OperandType::TENSOR_QUANT16_ASYMM};
        minSupportedVersion = kVersionFeatureLevel4;
    } else {
        return NN_ERROR() << "Unsupported input tensor type for operation " << kOperationName;
    }
    NN_RET_CHECK(validateInputTypes(context, inExpectedTypes));
    NN_RET_CHECK(validateOutputTypes(context, outExpectedTypes));
    return minSupportedVersion;
}

}  // namespace heatmap_max_keypoint

NN_DEFINE_VALIDATION_FUNCTION(HEATMAP_MAX_KEYPOINT, heatmap_max_keypoint::validate);

}  // namespace android::nn
