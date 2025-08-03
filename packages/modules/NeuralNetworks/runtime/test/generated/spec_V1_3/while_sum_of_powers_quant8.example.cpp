// Generated from while_sum_of_powers_quant8.mod.py
// DO NOT EDIT
// clang-format off
#include "TestHarness.h"
using namespace test_helper;  // NOLINT(google-build-using-namespace)

namespace generated_tests::while_sum_of_powers_quant8 {

const TestModel& get_test_model_n_0() {
    static TestModel model = {
        .main = {
                .operands = {{ // param10
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
                        }, { // sum_init
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 129})
                        }, { // param12
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // n
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // x
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({132, 134})
                        }, { // sum
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 129})
                        }},
                .operations = {{
                            .type = TestOperationType::WHILE,
                            .inputs = {0, 1, 2, 3, 4, 5},
                            .outputs = {6}
                        }},
                .inputIndexes = {4, 5},
                .outputIndexes = {6}
            },
        .referenced = {{ // param
                .operands = {{ // sum1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS_EQUAL,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // sum2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 2,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 2,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // sum_out
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i_out
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // zero
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128})
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
                        }, { // xi_init
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param4
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({2})
                        }, { // param5
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({3})
                        }, { // param6
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // xi2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param7
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
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
                            .inputs = {3, 6, 7},
                            .outputs = {8}
                        }, {
                            .type = TestOperationType::WHILE,
                            .inputs = {9, 10, 8, 11, 1, 3},
                            .outputs = {12}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 13, 14},
                            .outputs = {5}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {0, 12, 15},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }, { // param
                .operands = {{ // xi
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out1
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // xi1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // xi_out
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j_out
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
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
                        }, { // param1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
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
                            .type = TestOperationType::MUL,
                            .inputs = {0, 3, 6},
                            .outputs = {4}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 7, 8},
                            .outputs = {5}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_n_0 = TestModelManager::get().add("while_sum_of_powers_quant8_n_0", get_test_model_n_0());

}  // namespace generated_tests::while_sum_of_powers_quant8

namespace generated_tests::while_sum_of_powers_quant8 {

const TestModel& get_test_model_n_0_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // param10
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
                        }, { // sum_init
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 129})
                        }, { // param12
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // n
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // x
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // sum
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 129})
                        }, { // x_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({132, 134})
                        }, { // placeholder
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
                        }, { // param65
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
                            .inputs = {7, 8, 9},
                            .outputs = {5}
                        }, {
                            .type = TestOperationType::WHILE,
                            .inputs = {0, 1, 2, 3, 4, 5},
                            .outputs = {6}
                        }},
                .inputIndexes = {4, 7},
                .outputIndexes = {6}
            },
        .referenced = {{ // param
                .operands = {{ // sum1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS_EQUAL,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // sum2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 2,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 2,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // sum_out
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i_out
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // zero
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128})
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
                        }, { // xi_init
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param4
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({2})
                        }, { // param5
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({3})
                        }, { // param6
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // xi2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param7
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
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
                            .inputs = {3, 6, 7},
                            .outputs = {8}
                        }, {
                            .type = TestOperationType::WHILE,
                            .inputs = {9, 10, 8, 11, 1, 3},
                            .outputs = {12}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 13, 14},
                            .outputs = {5}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {0, 12, 15},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }, { // param
                .operands = {{ // xi
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out1
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // xi1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // xi_out
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j_out
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
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
                        }, { // param1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
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
                            .type = TestOperationType::MUL,
                            .inputs = {0, 3, 6},
                            .outputs = {4}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 7, 8},
                            .outputs = {5}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_n_0_all_inputs_as_internal = TestModelManager::get().add("while_sum_of_powers_quant8_n_0_all_inputs_as_internal", get_test_model_n_0_all_inputs_as_internal());

}  // namespace generated_tests::while_sum_of_powers_quant8

namespace generated_tests::while_sum_of_powers_quant8 {

const TestModel& get_test_model_n_0_all_tensors_as_inputs() {
    static TestModel model = {
        .main = {
                .operands = {{ // param10
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
                        }, { // sum_init
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 129})
                        }, { // param12
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // n
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // x
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({132, 134})
                        }, { // sum
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 129})
                        }},
                .operations = {{
                            .type = TestOperationType::WHILE,
                            .inputs = {0, 1, 2, 3, 4, 5},
                            .outputs = {6}
                        }},
                .inputIndexes = {2, 4, 5},
                .outputIndexes = {6}
            },
        .referenced = {{ // param
                .operands = {{ // sum1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS_EQUAL,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // sum2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 2,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 2,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // sum_out
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i_out
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // zero
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128})
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
                        }, { // xi_init
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param4
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({2})
                        }, { // param5
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({3})
                        }, { // param6
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // xi2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param7
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
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
                            .inputs = {3, 6, 7},
                            .outputs = {8}
                        }, {
                            .type = TestOperationType::WHILE,
                            .inputs = {9, 10, 8, 11, 1, 3},
                            .outputs = {12}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 13, 14},
                            .outputs = {5}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {0, 12, 15},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }, { // param
                .operands = {{ // xi
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out1
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // xi1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // xi_out
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j_out
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
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
                        }, { // param1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
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
                            .type = TestOperationType::MUL,
                            .inputs = {0, 3, 6},
                            .outputs = {4}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 7, 8},
                            .outputs = {5}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_n_0_all_tensors_as_inputs = TestModelManager::get().add("while_sum_of_powers_quant8_n_0_all_tensors_as_inputs", get_test_model_n_0_all_tensors_as_inputs());

}  // namespace generated_tests::while_sum_of_powers_quant8

namespace generated_tests::while_sum_of_powers_quant8 {

const TestModel& get_test_model_n_0_all_tensors_as_inputs_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // param10
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
                        }, { // sum_init
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param12
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // n
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // x
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // sum
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 129})
                        }, { // sum_init_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 129})
                        }, { // placeholder1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
                        }, { // param66
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // x_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({132, 134})
                        }, { // placeholder2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
                        }, { // param67
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
                            .inputs = {7, 8, 9},
                            .outputs = {2}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {10, 11, 12},
                            .outputs = {5}
                        }, {
                            .type = TestOperationType::WHILE,
                            .inputs = {0, 1, 2, 3, 4, 5},
                            .outputs = {6}
                        }},
                .inputIndexes = {4, 7, 10},
                .outputIndexes = {6}
            },
        .referenced = {{ // param
                .operands = {{ // sum1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS_EQUAL,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // sum2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 2,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 2,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // sum_out
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i_out
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // zero
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128})
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
                        }, { // xi_init
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param4
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({2})
                        }, { // param5
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({3})
                        }, { // param6
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // xi2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param7
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
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
                            .inputs = {3, 6, 7},
                            .outputs = {8}
                        }, {
                            .type = TestOperationType::WHILE,
                            .inputs = {9, 10, 8, 11, 1, 3},
                            .outputs = {12}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 13, 14},
                            .outputs = {5}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {0, 12, 15},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }, { // param
                .operands = {{ // xi
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out1
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // xi1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // xi_out
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j_out
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
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
                        }, { // param1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
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
                            .type = TestOperationType::MUL,
                            .inputs = {0, 3, 6},
                            .outputs = {4}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 7, 8},
                            .outputs = {5}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_n_0_all_tensors_as_inputs_all_inputs_as_internal = TestModelManager::get().add("while_sum_of_powers_quant8_n_0_all_tensors_as_inputs_all_inputs_as_internal", get_test_model_n_0_all_tensors_as_inputs_all_inputs_as_internal());

}  // namespace generated_tests::while_sum_of_powers_quant8

namespace generated_tests::while_sum_of_powers_quant8 {

const TestModel& get_test_model_n_0_all_outputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // param10
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
                        }, { // sum_init
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 129})
                        }, { // param12
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // n
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // x
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({132, 134})
                        }, { // sum
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // placeholder3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
                        }, { // param68
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // sum_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 129})
                        }},
                .operations = {{
                            .type = TestOperationType::WHILE,
                            .inputs = {0, 1, 2, 3, 4, 5},
                            .outputs = {6}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {6, 7, 8},
                            .outputs = {9}
                        }},
                .inputIndexes = {4, 5},
                .outputIndexes = {9}
            },
        .referenced = {{ // param
                .operands = {{ // sum1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS_EQUAL,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // sum2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 2,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 2,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // sum_out
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i_out
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // zero
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128})
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
                        }, { // xi_init
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param4
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({2})
                        }, { // param5
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({3})
                        }, { // param6
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // xi2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param7
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
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
                            .inputs = {3, 6, 7},
                            .outputs = {8}
                        }, {
                            .type = TestOperationType::WHILE,
                            .inputs = {9, 10, 8, 11, 1, 3},
                            .outputs = {12}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 13, 14},
                            .outputs = {5}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {0, 12, 15},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }, { // param
                .operands = {{ // xi
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out1
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // xi1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // xi_out
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j_out
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
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
                        }, { // param1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
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
                            .type = TestOperationType::MUL,
                            .inputs = {0, 3, 6},
                            .outputs = {4}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 7, 8},
                            .outputs = {5}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_n_0_all_outputs_as_internal = TestModelManager::get().add("while_sum_of_powers_quant8_n_0_all_outputs_as_internal", get_test_model_n_0_all_outputs_as_internal());

}  // namespace generated_tests::while_sum_of_powers_quant8

namespace generated_tests::while_sum_of_powers_quant8 {

const TestModel& get_test_model_n_1() {
    static TestModel model = {
        .main = {
                .operands = {{ // param23
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param24
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // sum_init1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 129})
                        }, { // param25
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // n3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // x5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({132, 134})
                        }, { // sum3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({131, 132})
                        }},
                .operations = {{
                            .type = TestOperationType::WHILE,
                            .inputs = {0, 1, 2, 3, 4, 5},
                            .outputs = {6}
                        }},
                .inputIndexes = {4, 5},
                .outputIndexes = {6}
            },
        .referenced = {{ // param
                .operands = {{ // sum4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i4
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n4
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out2
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS_EQUAL,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // sum5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i5
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 2,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n5
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 2,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // sum_out1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i_out1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // zero1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128})
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
                        }, { // xi_init1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param17
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({2})
                        }, { // param18
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({3})
                        }, { // param19
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // xi5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param20
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
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
                            .inputs = {3, 6, 7},
                            .outputs = {8}
                        }, {
                            .type = TestOperationType::WHILE,
                            .inputs = {9, 10, 8, 11, 1, 3},
                            .outputs = {12}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 13, 14},
                            .outputs = {5}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {0, 12, 15},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }, { // param
                .operands = {{ // xi3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i6
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out3
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // xi4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i7
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // xi_out1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j_out1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
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
                        }, { // param14
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
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
                            .type = TestOperationType::MUL,
                            .inputs = {0, 3, 6},
                            .outputs = {4}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 7, 8},
                            .outputs = {5}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_n_1 = TestModelManager::get().add("while_sum_of_powers_quant8_n_1", get_test_model_n_1());

}  // namespace generated_tests::while_sum_of_powers_quant8

namespace generated_tests::while_sum_of_powers_quant8 {

const TestModel& get_test_model_n_1_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // param23
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param24
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // sum_init1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 129})
                        }, { // param25
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // n3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // x5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // sum3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({131, 132})
                        }, { // x5_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({132, 134})
                        }, { // placeholder7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
                        }, { // param72
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
                            .inputs = {7, 8, 9},
                            .outputs = {5}
                        }, {
                            .type = TestOperationType::WHILE,
                            .inputs = {0, 1, 2, 3, 4, 5},
                            .outputs = {6}
                        }},
                .inputIndexes = {4, 7},
                .outputIndexes = {6}
            },
        .referenced = {{ // param
                .operands = {{ // sum4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i4
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n4
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out2
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS_EQUAL,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // sum5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i5
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 2,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n5
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 2,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // sum_out1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i_out1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // zero1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128})
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
                        }, { // xi_init1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param17
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({2})
                        }, { // param18
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({3})
                        }, { // param19
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // xi5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param20
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
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
                            .inputs = {3, 6, 7},
                            .outputs = {8}
                        }, {
                            .type = TestOperationType::WHILE,
                            .inputs = {9, 10, 8, 11, 1, 3},
                            .outputs = {12}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 13, 14},
                            .outputs = {5}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {0, 12, 15},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }, { // param
                .operands = {{ // xi3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i6
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out3
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // xi4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i7
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // xi_out1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j_out1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
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
                        }, { // param14
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
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
                            .type = TestOperationType::MUL,
                            .inputs = {0, 3, 6},
                            .outputs = {4}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 7, 8},
                            .outputs = {5}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_n_1_all_inputs_as_internal = TestModelManager::get().add("while_sum_of_powers_quant8_n_1_all_inputs_as_internal", get_test_model_n_1_all_inputs_as_internal());

}  // namespace generated_tests::while_sum_of_powers_quant8

namespace generated_tests::while_sum_of_powers_quant8 {

const TestModel& get_test_model_n_1_all_tensors_as_inputs() {
    static TestModel model = {
        .main = {
                .operands = {{ // param23
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param24
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // sum_init1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 129})
                        }, { // param25
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // n3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // x5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({132, 134})
                        }, { // sum3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({131, 132})
                        }},
                .operations = {{
                            .type = TestOperationType::WHILE,
                            .inputs = {0, 1, 2, 3, 4, 5},
                            .outputs = {6}
                        }},
                .inputIndexes = {2, 4, 5},
                .outputIndexes = {6}
            },
        .referenced = {{ // param
                .operands = {{ // sum4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i4
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n4
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out2
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS_EQUAL,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // sum5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i5
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 2,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n5
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 2,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // sum_out1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i_out1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // zero1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128})
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
                        }, { // xi_init1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param17
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({2})
                        }, { // param18
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({3})
                        }, { // param19
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // xi5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param20
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
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
                            .inputs = {3, 6, 7},
                            .outputs = {8}
                        }, {
                            .type = TestOperationType::WHILE,
                            .inputs = {9, 10, 8, 11, 1, 3},
                            .outputs = {12}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 13, 14},
                            .outputs = {5}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {0, 12, 15},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }, { // param
                .operands = {{ // xi3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i6
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out3
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // xi4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i7
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // xi_out1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j_out1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
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
                        }, { // param14
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
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
                            .type = TestOperationType::MUL,
                            .inputs = {0, 3, 6},
                            .outputs = {4}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 7, 8},
                            .outputs = {5}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_n_1_all_tensors_as_inputs = TestModelManager::get().add("while_sum_of_powers_quant8_n_1_all_tensors_as_inputs", get_test_model_n_1_all_tensors_as_inputs());

}  // namespace generated_tests::while_sum_of_powers_quant8

namespace generated_tests::while_sum_of_powers_quant8 {

const TestModel& get_test_model_n_1_all_tensors_as_inputs_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // param23
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param24
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // sum_init1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param25
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // n3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // x5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // sum3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({131, 132})
                        }, { // sum_init1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 129})
                        }, { // placeholder8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
                        }, { // param73
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // x5_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({132, 134})
                        }, { // placeholder9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
                        }, { // param74
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
                            .inputs = {7, 8, 9},
                            .outputs = {2}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {10, 11, 12},
                            .outputs = {5}
                        }, {
                            .type = TestOperationType::WHILE,
                            .inputs = {0, 1, 2, 3, 4, 5},
                            .outputs = {6}
                        }},
                .inputIndexes = {4, 7, 10},
                .outputIndexes = {6}
            },
        .referenced = {{ // param
                .operands = {{ // sum4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i4
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n4
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out2
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS_EQUAL,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // sum5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i5
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 2,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n5
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 2,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // sum_out1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i_out1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // zero1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128})
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
                        }, { // xi_init1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param17
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({2})
                        }, { // param18
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({3})
                        }, { // param19
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // xi5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param20
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
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
                            .inputs = {3, 6, 7},
                            .outputs = {8}
                        }, {
                            .type = TestOperationType::WHILE,
                            .inputs = {9, 10, 8, 11, 1, 3},
                            .outputs = {12}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 13, 14},
                            .outputs = {5}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {0, 12, 15},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }, { // param
                .operands = {{ // xi3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i6
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out3
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // xi4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i7
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // xi_out1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j_out1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
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
                        }, { // param14
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
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
                            .type = TestOperationType::MUL,
                            .inputs = {0, 3, 6},
                            .outputs = {4}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 7, 8},
                            .outputs = {5}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_n_1_all_tensors_as_inputs_all_inputs_as_internal = TestModelManager::get().add("while_sum_of_powers_quant8_n_1_all_tensors_as_inputs_all_inputs_as_internal", get_test_model_n_1_all_tensors_as_inputs_all_inputs_as_internal());

}  // namespace generated_tests::while_sum_of_powers_quant8

namespace generated_tests::while_sum_of_powers_quant8 {

const TestModel& get_test_model_n_1_all_outputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // param23
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param24
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // sum_init1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 129})
                        }, { // param25
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // n3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // x5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({132, 134})
                        }, { // sum3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // placeholder10
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
                        }, { // param75
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // sum3_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({131, 132})
                        }},
                .operations = {{
                            .type = TestOperationType::WHILE,
                            .inputs = {0, 1, 2, 3, 4, 5},
                            .outputs = {6}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {6, 7, 8},
                            .outputs = {9}
                        }},
                .inputIndexes = {4, 5},
                .outputIndexes = {9}
            },
        .referenced = {{ // param
                .operands = {{ // sum4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i4
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n4
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out2
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS_EQUAL,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // sum5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i5
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 2,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n5
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 2,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // sum_out1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i_out1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // zero1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128})
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
                        }, { // xi_init1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param17
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({2})
                        }, { // param18
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({3})
                        }, { // param19
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // xi5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param20
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
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
                            .inputs = {3, 6, 7},
                            .outputs = {8}
                        }, {
                            .type = TestOperationType::WHILE,
                            .inputs = {9, 10, 8, 11, 1, 3},
                            .outputs = {12}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 13, 14},
                            .outputs = {5}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {0, 12, 15},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }, { // param
                .operands = {{ // xi3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i6
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out3
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // xi4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i7
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // xi_out1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j_out1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
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
                        }, { // param14
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
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
                            .type = TestOperationType::MUL,
                            .inputs = {0, 3, 6},
                            .outputs = {4}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 7, 8},
                            .outputs = {5}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_n_1_all_outputs_as_internal = TestModelManager::get().add("while_sum_of_powers_quant8_n_1_all_outputs_as_internal", get_test_model_n_1_all_outputs_as_internal());

}  // namespace generated_tests::while_sum_of_powers_quant8

namespace generated_tests::while_sum_of_powers_quant8 {

const TestModel& get_test_model_n_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // param36
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param37
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // sum_init2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 129})
                        }, { // param38
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // n6
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // x10
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({132, 134})
                        }, { // sum6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({135, 141})
                        }},
                .operations = {{
                            .type = TestOperationType::WHILE,
                            .inputs = {0, 1, 2, 3, 4, 5},
                            .outputs = {6}
                        }},
                .inputIndexes = {4, 5},
                .outputIndexes = {6}
            },
        .referenced = {{ // param
                .operands = {{ // sum7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i8
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n7
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out4
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS_EQUAL,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // sum8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i9
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 2,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n8
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 2,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // sum_out2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i_out2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // zero2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128})
                        }, { // param29
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // xi_init2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param30
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({2})
                        }, { // param31
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({3})
                        }, { // param32
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // xi8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param33
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param34
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param35
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
                            .inputs = {3, 6, 7},
                            .outputs = {8}
                        }, {
                            .type = TestOperationType::WHILE,
                            .inputs = {9, 10, 8, 11, 1, 3},
                            .outputs = {12}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 13, 14},
                            .outputs = {5}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {0, 12, 15},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }, { // param
                .operands = {{ // xi6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j4
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i10
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x13
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out5
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // xi7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j5
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i11
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x14
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // xi_out2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j_out2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // param26
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param27
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param28
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
                            .type = TestOperationType::MUL,
                            .inputs = {0, 3, 6},
                            .outputs = {4}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 7, 8},
                            .outputs = {5}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_n_2 = TestModelManager::get().add("while_sum_of_powers_quant8_n_2", get_test_model_n_2());

}  // namespace generated_tests::while_sum_of_powers_quant8

namespace generated_tests::while_sum_of_powers_quant8 {

const TestModel& get_test_model_n_2_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // param36
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param37
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // sum_init2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 129})
                        }, { // param38
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // n6
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // x10
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // sum6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({135, 141})
                        }, { // x10_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({132, 134})
                        }, { // placeholder14
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
                        }, { // param79
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
                            .inputs = {7, 8, 9},
                            .outputs = {5}
                        }, {
                            .type = TestOperationType::WHILE,
                            .inputs = {0, 1, 2, 3, 4, 5},
                            .outputs = {6}
                        }},
                .inputIndexes = {4, 7},
                .outputIndexes = {6}
            },
        .referenced = {{ // param
                .operands = {{ // sum7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i8
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n7
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out4
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS_EQUAL,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // sum8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i9
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 2,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n8
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 2,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // sum_out2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i_out2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // zero2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128})
                        }, { // param29
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // xi_init2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param30
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({2})
                        }, { // param31
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({3})
                        }, { // param32
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // xi8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param33
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param34
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param35
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
                            .inputs = {3, 6, 7},
                            .outputs = {8}
                        }, {
                            .type = TestOperationType::WHILE,
                            .inputs = {9, 10, 8, 11, 1, 3},
                            .outputs = {12}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 13, 14},
                            .outputs = {5}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {0, 12, 15},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }, { // param
                .operands = {{ // xi6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j4
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i10
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x13
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out5
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // xi7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j5
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i11
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x14
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // xi_out2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j_out2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // param26
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param27
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param28
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
                            .type = TestOperationType::MUL,
                            .inputs = {0, 3, 6},
                            .outputs = {4}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 7, 8},
                            .outputs = {5}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_n_2_all_inputs_as_internal = TestModelManager::get().add("while_sum_of_powers_quant8_n_2_all_inputs_as_internal", get_test_model_n_2_all_inputs_as_internal());

}  // namespace generated_tests::while_sum_of_powers_quant8

namespace generated_tests::while_sum_of_powers_quant8 {

const TestModel& get_test_model_n_2_all_tensors_as_inputs() {
    static TestModel model = {
        .main = {
                .operands = {{ // param36
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param37
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // sum_init2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 129})
                        }, { // param38
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // n6
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // x10
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({132, 134})
                        }, { // sum6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({135, 141})
                        }},
                .operations = {{
                            .type = TestOperationType::WHILE,
                            .inputs = {0, 1, 2, 3, 4, 5},
                            .outputs = {6}
                        }},
                .inputIndexes = {2, 4, 5},
                .outputIndexes = {6}
            },
        .referenced = {{ // param
                .operands = {{ // sum7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i8
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n7
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out4
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS_EQUAL,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // sum8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i9
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 2,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n8
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 2,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // sum_out2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i_out2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // zero2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128})
                        }, { // param29
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // xi_init2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param30
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({2})
                        }, { // param31
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({3})
                        }, { // param32
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // xi8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param33
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param34
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param35
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
                            .inputs = {3, 6, 7},
                            .outputs = {8}
                        }, {
                            .type = TestOperationType::WHILE,
                            .inputs = {9, 10, 8, 11, 1, 3},
                            .outputs = {12}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 13, 14},
                            .outputs = {5}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {0, 12, 15},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }, { // param
                .operands = {{ // xi6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j4
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i10
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x13
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out5
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // xi7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j5
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i11
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x14
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // xi_out2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j_out2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // param26
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param27
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param28
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
                            .type = TestOperationType::MUL,
                            .inputs = {0, 3, 6},
                            .outputs = {4}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 7, 8},
                            .outputs = {5}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_n_2_all_tensors_as_inputs = TestModelManager::get().add("while_sum_of_powers_quant8_n_2_all_tensors_as_inputs", get_test_model_n_2_all_tensors_as_inputs());

}  // namespace generated_tests::while_sum_of_powers_quant8

namespace generated_tests::while_sum_of_powers_quant8 {

const TestModel& get_test_model_n_2_all_tensors_as_inputs_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // param36
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param37
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // sum_init2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param38
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // n6
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // x10
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // sum6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({135, 141})
                        }, { // sum_init2_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 129})
                        }, { // placeholder15
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
                        }, { // param80
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // x10_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({132, 134})
                        }, { // placeholder16
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
                        }, { // param81
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
                            .inputs = {7, 8, 9},
                            .outputs = {2}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {10, 11, 12},
                            .outputs = {5}
                        }, {
                            .type = TestOperationType::WHILE,
                            .inputs = {0, 1, 2, 3, 4, 5},
                            .outputs = {6}
                        }},
                .inputIndexes = {4, 7, 10},
                .outputIndexes = {6}
            },
        .referenced = {{ // param
                .operands = {{ // sum7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i8
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n7
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out4
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS_EQUAL,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // sum8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i9
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 2,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n8
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 2,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // sum_out2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i_out2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // zero2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128})
                        }, { // param29
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // xi_init2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param30
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({2})
                        }, { // param31
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({3})
                        }, { // param32
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // xi8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param33
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param34
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param35
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
                            .inputs = {3, 6, 7},
                            .outputs = {8}
                        }, {
                            .type = TestOperationType::WHILE,
                            .inputs = {9, 10, 8, 11, 1, 3},
                            .outputs = {12}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 13, 14},
                            .outputs = {5}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {0, 12, 15},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }, { // param
                .operands = {{ // xi6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j4
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i10
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x13
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out5
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // xi7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j5
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i11
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x14
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // xi_out2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j_out2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // param26
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param27
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param28
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
                            .type = TestOperationType::MUL,
                            .inputs = {0, 3, 6},
                            .outputs = {4}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 7, 8},
                            .outputs = {5}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_n_2_all_tensors_as_inputs_all_inputs_as_internal = TestModelManager::get().add("while_sum_of_powers_quant8_n_2_all_tensors_as_inputs_all_inputs_as_internal", get_test_model_n_2_all_tensors_as_inputs_all_inputs_as_internal());

}  // namespace generated_tests::while_sum_of_powers_quant8

namespace generated_tests::while_sum_of_powers_quant8 {

const TestModel& get_test_model_n_2_all_outputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // param36
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param37
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // sum_init2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 129})
                        }, { // param38
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // n6
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // x10
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({132, 134})
                        }, { // sum6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // placeholder17
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
                        }, { // param82
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // sum6_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({135, 141})
                        }},
                .operations = {{
                            .type = TestOperationType::WHILE,
                            .inputs = {0, 1, 2, 3, 4, 5},
                            .outputs = {6}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {6, 7, 8},
                            .outputs = {9}
                        }},
                .inputIndexes = {4, 5},
                .outputIndexes = {9}
            },
        .referenced = {{ // param
                .operands = {{ // sum7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i8
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n7
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out4
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS_EQUAL,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // sum8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i9
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 2,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n8
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 2,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // sum_out2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i_out2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // zero2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128})
                        }, { // param29
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // xi_init2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param30
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({2})
                        }, { // param31
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({3})
                        }, { // param32
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // xi8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param33
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param34
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param35
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
                            .inputs = {3, 6, 7},
                            .outputs = {8}
                        }, {
                            .type = TestOperationType::WHILE,
                            .inputs = {9, 10, 8, 11, 1, 3},
                            .outputs = {12}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 13, 14},
                            .outputs = {5}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {0, 12, 15},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }, { // param
                .operands = {{ // xi6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j4
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i10
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x13
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out5
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // xi7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j5
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i11
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x14
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // xi_out2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j_out2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // param26
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param27
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param28
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
                            .type = TestOperationType::MUL,
                            .inputs = {0, 3, 6},
                            .outputs = {4}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 7, 8},
                            .outputs = {5}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_n_2_all_outputs_as_internal = TestModelManager::get().add("while_sum_of_powers_quant8_n_2_all_outputs_as_internal", get_test_model_n_2_all_outputs_as_internal());

}  // namespace generated_tests::while_sum_of_powers_quant8

namespace generated_tests::while_sum_of_powers_quant8 {

const TestModel& get_test_model_n_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // param49
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param50
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // sum_init3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 129})
                        }, { // param51
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // n9
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // x15
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({132, 134})
                        }, { // sum9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({143, 168})
                        }},
                .operations = {{
                            .type = TestOperationType::WHILE,
                            .inputs = {0, 1, 2, 3, 4, 5},
                            .outputs = {6}
                        }},
                .inputIndexes = {4, 5},
                .outputIndexes = {6}
            },
        .referenced = {{ // param
                .operands = {{ // sum10
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i12
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n10
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x16
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out6
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS_EQUAL,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // sum11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i13
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 2,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n11
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x17
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 2,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // sum_out3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i_out3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // zero3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128})
                        }, { // param42
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // xi_init3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param43
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({2})
                        }, { // param44
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({3})
                        }, { // param45
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // xi11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param46
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param47
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param48
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
                            .inputs = {3, 6, 7},
                            .outputs = {8}
                        }, {
                            .type = TestOperationType::WHILE,
                            .inputs = {9, 10, 8, 11, 1, 3},
                            .outputs = {12}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 13, 14},
                            .outputs = {5}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {0, 12, 15},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }, { // param
                .operands = {{ // xi9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j6
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i14
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x18
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out7
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // xi10
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j7
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i15
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x19
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // xi_out3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j_out3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // param39
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param40
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param41
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
                            .type = TestOperationType::MUL,
                            .inputs = {0, 3, 6},
                            .outputs = {4}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 7, 8},
                            .outputs = {5}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_n_3 = TestModelManager::get().add("while_sum_of_powers_quant8_n_3", get_test_model_n_3());

}  // namespace generated_tests::while_sum_of_powers_quant8

namespace generated_tests::while_sum_of_powers_quant8 {

const TestModel& get_test_model_n_3_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // param49
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param50
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // sum_init3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 129})
                        }, { // param51
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // n9
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // x15
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // sum9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({143, 168})
                        }, { // x15_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({132, 134})
                        }, { // placeholder21
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
                        }, { // param86
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
                            .inputs = {7, 8, 9},
                            .outputs = {5}
                        }, {
                            .type = TestOperationType::WHILE,
                            .inputs = {0, 1, 2, 3, 4, 5},
                            .outputs = {6}
                        }},
                .inputIndexes = {4, 7},
                .outputIndexes = {6}
            },
        .referenced = {{ // param
                .operands = {{ // sum10
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i12
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n10
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x16
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out6
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS_EQUAL,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // sum11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i13
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 2,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n11
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x17
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 2,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // sum_out3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i_out3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // zero3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128})
                        }, { // param42
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // xi_init3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param43
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({2})
                        }, { // param44
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({3})
                        }, { // param45
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // xi11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param46
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param47
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param48
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
                            .inputs = {3, 6, 7},
                            .outputs = {8}
                        }, {
                            .type = TestOperationType::WHILE,
                            .inputs = {9, 10, 8, 11, 1, 3},
                            .outputs = {12}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 13, 14},
                            .outputs = {5}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {0, 12, 15},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }, { // param
                .operands = {{ // xi9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j6
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i14
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x18
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out7
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // xi10
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j7
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i15
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x19
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // xi_out3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j_out3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // param39
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param40
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param41
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
                            .type = TestOperationType::MUL,
                            .inputs = {0, 3, 6},
                            .outputs = {4}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 7, 8},
                            .outputs = {5}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_n_3_all_inputs_as_internal = TestModelManager::get().add("while_sum_of_powers_quant8_n_3_all_inputs_as_internal", get_test_model_n_3_all_inputs_as_internal());

}  // namespace generated_tests::while_sum_of_powers_quant8

namespace generated_tests::while_sum_of_powers_quant8 {

const TestModel& get_test_model_n_3_all_tensors_as_inputs() {
    static TestModel model = {
        .main = {
                .operands = {{ // param49
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param50
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // sum_init3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 129})
                        }, { // param51
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // n9
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // x15
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({132, 134})
                        }, { // sum9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({143, 168})
                        }},
                .operations = {{
                            .type = TestOperationType::WHILE,
                            .inputs = {0, 1, 2, 3, 4, 5},
                            .outputs = {6}
                        }},
                .inputIndexes = {2, 4, 5},
                .outputIndexes = {6}
            },
        .referenced = {{ // param
                .operands = {{ // sum10
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i12
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n10
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x16
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out6
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS_EQUAL,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // sum11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i13
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 2,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n11
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x17
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 2,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // sum_out3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i_out3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // zero3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128})
                        }, { // param42
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // xi_init3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param43
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({2})
                        }, { // param44
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({3})
                        }, { // param45
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // xi11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param46
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param47
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param48
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
                            .inputs = {3, 6, 7},
                            .outputs = {8}
                        }, {
                            .type = TestOperationType::WHILE,
                            .inputs = {9, 10, 8, 11, 1, 3},
                            .outputs = {12}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 13, 14},
                            .outputs = {5}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {0, 12, 15},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }, { // param
                .operands = {{ // xi9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j6
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i14
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x18
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out7
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // xi10
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j7
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i15
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x19
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // xi_out3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j_out3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // param39
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param40
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param41
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
                            .type = TestOperationType::MUL,
                            .inputs = {0, 3, 6},
                            .outputs = {4}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 7, 8},
                            .outputs = {5}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_n_3_all_tensors_as_inputs = TestModelManager::get().add("while_sum_of_powers_quant8_n_3_all_tensors_as_inputs", get_test_model_n_3_all_tensors_as_inputs());

}  // namespace generated_tests::while_sum_of_powers_quant8

namespace generated_tests::while_sum_of_powers_quant8 {

const TestModel& get_test_model_n_3_all_tensors_as_inputs_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // param49
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param50
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // sum_init3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param51
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // n9
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // x15
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // sum9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({143, 168})
                        }, { // sum_init3_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 129})
                        }, { // placeholder22
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
                        }, { // param87
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // x15_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({132, 134})
                        }, { // placeholder23
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
                        }, { // param88
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
                            .inputs = {7, 8, 9},
                            .outputs = {2}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {10, 11, 12},
                            .outputs = {5}
                        }, {
                            .type = TestOperationType::WHILE,
                            .inputs = {0, 1, 2, 3, 4, 5},
                            .outputs = {6}
                        }},
                .inputIndexes = {4, 7, 10},
                .outputIndexes = {6}
            },
        .referenced = {{ // param
                .operands = {{ // sum10
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i12
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n10
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x16
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out6
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS_EQUAL,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // sum11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i13
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 2,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n11
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x17
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 2,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // sum_out3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i_out3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // zero3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128})
                        }, { // param42
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // xi_init3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param43
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({2})
                        }, { // param44
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({3})
                        }, { // param45
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // xi11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param46
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param47
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param48
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
                            .inputs = {3, 6, 7},
                            .outputs = {8}
                        }, {
                            .type = TestOperationType::WHILE,
                            .inputs = {9, 10, 8, 11, 1, 3},
                            .outputs = {12}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 13, 14},
                            .outputs = {5}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {0, 12, 15},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }, { // param
                .operands = {{ // xi9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j6
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i14
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x18
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out7
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // xi10
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j7
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i15
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x19
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // xi_out3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j_out3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // param39
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param40
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param41
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
                            .type = TestOperationType::MUL,
                            .inputs = {0, 3, 6},
                            .outputs = {4}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 7, 8},
                            .outputs = {5}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_n_3_all_tensors_as_inputs_all_inputs_as_internal = TestModelManager::get().add("while_sum_of_powers_quant8_n_3_all_tensors_as_inputs_all_inputs_as_internal", get_test_model_n_3_all_tensors_as_inputs_all_inputs_as_internal());

}  // namespace generated_tests::while_sum_of_powers_quant8

namespace generated_tests::while_sum_of_powers_quant8 {

const TestModel& get_test_model_n_3_all_outputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // param49
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param50
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // sum_init3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 129})
                        }, { // param51
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // n9
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // x15
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({132, 134})
                        }, { // sum9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // placeholder24
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
                        }, { // param89
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // sum9_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({143, 168})
                        }},
                .operations = {{
                            .type = TestOperationType::WHILE,
                            .inputs = {0, 1, 2, 3, 4, 5},
                            .outputs = {6}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {6, 7, 8},
                            .outputs = {9}
                        }},
                .inputIndexes = {4, 5},
                .outputIndexes = {9}
            },
        .referenced = {{ // param
                .operands = {{ // sum10
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i12
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n10
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x16
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out6
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS_EQUAL,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // sum11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i13
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 2,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n11
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x17
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 2,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // sum_out3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i_out3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // zero3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128})
                        }, { // param42
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // xi_init3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param43
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({2})
                        }, { // param44
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({3})
                        }, { // param45
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // xi11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param46
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param47
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param48
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
                            .inputs = {3, 6, 7},
                            .outputs = {8}
                        }, {
                            .type = TestOperationType::WHILE,
                            .inputs = {9, 10, 8, 11, 1, 3},
                            .outputs = {12}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 13, 14},
                            .outputs = {5}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {0, 12, 15},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }, { // param
                .operands = {{ // xi9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j6
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i14
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x18
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out7
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // xi10
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j7
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i15
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x19
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // xi_out3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j_out3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // param39
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param40
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param41
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
                            .type = TestOperationType::MUL,
                            .inputs = {0, 3, 6},
                            .outputs = {4}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 7, 8},
                            .outputs = {5}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_n_3_all_outputs_as_internal = TestModelManager::get().add("while_sum_of_powers_quant8_n_3_all_outputs_as_internal", get_test_model_n_3_all_outputs_as_internal());

}  // namespace generated_tests::while_sum_of_powers_quant8

namespace generated_tests::while_sum_of_powers_quant8 {

const TestModel& get_test_model_n_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // param62
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param63
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // sum_init4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 129})
                        }, { // param64
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // n12
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({4})
                        }, { // x20
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({132, 134})
                        }, { // sum12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({159, 249})
                        }},
                .operations = {{
                            .type = TestOperationType::WHILE,
                            .inputs = {0, 1, 2, 3, 4, 5},
                            .outputs = {6}
                        }},
                .inputIndexes = {4, 5},
                .outputIndexes = {6}
            },
        .referenced = {{ // param
                .operands = {{ // sum13
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i16
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n13
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x21
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out8
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS_EQUAL,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // sum14
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i17
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 2,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n14
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x22
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 2,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // sum_out4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i_out4
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // zero4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128})
                        }, { // param55
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // xi_init4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param56
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({2})
                        }, { // param57
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({3})
                        }, { // param58
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // xi14
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param59
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param60
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param61
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
                            .inputs = {3, 6, 7},
                            .outputs = {8}
                        }, {
                            .type = TestOperationType::WHILE,
                            .inputs = {9, 10, 8, 11, 1, 3},
                            .outputs = {12}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 13, 14},
                            .outputs = {5}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {0, 12, 15},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }, { // param
                .operands = {{ // xi12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j8
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i18
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x23
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out9
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // xi13
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j9
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i19
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x24
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // xi_out4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j_out4
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // param52
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param53
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param54
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
                            .type = TestOperationType::MUL,
                            .inputs = {0, 3, 6},
                            .outputs = {4}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 7, 8},
                            .outputs = {5}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_n_4 = TestModelManager::get().add("while_sum_of_powers_quant8_n_4", get_test_model_n_4());

}  // namespace generated_tests::while_sum_of_powers_quant8

namespace generated_tests::while_sum_of_powers_quant8 {

const TestModel& get_test_model_n_4_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // param62
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param63
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // sum_init4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 129})
                        }, { // param64
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // n12
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({4})
                        }, { // x20
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // sum12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({159, 249})
                        }, { // x20_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({132, 134})
                        }, { // placeholder28
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
                        }, { // param93
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
                            .inputs = {7, 8, 9},
                            .outputs = {5}
                        }, {
                            .type = TestOperationType::WHILE,
                            .inputs = {0, 1, 2, 3, 4, 5},
                            .outputs = {6}
                        }},
                .inputIndexes = {4, 7},
                .outputIndexes = {6}
            },
        .referenced = {{ // param
                .operands = {{ // sum13
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i16
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n13
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x21
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out8
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS_EQUAL,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // sum14
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i17
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 2,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n14
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x22
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 2,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // sum_out4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i_out4
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // zero4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128})
                        }, { // param55
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // xi_init4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param56
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({2})
                        }, { // param57
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({3})
                        }, { // param58
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // xi14
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param59
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param60
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param61
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
                            .inputs = {3, 6, 7},
                            .outputs = {8}
                        }, {
                            .type = TestOperationType::WHILE,
                            .inputs = {9, 10, 8, 11, 1, 3},
                            .outputs = {12}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 13, 14},
                            .outputs = {5}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {0, 12, 15},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }, { // param
                .operands = {{ // xi12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j8
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i18
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x23
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out9
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // xi13
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j9
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i19
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x24
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // xi_out4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j_out4
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // param52
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param53
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param54
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
                            .type = TestOperationType::MUL,
                            .inputs = {0, 3, 6},
                            .outputs = {4}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 7, 8},
                            .outputs = {5}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_n_4_all_inputs_as_internal = TestModelManager::get().add("while_sum_of_powers_quant8_n_4_all_inputs_as_internal", get_test_model_n_4_all_inputs_as_internal());

}  // namespace generated_tests::while_sum_of_powers_quant8

namespace generated_tests::while_sum_of_powers_quant8 {

const TestModel& get_test_model_n_4_all_tensors_as_inputs() {
    static TestModel model = {
        .main = {
                .operands = {{ // param62
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param63
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // sum_init4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 129})
                        }, { // param64
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // n12
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({4})
                        }, { // x20
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({132, 134})
                        }, { // sum12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({159, 249})
                        }},
                .operations = {{
                            .type = TestOperationType::WHILE,
                            .inputs = {0, 1, 2, 3, 4, 5},
                            .outputs = {6}
                        }},
                .inputIndexes = {2, 4, 5},
                .outputIndexes = {6}
            },
        .referenced = {{ // param
                .operands = {{ // sum13
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i16
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n13
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x21
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out8
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS_EQUAL,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // sum14
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i17
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 2,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n14
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x22
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 2,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // sum_out4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i_out4
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // zero4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128})
                        }, { // param55
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // xi_init4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param56
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({2})
                        }, { // param57
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({3})
                        }, { // param58
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // xi14
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param59
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param60
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param61
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
                            .inputs = {3, 6, 7},
                            .outputs = {8}
                        }, {
                            .type = TestOperationType::WHILE,
                            .inputs = {9, 10, 8, 11, 1, 3},
                            .outputs = {12}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 13, 14},
                            .outputs = {5}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {0, 12, 15},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }, { // param
                .operands = {{ // xi12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j8
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i18
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x23
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out9
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // xi13
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j9
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i19
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x24
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // xi_out4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j_out4
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // param52
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param53
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param54
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
                            .type = TestOperationType::MUL,
                            .inputs = {0, 3, 6},
                            .outputs = {4}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 7, 8},
                            .outputs = {5}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_n_4_all_tensors_as_inputs = TestModelManager::get().add("while_sum_of_powers_quant8_n_4_all_tensors_as_inputs", get_test_model_n_4_all_tensors_as_inputs());

}  // namespace generated_tests::while_sum_of_powers_quant8

namespace generated_tests::while_sum_of_powers_quant8 {

const TestModel& get_test_model_n_4_all_tensors_as_inputs_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // param62
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param63
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // sum_init4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param64
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // n12
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({4})
                        }, { // x20
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // sum12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({159, 249})
                        }, { // sum_init4_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 129})
                        }, { // placeholder29
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
                        }, { // param94
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // x20_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({132, 134})
                        }, { // placeholder30
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
                        }, { // param95
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
                            .inputs = {7, 8, 9},
                            .outputs = {2}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {10, 11, 12},
                            .outputs = {5}
                        }, {
                            .type = TestOperationType::WHILE,
                            .inputs = {0, 1, 2, 3, 4, 5},
                            .outputs = {6}
                        }},
                .inputIndexes = {4, 7, 10},
                .outputIndexes = {6}
            },
        .referenced = {{ // param
                .operands = {{ // sum13
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i16
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n13
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x21
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out8
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS_EQUAL,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // sum14
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i17
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 2,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n14
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x22
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 2,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // sum_out4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i_out4
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // zero4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128})
                        }, { // param55
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // xi_init4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param56
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({2})
                        }, { // param57
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({3})
                        }, { // param58
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // xi14
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param59
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param60
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param61
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
                            .inputs = {3, 6, 7},
                            .outputs = {8}
                        }, {
                            .type = TestOperationType::WHILE,
                            .inputs = {9, 10, 8, 11, 1, 3},
                            .outputs = {12}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 13, 14},
                            .outputs = {5}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {0, 12, 15},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }, { // param
                .operands = {{ // xi12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j8
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i18
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x23
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out9
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // xi13
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j9
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i19
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x24
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // xi_out4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j_out4
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // param52
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param53
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param54
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
                            .type = TestOperationType::MUL,
                            .inputs = {0, 3, 6},
                            .outputs = {4}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 7, 8},
                            .outputs = {5}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_n_4_all_tensors_as_inputs_all_inputs_as_internal = TestModelManager::get().add("while_sum_of_powers_quant8_n_4_all_tensors_as_inputs_all_inputs_as_internal", get_test_model_n_4_all_tensors_as_inputs_all_inputs_as_internal());

}  // namespace generated_tests::while_sum_of_powers_quant8

namespace generated_tests::while_sum_of_powers_quant8 {

const TestModel& get_test_model_n_4_all_outputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // param62
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({0})
                        }, { // param63
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({1})
                        }, { // sum_init4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 129})
                        }, { // param64
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // n12
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({4})
                        }, { // x20
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({132, 134})
                        }, { // sum12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // placeholder31
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
                        }, { // param96
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // sum12_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({159, 249})
                        }},
                .operations = {{
                            .type = TestOperationType::WHILE,
                            .inputs = {0, 1, 2, 3, 4, 5},
                            .outputs = {6}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {6, 7, 8},
                            .outputs = {9}
                        }},
                .inputIndexes = {4, 5},
                .outputIndexes = {9}
            },
        .referenced = {{ // param
                .operands = {{ // sum13
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i16
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n13
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x21
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out8
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS_EQUAL,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // sum14
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i17
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 2,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // n14
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x22
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 2,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // sum_out4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // i_out4
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // zero4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128})
                        }, { // param55
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // xi_init4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param56
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({2})
                        }, { // param57
                            .type = TestOperandType::SUBGRAPH,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint32_t>({3})
                        }, { // param58
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // xi14
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param59
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param60
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param61
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
                            .inputs = {3, 6, 7},
                            .outputs = {8}
                        }, {
                            .type = TestOperationType::WHILE,
                            .inputs = {9, 10, 8, 11, 1, 3},
                            .outputs = {12}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 13, 14},
                            .outputs = {5}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {0, 12, 15},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }, { // param
                .operands = {{ // xi12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j8
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i18
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x23
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out9
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({})
                        }},
                .operations = {{
                            .type = TestOperationType::LESS,
                            .inputs = {1, 2},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4}
            }, { // param
                .operands = {{ // xi13
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j9
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // i19
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // x24
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // xi_out4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // j_out4
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // param52
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param53
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param54
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
                            .type = TestOperationType::MUL,
                            .inputs = {0, 3, 6},
                            .outputs = {4}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {1, 7, 8},
                            .outputs = {5}
                        }},
                .inputIndexes = {0, 1, 2, 3},
                .outputIndexes = {4, 5}
            }},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_n_4_all_outputs_as_internal = TestModelManager::get().add("while_sum_of_powers_quant8_n_4_all_outputs_as_internal", get_test_model_n_4_all_outputs_as_internal());

}  // namespace generated_tests::while_sum_of_powers_quant8

