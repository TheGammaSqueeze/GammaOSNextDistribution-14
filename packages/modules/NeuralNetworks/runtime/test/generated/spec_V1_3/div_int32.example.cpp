// Generated from div_int32.mod.py
// DO NOT EDIT
// clang-format off
#include "TestHarness.h"
using namespace test_helper;  // NOLINT(google-build-using-namespace)

namespace generated_tests::div_int32 {

const TestModel& get_test_model() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2, 4, 6},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-6, -6, -6, -6, -6, -6, -5, -5, -5, -5, -5, -5, -4, -4, -4, -4, -4, -4, -3, -3, -3, -3, -3, -3, -2, -2, -2, -2, -2, -2, -1, -1, -1, -1, -1, -1, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 6, 6, 6, 6, 6, 6, 7, 7, 7, 7, 7, 7, 8, 8, 8, 8, 8, 8, 9, 9, 9, 9, 9, 9})
                        }, { // input1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2, 4, 6},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-3, -2, -1, 1, 2, 3, -3, -2, -1, 1, 2, 3, -3, -2, -1, 1, 2, 3, -3, -2, -1, 1, 2, 3, -3, -2, -1, 1, 2, 3, -3, -2, -1, 1, 2, 3, -3, -2, -1, 1, 2, 3, -3, -2, -1, 1, 2, 3, -3, -2, -1, 1, 2, 3, -3, -2, -1, 1, 2, 3, -3, -2, -1, 1, 2, 3, -3, -2, -1, 1, 2, 3, -3, -2, -1, 1, 2, 3, -3, -2, -1, 1, 2, 3, -3, -2, -1, 1, 2, 3, -3, -2, -1, 1, 2, 3})
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
                        }, { // output
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2, 4, 6},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2, 3, 6, -6, -3, -2, 1, 2, 5, -5, -3, -2, 1, 2, 4, -4, -2, -2, 1, 1, 3, -3, -2, -1, 0, 1, 2, -2, -1, -1, 0, 0, 1, -1, -1, -1, 0, 0, 0, 0, 0, 0, -1, -1, -1, 1, 0, 0, -1, -1, -2, 2, 1, 0, -1, -2, -3, 3, 1, 1, -2, -2, -4, 4, 2, 1, -2, -3, -5, 5, 2, 1, -2, -3, -6, 6, 3, 2, -3, -4, -7, 7, 3, 2, -3, -4, -8, 8, 4, 2, -3, -5, -9, 9, 4, 3})
                        }},
                .operations = {{
                            .type = TestOperationType::DIV,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {0, 1},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model = TestModelManager::get().add("div_int32", get_test_model());

}  // namespace generated_tests::div_int32

namespace generated_tests::div_int32 {

const TestModel& get_test_model_by_zero() {
    static TestModel model = {
        .main = { // by_zero
                .operands = {{ // input01
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // input11
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
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
                        }, { // output1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = true,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }},
                .operations = {{
                            .type = TestOperationType::DIV,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {0, 1},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_by_zero = TestModelManager::get().add("div_int32_by_zero", get_test_model_by_zero());

}  // namespace generated_tests::div_int32

