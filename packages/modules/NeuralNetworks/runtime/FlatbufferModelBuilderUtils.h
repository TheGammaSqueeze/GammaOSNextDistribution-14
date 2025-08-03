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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_FLATBUFFER_MODEL_BUILDER_UTILS_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_FLATBUFFER_MODEL_BUILDER_UTILS_H

#include <nnapi/Result.h>
#include <nnapi/TypeUtils.h>
#include <tensorflow/lite/schema/schema_generated.h>

#include <algorithm>
#include <vector>

#include "NeuralNetworks.h"
#include "TypeManager.h"

namespace android {
namespace nn {

using SubGraphFlatbuffer = flatbuffers::Offset<tflite::SubGraph>;
using SubGraphsFlatbuffer = flatbuffers::Offset<flatbuffers::Vector<SubGraphFlatbuffer>>;

using OperatorCodeFlatbuffer = flatbuffers::Offset<tflite::OperatorCode>;
using OperatorFlatbuffer = flatbuffers::Offset<tflite::Operator>;
using OperatorsFlatbuffer = flatbuffers::Offset<flatbuffers::Vector<OperatorFlatbuffer>>;

using TensorFlatbuffer = flatbuffers::Offset<tflite::Tensor>;
using TensorsFlatbuffer = flatbuffers::Offset<flatbuffers::Vector<TensorFlatbuffer>>;

using BufferFlatbuffer = flatbuffers::Offset<tflite::Buffer>;

using MetadataFlatbuffer = flatbuffers::Offset<tflite::Metadata>;

using ModelFlatbuffer = flatbuffers::Offset<tflite::Model>;

// Only supports tensor types
// Will crash if passed in a scalar type
inline Result<tflite::TensorType> getTensorFlatbufferOperandType(const OperandType& type) {
    CHECK(TypeManager::get()->isTensorType(type));

    // TODO: Map more operands
    switch (type) {
        case OperandType::TENSOR_FLOAT32:
            return tflite::TensorType::TensorType_FLOAT32;
        case OperandType::TENSOR_INT32:
            return tflite::TensorType::TensorType_INT32;
        case OperandType::TENSOR_QUANT8_ASYMM_SIGNED:
            return tflite::TensorType::TensorType_INT8;
        default:
            NN_RET_CHECK_FAIL() << "OperandType not supported: " << type;
    }
}

inline tflite::BuiltinOperator getFlatbufferOperator(const OperationType& type) {
    // TODO: Add more operation types
    switch (type) {
        case OperationType::PAD:
            return tflite::BuiltinOperator::BuiltinOperator_PAD;
        case OperationType::CONV_2D:
            return tflite::BuiltinOperator::BuiltinOperator_CONV_2D;
        case OperationType::ADD:
            return tflite::BuiltinOperator::BuiltinOperator_ADD;
        case OperationType::DEPTHWISE_CONV_2D:
            return tflite::BuiltinOperator::BuiltinOperator_DEPTHWISE_CONV_2D;
        case OperationType::LOGISTIC:
            return tflite::BuiltinOperator::BuiltinOperator_LOGISTIC;
        default:
            LOG(FATAL) << "OperationType not supported: " << type;
            return {};
    }
}

// Referenced from external/tensorflow/tensorflow/lite/tools/versioning/op_version.cc
inline int32_t getMaxOperatorVersionCode(tflite::BuiltinOperator builtinCode) {
    // TODO: Add more builtin_codes
    switch (builtinCode) {
        case tflite::BuiltinOperator::BuiltinOperator_CONV_2D:
            return 5;
        case tflite::BuiltinOperator::BuiltinOperator_DEPTHWISE_CONV_2D:
            return 6;
        case tflite::BuiltinOperator::BuiltinOperator_ADD:
            return 4;
        case tflite::BuiltinOperator::BuiltinOperator_PAD:
            return 4;
        case tflite::BuiltinOperator::BuiltinOperator_LOGISTIC:
            return 3;
        default:
            LOG(FATAL) << "BuiltinOperator not supported: " << builtinCode;
            return {};
    }
}

inline Result<tflite::ActivationFunctionType> getTfliteActivation(FusedActivationFunc activation) {
    switch (activation) {
        case FusedActivationFunc::NONE:
            return tflite::ActivationFunctionType::ActivationFunctionType_NONE;
        case FusedActivationFunc::RELU:
            return tflite::ActivationFunctionType::ActivationFunctionType_RELU;
        case FusedActivationFunc::RELU1:
            return tflite::ActivationFunctionType::ActivationFunctionType_RELU_N1_TO_1;
        case FusedActivationFunc::RELU6:
            return tflite::ActivationFunctionType::ActivationFunctionType_RELU6;
        default:
            NN_RET_CHECK_FAIL() << "FusedActivationFunc not supported: " << activation;
    }
}

inline bool tensorOperandHasUnspecifiedRank(const Operand& operand) {
    return TypeManager::get()->isTensorType(operand.type) && operand.dimensions.empty();
}

inline Result<void> checkAllTensorOperandsHaveSpecifiedRank(const std::vector<Operand>& operands) {
    NN_RET_CHECK(std::none_of(operands.begin(), operands.end(), &tensorOperandHasUnspecifiedRank))
            << "At least one Operand has unspecified rank";
    return {};
}

inline bool subgraphOutputOperandHasDynamicShape(const Operand& operand) {
    return operand.lifetime == Operand::LifeTime::SUBGRAPH_OUTPUT &&
           std::any_of(operand.dimensions.begin(), operand.dimensions.end(),
                       [](const uint32_t& dim) { return dim == 0; });
}

inline Result<void> checkNoSubgraphOutputOperandsHaveDynamicShape(
        const std::vector<Operand>& operands) {
    NN_RET_CHECK(
            std::none_of(operands.begin(), operands.end(), &subgraphOutputOperandHasDynamicShape))
            << "At least one subgraph output Operand has dynamic shape";
    return {};
}

inline bool isOperandConstant(const Operand& operand) {
    return operand.lifetime == Operand::LifeTime::CONSTANT_COPY ||
           operand.lifetime == Operand::LifeTime::CONSTANT_REFERENCE;
}

inline tflite::Padding getTFLitePadding(int32_t paddingType) {
    switch (paddingType) {
        case ANEURALNETWORKS_PADDING_VALID:  // VALID
        case 0:
            return tflite::Padding::Padding_VALID;
        case ANEURALNETWORKS_PADDING_SAME:  // SAME
            return tflite::Padding::Padding_SAME;
        default:
            LOG(FATAL) << "Unsupported NNAPI NDK padding type: " << paddingType;
            return {};
    }
}

// Replace all 0 dimensions to -1 since TFLite only supports -1 as an unknown dimension
inline void replaceZeroDimensions(std::vector<int32_t>* dims) {
    std::replace(dims->begin(), dims->end(), 0, -1);
}

}  // namespace nn
}  // namespace android

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_FLATBUFFER_MODEL_BUILDER_UTILS_H
