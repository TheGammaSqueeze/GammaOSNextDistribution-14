/*
 * Copyright (C) 2020 The Android Open Source Project
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

#define LOG_TAG "SampleDriverFloatXNNPACK"

#include <CpuExecutor.h>
#include <HalInterfaces.h>
#include <Utils.h>
#include <ValidateHal.h>
#include <android-base/logging.h>
#include <hidl/LegacySupport.h>
#include <hwbinder/IPCThreadState.h>
#include <xnnpack.h>

#include <algorithm>
#include <cstdint>
#include <limits>
#include <memory>
#include <string>
#include <thread>
#include <tuple>
#include <unordered_set>
#include <utility>
#include <vector>

#include "SampleDriverPartial.h"
#include "SampleDriverUtils.h"

namespace android {
namespace nn {
namespace sample_driver {

namespace {

#define NN_DRIVER_RETURN_IF_ERROR(expr)              \
    do {                                             \
        V1_3::ErrorStatus _errorCode = (expr);       \
        if (_errorCode != V1_3::ErrorStatus::NONE) { \
            return _errorCode;                       \
        }                                            \
    } while (0)

const size_t kNumOfWorkerThreads = 1;
static const V1_2::Timing kNoTiming = {.timeOnDevice = UINT64_MAX, .timeInDriver = UINT64_MAX};

bool isScalarType(OperandType type) {
    switch (type) {
        case OperandType::FLOAT16:
        case OperandType::FLOAT32:
        case OperandType::INT32:
        case OperandType::UINT32:
        case OperandType::BOOL:
            return true;
        default:
            return false;
    }
}

void updateForArguments(const std::vector<uint32_t>& indexes,
                        const hardware::hidl_vec<V1_0::RequestArgument>& arguments,
                        const std::vector<RunTimePoolInfo>& requestPoolInfos,
                        RunTimeOperandInfo* operands) {
    CHECK_EQ(indexes.size(), arguments.size());
    for (size_t i = 0; i < indexes.size(); i++) {
        const uint32_t operandIndex = indexes[i];
        const V1_0::RequestArgument& from = arguments[i];
        RunTimeOperandInfo& to = operands[operandIndex];
        if (from.dimensions.size() > 0) {
            // It's the responsibility of the caller to validate that
            // from.dimensions only modifies the dimensions that were
            // unspecified in the model.  That's the case in SampleDriver.cpp
            // with the call to validateRequest().
            // TODO make sure that's the case for the default CPU path.
            to.dimensions = from.dimensions;
        }
        if (from.hasNoValue) {
            to.lifetime = Operand::LifeTime::NO_VALUE;
            CHECK(to.buffer == nullptr);
            to.length = 0;
        } else {
            auto poolIndex = from.location.poolIndex;
            CHECK_LT(poolIndex, requestPoolInfos.size());
            auto& r = requestPoolInfos[poolIndex];
            to.buffer = r.getBuffer() + from.location.offset;
            if (from.location.offset == 0 && from.location.length == 0) {
                // Use the entire memory region.
                to.length = r.getSize();
            } else {
                to.length = from.location.length;
            }
        }
    }
}

std::vector<RunTimeOperandInfo> initializeRunTimeInfo(
        const V1_3::Subgraph& subgraph, const std::vector<RunTimePoolInfo>& modelPoolInfos,
        const hardware::hidl_vec<uint8_t>* mModelOperandValues) {
    const size_t count = subgraph.operands.size();
    std::vector<RunTimeOperandInfo> operands(count);
    for (size_t i = 0; i < count; i++) {
        const V1_3::Operand& from = subgraph.operands[i];
        RunTimeOperandInfo& to = operands[i];
        to.type = uncheckedConvert(from.type);
        to.dimensions = from.dimensions;
        to.scale = from.scale;
        to.zeroPoint = from.zeroPoint;
        to.length = from.location.length;
        to.lifetime = uncheckedConvert(from.lifetime);
        to.extraParams = uncheckedConvert(from.extraParams);
        switch (from.lifetime) {
            case V1_3::OperandLifeTime::TEMPORARY_VARIABLE:
                to.buffer = nullptr;
                to.numberOfUsesLeft = from.numberOfConsumers;
                break;
            case V1_3::OperandLifeTime::CONSTANT_COPY:
                to.buffer = const_cast<uint8_t*>(&(*mModelOperandValues)[from.location.offset]);
                to.numberOfUsesLeft = 0;
                break;
            case V1_3::OperandLifeTime::CONSTANT_REFERENCE: {
                auto poolIndex = from.location.poolIndex;
                CHECK_LT(poolIndex, modelPoolInfos.size());
                auto& r = modelPoolInfos[poolIndex];
                to.buffer = r.getBuffer() + from.location.offset;
                to.numberOfUsesLeft = 0;
                break;
            }
            case V1_3::OperandLifeTime::SUBGRAPH:
            case V1_3::OperandLifeTime::SUBGRAPH_INPUT:
            case V1_3::OperandLifeTime::SUBGRAPH_OUTPUT:
            case V1_3::OperandLifeTime::NO_VALUE:
                to.buffer = nullptr;
                to.numberOfUsesLeft = 0;
                break;
        }
    }
    return operands;
}

}  // namespace

class Subgraph {
   public:
    static Subgraph* Create(const hardware::hidl_vec<V1_3::Operation>& operations,
                            std::vector<RunTimeOperandInfo>& operands,
                            const std::vector<uint32_t>& inputIndexes,
                            const std::vector<uint32_t>& outputIndexes, pthreadpool_t threadpool,
                            bool useStaticBuffer = false) {
        // Convert subgraph inputs and outputs to hash sets for faster lookup.
        const std::unordered_set<uint32_t> inputs(inputIndexes.begin(), inputIndexes.end());
        const std::unordered_set<uint32_t> outputs(outputIndexes.begin(), outputIndexes.end());
        std::unordered_set<uint32_t> externals(outputs);

        xnn_subgraph_t subgraphPtr = nullptr;
        xnn_status status = xnn_create_subgraph(
                /*external_value_ids=*/operands.size(), /*flags=*/0, &subgraphPtr);
        if (status != xnn_status_success) {
            LOG(ERROR) << "XNNPACK xnn_create_subgraph FAILED";
            return nullptr;
        }

        // Smart pointer to automatically release subgraph on exit.
        std::unique_ptr<xnn_subgraph, decltype(&xnn_delete_subgraph)> subgraph(
                subgraphPtr, &xnn_delete_subgraph);

        // Detect which tensors are used as inputs or outputs of any subgraph nodes.
        // -1 denotes tensor not used in the subgraph.
        std::vector<int> tensors(operands.size(), -1);

        for (const auto& operation : operations) {
            const std::vector<uint32_t>& ins = operation.inputs;
            const std::vector<uint32_t>& outs = operation.outputs;
            switch (operation.type) {
                case V1_3::OperationType::MEAN:
                case V1_3::OperationType::PAD:
                case V1_3::OperationType::RESHAPE:
                case V1_3::OperationType::RESIZE_BILINEAR:
                    // Ignore the second input (axes, static padding, or new shape),
                    // because it is represented as parameters of the XNNPACK operator
                    // rather than extra input.
                    {
                        const int t = ins[0];
                        tensors[t] = t;
                    }
                    break;
                default:
                    // All other operators: process all inputs
                    for (size_t k = 0; k < ins.size(); k++) {
                        if (isScalarType(operands[ins[k]].type)) continue;
                        const int t = ins[k];
                        tensors[t] = t;
                    }
            }
            for (size_t k = 0; k < outs.size(); k++) {
                if (isScalarType(operands[outs[k]].type)) continue;
                const int t = outs[k];
                tensors[t] = t;
            }
        }

        // XNNPACK Value IDs for NNAPI Operands
        std::vector<uint32_t> xnnpackTensors(operands.size());
        for (int t : tensors) {
            if (t < 0) continue;
            if (operands[tensors[t]].type != OperandType::TENSOR_FLOAT32) {
                LOG(ERROR) << "XNNPACK only support FLOAT32 tensors";
                return nullptr;
            }

            uint32_t flags = 0;
            const void* data = nullptr;
            if (operands[tensors[t]].lifetime == Operand::LifeTime::CONSTANT_COPY ||
                operands[tensors[t]].lifetime == Operand::LifeTime::CONSTANT_REFERENCE ||
                operands[tensors[t]].lifetime == Operand::LifeTime::POINTER) {
                data = operands[tensors[t]].buffer;
            }
            if (inputs.count(t) != 0) {
                flags |= XNN_VALUE_FLAG_EXTERNAL_INPUT;
                CHECK(data == nullptr);
                VLOG(DRIVER) << "found input tensor, add to external";
                externals.insert(static_cast<uint32_t>(t));
            }
            if (outputs.count(t) != 0) {
                flags |= XNN_VALUE_FLAG_EXTERNAL_OUTPUT;
            }

            std::vector<size_t> dims(operands[tensors[t]].dimensions.size());
            for (size_t i = 0; i < dims.size(); i++) {
                dims[i] = operands[tensors[t]].dimensions[i];
            }

            const xnn_status status = xnn_define_tensor_value(
                    subgraph.get(), xnn_datatype_fp32, dims.size(), dims.data(), data,
                    static_cast<uint32_t>(t), flags, &xnnpackTensors[t]);
            if (status != xnn_status_success) {
                LOG(ERROR) << "XNNPACK xnn_define_tensor_value failed";
                return nullptr;
            }
        }

        // Create XNNPACK nodes for NNAPI Operations
        for (const auto& operation : operations) {
            if (VisitNode(subgraph.get(), operation, operands.data(), xnnpackTensors) !=
                V1_3::ErrorStatus::NONE) {
                LOG(ERROR) << "XNNPACK add op failed";
                return nullptr;
            }
        }

        xnn_runtime_t runtimePtr = nullptr;
        status = xnn_create_runtime_v2(subgraph.get(), threadpool, /*flags=*/0, &runtimePtr);
        if (status != xnn_status_success) {
            LOG(ERROR) << "XNNPACK xnn_create_runtime_v2 FAILED";
            return nullptr;
        }
        return new Subgraph(runtimePtr, std::move(externals), useStaticBuffer);
    }

    V1_3::ErrorStatus Prepare() { return V1_3::ErrorStatus::NONE; }

    V1_3::ErrorStatus Invoke(RunTimeOperandInfo* operands) {
        VLOG(DRIVER) << "Subgraph::Invoke() start";
        if (!mUseStaticBuffer || mFirstRun) {
            VLOG(DRIVER) << "Setup buffer for Subgraph";
            std::vector<xnn_external_value> externalValues;

            for (uint32_t t : mExternals) {
                xnn_external_value value = {.id = 0, .data = nullptr};
                value.id = t;
                value.data = operands[t].buffer;
                externalValues.push_back(value);
            }

            const xnn_status status =
                    xnn_setup_runtime(mRuntime.get(), externalValues.size(), externalValues.data());
            if (status != xnn_status_success) {
                LOG(ERROR) << "XNNPACK xnn_setup_runtime FAILED";
                return V1_3::ErrorStatus::GENERAL_FAILURE;
            }
            mFirstRun = false;
        }
        VLOG(DRIVER) << "Subgraph::Invoke() finished xnn_setup_runtime";
        const xnn_status status = xnn_invoke_runtime(mRuntime.get());
        if (status != xnn_status_success) {
            LOG(ERROR) << "XNNPACK xnn_invoke_runtime FAILED";
            return V1_3::ErrorStatus::GENERAL_FAILURE;
        }

        return V1_3::ErrorStatus::NONE;
    }

    static V1_3::ErrorStatus CalculatePadding(int padding, uint32_t* flags) {
        switch (padding) {
            case ANEURALNETWORKS_PADDING_SAME:
                *flags = XNN_FLAG_TENSORFLOW_SAME_PADDING;
                return V1_3::ErrorStatus::NONE;
            case ANEURALNETWORKS_PADDING_VALID:
                *flags = 0;
                return V1_3::ErrorStatus::NONE;
            default:
                LOG(ERROR) << "invalid padding mode";
                return V1_3::ErrorStatus::INVALID_ARGUMENT;
        }
    }

    static V1_3::ErrorStatus ConvertActivationToOutputRange(int activation, float* outputMin,
                                                            float* outputMax) {
        switch (activation) {
            case ANEURALNETWORKS_FUSED_NONE:
                *outputMin = -std::numeric_limits<float>::infinity();
                *outputMax = +std::numeric_limits<float>::infinity();
                return V1_3::ErrorStatus::NONE;
            case ANEURALNETWORKS_FUSED_RELU:
                *outputMin = 0.0f;
                *outputMax = +std::numeric_limits<float>::infinity();
                return V1_3::ErrorStatus::NONE;
            case ANEURALNETWORKS_FUSED_RELU1:
                *outputMin = -1.0f;
                *outputMax = +1.0f;
                return V1_3::ErrorStatus::NONE;
            case ANEURALNETWORKS_FUSED_RELU6:
                *outputMin = 0.0f;
                *outputMax = 6.0f;
                return V1_3::ErrorStatus::NONE;
            default:
                return V1_3::ErrorStatus::INVALID_ARGUMENT;
        }
    }

    static V1_3::ErrorStatus CheckConvolutionParams(int32_t stride_width, int32_t stride_height,
                                                    int32_t dilation_width_factor,
                                                    int32_t dilation_height_factor) {
        if (stride_width <= 0) {
            return V1_3::ErrorStatus::INVALID_ARGUMENT;
        }
        if (stride_height <= 0) {
            return V1_3::ErrorStatus::INVALID_ARGUMENT;
        }

        if (dilation_width_factor <= 0) {
            return V1_3::ErrorStatus::INVALID_ARGUMENT;
        }
        if (dilation_height_factor <= 0) {
            return V1_3::ErrorStatus::INVALID_ARGUMENT;
        }
        return V1_3::ErrorStatus::NONE;
    }

    static V1_3::ErrorStatus CheckDepthwiseConvolutionParams(
            int32_t stride_width, int32_t stride_height, int32_t dilation_width_factor,
            int32_t dilation_height_factor, int32_t depth_multiplier, uint32_t output_channels) {
        if (stride_width <= 0) {
            return V1_3::ErrorStatus::INVALID_ARGUMENT;
        }
        if (stride_height <= 0) {
            return V1_3::ErrorStatus::INVALID_ARGUMENT;
        }

        if (depth_multiplier <= 0) {
            return V1_3::ErrorStatus::INVALID_ARGUMENT;
        }
        if (output_channels % depth_multiplier != 0) {
            return V1_3::ErrorStatus::INVALID_ARGUMENT;
        }

        if (dilation_width_factor <= 0) {
            return V1_3::ErrorStatus::INVALID_ARGUMENT;
        }
        if (dilation_height_factor <= 0) {
            return V1_3::ErrorStatus::INVALID_ARGUMENT;
        }

        return V1_3::ErrorStatus::NONE;
    }

    static V1_3::ErrorStatus CheckPoolingParams(int32_t stride_width, int32_t stride_height,
                                                int32_t filter_width, int32_t filter_height) {
        if (stride_width <= 0) {
            return V1_3::ErrorStatus::INVALID_ARGUMENT;
        }
        if (stride_height <= 0) {
            return V1_3::ErrorStatus::INVALID_ARGUMENT;
        }

        if (filter_width <= 0) {
            return V1_3::ErrorStatus::INVALID_ARGUMENT;
        }
        if (filter_height <= 0) {
            return V1_3::ErrorStatus::INVALID_ARGUMENT;
        }
        if (filter_width == 1 && filter_height == 1 && std::max(stride_width, stride_height) > 1) {
            return V1_3::ErrorStatus::INVALID_ARGUMENT;
        }
        return V1_3::ErrorStatus::NONE;
    }

    static V1_3::ErrorStatus CheckNumInputsAndOutputs(const V1_3::Operation& operation,
                                                      uint32_t expected_num_inputs,
                                                      uint32_t expected_num_outputs) {
        if (operation.inputs.size() != expected_num_inputs) {
            return V1_3::ErrorStatus::INVALID_ARGUMENT;
        }
        if (operation.outputs.size() != expected_num_outputs) {
            return V1_3::ErrorStatus::INVALID_ARGUMENT;
        }
        return V1_3::ErrorStatus::NONE;
    }

    static V1_3::ErrorStatus CheckTensorType(OperandType tensor_type, OperandType expected_type) {
        if (tensor_type != expected_type) {
            return V1_3::ErrorStatus::INVALID_ARGUMENT;
        }
        return V1_3::ErrorStatus::NONE;
    }

    static V1_3::ErrorStatus CheckTensorFloatType(OperandType tensor_type) {
        if (tensor_type != OperandType::TENSOR_FLOAT32) {
            return V1_3::ErrorStatus::INVALID_ARGUMENT;
        }
        return V1_3::ErrorStatus::NONE;
    }

    static V1_3::ErrorStatus CheckTensorShape(std::vector<uint32_t>& dimensions,
                                              uint32_t min_num_dims, uint32_t max_num_dims) {
        if (min_num_dims == max_num_dims) {
            if (dimensions.size() != min_num_dims) {
                return V1_3::ErrorStatus::INVALID_ARGUMENT;
            }
        } else {
            if (dimensions.size() < min_num_dims || dimensions.size() > max_num_dims) {
                return V1_3::ErrorStatus::INVALID_ARGUMENT;
            }
        }
        for (size_t i = 0; i < dimensions.size(); i++) {
            if (dimensions[i] <= 0) {
                return V1_3::ErrorStatus::INVALID_ARGUMENT;
            }
        }
        return V1_3::ErrorStatus::NONE;
    }

    static V1_3::ErrorStatus CheckTensorShape(std::vector<uint32_t>& dimensions,
                                              int expected_num_dims) {
        return CheckTensorShape(dimensions, expected_num_dims, expected_num_dims);
    }

    static V1_3::ErrorStatus CheckSlopeTensorShape(std::vector<uint32_t>& dimensions) {
        if (dimensions.size() < 1) {
            return V1_3::ErrorStatus::INVALID_ARGUMENT;
        }
        // Validate that all non-channel dimensions (if any) are exactly 1.
        for (size_t i = 0; i < dimensions.size() - 1; i++) {
            if (dimensions[i] != 1) {
                return V1_3::ErrorStatus::INVALID_ARGUMENT;
            }
        }
        return V1_3::ErrorStatus::NONE;
    }

    static V1_3::ErrorStatus CheckAxesTensorShape(std::vector<uint32_t>& dimensions) {
        if (dimensions.size() != 1) {
            return V1_3::ErrorStatus::INVALID_ARGUMENT;
        }
        return V1_3::ErrorStatus::NONE;
    }

    static V1_3::ErrorStatus CheckShapeTensorShape(std::vector<uint32_t>& dimensions) {
        if (dimensions.size() != 1) {
            return V1_3::ErrorStatus::INVALID_ARGUMENT;
        }
        return V1_3::ErrorStatus::NONE;
    }

    static V1_3::ErrorStatus CheckTensorStaticAllocation(Operand::LifeTime lifetime) {
        if (lifetime != Operand::LifeTime::CONSTANT_COPY &&
            lifetime != Operand::LifeTime::CONSTANT_REFERENCE &&
            lifetime != Operand::LifeTime::POINTER) {
            VLOG(DRIVER) << "CheckTensorStaticAllocation: " << toString(convertToV1_3(lifetime));
            return V1_3::ErrorStatus::INVALID_ARGUMENT;
        }
        return V1_3::ErrorStatus::NONE;
    }

    static V1_3::ErrorStatus VisitNode(xnn_subgraph_t subgraph, const V1_3::Operation& operation,
                                       RunTimeOperandInfo* operands,
                                       const std::vector<uint32_t>& xnnpackTensors) {
        switch (operation.type) {
            case V1_3::OperationType::ABS:
                return VisitAbsNode(subgraph, operation, operands, xnnpackTensors);
            case V1_3::OperationType::ADD:
                return VisitAddNode(subgraph, operation, operands, xnnpackTensors);
            case V1_3::OperationType::AVERAGE_POOL_2D:
                return VisitAveragePool2DNode(subgraph, operation, operands, xnnpackTensors);
            case V1_3::OperationType::CONV_2D:
                return VisitConv2DNode(subgraph, operation, operands, xnnpackTensors);
            case V1_3::OperationType::DEPTHWISE_CONV_2D:
                return VisitDepthwiseConv2DNode(subgraph, operation, operands, xnnpackTensors);
            case V1_3::OperationType::DIV:
                return VisitDivNode(subgraph, operation, operands, xnnpackTensors);
            case V1_3::OperationType::FLOOR:
                return VisitFloorNode(subgraph, operation, operands, xnnpackTensors);
            case V1_3::OperationType::FULLY_CONNECTED:
                return VisitFullyConnectedNode(subgraph, operation, operands, xnnpackTensors);
            case V1_3::OperationType::HARD_SWISH:
                return VisitHardSwishNode(subgraph, operation, operands, xnnpackTensors);
            case V1_3::OperationType::LOGISTIC:
                return VisitLogisticNode(subgraph, operation, operands, xnnpackTensors);
            case V1_3::OperationType::MAX_POOL_2D:
                return VisitMaxPool2DNode(subgraph, operation, operands, xnnpackTensors);
            case V1_3::OperationType::MAXIMUM:
                return VisitMaximumNode(subgraph, operation, operands, xnnpackTensors);
            case V1_3::OperationType::MEAN:
                return VisitMeanNode(subgraph, operation, operands, xnnpackTensors);
            case V1_3::OperationType::MINIMUM:
                return VisitMinimumNode(subgraph, operation, operands, xnnpackTensors);
            case V1_3::OperationType::MUL:
                return VisitMulNode(subgraph, operation, operands, xnnpackTensors);
            case V1_3::OperationType::NEG:
                return VisitNegNode(subgraph, operation, operands, xnnpackTensors);
            case V1_3::OperationType::PAD:
                return VisitPadNode(subgraph, operation, operands, 0.0f, xnnpackTensors);
            case V1_3::OperationType::PAD_V2:
                return VisitPadV2Node(subgraph, operation, operands, xnnpackTensors);
            case V1_3::OperationType::RESHAPE:
                return VisitReshapeNode(subgraph, operation, operands, xnnpackTensors);
            case V1_3::OperationType::RESIZE_BILINEAR:
                return VisitResizeBilinearNode(subgraph, operation, operands, xnnpackTensors);
            case V1_3::OperationType::PRELU:
                return VisitPreluNode(subgraph, operation, operands, xnnpackTensors);
            case V1_3::OperationType::RELU:
                return VisitReluNode(subgraph, operation, operands, 0.0f,
                                     std::numeric_limits<float>::infinity(), xnnpackTensors);
            case V1_3::OperationType::RELU1:
                return VisitReluNode(subgraph, operation, operands, -1.0f, 1.0f, xnnpackTensors);
            case V1_3::OperationType::RELU6:
                return VisitReluNode(subgraph, operation, operands, 0.0f, 6.0f, xnnpackTensors);
            case V1_3::OperationType::SQRT:
                return VisitSqrtNode(subgraph, operation, operands, xnnpackTensors);
            case V1_3::OperationType::SUB:
                return VisitSubNode(subgraph, operation, operands, xnnpackTensors);
            case V1_3::OperationType::SOFTMAX:
                return VisitSoftmaxNode(subgraph, operation, operands, xnnpackTensors);
            default:
                return V1_3::ErrorStatus::INVALID_ARGUMENT;
        }
    }

    static V1_3::ErrorStatus VisitAbsNode(xnn_subgraph_t subgraph, const V1_3::Operation& operation,
                                          RunTimeOperandInfo* operands,
                                          const std::vector<uint32_t>& xnnpackTensors) {
        const hardware::hidl_vec<uint32_t>& ins = operation.inputs;
        const hardware::hidl_vec<uint32_t>& outs = operation.outputs;
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[ins[0]].type));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[outs[0]].type));

        if (subgraph != nullptr) {
            const xnn_status status =
                    xnn_define_abs(subgraph, /*input_id=*/xnnpackTensors[ins[0]],
                                   /*output_id=*/xnnpackTensors[outs[0]], /*flags=*/0);
            if (status != xnn_status_success) {
                LOG(ERROR) << "XNNPACK xnn_define_abs FAILED";
                return V1_3::ErrorStatus::GENERAL_FAILURE;
            }
        }
        return V1_3::ErrorStatus::NONE;
    }

    static V1_3::ErrorStatus VisitAddNode(xnn_subgraph_t subgraph, const V1_3::Operation& operation,
                                          RunTimeOperandInfo* operands,
                                          const std::vector<uint32_t>& xnnpackTensors) {
        const hardware::hidl_vec<uint32_t>& ins = operation.inputs;
        const hardware::hidl_vec<uint32_t>& outs = operation.outputs;
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[ins[0]].type));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[ins[1]].type));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorStaticAllocation(operands[ins[2]].lifetime));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[outs[0]].type));

        float outputMin = -std::numeric_limits<float>::infinity();
        float outputMax = +std::numeric_limits<float>::infinity();
        int activation = getScalarData<int32_t>(operands[ins[2]]);
        NN_DRIVER_RETURN_IF_ERROR(
                ConvertActivationToOutputRange(activation, &outputMin, &outputMax));

        if (subgraph != nullptr) {
            const xnn_status status =
                    xnn_define_add2(subgraph, outputMin, outputMax,
                                    /*input1_id=*/xnnpackTensors[ins[0]],
                                    /*input2_id=*/xnnpackTensors[ins[1]],
                                    /*output_id=*/xnnpackTensors[outs[0]], /*flags=*/0);
            if (status != xnn_status_success) {
                LOG(ERROR) << "XNNPACK xnn_define_add2 FAILED";
                return V1_3::ErrorStatus::GENERAL_FAILURE;
            }
        }
        return V1_3::ErrorStatus::NONE;
    }

    static V1_3::ErrorStatus VisitAveragePool2DNode(xnn_subgraph_t subgraph,
                                                    const V1_3::Operation& operation,
                                                    RunTimeOperandInfo* operands,
                                                    const std::vector<uint32_t>& xnnpackTensors) {
        const hardware::hidl_vec<uint32_t>& ins = operation.inputs;
        const hardware::hidl_vec<uint32_t>& outs = operation.outputs;
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[ins[0]].type));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[outs[0]].type));
        // Make sure all scalar params are constant.
        for (uint32_t i = 1; i < ins.size(); i++) {
            NN_DRIVER_RETURN_IF_ERROR(CheckTensorStaticAllocation(operands[ins[i]].lifetime));
        }

        bool use_nchw = false;
        if (ins.size() == 8) {
            use_nchw = getScalarData<bool>(operands[ins[7]]);
        }
        if (ins.size() == 11) {
            use_nchw = getScalarData<bool>(operands[ins[10]]);
        }
        if (use_nchw) {
            VLOG(DRIVER) << "XNNPACK VisitAveragePool2DNode FAILED: only NHWC layout is supported";
            return V1_3::ErrorStatus::INVALID_ARGUMENT;
        }

        int32_t stride_width, stride_height, filter_width, filter_height, activation;
        uint32_t input_padding_top = 0;
        uint32_t input_padding_right = 0;
        uint32_t input_padding_bottom = 0;
        uint32_t input_padding_left = 0;
        uint32_t flags = 0;
        if (ins.size() >= 10) {
            // Explicit padding
            input_padding_left = static_cast<uint32_t>(getScalarData<int32_t>(operands[ins[1]]));
            input_padding_right = static_cast<uint32_t>(getScalarData<int32_t>(operands[ins[2]]));
            input_padding_top = static_cast<uint32_t>(getScalarData<int32_t>(operands[ins[3]]));
            input_padding_bottom = static_cast<uint32_t>(getScalarData<int32_t>(operands[ins[4]]));
            stride_width = getScalarData<int32_t>(operands[ins[5]]);
            stride_height = getScalarData<int32_t>(operands[ins[6]]);
            filter_width = getScalarData<int32_t>(operands[ins[7]]);
            filter_height = getScalarData<int32_t>(operands[ins[8]]);
            activation = getScalarData<int32_t>(operands[ins[9]]);
        } else {
            // Implicit padding
            int padding_implicit = getScalarData<int32_t>(operands[ins[1]]);
            NN_DRIVER_RETURN_IF_ERROR(CalculatePadding(padding_implicit, &flags));
            stride_width = getScalarData<int32_t>(operands[ins[2]]);
            stride_height = getScalarData<int32_t>(operands[ins[3]]);
            filter_width = getScalarData<int32_t>(operands[ins[4]]);
            filter_height = getScalarData<int32_t>(operands[ins[5]]);
            activation = getScalarData<int32_t>(operands[ins[6]]);
        }
        NN_DRIVER_RETURN_IF_ERROR(
                CheckPoolingParams(stride_width, stride_height, filter_width, filter_height));

        float outputMin = -std::numeric_limits<float>::infinity();
        float outputMax = +std::numeric_limits<float>::infinity();
        NN_DRIVER_RETURN_IF_ERROR(
                ConvertActivationToOutputRange(activation, &outputMin, &outputMax));

        if (subgraph != nullptr) {
            xnn_status status = xnn_status_success;
            if (filter_width == 1 && filter_height == 1) {
                status = xnn_define_clamp(subgraph, outputMin, outputMax,
                                          /*input_id=*/xnnpackTensors[ins[0]],
                                          /*output_id=*/xnnpackTensors[outs[0]], /*flags=*/0);
            } else {
                status = xnn_define_average_pooling_2d(
                        subgraph, input_padding_top, input_padding_right, input_padding_bottom,
                        input_padding_left, static_cast<uint32_t>(filter_height),
                        static_cast<uint32_t>(filter_width), static_cast<uint32_t>(stride_height),
                        static_cast<uint32_t>(stride_width), outputMin, outputMax,
                        /*input_id=*/xnnpackTensors[ins[0]],
                        /*output_id=*/xnnpackTensors[outs[0]], flags);
            }
            if (status != xnn_status_success) {
                LOG(ERROR) << "XNNPACK xnn_define_average_pooling_2d FAILED";
                return V1_3::ErrorStatus::GENERAL_FAILURE;
            }
        }
        return V1_3::ErrorStatus::NONE;
    }

    static V1_3::ErrorStatus VisitConv2DNode(xnn_subgraph_t subgraph,
                                             const V1_3::Operation& operation,
                                             RunTimeOperandInfo* operands,
                                             const std::vector<uint32_t>& xnnpackTensors) {
        const hardware::hidl_vec<uint32_t>& ins = operation.inputs;
        const hardware::hidl_vec<uint32_t>& outs = operation.outputs;
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[ins[0]].type));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[ins[1]].type));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorStaticAllocation(operands[ins[1]].lifetime));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[ins[2]].type));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorStaticAllocation(operands[ins[2]].lifetime));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[outs[0]].type));
        // Make sure all scalar params are constant.
        for (uint32_t i = 3; i < ins.size(); i++) {
            NN_DRIVER_RETURN_IF_ERROR(CheckTensorStaticAllocation(operands[ins[i]].lifetime));
        }

        bool use_nchw = false;
        if (ins.size() >= 8 && operands[ins[7]].type == OperandType::BOOL) {
            use_nchw = getScalarData<bool>(operands[ins[7]]);
        }
        if (ins.size() >= 11) {
            use_nchw = getScalarData<bool>(operands[ins[10]]);
        }
        if (use_nchw) {
            VLOG(DRIVER) << "XNNPACK VisitConv2DNode FAILED: only NHWC layout is supported";
            return V1_3::ErrorStatus::INVALID_ARGUMENT;
        }

        int32_t stride_width, stride_height, activation;
        int32_t dilation_width_factor = 1;
        int32_t dilation_height_factor = 1;
        uint32_t input_padding_top = 0;
        uint32_t input_padding_right = 0;
        uint32_t input_padding_bottom = 0;
        uint32_t input_padding_left = 0;
        uint32_t flags = 0;
        if (ins.size() >= 10 && operands[ins[7]].type != OperandType::BOOL) {
            // Explicit padding
            input_padding_left = static_cast<uint32_t>(getScalarData<int32_t>(operands[ins[3]]));
            input_padding_right = static_cast<uint32_t>(getScalarData<int32_t>(operands[ins[4]]));
            input_padding_top = static_cast<uint32_t>(getScalarData<int32_t>(operands[ins[5]]));
            input_padding_bottom = static_cast<uint32_t>(getScalarData<int32_t>(operands[ins[6]]));
            stride_width = getScalarData<int32_t>(operands[ins[7]]);
            stride_height = getScalarData<int32_t>(operands[ins[8]]);
            activation = getScalarData<int32_t>(operands[ins[9]]);
            if (ins.size() == 13) {
                dilation_width_factor = getScalarData<int32_t>(operands[ins[11]]);
                dilation_height_factor = getScalarData<int32_t>(operands[ins[12]]);
            }
        } else {
            // Implicit padding
            int padding_implicit = getScalarData<int32_t>(operands[ins[3]]);
            NN_DRIVER_RETURN_IF_ERROR(CalculatePadding(padding_implicit, &flags));
            stride_width = getScalarData<int32_t>(operands[ins[4]]);
            stride_height = getScalarData<int32_t>(operands[ins[5]]);
            activation = getScalarData<int32_t>(operands[ins[6]]);
            if (ins.size() == 10) {
                dilation_width_factor = getScalarData<int32_t>(operands[ins[8]]);
                dilation_height_factor = getScalarData<int32_t>(operands[ins[9]]);
            }
        }
        NN_DRIVER_RETURN_IF_ERROR(CheckConvolutionParams(
                stride_width, stride_height, dilation_width_factor, dilation_height_factor));

        float outputMin = -std::numeric_limits<float>::infinity();
        float outputMax = +std::numeric_limits<float>::infinity();
        NN_DRIVER_RETURN_IF_ERROR(
                ConvertActivationToOutputRange(activation, &outputMin, &outputMax));

        const RunTimeOperandInfo& filter = operands[ins[1]];
        const uint32_t output_channels = filter.dimensions[0];
        const uint32_t kernel_height = filter.dimensions[1];
        const uint32_t kernel_width = filter.dimensions[2];
        const uint32_t input_channels = filter.dimensions[3];

        if (subgraph != nullptr) {
            const xnn_status status = xnn_define_convolution_2d(
                    subgraph, input_padding_top, input_padding_right, input_padding_bottom,
                    input_padding_left, static_cast<uint32_t>(kernel_height),
                    static_cast<uint32_t>(kernel_width), static_cast<uint32_t>(stride_height),
                    static_cast<uint32_t>(stride_width),
                    static_cast<uint32_t>(dilation_height_factor),
                    static_cast<uint32_t>(dilation_width_factor),
                    /*groups=*/1, static_cast<size_t>(input_channels),
                    static_cast<size_t>(output_channels), outputMin, outputMax,
                    /*input_id=*/xnnpackTensors[ins[0]],
                    /*filter_id=*/xnnpackTensors[ins[1]],
                    /*bias_id=*/xnnpackTensors[ins[2]],
                    /*output_id=*/xnnpackTensors[outs[0]], flags);
            if (status != xnn_status_success) {
                LOG(ERROR) << "XNNPACK xnn_define_convolution_2d FAILED";
                return V1_3::ErrorStatus::GENERAL_FAILURE;
            }
        }

        return V1_3::ErrorStatus::NONE;
    }

    static V1_3::ErrorStatus VisitDepthwiseConv2DNode(xnn_subgraph_t subgraph,
                                                      const V1_3::Operation& operation,
                                                      RunTimeOperandInfo* operands,
                                                      const std::vector<uint32_t>& xnnpackTensors) {
        const hardware::hidl_vec<uint32_t>& ins = operation.inputs;
        const hardware::hidl_vec<uint32_t>& outs = operation.outputs;
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[ins[0]].type));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[ins[1]].type));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorStaticAllocation(operands[ins[1]].lifetime));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[ins[2]].type));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorStaticAllocation(operands[ins[2]].lifetime));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[outs[0]].type));
        // Make sure all scalar params are constant.
        for (uint32_t i = 3; i < ins.size(); i++) {
            NN_DRIVER_RETURN_IF_ERROR(CheckTensorStaticAllocation(operands[ins[i]].lifetime));
        }

        bool use_nchw = false;
        if (ins.size() >= 9 && operands[ins[8]].type == OperandType::BOOL) {
            use_nchw = getScalarData<bool>(operands[ins[8]]);
        }
        if (ins.size() >= 12) {
            use_nchw = getScalarData<bool>(operands[ins[11]]);
        }
        if (use_nchw) {
            VLOG(DRIVER)
                    << "XNNPACK VisitDepthwiseConv2DNode FAILED: only NHWC layout is supported";
            return V1_3::ErrorStatus::INVALID_ARGUMENT;
        }

        int32_t stride_width, stride_height, depth_multiplier, activation;
        int32_t dilation_width_factor = 1;
        int32_t dilation_height_factor = 1;
        uint32_t input_padding_top = 0;
        uint32_t input_padding_right = 0;
        uint32_t input_padding_bottom = 0;
        uint32_t input_padding_left = 0;
        uint32_t flags = 0;
        if (ins.size() >= 11 && operands[ins[8]].type != OperandType::BOOL) {
            // Explicit padding
            input_padding_left = static_cast<uint32_t>(getScalarData<int32_t>(operands[ins[3]]));
            input_padding_right = static_cast<uint32_t>(getScalarData<int32_t>(operands[ins[4]]));
            input_padding_top = static_cast<uint32_t>(getScalarData<int32_t>(operands[ins[5]]));
            input_padding_bottom = static_cast<uint32_t>(getScalarData<int32_t>(operands[ins[6]]));
            stride_width = getScalarData<int32_t>(operands[ins[7]]);
            stride_height = getScalarData<int32_t>(operands[ins[8]]);
            depth_multiplier = getScalarData<int32_t>(operands[ins[9]]);
            activation = getScalarData<int32_t>(operands[ins[10]]);
            if (ins.size() == 14) {
                dilation_width_factor = getScalarData<int32_t>(operands[ins[12]]);
                dilation_height_factor = getScalarData<int32_t>(operands[ins[13]]);
            }
        } else {
            // Implicit padding
            int padding_implicit = getScalarData<int32_t>(operands[ins[3]]);
            NN_DRIVER_RETURN_IF_ERROR(CalculatePadding(padding_implicit, &flags));
            stride_width = getScalarData<int32_t>(operands[ins[4]]);
            stride_height = getScalarData<int32_t>(operands[ins[5]]);
            depth_multiplier = getScalarData<int32_t>(operands[ins[6]]);
            activation = getScalarData<int32_t>(operands[ins[7]]);
            if (ins.size() == 11) {
                dilation_width_factor = getScalarData<int32_t>(operands[ins[9]]);
                dilation_height_factor = getScalarData<int32_t>(operands[ins[10]]);
            }
        }
        float outputMin = -std::numeric_limits<float>::infinity();
        float outputMax = +std::numeric_limits<float>::infinity();
        NN_DRIVER_RETURN_IF_ERROR(
                ConvertActivationToOutputRange(activation, &outputMin, &outputMax));

        const RunTimeOperandInfo& filter = operands[ins[1]];
        const uint32_t output_channels = filter.dimensions[3];
        const uint32_t kernel_height = filter.dimensions[1];
        const uint32_t kernel_width = filter.dimensions[2];
        NN_DRIVER_RETURN_IF_ERROR(CheckDepthwiseConvolutionParams(
                stride_width, stride_height, dilation_width_factor, dilation_height_factor,
                depth_multiplier, output_channels));

        if (subgraph != nullptr) {
            const xnn_status status = xnn_define_depthwise_convolution_2d(
                    subgraph, input_padding_top, input_padding_right, input_padding_bottom,
                    input_padding_left, static_cast<uint32_t>(kernel_height),
                    static_cast<uint32_t>(kernel_width), static_cast<uint32_t>(stride_height),
                    static_cast<uint32_t>(stride_width),
                    static_cast<uint32_t>(dilation_height_factor),
                    static_cast<uint32_t>(dilation_width_factor),
                    static_cast<uint32_t>(depth_multiplier),
                    /*input_channels=*/
                    static_cast<uint32_t>(output_channels / depth_multiplier), outputMin, outputMax,
                    /*input_id=*/xnnpackTensors[ins[0]],
                    /*filter_id=*/xnnpackTensors[ins[1]],
                    /*bias_id=*/xnnpackTensors[ins[2]],
                    /*output_id=*/xnnpackTensors[outs[0]], flags);
            if (status != xnn_status_success) {
                LOG(ERROR) << "XNNPACK xnn_define_depthwise_convolution_2d FAILED";
                return V1_3::ErrorStatus::GENERAL_FAILURE;
            }
        }
        return V1_3::ErrorStatus::NONE;
    }

    static V1_3::ErrorStatus VisitDivNode(xnn_subgraph_t subgraph, const V1_3::Operation& operation,
                                          RunTimeOperandInfo* operands,
                                          const std::vector<uint32_t>& xnnpackTensors) {
        const hardware::hidl_vec<uint32_t>& ins = operation.inputs;
        const hardware::hidl_vec<uint32_t>& outs = operation.outputs;
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[ins[0]].type));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[ins[1]].type));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorStaticAllocation(operands[ins[2]].lifetime));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[outs[0]].type));

        float outputMin = -std::numeric_limits<float>::infinity();
        float outputMax = +std::numeric_limits<float>::infinity();
        int activation = getScalarData<int32_t>(operands[ins[2]]);
        NN_DRIVER_RETURN_IF_ERROR(
                ConvertActivationToOutputRange(activation, &outputMin, &outputMax));

        if (subgraph != nullptr) {
            const xnn_status status =
                    xnn_define_divide(subgraph, outputMin, outputMax,
                                      /*input1_id=*/xnnpackTensors[ins[0]],
                                      /*input2_id=*/xnnpackTensors[ins[1]],
                                      /*output_id=*/xnnpackTensors[outs[0]], /*flags=*/0);
            if (status != xnn_status_success) {
                LOG(ERROR) << "XNNPACK xnn_define_divide FAILED";
                return V1_3::ErrorStatus::GENERAL_FAILURE;
            }
        }
        return V1_3::ErrorStatus::NONE;
    }

    static V1_3::ErrorStatus VisitFullyConnectedNode(xnn_subgraph_t subgraph,
                                                     const V1_3::Operation& operation,
                                                     RunTimeOperandInfo* operands,
                                                     const std::vector<uint32_t>& xnnpackTensors) {
        const hardware::hidl_vec<uint32_t>& ins = operation.inputs;
        const hardware::hidl_vec<uint32_t>& outs = operation.outputs;
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[ins[0]].type));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[ins[1]].type));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorStaticAllocation(operands[ins[1]].lifetime));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[ins[2]].type));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorStaticAllocation(operands[ins[2]].lifetime));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorStaticAllocation(operands[ins[3]].lifetime));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[outs[0]].type));

        float outputMin = -std::numeric_limits<float>::infinity();
        float outputMax = +std::numeric_limits<float>::infinity();
        int activation = getScalarData<int32_t>(operands[ins[3]]);
        NN_DRIVER_RETURN_IF_ERROR(
                ConvertActivationToOutputRange(activation, &outputMin, &outputMax));

        if (subgraph != nullptr) {
            const xnn_status status =
                    xnn_define_fully_connected(subgraph, outputMin, outputMax,
                                               /*input_id=*/xnnpackTensors[ins[0]],
                                               /*filter_id=*/xnnpackTensors[ins[1]],
                                               /*bias_id=*/xnnpackTensors[ins[2]],
                                               /*output_id=*/xnnpackTensors[outs[0]],
                                               /*flags=*/XNN_FLAG_TENSORFLOW_RESHAPE_2D);
            if (status != xnn_status_success) {
                LOG(ERROR) << "XNNPACK xnn_define_fully_connected FAILED";
                return V1_3::ErrorStatus::GENERAL_FAILURE;
            }
        }
        return V1_3::ErrorStatus::NONE;
    }

    static V1_3::ErrorStatus VisitFloorNode(xnn_subgraph_t subgraph,
                                            const V1_3::Operation& operation,
                                            RunTimeOperandInfo* operands,
                                            const std::vector<uint32_t>& xnnpackTensors) {
        const hardware::hidl_vec<uint32_t>& ins = operation.inputs;
        const hardware::hidl_vec<uint32_t>& outs = operation.outputs;
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[ins[0]].type));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[outs[0]].type));

        if (subgraph != nullptr) {
            const xnn_status status =
                    xnn_define_floor(subgraph,
                                     /*input1_id=*/xnnpackTensors[ins[0]],
                                     /*output_id=*/xnnpackTensors[outs[0]], /*flags=*/0);
            if (status != xnn_status_success) {
                LOG(ERROR) << "XNNPACK xnn_define_floor FAILED";
                return V1_3::ErrorStatus::GENERAL_FAILURE;
            }
        }
        return V1_3::ErrorStatus::NONE;
    }

    static V1_3::ErrorStatus VisitHardSwishNode(xnn_subgraph_t subgraph,
                                                const V1_3::Operation& operation,
                                                RunTimeOperandInfo* operands,
                                                const std::vector<uint32_t>& xnnpackTensors) {
        const hardware::hidl_vec<uint32_t>& ins = operation.inputs;
        const hardware::hidl_vec<uint32_t>& outs = operation.outputs;
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[ins[0]].type));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[outs[0]].type));

        if (subgraph != nullptr) {
            const xnn_status status =
                    xnn_define_hardswish(subgraph, /*input_id=*/xnnpackTensors[ins[0]],
                                         /*output_id=*/xnnpackTensors[outs[0]], /*flags=*/0);
            if (status != xnn_status_success) {
                LOG(ERROR) << "XNNPACK xnn_define_hardswish FAILED";
                return V1_3::ErrorStatus::GENERAL_FAILURE;
            }
        }
        return V1_3::ErrorStatus::NONE;
    }

    static V1_3::ErrorStatus VisitLogisticNode(xnn_subgraph_t subgraph,
                                               const V1_3::Operation& operation,
                                               RunTimeOperandInfo* operands,
                                               const std::vector<uint32_t>& xnnpackTensors) {
        const hardware::hidl_vec<uint32_t>& ins = operation.inputs;
        const hardware::hidl_vec<uint32_t>& outs = operation.outputs;
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[ins[0]].type));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[outs[0]].type));

        if (subgraph != nullptr) {
            const xnn_status status =
                    xnn_define_sigmoid(subgraph, /*input_id=*/xnnpackTensors[ins[0]],
                                       /*output_id=*/xnnpackTensors[outs[0]], /*flags=*/0);
            if (status != xnn_status_success) {
                LOG(ERROR) << "XNNPACK xnn_define_sigmoid FAILED";
                return V1_3::ErrorStatus::GENERAL_FAILURE;
            }
        }
        return V1_3::ErrorStatus::NONE;
    }

    static V1_3::ErrorStatus VisitMaxPool2DNode(xnn_subgraph_t subgraph,
                                                const V1_3::Operation& operation,
                                                RunTimeOperandInfo* operands,
                                                const std::vector<uint32_t>& xnnpackTensors) {
        const hardware::hidl_vec<uint32_t>& ins = operation.inputs;
        const hardware::hidl_vec<uint32_t>& outs = operation.outputs;
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[ins[0]].type));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[outs[0]].type));
        // Make sure all scalar params are constant.
        for (uint32_t i = 1; i < ins.size(); i++) {
            NN_DRIVER_RETURN_IF_ERROR(CheckTensorStaticAllocation(operands[ins[i]].lifetime));
        }

        bool use_nchw = false;
        if (ins.size() == 8) {
            use_nchw = getScalarData<bool>(operands[ins[7]]);
        }
        if (ins.size() == 11) {
            use_nchw = getScalarData<bool>(operands[ins[10]]);
        }
        if (use_nchw) {
            VLOG(DRIVER) << "XNNPACK VisitMaxPool2DNode FAILED: only NHWC layout is supported";
            return V1_3::ErrorStatus::INVALID_ARGUMENT;
        }

        int32_t stride_width, stride_height, filter_width, filter_height, activation;
        uint32_t input_padding_top = 0;
        uint32_t input_padding_right = 0;
        uint32_t input_padding_bottom = 0;
        uint32_t input_padding_left = 0;
        uint32_t flags = 0;
        if (ins.size() >= 10) {
            // Explicit padding
            input_padding_left = static_cast<uint32_t>(getScalarData<int32_t>(operands[ins[1]]));
            input_padding_right = static_cast<uint32_t>(getScalarData<int32_t>(operands[ins[2]]));
            input_padding_top = static_cast<uint32_t>(getScalarData<int32_t>(operands[ins[3]]));
            input_padding_bottom = static_cast<uint32_t>(getScalarData<int32_t>(operands[ins[4]]));
            stride_width = getScalarData<int32_t>(operands[ins[5]]);
            stride_height = getScalarData<int32_t>(operands[ins[6]]);
            filter_width = getScalarData<int32_t>(operands[ins[7]]);
            filter_height = getScalarData<int32_t>(operands[ins[8]]);
            activation = getScalarData<int32_t>(operands[ins[9]]);
        } else {
            // Implicit padding
            int padding_implicit = getScalarData<int32_t>(operands[ins[1]]);
            NN_DRIVER_RETURN_IF_ERROR(CalculatePadding(padding_implicit, &flags));
            stride_width = getScalarData<int32_t>(operands[ins[2]]);
            stride_height = getScalarData<int32_t>(operands[ins[3]]);
            filter_width = getScalarData<int32_t>(operands[ins[4]]);
            filter_height = getScalarData<int32_t>(operands[ins[5]]);
            activation = getScalarData<int32_t>(operands[ins[6]]);
        }
        NN_DRIVER_RETURN_IF_ERROR(
                CheckPoolingParams(stride_width, stride_height, filter_width, filter_height));

        float outputMin = -std::numeric_limits<float>::infinity();
        float outputMax = +std::numeric_limits<float>::infinity();
        NN_DRIVER_RETURN_IF_ERROR(
                ConvertActivationToOutputRange(activation, &outputMin, &outputMax));

        if (subgraph != nullptr) {
            xnn_status status = xnn_status_success;
            if (filter_width == 1 && filter_height == 1) {
                status = xnn_define_clamp(subgraph, outputMin, outputMax,
                                          /*input_id=*/xnnpackTensors[ins[0]],
                                          /*output_id=*/xnnpackTensors[outs[0]], /*flags=*/0);
            } else {
                status = xnn_define_max_pooling_2d(
                        subgraph, input_padding_top, input_padding_right, input_padding_bottom,
                        input_padding_left, static_cast<uint32_t>(filter_height),
                        static_cast<uint32_t>(filter_width), static_cast<uint32_t>(stride_height),
                        static_cast<uint32_t>(stride_width), /*dilation_height=*/1,
                        /*dilation_width=*/1, outputMin, outputMax,
                        /*input_id=*/xnnpackTensors[ins[0]],
                        /*output_id=*/xnnpackTensors[outs[0]], flags);
            }
            if (status != xnn_status_success) {
                LOG(ERROR) << "XNNPACK xnn_define_max_pooling_2d FAILED";
                return V1_3::ErrorStatus::GENERAL_FAILURE;
            }
        }
        return V1_3::ErrorStatus::NONE;
    }

    static V1_3::ErrorStatus VisitMaximumNode(xnn_subgraph_t subgraph,
                                              const V1_3::Operation& operation,
                                              RunTimeOperandInfo* operands,
                                              const std::vector<uint32_t>& xnnpackTensors) {
        const hardware::hidl_vec<uint32_t>& ins = operation.inputs;
        const hardware::hidl_vec<uint32_t>& outs = operation.outputs;
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[ins[0]].type));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[ins[1]].type));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorStaticAllocation(operands[ins[2]].lifetime));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[outs[0]].type));

        float outputMin = -std::numeric_limits<float>::infinity();
        float outputMax = +std::numeric_limits<float>::infinity();
        int activation = getScalarData<int32_t>(operands[ins[2]]);
        NN_DRIVER_RETURN_IF_ERROR(
                ConvertActivationToOutputRange(activation, &outputMin, &outputMax));

        if (subgraph != nullptr) {
            const xnn_status status =
                    xnn_define_maximum2(subgraph,
                                        /*input1_id=*/xnnpackTensors[ins[0]],
                                        /*input2_id=*/xnnpackTensors[ins[1]],
                                        /*output_id=*/xnnpackTensors[outs[0]], /*flags=*/0);
            if (status != xnn_status_success) {
                LOG(ERROR) << "XNNPACK xnn_define_maximum2 FAILED";
                return V1_3::ErrorStatus::GENERAL_FAILURE;
            }
        }
        return V1_3::ErrorStatus::NONE;
    }

    static V1_3::ErrorStatus VisitMeanNode(xnn_subgraph_t subgraph,
                                           const V1_3::Operation& operation,
                                           RunTimeOperandInfo* operands,
                                           const std::vector<uint32_t>& xnnpackTensors) {
        const hardware::hidl_vec<uint32_t>& ins = operation.inputs;
        const hardware::hidl_vec<uint32_t>& outs = operation.outputs;
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[ins[0]].type));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorShape(operands[ins[0]].dimensions, 4));
        NN_DRIVER_RETURN_IF_ERROR(CheckAxesTensorShape(operands[ins[1]].dimensions));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorStaticAllocation(operands[ins[1]].lifetime));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorStaticAllocation(operands[ins[2]].lifetime));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[outs[0]].type));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorShape(operands[outs[0]].dimensions, 4));

        int keep_dims = getScalarData<int32_t>(operands[ins[2]]);
        if (keep_dims <= 0) {
            LOG(ERROR) << "XNNPACK VisitMeanNode FAILED: only support keep_dims";
            return V1_3::ErrorStatus::INVALID_ARGUMENT;
        }
        const int32_t* axes_buffer = reinterpret_cast<const int32_t*>(operands[ins[1]].buffer);
        if (operands[ins[1]].dimensions[0] != 2) {
            LOG(ERROR) << "XNNPACK VisitMeanNode FAILED: unsupported axes";
            return V1_3::ErrorStatus::INVALID_ARGUMENT;
        }
        if (std::min(axes_buffer[0], axes_buffer[1]) != 1 ||
            std::max(axes_buffer[0], axes_buffer[1]) != 2) {
            LOG(ERROR) << "XNNPACK VisitMeanNode FAILED: unsupported axes";
            return V1_3::ErrorStatus::INVALID_ARGUMENT;
        }
        if (subgraph != nullptr) {
            const xnn_status status = xnn_define_global_average_pooling_2d(
                    subgraph,
                    /*outputMin=*/-std::numeric_limits<float>::infinity(),
                    /*outputMax=*/+std::numeric_limits<float>::infinity(),
                    /*input_id=*/xnnpackTensors[ins[0]],
                    /*output_id=*/xnnpackTensors[outs[0]], /*flags=*/0);
            if (status != xnn_status_success) {
                LOG(ERROR) << "XNNPACK xnn_define_global_average_pooling_2d FAILED";
                return V1_3::ErrorStatus::GENERAL_FAILURE;
            }
        }
        return V1_3::ErrorStatus::NONE;
    }

    static V1_3::ErrorStatus VisitMinimumNode(xnn_subgraph_t subgraph,
                                              const V1_3::Operation& operation,
                                              RunTimeOperandInfo* operands,
                                              const std::vector<uint32_t>& xnnpackTensors) {
        const hardware::hidl_vec<uint32_t>& ins = operation.inputs;
        const hardware::hidl_vec<uint32_t>& outs = operation.outputs;
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[ins[0]].type));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[ins[1]].type));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorStaticAllocation(operands[ins[2]].lifetime));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[outs[0]].type));

        float outputMin = -std::numeric_limits<float>::infinity();
        float outputMax = +std::numeric_limits<float>::infinity();
        int activation = getScalarData<int32_t>(operands[ins[2]]);
        NN_DRIVER_RETURN_IF_ERROR(
                ConvertActivationToOutputRange(activation, &outputMin, &outputMax));

        if (subgraph != nullptr) {
            const xnn_status status =
                    xnn_define_minimum2(subgraph,
                                        /*input1_id=*/xnnpackTensors[ins[0]],
                                        /*input2_id=*/xnnpackTensors[ins[1]],
                                        /*output_id=*/xnnpackTensors[outs[0]], /*flags=*/0);
            if (status != xnn_status_success) {
                LOG(ERROR) << "XNNPACK xnn_define_minimum2 FAILED";
                return V1_3::ErrorStatus::GENERAL_FAILURE;
            }
        }
        return V1_3::ErrorStatus::NONE;
    }

    static V1_3::ErrorStatus VisitMulNode(xnn_subgraph_t subgraph, const V1_3::Operation& operation,
                                          RunTimeOperandInfo* operands,
                                          const std::vector<uint32_t>& xnnpackTensors) {
        const hardware::hidl_vec<uint32_t>& ins = operation.inputs;
        const hardware::hidl_vec<uint32_t>& outs = operation.outputs;
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[ins[0]].type));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[ins[1]].type));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorStaticAllocation(operands[ins[2]].lifetime));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[outs[0]].type));

        int activation = getScalarData<int32_t>(operands[ins[2]]);
        float outputMin = -std::numeric_limits<float>::infinity();
        float outputMax = +std::numeric_limits<float>::infinity();
        NN_DRIVER_RETURN_IF_ERROR(
                ConvertActivationToOutputRange(activation, &outputMin, &outputMax));

        if (subgraph != nullptr) {
            const xnn_status status =
                    xnn_define_multiply2(subgraph, outputMin, outputMax,
                                         /*input1_id=*/xnnpackTensors[ins[0]],
                                         /*input2_id=*/xnnpackTensors[ins[1]],
                                         /*output_id=*/xnnpackTensors[outs[0]], /*flags=*/0);
            if (status != xnn_status_success) {
                LOG(ERROR) << "XNNPACK xnn_define_multiply2 FAILED";
                return V1_3::ErrorStatus::GENERAL_FAILURE;
            }
        }
        return V1_3::ErrorStatus::NONE;
    }

    static V1_3::ErrorStatus VisitNegNode(xnn_subgraph_t subgraph, const V1_3::Operation& operation,
                                          RunTimeOperandInfo* operands,
                                          const std::vector<uint32_t>& xnnpackTensors) {
        const hardware::hidl_vec<uint32_t>& ins = operation.inputs;
        const hardware::hidl_vec<uint32_t>& outs = operation.outputs;
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[ins[0]].type));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[outs[0]].type));

        if (subgraph != nullptr) {
            const xnn_status status =
                    xnn_define_negate(subgraph,
                                      /*input1_id=*/xnnpackTensors[ins[0]],
                                      /*output_id=*/xnnpackTensors[outs[0]], /*flags=*/0);
            if (status != xnn_status_success) {
                LOG(ERROR) << "XNNPACK xnn_define_negate FAILED";
                return V1_3::ErrorStatus::GENERAL_FAILURE;
            }
        }
        return V1_3::ErrorStatus::NONE;
    }

    static V1_3::ErrorStatus VisitPreluNode(xnn_subgraph_t subgraph,
                                            const V1_3::Operation& operation,
                                            RunTimeOperandInfo* operands,
                                            const std::vector<uint32_t>& xnnpackTensors) {
        const hardware::hidl_vec<uint32_t>& ins = operation.inputs;
        const hardware::hidl_vec<uint32_t>& outs = operation.outputs;
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[ins[0]].type));
        NN_DRIVER_RETURN_IF_ERROR(
                CheckTensorShape(operands[ins[0]].dimensions, 1, XNN_MAX_TENSOR_DIMS));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[ins[1]].type));
        NN_DRIVER_RETURN_IF_ERROR(CheckSlopeTensorShape(operands[ins[1]].dimensions));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[outs[0]].type));
        NN_DRIVER_RETURN_IF_ERROR(
                CheckTensorShape(operands[outs[0]].dimensions, 1, XNN_MAX_TENSOR_DIMS));

        if (subgraph != nullptr) {
            const xnn_status status =
                    xnn_define_prelu(subgraph, /*input_id=*/xnnpackTensors[ins[0]],
                                     /*slope_id=*/xnnpackTensors[ins[1]],
                                     /*output_id=*/xnnpackTensors[outs[0]], /*flags=*/0);
            if (status != xnn_status_success) {
                LOG(ERROR) << "XNNPACK xnn_define_prelu FAILED";
                return V1_3::ErrorStatus::GENERAL_FAILURE;
            }
        }
        return V1_3::ErrorStatus::NONE;
    }

    static V1_3::ErrorStatus VisitPadNode(xnn_subgraph_t subgraph, const V1_3::Operation& operation,
                                          RunTimeOperandInfo* operands, float padding_value,
                                          const std::vector<uint32_t>& xnnpackTensors) {
        const hardware::hidl_vec<uint32_t>& ins = operation.inputs;
        const hardware::hidl_vec<uint32_t>& outs = operation.outputs;
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[ins[0]].type));
        NN_DRIVER_RETURN_IF_ERROR(
                CheckTensorShape(operands[ins[0]].dimensions, 1, XNN_MAX_TENSOR_DIMS));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorStaticAllocation(operands[ins[1]].lifetime));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[outs[0]].type));
        NN_DRIVER_RETURN_IF_ERROR(
                CheckTensorShape(operands[outs[0]].dimensions, 1, XNN_MAX_TENSOR_DIMS));

        const int32_t* paddings_data = reinterpret_cast<const int32_t*>(operands[ins[1]].buffer);
        for (size_t i = 0; i < operands[ins[1]].dimensions.size() * 2; i++) {
            if (paddings_data[i] < 0) return V1_3::ErrorStatus::INVALID_ARGUMENT;
        }
        if (subgraph != nullptr) {
            std::array<size_t, XNN_MAX_TENSOR_DIMS> pre_paddings{};
            std::array<size_t, XNN_MAX_TENSOR_DIMS> post_paddings{};
            for (size_t i = 0; i < operands[ins[1]].dimensions.size(); i++) {
                pre_paddings[i] = static_cast<size_t>(paddings_data[i * 2 + 0]);
                post_paddings[i] = static_cast<size_t>(paddings_data[i * 2 + 1]);
            }
            const xnn_status status = xnn_define_static_constant_pad(
                    subgraph, pre_paddings.data(), post_paddings.data(), padding_value,
                    /*input_id=*/xnnpackTensors[ins[0]],
                    /*output_id=*/xnnpackTensors[outs[0]], /*flags=*/0);
            if (status != xnn_status_success) {
                LOG(ERROR) << "XNNPACK xnn_define_static_constant_pad FAILED";
                return V1_3::ErrorStatus::GENERAL_FAILURE;
            }
        }
        return V1_3::ErrorStatus::NONE;
    }

    static V1_3::ErrorStatus VisitPadV2Node(xnn_subgraph_t subgraph,
                                            const V1_3::Operation& operation,
                                            RunTimeOperandInfo* operands,
                                            const std::vector<uint32_t>& xnnpackTensors) {
        const hardware::hidl_vec<uint32_t>& ins = operation.inputs;
        if (operands[ins[2]].type != OperandType::FLOAT32) {
            return V1_3::ErrorStatus::INVALID_ARGUMENT;
        }
        float padding_value = getScalarData<float>(operands[ins[2]]);
        return VisitPadNode(subgraph, operation, operands, padding_value, xnnpackTensors);
    }

    static V1_3::ErrorStatus VisitReshapeNode(xnn_subgraph_t subgraph,
                                              const V1_3::Operation& operation,
                                              RunTimeOperandInfo* operands,
                                              const std::vector<uint32_t>& xnnpackTensors) {
        const hardware::hidl_vec<uint32_t>& ins = operation.inputs;
        const hardware::hidl_vec<uint32_t>& outs = operation.outputs;
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[ins[0]].type));
        NN_DRIVER_RETURN_IF_ERROR(
                CheckTensorShape(operands[ins[0]].dimensions, 0, XNN_MAX_TENSOR_DIMS));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorStaticAllocation(operands[ins[1]].lifetime));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[outs[0]].type));
        NN_DRIVER_RETURN_IF_ERROR(
                CheckTensorShape(operands[outs[0]].dimensions, 0, XNN_MAX_TENSOR_DIMS));

        if (subgraph != nullptr) {
            std::array<size_t, XNN_MAX_TENSOR_DIMS> new_shape;
            for (uint32_t i = 0; i < operands[outs[0]].dimensions.size(); i++) {
                new_shape[i] = static_cast<size_t>(operands[outs[0]].dimensions[i]);
            }
            const xnn_status status = xnn_define_static_reshape(
                    subgraph, static_cast<size_t>(operands[outs[0]].dimensions.size()),
                    new_shape.data(),
                    /*input_id=*/xnnpackTensors[ins[0]],
                    /*output_id=*/xnnpackTensors[outs[0]], /*flags=*/0);
            if (status != xnn_status_success) {
                LOG(ERROR) << "XNNPACK xnn_define_static_reshape FAILED";
                return V1_3::ErrorStatus::GENERAL_FAILURE;
            }
        }
        return V1_3::ErrorStatus::NONE;
    }

    static V1_3::ErrorStatus VisitResizeBilinearNode(xnn_subgraph_t subgraph,
                                                     const V1_3::Operation& operation,
                                                     RunTimeOperandInfo* operands,
                                                     const std::vector<uint32_t>& xnnpackTensors) {
        const hardware::hidl_vec<uint32_t>& ins = operation.inputs;
        const hardware::hidl_vec<uint32_t>& outs = operation.outputs;
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[ins[0]].type));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorShape(operands[ins[0]].dimensions, 4));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[outs[0]].type));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorShape(operands[outs[0]].dimensions, 4));
        // Make sure all scalar params are constant.
        for (uint32_t i = 1; i < ins.size(); i++) {
            NN_DRIVER_RETURN_IF_ERROR(CheckTensorStaticAllocation(operands[ins[i]].lifetime));
        }

        if (ins.size() >= 4) {
            bool use_nchw = getScalarData<bool>(operands[ins[3]]);
            if (use_nchw) {
                VLOG(DRIVER)
                        << "XNNPACK VisitResizeBilinearNode FAILED: only NHWC layout is supported";
                return V1_3::ErrorStatus::INVALID_ARGUMENT;
            }
        }

        size_t new_height, new_width;
        if (operands[ins[1]].type == OperandType::INT32) {
            // explicitly specify the output dimension.
            new_width = static_cast<size_t>(getScalarData<int32_t>(operands[ins[1]]));
            new_height = static_cast<size_t>(getScalarData<int32_t>(operands[ins[2]]));
        } else if (operands[ins[1]].type == OperandType::FLOAT32) {
            // specify the output dimension scaling factor.
            float width_scale = getScalarData<float>(operands[ins[1]]);
            float height_scale = getScalarData<float>(operands[ins[2]]);
            if (width_scale <= 0 || height_scale <= 0) {
                return V1_3::ErrorStatus::INVALID_ARGUMENT;
            }
            new_height = static_cast<size_t>(operands[ins[0]].dimensions[1] * height_scale);
            new_width = static_cast<size_t>(operands[ins[0]].dimensions[2] * width_scale);
        } else {
            return V1_3::ErrorStatus::INVALID_ARGUMENT;
        }

        bool align_corners = false;
        bool half_pixel_centers = false;
        if (ins.size() == 6) {
            align_corners = getScalarData<bool>(operands[ins[4]]);
            half_pixel_centers = getScalarData<bool>(operands[ins[5]]);
        }
        if (align_corners && !half_pixel_centers) {
            return V1_3::ErrorStatus::INVALID_ARGUMENT;
        }
        if (subgraph != nullptr) {
            uint32_t flags = 0;
            if (align_corners) {
                flags |= XNN_FLAG_ALIGN_CORNERS;
            } else if (!half_pixel_centers) {
                flags |= XNN_FLAG_TENSORFLOW_LEGACY_MODE;
            }
            const xnn_status status = xnn_define_static_resize_bilinear_2d(
                    subgraph, new_height, new_width,
                    /*input_id=*/xnnpackTensors[ins[0]],
                    /*output_id=*/xnnpackTensors[outs[0]], flags);
            if (status != xnn_status_success) {
                LOG(ERROR) << "XNNPACK xnn_define_static_resize_bilinear_2d FAILED";
                return V1_3::ErrorStatus::GENERAL_FAILURE;
            }
        }
        return V1_3::ErrorStatus::NONE;
    }

    static V1_3::ErrorStatus VisitReluNode(xnn_subgraph_t subgraph,
                                           const V1_3::Operation& operation,
                                           RunTimeOperandInfo* operands, float outputMin,
                                           float outputMax,
                                           const std::vector<uint32_t>& xnnpackTensors) {
        const hardware::hidl_vec<uint32_t>& ins = operation.inputs;
        const hardware::hidl_vec<uint32_t>& outs = operation.outputs;
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[ins[0]].type));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[outs[0]].type));

        if (subgraph != nullptr) {
            const xnn_status status =
                    xnn_define_clamp(subgraph, outputMin, outputMax,
                                     /*input_id=*/xnnpackTensors[ins[0]],
                                     /*output_id=*/xnnpackTensors[outs[0]], /*flags=*/0);
            if (status != xnn_status_success) {
                LOG(ERROR) << "XNNPACK xnn_define_clamp FAILED";
                return V1_3::ErrorStatus::GENERAL_FAILURE;
            }
        }
        return V1_3::ErrorStatus::NONE;
    }

    static V1_3::ErrorStatus VisitSqrtNode(xnn_subgraph_t subgraph,
                                           const V1_3::Operation& operation,
                                           RunTimeOperandInfo* operands,
                                           const std::vector<uint32_t>& xnnpackTensors) {
        const hardware::hidl_vec<uint32_t>& ins = operation.inputs;
        const hardware::hidl_vec<uint32_t>& outs = operation.outputs;
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[ins[0]].type));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[outs[0]].type));

        if (subgraph != nullptr) {
            const xnn_status status =
                    xnn_define_square_root(subgraph,
                                           /*input1_id=*/xnnpackTensors[ins[0]],
                                           /*output_id=*/xnnpackTensors[outs[0]], /*flags=*/0);
            if (status != xnn_status_success) {
                LOG(ERROR) << "XNNPACK xnn_define_bankers_rounding FAILED";
                return V1_3::ErrorStatus::GENERAL_FAILURE;
            }
        }
        return V1_3::ErrorStatus::NONE;
    }

    static V1_3::ErrorStatus VisitSubNode(xnn_subgraph_t subgraph, const V1_3::Operation& operation,
                                          RunTimeOperandInfo* operands,
                                          const std::vector<uint32_t>& xnnpackTensors) {
        const hardware::hidl_vec<uint32_t>& ins = operation.inputs;
        const hardware::hidl_vec<uint32_t>& outs = operation.outputs;
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[ins[0]].type));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[ins[1]].type));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorStaticAllocation(operands[ins[2]].lifetime));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[outs[0]].type));

        float outputMin = -std::numeric_limits<float>::infinity();
        float outputMax = +std::numeric_limits<float>::infinity();
        int activation = getScalarData<int32_t>(operands[ins[2]]);
        NN_DRIVER_RETURN_IF_ERROR(
                ConvertActivationToOutputRange(activation, &outputMin, &outputMax));

        if (subgraph != nullptr) {
            const xnn_status status =
                    xnn_define_subtract(subgraph, outputMin, outputMax,
                                        /*input1_id=*/xnnpackTensors[ins[0]],
                                        /*input2_id=*/xnnpackTensors[ins[1]],
                                        /*output_id=*/xnnpackTensors[outs[0]], /*flags=*/0);
            if (status != xnn_status_success) {
                LOG(ERROR) << "XNNPACK xnn_define_subtract FAILED";
                return V1_3::ErrorStatus::GENERAL_FAILURE;
            }
        }
        return V1_3::ErrorStatus::NONE;
    }

    static V1_3::ErrorStatus VisitSoftmaxNode(xnn_subgraph_t subgraph,
                                              const V1_3::Operation& operation,
                                              RunTimeOperandInfo* operands,
                                              const std::vector<uint32_t>& xnnpackTensors) {
        const hardware::hidl_vec<uint32_t>& ins = operation.inputs;
        const hardware::hidl_vec<uint32_t>& outs = operation.outputs;
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[ins[0]].type));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorStaticAllocation(operands[ins[1]].lifetime));
        NN_DRIVER_RETURN_IF_ERROR(CheckTensorFloatType(operands[outs[0]].type));

        float beta = getScalarData<float>(operands[ins[1]]);
        if (beta != 1.0f) {
            LOG(ERROR) << "XNNPACK VisitSoftmaxNode FAILED, unsupported beta value: " << beta;
            return V1_3::ErrorStatus::INVALID_ARGUMENT;
        }
        if (ins.size() >= 3) {
            NN_DRIVER_RETURN_IF_ERROR(CheckTensorStaticAllocation(operands[ins[2]].lifetime));
            int axis = getScalarData<int32_t>(operands[ins[2]]);
            if (axis != -1) {
                LOG(ERROR) << "XNNPACK VisitSoftmaxNode FAILED, unsupported axis value: " << axis;
                return V1_3::ErrorStatus::INVALID_ARGUMENT;
            }
        }
        if (subgraph != nullptr) {
            const xnn_status status =
                    xnn_define_softmax(subgraph, /*input_id=*/xnnpackTensors[ins[0]],
                                       /*output_id=*/xnnpackTensors[outs[0]], /*flags=*/0);
            if (status != xnn_status_success) {
                LOG(ERROR) << "XNNPACK xnn_define_softmax FAILED";
                return V1_3::ErrorStatus::GENERAL_FAILURE;
            }
        }

        return V1_3::ErrorStatus::NONE;
    }

   private:
    Subgraph(xnn_runtime_t runtime, std::unordered_set<uint32_t>&& externals,
             bool useStaticBuffer = false)
        : mRuntime(runtime, &xnn_delete_runtime),
          mExternals(externals),
          mUseStaticBuffer(useStaticBuffer) {}

    // XNNPACK Runtime (subgraph + workspace) with smart-pointer for lifetime
    // management.
    std::unique_ptr<xnn_runtime, decltype(&xnn_delete_runtime)> mRuntime{nullptr,
                                                                         &xnn_delete_runtime};
    std::unordered_set<uint32_t> mExternals;
    bool mFirstRun = true;
    bool mUseStaticBuffer;
};

class SamplePreparedModelXNNPACK : public SamplePreparedModel {
   public:
    SamplePreparedModelXNNPACK(const V1_3::Model& model, const SampleDriver* driver,
                               V1_1::ExecutionPreference preference, uid_t userId,
                               V1_3::Priority priority)
        : SamplePreparedModel(model, driver, preference, userId, priority),
          mSubgraph(nullptr),
          mThreadpool(nullptr) {}
    ~SamplePreparedModelXNNPACK() {
        delete mSubgraph;
        pthreadpool_destroy(mThreadpool);
    };
    bool initialize();
    hardware::Return<V1_0::ErrorStatus> execute(
            const V1_0::Request& request, const sp<V1_0::IExecutionCallback>& callback) override;
    hardware::Return<V1_0::ErrorStatus> execute_1_2(
            const V1_0::Request& request, V1_2::MeasureTiming measure,
            const sp<V1_2::IExecutionCallback>& callback) override;
    hardware::Return<V1_3::ErrorStatus> execute_1_3(
            const V1_3::Request& request, V1_2::MeasureTiming measure,
            const V1_3::OptionalTimePoint& deadline,
            const V1_3::OptionalTimeoutDuration& loopTimeoutDuration,
            const sp<V1_3::IExecutionCallback>& callback) override;
    hardware::Return<void> executeSynchronously(const V1_0::Request& request,
                                                V1_2::MeasureTiming measure,
                                                executeSynchronously_cb cb) override;
    hardware::Return<void> executeSynchronously_1_3(
            const V1_3::Request& request, V1_2::MeasureTiming measure,
            const V1_3::OptionalTimePoint& deadline,
            const V1_3::OptionalTimeoutDuration& loopTimeoutDuration,
            executeSynchronously_1_3_cb cb) override;
    hardware::Return<void> configureExecutionBurst(
            const sp<V1_2::IBurstCallback>& callback,
            const MQDescriptorSync<V1_2::FmqRequestDatum>& requestChannel,
            const MQDescriptorSync<V1_2::FmqResultDatum>& resultChannel,
            configureExecutionBurst_cb cb) override;
    hardware::Return<void> executeFenced(const V1_3::Request& request,
                                         const hardware::hidl_vec<hardware::hidl_handle>& wait_for,
                                         V1_2::MeasureTiming measure,
                                         const V1_3::OptionalTimePoint& deadline,
                                         const V1_3::OptionalTimeoutDuration& loopTimeoutDuration,
                                         const V1_3::OptionalTimeoutDuration& duration,
                                         executeFenced_cb callback) override;

   private:
    Subgraph* mSubgraph;
    std::vector<RunTimeOperandInfo> mOperands;
    pthreadpool* mThreadpool;
};

hardware::Return<void> SamplePreparedModelXNNPACK::configureExecutionBurst(
        const sp<V1_2::IBurstCallback>& callback,
        const MQDescriptorSync<V1_2::FmqRequestDatum>& requestChannel,
        const MQDescriptorSync<V1_2::FmqResultDatum>& resultChannel,
        configureExecutionBurst_cb cb) {
    VLOG(DRIVER) << "SamplePreparedModelXNNPACK::configureExecutionBurst not supported";
    cb(V1_0::ErrorStatus::GENERAL_FAILURE, {});
    return hardware::Void();
}

bool SamplePreparedModelXNNPACK::initialize() {
    auto status = SamplePreparedModel::initialize();
    mThreadpool = pthreadpool_create(kNumOfWorkerThreads);
    if (mThreadpool == nullptr) {
        VLOG(DRIVER) << "SamplePreparedModelXNNPACK::initialize failed to create pthreadpool, "
                        "fallback to single threaded execution";
    }
    const V1_3::Model* model = getModel();
    mOperands = initializeRunTimeInfo(model->main, mPoolInfos, &model->operandValues);
    mSubgraph = Subgraph::Create(model->main.operations, mOperands, model->main.inputIndexes,
                                 model->main.outputIndexes, mThreadpool);
    return status;
}

template <typename T_IExecutionCallback>
void asyncExecuteXNNPACK(Subgraph* subgraph, RunTimeOperandInfo* operands,
                         const V1_3::Request& request, V1_2::MeasureTiming measure,
                         const V1_3::Model& model, const LegacyOptionalTimePoint& deadline,
                         const V1_3::OptionalTimeoutDuration& loopTimeoutDuration,
                         const sp<T_IExecutionCallback>& callback) {
    std::vector<RunTimePoolInfo> requestPoolInfos;
    if (!setRunTimePoolInfosFromMemoryPools(&requestPoolInfos, uncheckedConvert(request.pools))) {
        notify(callback, V1_3::ErrorStatus::GENERAL_FAILURE, {}, kNoTiming);
    }
    updateForArguments(model.main.inputIndexes, request.inputs, requestPoolInfos, operands);
    updateForArguments(model.main.outputIndexes, request.outputs, requestPoolInfos, operands);
    auto status = subgraph->Invoke(operands);
    VLOG(DRIVER) << "XNNPACK subgraph invoke returned " << toString(status);
    if (status == V1_3::ErrorStatus::NONE) {
        VLOG(DRIVER) << "Completed run normally";
        for (auto& runtimeInfo : requestPoolInfos) {
            runtimeInfo.flush();
        }
    }
    notify(callback, status, {}, kNoTiming);
}

template <typename T_IExecutionCallback>
V1_3::ErrorStatus executeXNNPACKBase(Subgraph* subgraph, RunTimeOperandInfo* operands,
                                     const V1_3::Request& request, V1_2::MeasureTiming measure,
                                     const V1_3::Model& model,
                                     const V1_3::OptionalTimePoint& halDeadline,
                                     const V1_3::OptionalTimeoutDuration& loopTimeoutDuration,
                                     const sp<T_IExecutionCallback>& callback) {
    VLOG(DRIVER) << "executeXNNPACKBase(" << SHOW_IF_DEBUG(toString(request)) << ")";

    if (callback.get() == nullptr) {
        LOG(ERROR) << "invalid callback passed to executeXNNPACKBase";
        return V1_3::ErrorStatus::INVALID_ARGUMENT;
    }
    if (!validateRequest(request, model, /*allowUnspecifiedOutput=*/false)) {
        notify(callback, V1_3::ErrorStatus::INVALID_ARGUMENT, {}, kNoTiming);
        return V1_3::ErrorStatus::INVALID_ARGUMENT;
    }
    const auto deadline = makeDeadline(halDeadline);
    if (hasDeadlinePassed(deadline)) {
        notify(callback, V1_3::ErrorStatus::MISSED_DEADLINE_PERSISTENT, {}, kNoTiming);
        return V1_3::ErrorStatus::NONE;
    }

    // This thread is intentionally detached because the sample driver service
    // is expected to live forever.
    std::thread([&subgraph, &operands, &model, request, measure, deadline, loopTimeoutDuration,
                 callback] {
        asyncExecuteXNNPACK(subgraph, operands, request, measure, model, deadline,
                            loopTimeoutDuration, callback);
    }).detach();

    return V1_3::ErrorStatus::NONE;
}

hardware::Return<V1_0::ErrorStatus> SamplePreparedModelXNNPACK::execute(
        const V1_0::Request& request, const sp<V1_0::IExecutionCallback>& callback) {
    const V1_3::Model* model = getModel();
    const V1_3::ErrorStatus status =
            executeXNNPACKBase(mSubgraph, mOperands.data(), convertToV1_3(request),
                               V1_2::MeasureTiming::NO, *model, {}, {}, callback);
    return convertToV1_0(status);
}

hardware::Return<V1_0::ErrorStatus> SamplePreparedModelXNNPACK::execute_1_2(
        const V1_0::Request& request, V1_2::MeasureTiming measure,
        const sp<V1_2::IExecutionCallback>& callback) {
    const V1_3::Model* model = getModel();
    const V1_3::ErrorStatus status = executeXNNPACKBase(
            mSubgraph, mOperands.data(), convertToV1_3(request), measure, *model, {}, {}, callback);
    return convertToV1_0(status);
}

hardware::Return<V1_3::ErrorStatus> SamplePreparedModelXNNPACK::execute_1_3(
        const V1_3::Request& request, V1_2::MeasureTiming measure,
        const V1_3::OptionalTimePoint& deadline,
        const V1_3::OptionalTimeoutDuration& loopTimeoutDuration,
        const sp<V1_3::IExecutionCallback>& callback) {
    const V1_3::Model* model = getModel();
    return executeXNNPACKBase(mSubgraph, mOperands.data(), request, measure, *model, deadline,
                              loopTimeoutDuration, callback);
}

static std::tuple<V1_3::ErrorStatus, hardware::hidl_vec<V1_2::OutputShape>, V1_2::Timing>
executeSynchronouslyXNNPACKBase(Subgraph* subgraph, RunTimeOperandInfo* operands,
                                const V1_3::Request& request, V1_2::MeasureTiming measure,
                                const V1_3::Model& model,
                                const V1_3::OptionalTimePoint& halDeadline,
                                const V1_3::OptionalTimeoutDuration& loopTimeoutDuration) {
    VLOG(DRIVER) << "executeSynchronouslyXNNPACKBase(" << SHOW_IF_DEBUG(toString(request)) << ")";

    if (!validateRequest(request, model, /*allowUnspecifiedOutput=*/false)) {
        return {V1_3::ErrorStatus::INVALID_ARGUMENT, {}, kNoTiming};
    }
    const auto deadline = makeDeadline(halDeadline);
    if (hasDeadlinePassed(deadline)) {
        return {V1_3::ErrorStatus::MISSED_DEADLINE_PERSISTENT, {}, kNoTiming};
    }

    std::vector<RunTimePoolInfo> requestPoolInfos;
    if (!setRunTimePoolInfosFromMemoryPools(&requestPoolInfos, uncheckedConvert(request.pools))) {
        return {V1_3::ErrorStatus::GENERAL_FAILURE, {}, kNoTiming};
    }
    updateForArguments(model.main.inputIndexes, request.inputs, requestPoolInfos, operands);
    updateForArguments(model.main.outputIndexes, request.outputs, requestPoolInfos, operands);
    VLOG(DRIVER) << "XNNPACK subgraph invoke started";
    auto status = subgraph->Invoke(operands);
    VLOG(DRIVER) << "XNNPACK subgraph invoke returned " << toString(status);
    if (status == V1_3::ErrorStatus::NONE) {
        VLOG(DRIVER) << "Completed run normally";
        for (auto& runtimeInfo : requestPoolInfos) {
            runtimeInfo.flush();
        }
    }
    return {status, {}, kNoTiming};
}

hardware::Return<void> SamplePreparedModelXNNPACK::executeSynchronously(
        const V1_0::Request& request, V1_2::MeasureTiming measure, executeSynchronously_cb cb) {
    const V1_3::Model* model = getModel();
    auto [status, outputShapes, timing] = executeSynchronouslyXNNPACKBase(
            mSubgraph, mOperands.data(), convertToV1_3(request), measure, *model, {}, {});
    cb(convertToV1_0(status), std::move(outputShapes), timing);
    return hardware::Void();
}

hardware::Return<void> SamplePreparedModelXNNPACK::executeSynchronously_1_3(
        const V1_3::Request& request, V1_2::MeasureTiming measure,
        const V1_3::OptionalTimePoint& deadline,
        const V1_3::OptionalTimeoutDuration& loopTimeoutDuration, executeSynchronously_1_3_cb cb) {
    const V1_3::Model* model = getModel();
    auto [status, outputShapes, timing] = executeSynchronouslyXNNPACKBase(
            mSubgraph, mOperands.data(), request, measure, *model, deadline, loopTimeoutDuration);
    cb(status, std::move(outputShapes), timing);
    return hardware::Void();
}

// The sample driver will finish the execution and then return.
hardware::Return<void> SamplePreparedModelXNNPACK::executeFenced(
        const V1_3::Request& request, const hardware::hidl_vec<hardware::hidl_handle>& waitFor,
        V1_2::MeasureTiming measure, const V1_3::OptionalTimePoint& halDeadline,
        const V1_3::OptionalTimeoutDuration& loopTimeoutDuration,
        const V1_3::OptionalTimeoutDuration& duration, executeFenced_cb cb) {
    VLOG(DRIVER) << "executeFenced(" << SHOW_IF_DEBUG(toString(request)) << ")";
    const V1_3::Model* model = getModel();
    if (!validateRequest(request, *model, /*allowUnspecifiedOutput=*/false)) {
        cb(V1_3::ErrorStatus::INVALID_ARGUMENT, hardware::hidl_handle(nullptr), nullptr);
        return hardware::Void();
    }
    const auto deadline = makeDeadline(halDeadline);
    if (hasDeadlinePassed(deadline)) {
        cb(V1_3::ErrorStatus::MISSED_DEADLINE_PERSISTENT, hardware::hidl_handle(nullptr), nullptr);
        return hardware::Void();
    }

    // Wait for the dependent events to signal
    for (const auto& fenceHandle : waitFor) {
        if (!fenceHandle.getNativeHandle()) {
            cb(V1_3::ErrorStatus::INVALID_ARGUMENT, hardware::hidl_handle(nullptr), nullptr);
            return hardware::Void();
        }
        int syncFenceFd = fenceHandle.getNativeHandle()->data[0];
        if (syncWait(syncFenceFd, -1) != FenceState::SIGNALED) {
            LOG(ERROR) << "syncWait failed";
            cb(V1_3::ErrorStatus::GENERAL_FAILURE, hardware::hidl_handle(nullptr), nullptr);
            return hardware::Void();
        }
    }
    std::vector<RunTimePoolInfo> requestPoolInfos;
    if (!setRunTimePoolInfosFromMemoryPools(&requestPoolInfos, uncheckedConvert(request.pools))) {
        cb(V1_3::ErrorStatus::GENERAL_FAILURE, hardware::hidl_handle(nullptr), nullptr);
    }
    updateForArguments(model->main.inputIndexes, request.inputs, requestPoolInfos,
                       mOperands.data());
    updateForArguments(model->main.outputIndexes, request.outputs, requestPoolInfos,
                       mOperands.data());
    auto status = mSubgraph->Invoke(mOperands.data());
    VLOG(DRIVER) << "XNNPACK subgraph invoke returned " << toString(status);
    if (status == V1_3::ErrorStatus::NONE) {
        VLOG(DRIVER) << "Completed run normally";
        for (auto& runtimeInfo : requestPoolInfos) {
            runtimeInfo.flush();
        }
    }

    sp<SampleFencedExecutionCallback> fencedExecutionCallback =
            new SampleFencedExecutionCallback(kNoTiming, kNoTiming, status);
    cb(status, hardware::hidl_handle(nullptr), fencedExecutionCallback);
    return hardware::Void();
}

class SampleDriverFloatXNNPACK : public SampleDriverPartial {
   public:
    SampleDriverFloatXNNPACK(const std::string& name) : SampleDriverPartial(name.c_str()) {}
    hardware::Return<void> getCapabilities_1_3(getCapabilities_1_3_cb cb) override;
    hardware::Return<V1_0::ErrorStatus> prepareModel(
            const V1_0::Model& model, const sp<V1_0::IPreparedModelCallback>& callback) override;
    hardware::Return<V1_0::ErrorStatus> prepareModel_1_1(
            const V1_1::Model& model, V1_1::ExecutionPreference preference,
            const sp<V1_0::IPreparedModelCallback>& callback) override;
    hardware::Return<V1_0::ErrorStatus> prepareModel_1_2(
            const V1_2::Model& model, V1_1::ExecutionPreference preference,
            const hardware::hidl_vec<hardware::hidl_handle>& modelCache,
            const hardware::hidl_vec<hardware::hidl_handle>& dataCache, const HalCacheToken& token,
            const sp<V1_2::IPreparedModelCallback>& callback) override;
    hardware::Return<V1_3::ErrorStatus> prepareModel_1_3(
            const V1_3::Model& model, V1_1::ExecutionPreference preference, V1_3::Priority priority,
            const V1_3::OptionalTimePoint& deadline,
            const hardware::hidl_vec<hardware::hidl_handle>& modelCache,
            const hardware::hidl_vec<hardware::hidl_handle>& dataCache, const HalCacheToken& token,
            const sp<V1_3::IPreparedModelCallback>& callback) override;
    hardware::Return<void> allocate(
            const V1_3::BufferDesc& desc,
            const hardware::hidl_vec<sp<V1_3::IPreparedModel>>& preparedModels,
            const hardware::hidl_vec<V1_3::BufferRole>& inputRoles,
            const hardware::hidl_vec<V1_3::BufferRole>& outputRoles, allocate_cb cb) override;

   private:
    std::vector<bool> getSupportedOperationsImpl(const V1_3::Model& model) const override;
};

template <typename T_Model, typename T_IPreparedModelCallback>
V1_3::ErrorStatus prepareModelXNNPACK(const T_Model& model, const SampleDriver* driver,
                                      V1_1::ExecutionPreference preference, V1_3::Priority priority,
                                      const V1_3::OptionalTimePoint& deadline,
                                      const sp<T_IPreparedModelCallback>& callback) {
    const uid_t userId = hardware::IPCThreadState::self()->getCallingUid();
    if (callback.get() == nullptr) {
        LOG(ERROR) << "invalid callback passed to prepareModelBase";
        return V1_3::ErrorStatus::INVALID_ARGUMENT;
    }
    if (VLOG_IS_ON(DRIVER)) {
        VLOG(DRIVER) << "prepareModelBase";
        logModelToInfo(model);
    }
    if (!validateModel(model) || !validateExecutionPreference(preference) ||
        !validatePriority(priority)) {
        notify(callback, V1_3::ErrorStatus::INVALID_ARGUMENT, nullptr);
        return V1_3::ErrorStatus::INVALID_ARGUMENT;
    }

    // asynchronously prepare the model from a new, detached thread
    std::thread([model, driver, preference, userId, priority, callback] {
        sp<SamplePreparedModelXNNPACK> preparedModel = new SamplePreparedModelXNNPACK(
                convertToV1_3(model), driver, preference, userId, priority);
        if (!preparedModel->initialize()) {
            notify(callback, V1_3::ErrorStatus::INVALID_ARGUMENT, nullptr);
            return;
        }
        notify(callback, V1_3::ErrorStatus::NONE, preparedModel);
    }).detach();

    return V1_3::ErrorStatus::NONE;
}

hardware::Return<V1_0::ErrorStatus> SampleDriverFloatXNNPACK::prepareModel(
        const V1_0::Model& model, const sp<V1_0::IPreparedModelCallback>& callback) {
    const V1_3::ErrorStatus status =
            prepareModelXNNPACK(model, this, V1_1::ExecutionPreference::FAST_SINGLE_ANSWER,
                                kDefaultPriority13, {}, callback);
    return convertToV1_0(status);
}

hardware::Return<V1_0::ErrorStatus> SampleDriverFloatXNNPACK::prepareModel_1_1(
        const V1_1::Model& model, V1_1::ExecutionPreference preference,
        const sp<V1_0::IPreparedModelCallback>& callback) {
    const V1_3::ErrorStatus status =
            prepareModelXNNPACK(model, this, preference, kDefaultPriority13, {}, callback);
    return convertToV1_0(status);
}

hardware::Return<V1_0::ErrorStatus> SampleDriverFloatXNNPACK::prepareModel_1_2(
        const V1_2::Model& model, V1_1::ExecutionPreference preference,
        const hardware::hidl_vec<hardware::hidl_handle>&,
        const hardware::hidl_vec<hardware::hidl_handle>&, const HalCacheToken&,
        const sp<V1_2::IPreparedModelCallback>& callback) {
    const V1_3::ErrorStatus status =
            prepareModelXNNPACK(model, this, preference, kDefaultPriority13, {}, callback);
    return convertToV1_0(status);
}

hardware::Return<V1_3::ErrorStatus> SampleDriverFloatXNNPACK::prepareModel_1_3(
        const V1_3::Model& model, V1_1::ExecutionPreference preference, V1_3::Priority priority,
        const V1_3::OptionalTimePoint& deadline,
        const hardware::hidl_vec<hardware::hidl_handle>& modelCache,
        const hardware::hidl_vec<hardware::hidl_handle>& dataCache, const HalCacheToken& token,
        const sp<V1_3::IPreparedModelCallback>& callback) {
    return prepareModelXNNPACK(model, this, preference, priority, deadline, callback);
}

hardware::Return<void> SampleDriverFloatXNNPACK::getCapabilities_1_3(getCapabilities_1_3_cb cb) {
    android::nn::initVLogMask();
    VLOG(DRIVER) << "SampleDriverFloatXNNPACK::getCapabilities()";

    V1_3::Capabilities capabilities = {
            .relaxedFloat32toFloat16PerformanceScalar = {.execTime = 0.7f, .powerUsage = 1.1f},
            .relaxedFloat32toFloat16PerformanceTensor = {.execTime = 0.7f, .powerUsage = 1.1f},
            .operandPerformance = nonExtensionOperandPerformance<HalVersion::V1_3>({1.0f, 1.0f}),
            .ifPerformance = {.execTime = 1.0f, .powerUsage = 1.0f},
            .whilePerformance = {.execTime = 1.0f, .powerUsage = 1.0f}};
    update(&capabilities.operandPerformance, V1_3::OperandType::TENSOR_FLOAT32,
           {.execTime = 0.8f, .powerUsage = 1.2f});
    update(&capabilities.operandPerformance, V1_3::OperandType::FLOAT32,
           {.execTime = 0.8f, .powerUsage = 1.2f});

    cb(V1_3::ErrorStatus::NONE, capabilities);
    return hardware::Void();
}

std::vector<bool> SampleDriverFloatXNNPACK::getSupportedOperationsImpl(
        const V1_3::Model& model) const {
    std::vector<RunTimePoolInfo> poolInfos;
    setRunTimePoolInfosFromCanonicalMemories(&poolInfos, uncheckedConvert(model.pools));
    auto operands = initializeRunTimeInfo(model.main, poolInfos, &model.operandValues);
    const size_t count = model.main.operations.size();
    std::vector<bool> supported(count);
    for (size_t i = 0; i < count; i++) {
        bool isSupportedOp = false;
        const V1_3::Operation& operation = model.main.operations[i];
        if (Subgraph::VisitNode(/*subgraph=*/nullptr, operation, operands.data(), {}) ==
            V1_3::ErrorStatus::NONE) {
            isSupportedOp = true;
        }
        supported[i] = isSupportedOp;
    }
    return supported;
}

hardware::Return<void> SampleDriverFloatXNNPACK::allocate(
        const V1_3::BufferDesc& desc,
        const hardware::hidl_vec<sp<V1_3::IPreparedModel>>& preparedModels,
        const hardware::hidl_vec<V1_3::BufferRole>& inputRoles,
        const hardware::hidl_vec<V1_3::BufferRole>& outputRoles, allocate_cb cb) {
    VLOG(DRIVER) << "SampleDriverFloatXNNPACK::allocate not supported";
    constexpr uint32_t kInvalidBufferToken = 0;
    cb(V1_3::ErrorStatus::INVALID_ARGUMENT, nullptr, kInvalidBufferToken);
    return hardware::Void();
}

}  // namespace sample_driver
}  // namespace nn
}  // namespace android

using android::sp;
using android::nn::sample_driver::SampleDriverFloatXNNPACK;

int main() {
    const std::string name = "nnapi-sample_float_xnnpack";
    const auto driver = sp<SampleDriverFloatXNNPACK>::make(name);
    xnn_status status = xnn_initialize(/*allocator=*/nullptr);
    if (status != xnn_status_success) {
        return 0;
    }
    return run(driver, name);
}
