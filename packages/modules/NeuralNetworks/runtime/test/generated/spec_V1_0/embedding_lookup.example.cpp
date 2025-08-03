// Generated from embedding_lookup.mod.py
// DO NOT EDIT
// clang-format off
#include "TestHarness.h"
using namespace test_helper;  // NOLINT(google-build-using-namespace)

namespace generated_tests::embedding_lookup {

const TestModel& get_test_model() {
    static TestModel model = {
        .main = {
                .operands = {{ // index
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 0, 2})
                        }, { // value
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 2, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.01f, 0.02f, 0.03f, 0.1f, 0.11f, 0.12000000000000001f, 0.13f, 1.0f, 1.01f, 1.02f, 1.03f, 1.1f, 1.11f, 1.12f, 1.1300000000000001f, 2.0f, 2.01f, 2.02f, 2.03f, 2.1f, 2.11f, 2.12f, 2.13f})
                        }, { // output
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 2, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 1.01f, 1.02f, 1.03f, 1.1f, 1.11f, 1.12f, 1.13f, 0.0f, 0.01f, 0.02f, 0.03f, 0.1f, 0.11f, 0.12f, 0.13f, 2.0f, 2.01f, 2.02f, 2.03f, 2.1f, 2.11f, 2.12f, 2.13f})
                        }},
                .operations = {{
                            .type = TestOperationType::EMBEDDING_LOOKUP,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {0, 1},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_0
    };
    return model;
}

const auto dummy_test_model = TestModelManager::get().add("embedding_lookup", get_test_model());

}  // namespace generated_tests::embedding_lookup

namespace generated_tests::embedding_lookup {

const TestModel& get_test_model_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // index
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 0, 2})
                        }, { // value
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 2, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // output
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 2, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 1.01f, 1.02f, 1.03f, 1.1f, 1.11f, 1.12f, 1.13f, 0.0f, 0.01f, 0.02f, 0.03f, 0.1f, 0.11f, 0.12f, 0.13f, 2.0f, 2.01f, 2.02f, 2.03f, 2.1f, 2.11f, 2.12f, 2.13f})
                        }, { // value_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 2, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.01f, 0.02f, 0.03f, 0.1f, 0.11f, 0.12000000000000001f, 0.13f, 1.0f, 1.01f, 1.02f, 1.03f, 1.1f, 1.11f, 1.12f, 1.1300000000000001f, 2.0f, 2.01f, 2.02f, 2.03f, 2.1f, 2.11f, 2.12f, 2.13f})
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
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {3, 4, 5},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::EMBEDDING_LOOKUP,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {0, 3},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_0
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal = TestModelManager::get().add("embedding_lookup_all_inputs_as_internal", get_test_model_all_inputs_as_internal());

}  // namespace generated_tests::embedding_lookup

