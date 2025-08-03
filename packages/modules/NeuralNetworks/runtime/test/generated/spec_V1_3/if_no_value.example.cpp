// Generated from if_no_value.mod.py
// DO NOT EDIT
// clang-format off
#include "TestHarness.h"
using namespace test_helper;  // NOLINT(google-build-using-namespace)

namespace generated_tests::if_no_value {

const TestModel& get_test_model() {
    static TestModel model = {
        .main = {
                .operands = {{ // cond
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::NO_VALUE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }, { // param2
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param3
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // x
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f})
                        }, { // y
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({8.0f, 7.0f, 6.0f, 5.0f, 4.0f, 3.0f, 2.0f, 1.0f, 0.0f, -1.0f, -2.0f, -3.0f})
                        }, { // z
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({8.0f, 7.0f, 6.0f, 5.0f, 4.0f, 3.0f, 2.0f, 1.0f, 0.0f, -1.0f, -2.0f, -3.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::IF,
                            .inputs = {0, 1, 2, 3, 4},
                            .outputs = {5}
                        }},
                .inputIndexes = {3, 4},
                .outputIndexes = {5}
            },
        .referenced = {{ // param
                .operands = {{ // x1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // y1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                        }, { // z1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {0, 1},
                .outputIndexes = {3}
            }, { // param
                .operands = {{ // x2
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // y2
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                        }, { // z2
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {0, 1},
                .outputIndexes = {3}
            }},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = true,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model = TestModelManager::get().add("if_no_value", get_test_model());

}  // namespace generated_tests::if_no_value

namespace generated_tests::if_no_value {

const TestModel& get_test_model_relaxed() {
    static TestModel model = {
        .main = {
                .operands = {{ // cond
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::NO_VALUE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }, { // param2
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param3
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // x
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f})
                        }, { // y
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({8.0f, 7.0f, 6.0f, 5.0f, 4.0f, 3.0f, 2.0f, 1.0f, 0.0f, -1.0f, -2.0f, -3.0f})
                        }, { // z
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({8.0f, 7.0f, 6.0f, 5.0f, 4.0f, 3.0f, 2.0f, 1.0f, 0.0f, -1.0f, -2.0f, -3.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::IF,
                            .inputs = {0, 1, 2, 3, 4},
                            .outputs = {5}
                        }},
                .inputIndexes = {3, 4},
                .outputIndexes = {5}
            },
        .referenced = {{ // param
                .operands = {{ // x1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // y1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                        }, { // z1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {0, 1},
                .outputIndexes = {3}
            }, { // param
                .operands = {{ // x2
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // y2
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                        }, { // z2
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {0, 1},
                .outputIndexes = {3}
            }},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = true,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed = TestModelManager::get().add("if_no_value_relaxed", get_test_model_relaxed());

}  // namespace generated_tests::if_no_value

namespace generated_tests::if_no_value {

const TestModel& get_test_model_float16() {
    static TestModel model = {
        .main = {
                .operands = {{ // cond
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::NO_VALUE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }, { // param2
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param3
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // x
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f})
                        }, { // y
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({8.0f, 7.0f, 6.0f, 5.0f, 4.0f, 3.0f, 2.0f, 1.0f, 0.0f, -1.0f, -2.0f, -3.0f})
                        }, { // z
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({8.0f, 7.0f, 6.0f, 5.0f, 4.0f, 3.0f, 2.0f, 1.0f, 0.0f, -1.0f, -2.0f, -3.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::IF,
                            .inputs = {0, 1, 2, 3, 4},
                            .outputs = {5}
                        }},
                .inputIndexes = {3, 4},
                .outputIndexes = {5}
            },
        .referenced = {{ // param
                .operands = {{ // x1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // y1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
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
                        }, { // z1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {0, 1},
                .outputIndexes = {3}
            }, { // param
                .operands = {{ // x2
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // y2
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
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
                        }, { // z2
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {0, 1},
                .outputIndexes = {3}
            }},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = true,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_float16 = TestModelManager::get().add("if_no_value_float16", get_test_model_float16());

}  // namespace generated_tests::if_no_value

namespace generated_tests::if_no_value {

const TestModel& get_test_model_int32() {
    static TestModel model = {
        .main = {
                .operands = {{ // cond
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::NO_VALUE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }, { // param2
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param3
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // x
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12})
                        }, { // y
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({8, 7, 6, 5, 4, 3, 2, 1, 0, -1, -2, -3})
                        }, { // z
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({8, 7, 6, 5, 4, 3, 2, 1, 0, -1, -2, -3})
                        }},
                .operations = {{
                            .type = TestOperationType::IF,
                            .inputs = {0, 1, 2, 3, 4},
                            .outputs = {5}
                        }},
                .inputIndexes = {3, 4},
                .outputIndexes = {5}
            },
        .referenced = {{ // param
                .operands = {{ // x1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // y1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
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
                        }, { // z1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {0, 1},
                .outputIndexes = {3}
            }, { // param
                .operands = {{ // x2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // y2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
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
                        }, { // z2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {0, 1},
                .outputIndexes = {3}
            }},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = true,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_int32 = TestModelManager::get().add("if_no_value_int32", get_test_model_int32());

}  // namespace generated_tests::if_no_value

namespace generated_tests::if_no_value {

const TestModel& get_test_model_quant8() {
    static TestModel model = {
        .main = {
                .operands = {{ // cond
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::NO_VALUE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }, { // param2
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param3
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // x
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112})
                        }, { // y
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({108, 107, 106, 105, 104, 103, 102, 101, 100, 99, 98, 97})
                        }, { // z
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({108, 107, 106, 105, 104, 103, 102, 101, 100, 99, 98, 97})
                        }},
                .operations = {{
                            .type = TestOperationType::IF,
                            .inputs = {0, 1, 2, 3, 4},
                            .outputs = {5}
                        }},
                .inputIndexes = {3, 4},
                .outputIndexes = {5}
            },
        .referenced = {{ // param
                .operands = {{ // x1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // y1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                        }, { // z1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {0, 1},
                .outputIndexes = {3}
            }, { // param
                .operands = {{ // x2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // y2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                        }, { // z2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {0, 1},
                .outputIndexes = {3}
            }},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = true,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_quant8 = TestModelManager::get().add("if_no_value_quant8", get_test_model_quant8());

}  // namespace generated_tests::if_no_value

namespace generated_tests::if_no_value {

const TestModel& get_test_model_quant8_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // cond
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::NO_VALUE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }, { // param2
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param3
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // x
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112})
                        }, { // y
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({108, 107, 106, 105, 104, 103, 102, 101, 100, 99, 98, 97})
                        }, { // z
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({108, 107, 106, 105, 104, 103, 102, 101, 100, 99, 98, 97})
                        }},
                .operations = {{
                            .type = TestOperationType::IF,
                            .inputs = {0, 1, 2, 3, 4},
                            .outputs = {5}
                        }},
                .inputIndexes = {3, 4},
                .outputIndexes = {5}
            },
        .referenced = {{ // param
                .operands = {{ // x1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // y1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // z1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {0, 1},
                .outputIndexes = {3}
            }, { // param
                .operands = {{ // x2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // y2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // z2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {0, 1},
                .outputIndexes = {3}
            }},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = true,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_quant8_signed = TestModelManager::get().add("if_no_value_quant8_signed", get_test_model_quant8_signed());

}  // namespace generated_tests::if_no_value

