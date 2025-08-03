// Generated from hashtable_lookup_quant8.mod.py
// DO NOT EDIT
// clang-format off
#include "TestHarness.h"
using namespace test_helper;  // NOLINT(google-build-using-namespace)

namespace generated_tests::hashtable_lookup_quant8 {

const TestModel& get_test_model() {
    static TestModel model = {
        .main = {
                .operands = {{ // lookup
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({123, 250, 255, 0})
                        }, { // key
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 123, 255})
                        }, { // value
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 10, 11, 20, 21})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({10, 11, 0, 0, 20, 21, 0, 1})
                        }, { // hits
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1, 0, 1, 1})
                        }},
                .operations = {{
                            .type = TestOperationType::HASHTABLE_LOOKUP,
                            .inputs = {0, 1, 2},
                            .outputs = {3, 4}
                        }},
                .inputIndexes = {0, 1, 2},
                .outputIndexes = {3, 4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_0
    };
    return model;
}

const auto dummy_test_model = TestModelManager::get().add("hashtable_lookup_quant8", get_test_model());

}  // namespace generated_tests::hashtable_lookup_quant8

namespace generated_tests::hashtable_lookup_quant8 {

const TestModel& get_test_model_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // lookup
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({123, 250, 255, 0})
                        }, { // key
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 123, 255})
                        }, { // value
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({10, 11, 0, 0, 20, 21, 0, 1})
                        }, { // hits
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1, 0, 1, 1})
                        }, { // value_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 10, 11, 20, 21})
                        }, { // placeholder
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
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
                            .inputs = {5, 6, 7},
                            .outputs = {2}
                        }, {
                            .type = TestOperationType::HASHTABLE_LOOKUP,
                            .inputs = {0, 1, 2},
                            .outputs = {3, 4}
                        }},
                .inputIndexes = {0, 1, 5},
                .outputIndexes = {3, 4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_0
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal = TestModelManager::get().add("hashtable_lookup_quant8_all_inputs_as_internal", get_test_model_all_inputs_as_internal());

}  // namespace generated_tests::hashtable_lookup_quant8

