// Generated from box_with_nms_limit_linear.mod.py
// DO NOT EDIT
// clang-format off
#include "TestHarness.h"
using namespace test_helper;  // NOLINT(google-build-using-namespace)

namespace generated_tests::box_with_nms_limit_linear {

const TestModel& get_test_model() {
    static TestModel model = {
        .main = {
                .operands = {{ // scores
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {19, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.9f, 0.95f, 0.75f, 0.8f, 0.7f, 0.85f, 0.6f, 0.9f, 0.95f, 0.9f, 0.65f, 0.9f, 0.8f, 0.85f, 0.8f, 0.6f, 0.6f, 0.2f, 0.6f, 0.8f, 0.4f, 0.9f, 0.55f, 0.6f, 0.9f, 0.75f, 0.7f, 0.8f, 0.7f, 0.85f, 0.9f, 0.95f, 0.75f, 0.8f, 0.85f, 0.8f, 0.6f, 0.9f, 0.95f, 0.6f, 0.6f, 0.2f, 0.5f, 0.9f, 0.8f, 0.9f, 0.75f, 0.7f, 0.9f, 0.65f, 0.9f, 0.9f, 0.55f, 0.6f, 0.6f, 0.8f, 0.4f})
                        }, { // roi
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {19, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 1.0f, 10.0f, 10.0f, 0.0f, 0.0f, 10.0f, 10.0f, 0.0f, 0.0f, 10.0f, 10.0f, 2.0f, 2.0f, 11.0f, 11.0f, 1.0f, 1.0f, 11.0f, 11.0f, 1.0f, 1.0f, 11.0f, 11.0f, 3.0f, 3.0f, 12.0f, 12.0f, 2.0f, 2.0f, 12.0f, 12.0f, 2.0f, 2.0f, 12.0f, 12.0f, 4.0f, 4.0f, 13.0f, 13.0f, 3.0f, 3.0f, 13.0f, 13.0f, 3.0f, 3.0f, 13.0f, 13.0f, 5.0f, 5.0f, 14.0f, 14.0f, 4.0f, 4.0f, 14.0f, 14.0f, 4.0f, 4.0f, 14.0f, 14.0f, 6.0f, 6.0f, 15.0f, 15.0f, 5.0f, 5.0f, 15.0f, 15.0f, 5.0f, 5.0f, 15.0f, 15.0f, 7.0f, 7.0f, 16.0f, 16.0f, 6.0f, 6.0f, 16.0f, 16.0f, 6.0f, 6.0f, 16.0f, 16.0f, 8.0f, 8.0f, 17.0f, 17.0f, 7.0f, 7.0f, 17.0f, 17.0f, 7.0f, 7.0f, 17.0f, 17.0f, 9.0f, 9.0f, 18.0f, 18.0f, 8.0f, 8.0f, 18.0f, 18.0f, 8.0f, 8.0f, 18.0f, 18.0f, 2.0f, 2.0f, 11.0f, 11.0f, 2.0f, 2.0f, 12.0f, 12.0f, 2.0f, 2.0f, 12.0f, 12.0f, 1.0f, 1.0f, 10.0f, 10.0f, 1.0f, 1.0f, 11.0f, 11.0f, 1.0f, 1.0f, 11.0f, 11.0f, 5.0f, 5.0f, 14.0f, 14.0f, 5.0f, 5.0f, 15.0f, 15.0f, 5.0f, 5.0f, 15.0f, 15.0f, 3.0f, 3.0f, 12.0f, 12.0f, 3.0f, 3.0f, 13.0f, 13.0f, 3.0f, 3.0f, 13.0f, 13.0f, 6.0f, 6.0f, 15.0f, 15.0f, 6.0f, 6.0f, 16.0f, 16.0f, 6.0f, 6.0f, 16.0f, 16.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 2.0f, 2.0f, 0.0f, 0.0f, 2.0f, 2.0f, 9.0f, 9.0f, 18.0f, 18.0f, 9.0f, 9.0f, 19.0f, 19.0f, 9.0f, 9.0f, 19.0f, 19.0f, 4.0f, 4.0f, 13.0f, 13.0f, 4.0f, 4.0f, 14.0f, 14.0f, 4.0f, 4.0f, 14.0f, 14.0f, 8.0f, 8.0f, 17.0f, 17.0f, 8.0f, 8.0f, 18.0f, 18.0f, 8.0f, 8.0f, 18.0f, 18.0f, 7.0f, 7.0f, 16.0f, 16.0f, 7.0f, 7.0f, 17.0f, 17.0f, 7.0f, 7.0f, 17.0f, 17.0f})
                        }, { // batchSplit
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {19},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1})
                        }, { // param
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // param1
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-1})
                        }, { // param2
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param3
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.4f})
                        }, { // param4
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f})
                        }, { // param5
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // scoresOut
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {16},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.95f, 0.85f, 0.75f, 0.95f, 0.7f, 0.42352945f, 0.39705884f, 0.95f, 0.9f, 0.85f, 0.75f, 0.95f, 0.8f, 0.7f, 0.42352945f, 0.39705884f})
                        }, { // roiOut
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {16, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 10.0f, 10.0f, 4.0f, 4.0f, 14.0f, 14.0f, 8.0f, 8.0f, 18.0f, 18.0f, 2.0f, 2.0f, 12.0f, 12.0f, 8.0f, 8.0f, 18.0f, 18.0f, 4.0f, 4.0f, 14.0f, 14.0f, 0.0f, 0.0f, 10.0f, 10.0f, 1.0f, 1.0f, 11.0f, 11.0f, 0.0f, 0.0f, 2.0f, 2.0f, 5.0f, 5.0f, 15.0f, 15.0f, 9.0f, 9.0f, 19.0f, 19.0f, 3.0f, 3.0f, 13.0f, 13.0f, 0.0f, 0.0f, 2.0f, 2.0f, 9.0f, 9.0f, 19.0f, 19.0f, 5.0f, 5.0f, 15.0f, 15.0f, 1.0f, 1.0f, 11.0f, 11.0f})
                        }, { // classesOut
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {16},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 1, 2, 2, 2, 2, 1, 1, 1, 1, 2, 2, 2, 2, 2})
                        }, { // batchSplitOut
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {16},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1})
                        }},
                .operations = {{
                            .type = TestOperationType::BOX_WITH_NMS_LIMIT,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8},
                            .outputs = {9, 10, 11, 12}
                        }},
                .inputIndexes = {0, 1, 2},
                .outputIndexes = {9, 10, 11, 12}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model = TestModelManager::get().add("box_with_nms_limit_linear", get_test_model());

}  // namespace generated_tests::box_with_nms_limit_linear

namespace generated_tests::box_with_nms_limit_linear {

const TestModel& get_test_model_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // scores
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {19, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // roi
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {19, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // batchSplit
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {19},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1})
                        }, { // param
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // param1
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-1})
                        }, { // param2
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param3
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.4f})
                        }, { // param4
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f})
                        }, { // param5
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // scoresOut
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {16},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.95f, 0.85f, 0.75f, 0.95f, 0.7f, 0.42352945f, 0.39705884f, 0.95f, 0.9f, 0.85f, 0.75f, 0.95f, 0.8f, 0.7f, 0.42352945f, 0.39705884f})
                        }, { // roiOut
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {16, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 10.0f, 10.0f, 4.0f, 4.0f, 14.0f, 14.0f, 8.0f, 8.0f, 18.0f, 18.0f, 2.0f, 2.0f, 12.0f, 12.0f, 8.0f, 8.0f, 18.0f, 18.0f, 4.0f, 4.0f, 14.0f, 14.0f, 0.0f, 0.0f, 10.0f, 10.0f, 1.0f, 1.0f, 11.0f, 11.0f, 0.0f, 0.0f, 2.0f, 2.0f, 5.0f, 5.0f, 15.0f, 15.0f, 9.0f, 9.0f, 19.0f, 19.0f, 3.0f, 3.0f, 13.0f, 13.0f, 0.0f, 0.0f, 2.0f, 2.0f, 9.0f, 9.0f, 19.0f, 19.0f, 5.0f, 5.0f, 15.0f, 15.0f, 1.0f, 1.0f, 11.0f, 11.0f})
                        }, { // classesOut
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {16},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 1, 2, 2, 2, 2, 1, 1, 1, 1, 2, 2, 2, 2, 2})
                        }, { // batchSplitOut
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {16},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1})
                        }, { // scores_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {19, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.9f, 0.95f, 0.75f, 0.8f, 0.7f, 0.85f, 0.6f, 0.9f, 0.95f, 0.9f, 0.65f, 0.9f, 0.8f, 0.85f, 0.8f, 0.6f, 0.6f, 0.2f, 0.6f, 0.8f, 0.4f, 0.9f, 0.55f, 0.6f, 0.9f, 0.75f, 0.7f, 0.8f, 0.7f, 0.85f, 0.9f, 0.95f, 0.75f, 0.8f, 0.85f, 0.8f, 0.6f, 0.9f, 0.95f, 0.6f, 0.6f, 0.2f, 0.5f, 0.9f, 0.8f, 0.9f, 0.75f, 0.7f, 0.9f, 0.65f, 0.9f, 0.9f, 0.55f, 0.6f, 0.6f, 0.8f, 0.4f})
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
                        }, { // roi_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {19, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 1.0f, 10.0f, 10.0f, 0.0f, 0.0f, 10.0f, 10.0f, 0.0f, 0.0f, 10.0f, 10.0f, 2.0f, 2.0f, 11.0f, 11.0f, 1.0f, 1.0f, 11.0f, 11.0f, 1.0f, 1.0f, 11.0f, 11.0f, 3.0f, 3.0f, 12.0f, 12.0f, 2.0f, 2.0f, 12.0f, 12.0f, 2.0f, 2.0f, 12.0f, 12.0f, 4.0f, 4.0f, 13.0f, 13.0f, 3.0f, 3.0f, 13.0f, 13.0f, 3.0f, 3.0f, 13.0f, 13.0f, 5.0f, 5.0f, 14.0f, 14.0f, 4.0f, 4.0f, 14.0f, 14.0f, 4.0f, 4.0f, 14.0f, 14.0f, 6.0f, 6.0f, 15.0f, 15.0f, 5.0f, 5.0f, 15.0f, 15.0f, 5.0f, 5.0f, 15.0f, 15.0f, 7.0f, 7.0f, 16.0f, 16.0f, 6.0f, 6.0f, 16.0f, 16.0f, 6.0f, 6.0f, 16.0f, 16.0f, 8.0f, 8.0f, 17.0f, 17.0f, 7.0f, 7.0f, 17.0f, 17.0f, 7.0f, 7.0f, 17.0f, 17.0f, 9.0f, 9.0f, 18.0f, 18.0f, 8.0f, 8.0f, 18.0f, 18.0f, 8.0f, 8.0f, 18.0f, 18.0f, 2.0f, 2.0f, 11.0f, 11.0f, 2.0f, 2.0f, 12.0f, 12.0f, 2.0f, 2.0f, 12.0f, 12.0f, 1.0f, 1.0f, 10.0f, 10.0f, 1.0f, 1.0f, 11.0f, 11.0f, 1.0f, 1.0f, 11.0f, 11.0f, 5.0f, 5.0f, 14.0f, 14.0f, 5.0f, 5.0f, 15.0f, 15.0f, 5.0f, 5.0f, 15.0f, 15.0f, 3.0f, 3.0f, 12.0f, 12.0f, 3.0f, 3.0f, 13.0f, 13.0f, 3.0f, 3.0f, 13.0f, 13.0f, 6.0f, 6.0f, 15.0f, 15.0f, 6.0f, 6.0f, 16.0f, 16.0f, 6.0f, 6.0f, 16.0f, 16.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 2.0f, 2.0f, 0.0f, 0.0f, 2.0f, 2.0f, 9.0f, 9.0f, 18.0f, 18.0f, 9.0f, 9.0f, 19.0f, 19.0f, 9.0f, 9.0f, 19.0f, 19.0f, 4.0f, 4.0f, 13.0f, 13.0f, 4.0f, 4.0f, 14.0f, 14.0f, 4.0f, 4.0f, 14.0f, 14.0f, 8.0f, 8.0f, 17.0f, 17.0f, 8.0f, 8.0f, 18.0f, 18.0f, 8.0f, 8.0f, 18.0f, 18.0f, 7.0f, 7.0f, 16.0f, 16.0f, 7.0f, 7.0f, 17.0f, 17.0f, 7.0f, 7.0f, 17.0f, 17.0f})
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
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {13, 14, 15},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {16, 17, 18},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::BOX_WITH_NMS_LIMIT,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8},
                            .outputs = {9, 10, 11, 12}
                        }},
                .inputIndexes = {2, 13, 16},
                .outputIndexes = {9, 10, 11, 12}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal = TestModelManager::get().add("box_with_nms_limit_linear_all_inputs_as_internal", get_test_model_all_inputs_as_internal());

}  // namespace generated_tests::box_with_nms_limit_linear

namespace generated_tests::box_with_nms_limit_linear {

const TestModel& get_test_model_relaxed() {
    static TestModel model = {
        .main = {
                .operands = {{ // scores
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {19, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.9f, 0.95f, 0.75f, 0.8f, 0.7f, 0.85f, 0.6f, 0.9f, 0.95f, 0.9f, 0.65f, 0.9f, 0.8f, 0.85f, 0.8f, 0.6f, 0.6f, 0.2f, 0.6f, 0.8f, 0.4f, 0.9f, 0.55f, 0.6f, 0.9f, 0.75f, 0.7f, 0.8f, 0.7f, 0.85f, 0.9f, 0.95f, 0.75f, 0.8f, 0.85f, 0.8f, 0.6f, 0.9f, 0.95f, 0.6f, 0.6f, 0.2f, 0.5f, 0.9f, 0.8f, 0.9f, 0.75f, 0.7f, 0.9f, 0.65f, 0.9f, 0.9f, 0.55f, 0.6f, 0.6f, 0.8f, 0.4f})
                        }, { // roi
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {19, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 1.0f, 10.0f, 10.0f, 0.0f, 0.0f, 10.0f, 10.0f, 0.0f, 0.0f, 10.0f, 10.0f, 2.0f, 2.0f, 11.0f, 11.0f, 1.0f, 1.0f, 11.0f, 11.0f, 1.0f, 1.0f, 11.0f, 11.0f, 3.0f, 3.0f, 12.0f, 12.0f, 2.0f, 2.0f, 12.0f, 12.0f, 2.0f, 2.0f, 12.0f, 12.0f, 4.0f, 4.0f, 13.0f, 13.0f, 3.0f, 3.0f, 13.0f, 13.0f, 3.0f, 3.0f, 13.0f, 13.0f, 5.0f, 5.0f, 14.0f, 14.0f, 4.0f, 4.0f, 14.0f, 14.0f, 4.0f, 4.0f, 14.0f, 14.0f, 6.0f, 6.0f, 15.0f, 15.0f, 5.0f, 5.0f, 15.0f, 15.0f, 5.0f, 5.0f, 15.0f, 15.0f, 7.0f, 7.0f, 16.0f, 16.0f, 6.0f, 6.0f, 16.0f, 16.0f, 6.0f, 6.0f, 16.0f, 16.0f, 8.0f, 8.0f, 17.0f, 17.0f, 7.0f, 7.0f, 17.0f, 17.0f, 7.0f, 7.0f, 17.0f, 17.0f, 9.0f, 9.0f, 18.0f, 18.0f, 8.0f, 8.0f, 18.0f, 18.0f, 8.0f, 8.0f, 18.0f, 18.0f, 2.0f, 2.0f, 11.0f, 11.0f, 2.0f, 2.0f, 12.0f, 12.0f, 2.0f, 2.0f, 12.0f, 12.0f, 1.0f, 1.0f, 10.0f, 10.0f, 1.0f, 1.0f, 11.0f, 11.0f, 1.0f, 1.0f, 11.0f, 11.0f, 5.0f, 5.0f, 14.0f, 14.0f, 5.0f, 5.0f, 15.0f, 15.0f, 5.0f, 5.0f, 15.0f, 15.0f, 3.0f, 3.0f, 12.0f, 12.0f, 3.0f, 3.0f, 13.0f, 13.0f, 3.0f, 3.0f, 13.0f, 13.0f, 6.0f, 6.0f, 15.0f, 15.0f, 6.0f, 6.0f, 16.0f, 16.0f, 6.0f, 6.0f, 16.0f, 16.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 2.0f, 2.0f, 0.0f, 0.0f, 2.0f, 2.0f, 9.0f, 9.0f, 18.0f, 18.0f, 9.0f, 9.0f, 19.0f, 19.0f, 9.0f, 9.0f, 19.0f, 19.0f, 4.0f, 4.0f, 13.0f, 13.0f, 4.0f, 4.0f, 14.0f, 14.0f, 4.0f, 4.0f, 14.0f, 14.0f, 8.0f, 8.0f, 17.0f, 17.0f, 8.0f, 8.0f, 18.0f, 18.0f, 8.0f, 8.0f, 18.0f, 18.0f, 7.0f, 7.0f, 16.0f, 16.0f, 7.0f, 7.0f, 17.0f, 17.0f, 7.0f, 7.0f, 17.0f, 17.0f})
                        }, { // batchSplit
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {19},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1})
                        }, { // param
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // param1
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-1})
                        }, { // param2
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param3
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.4f})
                        }, { // param4
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f})
                        }, { // param5
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // scoresOut
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {16},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.95f, 0.85f, 0.75f, 0.95f, 0.7f, 0.42352945f, 0.39705884f, 0.95f, 0.9f, 0.85f, 0.75f, 0.95f, 0.8f, 0.7f, 0.42352945f, 0.39705884f})
                        }, { // roiOut
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {16, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 10.0f, 10.0f, 4.0f, 4.0f, 14.0f, 14.0f, 8.0f, 8.0f, 18.0f, 18.0f, 2.0f, 2.0f, 12.0f, 12.0f, 8.0f, 8.0f, 18.0f, 18.0f, 4.0f, 4.0f, 14.0f, 14.0f, 0.0f, 0.0f, 10.0f, 10.0f, 1.0f, 1.0f, 11.0f, 11.0f, 0.0f, 0.0f, 2.0f, 2.0f, 5.0f, 5.0f, 15.0f, 15.0f, 9.0f, 9.0f, 19.0f, 19.0f, 3.0f, 3.0f, 13.0f, 13.0f, 0.0f, 0.0f, 2.0f, 2.0f, 9.0f, 9.0f, 19.0f, 19.0f, 5.0f, 5.0f, 15.0f, 15.0f, 1.0f, 1.0f, 11.0f, 11.0f})
                        }, { // classesOut
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {16},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 1, 2, 2, 2, 2, 1, 1, 1, 1, 2, 2, 2, 2, 2})
                        }, { // batchSplitOut
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {16},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1})
                        }},
                .operations = {{
                            .type = TestOperationType::BOX_WITH_NMS_LIMIT,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8},
                            .outputs = {9, 10, 11, 12}
                        }},
                .inputIndexes = {0, 1, 2},
                .outputIndexes = {9, 10, 11, 12}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed = TestModelManager::get().add("box_with_nms_limit_linear_relaxed", get_test_model_relaxed());

}  // namespace generated_tests::box_with_nms_limit_linear

namespace generated_tests::box_with_nms_limit_linear {

const TestModel& get_test_model_relaxed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // scores
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {19, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // roi
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {19, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // batchSplit
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {19},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1})
                        }, { // param
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // param1
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-1})
                        }, { // param2
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param3
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.4f})
                        }, { // param4
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f})
                        }, { // param5
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // scoresOut
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {16},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.95f, 0.85f, 0.75f, 0.95f, 0.7f, 0.42352945f, 0.39705884f, 0.95f, 0.9f, 0.85f, 0.75f, 0.95f, 0.8f, 0.7f, 0.42352945f, 0.39705884f})
                        }, { // roiOut
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {16, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 10.0f, 10.0f, 4.0f, 4.0f, 14.0f, 14.0f, 8.0f, 8.0f, 18.0f, 18.0f, 2.0f, 2.0f, 12.0f, 12.0f, 8.0f, 8.0f, 18.0f, 18.0f, 4.0f, 4.0f, 14.0f, 14.0f, 0.0f, 0.0f, 10.0f, 10.0f, 1.0f, 1.0f, 11.0f, 11.0f, 0.0f, 0.0f, 2.0f, 2.0f, 5.0f, 5.0f, 15.0f, 15.0f, 9.0f, 9.0f, 19.0f, 19.0f, 3.0f, 3.0f, 13.0f, 13.0f, 0.0f, 0.0f, 2.0f, 2.0f, 9.0f, 9.0f, 19.0f, 19.0f, 5.0f, 5.0f, 15.0f, 15.0f, 1.0f, 1.0f, 11.0f, 11.0f})
                        }, { // classesOut
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {16},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 1, 2, 2, 2, 2, 1, 1, 1, 1, 2, 2, 2, 2, 2})
                        }, { // batchSplitOut
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {16},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1})
                        }, { // scores_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {19, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.9f, 0.95f, 0.75f, 0.8f, 0.7f, 0.85f, 0.6f, 0.9f, 0.95f, 0.9f, 0.65f, 0.9f, 0.8f, 0.85f, 0.8f, 0.6f, 0.6f, 0.2f, 0.6f, 0.8f, 0.4f, 0.9f, 0.55f, 0.6f, 0.9f, 0.75f, 0.7f, 0.8f, 0.7f, 0.85f, 0.9f, 0.95f, 0.75f, 0.8f, 0.85f, 0.8f, 0.6f, 0.9f, 0.95f, 0.6f, 0.6f, 0.2f, 0.5f, 0.9f, 0.8f, 0.9f, 0.75f, 0.7f, 0.9f, 0.65f, 0.9f, 0.9f, 0.55f, 0.6f, 0.6f, 0.8f, 0.4f})
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
                        }, { // roi_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {19, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 1.0f, 10.0f, 10.0f, 0.0f, 0.0f, 10.0f, 10.0f, 0.0f, 0.0f, 10.0f, 10.0f, 2.0f, 2.0f, 11.0f, 11.0f, 1.0f, 1.0f, 11.0f, 11.0f, 1.0f, 1.0f, 11.0f, 11.0f, 3.0f, 3.0f, 12.0f, 12.0f, 2.0f, 2.0f, 12.0f, 12.0f, 2.0f, 2.0f, 12.0f, 12.0f, 4.0f, 4.0f, 13.0f, 13.0f, 3.0f, 3.0f, 13.0f, 13.0f, 3.0f, 3.0f, 13.0f, 13.0f, 5.0f, 5.0f, 14.0f, 14.0f, 4.0f, 4.0f, 14.0f, 14.0f, 4.0f, 4.0f, 14.0f, 14.0f, 6.0f, 6.0f, 15.0f, 15.0f, 5.0f, 5.0f, 15.0f, 15.0f, 5.0f, 5.0f, 15.0f, 15.0f, 7.0f, 7.0f, 16.0f, 16.0f, 6.0f, 6.0f, 16.0f, 16.0f, 6.0f, 6.0f, 16.0f, 16.0f, 8.0f, 8.0f, 17.0f, 17.0f, 7.0f, 7.0f, 17.0f, 17.0f, 7.0f, 7.0f, 17.0f, 17.0f, 9.0f, 9.0f, 18.0f, 18.0f, 8.0f, 8.0f, 18.0f, 18.0f, 8.0f, 8.0f, 18.0f, 18.0f, 2.0f, 2.0f, 11.0f, 11.0f, 2.0f, 2.0f, 12.0f, 12.0f, 2.0f, 2.0f, 12.0f, 12.0f, 1.0f, 1.0f, 10.0f, 10.0f, 1.0f, 1.0f, 11.0f, 11.0f, 1.0f, 1.0f, 11.0f, 11.0f, 5.0f, 5.0f, 14.0f, 14.0f, 5.0f, 5.0f, 15.0f, 15.0f, 5.0f, 5.0f, 15.0f, 15.0f, 3.0f, 3.0f, 12.0f, 12.0f, 3.0f, 3.0f, 13.0f, 13.0f, 3.0f, 3.0f, 13.0f, 13.0f, 6.0f, 6.0f, 15.0f, 15.0f, 6.0f, 6.0f, 16.0f, 16.0f, 6.0f, 6.0f, 16.0f, 16.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 2.0f, 2.0f, 0.0f, 0.0f, 2.0f, 2.0f, 9.0f, 9.0f, 18.0f, 18.0f, 9.0f, 9.0f, 19.0f, 19.0f, 9.0f, 9.0f, 19.0f, 19.0f, 4.0f, 4.0f, 13.0f, 13.0f, 4.0f, 4.0f, 14.0f, 14.0f, 4.0f, 4.0f, 14.0f, 14.0f, 8.0f, 8.0f, 17.0f, 17.0f, 8.0f, 8.0f, 18.0f, 18.0f, 8.0f, 8.0f, 18.0f, 18.0f, 7.0f, 7.0f, 16.0f, 16.0f, 7.0f, 7.0f, 17.0f, 17.0f, 7.0f, 7.0f, 17.0f, 17.0f})
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
                            .inputs = {13, 14, 15},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {16, 17, 18},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::BOX_WITH_NMS_LIMIT,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8},
                            .outputs = {9, 10, 11, 12}
                        }},
                .inputIndexes = {2, 13, 16},
                .outputIndexes = {9, 10, 11, 12}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_all_inputs_as_internal = TestModelManager::get().add("box_with_nms_limit_linear_relaxed_all_inputs_as_internal", get_test_model_relaxed_all_inputs_as_internal());

}  // namespace generated_tests::box_with_nms_limit_linear

namespace generated_tests::box_with_nms_limit_linear {

const TestModel& get_test_model_float16() {
    static TestModel model = {
        .main = {
                .operands = {{ // scores
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {19, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.8999999761581421f, 0.949999988079071f, 0.75f, 0.800000011920929f, 0.699999988079071f, 0.8500000238418579f, 0.6000000238418579f, 0.8999999761581421f, 0.949999988079071f, 0.8999999761581421f, 0.6499999761581421f, 0.8999999761581421f, 0.800000011920929f, 0.8500000238418579f, 0.800000011920929f, 0.6000000238418579f, 0.6000000238418579f, 0.20000000298023224f, 0.6000000238418579f, 0.800000011920929f, 0.4000000059604645f, 0.8999999761581421f, 0.550000011920929f, 0.6000000238418579f, 0.8999999761581421f, 0.75f, 0.699999988079071f, 0.800000011920929f, 0.699999988079071f, 0.8500000238418579f, 0.8999999761581421f, 0.949999988079071f, 0.75f, 0.800000011920929f, 0.8500000238418579f, 0.800000011920929f, 0.6000000238418579f, 0.8999999761581421f, 0.949999988079071f, 0.6000000238418579f, 0.6000000238418579f, 0.20000000298023224f, 0.5f, 0.8999999761581421f, 0.800000011920929f, 0.8999999761581421f, 0.75f, 0.699999988079071f, 0.8999999761581421f, 0.6499999761581421f, 0.8999999761581421f, 0.8999999761581421f, 0.550000011920929f, 0.6000000238418579f, 0.6000000238418579f, 0.800000011920929f, 0.4000000059604645f})
                        }, { // roi
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {19, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f, 1.0f, 10.0f, 10.0f, 0.0f, 0.0f, 10.0f, 10.0f, 0.0f, 0.0f, 10.0f, 10.0f, 2.0f, 2.0f, 11.0f, 11.0f, 1.0f, 1.0f, 11.0f, 11.0f, 1.0f, 1.0f, 11.0f, 11.0f, 3.0f, 3.0f, 12.0f, 12.0f, 2.0f, 2.0f, 12.0f, 12.0f, 2.0f, 2.0f, 12.0f, 12.0f, 4.0f, 4.0f, 13.0f, 13.0f, 3.0f, 3.0f, 13.0f, 13.0f, 3.0f, 3.0f, 13.0f, 13.0f, 5.0f, 5.0f, 14.0f, 14.0f, 4.0f, 4.0f, 14.0f, 14.0f, 4.0f, 4.0f, 14.0f, 14.0f, 6.0f, 6.0f, 15.0f, 15.0f, 5.0f, 5.0f, 15.0f, 15.0f, 5.0f, 5.0f, 15.0f, 15.0f, 7.0f, 7.0f, 16.0f, 16.0f, 6.0f, 6.0f, 16.0f, 16.0f, 6.0f, 6.0f, 16.0f, 16.0f, 8.0f, 8.0f, 17.0f, 17.0f, 7.0f, 7.0f, 17.0f, 17.0f, 7.0f, 7.0f, 17.0f, 17.0f, 9.0f, 9.0f, 18.0f, 18.0f, 8.0f, 8.0f, 18.0f, 18.0f, 8.0f, 8.0f, 18.0f, 18.0f, 2.0f, 2.0f, 11.0f, 11.0f, 2.0f, 2.0f, 12.0f, 12.0f, 2.0f, 2.0f, 12.0f, 12.0f, 1.0f, 1.0f, 10.0f, 10.0f, 1.0f, 1.0f, 11.0f, 11.0f, 1.0f, 1.0f, 11.0f, 11.0f, 5.0f, 5.0f, 14.0f, 14.0f, 5.0f, 5.0f, 15.0f, 15.0f, 5.0f, 5.0f, 15.0f, 15.0f, 3.0f, 3.0f, 12.0f, 12.0f, 3.0f, 3.0f, 13.0f, 13.0f, 3.0f, 3.0f, 13.0f, 13.0f, 6.0f, 6.0f, 15.0f, 15.0f, 6.0f, 6.0f, 16.0f, 16.0f, 6.0f, 6.0f, 16.0f, 16.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 2.0f, 2.0f, 0.0f, 0.0f, 2.0f, 2.0f, 9.0f, 9.0f, 18.0f, 18.0f, 9.0f, 9.0f, 19.0f, 19.0f, 9.0f, 9.0f, 19.0f, 19.0f, 4.0f, 4.0f, 13.0f, 13.0f, 4.0f, 4.0f, 14.0f, 14.0f, 4.0f, 4.0f, 14.0f, 14.0f, 8.0f, 8.0f, 17.0f, 17.0f, 8.0f, 8.0f, 18.0f, 18.0f, 8.0f, 8.0f, 18.0f, 18.0f, 7.0f, 7.0f, 16.0f, 16.0f, 7.0f, 7.0f, 17.0f, 17.0f, 7.0f, 7.0f, 17.0f, 17.0f})
                        }, { // batchSplit
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {19},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1})
                        }, { // param
                            .type = TestOperandType::FLOAT16,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.30000001192092896f})
                        }, { // param1
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-1})
                        }, { // param2
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param3
                            .type = TestOperandType::FLOAT16,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.4000000059604645f})
                        }, { // param4
                            .type = TestOperandType::FLOAT16,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f})
                        }, { // param5
                            .type = TestOperandType::FLOAT16,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.30000001192092896f})
                        }, { // scoresOut
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {16},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.949999988079071f, 0.8500000238418579f, 0.75f, 0.949999988079071f, 0.699999988079071f, 0.4235294461250305f, 0.3970588445663452f, 0.949999988079071f, 0.8999999761581421f, 0.8500000238418579f, 0.75f, 0.949999988079071f, 0.800000011920929f, 0.699999988079071f, 0.4235294461250305f, 0.3970588445663452f})
                        }, { // roiOut
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {16, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 0.0f, 10.0f, 10.0f, 4.0f, 4.0f, 14.0f, 14.0f, 8.0f, 8.0f, 18.0f, 18.0f, 2.0f, 2.0f, 12.0f, 12.0f, 8.0f, 8.0f, 18.0f, 18.0f, 4.0f, 4.0f, 14.0f, 14.0f, 0.0f, 0.0f, 10.0f, 10.0f, 1.0f, 1.0f, 11.0f, 11.0f, 0.0f, 0.0f, 2.0f, 2.0f, 5.0f, 5.0f, 15.0f, 15.0f, 9.0f, 9.0f, 19.0f, 19.0f, 3.0f, 3.0f, 13.0f, 13.0f, 0.0f, 0.0f, 2.0f, 2.0f, 9.0f, 9.0f, 19.0f, 19.0f, 5.0f, 5.0f, 15.0f, 15.0f, 1.0f, 1.0f, 11.0f, 11.0f})
                        }, { // classesOut
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {16},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 1, 2, 2, 2, 2, 1, 1, 1, 1, 2, 2, 2, 2, 2})
                        }, { // batchSplitOut
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {16},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1})
                        }},
                .operations = {{
                            .type = TestOperationType::BOX_WITH_NMS_LIMIT,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8},
                            .outputs = {9, 10, 11, 12}
                        }},
                .inputIndexes = {0, 1, 2},
                .outputIndexes = {9, 10, 11, 12}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16 = TestModelManager::get().add("box_with_nms_limit_linear_float16", get_test_model_float16());

}  // namespace generated_tests::box_with_nms_limit_linear

namespace generated_tests::box_with_nms_limit_linear {

const TestModel& get_test_model_float16_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // scores
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {19, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // roi
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {19, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // batchSplit
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {19},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1})
                        }, { // param
                            .type = TestOperandType::FLOAT16,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.30000001192092896f})
                        }, { // param1
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-1})
                        }, { // param2
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param3
                            .type = TestOperandType::FLOAT16,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.4000000059604645f})
                        }, { // param4
                            .type = TestOperandType::FLOAT16,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f})
                        }, { // param5
                            .type = TestOperandType::FLOAT16,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.30000001192092896f})
                        }, { // scoresOut
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {16},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.949999988079071f, 0.8500000238418579f, 0.75f, 0.949999988079071f, 0.699999988079071f, 0.4235294461250305f, 0.3970588445663452f, 0.949999988079071f, 0.8999999761581421f, 0.8500000238418579f, 0.75f, 0.949999988079071f, 0.800000011920929f, 0.699999988079071f, 0.4235294461250305f, 0.3970588445663452f})
                        }, { // roiOut
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {16, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 0.0f, 10.0f, 10.0f, 4.0f, 4.0f, 14.0f, 14.0f, 8.0f, 8.0f, 18.0f, 18.0f, 2.0f, 2.0f, 12.0f, 12.0f, 8.0f, 8.0f, 18.0f, 18.0f, 4.0f, 4.0f, 14.0f, 14.0f, 0.0f, 0.0f, 10.0f, 10.0f, 1.0f, 1.0f, 11.0f, 11.0f, 0.0f, 0.0f, 2.0f, 2.0f, 5.0f, 5.0f, 15.0f, 15.0f, 9.0f, 9.0f, 19.0f, 19.0f, 3.0f, 3.0f, 13.0f, 13.0f, 0.0f, 0.0f, 2.0f, 2.0f, 9.0f, 9.0f, 19.0f, 19.0f, 5.0f, 5.0f, 15.0f, 15.0f, 1.0f, 1.0f, 11.0f, 11.0f})
                        }, { // classesOut
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {16},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 1, 2, 2, 2, 2, 1, 1, 1, 1, 2, 2, 2, 2, 2})
                        }, { // batchSplitOut
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {16},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1})
                        }, { // scores_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {19, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.8999999761581421f, 0.949999988079071f, 0.75f, 0.800000011920929f, 0.699999988079071f, 0.8500000238418579f, 0.6000000238418579f, 0.8999999761581421f, 0.949999988079071f, 0.8999999761581421f, 0.6499999761581421f, 0.8999999761581421f, 0.800000011920929f, 0.8500000238418579f, 0.800000011920929f, 0.6000000238418579f, 0.6000000238418579f, 0.20000000298023224f, 0.6000000238418579f, 0.800000011920929f, 0.4000000059604645f, 0.8999999761581421f, 0.550000011920929f, 0.6000000238418579f, 0.8999999761581421f, 0.75f, 0.699999988079071f, 0.800000011920929f, 0.699999988079071f, 0.8500000238418579f, 0.8999999761581421f, 0.949999988079071f, 0.75f, 0.800000011920929f, 0.8500000238418579f, 0.800000011920929f, 0.6000000238418579f, 0.8999999761581421f, 0.949999988079071f, 0.6000000238418579f, 0.6000000238418579f, 0.20000000298023224f, 0.5f, 0.8999999761581421f, 0.800000011920929f, 0.8999999761581421f, 0.75f, 0.699999988079071f, 0.8999999761581421f, 0.6499999761581421f, 0.8999999761581421f, 0.8999999761581421f, 0.550000011920929f, 0.6000000238418579f, 0.6000000238418579f, 0.800000011920929f, 0.4000000059604645f})
                        }, { // placeholder4
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
                        }, { // roi_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {19, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f, 1.0f, 10.0f, 10.0f, 0.0f, 0.0f, 10.0f, 10.0f, 0.0f, 0.0f, 10.0f, 10.0f, 2.0f, 2.0f, 11.0f, 11.0f, 1.0f, 1.0f, 11.0f, 11.0f, 1.0f, 1.0f, 11.0f, 11.0f, 3.0f, 3.0f, 12.0f, 12.0f, 2.0f, 2.0f, 12.0f, 12.0f, 2.0f, 2.0f, 12.0f, 12.0f, 4.0f, 4.0f, 13.0f, 13.0f, 3.0f, 3.0f, 13.0f, 13.0f, 3.0f, 3.0f, 13.0f, 13.0f, 5.0f, 5.0f, 14.0f, 14.0f, 4.0f, 4.0f, 14.0f, 14.0f, 4.0f, 4.0f, 14.0f, 14.0f, 6.0f, 6.0f, 15.0f, 15.0f, 5.0f, 5.0f, 15.0f, 15.0f, 5.0f, 5.0f, 15.0f, 15.0f, 7.0f, 7.0f, 16.0f, 16.0f, 6.0f, 6.0f, 16.0f, 16.0f, 6.0f, 6.0f, 16.0f, 16.0f, 8.0f, 8.0f, 17.0f, 17.0f, 7.0f, 7.0f, 17.0f, 17.0f, 7.0f, 7.0f, 17.0f, 17.0f, 9.0f, 9.0f, 18.0f, 18.0f, 8.0f, 8.0f, 18.0f, 18.0f, 8.0f, 8.0f, 18.0f, 18.0f, 2.0f, 2.0f, 11.0f, 11.0f, 2.0f, 2.0f, 12.0f, 12.0f, 2.0f, 2.0f, 12.0f, 12.0f, 1.0f, 1.0f, 10.0f, 10.0f, 1.0f, 1.0f, 11.0f, 11.0f, 1.0f, 1.0f, 11.0f, 11.0f, 5.0f, 5.0f, 14.0f, 14.0f, 5.0f, 5.0f, 15.0f, 15.0f, 5.0f, 5.0f, 15.0f, 15.0f, 3.0f, 3.0f, 12.0f, 12.0f, 3.0f, 3.0f, 13.0f, 13.0f, 3.0f, 3.0f, 13.0f, 13.0f, 6.0f, 6.0f, 15.0f, 15.0f, 6.0f, 6.0f, 16.0f, 16.0f, 6.0f, 6.0f, 16.0f, 16.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 2.0f, 2.0f, 0.0f, 0.0f, 2.0f, 2.0f, 9.0f, 9.0f, 18.0f, 18.0f, 9.0f, 9.0f, 19.0f, 19.0f, 9.0f, 9.0f, 19.0f, 19.0f, 4.0f, 4.0f, 13.0f, 13.0f, 4.0f, 4.0f, 14.0f, 14.0f, 4.0f, 4.0f, 14.0f, 14.0f, 8.0f, 8.0f, 17.0f, 17.0f, 8.0f, 8.0f, 18.0f, 18.0f, 8.0f, 8.0f, 18.0f, 18.0f, 7.0f, 7.0f, 16.0f, 16.0f, 7.0f, 7.0f, 17.0f, 17.0f, 7.0f, 7.0f, 17.0f, 17.0f})
                        }, { // placeholder5
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
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {13, 14, 15},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {16, 17, 18},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::BOX_WITH_NMS_LIMIT,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8},
                            .outputs = {9, 10, 11, 12}
                        }},
                .inputIndexes = {2, 13, 16},
                .outputIndexes = {9, 10, 11, 12}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_all_inputs_as_internal = TestModelManager::get().add("box_with_nms_limit_linear_float16_all_inputs_as_internal", get_test_model_float16_all_inputs_as_internal());

}  // namespace generated_tests::box_with_nms_limit_linear

namespace generated_tests::box_with_nms_limit_linear {

const TestModel& get_test_model_quant8() {
    static TestModel model = {
        .main = {
                .operands = {{ // scores
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {19, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({90, 95, 75, 80, 70, 85, 60, 90, 95, 90, 65, 90, 80, 85, 80, 60, 60, 20, 60, 80, 40, 90, 55, 60, 90, 75, 70, 80, 70, 85, 90, 95, 75, 80, 85, 80, 60, 90, 95, 60, 60, 20, 50, 90, 80, 90, 75, 70, 90, 65, 90, 90, 55, 60, 60, 80, 40})
                        }, { // roi
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {19, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({8, 8, 80, 80, 0, 0, 80, 80, 0, 0, 80, 80, 16, 16, 88, 88, 8, 8, 88, 88, 8, 8, 88, 88, 24, 24, 96, 96, 16, 16, 96, 96, 16, 16, 96, 96, 32, 32, 104, 104, 24, 24, 104, 104, 24, 24, 104, 104, 40, 40, 112, 112, 32, 32, 112, 112, 32, 32, 112, 112, 48, 48, 120, 120, 40, 40, 120, 120, 40, 40, 120, 120, 56, 56, 128, 128, 48, 48, 128, 128, 48, 48, 128, 128, 64, 64, 136, 136, 56, 56, 136, 136, 56, 56, 136, 136, 72, 72, 144, 144, 64, 64, 144, 144, 64, 64, 144, 144, 16, 16, 88, 88, 16, 16, 96, 96, 16, 16, 96, 96, 8, 8, 80, 80, 8, 8, 88, 88, 8, 8, 88, 88, 40, 40, 112, 112, 40, 40, 120, 120, 40, 40, 120, 120, 24, 24, 96, 96, 24, 24, 104, 104, 24, 24, 104, 104, 48, 48, 120, 120, 48, 48, 128, 128, 48, 48, 128, 128, 0, 0, 8, 8, 0, 0, 16, 16, 0, 0, 16, 16, 72, 72, 144, 144, 72, 72, 152, 152, 72, 72, 152, 152, 32, 32, 104, 104, 32, 32, 112, 112, 32, 32, 112, 112, 64, 64, 136, 136, 64, 64, 144, 144, 64, 64, 144, 144, 56, 56, 128, 128, 56, 56, 136, 136, 56, 56, 136, 136})
                        }, { // batchSplit
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {19},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1})
                        }, { // param
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // param1
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-1})
                        }, { // param2
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param3
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.4f})
                        }, { // param4
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f})
                        }, { // param5
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // scoresOut
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {16},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({95, 85, 75, 95, 70, 42, 40, 95, 90, 85, 75, 95, 80, 70, 42, 40})
                        }, { // roiOut
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {16, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({0, 0, 80, 80, 32, 32, 112, 112, 64, 64, 144, 144, 16, 16, 96, 96, 64, 64, 144, 144, 32, 32, 112, 112, 0, 0, 80, 80, 8, 8, 88, 88, 0, 0, 16, 16, 40, 40, 120, 120, 72, 72, 152, 152, 24, 24, 104, 104, 0, 0, 16, 16, 72, 72, 152, 152, 40, 40, 120, 120, 8, 8, 88, 88})
                        }, { // classesOut
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {16},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 1, 2, 2, 2, 2, 1, 1, 1, 1, 2, 2, 2, 2, 2})
                        }, { // batchSplitOut
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {16},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1})
                        }},
                .operations = {{
                            .type = TestOperationType::BOX_WITH_NMS_LIMIT,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8},
                            .outputs = {9, 10, 11, 12}
                        }},
                .inputIndexes = {0, 1, 2},
                .outputIndexes = {9, 10, 11, 12}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8 = TestModelManager::get().add("box_with_nms_limit_linear_quant8", get_test_model_quant8());

}  // namespace generated_tests::box_with_nms_limit_linear

namespace generated_tests::box_with_nms_limit_linear {

const TestModel& get_test_model_quant8_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // scores
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {19, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // roi
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {19, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({8, 8, 80, 80, 0, 0, 80, 80, 0, 0, 80, 80, 16, 16, 88, 88, 8, 8, 88, 88, 8, 8, 88, 88, 24, 24, 96, 96, 16, 16, 96, 96, 16, 16, 96, 96, 32, 32, 104, 104, 24, 24, 104, 104, 24, 24, 104, 104, 40, 40, 112, 112, 32, 32, 112, 112, 32, 32, 112, 112, 48, 48, 120, 120, 40, 40, 120, 120, 40, 40, 120, 120, 56, 56, 128, 128, 48, 48, 128, 128, 48, 48, 128, 128, 64, 64, 136, 136, 56, 56, 136, 136, 56, 56, 136, 136, 72, 72, 144, 144, 64, 64, 144, 144, 64, 64, 144, 144, 16, 16, 88, 88, 16, 16, 96, 96, 16, 16, 96, 96, 8, 8, 80, 80, 8, 8, 88, 88, 8, 8, 88, 88, 40, 40, 112, 112, 40, 40, 120, 120, 40, 40, 120, 120, 24, 24, 96, 96, 24, 24, 104, 104, 24, 24, 104, 104, 48, 48, 120, 120, 48, 48, 128, 128, 48, 48, 128, 128, 0, 0, 8, 8, 0, 0, 16, 16, 0, 0, 16, 16, 72, 72, 144, 144, 72, 72, 152, 152, 72, 72, 152, 152, 32, 32, 104, 104, 32, 32, 112, 112, 32, 32, 112, 112, 64, 64, 136, 136, 64, 64, 144, 144, 64, 64, 144, 144, 56, 56, 128, 128, 56, 56, 136, 136, 56, 56, 136, 136})
                        }, { // batchSplit
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {19},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1})
                        }, { // param
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // param1
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-1})
                        }, { // param2
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param3
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.4f})
                        }, { // param4
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f})
                        }, { // param5
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // scoresOut
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {16},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({95, 85, 75, 95, 70, 42, 40, 95, 90, 85, 75, 95, 80, 70, 42, 40})
                        }, { // roiOut
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {16, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({0, 0, 80, 80, 32, 32, 112, 112, 64, 64, 144, 144, 16, 16, 96, 96, 64, 64, 144, 144, 32, 32, 112, 112, 0, 0, 80, 80, 8, 8, 88, 88, 0, 0, 16, 16, 40, 40, 120, 120, 72, 72, 152, 152, 24, 24, 104, 104, 0, 0, 16, 16, 72, 72, 152, 152, 40, 40, 120, 120, 8, 8, 88, 88})
                        }, { // classesOut
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {16},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 1, 2, 2, 2, 2, 1, 1, 1, 1, 2, 2, 2, 2, 2})
                        }, { // batchSplitOut
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {16},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1})
                        }, { // scores_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {19, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({90, 95, 75, 80, 70, 85, 60, 90, 95, 90, 65, 90, 80, 85, 80, 60, 60, 20, 60, 80, 40, 90, 55, 60, 90, 75, 70, 80, 70, 85, 90, 95, 75, 80, 85, 80, 60, 90, 95, 60, 60, 20, 50, 90, 80, 90, 75, 70, 90, 65, 90, 90, 55, 60, 60, 80, 40})
                        }, { // placeholder6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
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
                            .inputs = {13, 14, 15},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::BOX_WITH_NMS_LIMIT,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8},
                            .outputs = {9, 10, 11, 12}
                        }},
                .inputIndexes = {1, 2, 13},
                .outputIndexes = {9, 10, 11, 12}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_all_inputs_as_internal = TestModelManager::get().add("box_with_nms_limit_linear_quant8_all_inputs_as_internal", get_test_model_quant8_all_inputs_as_internal());

}  // namespace generated_tests::box_with_nms_limit_linear

namespace generated_tests::box_with_nms_limit_linear {

const TestModel& get_test_model_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // scores1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {19, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.9f, 0.95f, 0.75f, 0.8f, 0.7f, 0.85f, 0.6f, 0.9f, 0.95f, 0.9f, 0.65f, 0.9f, 0.8f, 0.85f, 0.8f, 0.6f, 0.6f, 0.2f, 0.6f, 0.8f, 0.4f, 0.9f, 0.55f, 0.6f, 0.9f, 0.75f, 0.7f, 0.8f, 0.7f, 0.85f, 0.9f, 0.95f, 0.75f, 0.8f, 0.85f, 0.8f, 0.6f, 0.9f, 0.95f, 0.6f, 0.6f, 0.2f, 0.5f, 0.9f, 0.8f, 0.9f, 0.75f, 0.7f, 0.9f, 0.65f, 0.9f, 0.9f, 0.55f, 0.6f, 0.6f, 0.8f, 0.4f})
                        }, { // roi1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {19, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 1.0f, 10.0f, 10.0f, 0.0f, 0.0f, 10.0f, 10.0f, 0.0f, 0.0f, 10.0f, 10.0f, 2.0f, 2.0f, 11.0f, 11.0f, 1.0f, 1.0f, 11.0f, 11.0f, 1.0f, 1.0f, 11.0f, 11.0f, 3.0f, 3.0f, 12.0f, 12.0f, 2.0f, 2.0f, 12.0f, 12.0f, 2.0f, 2.0f, 12.0f, 12.0f, 4.0f, 4.0f, 13.0f, 13.0f, 3.0f, 3.0f, 13.0f, 13.0f, 3.0f, 3.0f, 13.0f, 13.0f, 5.0f, 5.0f, 14.0f, 14.0f, 4.0f, 4.0f, 14.0f, 14.0f, 4.0f, 4.0f, 14.0f, 14.0f, 6.0f, 6.0f, 15.0f, 15.0f, 5.0f, 5.0f, 15.0f, 15.0f, 5.0f, 5.0f, 15.0f, 15.0f, 7.0f, 7.0f, 16.0f, 16.0f, 6.0f, 6.0f, 16.0f, 16.0f, 6.0f, 6.0f, 16.0f, 16.0f, 8.0f, 8.0f, 17.0f, 17.0f, 7.0f, 7.0f, 17.0f, 17.0f, 7.0f, 7.0f, 17.0f, 17.0f, 9.0f, 9.0f, 18.0f, 18.0f, 8.0f, 8.0f, 18.0f, 18.0f, 8.0f, 8.0f, 18.0f, 18.0f, 2.0f, 2.0f, 11.0f, 11.0f, 2.0f, 2.0f, 12.0f, 12.0f, 2.0f, 2.0f, 12.0f, 12.0f, 1.0f, 1.0f, 10.0f, 10.0f, 1.0f, 1.0f, 11.0f, 11.0f, 1.0f, 1.0f, 11.0f, 11.0f, 5.0f, 5.0f, 14.0f, 14.0f, 5.0f, 5.0f, 15.0f, 15.0f, 5.0f, 5.0f, 15.0f, 15.0f, 3.0f, 3.0f, 12.0f, 12.0f, 3.0f, 3.0f, 13.0f, 13.0f, 3.0f, 3.0f, 13.0f, 13.0f, 6.0f, 6.0f, 15.0f, 15.0f, 6.0f, 6.0f, 16.0f, 16.0f, 6.0f, 6.0f, 16.0f, 16.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 2.0f, 2.0f, 0.0f, 0.0f, 2.0f, 2.0f, 9.0f, 9.0f, 18.0f, 18.0f, 9.0f, 9.0f, 19.0f, 19.0f, 9.0f, 9.0f, 19.0f, 19.0f, 4.0f, 4.0f, 13.0f, 13.0f, 4.0f, 4.0f, 14.0f, 14.0f, 4.0f, 4.0f, 14.0f, 14.0f, 8.0f, 8.0f, 17.0f, 17.0f, 8.0f, 8.0f, 18.0f, 18.0f, 8.0f, 8.0f, 18.0f, 18.0f, 7.0f, 7.0f, 16.0f, 16.0f, 7.0f, 7.0f, 17.0f, 17.0f, 7.0f, 7.0f, 17.0f, 17.0f})
                        }, { // batchSplit1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {19},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 1, 1, 1, 1, 1, 1, 1, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3})
                        }, { // param6
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // param7
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({8})
                        }, { // param8
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param9
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.4f})
                        }, { // param10
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.5f})
                        }, { // param11
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // scoresOut1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {15},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.95f, 0.85f, 0.75f, 0.95f, 0.7f, 0.42352945f, 0.39705884f, 0.95f, 0.9f, 0.85f, 0.75f, 0.95f, 0.8f, 0.7f, 0.42352945f})
                        }, { // roiOut1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {15, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 10.0f, 10.0f, 4.0f, 4.0f, 14.0f, 14.0f, 8.0f, 8.0f, 18.0f, 18.0f, 2.0f, 2.0f, 12.0f, 12.0f, 8.0f, 8.0f, 18.0f, 18.0f, 4.0f, 4.0f, 14.0f, 14.0f, 0.0f, 0.0f, 10.0f, 10.0f, 1.0f, 1.0f, 11.0f, 11.0f, 0.0f, 0.0f, 2.0f, 2.0f, 5.0f, 5.0f, 15.0f, 15.0f, 9.0f, 9.0f, 19.0f, 19.0f, 3.0f, 3.0f, 13.0f, 13.0f, 0.0f, 0.0f, 2.0f, 2.0f, 9.0f, 9.0f, 19.0f, 19.0f, 5.0f, 5.0f, 15.0f, 15.0f})
                        }, { // classesOut1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {15},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 1, 2, 2, 2, 2, 1, 1, 1, 1, 2, 2, 2, 2})
                        }, { // batchSplitOut1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {15},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 1, 1, 1, 1, 1, 3, 3, 3, 3, 3, 3, 3, 3})
                        }},
                .operations = {{
                            .type = TestOperationType::BOX_WITH_NMS_LIMIT,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8},
                            .outputs = {9, 10, 11, 12}
                        }},
                .inputIndexes = {0, 1, 2},
                .outputIndexes = {9, 10, 11, 12}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_2 = TestModelManager::get().add("box_with_nms_limit_linear_2", get_test_model_2());

}  // namespace generated_tests::box_with_nms_limit_linear

namespace generated_tests::box_with_nms_limit_linear {

const TestModel& get_test_model_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // scores1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {19, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // roi1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {19, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // batchSplit1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {19},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 1, 1, 1, 1, 1, 1, 1, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3})
                        }, { // param6
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // param7
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({8})
                        }, { // param8
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param9
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.4f})
                        }, { // param10
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.5f})
                        }, { // param11
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // scoresOut1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {15},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.95f, 0.85f, 0.75f, 0.95f, 0.7f, 0.42352945f, 0.39705884f, 0.95f, 0.9f, 0.85f, 0.75f, 0.95f, 0.8f, 0.7f, 0.42352945f})
                        }, { // roiOut1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {15, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 10.0f, 10.0f, 4.0f, 4.0f, 14.0f, 14.0f, 8.0f, 8.0f, 18.0f, 18.0f, 2.0f, 2.0f, 12.0f, 12.0f, 8.0f, 8.0f, 18.0f, 18.0f, 4.0f, 4.0f, 14.0f, 14.0f, 0.0f, 0.0f, 10.0f, 10.0f, 1.0f, 1.0f, 11.0f, 11.0f, 0.0f, 0.0f, 2.0f, 2.0f, 5.0f, 5.0f, 15.0f, 15.0f, 9.0f, 9.0f, 19.0f, 19.0f, 3.0f, 3.0f, 13.0f, 13.0f, 0.0f, 0.0f, 2.0f, 2.0f, 9.0f, 9.0f, 19.0f, 19.0f, 5.0f, 5.0f, 15.0f, 15.0f})
                        }, { // classesOut1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {15},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 1, 2, 2, 2, 2, 1, 1, 1, 1, 2, 2, 2, 2})
                        }, { // batchSplitOut1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {15},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 1, 1, 1, 1, 1, 3, 3, 3, 3, 3, 3, 3, 3})
                        }, { // scores1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {19, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.9f, 0.95f, 0.75f, 0.8f, 0.7f, 0.85f, 0.6f, 0.9f, 0.95f, 0.9f, 0.65f, 0.9f, 0.8f, 0.85f, 0.8f, 0.6f, 0.6f, 0.2f, 0.6f, 0.8f, 0.4f, 0.9f, 0.55f, 0.6f, 0.9f, 0.75f, 0.7f, 0.8f, 0.7f, 0.85f, 0.9f, 0.95f, 0.75f, 0.8f, 0.85f, 0.8f, 0.6f, 0.9f, 0.95f, 0.6f, 0.6f, 0.2f, 0.5f, 0.9f, 0.8f, 0.9f, 0.75f, 0.7f, 0.9f, 0.65f, 0.9f, 0.9f, 0.55f, 0.6f, 0.6f, 0.8f, 0.4f})
                        }, { // placeholder7
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
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
                        }, { // roi1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {19, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 1.0f, 10.0f, 10.0f, 0.0f, 0.0f, 10.0f, 10.0f, 0.0f, 0.0f, 10.0f, 10.0f, 2.0f, 2.0f, 11.0f, 11.0f, 1.0f, 1.0f, 11.0f, 11.0f, 1.0f, 1.0f, 11.0f, 11.0f, 3.0f, 3.0f, 12.0f, 12.0f, 2.0f, 2.0f, 12.0f, 12.0f, 2.0f, 2.0f, 12.0f, 12.0f, 4.0f, 4.0f, 13.0f, 13.0f, 3.0f, 3.0f, 13.0f, 13.0f, 3.0f, 3.0f, 13.0f, 13.0f, 5.0f, 5.0f, 14.0f, 14.0f, 4.0f, 4.0f, 14.0f, 14.0f, 4.0f, 4.0f, 14.0f, 14.0f, 6.0f, 6.0f, 15.0f, 15.0f, 5.0f, 5.0f, 15.0f, 15.0f, 5.0f, 5.0f, 15.0f, 15.0f, 7.0f, 7.0f, 16.0f, 16.0f, 6.0f, 6.0f, 16.0f, 16.0f, 6.0f, 6.0f, 16.0f, 16.0f, 8.0f, 8.0f, 17.0f, 17.0f, 7.0f, 7.0f, 17.0f, 17.0f, 7.0f, 7.0f, 17.0f, 17.0f, 9.0f, 9.0f, 18.0f, 18.0f, 8.0f, 8.0f, 18.0f, 18.0f, 8.0f, 8.0f, 18.0f, 18.0f, 2.0f, 2.0f, 11.0f, 11.0f, 2.0f, 2.0f, 12.0f, 12.0f, 2.0f, 2.0f, 12.0f, 12.0f, 1.0f, 1.0f, 10.0f, 10.0f, 1.0f, 1.0f, 11.0f, 11.0f, 1.0f, 1.0f, 11.0f, 11.0f, 5.0f, 5.0f, 14.0f, 14.0f, 5.0f, 5.0f, 15.0f, 15.0f, 5.0f, 5.0f, 15.0f, 15.0f, 3.0f, 3.0f, 12.0f, 12.0f, 3.0f, 3.0f, 13.0f, 13.0f, 3.0f, 3.0f, 13.0f, 13.0f, 6.0f, 6.0f, 15.0f, 15.0f, 6.0f, 6.0f, 16.0f, 16.0f, 6.0f, 6.0f, 16.0f, 16.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 2.0f, 2.0f, 0.0f, 0.0f, 2.0f, 2.0f, 9.0f, 9.0f, 18.0f, 18.0f, 9.0f, 9.0f, 19.0f, 19.0f, 9.0f, 9.0f, 19.0f, 19.0f, 4.0f, 4.0f, 13.0f, 13.0f, 4.0f, 4.0f, 14.0f, 14.0f, 4.0f, 4.0f, 14.0f, 14.0f, 8.0f, 8.0f, 17.0f, 17.0f, 8.0f, 8.0f, 18.0f, 18.0f, 8.0f, 8.0f, 18.0f, 18.0f, 7.0f, 7.0f, 16.0f, 16.0f, 7.0f, 7.0f, 17.0f, 17.0f, 7.0f, 7.0f, 17.0f, 17.0f})
                        }, { // placeholder8
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
                        }, { // param20
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
                            .inputs = {13, 14, 15},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {16, 17, 18},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::BOX_WITH_NMS_LIMIT,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8},
                            .outputs = {9, 10, 11, 12}
                        }},
                .inputIndexes = {2, 13, 16},
                .outputIndexes = {9, 10, 11, 12}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_2 = TestModelManager::get().add("box_with_nms_limit_linear_all_inputs_as_internal_2", get_test_model_all_inputs_as_internal_2());

}  // namespace generated_tests::box_with_nms_limit_linear

namespace generated_tests::box_with_nms_limit_linear {

const TestModel& get_test_model_relaxed_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // scores1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {19, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.9f, 0.95f, 0.75f, 0.8f, 0.7f, 0.85f, 0.6f, 0.9f, 0.95f, 0.9f, 0.65f, 0.9f, 0.8f, 0.85f, 0.8f, 0.6f, 0.6f, 0.2f, 0.6f, 0.8f, 0.4f, 0.9f, 0.55f, 0.6f, 0.9f, 0.75f, 0.7f, 0.8f, 0.7f, 0.85f, 0.9f, 0.95f, 0.75f, 0.8f, 0.85f, 0.8f, 0.6f, 0.9f, 0.95f, 0.6f, 0.6f, 0.2f, 0.5f, 0.9f, 0.8f, 0.9f, 0.75f, 0.7f, 0.9f, 0.65f, 0.9f, 0.9f, 0.55f, 0.6f, 0.6f, 0.8f, 0.4f})
                        }, { // roi1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {19, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 1.0f, 10.0f, 10.0f, 0.0f, 0.0f, 10.0f, 10.0f, 0.0f, 0.0f, 10.0f, 10.0f, 2.0f, 2.0f, 11.0f, 11.0f, 1.0f, 1.0f, 11.0f, 11.0f, 1.0f, 1.0f, 11.0f, 11.0f, 3.0f, 3.0f, 12.0f, 12.0f, 2.0f, 2.0f, 12.0f, 12.0f, 2.0f, 2.0f, 12.0f, 12.0f, 4.0f, 4.0f, 13.0f, 13.0f, 3.0f, 3.0f, 13.0f, 13.0f, 3.0f, 3.0f, 13.0f, 13.0f, 5.0f, 5.0f, 14.0f, 14.0f, 4.0f, 4.0f, 14.0f, 14.0f, 4.0f, 4.0f, 14.0f, 14.0f, 6.0f, 6.0f, 15.0f, 15.0f, 5.0f, 5.0f, 15.0f, 15.0f, 5.0f, 5.0f, 15.0f, 15.0f, 7.0f, 7.0f, 16.0f, 16.0f, 6.0f, 6.0f, 16.0f, 16.0f, 6.0f, 6.0f, 16.0f, 16.0f, 8.0f, 8.0f, 17.0f, 17.0f, 7.0f, 7.0f, 17.0f, 17.0f, 7.0f, 7.0f, 17.0f, 17.0f, 9.0f, 9.0f, 18.0f, 18.0f, 8.0f, 8.0f, 18.0f, 18.0f, 8.0f, 8.0f, 18.0f, 18.0f, 2.0f, 2.0f, 11.0f, 11.0f, 2.0f, 2.0f, 12.0f, 12.0f, 2.0f, 2.0f, 12.0f, 12.0f, 1.0f, 1.0f, 10.0f, 10.0f, 1.0f, 1.0f, 11.0f, 11.0f, 1.0f, 1.0f, 11.0f, 11.0f, 5.0f, 5.0f, 14.0f, 14.0f, 5.0f, 5.0f, 15.0f, 15.0f, 5.0f, 5.0f, 15.0f, 15.0f, 3.0f, 3.0f, 12.0f, 12.0f, 3.0f, 3.0f, 13.0f, 13.0f, 3.0f, 3.0f, 13.0f, 13.0f, 6.0f, 6.0f, 15.0f, 15.0f, 6.0f, 6.0f, 16.0f, 16.0f, 6.0f, 6.0f, 16.0f, 16.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 2.0f, 2.0f, 0.0f, 0.0f, 2.0f, 2.0f, 9.0f, 9.0f, 18.0f, 18.0f, 9.0f, 9.0f, 19.0f, 19.0f, 9.0f, 9.0f, 19.0f, 19.0f, 4.0f, 4.0f, 13.0f, 13.0f, 4.0f, 4.0f, 14.0f, 14.0f, 4.0f, 4.0f, 14.0f, 14.0f, 8.0f, 8.0f, 17.0f, 17.0f, 8.0f, 8.0f, 18.0f, 18.0f, 8.0f, 8.0f, 18.0f, 18.0f, 7.0f, 7.0f, 16.0f, 16.0f, 7.0f, 7.0f, 17.0f, 17.0f, 7.0f, 7.0f, 17.0f, 17.0f})
                        }, { // batchSplit1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {19},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 1, 1, 1, 1, 1, 1, 1, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3})
                        }, { // param6
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // param7
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({8})
                        }, { // param8
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param9
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.4f})
                        }, { // param10
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.5f})
                        }, { // param11
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // scoresOut1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {15},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.95f, 0.85f, 0.75f, 0.95f, 0.7f, 0.42352945f, 0.39705884f, 0.95f, 0.9f, 0.85f, 0.75f, 0.95f, 0.8f, 0.7f, 0.42352945f})
                        }, { // roiOut1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {15, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 10.0f, 10.0f, 4.0f, 4.0f, 14.0f, 14.0f, 8.0f, 8.0f, 18.0f, 18.0f, 2.0f, 2.0f, 12.0f, 12.0f, 8.0f, 8.0f, 18.0f, 18.0f, 4.0f, 4.0f, 14.0f, 14.0f, 0.0f, 0.0f, 10.0f, 10.0f, 1.0f, 1.0f, 11.0f, 11.0f, 0.0f, 0.0f, 2.0f, 2.0f, 5.0f, 5.0f, 15.0f, 15.0f, 9.0f, 9.0f, 19.0f, 19.0f, 3.0f, 3.0f, 13.0f, 13.0f, 0.0f, 0.0f, 2.0f, 2.0f, 9.0f, 9.0f, 19.0f, 19.0f, 5.0f, 5.0f, 15.0f, 15.0f})
                        }, { // classesOut1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {15},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 1, 2, 2, 2, 2, 1, 1, 1, 1, 2, 2, 2, 2})
                        }, { // batchSplitOut1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {15},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 1, 1, 1, 1, 1, 3, 3, 3, 3, 3, 3, 3, 3})
                        }},
                .operations = {{
                            .type = TestOperationType::BOX_WITH_NMS_LIMIT,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8},
                            .outputs = {9, 10, 11, 12}
                        }},
                .inputIndexes = {0, 1, 2},
                .outputIndexes = {9, 10, 11, 12}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_2 = TestModelManager::get().add("box_with_nms_limit_linear_relaxed_2", get_test_model_relaxed_2());

}  // namespace generated_tests::box_with_nms_limit_linear

namespace generated_tests::box_with_nms_limit_linear {

const TestModel& get_test_model_relaxed_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // scores1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {19, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // roi1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {19, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // batchSplit1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {19},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 1, 1, 1, 1, 1, 1, 1, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3})
                        }, { // param6
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // param7
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({8})
                        }, { // param8
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param9
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.4f})
                        }, { // param10
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.5f})
                        }, { // param11
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // scoresOut1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {15},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.95f, 0.85f, 0.75f, 0.95f, 0.7f, 0.42352945f, 0.39705884f, 0.95f, 0.9f, 0.85f, 0.75f, 0.95f, 0.8f, 0.7f, 0.42352945f})
                        }, { // roiOut1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {15, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 10.0f, 10.0f, 4.0f, 4.0f, 14.0f, 14.0f, 8.0f, 8.0f, 18.0f, 18.0f, 2.0f, 2.0f, 12.0f, 12.0f, 8.0f, 8.0f, 18.0f, 18.0f, 4.0f, 4.0f, 14.0f, 14.0f, 0.0f, 0.0f, 10.0f, 10.0f, 1.0f, 1.0f, 11.0f, 11.0f, 0.0f, 0.0f, 2.0f, 2.0f, 5.0f, 5.0f, 15.0f, 15.0f, 9.0f, 9.0f, 19.0f, 19.0f, 3.0f, 3.0f, 13.0f, 13.0f, 0.0f, 0.0f, 2.0f, 2.0f, 9.0f, 9.0f, 19.0f, 19.0f, 5.0f, 5.0f, 15.0f, 15.0f})
                        }, { // classesOut1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {15},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 1, 2, 2, 2, 2, 1, 1, 1, 1, 2, 2, 2, 2})
                        }, { // batchSplitOut1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {15},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 1, 1, 1, 1, 1, 3, 3, 3, 3, 3, 3, 3, 3})
                        }, { // scores1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {19, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.9f, 0.95f, 0.75f, 0.8f, 0.7f, 0.85f, 0.6f, 0.9f, 0.95f, 0.9f, 0.65f, 0.9f, 0.8f, 0.85f, 0.8f, 0.6f, 0.6f, 0.2f, 0.6f, 0.8f, 0.4f, 0.9f, 0.55f, 0.6f, 0.9f, 0.75f, 0.7f, 0.8f, 0.7f, 0.85f, 0.9f, 0.95f, 0.75f, 0.8f, 0.85f, 0.8f, 0.6f, 0.9f, 0.95f, 0.6f, 0.6f, 0.2f, 0.5f, 0.9f, 0.8f, 0.9f, 0.75f, 0.7f, 0.9f, 0.65f, 0.9f, 0.9f, 0.55f, 0.6f, 0.6f, 0.8f, 0.4f})
                        }, { // placeholder9
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
                        }, { // param21
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // roi1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {19, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 1.0f, 10.0f, 10.0f, 0.0f, 0.0f, 10.0f, 10.0f, 0.0f, 0.0f, 10.0f, 10.0f, 2.0f, 2.0f, 11.0f, 11.0f, 1.0f, 1.0f, 11.0f, 11.0f, 1.0f, 1.0f, 11.0f, 11.0f, 3.0f, 3.0f, 12.0f, 12.0f, 2.0f, 2.0f, 12.0f, 12.0f, 2.0f, 2.0f, 12.0f, 12.0f, 4.0f, 4.0f, 13.0f, 13.0f, 3.0f, 3.0f, 13.0f, 13.0f, 3.0f, 3.0f, 13.0f, 13.0f, 5.0f, 5.0f, 14.0f, 14.0f, 4.0f, 4.0f, 14.0f, 14.0f, 4.0f, 4.0f, 14.0f, 14.0f, 6.0f, 6.0f, 15.0f, 15.0f, 5.0f, 5.0f, 15.0f, 15.0f, 5.0f, 5.0f, 15.0f, 15.0f, 7.0f, 7.0f, 16.0f, 16.0f, 6.0f, 6.0f, 16.0f, 16.0f, 6.0f, 6.0f, 16.0f, 16.0f, 8.0f, 8.0f, 17.0f, 17.0f, 7.0f, 7.0f, 17.0f, 17.0f, 7.0f, 7.0f, 17.0f, 17.0f, 9.0f, 9.0f, 18.0f, 18.0f, 8.0f, 8.0f, 18.0f, 18.0f, 8.0f, 8.0f, 18.0f, 18.0f, 2.0f, 2.0f, 11.0f, 11.0f, 2.0f, 2.0f, 12.0f, 12.0f, 2.0f, 2.0f, 12.0f, 12.0f, 1.0f, 1.0f, 10.0f, 10.0f, 1.0f, 1.0f, 11.0f, 11.0f, 1.0f, 1.0f, 11.0f, 11.0f, 5.0f, 5.0f, 14.0f, 14.0f, 5.0f, 5.0f, 15.0f, 15.0f, 5.0f, 5.0f, 15.0f, 15.0f, 3.0f, 3.0f, 12.0f, 12.0f, 3.0f, 3.0f, 13.0f, 13.0f, 3.0f, 3.0f, 13.0f, 13.0f, 6.0f, 6.0f, 15.0f, 15.0f, 6.0f, 6.0f, 16.0f, 16.0f, 6.0f, 6.0f, 16.0f, 16.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 2.0f, 2.0f, 0.0f, 0.0f, 2.0f, 2.0f, 9.0f, 9.0f, 18.0f, 18.0f, 9.0f, 9.0f, 19.0f, 19.0f, 9.0f, 9.0f, 19.0f, 19.0f, 4.0f, 4.0f, 13.0f, 13.0f, 4.0f, 4.0f, 14.0f, 14.0f, 4.0f, 4.0f, 14.0f, 14.0f, 8.0f, 8.0f, 17.0f, 17.0f, 8.0f, 8.0f, 18.0f, 18.0f, 8.0f, 8.0f, 18.0f, 18.0f, 7.0f, 7.0f, 16.0f, 16.0f, 7.0f, 7.0f, 17.0f, 17.0f, 7.0f, 7.0f, 17.0f, 17.0f})
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
                        }, { // param22
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
                            .inputs = {13, 14, 15},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {16, 17, 18},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::BOX_WITH_NMS_LIMIT,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8},
                            .outputs = {9, 10, 11, 12}
                        }},
                .inputIndexes = {2, 13, 16},
                .outputIndexes = {9, 10, 11, 12}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_all_inputs_as_internal_2 = TestModelManager::get().add("box_with_nms_limit_linear_relaxed_all_inputs_as_internal_2", get_test_model_relaxed_all_inputs_as_internal_2());

}  // namespace generated_tests::box_with_nms_limit_linear

namespace generated_tests::box_with_nms_limit_linear {

const TestModel& get_test_model_float16_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // scores1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {19, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.8999999761581421f, 0.949999988079071f, 0.75f, 0.800000011920929f, 0.699999988079071f, 0.8500000238418579f, 0.6000000238418579f, 0.8999999761581421f, 0.949999988079071f, 0.8999999761581421f, 0.6499999761581421f, 0.8999999761581421f, 0.800000011920929f, 0.8500000238418579f, 0.800000011920929f, 0.6000000238418579f, 0.6000000238418579f, 0.20000000298023224f, 0.6000000238418579f, 0.800000011920929f, 0.4000000059604645f, 0.8999999761581421f, 0.550000011920929f, 0.6000000238418579f, 0.8999999761581421f, 0.75f, 0.699999988079071f, 0.800000011920929f, 0.699999988079071f, 0.8500000238418579f, 0.8999999761581421f, 0.949999988079071f, 0.75f, 0.800000011920929f, 0.8500000238418579f, 0.800000011920929f, 0.6000000238418579f, 0.8999999761581421f, 0.949999988079071f, 0.6000000238418579f, 0.6000000238418579f, 0.20000000298023224f, 0.5f, 0.8999999761581421f, 0.800000011920929f, 0.8999999761581421f, 0.75f, 0.699999988079071f, 0.8999999761581421f, 0.6499999761581421f, 0.8999999761581421f, 0.8999999761581421f, 0.550000011920929f, 0.6000000238418579f, 0.6000000238418579f, 0.800000011920929f, 0.4000000059604645f})
                        }, { // roi1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {19, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f, 1.0f, 10.0f, 10.0f, 0.0f, 0.0f, 10.0f, 10.0f, 0.0f, 0.0f, 10.0f, 10.0f, 2.0f, 2.0f, 11.0f, 11.0f, 1.0f, 1.0f, 11.0f, 11.0f, 1.0f, 1.0f, 11.0f, 11.0f, 3.0f, 3.0f, 12.0f, 12.0f, 2.0f, 2.0f, 12.0f, 12.0f, 2.0f, 2.0f, 12.0f, 12.0f, 4.0f, 4.0f, 13.0f, 13.0f, 3.0f, 3.0f, 13.0f, 13.0f, 3.0f, 3.0f, 13.0f, 13.0f, 5.0f, 5.0f, 14.0f, 14.0f, 4.0f, 4.0f, 14.0f, 14.0f, 4.0f, 4.0f, 14.0f, 14.0f, 6.0f, 6.0f, 15.0f, 15.0f, 5.0f, 5.0f, 15.0f, 15.0f, 5.0f, 5.0f, 15.0f, 15.0f, 7.0f, 7.0f, 16.0f, 16.0f, 6.0f, 6.0f, 16.0f, 16.0f, 6.0f, 6.0f, 16.0f, 16.0f, 8.0f, 8.0f, 17.0f, 17.0f, 7.0f, 7.0f, 17.0f, 17.0f, 7.0f, 7.0f, 17.0f, 17.0f, 9.0f, 9.0f, 18.0f, 18.0f, 8.0f, 8.0f, 18.0f, 18.0f, 8.0f, 8.0f, 18.0f, 18.0f, 2.0f, 2.0f, 11.0f, 11.0f, 2.0f, 2.0f, 12.0f, 12.0f, 2.0f, 2.0f, 12.0f, 12.0f, 1.0f, 1.0f, 10.0f, 10.0f, 1.0f, 1.0f, 11.0f, 11.0f, 1.0f, 1.0f, 11.0f, 11.0f, 5.0f, 5.0f, 14.0f, 14.0f, 5.0f, 5.0f, 15.0f, 15.0f, 5.0f, 5.0f, 15.0f, 15.0f, 3.0f, 3.0f, 12.0f, 12.0f, 3.0f, 3.0f, 13.0f, 13.0f, 3.0f, 3.0f, 13.0f, 13.0f, 6.0f, 6.0f, 15.0f, 15.0f, 6.0f, 6.0f, 16.0f, 16.0f, 6.0f, 6.0f, 16.0f, 16.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 2.0f, 2.0f, 0.0f, 0.0f, 2.0f, 2.0f, 9.0f, 9.0f, 18.0f, 18.0f, 9.0f, 9.0f, 19.0f, 19.0f, 9.0f, 9.0f, 19.0f, 19.0f, 4.0f, 4.0f, 13.0f, 13.0f, 4.0f, 4.0f, 14.0f, 14.0f, 4.0f, 4.0f, 14.0f, 14.0f, 8.0f, 8.0f, 17.0f, 17.0f, 8.0f, 8.0f, 18.0f, 18.0f, 8.0f, 8.0f, 18.0f, 18.0f, 7.0f, 7.0f, 16.0f, 16.0f, 7.0f, 7.0f, 17.0f, 17.0f, 7.0f, 7.0f, 17.0f, 17.0f})
                        }, { // batchSplit1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {19},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 1, 1, 1, 1, 1, 1, 1, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3})
                        }, { // param6
                            .type = TestOperandType::FLOAT16,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.30000001192092896f})
                        }, { // param7
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({8})
                        }, { // param8
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param9
                            .type = TestOperandType::FLOAT16,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.4000000059604645f})
                        }, { // param10
                            .type = TestOperandType::FLOAT16,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.5f})
                        }, { // param11
                            .type = TestOperandType::FLOAT16,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.30000001192092896f})
                        }, { // scoresOut1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {15},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.949999988079071f, 0.8500000238418579f, 0.75f, 0.949999988079071f, 0.699999988079071f, 0.4235294461250305f, 0.3970588445663452f, 0.949999988079071f, 0.8999999761581421f, 0.8500000238418579f, 0.75f, 0.949999988079071f, 0.800000011920929f, 0.699999988079071f, 0.4235294461250305f})
                        }, { // roiOut1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {15, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 0.0f, 10.0f, 10.0f, 4.0f, 4.0f, 14.0f, 14.0f, 8.0f, 8.0f, 18.0f, 18.0f, 2.0f, 2.0f, 12.0f, 12.0f, 8.0f, 8.0f, 18.0f, 18.0f, 4.0f, 4.0f, 14.0f, 14.0f, 0.0f, 0.0f, 10.0f, 10.0f, 1.0f, 1.0f, 11.0f, 11.0f, 0.0f, 0.0f, 2.0f, 2.0f, 5.0f, 5.0f, 15.0f, 15.0f, 9.0f, 9.0f, 19.0f, 19.0f, 3.0f, 3.0f, 13.0f, 13.0f, 0.0f, 0.0f, 2.0f, 2.0f, 9.0f, 9.0f, 19.0f, 19.0f, 5.0f, 5.0f, 15.0f, 15.0f})
                        }, { // classesOut1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {15},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 1, 2, 2, 2, 2, 1, 1, 1, 1, 2, 2, 2, 2})
                        }, { // batchSplitOut1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {15},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 1, 1, 1, 1, 1, 3, 3, 3, 3, 3, 3, 3, 3})
                        }},
                .operations = {{
                            .type = TestOperationType::BOX_WITH_NMS_LIMIT,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8},
                            .outputs = {9, 10, 11, 12}
                        }},
                .inputIndexes = {0, 1, 2},
                .outputIndexes = {9, 10, 11, 12}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_2 = TestModelManager::get().add("box_with_nms_limit_linear_float16_2", get_test_model_float16_2());

}  // namespace generated_tests::box_with_nms_limit_linear

namespace generated_tests::box_with_nms_limit_linear {

const TestModel& get_test_model_float16_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // scores1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {19, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // roi1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {19, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // batchSplit1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {19},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 1, 1, 1, 1, 1, 1, 1, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3})
                        }, { // param6
                            .type = TestOperandType::FLOAT16,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.30000001192092896f})
                        }, { // param7
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({8})
                        }, { // param8
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param9
                            .type = TestOperandType::FLOAT16,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.4000000059604645f})
                        }, { // param10
                            .type = TestOperandType::FLOAT16,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.5f})
                        }, { // param11
                            .type = TestOperandType::FLOAT16,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.30000001192092896f})
                        }, { // scoresOut1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {15},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.949999988079071f, 0.8500000238418579f, 0.75f, 0.949999988079071f, 0.699999988079071f, 0.4235294461250305f, 0.3970588445663452f, 0.949999988079071f, 0.8999999761581421f, 0.8500000238418579f, 0.75f, 0.949999988079071f, 0.800000011920929f, 0.699999988079071f, 0.4235294461250305f})
                        }, { // roiOut1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {15, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 0.0f, 10.0f, 10.0f, 4.0f, 4.0f, 14.0f, 14.0f, 8.0f, 8.0f, 18.0f, 18.0f, 2.0f, 2.0f, 12.0f, 12.0f, 8.0f, 8.0f, 18.0f, 18.0f, 4.0f, 4.0f, 14.0f, 14.0f, 0.0f, 0.0f, 10.0f, 10.0f, 1.0f, 1.0f, 11.0f, 11.0f, 0.0f, 0.0f, 2.0f, 2.0f, 5.0f, 5.0f, 15.0f, 15.0f, 9.0f, 9.0f, 19.0f, 19.0f, 3.0f, 3.0f, 13.0f, 13.0f, 0.0f, 0.0f, 2.0f, 2.0f, 9.0f, 9.0f, 19.0f, 19.0f, 5.0f, 5.0f, 15.0f, 15.0f})
                        }, { // classesOut1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {15},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 1, 2, 2, 2, 2, 1, 1, 1, 1, 2, 2, 2, 2})
                        }, { // batchSplitOut1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {15},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 1, 1, 1, 1, 1, 3, 3, 3, 3, 3, 3, 3, 3})
                        }, { // scores1_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {19, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.8999999761581421f, 0.949999988079071f, 0.75f, 0.800000011920929f, 0.699999988079071f, 0.8500000238418579f, 0.6000000238418579f, 0.8999999761581421f, 0.949999988079071f, 0.8999999761581421f, 0.6499999761581421f, 0.8999999761581421f, 0.800000011920929f, 0.8500000238418579f, 0.800000011920929f, 0.6000000238418579f, 0.6000000238418579f, 0.20000000298023224f, 0.6000000238418579f, 0.800000011920929f, 0.4000000059604645f, 0.8999999761581421f, 0.550000011920929f, 0.6000000238418579f, 0.8999999761581421f, 0.75f, 0.699999988079071f, 0.800000011920929f, 0.699999988079071f, 0.8500000238418579f, 0.8999999761581421f, 0.949999988079071f, 0.75f, 0.800000011920929f, 0.8500000238418579f, 0.800000011920929f, 0.6000000238418579f, 0.8999999761581421f, 0.949999988079071f, 0.6000000238418579f, 0.6000000238418579f, 0.20000000298023224f, 0.5f, 0.8999999761581421f, 0.800000011920929f, 0.8999999761581421f, 0.75f, 0.699999988079071f, 0.8999999761581421f, 0.6499999761581421f, 0.8999999761581421f, 0.8999999761581421f, 0.550000011920929f, 0.6000000238418579f, 0.6000000238418579f, 0.800000011920929f, 0.4000000059604645f})
                        }, { // placeholder11
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
                        }, { // param23
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // roi1_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {19, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f, 1.0f, 10.0f, 10.0f, 0.0f, 0.0f, 10.0f, 10.0f, 0.0f, 0.0f, 10.0f, 10.0f, 2.0f, 2.0f, 11.0f, 11.0f, 1.0f, 1.0f, 11.0f, 11.0f, 1.0f, 1.0f, 11.0f, 11.0f, 3.0f, 3.0f, 12.0f, 12.0f, 2.0f, 2.0f, 12.0f, 12.0f, 2.0f, 2.0f, 12.0f, 12.0f, 4.0f, 4.0f, 13.0f, 13.0f, 3.0f, 3.0f, 13.0f, 13.0f, 3.0f, 3.0f, 13.0f, 13.0f, 5.0f, 5.0f, 14.0f, 14.0f, 4.0f, 4.0f, 14.0f, 14.0f, 4.0f, 4.0f, 14.0f, 14.0f, 6.0f, 6.0f, 15.0f, 15.0f, 5.0f, 5.0f, 15.0f, 15.0f, 5.0f, 5.0f, 15.0f, 15.0f, 7.0f, 7.0f, 16.0f, 16.0f, 6.0f, 6.0f, 16.0f, 16.0f, 6.0f, 6.0f, 16.0f, 16.0f, 8.0f, 8.0f, 17.0f, 17.0f, 7.0f, 7.0f, 17.0f, 17.0f, 7.0f, 7.0f, 17.0f, 17.0f, 9.0f, 9.0f, 18.0f, 18.0f, 8.0f, 8.0f, 18.0f, 18.0f, 8.0f, 8.0f, 18.0f, 18.0f, 2.0f, 2.0f, 11.0f, 11.0f, 2.0f, 2.0f, 12.0f, 12.0f, 2.0f, 2.0f, 12.0f, 12.0f, 1.0f, 1.0f, 10.0f, 10.0f, 1.0f, 1.0f, 11.0f, 11.0f, 1.0f, 1.0f, 11.0f, 11.0f, 5.0f, 5.0f, 14.0f, 14.0f, 5.0f, 5.0f, 15.0f, 15.0f, 5.0f, 5.0f, 15.0f, 15.0f, 3.0f, 3.0f, 12.0f, 12.0f, 3.0f, 3.0f, 13.0f, 13.0f, 3.0f, 3.0f, 13.0f, 13.0f, 6.0f, 6.0f, 15.0f, 15.0f, 6.0f, 6.0f, 16.0f, 16.0f, 6.0f, 6.0f, 16.0f, 16.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 2.0f, 2.0f, 0.0f, 0.0f, 2.0f, 2.0f, 9.0f, 9.0f, 18.0f, 18.0f, 9.0f, 9.0f, 19.0f, 19.0f, 9.0f, 9.0f, 19.0f, 19.0f, 4.0f, 4.0f, 13.0f, 13.0f, 4.0f, 4.0f, 14.0f, 14.0f, 4.0f, 4.0f, 14.0f, 14.0f, 8.0f, 8.0f, 17.0f, 17.0f, 8.0f, 8.0f, 18.0f, 18.0f, 8.0f, 8.0f, 18.0f, 18.0f, 7.0f, 7.0f, 16.0f, 16.0f, 7.0f, 7.0f, 17.0f, 17.0f, 7.0f, 7.0f, 17.0f, 17.0f})
                        }, { // placeholder12
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
                        }, { // param24
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
                            .inputs = {13, 14, 15},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {16, 17, 18},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::BOX_WITH_NMS_LIMIT,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8},
                            .outputs = {9, 10, 11, 12}
                        }},
                .inputIndexes = {2, 13, 16},
                .outputIndexes = {9, 10, 11, 12}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_all_inputs_as_internal_2 = TestModelManager::get().add("box_with_nms_limit_linear_float16_all_inputs_as_internal_2", get_test_model_float16_all_inputs_as_internal_2());

}  // namespace generated_tests::box_with_nms_limit_linear

namespace generated_tests::box_with_nms_limit_linear {

const TestModel& get_test_model_quant8_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // scores1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {19, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({218, 223, 203, 208, 198, 213, 188, 218, 223, 218, 193, 218, 208, 213, 208, 188, 188, 148, 188, 208, 168, 218, 183, 188, 218, 203, 198, 208, 198, 213, 218, 223, 203, 208, 213, 208, 188, 218, 223, 188, 188, 148, 178, 218, 208, 218, 203, 198, 218, 193, 218, 218, 183, 188, 188, 208, 168})
                        }, { // roi1
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {19, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({8, 8, 80, 80, 0, 0, 80, 80, 0, 0, 80, 80, 16, 16, 88, 88, 8, 8, 88, 88, 8, 8, 88, 88, 24, 24, 96, 96, 16, 16, 96, 96, 16, 16, 96, 96, 32, 32, 104, 104, 24, 24, 104, 104, 24, 24, 104, 104, 40, 40, 112, 112, 32, 32, 112, 112, 32, 32, 112, 112, 48, 48, 120, 120, 40, 40, 120, 120, 40, 40, 120, 120, 56, 56, 128, 128, 48, 48, 128, 128, 48, 48, 128, 128, 64, 64, 136, 136, 56, 56, 136, 136, 56, 56, 136, 136, 72, 72, 144, 144, 64, 64, 144, 144, 64, 64, 144, 144, 16, 16, 88, 88, 16, 16, 96, 96, 16, 16, 96, 96, 8, 8, 80, 80, 8, 8, 88, 88, 8, 8, 88, 88, 40, 40, 112, 112, 40, 40, 120, 120, 40, 40, 120, 120, 24, 24, 96, 96, 24, 24, 104, 104, 24, 24, 104, 104, 48, 48, 120, 120, 48, 48, 128, 128, 48, 48, 128, 128, 0, 0, 8, 8, 0, 0, 16, 16, 0, 0, 16, 16, 72, 72, 144, 144, 72, 72, 152, 152, 72, 72, 152, 152, 32, 32, 104, 104, 32, 32, 112, 112, 32, 32, 112, 112, 64, 64, 136, 136, 64, 64, 144, 144, 64, 64, 144, 144, 56, 56, 128, 128, 56, 56, 136, 136, 56, 56, 136, 136})
                        }, { // batchSplit1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {19},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 1, 1, 1, 1, 1, 1, 1, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3})
                        }, { // param6
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // param7
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({8})
                        }, { // param8
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param9
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.4f})
                        }, { // param10
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.5f})
                        }, { // param11
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // scoresOut1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {15},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({223, 213, 203, 223, 198, 170, 168, 223, 218, 213, 203, 223, 208, 198, 170})
                        }, { // roiOut1
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {15, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({0, 0, 80, 80, 32, 32, 112, 112, 64, 64, 144, 144, 16, 16, 96, 96, 64, 64, 144, 144, 32, 32, 112, 112, 0, 0, 80, 80, 8, 8, 88, 88, 0, 0, 16, 16, 40, 40, 120, 120, 72, 72, 152, 152, 24, 24, 104, 104, 0, 0, 16, 16, 72, 72, 152, 152, 40, 40, 120, 120})
                        }, { // classesOut1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {15},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 1, 2, 2, 2, 2, 1, 1, 1, 1, 2, 2, 2, 2})
                        }, { // batchSplitOut1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {15},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 1, 1, 1, 1, 1, 3, 3, 3, 3, 3, 3, 3, 3})
                        }},
                .operations = {{
                            .type = TestOperationType::BOX_WITH_NMS_LIMIT,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8},
                            .outputs = {9, 10, 11, 12}
                        }},
                .inputIndexes = {0, 1, 2},
                .outputIndexes = {9, 10, 11, 12}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_2 = TestModelManager::get().add("box_with_nms_limit_linear_quant8_2", get_test_model_quant8_2());

}  // namespace generated_tests::box_with_nms_limit_linear

namespace generated_tests::box_with_nms_limit_linear {

const TestModel& get_test_model_quant8_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // scores1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {19, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // roi1
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {19, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({8, 8, 80, 80, 0, 0, 80, 80, 0, 0, 80, 80, 16, 16, 88, 88, 8, 8, 88, 88, 8, 8, 88, 88, 24, 24, 96, 96, 16, 16, 96, 96, 16, 16, 96, 96, 32, 32, 104, 104, 24, 24, 104, 104, 24, 24, 104, 104, 40, 40, 112, 112, 32, 32, 112, 112, 32, 32, 112, 112, 48, 48, 120, 120, 40, 40, 120, 120, 40, 40, 120, 120, 56, 56, 128, 128, 48, 48, 128, 128, 48, 48, 128, 128, 64, 64, 136, 136, 56, 56, 136, 136, 56, 56, 136, 136, 72, 72, 144, 144, 64, 64, 144, 144, 64, 64, 144, 144, 16, 16, 88, 88, 16, 16, 96, 96, 16, 16, 96, 96, 8, 8, 80, 80, 8, 8, 88, 88, 8, 8, 88, 88, 40, 40, 112, 112, 40, 40, 120, 120, 40, 40, 120, 120, 24, 24, 96, 96, 24, 24, 104, 104, 24, 24, 104, 104, 48, 48, 120, 120, 48, 48, 128, 128, 48, 48, 128, 128, 0, 0, 8, 8, 0, 0, 16, 16, 0, 0, 16, 16, 72, 72, 144, 144, 72, 72, 152, 152, 72, 72, 152, 152, 32, 32, 104, 104, 32, 32, 112, 112, 32, 32, 112, 112, 64, 64, 136, 136, 64, 64, 144, 144, 64, 64, 144, 144, 56, 56, 128, 128, 56, 56, 136, 136, 56, 56, 136, 136})
                        }, { // batchSplit1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {19},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 1, 1, 1, 1, 1, 1, 1, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3})
                        }, { // param6
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // param7
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({8})
                        }, { // param8
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param9
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.4f})
                        }, { // param10
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.5f})
                        }, { // param11
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // scoresOut1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {15},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({223, 213, 203, 223, 198, 170, 168, 223, 218, 213, 203, 223, 208, 198, 170})
                        }, { // roiOut1
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {15, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({0, 0, 80, 80, 32, 32, 112, 112, 64, 64, 144, 144, 16, 16, 96, 96, 64, 64, 144, 144, 32, 32, 112, 112, 0, 0, 80, 80, 8, 8, 88, 88, 0, 0, 16, 16, 40, 40, 120, 120, 72, 72, 152, 152, 24, 24, 104, 104, 0, 0, 16, 16, 72, 72, 152, 152, 40, 40, 120, 120})
                        }, { // classesOut1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {15},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 1, 2, 2, 2, 2, 1, 1, 1, 1, 2, 2, 2, 2})
                        }, { // batchSplitOut1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {15},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 1, 1, 1, 1, 1, 3, 3, 3, 3, 3, 3, 3, 3})
                        }, { // scores1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {19, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({218, 223, 203, 208, 198, 213, 188, 218, 223, 218, 193, 218, 208, 213, 208, 188, 188, 148, 188, 208, 168, 218, 183, 188, 218, 203, 198, 208, 198, 213, 218, 223, 203, 208, 213, 208, 188, 218, 223, 188, 188, 148, 178, 218, 208, 218, 203, 198, 218, 193, 218, 218, 183, 188, 188, 208, 168})
                        }, { // placeholder13
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
                        }, { // param25
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
                            .inputs = {13, 14, 15},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::BOX_WITH_NMS_LIMIT,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8},
                            .outputs = {9, 10, 11, 12}
                        }},
                .inputIndexes = {1, 2, 13},
                .outputIndexes = {9, 10, 11, 12}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_all_inputs_as_internal_2 = TestModelManager::get().add("box_with_nms_limit_linear_quant8_all_inputs_as_internal_2", get_test_model_quant8_all_inputs_as_internal_2());

}  // namespace generated_tests::box_with_nms_limit_linear

