// Generated from if_constant.mod.py
// DO NOT EDIT
// clang-format off
#include "TestHarness.h"
using namespace test_helper;  // NOLINT(google-build-using-namespace)

namespace generated_tests::if_constant {

const TestModel& get_test_model_copy_true() {
    static TestModel model = {
        .main = {
                .operands = {{ // cond
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
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
                            .data = TestBuffer::createFromVector<float>({9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f})
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
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_copy_true = TestModelManager::get().add("if_constant_copy_true", get_test_model_copy_true());

}  // namespace generated_tests::if_constant

namespace generated_tests::if_constant {

const TestModel& get_test_model_copy_true_relaxed() {
    static TestModel model = {
        .main = {
                .operands = {{ // cond
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
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
                            .data = TestBuffer::createFromVector<float>({9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f})
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
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_copy_true_relaxed = TestModelManager::get().add("if_constant_copy_true_relaxed", get_test_model_copy_true_relaxed());

}  // namespace generated_tests::if_constant

namespace generated_tests::if_constant {

const TestModel& get_test_model_copy_true_float16() {
    static TestModel model = {
        .main = {
                .operands = {{ // cond
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
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
                            .data = TestBuffer::createFromVector<_Float16>({9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f})
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
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_copy_true_float16 = TestModelManager::get().add("if_constant_copy_true_float16", get_test_model_copy_true_float16());

}  // namespace generated_tests::if_constant

namespace generated_tests::if_constant {

const TestModel& get_test_model_copy_true_int32() {
    static TestModel model = {
        .main = {
                .operands = {{ // cond
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
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
                            .data = TestBuffer::createFromVector<int32_t>({9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9})
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
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_copy_true_int32 = TestModelManager::get().add("if_constant_copy_true_int32", get_test_model_copy_true_int32());

}  // namespace generated_tests::if_constant

namespace generated_tests::if_constant {

const TestModel& get_test_model_copy_true_quant8() {
    static TestModel model = {
        .main = {
                .operands = {{ // cond
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
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
                            .data = TestBuffer::createFromVector<uint8_t>({109, 109, 109, 109, 109, 109, 109, 109, 109, 109, 109, 109})
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
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_copy_true_quant8 = TestModelManager::get().add("if_constant_copy_true_quant8", get_test_model_copy_true_quant8());

}  // namespace generated_tests::if_constant

namespace generated_tests::if_constant {

const TestModel& get_test_model_copy_true_quant8_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // cond
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
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
                            .data = TestBuffer::createFromVector<int8_t>({109, 109, 109, 109, 109, 109, 109, 109, 109, 109, 109, 109})
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
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_copy_true_quant8_signed = TestModelManager::get().add("if_constant_copy_true_quant8_signed", get_test_model_copy_true_quant8_signed());

}  // namespace generated_tests::if_constant

namespace generated_tests::if_constant {

const TestModel& get_test_model_copy_false() {
    static TestModel model = {
        .main = {
                .operands = {{ // cond1
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // param6
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param7
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // x3
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f})
                        }, { // y3
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({8.0f, 7.0f, 6.0f, 5.0f, 4.0f, 3.0f, 2.0f, 1.0f, 0.0f, -1.0f, -2.0f, -3.0f})
                        }, { // z3
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({-7.0f, -5.0f, -3.0f, -1.0f, 1.0f, 3.0f, 5.0f, 7.0f, 9.0f, 11.0f, 13.0f, 15.0f})
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
                .operands = {{ // x4
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // y4
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                        }, { // z4
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
                .operands = {{ // x5
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // y5
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                        }, { // z5
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
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_copy_false = TestModelManager::get().add("if_constant_copy_false", get_test_model_copy_false());

}  // namespace generated_tests::if_constant

namespace generated_tests::if_constant {

const TestModel& get_test_model_copy_false_relaxed() {
    static TestModel model = {
        .main = {
                .operands = {{ // cond1
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // param6
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param7
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // x3
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f})
                        }, { // y3
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({8.0f, 7.0f, 6.0f, 5.0f, 4.0f, 3.0f, 2.0f, 1.0f, 0.0f, -1.0f, -2.0f, -3.0f})
                        }, { // z3
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({-7.0f, -5.0f, -3.0f, -1.0f, 1.0f, 3.0f, 5.0f, 7.0f, 9.0f, 11.0f, 13.0f, 15.0f})
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
                .operands = {{ // x4
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // y4
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                        }, { // z4
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
                .operands = {{ // x5
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // y5
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                        }, { // z5
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
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_copy_false_relaxed = TestModelManager::get().add("if_constant_copy_false_relaxed", get_test_model_copy_false_relaxed());

}  // namespace generated_tests::if_constant

namespace generated_tests::if_constant {

const TestModel& get_test_model_copy_false_float16() {
    static TestModel model = {
        .main = {
                .operands = {{ // cond1
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // param6
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param7
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // x3
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f})
                        }, { // y3
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({8.0f, 7.0f, 6.0f, 5.0f, 4.0f, 3.0f, 2.0f, 1.0f, 0.0f, -1.0f, -2.0f, -3.0f})
                        }, { // z3
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({-7.0f, -5.0f, -3.0f, -1.0f, 1.0f, 3.0f, 5.0f, 7.0f, 9.0f, 11.0f, 13.0f, 15.0f})
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
                .operands = {{ // x4
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // y4
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
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
                        }, { // z4
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
                .operands = {{ // x5
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // y5
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
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
                        }, { // z5
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
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_copy_false_float16 = TestModelManager::get().add("if_constant_copy_false_float16", get_test_model_copy_false_float16());

}  // namespace generated_tests::if_constant

namespace generated_tests::if_constant {

const TestModel& get_test_model_copy_false_int32() {
    static TestModel model = {
        .main = {
                .operands = {{ // cond1
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // param6
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param7
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // x3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12})
                        }, { // y3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({8, 7, 6, 5, 4, 3, 2, 1, 0, -1, -2, -3})
                        }, { // z3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-7, -5, -3, -1, 1, 3, 5, 7, 9, 11, 13, 15})
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
                .operands = {{ // x4
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // y4
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
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
                        }, { // z4
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
                .operands = {{ // x5
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // y5
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
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
                        }, { // z5
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
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_copy_false_int32 = TestModelManager::get().add("if_constant_copy_false_int32", get_test_model_copy_false_int32());

}  // namespace generated_tests::if_constant

namespace generated_tests::if_constant {

const TestModel& get_test_model_copy_false_quant8() {
    static TestModel model = {
        .main = {
                .operands = {{ // cond1
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // param6
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param7
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // x3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112})
                        }, { // y3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({108, 107, 106, 105, 104, 103, 102, 101, 100, 99, 98, 97})
                        }, { // z3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({93, 95, 97, 99, 101, 103, 105, 107, 109, 111, 113, 115})
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
                .operands = {{ // x4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // y4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                        }, { // z4
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
                .operands = {{ // x5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // y5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                        }, { // z5
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
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_copy_false_quant8 = TestModelManager::get().add("if_constant_copy_false_quant8", get_test_model_copy_false_quant8());

}  // namespace generated_tests::if_constant

namespace generated_tests::if_constant {

const TestModel& get_test_model_copy_false_quant8_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // cond1
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // param6
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param7
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // x3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112})
                        }, { // y3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({108, 107, 106, 105, 104, 103, 102, 101, 100, 99, 98, 97})
                        }, { // z3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({93, 95, 97, 99, 101, 103, 105, 107, 109, 111, 113, 115})
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
                .operands = {{ // x4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // y4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // z4
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
                .operands = {{ // x5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // y5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // z5
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
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_copy_false_quant8_signed = TestModelManager::get().add("if_constant_copy_false_quant8_signed", get_test_model_copy_false_quant8_signed());

}  // namespace generated_tests::if_constant

namespace generated_tests::if_constant {

const TestModel& get_test_model_reference_true() {
    static TestModel model = {
        .main = {
                .operands = {{ // cond2
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_REFERENCE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // param10
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param11
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // x6
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f})
                        }, { // y6
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({8.0f, 7.0f, 6.0f, 5.0f, 4.0f, 3.0f, 2.0f, 1.0f, 0.0f, -1.0f, -2.0f, -3.0f})
                        }, { // z6
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f})
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
                .operands = {{ // x7
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // y7
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // param8
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_REFERENCE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // z7
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
                .operands = {{ // x8
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // y8
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // param9
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_REFERENCE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // z8
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
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_reference_true = TestModelManager::get().add("if_constant_reference_true", get_test_model_reference_true());

}  // namespace generated_tests::if_constant

namespace generated_tests::if_constant {

const TestModel& get_test_model_reference_true_relaxed() {
    static TestModel model = {
        .main = {
                .operands = {{ // cond2
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_REFERENCE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // param10
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param11
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // x6
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f})
                        }, { // y6
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({8.0f, 7.0f, 6.0f, 5.0f, 4.0f, 3.0f, 2.0f, 1.0f, 0.0f, -1.0f, -2.0f, -3.0f})
                        }, { // z6
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f})
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
                .operands = {{ // x7
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // y7
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // param8
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_REFERENCE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // z7
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
                .operands = {{ // x8
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // y8
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // param9
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_REFERENCE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // z8
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
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_reference_true_relaxed = TestModelManager::get().add("if_constant_reference_true_relaxed", get_test_model_reference_true_relaxed());

}  // namespace generated_tests::if_constant

namespace generated_tests::if_constant {

const TestModel& get_test_model_reference_true_float16() {
    static TestModel model = {
        .main = {
                .operands = {{ // cond2
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_REFERENCE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // param10
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param11
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // x6
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f})
                        }, { // y6
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({8.0f, 7.0f, 6.0f, 5.0f, 4.0f, 3.0f, 2.0f, 1.0f, 0.0f, -1.0f, -2.0f, -3.0f})
                        }, { // z6
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f, 9.0f})
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
                .operands = {{ // x7
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // y7
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // param8
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_REFERENCE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // z7
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
                .operands = {{ // x8
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // y8
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // param9
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_REFERENCE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // z8
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
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_reference_true_float16 = TestModelManager::get().add("if_constant_reference_true_float16", get_test_model_reference_true_float16());

}  // namespace generated_tests::if_constant

namespace generated_tests::if_constant {

const TestModel& get_test_model_reference_true_int32() {
    static TestModel model = {
        .main = {
                .operands = {{ // cond2
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_REFERENCE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // param10
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param11
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // x6
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12})
                        }, { // y6
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({8, 7, 6, 5, 4, 3, 2, 1, 0, -1, -2, -3})
                        }, { // z6
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9})
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
                .operands = {{ // x7
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // y7
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // param8
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_REFERENCE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // z7
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
                .operands = {{ // x8
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // y8
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // param9
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_REFERENCE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // z8
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
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_reference_true_int32 = TestModelManager::get().add("if_constant_reference_true_int32", get_test_model_reference_true_int32());

}  // namespace generated_tests::if_constant

namespace generated_tests::if_constant {

const TestModel& get_test_model_reference_true_quant8() {
    static TestModel model = {
        .main = {
                .operands = {{ // cond2
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_REFERENCE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // param10
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param11
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // x6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112})
                        }, { // y6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({108, 107, 106, 105, 104, 103, 102, 101, 100, 99, 98, 97})
                        }, { // z6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({109, 109, 109, 109, 109, 109, 109, 109, 109, 109, 109, 109})
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
                .operands = {{ // x7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // y7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param8
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_REFERENCE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // z7
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
                .operands = {{ // x8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // y8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param9
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_REFERENCE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // z8
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
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_reference_true_quant8 = TestModelManager::get().add("if_constant_reference_true_quant8", get_test_model_reference_true_quant8());

}  // namespace generated_tests::if_constant

namespace generated_tests::if_constant {

const TestModel& get_test_model_reference_true_quant8_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // cond2
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_REFERENCE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // param10
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param11
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // x6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112})
                        }, { // y6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({108, 107, 106, 105, 104, 103, 102, 101, 100, 99, 98, 97})
                        }, { // z6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({109, 109, 109, 109, 109, 109, 109, 109, 109, 109, 109, 109})
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
                .operands = {{ // x7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // y7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param8
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_REFERENCE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // z7
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
                .operands = {{ // x8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // y8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param9
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_REFERENCE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // z8
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
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_reference_true_quant8_signed = TestModelManager::get().add("if_constant_reference_true_quant8_signed", get_test_model_reference_true_quant8_signed());

}  // namespace generated_tests::if_constant

namespace generated_tests::if_constant {

const TestModel& get_test_model_reference_false() {
    static TestModel model = {
        .main = {
                .operands = {{ // cond3
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_REFERENCE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // param14
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param15
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // x9
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f})
                        }, { // y9
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({8.0f, 7.0f, 6.0f, 5.0f, 4.0f, 3.0f, 2.0f, 1.0f, 0.0f, -1.0f, -2.0f, -3.0f})
                        }, { // z9
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({-7.0f, -5.0f, -3.0f, -1.0f, 1.0f, 3.0f, 5.0f, 7.0f, 9.0f, 11.0f, 13.0f, 15.0f})
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
                .operands = {{ // x10
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // y10
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // param12
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_REFERENCE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // z10
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
                .operands = {{ // x11
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // y11
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // param13
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_REFERENCE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // z11
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
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_reference_false = TestModelManager::get().add("if_constant_reference_false", get_test_model_reference_false());

}  // namespace generated_tests::if_constant

namespace generated_tests::if_constant {

const TestModel& get_test_model_reference_false_relaxed() {
    static TestModel model = {
        .main = {
                .operands = {{ // cond3
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_REFERENCE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // param14
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param15
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // x9
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f})
                        }, { // y9
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({8.0f, 7.0f, 6.0f, 5.0f, 4.0f, 3.0f, 2.0f, 1.0f, 0.0f, -1.0f, -2.0f, -3.0f})
                        }, { // z9
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({-7.0f, -5.0f, -3.0f, -1.0f, 1.0f, 3.0f, 5.0f, 7.0f, 9.0f, 11.0f, 13.0f, 15.0f})
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
                .operands = {{ // x10
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // y10
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // param12
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_REFERENCE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // z10
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
                .operands = {{ // x11
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // y11
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // param13
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_REFERENCE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // z11
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
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_reference_false_relaxed = TestModelManager::get().add("if_constant_reference_false_relaxed", get_test_model_reference_false_relaxed());

}  // namespace generated_tests::if_constant

namespace generated_tests::if_constant {

const TestModel& get_test_model_reference_false_float16() {
    static TestModel model = {
        .main = {
                .operands = {{ // cond3
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_REFERENCE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // param14
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param15
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // x9
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f})
                        }, { // y9
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({8.0f, 7.0f, 6.0f, 5.0f, 4.0f, 3.0f, 2.0f, 1.0f, 0.0f, -1.0f, -2.0f, -3.0f})
                        }, { // z9
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({-7.0f, -5.0f, -3.0f, -1.0f, 1.0f, 3.0f, 5.0f, 7.0f, 9.0f, 11.0f, 13.0f, 15.0f})
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
                .operands = {{ // x10
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // y10
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // param12
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_REFERENCE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // z10
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
                .operands = {{ // x11
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // y11
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // param13
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_REFERENCE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // z11
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
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_reference_false_float16 = TestModelManager::get().add("if_constant_reference_false_float16", get_test_model_reference_false_float16());

}  // namespace generated_tests::if_constant

namespace generated_tests::if_constant {

const TestModel& get_test_model_reference_false_int32() {
    static TestModel model = {
        .main = {
                .operands = {{ // cond3
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_REFERENCE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // param14
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param15
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // x9
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12})
                        }, { // y9
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({8, 7, 6, 5, 4, 3, 2, 1, 0, -1, -2, -3})
                        }, { // z9
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-7, -5, -3, -1, 1, 3, 5, 7, 9, 11, 13, 15})
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
                .operands = {{ // x10
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // y10
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // param12
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_REFERENCE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // z10
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
                .operands = {{ // x11
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // y11
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // param13
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_REFERENCE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // z11
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
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_reference_false_int32 = TestModelManager::get().add("if_constant_reference_false_int32", get_test_model_reference_false_int32());

}  // namespace generated_tests::if_constant

namespace generated_tests::if_constant {

const TestModel& get_test_model_reference_false_quant8() {
    static TestModel model = {
        .main = {
                .operands = {{ // cond3
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_REFERENCE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // param14
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param15
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // x9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112})
                        }, { // y9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({108, 107, 106, 105, 104, 103, 102, 101, 100, 99, 98, 97})
                        }, { // z9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({93, 95, 97, 99, 101, 103, 105, 107, 109, 111, 113, 115})
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
                .operands = {{ // x10
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // y10
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param12
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_REFERENCE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // z10
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
                .operands = {{ // x11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // y11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param13
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_REFERENCE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // z11
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
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_reference_false_quant8 = TestModelManager::get().add("if_constant_reference_false_quant8", get_test_model_reference_false_quant8());

}  // namespace generated_tests::if_constant

namespace generated_tests::if_constant {

const TestModel& get_test_model_reference_false_quant8_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // cond3
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_REFERENCE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // param14
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param15
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // x9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112})
                        }, { // y9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({108, 107, 106, 105, 104, 103, 102, 101, 100, 99, 98, 97})
                        }, { // z9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({93, 95, 97, 99, 101, 103, 105, 107, 109, 111, 113, 115})
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
                .operands = {{ // x10
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // y10
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param12
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_REFERENCE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // z10
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
                .operands = {{ // x11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // y11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param13
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_REFERENCE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // z11
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
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_reference_false_quant8_signed = TestModelManager::get().add("if_constant_reference_false_quant8_signed", get_test_model_reference_false_quant8_signed());

}  // namespace generated_tests::if_constant

