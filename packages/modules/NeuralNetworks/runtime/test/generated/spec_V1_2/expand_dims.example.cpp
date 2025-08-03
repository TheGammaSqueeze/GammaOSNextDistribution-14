// Generated from expand_dims.mod.py
// DO NOT EDIT
// clang-format off
#include "TestHarness.h"
using namespace test_helper;  // NOLINT(google-build-using-namespace)

namespace generated_tests::expand_dims {

const TestModel& get_test_model() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.2f, -3.4f, 5.6f, 7.8f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.2f, -3.4f, 5.6f, 7.8f})
                        }},
                .operations = {{
                            .type = TestOperationType::EXPAND_DIMS,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model = TestModelManager::get().add("expand_dims", get_test_model());

}  // namespace generated_tests::expand_dims

namespace generated_tests::expand_dims {

const TestModel& get_test_model_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
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
                        }, { // output
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.2f, -3.4f, 5.6f, 7.8f})
                        }, { // input0_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.2f, -3.4f, 5.6f, 7.8f})
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
                            .inputs = {3, 4, 5},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::EXPAND_DIMS,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {3},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal = TestModelManager::get().add("expand_dims_all_inputs_as_internal", get_test_model_all_inputs_as_internal());

}  // namespace generated_tests::expand_dims

namespace generated_tests::expand_dims {

const TestModel& get_test_model_relaxed() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.2f, -3.4f, 5.6f, 7.8f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.2f, -3.4f, 5.6f, 7.8f})
                        }},
                .operations = {{
                            .type = TestOperationType::EXPAND_DIMS,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed = TestModelManager::get().add("expand_dims_relaxed", get_test_model_relaxed());

}  // namespace generated_tests::expand_dims

namespace generated_tests::expand_dims {

const TestModel& get_test_model_relaxed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
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
                        }, { // output
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.2f, -3.4f, 5.6f, 7.8f})
                        }, { // input0_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.2f, -3.4f, 5.6f, 7.8f})
                        }, { // placeholder1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
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
                            .inputs = {3, 4, 5},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::EXPAND_DIMS,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {3},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_all_inputs_as_internal = TestModelManager::get().add("expand_dims_relaxed_all_inputs_as_internal", get_test_model_relaxed_all_inputs_as_internal());

}  // namespace generated_tests::expand_dims

namespace generated_tests::expand_dims {

const TestModel& get_test_model_quant8() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 120, 138, 143})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 120, 138, 143})
                        }},
                .operations = {{
                            .type = TestOperationType::EXPAND_DIMS,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8 = TestModelManager::get().add("expand_dims_quant8", get_test_model_quant8());

}  // namespace generated_tests::expand_dims

namespace generated_tests::expand_dims {

const TestModel& get_test_model_quant8_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 127,
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
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 120, 138, 143})
                        }, { // input0_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 120, 138, 143})
                        }, { // placeholder2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 127,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({127})
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
                            .inputs = {3, 4, 5},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::EXPAND_DIMS,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {3},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_all_inputs_as_internal = TestModelManager::get().add("expand_dims_quant8_all_inputs_as_internal", get_test_model_quant8_all_inputs_as_internal());

}  // namespace generated_tests::expand_dims

namespace generated_tests::expand_dims {

const TestModel& get_test_model_int32() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, -3, 6, 8})
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
                            .dimensions = {1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, -3, 6, 8})
                        }},
                .operations = {{
                            .type = TestOperationType::EXPAND_DIMS,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_int32 = TestModelManager::get().add("expand_dims_int32", get_test_model_int32());

}  // namespace generated_tests::expand_dims

namespace generated_tests::expand_dims {

const TestModel& get_test_model_float16() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.2000000476837158f, -3.4000000953674316f, 5.599999904632568f, 7.800000190734863f})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.2000000476837158f, -3.4000000953674316f, 5.599999904632568f, 7.800000190734863f})
                        }},
                .operations = {{
                            .type = TestOperationType::EXPAND_DIMS,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16 = TestModelManager::get().add("expand_dims_float16", get_test_model_float16());

}  // namespace generated_tests::expand_dims

namespace generated_tests::expand_dims {

const TestModel& get_test_model_float16_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
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
                        }, { // output
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.2000000476837158f, -3.4000000953674316f, 5.599999904632568f, 7.800000190734863f})
                        }, { // input0_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.2000000476837158f, -3.4000000953674316f, 5.599999904632568f, 7.800000190734863f})
                        }, { // placeholder3
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
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
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {3, 4, 5},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::EXPAND_DIMS,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {3},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_all_inputs_as_internal = TestModelManager::get().add("expand_dims_float16_all_inputs_as_internal", get_test_model_float16_all_inputs_as_internal());

}  // namespace generated_tests::expand_dims

namespace generated_tests::expand_dims {

const TestModel& get_test_model_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.2f, -3.4f, 5.6f, 7.8f})
                        }, { // param1
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // output1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.2f, -3.4f, 5.6f, 7.8f})
                        }},
                .operations = {{
                            .type = TestOperationType::EXPAND_DIMS,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_2 = TestModelManager::get().add("expand_dims_2", get_test_model_2());

}  // namespace generated_tests::expand_dims

namespace generated_tests::expand_dims {

const TestModel& get_test_model_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
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
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // output1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.2f, -3.4f, 5.6f, 7.8f})
                        }, { // input0_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.2f, -3.4f, 5.6f, 7.8f})
                        }, { // placeholder4
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
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
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {3, 4, 5},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::EXPAND_DIMS,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {3},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_2 = TestModelManager::get().add("expand_dims_all_inputs_as_internal_2", get_test_model_all_inputs_as_internal_2());

}  // namespace generated_tests::expand_dims

namespace generated_tests::expand_dims {

const TestModel& get_test_model_relaxed_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.2f, -3.4f, 5.6f, 7.8f})
                        }, { // param1
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // output1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.2f, -3.4f, 5.6f, 7.8f})
                        }},
                .operations = {{
                            .type = TestOperationType::EXPAND_DIMS,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_2 = TestModelManager::get().add("expand_dims_relaxed_2", get_test_model_relaxed_2());

}  // namespace generated_tests::expand_dims

namespace generated_tests::expand_dims {

const TestModel& get_test_model_relaxed_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
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
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // output1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.2f, -3.4f, 5.6f, 7.8f})
                        }, { // input0_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.2f, -3.4f, 5.6f, 7.8f})
                        }, { // placeholder5
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
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
                            .inputs = {3, 4, 5},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::EXPAND_DIMS,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {3},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_all_inputs_as_internal_2 = TestModelManager::get().add("expand_dims_relaxed_all_inputs_as_internal_2", get_test_model_relaxed_all_inputs_as_internal_2());

}  // namespace generated_tests::expand_dims

namespace generated_tests::expand_dims {

const TestModel& get_test_model_quant8_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 120, 138, 143})
                        }, { // param1
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // output1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 120, 138, 143})
                        }},
                .operations = {{
                            .type = TestOperationType::EXPAND_DIMS,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_2 = TestModelManager::get().add("expand_dims_quant8_2", get_test_model_quant8_2());

}  // namespace generated_tests::expand_dims

namespace generated_tests::expand_dims {

const TestModel& get_test_model_quant8_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 127,
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
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // output1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 120, 138, 143})
                        }, { // input0_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 120, 138, 143})
                        }, { // placeholder6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 127,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({127})
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
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {3, 4, 5},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::EXPAND_DIMS,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {3},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_all_inputs_as_internal_2 = TestModelManager::get().add("expand_dims_quant8_all_inputs_as_internal_2", get_test_model_quant8_all_inputs_as_internal_2());

}  // namespace generated_tests::expand_dims

namespace generated_tests::expand_dims {

const TestModel& get_test_model_int32_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, -3, 6, 8})
                        }, { // param1
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // output1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, -3, 6, 8})
                        }},
                .operations = {{
                            .type = TestOperationType::EXPAND_DIMS,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_int32_2 = TestModelManager::get().add("expand_dims_int32_2", get_test_model_int32_2());

}  // namespace generated_tests::expand_dims

namespace generated_tests::expand_dims {

const TestModel& get_test_model_float16_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.2000000476837158f, -3.4000000953674316f, 5.599999904632568f, 7.800000190734863f})
                        }, { // param1
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // output1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.2000000476837158f, -3.4000000953674316f, 5.599999904632568f, 7.800000190734863f})
                        }},
                .operations = {{
                            .type = TestOperationType::EXPAND_DIMS,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_2 = TestModelManager::get().add("expand_dims_float16_2", get_test_model_float16_2());

}  // namespace generated_tests::expand_dims

namespace generated_tests::expand_dims {

const TestModel& get_test_model_float16_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
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
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // output1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.2000000476837158f, -3.4000000953674316f, 5.599999904632568f, 7.800000190734863f})
                        }, { // input0_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.2000000476837158f, -3.4000000953674316f, 5.599999904632568f, 7.800000190734863f})
                        }, { // placeholder7
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
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
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {3, 4, 5},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::EXPAND_DIMS,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {3},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_all_inputs_as_internal_2 = TestModelManager::get().add("expand_dims_float16_all_inputs_as_internal_2", get_test_model_float16_all_inputs_as_internal_2());

}  // namespace generated_tests::expand_dims

namespace generated_tests::expand_dims {

const TestModel& get_test_model_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.2f, -3.4f, 5.6f, 7.8f})
                        }, { // param2
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // output2
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.2f, -3.4f, 5.6f, 7.8f})
                        }},
                .operations = {{
                            .type = TestOperationType::EXPAND_DIMS,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_3 = TestModelManager::get().add("expand_dims_3", get_test_model_3());

}  // namespace generated_tests::expand_dims

namespace generated_tests::expand_dims {

const TestModel& get_test_model_all_inputs_as_internal_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // param2
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // output2
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.2f, -3.4f, 5.6f, 7.8f})
                        }, { // input0_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.2f, -3.4f, 5.6f, 7.8f})
                        }, { // placeholder8
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
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
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {3, 4, 5},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::EXPAND_DIMS,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {3},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_3 = TestModelManager::get().add("expand_dims_all_inputs_as_internal_3", get_test_model_all_inputs_as_internal_3());

}  // namespace generated_tests::expand_dims

namespace generated_tests::expand_dims {

const TestModel& get_test_model_relaxed_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.2f, -3.4f, 5.6f, 7.8f})
                        }, { // param2
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // output2
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.2f, -3.4f, 5.6f, 7.8f})
                        }},
                .operations = {{
                            .type = TestOperationType::EXPAND_DIMS,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_3 = TestModelManager::get().add("expand_dims_relaxed_3", get_test_model_relaxed_3());

}  // namespace generated_tests::expand_dims

namespace generated_tests::expand_dims {

const TestModel& get_test_model_relaxed_all_inputs_as_internal_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // param2
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // output2
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.2f, -3.4f, 5.6f, 7.8f})
                        }, { // input0_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.2f, -3.4f, 5.6f, 7.8f})
                        }, { // placeholder9
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
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
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {3, 4, 5},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::EXPAND_DIMS,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {3},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_all_inputs_as_internal_3 = TestModelManager::get().add("expand_dims_relaxed_all_inputs_as_internal_3", get_test_model_relaxed_all_inputs_as_internal_3());

}  // namespace generated_tests::expand_dims

namespace generated_tests::expand_dims {

const TestModel& get_test_model_quant8_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 120, 138, 143})
                        }, { // param2
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // output2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 120, 138, 143})
                        }},
                .operations = {{
                            .type = TestOperationType::EXPAND_DIMS,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_3 = TestModelManager::get().add("expand_dims_quant8_3", get_test_model_quant8_3());

}  // namespace generated_tests::expand_dims

namespace generated_tests::expand_dims {

const TestModel& get_test_model_quant8_all_inputs_as_internal_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 127,
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
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // output2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 120, 138, 143})
                        }, { // input0_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 120, 138, 143})
                        }, { // placeholder10
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 127,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({127})
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
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {3, 4, 5},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::EXPAND_DIMS,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {3},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_all_inputs_as_internal_3 = TestModelManager::get().add("expand_dims_quant8_all_inputs_as_internal_3", get_test_model_quant8_all_inputs_as_internal_3());

}  // namespace generated_tests::expand_dims

namespace generated_tests::expand_dims {

const TestModel& get_test_model_int32_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, -3, 6, 8})
                        }, { // param2
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // output2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, -3, 6, 8})
                        }},
                .operations = {{
                            .type = TestOperationType::EXPAND_DIMS,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_int32_3 = TestModelManager::get().add("expand_dims_int32_3", get_test_model_int32_3());

}  // namespace generated_tests::expand_dims

namespace generated_tests::expand_dims {

const TestModel& get_test_model_float16_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.2000000476837158f, -3.4000000953674316f, 5.599999904632568f, 7.800000190734863f})
                        }, { // param2
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // output2
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.2000000476837158f, -3.4000000953674316f, 5.599999904632568f, 7.800000190734863f})
                        }},
                .operations = {{
                            .type = TestOperationType::EXPAND_DIMS,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_3 = TestModelManager::get().add("expand_dims_float16_3", get_test_model_float16_3());

}  // namespace generated_tests::expand_dims

namespace generated_tests::expand_dims {

const TestModel& get_test_model_float16_all_inputs_as_internal_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // param2
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // output2
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.2000000476837158f, -3.4000000953674316f, 5.599999904632568f, 7.800000190734863f})
                        }, { // input0_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.2000000476837158f, -3.4000000953674316f, 5.599999904632568f, 7.800000190734863f})
                        }, { // placeholder11
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
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
                            .type = TestOperationType::ADD,
                            .inputs = {3, 4, 5},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::EXPAND_DIMS,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {3},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_all_inputs_as_internal_3 = TestModelManager::get().add("expand_dims_float16_all_inputs_as_internal_3", get_test_model_float16_all_inputs_as_internal_3());

}  // namespace generated_tests::expand_dims

namespace generated_tests::expand_dims {

const TestModel& get_test_model_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.2f, -3.4f, 5.6f, 7.8f})
                        }, { // param3
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-1})
                        }, { // output2
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.2f, -3.4f, 5.6f, 7.8f})
                        }},
                .operations = {{
                            .type = TestOperationType::EXPAND_DIMS,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_4 = TestModelManager::get().add("expand_dims_4", get_test_model_4());

}  // namespace generated_tests::expand_dims

namespace generated_tests::expand_dims {

const TestModel& get_test_model_all_inputs_as_internal_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // param3
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-1})
                        }, { // output2
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.2f, -3.4f, 5.6f, 7.8f})
                        }, { // input0_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.2f, -3.4f, 5.6f, 7.8f})
                        }, { // placeholder12
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
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
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {3, 4, 5},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::EXPAND_DIMS,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {3},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal_4 = TestModelManager::get().add("expand_dims_all_inputs_as_internal_4", get_test_model_all_inputs_as_internal_4());

}  // namespace generated_tests::expand_dims

namespace generated_tests::expand_dims {

const TestModel& get_test_model_relaxed_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.2f, -3.4f, 5.6f, 7.8f})
                        }, { // param3
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-1})
                        }, { // output2
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.2f, -3.4f, 5.6f, 7.8f})
                        }},
                .operations = {{
                            .type = TestOperationType::EXPAND_DIMS,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_4 = TestModelManager::get().add("expand_dims_relaxed_4", get_test_model_relaxed_4());

}  // namespace generated_tests::expand_dims

namespace generated_tests::expand_dims {

const TestModel& get_test_model_relaxed_all_inputs_as_internal_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // param3
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-1})
                        }, { // output2
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.2f, -3.4f, 5.6f, 7.8f})
                        }, { // input0_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.2f, -3.4f, 5.6f, 7.8f})
                        }, { // placeholder13
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
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
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {3, 4, 5},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::EXPAND_DIMS,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {3},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_all_inputs_as_internal_4 = TestModelManager::get().add("expand_dims_relaxed_all_inputs_as_internal_4", get_test_model_relaxed_all_inputs_as_internal_4());

}  // namespace generated_tests::expand_dims

namespace generated_tests::expand_dims {

const TestModel& get_test_model_quant8_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 120, 138, 143})
                        }, { // param3
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-1})
                        }, { // output2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 120, 138, 143})
                        }},
                .operations = {{
                            .type = TestOperationType::EXPAND_DIMS,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_4 = TestModelManager::get().add("expand_dims_quant8_4", get_test_model_quant8_4());

}  // namespace generated_tests::expand_dims

namespace generated_tests::expand_dims {

const TestModel& get_test_model_quant8_all_inputs_as_internal_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 127,
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
                            .data = TestBuffer::createFromVector<int32_t>({-1})
                        }, { // output2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 120, 138, 143})
                        }, { // input0_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 127,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({129, 120, 138, 143})
                        }, { // placeholder14
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 127,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({127})
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
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {3, 4, 5},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::EXPAND_DIMS,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {3},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_all_inputs_as_internal_4 = TestModelManager::get().add("expand_dims_quant8_all_inputs_as_internal_4", get_test_model_quant8_all_inputs_as_internal_4());

}  // namespace generated_tests::expand_dims

namespace generated_tests::expand_dims {

const TestModel& get_test_model_int32_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, -3, 6, 8})
                        }, { // param3
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-1})
                        }, { // output2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, -3, 6, 8})
                        }},
                .operations = {{
                            .type = TestOperationType::EXPAND_DIMS,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_int32_4 = TestModelManager::get().add("expand_dims_int32_4", get_test_model_int32_4());

}  // namespace generated_tests::expand_dims

namespace generated_tests::expand_dims {

const TestModel& get_test_model_float16_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.2000000476837158f, -3.4000000953674316f, 5.599999904632568f, 7.800000190734863f})
                        }, { // param3
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-1})
                        }, { // output2
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.2000000476837158f, -3.4000000953674316f, 5.599999904632568f, 7.800000190734863f})
                        }},
                .operations = {{
                            .type = TestOperationType::EXPAND_DIMS,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_4 = TestModelManager::get().add("expand_dims_float16_4", get_test_model_float16_4());

}  // namespace generated_tests::expand_dims

namespace generated_tests::expand_dims {

const TestModel& get_test_model_float16_all_inputs_as_internal_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // param3
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-1})
                        }, { // output2
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.2000000476837158f, -3.4000000953674316f, 5.599999904632568f, 7.800000190734863f})
                        }, { // input0_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.2000000476837158f, -3.4000000953674316f, 5.599999904632568f, 7.800000190734863f})
                        }, { // placeholder15
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
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
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {3, 4, 5},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::EXPAND_DIMS,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {3},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_all_inputs_as_internal_4 = TestModelManager::get().add("expand_dims_float16_all_inputs_as_internal_4", get_test_model_float16_all_inputs_as_internal_4());

}  // namespace generated_tests::expand_dims

