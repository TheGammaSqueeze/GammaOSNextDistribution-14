/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include "FullyConnected.h"

#include <vector>

#include "OperationsValidationUtils.h"

namespace android::nn {
namespace fully_connected {

bool validateShapes(const Shape& input, const Shape& weights, const Shape& bias, Shape* output) {
    // Check all the parameters of tensor match within themselves and match the
    // input configuration.
    NN_RET_CHECK(weights.type == input.type);
    if (input.type == OperandType::TENSOR_QUANT8_ASYMM ||
        input.type == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
        NN_RET_CHECK(bias.type == OperandType::TENSOR_INT32);
    } else {
        NN_RET_CHECK(bias.type == input.type);
    }
    // The Tensorflow fully connected layer specification says that input should
    // be of at least rank 2, so we check. Tflite doesn't check.
    NN_RET_CHECK_GE(getNumberOfDimensions(input), 2u);
    NN_RET_CHECK_LE(getNumberOfDimensions(input), 4u);
    NN_RET_CHECK_EQ(getNumberOfDimensions(weights), 2u);
    NN_RET_CHECK_EQ(getNumberOfDimensions(bias), 1u);
    uint32_t input_n_elements = getNumberOfElements(input);
    uint32_t num_units = getSizeOfDimension(weights, 0u);
    uint32_t input_size = getSizeOfDimension(weights, 1u);
    uint32_t bias_len = getSizeOfDimension(bias, 0u);
    uint32_t batch_size = 0;
    if (input_size != 0) {
        NN_RET_CHECK_EQ(input_n_elements % input_size, 0u);
        batch_size = input_n_elements / input_size;
    }
    if (num_units != 0 && bias_len != 0) {
        NN_RET_CHECK_EQ(bias_len, num_units);
    }
    if (output != nullptr) {
        // Only batch_size can be 0.
        NN_RET_CHECK_GT(num_units, 0u);
        NN_RET_CHECK_GT(input_size, 0u);
        output->type = input.type;
        output->dimensions = {batch_size, num_units};
    }
    return true;
}

Result<Version> validate(const IOperationValidationContext* context) {
    NN_RET_CHECK_EQ(context->getNumInputs(), kNumInputs);
    NN_RET_CHECK_EQ(context->getNumOutputs(), kNumOutputs);
    auto inputType = context->getInputType(kInputTensor);
    std::vector<OperandType> inExpectedTypes;
    std::vector<OperandType> outExpectedTypes;
    auto minSupportedVersion = kVersionFeatureLevel1;
    if (inputType == OperandType::TENSOR_FLOAT32) {
        minSupportedVersion = kVersionFeatureLevel1;
        inExpectedTypes = {
                OperandType::TENSOR_FLOAT32,
                OperandType::TENSOR_FLOAT32,
                OperandType::TENSOR_FLOAT32,
                OperandType::INT32,
        };
    } else if (inputType == OperandType::TENSOR_FLOAT16) {
        minSupportedVersion = kVersionFeatureLevel3;
        inExpectedTypes = {
                OperandType::TENSOR_FLOAT16,
                OperandType::TENSOR_FLOAT16,
                OperandType::TENSOR_FLOAT16,
                OperandType::INT32,
        };
    } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM) {
        // NeuralNetworks.h specifies that ANEURALNETWORKS_FULLY_CONNECTED's output must
        // meet "outputScale > inputScale * weightsScale" for the operand type
        // ANEURALNETWORKS_TENSOR_QUANT8_ASYMM before API level 29.
        const float inputScale = context->getInputShape(kInputTensor).scale;
        const float weightsScale = context->getInputShape(kWeightsTensor).scale;
        const float outputScale = context->getOutputShape(kOutputTensor).scale;
        bool meetsQuantizedScaleConstraintBeforeV1_2 = (outputScale > inputScale * weightsScale);

        if (!meetsQuantizedScaleConstraintBeforeV1_2) {
            minSupportedVersion = kVersionFeatureLevel3;
        } else {
            minSupportedVersion = kVersionFeatureLevel1;
        }

        inExpectedTypes = {
                OperandType::TENSOR_QUANT8_ASYMM,
                OperandType::TENSOR_QUANT8_ASYMM,
                OperandType::TENSOR_INT32,
                OperandType::INT32,
        };
    } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
        minSupportedVersion = kVersionFeatureLevel4;

        inExpectedTypes = {
                OperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                OperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                OperandType::TENSOR_INT32,
                OperandType::INT32,
        };
    } else {
        NN_RET_CHECK_FAIL() << "Unsupported input tensor type for operation " << kOperationName;
    }
    NN_RET_CHECK(validateInputTypes(context, inExpectedTypes));
    NN_RET_CHECK(validateOutputTypes(context, {inputType}));

    Shape input = context->getInputShape(kInputTensor);
    Shape weights = context->getInputShape(kWeightsTensor);
    Shape bias = context->getInputShape(kBiasTensor);
    if (hasKnownRank(input) && hasKnownRank(weights) && hasKnownRank(bias)) {
        NN_RET_CHECK(validateShapes(input, weights, bias));
    }

    return minSupportedVersion;
}

}  // namespace fully_connected

NN_DEFINE_VALIDATION_FUNCTION(FULLY_CONNECTED, fully_connected::validate);

}  // namespace android::nn
