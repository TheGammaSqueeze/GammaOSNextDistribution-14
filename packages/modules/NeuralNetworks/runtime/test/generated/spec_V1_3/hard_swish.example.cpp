// Generated from hard_swish.mod.py
// DO NOT EDIT
// clang-format off
#include "TestHarness.h"
using namespace test_helper;  // NOLINT(google-build-using-namespace)

namespace generated_tests::hard_swish {

const TestModel& get_test_model_simple() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {40},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({4.53125f, 3.90625f, 3.046875f, -8.59375f, -1.328125f, 1.328125f, 0.0f, -8.515625f, -8.984375f, -0.234375f, 0.859375f, 9.84375f, -0.15625f, -8.515625f, 8.671875f, 4.609375f, 9.21875f, -1.796875f, 1.171875f, 9.375f, -8.75f, 2.421875f, -8.125f, -1.09375f, -9.609375f, -1.015625f, -9.84375f, 2.578125f, 4.921875f, -5.078125f, 5.0f, -0.859375f, 1.953125f, -6.640625f, -7.8125f, 4.453125f, -4.453125f, -6.875f, 0.78125f, 0.859375f})
                        }, { // output0
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {40},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({4.53125f, 3.90625f, 3.046875f, 0.0f, -0.3700765f, 0.9580485f, 0.0f, 0.0f, 0.0f, -0.1080322f, 0.5527751f, 9.84375f, -0.074056f, 0.0f, 8.671875f, 4.609375f, 9.21875f, -0.3603109f, 0.8148193f, 9.375f, 0.0f, 2.1885173f, 0.0f, -0.3474935f, 0.0f, -0.3358968f, 0.0f, 2.3968506f, 4.921875f, 0.0f, 5.0f, -0.3065999f, 1.6123454f, 0.0f, 0.0f, 4.453125f, 0.0f, 0.0f, 0.4923503f, 0.5527751f})
                        }},
                .operations = {{
                            .type = TestOperationType::HARD_SWISH,
                            .inputs = {0},
                            .outputs = {1}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {1}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_simple = TestModelManager::get().add("hard_swish_simple", get_test_model_simple());

}  // namespace generated_tests::hard_swish

namespace generated_tests::hard_swish {

const TestModel& get_test_model_simple_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {40},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // output0
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {40},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({4.53125f, 3.90625f, 3.046875f, 0.0f, -0.3700765f, 0.9580485f, 0.0f, 0.0f, 0.0f, -0.1080322f, 0.5527751f, 9.84375f, -0.074056f, 0.0f, 8.671875f, 4.609375f, 9.21875f, -0.3603109f, 0.8148193f, 9.375f, 0.0f, 2.1885173f, 0.0f, -0.3474935f, 0.0f, -0.3358968f, 0.0f, 2.3968506f, 4.921875f, 0.0f, 5.0f, -0.3065999f, 1.6123454f, 0.0f, 0.0f, 4.453125f, 0.0f, 0.0f, 0.4923503f, 0.5527751f})
                        }, { // input0_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {40},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({4.53125f, 3.90625f, 3.046875f, -8.59375f, -1.328125f, 1.328125f, 0.0f, -8.515625f, -8.984375f, -0.234375f, 0.859375f, 9.84375f, -0.15625f, -8.515625f, 8.671875f, 4.609375f, 9.21875f, -1.796875f, 1.171875f, 9.375f, -8.75f, 2.421875f, -8.125f, -1.09375f, -9.609375f, -1.015625f, -9.84375f, 2.578125f, 4.921875f, -5.078125f, 5.0f, -0.859375f, 1.953125f, -6.640625f, -7.8125f, 4.453125f, -4.453125f, -6.875f, 0.78125f, 0.859375f})
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
                            .inputs = {2, 3, 4},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::HARD_SWISH,
                            .inputs = {0},
                            .outputs = {1}
                        }},
                .inputIndexes = {2},
                .outputIndexes = {1}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_simple_all_inputs_as_internal = TestModelManager::get().add("hard_swish_simple_all_inputs_as_internal", get_test_model_simple_all_inputs_as_internal());

}  // namespace generated_tests::hard_swish

namespace generated_tests::hard_swish {

const TestModel& get_test_model_simple_float16() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {40},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({4.53125f, 3.90625f, 3.046875f, -8.59375f, -1.328125f, 1.328125f, 0.0f, -8.515625f, -8.984375f, -0.234375f, 0.859375f, 9.84375f, -0.15625f, -8.515625f, 8.671875f, 4.609375f, 9.21875f, -1.796875f, 1.171875f, 9.375f, -8.75f, 2.421875f, -8.125f, -1.09375f, -9.609375f, -1.015625f, -9.84375f, 2.578125f, 4.921875f, -5.078125f, 5.0f, -0.859375f, 1.953125f, -6.640625f, -7.8125f, 4.453125f, -4.453125f, -6.875f, 0.78125f, 0.859375f})
                        }, { // output0
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {40},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({4.53125f, 3.90625f, 3.046875f, 0.0f, -0.3700765073299408f, 0.9580485224723816f, 0.0f, 0.0f, 0.0f, -0.10803219676017761f, 0.5527750849723816f, 9.84375f, -0.0740559995174408f, 0.0f, 8.671875f, 4.609375f, 9.21875f, -0.3603109121322632f, 0.8148192763328552f, 9.375f, 0.0f, 2.1885173320770264f, 0.0f, -0.3474934995174408f, 0.0f, -0.3358967900276184f, 0.0f, 2.3968505859375f, 4.921875f, 0.0f, 5.0f, -0.306599885225296f, 1.6123454570770264f, 0.0f, 0.0f, 4.453125f, 0.0f, 0.0f, 0.492350310087204f, 0.5527750849723816f})
                        }},
                .operations = {{
                            .type = TestOperationType::HARD_SWISH,
                            .inputs = {0},
                            .outputs = {1}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {1}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_simple_float16 = TestModelManager::get().add("hard_swish_simple_float16", get_test_model_simple_float16());

}  // namespace generated_tests::hard_swish

namespace generated_tests::hard_swish {

const TestModel& get_test_model_simple_float16_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {40},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // output0
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {40},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({4.53125f, 3.90625f, 3.046875f, 0.0f, -0.3700765073299408f, 0.9580485224723816f, 0.0f, 0.0f, 0.0f, -0.10803219676017761f, 0.5527750849723816f, 9.84375f, -0.0740559995174408f, 0.0f, 8.671875f, 4.609375f, 9.21875f, -0.3603109121322632f, 0.8148192763328552f, 9.375f, 0.0f, 2.1885173320770264f, 0.0f, -0.3474934995174408f, 0.0f, -0.3358967900276184f, 0.0f, 2.3968505859375f, 4.921875f, 0.0f, 5.0f, -0.306599885225296f, 1.6123454570770264f, 0.0f, 0.0f, 4.453125f, 0.0f, 0.0f, 0.492350310087204f, 0.5527750849723816f})
                        }, { // input0_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {40},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({4.53125f, 3.90625f, 3.046875f, -8.59375f, -1.328125f, 1.328125f, 0.0f, -8.515625f, -8.984375f, -0.234375f, 0.859375f, 9.84375f, -0.15625f, -8.515625f, 8.671875f, 4.609375f, 9.21875f, -1.796875f, 1.171875f, 9.375f, -8.75f, 2.421875f, -8.125f, -1.09375f, -9.609375f, -1.015625f, -9.84375f, 2.578125f, 4.921875f, -5.078125f, 5.0f, -0.859375f, 1.953125f, -6.640625f, -7.8125f, 4.453125f, -4.453125f, -6.875f, 0.78125f, 0.859375f})
                        }, { // placeholder1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
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
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {2, 3, 4},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::HARD_SWISH,
                            .inputs = {0},
                            .outputs = {1}
                        }},
                .inputIndexes = {2},
                .outputIndexes = {1}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_simple_float16_all_inputs_as_internal = TestModelManager::get().add("hard_swish_simple_float16_all_inputs_as_internal", get_test_model_simple_float16_all_inputs_as_internal());

}  // namespace generated_tests::hard_swish

namespace generated_tests::hard_swish {

const TestModel& get_test_model_simple_relaxed() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {40},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({4.53125f, 3.90625f, 3.046875f, -8.59375f, -1.328125f, 1.328125f, 0.0f, -8.515625f, -8.984375f, -0.234375f, 0.859375f, 9.84375f, -0.15625f, -8.515625f, 8.671875f, 4.609375f, 9.21875f, -1.796875f, 1.171875f, 9.375f, -8.75f, 2.421875f, -8.125f, -1.09375f, -9.609375f, -1.015625f, -9.84375f, 2.578125f, 4.921875f, -5.078125f, 5.0f, -0.859375f, 1.953125f, -6.640625f, -7.8125f, 4.453125f, -4.453125f, -6.875f, 0.78125f, 0.859375f})
                        }, { // output0
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {40},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({4.53125f, 3.90625f, 3.046875f, 0.0f, -0.3700765f, 0.9580485f, 0.0f, 0.0f, 0.0f, -0.1080322f, 0.5527751f, 9.84375f, -0.074056f, 0.0f, 8.671875f, 4.609375f, 9.21875f, -0.3603109f, 0.8148193f, 9.375f, 0.0f, 2.1885173f, 0.0f, -0.3474935f, 0.0f, -0.3358968f, 0.0f, 2.3968506f, 4.921875f, 0.0f, 5.0f, -0.3065999f, 1.6123454f, 0.0f, 0.0f, 4.453125f, 0.0f, 0.0f, 0.4923503f, 0.5527751f})
                        }},
                .operations = {{
                            .type = TestOperationType::HARD_SWISH,
                            .inputs = {0},
                            .outputs = {1}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {1}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_simple_relaxed = TestModelManager::get().add("hard_swish_simple_relaxed", get_test_model_simple_relaxed());

}  // namespace generated_tests::hard_swish

namespace generated_tests::hard_swish {

const TestModel& get_test_model_simple_relaxed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {40},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // output0
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {40},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({4.53125f, 3.90625f, 3.046875f, 0.0f, -0.3700765f, 0.9580485f, 0.0f, 0.0f, 0.0f, -0.1080322f, 0.5527751f, 9.84375f, -0.074056f, 0.0f, 8.671875f, 4.609375f, 9.21875f, -0.3603109f, 0.8148193f, 9.375f, 0.0f, 2.1885173f, 0.0f, -0.3474935f, 0.0f, -0.3358968f, 0.0f, 2.3968506f, 4.921875f, 0.0f, 5.0f, -0.3065999f, 1.6123454f, 0.0f, 0.0f, 4.453125f, 0.0f, 0.0f, 0.4923503f, 0.5527751f})
                        }, { // input0_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {40},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({4.53125f, 3.90625f, 3.046875f, -8.59375f, -1.328125f, 1.328125f, 0.0f, -8.515625f, -8.984375f, -0.234375f, 0.859375f, 9.84375f, -0.15625f, -8.515625f, 8.671875f, 4.609375f, 9.21875f, -1.796875f, 1.171875f, 9.375f, -8.75f, 2.421875f, -8.125f, -1.09375f, -9.609375f, -1.015625f, -9.84375f, 2.578125f, 4.921875f, -5.078125f, 5.0f, -0.859375f, 1.953125f, -6.640625f, -7.8125f, 4.453125f, -4.453125f, -6.875f, 0.78125f, 0.859375f})
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
                            .type = TestOperationType::ADD,
                            .inputs = {2, 3, 4},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::HARD_SWISH,
                            .inputs = {0},
                            .outputs = {1}
                        }},
                .inputIndexes = {2},
                .outputIndexes = {1}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_simple_relaxed_all_inputs_as_internal = TestModelManager::get().add("hard_swish_simple_relaxed_all_inputs_as_internal", get_test_model_simple_relaxed_all_inputs_as_internal());

}  // namespace generated_tests::hard_swish

namespace generated_tests::hard_swish {

const TestModel& get_test_model_simple_quant8() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {40},
                            .numberOfConsumers = 1,
                            .scale = 0.078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({186, 178, 167, 18, 111, 145, 128, 19, 13, 125, 139, 254, 126, 19, 239, 187, 246, 105, 143, 248, 16, 159, 24, 114, 5, 115, 2, 161, 191, 63, 192, 117, 153, 43, 28, 185, 71, 40, 138, 139})
                        }, { // output0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {40},
                            .numberOfConsumers = 0,
                            .scale = 0.078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({186, 178, 167, 128, 123, 140, 128, 128, 128, 127, 135, 254, 127, 128, 239, 187, 246, 123, 138, 248, 128, 156, 128, 124, 128, 124, 128, 159, 191, 128, 192, 124, 149, 128, 128, 185, 128, 128, 134, 135})
                        }},
                .operations = {{
                            .type = TestOperationType::HARD_SWISH,
                            .inputs = {0},
                            .outputs = {1}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {1}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_simple_quant8 = TestModelManager::get().add("hard_swish_simple_quant8", get_test_model_simple_quant8());

}  // namespace generated_tests::hard_swish

namespace generated_tests::hard_swish {

const TestModel& get_test_model_simple_quant8_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {40},
                            .numberOfConsumers = 1,
                            .scale = 0.078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // output0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {40},
                            .numberOfConsumers = 0,
                            .scale = 0.078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({186, 178, 167, 128, 123, 140, 128, 128, 128, 127, 135, 254, 127, 128, 239, 187, 246, 123, 138, 248, 128, 156, 128, 124, 128, 124, 128, 159, 191, 128, 192, 124, 149, 128, 128, 185, 128, 128, 134, 135})
                        }, { // input0_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {40},
                            .numberOfConsumers = 1,
                            .scale = 0.078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({186, 178, 167, 18, 111, 145, 128, 19, 13, 125, 139, 254, 126, 19, 239, 187, 246, 105, 143, 248, 16, 159, 24, 114, 5, 115, 2, 161, 191, 63, 192, 117, 153, 43, 28, 185, 71, 40, 138, 139})
                        }, { // placeholder3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
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
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {2, 3, 4},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::HARD_SWISH,
                            .inputs = {0},
                            .outputs = {1}
                        }},
                .inputIndexes = {2},
                .outputIndexes = {1}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_simple_quant8_all_inputs_as_internal = TestModelManager::get().add("hard_swish_simple_quant8_all_inputs_as_internal", get_test_model_simple_quant8_all_inputs_as_internal());

}  // namespace generated_tests::hard_swish

namespace generated_tests::hard_swish {

const TestModel& get_test_model_simple_quant8_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {40},
                            .numberOfConsumers = 1,
                            .scale = 0.078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({58, 50, 39, -110, -17, 17, 0, -109, -115, -3, 11, 126, -2, -109, 111, 59, 118, -23, 15, 120, -112, 31, -104, -14, -123, -13, -126, 33, 63, -65, 64, -11, 25, -85, -100, 57, -57, -88, 10, 11})
                        }, { // output0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {40},
                            .numberOfConsumers = 0,
                            .scale = 0.078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({58, 50, 39, 0, -5, 12, 0, 0, 0, -1, 7, 126, -1, 0, 111, 59, 118, -5, 10, 120, 0, 28, 0, -4, 0, -4, 0, 31, 63, 0, 64, -4, 21, 0, 0, 57, 0, 0, 6, 7})
                        }},
                .operations = {{
                            .type = TestOperationType::HARD_SWISH,
                            .inputs = {0},
                            .outputs = {1}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {1}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_simple_quant8_signed = TestModelManager::get().add("hard_swish_simple_quant8_signed", get_test_model_simple_quant8_signed());

}  // namespace generated_tests::hard_swish

namespace generated_tests::hard_swish {

const TestModel& get_test_model_simple_quant8_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {40},
                            .numberOfConsumers = 1,
                            .scale = 0.078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // output0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {40},
                            .numberOfConsumers = 0,
                            .scale = 0.078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({58, 50, 39, 0, -5, 12, 0, 0, 0, -1, 7, 126, -1, 0, 111, 59, 118, -5, 10, 120, 0, 28, 0, -4, 0, -4, 0, 31, 63, 0, 64, -4, 21, 0, 0, 57, 0, 0, 6, 7})
                        }, { // input0_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {40},
                            .numberOfConsumers = 1,
                            .scale = 0.078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({58, 50, 39, -110, -17, 17, 0, -109, -115, -3, 11, 126, -2, -109, 111, 59, 118, -23, 15, 120, -112, 31, -104, -14, -123, -13, -126, 33, 63, -65, 64, -11, 25, -85, -100, 57, -57, -88, 10, 11})
                        }, { // placeholder4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0})
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
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {2, 3, 4},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::HARD_SWISH,
                            .inputs = {0},
                            .outputs = {1}
                        }},
                .inputIndexes = {2},
                .outputIndexes = {1}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_simple_quant8_signed_all_inputs_as_internal = TestModelManager::get().add("hard_swish_simple_quant8_signed_all_inputs_as_internal", get_test_model_simple_quant8_signed_all_inputs_as_internal());

}  // namespace generated_tests::hard_swish

namespace generated_tests::hard_swish {

const TestModel& get_test_model_simple_quant8_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {40},
                            .numberOfConsumers = 1,
                            .scale = 0.078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({186, 178, 167, 18, 111, 145, 128, 19, 13, 125, 139, 254, 126, 19, 239, 187, 246, 105, 143, 248, 16, 159, 24, 114, 5, 115, 2, 161, 191, 63, 192, 117, 153, 43, 28, 185, 71, 40, 138, 139})
                        }, { // output0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {40},
                            .numberOfConsumers = 0,
                            .scale = 0.03125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({145, 125, 98, 0, 0, 31, 0, 0, 0, 0, 18, 255, 0, 0, 255, 148, 255, 0, 26, 255, 0, 70, 0, 0, 0, 0, 0, 77, 158, 0, 160, 0, 52, 0, 0, 142, 0, 0, 16, 18})
                        }},
                .operations = {{
                            .type = TestOperationType::HARD_SWISH,
                            .inputs = {0},
                            .outputs = {1}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {1}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_simple_quant8_2 = TestModelManager::get().add("hard_swish_simple_quant8_2", get_test_model_simple_quant8_2());

}  // namespace generated_tests::hard_swish

namespace generated_tests::hard_swish {

const TestModel& get_test_model_simple_quant8_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {40},
                            .numberOfConsumers = 1,
                            .scale = 0.078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // output0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {40},
                            .numberOfConsumers = 0,
                            .scale = 0.03125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({145, 125, 98, 0, 0, 31, 0, 0, 0, 0, 18, 255, 0, 0, 255, 148, 255, 0, 26, 255, 0, 70, 0, 0, 0, 0, 0, 77, 158, 0, 160, 0, 52, 0, 0, 142, 0, 0, 16, 18})
                        }, { // input0_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {40},
                            .numberOfConsumers = 1,
                            .scale = 0.078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({186, 178, 167, 18, 111, 145, 128, 19, 13, 125, 139, 254, 126, 19, 239, 187, 246, 105, 143, 248, 16, 159, 24, 114, 5, 115, 2, 161, 191, 63, 192, 117, 153, 43, 28, 185, 71, 40, 138, 139})
                        }, { // placeholder5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
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
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {2, 3, 4},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::HARD_SWISH,
                            .inputs = {0},
                            .outputs = {1}
                        }},
                .inputIndexes = {2},
                .outputIndexes = {1}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_simple_quant8_all_inputs_as_internal_2 = TestModelManager::get().add("hard_swish_simple_quant8_all_inputs_as_internal_2", get_test_model_simple_quant8_all_inputs_as_internal_2());

}  // namespace generated_tests::hard_swish

namespace generated_tests::hard_swish {

const TestModel& get_test_model_simple_quant8_signed_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {40},
                            .numberOfConsumers = 1,
                            .scale = 0.078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({58, 50, 39, -110, -17, 17, 0, -109, -115, -3, 11, 126, -2, -109, 111, 59, 118, -23, 15, 120, -112, 31, -104, -14, -123, -13, -126, 33, 63, -65, 64, -11, 25, -85, -100, 57, -57, -88, 10, 11})
                        }, { // output0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {40},
                            .numberOfConsumers = 0,
                            .scale = 0.03125f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({17, -3, -30, -128, -128, -97, -128, -128, -128, -128, -110, 127, -128, -128, 127, 20, 127, -128, -102, 127, -128, -58, -128, -128, -128, -128, -128, -51, 30, -128, 32, -128, -76, -128, -128, 14, -128, -128, -112, -110})
                        }},
                .operations = {{
                            .type = TestOperationType::HARD_SWISH,
                            .inputs = {0},
                            .outputs = {1}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {1}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_simple_quant8_signed_2 = TestModelManager::get().add("hard_swish_simple_quant8_signed_2", get_test_model_simple_quant8_signed_2());

}  // namespace generated_tests::hard_swish

namespace generated_tests::hard_swish {

const TestModel& get_test_model_simple_quant8_signed_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {40},
                            .numberOfConsumers = 1,
                            .scale = 0.078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // output0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {40},
                            .numberOfConsumers = 0,
                            .scale = 0.03125f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({17, -3, -30, -128, -128, -97, -128, -128, -128, -128, -110, 127, -128, -128, 127, 20, 127, -128, -102, 127, -128, -58, -128, -128, -128, -128, -128, -51, 30, -128, 32, -128, -76, -128, -128, 14, -128, -128, -112, -110})
                        }, { // input0_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {40},
                            .numberOfConsumers = 1,
                            .scale = 0.078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({58, 50, 39, -110, -17, 17, 0, -109, -115, -3, 11, 126, -2, -109, 111, 59, 118, -23, 15, 120, -112, 31, -104, -14, -123, -13, -126, 33, 63, -65, 64, -11, 25, -85, -100, 57, -57, -88, 10, 11})
                        }, { // placeholder6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0})
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
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {2, 3, 4},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::HARD_SWISH,
                            .inputs = {0},
                            .outputs = {1}
                        }},
                .inputIndexes = {2},
                .outputIndexes = {1}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_simple_quant8_signed_all_inputs_as_internal_2 = TestModelManager::get().add("hard_swish_simple_quant8_signed_all_inputs_as_internal_2", get_test_model_simple_quant8_signed_all_inputs_as_internal_2());

}  // namespace generated_tests::hard_swish

namespace generated_tests::hard_swish {

const TestModel& get_test_model_5d_input() {
    static TestModel model = {
        .main = {
                .operands = {{ // input01
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 2, 5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({4.53125f, 3.90625f, 3.046875f, -8.59375f, -1.328125f, 1.328125f, 0.0f, -8.515625f, -8.984375f, -0.234375f, 0.859375f, 9.84375f, -0.15625f, -8.515625f, 8.671875f, 4.609375f, 9.21875f, -1.796875f, 1.171875f, 9.375f, -8.75f, 2.421875f, -8.125f, -1.09375f, -9.609375f, -1.015625f, -9.84375f, 2.578125f, 4.921875f, -5.078125f, 5.0f, -0.859375f, 1.953125f, -6.640625f, -7.8125f, 4.453125f, -4.453125f, -6.875f, 0.78125f, 0.859375f})
                        }, { // output01
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 2, 5},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({4.53125f, 3.90625f, 3.046875f, 0.0f, -0.3700765f, 0.9580485f, 0.0f, 0.0f, 0.0f, -0.1080322f, 0.5527751f, 9.84375f, -0.074056f, 0.0f, 8.671875f, 4.609375f, 9.21875f, -0.3603109f, 0.8148193f, 9.375f, 0.0f, 2.1885173f, 0.0f, -0.3474935f, 0.0f, -0.3358968f, 0.0f, 2.3968506f, 4.921875f, 0.0f, 5.0f, -0.3065999f, 1.6123454f, 0.0f, 0.0f, 4.453125f, 0.0f, 0.0f, 0.4923503f, 0.5527751f})
                        }},
                .operations = {{
                            .type = TestOperationType::HARD_SWISH,
                            .inputs = {0},
                            .outputs = {1}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {1}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_5d_input = TestModelManager::get().add("hard_swish_5d_input", get_test_model_5d_input());

}  // namespace generated_tests::hard_swish

namespace generated_tests::hard_swish {

const TestModel& get_test_model_5d_input_float16() {
    static TestModel model = {
        .main = {
                .operands = {{ // input01
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 2, 2, 2, 5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({4.53125f, 3.90625f, 3.046875f, -8.59375f, -1.328125f, 1.328125f, 0.0f, -8.515625f, -8.984375f, -0.234375f, 0.859375f, 9.84375f, -0.15625f, -8.515625f, 8.671875f, 4.609375f, 9.21875f, -1.796875f, 1.171875f, 9.375f, -8.75f, 2.421875f, -8.125f, -1.09375f, -9.609375f, -1.015625f, -9.84375f, 2.578125f, 4.921875f, -5.078125f, 5.0f, -0.859375f, 1.953125f, -6.640625f, -7.8125f, 4.453125f, -4.453125f, -6.875f, 0.78125f, 0.859375f})
                        }, { // output01
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 2, 2, 2, 5},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({4.53125f, 3.90625f, 3.046875f, 0.0f, -0.3700765073299408f, 0.9580485224723816f, 0.0f, 0.0f, 0.0f, -0.10803219676017761f, 0.5527750849723816f, 9.84375f, -0.0740559995174408f, 0.0f, 8.671875f, 4.609375f, 9.21875f, -0.3603109121322632f, 0.8148192763328552f, 9.375f, 0.0f, 2.1885173320770264f, 0.0f, -0.3474934995174408f, 0.0f, -0.3358967900276184f, 0.0f, 2.3968505859375f, 4.921875f, 0.0f, 5.0f, -0.306599885225296f, 1.6123454570770264f, 0.0f, 0.0f, 4.453125f, 0.0f, 0.0f, 0.492350310087204f, 0.5527750849723816f})
                        }},
                .operations = {{
                            .type = TestOperationType::HARD_SWISH,
                            .inputs = {0},
                            .outputs = {1}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {1}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_5d_input_float16 = TestModelManager::get().add("hard_swish_5d_input_float16", get_test_model_5d_input_float16());

}  // namespace generated_tests::hard_swish

namespace generated_tests::hard_swish {

const TestModel& get_test_model_5d_input_relaxed() {
    static TestModel model = {
        .main = {
                .operands = {{ // input01
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 2, 5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({4.53125f, 3.90625f, 3.046875f, -8.59375f, -1.328125f, 1.328125f, 0.0f, -8.515625f, -8.984375f, -0.234375f, 0.859375f, 9.84375f, -0.15625f, -8.515625f, 8.671875f, 4.609375f, 9.21875f, -1.796875f, 1.171875f, 9.375f, -8.75f, 2.421875f, -8.125f, -1.09375f, -9.609375f, -1.015625f, -9.84375f, 2.578125f, 4.921875f, -5.078125f, 5.0f, -0.859375f, 1.953125f, -6.640625f, -7.8125f, 4.453125f, -4.453125f, -6.875f, 0.78125f, 0.859375f})
                        }, { // output01
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 2, 5},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({4.53125f, 3.90625f, 3.046875f, 0.0f, -0.3700765f, 0.9580485f, 0.0f, 0.0f, 0.0f, -0.1080322f, 0.5527751f, 9.84375f, -0.074056f, 0.0f, 8.671875f, 4.609375f, 9.21875f, -0.3603109f, 0.8148193f, 9.375f, 0.0f, 2.1885173f, 0.0f, -0.3474935f, 0.0f, -0.3358968f, 0.0f, 2.3968506f, 4.921875f, 0.0f, 5.0f, -0.3065999f, 1.6123454f, 0.0f, 0.0f, 4.453125f, 0.0f, 0.0f, 0.4923503f, 0.5527751f})
                        }},
                .operations = {{
                            .type = TestOperationType::HARD_SWISH,
                            .inputs = {0},
                            .outputs = {1}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {1}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_5d_input_relaxed = TestModelManager::get().add("hard_swish_5d_input_relaxed", get_test_model_5d_input_relaxed());

}  // namespace generated_tests::hard_swish

namespace generated_tests::hard_swish {

const TestModel& get_test_model_5d_input_quant8() {
    static TestModel model = {
        .main = {
                .operands = {{ // input01
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 2, 5},
                            .numberOfConsumers = 1,
                            .scale = 0.078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({186, 178, 167, 18, 111, 145, 128, 19, 13, 125, 139, 254, 126, 19, 239, 187, 246, 105, 143, 248, 16, 159, 24, 114, 5, 115, 2, 161, 191, 63, 192, 117, 153, 43, 28, 185, 71, 40, 138, 139})
                        }, { // output01
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 2, 5},
                            .numberOfConsumers = 0,
                            .scale = 0.078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({186, 178, 167, 128, 123, 140, 128, 128, 128, 127, 135, 254, 127, 128, 239, 187, 246, 123, 138, 248, 128, 156, 128, 124, 128, 124, 128, 159, 191, 128, 192, 124, 149, 128, 128, 185, 128, 128, 134, 135})
                        }},
                .operations = {{
                            .type = TestOperationType::HARD_SWISH,
                            .inputs = {0},
                            .outputs = {1}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {1}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_5d_input_quant8 = TestModelManager::get().add("hard_swish_5d_input_quant8", get_test_model_5d_input_quant8());

}  // namespace generated_tests::hard_swish

namespace generated_tests::hard_swish {

const TestModel& get_test_model_5d_input_quant8_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // input01
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 2, 5},
                            .numberOfConsumers = 1,
                            .scale = 0.078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({58, 50, 39, -110, -17, 17, 0, -109, -115, -3, 11, 126, -2, -109, 111, 59, 118, -23, 15, 120, -112, 31, -104, -14, -123, -13, -126, 33, 63, -65, 64, -11, 25, -85, -100, 57, -57, -88, 10, 11})
                        }, { // output01
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 2, 5},
                            .numberOfConsumers = 0,
                            .scale = 0.078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({58, 50, 39, 0, -5, 12, 0, 0, 0, -1, 7, 126, -1, 0, 111, 59, 118, -5, 10, 120, 0, 28, 0, -4, 0, -4, 0, 31, 63, 0, 64, -4, 21, 0, 0, 57, 0, 0, 6, 7})
                        }},
                .operations = {{
                            .type = TestOperationType::HARD_SWISH,
                            .inputs = {0},
                            .outputs = {1}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {1}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_5d_input_quant8_signed = TestModelManager::get().add("hard_swish_5d_input_quant8_signed", get_test_model_5d_input_quant8_signed());

}  // namespace generated_tests::hard_swish

namespace generated_tests::hard_swish {

const TestModel& get_test_model_5d_input_quant8_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // input01
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 2, 5},
                            .numberOfConsumers = 1,
                            .scale = 0.078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({186, 178, 167, 18, 111, 145, 128, 19, 13, 125, 139, 254, 126, 19, 239, 187, 246, 105, 143, 248, 16, 159, 24, 114, 5, 115, 2, 161, 191, 63, 192, 117, 153, 43, 28, 185, 71, 40, 138, 139})
                        }, { // output01
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 2, 5},
                            .numberOfConsumers = 0,
                            .scale = 0.03125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({145, 125, 98, 0, 0, 31, 0, 0, 0, 0, 18, 255, 0, 0, 255, 148, 255, 0, 26, 255, 0, 70, 0, 0, 0, 0, 0, 77, 158, 0, 160, 0, 52, 0, 0, 142, 0, 0, 16, 18})
                        }},
                .operations = {{
                            .type = TestOperationType::HARD_SWISH,
                            .inputs = {0},
                            .outputs = {1}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {1}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_5d_input_quant8_2 = TestModelManager::get().add("hard_swish_5d_input_quant8_2", get_test_model_5d_input_quant8_2());

}  // namespace generated_tests::hard_swish

namespace generated_tests::hard_swish {

const TestModel& get_test_model_5d_input_quant8_signed_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // input01
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 2, 5},
                            .numberOfConsumers = 1,
                            .scale = 0.078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({58, 50, 39, -110, -17, 17, 0, -109, -115, -3, 11, 126, -2, -109, 111, 59, 118, -23, 15, 120, -112, 31, -104, -14, -123, -13, -126, 33, 63, -65, 64, -11, 25, -85, -100, 57, -57, -88, 10, 11})
                        }, { // output01
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 2, 5},
                            .numberOfConsumers = 0,
                            .scale = 0.03125f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({17, -3, -30, -128, -128, -97, -128, -128, -128, -128, -110, 127, -128, -128, 127, 20, 127, -128, -102, 127, -128, -58, -128, -128, -128, -128, -128, -51, 30, -128, 32, -128, -76, -128, -128, 14, -128, -128, -112, -110})
                        }},
                .operations = {{
                            .type = TestOperationType::HARD_SWISH,
                            .inputs = {0},
                            .outputs = {1}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {1}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_5d_input_quant8_signed_2 = TestModelManager::get().add("hard_swish_5d_input_quant8_signed_2", get_test_model_5d_input_quant8_signed_2());

}  // namespace generated_tests::hard_swish

