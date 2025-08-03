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

// Contains all the entry points to the C Neural Networks API.
// We do basic validation of the operands and then call the class
// that implements the functionality.

#define LOG_TAG "NeuralNetworks"

#include <ControlFlow.h>
#include <LegacyUtils.h>
#include <MetaModel.h>
#include <Tracing.h>
#include <nnapi/Types.h>

#include <algorithm>
#include <cstddef>
#include <memory>
#include <utility>
#include <vector>

#include "BurstBuilder.h"
#include "CompilationBuilder.h"
#include "Event.h"
#include "ExecutionBuilder.h"
#include "ExecutionCallback.h"
#include "FlatbufferModelBuilder.h"
#include "Manager.h"
#include "Memory.h"
#include "NeuralNetworks.h"
#include "NeuralNetworksExtensions.h"
#include "NeuralNetworksOEM.h"
#include "Telemetry.h"

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wunused-parameter"
#include "tensorflow/lite/interpreter.h"
#include "tensorflow/lite/kernels/register.h"
#include "tensorflow/lite/model.h"
#pragma clang diagnostic pop

using namespace android::nn;

// Make sure the constants defined in the header files have not changed values.
// IMPORTANT: When adding new values, update kNumberOfDataTypes or kNumberOfDataTypesOEM
// in Utils.h.
static_assert(ANEURALNETWORKS_FLOAT32 == 0, "ANEURALNETWORKS_FLOAT32 has changed");
static_assert(ANEURALNETWORKS_INT32 == 1, "ANEURALNETWORKS_INT32 has changed");
static_assert(ANEURALNETWORKS_UINT32 == 2, "ANEURALNETWORKS_UINT32 has changed");
static_assert(ANEURALNETWORKS_TENSOR_FLOAT32 == 3, "ANEURALNETWORKS_TENSOR_FLOAT32 has changed");
static_assert(ANEURALNETWORKS_TENSOR_INT32 == 4, "ANEURALNETWORKS_TENSOR_INT32 has changed");
static_assert(ANEURALNETWORKS_TENSOR_QUANT8_ASYMM == 5,
              "ANEURALNETWORKS_TENSOR_QUANT8_ASYMM has changed");
static_assert(ANEURALNETWORKS_BOOL == 6, "ANEURALNETWORKS_BOOL has changed");
static_assert(ANEURALNETWORKS_TENSOR_QUANT16_SYMM == 7,
              "ANEURALNETWORKS_TENSOR_QUANT16_SYMM has changed");
static_assert(ANEURALNETWORKS_TENSOR_FLOAT16 == 8, "ANEURALNETWORKS_TENSOR_FLOAT16 has changed");
static_assert(ANEURALNETWORKS_TENSOR_BOOL8 == 9, "ANEURALNETWORKS_TENSOR_BOOL8 has changed");
static_assert(ANEURALNETWORKS_FLOAT16 == 10, "ANEURALNETWORKS_FLOAT16 has changed");
static_assert(ANEURALNETWORKS_TENSOR_QUANT8_SYMM_PER_CHANNEL == 11,
              "ANEURALNETWORKS_TENSOR_QUANT8_SYMM_PER_CHANNEL has changed");
static_assert(ANEURALNETWORKS_TENSOR_QUANT16_ASYMM == 12,
              "ANEURALNETWORKS_TENSOR_QUANT16_ASYMM has changed");
static_assert(ANEURALNETWORKS_TENSOR_QUANT8_SYMM == 13,
              "ANEURALNETWORKS_TENSOR_QUANT8_SYMM has changed");
static_assert(ANEURALNETWORKS_OEM_SCALAR == 10000, "ANEURALNETWORKS_OEM_SCALAR has changed");
static_assert(ANEURALNETWORKS_TENSOR_OEM_BYTE == 10001,
              "ANEURALNETWORKS_TENSOR_OEM_BYTE has changed");

// IMPORTANT: When adding new values, update kNumberOfOperationTypes or
// kNumberOfOperationTypesOEMin Utils.h.
static_assert(ANEURALNETWORKS_ADD == 0, "ANEURALNETWORKS_ADD has changed");
static_assert(ANEURALNETWORKS_AVERAGE_POOL_2D == 1, "ANEURALNETWORKS_AVERAGE_POOL_2D has changed");
static_assert(ANEURALNETWORKS_CONCATENATION == 2, "ANEURALNETWORKS_CONCATENATION has changed");
static_assert(ANEURALNETWORKS_CONV_2D == 3, "ANEURALNETWORKS_CONV_2D has changed");
static_assert(ANEURALNETWORKS_DEPTHWISE_CONV_2D == 4,
              "ANEURALNETWORKS_DEPTHWISE_CONV_2D has changed");
static_assert(ANEURALNETWORKS_DEPTH_TO_SPACE == 5, "ANEURALNETWORKS_DEPTH_TO_SPACE has changed");
static_assert(ANEURALNETWORKS_DEQUANTIZE == 6, "ANEURALNETWORKS_DEQUANTIZE has changed");
static_assert(ANEURALNETWORKS_EMBEDDING_LOOKUP == 7,
              "ANEURALNETWORKS_EMBEDDING_LOOKUP has changed");
static_assert(ANEURALNETWORKS_FLOOR == 8, "ANEURALNETWORKS_FLOOR has changed");
static_assert(ANEURALNETWORKS_FULLY_CONNECTED == 9, "ANEURALNETWORKS_FULLY_CONNECTED has changed");
static_assert(ANEURALNETWORKS_HASHTABLE_LOOKUP == 10,
              "ANEURALNETWORKS_HASHTABLE_LOOKUP has changed");
static_assert(ANEURALNETWORKS_L2_NORMALIZATION == 11,
              "ANEURALNETWORKS_L2_NORMALIZATION has changed");
static_assert(ANEURALNETWORKS_L2_POOL_2D == 12, "ANEURALNETWORKS_L2_POOL has changed");
static_assert(ANEURALNETWORKS_LOCAL_RESPONSE_NORMALIZATION == 13,
              "ANEURALNETWORKS_LOCAL_RESPONSE_NORMALIZATION has changed");
static_assert(ANEURALNETWORKS_LOGISTIC == 14, "ANEURALNETWORKS_LOGISTIC has changed");
static_assert(ANEURALNETWORKS_LSH_PROJECTION == 15, "ANEURALNETWORKS_LSH_PROJECTION has changed");
static_assert(ANEURALNETWORKS_LSTM == 16, "ANEURALNETWORKS_LSTM has changed");
static_assert(ANEURALNETWORKS_MAX_POOL_2D == 17, "ANEURALNETWORKS_MAX_POOL has changed");
static_assert(ANEURALNETWORKS_MUL == 18, "ANEURALNETWORKS_MUL has changed");
static_assert(ANEURALNETWORKS_RELU == 19, "ANEURALNETWORKS_RELU has changed");
static_assert(ANEURALNETWORKS_RELU1 == 20, "ANEURALNETWORKS_RELU1 has changed");
static_assert(ANEURALNETWORKS_RELU6 == 21, "ANEURALNETWORKS_RELU6 has changed");
static_assert(ANEURALNETWORKS_RESHAPE == 22, "ANEURALNETWORKS_RESHAPE has changed");
static_assert(ANEURALNETWORKS_RESIZE_BILINEAR == 23, "ANEURALNETWORKS_RESIZE_BILINEAR has changed");
static_assert(ANEURALNETWORKS_RNN == 24, "ANEURALNETWORKS_RNN has changed");
static_assert(ANEURALNETWORKS_SOFTMAX == 25, "ANEURALNETWORKS_SOFTMAX has changed");
static_assert(ANEURALNETWORKS_SPACE_TO_DEPTH == 26, "ANEURALNETWORKS_SPACE_TO_DEPTH has changed");
static_assert(ANEURALNETWORKS_SVDF == 27, "ANEURALNETWORKS_SVDF has changed");
static_assert(ANEURALNETWORKS_TANH == 28, "ANEURALNETWORKS_TANH has changed");

static_assert(ANEURALNETWORKS_BATCH_TO_SPACE_ND == 29,
              "ANEURALNETWORKS_BATCH_TO_SPACE_ND has changed");
static_assert(ANEURALNETWORKS_DIV == 30, "ANEURALNETWORKS_DIV has changed");
static_assert(ANEURALNETWORKS_MEAN == 31, "ANEURALNETWORKS_MEAN has changed");
static_assert(ANEURALNETWORKS_PAD == 32, "ANEURALNETWORKS_PAD has changed");
static_assert(ANEURALNETWORKS_SPACE_TO_BATCH_ND == 33,
              "ANEURALNETWORKS_SPACE_TO_BATCH_ND has changed");
static_assert(ANEURALNETWORKS_SQUEEZE == 34, "ANEURALNETWORKS_SQUEEZE has changed");
static_assert(ANEURALNETWORKS_STRIDED_SLICE == 35, "ANEURALNETWORKS_STRIDED_SLICE has changed");
static_assert(ANEURALNETWORKS_SUB == 36, "ANEURALNETWORKS_TANH has changed");
static_assert(ANEURALNETWORKS_TRANSPOSE == 37, "ANEURALNETWORKS_TRANSPOSE has changed");

static_assert(ANEURALNETWORKS_ABS == 38, "ANEURALNETWORKS_ABS has changed");
static_assert(ANEURALNETWORKS_ARGMAX == 39, "ANEURALNETWORKS_ARGMAX has changed");
static_assert(ANEURALNETWORKS_ARGMIN == 40, "ANEURALNETWORKS_ARGMIN has changed");
static_assert(ANEURALNETWORKS_AXIS_ALIGNED_BBOX_TRANSFORM == 41,
              "ANEURALNETWORKS_AXIS_ALIGNED_BBOX_TRANSFORM has changed");
static_assert(ANEURALNETWORKS_BIDIRECTIONAL_SEQUENCE_LSTM == 42,
              "ANEURALNETWORKS_BIDIRECTIONAL_SEQUENCE_LSTM has changed");
static_assert(ANEURALNETWORKS_BIDIRECTIONAL_SEQUENCE_RNN == 43,
              "ANEURALNETWORKS_BIDIRECTIONAL_SEQUENCE_RNN has changed");
static_assert(ANEURALNETWORKS_BOX_WITH_NMS_LIMIT == 44,
              "ANEURALNETWORKS_BOX_WITH_NMS_LIMIT has changed");
static_assert(ANEURALNETWORKS_CAST == 45, "ANEURALNETWORKS_CAST has changed");
static_assert(ANEURALNETWORKS_CHANNEL_SHUFFLE == 46, "ANEURALNETWORKS_CHANNEL_SHUFFLE has changed");
static_assert(ANEURALNETWORKS_DETECTION_POSTPROCESSING == 47,
              "ANEURALNETWORKS_DETECTION_POSTPROCESSING has changed");
static_assert(ANEURALNETWORKS_EQUAL == 48, "ANEURALNETWORKS_EQUAL has changed");
static_assert(ANEURALNETWORKS_EXP == 49, "ANEURALNETWORKS_EXP has changed");
static_assert(ANEURALNETWORKS_EXPAND_DIMS == 50, "ANEURALNETWORKS_EXPAND_DIMS has changed");
static_assert(ANEURALNETWORKS_GATHER == 51, "ANEURALNETWORKS_GATHER has changed");
static_assert(ANEURALNETWORKS_GENERATE_PROPOSALS == 52,
              "ANEURALNETWORKS_GENERATE_PROPOSALS has changed");
static_assert(ANEURALNETWORKS_GREATER == 53, "ANEURALNETWORKS_GREATER has changed");
static_assert(ANEURALNETWORKS_GREATER_EQUAL == 54, "ANEURALNETWORKS_GREATER_EQUAL has changed");
static_assert(ANEURALNETWORKS_GROUPED_CONV_2D == 55, "ANEURALNETWORKS_GROUPED_CONV_2D has changed");
static_assert(ANEURALNETWORKS_HEATMAP_MAX_KEYPOINT == 56,
              "ANEURALNETWORKS_HEATMAP_MAX_KEYPOINT has changed");
static_assert(ANEURALNETWORKS_INSTANCE_NORMALIZATION == 57,
              "ANEURALNETWORKS_INSTANCE_NORMALIZATION has changed");
static_assert(ANEURALNETWORKS_LESS == 58, "ANEURALNETWORKS_LESS has changed");
static_assert(ANEURALNETWORKS_LESS_EQUAL == 59, "ANEURALNETWORKS_LESS_EQUAL has changed");
static_assert(ANEURALNETWORKS_LOG == 60, "ANEURALNETWORKS_LOG has changed");
static_assert(ANEURALNETWORKS_LOGICAL_AND == 61, "ANEURALNETWORKS_LOGICAL_AND has changed");
static_assert(ANEURALNETWORKS_LOGICAL_NOT == 62, "ANEURALNETWORKS_LOGICAL_NOT has changed");
static_assert(ANEURALNETWORKS_LOGICAL_OR == 63, "ANEURALNETWORKS_LOGICAL_OR has changed");
static_assert(ANEURALNETWORKS_LOG_SOFTMAX == 64, "ANEURALNETWORKS_LOG_SOFTMAX has changed");
static_assert(ANEURALNETWORKS_MAXIMUM == 65, "ANEURALNETWORKS_MAXIMUM has changed");
static_assert(ANEURALNETWORKS_MINIMUM == 66, "ANEURALNETWORKS_MINIMUM has changed");
static_assert(ANEURALNETWORKS_NEG == 67, "ANEURALNETWORKS_NEG has changed");
static_assert(ANEURALNETWORKS_NOT_EQUAL == 68, "ANEURALNETWORKS_NOT_EQUAL has changed");
static_assert(ANEURALNETWORKS_PAD_V2 == 69, "ANEURALNETWORKS_PAD_V2 has changed");
static_assert(ANEURALNETWORKS_POW == 70, "ANEURALNETWORKS_POW has changed");
static_assert(ANEURALNETWORKS_PRELU == 71, "ANEURALNETWORKS_PRELU has changed");
static_assert(ANEURALNETWORKS_QUANTIZE == 72, "ANEURALNETWORKS_QUANTIZE has changed");
static_assert(ANEURALNETWORKS_QUANTIZED_16BIT_LSTM == 73,
              "ANEURALNETWORKS_QUANTIZED_16BIT_LSTM has changed");
static_assert(ANEURALNETWORKS_RANDOM_MULTINOMIAL == 74,
              "ANEURALNETWORKS_RANDOM_MULTINOMIAL has changed");
static_assert(ANEURALNETWORKS_REDUCE_ALL == 75, "ANEURALNETWORKS_REDUCE_ALL has changed");
static_assert(ANEURALNETWORKS_REDUCE_ANY == 76, "ANEURALNETWORKS_REDUCE_ANY has changed");
static_assert(ANEURALNETWORKS_REDUCE_MAX == 77, "ANEURALNETWORKS_REDUCE_MAX has changed");
static_assert(ANEURALNETWORKS_REDUCE_MIN == 78, "ANEURALNETWORKS_REDUCE_MIN has changed");
static_assert(ANEURALNETWORKS_REDUCE_PROD == 79, "ANEURALNETWORKS_REDUCE_PROD has changed");
static_assert(ANEURALNETWORKS_REDUCE_SUM == 80, "ANEURALNETWORKS_REDUCE_SUM has changed");
static_assert(ANEURALNETWORKS_ROI_ALIGN == 81, "ANEURALNETWORKS_ROI_ALIGN has changed");
static_assert(ANEURALNETWORKS_ROI_POOLING == 82, "ANEURALNETWORKS_ROI_POOLING has changed");
static_assert(ANEURALNETWORKS_RSQRT == 83, "ANEURALNETWORKS_RSQRT has changed");
static_assert(ANEURALNETWORKS_SELECT == 84, "ANEURALNETWORKS_SELECT has changed");
static_assert(ANEURALNETWORKS_SIN == 85, "ANEURALNETWORKS_SIN has changed");
static_assert(ANEURALNETWORKS_SLICE == 86, "ANEURALNETWORKS_SLICE has changed");
static_assert(ANEURALNETWORKS_SPLIT == 87, "ANEURALNETWORKS_SPLIT has changed");
static_assert(ANEURALNETWORKS_SQRT == 88, "ANEURALNETWORKS_SQRT has changed");
static_assert(ANEURALNETWORKS_TILE == 89, "ANEURALNETWORKS_TILE has changed");
static_assert(ANEURALNETWORKS_TOPK_V2 == 90, "ANEURALNETWORKS_TOPK_V2 has changed");
static_assert(ANEURALNETWORKS_TRANSPOSE_CONV_2D == 91,
              "ANEURALNETWORKS_TRANSPOSE_CONV_2D has changed");
static_assert(ANEURALNETWORKS_UNIDIRECTIONAL_SEQUENCE_LSTM == 92,
              "ANEURALNETWORKS_UNIDIRECTIONAL_SEQUENCE_LSTM has changed");
static_assert(ANEURALNETWORKS_UNIDIRECTIONAL_SEQUENCE_RNN == 93,
              "ANEURALNETWORKS_UNIDIRECTIONAL_SEQUENCE_RNN has changed");
static_assert(ANEURALNETWORKS_RESIZE_NEAREST_NEIGHBOR == 94,
              "ANEURALNETWORKS_RESIZE_NEAREST_NEIGHBOR has changed");
static_assert(ANEURALNETWORKS_QUANTIZED_LSTM == 95, "ANEURALNETWORKS_QUANTIZED_LSTM has changed");
static_assert(ANEURALNETWORKS_IF == 96, "ANEURALNETWORKS_IF has changed");
static_assert(ANEURALNETWORKS_WHILE == 97, "ANEURALNETWORKS_WHILE has changed");
static_assert(ANEURALNETWORKS_ELU == 98, "ANEURALNETWORKS_ELU has changed");
static_assert(ANEURALNETWORKS_HARD_SWISH == 99, "ANEURALNETWORKS_HARD_SWISH has changed");
static_assert(ANEURALNETWORKS_FILL == 100, "ANEURALNETWORKS_FILL has changed");
static_assert(ANEURALNETWORKS_RANK == 101, "ANEURALNETWORKS_RANK has changed");
static_assert(ANEURALNETWORKS_BATCH_MATMUL == 102, "ANEURALNETWORKS_BATCH_MATMUL has changed");
static_assert(ANEURALNETWORKS_PACK == 103, "ANEURALNETWORKS_PACK has changed");
static_assert(ANEURALNETWORKS_MIRROR_PAD == 104, "ANEURALNETWORKS_MIRROR_PAD has changed");
static_assert(ANEURALNETWORKS_REVERSE == 105, "ANEURALNETWORKS_REVERSE has changed");
static_assert(ANEURALNETWORKS_OEM_OPERATION == 10000, "ANEURALNETWORKS_OEM_OPERATION has changed");

static_assert(ANEURALNETWORKS_FUSED_NONE == 0, "ANEURALNETWORKS_FUSED_NONE has changed");
static_assert(ANEURALNETWORKS_FUSED_RELU == 1, "ANEURALNETWORKS_FUSED_RELU has changed");
static_assert(ANEURALNETWORKS_FUSED_RELU1 == 2, "ANEURALNETWORKS_FUSED_RELU1 has changed");
static_assert(ANEURALNETWORKS_FUSED_RELU6 == 3, "ANEURALNETWORKS_FUSED_RELU6 has changed");

static_assert(ANEURALNETWORKS_PREFER_LOW_POWER == 0,
              "ANEURALNETWORKS_PREFER_LOW_POWER has changed");
static_assert(ANEURALNETWORKS_PREFER_FAST_SINGLE_ANSWER == 1,
              "ANEURALNETWORKS_PREFER_FAST_SINGLE_ANSWER has changed");
static_assert(ANEURALNETWORKS_PREFER_SUSTAINED_SPEED == 2,
              "ANEURALNETWORKS_PREFER_SUSTAINED_SPEED has changed");

static_assert(ANEURALNETWORKS_NO_ERROR == 0, "ANEURALNETWORKS_NO_ERROR has changed");
static_assert(ANEURALNETWORKS_OUT_OF_MEMORY == 1, "ANEURALNETWORKS_OUT_OF_MEMORY has changed");
static_assert(ANEURALNETWORKS_INCOMPLETE == 2, "ANEURALNETWORKS_INCOMPLETE has changed");
static_assert(ANEURALNETWORKS_UNEXPECTED_NULL == 3, "ANEURALNETWORKS_UNEXPECTED_NULL has changed");
static_assert(ANEURALNETWORKS_BAD_DATA == 4, "ANEURALNETWORKS_BAD_DATA has changed");
static_assert(ANEURALNETWORKS_OP_FAILED == 5, "ANEURALNETWORKS_OP_FAILED has changed");
static_assert(ANEURALNETWORKS_BAD_STATE == 6, "ANEURALNETWORKS_BAD_STATE has changed");
static_assert(ANEURALNETWORKS_UNMAPPABLE == 7, "ANEURALNETWORKS_UNMAPPABLE has changed");
static_assert(ANEURALNETWORKS_OUTPUT_INSUFFICIENT_SIZE == 8,
              "ANEURALNETWORKS_OUTPUT_INSUFFICIENT_SIZE has changed");
static_assert(ANEURALNETWORKS_UNAVAILABLE_DEVICE == 9,
              "ANEURALNETWORKS_UNAVAILABLE_DEVICE has changed");
static_assert(ANEURALNETWORKS_MISSED_DEADLINE_TRANSIENT == 10,
              "ANEURALNETWORKS_MISSED_DEADLINE_TRANSIENT has changed");
static_assert(ANEURALNETWORKS_MISSED_DEADLINE_PERSISTENT == 11,
              "ANEURALNETWORKS_MISSED_DEADLINE_PERSISTENT has changed");
static_assert(ANEURALNETWORKS_RESOURCE_EXHAUSTED_TRANSIENT == 12,
              "ANEURALNETWORKS_RESOURCE_EXHAUSTED_TRANSIENT has changed");
static_assert(ANEURALNETWORKS_RESOURCE_EXHAUSTED_PERSISTENT == 13,
              "ANEURALNETWORKS_RESOURCE_EXHAUSTED_PERSISTENT has changed");
static_assert(ANEURALNETWORKS_DEAD_OBJECT == 14, "ANEURALNETWORKS_DEAD_OBJECT has changed");

static_assert(ANEURALNETWORKS_MAX_SIZE_OF_IMMEDIATELY_COPIED_VALUES == 128,
              "ANEURALNETWORKS_MAX_SIZE_OF_IMMEDIATELY_COPIED_VALUES has changed");

static_assert(ANEURALNETWORKS_DEVICE_UNKNOWN == 0, "ANEURALNETWORKS_DEVICE_UNKNOWN has changed");
static_assert(ANEURALNETWORKS_DEVICE_OTHER == 1, "ANEURALNETWORKS_DEVICE_OTHER has changed");
static_assert(ANEURALNETWORKS_DEVICE_CPU == 2, "ANEURALNETWORKS_DEVICE_CPU has changed");
static_assert(ANEURALNETWORKS_DEVICE_GPU == 3, "ANEURALNETWORKS_DEVICE_GPU has changed");
static_assert(ANEURALNETWORKS_DEVICE_ACCELERATOR == 4,
              "ANEURALNETWORKS_DEVICE_ACCELERATOR has changed");

static_assert(ANEURALNETWORKS_DURATION_ON_HARDWARE == 0,
              "ANEURALNETWORKS_DURATION_ON_HARDWARE has changed");
static_assert(ANEURALNETWORKS_DURATION_IN_DRIVER == 1,
              "ANEURALNETWORKS_DURATION_IN_DRIVER has changed");
static_assert(ANEURALNETWORKS_FENCED_DURATION_ON_HARDWARE == 2,
              "ANEURALNETWORKS_FENCED_DURATION_ON_HARDWARE has changed");
static_assert(ANEURALNETWORKS_FENCED_DURATION_IN_DRIVER == 3,
              "ANEURALNETWORKS_FENCED_DURATION_IN_DRIVER has changed");

// Make sure that the constants are compatible with the values defined in
// hardware/interfaces/neuralnetworks/1.0/types.hal.
static_assert(static_cast<int32_t>(OperandType::OEM) == ANEURALNETWORKS_OEM_SCALAR,
              "OEM != ANEURALNETWORKS_OEM");
static_assert(static_cast<int32_t>(OperandType::FLOAT32) == ANEURALNETWORKS_FLOAT32,
              "FLOAT32 != ANEURALNETWORKS_FLOAT32");
static_assert(static_cast<int32_t>(OperandType::INT32) == ANEURALNETWORKS_INT32,
              "INT32 != ANEURALNETWORKS_INT32");
static_assert(static_cast<int32_t>(OperandType::UINT32) == ANEURALNETWORKS_UINT32,
              "UINT32 != ANEURALNETWORKS_UINT32");
static_assert(static_cast<int32_t>(OperandType::TENSOR_OEM_BYTE) == ANEURALNETWORKS_TENSOR_OEM_BYTE,
              "TENSOR_OEM_BYTE != ANEURALNETWORKS_TENSOR_OEM_BYTE");
static_assert(static_cast<int32_t>(OperandType::TENSOR_FLOAT32) == ANEURALNETWORKS_TENSOR_FLOAT32,
              "TENSOR_FLOAT32 != ANEURALNETWORKS_TENSOR_FLOAT32");
static_assert(static_cast<int32_t>(OperandType::TENSOR_QUANT8_ASYMM) ==
                      ANEURALNETWORKS_TENSOR_QUANT8_ASYMM,
              "TENSOR_QUANT8_ASYMM != ANEURALNETWORKS_TENSOR_QUANT8_ASYMM");

static_assert(static_cast<int32_t>(OperationType::ADD) == ANEURALNETWORKS_ADD,
              "OperationType::ADD != ANEURALNETWORKS_ADD");
static_assert(static_cast<int32_t>(OperationType::AVERAGE_POOL_2D) ==
                      ANEURALNETWORKS_AVERAGE_POOL_2D,
              "OperationType::AVERAGE_POOL_2D != ANEURALNETWORKS_AVERAGE_POOL_2D");
static_assert(static_cast<int32_t>(OperationType::CONV_2D) == ANEURALNETWORKS_CONV_2D,
              "OperationType::CONV_2D != ANEURALNETWORKS_CONV_2D");
static_assert(static_cast<int32_t>(OperationType::DEPTHWISE_CONV_2D) ==
                      ANEURALNETWORKS_DEPTHWISE_CONV_2D,
              "OperationType::DEPTHWISE_CONV_2D != ANEURALNETWORKS_DEPTHWISE_CONV_2D");
static_assert(static_cast<int32_t>(OperationType::DEPTH_TO_SPACE) == ANEURALNETWORKS_DEPTH_TO_SPACE,
              "OperationType::DEPTH_TO_SPACE != ANEURALNETWORKS_DEPTH_TO_SPACE");
static_assert(static_cast<int32_t>(OperationType::DEQUANTIZE) == ANEURALNETWORKS_DEQUANTIZE,
              "OperationType::DEQUANTIZE != ANEURALNETWORKS_DEQUANTIZE");
static_assert(static_cast<int32_t>(OperationType::EMBEDDING_LOOKUP) ==
                      ANEURALNETWORKS_EMBEDDING_LOOKUP,
              "OperationType::EMBEDDING_LOOKUP != ANEURALNETWORKS_EMBEDDING_LOOKUP");
static_assert(static_cast<int32_t>(OperationType::FLOOR) == ANEURALNETWORKS_FLOOR,
              "OperationType::FLOOR != ANEURALNETWORKS_FLOOR");
static_assert(static_cast<int32_t>(OperationType::FULLY_CONNECTED) ==
                      ANEURALNETWORKS_FULLY_CONNECTED,
              "OperationType::FULLY_CONNECTED != ANEURALNETWORKS_FULLY_CONNECTED");
static_assert(static_cast<int32_t>(OperationType::HASHTABLE_LOOKUP) ==
                      ANEURALNETWORKS_HASHTABLE_LOOKUP,
              "OperationType::HASHTABLE_LOOKUP != ANEURALNETWORKS_HASHTABLE_LOOKUP");
static_assert(static_cast<int32_t>(OperationType::L2_NORMALIZATION) ==
                      ANEURALNETWORKS_L2_NORMALIZATION,
              "OperationType::L2_NORMALIZATION != ANEURALNETWORKS_L2_NORMALIZATION");
static_assert(static_cast<int32_t>(OperationType::L2_POOL_2D) == ANEURALNETWORKS_L2_POOL_2D,
              "OperationType::L2_POOL_2D != ANEURALNETWORKS_L2_POOL_2D");
static_assert(static_cast<int32_t>(OperationType::LOCAL_RESPONSE_NORMALIZATION) ==
                      ANEURALNETWORKS_LOCAL_RESPONSE_NORMALIZATION,
              "OperationType::LOCAL_RESPONSE_NORMALIZATION != "
              "ANEURALNETWORKS_LOCAL_RESPONSE_NORMALIZATION");
static_assert(static_cast<int32_t>(OperationType::LOGISTIC) == ANEURALNETWORKS_LOGISTIC,
              "OperationType::LOGISTIC != ANEURALNETWORKS_LOGISTIC");
static_assert(static_cast<int32_t>(OperationType::LSH_PROJECTION) == ANEURALNETWORKS_LSH_PROJECTION,
              "OperationType::LSH_PROJECTION != ANEURALNETWORKS_LSH_PROJECTION");
static_assert(static_cast<int32_t>(OperationType::LSTM) == ANEURALNETWORKS_LSTM,
              "OperationType::LSTM != ANEURALNETWORKS_LSTM");
static_assert(static_cast<int32_t>(OperationType::MAX_POOL_2D) == ANEURALNETWORKS_MAX_POOL_2D,
              "OperationType::MAX_POOL_2D != ANEURALNETWORKS_MAX_POOL_2D");
static_assert(static_cast<int32_t>(OperationType::MUL) == ANEURALNETWORKS_MUL,
              "OperationType::MUL != ANEURALNETWORKS_MUL");
static_assert(static_cast<int32_t>(OperationType::RELU) == ANEURALNETWORKS_RELU,
              "OperationType::RELU != ANEURALNETWORKS_RELU");
static_assert(static_cast<int32_t>(OperationType::RELU1) == ANEURALNETWORKS_RELU1,
              "OperationType::RELU1 != ANEURALNETWORKS_RELU1");
static_assert(static_cast<int32_t>(OperationType::RELU6) == ANEURALNETWORKS_RELU6,
              "OperationType::RELU6 != ANEURALNETWORKS_RELU6");
static_assert(static_cast<int32_t>(OperationType::RESHAPE) == ANEURALNETWORKS_RESHAPE,
              "OperationType::RESHAPE != ANEURALNETWORKS_RESHAPE");
static_assert(static_cast<int32_t>(OperationType::RESIZE_BILINEAR) ==
                      ANEURALNETWORKS_RESIZE_BILINEAR,
              "OperationType::RESIZE_BILINEAR != ANEURALNETWORKS_RESIZE_BILINEAR");
static_assert(static_cast<int32_t>(OperationType::RNN) == ANEURALNETWORKS_RNN,
              "OperationType::RNN != ANEURALNETWORKS_RNN");
static_assert(static_cast<int32_t>(OperationType::SOFTMAX) == ANEURALNETWORKS_SOFTMAX,
              "OperationType::SOFTMAX != ANEURALNETWORKS_SOFTMAX");
static_assert(static_cast<int32_t>(OperationType::SPACE_TO_DEPTH) == ANEURALNETWORKS_SPACE_TO_DEPTH,
              "OperationType::SPACE_TO_DEPTH != ANEURALNETWORKS_SPACE_TO_DEPTH");
static_assert(static_cast<int32_t>(OperationType::SVDF) == ANEURALNETWORKS_SVDF,
              "OperationType::SVDF != ANEURALNETWORKS_SVDF");
static_assert(static_cast<int32_t>(OperationType::TANH) == ANEURALNETWORKS_TANH,
              "OperationType::TANH != ANEURALNETWORKS_TANH");

static_assert(static_cast<int32_t>(FusedActivationFunc::NONE) == ANEURALNETWORKS_FUSED_NONE,
              "FusedActivationFunc::NONE != ANEURALNETWORKS_FUSED_NONE");
static_assert(static_cast<int32_t>(FusedActivationFunc::RELU) == ANEURALNETWORKS_FUSED_RELU,
              "FusedActivationFunc::RELU != ANEURALNETWORKS_FUSED_RELU");
static_assert(static_cast<int32_t>(FusedActivationFunc::RELU1) == ANEURALNETWORKS_FUSED_RELU1,
              "FusedActivationFunc::RELU1 != ANEURALNETWORKS_FUSED_RELU1");
static_assert(static_cast<int32_t>(FusedActivationFunc::RELU6) == ANEURALNETWORKS_FUSED_RELU6,
              "FusedActivationFunc::RELU6 != ANEURALNETWORKS_FUSED_RELU6");

// Make sure that the constants are compatible with the values defined in
// hardware/interfaces/neuralnetworks/1.1/types.hal.
static_assert(static_cast<int32_t>(OperationType::BATCH_TO_SPACE_ND) ==
                      ANEURALNETWORKS_BATCH_TO_SPACE_ND,
              "OperationType::BATCH_TO_SPACE_ND != ANEURALNETWORKS_BATCH_TO_SPACE_ND");
static_assert(static_cast<int32_t>(OperationType::DIV) == ANEURALNETWORKS_DIV,
              "OperationType::DIV != ANEURALNETWORKS_DIV");
static_assert(static_cast<int32_t>(OperationType::MEAN) == ANEURALNETWORKS_MEAN,
              "OperationType::MEAN != ANEURALNETWORKS_MEAN");
static_assert(static_cast<int32_t>(OperationType::PAD) == ANEURALNETWORKS_PAD,
              "OperationType::PAD != ANEURALNETWORKS_PAD");
static_assert(static_cast<int32_t>(OperationType::SPACE_TO_BATCH_ND) ==
                      ANEURALNETWORKS_SPACE_TO_BATCH_ND,
              "OperationType::SPACE_TO_BATCH_ND != ANEURALNETWORKS_SPACE_TO_BATCH_ND");
static_assert(static_cast<int32_t>(OperationType::SQUEEZE) == ANEURALNETWORKS_SQUEEZE,
              "OperationType::SQUEEZE != ANEURALNETWORKS_SQUEEZE");
static_assert(static_cast<int32_t>(OperationType::STRIDED_SLICE) == ANEURALNETWORKS_STRIDED_SLICE,
              "OperationType::STRIDED_SLICE != ANEURALNETWORKS_STRIDED_SLICE");
static_assert(static_cast<int32_t>(OperationType::SUB) == ANEURALNETWORKS_SUB,
              "OperationType::SUB != ANEURALNETWORKS_SUB");
static_assert(static_cast<int32_t>(OperationType::TRANSPOSE) == ANEURALNETWORKS_TRANSPOSE,
              "OperationType::TRANSPOSE != ANEURALNETWORKS_TRANSPOSE");

// Make sure that the constants are compatible with the values defined in
// hardware/interfaces/neuralnetworks/1.2/types.hal.
static_assert(static_cast<int32_t>(OperandType::BOOL) == ANEURALNETWORKS_BOOL,
              "BOOL != ANEURALNETWORKS_BOOL");
static_assert(static_cast<int32_t>(OperandType::TENSOR_QUANT16_SYMM) ==
                      ANEURALNETWORKS_TENSOR_QUANT16_SYMM,
              "TENSOR_QUANT16_SYMM != ANEURALNETWORKS_TENSOR_QUANT16_SYMM");
static_assert(static_cast<int32_t>(OperandType::TENSOR_FLOAT16) == ANEURALNETWORKS_TENSOR_FLOAT16,
              "TENSOR_FLOAT16 != ANEURALNETWORKS_TENSOR_FLOAT16");
static_assert(static_cast<int32_t>(OperandType::TENSOR_BOOL8) == ANEURALNETWORKS_TENSOR_BOOL8,
              "TENSOR_BOOL8 != ANEURALNETWORKS_TENSOR_BOOL8");
static_assert(static_cast<int32_t>(OperandType::FLOAT16) == ANEURALNETWORKS_FLOAT16,
              "FLOAT16 != ANEURALNETWORKS_FLOAT16");
static_assert(static_cast<int32_t>(OperandType::TENSOR_QUANT8_SYMM_PER_CHANNEL) ==
                      ANEURALNETWORKS_TENSOR_QUANT8_SYMM_PER_CHANNEL,
              "TENSOR_QUANT8_SYMM_PER_CHANNEL != ANEURALNETWORKS_TENSOR_QUANT8_SYMM_PER_CHANNEL");
static_assert(static_cast<int32_t>(OperandType::TENSOR_QUANT16_ASYMM) ==
                      ANEURALNETWORKS_TENSOR_QUANT16_ASYMM,
              "TENSOR_QUANT16_ASYMM != ANEURALNETWORKS_TENSOR_QUANT16_ASYMM");
static_assert(static_cast<int32_t>(OperandType::TENSOR_QUANT8_SYMM) ==
                      ANEURALNETWORKS_TENSOR_QUANT8_SYMM,
              "TENSOR_QUANT8_SYMM != ANEURALNETWORKS_TENSOR_QUANT8_SYMM");

static_assert(static_cast<int32_t>(OperationType::ABS) == ANEURALNETWORKS_ABS,
              "OperationType::ABS != ANEURALNETWORKS_ABS");
static_assert(static_cast<int32_t>(OperationType::ARGMAX) == ANEURALNETWORKS_ARGMAX,
              "OperationType::ARGMAX != ANEURALNETWORKS_ARGMAX");
static_assert(static_cast<int32_t>(OperationType::ARGMIN) == ANEURALNETWORKS_ARGMIN,
              "OperationType::ARGMIN != ANEURALNETWORKS_ARGMIN");
static_assert(static_cast<int32_t>(OperationType::AXIS_ALIGNED_BBOX_TRANSFORM) ==
                      ANEURALNETWORKS_AXIS_ALIGNED_BBOX_TRANSFORM,
              "OperationType::AXIS_ALIGNED_BBOX_TRANSFORM != "
              "ANEURALNETWORKS_AXIS_ALIGNED_BBOX_TRANSFORM");
static_assert(static_cast<int32_t>(OperationType::BIDIRECTIONAL_SEQUENCE_LSTM) ==
                      ANEURALNETWORKS_BIDIRECTIONAL_SEQUENCE_LSTM,
              "OperationType::BIDIRECTIONAL_SEQUENCE_LSTM != "
              "ANEURALNETWORKS_BIDIRECTIONAL_SEQUENCE_LSTM");
static_assert(
        static_cast<int32_t>(OperationType::BIDIRECTIONAL_SEQUENCE_RNN) ==
                ANEURALNETWORKS_BIDIRECTIONAL_SEQUENCE_RNN,
        "OperationType::BIDIRECTIONAL_SEQUENCE_RNN != ANEURALNETWORKS_BIDIRECTIONAL_SEQUENCE_RNN");
static_assert(static_cast<int32_t>(OperationType::BOX_WITH_NMS_LIMIT) ==
                      ANEURALNETWORKS_BOX_WITH_NMS_LIMIT,
              "OperationType::BOX_WITH_NMS_LIMIT != ANEURALNETWORKS_BOX_WITH_NMS_LIMIT");
static_assert(static_cast<int32_t>(OperationType::CAST) == ANEURALNETWORKS_CAST,
              "OperationType::CAST != ANEURALNETWORKS_CAST");
static_assert(static_cast<int32_t>(OperationType::CHANNEL_SHUFFLE) ==
                      ANEURALNETWORKS_CHANNEL_SHUFFLE,
              "OperationType::CHANNEL_SHUFFLE != ANEURALNETWORKS_CHANNEL_SHUFFLE");
static_assert(
        static_cast<int32_t>(OperationType::DETECTION_POSTPROCESSING) ==
                ANEURALNETWORKS_DETECTION_POSTPROCESSING,
        "OperationType::DETECTION_POSTPROCESSING != ANEURALNETWORKS_DETECTION_POSTPROCESSING");
static_assert(static_cast<int32_t>(OperationType::EQUAL) == ANEURALNETWORKS_EQUAL,
              "OperationType::EQUAL != ANEURALNETWORKS_EQUAL");
static_assert(static_cast<int32_t>(OperationType::EXP) == ANEURALNETWORKS_EXP,
              "OperationType::EXP != ANEURALNETWORKS_EXP");
static_assert(static_cast<int32_t>(OperationType::EXPAND_DIMS) == ANEURALNETWORKS_EXPAND_DIMS,
              "OperationType::EXPAND_DIMS != ANEURALNETWORKS_EXPAND_DIMS");
static_assert(static_cast<int32_t>(OperationType::GATHER) == ANEURALNETWORKS_GATHER,
              "OperationType::GATHER != ANEURALNETWORKS_GATHER");
static_assert(static_cast<int32_t>(OperationType::GENERATE_PROPOSALS) ==
                      ANEURALNETWORKS_GENERATE_PROPOSALS,
              "OperationType::GENERATE_PROPOSALS != ANEURALNETWORKS_GENERATE_PROPOSALS");
static_assert(static_cast<int32_t>(OperationType::GREATER) == ANEURALNETWORKS_GREATER,
              "OperationType::GREATER != ANEURALNETWORKS_GREATER");
static_assert(static_cast<int32_t>(OperationType::GREATER_EQUAL) == ANEURALNETWORKS_GREATER_EQUAL,
              "OperationType::GREATER_EQUAL != ANEURALNETWORKS_GREATER_EQUAL");
static_assert(static_cast<int32_t>(OperationType::GROUPED_CONV_2D) ==
                      ANEURALNETWORKS_GROUPED_CONV_2D,
              "OperationType::GROUPED_CONV_2D != ANEURALNETWORKS_GROUPED_CONV_2D");
static_assert(static_cast<int32_t>(OperationType::HEATMAP_MAX_KEYPOINT) ==
                      ANEURALNETWORKS_HEATMAP_MAX_KEYPOINT,
              "OperationType::HEATMAP_MAX_KEYPOINT != ANEURALNETWORKS_HEATMAP_MAX_KEYPOINT");
static_assert(static_cast<int32_t>(OperationType::INSTANCE_NORMALIZATION) ==
                      ANEURALNETWORKS_INSTANCE_NORMALIZATION,
              "OperationType::INSTANCE_NORMALIZATION != ANEURALNETWORKS_INSTANCE_NORMALIZATION");
static_assert(static_cast<int32_t>(OperationType::LESS) == ANEURALNETWORKS_LESS,
              "OperationType::LESS != ANEURALNETWORKS_LESS");
static_assert(static_cast<int32_t>(OperationType::LESS_EQUAL) == ANEURALNETWORKS_LESS_EQUAL,
              "OperationType::LESS_EQUAL != ANEURALNETWORKS_LESS_EQUAL");
static_assert(static_cast<int32_t>(OperationType::LOG) == ANEURALNETWORKS_LOG,
              "OperationType::LOG != ANEURALNETWORKS_LOG");
static_assert(static_cast<int32_t>(OperationType::LOGICAL_AND) == ANEURALNETWORKS_LOGICAL_AND,
              "OperationType::LOGICAL_AND != ANEURALNETWORKS_LOGICAL_AND");
static_assert(static_cast<int32_t>(OperationType::LOGICAL_NOT) == ANEURALNETWORKS_LOGICAL_NOT,
              "OperationType::LOGICAL_NOT != ANEURALNETWORKS_LOGICAL_NOT");
static_assert(static_cast<int32_t>(OperationType::LOGICAL_OR) == ANEURALNETWORKS_LOGICAL_OR,
              "OperationType::LOGICAL_OR != ANEURALNETWORKS_LOGICAL_OR");
static_assert(static_cast<int32_t>(OperationType::LOG_SOFTMAX) == ANEURALNETWORKS_LOG_SOFTMAX,
              "OperationType::LOG_SOFTMAX != ANEURALNETWORKS_LOG_SOFTMAX");
static_assert(static_cast<int32_t>(OperationType::MAXIMUM) == ANEURALNETWORKS_MAXIMUM,
              "OperationType::MAXIMUM != ANEURALNETWORKS_MAXIMUM");
static_assert(static_cast<int32_t>(OperationType::MINIMUM) == ANEURALNETWORKS_MINIMUM,
              "OperationType::MINIMUM != ANEURALNETWORKS_MINIMUM");
static_assert(static_cast<int32_t>(OperationType::NEG) == ANEURALNETWORKS_NEG,
              "OperationType::NEG != ANEURALNETWORKS_NEG");
static_assert(static_cast<int32_t>(OperationType::NOT_EQUAL) == ANEURALNETWORKS_NOT_EQUAL,
              "OperationType::NOT_EQUAL != ANEURALNETWORKS_NOT_EQUAL");
static_assert(static_cast<int32_t>(OperationType::PAD_V2) == ANEURALNETWORKS_PAD_V2,
              "OperationType::PAD_V2 != ANEURALNETWORKS_PAD_V2");
static_assert(static_cast<int32_t>(OperationType::POW) == ANEURALNETWORKS_POW,
              "OperationType::POW != ANEURALNETWORKS_POW");
static_assert(static_cast<int32_t>(OperationType::PRELU) == ANEURALNETWORKS_PRELU,
              "OperationType::PRELU != ANEURALNETWORKS_PRELU");
static_assert(static_cast<int32_t>(OperationType::QUANTIZE) == ANEURALNETWORKS_QUANTIZE,
              "OperationType::QUANTIZE != ANEURALNETWORKS_QUANTIZE");
static_assert(static_cast<int32_t>(OperationType::QUANTIZED_16BIT_LSTM) ==
                      ANEURALNETWORKS_QUANTIZED_16BIT_LSTM,
              "OperationType::QUANTIZED_16BIT_LSTM != ANEURALNETWORKS_QUANTIZED_16BIT_LSTM");
static_assert(static_cast<int32_t>(OperationType::RANDOM_MULTINOMIAL) ==
                      ANEURALNETWORKS_RANDOM_MULTINOMIAL,
              "OperationType::RANDOM_MULTINOMIAL != ANEURALNETWORKS_RANDOM_MULTINOMIAL");
static_assert(static_cast<int32_t>(OperationType::REDUCE_ALL) == ANEURALNETWORKS_REDUCE_ALL,
              "OperationType::REDUCE_ALL != ANEURALNETWORKS_REDUCE_ALL");
static_assert(static_cast<int32_t>(OperationType::REDUCE_ANY) == ANEURALNETWORKS_REDUCE_ANY,
              "OperationType::REDUCE_ANY != ANEURALNETWORKS_REDUCE_ANY");
static_assert(static_cast<int32_t>(OperationType::REDUCE_MAX) == ANEURALNETWORKS_REDUCE_MAX,
              "OperationType::REDUCE_MAX != ANEURALNETWORKS_REDUCE_MAX");
static_assert(static_cast<int32_t>(OperationType::REDUCE_MIN) == ANEURALNETWORKS_REDUCE_MIN,
              "OperationType::REDUCE_MIN != ANEURALNETWORKS_REDUCE_MIN");
static_assert(static_cast<int32_t>(OperationType::REDUCE_PROD) == ANEURALNETWORKS_REDUCE_PROD,
              "OperationType::REDUCE_PROD != ANEURALNETWORKS_REDUCE_PROD");
static_assert(static_cast<int32_t>(OperationType::REDUCE_SUM) == ANEURALNETWORKS_REDUCE_SUM,
              "OperationType::REDUCE_SUM != ANEURALNETWORKS_REDUCE_SUM");
static_assert(static_cast<int32_t>(OperationType::ROI_ALIGN) == ANEURALNETWORKS_ROI_ALIGN,
              "OperationType::ROI_ALIGN != ANEURALNETWORKS_ROI_ALIGN");
static_assert(static_cast<int32_t>(OperationType::ROI_POOLING) == ANEURALNETWORKS_ROI_POOLING,
              "OperationType::ROI_POOLING != ANEURALNETWORKS_ROI_POOLING");
static_assert(static_cast<int32_t>(OperationType::RSQRT) == ANEURALNETWORKS_RSQRT,
              "OperationType::RSQRT != ANEURALNETWORKS_RSQRT");
static_assert(static_cast<int32_t>(OperationType::SELECT) == ANEURALNETWORKS_SELECT,
              "OperationType::SELECT != ANEURALNETWORKS_SELECT");
static_assert(static_cast<int32_t>(OperationType::SIN) == ANEURALNETWORKS_SIN,
              "OperationType::SIN != ANEURALNETWORKS_SIN");
static_assert(static_cast<int32_t>(OperationType::SLICE) == ANEURALNETWORKS_SLICE,
              "OperationType::SLICE != ANEURALNETWORKS_SLICE");
static_assert(static_cast<int32_t>(OperationType::SPLIT) == ANEURALNETWORKS_SPLIT,
              "OperationType::SPLIT != ANEURALNETWORKS_SPLIT");
static_assert(static_cast<int32_t>(OperationType::SQRT) == ANEURALNETWORKS_SQRT,
              "OperationType::SQRT != ANEURALNETWORKS_SQRT");
static_assert(static_cast<int32_t>(OperationType::TILE) == ANEURALNETWORKS_TILE,
              "OperationType::TILE != ANEURALNETWORKS_TILE");
static_assert(static_cast<int32_t>(OperationType::TOPK_V2) == ANEURALNETWORKS_TOPK_V2,
              "OperationType::TOPK_V2 != ANEURALNETWORKS_TOPK_V2");
static_assert(static_cast<int32_t>(OperationType::TRANSPOSE_CONV_2D) ==
                      ANEURALNETWORKS_TRANSPOSE_CONV_2D,
              "OperationType::TRANSPOSE_CONV_2D != ANEURALNETWORKS_TRANSPOSE_CONV_2D");
static_assert(static_cast<int32_t>(OperationType::UNIDIRECTIONAL_SEQUENCE_LSTM) ==
                      ANEURALNETWORKS_UNIDIRECTIONAL_SEQUENCE_LSTM,
              "OperationType::UNIDIRECTIONAL_SEQUENCE_LSTM != "
              "ANEURALNETWORKS_UNIDIRECTIONAL_SEQUENCE_LSTM");
static_assert(static_cast<int32_t>(OperationType::UNIDIRECTIONAL_SEQUENCE_RNN) ==
                      ANEURALNETWORKS_UNIDIRECTIONAL_SEQUENCE_RNN,
              "OperationType::UNIDIRECTIONAL_SEQUENCE_RNN != "
              "ANEURALNETWORKS_UNIDIRECTIONAL_SEQUENCE_RNN");
static_assert(static_cast<int32_t>(OperationType::RESIZE_NEAREST_NEIGHBOR) ==
                      ANEURALNETWORKS_RESIZE_NEAREST_NEIGHBOR,
              "OperationType::RESIZE_NEAREST_NEIGHBOR != ANEURALNETWORKS_RESIZE_NEAREST_NEIGHBOR");
static_assert(static_cast<int32_t>(OperationType::QUANTIZED_LSTM) == ANEURALNETWORKS_QUANTIZED_LSTM,
              "OperationType::QUANTIZED_LSTM != ANEURALNETWORKS_QUANTIZED_LSTM");
static_assert(static_cast<int32_t>(OperationType::IF) == ANEURALNETWORKS_IF,
              "OperationType::IF != ANEURALNETWORKS_IF");
static_assert(static_cast<int32_t>(OperationType::WHILE) == ANEURALNETWORKS_WHILE,
              "OperationType::WHILE != ANEURALNETWORKS_WHILE");
static_assert(static_cast<int32_t>(OperationType::ELU) == ANEURALNETWORKS_ELU,
              "OperationType::ELU != ANEURALNETWORKS_ELU");
static_assert(static_cast<int32_t>(OperationType::HARD_SWISH) == ANEURALNETWORKS_HARD_SWISH,
              "OperationType::HARD_SWISH != ANEURALNETWORKS_HARD_SWISH");
static_assert(static_cast<int32_t>(OperationType::FILL) == ANEURALNETWORKS_FILL,
              "OperationType::FILL != ANEURALNETWORKS_FILL");
static_assert(static_cast<int32_t>(OperationType::RANK) == ANEURALNETWORKS_RANK,
              "OperationType::RANK != ANEURALNETWORKS_RANK");
static_assert(static_cast<int32_t>(OperationType::BATCH_MATMUL) == ANEURALNETWORKS_BATCH_MATMUL,
              "OperationType::BATCH_MATMUL != ANEURALNETWORKS_BATCH_MATMUL");
static_assert(static_cast<int32_t>(OperationType::PACK) == ANEURALNETWORKS_PACK,
              "OperationType::PACK != ANEURALNETWORKS_PACK");
static_assert(static_cast<int32_t>(OperationType::MIRROR_PAD) == ANEURALNETWORKS_MIRROR_PAD,
              "OperationType::MIRROR_PAD != ANEURALNETWORKS_MIRROR_PAD");
static_assert(static_cast<int32_t>(OperationType::REVERSE) == ANEURALNETWORKS_REVERSE,
              "OperationType::REVERSE != ANEURALNETWORKS_REVERSE");

static_assert(static_cast<int32_t>(DeviceType::OTHER) == ANEURALNETWORKS_DEVICE_OTHER,
              "DeviceType::OTHER != ANEURALNETWORKS_DEVICE_OTHER");
static_assert(static_cast<int32_t>(DeviceType::CPU) == ANEURALNETWORKS_DEVICE_CPU,
              "DeviceType::CPU != ANEURALNETWORKS_DEVICE_CPU");
static_assert(static_cast<int32_t>(DeviceType::GPU) == ANEURALNETWORKS_DEVICE_GPU,
              "DeviceType::GPU != ANEURALNETWORKS_DEVICE_GPU");
static_assert(static_cast<int32_t>(DeviceType::ACCELERATOR) == ANEURALNETWORKS_DEVICE_ACCELERATOR,
              "DeviceType::ACCELERATOR != ANEURALNETWORKS_DEVICE_ACCELERATOR");

// Make sure that the constants are compatible with the values defined in
// hardware/interfaces/neuralnetworks/1.3/types.hal.
static_assert(android::nn::convertToCanonicalPriority(ANEURALNETWORKS_PRIORITY_LOW) ==
                      Priority::LOW,
              "ANEURALNETWORKS_PRIORITY_LOW does not map to Priority::LOW");
static_assert(android::nn::convertToCanonicalPriority(ANEURALNETWORKS_PRIORITY_MEDIUM) ==
                      Priority::MEDIUM,
              "ANEURALNETWORKS_PRIORITY_MEDIUM does not map to Priority::MEDIUM");
static_assert(android::nn::convertToCanonicalPriority(ANEURALNETWORKS_PRIORITY_HIGH) ==
                      Priority::HIGH,
              "ANEURALNETWORKS_PRIORITY_HIGH does not map to Priority::HIGH");

// Asserts for ANeuralNetworksOperandType memory layout
static_assert(offsetof(ANeuralNetworksOperandType, type) == 0,
              "ANeuralNetworksOperandType.type offset != 0");
static_assert(offsetof(ANeuralNetworksOperandType, dimensionCount) == 4,
              "ANeuralNetworksOperandType.dimensionCount offset != 4");
static_assert(offsetof(ANeuralNetworksOperandType, dimensions) == 8,
              "ANeuralNetworksOperandType.dimensions offset != 8");
static_assert(offsetof(ANeuralNetworksOperandType, scale) == 8 + sizeof(void*),
              "ANeuralNetworksOperandType.scale offset != 8 + sizeof(void*)");
static_assert(offsetof(ANeuralNetworksOperandType, zeroPoint) == 12 + sizeof(void*),
              "ANeuralNetworksOperandType.zeroPoint offset != 12 + sizeof(void*)");
static_assert(sizeof(ANeuralNetworksOperandType) == 16 + sizeof(void*),
              "ANeuralNetworksOperandType size changed");
static_assert(alignof(ANeuralNetworksOperandType) == alignof(void*),
              "ANeuralNetworksOperandType alignment changed");

// Asserts for ANeuralNetworksSymmPerChannelQuantParams memory layout
static_assert(offsetof(ANeuralNetworksSymmPerChannelQuantParams, channelDim) == 0,
              "ANeuralNetworksSymmPerChannelQuantParams.channelDim offset != 4 + sizeof(void*)");
static_assert(offsetof(ANeuralNetworksSymmPerChannelQuantParams, scaleCount) == 4,
              "ANeuralNetworksSymmPerChannelQuantParams.scaleCount offset != 0");
static_assert(offsetof(ANeuralNetworksSymmPerChannelQuantParams, scales) == 8,
              "ANeuralNetworksSymmPerChannelQuantParams.scales offset != 4");
static_assert(sizeof(ANeuralNetworksSymmPerChannelQuantParams) == 8 + sizeof(void*),
              "ANeuralNetworksSymmPerChannelQuantParams size != 8 + sizeof(void*)");
static_assert(alignof(ANeuralNetworksSymmPerChannelQuantParams) == alignof(void*),
              "ANeuralNetworksOperandType alignment changed");

// Asserts for compilation caching
static_assert(ANEURALNETWORKS_BYTE_SIZE_OF_CACHE_TOKEN == 32,
              "ANEURALNETWORKS_BYTE_SIZE_OF_CACHE_TOKEN has changed");
static_assert(ANEURALNETWORKS_BYTE_SIZE_OF_CACHE_TOKEN == kByteSizeOfCacheToken,
              "ANEURALNETWORKS_BYTE_SIZE_OF_CACHE_TOKEN != kByteSizeOfCacheToken");

// Asserts for compilation priority
static_assert(ANEURALNETWORKS_PRIORITY_LOW == 90, "ANEURALNETWORKS_PRIORITY_LOW has changed");
static_assert(ANEURALNETWORKS_PRIORITY_MEDIUM == 100,
              "ANEURALNETWORKS_PRIORITY_MEDIUM has changed");
static_assert(ANEURALNETWORKS_PRIORITY_HIGH == 110, "ANEURALNETWORKS_PRIORITY_HIGH has changed");
static_assert(ANEURALNETWORKS_PRIORITY_DEFAULT == ANEURALNETWORKS_PRIORITY_MEDIUM,
              "ANEURALNETWORKS_PRIORITY_DEFAULT has changed");

// Asserts for feature levels
static_assert(ANEURALNETWORKS_FEATURE_LEVEL_1 == 27, "ANEURALNETWORKS_FEATURE_LEVEL_1 has changed");
static_assert(ANEURALNETWORKS_FEATURE_LEVEL_2 == 28, "ANEURALNETWORKS_FEATURE_LEVEL_2 has changed");
static_assert(ANEURALNETWORKS_FEATURE_LEVEL_3 == 29, "ANEURALNETWORKS_FEATURE_LEVEL_3 has changed");
static_assert(ANEURALNETWORKS_FEATURE_LEVEL_4 == 30, "ANEURALNETWORKS_FEATURE_LEVEL_4 has changed");
static_assert(ANEURALNETWORKS_FEATURE_LEVEL_5 == 31, "ANEURALNETWORKS_FEATURE_LEVEL_5 has changed");
static_assert(ANEURALNETWORKS_FEATURE_LEVEL_6 == 1000006,
              "ANEURALNETWORKS_FEATURE_LEVEL_6 has changed");
static_assert(ANEURALNETWORKS_FEATURE_LEVEL_7 == 1000007,
              "ANEURALNETWORKS_FEATURE_LEVEL_7 has changed");
static_assert(ANEURALNETWORKS_FEATURE_LEVEL_8 == 1000008,
              "ANEURALNETWORKS_FEATURE_LEVEL_8 has changed");

int ANeuralNetworks_getDeviceCount(uint32_t* numDevices) {
    if (numDevices == nullptr) {
        LOG(ERROR) << "ANeuralNetworks_getDeviceCount passed a nullptr";
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    *numDevices = DeviceManager::get()->getDrivers().size();
    return ANEURALNETWORKS_NO_ERROR;
}

int ANeuralNetworks_getDevice(uint32_t devIndex, ANeuralNetworksDevice** device) {
    if (device == nullptr) {
        LOG(ERROR) << "ANeuralNetworks_getDevice passed a nullptr";
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    const std::vector<std::shared_ptr<Device>>& devices = DeviceManager::get()->getDrivers();
    if (devIndex >= devices.size()) {
        LOG(ERROR) << "ANeuralNetworks_getDevice passed an invalid device index";
        return ANEURALNETWORKS_BAD_DATA;
    }
    *device = reinterpret_cast<ANeuralNetworksDevice*>(devices.at(devIndex).get());
    return ANEURALNETWORKS_NO_ERROR;
}

int ANeuralNetworksDevice_getName(const ANeuralNetworksDevice* device, const char** name) {
    if (device == nullptr || name == nullptr) {
        LOG(ERROR) << "ANeuralNetworksDevice_getName passed a nullptr";
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    const Device* d = reinterpret_cast<const Device*>(device);
    *name = d->getName().c_str();
    return ANEURALNETWORKS_NO_ERROR;
}

int ANeuralNetworksDevice_getVersion(const ANeuralNetworksDevice* device, const char** version) {
    if (device == nullptr || version == nullptr) {
        LOG(ERROR) << "ANeuralNetworksDevice_getVersion passed a nullptr";
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    const Device* d = reinterpret_cast<const Device*>(device);
    *version = d->getVersionString().c_str();
    return ANEURALNETWORKS_NO_ERROR;
}

int ANeuralNetworksDevice_getType(const ANeuralNetworksDevice* device, int32_t* type) {
    if (device == nullptr || type == nullptr) {
        LOG(ERROR) << "ANeuralNetworksDevice_getType passed a nullptr";
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    const Device* d = reinterpret_cast<const Device*>(device);
    int32_t dType = d->getType();
    if (dType < 0) {
        return ANEURALNETWORKS_OP_FAILED;
    }
    *type = d->getType();
    return ANEURALNETWORKS_NO_ERROR;
}

#ifdef NN_DEBUGGABLE
static int64_t sRuntimeFeatureLevel = 0;
void forTest_setRuntimeFeatureLevel(int64_t level) {
    sRuntimeFeatureLevel = level;
}
#endif

// Since ANeuralNetworks_getRuntimeFeatureLevel is new in 31 while libneuralnetwork targets
// "min_sdk_version: 30", calling it should be properly guarded (e.g. __builtin_available).
// But calling it within the same compilation unit is perfectly fine. Guarding it doesn't
// make any sense and is simply wrong. (It's available on a system where __builtin_available(30)
// evaluates to false.)
// To make the compiler happy we introduce getRuntimeFeatureLevelImpl() and call it within the
// library.
static inline int64_t getRuntimeFeatureLevelImpl() {
#ifdef NN_DEBUGGABLE
    if (sRuntimeFeatureLevel) {
        return sRuntimeFeatureLevel;
    }
#endif
    return DeviceManager::get()->getRuntimeFeatureLevel();
}

int ANeuralNetworksDevice_getFeatureLevel(const ANeuralNetworksDevice* device,
                                          int64_t* featureLevel) {
    if (device == nullptr || featureLevel == nullptr) {
        LOG(ERROR) << "ANeuralNetworksDevice_getFeatureLevel passed a nullptr";
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    Device* d = reinterpret_cast<Device*>(const_cast<ANeuralNetworksDevice*>(device));
    int64_t dFeatureLevel = DeviceManager::versionToFeatureLevel(d->getFeatureLevel().level);
    if (dFeatureLevel < 0) {
        return ANEURALNETWORKS_BAD_STATE;
    }
    *featureLevel = std::min(getRuntimeFeatureLevelImpl(), dFeatureLevel);
    return ANEURALNETWORKS_NO_ERROR;
}

int ANeuralNetworksDevice_wait(const ANeuralNetworksDevice* device) {
    if (device == nullptr) {
        LOG(ERROR) << "ANeuralNetworksDevice_wait passed a nullptr";
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    const Device* d = reinterpret_cast<const Device*>(device);
    return d->wait();
}

int ANeuralNetworksModel_getSupportedOperationsForDevices(
        const ANeuralNetworksModel* model, const ANeuralNetworksDevice* const* devices,
        uint32_t numDevices, bool* supportedOps) {
    NNTRACE_RT(NNTRACE_PHASE_COMPILATION, "ANeuralNetworksModel_getSupportedOperationsForDevices");
    if (model == nullptr || devices == nullptr || supportedOps == nullptr) {
        LOG(ERROR) << "ANeuralNetworksModel_getSupportedOperationsForDevices passed a nullptr";
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    if (numDevices == 0) {
        LOG(ERROR) << "ANeuralNetworksModel_getSupportedOperationsForDevices passed an empty "
                      "device list";
        return ANEURALNETWORKS_BAD_DATA;
    }
    const FlatbufferModelBuilder* m = reinterpret_cast<const FlatbufferModelBuilder*>(model);
    if (!m->isFinished() || !m->isValid()) {
        LOG(ERROR) << "ANeuralNetworksModel_getSupportedOperationsForDevices passed an unfinished "
                      "or invalid Model";
        return ANEURALNETWORKS_BAD_STATE;
    }

    const Model canonicalModel = m->makeModel();
    const std::vector<uint32_t>& opMap = m->getSortedOperationMapping();
    // init the output array to false for all the operations.
    std::fill(supportedOps, supportedOps + opMap.size(), false);
    for (uint32_t i = 0; i < numDevices; i++) {
        if (devices[i] == nullptr) {
            LOG(ERROR) << "ANeuralNetworksModel_getSupportedOperationsForDevices passed a nullptr "
                          "as a device";
            return ANEURALNETWORKS_UNEXPECTED_NULL;
        }
        for (uint32_t j = i + 1; j < numDevices; j++) {
            if (devices[i] == devices[j]) {
                LOG(ERROR) << "ANeuralNetworksModel_getSupportedOperationsForDevices passed "
                              "duplicate devices";
                return ANEURALNETWORKS_BAD_DATA;
            }
        }

        Device* d = reinterpret_cast<Device*>(const_cast<ANeuralNetworksDevice*>(devices[i]));
        const MetaModel metaModel(canonicalModel, DeviceManager::get()->strictSlicing());
        const std::vector<bool> supportsByDevice = d->getSupportedOperations(metaModel);
        for (uint32_t j = 0; j < supportsByDevice.size(); j++) {
            uint32_t originalIdx = opMap[j];
            supportedOps[originalIdx] |= supportsByDevice[j];
        }
    }
    return ANEURALNETWORKS_NO_ERROR;
}

int ANeuralNetworksCompilation_createForDevices(ANeuralNetworksModel* /* model */,
                                                const ANeuralNetworksDevice* const* /* devices */,
                                                uint32_t /* numDevices */,
                                                ANeuralNetworksCompilation** /* compilation */) {
    NNTRACE_RT(NNTRACE_PHASE_COMPILATION, "ANeuralNetworksCompilation_createForDevices");
    // Not supported yet in NNAPI v2
    LOG(ERROR) << "ANeuralNetworksCompilation_createForDevices unimplemented in Neural Networks V2";
    return ANEURALNETWORKS_OP_FAILED;
}

struct ExecutionContext {
    // inputs are always copied before execution while outputs may be set by custom allocation
    std::vector<void*> outputs;
    std::vector<size_t> outputSizes;
    std::vector<bool> isOutputSpecifiedAtIndex;
    std::vector<const void*> inputs;
    std::vector<size_t> inputSizes;

    std::unique_ptr<tflite::Interpreter> interpreter;

    ExecutionContext(std::unique_ptr<tflite::Interpreter> interpreter)
        : outputs(interpreter->outputs().size()),
          outputSizes(interpreter->outputs().size()),
          isOutputSpecifiedAtIndex(interpreter->outputs().size(), false),
          inputs(interpreter->inputs().size()),
          inputSizes(interpreter->inputs().size()),
          interpreter(std::move(interpreter)) {}
};

int ANeuralNetworksExecution_compute(ANeuralNetworksExecution* execution) {
    NNTRACE_RT(NNTRACE_PHASE_EXECUTION, "ANeuralNetworksExecution_compute");
    if (!execution) {
        LOG(ERROR) << "ANeuralNetworksExecution_compute passed a nullptr";
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }

    auto context = reinterpret_cast<ExecutionContext*>(execution);
    if (std::any_of(context->isOutputSpecifiedAtIndex.begin(),
                    context->isOutputSpecifiedAtIndex.end(), [](bool isSet) { return !isSet; })) {
        LOG(ERROR) << "ANeuralNetworksExecution_compute not all output buffers are specified";
        return ANEURALNETWORKS_BAD_DATA;
    }

    auto result = context->interpreter->AllocateTensors();
    if (result != kTfLiteOk) {
        LOG(ERROR) << "ANeuralNetworksExecution_compute allocate tensors failed";
        return ANEURALNETWORKS_OP_FAILED;
    }

    for (uint32_t index = 0; index < context->interpreter->inputs().size(); index++) {
        const void* buffer = context->inputs[index];
        if (buffer == nullptr) {
            LOG(ERROR) << "ANeuralNetworksExecution_compute not all input buffers are specified";
            return ANEURALNETWORKS_BAD_DATA;
        }
        size_t length = context->inputSizes[index];
        std::memcpy(context->interpreter->input_tensor(index)->data.raw, buffer, length);
    }

    if (context->interpreter->Invoke() != kTfLiteOk) {
        return ANEURALNETWORKS_OP_FAILED;
    }

    for (uint32_t i = 0; i < context->interpreter->outputs().size(); i++) {
        if (context->outputs[i] == nullptr) {
            continue;
        }

        const size_t bufferSize = context->outputSizes[i];
        std::memcpy(context->outputs[i], context->interpreter->output_tensor(i)->data.raw,
                    bufferSize);
    }
    return ANEURALNETWORKS_NO_ERROR;
}

int ANeuralNetworksExecution_setMeasureTiming(ANeuralNetworksExecution* /* execution */,
                                              bool /* measure */) {
    NNTRACE_RT(NNTRACE_PHASE_EXECUTION, "ANeuralNetworksExecution_setMeasureTiming");
    // Not supported yet in NNAPI v2
    LOG(ERROR) << "ANeuralNetworksExecution_setMeasureTiming unimplemented in Neural Networks V2";
    return ANEURALNETWORKS_OP_FAILED;
}

int ANeuralNetworksExecution_getDuration(const ANeuralNetworksExecution* /* execution */,
                                         int32_t /* durationCode */, uint64_t* /* duration */) {
    NNTRACE_RT(NNTRACE_PHASE_EXECUTION, "ANeuralNetworksExecution_getDuration");
    // Not supported yet in NNAPI v2
    LOG(ERROR) << "ANeuralNetworksExecution_getDuration unimplemented in Neural Networks V2";
    return ANEURALNETWORKS_OP_FAILED;
}

int ANeuralNetworksBurst_create(ANeuralNetworksCompilation* compilation,
                                ANeuralNetworksBurst** burst) {
    NNTRACE_RT(NNTRACE_PHASE_PREPARATION, "ANeuralNetworksBurst_create");
    if (!compilation || !burst) {
        LOG(ERROR) << "ANeuralNetworksBurst_create passed a nullptr";
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }

    CompilationBuilder* c = reinterpret_cast<CompilationBuilder*>(compilation);
    BurstBuilder* b = nullptr;
    int result = c->createBurst(&b);
    *burst = reinterpret_cast<ANeuralNetworksBurst*>(b);
    return result;
}

void ANeuralNetworksBurst_free(ANeuralNetworksBurst* burst) {
    NNTRACE_RT(NNTRACE_PHASE_TERMINATION, "ANeuralNetworksBurst_free");
    // No validation.  Free of nullptr is valid.
    BurstBuilder* b = reinterpret_cast<BurstBuilder*>(burst);
    delete b;
}

int ANeuralNetworksExecution_burstCompute(ANeuralNetworksExecution* /* execution */,
                                          ANeuralNetworksBurst* /* burst */) {
    NNTRACE_RT(NNTRACE_PHASE_EXECUTION, "ANeuralNetworksExecution_burstCompute");
    // Not supported yet in NNAPI v2
    LOG(ERROR) << "ANeuralNetworksExecution_burstCompute unimplemented in Neural Networks V2";
    return ANEURALNETWORKS_OP_FAILED;
}

int ANeuralNetworksMemoryDesc_create(ANeuralNetworksMemoryDesc** desc) {
    NNTRACE_RT(NNTRACE_PHASE_COMPILATION, "ANeuralNetworksMemoryDesc_create");
    if (desc != nullptr) {
        *desc = nullptr;
    }
    if (!desc) {
        LOG(ERROR) << "ANeuralNetworksMemoryDesc_create passed a nullptr";
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    auto mb = std::make_unique<MemoryBuilder>();
    *desc = reinterpret_cast<ANeuralNetworksMemoryDesc*>(mb.release());
    return ANEURALNETWORKS_NO_ERROR;
}

void ANeuralNetworksMemoryDesc_free(ANeuralNetworksMemoryDesc* desc) {
    NNTRACE_RT(NNTRACE_PHASE_TERMINATION, "ANeuralNetworksMemoryDesc_free");
    // No validation.  Free of nullptr is valid.
    MemoryBuilder* mb = reinterpret_cast<MemoryBuilder*>(desc);
    delete mb;
}

int ANeuralNetworksMemoryDesc_addInputRole(ANeuralNetworksMemoryDesc* desc,
                                           const ANeuralNetworksCompilation* compilation,
                                           uint32_t index, float frequency) {
    NNTRACE_RT(NNTRACE_PHASE_COMPILATION, "ANeuralNetworksMemoryDesc_addInputRole");
    if (!desc || !compilation) {
        LOG(ERROR) << "ANeuralNetworksMemoryDesc_addInputRole passed a nullptr";
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    MemoryBuilder* mb = reinterpret_cast<MemoryBuilder*>(desc);
    const CompilationBuilder* c = reinterpret_cast<const CompilationBuilder*>(compilation);
    return mb->addRole(*c, IOType::INPUT, index, frequency);
}

int ANeuralNetworksMemoryDesc_addOutputRole(ANeuralNetworksMemoryDesc* desc,
                                            const ANeuralNetworksCompilation* compilation,
                                            uint32_t index, float frequency) {
    NNTRACE_RT(NNTRACE_PHASE_COMPILATION, "ANeuralNetworksMemoryDesc_addOutputRole");
    if (!desc || !compilation) {
        LOG(ERROR) << "ANeuralNetworksMemoryDesc_addOutputRole passed a nullptr";
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    MemoryBuilder* mb = reinterpret_cast<MemoryBuilder*>(desc);
    const CompilationBuilder* c = reinterpret_cast<const CompilationBuilder*>(compilation);
    return mb->addRole(*c, IOType::OUTPUT, index, frequency);
}

int ANeuralNetworksMemoryDesc_setDimensions(ANeuralNetworksMemoryDesc* desc, uint32_t rank,
                                            const uint32_t* dimensions) {
    NNTRACE_RT(NNTRACE_PHASE_COMPILATION, "ANeuralNetworksMemoryDesc_setDimensions");
    if (!desc || (!dimensions && rank > 0)) {
        LOG(ERROR) << "ANeuralNetworksMemoryDesc_setDimensions passed a nullptr";
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    const std::vector<uint32_t> dims(dimensions, dimensions + rank);
    MemoryBuilder* mb = reinterpret_cast<MemoryBuilder*>(desc);
    return mb->setDimensions(dims);
}

int ANeuralNetworksMemoryDesc_finish(ANeuralNetworksMemoryDesc* desc) {
    NNTRACE_RT(NNTRACE_PHASE_COMPILATION, "ANeuralNetworksMemoryDesc_finish");
    if (!desc) {
        LOG(ERROR) << "ANeuralNetworksMemoryDesc_finish passed a nullptr";
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    MemoryBuilder* mb = reinterpret_cast<MemoryBuilder*>(desc);
    return mb->finish();
}

int ANeuralNetworksMemory_createFromDesc(const ANeuralNetworksMemoryDesc* desc,
                                         ANeuralNetworksMemory** memory) {
    NNTRACE_RT(NNTRACE_PHASE_COMPILATION, "ANeuralNetworksMemory_createFromDesc");
    if (memory != nullptr) {
        *memory = nullptr;
    }
    if (!desc || !memory) {
        LOG(ERROR) << "ANeuralNetworksMemory_createFromDesc passed a nullptr";
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    const MemoryBuilder* mb = reinterpret_cast<const MemoryBuilder*>(desc);
    auto [n, m] = mb->allocate();
    if (n != ANEURALNETWORKS_NO_ERROR) {
        return n;
    }
    *memory = reinterpret_cast<ANeuralNetworksMemory*>(m.release());
    return ANEURALNETWORKS_NO_ERROR;
}

int ANeuralNetworksMemory_copy(const ANeuralNetworksMemory* src, const ANeuralNetworksMemory* dst) {
    NNTRACE_RT(NNTRACE_PHASE_EXECUTION, "ANeuralNetworksMemory_copy");
    if (!src || !dst) {
        LOG(ERROR) << "ANeuralNetworksMemory_copy passed a nullptr";
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    const RuntimeMemory* s = reinterpret_cast<const RuntimeMemory*>(src);
    const RuntimeMemory* d = reinterpret_cast<const RuntimeMemory*>(dst);
    return RuntimeMemory::copy(*s, *d);
}

int ANeuralNetworksMemory_createFromFd(size_t size, int prot, int fd, size_t offset,
                                       ANeuralNetworksMemory** memory) {
    NNTRACE_RT(NNTRACE_PHASE_PREPARATION, "ANeuralNetworksMemory_createFromFd");
    if (memory != nullptr) {
        *memory = nullptr;
    }
    if (!memory) {
        LOG(ERROR) << "ANeuralNetworksMemory_createFromFd passed a nullptr";
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    int n = ANEURALNETWORKS_NO_ERROR;
    std::unique_ptr<MemoryFd> m;
    std::tie(n, m) = MemoryFd::create(size, prot, fd, offset);
    if (n != ANEURALNETWORKS_NO_ERROR) {
        return n;
    }
    *memory = reinterpret_cast<ANeuralNetworksMemory*>(m.release());
    return ANEURALNETWORKS_NO_ERROR;
}

int ANeuralNetworksMemory_createFromAHardwareBuffer(const AHardwareBuffer* ahwb,
                                                    ANeuralNetworksMemory** memory) {
    NNTRACE_RT(NNTRACE_PHASE_PREPARATION, "ANeuralNetworksMemory_createFromAHardwareBuffer");
    if (memory != nullptr) {
        *memory = nullptr;
    }
    if (!ahwb || !memory) {
        LOG(ERROR) << "ANeuralNetworksMemory_createFromAHardwareBuffer passed a nullptr";
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    int n = ANEURALNETWORKS_NO_ERROR;
    std::unique_ptr<MemoryAHWB> m;
    std::tie(n, m) = MemoryAHWB::create(*ahwb);
    if (n != ANEURALNETWORKS_NO_ERROR) {
        return n;
    }
    *memory = reinterpret_cast<ANeuralNetworksMemory*>(m.release());
    return ANEURALNETWORKS_NO_ERROR;
}

void ANeuralNetworksMemory_free(ANeuralNetworksMemory* memory) {
    NNTRACE_RT(NNTRACE_PHASE_TERMINATION, "ANeuralNetworksMemory_free");
    // No validation.  Free of nullptr is valid.
    RuntimeMemory* m = reinterpret_cast<RuntimeMemory*>(memory);
    delete m;
}

int ANeuralNetworksModel_create(ANeuralNetworksModel** model) {
    NNTRACE_RT(NNTRACE_PHASE_PREPARATION, "ANeuralNetworksModel_create");
    initVLogMask();
    if (!model) {
        LOG(ERROR) << "ANeuralNetworksModel_create passed a nullptr";
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    FlatbufferModelBuilder* m = new (std::nothrow) FlatbufferModelBuilder();
    if (m == nullptr) {
        *model = nullptr;
        return ANEURALNETWORKS_OUT_OF_MEMORY;
    }
    *model = reinterpret_cast<ANeuralNetworksModel*>(m);
    return ANEURALNETWORKS_NO_ERROR;
}

void ANeuralNetworksModel_free(ANeuralNetworksModel* model) {
    NNTRACE_RT(NNTRACE_PHASE_TERMINATION, "ANeuralNetworksModel_free");
    // No validation.  Free of nullptr is valid.
    FlatbufferModelBuilder* m = reinterpret_cast<FlatbufferModelBuilder*>(model);
    delete m;
}

int ANeuralNetworksModel_finish(ANeuralNetworksModel* model) {
    NNTRACE_RT(NNTRACE_PHASE_PREPARATION, "ANeuralNetworksModel_finish");
    if (!model) {
        LOG(ERROR) << "ANeuralNetworksModel_finish passed a nullptr";
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    FlatbufferModelBuilder* m = reinterpret_cast<FlatbufferModelBuilder*>(model);
    return m->finish();
}

int ANeuralNetworksModel_addOperand(ANeuralNetworksModel* model,
                                    const ANeuralNetworksOperandType* type) {
    NNTRACE_RT(NNTRACE_PHASE_PREPARATION, "ANeuralNetworksModel_addOperand");
    if (!model || !type) {
        LOG(ERROR) << "ANeuralNetworksModel_addOperand passed a nullptr";
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    FlatbufferModelBuilder* m = reinterpret_cast<FlatbufferModelBuilder*>(model);
    return m->addOperand(*type);
}

int ANeuralNetworksModel_setOperandValue(ANeuralNetworksModel* model, int32_t index,
                                         const void* buffer, size_t length) {
    NNTRACE_RT(NNTRACE_PHASE_PREPARATION, "ANeuralNetworksModel_setOperandValue");
    if (!model || (!buffer && length != 0)) {
        LOG(ERROR) << "ANeuralNetworksModel_setOperandValue passed a nullptr";
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    FlatbufferModelBuilder* m = reinterpret_cast<FlatbufferModelBuilder*>(model);
    return m->setOperandValue(index, buffer, length);
}

int ANeuralNetworksModel_setOperandValueFromMemory(ANeuralNetworksModel* model, int32_t index,
                                                   const ANeuralNetworksMemory* memory,
                                                   size_t offset, size_t length) {
    NNTRACE_RT(NNTRACE_PHASE_PREPARATION, "ANeuralNetworksModel_setOperandValueFromMemory");
    if (!model || !memory) {
        LOG(ERROR) << "ANeuralNetworksModel_setOperandValue passed a nullptr";
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    const RuntimeMemory* mem = reinterpret_cast<const RuntimeMemory*>(memory);
    FlatbufferModelBuilder* m = reinterpret_cast<FlatbufferModelBuilder*>(model);
    return m->setOperandValueFromMemory(index, mem, offset, length);
}

int ANeuralNetworksModel_setOperandValueFromModel(ANeuralNetworksModel* model, int32_t index,
                                                  const ANeuralNetworksModel* value) {
    NNTRACE_RT(NNTRACE_PHASE_PREPARATION, "ANeuralNetworksModel_setOperandValueFromModel");
    if (!model || !value) {
        LOG(ERROR) << "ANeuralNetworksModel_setOperandValueFromModel passed a nullptr";
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    const FlatbufferModelBuilder* val = reinterpret_cast<const FlatbufferModelBuilder*>(value);
    FlatbufferModelBuilder* m = reinterpret_cast<FlatbufferModelBuilder*>(model);
    return m->setOperandValueFromModel(index, val);
}

int ANeuralNetworksModel_addOperation(ANeuralNetworksModel* model,
                                      ANeuralNetworksOperationType type, uint32_t inputCount,
                                      const uint32_t* inputs, uint32_t outputCount,
                                      const uint32_t* outputs) {
    NNTRACE_RT(NNTRACE_PHASE_PREPARATION, "ANeuralNetworksModel_addOperation");
    if (!model || !inputs || !outputs) {
        LOG(ERROR) << "ANeuralNetworksModel_addOperation passed a nullptr";
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    FlatbufferModelBuilder* m = reinterpret_cast<FlatbufferModelBuilder*>(model);
    return m->addOperation(type, inputCount, inputs, outputCount, outputs);
}

int ANeuralNetworksModel_setOperandSymmPerChannelQuantParams(
        ANeuralNetworksModel* model, int32_t index,
        const ANeuralNetworksSymmPerChannelQuantParams* channelQuant) {
    NNTRACE_RT(NNTRACE_PHASE_PREPARATION,
               "ANeuralNetworksModel_setOperandSymmPerChannelQuantParams");
    if (!model || !channelQuant) {
        LOG(ERROR) << "ANeuralNetworksModel_setOperandSymmPerChannelQuantParams passed a nullptr";
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    FlatbufferModelBuilder* m = reinterpret_cast<FlatbufferModelBuilder*>(model);
    return m->setOperandSymmPerChannelQuantParams(index, *channelQuant);
}

int ANeuralNetworksModel_identifyInputsAndOutputs(ANeuralNetworksModel* model, uint32_t inputCount,
                                                  const uint32_t* inputs, uint32_t outputCount,
                                                  const uint32_t* outputs) {
    NNTRACE_RT(NNTRACE_PHASE_PREPARATION, "ANeuralNetworksModel_identifyInputsAndOutputs");
    if (!model || !inputs || !outputs) {
        LOG(ERROR) << ("ANeuralNetworksModel_identifyInputsAndOutputs passed a nullptr");
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    FlatbufferModelBuilder* m = reinterpret_cast<FlatbufferModelBuilder*>(model);
    return m->identifyInputsAndOutputs(inputCount, inputs, outputCount, outputs);
}

int ANeuralNetworksModel_relaxComputationFloat32toFloat16(ANeuralNetworksModel* model, bool allow) {
    NNTRACE_RT(NNTRACE_PHASE_PREPARATION, "ANeuralNetworksModel_relaxComputationFloat32toFloat16");
    if (!model) {
        LOG(ERROR) << ("ANeuralNetworksModel_relaxComputationFloat32toFloat16 passed a nullptr");
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    FlatbufferModelBuilder* m = reinterpret_cast<FlatbufferModelBuilder*>(model);
    return m->relaxComputationFloat32toFloat16(allow);
}

struct CompilationContext {
    std::unique_ptr<tflite::FlatBufferModel> flatBufferModel;
    bool isFinished;

    CompilationContext(std::unique_ptr<tflite::FlatBufferModel> flatBufferModel)
        : flatBufferModel(std::move(flatBufferModel)), isFinished(false) {}
};

int ANeuralNetworksCompilation_create(ANeuralNetworksModel* model,
                                      ANeuralNetworksCompilation** compilation) {
    NNTRACE_RT(NNTRACE_PHASE_COMPILATION, "ANeuralNetworksCompilation_create");
    if (!model || !compilation) {
        LOG(ERROR) << "ANeuralNetworksCompilation_create passed a nullptr";
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }

    FlatbufferModelBuilder* m = reinterpret_cast<FlatbufferModelBuilder*>(model);

    auto tfliteModel = m->createTfliteModel();
    if (!tfliteModel.ok()) {
        LOG(ERROR) << "ANeuralNetworksCompilation_create error: " << tfliteModel.error();
        return ANEURALNETWORKS_OP_FAILED;
    }

    std::unique_ptr<tflite::FlatBufferModel> flatBufferModel =
            tflite::FlatBufferModel::BuildFromModel(tfliteModel.value());
    if (!flatBufferModel) {
        LOG(ERROR) << "ANeuralNetworksCompilation_create error: tflite::BuildFromModel error";
        return ANEURALNETWORKS_OP_FAILED;
    }

    std::unique_ptr<CompilationContext> context =
            std::make_unique<CompilationContext>(std::move(flatBufferModel));
    *compilation = reinterpret_cast<ANeuralNetworksCompilation*>(context.release());
    return ANEURALNETWORKS_NO_ERROR;
}

void ANeuralNetworksCompilation_free(ANeuralNetworksCompilation* compilation) {
    NNTRACE_RT(NNTRACE_PHASE_TERMINATION, "ANeuralNetworksCompilation_free");
    // No validation.  Free of nullptr is valid.
    auto c = reinterpret_cast<CompilationContext*>(compilation);
    delete c;
}

int ANeuralNetworksCompilation_setPreference(ANeuralNetworksCompilation* /* compilation */,
                                             int32_t /* preference */) {
    NNTRACE_RT(NNTRACE_PHASE_COMPILATION, "ANeuralNetworksCompilation_setPreference");
    // Not supported yet in NNAPI v2
    LOG(ERROR) << "ANeuralNetworksCompilation_setPreference unimplemented in Neural Networks V2";
    return ANEURALNETWORKS_OP_FAILED;
}

int ANeuralNetworksCompilation_setCaching(ANeuralNetworksCompilation* /* compilation */,
                                          const char* /* cacheDir */, const uint8_t* /* token */) {
    NNTRACE_RT(NNTRACE_PHASE_COMPILATION, "ANeuralNetworksCompilation_setCaching");
    // Not supported yet in NNAPI v2
    LOG(ERROR) << "ANeuralNetworksCompilation_setCaching unimplemented in Neural Networks V2";
    return ANEURALNETWORKS_OP_FAILED;
}

int ANeuralNetworksCompilation_finish(ANeuralNetworksCompilation* compilation) {
    NNTRACE_RT(NNTRACE_PHASE_COMPILATION, "ANeuralNetworksCompilation_finish");
    if (!compilation) {
        LOG(ERROR) << "ANeuralNetworksCompilation_finish passed a nullptr";
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }

    auto context = reinterpret_cast<CompilationContext*>(compilation);
    if (context->isFinished) {
        LOG(ERROR) << "ANeuralNetworksCompilation_finish has already been called";
        return ANEURALNETWORKS_BAD_STATE;
    }
    context->isFinished = true;

    return ANEURALNETWORKS_NO_ERROR;
}

int ANeuralNetworksCompilation_setPriority(ANeuralNetworksCompilation* /* compilation */,
                                           int /* priority */) {
    NNTRACE_RT(NNTRACE_PHASE_COMPILATION, "ANeuralNetworksCompilation_setPriority");
    // Not supported yet in NNAPI v2
    LOG(ERROR) << "ANeuralNetworksCompilation_setPriority unimplemented in Neural Networks V2";
    return ANEURALNETWORKS_OP_FAILED;
}

int ANeuralNetworksCompilation_setTimeout(ANeuralNetworksCompilation* /* compilation */,
                                          uint64_t /* duration */) {
    NNTRACE_RT(NNTRACE_PHASE_COMPILATION, "ANeuralNetworksCompilation_setTimeout");
    // Not supported yet in NNAPI v2
    LOG(ERROR) << "ANeuralNetworksCompilation_setTimeout unimplemented in Neural Networks V2";
    return ANEURALNETWORKS_OP_FAILED;
}

int ANeuralNetworksExecution_create(ANeuralNetworksCompilation* compilation,
                                    ANeuralNetworksExecution** execution) {
    NNTRACE_RT(NNTRACE_PHASE_EXECUTION, "ANeuralNetworksExecution_create");
    if (!compilation || !execution) {
        LOG(ERROR) << "ANeuralNetworksExecution_create passed a nullptr";
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    auto c = reinterpret_cast<CompilationContext*>(compilation);

    tflite::ops::builtin::BuiltinOpResolver resolver;
    std::unique_ptr<tflite::Interpreter> interpreter;
    auto status = tflite::InterpreterBuilder(*c->flatBufferModel, resolver)(&interpreter);
    if (status != kTfLiteOk) {
        LOG(ERROR) << "ANeuralNetworksExecution_create error: interpreter build status " << status
                   << " != " << kTfLiteOk;
        return ANEURALNETWORKS_OP_FAILED;
    }

    std::unique_ptr<ExecutionContext> context =
            std::make_unique<ExecutionContext>(std::move(interpreter));
    *execution = reinterpret_cast<ANeuralNetworksExecution*>(context.release());
    return ANEURALNETWORKS_NO_ERROR;
}

void ANeuralNetworksExecution_free(ANeuralNetworksExecution* execution) {
    NNTRACE_RT(NNTRACE_PHASE_EXECUTION, "ANeuralNetworksExecution_free");
    // Free of nullptr is valid.
    auto r = reinterpret_cast<ExecutionContext*>(execution);
    delete r;
}

int ANeuralNetworksExecution_getOutputOperandRank(ANeuralNetworksExecution* /* execution */,
                                                  int32_t /* index */, uint32_t* /* rank */) {
    NNTRACE_RT(NNTRACE_PHASE_EXECUTION, "ANeuralNetworksExecution_getOutputOperandRank");
    // Not supported yet in NNAPI v2
    LOG(ERROR)
            << "ANeuralNetworksExecution_getOutputOperandRank unimplemented in Neural Networks V2";
    return ANEURALNETWORKS_OP_FAILED;
}

int ANeuralNetworksExecution_getOutputOperandDimensions(ANeuralNetworksExecution* /* execution */,
                                                        int32_t /* index */,
                                                        uint32_t* /* dimensions */) {
    NNTRACE_RT(NNTRACE_PHASE_EXECUTION, "ANeuralNetworksExecution_getOutputOperandDimensions");
    // Not supported yet in NNAPI v2
    LOG(ERROR) << "ANeuralNetworksExecution_getOutputOperandDimensions unimplemented in Neural "
                  "Networks V2";
    return ANEURALNETWORKS_OP_FAILED;
}

int ANeuralNetworksExecution_setInput(ANeuralNetworksExecution* execution, int32_t index,
                                      const ANeuralNetworksOperandType* type, const void* buffer,
                                      size_t length) {
    NNTRACE_RT(NNTRACE_PHASE_INPUTS_AND_OUTPUTS, "ANeuralNetworksExecution_setInput");
    // We do not support dynamic shapes
    if (type != nullptr) {
        LOG(ERROR) << "ANeuralNetworksExecution_setInput expected a nullptr for "
                      "ANeuralNetworksOperandType* argument";
        return ANEURALNETWORKS_BAD_DATA;
    }
    if (!execution || (!buffer && length != 0)) {
        LOG(ERROR) << "ANeuralNetworksExecution_setInput passed a nullptr";
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    auto context = reinterpret_cast<ExecutionContext*>(execution);
    if (index < 0 || index >= static_cast<int32_t>(context->interpreter->inputs().size())) {
        LOG(ERROR) << "ANeuralNetworksExecution_setInput index out of bounds";
        return ANEURALNETWORKS_BAD_DATA;
    }

    if (context->interpreter->input_tensor(index)->bytes != length) {
        LOG(ERROR)
                << "ANeuralNetworksExecution_setInput input bytes is different from buffer length";
        return ANEURALNETWORKS_BAD_DATA;
    }
    context->inputs[index] = buffer;
    context->inputSizes[index] = length;
    return ANEURALNETWORKS_NO_ERROR;
}

int ANeuralNetworksExecution_setInputFromMemory(ANeuralNetworksExecution* /* execution */,
                                                int32_t /* index */,
                                                const ANeuralNetworksOperandType* /* type */,
                                                const ANeuralNetworksMemory* /* memory */,
                                                size_t /* offset */, size_t /* length */) {
    NNTRACE_RT(NNTRACE_PHASE_INPUTS_AND_OUTPUTS, "ANeuralNetworksExecution_setInputFromMemory");
    // Not supported yet in NNAPI v2
    LOG(ERROR) << "ANeuralNetworksExecution_setInputFromMemory unimplemented in Neural Networks V2";
    return ANEURALNETWORKS_OP_FAILED;
}

int ANeuralNetworksExecution_setOutput(ANeuralNetworksExecution* execution, int32_t index,
                                       const ANeuralNetworksOperandType* type, void* buffer,
                                       size_t length) {
    NNTRACE_RT(NNTRACE_PHASE_INPUTS_AND_OUTPUTS, "ANeuralNetworksExecution_setOutput");
    // We do not support dynamic shapes
    if (type != nullptr) {
        LOG(ERROR) << "ANeuralNetworksExecution_setOutput expected a nullptr for "
                      "ANeuralNetworksOperandType* argument";
        return ANEURALNETWORKS_BAD_DATA;
    }

    if (!execution || (!buffer && length != 0)) {
        LOG(ERROR) << "ANeuralNetworksExecution_setOutput passed a nullptr ";
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }

    auto context = reinterpret_cast<ExecutionContext*>(execution);
    if (index < 0 || index >= static_cast<int32_t>(context->interpreter->outputs().size())) {
        LOG(ERROR) << "ANeuralNetworksExecution_setOutput index out of bounds";
        return ANEURALNETWORKS_BAD_DATA;
    }

    const size_t bufferSize = std::max<size_t>(length, 1);
    if (bufferSize != context->interpreter->output_tensor(index)->bytes) {
        LOG(ERROR) << "ANeuralNetworksExecution_setOutput length is not equal to the output tensor "
                      "size";
        return ANEURALNETWORKS_BAD_DATA;
    }

    const intptr_t dataPtrValue = reinterpret_cast<intptr_t>(buffer);
    if (dataPtrValue % tflite::kDefaultTensorAlignment != 0) {
        context->outputs[index] = buffer;
        context->outputSizes[index] = bufferSize;
    } else {
        TfLiteCustomAllocation allocation = {.data = buffer, .bytes = bufferSize};
        context->interpreter->SetCustomAllocationForTensor(context->interpreter->outputs()[index],
                                                           allocation,
                                                           kTfLiteCustomAllocationFlagsNone);
    }

    context->isOutputSpecifiedAtIndex[index] = true;
    return ANEURALNETWORKS_NO_ERROR;
}

int ANeuralNetworksExecution_setOutputFromMemory(ANeuralNetworksExecution* /* execution */,
                                                 int32_t /* index */,
                                                 const ANeuralNetworksOperandType* /* type */,
                                                 const ANeuralNetworksMemory* /* memory */,
                                                 size_t /* offset */, size_t /* length */) {
    NNTRACE_RT(NNTRACE_PHASE_INPUTS_AND_OUTPUTS, "ANeuralNetworksExecution_setOutputFromMemory");
    // Not supported yet in NNAPI v2
    LOG(ERROR)
            << "ANeuralNetworksExecution_setOutputFromMemory unimplemented in Neural Networks V2";
    return ANEURALNETWORKS_OP_FAILED;
}

int ANeuralNetworksExecution_startCompute(ANeuralNetworksExecution* /* execution */,
                                          ANeuralNetworksEvent** /* event */) {
    NNTRACE_RT(NNTRACE_PHASE_EXECUTION, "ANeuralNetworksExecution_startCompute");
    // Not supported yet in NNAPI v2
    LOG(ERROR) << "ANeuralNetworksExecution_startCompute unimplemented in Neural Networks V2";
    return ANEURALNETWORKS_OP_FAILED;
}

int ANeuralNetworksExecution_setTimeout(ANeuralNetworksExecution* /* execution */,
                                        uint64_t /* duration */) {
    NNTRACE_RT(NNTRACE_PHASE_EXECUTION, "ANeuralNetworksExecution_setTimeout");
    // Not supported yet in NNAPI v2
    LOG(ERROR) << "ANeuralNetworksExecution_setTimeout unimplemented in Neural Networks V2";
    return ANEURALNETWORKS_OP_FAILED;
}

int ANeuralNetworksEvent_wait(ANeuralNetworksEvent* event) {
    NNTRACE_RT(NNTRACE_PHASE_EXECUTION, "ANeuralNetworksEvent_wait");
    if (event == nullptr) {
        LOG(ERROR) << "ANeuralNetworksEvent_wait passed a nullptr";
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }

    IEvent* e = reinterpret_cast<IEvent*>(event);
    return convertErrorStatusToResultCode(e->wait());
}

void ANeuralNetworksEvent_free(ANeuralNetworksEvent* event) {
    NNTRACE_RT(NNTRACE_PHASE_EXECUTION, "ANeuralNetworksEvent_free");
    // No validation.  Free of nullptr is valid.
    if (event) {
        IEvent* e = reinterpret_cast<IEvent*>(event);
        e->wait();
        delete e;
    }
}

int ANeuralNetworksExecution_setLoopTimeout(ANeuralNetworksExecution* /* execution */,
                                            uint64_t /* duration */) {
    NNTRACE_RT(NNTRACE_PHASE_EXECUTION, "ANeuralNetworksExecution_setLoopTimeout");
    // Not supported yet in NNAPI v2
    LOG(ERROR) << "ANeuralNetworksExecution_setLoopTimeout unimplemented in Neural Networks V2";
    return ANEURALNETWORKS_OP_FAILED;
}

uint64_t ANeuralNetworks_getDefaultLoopTimeout() {
    return operation_while::kTimeoutNsDefault;
}

uint64_t ANeuralNetworks_getMaximumLoopTimeout() {
    return operation_while::kTimeoutNsMaximum;
}

int ANeuralNetworksDevice_getExtensionSupport(const ANeuralNetworksDevice* device,
                                              const char* extensionName,
                                              bool* isExtensionSupported) {
    if (device == nullptr || extensionName == nullptr || isExtensionSupported == nullptr) {
        LOG(ERROR) << "ANeuralNetworksDevice_getExtensionSupport passed a nullptr";
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }

    const Device* d = reinterpret_cast<const Device*>(device);
    const auto& supportedExtensions = d->getSupportedExtensions();
    *isExtensionSupported = std::any_of(supportedExtensions.begin(), supportedExtensions.end(),
                                        [extensionName](const auto& supportedExtension) {
                                            return supportedExtension.name == extensionName;
                                        });

    return ANEURALNETWORKS_NO_ERROR;
}

int ANeuralNetworksModel_getExtensionOperandType(ANeuralNetworksModel* model,
                                                 const char* extensionName,
                                                 uint16_t operandCodeWithinExtension,
                                                 int32_t* type) {
    NNTRACE_RT(NNTRACE_PHASE_PREPARATION, "ANeuralNetworksModel_getExtensionOperandType");
    if (!model || !extensionName || !type) {
        LOG(ERROR) << "ANeuralNetworksModel_getExtensionOperandType passed a nullptr";
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    FlatbufferModelBuilder* m = reinterpret_cast<FlatbufferModelBuilder*>(model);
    return m->getExtensionType(extensionName, operandCodeWithinExtension, type);
}

int ANeuralNetworksModel_getExtensionOperationType(ANeuralNetworksModel* model,
                                                   const char* extensionName,
                                                   uint16_t operationCodeWithinExtension,
                                                   ANeuralNetworksOperationType* type) {
    NNTRACE_RT(NNTRACE_PHASE_PREPARATION, "ANeuralNetworksModel_getExtensionOperationType");
    if (!model || !extensionName || !type) {
        LOG(ERROR) << "ANeuralNetworksModel_getExtensionOperationType passed a nullptr";
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    FlatbufferModelBuilder* m = reinterpret_cast<FlatbufferModelBuilder*>(model);
    return m->getExtensionType(extensionName, operationCodeWithinExtension, type);
}

int ANeuralNetworksModel_setOperandExtensionData(ANeuralNetworksModel* model, int32_t index,
                                                 const void* data, size_t length) {
    NNTRACE_RT(NNTRACE_PHASE_PREPARATION, "ANeuralNetworksModel_setOperandExtensionData");
    if (!model || (!data && length != 0)) {
        LOG(ERROR) << "ANeuralNetworksModel_setOperandExtensionData passed a nullptr";
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    FlatbufferModelBuilder* m = reinterpret_cast<FlatbufferModelBuilder*>(model);
    return m->setOperandExtensionData(index, data, length);
}

int ANeuralNetworksCompilation_addExtensionAttribute(ANeuralNetworksCompilation* /* compilation */,
                                                     const char* /* extensionName */,
                                                     uint16_t /* attributeCodeWithinExtension */,
                                                     const void* /* data */, size_t /* length */) {
    NNTRACE_RT(NNTRACE_PHASE_COMPILATION, "ANeuralNetworksCompilation_addExtensionAttribute");
    // Not supported yet in NNAPI v2
    LOG(ERROR) << "ANeuralNetworksCompilation_addExtensionAttribute unimplemented in Neural "
                  "Networks V2";
    return ANEURALNETWORKS_OP_FAILED;
}

int ANeuralNetworksExecution_addExtensionAttribute(ANeuralNetworksExecution* /* execution */,
                                                   const char* /* extensionName */,
                                                   uint16_t /* attributeCodeWithinExtension */,
                                                   const void* /* data */, size_t /* length */) {
    NNTRACE_RT(NNTRACE_PHASE_EXECUTION, "ANeuralNetworksExecution_addExtensionAttribute");
    // Not supported yet in NNAPI v2
    LOG(ERROR)
            << "ANeuralNetworksExecution_addExtensionAttribute unimplemented in Neural Networks V2";
    return ANEURALNETWORKS_OP_FAILED;
}

int ANeuralNetworksEvent_createFromSyncFenceFd(int syncFenceFd, ANeuralNetworksEvent** event) {
    if (event == nullptr) {
        LOG(ERROR) << "ANeuralNetworksEvent_createFromSyncFenceFd passed a nullptr";
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    if (syncFenceFd <= 0) {
        LOG(ERROR) << "ANeuralNetworksEvent_createFromSyncFenceFd passed an invalid fd: "
                   << syncFenceFd;
        *event = nullptr;
        return ANEURALNETWORKS_BAD_DATA;
    }
    std::unique_ptr<SyncFenceEvent> e =
            std::make_unique<SyncFenceEvent>(syncFenceFd, nullptr, nullptr);
    *event = reinterpret_cast<ANeuralNetworksEvent*>(e.release());
    return ANEURALNETWORKS_NO_ERROR;
}

int ANeuralNetworksEvent_getSyncFenceFd(const ANeuralNetworksEvent* event, int* syncFenceFd) {
    if (syncFenceFd == nullptr) {
        LOG(ERROR) << "ANeuralNetworksEvent_getSyncFenceFd passed a nullptr";
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    *syncFenceFd = -1;
    if (event == nullptr) {
        LOG(ERROR) << "ANeuralNetworksEvent_getSyncFenceFd passed a nullptr";
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    const IEvent* e = reinterpret_cast<const IEvent*>(event);
    // The client owns the dupped fd, and is responsible for closing it.
    *syncFenceFd = e->getSyncFenceFd(/*shouldDup*/ true);
    if (*syncFenceFd <= 0) {
        LOG(ERROR) << "ANeuralNetworksEvent_getSyncFenceFd unable to get valid sync_fence fd";
        *syncFenceFd = -1;
        return ANEURALNETWORKS_BAD_DATA;
    }
    return ANEURALNETWORKS_NO_ERROR;
}

int ANeuralNetworksExecution_startComputeWithDependencies(
        ANeuralNetworksExecution* /* execution */,
        const ANeuralNetworksEvent* const* /* dependencies */, uint32_t /* numOfDependencies */,
        uint64_t /* duration */, ANeuralNetworksEvent** /* event */) {
    NNTRACE_RT(NNTRACE_PHASE_EXECUTION, "ANeuralNetworksExecution_startComputeWithDependencies");
    // Not supported yet in NNAPI v2
    LOG(ERROR) << "ANeuralNetworksExecution_startComputeWithDependencies unimplemented in Neural "
                  "Networks V2";
    return ANEURALNETWORKS_OP_FAILED;
}

int64_t ANeuralNetworks_getRuntimeFeatureLevel() {
    return getRuntimeFeatureLevelImpl();
}

int ANeuralNetworksExecution_enableInputAndOutputPadding(ANeuralNetworksExecution* /* execution */,
                                                         bool /* enable */) {
    NNTRACE_RT(NNTRACE_PHASE_EXECUTION, "ANeuralNetworksExecution_enableInputAndOutputPadding");
    // Not supported yet in NNAPI v2
    LOG(ERROR) << "ANeuralNetworksExecution_enableInputAndOutputPadding unimplemented in Neural "
                  "Networks V2";
    return ANEURALNETWORKS_OP_FAILED;
}

int ANeuralNetworksCompilation_getPreferredMemoryAlignmentForInput(
        const ANeuralNetworksCompilation* /* compilation */, uint32_t /* index */,
        uint32_t* /* alignment */) {
    NNTRACE_RT(NNTRACE_PHASE_COMPILATION,
               "ANeuralNetworksCompilation_getPreferredMemoryAlignmentForInput");
    // Not supported yet in NNAPI v2
    LOG(ERROR) << "ANeuralNetworksCompilation_getPreferredMemoryAlignmentForInput unimplemented in "
                  "Neural Networks V2";
    return ANEURALNETWORKS_OP_FAILED;
}

int ANeuralNetworksCompilation_getPreferredMemoryPaddingForInput(
        const ANeuralNetworksCompilation* /* compilation */, uint32_t /* index */,
        uint32_t* /* padding */) {
    NNTRACE_RT(NNTRACE_PHASE_COMPILATION,
               "ANeuralNetworksCompilation_getPreferredMemoryPaddingForInput");
    // Not supported yet in NNAPI v2
    LOG(ERROR) << "ANeuralNetworksCompilation_getPreferredMemoryPaddingForInput unimplemented in "
                  "Neural Networks V2";
    return ANEURALNETWORKS_OP_FAILED;
}

int ANeuralNetworksCompilation_getPreferredMemoryAlignmentForOutput(
        const ANeuralNetworksCompilation* /* compilation */, uint32_t /* index */,
        uint32_t* /* alignment */) {
    NNTRACE_RT(NNTRACE_PHASE_COMPILATION,
               "ANeuralNetworksCompilation_getPreferredMemoryAlignmentForOutput");
    // Not supported yet in NNAPI v2
    LOG(ERROR)
            << "ANeuralNetworksCompilation_getPreferredMemoryAlignmentForOutput unimplemented in "
               "Neural Networks V2";
    return ANEURALNETWORKS_OP_FAILED;
}

int ANeuralNetworksCompilation_getPreferredMemoryPaddingForOutput(
        const ANeuralNetworksCompilation* /* compilation */, uint32_t /* index */,
        uint32_t* /* padding */) {
    NNTRACE_RT(NNTRACE_PHASE_COMPILATION,
               "ANeuralNetworksCompilation_getPreferredMemoryPaddingForOutput");
    // Not supported yet in NNAPI v2
    LOG(ERROR) << "ANeuralNetworksCompilation_getPreferredMemoryPaddingForOutput unimplemented in "
                  "Neural Networks V2";
    return ANEURALNETWORKS_OP_FAILED;
}

int ANeuralNetworksExecution_setReusable(ANeuralNetworksExecution* /* execution */,
                                         bool /* reusable */) {
    NNTRACE_RT(NNTRACE_PHASE_EXECUTION, "ANeuralNetworksExecution_setReusable");
    // Not supported yet in NNAPI v2
    LOG(ERROR) << "ANeuralNetworksExecution_setReusable unimplemented in Neural Networks V2";
    return ANEURALNETWORKS_OP_FAILED;
}
