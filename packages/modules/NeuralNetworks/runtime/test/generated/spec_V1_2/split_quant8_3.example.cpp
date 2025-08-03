// Generated from split_quant8_3.mod.py
// DO NOT EDIT
// clang-format off
#include "TestHarness.h"
using namespace test_helper;  // NOLINT(google-build-using-namespace)

namespace generated_tests::split_quant8_3 {

const TestModel& get_test_model() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 1,
                            .scale = 2.0f,
                            .zeroPoint = 3,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1, 2, 3, 4, 5, 6})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // num_splits
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // output0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 1},
                            .numberOfConsumers = 0,
                            .scale = 2.0f,
                            .zeroPoint = 3,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1, 4})
                        }, { // output1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 1},
                            .numberOfConsumers = 0,
                            .scale = 2.0f,
                            .zeroPoint = 3,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({2, 5})
                        }, { // output2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 1},
                            .numberOfConsumers = 0,
                            .scale = 2.0f,
                            .zeroPoint = 3,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({3, 6})
                        }},
                .operations = {{
                            .type = TestOperationType::SPLIT,
                            .inputs = {0, 1, 2},
                            .outputs = {3, 4, 5}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {3, 4, 5}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model = TestModelManager::get().add("split_quant8_3", get_test_model());

}  // namespace generated_tests::split_quant8_3

namespace generated_tests::split_quant8_3 {

const TestModel& get_test_model_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 1,
                            .scale = 2.0f,
                            .zeroPoint = 3,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // num_splits
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // output0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 1},
                            .numberOfConsumers = 0,
                            .scale = 2.0f,
                            .zeroPoint = 3,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1, 4})
                        }, { // output1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 1},
                            .numberOfConsumers = 0,
                            .scale = 2.0f,
                            .zeroPoint = 3,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({2, 5})
                        }, { // output2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 1},
                            .numberOfConsumers = 0,
                            .scale = 2.0f,
                            .zeroPoint = 3,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({3, 6})
                        }, { // input0_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 1,
                            .scale = 2.0f,
                            .zeroPoint = 3,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1, 2, 3, 4, 5, 6})
                        }, { // placeholder
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 2.0f,
                            .zeroPoint = 3,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({3})
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
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {6, 7, 8},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPLIT,
                            .inputs = {0, 1, 2},
                            .outputs = {3, 4, 5}
                        }},
                .inputIndexes = {6},
                .outputIndexes = {3, 4, 5}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal = TestModelManager::get().add("split_quant8_3_all_inputs_as_internal", get_test_model_all_inputs_as_internal());

}  // namespace generated_tests::split_quant8_3

