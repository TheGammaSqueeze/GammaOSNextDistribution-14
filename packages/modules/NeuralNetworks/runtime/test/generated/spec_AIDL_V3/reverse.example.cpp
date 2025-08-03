// Generated from reverse.mod.py
// DO NOT EDIT
// clang-format off
#include "TestHarness.h"
using namespace test_helper;  // NOLINT(google-build-using-namespace)

namespace generated_tests::reverse {

const TestModel& get_test_model_dim1() {
    static TestModel model = {
        .main = {
                .operands = {{ // in
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({6.0f, 7.0f, 8.0f})
                        }, { // param
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // out
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({8.0f, 7.0f, 6.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::REVERSE,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_dim1 = TestModelManager::get().add("reverse_dim1", get_test_model_dim1());

}  // namespace generated_tests::reverse

namespace generated_tests::reverse {

const TestModel& get_test_model_dim1_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // in
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // param
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // out
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({8.0f, 7.0f, 6.0f})
                        }, { // in_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({6.0f, 7.0f, 8.0f})
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
                            .type = TestOperationType::REVERSE,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_dim1_all_inputs_as_internal = TestModelManager::get().add("reverse_dim1_all_inputs_as_internal", get_test_model_dim1_all_inputs_as_internal());

}  // namespace generated_tests::reverse

namespace generated_tests::reverse {

const TestModel& get_test_model_dim1_float16() {
    static TestModel model = {
        .main = {
                .operands = {{ // in
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({6.0f, 7.0f, 8.0f})
                        }, { // param
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // out
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({8.0f, 7.0f, 6.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::REVERSE,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_dim1_float16 = TestModelManager::get().add("reverse_dim1_float16", get_test_model_dim1_float16());

}  // namespace generated_tests::reverse

namespace generated_tests::reverse {

const TestModel& get_test_model_dim1_float16_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // in
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // param
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // out
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({8.0f, 7.0f, 6.0f})
                        }, { // in_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({6.0f, 7.0f, 8.0f})
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
                            .type = TestOperationType::REVERSE,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_dim1_float16_all_inputs_as_internal = TestModelManager::get().add("reverse_dim1_float16_all_inputs_as_internal", get_test_model_dim1_float16_all_inputs_as_internal());

}  // namespace generated_tests::reverse

namespace generated_tests::reverse {

const TestModel& get_test_model_dim1_quant8_asymm() {
    static TestModel model = {
        .main = {
                .operands = {{ // in
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({16, 18, 20})
                        }, { // param
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // out
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({20, 18, 16})
                        }},
                .operations = {{
                            .type = TestOperationType::REVERSE,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_dim1_quant8_asymm = TestModelManager::get().add("reverse_dim1_quant8_asymm", get_test_model_dim1_quant8_asymm());

}  // namespace generated_tests::reverse

namespace generated_tests::reverse {

const TestModel& get_test_model_dim1_quant8_asymm_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // in
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // out
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({20, 18, 16})
                        }, { // in_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({16, 18, 20})
                        }, { // placeholder2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({4})
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
                            .type = TestOperationType::REVERSE,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_dim1_quant8_asymm_all_inputs_as_internal = TestModelManager::get().add("reverse_dim1_quant8_asymm_all_inputs_as_internal", get_test_model_dim1_quant8_asymm_all_inputs_as_internal());

}  // namespace generated_tests::reverse

namespace generated_tests::reverse {

const TestModel& get_test_model_dim1_quant8_asymm_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // in
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({15, 19, 23})
                        }, { // param
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // out
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({23, 19, 15})
                        }},
                .operations = {{
                            .type = TestOperationType::REVERSE,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_dim1_quant8_asymm_signed = TestModelManager::get().add("reverse_dim1_quant8_asymm_signed", get_test_model_dim1_quant8_asymm_signed());

}  // namespace generated_tests::reverse

namespace generated_tests::reverse {

const TestModel& get_test_model_dim1_quant8_asymm_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // in
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // out
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({23, 19, 15})
                        }, { // in_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({15, 19, 23})
                        }, { // placeholder3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-9})
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
                            .type = TestOperationType::REVERSE,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_dim1_quant8_asymm_signed_all_inputs_as_internal = TestModelManager::get().add("reverse_dim1_quant8_asymm_signed_all_inputs_as_internal", get_test_model_dim1_quant8_asymm_signed_all_inputs_as_internal());

}  // namespace generated_tests::reverse

namespace generated_tests::reverse {

const TestModel& get_test_model_dim1_int32() {
    static TestModel model = {
        .main = {
                .operands = {{ // in
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({6, 7, 8})
                        }, { // param
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // out
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({8, 7, 6})
                        }},
                .operations = {{
                            .type = TestOperationType::REVERSE,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_dim1_int32 = TestModelManager::get().add("reverse_dim1_int32", get_test_model_dim1_int32());

}  // namespace generated_tests::reverse

namespace generated_tests::reverse {

const TestModel& get_test_model_dim3_axis0() {
    static TestModel model = {
        .main = {
                .operands = {{ // in1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f})
                        }, { // param1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // out1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::REVERSE,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_dim3_axis0 = TestModelManager::get().add("reverse_dim3_axis0", get_test_model_dim3_axis0());

}  // namespace generated_tests::reverse

namespace generated_tests::reverse {

const TestModel& get_test_model_dim3_axis0_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // in1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // param1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // out1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f})
                        }, { // in1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f})
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
                            .type = TestOperationType::REVERSE,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_dim3_axis0_all_inputs_as_internal = TestModelManager::get().add("reverse_dim3_axis0_all_inputs_as_internal", get_test_model_dim3_axis0_all_inputs_as_internal());

}  // namespace generated_tests::reverse

namespace generated_tests::reverse {

const TestModel& get_test_model_dim3_axis0_float16() {
    static TestModel model = {
        .main = {
                .operands = {{ // in1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f})
                        }, { // param1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // out1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::REVERSE,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_dim3_axis0_float16 = TestModelManager::get().add("reverse_dim3_axis0_float16", get_test_model_dim3_axis0_float16());

}  // namespace generated_tests::reverse

namespace generated_tests::reverse {

const TestModel& get_test_model_dim3_axis0_float16_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // in1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // param1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // out1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f})
                        }, { // in1_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f})
                        }, { // placeholder5
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
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
                            .type = TestOperationType::REVERSE,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_dim3_axis0_float16_all_inputs_as_internal = TestModelManager::get().add("reverse_dim3_axis0_float16_all_inputs_as_internal", get_test_model_dim3_axis0_float16_all_inputs_as_internal());

}  // namespace generated_tests::reverse

namespace generated_tests::reverse {

const TestModel& get_test_model_dim3_axis0_quant8_asymm() {
    static TestModel model = {
        .main = {
                .operands = {{ // in1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32, 34, 36, 38, 40, 42, 44, 46, 48, 50})
                        }, { // param1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // out1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({28, 30, 32, 34, 36, 38, 40, 42, 44, 46, 48, 50, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26})
                        }},
                .operations = {{
                            .type = TestOperationType::REVERSE,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_dim3_axis0_quant8_asymm = TestModelManager::get().add("reverse_dim3_axis0_quant8_asymm", get_test_model_dim3_axis0_quant8_asymm());

}  // namespace generated_tests::reverse

namespace generated_tests::reverse {

const TestModel& get_test_model_dim3_axis0_quant8_asymm_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // in1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // out1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({28, 30, 32, 34, 36, 38, 40, 42, 44, 46, 48, 50, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26})
                        }, { // in1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32, 34, 36, 38, 40, 42, 44, 46, 48, 50})
                        }, { // placeholder6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({4})
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
                            .type = TestOperationType::REVERSE,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_dim3_axis0_quant8_asymm_all_inputs_as_internal = TestModelManager::get().add("reverse_dim3_axis0_quant8_asymm_all_inputs_as_internal", get_test_model_dim3_axis0_quant8_asymm_all_inputs_as_internal());

}  // namespace generated_tests::reverse

namespace generated_tests::reverse {

const TestModel& get_test_model_dim3_axis0_quant8_asymm_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // in1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-9, -5, -1, 3, 7, 11, 15, 19, 23, 27, 31, 35, 39, 43, 47, 51, 55, 59, 63, 67, 71, 75, 79, 83})
                        }, { // param1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // out1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({39, 43, 47, 51, 55, 59, 63, 67, 71, 75, 79, 83, -9, -5, -1, 3, 7, 11, 15, 19, 23, 27, 31, 35})
                        }},
                .operations = {{
                            .type = TestOperationType::REVERSE,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_dim3_axis0_quant8_asymm_signed = TestModelManager::get().add("reverse_dim3_axis0_quant8_asymm_signed", get_test_model_dim3_axis0_quant8_asymm_signed());

}  // namespace generated_tests::reverse

namespace generated_tests::reverse {

const TestModel& get_test_model_dim3_axis0_quant8_asymm_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // in1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // out1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({39, 43, 47, 51, 55, 59, 63, 67, 71, 75, 79, 83, -9, -5, -1, 3, 7, 11, 15, 19, 23, 27, 31, 35})
                        }, { // in1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-9, -5, -1, 3, 7, 11, 15, 19, 23, 27, 31, 35, 39, 43, 47, 51, 55, 59, 63, 67, 71, 75, 79, 83})
                        }, { // placeholder7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-9})
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
                            .type = TestOperationType::REVERSE,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_dim3_axis0_quant8_asymm_signed_all_inputs_as_internal = TestModelManager::get().add("reverse_dim3_axis0_quant8_asymm_signed_all_inputs_as_internal", get_test_model_dim3_axis0_quant8_asymm_signed_all_inputs_as_internal());

}  // namespace generated_tests::reverse

namespace generated_tests::reverse {

const TestModel& get_test_model_dim3_axis0_int32() {
    static TestModel model = {
        .main = {
                .operands = {{ // in1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23})
                        }, { // param1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // out1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11})
                        }},
                .operations = {{
                            .type = TestOperationType::REVERSE,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_dim3_axis0_int32 = TestModelManager::get().add("reverse_dim3_axis0_int32", get_test_model_dim3_axis0_int32());

}  // namespace generated_tests::reverse

namespace generated_tests::reverse {

const TestModel& get_test_model_dim3_axis1() {
    static TestModel model = {
        .main = {
                .operands = {{ // in2
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // out2
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({8.0f, 9.0f, 10.0f, 11.0f, 4.0f, 5.0f, 6.0f, 7.0f, 0.0f, 1.0f, 2.0f, 3.0f, 20.0f, 21.0f, 22.0f, 23.0f, 16.0f, 17.0f, 18.0f, 19.0f, 12.0f, 13.0f, 14.0f, 15.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::REVERSE,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_dim3_axis1 = TestModelManager::get().add("reverse_dim3_axis1", get_test_model_dim3_axis1());

}  // namespace generated_tests::reverse

namespace generated_tests::reverse {

const TestModel& get_test_model_dim3_axis1_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // in2
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // out2
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({8.0f, 9.0f, 10.0f, 11.0f, 4.0f, 5.0f, 6.0f, 7.0f, 0.0f, 1.0f, 2.0f, 3.0f, 20.0f, 21.0f, 22.0f, 23.0f, 16.0f, 17.0f, 18.0f, 19.0f, 12.0f, 13.0f, 14.0f, 15.0f})
                        }, { // in2_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f})
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
                            .type = TestOperationType::REVERSE,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_dim3_axis1_all_inputs_as_internal = TestModelManager::get().add("reverse_dim3_axis1_all_inputs_as_internal", get_test_model_dim3_axis1_all_inputs_as_internal());

}  // namespace generated_tests::reverse

namespace generated_tests::reverse {

const TestModel& get_test_model_dim3_axis1_float16() {
    static TestModel model = {
        .main = {
                .operands = {{ // in2
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // out2
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({8.0f, 9.0f, 10.0f, 11.0f, 4.0f, 5.0f, 6.0f, 7.0f, 0.0f, 1.0f, 2.0f, 3.0f, 20.0f, 21.0f, 22.0f, 23.0f, 16.0f, 17.0f, 18.0f, 19.0f, 12.0f, 13.0f, 14.0f, 15.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::REVERSE,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_dim3_axis1_float16 = TestModelManager::get().add("reverse_dim3_axis1_float16", get_test_model_dim3_axis1_float16());

}  // namespace generated_tests::reverse

namespace generated_tests::reverse {

const TestModel& get_test_model_dim3_axis1_float16_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // in2
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // out2
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({8.0f, 9.0f, 10.0f, 11.0f, 4.0f, 5.0f, 6.0f, 7.0f, 0.0f, 1.0f, 2.0f, 3.0f, 20.0f, 21.0f, 22.0f, 23.0f, 16.0f, 17.0f, 18.0f, 19.0f, 12.0f, 13.0f, 14.0f, 15.0f})
                        }, { // in2_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f})
                        }, { // placeholder9
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
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
                            .type = TestOperationType::REVERSE,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_dim3_axis1_float16_all_inputs_as_internal = TestModelManager::get().add("reverse_dim3_axis1_float16_all_inputs_as_internal", get_test_model_dim3_axis1_float16_all_inputs_as_internal());

}  // namespace generated_tests::reverse

namespace generated_tests::reverse {

const TestModel& get_test_model_dim3_axis1_quant8_asymm() {
    static TestModel model = {
        .main = {
                .operands = {{ // in2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32, 34, 36, 38, 40, 42, 44, 46, 48, 50})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // out2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({20, 22, 24, 26, 12, 14, 16, 18, 4, 6, 8, 10, 44, 46, 48, 50, 36, 38, 40, 42, 28, 30, 32, 34})
                        }},
                .operations = {{
                            .type = TestOperationType::REVERSE,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_dim3_axis1_quant8_asymm = TestModelManager::get().add("reverse_dim3_axis1_quant8_asymm", get_test_model_dim3_axis1_quant8_asymm());

}  // namespace generated_tests::reverse

namespace generated_tests::reverse {

const TestModel& get_test_model_dim3_axis1_quant8_asymm_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // in2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // out2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({20, 22, 24, 26, 12, 14, 16, 18, 4, 6, 8, 10, 44, 46, 48, 50, 36, 38, 40, 42, 28, 30, 32, 34})
                        }, { // in2_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32, 34, 36, 38, 40, 42, 44, 46, 48, 50})
                        }, { // placeholder10
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({4})
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
                            .type = TestOperationType::REVERSE,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_dim3_axis1_quant8_asymm_all_inputs_as_internal = TestModelManager::get().add("reverse_dim3_axis1_quant8_asymm_all_inputs_as_internal", get_test_model_dim3_axis1_quant8_asymm_all_inputs_as_internal());

}  // namespace generated_tests::reverse

namespace generated_tests::reverse {

const TestModel& get_test_model_dim3_axis1_quant8_asymm_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // in2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-9, -5, -1, 3, 7, 11, 15, 19, 23, 27, 31, 35, 39, 43, 47, 51, 55, 59, 63, 67, 71, 75, 79, 83})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // out2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({23, 27, 31, 35, 7, 11, 15, 19, -9, -5, -1, 3, 71, 75, 79, 83, 55, 59, 63, 67, 39, 43, 47, 51})
                        }},
                .operations = {{
                            .type = TestOperationType::REVERSE,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_dim3_axis1_quant8_asymm_signed = TestModelManager::get().add("reverse_dim3_axis1_quant8_asymm_signed", get_test_model_dim3_axis1_quant8_asymm_signed());

}  // namespace generated_tests::reverse

namespace generated_tests::reverse {

const TestModel& get_test_model_dim3_axis1_quant8_asymm_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // in2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // out2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({23, 27, 31, 35, 7, 11, 15, 19, -9, -5, -1, 3, 71, 75, 79, 83, 55, 59, 63, 67, 39, 43, 47, 51})
                        }, { // in2_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-9, -5, -1, 3, 7, 11, 15, 19, 23, 27, 31, 35, 39, 43, 47, 51, 55, 59, 63, 67, 71, 75, 79, 83})
                        }, { // placeholder11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-9})
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
                            .type = TestOperationType::REVERSE,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_dim3_axis1_quant8_asymm_signed_all_inputs_as_internal = TestModelManager::get().add("reverse_dim3_axis1_quant8_asymm_signed_all_inputs_as_internal", get_test_model_dim3_axis1_quant8_asymm_signed_all_inputs_as_internal());

}  // namespace generated_tests::reverse

namespace generated_tests::reverse {

const TestModel& get_test_model_dim3_axis1_int32() {
    static TestModel model = {
        .main = {
                .operands = {{ // in2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // out2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({8, 9, 10, 11, 4, 5, 6, 7, 0, 1, 2, 3, 20, 21, 22, 23, 16, 17, 18, 19, 12, 13, 14, 15})
                        }},
                .operations = {{
                            .type = TestOperationType::REVERSE,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_dim3_axis1_int32 = TestModelManager::get().add("reverse_dim3_axis1_int32", get_test_model_dim3_axis1_int32());

}  // namespace generated_tests::reverse

namespace generated_tests::reverse {

const TestModel& get_test_model_dim3_axis2() {
    static TestModel model = {
        .main = {
                .operands = {{ // in3
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // out3
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({3.0f, 2.0f, 1.0f, 0.0f, 7.0f, 6.0f, 5.0f, 4.0f, 11.0f, 10.0f, 9.0f, 8.0f, 15.0f, 14.0f, 13.0f, 12.0f, 19.0f, 18.0f, 17.0f, 16.0f, 23.0f, 22.0f, 21.0f, 20.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::REVERSE,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_dim3_axis2 = TestModelManager::get().add("reverse_dim3_axis2", get_test_model_dim3_axis2());

}  // namespace generated_tests::reverse

namespace generated_tests::reverse {

const TestModel& get_test_model_dim3_axis2_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // in3
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // out3
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({3.0f, 2.0f, 1.0f, 0.0f, 7.0f, 6.0f, 5.0f, 4.0f, 11.0f, 10.0f, 9.0f, 8.0f, 15.0f, 14.0f, 13.0f, 12.0f, 19.0f, 18.0f, 17.0f, 16.0f, 23.0f, 22.0f, 21.0f, 20.0f})
                        }, { // in3_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f})
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
                            .type = TestOperationType::REVERSE,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_dim3_axis2_all_inputs_as_internal = TestModelManager::get().add("reverse_dim3_axis2_all_inputs_as_internal", get_test_model_dim3_axis2_all_inputs_as_internal());

}  // namespace generated_tests::reverse

namespace generated_tests::reverse {

const TestModel& get_test_model_dim3_axis2_float16() {
    static TestModel model = {
        .main = {
                .operands = {{ // in3
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // out3
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({3.0f, 2.0f, 1.0f, 0.0f, 7.0f, 6.0f, 5.0f, 4.0f, 11.0f, 10.0f, 9.0f, 8.0f, 15.0f, 14.0f, 13.0f, 12.0f, 19.0f, 18.0f, 17.0f, 16.0f, 23.0f, 22.0f, 21.0f, 20.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::REVERSE,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_dim3_axis2_float16 = TestModelManager::get().add("reverse_dim3_axis2_float16", get_test_model_dim3_axis2_float16());

}  // namespace generated_tests::reverse

namespace generated_tests::reverse {

const TestModel& get_test_model_dim3_axis2_float16_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // in3
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // out3
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({3.0f, 2.0f, 1.0f, 0.0f, 7.0f, 6.0f, 5.0f, 4.0f, 11.0f, 10.0f, 9.0f, 8.0f, 15.0f, 14.0f, 13.0f, 12.0f, 19.0f, 18.0f, 17.0f, 16.0f, 23.0f, 22.0f, 21.0f, 20.0f})
                        }, { // in3_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f})
                        }, { // placeholder13
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
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
                            .type = TestOperationType::REVERSE,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_dim3_axis2_float16_all_inputs_as_internal = TestModelManager::get().add("reverse_dim3_axis2_float16_all_inputs_as_internal", get_test_model_dim3_axis2_float16_all_inputs_as_internal());

}  // namespace generated_tests::reverse

namespace generated_tests::reverse {

const TestModel& get_test_model_dim3_axis2_quant8_asymm() {
    static TestModel model = {
        .main = {
                .operands = {{ // in3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32, 34, 36, 38, 40, 42, 44, 46, 48, 50})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // out3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({10, 8, 6, 4, 18, 16, 14, 12, 26, 24, 22, 20, 34, 32, 30, 28, 42, 40, 38, 36, 50, 48, 46, 44})
                        }},
                .operations = {{
                            .type = TestOperationType::REVERSE,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_dim3_axis2_quant8_asymm = TestModelManager::get().add("reverse_dim3_axis2_quant8_asymm", get_test_model_dim3_axis2_quant8_asymm());

}  // namespace generated_tests::reverse

namespace generated_tests::reverse {

const TestModel& get_test_model_dim3_axis2_quant8_asymm_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // in3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // out3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({10, 8, 6, 4, 18, 16, 14, 12, 26, 24, 22, 20, 34, 32, 30, 28, 42, 40, 38, 36, 50, 48, 46, 44})
                        }, { // in3_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32, 34, 36, 38, 40, 42, 44, 46, 48, 50})
                        }, { // placeholder14
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({4})
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
                            .type = TestOperationType::REVERSE,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_dim3_axis2_quant8_asymm_all_inputs_as_internal = TestModelManager::get().add("reverse_dim3_axis2_quant8_asymm_all_inputs_as_internal", get_test_model_dim3_axis2_quant8_asymm_all_inputs_as_internal());

}  // namespace generated_tests::reverse

namespace generated_tests::reverse {

const TestModel& get_test_model_dim3_axis2_quant8_asymm_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // in3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-9, -5, -1, 3, 7, 11, 15, 19, 23, 27, 31, 35, 39, 43, 47, 51, 55, 59, 63, 67, 71, 75, 79, 83})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // out3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({3, -1, -5, -9, 19, 15, 11, 7, 35, 31, 27, 23, 51, 47, 43, 39, 67, 63, 59, 55, 83, 79, 75, 71})
                        }},
                .operations = {{
                            .type = TestOperationType::REVERSE,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_dim3_axis2_quant8_asymm_signed = TestModelManager::get().add("reverse_dim3_axis2_quant8_asymm_signed", get_test_model_dim3_axis2_quant8_asymm_signed());

}  // namespace generated_tests::reverse

namespace generated_tests::reverse {

const TestModel& get_test_model_dim3_axis2_quant8_asymm_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // in3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // out3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({3, -1, -5, -9, 19, 15, 11, 7, 35, 31, 27, 23, 51, 47, 43, 39, 67, 63, 59, 55, 83, 79, 75, 71})
                        }, { // in3_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-9, -5, -1, 3, 7, 11, 15, 19, 23, 27, 31, 35, 39, 43, 47, 51, 55, 59, 63, 67, 71, 75, 79, 83})
                        }, { // placeholder15
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-9})
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
                            .type = TestOperationType::REVERSE,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_dim3_axis2_quant8_asymm_signed_all_inputs_as_internal = TestModelManager::get().add("reverse_dim3_axis2_quant8_asymm_signed_all_inputs_as_internal", get_test_model_dim3_axis2_quant8_asymm_signed_all_inputs_as_internal());

}  // namespace generated_tests::reverse

namespace generated_tests::reverse {

const TestModel& get_test_model_dim3_axis2_int32() {
    static TestModel model = {
        .main = {
                .operands = {{ // in3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // out3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2, 1, 0, 7, 6, 5, 4, 11, 10, 9, 8, 15, 14, 13, 12, 19, 18, 17, 16, 23, 22, 21, 20})
                        }},
                .operations = {{
                            .type = TestOperationType::REVERSE,
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
        .minSupportedVersion = TestHalVersion::AIDL_V3
    };
    return model;
}

const auto dummy_test_model_dim3_axis2_int32 = TestModelManager::get().add("reverse_dim3_axis2_int32", get_test_model_dim3_axis2_int32());

}  // namespace generated_tests::reverse

