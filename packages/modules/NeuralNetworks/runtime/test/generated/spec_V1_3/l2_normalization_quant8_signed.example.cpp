// Generated from l2_normalization_quant8_signed.mod.py
// DO NOT EDIT
// clang-format off
#include "TestHarness.h"
using namespace test_helper;  // NOLINT(google-build-using-namespace)

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim4_axis0_quant8_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -16, 24, -6, 24, 104, 64, -66, -96, -36, -96, 24, 54, -6, 54, -56, -56, -96, -6, 104, 64, 24, 24})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 2, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 102, 102, 46, 61, 102, 82, 77, 0, 77, 0, 61, 77, 46, 77, 102, 102, 0, 77, 102, 82, 61, 61})
                        }},
                .operations = {{
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim4_axis0_quant8_signed = TestModelManager::get().add("l2_normalization_quant8_signed_dim4_axis0_quant8_signed", get_test_model_dim4_axis0_quant8_signed());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim4_axis0_quant8_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 2, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 102, 102, 46, 61, 102, 82, 77, 0, 77, 0, 61, 77, 46, 77, 102, 102, 0, 77, 102, 82, 61, 61})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -16, 24, -6, 24, 104, 64, -66, -96, -36, -96, 24, 54, -6, 54, -56, -56, -96, -6, 104, 64, 24, 24})
                        }, { // placeholder
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96})
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
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim4_axis0_quant8_signed_all_inputs_as_internal = TestModelManager::get().add("l2_normalization_quant8_signed_dim4_axis0_quant8_signed_all_inputs_as_internal", get_test_model_dim4_axis0_quant8_signed_all_inputs_as_internal());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim4_axis0_neg_quant8_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -16, 24, -6, 24, 104, 64, -66, -96, -36, -96, 24, 54, -6, 54, -56, -56, -96, -6, 104, 64, 24, 24})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-4})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 2, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 102, 102, 46, 61, 102, 82, 77, 0, 77, 0, 61, 77, 46, 77, 102, 102, 0, 77, 102, 82, 61, 61})
                        }},
                .operations = {{
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim4_axis0_neg_quant8_signed = TestModelManager::get().add("l2_normalization_quant8_signed_dim4_axis0_neg_quant8_signed", get_test_model_dim4_axis0_neg_quant8_signed());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim4_axis0_neg_quant8_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-4})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 2, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 102, 102, 46, 61, 102, 82, 77, 0, 77, 0, 61, 77, 46, 77, 102, 102, 0, 77, 102, 82, 61, 61})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -16, 24, -6, 24, 104, 64, -66, -96, -36, -96, 24, 54, -6, 54, -56, -56, -96, -6, 104, 64, 24, 24})
                        }, { // placeholder1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96})
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
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim4_axis0_neg_quant8_signed_all_inputs_as_internal = TestModelManager::get().add("l2_normalization_quant8_signed_dim4_axis0_neg_quant8_signed_all_inputs_as_internal", get_test_model_dim4_axis0_neg_quant8_signed_all_inputs_as_internal());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim4_axis1_quant8_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -16, 24, -66, -96, -36, -96, -56, -56, -96, -6, -6, 24, 104, 64, 24, 54, -6, 54, 104, 64, 24, 24})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 102, 102, 77, 0, 77, 0, 102, 102, 0, 77, 46, 61, 102, 82, 61, 77, 46, 77, 102, 82, 61, 61})
                        }},
                .operations = {{
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim4_axis1_quant8_signed = TestModelManager::get().add("l2_normalization_quant8_signed_dim4_axis1_quant8_signed", get_test_model_dim4_axis1_quant8_signed());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim4_axis1_quant8_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 102, 102, 77, 0, 77, 0, 102, 102, 0, 77, 46, 61, 102, 82, 61, 77, 46, 77, 102, 82, 61, 61})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -16, 24, -66, -96, -36, -96, -56, -56, -96, -6, -6, 24, 104, 64, 24, 54, -6, 54, 104, 64, 24, 24})
                        }, { // placeholder2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96})
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
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim4_axis1_quant8_signed_all_inputs_as_internal = TestModelManager::get().add("l2_normalization_quant8_signed_dim4_axis1_quant8_signed_all_inputs_as_internal", get_test_model_dim4_axis1_quant8_signed_all_inputs_as_internal());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim4_axis1_neg_quant8_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -16, 24, -66, -96, -36, -96, -56, -56, -96, -6, -6, 24, 104, 64, 24, 54, -6, 54, 104, 64, 24, 24})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-3})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 102, 102, 77, 0, 77, 0, 102, 102, 0, 77, 46, 61, 102, 82, 61, 77, 46, 77, 102, 82, 61, 61})
                        }},
                .operations = {{
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim4_axis1_neg_quant8_signed = TestModelManager::get().add("l2_normalization_quant8_signed_dim4_axis1_neg_quant8_signed", get_test_model_dim4_axis1_neg_quant8_signed());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim4_axis1_neg_quant8_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-3})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 102, 102, 77, 0, 77, 0, 102, 102, 0, 77, 46, 61, 102, 82, 61, 77, 46, 77, 102, 82, 61, 61})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -16, 24, -66, -96, -36, -96, -56, -56, -96, -6, -6, 24, 104, 64, 24, 54, -6, 54, 104, 64, 24, 24})
                        }, { // placeholder3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96})
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
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim4_axis1_neg_quant8_signed_all_inputs_as_internal = TestModelManager::get().add("l2_normalization_quant8_signed_dim4_axis1_neg_quant8_signed_all_inputs_as_internal", get_test_model_dim4_axis1_neg_quant8_signed_all_inputs_as_internal());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim4_axis2_quant8_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 3, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -66, -96, -56, -56, -16, 24, -36, -96, -96, -6, -6, 24, 24, 54, 104, 64, 104, 64, -6, 54, 24, 24})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 3, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 77, 0, 102, 102, 102, 102, 77, 0, 0, 77, 46, 61, 61, 77, 102, 82, 102, 82, 46, 77, 61, 61})
                        }},
                .operations = {{
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim4_axis2_quant8_signed = TestModelManager::get().add("l2_normalization_quant8_signed_dim4_axis2_quant8_signed", get_test_model_dim4_axis2_quant8_signed());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim4_axis2_quant8_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 3, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 3, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 77, 0, 102, 102, 102, 102, 77, 0, 0, 77, 46, 61, 61, 77, 102, 82, 102, 82, 46, 77, 61, 61})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 3, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -66, -96, -56, -56, -16, 24, -36, -96, -96, -6, -6, 24, 24, 54, 104, 64, 104, 64, -6, 54, 24, 24})
                        }, { // placeholder4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96})
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
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim4_axis2_quant8_signed_all_inputs_as_internal = TestModelManager::get().add("l2_normalization_quant8_signed_dim4_axis2_quant8_signed_all_inputs_as_internal", get_test_model_dim4_axis2_quant8_signed_all_inputs_as_internal());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim4_axis2_neg_quant8_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 3, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -66, -96, -56, -56, -16, 24, -36, -96, -96, -6, -6, 24, 24, 54, 104, 64, 104, 64, -6, 54, 24, 24})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-2})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 3, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 77, 0, 102, 102, 102, 102, 77, 0, 0, 77, 46, 61, 61, 77, 102, 82, 102, 82, 46, 77, 61, 61})
                        }},
                .operations = {{
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim4_axis2_neg_quant8_signed = TestModelManager::get().add("l2_normalization_quant8_signed_dim4_axis2_neg_quant8_signed", get_test_model_dim4_axis2_neg_quant8_signed());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim4_axis2_neg_quant8_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 3, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-2})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 3, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 77, 0, 102, 102, 102, 102, 77, 0, 0, 77, 46, 61, 61, 77, 102, 82, 102, 82, 46, 77, 61, 61})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 3, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -66, -96, -56, -56, -16, 24, -36, -96, -96, -6, -6, 24, 24, 54, 104, 64, 104, 64, -6, 54, 24, 24})
                        }, { // placeholder5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96})
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
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim4_axis2_neg_quant8_signed_all_inputs_as_internal = TestModelManager::get().add("l2_normalization_quant8_signed_dim4_axis2_neg_quant8_signed_all_inputs_as_internal", get_test_model_dim4_axis2_neg_quant8_signed_all_inputs_as_internal());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim4_axis3_quant8_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -56, -66, -96, -56, -16, -36, -96, 24, -96, -6, -6, 24, 104, 24, 54, 64, 104, -6, 24, 64, 54, 24})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 102, 77, 0, 102, 102, 77, 0, 102, 0, 77, 46, 61, 102, 61, 77, 82, 102, 46, 61, 82, 77, 61})
                        }},
                .operations = {{
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim4_axis3_quant8_signed = TestModelManager::get().add("l2_normalization_quant8_signed_dim4_axis3_quant8_signed", get_test_model_dim4_axis3_quant8_signed());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim4_axis3_quant8_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 102, 77, 0, 102, 102, 77, 0, 102, 0, 77, 46, 61, 102, 61, 77, 82, 102, 46, 61, 82, 77, 61})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -56, -66, -96, -56, -16, -36, -96, 24, -96, -6, -6, 24, 104, 24, 54, 64, 104, -6, 24, 64, 54, 24})
                        }, { // placeholder6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96})
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
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim4_axis3_quant8_signed_all_inputs_as_internal = TestModelManager::get().add("l2_normalization_quant8_signed_dim4_axis3_quant8_signed_all_inputs_as_internal", get_test_model_dim4_axis3_quant8_signed_all_inputs_as_internal());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim4_axis3_neg_quant8_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -56, -66, -96, -56, -16, -36, -96, 24, -96, -6, -6, 24, 104, 24, 54, 64, 104, -6, 24, 64, 54, 24})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-1})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 102, 77, 0, 102, 102, 77, 0, 102, 0, 77, 46, 61, 102, 61, 77, 82, 102, 46, 61, 82, 77, 61})
                        }},
                .operations = {{
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim4_axis3_neg_quant8_signed = TestModelManager::get().add("l2_normalization_quant8_signed_dim4_axis3_neg_quant8_signed", get_test_model_dim4_axis3_neg_quant8_signed());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim4_axis3_neg_quant8_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-1})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 102, 77, 0, 102, 102, 77, 0, 102, 0, 77, 46, 61, 102, 61, 77, 82, 102, 46, 61, 82, 77, 61})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -56, -66, -96, -56, -16, -36, -96, 24, -96, -6, -6, 24, 104, 24, 54, 64, 104, -6, 24, 64, 54, 24})
                        }, { // placeholder7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96})
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
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim4_axis3_neg_quant8_signed_all_inputs_as_internal = TestModelManager::get().add("l2_normalization_quant8_signed_dim4_axis3_neg_quant8_signed_all_inputs_as_internal", get_test_model_dim4_axis3_neg_quant8_signed_all_inputs_as_internal());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim3_axis0_quant8_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -16, 24, -66, -96, -36, -96, -56, -56, -96, -6})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 102, 102, 77, 0, 77, 0, 102, 102, 0, 77})
                        }},
                .operations = {{
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim3_axis0_quant8_signed = TestModelManager::get().add("l2_normalization_quant8_signed_dim3_axis0_quant8_signed", get_test_model_dim3_axis0_quant8_signed());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim3_axis0_quant8_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 102, 102, 77, 0, 77, 0, 102, 102, 0, 77})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -16, 24, -66, -96, -36, -96, -56, -56, -96, -6})
                        }, { // placeholder8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96})
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
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim3_axis0_quant8_signed_all_inputs_as_internal = TestModelManager::get().add("l2_normalization_quant8_signed_dim3_axis0_quant8_signed_all_inputs_as_internal", get_test_model_dim3_axis0_quant8_signed_all_inputs_as_internal());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim3_axis0_neg_quant8_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -16, 24, -66, -96, -36, -96, -56, -56, -96, -6})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-3})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 102, 102, 77, 0, 77, 0, 102, 102, 0, 77})
                        }},
                .operations = {{
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim3_axis0_neg_quant8_signed = TestModelManager::get().add("l2_normalization_quant8_signed_dim3_axis0_neg_quant8_signed", get_test_model_dim3_axis0_neg_quant8_signed());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim3_axis0_neg_quant8_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-3})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 102, 102, 77, 0, 77, 0, 102, 102, 0, 77})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -16, 24, -66, -96, -36, -96, -56, -56, -96, -6})
                        }, { // placeholder9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96})
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
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim3_axis0_neg_quant8_signed_all_inputs_as_internal = TestModelManager::get().add("l2_normalization_quant8_signed_dim3_axis0_neg_quant8_signed_all_inputs_as_internal", get_test_model_dim3_axis0_neg_quant8_signed_all_inputs_as_internal());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim3_axis1_quant8_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -66, -96, -56, -56, -16, 24, -36, -96, -96, -6})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 77, 0, 102, 102, 102, 102, 77, 0, 0, 77})
                        }},
                .operations = {{
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim3_axis1_quant8_signed = TestModelManager::get().add("l2_normalization_quant8_signed_dim3_axis1_quant8_signed", get_test_model_dim3_axis1_quant8_signed());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim3_axis1_quant8_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 77, 0, 102, 102, 102, 102, 77, 0, 0, 77})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -66, -96, -56, -56, -16, 24, -36, -96, -96, -6})
                        }, { // placeholder10
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96})
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
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim3_axis1_quant8_signed_all_inputs_as_internal = TestModelManager::get().add("l2_normalization_quant8_signed_dim3_axis1_quant8_signed_all_inputs_as_internal", get_test_model_dim3_axis1_quant8_signed_all_inputs_as_internal());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim3_axis1_neg_quant8_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -66, -96, -56, -56, -16, 24, -36, -96, -96, -6})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-2})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 77, 0, 102, 102, 102, 102, 77, 0, 0, 77})
                        }},
                .operations = {{
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim3_axis1_neg_quant8_signed = TestModelManager::get().add("l2_normalization_quant8_signed_dim3_axis1_neg_quant8_signed", get_test_model_dim3_axis1_neg_quant8_signed());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim3_axis1_neg_quant8_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-2})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 77, 0, 102, 102, 102, 102, 77, 0, 0, 77})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -66, -96, -56, -56, -16, 24, -36, -96, -96, -6})
                        }, { // placeholder11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96})
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
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim3_axis1_neg_quant8_signed_all_inputs_as_internal = TestModelManager::get().add("l2_normalization_quant8_signed_dim3_axis1_neg_quant8_signed_all_inputs_as_internal", get_test_model_dim3_axis1_neg_quant8_signed_all_inputs_as_internal());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim3_axis2_quant8_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -56, -66, -96, -56, -16, -36, -96, 24, -96, -6})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 102, 77, 0, 102, 102, 77, 0, 102, 0, 77})
                        }},
                .operations = {{
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim3_axis2_quant8_signed = TestModelManager::get().add("l2_normalization_quant8_signed_dim3_axis2_quant8_signed", get_test_model_dim3_axis2_quant8_signed());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim3_axis2_quant8_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 102, 77, 0, 102, 102, 77, 0, 102, 0, 77})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -56, -66, -96, -56, -16, -36, -96, 24, -96, -6})
                        }, { // placeholder12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96})
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
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim3_axis2_quant8_signed_all_inputs_as_internal = TestModelManager::get().add("l2_normalization_quant8_signed_dim3_axis2_quant8_signed_all_inputs_as_internal", get_test_model_dim3_axis2_quant8_signed_all_inputs_as_internal());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim3_axis2_neg_quant8_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -56, -66, -96, -56, -16, -36, -96, 24, -96, -6})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-1})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 102, 77, 0, 102, 102, 77, 0, 102, 0, 77})
                        }},
                .operations = {{
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim3_axis2_neg_quant8_signed = TestModelManager::get().add("l2_normalization_quant8_signed_dim3_axis2_neg_quant8_signed", get_test_model_dim3_axis2_neg_quant8_signed());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim3_axis2_neg_quant8_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-1})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 102, 77, 0, 102, 102, 77, 0, 102, 0, 77})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -56, -66, -96, -56, -16, -36, -96, 24, -96, -6})
                        }, { // placeholder13
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96})
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
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim3_axis2_neg_quant8_signed_all_inputs_as_internal = TestModelManager::get().add("l2_normalization_quant8_signed_dim3_axis2_neg_quant8_signed_all_inputs_as_internal", get_test_model_dim3_axis2_neg_quant8_signed_all_inputs_as_internal());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim2_axis0_quant8_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -66, -96, -56, -56})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 77, 0, 102, 102})
                        }},
                .operations = {{
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim2_axis0_quant8_signed = TestModelManager::get().add("l2_normalization_quant8_signed_dim2_axis0_quant8_signed", get_test_model_dim2_axis0_quant8_signed());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim2_axis0_quant8_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 77, 0, 102, 102})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -66, -96, -56, -56})
                        }, { // placeholder14
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96})
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
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim2_axis0_quant8_signed_all_inputs_as_internal = TestModelManager::get().add("l2_normalization_quant8_signed_dim2_axis0_quant8_signed_all_inputs_as_internal", get_test_model_dim2_axis0_quant8_signed_all_inputs_as_internal());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim2_axis0_neg_quant8_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -66, -96, -56, -56})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-2})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 77, 0, 102, 102})
                        }},
                .operations = {{
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim2_axis0_neg_quant8_signed = TestModelManager::get().add("l2_normalization_quant8_signed_dim2_axis0_neg_quant8_signed", get_test_model_dim2_axis0_neg_quant8_signed());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim2_axis0_neg_quant8_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-2})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 77, 0, 102, 102})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -66, -96, -56, -56})
                        }, { // placeholder15
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96})
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
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim2_axis0_neg_quant8_signed_all_inputs_as_internal = TestModelManager::get().add("l2_normalization_quant8_signed_dim2_axis0_neg_quant8_signed_all_inputs_as_internal", get_test_model_dim2_axis0_neg_quant8_signed_all_inputs_as_internal());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim2_axis1_quant8_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -56, -66, -96, -56})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 102, 77, 0, 102})
                        }},
                .operations = {{
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim2_axis1_quant8_signed = TestModelManager::get().add("l2_normalization_quant8_signed_dim2_axis1_quant8_signed", get_test_model_dim2_axis1_quant8_signed());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim2_axis1_quant8_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 102, 77, 0, 102})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -56, -66, -96, -56})
                        }, { // placeholder16
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96})
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
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim2_axis1_quant8_signed_all_inputs_as_internal = TestModelManager::get().add("l2_normalization_quant8_signed_dim2_axis1_quant8_signed_all_inputs_as_internal", get_test_model_dim2_axis1_quant8_signed_all_inputs_as_internal());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim2_axis1_neg_quant8_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -56, -66, -96, -56})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-1})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 102, 77, 0, 102})
                        }},
                .operations = {{
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim2_axis1_neg_quant8_signed = TestModelManager::get().add("l2_normalization_quant8_signed_dim2_axis1_neg_quant8_signed", get_test_model_dim2_axis1_neg_quant8_signed());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim2_axis1_neg_quant8_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-1})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 102, 77, 0, 102})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -56, -66, -96, -56})
                        }, { // placeholder17
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96})
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
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim2_axis1_neg_quant8_signed_all_inputs_as_internal = TestModelManager::get().add("l2_normalization_quant8_signed_dim2_axis1_neg_quant8_signed_all_inputs_as_internal", get_test_model_dim2_axis1_neg_quant8_signed_all_inputs_as_internal());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim1_axis0_quant8_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -56})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 102})
                        }},
                .operations = {{
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim1_axis0_quant8_signed = TestModelManager::get().add("l2_normalization_quant8_signed_dim1_axis0_quant8_signed", get_test_model_dim1_axis0_quant8_signed());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim1_axis0_quant8_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 102})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -56})
                        }, { // placeholder18
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96})
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
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim1_axis0_quant8_signed_all_inputs_as_internal = TestModelManager::get().add("l2_normalization_quant8_signed_dim1_axis0_quant8_signed_all_inputs_as_internal", get_test_model_dim1_axis0_quant8_signed_all_inputs_as_internal());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim1_axis0_neg_quant8_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -56})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-1})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 102})
                        }},
                .operations = {{
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim1_axis0_neg_quant8_signed = TestModelManager::get().add("l2_normalization_quant8_signed_dim1_axis0_neg_quant8_signed", get_test_model_dim1_axis0_neg_quant8_signed());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim1_axis0_neg_quant8_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // axis
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-1})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 102})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -56})
                        }, { // placeholder19
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96})
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
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim1_axis0_neg_quant8_signed_all_inputs_as_internal = TestModelManager::get().add("l2_normalization_quant8_signed_dim1_axis0_neg_quant8_signed_all_inputs_as_internal", get_test_model_dim1_axis0_neg_quant8_signed_all_inputs_as_internal());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim4_axis3_quant8_signed_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -56, -66, -96, -56, -16, -36, -96, 24, -96, -6, -6, 24, 104, 24, 54, 64, 104, -6, 24, 64, 54, 24})
                        }, { // op21
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 102, 77, 0, 102, 102, 77, 0, 102, 0, 77, 46, 61, 102, 61, 77, 82, 102, 46, 61, 82, 77, 61})
                        }},
                .operations = {{
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim4_axis3_quant8_signed_2 = TestModelManager::get().add("l2_normalization_quant8_signed_dim4_axis3_quant8_signed_2", get_test_model_dim4_axis3_quant8_signed_2());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim4_axis3_quant8_signed_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // op21
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 102, 77, 0, 102, 102, 77, 0, 102, 0, 77, 46, 61, 102, 61, 77, 82, 102, 46, 61, 82, 77, 61})
                        }, { // op11_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -56, -66, -96, -56, -16, -36, -96, 24, -96, -6, -6, 24, 104, 24, 54, 64, 104, -6, 24, 64, 54, 24})
                        }, { // placeholder20
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96})
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
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {2, 3, 4},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim4_axis3_quant8_signed_all_inputs_as_internal_2 = TestModelManager::get().add("l2_normalization_quant8_signed_dim4_axis3_quant8_signed_all_inputs_as_internal_2", get_test_model_dim4_axis3_quant8_signed_all_inputs_as_internal_2());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim3_axis2_quant8_signed_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -56, -66, -96, -56, -16, -36, -96, 24, -96, -6})
                        }, { // op21
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 102, 77, 0, 102, 102, 77, 0, 102, 0, 77})
                        }},
                .operations = {{
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim3_axis2_quant8_signed_2 = TestModelManager::get().add("l2_normalization_quant8_signed_dim3_axis2_quant8_signed_2", get_test_model_dim3_axis2_quant8_signed_2());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim3_axis2_quant8_signed_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // op21
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 102, 77, 0, 102, 102, 77, 0, 102, 0, 77})
                        }, { // op11_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -56, -66, -96, -56, -16, -36, -96, 24, -96, -6})
                        }, { // placeholder21
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96})
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
                            .inputs = {2, 3, 4},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim3_axis2_quant8_signed_all_inputs_as_internal_2 = TestModelManager::get().add("l2_normalization_quant8_signed_dim3_axis2_quant8_signed_all_inputs_as_internal_2", get_test_model_dim3_axis2_quant8_signed_all_inputs_as_internal_2());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim2_axis1_quant8_signed_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -56, -66, -96, -56})
                        }, { // op21
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 102, 77, 0, 102})
                        }},
                .operations = {{
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim2_axis1_quant8_signed_2 = TestModelManager::get().add("l2_normalization_quant8_signed_dim2_axis1_quant8_signed_2", get_test_model_dim2_axis1_quant8_signed_2());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim2_axis1_quant8_signed_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // op21
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 102, 77, 0, 102})
                        }, { // op11_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -56, -66, -96, -56})
                        }, { // placeholder22
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96})
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
                            .inputs = {2, 3, 4},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim2_axis1_quant8_signed_all_inputs_as_internal_2 = TestModelManager::get().add("l2_normalization_quant8_signed_dim2_axis1_quant8_signed_all_inputs_as_internal_2", get_test_model_dim2_axis1_quant8_signed_all_inputs_as_internal_2());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim1_axis0_quant8_signed_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -56})
                        }, { // op21
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 102})
                        }},
                .operations = {{
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim1_axis0_quant8_signed_2 = TestModelManager::get().add("l2_normalization_quant8_signed_dim1_axis0_quant8_signed_2", get_test_model_dim1_axis0_quant8_signed_2());

}  // namespace generated_tests::l2_normalization_quant8_signed

namespace generated_tests::l2_normalization_quant8_signed {

const TestModel& get_test_model_dim1_axis0_quant8_signed_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // op21
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 77, 102})
                        }, { // op11_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96, -66, -56})
                        }, { // placeholder23
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -96,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-96})
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
                            .inputs = {2, 3, 4},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim1_axis0_quant8_signed_all_inputs_as_internal_2 = TestModelManager::get().add("l2_normalization_quant8_signed_dim1_axis0_quant8_signed_all_inputs_as_internal_2", get_test_model_dim1_axis0_quant8_signed_all_inputs_as_internal_2());

}  // namespace generated_tests::l2_normalization_quant8_signed

