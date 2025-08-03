// Generated from axis_aligned_bbox_transform.mod.py
// DO NOT EDIT
// clang-format off
#include "TestHarness.h"
using namespace test_helper;  // NOLINT(google-build-using-namespace)

namespace generated_tests::axis_aligned_bbox_transform {

const TestModel& get_test_model() {
    static TestModel model = {
        .main = {
                .operands = {{ // roi
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {5, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({100.0f, 150.0f, 400.0f, 430.0f, 120.0f, 60.0f, 122.0f, 61.0f, 10.0f, 20.0f, 20.0f, 50.0f, 50.0f, 120.0f, 150.0f, 250.0f, 400.0f, 100.0f, 1000.0f, 2000.0f})
                        }, { // bboxDeltas
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.2f, 0.2f, 0.1f, 0.1f, 0.3f, -0.1f, -0.2f, 0.1f, -0.5f, 0.2f, 0.2f, -0.5f, -0.1f, -0.1f, 2.5f, 3.0f, -0.5f, -0.5f, 1.0f, 1.0f, 0.5f, 0.5f, -1.5f, -1.2f, 0.2f, 0.2f, -3.0f, -4.0f, 1.0f, -0.5f, 0.3f, 0.5f, 0.3f, -0.2f, 1.1f, -0.8f, 0.1f, 0.05f, -0.5f, -0.5f})
                        }, { // batchSplit
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 1, 2, 2, 3})
                        }, { // imageInfo
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({512.0f, 512.0f, 128.0f, 256.0f, 256.0f, 256.0f, 1024.0f, 512.0f})
                        }, { // out
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({144.22435f, 191.276062f, 475.775635f, 500.723938f, 217.190384f, 107.276062f, 462.809631f, 416.723938f, 118.778594f, 60.396736f, 121.221406f, 61.003266f, 108.617508f, 50.357232f, 132.982498f, 70.442772f, 0.0f, 0.0f, 23.59140714f, 60.77422571f, 18.88435f, 45.48208571f, 21.11565f, 54.51791429f, 117.51063714f, 209.80948286f, 122.48935143f, 212.19050857f, 132.50705143f, 12.83312286f, 255.99999571f, 227.16685714f, 0.0f, 243.1374815f, 512.0f, 1024.0f, 512.0f, 568.7958375f, 512.0f, 1024.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::AXIS_ALIGNED_BBOX_TRANSFORM,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model = TestModelManager::get().add("axis_aligned_bbox_transform", get_test_model());

}  // namespace generated_tests::axis_aligned_bbox_transform

namespace generated_tests::axis_aligned_bbox_transform {

const TestModel& get_test_model_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // roi
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {5, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // bboxDeltas
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // batchSplit
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 1, 2, 2, 3})
                        }, { // imageInfo
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // out
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({144.22435f, 191.276062f, 475.775635f, 500.723938f, 217.190384f, 107.276062f, 462.809631f, 416.723938f, 118.778594f, 60.396736f, 121.221406f, 61.003266f, 108.617508f, 50.357232f, 132.982498f, 70.442772f, 0.0f, 0.0f, 23.59140714f, 60.77422571f, 18.88435f, 45.48208571f, 21.11565f, 54.51791429f, 117.51063714f, 209.80948286f, 122.48935143f, 212.19050857f, 132.50705143f, 12.83312286f, 255.99999571f, 227.16685714f, 0.0f, 243.1374815f, 512.0f, 1024.0f, 512.0f, 568.7958375f, 512.0f, 1024.0f})
                        }, { // roi_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {5, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({100.0f, 150.0f, 400.0f, 430.0f, 120.0f, 60.0f, 122.0f, 61.0f, 10.0f, 20.0f, 20.0f, 50.0f, 50.0f, 120.0f, 150.0f, 250.0f, 400.0f, 100.0f, 1000.0f, 2000.0f})
                        }, { // placeholder
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
                        }, { // param
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // bboxDeltas_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.2f, 0.2f, 0.1f, 0.1f, 0.3f, -0.1f, -0.2f, 0.1f, -0.5f, 0.2f, 0.2f, -0.5f, -0.1f, -0.1f, 2.5f, 3.0f, -0.5f, -0.5f, 1.0f, 1.0f, 0.5f, 0.5f, -1.5f, -1.2f, 0.2f, 0.2f, -3.0f, -4.0f, 1.0f, -0.5f, 0.3f, 0.5f, 0.3f, -0.2f, 1.1f, -0.8f, 0.1f, 0.05f, -0.5f, -0.5f})
                        }, { // placeholder1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
                        }, { // param1
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // imageInfo_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({512.0f, 512.0f, 128.0f, 256.0f, 256.0f, 256.0f, 1024.0f, 512.0f})
                        }, { // placeholder2
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
                        }, { // param2
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {8, 9, 10},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {11, 12, 13},
                            .outputs = {3}
                        }, {
                            .type = TestOperationType::AXIS_ALIGNED_BBOX_TRANSFORM,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {2, 5, 8, 11},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal = TestModelManager::get().add("axis_aligned_bbox_transform_all_inputs_as_internal", get_test_model_all_inputs_as_internal());

}  // namespace generated_tests::axis_aligned_bbox_transform

namespace generated_tests::axis_aligned_bbox_transform {

const TestModel& get_test_model_relaxed() {
    static TestModel model = {
        .main = {
                .operands = {{ // roi
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {5, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({100.0f, 150.0f, 400.0f, 430.0f, 120.0f, 60.0f, 122.0f, 61.0f, 10.0f, 20.0f, 20.0f, 50.0f, 50.0f, 120.0f, 150.0f, 250.0f, 400.0f, 100.0f, 1000.0f, 2000.0f})
                        }, { // bboxDeltas
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.2f, 0.2f, 0.1f, 0.1f, 0.3f, -0.1f, -0.2f, 0.1f, -0.5f, 0.2f, 0.2f, -0.5f, -0.1f, -0.1f, 2.5f, 3.0f, -0.5f, -0.5f, 1.0f, 1.0f, 0.5f, 0.5f, -1.5f, -1.2f, 0.2f, 0.2f, -3.0f, -4.0f, 1.0f, -0.5f, 0.3f, 0.5f, 0.3f, -0.2f, 1.1f, -0.8f, 0.1f, 0.05f, -0.5f, -0.5f})
                        }, { // batchSplit
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 1, 2, 2, 3})
                        }, { // imageInfo
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({512.0f, 512.0f, 128.0f, 256.0f, 256.0f, 256.0f, 1024.0f, 512.0f})
                        }, { // out
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({144.22435f, 191.276062f, 475.775635f, 500.723938f, 217.190384f, 107.276062f, 462.809631f, 416.723938f, 118.778594f, 60.396736f, 121.221406f, 61.003266f, 108.617508f, 50.357232f, 132.982498f, 70.442772f, 0.0f, 0.0f, 23.59140714f, 60.77422571f, 18.88435f, 45.48208571f, 21.11565f, 54.51791429f, 117.51063714f, 209.80948286f, 122.48935143f, 212.19050857f, 132.50705143f, 12.83312286f, 255.99999571f, 227.16685714f, 0.0f, 243.1374815f, 512.0f, 1024.0f, 512.0f, 568.7958375f, 512.0f, 1024.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::AXIS_ALIGNED_BBOX_TRANSFORM,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed = TestModelManager::get().add("axis_aligned_bbox_transform_relaxed", get_test_model_relaxed());

}  // namespace generated_tests::axis_aligned_bbox_transform

namespace generated_tests::axis_aligned_bbox_transform {

const TestModel& get_test_model_relaxed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // roi
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {5, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // bboxDeltas
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // batchSplit
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 1, 2, 2, 3})
                        }, { // imageInfo
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // out
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({144.22435f, 191.276062f, 475.775635f, 500.723938f, 217.190384f, 107.276062f, 462.809631f, 416.723938f, 118.778594f, 60.396736f, 121.221406f, 61.003266f, 108.617508f, 50.357232f, 132.982498f, 70.442772f, 0.0f, 0.0f, 23.59140714f, 60.77422571f, 18.88435f, 45.48208571f, 21.11565f, 54.51791429f, 117.51063714f, 209.80948286f, 122.48935143f, 212.19050857f, 132.50705143f, 12.83312286f, 255.99999571f, 227.16685714f, 0.0f, 243.1374815f, 512.0f, 1024.0f, 512.0f, 568.7958375f, 512.0f, 1024.0f})
                        }, { // roi_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {5, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({100.0f, 150.0f, 400.0f, 430.0f, 120.0f, 60.0f, 122.0f, 61.0f, 10.0f, 20.0f, 20.0f, 50.0f, 50.0f, 120.0f, 150.0f, 250.0f, 400.0f, 100.0f, 1000.0f, 2000.0f})
                        }, { // placeholder3
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
                        }, { // param3
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // bboxDeltas_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.2f, 0.2f, 0.1f, 0.1f, 0.3f, -0.1f, -0.2f, 0.1f, -0.5f, 0.2f, 0.2f, -0.5f, -0.1f, -0.1f, 2.5f, 3.0f, -0.5f, -0.5f, 1.0f, 1.0f, 0.5f, 0.5f, -1.5f, -1.2f, 0.2f, 0.2f, -3.0f, -4.0f, 1.0f, -0.5f, 0.3f, 0.5f, 0.3f, -0.2f, 1.1f, -0.8f, 0.1f, 0.05f, -0.5f, -0.5f})
                        }, { // placeholder4
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
                        }, { // param4
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // imageInfo_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({512.0f, 512.0f, 128.0f, 256.0f, 256.0f, 256.0f, 1024.0f, 512.0f})
                        }, { // placeholder5
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
                        }, { // param5
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {8, 9, 10},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {11, 12, 13},
                            .outputs = {3}
                        }, {
                            .type = TestOperationType::AXIS_ALIGNED_BBOX_TRANSFORM,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {2, 5, 8, 11},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_all_inputs_as_internal = TestModelManager::get().add("axis_aligned_bbox_transform_relaxed_all_inputs_as_internal", get_test_model_relaxed_all_inputs_as_internal());

}  // namespace generated_tests::axis_aligned_bbox_transform

namespace generated_tests::axis_aligned_bbox_transform {

const TestModel& get_test_model_float16() {
    static TestModel model = {
        .main = {
                .operands = {{ // roi
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {5, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({100.0f, 150.0f, 400.0f, 430.0f, 120.0f, 60.0f, 122.0f, 61.0f, 10.0f, 20.0f, 20.0f, 50.0f, 50.0f, 120.0f, 150.0f, 250.0f, 400.0f, 100.0f, 1000.0f, 2000.0f})
                        }, { // bboxDeltas
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.20000000298023224f, 0.20000000298023224f, 0.10000000149011612f, 0.10000000149011612f, 0.30000001192092896f, -0.10000000149011612f, -0.20000000298023224f, 0.10000000149011612f, -0.5f, 0.20000000298023224f, 0.20000000298023224f, -0.5f, -0.10000000149011612f, -0.10000000149011612f, 2.5f, 3.0f, -0.5f, -0.5f, 1.0f, 1.0f, 0.5f, 0.5f, -1.5f, -1.2000000476837158f, 0.20000000298023224f, 0.20000000298023224f, -3.0f, -4.0f, 1.0f, -0.5f, 0.30000001192092896f, 0.5f, 0.30000001192092896f, -0.20000000298023224f, 1.100000023841858f, -0.800000011920929f, 0.10000000149011612f, 0.05000000074505806f, -0.5f, -0.5f})
                        }, { // batchSplit
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 1, 2, 2, 3})
                        }, { // imageInfo
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({512.0f, 512.0f, 128.0f, 256.0f, 256.0f, 256.0f, 1024.0f, 512.0f})
                        }, { // out
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({144.22434997558594f, 191.27606201171875f, 475.775634765625f, 500.72393798828125f, 217.1903839111328f, 107.27606201171875f, 462.80963134765625f, 416.72393798828125f, 118.77859497070312f, 60.39673614501953f, 121.22140502929688f, 61.003265380859375f, 108.61750793457031f, 50.35723114013672f, 132.9824981689453f, 70.4427719116211f, 0.0f, 0.0f, 23.591407775878906f, 60.774227142333984f, 18.884349822998047f, 45.482086181640625f, 21.115650177001953f, 54.517913818359375f, 117.51063537597656f, 209.80947875976562f, 122.48934936523438f, 212.1905059814453f, 132.50704956054688f, 12.833123207092285f, 256.0f, 227.16685485839844f, 0.0f, 243.13748168945312f, 512.0f, 1024.0f, 512.0f, 568.7958374023438f, 512.0f, 1024.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::AXIS_ALIGNED_BBOX_TRANSFORM,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16 = TestModelManager::get().add("axis_aligned_bbox_transform_float16", get_test_model_float16());

}  // namespace generated_tests::axis_aligned_bbox_transform

namespace generated_tests::axis_aligned_bbox_transform {

const TestModel& get_test_model_float16_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // roi
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {5, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // bboxDeltas
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // batchSplit
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 1, 2, 2, 3})
                        }, { // imageInfo
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // out
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({144.22434997558594f, 191.27606201171875f, 475.775634765625f, 500.72393798828125f, 217.1903839111328f, 107.27606201171875f, 462.80963134765625f, 416.72393798828125f, 118.77859497070312f, 60.39673614501953f, 121.22140502929688f, 61.003265380859375f, 108.61750793457031f, 50.35723114013672f, 132.9824981689453f, 70.4427719116211f, 0.0f, 0.0f, 23.591407775878906f, 60.774227142333984f, 18.884349822998047f, 45.482086181640625f, 21.115650177001953f, 54.517913818359375f, 117.51063537597656f, 209.80947875976562f, 122.48934936523438f, 212.1905059814453f, 132.50704956054688f, 12.833123207092285f, 256.0f, 227.16685485839844f, 0.0f, 243.13748168945312f, 512.0f, 1024.0f, 512.0f, 568.7958374023438f, 512.0f, 1024.0f})
                        }, { // roi_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {5, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({100.0f, 150.0f, 400.0f, 430.0f, 120.0f, 60.0f, 122.0f, 61.0f, 10.0f, 20.0f, 20.0f, 50.0f, 50.0f, 120.0f, 150.0f, 250.0f, 400.0f, 100.0f, 1000.0f, 2000.0f})
                        }, { // placeholder6
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
                        }, { // param6
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // bboxDeltas_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.20000000298023224f, 0.20000000298023224f, 0.10000000149011612f, 0.10000000149011612f, 0.30000001192092896f, -0.10000000149011612f, -0.20000000298023224f, 0.10000000149011612f, -0.5f, 0.20000000298023224f, 0.20000000298023224f, -0.5f, -0.10000000149011612f, -0.10000000149011612f, 2.5f, 3.0f, -0.5f, -0.5f, 1.0f, 1.0f, 0.5f, 0.5f, -1.5f, -1.2000000476837158f, 0.20000000298023224f, 0.20000000298023224f, -3.0f, -4.0f, 1.0f, -0.5f, 0.30000001192092896f, 0.5f, 0.30000001192092896f, -0.20000000298023224f, 1.100000023841858f, -0.800000011920929f, 0.10000000149011612f, 0.05000000074505806f, -0.5f, -0.5f})
                        }, { // placeholder7
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
                        }, { // param7
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // imageInfo_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({512.0f, 512.0f, 128.0f, 256.0f, 256.0f, 256.0f, 1024.0f, 512.0f})
                        }, { // placeholder8
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
                        }, { // param8
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {8, 9, 10},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {11, 12, 13},
                            .outputs = {3}
                        }, {
                            .type = TestOperationType::AXIS_ALIGNED_BBOX_TRANSFORM,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {2, 5, 8, 11},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_all_inputs_as_internal = TestModelManager::get().add("axis_aligned_bbox_transform_float16_all_inputs_as_internal", get_test_model_float16_all_inputs_as_internal());

}  // namespace generated_tests::axis_aligned_bbox_transform

namespace generated_tests::axis_aligned_bbox_transform {

const TestModel& get_test_model_quant8() {
    static TestModel model = {
        .main = {
                .operands = {{ // roi
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {5, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({800, 1200, 3200, 3440, 960, 480, 976, 488, 80, 160, 160, 400, 400, 960, 1200, 2000, 3200, 800, 8000, 16000})
                        }, { // bboxDeltas
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.05f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({132, 132, 130, 130, 134, 126, 124, 130, 118, 132, 132, 118, 126, 126, 178, 188, 118, 118, 148, 148, 138, 138, 98, 104, 132, 132, 68, 48, 148, 118, 134, 138, 134, 124, 150, 112, 130, 129, 118, 118})
                        }, { // batchSplit
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 1, 2, 2, 3})
                        }, { // imageInfo
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({4096, 4096, 1024, 2048, 2048, 2048, 8192, 4096})
                        }, { // out
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 0,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({1154, 1530, 3806, 4006, 1738, 858, 3702, 3334, 950, 483, 970, 488, 869, 403, 1064, 564, 0, 0, 189, 486, 151, 364, 169, 436, 940, 1678, 980, 1698, 1060, 103, 2048, 1817, 0, 1945, 4096, 8192, 4096, 4550, 4096, 8192})
                        }},
                .operations = {{
                            .type = TestOperationType::AXIS_ALIGNED_BBOX_TRANSFORM,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8 = TestModelManager::get().add("axis_aligned_bbox_transform_quant8", get_test_model_quant8());

}  // namespace generated_tests::axis_aligned_bbox_transform

namespace generated_tests::axis_aligned_bbox_transform {

const TestModel& get_test_model_quant8_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // roi
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {5, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({800, 1200, 3200, 3440, 960, 480, 976, 488, 80, 160, 160, 400, 400, 960, 1200, 2000, 3200, 800, 8000, 16000})
                        }, { // bboxDeltas
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.05f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // batchSplit
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 1, 2, 2, 3})
                        }, { // imageInfo
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({4096, 4096, 1024, 2048, 2048, 2048, 8192, 4096})
                        }, { // out
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 0,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({1154, 1530, 3806, 4006, 1738, 858, 3702, 3334, 950, 483, 970, 488, 869, 403, 1064, 564, 0, 0, 189, 486, 151, 364, 169, 436, 940, 1678, 980, 1698, 1060, 103, 2048, 1817, 0, 1945, 4096, 8192, 4096, 4550, 4096, 8192})
                        }, { // bboxDeltas_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.05f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({132, 132, 130, 130, 134, 126, 124, 130, 118, 132, 132, 118, 126, 126, 178, 188, 118, 118, 148, 148, 138, 138, 98, 104, 132, 132, 68, 48, 148, 118, 134, 138, 134, 124, 150, 112, 130, 129, 118, 118})
                        }, { // placeholder9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.05f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
                        }, { // param9
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {5, 6, 7},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::AXIS_ALIGNED_BBOX_TRANSFORM,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 2, 3, 5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_all_inputs_as_internal = TestModelManager::get().add("axis_aligned_bbox_transform_quant8_all_inputs_as_internal", get_test_model_quant8_all_inputs_as_internal());

}  // namespace generated_tests::axis_aligned_bbox_transform

namespace generated_tests::axis_aligned_bbox_transform {

const TestModel& get_test_model_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // roi1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {5, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({100.0f, 150.0f, 400.0f, 430.0f, 120.0f, 60.0f, 122.0f, 61.0f, 10.0f, 20.0f, 20.0f, 50.0f, 50.0f, 120.0f, 150.0f, 250.0f, 400.0f, 100.0f, 1000.0f, 2000.0f})
                        }, { // bboxDeltas1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.2f, 0.2f, 0.1f, 0.1f, 0.3f, -0.1f, -0.2f, 0.1f, -0.5f, 0.2f, 0.2f, -0.5f, -0.1f, -0.1f, 2.5f, 3.0f, -0.5f, -0.5f, 1.0f, 1.0f, 0.5f, 0.5f, -1.5f, -1.2f, 0.2f, 0.2f, -3.0f, -4.0f, 1.0f, -0.5f, 0.3f, 0.5f, 0.3f, -0.2f, 1.1f, -0.8f, 0.1f, 0.05f, -0.5f, -0.5f})
                        }, { // batchSplit1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 2, 5, 5, 6})
                        }, { // imageInfo1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {7, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({512.0f, 512.0f, 32.0f, 32.0f, 128.0f, 256.0f, 32.0f, 32.0f, 32.0f, 32.0f, 256.0f, 256.0f, 1024.0f, 512.0f})
                        }, { // out1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({144.22435f, 191.276062f, 475.775635f, 500.723938f, 217.190384f, 107.276062f, 462.809631f, 416.723938f, 118.778594f, 60.396736f, 121.221406f, 61.003266f, 108.617508f, 50.357232f, 132.982498f, 70.442772f, 0.0f, 0.0f, 23.59140714f, 60.77422571f, 18.88435f, 45.48208571f, 21.11565f, 54.51791429f, 117.51063714f, 209.80948286f, 122.48935143f, 212.19050857f, 132.50705143f, 12.83312286f, 255.99999571f, 227.16685714f, 0.0f, 243.1374815f, 512.0f, 1024.0f, 512.0f, 568.7958375f, 512.0f, 1024.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::AXIS_ALIGNED_BBOX_TRANSFORM,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_2 = TestModelManager::get().add("axis_aligned_bbox_transform_2", get_test_model_2());

}  // namespace generated_tests::axis_aligned_bbox_transform

namespace generated_tests::axis_aligned_bbox_transform {

const TestModel& get_test_model_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // roi1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {5, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // bboxDeltas1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // batchSplit1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 2, 5, 5, 6})
                        }, { // imageInfo1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {7, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // out1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({144.22435f, 191.276062f, 475.775635f, 500.723938f, 217.190384f, 107.276062f, 462.809631f, 416.723938f, 118.778594f, 60.396736f, 121.221406f, 61.003266f, 108.617508f, 50.357232f, 132.982498f, 70.442772f, 0.0f, 0.0f, 23.59140714f, 60.77422571f, 18.88435f, 45.48208571f, 21.11565f, 54.51791429f, 117.51063714f, 209.80948286f, 122.48935143f, 212.19050857f, 132.50705143f, 12.83312286f, 255.99999571f, 227.16685714f, 0.0f, 243.1374815f, 512.0f, 1024.0f, 512.0f, 568.7958375f, 512.0f, 1024.0f})
                        }, { // roi1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {5, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({100.0f, 150.0f, 400.0f, 430.0f, 120.0f, 60.0f, 122.0f, 61.0f, 10.0f, 20.0f, 20.0f, 50.0f, 50.0f, 120.0f, 150.0f, 250.0f, 400.0f, 100.0f, 1000.0f, 2000.0f})
                        }, { // placeholder10
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
                        }, { // param10
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // bboxDeltas1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.2f, 0.2f, 0.1f, 0.1f, 0.3f, -0.1f, -0.2f, 0.1f, -0.5f, 0.2f, 0.2f, -0.5f, -0.1f, -0.1f, 2.5f, 3.0f, -0.5f, -0.5f, 1.0f, 1.0f, 0.5f, 0.5f, -1.5f, -1.2f, 0.2f, 0.2f, -3.0f, -4.0f, 1.0f, -0.5f, 0.3f, 0.5f, 0.3f, -0.2f, 1.1f, -0.8f, 0.1f, 0.05f, -0.5f, -0.5f})
                        }, { // placeholder11
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
                        }, { // param11
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // imageInfo1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {7, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({512.0f, 512.0f, 32.0f, 32.0f, 128.0f, 256.0f, 32.0f, 32.0f, 32.0f, 32.0f, 256.0f, 256.0f, 1024.0f, 512.0f})
                        }, { // placeholder12
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
                        }, { // param12
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {8, 9, 10},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {11, 12, 13},
                            .outputs = {3}
                        }, {
                            .type = TestOperationType::AXIS_ALIGNED_BBOX_TRANSFORM,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {2, 5, 8, 11},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_2 = TestModelManager::get().add("axis_aligned_bbox_transform_all_inputs_as_internal_2", get_test_model_all_inputs_as_internal_2());

}  // namespace generated_tests::axis_aligned_bbox_transform

namespace generated_tests::axis_aligned_bbox_transform {

const TestModel& get_test_model_relaxed_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // roi1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {5, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({100.0f, 150.0f, 400.0f, 430.0f, 120.0f, 60.0f, 122.0f, 61.0f, 10.0f, 20.0f, 20.0f, 50.0f, 50.0f, 120.0f, 150.0f, 250.0f, 400.0f, 100.0f, 1000.0f, 2000.0f})
                        }, { // bboxDeltas1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.2f, 0.2f, 0.1f, 0.1f, 0.3f, -0.1f, -0.2f, 0.1f, -0.5f, 0.2f, 0.2f, -0.5f, -0.1f, -0.1f, 2.5f, 3.0f, -0.5f, -0.5f, 1.0f, 1.0f, 0.5f, 0.5f, -1.5f, -1.2f, 0.2f, 0.2f, -3.0f, -4.0f, 1.0f, -0.5f, 0.3f, 0.5f, 0.3f, -0.2f, 1.1f, -0.8f, 0.1f, 0.05f, -0.5f, -0.5f})
                        }, { // batchSplit1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 2, 5, 5, 6})
                        }, { // imageInfo1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {7, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({512.0f, 512.0f, 32.0f, 32.0f, 128.0f, 256.0f, 32.0f, 32.0f, 32.0f, 32.0f, 256.0f, 256.0f, 1024.0f, 512.0f})
                        }, { // out1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({144.22435f, 191.276062f, 475.775635f, 500.723938f, 217.190384f, 107.276062f, 462.809631f, 416.723938f, 118.778594f, 60.396736f, 121.221406f, 61.003266f, 108.617508f, 50.357232f, 132.982498f, 70.442772f, 0.0f, 0.0f, 23.59140714f, 60.77422571f, 18.88435f, 45.48208571f, 21.11565f, 54.51791429f, 117.51063714f, 209.80948286f, 122.48935143f, 212.19050857f, 132.50705143f, 12.83312286f, 255.99999571f, 227.16685714f, 0.0f, 243.1374815f, 512.0f, 1024.0f, 512.0f, 568.7958375f, 512.0f, 1024.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::AXIS_ALIGNED_BBOX_TRANSFORM,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_2 = TestModelManager::get().add("axis_aligned_bbox_transform_relaxed_2", get_test_model_relaxed_2());

}  // namespace generated_tests::axis_aligned_bbox_transform

namespace generated_tests::axis_aligned_bbox_transform {

const TestModel& get_test_model_relaxed_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // roi1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {5, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // bboxDeltas1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // batchSplit1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 2, 5, 5, 6})
                        }, { // imageInfo1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {7, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // out1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({144.22435f, 191.276062f, 475.775635f, 500.723938f, 217.190384f, 107.276062f, 462.809631f, 416.723938f, 118.778594f, 60.396736f, 121.221406f, 61.003266f, 108.617508f, 50.357232f, 132.982498f, 70.442772f, 0.0f, 0.0f, 23.59140714f, 60.77422571f, 18.88435f, 45.48208571f, 21.11565f, 54.51791429f, 117.51063714f, 209.80948286f, 122.48935143f, 212.19050857f, 132.50705143f, 12.83312286f, 255.99999571f, 227.16685714f, 0.0f, 243.1374815f, 512.0f, 1024.0f, 512.0f, 568.7958375f, 512.0f, 1024.0f})
                        }, { // roi1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {5, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({100.0f, 150.0f, 400.0f, 430.0f, 120.0f, 60.0f, 122.0f, 61.0f, 10.0f, 20.0f, 20.0f, 50.0f, 50.0f, 120.0f, 150.0f, 250.0f, 400.0f, 100.0f, 1000.0f, 2000.0f})
                        }, { // placeholder13
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
                        }, { // param13
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // bboxDeltas1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.2f, 0.2f, 0.1f, 0.1f, 0.3f, -0.1f, -0.2f, 0.1f, -0.5f, 0.2f, 0.2f, -0.5f, -0.1f, -0.1f, 2.5f, 3.0f, -0.5f, -0.5f, 1.0f, 1.0f, 0.5f, 0.5f, -1.5f, -1.2f, 0.2f, 0.2f, -3.0f, -4.0f, 1.0f, -0.5f, 0.3f, 0.5f, 0.3f, -0.2f, 1.1f, -0.8f, 0.1f, 0.05f, -0.5f, -0.5f})
                        }, { // placeholder14
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
                        }, { // param14
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // imageInfo1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {7, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({512.0f, 512.0f, 32.0f, 32.0f, 128.0f, 256.0f, 32.0f, 32.0f, 32.0f, 32.0f, 256.0f, 256.0f, 1024.0f, 512.0f})
                        }, { // placeholder15
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
                        }, { // param15
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {8, 9, 10},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {11, 12, 13},
                            .outputs = {3}
                        }, {
                            .type = TestOperationType::AXIS_ALIGNED_BBOX_TRANSFORM,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {2, 5, 8, 11},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_all_inputs_as_internal_2 = TestModelManager::get().add("axis_aligned_bbox_transform_relaxed_all_inputs_as_internal_2", get_test_model_relaxed_all_inputs_as_internal_2());

}  // namespace generated_tests::axis_aligned_bbox_transform

namespace generated_tests::axis_aligned_bbox_transform {

const TestModel& get_test_model_float16_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // roi1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {5, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({100.0f, 150.0f, 400.0f, 430.0f, 120.0f, 60.0f, 122.0f, 61.0f, 10.0f, 20.0f, 20.0f, 50.0f, 50.0f, 120.0f, 150.0f, 250.0f, 400.0f, 100.0f, 1000.0f, 2000.0f})
                        }, { // bboxDeltas1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.20000000298023224f, 0.20000000298023224f, 0.10000000149011612f, 0.10000000149011612f, 0.30000001192092896f, -0.10000000149011612f, -0.20000000298023224f, 0.10000000149011612f, -0.5f, 0.20000000298023224f, 0.20000000298023224f, -0.5f, -0.10000000149011612f, -0.10000000149011612f, 2.5f, 3.0f, -0.5f, -0.5f, 1.0f, 1.0f, 0.5f, 0.5f, -1.5f, -1.2000000476837158f, 0.20000000298023224f, 0.20000000298023224f, -3.0f, -4.0f, 1.0f, -0.5f, 0.30000001192092896f, 0.5f, 0.30000001192092896f, -0.20000000298023224f, 1.100000023841858f, -0.800000011920929f, 0.10000000149011612f, 0.05000000074505806f, -0.5f, -0.5f})
                        }, { // batchSplit1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 2, 5, 5, 6})
                        }, { // imageInfo1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {7, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({512.0f, 512.0f, 32.0f, 32.0f, 128.0f, 256.0f, 32.0f, 32.0f, 32.0f, 32.0f, 256.0f, 256.0f, 1024.0f, 512.0f})
                        }, { // out1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({144.22434997558594f, 191.27606201171875f, 475.775634765625f, 500.72393798828125f, 217.1903839111328f, 107.27606201171875f, 462.80963134765625f, 416.72393798828125f, 118.77859497070312f, 60.39673614501953f, 121.22140502929688f, 61.003265380859375f, 108.61750793457031f, 50.35723114013672f, 132.9824981689453f, 70.4427719116211f, 0.0f, 0.0f, 23.591407775878906f, 60.774227142333984f, 18.884349822998047f, 45.482086181640625f, 21.115650177001953f, 54.517913818359375f, 117.51063537597656f, 209.80947875976562f, 122.48934936523438f, 212.1905059814453f, 132.50704956054688f, 12.833123207092285f, 256.0f, 227.16685485839844f, 0.0f, 243.13748168945312f, 512.0f, 1024.0f, 512.0f, 568.7958374023438f, 512.0f, 1024.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::AXIS_ALIGNED_BBOX_TRANSFORM,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_2 = TestModelManager::get().add("axis_aligned_bbox_transform_float16_2", get_test_model_float16_2());

}  // namespace generated_tests::axis_aligned_bbox_transform

namespace generated_tests::axis_aligned_bbox_transform {

const TestModel& get_test_model_float16_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // roi1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {5, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // bboxDeltas1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // batchSplit1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 2, 5, 5, 6})
                        }, { // imageInfo1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {7, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // out1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({144.22434997558594f, 191.27606201171875f, 475.775634765625f, 500.72393798828125f, 217.1903839111328f, 107.27606201171875f, 462.80963134765625f, 416.72393798828125f, 118.77859497070312f, 60.39673614501953f, 121.22140502929688f, 61.003265380859375f, 108.61750793457031f, 50.35723114013672f, 132.9824981689453f, 70.4427719116211f, 0.0f, 0.0f, 23.591407775878906f, 60.774227142333984f, 18.884349822998047f, 45.482086181640625f, 21.115650177001953f, 54.517913818359375f, 117.51063537597656f, 209.80947875976562f, 122.48934936523438f, 212.1905059814453f, 132.50704956054688f, 12.833123207092285f, 256.0f, 227.16685485839844f, 0.0f, 243.13748168945312f, 512.0f, 1024.0f, 512.0f, 568.7958374023438f, 512.0f, 1024.0f})
                        }, { // roi1_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {5, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({100.0f, 150.0f, 400.0f, 430.0f, 120.0f, 60.0f, 122.0f, 61.0f, 10.0f, 20.0f, 20.0f, 50.0f, 50.0f, 120.0f, 150.0f, 250.0f, 400.0f, 100.0f, 1000.0f, 2000.0f})
                        }, { // placeholder16
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
                        }, { // param16
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // bboxDeltas1_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.20000000298023224f, 0.20000000298023224f, 0.10000000149011612f, 0.10000000149011612f, 0.30000001192092896f, -0.10000000149011612f, -0.20000000298023224f, 0.10000000149011612f, -0.5f, 0.20000000298023224f, 0.20000000298023224f, -0.5f, -0.10000000149011612f, -0.10000000149011612f, 2.5f, 3.0f, -0.5f, -0.5f, 1.0f, 1.0f, 0.5f, 0.5f, -1.5f, -1.2000000476837158f, 0.20000000298023224f, 0.20000000298023224f, -3.0f, -4.0f, 1.0f, -0.5f, 0.30000001192092896f, 0.5f, 0.30000001192092896f, -0.20000000298023224f, 1.100000023841858f, -0.800000011920929f, 0.10000000149011612f, 0.05000000074505806f, -0.5f, -0.5f})
                        }, { // placeholder17
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
                        }, { // param17
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // imageInfo1_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {7, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({512.0f, 512.0f, 32.0f, 32.0f, 128.0f, 256.0f, 32.0f, 32.0f, 32.0f, 32.0f, 256.0f, 256.0f, 1024.0f, 512.0f})
                        }, { // placeholder18
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
                        }, { // param18
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {8, 9, 10},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {11, 12, 13},
                            .outputs = {3}
                        }, {
                            .type = TestOperationType::AXIS_ALIGNED_BBOX_TRANSFORM,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {2, 5, 8, 11},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_all_inputs_as_internal_2 = TestModelManager::get().add("axis_aligned_bbox_transform_float16_all_inputs_as_internal_2", get_test_model_float16_all_inputs_as_internal_2());

}  // namespace generated_tests::axis_aligned_bbox_transform

namespace generated_tests::axis_aligned_bbox_transform {

const TestModel& get_test_model_quant8_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // roi1
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {5, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({800, 1200, 3200, 3440, 960, 480, 976, 488, 80, 160, 160, 400, 400, 960, 1200, 2000, 3200, 800, 8000, 16000})
                        }, { // bboxDeltas1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.05f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({132, 132, 130, 130, 134, 126, 124, 130, 118, 132, 132, 118, 126, 126, 178, 188, 118, 118, 148, 148, 138, 138, 98, 104, 132, 132, 68, 48, 148, 118, 134, 138, 134, 124, 150, 112, 130, 129, 118, 118})
                        }, { // batchSplit1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 2, 5, 5, 6})
                        }, { // imageInfo1
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {7, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({4096, 4096, 256, 256, 1024, 2048, 256, 256, 256, 256, 2048, 2048, 8192, 4096})
                        }, { // out1
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 0,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({1154, 1530, 3806, 4006, 1738, 858, 3702, 3334, 950, 483, 970, 488, 869, 403, 1064, 564, 0, 0, 189, 486, 151, 364, 169, 436, 940, 1678, 980, 1698, 1060, 103, 2048, 1817, 0, 1945, 4096, 8192, 4096, 4550, 4096, 8192})
                        }},
                .operations = {{
                            .type = TestOperationType::AXIS_ALIGNED_BBOX_TRANSFORM,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_2 = TestModelManager::get().add("axis_aligned_bbox_transform_quant8_2", get_test_model_quant8_2());

}  // namespace generated_tests::axis_aligned_bbox_transform

namespace generated_tests::axis_aligned_bbox_transform {

const TestModel& get_test_model_quant8_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // roi1
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {5, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({800, 1200, 3200, 3440, 960, 480, 976, 488, 80, 160, 160, 400, 400, 960, 1200, 2000, 3200, 800, 8000, 16000})
                        }, { // bboxDeltas1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.05f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // batchSplit1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 2, 5, 5, 6})
                        }, { // imageInfo1
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {7, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({4096, 4096, 256, 256, 1024, 2048, 256, 256, 256, 256, 2048, 2048, 8192, 4096})
                        }, { // out1
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 0,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({1154, 1530, 3806, 4006, 1738, 858, 3702, 3334, 950, 483, 970, 488, 869, 403, 1064, 564, 0, 0, 189, 486, 151, 364, 169, 436, 940, 1678, 980, 1698, 1060, 103, 2048, 1817, 0, 1945, 4096, 8192, 4096, 4550, 4096, 8192})
                        }, { // bboxDeltas1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.05f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({132, 132, 130, 130, 134, 126, 124, 130, 118, 132, 132, 118, 126, 126, 178, 188, 118, 118, 148, 148, 138, 138, 98, 104, 132, 132, 68, 48, 148, 118, 134, 138, 134, 124, 150, 112, 130, 129, 118, 118})
                        }, { // placeholder19
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.05f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
                        }, { // param19
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {5, 6, 7},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::AXIS_ALIGNED_BBOX_TRANSFORM,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 2, 3, 5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_all_inputs_as_internal_2 = TestModelManager::get().add("axis_aligned_bbox_transform_quant8_all_inputs_as_internal_2", get_test_model_quant8_all_inputs_as_internal_2());

}  // namespace generated_tests::axis_aligned_bbox_transform

