// Generated from pack.mod.py
// DO NOT EDIT
// clang-format off
#include "TestHarness.h"
using namespace test_helper;  // NOLINT(google-build-using-namespace)

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_unary_axis0() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // in0
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({3.0f, 4.0f})
                        }, { // out
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({3.0f, 4.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::PACK,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {1},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_unary_axis0 = TestModelManager::get().add("pack_FLOAT32_unary_axis0", get_test_model_FLOAT32_unary_axis0());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_unary_axis0_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // in0
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // out
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({3.0f, 4.0f})
                        }, { // in0_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({3.0f, 4.0f})
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
                            .inputs = {3, 4, 5},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::PACK,
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
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_unary_axis0_all_inputs_as_internal = TestModelManager::get().add("pack_FLOAT32_unary_axis0_all_inputs_as_internal", get_test_model_FLOAT32_unary_axis0_all_inputs_as_internal());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_unary_axis0_float16() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // in0
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({3.0f, 4.0f})
                        }, { // out
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({3.0f, 4.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::PACK,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {1},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_unary_axis0_float16 = TestModelManager::get().add("pack_FLOAT32_unary_axis0_float16", get_test_model_FLOAT32_unary_axis0_float16());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_unary_axis0_float16_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // in0
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // out
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({3.0f, 4.0f})
                        }, { // in0_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({3.0f, 4.0f})
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
                            .inputs = {3, 4, 5},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::PACK,
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
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_unary_axis0_float16_all_inputs_as_internal = TestModelManager::get().add("pack_FLOAT32_unary_axis0_float16_all_inputs_as_internal", get_test_model_FLOAT32_unary_axis0_float16_all_inputs_as_internal());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_unary_axis0_quant8_asymm() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // in0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({10, 12})
                        }, { // out
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({10, 12})
                        }},
                .operations = {{
                            .type = TestOperationType::PACK,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {1},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_unary_axis0_quant8_asymm = TestModelManager::get().add("pack_FLOAT32_unary_axis0_quant8_asymm", get_test_model_FLOAT32_unary_axis0_quant8_asymm());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_unary_axis0_quant8_asymm_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // in0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({10, 12})
                        }, { // in0_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({10, 12})
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
                            .inputs = {3, 4, 5},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::PACK,
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
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_unary_axis0_quant8_asymm_all_inputs_as_internal = TestModelManager::get().add("pack_FLOAT32_unary_axis0_quant8_asymm_all_inputs_as_internal", get_test_model_FLOAT32_unary_axis0_quant8_asymm_all_inputs_as_internal());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_unary_axis0_quant8_asymm_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // in0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({3, 7})
                        }, { // out
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({3, 7})
                        }},
                .operations = {{
                            .type = TestOperationType::PACK,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {1},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_unary_axis0_quant8_asymm_signed = TestModelManager::get().add("pack_FLOAT32_unary_axis0_quant8_asymm_signed", get_test_model_FLOAT32_unary_axis0_quant8_asymm_signed());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_unary_axis0_quant8_asymm_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // in0
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // out
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({3, 7})
                        }, { // in0_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({3, 7})
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
                            .inputs = {3, 4, 5},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::PACK,
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
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_unary_axis0_quant8_asymm_signed_all_inputs_as_internal = TestModelManager::get().add("pack_FLOAT32_unary_axis0_quant8_asymm_signed_all_inputs_as_internal", get_test_model_FLOAT32_unary_axis0_quant8_asymm_signed_all_inputs_as_internal());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_unary_axis0_int32() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // in0
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 4})
                        }, { // out
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 4})
                        }},
                .operations = {{
                            .type = TestOperationType::PACK,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {1},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_unary_axis0_int32 = TestModelManager::get().add("pack_FLOAT32_unary_axis0_int32", get_test_model_FLOAT32_unary_axis0_int32());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_unary_axis1() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis1
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // in01
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({3.0f, 4.0f})
                        }, { // out1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({3.0f, 4.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::PACK,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {1},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_unary_axis1 = TestModelManager::get().add("pack_FLOAT32_unary_axis1", get_test_model_FLOAT32_unary_axis1());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_unary_axis1_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis1
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // in01
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // out1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({3.0f, 4.0f})
                        }, { // in01_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({3.0f, 4.0f})
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
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::PACK,
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
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_unary_axis1_all_inputs_as_internal = TestModelManager::get().add("pack_FLOAT32_unary_axis1_all_inputs_as_internal", get_test_model_FLOAT32_unary_axis1_all_inputs_as_internal());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_unary_axis1_float16() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis1
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // in01
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({3.0f, 4.0f})
                        }, { // out1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({3.0f, 4.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::PACK,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {1},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_unary_axis1_float16 = TestModelManager::get().add("pack_FLOAT32_unary_axis1_float16", get_test_model_FLOAT32_unary_axis1_float16());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_unary_axis1_float16_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis1
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // in01
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // out1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({3.0f, 4.0f})
                        }, { // in01_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({3.0f, 4.0f})
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
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::PACK,
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
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_unary_axis1_float16_all_inputs_as_internal = TestModelManager::get().add("pack_FLOAT32_unary_axis1_float16_all_inputs_as_internal", get_test_model_FLOAT32_unary_axis1_float16_all_inputs_as_internal());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_unary_axis1_quant8_asymm() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis1
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // in01
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({10, 12})
                        }, { // out1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({10, 12})
                        }},
                .operations = {{
                            .type = TestOperationType::PACK,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {1},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_unary_axis1_quant8_asymm = TestModelManager::get().add("pack_FLOAT32_unary_axis1_quant8_asymm", get_test_model_FLOAT32_unary_axis1_quant8_asymm());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_unary_axis1_quant8_asymm_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis1
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // in01
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({10, 12})
                        }, { // in01_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({10, 12})
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
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::PACK,
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
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_unary_axis1_quant8_asymm_all_inputs_as_internal = TestModelManager::get().add("pack_FLOAT32_unary_axis1_quant8_asymm_all_inputs_as_internal", get_test_model_FLOAT32_unary_axis1_quant8_asymm_all_inputs_as_internal());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_unary_axis1_quant8_asymm_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis1
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // in01
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({3, 7})
                        }, { // out1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({3, 7})
                        }},
                .operations = {{
                            .type = TestOperationType::PACK,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {1},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_unary_axis1_quant8_asymm_signed = TestModelManager::get().add("pack_FLOAT32_unary_axis1_quant8_asymm_signed", get_test_model_FLOAT32_unary_axis1_quant8_asymm_signed());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_unary_axis1_quant8_asymm_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis1
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // in01
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // out1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({3, 7})
                        }, { // in01_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({3, 7})
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
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::PACK,
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
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_unary_axis1_quant8_asymm_signed_all_inputs_as_internal = TestModelManager::get().add("pack_FLOAT32_unary_axis1_quant8_asymm_signed_all_inputs_as_internal", get_test_model_FLOAT32_unary_axis1_quant8_asymm_signed_all_inputs_as_internal());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_unary_axis1_int32() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis1
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // in01
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 4})
                        }, { // out1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 4})
                        }},
                .operations = {{
                            .type = TestOperationType::PACK,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {1},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_unary_axis1_int32 = TestModelManager::get().add("pack_FLOAT32_unary_axis1_int32", get_test_model_FLOAT32_unary_axis1_int32());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_binary_axis0() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis2
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // in02
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f})
                        }, { // in1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f})
                        }, { // out2
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::PACK,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {1, 2},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_binary_axis0 = TestModelManager::get().add("pack_FLOAT32_binary_axis0", get_test_model_FLOAT32_binary_axis0());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_binary_axis0_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis2
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // in02
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // in1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // out2
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f})
                        }, { // in02_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f})
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
                        }, { // in1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f})
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
                            .inputs = {4, 5, 6},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {2}
                        }, {
                            .type = TestOperationType::PACK,
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
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_binary_axis0_all_inputs_as_internal = TestModelManager::get().add("pack_FLOAT32_binary_axis0_all_inputs_as_internal", get_test_model_FLOAT32_binary_axis0_all_inputs_as_internal());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_binary_axis0_float16() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis2
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // in02
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f})
                        }, { // in1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f})
                        }, { // out2
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::PACK,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {1, 2},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_binary_axis0_float16 = TestModelManager::get().add("pack_FLOAT32_binary_axis0_float16", get_test_model_FLOAT32_binary_axis0_float16());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_binary_axis0_float16_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis2
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // in02
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // in1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // out2
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f})
                        }, { // in02_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f})
                        }, { // placeholder10
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
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
                        }, { // in1_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f})
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
                            .inputs = {4, 5, 6},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {2}
                        }, {
                            .type = TestOperationType::PACK,
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
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_binary_axis0_float16_all_inputs_as_internal = TestModelManager::get().add("pack_FLOAT32_binary_axis0_float16_all_inputs_as_internal", get_test_model_FLOAT32_binary_axis0_float16_all_inputs_as_internal());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_binary_axis0_quant8_asymm() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis2
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // in02
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26})
                        }, { // in1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({28, 30, 32, 34, 36, 38, 40, 42, 44, 46, 48, 50})
                        }, { // out2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32, 34, 36, 38, 40, 42, 44, 46, 48, 50})
                        }},
                .operations = {{
                            .type = TestOperationType::PACK,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {1, 2},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_binary_axis0_quant8_asymm = TestModelManager::get().add("pack_FLOAT32_binary_axis0_quant8_asymm", get_test_model_FLOAT32_binary_axis0_quant8_asymm());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_binary_axis0_quant8_asymm_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis2
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // in02
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // in1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32, 34, 36, 38, 40, 42, 44, 46, 48, 50})
                        }, { // in02_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26})
                        }, { // placeholder12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({4})
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
                        }, { // in1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({28, 30, 32, 34, 36, 38, 40, 42, 44, 46, 48, 50})
                        }, { // placeholder13
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({4})
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
                            .inputs = {4, 5, 6},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {2}
                        }, {
                            .type = TestOperationType::PACK,
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
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_binary_axis0_quant8_asymm_all_inputs_as_internal = TestModelManager::get().add("pack_FLOAT32_binary_axis0_quant8_asymm_all_inputs_as_internal", get_test_model_FLOAT32_binary_axis0_quant8_asymm_all_inputs_as_internal());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_binary_axis0_quant8_asymm_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis2
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // in02
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-9, -5, -1, 3, 7, 11, 15, 19, 23, 27, 31, 35})
                        }, { // in1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({39, 43, 47, 51, 55, 59, 63, 67, 71, 75, 79, 83})
                        }, { // out2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-9, -5, -1, 3, 7, 11, 15, 19, 23, 27, 31, 35, 39, 43, 47, 51, 55, 59, 63, 67, 71, 75, 79, 83})
                        }},
                .operations = {{
                            .type = TestOperationType::PACK,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {1, 2},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_binary_axis0_quant8_asymm_signed = TestModelManager::get().add("pack_FLOAT32_binary_axis0_quant8_asymm_signed", get_test_model_FLOAT32_binary_axis0_quant8_asymm_signed());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_binary_axis0_quant8_asymm_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis2
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // in02
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // in1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // out2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-9, -5, -1, 3, 7, 11, 15, 19, 23, 27, 31, 35, 39, 43, 47, 51, 55, 59, 63, 67, 71, 75, 79, 83})
                        }, { // in02_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-9, -5, -1, 3, 7, 11, 15, 19, 23, 27, 31, 35})
                        }, { // placeholder14
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-9})
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
                        }, { // in1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({39, 43, 47, 51, 55, 59, 63, 67, 71, 75, 79, 83})
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
                            .inputs = {4, 5, 6},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {2}
                        }, {
                            .type = TestOperationType::PACK,
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
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_binary_axis0_quant8_asymm_signed_all_inputs_as_internal = TestModelManager::get().add("pack_FLOAT32_binary_axis0_quant8_asymm_signed_all_inputs_as_internal", get_test_model_FLOAT32_binary_axis0_quant8_asymm_signed_all_inputs_as_internal());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_binary_axis0_int32() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis2
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // in02
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11})
                        }, { // in1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23})
                        }, { // out2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 3, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23})
                        }},
                .operations = {{
                            .type = TestOperationType::PACK,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {1, 2},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_binary_axis0_int32 = TestModelManager::get().add("pack_FLOAT32_binary_axis0_int32", get_test_model_FLOAT32_binary_axis0_int32());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_binary_axis1() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis3
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // in03
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f})
                        }, { // in11
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f})
                        }, { // out3
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 2, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 12.0f, 13.0f, 14.0f, 15.0f, 4.0f, 5.0f, 6.0f, 7.0f, 16.0f, 17.0f, 18.0f, 19.0f, 8.0f, 9.0f, 10.0f, 11.0f, 20.0f, 21.0f, 22.0f, 23.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::PACK,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {1, 2},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_binary_axis1 = TestModelManager::get().add("pack_FLOAT32_binary_axis1", get_test_model_FLOAT32_binary_axis1());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_binary_axis1_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis3
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // in03
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // in11
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // out3
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 2, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 12.0f, 13.0f, 14.0f, 15.0f, 4.0f, 5.0f, 6.0f, 7.0f, 16.0f, 17.0f, 18.0f, 19.0f, 8.0f, 9.0f, 10.0f, 11.0f, 20.0f, 21.0f, 22.0f, 23.0f})
                        }, { // in03_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f})
                        }, { // placeholder16
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
                        }, { // in11_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f})
                        }, { // placeholder17
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
                            .inputs = {4, 5, 6},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {2}
                        }, {
                            .type = TestOperationType::PACK,
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
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_binary_axis1_all_inputs_as_internal = TestModelManager::get().add("pack_FLOAT32_binary_axis1_all_inputs_as_internal", get_test_model_FLOAT32_binary_axis1_all_inputs_as_internal());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_binary_axis1_float16() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis3
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // in03
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f})
                        }, { // in11
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f})
                        }, { // out3
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 2, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 1.0f, 2.0f, 3.0f, 12.0f, 13.0f, 14.0f, 15.0f, 4.0f, 5.0f, 6.0f, 7.0f, 16.0f, 17.0f, 18.0f, 19.0f, 8.0f, 9.0f, 10.0f, 11.0f, 20.0f, 21.0f, 22.0f, 23.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::PACK,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {1, 2},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_binary_axis1_float16 = TestModelManager::get().add("pack_FLOAT32_binary_axis1_float16", get_test_model_FLOAT32_binary_axis1_float16());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_binary_axis1_float16_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis3
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // in03
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // in11
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // out3
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 2, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 1.0f, 2.0f, 3.0f, 12.0f, 13.0f, 14.0f, 15.0f, 4.0f, 5.0f, 6.0f, 7.0f, 16.0f, 17.0f, 18.0f, 19.0f, 8.0f, 9.0f, 10.0f, 11.0f, 20.0f, 21.0f, 22.0f, 23.0f})
                        }, { // in03_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f})
                        }, { // placeholder18
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
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
                        }, { // in11_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f})
                        }, { // placeholder19
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
                            .inputs = {4, 5, 6},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {2}
                        }, {
                            .type = TestOperationType::PACK,
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
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_binary_axis1_float16_all_inputs_as_internal = TestModelManager::get().add("pack_FLOAT32_binary_axis1_float16_all_inputs_as_internal", get_test_model_FLOAT32_binary_axis1_float16_all_inputs_as_internal());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_binary_axis1_quant8_asymm() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis3
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // in03
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26})
                        }, { // in11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({28, 30, 32, 34, 36, 38, 40, 42, 44, 46, 48, 50})
                        }, { // out3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 2, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({4, 6, 8, 10, 28, 30, 32, 34, 12, 14, 16, 18, 36, 38, 40, 42, 20, 22, 24, 26, 44, 46, 48, 50})
                        }},
                .operations = {{
                            .type = TestOperationType::PACK,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {1, 2},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_binary_axis1_quant8_asymm = TestModelManager::get().add("pack_FLOAT32_binary_axis1_quant8_asymm", get_test_model_FLOAT32_binary_axis1_quant8_asymm());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_binary_axis1_quant8_asymm_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis3
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // in03
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // in11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 2, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({4, 6, 8, 10, 28, 30, 32, 34, 12, 14, 16, 18, 36, 38, 40, 42, 20, 22, 24, 26, 44, 46, 48, 50})
                        }, { // in03_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26})
                        }, { // placeholder20
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({4})
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
                        }, { // in11_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({28, 30, 32, 34, 36, 38, 40, 42, 44, 46, 48, 50})
                        }, { // placeholder21
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({4})
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
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {4, 5, 6},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {2}
                        }, {
                            .type = TestOperationType::PACK,
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
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_binary_axis1_quant8_asymm_all_inputs_as_internal = TestModelManager::get().add("pack_FLOAT32_binary_axis1_quant8_asymm_all_inputs_as_internal", get_test_model_FLOAT32_binary_axis1_quant8_asymm_all_inputs_as_internal());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_binary_axis1_quant8_asymm_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis3
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // in03
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-9, -5, -1, 3, 7, 11, 15, 19, 23, 27, 31, 35})
                        }, { // in11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({39, 43, 47, 51, 55, 59, 63, 67, 71, 75, 79, 83})
                        }, { // out3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 2, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-9, -5, -1, 3, 39, 43, 47, 51, 7, 11, 15, 19, 55, 59, 63, 67, 23, 27, 31, 35, 71, 75, 79, 83})
                        }},
                .operations = {{
                            .type = TestOperationType::PACK,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {1, 2},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_binary_axis1_quant8_asymm_signed = TestModelManager::get().add("pack_FLOAT32_binary_axis1_quant8_asymm_signed", get_test_model_FLOAT32_binary_axis1_quant8_asymm_signed());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_binary_axis1_quant8_asymm_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis3
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // in03
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // in11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // out3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 2, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-9, -5, -1, 3, 39, 43, 47, 51, 7, 11, 15, 19, 55, 59, 63, 67, 23, 27, 31, 35, 71, 75, 79, 83})
                        }, { // in03_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-9, -5, -1, 3, 7, 11, 15, 19, 23, 27, 31, 35})
                        }, { // placeholder22
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-9})
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
                        }, { // in11_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({39, 43, 47, 51, 55, 59, 63, 67, 71, 75, 79, 83})
                        }, { // placeholder23
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-9})
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
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {4, 5, 6},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {2}
                        }, {
                            .type = TestOperationType::PACK,
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
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_binary_axis1_quant8_asymm_signed_all_inputs_as_internal = TestModelManager::get().add("pack_FLOAT32_binary_axis1_quant8_asymm_signed_all_inputs_as_internal", get_test_model_FLOAT32_binary_axis1_quant8_asymm_signed_all_inputs_as_internal());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_binary_axis1_int32() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis3
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // in03
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11})
                        }, { // in11
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23})
                        }, { // out3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3, 2, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 1, 2, 3, 12, 13, 14, 15, 4, 5, 6, 7, 16, 17, 18, 19, 8, 9, 10, 11, 20, 21, 22, 23})
                        }},
                .operations = {{
                            .type = TestOperationType::PACK,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {1, 2},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_binary_axis1_int32 = TestModelManager::get().add("pack_FLOAT32_binary_axis1_int32", get_test_model_FLOAT32_binary_axis1_int32());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_binary_axis2() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis4
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // in04
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f})
                        }, { // in12
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f})
                        }, { // out4
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 1.0f, 13.0f, 2.0f, 14.0f, 3.0f, 15.0f, 4.0f, 16.0f, 5.0f, 17.0f, 6.0f, 18.0f, 7.0f, 19.0f, 8.0f, 20.0f, 9.0f, 21.0f, 10.0f, 22.0f, 11.0f, 23.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::PACK,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {1, 2},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_binary_axis2 = TestModelManager::get().add("pack_FLOAT32_binary_axis2", get_test_model_FLOAT32_binary_axis2());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_binary_axis2_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis4
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // in04
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // in12
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // out4
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 1.0f, 13.0f, 2.0f, 14.0f, 3.0f, 15.0f, 4.0f, 16.0f, 5.0f, 17.0f, 6.0f, 18.0f, 7.0f, 19.0f, 8.0f, 20.0f, 9.0f, 21.0f, 10.0f, 22.0f, 11.0f, 23.0f})
                        }, { // in04_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f})
                        }, { // placeholder24
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
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
                        }, { // in12_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f})
                        }, { // placeholder25
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
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
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {4, 5, 6},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {2}
                        }, {
                            .type = TestOperationType::PACK,
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
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_binary_axis2_all_inputs_as_internal = TestModelManager::get().add("pack_FLOAT32_binary_axis2_all_inputs_as_internal", get_test_model_FLOAT32_binary_axis2_all_inputs_as_internal());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_binary_axis2_float16() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis4
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // in04
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f})
                        }, { // in12
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f})
                        }, { // out4
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 1.0f, 13.0f, 2.0f, 14.0f, 3.0f, 15.0f, 4.0f, 16.0f, 5.0f, 17.0f, 6.0f, 18.0f, 7.0f, 19.0f, 8.0f, 20.0f, 9.0f, 21.0f, 10.0f, 22.0f, 11.0f, 23.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::PACK,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {1, 2},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_binary_axis2_float16 = TestModelManager::get().add("pack_FLOAT32_binary_axis2_float16", get_test_model_FLOAT32_binary_axis2_float16());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_binary_axis2_float16_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis4
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // in04
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // in12
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // out4
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 1.0f, 13.0f, 2.0f, 14.0f, 3.0f, 15.0f, 4.0f, 16.0f, 5.0f, 17.0f, 6.0f, 18.0f, 7.0f, 19.0f, 8.0f, 20.0f, 9.0f, 21.0f, 10.0f, 22.0f, 11.0f, 23.0f})
                        }, { // in04_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f})
                        }, { // placeholder26
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
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
                        }, { // in12_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f})
                        }, { // placeholder27
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
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
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {4, 5, 6},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {2}
                        }, {
                            .type = TestOperationType::PACK,
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
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_binary_axis2_float16_all_inputs_as_internal = TestModelManager::get().add("pack_FLOAT32_binary_axis2_float16_all_inputs_as_internal", get_test_model_FLOAT32_binary_axis2_float16_all_inputs_as_internal());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_binary_axis2_quant8_asymm() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis4
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // in04
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26})
                        }, { // in12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({28, 30, 32, 34, 36, 38, 40, 42, 44, 46, 48, 50})
                        }, { // out4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({4, 28, 6, 30, 8, 32, 10, 34, 12, 36, 14, 38, 16, 40, 18, 42, 20, 44, 22, 46, 24, 48, 26, 50})
                        }},
                .operations = {{
                            .type = TestOperationType::PACK,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {1, 2},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_binary_axis2_quant8_asymm = TestModelManager::get().add("pack_FLOAT32_binary_axis2_quant8_asymm", get_test_model_FLOAT32_binary_axis2_quant8_asymm());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_binary_axis2_quant8_asymm_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis4
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // in04
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // in12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // out4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({4, 28, 6, 30, 8, 32, 10, 34, 12, 36, 14, 38, 16, 40, 18, 42, 20, 44, 22, 46, 24, 48, 26, 50})
                        }, { // in04_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26})
                        }, { // placeholder28
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({4})
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
                        }, { // in12_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({28, 30, 32, 34, 36, 38, 40, 42, 44, 46, 48, 50})
                        }, { // placeholder29
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 4,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({4})
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
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {4, 5, 6},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {2}
                        }, {
                            .type = TestOperationType::PACK,
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
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_binary_axis2_quant8_asymm_all_inputs_as_internal = TestModelManager::get().add("pack_FLOAT32_binary_axis2_quant8_asymm_all_inputs_as_internal", get_test_model_FLOAT32_binary_axis2_quant8_asymm_all_inputs_as_internal());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_binary_axis2_quant8_asymm_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis4
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // in04
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-9, -5, -1, 3, 7, 11, 15, 19, 23, 27, 31, 35})
                        }, { // in12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({39, 43, 47, 51, 55, 59, 63, 67, 71, 75, 79, 83})
                        }, { // out4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-9, 39, -5, 43, -1, 47, 3, 51, 7, 55, 11, 59, 15, 63, 19, 67, 23, 71, 27, 75, 31, 79, 35, 83})
                        }},
                .operations = {{
                            .type = TestOperationType::PACK,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {1, 2},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_binary_axis2_quant8_asymm_signed = TestModelManager::get().add("pack_FLOAT32_binary_axis2_quant8_asymm_signed", get_test_model_FLOAT32_binary_axis2_quant8_asymm_signed());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_binary_axis2_quant8_asymm_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis4
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // in04
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // in12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // out4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-9, 39, -5, 43, -1, 47, 3, 51, 7, 55, 11, 59, 15, 63, 19, 67, 23, 71, 27, 75, 31, 79, 35, 83})
                        }, { // in04_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-9, -5, -1, 3, 7, 11, 15, 19, 23, 27, 31, 35})
                        }, { // placeholder30
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-9})
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
                        }, { // in12_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({39, 43, 47, 51, 55, 59, 63, 67, 71, 75, 79, 83})
                        }, { // placeholder31
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -9,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-9})
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
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {4, 5, 6},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {7, 8, 9},
                            .outputs = {2}
                        }, {
                            .type = TestOperationType::PACK,
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
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_binary_axis2_quant8_asymm_signed_all_inputs_as_internal = TestModelManager::get().add("pack_FLOAT32_binary_axis2_quant8_asymm_signed_all_inputs_as_internal", get_test_model_FLOAT32_binary_axis2_quant8_asymm_signed_all_inputs_as_internal());

}  // namespace generated_tests::pack

namespace generated_tests::pack {

const TestModel& get_test_model_FLOAT32_binary_axis2_int32() {
    static TestModel model = {
        .main = {
                .operands = {{ // axis4
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // in04
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11})
                        }, { // in12
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23})
                        }, { // out4
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {3, 4, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 12, 1, 13, 2, 14, 3, 15, 4, 16, 5, 17, 6, 18, 7, 19, 8, 20, 9, 21, 10, 22, 11, 23})
                        }},
                .operations = {{
                            .type = TestOperationType::PACK,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {1, 2},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::AIDL_V2
    };
    return model;
}

const auto dummy_test_model_FLOAT32_binary_axis2_int32 = TestModelManager::get().add("pack_FLOAT32_binary_axis2_int32", get_test_model_FLOAT32_binary_axis2_int32());

}  // namespace generated_tests::pack

