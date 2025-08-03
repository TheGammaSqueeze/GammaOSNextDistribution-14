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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_UTILS_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_UTILS_H

#include <string>
#include <vector>

#include "nnapi/TypeUtils.h"
#include "nnapi/Types.h"

namespace android::nn {

// DEPRECATED. Use NN_RET_CHECK instead.
#define NN_CHECK(x) NN_RET_CHECK(x)
#define NN_OPS_CHECK(x) NN_RET_CHECK(x)

// DEPRECATED. Use NN_RET_CHECK_EQ instead.
#define NN_CHECK_EQ(x, y) NN_RET_CHECK_EQ(x, y)

#ifdef NN_EXPERIMENTAL_FEATURE
#define NN_FOR_EACH_EXPERIMENTAL_OPERATION_IF_ENABLED(CALL) CALL(DENSIFY)
#else  // NN_EXPERIMENTAL_FEATURE
#define NN_FOR_EACH_EXPERIMENTAL_OPERATION_IF_ENABLED(CALL)
#endif  // NN_EXPERIMENTAL_FEATURE

// TODO(b/213798075): Generate this operation list in a macro with the API generator.
#define NN_FOR_EACH_OPERATION(CALL)    \
    CALL(ADD)                          \
    CALL(AVERAGE_POOL_2D)              \
    CALL(CONCATENATION)                \
    CALL(CONV_2D)                      \
    CALL(DEPTHWISE_CONV_2D)            \
    CALL(DEPTH_TO_SPACE)               \
    CALL(DEQUANTIZE)                   \
    CALL(EMBEDDING_LOOKUP)             \
    CALL(FLOOR)                        \
    CALL(FULLY_CONNECTED)              \
    CALL(HASHTABLE_LOOKUP)             \
    CALL(L2_NORMALIZATION)             \
    CALL(L2_POOL_2D)                   \
    CALL(LOCAL_RESPONSE_NORMALIZATION) \
    CALL(LOGISTIC)                     \
    CALL(LSH_PROJECTION)               \
    CALL(LSTM)                         \
    CALL(MAX_POOL_2D)                  \
    CALL(MUL)                          \
    CALL(RELU)                         \
    CALL(RELU1)                        \
    CALL(RELU6)                        \
    CALL(RESHAPE)                      \
    CALL(RESIZE_BILINEAR)              \
    CALL(RNN)                          \
    CALL(SOFTMAX)                      \
    CALL(SPACE_TO_DEPTH)               \
    CALL(SVDF)                         \
    CALL(TANH)                         \
    CALL(BATCH_TO_SPACE_ND)            \
    CALL(DIV)                          \
    CALL(MEAN)                         \
    CALL(PAD)                          \
    CALL(SPACE_TO_BATCH_ND)            \
    CALL(SQUEEZE)                      \
    CALL(STRIDED_SLICE)                \
    CALL(SUB)                          \
    CALL(TRANSPOSE)                    \
    CALL(ABS)                          \
    CALL(ARGMAX)                       \
    CALL(ARGMIN)                       \
    CALL(AXIS_ALIGNED_BBOX_TRANSFORM)  \
    CALL(BIDIRECTIONAL_SEQUENCE_LSTM)  \
    CALL(BIDIRECTIONAL_SEQUENCE_RNN)   \
    CALL(BOX_WITH_NMS_LIMIT)           \
    CALL(CAST)                         \
    CALL(CHANNEL_SHUFFLE)              \
    CALL(DETECTION_POSTPROCESSING)     \
    CALL(EQUAL)                        \
    CALL(EXP)                          \
    CALL(EXPAND_DIMS)                  \
    CALL(GATHER)                       \
    CALL(GENERATE_PROPOSALS)           \
    CALL(GREATER)                      \
    CALL(GREATER_EQUAL)                \
    CALL(GROUPED_CONV_2D)              \
    CALL(HEATMAP_MAX_KEYPOINT)         \
    CALL(INSTANCE_NORMALIZATION)       \
    CALL(LESS)                         \
    CALL(LESS_EQUAL)                   \
    CALL(LOG)                          \
    CALL(LOGICAL_AND)                  \
    CALL(LOGICAL_NOT)                  \
    CALL(LOGICAL_OR)                   \
    CALL(LOG_SOFTMAX)                  \
    CALL(MAXIMUM)                      \
    CALL(MINIMUM)                      \
    CALL(NEG)                          \
    CALL(NOT_EQUAL)                    \
    CALL(PAD_V2)                       \
    CALL(POW)                          \
    CALL(PRELU)                        \
    CALL(QUANTIZE)                     \
    CALL(QUANTIZED_16BIT_LSTM)         \
    CALL(RANDOM_MULTINOMIAL)           \
    CALL(REDUCE_ALL)                   \
    CALL(REDUCE_ANY)                   \
    CALL(REDUCE_MAX)                   \
    CALL(REDUCE_MIN)                   \
    CALL(REDUCE_PROD)                  \
    CALL(REDUCE_SUM)                   \
    CALL(ROI_ALIGN)                    \
    CALL(ROI_POOLING)                  \
    CALL(RSQRT)                        \
    CALL(SELECT)                       \
    CALL(SIN)                          \
    CALL(SLICE)                        \
    CALL(SPLIT)                        \
    CALL(SQRT)                         \
    CALL(TILE)                         \
    CALL(TOPK_V2)                      \
    CALL(TRANSPOSE_CONV_2D)            \
    CALL(UNIDIRECTIONAL_SEQUENCE_LSTM) \
    CALL(UNIDIRECTIONAL_SEQUENCE_RNN)  \
    CALL(RESIZE_NEAREST_NEIGHBOR)      \
    CALL(QUANTIZED_LSTM)               \
    CALL(IF)                           \
    CALL(WHILE)                        \
    CALL(ELU)                          \
    CALL(HARD_SWISH)                   \
    CALL(FILL)                         \
    CALL(RANK)                         \
    CALL(BATCH_MATMUL)                 \
    CALL(PACK)                         \
    CALL(MIRROR_PAD)                   \
    CALL(REVERSE)                      \
    CALL(OEM_OPERATION)                \
    NN_FOR_EACH_EXPERIMENTAL_OPERATION_IF_ENABLED(CALL)

// An 8-bit boolean type (sizeof(bool) is implementation-defined).
typedef uint8_t bool8;

// Stores operand type information. "Shape" is a historical name.
struct Shape {
    OperandType type = OperandType::FLOAT32;
    std::vector<uint32_t> dimensions;
    float scale = 0.0f;
    int32_t offset = 0;
    Operand::ExtraParams extraParams;
};

// Verifies that the two shapes are the same.
bool SameShape(const Shape& in1, const Shape& in2);

// Sets out to the same shape as in.
bool SetShape(const Shape& in, Shape* out);

// Return the total number of elements, i.e. all the dimensions multiplied
// together. For a scalar, returns one.
uint32_t getNumberOfElements(const Shape& shape);
uint32_t getNumberOfElements(const Shape& shape, size_t firstAxisInclusive,
                             size_t lastAxisExclusive);

uint32_t getNumberOfDimensions(const Shape& shape);

uint32_t getSizeOfDimension(const Shape& shape, uint32_t dimensionIdx);

uint32_t hasKnownRank(const Shape& shape);

}  // namespace android::nn

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_OPERATIONS_UTILS_H
