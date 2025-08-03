// Generated from prelu_quant8_signed.mod.py
// DO NOT EDIT
// clang-format off
#include "TestHarness.h"
using namespace test_helper;  // NOLINT(google-build-using-namespace)

namespace generated_tests::prelu_quant8_signed {

const TestModel& get_test_model_quant8_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 0, 0, 4, 4, 4, -4, -4, -4, -8, -8, -8})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -78,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-78, -74, -70})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -8, -8, -6, -6, -6, -8, -10, -12, -8, -12, -16})
                        }},
                .operations = {{
                            .type = TestOperationType::PRELU,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_quant8_signed = TestModelManager::get().add("prelu_quant8_signed_quant8_signed", get_test_model_quant8_signed());

}  // namespace generated_tests::prelu_quant8_signed

namespace generated_tests::prelu_quant8_signed {

const TestModel& get_test_model_quant8_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -78,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-78, -74, -70})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -8, -8, -6, -6, -6, -8, -10, -12, -8, -12, -16})
                        }, { // input_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 0, 0, 4, 4, 4, -4, -4, -4, -8, -8, -8})
                        }, { // placeholder
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0})
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
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::PRELU,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_quant8_signed_all_inputs_as_internal = TestModelManager::get().add("prelu_quant8_signed_quant8_signed_all_inputs_as_internal", get_test_model_quant8_signed_all_inputs_as_internal());

}  // namespace generated_tests::prelu_quant8_signed

namespace generated_tests::prelu_quant8_signed {

const TestModel& get_test_model_quant8_signed_all_tensors_as_inputs() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 0, 0, 4, 4, 4, -4, -4, -4, -8, -8, -8})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -78,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-78, -74, -70})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -8, -8, -6, -6, -6, -8, -10, -12, -8, -12, -16})
                        }},
                .operations = {{
                            .type = TestOperationType::PRELU,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {0, 1},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_quant8_signed_all_tensors_as_inputs = TestModelManager::get().add("prelu_quant8_signed_quant8_signed_all_tensors_as_inputs", get_test_model_quant8_signed_all_tensors_as_inputs());

}  // namespace generated_tests::prelu_quant8_signed

namespace generated_tests::prelu_quant8_signed {

const TestModel& get_test_model_quant8_signed_all_tensors_as_inputs_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -78,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -8, -8, -6, -6, -6, -8, -10, -12, -8, -12, -16})
                        }, { // input_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 0, 0, 4, 4, 4, -4, -4, -4, -8, -8, -8})
                        }, { // placeholder1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0})
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
                        }, { // alpha_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -78,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-78, -74, -70})
                        }, { // placeholder2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -78,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-78})
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
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {6, 7, 8},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::PRELU,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {3, 6},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_quant8_signed_all_tensors_as_inputs_all_inputs_as_internal = TestModelManager::get().add("prelu_quant8_signed_quant8_signed_all_tensors_as_inputs_all_inputs_as_internal", get_test_model_quant8_signed_all_tensors_as_inputs_all_inputs_as_internal());

}  // namespace generated_tests::prelu_quant8_signed

namespace generated_tests::prelu_quant8_signed {

const TestModel& get_test_model_quant8_signed_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 0, 0, 4, 4, 4, -4, -4, -4, -8, -8, -8})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -78,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-78, -74, -70})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -8, -8, -4, -4, -4, -8, -12, -16, -8, -16, -24})
                        }},
                .operations = {{
                            .type = TestOperationType::PRELU,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_quant8_signed_2 = TestModelManager::get().add("prelu_quant8_signed_quant8_signed_2", get_test_model_quant8_signed_2());

}  // namespace generated_tests::prelu_quant8_signed

namespace generated_tests::prelu_quant8_signed {

const TestModel& get_test_model_quant8_signed_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -78,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-78, -74, -70})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -8, -8, -4, -4, -4, -8, -12, -16, -8, -16, -24})
                        }, { // input_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 0, 0, 4, 4, 4, -4, -4, -4, -8, -8, -8})
                        }, { // placeholder3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0})
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
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::PRELU,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_quant8_signed_all_inputs_as_internal_2 = TestModelManager::get().add("prelu_quant8_signed_quant8_signed_all_inputs_as_internal_2", get_test_model_quant8_signed_all_inputs_as_internal_2());

}  // namespace generated_tests::prelu_quant8_signed

namespace generated_tests::prelu_quant8_signed {

const TestModel& get_test_model_quant8_signed_all_tensors_as_inputs_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 0, 0, 4, 4, 4, -4, -4, -4, -8, -8, -8})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -78,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-78, -74, -70})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -8, -8, -4, -4, -4, -8, -12, -16, -8, -16, -24})
                        }},
                .operations = {{
                            .type = TestOperationType::PRELU,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {0, 1},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_quant8_signed_all_tensors_as_inputs_2 = TestModelManager::get().add("prelu_quant8_signed_quant8_signed_all_tensors_as_inputs_2", get_test_model_quant8_signed_all_tensors_as_inputs_2());

}  // namespace generated_tests::prelu_quant8_signed

namespace generated_tests::prelu_quant8_signed {

const TestModel& get_test_model_quant8_signed_all_tensors_as_inputs_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -78,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -8, -8, -4, -4, -4, -8, -12, -16, -8, -16, -24})
                        }, { // input_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 0, 0, 4, 4, 4, -4, -4, -4, -8, -8, -8})
                        }, { // placeholder4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
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
                        }, { // alpha_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -78,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-78, -74, -70})
                        }, { // placeholder5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -78,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-78})
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
                            .type = TestOperationType::ADD,
                            .inputs = {6, 7, 8},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::PRELU,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {3, 6},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_quant8_signed_all_tensors_as_inputs_all_inputs_as_internal_2 = TestModelManager::get().add("prelu_quant8_signed_quant8_signed_all_tensors_as_inputs_all_inputs_as_internal_2", get_test_model_quant8_signed_all_tensors_as_inputs_all_inputs_as_internal_2());

}  // namespace generated_tests::prelu_quant8_signed

namespace generated_tests::prelu_quant8_signed {

const TestModel& get_test_model_quant8_signed_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 0, 0, 4, 4, 4, -4, -4, -4, -8, -8, -8})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = -78,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-78, -76, -74})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.125f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -8, -8, 0, 0, 0, -8, -16, -24, -8, -24, -40})
                        }},
                .operations = {{
                            .type = TestOperationType::PRELU,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_quant8_signed_3 = TestModelManager::get().add("prelu_quant8_signed_quant8_signed_3", get_test_model_quant8_signed_3());

}  // namespace generated_tests::prelu_quant8_signed

namespace generated_tests::prelu_quant8_signed {

const TestModel& get_test_model_quant8_signed_all_inputs_as_internal_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = -78,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-78, -76, -74})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.125f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -8, -8, 0, 0, 0, -8, -16, -24, -8, -24, -40})
                        }, { // input_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 0, 0, 4, 4, 4, -4, -4, -4, -8, -8, -8})
                        }, { // placeholder6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
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
                            .inputs = {3, 4, 5},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::PRELU,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_quant8_signed_all_inputs_as_internal_3 = TestModelManager::get().add("prelu_quant8_signed_quant8_signed_all_inputs_as_internal_3", get_test_model_quant8_signed_all_inputs_as_internal_3());

}  // namespace generated_tests::prelu_quant8_signed

namespace generated_tests::prelu_quant8_signed {

const TestModel& get_test_model_quant8_signed_all_tensors_as_inputs_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 0, 0, 4, 4, 4, -4, -4, -4, -8, -8, -8})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = -78,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-78, -76, -74})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.125f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -8, -8, 0, 0, 0, -8, -16, -24, -8, -24, -40})
                        }},
                .operations = {{
                            .type = TestOperationType::PRELU,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {0, 1},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_quant8_signed_all_tensors_as_inputs_3 = TestModelManager::get().add("prelu_quant8_signed_quant8_signed_all_tensors_as_inputs_3", get_test_model_quant8_signed_all_tensors_as_inputs_3());

}  // namespace generated_tests::prelu_quant8_signed

namespace generated_tests::prelu_quant8_signed {

const TestModel& get_test_model_quant8_signed_all_tensors_as_inputs_all_inputs_as_internal_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = -78,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.125f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -8, -8, 0, 0, 0, -8, -16, -24, -8, -24, -40})
                        }, { // input_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 0, 0, 4, 4, 4, -4, -4, -4, -8, -8, -8})
                        }, { // placeholder7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0})
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
                        }, { // alpha_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = -78,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-78, -76, -74})
                        }, { // placeholder8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = -78,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-78})
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
                            .type = TestOperationType::ADD,
                            .inputs = {6, 7, 8},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::PRELU,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {3, 6},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_quant8_signed_all_tensors_as_inputs_all_inputs_as_internal_3 = TestModelManager::get().add("prelu_quant8_signed_quant8_signed_all_tensors_as_inputs_all_inputs_as_internal_3", get_test_model_quant8_signed_all_tensors_as_inputs_all_inputs_as_internal_3());

}  // namespace generated_tests::prelu_quant8_signed

namespace generated_tests::prelu_quant8_signed {

const TestModel& get_test_model_quant8_signed_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 0, 0, 4, 4, 4, -4, -4, -4, -8, -8, -8})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = -78,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-78, -76, -74})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -8, -8, 2, 2, 2, -8, -18, -28, -8, -28, -48})
                        }},
                .operations = {{
                            .type = TestOperationType::PRELU,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_quant8_signed_4 = TestModelManager::get().add("prelu_quant8_signed_quant8_signed_4", get_test_model_quant8_signed_4());

}  // namespace generated_tests::prelu_quant8_signed

namespace generated_tests::prelu_quant8_signed {

const TestModel& get_test_model_quant8_signed_all_inputs_as_internal_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = -78,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-78, -76, -74})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -8, -8, 2, 2, 2, -8, -18, -28, -8, -28, -48})
                        }, { // input_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 0, 0, 4, 4, 4, -4, -4, -4, -8, -8, -8})
                        }, { // placeholder9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0})
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
                            .type = TestOperationType::PRELU,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_quant8_signed_all_inputs_as_internal_4 = TestModelManager::get().add("prelu_quant8_signed_quant8_signed_all_inputs_as_internal_4", get_test_model_quant8_signed_all_inputs_as_internal_4());

}  // namespace generated_tests::prelu_quant8_signed

namespace generated_tests::prelu_quant8_signed {

const TestModel& get_test_model_quant8_signed_all_tensors_as_inputs_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 0, 0, 4, 4, 4, -4, -4, -4, -8, -8, -8})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = -78,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-78, -76, -74})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -8, -8, 2, 2, 2, -8, -18, -28, -8, -28, -48})
                        }},
                .operations = {{
                            .type = TestOperationType::PRELU,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {0, 1},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_quant8_signed_all_tensors_as_inputs_4 = TestModelManager::get().add("prelu_quant8_signed_quant8_signed_all_tensors_as_inputs_4", get_test_model_quant8_signed_all_tensors_as_inputs_4());

}  // namespace generated_tests::prelu_quant8_signed

namespace generated_tests::prelu_quant8_signed {

const TestModel& get_test_model_quant8_signed_all_tensors_as_inputs_all_inputs_as_internal_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = -78,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = -8,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, -8, -8, 2, 2, 2, -8, -18, -28, -8, -28, -48})
                        }, { // input_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 0, 0, 4, 4, 4, -4, -4, -4, -8, -8, -8})
                        }, { // placeholder10
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0})
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
                        }, { // alpha_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = -78,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-78, -76, -74})
                        }, { // placeholder11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = -78,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-78})
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
                            .type = TestOperationType::ADD,
                            .inputs = {6, 7, 8},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::PRELU,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {3, 6},
                .outputIndexes = {2}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_quant8_signed_all_tensors_as_inputs_all_inputs_as_internal_4 = TestModelManager::get().add("prelu_quant8_signed_quant8_signed_all_tensors_as_inputs_all_inputs_as_internal_4", get_test_model_quant8_signed_all_tensors_as_inputs_all_inputs_as_internal_4());

}  // namespace generated_tests::prelu_quant8_signed

