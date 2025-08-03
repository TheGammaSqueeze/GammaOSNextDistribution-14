// Generated from sub_quant8_signed.mod.py
// DO NOT EDIT
// clang-format off
#include "TestHarness.h"
using namespace test_helper;  // NOLINT(google-build-using-namespace)

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -124, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -123, -124, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, 122, 121, 120, 119, 118, 117, -128, -128, -128, -128, -128, -128, 123, 122, 121, 120, 119, 118, -127, -128, -128, -128, -128, -128, 124, 123, 122, 121, 120, 119, -126, -127, -128, -128, -128, -128, 125, 124, 123, 122, 121, 120, -125, -126, -127, -128, -128, -128, 126, 125, 124, 123, 122, 121, -124, -125, -126, -127, -128, -128, 127, 126, 125, 124, 123, 122, -123, -124, -125, -126, -127, -128})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model = TestModelManager::get().add("sub_quant8_signed", get_test_model());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
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
                        }, { // output0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -124, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -123, -124, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, 122, 121, 120, 119, 118, 117, -128, -128, -128, -128, -128, -128, 123, 122, 121, 120, 119, 118, -127, -128, -128, -128, -128, -128, 124, 123, 122, 121, 120, 119, -126, -127, -128, -128, -128, -128, 125, 124, 123, 122, 121, 120, -125, -126, -127, -128, -128, -128, 126, 125, 124, 123, 122, 121, -124, -125, -126, -127, -128, -128, 127, 126, 125, 124, 123, 122, -123, -124, -125, -126, -127, -128})
                        }, { // input0_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
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
                        }, { // input1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal", get_test_model_all_inputs_as_internal());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // input01
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output01
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -124, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -123, -124, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -122, -123, -124, -125, -126, -127, -128, -128, -128, -128, -128, -128, 123, 122, 121, 120, 119, 118, -127, -128, -128, -128, -128, -128, 124, 123, 122, 121, 120, 119, -126, -127, -128, -128, -128, -128, 125, 124, 123, 122, 121, 120, -125, -126, -127, -128, -128, -128, 126, 125, 124, 123, 122, 121, -124, -125, -126, -127, -128, -128, 127, 126, 125, 124, 123, 122, -123, -124, -125, -126, -127, -128, 127, 127, 126, 125, 124, 123, -122, -123, -124, -125, -126, -127})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_2 = TestModelManager::get().add("sub_quant8_signed_2", get_test_model_2());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // input01
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
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
                        }, { // output01
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -124, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -123, -124, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -122, -123, -124, -125, -126, -127, -128, -128, -128, -128, -128, -128, 123, 122, 121, 120, 119, 118, -127, -128, -128, -128, -128, -128, 124, 123, 122, 121, 120, 119, -126, -127, -128, -128, -128, -128, 125, 124, 123, 122, 121, 120, -125, -126, -127, -128, -128, -128, 126, 125, 124, 123, 122, 121, -124, -125, -126, -127, -128, -128, 127, 126, 125, 124, 123, 122, -123, -124, -125, -126, -127, -128, 127, 127, 126, 125, 124, 123, -122, -123, -124, -125, -126, -127})
                        }, { // input01_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
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
                        }, { // input11_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param83
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_2 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_2", get_test_model_all_inputs_as_internal_2());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // input02
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output02
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -108, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, 92, -8, -108, -128, -128, -128, -128, -128, -128, -128, -128, -128, 127, 92, -8, -108, -128, -128, -128, -128, -128, -128, -128, -128, 127, 127, 92, -8, -108, -128, -128, -128, -128, -128, -128, -128, 127, 127, 127, 92, -8, -108, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 92, -8, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -8, -108, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, 92, -8, -108, -128, -128, -128, 127, 127, 127, 127, 127, 127, 127, 92, -8, -108, -128, -128, 127, 127, 127, 127, 127, 127, 127, 127, 92, -8, -108, -128, 127, 127, 127, 127, 127, 127, 127, 127, 127, 92, -8, -108, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 92, -8})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_3 = TestModelManager::get().add("sub_quant8_signed_3", get_test_model_3());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // input02
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // output02
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -108, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, 92, -8, -108, -128, -128, -128, -128, -128, -128, -128, -128, -128, 127, 92, -8, -108, -128, -128, -128, -128, -128, -128, -128, -128, 127, 127, 92, -8, -108, -128, -128, -128, -128, -128, -128, -128, 127, 127, 127, 92, -8, -108, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 92, -8, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -8, -108, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, 92, -8, -108, -128, -128, -128, 127, 127, 127, 127, 127, 127, 127, 92, -8, -108, -128, -128, 127, 127, 127, 127, 127, 127, 127, 127, 92, -8, -108, -128, 127, 127, 127, 127, 127, 127, 127, 127, 127, 92, -8, -108, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 92, -8})
                        }, { // input02_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param84
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input12_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param85
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_3 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_3", get_test_model_all_inputs_as_internal_3());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // input03
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input13
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output03
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -34, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -32, -33, -33, -33, -33, -33, 17, 17, 17, 17, 17, 16, -8, -8, -8, -8, -8, -8, 17, 17, 17, 17, 17, 17, -8, -8, -8, -8, -8, -8, 17, 17, 17, 17, 17, 17, -8, -8, -8, -8, -8, -8, 17, 17, 17, 17, 17, 17, -8, -8, -8, -8, -8, -8, 17, 17, 17, 17, 17, 17, -8, -8, -8, -8, -8, -8, 18, 17, 17, 17, 17, 17, -8, -8, -8, -8, -8, -8})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_4 = TestModelManager::get().add("sub_quant8_signed_4", get_test_model_4());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // input03
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input13
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // output03
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -34, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -32, -33, -33, -33, -33, -33, 17, 17, 17, 17, 17, 16, -8, -8, -8, -8, -8, -8, 17, 17, 17, 17, 17, 17, -8, -8, -8, -8, -8, -8, 17, 17, 17, 17, 17, 17, -8, -8, -8, -8, -8, -8, 17, 17, 17, 17, 17, 17, -8, -8, -8, -8, -8, -8, 17, 17, 17, 17, 17, 17, -8, -8, -8, -8, -8, -8, 18, 17, 17, 17, 17, 17, -8, -8, -8, -8, -8, -8})
                        }, { // input03_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
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
                        }, { // input13_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
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
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_4 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_4", get_test_model_all_inputs_as_internal_4());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_5() {
    static TestModel model = {
        .main = {
                .operands = {{ // input04
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input14
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output04
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -124, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -123, -124, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -122, -123, -124, -125, -126, -127, -128, -128, -128, -128, -128, -128, 123, 122, 121, 120, 119, 118, -127, -128, -128, -128, -128, -128, 124, 123, 122, 121, 120, 119, -126, -127, -128, -128, -128, -128, 125, 124, 123, 122, 121, 120, -125, -126, -127, -128, -128, -128, 126, 125, 124, 123, 122, 121, -124, -125, -126, -127, -128, -128, 127, 126, 125, 124, 123, 122, -123, -124, -125, -126, -127, -128, 127, 127, 126, 125, 124, 123, -122, -123, -124, -125, -126, -127})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_5 = TestModelManager::get().add("sub_quant8_signed_5", get_test_model_5());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_5() {
    static TestModel model = {
        .main = {
                .operands = {{ // input04
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input14
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
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
                        }, { // output04
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -124, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -123, -124, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -122, -123, -124, -125, -126, -127, -128, -128, -128, -128, -128, -128, 123, 122, 121, 120, 119, 118, -127, -128, -128, -128, -128, -128, 124, 123, 122, 121, 120, 119, -126, -127, -128, -128, -128, -128, 125, 124, 123, 122, 121, 120, -125, -126, -127, -128, -128, -128, 126, 125, 124, 123, 122, 121, -124, -125, -126, -127, -128, -128, 127, 126, 125, 124, 123, 122, -123, -124, -125, -126, -127, -128, 127, 127, 126, 125, 124, 123, -122, -123, -124, -125, -126, -127})
                        }, { // input04_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
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
                        }, { // input14_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127})
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
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_5 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_5", get_test_model_all_inputs_as_internal_5());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_6() {
    static TestModel model = {
        .main = {
                .operands = {{ // input05
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input15
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output05
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -124, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -123, -124, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -122, -123, -124, -125, -126, -127, -128, -128, -128, -128, -128, -128, -121, -122, -123, -124, -125, -126, -128, -128, -128, -128, -128, -128, 124, 123, 122, 121, 120, 119, -126, -127, -128, -128, -128, -128, 125, 124, 123, 122, 121, 120, -125, -126, -127, -128, -128, -128, 126, 125, 124, 123, 122, 121, -124, -125, -126, -127, -128, -128, 127, 126, 125, 124, 123, 122, -123, -124, -125, -126, -127, -128, 127, 127, 126, 125, 124, 123, -122, -123, -124, -125, -126, -127, 127, 127, 127, 126, 125, 124, -121, -122, -123, -124, -125, -126})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_6 = TestModelManager::get().add("sub_quant8_signed_6", get_test_model_6());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_6() {
    static TestModel model = {
        .main = {
                .operands = {{ // input05
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input15
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
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
                        }, { // output05
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -124, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -123, -124, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -122, -123, -124, -125, -126, -127, -128, -128, -128, -128, -128, -128, -121, -122, -123, -124, -125, -126, -128, -128, -128, -128, -128, -128, 124, 123, 122, 121, 120, 119, -126, -127, -128, -128, -128, -128, 125, 124, 123, 122, 121, 120, -125, -126, -127, -128, -128, -128, 126, 125, 124, 123, 122, 121, -124, -125, -126, -127, -128, -128, 127, 126, 125, 124, 123, 122, -123, -124, -125, -126, -127, -128, 127, 127, 126, 125, 124, 123, -122, -123, -124, -125, -126, -127, 127, 127, 127, 126, 125, 124, -121, -122, -123, -124, -125, -126})
                        }, { // input05_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder10
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param90
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input15_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127})
                        }, { // param91
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_6 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_6", get_test_model_all_inputs_as_internal_6());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_7() {
    static TestModel model = {
        .main = {
                .operands = {{ // input06
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input16
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output06
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({92, -8, -108, -128, -128, -128, -128, -128, -128, -128, -128, -128, 127, 92, -8, -108, -128, -128, -128, -128, -128, -128, -128, -128, 127, 127, 92, -8, -108, -128, -128, -128, -128, -128, -128, -128, 127, 127, 127, 92, -8, -108, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 92, -8, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 92, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, 92, -8, -108, -128, -128, -128, 127, 127, 127, 127, 127, 127, 127, 92, -8, -108, -128, -128, 127, 127, 127, 127, 127, 127, 127, 127, 92, -8, -108, -128, 127, 127, 127, 127, 127, 127, 127, 127, 127, 92, -8, -108, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 92, -8, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 92})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_7 = TestModelManager::get().add("sub_quant8_signed_7", get_test_model_7());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_7() {
    static TestModel model = {
        .main = {
                .operands = {{ // input06
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input16
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // output06
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({92, -8, -108, -128, -128, -128, -128, -128, -128, -128, -128, -128, 127, 92, -8, -108, -128, -128, -128, -128, -128, -128, -128, -128, 127, 127, 92, -8, -108, -128, -128, -128, -128, -128, -128, -128, 127, 127, 127, 92, -8, -108, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 92, -8, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 92, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, 92, -8, -108, -128, -128, -128, 127, 127, 127, 127, 127, 127, 127, 92, -8, -108, -128, -128, 127, 127, 127, 127, 127, 127, 127, 127, 92, -8, -108, -128, 127, 127, 127, 127, 127, 127, 127, 127, 127, 92, -8, -108, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 92, -8, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 92})
                        }, { // input06_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param92
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input16_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder13
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_7 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_7", get_test_model_all_inputs_as_internal_7());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_8() {
    static TestModel model = {
        .main = {
                .operands = {{ // input07
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input17
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output07
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -32, -33, -33, -33, -33, -33, -7, -8, -8, -8, -8, -8, -32, -32, -33, -33, -33, -33, 17, 17, 17, 17, 17, 17, -8, -8, -8, -8, -8, -8, 17, 17, 17, 17, 17, 17, -8, -8, -8, -8, -8, -8, 17, 17, 17, 17, 17, 17, -8, -8, -8, -8, -8, -8, 17, 17, 17, 17, 17, 17, -8, -8, -8, -8, -8, -8, 18, 17, 17, 17, 17, 17, -8, -8, -8, -8, -8, -8, 18, 18, 17, 17, 17, 17, -7, -8, -8, -8, -8, -8})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_8 = TestModelManager::get().add("sub_quant8_signed_8", get_test_model_8());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_8() {
    static TestModel model = {
        .main = {
                .operands = {{ // input07
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input17
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // output07
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -32, -33, -33, -33, -33, -33, -7, -8, -8, -8, -8, -8, -32, -32, -33, -33, -33, -33, 17, 17, 17, 17, 17, 17, -8, -8, -8, -8, -8, -8, 17, 17, 17, 17, 17, 17, -8, -8, -8, -8, -8, -8, 17, 17, 17, 17, 17, 17, -8, -8, -8, -8, -8, -8, 17, 17, 17, 17, 17, 17, -8, -8, -8, -8, -8, -8, 18, 17, 17, 17, 17, 17, -8, -8, -8, -8, -8, -8, 18, 18, 17, 17, 17, 17, -7, -8, -8, -8, -8, -8})
                        }, { // input07_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder14
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
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
                        }, { // input17_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder15
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_8 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_8", get_test_model_all_inputs_as_internal_8());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_9() {
    static TestModel model = {
        .main = {
                .operands = {{ // input08
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input18
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output08
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127, -127, -127, -127, -127, -127, -128, -128, -128, -128, -128, -128, -126, -126, -126, -126, -126, -126, -128, -128, -128, -128, -128, -128, -125, -125, -125, -125, -125, -125, -127, -127, -127, -127, -127, -127, -124, -124, -124, -124, -124, -124, -126, -126, -126, -126, -126, -126, -123, -123, -123, -123, -123, -123, -125, -125, -125, -125, -125, -125, -122, -122, -122, -122, -122, -122, -124, -124, -124, -124, -124, -124, 123, 123, 123, 123, 123, 123, 121, 121, 121, 121, 121, 121, 124, 124, 124, 124, 124, 124, 122, 122, 122, 122, 122, 122, 125, 125, 125, 125, 125, 125, 123, 123, 123, 123, 123, 123, 126, 126, 126, 126, 126, 126, 124, 124, 124, 124, 124, 124, 127, 127, 127, 127, 127, 127, 125, 125, 125, 125, 125, 125, 127, 127, 127, 127, 127, 127, 126, 126, 126, 126, 126, 126})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_9 = TestModelManager::get().add("sub_quant8_signed_9", get_test_model_9());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_9() {
    static TestModel model = {
        .main = {
                .operands = {{ // input08
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input18
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // output08
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127, -127, -127, -127, -127, -127, -128, -128, -128, -128, -128, -128, -126, -126, -126, -126, -126, -126, -128, -128, -128, -128, -128, -128, -125, -125, -125, -125, -125, -125, -127, -127, -127, -127, -127, -127, -124, -124, -124, -124, -124, -124, -126, -126, -126, -126, -126, -126, -123, -123, -123, -123, -123, -123, -125, -125, -125, -125, -125, -125, -122, -122, -122, -122, -122, -122, -124, -124, -124, -124, -124, -124, 123, 123, 123, 123, 123, 123, 121, 121, 121, 121, 121, 121, 124, 124, 124, 124, 124, 124, 122, 122, 122, 122, 122, 122, 125, 125, 125, 125, 125, 125, 123, 123, 123, 123, 123, 123, 126, 126, 126, 126, 126, 126, 124, 124, 124, 124, 124, 124, 127, 127, 127, 127, 127, 127, 125, 125, 125, 125, 125, 125, 127, 127, 127, 127, 127, 127, 126, 126, 126, 126, 126, 126})
                        }, { // input08_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder16
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
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
                        }, { // input18_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder17
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param97
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_9 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_9", get_test_model_all_inputs_as_internal_9());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_10() {
    static TestModel model = {
        .main = {
                .operands = {{ // input09
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input19
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output09
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-126, -126, -126, -126, -126, -126, -128, -128, -128, -128, -128, -128, -125, -125, -125, -125, -125, -125, -127, -127, -127, -127, -127, -127, -124, -124, -124, -124, -124, -124, -126, -126, -126, -126, -126, -126, -123, -123, -123, -123, -123, -123, -125, -125, -125, -125, -125, -125, -122, -122, -122, -122, -122, -122, -124, -124, -124, -124, -124, -124, -121, -121, -121, -121, -121, -121, -123, -123, -123, -123, -123, -123, 124, 124, 124, 124, 124, 124, 122, 122, 122, 122, 122, 122, 125, 125, 125, 125, 125, 125, 123, 123, 123, 123, 123, 123, 126, 126, 126, 126, 126, 126, 124, 124, 124, 124, 124, 124, 127, 127, 127, 127, 127, 127, 125, 125, 125, 125, 125, 125, 127, 127, 127, 127, 127, 127, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_10 = TestModelManager::get().add("sub_quant8_signed_10", get_test_model_10());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_10() {
    static TestModel model = {
        .main = {
                .operands = {{ // input09
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input19
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // output09
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-126, -126, -126, -126, -126, -126, -128, -128, -128, -128, -128, -128, -125, -125, -125, -125, -125, -125, -127, -127, -127, -127, -127, -127, -124, -124, -124, -124, -124, -124, -126, -126, -126, -126, -126, -126, -123, -123, -123, -123, -123, -123, -125, -125, -125, -125, -125, -125, -122, -122, -122, -122, -122, -122, -124, -124, -124, -124, -124, -124, -121, -121, -121, -121, -121, -121, -123, -123, -123, -123, -123, -123, 124, 124, 124, 124, 124, 124, 122, 122, 122, 122, 122, 122, 125, 125, 125, 125, 125, 125, 123, 123, 123, 123, 123, 123, 126, 126, 126, 126, 126, 126, 124, 124, 124, 124, 124, 124, 127, 127, 127, 127, 127, 127, 125, 125, 125, 125, 125, 125, 127, 127, 127, 127, 127, 127, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input09_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder18
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param98
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input19_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder19
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param99
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_10 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_10", get_test_model_all_inputs_as_internal_10());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_11() {
    static TestModel model = {
        .main = {
                .operands = {{ // input010
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input110
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output010
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -38, -39, -40, -41, -42, -43, 127, 127, 127, 127, 127, 127, 62, 61, 60, 59, 58, 57, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_11 = TestModelManager::get().add("sub_quant8_signed_11", get_test_model_11());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_11() {
    static TestModel model = {
        .main = {
                .operands = {{ // input010
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input110
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // output010
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -38, -39, -40, -41, -42, -43, 127, 127, 127, 127, 127, 127, 62, 61, 60, 59, 58, 57, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input010_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder20
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param100
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input110_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder21
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param101
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_11 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_11", get_test_model_all_inputs_as_internal_11());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_12() {
    static TestModel model = {
        .main = {
                .operands = {{ // input011
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input111
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output011
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -7, -7, -7, -7, -7, -7, -8, -8, -8, -8, -8, -8, -7, -7, -7, -7, -7, -7, -8, -8, -8, -8, -8, -8, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 18, 18, 18, 18, 18, 18, 17, 17, 17, 17, 17, 17, 18, 18, 18, 18, 18, 18, 17, 17, 17, 17, 17, 17})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_12 = TestModelManager::get().add("sub_quant8_signed_12", get_test_model_12());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_12() {
    static TestModel model = {
        .main = {
                .operands = {{ // input011
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input111
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // output011
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -7, -7, -7, -7, -7, -7, -8, -8, -8, -8, -8, -8, -7, -7, -7, -7, -7, -7, -8, -8, -8, -8, -8, -8, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 18, 18, 18, 18, 18, 18, 17, 17, 17, 17, 17, 17, 18, 18, 18, 18, 18, 18, 17, 17, 17, 17, 17, 17})
                        }, { // input011_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder22
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param102
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input111_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder23
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param103
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_12 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_12", get_test_model_all_inputs_as_internal_12());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_13() {
    static TestModel model = {
        .main = {
                .operands = {{ // input012
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input112
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output012
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_13 = TestModelManager::get().add("sub_quant8_signed_13", get_test_model_13());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_13() {
    static TestModel model = {
        .main = {
                .operands = {{ // input012
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input112
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // output012
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128})
                        }, { // input012_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder24
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param104
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input112_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder25
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param105
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_13 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_13", get_test_model_all_inputs_as_internal_13());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_14() {
    static TestModel model = {
        .main = {
                .operands = {{ // input013
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input113
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output013
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_14 = TestModelManager::get().add("sub_quant8_signed_14", get_test_model_14());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_14() {
    static TestModel model = {
        .main = {
                .operands = {{ // input013
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input113
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // output013
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128})
                        }, { // input013_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder26
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param106
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input113_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder27
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param107
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_14 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_14", get_test_model_all_inputs_as_internal_14());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_15() {
    static TestModel model = {
        .main = {
                .operands = {{ // input014
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input114
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output014
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_15 = TestModelManager::get().add("sub_quant8_signed_15", get_test_model_15());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_15() {
    static TestModel model = {
        .main = {
                .operands = {{ // input014
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input114
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // output014
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128})
                        }, { // input014_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder28
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param108
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input114_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder29
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param109
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_15 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_15", get_test_model_all_inputs_as_internal_15());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_16() {
    static TestModel model = {
        .main = {
                .operands = {{ // input015
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input115
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output015
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 112, 110, 110, 108, 108, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -113, -114, -115, -116, -117, -118, 127, 127, 127, 127, 127, 127, -113, -114, -115, -116, -117, -118, 127, 127, 127, 127, 127, 127, -113, -114, -115, -116, -117, -118, 127, 127, 127, 127, 127, 127, -113, -114, -115, -116, -117, -118, 127, 127, 127, 127, 127, 127, -113, -114, -115, -116, -117, -118, 127, 127, 127, 127, 127, 127, -112, -114, -114, -116, -116, -118})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_16 = TestModelManager::get().add("sub_quant8_signed_16", get_test_model_16());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_16() {
    static TestModel model = {
        .main = {
                .operands = {{ // input015
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input115
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // output015
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 112, 110, 110, 108, 108, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -113, -114, -115, -116, -117, -118, 127, 127, 127, 127, 127, 127, -113, -114, -115, -116, -117, -118, 127, 127, 127, 127, 127, 127, -113, -114, -115, -116, -117, -118, 127, 127, 127, 127, 127, 127, -113, -114, -115, -116, -117, -118, 127, 127, 127, 127, 127, 127, -113, -114, -115, -116, -117, -118, 127, 127, 127, 127, 127, 127, -112, -114, -114, -116, -116, -118})
                        }, { // input015_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder30
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param110
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input115_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder31
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param111
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_16 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_16", get_test_model_all_inputs_as_internal_16());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_17() {
    static TestModel model = {
        .main = {
                .operands = {{ // input016
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input116
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output016
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -124, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, 121, 120, 119, 118, 117, 116, -128, -128, -128, -128, -128, -128, 122, 121, 120, 119, 118, 117, -128, -128, -128, -128, -128, -128, 123, 122, 121, 120, 119, 118, -127, -128, -128, -128, -128, -128, 124, 123, 122, 121, 120, 119, -126, -127, -128, -128, -128, -128, 125, 124, 123, 122, 121, 120, -125, -126, -127, -128, -128, -128, 126, 125, 124, 123, 122, 121, -124, -125, -126, -127, -128, -128})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_17 = TestModelManager::get().add("sub_quant8_signed_17", get_test_model_17());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_17() {
    static TestModel model = {
        .main = {
                .operands = {{ // input016
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input116
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // output016
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -124, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, 121, 120, 119, 118, 117, 116, -128, -128, -128, -128, -128, -128, 122, 121, 120, 119, 118, 117, -128, -128, -128, -128, -128, -128, 123, 122, 121, 120, 119, 118, -127, -128, -128, -128, -128, -128, 124, 123, 122, 121, 120, 119, -126, -127, -128, -128, -128, -128, 125, 124, 123, 122, 121, 120, -125, -126, -127, -128, -128, -128, 126, 125, 124, 123, 122, 121, -124, -125, -126, -127, -128, -128})
                        }, { // input016_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder32
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127})
                        }, { // param112
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input116_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder33
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param113
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_17 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_17", get_test_model_all_inputs_as_internal_17());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_18() {
    static TestModel model = {
        .main = {
                .operands = {{ // input017
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input117
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output017
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -124, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -123, -124, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, 122, 121, 120, 119, 118, 117, -128, -128, -128, -128, -128, -128, 123, 122, 121, 120, 119, 118, -127, -128, -128, -128, -128, -128, 124, 123, 122, 121, 120, 119, -126, -127, -128, -128, -128, -128, 125, 124, 123, 122, 121, 120, -125, -126, -127, -128, -128, -128, 126, 125, 124, 123, 122, 121, -124, -125, -126, -127, -128, -128, 127, 126, 125, 124, 123, 122, -123, -124, -125, -126, -127, -128})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_18 = TestModelManager::get().add("sub_quant8_signed_18", get_test_model_18());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_18() {
    static TestModel model = {
        .main = {
                .operands = {{ // input017
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input117
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // output017
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -124, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -123, -124, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, 122, 121, 120, 119, 118, 117, -128, -128, -128, -128, -128, -128, 123, 122, 121, 120, 119, 118, -127, -128, -128, -128, -128, -128, 124, 123, 122, 121, 120, 119, -126, -127, -128, -128, -128, -128, 125, 124, 123, 122, 121, 120, -125, -126, -127, -128, -128, -128, 126, 125, 124, 123, 122, 121, -124, -125, -126, -127, -128, -128, 127, 126, 125, 124, 123, 122, -123, -124, -125, -126, -127, -128})
                        }, { // input017_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder34
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127})
                        }, { // param114
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input117_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder35
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param115
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_18 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_18", get_test_model_all_inputs_as_internal_18());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_19() {
    static TestModel model = {
        .main = {
                .operands = {{ // input018
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input118
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output018
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-108, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -8, -108, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, 92, -8, -108, -128, -128, -128, -128, -128, -128, -128, -128, -128, 127, 92, -8, -108, -128, -128, -128, -128, -128, -128, -128, -128, 127, 127, 92, -8, -108, -128, -128, -128, -128, -128, -128, -128, 127, 127, 127, 92, -8, -108, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -108, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -8, -108, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, 92, -8, -108, -128, -128, -128, 127, 127, 127, 127, 127, 127, 127, 92, -8, -108, -128, -128, 127, 127, 127, 127, 127, 127, 127, 127, 92, -8, -108, -128, 127, 127, 127, 127, 127, 127, 127, 127, 127, 92, -8, -108})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_19 = TestModelManager::get().add("sub_quant8_signed_19", get_test_model_19());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_19() {
    static TestModel model = {
        .main = {
                .operands = {{ // input018
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input118
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // output018
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-108, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -8, -108, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, 92, -8, -108, -128, -128, -128, -128, -128, -128, -128, -128, -128, 127, 92, -8, -108, -128, -128, -128, -128, -128, -128, -128, -128, 127, 127, 92, -8, -108, -128, -128, -128, -128, -128, -128, -128, 127, 127, 127, 92, -8, -108, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -108, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -8, -108, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, 92, -8, -108, -128, -128, -128, 127, 127, 127, 127, 127, 127, 127, 92, -8, -108, -128, -128, 127, 127, 127, 127, 127, 127, 127, 127, 92, -8, -108, -128, 127, 127, 127, 127, 127, 127, 127, 127, 127, 92, -8, -108})
                        }, { // input018_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder36
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127})
                        }, { // param116
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input118_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder37
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param117
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_19 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_19", get_test_model_all_inputs_as_internal_19());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_20() {
    static TestModel model = {
        .main = {
                .operands = {{ // input019
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input119
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output019
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -8, -8, -8, -8, -9, -33, -33, -33, -33, -34, -34, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -34, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, 17, 17, 17, 17, 16, 16, -8, -8, -8, -8, -8, -9, 17, 17, 17, 17, 17, 16, -8, -8, -8, -8, -8, -8, 17, 17, 17, 17, 17, 17, -8, -8, -8, -8, -8, -8, 17, 17, 17, 17, 17, 17, -8, -8, -8, -8, -8, -8, 17, 17, 17, 17, 17, 17, -8, -8, -8, -8, -8, -8, 17, 17, 17, 17, 17, 17, -8, -8, -8, -8, -8, -8})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_20 = TestModelManager::get().add("sub_quant8_signed_20", get_test_model_20());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_20() {
    static TestModel model = {
        .main = {
                .operands = {{ // input019
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input119
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // output019
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -8, -8, -8, -8, -9, -33, -33, -33, -33, -34, -34, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -34, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, 17, 17, 17, 17, 16, 16, -8, -8, -8, -8, -8, -9, 17, 17, 17, 17, 17, 16, -8, -8, -8, -8, -8, -8, 17, 17, 17, 17, 17, 17, -8, -8, -8, -8, -8, -8, 17, 17, 17, 17, 17, 17, -8, -8, -8, -8, -8, -8, 17, 17, 17, 17, 17, 17, -8, -8, -8, -8, -8, -8, 17, 17, 17, 17, 17, 17, -8, -8, -8, -8, -8, -8})
                        }, { // input019_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder38
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127})
                        }, { // param118
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input119_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder39
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param119
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_20 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_20", get_test_model_all_inputs_as_internal_20());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_21() {
    static TestModel model = {
        .main = {
                .operands = {{ // input020
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input120
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output020
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -124, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -123, -124, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, 122, 121, 120, 119, 118, 117, -128, -128, -128, -128, -128, -128, 123, 122, 121, 120, 119, 118, -127, -128, -128, -128, -128, -128, 124, 123, 122, 121, 120, 119, -126, -127, -128, -128, -128, -128, 125, 124, 123, 122, 121, 120, -125, -126, -127, -128, -128, -128, 126, 125, 124, 123, 122, 121, -124, -125, -126, -127, -128, -128, 127, 126, 125, 124, 123, 122, -123, -124, -125, -126, -127, -128})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_21 = TestModelManager::get().add("sub_quant8_signed_21", get_test_model_21());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_21() {
    static TestModel model = {
        .main = {
                .operands = {{ // input020
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input120
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // output020
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -124, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -123, -124, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, 122, 121, 120, 119, 118, 117, -128, -128, -128, -128, -128, -128, 123, 122, 121, 120, 119, 118, -127, -128, -128, -128, -128, -128, 124, 123, 122, 121, 120, 119, -126, -127, -128, -128, -128, -128, 125, 124, 123, 122, 121, 120, -125, -126, -127, -128, -128, -128, 126, 125, 124, 123, 122, 121, -124, -125, -126, -127, -128, -128, 127, 126, 125, 124, 123, 122, -123, -124, -125, -126, -127, -128})
                        }, { // input020_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder40
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127})
                        }, { // param120
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input120_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder41
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127})
                        }, { // param121
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_21 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_21", get_test_model_all_inputs_as_internal_21());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_22() {
    static TestModel model = {
        .main = {
                .operands = {{ // input021
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input121
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output021
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -124, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -123, -124, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -122, -123, -124, -125, -126, -127, -128, -128, -128, -128, -128, -128, 123, 122, 121, 120, 119, 118, -127, -128, -128, -128, -128, -128, 124, 123, 122, 121, 120, 119, -126, -127, -128, -128, -128, -128, 125, 124, 123, 122, 121, 120, -125, -126, -127, -128, -128, -128, 126, 125, 124, 123, 122, 121, -124, -125, -126, -127, -128, -128, 127, 126, 125, 124, 123, 122, -123, -124, -125, -126, -127, -128, 127, 127, 126, 125, 124, 123, -122, -123, -124, -125, -126, -127})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_22 = TestModelManager::get().add("sub_quant8_signed_22", get_test_model_22());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_22() {
    static TestModel model = {
        .main = {
                .operands = {{ // input021
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input121
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // output021
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -124, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -123, -124, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -122, -123, -124, -125, -126, -127, -128, -128, -128, -128, -128, -128, 123, 122, 121, 120, 119, 118, -127, -128, -128, -128, -128, -128, 124, 123, 122, 121, 120, 119, -126, -127, -128, -128, -128, -128, 125, 124, 123, 122, 121, 120, -125, -126, -127, -128, -128, -128, 126, 125, 124, 123, 122, 121, -124, -125, -126, -127, -128, -128, 127, 126, 125, 124, 123, 122, -123, -124, -125, -126, -127, -128, 127, 127, 126, 125, 124, 123, -122, -123, -124, -125, -126, -127})
                        }, { // input021_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder42
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127})
                        }, { // param122
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input121_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder43
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127})
                        }, { // param123
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_22 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_22", get_test_model_all_inputs_as_internal_22());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_23() {
    static TestModel model = {
        .main = {
                .operands = {{ // input022
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input122
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output022
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -108, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, 92, -8, -108, -128, -128, -128, -128, -128, -128, -128, -128, -128, 127, 92, -8, -108, -128, -128, -128, -128, -128, -128, -128, -128, 127, 127, 92, -8, -108, -128, -128, -128, -128, -128, -128, -128, 127, 127, 127, 92, -8, -108, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 92, -8, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -8, -108, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, 92, -8, -108, -128, -128, -128, 127, 127, 127, 127, 127, 127, 127, 92, -8, -108, -128, -128, 127, 127, 127, 127, 127, 127, 127, 127, 92, -8, -108, -128, 127, 127, 127, 127, 127, 127, 127, 127, 127, 92, -8, -108, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 92, -8})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_23 = TestModelManager::get().add("sub_quant8_signed_23", get_test_model_23());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_23() {
    static TestModel model = {
        .main = {
                .operands = {{ // input022
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input122
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // output022
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -108, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, 92, -8, -108, -128, -128, -128, -128, -128, -128, -128, -128, -128, 127, 92, -8, -108, -128, -128, -128, -128, -128, -128, -128, -128, 127, 127, 92, -8, -108, -128, -128, -128, -128, -128, -128, -128, 127, 127, 127, 92, -8, -108, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 92, -8, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -8, -108, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, 92, -8, -108, -128, -128, -128, 127, 127, 127, 127, 127, 127, 127, 92, -8, -108, -128, -128, 127, 127, 127, 127, 127, 127, 127, 127, 92, -8, -108, -128, 127, 127, 127, 127, 127, 127, 127, 127, 127, 92, -8, -108, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 92, -8})
                        }, { // input022_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder44
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127})
                        }, { // param124
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input122_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder45
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127})
                        }, { // param125
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_23 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_23", get_test_model_all_inputs_as_internal_23());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_24() {
    static TestModel model = {
        .main = {
                .operands = {{ // input023
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input123
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output023
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -34, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -32, -33, -33, -33, -33, -33, 17, 17, 17, 17, 17, 16, -8, -8, -8, -8, -8, -8, 17, 17, 17, 17, 17, 17, -8, -8, -8, -8, -8, -8, 17, 17, 17, 17, 17, 17, -8, -8, -8, -8, -8, -8, 17, 17, 17, 17, 17, 17, -8, -8, -8, -8, -8, -8, 17, 17, 17, 17, 17, 17, -8, -8, -8, -8, -8, -8, 18, 17, 17, 17, 17, 17, -8, -8, -8, -8, -8, -8})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_24 = TestModelManager::get().add("sub_quant8_signed_24", get_test_model_24());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_24() {
    static TestModel model = {
        .main = {
                .operands = {{ // input023
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input123
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // output023
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -34, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -32, -33, -33, -33, -33, -33, 17, 17, 17, 17, 17, 16, -8, -8, -8, -8, -8, -8, 17, 17, 17, 17, 17, 17, -8, -8, -8, -8, -8, -8, 17, 17, 17, 17, 17, 17, -8, -8, -8, -8, -8, -8, 17, 17, 17, 17, 17, 17, -8, -8, -8, -8, -8, -8, 17, 17, 17, 17, 17, 17, -8, -8, -8, -8, -8, -8, 18, 17, 17, 17, 17, 17, -8, -8, -8, -8, -8, -8})
                        }, { // input023_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder46
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127})
                        }, { // param126
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input123_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder47
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127})
                        }, { // param127
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_24 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_24", get_test_model_all_inputs_as_internal_24());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_25() {
    static TestModel model = {
        .main = {
                .operands = {{ // input024
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input124
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output024
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -128, -128, -128, -128, -128, -128, -126, -126, -126, -126, -126, -126, -128, -128, -128, -128, -128, -128, -125, -125, -125, -125, -125, -125, -127, -127, -127, -127, -127, -127, -124, -124, -124, -124, -124, -124, -126, -126, -126, -126, -126, -126, -123, -123, -123, -123, -123, -123, -125, -125, -125, -125, -125, -125, 122, 122, 122, 122, 122, 122, 120, 120, 120, 120, 120, 120, 123, 123, 123, 123, 123, 123, 121, 121, 121, 121, 121, 121, 124, 124, 124, 124, 124, 124, 122, 122, 122, 122, 122, 122, 125, 125, 125, 125, 125, 125, 123, 123, 123, 123, 123, 123, 126, 126, 126, 126, 126, 126, 124, 124, 124, 124, 124, 124, 127, 127, 127, 127, 127, 127, 125, 125, 125, 125, 125, 125})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_25 = TestModelManager::get().add("sub_quant8_signed_25", get_test_model_25());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_25() {
    static TestModel model = {
        .main = {
                .operands = {{ // input024
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input124
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // output024
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -128, -128, -128, -128, -128, -128, -126, -126, -126, -126, -126, -126, -128, -128, -128, -128, -128, -128, -125, -125, -125, -125, -125, -125, -127, -127, -127, -127, -127, -127, -124, -124, -124, -124, -124, -124, -126, -126, -126, -126, -126, -126, -123, -123, -123, -123, -123, -123, -125, -125, -125, -125, -125, -125, 122, 122, 122, 122, 122, 122, 120, 120, 120, 120, 120, 120, 123, 123, 123, 123, 123, 123, 121, 121, 121, 121, 121, 121, 124, 124, 124, 124, 124, 124, 122, 122, 122, 122, 122, 122, 125, 125, 125, 125, 125, 125, 123, 123, 123, 123, 123, 123, 126, 126, 126, 126, 126, 126, 124, 124, 124, 124, 124, 124, 127, 127, 127, 127, 127, 127, 125, 125, 125, 125, 125, 125})
                        }, { // input024_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder48
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127})
                        }, { // param128
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input124_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder49
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param129
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_25 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_25", get_test_model_all_inputs_as_internal_25());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_26() {
    static TestModel model = {
        .main = {
                .operands = {{ // input025
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input125
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output025
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127, -127, -127, -127, -127, -127, -128, -128, -128, -128, -128, -128, -126, -126, -126, -126, -126, -126, -128, -128, -128, -128, -128, -128, -125, -125, -125, -125, -125, -125, -127, -127, -127, -127, -127, -127, -124, -124, -124, -124, -124, -124, -126, -126, -126, -126, -126, -126, -123, -123, -123, -123, -123, -123, -125, -125, -125, -125, -125, -125, -122, -122, -122, -122, -122, -122, -124, -124, -124, -124, -124, -124, 123, 123, 123, 123, 123, 123, 121, 121, 121, 121, 121, 121, 124, 124, 124, 124, 124, 124, 122, 122, 122, 122, 122, 122, 125, 125, 125, 125, 125, 125, 123, 123, 123, 123, 123, 123, 126, 126, 126, 126, 126, 126, 124, 124, 124, 124, 124, 124, 127, 127, 127, 127, 127, 127, 125, 125, 125, 125, 125, 125, 127, 127, 127, 127, 127, 127, 126, 126, 126, 126, 126, 126})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_26 = TestModelManager::get().add("sub_quant8_signed_26", get_test_model_26());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_26() {
    static TestModel model = {
        .main = {
                .operands = {{ // input025
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input125
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // output025
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127, -127, -127, -127, -127, -127, -128, -128, -128, -128, -128, -128, -126, -126, -126, -126, -126, -126, -128, -128, -128, -128, -128, -128, -125, -125, -125, -125, -125, -125, -127, -127, -127, -127, -127, -127, -124, -124, -124, -124, -124, -124, -126, -126, -126, -126, -126, -126, -123, -123, -123, -123, -123, -123, -125, -125, -125, -125, -125, -125, -122, -122, -122, -122, -122, -122, -124, -124, -124, -124, -124, -124, 123, 123, 123, 123, 123, 123, 121, 121, 121, 121, 121, 121, 124, 124, 124, 124, 124, 124, 122, 122, 122, 122, 122, 122, 125, 125, 125, 125, 125, 125, 123, 123, 123, 123, 123, 123, 126, 126, 126, 126, 126, 126, 124, 124, 124, 124, 124, 124, 127, 127, 127, 127, 127, 127, 125, 125, 125, 125, 125, 125, 127, 127, 127, 127, 127, 127, 126, 126, 126, 126, 126, 126})
                        }, { // input025_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder50
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127})
                        }, { // param130
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input125_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder51
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param131
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_26 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_26", get_test_model_all_inputs_as_internal_26());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_27() {
    static TestModel model = {
        .main = {
                .operands = {{ // input026
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input126
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output026
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({12, 11, 10, 9, 8, 7, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -38, -39, -40, -41, -42, -43, 127, 127, 127, 127, 127, 127, 62, 61, 60, 59, 58, 57, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_27 = TestModelManager::get().add("sub_quant8_signed_27", get_test_model_27());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_27() {
    static TestModel model = {
        .main = {
                .operands = {{ // input026
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input126
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // output026
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({12, 11, 10, 9, 8, 7, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -38, -39, -40, -41, -42, -43, 127, 127, 127, 127, 127, 127, 62, 61, 60, 59, 58, 57, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input026_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder52
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127})
                        }, { // param132
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input126_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder53
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param133
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_27 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_27", get_test_model_all_inputs_as_internal_27());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_28() {
    static TestModel model = {
        .main = {
                .operands = {{ // input027
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input127
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // param27
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output027
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -7, -7, -7, -7, -7, -7, -8, -8, -8, -8, -8, -8, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 18, 18, 18, 18, 18, 18, 17, 17, 17, 17, 17, 17})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_28 = TestModelManager::get().add("sub_quant8_signed_28", get_test_model_28());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_28() {
    static TestModel model = {
        .main = {
                .operands = {{ // input027
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input127
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param27
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output027
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -7, -7, -7, -7, -7, -7, -8, -8, -8, -8, -8, -8, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 18, 18, 18, 18, 18, 18, 17, 17, 17, 17, 17, 17})
                        }, { // input027_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder54
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127})
                        }, { // param134
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input127_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder55
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param135
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_28 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_28", get_test_model_all_inputs_as_internal_28());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_29() {
    static TestModel model = {
        .main = {
                .operands = {{ // input028
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input128
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output028
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_29 = TestModelManager::get().add("sub_quant8_signed_29", get_test_model_29());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_29() {
    static TestModel model = {
        .main = {
                .operands = {{ // input028
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input128
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // output028
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128})
                        }, { // input028_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder56
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127})
                        }, { // param136
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input128_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder57
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param137
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_29 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_29", get_test_model_all_inputs_as_internal_29());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_30() {
    static TestModel model = {
        .main = {
                .operands = {{ // input029
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input129
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output029
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_30 = TestModelManager::get().add("sub_quant8_signed_30", get_test_model_30());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_30() {
    static TestModel model = {
        .main = {
                .operands = {{ // input029
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input129
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // output029
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128})
                        }, { // input029_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder58
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127})
                        }, { // param138
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input129_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder59
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param139
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_30 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_30", get_test_model_all_inputs_as_internal_30());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_31() {
    static TestModel model = {
        .main = {
                .operands = {{ // input030
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input130
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // param30
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output030
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_31 = TestModelManager::get().add("sub_quant8_signed_31", get_test_model_31());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_31() {
    static TestModel model = {
        .main = {
                .operands = {{ // input030
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input130
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param30
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output030
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128})
                        }, { // input030_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder60
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127})
                        }, { // param140
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input130_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder61
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param141
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_31 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_31", get_test_model_all_inputs_as_internal_31());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_32() {
    static TestModel model = {
        .main = {
                .operands = {{ // input031
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input131
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // param31
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output031
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -113, -114, -115, -116, -117, -118, 127, 127, 127, 127, 127, 127, -113, -114, -115, -116, -117, -118, 127, 127, 127, 127, 127, 127, -113, -114, -115, -116, -117, -118, 127, 127, 127, 127, 127, 127, -113, -114, -115, -116, -117, -118, 127, 127, 127, 127, 127, 127, -113, -114, -115, -116, -117, -118, 127, 127, 127, 127, 127, 127, -113, -114, -115, -116, -117, -118})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_32 = TestModelManager::get().add("sub_quant8_signed_32", get_test_model_32());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_32() {
    static TestModel model = {
        .main = {
                .operands = {{ // input031
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input131
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param31
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output031
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -113, -114, -115, -116, -117, -118, 127, 127, 127, 127, 127, 127, -113, -114, -115, -116, -117, -118, 127, 127, 127, 127, 127, 127, -113, -114, -115, -116, -117, -118, 127, 127, 127, 127, 127, 127, -113, -114, -115, -116, -117, -118, 127, 127, 127, 127, 127, 127, -113, -114, -115, -116, -117, -118, 127, 127, 127, 127, 127, 127, -113, -114, -115, -116, -117, -118})
                        }, { // input031_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder62
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127})
                        }, { // param142
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input131_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder63
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param143
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_32 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_32", get_test_model_all_inputs_as_internal_32());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_33() {
    static TestModel model = {
        .main = {
                .operands = {{ // input032
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input132
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // param32
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output032
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_33 = TestModelManager::get().add("sub_quant8_signed_33", get_test_model_33());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_33() {
    static TestModel model = {
        .main = {
                .operands = {{ // input032
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input132
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param32
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output032
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128})
                        }, { // input032_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder64
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param144
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input132_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder65
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param145
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_33 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_33", get_test_model_all_inputs_as_internal_33());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_34() {
    static TestModel model = {
        .main = {
                .operands = {{ // input033
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input133
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // param33
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output033
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_34 = TestModelManager::get().add("sub_quant8_signed_34", get_test_model_34());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_34() {
    static TestModel model = {
        .main = {
                .operands = {{ // input033
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input133
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param33
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output033
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128})
                        }, { // input033_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder66
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param146
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input133_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder67
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param147
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_34 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_34", get_test_model_all_inputs_as_internal_34());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_35() {
    static TestModel model = {
        .main = {
                .operands = {{ // input034
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input134
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output034
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -125, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -124, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -123, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, 122, 22, -78, -128, -128, -128, -128, -128, -128, -128, -128, -128, 123, 23, -77, -128, -128, -128, -128, -128, -128, -128, -128, -128, 124, 24, -76, -128, -128, -128, -128, -128, -128, -128, -128, -128, 125, 25, -75, -128, -128, -128, -128, -128, -128, -128, -128, -128, 126, 26, -74, -128, -128, -128, -128, -128, -128, -128, -128, -128, 127, 27, -73, -128, -128, -128, -128, -128, -128, -128, -128, -128})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_35 = TestModelManager::get().add("sub_quant8_signed_35", get_test_model_35());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_35() {
    static TestModel model = {
        .main = {
                .operands = {{ // input034
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input134
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // output034
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -125, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -124, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -123, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, 122, 22, -78, -128, -128, -128, -128, -128, -128, -128, -128, -128, 123, 23, -77, -128, -128, -128, -128, -128, -128, -128, -128, -128, 124, 24, -76, -128, -128, -128, -128, -128, -128, -128, -128, -128, 125, 25, -75, -128, -128, -128, -128, -128, -128, -128, -128, -128, 126, 26, -74, -128, -128, -128, -128, -128, -128, -128, -128, -128, 127, 27, -73, -128, -128, -128, -128, -128, -128, -128, -128, -128})
                        }, { // input034_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder68
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param148
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input134_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder69
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param149
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_35 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_35", get_test_model_all_inputs_as_internal_35());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_36() {
    static TestModel model = {
        .main = {
                .operands = {{ // input035
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input135
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output035
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -8, -8, -8, -9, -9, -33, -33, -33, -33, -34, -34, -8, -8, -8, -8, -9, -9, -33, -33, -33, -33, -34, -34, -8, -8, -8, -8, -9, -9, -33, -33, -33, -33, -34, -34, -8, -8, -8, -8, -9, -9, -33, -33, -33, -33, -34, -34, -8, -8, -8, -8, -9, -9, -33, -33, -33, -33, -34, -34, -8, -8, -8, -8, -9, -9, -33, -33, -33, -33, -34, -34, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_36 = TestModelManager::get().add("sub_quant8_signed_36", get_test_model_36());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_36() {
    static TestModel model = {
        .main = {
                .operands = {{ // input035
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input135
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // output035
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -8, -8, -8, -9, -9, -33, -33, -33, -33, -34, -34, -8, -8, -8, -8, -9, -9, -33, -33, -33, -33, -34, -34, -8, -8, -8, -8, -9, -9, -33, -33, -33, -33, -34, -34, -8, -8, -8, -8, -9, -9, -33, -33, -33, -33, -34, -34, -8, -8, -8, -8, -9, -9, -33, -33, -33, -33, -34, -34, -8, -8, -8, -8, -9, -9, -33, -33, -33, -33, -34, -34, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33})
                        }, { // input035_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder70
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param150
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input135_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder71
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param151
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_36 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_36", get_test_model_all_inputs_as_internal_36());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_37() {
    static TestModel model = {
        .main = {
                .operands = {{ // input036
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input136
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // param36
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output036
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_37 = TestModelManager::get().add("sub_quant8_signed_37", get_test_model_37());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_37() {
    static TestModel model = {
        .main = {
                .operands = {{ // input036
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input136
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param36
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output036
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128})
                        }, { // input036_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder72
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param152
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input136_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder73
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127})
                        }, { // param153
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_37 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_37", get_test_model_all_inputs_as_internal_37());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_38() {
    static TestModel model = {
        .main = {
                .operands = {{ // input037
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input137
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // param37
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output037
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_38 = TestModelManager::get().add("sub_quant8_signed_38", get_test_model_38());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_38() {
    static TestModel model = {
        .main = {
                .operands = {{ // input037
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input137
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param37
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output037
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -125, -126, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128})
                        }, { // input037_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder74
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param154
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input137_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder75
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127})
                        }, { // param155
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_38 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_38", get_test_model_all_inputs_as_internal_38());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_39() {
    static TestModel model = {
        .main = {
                .operands = {{ // input038
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input138
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // param38
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output038
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -27, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -26, -126, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -25, -125, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -24, -124, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -23, -123, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, 127, 122, 22, -78, -128, -128, -128, -128, -128, -128, -128, -128, 127, 123, 23, -77, -128, -128, -128, -128, -128, -128, -128, -128, 127, 124, 24, -76, -128, -128, -128, -128, -128, -128, -128, -128, 127, 125, 25, -75, -128, -128, -128, -128, -128, -128, -128, -128, 127, 126, 26, -74, -128, -128, -128, -128, -128, -128, -128, -128, 127, 127, 27, -73, -128, -128, -128, -128, -128, -128, -128, -128})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_39 = TestModelManager::get().add("sub_quant8_signed_39", get_test_model_39());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_39() {
    static TestModel model = {
        .main = {
                .operands = {{ // input038
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input138
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param38
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output038
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -27, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -26, -126, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -25, -125, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -24, -124, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -23, -123, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, 127, 122, 22, -78, -128, -128, -128, -128, -128, -128, -128, -128, 127, 123, 23, -77, -128, -128, -128, -128, -128, -128, -128, -128, 127, 124, 24, -76, -128, -128, -128, -128, -128, -128, -128, -128, 127, 125, 25, -75, -128, -128, -128, -128, -128, -128, -128, -128, 127, 126, 26, -74, -128, -128, -128, -128, -128, -128, -128, -128, 127, 127, 27, -73, -128, -128, -128, -128, -128, -128, -128, -128})
                        }, { // input038_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder76
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param156
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input138_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder77
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127})
                        }, { // param157
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_39 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_39", get_test_model_all_inputs_as_internal_39());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_40() {
    static TestModel model = {
        .main = {
                .operands = {{ // input039
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input139
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output039
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -8, -8, -8, -8, -9, -33, -33, -33, -33, -33, -34, -8, -8, -8, -8, -8, -9, -33, -33, -33, -33, -33, -34, -8, -8, -8, -8, -8, -9, -33, -33, -33, -33, -33, -34, -8, -8, -8, -8, -8, -9, -33, -33, -33, -33, -33, -34, -8, -8, -8, -8, -8, -9, -33, -33, -33, -33, -33, -34, -8, -8, -8, -8, -8, -9, -33, -33, -33, -33, -33, -34, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_40 = TestModelManager::get().add("sub_quant8_signed_40", get_test_model_40());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_40() {
    static TestModel model = {
        .main = {
                .operands = {{ // input039
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input139
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // output039
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -8, -8, -8, -8, -9, -33, -33, -33, -33, -33, -34, -8, -8, -8, -8, -8, -9, -33, -33, -33, -33, -33, -34, -8, -8, -8, -8, -8, -9, -33, -33, -33, -33, -33, -34, -8, -8, -8, -8, -8, -9, -33, -33, -33, -33, -33, -34, -8, -8, -8, -8, -8, -9, -33, -33, -33, -33, -33, -34, -8, -8, -8, -8, -8, -9, -33, -33, -33, -33, -33, -34, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33, -8, -8, -8, -8, -8, -8, -33, -33, -33, -33, -33, -33})
                        }, { // input039_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder78
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param158
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input139_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder79
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127})
                        }, { // param159
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_40 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_40", get_test_model_all_inputs_as_internal_40());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_41() {
    static TestModel model = {
        .main = {
                .operands = {{ // input040
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input140
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // param40
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output040
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -126, -126, -126, -126, -126, -128, -128, -128, -128, -128, -128, -125, -126, -126, -126, -126, -126, -128, -128, -128, -128, -128, -128, -125, -125, -126, -126, -126, -126, -128, -128, -128, -128, -128, -128, -125, -125, -125, -126, -126, -126, -128, -128, -128, -128, -128, -128, -125, -125, -125, -125, -126, -126, -128, -128, -128, -128, -128, -128, -125, -125, -125, -125, -125, -126, -128, -128, -128, -128, -128, -128})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_41 = TestModelManager::get().add("sub_quant8_signed_41", get_test_model_41());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_41() {
    static TestModel model = {
        .main = {
                .operands = {{ // input040
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input140
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param40
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output040
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -126, -126, -126, -126, -126, -126, -128, -128, -128, -128, -128, -128, -125, -126, -126, -126, -126, -126, -128, -128, -128, -128, -128, -128, -125, -125, -126, -126, -126, -126, -128, -128, -128, -128, -128, -128, -125, -125, -125, -126, -126, -126, -128, -128, -128, -128, -128, -128, -125, -125, -125, -125, -126, -126, -128, -128, -128, -128, -128, -128, -125, -125, -125, -125, -125, -126, -128, -128, -128, -128, -128, -128})
                        }, { // input040_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder80
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param160
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input140_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder81
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param161
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_41 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_41", get_test_model_all_inputs_as_internal_41());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_42() {
    static TestModel model = {
        .main = {
                .operands = {{ // input041
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input141
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output041
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127, -127, -127, -127, -127, -127, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -128, -128, -128, -128, -128, -128, -125, -125, -125, -125, -125, -125, -127, -127, -127, -127, -127, -127, -124, -125, -125, -125, -125, -125, -127, -127, -127, -127, -127, -127, -124, -124, -125, -125, -125, -125, -127, -127, -127, -127, -127, -127, -124, -124, -124, -125, -125, -125, -127, -127, -127, -127, -127, -127, -124, -124, -124, -124, -125, -125, -127, -127, -127, -127, -127, -127, -124, -124, -124, -124, -124, -125, -127, -127, -127, -127, -127, -127})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_42 = TestModelManager::get().add("sub_quant8_signed_42", get_test_model_42());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_42() {
    static TestModel model = {
        .main = {
                .operands = {{ // input041
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input141
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // output041
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127, -127, -127, -127, -127, -127, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -128, -128, -128, -128, -128, -128, -125, -125, -125, -125, -125, -125, -127, -127, -127, -127, -127, -127, -124, -125, -125, -125, -125, -125, -127, -127, -127, -127, -127, -127, -124, -124, -125, -125, -125, -125, -127, -127, -127, -127, -127, -127, -124, -124, -124, -125, -125, -125, -127, -127, -127, -127, -127, -127, -124, -124, -124, -124, -125, -125, -127, -127, -127, -127, -127, -127, -124, -124, -124, -124, -124, -125, -127, -127, -127, -127, -127, -127})
                        }, { // input041_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder82
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param162
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input141_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder83
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param163
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_42 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_42", get_test_model_all_inputs_as_internal_42());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_43() {
    static TestModel model = {
        .main = {
                .operands = {{ // input042
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input142
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output042
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -9, -10, -11, -12, -13, -128, -128, -128, -128, -128, -128, -7, -8, -9, -10, -11, -12, -128, -128, -128, -128, -128, -128, -6, -7, -8, -9, -10, -11, -128, -128, -128, -128, -128, -128, -5, -6, -7, -8, -9, -10, -128, -128, -128, -128, -128, -128, -4, -5, -6, -7, -8, -9, -128, -128, -128, -128, -128, -128, -3, -4, -5, -6, -7, -8, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -8, -9, -10, -11, -12, -13, 127, 127, 127, 127, 127, 127, -7, -8, -9, -10, -11, -12, 127, 127, 127, 127, 127, 127, -6, -7, -8, -9, -10, -11, 127, 127, 127, 127, 127, 127, -5, -6, -7, -8, -9, -10, 127, 127, 127, 127, 127, 127, -4, -5, -6, -7, -8, -9, 127, 127, 127, 127, 127, 127, -3, -4, -5, -6, -7, -8})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_43 = TestModelManager::get().add("sub_quant8_signed_43", get_test_model_43());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_43() {
    static TestModel model = {
        .main = {
                .operands = {{ // input042
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input142
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // output042
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -9, -10, -11, -12, -13, -128, -128, -128, -128, -128, -128, -7, -8, -9, -10, -11, -12, -128, -128, -128, -128, -128, -128, -6, -7, -8, -9, -10, -11, -128, -128, -128, -128, -128, -128, -5, -6, -7, -8, -9, -10, -128, -128, -128, -128, -128, -128, -4, -5, -6, -7, -8, -9, -128, -128, -128, -128, -128, -128, -3, -4, -5, -6, -7, -8, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -8, -9, -10, -11, -12, -13, 127, 127, 127, 127, 127, 127, -7, -8, -9, -10, -11, -12, 127, 127, 127, 127, 127, 127, -6, -7, -8, -9, -10, -11, 127, 127, 127, 127, 127, 127, -5, -6, -7, -8, -9, -10, 127, 127, 127, 127, 127, 127, -4, -5, -6, -7, -8, -9, 127, 127, 127, 127, 127, 127, -3, -4, -5, -6, -7, -8})
                        }, { // input042_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder84
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param164
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input142_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder85
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param165
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_43 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_43", get_test_model_all_inputs_as_internal_43());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_44() {
    static TestModel model = {
        .main = {
                .operands = {{ // input043
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input143
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // param43
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output043
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_44 = TestModelManager::get().add("sub_quant8_signed_44", get_test_model_44());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_44() {
    static TestModel model = {
        .main = {
                .operands = {{ // input043
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input143
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param43
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output043
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8, -8})
                        }, { // input043_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder86
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param166
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input143_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder87
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param167
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_44 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_44", get_test_model_all_inputs_as_internal_44());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_45() {
    static TestModel model = {
        .main = {
                .operands = {{ // input044
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input144
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // param44
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output044
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_45 = TestModelManager::get().add("sub_quant8_signed_45", get_test_model_45());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_45() {
    static TestModel model = {
        .main = {
                .operands = {{ // input044
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input144
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param44
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output044
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128})
                        }, { // input044_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder88
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param168
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input144_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder89
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param169
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_45 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_45", get_test_model_all_inputs_as_internal_45());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_46() {
    static TestModel model = {
        .main = {
                .operands = {{ // input045
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input145
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // param45
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output045
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_46 = TestModelManager::get().add("sub_quant8_signed_46", get_test_model_46());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_46() {
    static TestModel model = {
        .main = {
                .operands = {{ // input045
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input145
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param45
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output045
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128})
                        }, { // input045_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder90
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param170
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input145_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder91
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param171
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_46 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_46", get_test_model_all_inputs_as_internal_46());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_47() {
    static TestModel model = {
        .main = {
                .operands = {{ // input046
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input146
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // param46
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output046
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_47 = TestModelManager::get().add("sub_quant8_signed_47", get_test_model_47());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_47() {
    static TestModel model = {
        .main = {
                .operands = {{ // input046
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input146
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param46
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output046
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128})
                        }, { // input046_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder92
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param172
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input146_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder93
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param173
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_47 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_47", get_test_model_all_inputs_as_internal_47());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_48() {
    static TestModel model = {
        .main = {
                .operands = {{ // input047
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input147
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output047
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_48 = TestModelManager::get().add("sub_quant8_signed_48", get_test_model_48());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_48() {
    static TestModel model = {
        .main = {
                .operands = {{ // input047
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input147
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // output047
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128, 112, 111, 110, 109, 108, 107, -128, -128, -128, -128, -128, -128})
                        }, { // input047_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder94
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param174
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input147_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder95
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param175
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_48 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_48", get_test_model_all_inputs_as_internal_48());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_49() {
    static TestModel model = {
        .main = {
                .operands = {{ // input048
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input148
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output048
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_49 = TestModelManager::get().add("sub_quant8_signed_49", get_test_model_49());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_49() {
    static TestModel model = {
        .main = {
                .operands = {{ // input048
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input148
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // output048
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input048_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder96
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param176
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input148_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder97
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param177
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_49 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_49", get_test_model_all_inputs_as_internal_49());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_50() {
    static TestModel model = {
        .main = {
                .operands = {{ // input049
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input149
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // param49
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output049
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_50 = TestModelManager::get().add("sub_quant8_signed_50", get_test_model_50());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_50() {
    static TestModel model = {
        .main = {
                .operands = {{ // input049
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input149
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param49
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output049
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input049_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder98
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param178
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input149_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder99
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param179
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_50 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_50", get_test_model_all_inputs_as_internal_50());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_51() {
    static TestModel model = {
        .main = {
                .operands = {{ // input050
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input150
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // param50
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output050
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_51 = TestModelManager::get().add("sub_quant8_signed_51", get_test_model_51());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_51() {
    static TestModel model = {
        .main = {
                .operands = {{ // input050
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input150
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param50
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output050
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input050_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder100
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param180
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input150_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder101
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param181
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_51 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_51", get_test_model_all_inputs_as_internal_51());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_52() {
    static TestModel model = {
        .main = {
                .operands = {{ // input051
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input151
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // param51
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output051
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -128, -128, -128, -128, -128, -128, -128, -126, -126, -126, -126, -126, -126, -128, -128, -128, -128, -128, -128, -125, -125, -125, -125, -125, -126, -128, -128, -128, -128, -128, -128, -124, -124, -124, -124, -124, -124, -128, -128, -128, -128, -128, -128, -123, -123, -123, -123, -123, -124, -128, -128, -128, -128, -128, -128, 122, 122, 122, 122, 122, 122, 97, 97, 97, 97, 97, 96, 123, 123, 123, 123, 123, 122, 98, 98, 98, 98, 98, 98, 124, 124, 124, 124, 124, 124, 99, 99, 99, 99, 99, 98, 125, 125, 125, 125, 125, 124, 100, 100, 100, 100, 100, 100, 126, 126, 126, 126, 126, 126, 101, 101, 101, 101, 101, 100, 127, 127, 127, 127, 127, 126, 102, 102, 102, 102, 102, 102})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_52 = TestModelManager::get().add("sub_quant8_signed_52", get_test_model_52());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_52() {
    static TestModel model = {
        .main = {
                .operands = {{ // input051
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input151
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param51
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output051
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -128, -128, -128, -128, -128, -128, -128, -126, -126, -126, -126, -126, -126, -128, -128, -128, -128, -128, -128, -125, -125, -125, -125, -125, -126, -128, -128, -128, -128, -128, -128, -124, -124, -124, -124, -124, -124, -128, -128, -128, -128, -128, -128, -123, -123, -123, -123, -123, -124, -128, -128, -128, -128, -128, -128, 122, 122, 122, 122, 122, 122, 97, 97, 97, 97, 97, 96, 123, 123, 123, 123, 123, 122, 98, 98, 98, 98, 98, 98, 124, 124, 124, 124, 124, 124, 99, 99, 99, 99, 99, 98, 125, 125, 125, 125, 125, 124, 100, 100, 100, 100, 100, 100, 126, 126, 126, 126, 126, 126, 101, 101, 101, 101, 101, 100, 127, 127, 127, 127, 127, 126, 102, 102, 102, 102, 102, 102})
                        }, { // input051_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder102
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param182
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input151_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder103
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param183
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_52 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_52", get_test_model_all_inputs_as_internal_52());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_53() {
    static TestModel model = {
        .main = {
                .operands = {{ // input052
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input152
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output052
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_53 = TestModelManager::get().add("sub_quant8_signed_53", get_test_model_53());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_53() {
    static TestModel model = {
        .main = {
                .operands = {{ // input052
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input152
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // output052
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input052_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder104
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param184
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input152_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder105
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127})
                        }, { // param185
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_53 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_53", get_test_model_all_inputs_as_internal_53());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_54() {
    static TestModel model = {
        .main = {
                .operands = {{ // input053
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input153
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // param53
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output053
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_54 = TestModelManager::get().add("sub_quant8_signed_54", get_test_model_54());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_54() {
    static TestModel model = {
        .main = {
                .operands = {{ // input053
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input153
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param53
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output053
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input053_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder106
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param186
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input153_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder107
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127})
                        }, { // param187
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_54 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_54", get_test_model_all_inputs_as_internal_54());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_55() {
    static TestModel model = {
        .main = {
                .operands = {{ // input054
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input154
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output054
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_55 = TestModelManager::get().add("sub_quant8_signed_55", get_test_model_55());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_55() {
    static TestModel model = {
        .main = {
                .operands = {{ // input054
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input154
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // output054
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input054_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder108
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param188
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input154_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder109
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127})
                        }, { // param189
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_55 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_55", get_test_model_all_inputs_as_internal_55());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_56() {
    static TestModel model = {
        .main = {
                .operands = {{ // input055
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input155
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output055
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -128, -128, -128, -128, -128, -128, -126, -126, -126, -126, -126, -126, -128, -128, -128, -128, -128, -128, -125, -125, -125, -125, -125, -125, -128, -128, -128, -128, -128, -128, -124, -124, -124, -124, -124, -124, -128, -128, -128, -128, -128, -128, -123, -123, -123, -123, -123, -123, -128, -128, -128, -128, -128, -128, 122, 122, 122, 122, 122, 122, 97, 97, 97, 97, 97, 97, 123, 123, 123, 123, 123, 123, 98, 98, 98, 98, 98, 98, 124, 124, 124, 124, 124, 124, 99, 99, 99, 99, 99, 99, 125, 125, 125, 125, 125, 125, 100, 100, 100, 100, 100, 100, 126, 126, 126, 126, 126, 126, 101, 101, 101, 101, 101, 101, 127, 127, 127, 127, 127, 127, 102, 102, 102, 102, 102, 102})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_56 = TestModelManager::get().add("sub_quant8_signed_56", get_test_model_56());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_56() {
    static TestModel model = {
        .main = {
                .operands = {{ // input055
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input155
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // output055
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -128, -128, -128, -128, -128, -128, -126, -126, -126, -126, -126, -126, -128, -128, -128, -128, -128, -128, -125, -125, -125, -125, -125, -125, -128, -128, -128, -128, -128, -128, -124, -124, -124, -124, -124, -124, -128, -128, -128, -128, -128, -128, -123, -123, -123, -123, -123, -123, -128, -128, -128, -128, -128, -128, 122, 122, 122, 122, 122, 122, 97, 97, 97, 97, 97, 97, 123, 123, 123, 123, 123, 123, 98, 98, 98, 98, 98, 98, 124, 124, 124, 124, 124, 124, 99, 99, 99, 99, 99, 99, 125, 125, 125, 125, 125, 125, 100, 100, 100, 100, 100, 100, 126, 126, 126, 126, 126, 126, 101, 101, 101, 101, 101, 101, 127, 127, 127, 127, 127, 127, 102, 102, 102, 102, 102, 102})
                        }, { // input055_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder110
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param190
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input155_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder111
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127})
                        }, { // param191
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_56 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_56", get_test_model_all_inputs_as_internal_56());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_57() {
    static TestModel model = {
        .main = {
                .operands = {{ // input056
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input156
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // param56
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output056
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_57 = TestModelManager::get().add("sub_quant8_signed_57", get_test_model_57());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_57() {
    static TestModel model = {
        .main = {
                .operands = {{ // input056
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input156
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param56
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output056
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input056_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder112
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param192
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input156_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder113
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param193
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_57 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_57", get_test_model_all_inputs_as_internal_57());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_58() {
    static TestModel model = {
        .main = {
                .operands = {{ // input057
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input157
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // param57
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output057
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_58 = TestModelManager::get().add("sub_quant8_signed_58", get_test_model_58());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_58() {
    static TestModel model = {
        .main = {
                .operands = {{ // input057
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input157
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param57
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output057
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input057_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder114
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param194
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input157_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder115
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param195
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_58 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_58", get_test_model_all_inputs_as_internal_58());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_59() {
    static TestModel model = {
        .main = {
                .operands = {{ // input058
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input158
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // param58
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output058
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_59 = TestModelManager::get().add("sub_quant8_signed_59", get_test_model_59());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_59() {
    static TestModel model = {
        .main = {
                .operands = {{ // input058
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input158
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param58
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output058
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input058_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder116
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param196
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input158_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder117
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param197
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_59 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_59", get_test_model_all_inputs_as_internal_59());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_60() {
    static TestModel model = {
        .main = {
                .operands = {{ // input059
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input159
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // param59
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output059
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_60 = TestModelManager::get().add("sub_quant8_signed_60", get_test_model_60());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_60() {
    static TestModel model = {
        .main = {
                .operands = {{ // input059
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input159
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param59
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output059
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input059_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder118
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param198
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input159_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder119
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param199
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_60 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_60", get_test_model_all_inputs_as_internal_60());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_61() {
    static TestModel model = {
        .main = {
                .operands = {{ // input060
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input160
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output060
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -118, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -108, -118, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -98, -108, -118, -128, -128, -128, -128, -128, -128, -128, -128, -128, -88, -98, -108, -118, -128, -128, -128, -128, -128, -128, -128, -128, -78, -88, -98, -108, -118, -128, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -118, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -108, -118, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -98, -108, -118, -128, -128, -128, 127, 127, 127, 127, 127, 127, -88, -98, -108, -118, -128, -128, 127, 127, 127, 127, 127, 127, -78, -88, -98, -108, -118, -128})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_61 = TestModelManager::get().add("sub_quant8_signed_61", get_test_model_61());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_61() {
    static TestModel model = {
        .main = {
                .operands = {{ // input060
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input160
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // output060
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -118, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -108, -118, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -98, -108, -118, -128, -128, -128, -128, -128, -128, -128, -128, -128, -88, -98, -108, -118, -128, -128, -128, -128, -128, -128, -128, -128, -78, -88, -98, -108, -118, -128, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -118, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -108, -118, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -98, -108, -118, -128, -128, -128, 127, 127, 127, 127, 127, 127, -88, -98, -108, -118, -128, -128, 127, 127, 127, 127, 127, 127, -78, -88, -98, -108, -118, -128})
                        }, { // input060_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder120
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param200
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input160_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder121
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param201
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_61 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_61", get_test_model_all_inputs_as_internal_61());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_62() {
    static TestModel model = {
        .main = {
                .operands = {{ // input061
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input161
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
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
                        }, { // output061
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -117, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -107, -117, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -97, -107, -117, -127, -128, -128, -128, -128, -128, -128, -128, -128, -87, -97, -107, -117, -127, -128, -128, -128, -128, -128, -128, -128, -77, -87, -97, -107, -117, -127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -127, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -117, -127, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -107, -117, -127, -128, -128, -128, 127, 127, 127, 127, 127, 127, -97, -107, -117, -127, -128, -128, 127, 127, 127, 127, 127, 127, -87, -97, -107, -117, -127, -128, 127, 127, 127, 127, 127, 127, -77, -87, -97, -107, -117, -127})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_62 = TestModelManager::get().add("sub_quant8_signed_62", get_test_model_62());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_62() {
    static TestModel model = {
        .main = {
                .operands = {{ // input061
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input161
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // output061
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -117, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -107, -117, -127, -128, -128, -128, -128, -128, -128, -128, -128, -128, -97, -107, -117, -127, -128, -128, -128, -128, -128, -128, -128, -128, -87, -97, -107, -117, -127, -128, -128, -128, -128, -128, -128, -128, -77, -87, -97, -107, -117, -127, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -127, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -117, -127, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -107, -117, -127, -128, -128, -128, 127, 127, 127, 127, 127, 127, -97, -107, -117, -127, -128, -128, 127, 127, 127, 127, 127, 127, -87, -97, -107, -117, -127, -128, 127, 127, 127, 127, 127, 127, -77, -87, -97, -107, -117, -127})
                        }, { // input061_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder122
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param202
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input161_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder123
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param203
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_62 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_62", get_test_model_all_inputs_as_internal_62());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_63() {
    static TestModel model = {
        .main = {
                .operands = {{ // input062
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input162
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // param62
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output062
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, 127, -8, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, 127, 127, -8, -128, -128, -128, -128, -128, -128, -128, -128, -128, 127, 127, 127, -8, -128, -128, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, -8, -128, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, -8, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -8, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, 127, -8, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, 127, 127, -8, -128, -128, -128, 127, 127, 127, 127, 127, 127, 127, 127, 127, -8, -128, -128, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, -8, -128, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, -8})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_63 = TestModelManager::get().add("sub_quant8_signed_63", get_test_model_63());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_63() {
    static TestModel model = {
        .main = {
                .operands = {{ // input062
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input162
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param62
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output062
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, 127, -8, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, 127, 127, -8, -128, -128, -128, -128, -128, -128, -128, -128, -128, 127, 127, 127, -8, -128, -128, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, -8, -128, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, -8, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -8, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, 127, -8, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, 127, 127, -8, -128, -128, -128, 127, 127, 127, 127, 127, 127, 127, 127, 127, -8, -128, -128, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, -8, -128, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, -8})
                        }, { // input062_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder124
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param204
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input162_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder125
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param205
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_63 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_63", get_test_model_all_inputs_as_internal_63());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_64() {
    static TestModel model = {
        .main = {
                .operands = {{ // input063
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // input163
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // param63
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output063
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -9, -10, -11, -12, -13, -128, -128, -128, -128, -128, -128, -7, -8, -9, -10, -11, -12, -128, -128, -128, -128, -128, -128, -6, -7, -8, -9, -10, -11, -128, -128, -128, -128, -128, -128, -5, -6, -7, -8, -9, -10, -128, -128, -128, -128, -128, -128, -4, -5, -6, -7, -8, -9, -128, -128, -128, -128, -128, -128, -3, -4, -5, -6, -7, -8, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -8, -9, -10, -11, -12, -13, 127, 127, 127, 127, 127, 127, -7, -8, -9, -10, -11, -12, 127, 127, 127, 127, 127, 127, -6, -7, -8, -9, -10, -11, 127, 127, 127, 127, 127, 127, -5, -6, -7, -8, -9, -10, 127, 127, 127, 127, 127, 127, -4, -5, -6, -7, -8, -9, 127, 127, 127, 127, 127, 127, -3, -4, -5, -6, -7, -8})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_64 = TestModelManager::get().add("sub_quant8_signed_64", get_test_model_64());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_64() {
    static TestModel model = {
        .main = {
                .operands = {{ // input063
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input163
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param63
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output063
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -9, -10, -11, -12, -13, -128, -128, -128, -128, -128, -128, -7, -8, -9, -10, -11, -12, -128, -128, -128, -128, -128, -128, -6, -7, -8, -9, -10, -11, -128, -128, -128, -128, -128, -128, -5, -6, -7, -8, -9, -10, -128, -128, -128, -128, -128, -128, -4, -5, -6, -7, -8, -9, -128, -128, -128, -128, -128, -128, -3, -4, -5, -6, -7, -8, -128, -128, -128, -128, -128, -128, 127, 127, 127, 127, 127, 127, -8, -9, -10, -11, -12, -13, 127, 127, 127, 127, 127, 127, -7, -8, -9, -10, -11, -12, 127, 127, 127, 127, 127, 127, -6, -7, -8, -9, -10, -11, 127, 127, 127, 127, 127, 127, -5, -6, -7, -8, -9, -10, 127, 127, 127, 127, 127, 127, -4, -5, -6, -7, -8, -9, 127, 127, 127, 127, 127, 127, -3, -4, -5, -6, -7, -8})
                        }, { // input063_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -126, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -125, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -124, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, -123, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127})
                        }, { // placeholder126
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param206
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input163_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125, -124, -123, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder127
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8})
                        }, { // param207
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_all_inputs_as_internal_64 = TestModelManager::get().add("sub_quant8_signed_all_inputs_as_internal_64", get_test_model_all_inputs_as_internal_64());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_quant8() {
    static TestModel model = {
        .main = { // quant8
                .operands = {{ // input064
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28, 72})
                        }, { // input164
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127, -126, -125, -124})
                        }, { // param64
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output064
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-29, 70, -31, 68})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_quant8 = TestModelManager::get().add("sub_quant8_signed_quant8", get_test_model_quant8());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_quant8_all_inputs_as_internal() {
    static TestModel model = {
        .main = { // quant8
                .operands = {{ // input064
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input164
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param64
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // output064
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-29, 70, -31, 68})
                        }, { // input064_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28, 72})
                        }, { // placeholder128
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param208
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input164_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127, -126, -125, -124})
                        }, { // placeholder129
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param209
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_quant8_all_inputs_as_internal = TestModelManager::get().add("sub_quant8_signed_quant8_all_inputs_as_internal", get_test_model_quant8_all_inputs_as_internal());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_quant8_2() {
    static TestModel model = {
        .main = { // quant8
                .operands = {{ // input065
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 4, 16, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, -122, -121, -120, -119, -118, -117, -116, -115, -114, -113, -112, -111, -110, -109, -108, -107, -106, -105, -104, -103, -102, -101, -100, -99, -98, -97, -96, -95, -94, -93, -92, -91, -90, -89, -88, -87, -86, -85, -84, -83, -82, -81, -80, -79, -78, -77, -76, -75, -74, -73, -72, -71, -70, -69, -68, -67, -66, -65, -64, -63, -62, -61, -60, -59, -58, -57, -56, -55, -54, -53, -52, -51, -50, -49, -48, -47, -46, -45, -44, -43, -42, -41, -40, -39, -38, -37, -36, -35, -34, -33, -32, -31, -30, -29, -28, -27, -26, -25, -24, -23, -22, -21, -20, -19, -18, -17, -16, -15, -14, -13, -12, -11, -10, -9, -8, -7, -6, -5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127})
                        }, { // input165
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 4, 16, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127, -40, 4, 105, 34, -89, 57, 109, 110, 31, 36, -52, -69, 16, -31, -34, 86, 68, 85, 93, -63, -12, -79, 94, 96, -65, -77, -10, 29, -22, -75, -83, 63, -70, 125, -57, 20, 126, 3, -88, -85, -71, -115, 0, 50, -98, -82, 98, 55, -61, 115, -84, -122, 64, 44, -99, -96, 82, -46, 42, 14, -109, 103, -1, 33, 18, 40, 67, -23, -59, 121, 118, -102, 23, 87, 62, -36, 117, -42, -124, -16, -19, -117, -78, -29, -32, 48, -11, -33, 116, 70, 49, -41, 41, -60, 25, 101, -123, -18, -39, 90, 9, -116, -121, -24, -74, -9, -107, -27, 27, -100, 83, -5, -94, -35, -126, 38, 102, -20, -86, 81, -53, 59, -114, -50, -87, 123, 112, 61, -13, 7, 124, 108, -68, 74, -58, 6, -28, 46, -119, -90, -95, -106, -111, -7, 73, -120, 111, 54, -81, 39, 51, 19, 45, -30, 24, 88, 75, -55, 22, 37, 95, 78, 10, 60, 71, -97, -54, 77, 114, -101, -3, 120, -47, -108, 127, -14, 11, -92, -67, -72, 17, -80, -112, 97, -45, 91, -66, -43, -2, 80, -128, 32, 43, 53, -26, 56, -105, -125, 12, -113, 122, 5, -15, 113, 13, -76, 35, 28, -48, -17, -38, 92, 15, -8, -44, 47, 89, -110, 58, -103, -49, -91, 26, 79, 52, 8, -64, 76, 30, -104, 65, 106, -56, -93, 1, -73, 104, 100, 21, -37, -6, -51, 84, 72, 107, -25, -4, 2, 119, -62, -118, -21, 99, 66, 69})
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
                        }, { // output065
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 4, 16, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -99, -128, -128, -113, -128, -128, -128, -128, -128, -121, -82, -128, -128, -102, -104, -128, -128, -128, -128, -86, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -82, -128, -128, -128, -128, -128, -128, -53, -128, -128, -57, -95, -128, -128, -128, -128, -128, -128, -128, -128, -123, -128, -102, -128, -128, -36, -128, -118, -128, -128, -38, -32, -128, -77, -128, -42, -121, -128, -46, -128, -128, -49, -107, -15, -128, -128, -118, -51, -128, -82, -128, -19, -82, -44, -128, -128, -128, -114, -128, -128, -128, -55, -128, -63, -126, -91, -128, 2, -26, -20, -8, -2, -105, -128, 10, -128, -128, -26, -128, -128, -123, -128, -72, -125, -128, -128, -43, -119, -128, -128, -128, -103, -128, -128, 7, -35, -128, -128, 15, -82, -128, -36, 26, -128, -66, -90, 14, -10, -4, -92, 6, 39, -128, -26, -128, -3, -25, -65, -128, 63, -96, -106, -115, -35, -116, 46, 67, -69, 57, -128, -59, -38, -128, -64, 26, -84, -76, 1, -29, -7, -128, -58, -34, 3, -87, -128, 72, -95, 67, 14, 57, -59, -111, -83, -38, 35, -104, -57, 78, -90, -128, 33, 71, -22, 53, -123, -118, -38, 21, -9, 37, -97, -84, -118, 15, -5, -10, -126, 56, 113, 17, -102, -68, -70})
                        }},
                .operations = {{
                            .type = TestOperationType::SUB,
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

const auto dummy_test_model_quant8_2 = TestModelManager::get().add("sub_quant8_signed_quant8_2", get_test_model_quant8_2());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_quant8_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = { // quant8
                .operands = {{ // input065
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 4, 16, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input165
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 4, 16, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                        }, { // output065
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 4, 16, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -99, -128, -128, -113, -128, -128, -128, -128, -128, -121, -82, -128, -128, -102, -104, -128, -128, -128, -128, -86, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -82, -128, -128, -128, -128, -128, -128, -53, -128, -128, -57, -95, -128, -128, -128, -128, -128, -128, -128, -128, -123, -128, -102, -128, -128, -36, -128, -118, -128, -128, -38, -32, -128, -77, -128, -42, -121, -128, -46, -128, -128, -49, -107, -15, -128, -128, -118, -51, -128, -82, -128, -19, -82, -44, -128, -128, -128, -114, -128, -128, -128, -55, -128, -63, -126, -91, -128, 2, -26, -20, -8, -2, -105, -128, 10, -128, -128, -26, -128, -128, -123, -128, -72, -125, -128, -128, -43, -119, -128, -128, -128, -103, -128, -128, 7, -35, -128, -128, 15, -82, -128, -36, 26, -128, -66, -90, 14, -10, -4, -92, 6, 39, -128, -26, -128, -3, -25, -65, -128, 63, -96, -106, -115, -35, -116, 46, 67, -69, 57, -128, -59, -38, -128, -64, 26, -84, -76, 1, -29, -7, -128, -58, -34, 3, -87, -128, 72, -95, 67, 14, 57, -59, -111, -83, -38, 35, -104, -57, 78, -90, -128, 33, 71, -22, 53, -123, -118, -38, 21, -9, 37, -97, -84, -118, 15, -5, -10, -126, 56, 113, 17, -102, -68, -70})
                        }, { // input065_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 4, 16, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, -122, -121, -120, -119, -118, -117, -116, -115, -114, -113, -112, -111, -110, -109, -108, -107, -106, -105, -104, -103, -102, -101, -100, -99, -98, -97, -96, -95, -94, -93, -92, -91, -90, -89, -88, -87, -86, -85, -84, -83, -82, -81, -80, -79, -78, -77, -76, -75, -74, -73, -72, -71, -70, -69, -68, -67, -66, -65, -64, -63, -62, -61, -60, -59, -58, -57, -56, -55, -54, -53, -52, -51, -50, -49, -48, -47, -46, -45, -44, -43, -42, -41, -40, -39, -38, -37, -36, -35, -34, -33, -32, -31, -30, -29, -28, -27, -26, -25, -24, -23, -22, -21, -20, -19, -18, -17, -16, -15, -14, -13, -12, -11, -10, -9, -8, -7, -6, -5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder130
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param210
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // input165_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 4, 16, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127, -40, 4, 105, 34, -89, 57, 109, 110, 31, 36, -52, -69, 16, -31, -34, 86, 68, 85, 93, -63, -12, -79, 94, 96, -65, -77, -10, 29, -22, -75, -83, 63, -70, 125, -57, 20, 126, 3, -88, -85, -71, -115, 0, 50, -98, -82, 98, 55, -61, 115, -84, -122, 64, 44, -99, -96, 82, -46, 42, 14, -109, 103, -1, 33, 18, 40, 67, -23, -59, 121, 118, -102, 23, 87, 62, -36, 117, -42, -124, -16, -19, -117, -78, -29, -32, 48, -11, -33, 116, 70, 49, -41, 41, -60, 25, 101, -123, -18, -39, 90, 9, -116, -121, -24, -74, -9, -107, -27, 27, -100, 83, -5, -94, -35, -126, 38, 102, -20, -86, 81, -53, 59, -114, -50, -87, 123, 112, 61, -13, 7, 124, 108, -68, 74, -58, 6, -28, 46, -119, -90, -95, -106, -111, -7, 73, -120, 111, 54, -81, 39, 51, 19, 45, -30, 24, 88, 75, -55, 22, 37, 95, 78, 10, 60, 71, -97, -54, 77, 114, -101, -3, 120, -47, -108, 127, -14, 11, -92, -67, -72, 17, -80, -112, 97, -45, 91, -66, -43, -2, 80, -128, 32, 43, 53, -26, 56, -105, -125, 12, -113, 122, 5, -15, 113, 13, -76, 35, 28, -48, -17, -38, 92, 15, -8, -44, 47, 89, -110, 58, -103, -49, -91, 26, 79, 52, 8, -64, 76, 30, -104, 65, 106, -56, -93, 1, -73, 104, 100, 21, -37, -6, -51, 84, 72, 107, -25, -4, 2, 119, -62, -118, -21, 99, 66, 69})
                        }, { // placeholder131
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param211
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4, 7},
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

const auto dummy_test_model_quant8_all_inputs_as_internal_2 = TestModelManager::get().add("sub_quant8_signed_quant8_all_inputs_as_internal_2", get_test_model_quant8_all_inputs_as_internal_2());

}  // namespace generated_tests::sub_quant8_signed

namespace generated_tests::sub_quant8_signed {

const TestModel& get_test_model_zero_sized_quant8_signed() {
    static TestModel model = {
        .main = { // zero_sized
                .operands = {{ // scores
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({9, 1})
                        }, { // roi
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {1, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({8, 8, 80, 80, 0, 0, 80, 80})
                        }, { // param66
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param67
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // param68
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-1})
                        }, { // param69
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param70
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.4f})
                        }, { // param71
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f})
                        }, { // param72
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {0},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // roiOut
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {0, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({})
                        }, { // classesOut
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {0},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // batchSplitOut
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {0},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // in
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({10, 20})
                        }, { // param73
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // param74
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // param75
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.0f})
                        }, { // param76
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.0f})
                        }, { // param77
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({4})
                        }, { // param78
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({4})
                        }, { // layout
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // featureMap
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {0, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // op
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({10, 20, 30, 40})
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
                        }, { // out
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {0, 2, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }},
                .operations = {{
                            .type = TestOperationType::BOX_WITH_NMS_LIMIT,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8},
                            .outputs = {9, 10, 11, 12}
                        }, {
                            .type = TestOperationType::ROI_ALIGN,
                            .inputs = {13, 10, 12, 14, 15, 16, 17, 18, 19, 20},
                            .outputs = {21}
                        }, {
                            .type = TestOperationType::SUB,
                            .inputs = {21, 22, 23},
                            .outputs = {24}
                        }},
                .inputIndexes = {13},
                .outputIndexes = {9, 11, 24}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_zero_sized_quant8_signed = TestModelManager::get().add("sub_quant8_signed_zero_sized_quant8_signed", get_test_model_zero_sized_quant8_signed());

}  // namespace generated_tests::sub_quant8_signed

