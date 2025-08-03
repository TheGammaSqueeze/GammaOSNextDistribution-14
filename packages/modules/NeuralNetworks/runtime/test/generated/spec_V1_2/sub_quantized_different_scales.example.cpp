// Generated from sub_quantized_different_scales.mod.py
// DO NOT EDIT
// clang-format off
#include "TestHarness.h"
using namespace test_helper;  // NOLINT(google-build-using-namespace)

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 5, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 250, 249, 248, 247, 246, 245, 0, 0, 0, 0, 0, 0, 251, 250, 249, 248, 247, 246, 1, 0, 0, 0, 0, 0, 252, 251, 250, 249, 248, 247, 2, 1, 0, 0, 0, 0, 253, 252, 251, 250, 249, 248, 3, 2, 1, 0, 0, 0, 254, 253, 252, 251, 250, 249, 4, 3, 2, 1, 0, 0, 255, 254, 253, 252, 251, 250, 5, 4, 3, 2, 1, 0})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model = TestModelManager::get().add("sub_quantized_different_scales", get_test_model());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
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
                        }, { // output0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 5, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 250, 249, 248, 247, 246, 245, 0, 0, 0, 0, 0, 0, 251, 250, 249, 248, 247, 246, 1, 0, 0, 0, 0, 0, 252, 251, 250, 249, 248, 247, 2, 1, 0, 0, 0, 0, 253, 252, 251, 250, 249, 248, 3, 2, 1, 0, 0, 0, 254, 253, 252, 251, 250, 249, 4, 3, 2, 1, 0, 0, 255, 254, 253, 252, 251, 250, 5, 4, 3, 2, 1, 0})
                        }, { // input0_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
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
                        }, { // input1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal", get_test_model_all_inputs_as_internal());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // input01
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 5, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 6, 5, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 251, 250, 249, 248, 247, 246, 1, 0, 0, 0, 0, 0, 252, 251, 250, 249, 248, 247, 2, 1, 0, 0, 0, 0, 253, 252, 251, 250, 249, 248, 3, 2, 1, 0, 0, 0, 254, 253, 252, 251, 250, 249, 4, 3, 2, 1, 0, 0, 255, 254, 253, 252, 251, 250, 5, 4, 3, 2, 1, 0, 255, 255, 254, 253, 252, 251, 6, 5, 4, 3, 2, 1})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_2 = TestModelManager::get().add("sub_quantized_different_scales_2", get_test_model_2());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // input01
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
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
                        }, { // output01
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 5, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 6, 5, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 251, 250, 249, 248, 247, 246, 1, 0, 0, 0, 0, 0, 252, 251, 250, 249, 248, 247, 2, 1, 0, 0, 0, 0, 253, 252, 251, 250, 249, 248, 3, 2, 1, 0, 0, 0, 254, 253, 252, 251, 250, 249, 4, 3, 2, 1, 0, 0, 255, 254, 253, 252, 251, 250, 5, 4, 3, 2, 1, 0, 255, 255, 254, 253, 252, 251, 6, 5, 4, 3, 2, 1})
                        }, { // input01_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
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
                        }, { // input11_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_2 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_2", get_test_model_all_inputs_as_internal_2());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // input02
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 220, 120, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 220, 120, 20, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 220, 120, 20, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 220, 120, 20, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 220, 120, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 120, 20, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 220, 120, 20, 0, 0, 0, 255, 255, 255, 255, 255, 255, 255, 220, 120, 20, 0, 0, 255, 255, 255, 255, 255, 255, 255, 255, 220, 120, 20, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255, 220, 120, 20, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 220, 120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_3 = TestModelManager::get().add("sub_quantized_different_scales_3", get_test_model_3());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // input02
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 220, 120, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 220, 120, 20, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 220, 120, 20, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 220, 120, 20, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 220, 120, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 120, 20, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 220, 120, 20, 0, 0, 0, 255, 255, 255, 255, 255, 255, 255, 220, 120, 20, 0, 0, 255, 255, 255, 255, 255, 255, 255, 255, 220, 120, 20, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255, 220, 120, 20, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 220, 120})
                        }, { // input02_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
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
                        }, { // input12_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_3 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_3", get_test_model_all_inputs_as_internal_3());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // input03
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input13
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 94, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 96, 95, 95, 95, 95, 95, 145, 145, 145, 145, 145, 144, 120, 120, 120, 120, 120, 120, 145, 145, 145, 145, 145, 145, 120, 120, 120, 120, 120, 120, 145, 145, 145, 145, 145, 145, 120, 120, 120, 120, 120, 120, 145, 145, 145, 145, 145, 145, 120, 120, 120, 120, 120, 120, 145, 145, 145, 145, 145, 145, 120, 120, 120, 120, 120, 120, 146, 145, 145, 145, 145, 145, 120, 120, 120, 120, 120, 120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_4 = TestModelManager::get().add("sub_quantized_different_scales_4", get_test_model_4());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // input03
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input13
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 94, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 96, 95, 95, 95, 95, 95, 145, 145, 145, 145, 145, 144, 120, 120, 120, 120, 120, 120, 145, 145, 145, 145, 145, 145, 120, 120, 120, 120, 120, 120, 145, 145, 145, 145, 145, 145, 120, 120, 120, 120, 120, 120, 145, 145, 145, 145, 145, 145, 120, 120, 120, 120, 120, 120, 145, 145, 145, 145, 145, 145, 120, 120, 120, 120, 120, 120, 146, 145, 145, 145, 145, 145, 120, 120, 120, 120, 120, 120})
                        }, { // input03_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
                        }, { // param70
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
                        }, { // param71
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_4 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_4", get_test_model_all_inputs_as_internal_4());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_5() {
    static TestModel model = {
        .main = {
                .operands = {{ // input04
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input14
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 5, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 6, 5, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 251, 250, 249, 248, 247, 246, 1, 0, 0, 0, 0, 0, 252, 251, 250, 249, 248, 247, 2, 1, 0, 0, 0, 0, 253, 252, 251, 250, 249, 248, 3, 2, 1, 0, 0, 0, 254, 253, 252, 251, 250, 249, 4, 3, 2, 1, 0, 0, 255, 254, 253, 252, 251, 250, 5, 4, 3, 2, 1, 0, 255, 255, 254, 253, 252, 251, 6, 5, 4, 3, 2, 1})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_5 = TestModelManager::get().add("sub_quantized_different_scales_5", get_test_model_5());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_5() {
    static TestModel model = {
        .main = {
                .operands = {{ // input04
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input14
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
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
                        }, { // output04
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 5, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 6, 5, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 251, 250, 249, 248, 247, 246, 1, 0, 0, 0, 0, 0, 252, 251, 250, 249, 248, 247, 2, 1, 0, 0, 0, 0, 253, 252, 251, 250, 249, 248, 3, 2, 1, 0, 0, 0, 254, 253, 252, 251, 250, 249, 4, 3, 2, 1, 0, 0, 255, 254, 253, 252, 251, 250, 5, 4, 3, 2, 1, 0, 255, 255, 254, 253, 252, 251, 6, 5, 4, 3, 2, 1})
                        }, { // input04_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
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
                        }, { // input14_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_5 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_5", get_test_model_all_inputs_as_internal_5());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_6() {
    static TestModel model = {
        .main = {
                .operands = {{ // input05
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input15
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 5, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 6, 5, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 7, 6, 5, 4, 3, 2, 0, 0, 0, 0, 0, 0, 252, 251, 250, 249, 248, 247, 2, 1, 0, 0, 0, 0, 253, 252, 251, 250, 249, 248, 3, 2, 1, 0, 0, 0, 254, 253, 252, 251, 250, 249, 4, 3, 2, 1, 0, 0, 255, 254, 253, 252, 251, 250, 5, 4, 3, 2, 1, 0, 255, 255, 254, 253, 252, 251, 6, 5, 4, 3, 2, 1, 255, 255, 255, 254, 253, 252, 7, 6, 5, 4, 3, 2})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_6 = TestModelManager::get().add("sub_quantized_different_scales_6", get_test_model_6());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_6() {
    static TestModel model = {
        .main = {
                .operands = {{ // input05
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input15
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
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
                        }, { // output05
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 5, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 6, 5, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 7, 6, 5, 4, 3, 2, 0, 0, 0, 0, 0, 0, 252, 251, 250, 249, 248, 247, 2, 1, 0, 0, 0, 0, 253, 252, 251, 250, 249, 248, 3, 2, 1, 0, 0, 0, 254, 253, 252, 251, 250, 249, 4, 3, 2, 1, 0, 0, 255, 254, 253, 252, 251, 250, 5, 4, 3, 2, 1, 0, 255, 255, 254, 253, 252, 251, 6, 5, 4, 3, 2, 1, 255, 255, 255, 254, 253, 252, 7, 6, 5, 4, 3, 2})
                        }, { // input05_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder10
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
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
                        }, { // input15_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_6 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_6", get_test_model_all_inputs_as_internal_6());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_7() {
    static TestModel model = {
        .main = {
                .operands = {{ // input06
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input16
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({220, 120, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 220, 120, 20, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 220, 120, 20, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 220, 120, 20, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 220, 120, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 220, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 220, 120, 20, 0, 0, 0, 255, 255, 255, 255, 255, 255, 255, 220, 120, 20, 0, 0, 255, 255, 255, 255, 255, 255, 255, 255, 220, 120, 20, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255, 220, 120, 20, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 220, 120, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 220})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_7 = TestModelManager::get().add("sub_quantized_different_scales_7", get_test_model_7());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_7() {
    static TestModel model = {
        .main = {
                .operands = {{ // input06
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input16
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({220, 120, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 220, 120, 20, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 220, 120, 20, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 220, 120, 20, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 220, 120, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 220, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 220, 120, 20, 0, 0, 0, 255, 255, 255, 255, 255, 255, 255, 220, 120, 20, 0, 0, 255, 255, 255, 255, 255, 255, 255, 255, 220, 120, 20, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255, 220, 120, 20, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 220, 120, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 220})
                        }, { // input06_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
                        }, { // param76
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder13
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1})
                        }, { // param77
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_7 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_7", get_test_model_all_inputs_as_internal_7());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_8() {
    static TestModel model = {
        .main = {
                .operands = {{ // input07
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input17
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 96, 95, 95, 95, 95, 95, 121, 120, 120, 120, 120, 120, 96, 96, 95, 95, 95, 95, 145, 145, 145, 145, 145, 145, 120, 120, 120, 120, 120, 120, 145, 145, 145, 145, 145, 145, 120, 120, 120, 120, 120, 120, 145, 145, 145, 145, 145, 145, 120, 120, 120, 120, 120, 120, 145, 145, 145, 145, 145, 145, 120, 120, 120, 120, 120, 120, 146, 145, 145, 145, 145, 145, 120, 120, 120, 120, 120, 120, 146, 146, 145, 145, 145, 145, 121, 120, 120, 120, 120, 120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_8 = TestModelManager::get().add("sub_quantized_different_scales_8", get_test_model_8());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_8() {
    static TestModel model = {
        .main = {
                .operands = {{ // input07
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input17
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 96, 95, 95, 95, 95, 95, 121, 120, 120, 120, 120, 120, 96, 96, 95, 95, 95, 95, 145, 145, 145, 145, 145, 145, 120, 120, 120, 120, 120, 120, 145, 145, 145, 145, 145, 145, 120, 120, 120, 120, 120, 120, 145, 145, 145, 145, 145, 145, 120, 120, 120, 120, 120, 120, 145, 145, 145, 145, 145, 145, 120, 120, 120, 120, 120, 120, 146, 145, 145, 145, 145, 145, 120, 120, 120, 120, 120, 120, 146, 146, 145, 145, 145, 145, 121, 120, 120, 120, 120, 120})
                        }, { // input07_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder14
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
                        }, { // param78
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder15
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_8 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_8", get_test_model_all_inputs_as_internal_8());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_9() {
    static TestModel model = {
        .main = {
                .operands = {{ // input08
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input18
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 2, 2, 2, 2, 2, 2, 0, 0, 0, 0, 0, 0, 3, 3, 3, 3, 3, 3, 1, 1, 1, 1, 1, 1, 4, 4, 4, 4, 4, 4, 2, 2, 2, 2, 2, 2, 5, 5, 5, 5, 5, 5, 3, 3, 3, 3, 3, 3, 6, 6, 6, 6, 6, 6, 4, 4, 4, 4, 4, 4, 251, 251, 251, 251, 251, 251, 249, 249, 249, 249, 249, 249, 252, 252, 252, 252, 252, 252, 250, 250, 250, 250, 250, 250, 253, 253, 253, 253, 253, 253, 251, 251, 251, 251, 251, 251, 254, 254, 254, 254, 254, 254, 252, 252, 252, 252, 252, 252, 255, 255, 255, 255, 255, 255, 253, 253, 253, 253, 253, 253, 255, 255, 255, 255, 255, 255, 254, 254, 254, 254, 254, 254})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_9 = TestModelManager::get().add("sub_quantized_different_scales_9", get_test_model_9());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_9() {
    static TestModel model = {
        .main = {
                .operands = {{ // input08
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input18
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 2, 2, 2, 2, 2, 2, 0, 0, 0, 0, 0, 0, 3, 3, 3, 3, 3, 3, 1, 1, 1, 1, 1, 1, 4, 4, 4, 4, 4, 4, 2, 2, 2, 2, 2, 2, 5, 5, 5, 5, 5, 5, 3, 3, 3, 3, 3, 3, 6, 6, 6, 6, 6, 6, 4, 4, 4, 4, 4, 4, 251, 251, 251, 251, 251, 251, 249, 249, 249, 249, 249, 249, 252, 252, 252, 252, 252, 252, 250, 250, 250, 250, 250, 250, 253, 253, 253, 253, 253, 253, 251, 251, 251, 251, 251, 251, 254, 254, 254, 254, 254, 254, 252, 252, 252, 252, 252, 252, 255, 255, 255, 255, 255, 255, 253, 253, 253, 253, 253, 253, 255, 255, 255, 255, 255, 255, 254, 254, 254, 254, 254, 254})
                        }, { // input08_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder16
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
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
                        }, { // input18_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder17
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_9 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_9", get_test_model_all_inputs_as_internal_9());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_10() {
    static TestModel model = {
        .main = {
                .operands = {{ // input09
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input19
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({2, 2, 2, 2, 2, 2, 0, 0, 0, 0, 0, 0, 3, 3, 3, 3, 3, 3, 1, 1, 1, 1, 1, 1, 4, 4, 4, 4, 4, 4, 2, 2, 2, 2, 2, 2, 5, 5, 5, 5, 5, 5, 3, 3, 3, 3, 3, 3, 6, 6, 6, 6, 6, 6, 4, 4, 4, 4, 4, 4, 7, 7, 7, 7, 7, 7, 5, 5, 5, 5, 5, 5, 252, 252, 252, 252, 252, 252, 250, 250, 250, 250, 250, 250, 253, 253, 253, 253, 253, 253, 251, 251, 251, 251, 251, 251, 254, 254, 254, 254, 254, 254, 252, 252, 252, 252, 252, 252, 255, 255, 255, 255, 255, 255, 253, 253, 253, 253, 253, 253, 255, 255, 255, 255, 255, 255, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_10 = TestModelManager::get().add("sub_quantized_different_scales_10", get_test_model_10());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_10() {
    static TestModel model = {
        .main = {
                .operands = {{ // input09
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input19
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({2, 2, 2, 2, 2, 2, 0, 0, 0, 0, 0, 0, 3, 3, 3, 3, 3, 3, 1, 1, 1, 1, 1, 1, 4, 4, 4, 4, 4, 4, 2, 2, 2, 2, 2, 2, 5, 5, 5, 5, 5, 5, 3, 3, 3, 3, 3, 3, 6, 6, 6, 6, 6, 6, 4, 4, 4, 4, 4, 4, 7, 7, 7, 7, 7, 7, 5, 5, 5, 5, 5, 5, 252, 252, 252, 252, 252, 252, 250, 250, 250, 250, 250, 250, 253, 253, 253, 253, 253, 253, 251, 251, 251, 251, 251, 251, 254, 254, 254, 254, 254, 254, 252, 252, 252, 252, 252, 252, 255, 255, 255, 255, 255, 255, 253, 253, 253, 253, 253, 253, 255, 255, 255, 255, 255, 255, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input09_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder18
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
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
                        }, { // input19_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder19
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_10 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_10", get_test_model_all_inputs_as_internal_10());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_11() {
    static TestModel model = {
        .main = {
                .operands = {{ // input010
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input110
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 90, 89, 88, 87, 86, 85, 255, 255, 255, 255, 255, 255, 190, 189, 188, 187, 186, 185, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_11 = TestModelManager::get().add("sub_quantized_different_scales_11", get_test_model_11());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_11() {
    static TestModel model = {
        .main = {
                .operands = {{ // input010
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input110
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 90, 89, 88, 87, 86, 85, 255, 255, 255, 255, 255, 255, 190, 189, 188, 187, 186, 185, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input010_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder20
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
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
                        }, { // input110_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder21
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_11 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_11", get_test_model_all_inputs_as_internal_11());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_12() {
    static TestModel model = {
        .main = {
                .operands = {{ // input011
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input111
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 121, 121, 121, 121, 121, 121, 120, 120, 120, 120, 120, 120, 121, 121, 121, 121, 121, 121, 120, 120, 120, 120, 120, 120, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 146, 146, 146, 146, 146, 146, 145, 145, 145, 145, 145, 145, 146, 146, 146, 146, 146, 146, 145, 145, 145, 145, 145, 145})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_12 = TestModelManager::get().add("sub_quantized_different_scales_12", get_test_model_12());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_12() {
    static TestModel model = {
        .main = {
                .operands = {{ // input011
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input111
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 121, 121, 121, 121, 121, 121, 120, 120, 120, 120, 120, 120, 121, 121, 121, 121, 121, 121, 120, 120, 120, 120, 120, 120, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 146, 146, 146, 146, 146, 146, 145, 145, 145, 145, 145, 145, 146, 146, 146, 146, 146, 146, 145, 145, 145, 145, 145, 145})
                        }, { // input011_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder22
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
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
                        }, { // input111_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder23
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_12 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_12", get_test_model_all_inputs_as_internal_12());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_13() {
    static TestModel model = {
        .main = {
                .operands = {{ // input012
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input112
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_13 = TestModelManager::get().add("sub_quantized_different_scales_13", get_test_model_13());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_13() {
    static TestModel model = {
        .main = {
                .operands = {{ // input012
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input112
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0})
                        }, { // input012_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder24
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
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
                        }, { // input112_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder25
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_13 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_13", get_test_model_all_inputs_as_internal_13());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_14() {
    static TestModel model = {
        .main = {
                .operands = {{ // input013
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input113
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_14 = TestModelManager::get().add("sub_quantized_different_scales_14", get_test_model_14());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_14() {
    static TestModel model = {
        .main = {
                .operands = {{ // input013
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input113
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0})
                        }, { // input013_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder26
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
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
                        }, { // input113_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder27
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_14 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_14", get_test_model_all_inputs_as_internal_14());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_15() {
    static TestModel model = {
        .main = {
                .operands = {{ // input014
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input114
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_15 = TestModelManager::get().add("sub_quantized_different_scales_15", get_test_model_15());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_15() {
    static TestModel model = {
        .main = {
                .operands = {{ // input014
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input114
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0})
                        }, { // input014_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder28
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
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
                        }, { // input114_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder29
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_15 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_15", get_test_model_all_inputs_as_internal_15());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_16() {
    static TestModel model = {
        .main = {
                .operands = {{ // input015
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input115
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 240, 238, 238, 236, 236, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 15, 14, 13, 12, 11, 10, 255, 255, 255, 255, 255, 255, 15, 14, 13, 12, 11, 10, 255, 255, 255, 255, 255, 255, 15, 14, 13, 12, 11, 10, 255, 255, 255, 255, 255, 255, 15, 14, 13, 12, 11, 10, 255, 255, 255, 255, 255, 255, 15, 14, 13, 12, 11, 10, 255, 255, 255, 255, 255, 255, 16, 14, 14, 12, 12, 10})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_16 = TestModelManager::get().add("sub_quantized_different_scales_16", get_test_model_16());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_16() {
    static TestModel model = {
        .main = {
                .operands = {{ // input015
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input115
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 240, 238, 238, 236, 236, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 15, 14, 13, 12, 11, 10, 255, 255, 255, 255, 255, 255, 15, 14, 13, 12, 11, 10, 255, 255, 255, 255, 255, 255, 15, 14, 13, 12, 11, 10, 255, 255, 255, 255, 255, 255, 15, 14, 13, 12, 11, 10, 255, 255, 255, 255, 255, 255, 15, 14, 13, 12, 11, 10, 255, 255, 255, 255, 255, 255, 16, 14, 14, 12, 12, 10})
                        }, { // input015_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder30
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
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
                        }, { // input115_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder31
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_16 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_16", get_test_model_all_inputs_as_internal_16());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_17() {
    static TestModel model = {
        .main = {
                .operands = {{ // input016
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input116
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 249, 248, 247, 246, 245, 244, 0, 0, 0, 0, 0, 0, 250, 249, 248, 247, 246, 245, 0, 0, 0, 0, 0, 0, 251, 250, 249, 248, 247, 246, 1, 0, 0, 0, 0, 0, 252, 251, 250, 249, 248, 247, 2, 1, 0, 0, 0, 0, 253, 252, 251, 250, 249, 248, 3, 2, 1, 0, 0, 0, 254, 253, 252, 251, 250, 249, 4, 3, 2, 1, 0, 0})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_17 = TestModelManager::get().add("sub_quantized_different_scales_17", get_test_model_17());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_17() {
    static TestModel model = {
        .main = {
                .operands = {{ // input016
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input116
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 249, 248, 247, 246, 245, 244, 0, 0, 0, 0, 0, 0, 250, 249, 248, 247, 246, 245, 0, 0, 0, 0, 0, 0, 251, 250, 249, 248, 247, 246, 1, 0, 0, 0, 0, 0, 252, 251, 250, 249, 248, 247, 2, 1, 0, 0, 0, 0, 253, 252, 251, 250, 249, 248, 3, 2, 1, 0, 0, 0, 254, 253, 252, 251, 250, 249, 4, 3, 2, 1, 0, 0})
                        }, { // input016_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder32
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1})
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
                        }, { // input116_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder33
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_17 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_17", get_test_model_all_inputs_as_internal_17());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_18() {
    static TestModel model = {
        .main = {
                .operands = {{ // input017
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input117
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 5, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 250, 249, 248, 247, 246, 245, 0, 0, 0, 0, 0, 0, 251, 250, 249, 248, 247, 246, 1, 0, 0, 0, 0, 0, 252, 251, 250, 249, 248, 247, 2, 1, 0, 0, 0, 0, 253, 252, 251, 250, 249, 248, 3, 2, 1, 0, 0, 0, 254, 253, 252, 251, 250, 249, 4, 3, 2, 1, 0, 0, 255, 254, 253, 252, 251, 250, 5, 4, 3, 2, 1, 0})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_18 = TestModelManager::get().add("sub_quantized_different_scales_18", get_test_model_18());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_18() {
    static TestModel model = {
        .main = {
                .operands = {{ // input017
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input117
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 5, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 250, 249, 248, 247, 246, 245, 0, 0, 0, 0, 0, 0, 251, 250, 249, 248, 247, 246, 1, 0, 0, 0, 0, 0, 252, 251, 250, 249, 248, 247, 2, 1, 0, 0, 0, 0, 253, 252, 251, 250, 249, 248, 3, 2, 1, 0, 0, 0, 254, 253, 252, 251, 250, 249, 4, 3, 2, 1, 0, 0, 255, 254, 253, 252, 251, 250, 5, 4, 3, 2, 1, 0})
                        }, { // input017_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder34
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1})
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
                        }, { // input117_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder35
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_18 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_18", get_test_model_all_inputs_as_internal_18());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_19() {
    static TestModel model = {
        .main = {
                .operands = {{ // input018
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input118
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 120, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 220, 120, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 220, 120, 20, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 220, 120, 20, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 220, 120, 20, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 20, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 120, 20, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 220, 120, 20, 0, 0, 0, 255, 255, 255, 255, 255, 255, 255, 220, 120, 20, 0, 0, 255, 255, 255, 255, 255, 255, 255, 255, 220, 120, 20, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255, 220, 120, 20})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_19 = TestModelManager::get().add("sub_quantized_different_scales_19", get_test_model_19());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_19() {
    static TestModel model = {
        .main = {
                .operands = {{ // input018
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input118
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 120, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 220, 120, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 220, 120, 20, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 220, 120, 20, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 220, 120, 20, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 20, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 120, 20, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 220, 120, 20, 0, 0, 0, 255, 255, 255, 255, 255, 255, 255, 220, 120, 20, 0, 0, 255, 255, 255, 255, 255, 255, 255, 255, 220, 120, 20, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255, 220, 120, 20})
                        }, { // input018_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder36
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1})
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
                        }, { // input118_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder37
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_19 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_19", get_test_model_all_inputs_as_internal_19());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_20() {
    static TestModel model = {
        .main = {
                .operands = {{ // input019
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input119
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 120, 120, 120, 120, 119, 95, 95, 95, 95, 94, 94, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 94, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 145, 145, 145, 145, 144, 144, 120, 120, 120, 120, 120, 119, 145, 145, 145, 145, 145, 144, 120, 120, 120, 120, 120, 120, 145, 145, 145, 145, 145, 145, 120, 120, 120, 120, 120, 120, 145, 145, 145, 145, 145, 145, 120, 120, 120, 120, 120, 120, 145, 145, 145, 145, 145, 145, 120, 120, 120, 120, 120, 120, 145, 145, 145, 145, 145, 145, 120, 120, 120, 120, 120, 120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_20 = TestModelManager::get().add("sub_quantized_different_scales_20", get_test_model_20());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_20() {
    static TestModel model = {
        .main = {
                .operands = {{ // input019
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input119
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 120, 120, 120, 120, 119, 95, 95, 95, 95, 94, 94, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 94, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 145, 145, 145, 145, 144, 144, 120, 120, 120, 120, 120, 119, 145, 145, 145, 145, 145, 144, 120, 120, 120, 120, 120, 120, 145, 145, 145, 145, 145, 145, 120, 120, 120, 120, 120, 120, 145, 145, 145, 145, 145, 145, 120, 120, 120, 120, 120, 120, 145, 145, 145, 145, 145, 145, 120, 120, 120, 120, 120, 120, 145, 145, 145, 145, 145, 145, 120, 120, 120, 120, 120, 120})
                        }, { // input019_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder38
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1})
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
                        }, { // input119_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder39
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_20 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_20", get_test_model_all_inputs_as_internal_20());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_21() {
    static TestModel model = {
        .main = {
                .operands = {{ // input020
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input120
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 5, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 250, 249, 248, 247, 246, 245, 0, 0, 0, 0, 0, 0, 251, 250, 249, 248, 247, 246, 1, 0, 0, 0, 0, 0, 252, 251, 250, 249, 248, 247, 2, 1, 0, 0, 0, 0, 253, 252, 251, 250, 249, 248, 3, 2, 1, 0, 0, 0, 254, 253, 252, 251, 250, 249, 4, 3, 2, 1, 0, 0, 255, 254, 253, 252, 251, 250, 5, 4, 3, 2, 1, 0})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_21 = TestModelManager::get().add("sub_quantized_different_scales_21", get_test_model_21());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_21() {
    static TestModel model = {
        .main = {
                .operands = {{ // input020
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input120
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 5, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 250, 249, 248, 247, 246, 245, 0, 0, 0, 0, 0, 0, 251, 250, 249, 248, 247, 246, 1, 0, 0, 0, 0, 0, 252, 251, 250, 249, 248, 247, 2, 1, 0, 0, 0, 0, 253, 252, 251, 250, 249, 248, 3, 2, 1, 0, 0, 0, 254, 253, 252, 251, 250, 249, 4, 3, 2, 1, 0, 0, 255, 254, 253, 252, 251, 250, 5, 4, 3, 2, 1, 0})
                        }, { // input020_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder40
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1})
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
                        }, { // input120_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder41
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_21 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_21", get_test_model_all_inputs_as_internal_21());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_22() {
    static TestModel model = {
        .main = {
                .operands = {{ // input021
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input121
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 5, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 6, 5, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 251, 250, 249, 248, 247, 246, 1, 0, 0, 0, 0, 0, 252, 251, 250, 249, 248, 247, 2, 1, 0, 0, 0, 0, 253, 252, 251, 250, 249, 248, 3, 2, 1, 0, 0, 0, 254, 253, 252, 251, 250, 249, 4, 3, 2, 1, 0, 0, 255, 254, 253, 252, 251, 250, 5, 4, 3, 2, 1, 0, 255, 255, 254, 253, 252, 251, 6, 5, 4, 3, 2, 1})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_22 = TestModelManager::get().add("sub_quantized_different_scales_22", get_test_model_22());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_22() {
    static TestModel model = {
        .main = {
                .operands = {{ // input021
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input121
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 5, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 6, 5, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 251, 250, 249, 248, 247, 246, 1, 0, 0, 0, 0, 0, 252, 251, 250, 249, 248, 247, 2, 1, 0, 0, 0, 0, 253, 252, 251, 250, 249, 248, 3, 2, 1, 0, 0, 0, 254, 253, 252, 251, 250, 249, 4, 3, 2, 1, 0, 0, 255, 254, 253, 252, 251, 250, 5, 4, 3, 2, 1, 0, 255, 255, 254, 253, 252, 251, 6, 5, 4, 3, 2, 1})
                        }, { // input021_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder42
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1})
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
                        }, { // input121_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder43
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_22 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_22", get_test_model_all_inputs_as_internal_22());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_23() {
    static TestModel model = {
        .main = {
                .operands = {{ // input022
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input122
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 220, 120, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 220, 120, 20, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 220, 120, 20, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 220, 120, 20, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 220, 120, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 120, 20, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 220, 120, 20, 0, 0, 0, 255, 255, 255, 255, 255, 255, 255, 220, 120, 20, 0, 0, 255, 255, 255, 255, 255, 255, 255, 255, 220, 120, 20, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255, 220, 120, 20, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 220, 120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_23 = TestModelManager::get().add("sub_quantized_different_scales_23", get_test_model_23());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_23() {
    static TestModel model = {
        .main = {
                .operands = {{ // input022
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input122
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 220, 120, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 220, 120, 20, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 220, 120, 20, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 220, 120, 20, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 220, 120, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 120, 20, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 220, 120, 20, 0, 0, 0, 255, 255, 255, 255, 255, 255, 255, 220, 120, 20, 0, 0, 255, 255, 255, 255, 255, 255, 255, 255, 220, 120, 20, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255, 220, 120, 20, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 220, 120})
                        }, { // input022_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder44
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1})
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
                        }, { // input122_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder45
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_23 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_23", get_test_model_all_inputs_as_internal_23());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_24() {
    static TestModel model = {
        .main = {
                .operands = {{ // input023
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input123
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 94, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 96, 95, 95, 95, 95, 95, 145, 145, 145, 145, 145, 144, 120, 120, 120, 120, 120, 120, 145, 145, 145, 145, 145, 145, 120, 120, 120, 120, 120, 120, 145, 145, 145, 145, 145, 145, 120, 120, 120, 120, 120, 120, 145, 145, 145, 145, 145, 145, 120, 120, 120, 120, 120, 120, 145, 145, 145, 145, 145, 145, 120, 120, 120, 120, 120, 120, 146, 145, 145, 145, 145, 145, 120, 120, 120, 120, 120, 120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_24 = TestModelManager::get().add("sub_quantized_different_scales_24", get_test_model_24());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_24() {
    static TestModel model = {
        .main = {
                .operands = {{ // input023
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input123
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 94, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 96, 95, 95, 95, 95, 95, 145, 145, 145, 145, 145, 144, 120, 120, 120, 120, 120, 120, 145, 145, 145, 145, 145, 145, 120, 120, 120, 120, 120, 120, 145, 145, 145, 145, 145, 145, 120, 120, 120, 120, 120, 120, 145, 145, 145, 145, 145, 145, 120, 120, 120, 120, 120, 120, 145, 145, 145, 145, 145, 145, 120, 120, 120, 120, 120, 120, 146, 145, 145, 145, 145, 145, 120, 120, 120, 120, 120, 120})
                        }, { // input023_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder46
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1})
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
                        }, { // input123_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder47
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_24 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_24", get_test_model_all_inputs_as_internal_24());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_25() {
    static TestModel model = {
        .main = {
                .operands = {{ // input024
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input124
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 2, 2, 2, 2, 2, 2, 0, 0, 0, 0, 0, 0, 3, 3, 3, 3, 3, 3, 1, 1, 1, 1, 1, 1, 4, 4, 4, 4, 4, 4, 2, 2, 2, 2, 2, 2, 5, 5, 5, 5, 5, 5, 3, 3, 3, 3, 3, 3, 250, 250, 250, 250, 250, 250, 248, 248, 248, 248, 248, 248, 251, 251, 251, 251, 251, 251, 249, 249, 249, 249, 249, 249, 252, 252, 252, 252, 252, 252, 250, 250, 250, 250, 250, 250, 253, 253, 253, 253, 253, 253, 251, 251, 251, 251, 251, 251, 254, 254, 254, 254, 254, 254, 252, 252, 252, 252, 252, 252, 255, 255, 255, 255, 255, 255, 253, 253, 253, 253, 253, 253})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_25 = TestModelManager::get().add("sub_quantized_different_scales_25", get_test_model_25());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_25() {
    static TestModel model = {
        .main = {
                .operands = {{ // input024
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input124
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 2, 2, 2, 2, 2, 2, 0, 0, 0, 0, 0, 0, 3, 3, 3, 3, 3, 3, 1, 1, 1, 1, 1, 1, 4, 4, 4, 4, 4, 4, 2, 2, 2, 2, 2, 2, 5, 5, 5, 5, 5, 5, 3, 3, 3, 3, 3, 3, 250, 250, 250, 250, 250, 250, 248, 248, 248, 248, 248, 248, 251, 251, 251, 251, 251, 251, 249, 249, 249, 249, 249, 249, 252, 252, 252, 252, 252, 252, 250, 250, 250, 250, 250, 250, 253, 253, 253, 253, 253, 253, 251, 251, 251, 251, 251, 251, 254, 254, 254, 254, 254, 254, 252, 252, 252, 252, 252, 252, 255, 255, 255, 255, 255, 255, 253, 253, 253, 253, 253, 253})
                        }, { // input024_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder48
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1})
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
                        }, { // input124_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder49
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_25 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_25", get_test_model_all_inputs_as_internal_25());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_26() {
    static TestModel model = {
        .main = {
                .operands = {{ // input025
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input125
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 2, 2, 2, 2, 2, 2, 0, 0, 0, 0, 0, 0, 3, 3, 3, 3, 3, 3, 1, 1, 1, 1, 1, 1, 4, 4, 4, 4, 4, 4, 2, 2, 2, 2, 2, 2, 5, 5, 5, 5, 5, 5, 3, 3, 3, 3, 3, 3, 6, 6, 6, 6, 6, 6, 4, 4, 4, 4, 4, 4, 251, 251, 251, 251, 251, 251, 249, 249, 249, 249, 249, 249, 252, 252, 252, 252, 252, 252, 250, 250, 250, 250, 250, 250, 253, 253, 253, 253, 253, 253, 251, 251, 251, 251, 251, 251, 254, 254, 254, 254, 254, 254, 252, 252, 252, 252, 252, 252, 255, 255, 255, 255, 255, 255, 253, 253, 253, 253, 253, 253, 255, 255, 255, 255, 255, 255, 254, 254, 254, 254, 254, 254})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_26 = TestModelManager::get().add("sub_quantized_different_scales_26", get_test_model_26());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_26() {
    static TestModel model = {
        .main = {
                .operands = {{ // input025
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input125
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 2, 2, 2, 2, 2, 2, 0, 0, 0, 0, 0, 0, 3, 3, 3, 3, 3, 3, 1, 1, 1, 1, 1, 1, 4, 4, 4, 4, 4, 4, 2, 2, 2, 2, 2, 2, 5, 5, 5, 5, 5, 5, 3, 3, 3, 3, 3, 3, 6, 6, 6, 6, 6, 6, 4, 4, 4, 4, 4, 4, 251, 251, 251, 251, 251, 251, 249, 249, 249, 249, 249, 249, 252, 252, 252, 252, 252, 252, 250, 250, 250, 250, 250, 250, 253, 253, 253, 253, 253, 253, 251, 251, 251, 251, 251, 251, 254, 254, 254, 254, 254, 254, 252, 252, 252, 252, 252, 252, 255, 255, 255, 255, 255, 255, 253, 253, 253, 253, 253, 253, 255, 255, 255, 255, 255, 255, 254, 254, 254, 254, 254, 254})
                        }, { // input025_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder50
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1})
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
                        }, { // input125_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder51
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_26 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_26", get_test_model_all_inputs_as_internal_26());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_27() {
    static TestModel model = {
        .main = {
                .operands = {{ // input026
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input126
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({140, 139, 138, 137, 136, 135, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 90, 89, 88, 87, 86, 85, 255, 255, 255, 255, 255, 255, 190, 189, 188, 187, 186, 185, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_27 = TestModelManager::get().add("sub_quantized_different_scales_27", get_test_model_27());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_27() {
    static TestModel model = {
        .main = {
                .operands = {{ // input026
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input126
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({140, 139, 138, 137, 136, 135, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 90, 89, 88, 87, 86, 85, 255, 255, 255, 255, 255, 255, 190, 189, 188, 187, 186, 185, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input026_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder52
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1})
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
                        }, { // input126_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder53
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_27 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_27", get_test_model_all_inputs_as_internal_27());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_28() {
    static TestModel model = {
        .main = {
                .operands = {{ // input027
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input127
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 121, 121, 121, 121, 121, 121, 120, 120, 120, 120, 120, 120, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 146, 146, 146, 146, 146, 146, 145, 145, 145, 145, 145, 145})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_28 = TestModelManager::get().add("sub_quantized_different_scales_28", get_test_model_28());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_28() {
    static TestModel model = {
        .main = {
                .operands = {{ // input027
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input127
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 121, 121, 121, 121, 121, 121, 120, 120, 120, 120, 120, 120, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 145, 146, 146, 146, 146, 146, 146, 145, 145, 145, 145, 145, 145})
                        }, { // input027_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder54
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1})
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
                        }, { // input127_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder55
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_28 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_28", get_test_model_all_inputs_as_internal_28());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_29() {
    static TestModel model = {
        .main = {
                .operands = {{ // input028
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input128
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_29 = TestModelManager::get().add("sub_quantized_different_scales_29", get_test_model_29());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_29() {
    static TestModel model = {
        .main = {
                .operands = {{ // input028
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input128
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0})
                        }, { // input028_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder56
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1})
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
                        }, { // input128_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder57
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_29 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_29", get_test_model_all_inputs_as_internal_29());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_30() {
    static TestModel model = {
        .main = {
                .operands = {{ // input029
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input129
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_30 = TestModelManager::get().add("sub_quantized_different_scales_30", get_test_model_30());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_30() {
    static TestModel model = {
        .main = {
                .operands = {{ // input029
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input129
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0})
                        }, { // input029_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder58
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1})
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
                        }, { // input129_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder59
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_30 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_30", get_test_model_all_inputs_as_internal_30());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_31() {
    static TestModel model = {
        .main = {
                .operands = {{ // input030
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input130
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_31 = TestModelManager::get().add("sub_quantized_different_scales_31", get_test_model_31());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_31() {
    static TestModel model = {
        .main = {
                .operands = {{ // input030
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input130
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0})
                        }, { // input030_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder60
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1})
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
                        }, { // input130_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder61
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_31 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_31", get_test_model_all_inputs_as_internal_31());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_32() {
    static TestModel model = {
        .main = {
                .operands = {{ // input031
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input131
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 15, 14, 13, 12, 11, 10, 255, 255, 255, 255, 255, 255, 15, 14, 13, 12, 11, 10, 255, 255, 255, 255, 255, 255, 15, 14, 13, 12, 11, 10, 255, 255, 255, 255, 255, 255, 15, 14, 13, 12, 11, 10, 255, 255, 255, 255, 255, 255, 15, 14, 13, 12, 11, 10, 255, 255, 255, 255, 255, 255, 15, 14, 13, 12, 11, 10})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_32 = TestModelManager::get().add("sub_quantized_different_scales_32", get_test_model_32());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_32() {
    static TestModel model = {
        .main = {
                .operands = {{ // input031
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input131
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 15, 14, 13, 12, 11, 10, 255, 255, 255, 255, 255, 255, 15, 14, 13, 12, 11, 10, 255, 255, 255, 255, 255, 255, 15, 14, 13, 12, 11, 10, 255, 255, 255, 255, 255, 255, 15, 14, 13, 12, 11, 10, 255, 255, 255, 255, 255, 255, 15, 14, 13, 12, 11, 10, 255, 255, 255, 255, 255, 255, 15, 14, 13, 12, 11, 10})
                        }, { // input031_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder62
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1})
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
                        }, { // input131_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder63
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_32 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_32", get_test_model_all_inputs_as_internal_32());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_33() {
    static TestModel model = {
        .main = {
                .operands = {{ // input032
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input132
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_33 = TestModelManager::get().add("sub_quantized_different_scales_33", get_test_model_33());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_33() {
    static TestModel model = {
        .main = {
                .operands = {{ // input032
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input132
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0})
                        }, { // input032_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder64
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
                        }, { // input132_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder65
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_33 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_33", get_test_model_all_inputs_as_internal_33());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_34() {
    static TestModel model = {
        .main = {
                .operands = {{ // input033
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input133
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_34 = TestModelManager::get().add("sub_quantized_different_scales_34", get_test_model_34());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_34() {
    static TestModel model = {
        .main = {
                .operands = {{ // input033
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input133
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0})
                        }, { // input033_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder66
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
                        }, { // input133_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder67
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_34 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_34", get_test_model_all_inputs_as_internal_34());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_35() {
    static TestModel model = {
        .main = {
                .operands = {{ // input034
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input134
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 250, 150, 50, 0, 0, 0, 0, 0, 0, 0, 0, 0, 251, 151, 51, 0, 0, 0, 0, 0, 0, 0, 0, 0, 252, 152, 52, 0, 0, 0, 0, 0, 0, 0, 0, 0, 253, 153, 53, 0, 0, 0, 0, 0, 0, 0, 0, 0, 254, 154, 54, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 155, 55, 0, 0, 0, 0, 0, 0, 0, 0, 0})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_35 = TestModelManager::get().add("sub_quantized_different_scales_35", get_test_model_35());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_35() {
    static TestModel model = {
        .main = {
                .operands = {{ // input034
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input134
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 250, 150, 50, 0, 0, 0, 0, 0, 0, 0, 0, 0, 251, 151, 51, 0, 0, 0, 0, 0, 0, 0, 0, 0, 252, 152, 52, 0, 0, 0, 0, 0, 0, 0, 0, 0, 253, 153, 53, 0, 0, 0, 0, 0, 0, 0, 0, 0, 254, 154, 54, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 155, 55, 0, 0, 0, 0, 0, 0, 0, 0, 0})
                        }, { // input034_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder68
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
                        }, { // input134_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder69
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_35 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_35", get_test_model_all_inputs_as_internal_35());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_36() {
    static TestModel model = {
        .main = {
                .operands = {{ // input035
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input135
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 120, 120, 120, 119, 119, 95, 95, 95, 95, 94, 94, 120, 120, 120, 120, 119, 119, 95, 95, 95, 95, 94, 94, 120, 120, 120, 120, 119, 119, 95, 95, 95, 95, 94, 94, 120, 120, 120, 120, 119, 119, 95, 95, 95, 95, 94, 94, 120, 120, 120, 120, 119, 119, 95, 95, 95, 95, 94, 94, 120, 120, 120, 120, 119, 119, 95, 95, 95, 95, 94, 94, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_36 = TestModelManager::get().add("sub_quantized_different_scales_36", get_test_model_36());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_36() {
    static TestModel model = {
        .main = {
                .operands = {{ // input035
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input135
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 120, 120, 120, 119, 119, 95, 95, 95, 95, 94, 94, 120, 120, 120, 120, 119, 119, 95, 95, 95, 95, 94, 94, 120, 120, 120, 120, 119, 119, 95, 95, 95, 95, 94, 94, 120, 120, 120, 120, 119, 119, 95, 95, 95, 95, 94, 94, 120, 120, 120, 120, 119, 119, 95, 95, 95, 95, 94, 94, 120, 120, 120, 120, 119, 119, 95, 95, 95, 95, 94, 94, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95})
                        }, { // input035_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder70
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
                        }, { // input135_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder71
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_36 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_36", get_test_model_all_inputs_as_internal_36());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_37() {
    static TestModel model = {
        .main = {
                .operands = {{ // input036
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input136
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_37 = TestModelManager::get().add("sub_quantized_different_scales_37", get_test_model_37());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_37() {
    static TestModel model = {
        .main = {
                .operands = {{ // input036
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input136
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0})
                        }, { // input036_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder72
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
                        }, { // input136_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder73
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_37 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_37", get_test_model_all_inputs_as_internal_37());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_38() {
    static TestModel model = {
        .main = {
                .operands = {{ // input037
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input137
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_38 = TestModelManager::get().add("sub_quantized_different_scales_38", get_test_model_38());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_38() {
    static TestModel model = {
        .main = {
                .operands = {{ // input037
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input137
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0})
                        }, { // input037_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder74
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
                        }, { // input137_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder75
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_38 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_38", get_test_model_all_inputs_as_internal_38());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_39() {
    static TestModel model = {
        .main = {
                .operands = {{ // input038
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input138
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({100, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 101, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 102, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 103, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 104, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 105, 5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 250, 150, 50, 0, 0, 0, 0, 0, 0, 0, 0, 255, 251, 151, 51, 0, 0, 0, 0, 0, 0, 0, 0, 255, 252, 152, 52, 0, 0, 0, 0, 0, 0, 0, 0, 255, 253, 153, 53, 0, 0, 0, 0, 0, 0, 0, 0, 255, 254, 154, 54, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 155, 55, 0, 0, 0, 0, 0, 0, 0, 0})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_39 = TestModelManager::get().add("sub_quantized_different_scales_39", get_test_model_39());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_39() {
    static TestModel model = {
        .main = {
                .operands = {{ // input038
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input138
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({100, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 101, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 102, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 103, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 104, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 105, 5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 250, 150, 50, 0, 0, 0, 0, 0, 0, 0, 0, 255, 251, 151, 51, 0, 0, 0, 0, 0, 0, 0, 0, 255, 252, 152, 52, 0, 0, 0, 0, 0, 0, 0, 0, 255, 253, 153, 53, 0, 0, 0, 0, 0, 0, 0, 0, 255, 254, 154, 54, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 155, 55, 0, 0, 0, 0, 0, 0, 0, 0})
                        }, { // input038_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder76
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
                        }, { // input138_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder77
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_39 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_39", get_test_model_all_inputs_as_internal_39());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_40() {
    static TestModel model = {
        .main = {
                .operands = {{ // input039
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input139
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 120, 120, 120, 120, 119, 95, 95, 95, 95, 95, 94, 120, 120, 120, 120, 120, 119, 95, 95, 95, 95, 95, 94, 120, 120, 120, 120, 120, 119, 95, 95, 95, 95, 95, 94, 120, 120, 120, 120, 120, 119, 95, 95, 95, 95, 95, 94, 120, 120, 120, 120, 120, 119, 95, 95, 95, 95, 95, 94, 120, 120, 120, 120, 120, 119, 95, 95, 95, 95, 95, 94, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_40 = TestModelManager::get().add("sub_quantized_different_scales_40", get_test_model_40());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_40() {
    static TestModel model = {
        .main = {
                .operands = {{ // input039
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input139
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 120, 120, 120, 120, 119, 95, 95, 95, 95, 95, 94, 120, 120, 120, 120, 120, 119, 95, 95, 95, 95, 95, 94, 120, 120, 120, 120, 120, 119, 95, 95, 95, 95, 95, 94, 120, 120, 120, 120, 120, 119, 95, 95, 95, 95, 95, 94, 120, 120, 120, 120, 120, 119, 95, 95, 95, 95, 95, 94, 120, 120, 120, 120, 120, 119, 95, 95, 95, 95, 95, 94, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95, 120, 120, 120, 120, 120, 120, 95, 95, 95, 95, 95, 95})
                        }, { // input039_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder78
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
                        }, { // input139_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder79
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_40 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_40", get_test_model_all_inputs_as_internal_40());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_41() {
    static TestModel model = {
        .main = {
                .operands = {{ // input040
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input140
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 2, 2, 2, 2, 2, 0, 0, 0, 0, 0, 0, 3, 2, 2, 2, 2, 2, 0, 0, 0, 0, 0, 0, 3, 3, 2, 2, 2, 2, 0, 0, 0, 0, 0, 0, 3, 3, 3, 2, 2, 2, 0, 0, 0, 0, 0, 0, 3, 3, 3, 3, 2, 2, 0, 0, 0, 0, 0, 0, 3, 3, 3, 3, 3, 2, 0, 0, 0, 0, 0, 0})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_41 = TestModelManager::get().add("sub_quantized_different_scales_41", get_test_model_41());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_41() {
    static TestModel model = {
        .main = {
                .operands = {{ // input040
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input140
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 2, 2, 2, 2, 2, 0, 0, 0, 0, 0, 0, 3, 2, 2, 2, 2, 2, 0, 0, 0, 0, 0, 0, 3, 3, 2, 2, 2, 2, 0, 0, 0, 0, 0, 0, 3, 3, 3, 2, 2, 2, 0, 0, 0, 0, 0, 0, 3, 3, 3, 3, 2, 2, 0, 0, 0, 0, 0, 0, 3, 3, 3, 3, 3, 2, 0, 0, 0, 0, 0, 0})
                        }, { // input040_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder80
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
                        }, { // input140_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder81
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_41 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_41", get_test_model_all_inputs_as_internal_41());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_42() {
    static TestModel model = {
        .main = {
                .operands = {{ // input041
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input141
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 3, 3, 3, 3, 3, 3, 1, 1, 1, 1, 1, 1, 4, 3, 3, 3, 3, 3, 1, 1, 1, 1, 1, 1, 4, 4, 3, 3, 3, 3, 1, 1, 1, 1, 1, 1, 4, 4, 4, 3, 3, 3, 1, 1, 1, 1, 1, 1, 4, 4, 4, 4, 3, 3, 1, 1, 1, 1, 1, 1, 4, 4, 4, 4, 4, 3, 1, 1, 1, 1, 1, 1})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_42 = TestModelManager::get().add("sub_quantized_different_scales_42", get_test_model_42());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_42() {
    static TestModel model = {
        .main = {
                .operands = {{ // input041
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input141
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 3, 3, 3, 3, 3, 3, 1, 1, 1, 1, 1, 1, 4, 3, 3, 3, 3, 3, 1, 1, 1, 1, 1, 1, 4, 4, 3, 3, 3, 3, 1, 1, 1, 1, 1, 1, 4, 4, 4, 3, 3, 3, 1, 1, 1, 1, 1, 1, 4, 4, 4, 4, 3, 3, 1, 1, 1, 1, 1, 1, 4, 4, 4, 4, 4, 3, 1, 1, 1, 1, 1, 1})
                        }, { // input041_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder82
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
                        }, { // input141_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder83
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_42 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_42", get_test_model_all_inputs_as_internal_42());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_43() {
    static TestModel model = {
        .main = {
                .operands = {{ // input042
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input142
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 119, 118, 117, 116, 115, 0, 0, 0, 0, 0, 0, 121, 120, 119, 118, 117, 116, 0, 0, 0, 0, 0, 0, 122, 121, 120, 119, 118, 117, 0, 0, 0, 0, 0, 0, 123, 122, 121, 120, 119, 118, 0, 0, 0, 0, 0, 0, 124, 123, 122, 121, 120, 119, 0, 0, 0, 0, 0, 0, 125, 124, 123, 122, 121, 120, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 120, 119, 118, 117, 116, 115, 255, 255, 255, 255, 255, 255, 121, 120, 119, 118, 117, 116, 255, 255, 255, 255, 255, 255, 122, 121, 120, 119, 118, 117, 255, 255, 255, 255, 255, 255, 123, 122, 121, 120, 119, 118, 255, 255, 255, 255, 255, 255, 124, 123, 122, 121, 120, 119, 255, 255, 255, 255, 255, 255, 125, 124, 123, 122, 121, 120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_43 = TestModelManager::get().add("sub_quantized_different_scales_43", get_test_model_43());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_43() {
    static TestModel model = {
        .main = {
                .operands = {{ // input042
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input142
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 119, 118, 117, 116, 115, 0, 0, 0, 0, 0, 0, 121, 120, 119, 118, 117, 116, 0, 0, 0, 0, 0, 0, 122, 121, 120, 119, 118, 117, 0, 0, 0, 0, 0, 0, 123, 122, 121, 120, 119, 118, 0, 0, 0, 0, 0, 0, 124, 123, 122, 121, 120, 119, 0, 0, 0, 0, 0, 0, 125, 124, 123, 122, 121, 120, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 120, 119, 118, 117, 116, 115, 255, 255, 255, 255, 255, 255, 121, 120, 119, 118, 117, 116, 255, 255, 255, 255, 255, 255, 122, 121, 120, 119, 118, 117, 255, 255, 255, 255, 255, 255, 123, 122, 121, 120, 119, 118, 255, 255, 255, 255, 255, 255, 124, 123, 122, 121, 120, 119, 255, 255, 255, 255, 255, 255, 125, 124, 123, 122, 121, 120})
                        }, { // input042_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder84
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
                        }, { // input142_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder85
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_43 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_43", get_test_model_all_inputs_as_internal_43());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_44() {
    static TestModel model = {
        .main = {
                .operands = {{ // input043
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input143
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_44 = TestModelManager::get().add("sub_quantized_different_scales_44", get_test_model_44());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_44() {
    static TestModel model = {
        .main = {
                .operands = {{ // input043
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input143
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120})
                        }, { // input043_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder86
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
                        }, { // input143_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder87
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_44 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_44", get_test_model_all_inputs_as_internal_44());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_45() {
    static TestModel model = {
        .main = {
                .operands = {{ // input044
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input144
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_45 = TestModelManager::get().add("sub_quantized_different_scales_45", get_test_model_45());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_45() {
    static TestModel model = {
        .main = {
                .operands = {{ // input044
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input144
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0})
                        }, { // input044_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder88
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
                        }, { // input144_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder89
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_45 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_45", get_test_model_all_inputs_as_internal_45());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_46() {
    static TestModel model = {
        .main = {
                .operands = {{ // input045
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input145
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_46 = TestModelManager::get().add("sub_quantized_different_scales_46", get_test_model_46());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_46() {
    static TestModel model = {
        .main = {
                .operands = {{ // input045
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input145
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0})
                        }, { // input045_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder90
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
                        }, { // input145_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder91
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_46 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_46", get_test_model_all_inputs_as_internal_46());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_47() {
    static TestModel model = {
        .main = {
                .operands = {{ // input046
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input146
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_47 = TestModelManager::get().add("sub_quantized_different_scales_47", get_test_model_47());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_47() {
    static TestModel model = {
        .main = {
                .operands = {{ // input046
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input146
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0})
                        }, { // input046_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder92
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
                        }, { // input146_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder93
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_47 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_47", get_test_model_all_inputs_as_internal_47());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_48() {
    static TestModel model = {
        .main = {
                .operands = {{ // input047
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input147
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_48 = TestModelManager::get().add("sub_quantized_different_scales_48", get_test_model_48());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_48() {
    static TestModel model = {
        .main = {
                .operands = {{ // input047
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input147
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0, 240, 239, 238, 237, 236, 235, 0, 0, 0, 0, 0, 0})
                        }, { // input047_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder94
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
                        }, { // input147_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder95
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_48 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_48", get_test_model_all_inputs_as_internal_48());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_49() {
    static TestModel model = {
        .main = {
                .operands = {{ // input048
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input148
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_49 = TestModelManager::get().add("sub_quantized_different_scales_49", get_test_model_49());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_49() {
    static TestModel model = {
        .main = {
                .operands = {{ // input048
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input148
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input048_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder96
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
                        }, { // input148_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder97
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_49 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_49", get_test_model_all_inputs_as_internal_49());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_50() {
    static TestModel model = {
        .main = {
                .operands = {{ // input049
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input149
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_50 = TestModelManager::get().add("sub_quantized_different_scales_50", get_test_model_50());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_50() {
    static TestModel model = {
        .main = {
                .operands = {{ // input049
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input149
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input049_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder98
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
                        }, { // input149_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder99
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_50 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_50", get_test_model_all_inputs_as_internal_50());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_51() {
    static TestModel model = {
        .main = {
                .operands = {{ // input050
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input150
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_51 = TestModelManager::get().add("sub_quantized_different_scales_51", get_test_model_51());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_51() {
    static TestModel model = {
        .main = {
                .operands = {{ // input050
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input150
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input050_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder100
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
                        }, { // input150_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder101
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_51 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_51", get_test_model_all_inputs_as_internal_51());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_52() {
    static TestModel model = {
        .main = {
                .operands = {{ // input051
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input151
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 2, 2, 2, 2, 2, 2, 0, 0, 0, 0, 0, 0, 3, 3, 3, 3, 3, 2, 0, 0, 0, 0, 0, 0, 4, 4, 4, 4, 4, 4, 0, 0, 0, 0, 0, 0, 5, 5, 5, 5, 5, 4, 0, 0, 0, 0, 0, 0, 250, 250, 250, 250, 250, 250, 225, 225, 225, 225, 225, 224, 251, 251, 251, 251, 251, 250, 226, 226, 226, 226, 226, 226, 252, 252, 252, 252, 252, 252, 227, 227, 227, 227, 227, 226, 253, 253, 253, 253, 253, 252, 228, 228, 228, 228, 228, 228, 254, 254, 254, 254, 254, 254, 229, 229, 229, 229, 229, 228, 255, 255, 255, 255, 255, 254, 230, 230, 230, 230, 230, 230})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_52 = TestModelManager::get().add("sub_quantized_different_scales_52", get_test_model_52());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_52() {
    static TestModel model = {
        .main = {
                .operands = {{ // input051
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input151
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 2, 2, 2, 2, 2, 2, 0, 0, 0, 0, 0, 0, 3, 3, 3, 3, 3, 2, 0, 0, 0, 0, 0, 0, 4, 4, 4, 4, 4, 4, 0, 0, 0, 0, 0, 0, 5, 5, 5, 5, 5, 4, 0, 0, 0, 0, 0, 0, 250, 250, 250, 250, 250, 250, 225, 225, 225, 225, 225, 224, 251, 251, 251, 251, 251, 250, 226, 226, 226, 226, 226, 226, 252, 252, 252, 252, 252, 252, 227, 227, 227, 227, 227, 226, 253, 253, 253, 253, 253, 252, 228, 228, 228, 228, 228, 228, 254, 254, 254, 254, 254, 254, 229, 229, 229, 229, 229, 228, 255, 255, 255, 255, 255, 254, 230, 230, 230, 230, 230, 230})
                        }, { // input051_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder102
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
                        }, { // input151_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder103
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_52 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_52", get_test_model_all_inputs_as_internal_52());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_53() {
    static TestModel model = {
        .main = {
                .operands = {{ // input052
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input152
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_53 = TestModelManager::get().add("sub_quantized_different_scales_53", get_test_model_53());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_53() {
    static TestModel model = {
        .main = {
                .operands = {{ // input052
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input152
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input052_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder104
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
                        }, { // input152_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder105
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_53 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_53", get_test_model_all_inputs_as_internal_53());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_54() {
    static TestModel model = {
        .main = {
                .operands = {{ // input053
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input153
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_54 = TestModelManager::get().add("sub_quantized_different_scales_54", get_test_model_54());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_54() {
    static TestModel model = {
        .main = {
                .operands = {{ // input053
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input153
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input053_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder106
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
                        }, { // input153_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder107
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_54 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_54", get_test_model_all_inputs_as_internal_54());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_55() {
    static TestModel model = {
        .main = {
                .operands = {{ // input054
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input154
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_55 = TestModelManager::get().add("sub_quantized_different_scales_55", get_test_model_55());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_55() {
    static TestModel model = {
        .main = {
                .operands = {{ // input054
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input154
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input054_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder108
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
                        }, { // input154_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder109
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_55 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_55", get_test_model_all_inputs_as_internal_55());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_56() {
    static TestModel model = {
        .main = {
                .operands = {{ // input055
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input155
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 2, 2, 2, 2, 2, 2, 0, 0, 0, 0, 0, 0, 3, 3, 3, 3, 3, 3, 0, 0, 0, 0, 0, 0, 4, 4, 4, 4, 4, 4, 0, 0, 0, 0, 0, 0, 5, 5, 5, 5, 5, 5, 0, 0, 0, 0, 0, 0, 250, 250, 250, 250, 250, 250, 225, 225, 225, 225, 225, 225, 251, 251, 251, 251, 251, 251, 226, 226, 226, 226, 226, 226, 252, 252, 252, 252, 252, 252, 227, 227, 227, 227, 227, 227, 253, 253, 253, 253, 253, 253, 228, 228, 228, 228, 228, 228, 254, 254, 254, 254, 254, 254, 229, 229, 229, 229, 229, 229, 255, 255, 255, 255, 255, 255, 230, 230, 230, 230, 230, 230})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_56 = TestModelManager::get().add("sub_quantized_different_scales_56", get_test_model_56());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_56() {
    static TestModel model = {
        .main = {
                .operands = {{ // input055
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input155
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 2, 2, 2, 2, 2, 2, 0, 0, 0, 0, 0, 0, 3, 3, 3, 3, 3, 3, 0, 0, 0, 0, 0, 0, 4, 4, 4, 4, 4, 4, 0, 0, 0, 0, 0, 0, 5, 5, 5, 5, 5, 5, 0, 0, 0, 0, 0, 0, 250, 250, 250, 250, 250, 250, 225, 225, 225, 225, 225, 225, 251, 251, 251, 251, 251, 251, 226, 226, 226, 226, 226, 226, 252, 252, 252, 252, 252, 252, 227, 227, 227, 227, 227, 227, 253, 253, 253, 253, 253, 253, 228, 228, 228, 228, 228, 228, 254, 254, 254, 254, 254, 254, 229, 229, 229, 229, 229, 229, 255, 255, 255, 255, 255, 255, 230, 230, 230, 230, 230, 230})
                        }, { // input055_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder110
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
                        }, { // input155_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder111
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_56 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_56", get_test_model_all_inputs_as_internal_56());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_57() {
    static TestModel model = {
        .main = {
                .operands = {{ // input056
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input156
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_57 = TestModelManager::get().add("sub_quantized_different_scales_57", get_test_model_57());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_57() {
    static TestModel model = {
        .main = {
                .operands = {{ // input056
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input156
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input056_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder112
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
                        }, { // input156_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder113
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_57 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_57", get_test_model_all_inputs_as_internal_57());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_58() {
    static TestModel model = {
        .main = {
                .operands = {{ // input057
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input157
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_58 = TestModelManager::get().add("sub_quantized_different_scales_58", get_test_model_58());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_58() {
    static TestModel model = {
        .main = {
                .operands = {{ // input057
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input157
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input057_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder114
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
                        }, { // input157_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder115
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_58 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_58", get_test_model_all_inputs_as_internal_58());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_59() {
    static TestModel model = {
        .main = {
                .operands = {{ // input058
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input158
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_59 = TestModelManager::get().add("sub_quantized_different_scales_59", get_test_model_59());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_59() {
    static TestModel model = {
        .main = {
                .operands = {{ // input058
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input158
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input058_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder116
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
                        }, { // input158_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder117
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_59 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_59", get_test_model_all_inputs_as_internal_59());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_60() {
    static TestModel model = {
        .main = {
                .operands = {{ // input059
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input159
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_60 = TestModelManager::get().add("sub_quantized_different_scales_60", get_test_model_60());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_60() {
    static TestModel model = {
        .main = {
                .operands = {{ // input059
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input159
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input059_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder118
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
                        }, { // input159_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder119
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_60 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_60", get_test_model_all_inputs_as_internal_60());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_61() {
    static TestModel model = {
        .main = {
                .operands = {{ // input060
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input160
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 20, 10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 30, 20, 10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 40, 30, 20, 10, 0, 0, 0, 0, 0, 0, 0, 0, 50, 40, 30, 20, 10, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 10, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 20, 10, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 30, 20, 10, 0, 0, 0, 255, 255, 255, 255, 255, 255, 40, 30, 20, 10, 0, 0, 255, 255, 255, 255, 255, 255, 50, 40, 30, 20, 10, 0})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_61 = TestModelManager::get().add("sub_quantized_different_scales_61", get_test_model_61());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_61() {
    static TestModel model = {
        .main = {
                .operands = {{ // input060
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input160
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 20, 10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 30, 20, 10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 40, 30, 20, 10, 0, 0, 0, 0, 0, 0, 0, 0, 50, 40, 30, 20, 10, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 10, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 20, 10, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 30, 20, 10, 0, 0, 0, 255, 255, 255, 255, 255, 255, 40, 30, 20, 10, 0, 0, 255, 255, 255, 255, 255, 255, 50, 40, 30, 20, 10, 0})
                        }, { // input060_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder120
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
                        }, { // input160_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder121
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_61 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_61", get_test_model_all_inputs_as_internal_61());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_62() {
    static TestModel model = {
        .main = {
                .operands = {{ // input061
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input161
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 11, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 21, 11, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 31, 21, 11, 1, 0, 0, 0, 0, 0, 0, 0, 0, 41, 31, 21, 11, 1, 0, 0, 0, 0, 0, 0, 0, 51, 41, 31, 21, 11, 1, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 1, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 11, 1, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 21, 11, 1, 0, 0, 0, 255, 255, 255, 255, 255, 255, 31, 21, 11, 1, 0, 0, 255, 255, 255, 255, 255, 255, 41, 31, 21, 11, 1, 0, 255, 255, 255, 255, 255, 255, 51, 41, 31, 21, 11, 1})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_62 = TestModelManager::get().add("sub_quantized_different_scales_62", get_test_model_62());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_62() {
    static TestModel model = {
        .main = {
                .operands = {{ // input061
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input161
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 1,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 11, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 21, 11, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 31, 21, 11, 1, 0, 0, 0, 0, 0, 0, 0, 0, 41, 31, 21, 11, 1, 0, 0, 0, 0, 0, 0, 0, 51, 41, 31, 21, 11, 1, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 1, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 11, 1, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 21, 11, 1, 0, 0, 0, 255, 255, 255, 255, 255, 255, 31, 21, 11, 1, 0, 0, 255, 255, 255, 255, 255, 255, 41, 31, 21, 11, 1, 0, 255, 255, 255, 255, 255, 255, 51, 41, 31, 21, 11, 1})
                        }, { // input061_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder122
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
                        }, { // input161_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder123
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_62 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_62", get_test_model_all_inputs_as_internal_62());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_63() {
    static TestModel model = {
        .main = {
                .operands = {{ // input062
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input162
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 120, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 120, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 120, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 120, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 120, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 120, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 255, 120, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 255, 255, 120, 0, 0, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255, 120, 0, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 120, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_63 = TestModelManager::get().add("sub_quantized_different_scales_63", get_test_model_63());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_63() {
    static TestModel model = {
        .main = {
                .operands = {{ // input062
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input162
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 120, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 120, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 120, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 120, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 120, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 120, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 255, 120, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 255, 255, 120, 0, 0, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255, 120, 0, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 120, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 120})
                        }, { // input062_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder124
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
                        }, { // input162_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder125
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_63 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_63", get_test_model_all_inputs_as_internal_63());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_64() {
    static TestModel model = {
        .main = {
                .operands = {{ // input063
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // input163
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 119, 118, 117, 116, 115, 0, 0, 0, 0, 0, 0, 121, 120, 119, 118, 117, 116, 0, 0, 0, 0, 0, 0, 122, 121, 120, 119, 118, 117, 0, 0, 0, 0, 0, 0, 123, 122, 121, 120, 119, 118, 0, 0, 0, 0, 0, 0, 124, 123, 122, 121, 120, 119, 0, 0, 0, 0, 0, 0, 125, 124, 123, 122, 121, 120, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 120, 119, 118, 117, 116, 115, 255, 255, 255, 255, 255, 255, 121, 120, 119, 118, 117, 116, 255, 255, 255, 255, 255, 255, 122, 121, 120, 119, 118, 117, 255, 255, 255, 255, 255, 255, 123, 122, 121, 120, 119, 118, 255, 255, 255, 255, 255, 255, 124, 123, 122, 121, 120, 119, 255, 255, 255, 255, 255, 255, 125, 124, 123, 122, 121, 120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_64 = TestModelManager::get().add("sub_quantized_different_scales_64", get_test_model_64());

}  // namespace generated_tests::sub_quantized_different_scales

namespace generated_tests::sub_quantized_different_scales {

const TestModel& get_test_model_all_inputs_as_internal_64() {
    static TestModel model = {
        .main = {
                .operands = {{ // input063
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // input163
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 0,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 119, 118, 117, 116, 115, 0, 0, 0, 0, 0, 0, 121, 120, 119, 118, 117, 116, 0, 0, 0, 0, 0, 0, 122, 121, 120, 119, 118, 117, 0, 0, 0, 0, 0, 0, 123, 122, 121, 120, 119, 118, 0, 0, 0, 0, 0, 0, 124, 123, 122, 121, 120, 119, 0, 0, 0, 0, 0, 0, 125, 124, 123, 122, 121, 120, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255, 255, 120, 119, 118, 117, 116, 115, 255, 255, 255, 255, 255, 255, 121, 120, 119, 118, 117, 116, 255, 255, 255, 255, 255, 255, 122, 121, 120, 119, 118, 117, 255, 255, 255, 255, 255, 255, 123, 122, 121, 120, 119, 118, 255, 255, 255, 255, 255, 255, 124, 123, 122, 121, 120, 119, 255, 255, 255, 255, 255, 255, 125, 124, 123, 122, 121, 120})
                        }, { // input063_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder126
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
                        }, { // input163_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {144},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 250, 251, 252, 253, 254, 255})
                        }, { // placeholder127
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 10.0f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_64 = TestModelManager::get().add("sub_quantized_different_scales_all_inputs_as_internal_64", get_test_model_all_inputs_as_internal_64());

}  // namespace generated_tests::sub_quantized_different_scales

