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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_GENERATE_PROPOSALS_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_GENERATE_PROPOSALS_H

#include "OperationsValidationUtils.h"

namespace android::nn::bbox_ops {

namespace axis_aligned_bbox_transform {

constexpr char kOperationName[] = "AXIS_ALIGNED_BBOX_TRANSFORM";

constexpr uint32_t kNumInputs = 4;
constexpr uint32_t kRoiTensor = 0;
constexpr uint32_t kDeltaTensor = 1;
constexpr uint32_t kBatchesTensor = 2;
constexpr uint32_t kImageInfoTensor = 3;

constexpr uint32_t kNumOutputs = 1;
constexpr uint32_t kOutputTensor = 0;

}  // namespace axis_aligned_bbox_transform

namespace box_with_nms_limit {

constexpr char kOperationName[] = "BOX_WITH_NMS_LIMIT";

constexpr uint32_t kNumInputs = 9;
constexpr uint32_t kScoreTensor = 0;
constexpr uint32_t kRoiTensor = 1;
constexpr uint32_t kBatchesTensor = 2;
constexpr uint32_t kScoreThresholdScalar = 3;
constexpr uint32_t kMaxNumDetectionScalar = 4;
constexpr uint32_t kNmsKernelScalar = 5;
constexpr uint32_t kIoUThresholdScalar = 6;
constexpr uint32_t kSigmaScalar = 7;
constexpr uint32_t kNmsScoreThresholdScalar = 8;

constexpr uint32_t kNumOutputs = 4;
constexpr uint32_t kOutputScoreTensor = 0;
constexpr uint32_t kOutputRoiTensor = 1;
constexpr uint32_t kOutputClassTensor = 2;
constexpr uint32_t kOutputBatchesTensor = 3;

}  // namespace box_with_nms_limit

namespace generate_proposals {

constexpr char kOperationName[] = "GENERATE_PROPOSALS";

constexpr uint32_t kNumInputs = 11;
constexpr uint32_t kScoreTensor = 0;
constexpr uint32_t kDeltaTensor = 1;
constexpr uint32_t kAnchorTensor = 2;
constexpr uint32_t kImageInfoTensor = 3;
constexpr uint32_t kHeightStrideSalar = 4;
constexpr uint32_t kWidthStrideScalar = 5;
constexpr uint32_t kPreNmsMaxScalar = 6;
constexpr uint32_t kPostNmsMaxScalar = 7;
constexpr uint32_t kIoUThresholdScalar = 8;
constexpr uint32_t kMinSizeScalar = 9;
constexpr uint32_t kLayoutScalar = 10;

constexpr uint32_t kNumOutputs = 3;
constexpr uint32_t kOutputScoreTensor = 0;
constexpr uint32_t kOutputRoiTensor = 1;
constexpr uint32_t kOutputBatchesTensor = 2;

}  // namespace generate_proposals

namespace detection_postprocess {

constexpr char kOperationName[] = "DETECTION_POSTPROCESS";

constexpr uint32_t kNumInputs = 14;
constexpr uint32_t kScoreTensor = 0;
constexpr uint32_t kDeltaTensor = 1;
constexpr uint32_t kAnchorTensor = 2;
constexpr uint32_t kScaleYScalar = 3;
constexpr uint32_t kScaleXScalar = 4;
constexpr uint32_t kScaleHScalar = 5;
constexpr uint32_t kScaleWScalar = 6;
constexpr uint32_t kUseRegularNmsScalar = 7;
constexpr uint32_t kMaxNumDetectionScalar = 8;
constexpr uint32_t kMaxClassesPerDetectionScalar = 9;
constexpr uint32_t kMaxNumDetectionPerClassScalar = 10;
constexpr uint32_t kScoreThresholdScalar = 11;
constexpr uint32_t kIoUThresholdScalar = 12;
constexpr uint32_t kIsBGInLabelScalar = 13;

constexpr uint32_t kNumOutputs = 4;
constexpr uint32_t kOutputScoreTensor = 0;
constexpr uint32_t kOutputRoiTensor = 1;
constexpr uint32_t kOutputClassTensor = 2;
constexpr uint32_t kOutputDetectionTensor = 3;

}  // namespace detection_postprocess

}  // namespace android::nn::bbox_ops

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_GENERATE_PROPOSALS_H
