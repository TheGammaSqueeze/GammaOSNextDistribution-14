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

#include "GenerateProposals.h"

#include <vector>

#include "OperationsValidationUtils.h"

namespace android::nn {
namespace bbox_ops {

namespace axis_aligned_bbox_transform {

Result<Version> validate(const IOperationValidationContext* context) {
    NN_RET_CHECK_EQ(context->getNumInputs(), kNumInputs);
    NN_RET_CHECK_EQ(context->getNumOutputs(), kNumOutputs);
    std::vector<OperandType> inExpectedTypes;
    auto inputType = context->getInputType(kRoiTensor);
    auto deltaInputType = context->getInputType(kDeltaTensor);
    if (inputType == OperandType::TENSOR_FLOAT32 || inputType == OperandType::TENSOR_FLOAT16) {
        inExpectedTypes = {inputType, inputType, OperandType::TENSOR_INT32, inputType};
    } else if (inputType == OperandType::TENSOR_QUANT16_ASYMM) {
        if (deltaInputType == OperandType::TENSOR_QUANT8_ASYMM ||
            deltaInputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
            inExpectedTypes = {OperandType::TENSOR_QUANT16_ASYMM, deltaInputType,
                               OperandType::TENSOR_INT32, OperandType::TENSOR_QUANT16_ASYMM};
        } else {
            return NN_ERROR() << "Unsupported input tensor type for operation " << kOperationName;
        }
    } else {
        return NN_ERROR() << "Unsupported input tensor type for operation " << kOperationName;
    }
    NN_RET_CHECK(validateInputTypes(context, inExpectedTypes));
    NN_RET_CHECK(validateOutputTypes(context, {inputType}));
    return kVersionFeatureLevel3;
}

}  // namespace axis_aligned_bbox_transform

namespace box_with_nms_limit {

Result<Version> validate(const IOperationValidationContext* context) {
    NN_RET_CHECK_EQ(context->getNumInputs(), kNumInputs);
    NN_RET_CHECK_EQ(context->getNumOutputs(), kNumOutputs);
    std::vector<OperandType> inExpectedTypes;
    std::vector<OperandType> outExpectedTypes;
    auto inputType = context->getInputType(kScoreTensor);
    if (inputType == OperandType::TENSOR_FLOAT16) {
        inExpectedTypes = {
                OperandType::TENSOR_FLOAT16, OperandType::TENSOR_FLOAT16, OperandType::TENSOR_INT32,
                OperandType::FLOAT16,        OperandType::INT32,          OperandType::INT32,
                OperandType::FLOAT16,        OperandType::FLOAT16,        OperandType::FLOAT16};
        outExpectedTypes = {OperandType::TENSOR_FLOAT16, OperandType::TENSOR_FLOAT16,
                            OperandType::TENSOR_INT32, OperandType::TENSOR_INT32};
    } else if (inputType == OperandType::TENSOR_FLOAT32) {
        inExpectedTypes = {
                OperandType::TENSOR_FLOAT32, OperandType::TENSOR_FLOAT32, OperandType::TENSOR_INT32,
                OperandType::FLOAT32,        OperandType::INT32,          OperandType::INT32,
                OperandType::FLOAT32,        OperandType::FLOAT32,        OperandType::FLOAT32};
        outExpectedTypes = {OperandType::TENSOR_FLOAT32, OperandType::TENSOR_FLOAT32,
                            OperandType::TENSOR_INT32, OperandType::TENSOR_INT32};
    } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM ||
               inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
        inExpectedTypes = {inputType,
                           OperandType::TENSOR_QUANT16_ASYMM,
                           OperandType::TENSOR_INT32,
                           OperandType::FLOAT32,
                           OperandType::INT32,
                           OperandType::INT32,
                           OperandType::FLOAT32,
                           OperandType::FLOAT32,
                           OperandType::FLOAT32};
        outExpectedTypes = {inputType, OperandType::TENSOR_QUANT16_ASYMM, OperandType::TENSOR_INT32,
                            OperandType::TENSOR_INT32};
    } else {
        NN_RET_CHECK_FAIL() << "Unsupported tensor type for operation " << kOperationName;
    }
    NN_RET_CHECK(validateInputTypes(context, inExpectedTypes));
    NN_RET_CHECK(validateOutputTypes(context, outExpectedTypes));
    if (inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
        return kVersionFeatureLevel4;
    } else {
        return kVersionFeatureLevel3;
    }
}

}  // namespace box_with_nms_limit

namespace generate_proposals {

Result<Version> validate(const IOperationValidationContext* context) {
    NN_RET_CHECK_EQ(context->getNumInputs(), kNumInputs);
    NN_RET_CHECK_EQ(context->getNumOutputs(), kNumOutputs);
    std::vector<OperandType> inExpectedTypes;
    std::vector<OperandType> outExpectedTypes;
    auto inputType = context->getInputType(kScoreTensor);
    if (inputType == OperandType::TENSOR_FLOAT16) {
        inExpectedTypes = {OperandType::TENSOR_FLOAT16,
                           OperandType::TENSOR_FLOAT16,
                           OperandType::TENSOR_FLOAT16,
                           OperandType::TENSOR_FLOAT16,
                           OperandType::FLOAT16,
                           OperandType::FLOAT16,
                           OperandType::INT32,
                           OperandType::INT32,
                           OperandType::FLOAT16,
                           OperandType::FLOAT16,
                           OperandType::BOOL};
        outExpectedTypes = {OperandType::TENSOR_FLOAT16, OperandType::TENSOR_FLOAT16,
                            OperandType::TENSOR_INT32};
    } else if (inputType == OperandType::TENSOR_FLOAT32) {
        inExpectedTypes = {OperandType::TENSOR_FLOAT32,
                           OperandType::TENSOR_FLOAT32,
                           OperandType::TENSOR_FLOAT32,
                           OperandType::TENSOR_FLOAT32,
                           OperandType::FLOAT32,
                           OperandType::FLOAT32,
                           OperandType::INT32,
                           OperandType::INT32,
                           OperandType::FLOAT32,
                           OperandType::FLOAT32,
                           OperandType::BOOL};
        outExpectedTypes = {OperandType::TENSOR_FLOAT32, OperandType::TENSOR_FLOAT32,
                            OperandType::TENSOR_INT32};
    } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM ||
               inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
        inExpectedTypes = {inputType,
                           inputType,
                           OperandType::TENSOR_QUANT16_SYMM,
                           OperandType::TENSOR_QUANT16_ASYMM,
                           OperandType::FLOAT32,
                           OperandType::FLOAT32,
                           OperandType::INT32,
                           OperandType::INT32,
                           OperandType::FLOAT32,
                           OperandType::FLOAT32,
                           OperandType::BOOL};
        outExpectedTypes = {inputType, OperandType::TENSOR_QUANT16_ASYMM,
                            OperandType::TENSOR_INT32};
    } else {
        NN_RET_CHECK_FAIL() << "Unsupported tensor type for operation " << kOperationName;
    }
    NN_RET_CHECK(validateInputTypes(context, inExpectedTypes));
    NN_RET_CHECK(validateOutputTypes(context, outExpectedTypes));
    if (inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
        return kVersionFeatureLevel4;
    } else {
        return kVersionFeatureLevel3;
    }
}

}  // namespace generate_proposals

namespace detection_postprocess {

Result<Version> validate(const IOperationValidationContext* context) {
    NN_RET_CHECK_EQ(context->getNumInputs(), kNumInputs);
    NN_RET_CHECK_EQ(context->getNumOutputs(), kNumOutputs);
    std::vector<OperandType> inExpectedTypes;
    std::vector<OperandType> outExpectedTypes;
    auto inputType = context->getInputType(kScoreTensor);
    if (inputType == OperandType::TENSOR_FLOAT16) {
        inExpectedTypes = {OperandType::TENSOR_FLOAT16, OperandType::TENSOR_FLOAT16,
                           OperandType::TENSOR_FLOAT16, OperandType::FLOAT16,
                           OperandType::FLOAT16,        OperandType::FLOAT16,
                           OperandType::FLOAT16,        OperandType::BOOL,
                           OperandType::INT32,          OperandType::INT32,
                           OperandType::INT32,          OperandType::FLOAT16,
                           OperandType::FLOAT16,        OperandType::BOOL};
    } else if (inputType == OperandType::TENSOR_FLOAT32) {
        inExpectedTypes = {OperandType::TENSOR_FLOAT32, OperandType::TENSOR_FLOAT32,
                           OperandType::TENSOR_FLOAT32, OperandType::FLOAT32,
                           OperandType::FLOAT32,        OperandType::FLOAT32,
                           OperandType::FLOAT32,        OperandType::BOOL,
                           OperandType::INT32,          OperandType::INT32,
                           OperandType::INT32,          OperandType::FLOAT32,
                           OperandType::FLOAT32,        OperandType::BOOL};
    } else {
        NN_RET_CHECK_FAIL() << "Unsupported tensor type for operation " << kOperationName;
    }
    NN_RET_CHECK(validateInputTypes(context, inExpectedTypes));
    NN_RET_CHECK(validateOutputTypes(
            context, {inputType, inputType, OperandType::TENSOR_INT32, OperandType::TENSOR_INT32}));
    return kVersionFeatureLevel3;
}

}  // namespace detection_postprocess

}  // namespace bbox_ops

NN_DEFINE_VALIDATION_FUNCTION(AXIS_ALIGNED_BBOX_TRANSFORM,
                              bbox_ops::axis_aligned_bbox_transform::validate);
NN_DEFINE_VALIDATION_FUNCTION(BOX_WITH_NMS_LIMIT, bbox_ops::box_with_nms_limit::validate);
NN_DEFINE_VALIDATION_FUNCTION(GENERATE_PROPOSALS, bbox_ops::generate_proposals::validate);
NN_DEFINE_VALIDATION_FUNCTION(DETECTION_POSTPROCESSING, bbox_ops::detection_postprocess::validate);

}  // namespace android::nn
