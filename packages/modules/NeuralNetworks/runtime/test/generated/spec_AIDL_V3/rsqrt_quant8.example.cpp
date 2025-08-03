// Generated from rsqrt_quant8.mod.py
// DO NOT EDIT
// clang-format off
#include "TestHarness.h"
using namespace test_helper;  // NOLINT(google-build-using-namespace)

namespace generated_tests::rsqrt_quant8 {

const TestModel& get_test_model_25h_0_25h_0() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1, 4, 16, 64})
                        }, { // output0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({8, 4, 2, 1})
                        }},
                .operations = {{
                            .type = TestOperationType::RSQRT,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_25h_0_25h_0 = TestModelManager::get().add("rsqrt_quant8_25h_0_25h_0", get_test_model_25h_0_25h_0());

}  // namespace generated_tests::rsqrt_quant8

namespace generated_tests::rsqrt_quant8 {

const TestModel& get_test_model_25h_0_25h_0_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // output0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({8, 4, 2, 1})
                        }, { // input0_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({1, 4, 16, 64})
                        }, { // placeholder
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
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
                            .inputs = {2, 3, 4},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::RSQRT,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_25h_0_25h_0_all_inputs_as_internal = TestModelManager::get().add("rsqrt_quant8_25h_0_25h_0_all_inputs_as_internal", get_test_model_25h_0_25h_0_all_inputs_as_internal());

}  // namespace generated_tests::rsqrt_quant8

namespace generated_tests::rsqrt_quant8 {

const TestModel& get_test_model_25h_0_1h_75() {
    static TestModel model = {
        .main = {
                .operands = {{ // input01
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({16, 64})
                        }, { // output01
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = 75,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({125, 100})
                        }},
                .operations = {{
                            .type = TestOperationType::RSQRT,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_25h_0_1h_75 = TestModelManager::get().add("rsqrt_quant8_25h_0_1h_75", get_test_model_25h_0_1h_75());

}  // namespace generated_tests::rsqrt_quant8

namespace generated_tests::rsqrt_quant8 {

const TestModel& get_test_model_25h_0_1h_75_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // input01
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // output01
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = 75,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({125, 100})
                        }, { // input01_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({16, 64})
                        }, { // placeholder1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
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
                            .type = TestOperationType::RSQRT,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_25h_0_1h_75_all_inputs_as_internal = TestModelManager::get().add("rsqrt_quant8_25h_0_1h_75_all_inputs_as_internal", get_test_model_25h_0_1h_75_all_inputs_as_internal());

}  // namespace generated_tests::rsqrt_quant8

namespace generated_tests::rsqrt_quant8 {

const TestModel& get_test_model_125t_10_25h_0() {
    static TestModel model = {
        .main = {
                .operands = {{ // input02
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 10,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({12, 18, 42})
                        }, { // output02
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({8, 4, 2})
                        }},
                .operations = {{
                            .type = TestOperationType::RSQRT,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_125t_10_25h_0 = TestModelManager::get().add("rsqrt_quant8_125t_10_25h_0", get_test_model_125t_10_25h_0());

}  // namespace generated_tests::rsqrt_quant8

namespace generated_tests::rsqrt_quant8 {

const TestModel& get_test_model_125t_10_25h_0_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // input02
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 10,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // output02
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({8, 4, 2})
                        }, { // input02_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 10,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({12, 18, 42})
                        }, { // placeholder2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 10,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({10})
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
                            .type = TestOperationType::RSQRT,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_125t_10_25h_0_all_inputs_as_internal = TestModelManager::get().add("rsqrt_quant8_125t_10_25h_0_all_inputs_as_internal", get_test_model_125t_10_25h_0_all_inputs_as_internal());

}  // namespace generated_tests::rsqrt_quant8

namespace generated_tests::rsqrt_quant8 {

const TestModel& get_test_model_125t_10_1h_75() {
    static TestModel model = {
        .main = {
                .operands = {{ // input03
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 10,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({42})
                        }, { // output03
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = 75,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({125})
                        }},
                .operations = {{
                            .type = TestOperationType::RSQRT,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_125t_10_1h_75 = TestModelManager::get().add("rsqrt_quant8_125t_10_1h_75", get_test_model_125t_10_1h_75());

}  // namespace generated_tests::rsqrt_quant8

namespace generated_tests::rsqrt_quant8 {

const TestModel& get_test_model_125t_10_1h_75_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // input03
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 10,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // output03
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = 75,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({125})
                        }, { // input03_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 10,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({42})
                        }, { // placeholder3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 10,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({10})
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
                            .type = TestOperationType::RSQRT,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_125t_10_1h_75_all_inputs_as_internal = TestModelManager::get().add("rsqrt_quant8_125t_10_1h_75_all_inputs_as_internal", get_test_model_125t_10_1h_75_all_inputs_as_internal());

}  // namespace generated_tests::rsqrt_quant8

