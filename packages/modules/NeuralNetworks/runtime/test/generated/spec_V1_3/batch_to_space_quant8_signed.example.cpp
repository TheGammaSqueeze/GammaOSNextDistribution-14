// Generated from batch_to_space_quant8_signed.mod.py
// DO NOT EDIT
// clang-format off
#include "TestHarness.h"
using namespace test_helper;  // NOLINT(google-build-using-namespace)

namespace generated_tests::batch_to_space_quant8_signed {

const TestModel& get_test_model() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {4, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16})
                        }, { // block_size
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2, 2})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({1, 5, 2, 6, 9, 13, 10, 14, 3, 7, 4, 8, 11, 15, 12, 16})
                        }},
                .operations = {{
                            .type = TestOperationType::BATCH_TO_SPACE_ND,
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

const auto dummy_test_model = TestModelManager::get().add("batch_to_space_quant8_signed", get_test_model());

}  // namespace generated_tests::batch_to_space_quant8_signed

namespace generated_tests::batch_to_space_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {4, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // block_size
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2, 2})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({1, 5, 2, 6, 9, 13, 10, 14, 3, 7, 4, 8, 11, 15, 12, 16})
                        }, { // input_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {4, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16})
                        }, { // placeholder
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0})
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
                            .type = TestOperationType::BATCH_TO_SPACE_ND,
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

const auto dummy_test_model_all_inputs_as_internal = TestModelManager::get().add("batch_to_space_quant8_signed_all_inputs_as_internal", get_test_model_all_inputs_as_internal());

}  // namespace generated_tests::batch_to_space_quant8_signed

namespace generated_tests::batch_to_space_quant8_signed {

const TestModel& get_test_model_all_tensors_as_inputs() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {4, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16})
                        }, { // block_size
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2, 2})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({1, 5, 2, 6, 9, 13, 10, 14, 3, 7, 4, 8, 11, 15, 12, 16})
                        }},
                .operations = {{
                            .type = TestOperationType::BATCH_TO_SPACE_ND,
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

const auto dummy_test_model_all_tensors_as_inputs = TestModelManager::get().add("batch_to_space_quant8_signed_all_tensors_as_inputs", get_test_model_all_tensors_as_inputs());

}  // namespace generated_tests::batch_to_space_quant8_signed

namespace generated_tests::batch_to_space_quant8_signed {

const TestModel& get_test_model_all_tensors_as_inputs_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {4, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // block_size
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2, 2})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({1, 5, 2, 6, 9, 13, 10, 14, 3, 7, 4, 8, 11, 15, 12, 16})
                        }, { // input_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {4, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16})
                        }, { // placeholder1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
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
                            .type = TestOperationType::BATCH_TO_SPACE_ND,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {1, 3},
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

const auto dummy_test_model_all_tensors_as_inputs_all_inputs_as_internal = TestModelManager::get().add("batch_to_space_quant8_signed_all_tensors_as_inputs_all_inputs_as_internal", get_test_model_all_tensors_as_inputs_all_inputs_as_internal());

}  // namespace generated_tests::batch_to_space_quant8_signed

namespace generated_tests::batch_to_space_quant8_signed {

const TestModel& get_test_model_nhwc_quant8_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {4, 1, 1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({14, 23, 32, 41, 54, 63, 72, 81})
                        }, { // param
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2, 2})
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
                        }, { // op4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({14, 23, 32, 41, 54, 63, 72, 81})
                        }},
                .operations = {{
                            .type = TestOperationType::BATCH_TO_SPACE_ND,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {0},
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

const auto dummy_test_model_nhwc_quant8_signed = TestModelManager::get().add("batch_to_space_quant8_signed_nhwc_quant8_signed", get_test_model_nhwc_quant8_signed());

}  // namespace generated_tests::batch_to_space_quant8_signed

namespace generated_tests::batch_to_space_quant8_signed {

const TestModel& get_test_model_nhwc_quant8_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {4, 1, 1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2, 2})
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
                        }, { // op4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({14, 23, 32, 41, 54, 63, 72, 81})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {4, 1, 1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({14, 23, 32, 41, 54, 63, 72, 81})
                        }, { // placeholder2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::BATCH_TO_SPACE_ND,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4},
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

const auto dummy_test_model_nhwc_quant8_signed_all_inputs_as_internal = TestModelManager::get().add("batch_to_space_quant8_signed_nhwc_quant8_signed_all_inputs_as_internal", get_test_model_nhwc_quant8_signed_all_inputs_as_internal());

}  // namespace generated_tests::batch_to_space_quant8_signed

namespace generated_tests::batch_to_space_quant8_signed {

const TestModel& get_test_model_nchw_quant8_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {4, 2, 1, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({14, 23, 32, 41, 54, 63, 72, 81})
                        }, { // param
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2, 2})
                        }, { // layout
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // op4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({14, 32, 54, 72, 23, 41, 63, 81})
                        }},
                .operations = {{
                            .type = TestOperationType::BATCH_TO_SPACE_ND,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {0},
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

const auto dummy_test_model_nchw_quant8_signed = TestModelManager::get().add("batch_to_space_quant8_signed_nchw_quant8_signed", get_test_model_nchw_quant8_signed());

}  // namespace generated_tests::batch_to_space_quant8_signed

namespace generated_tests::batch_to_space_quant8_signed {

const TestModel& get_test_model_nchw_quant8_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {4, 2, 1, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2, 2})
                        }, { // layout
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // op4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({14, 32, 54, 72, 23, 41, 63, 81})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {4, 2, 1, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({14, 23, 32, 41, 54, 63, 72, 81})
                        }, { // placeholder3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::BATCH_TO_SPACE_ND,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4},
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

const auto dummy_test_model_nchw_quant8_signed_all_inputs_as_internal = TestModelManager::get().add("batch_to_space_quant8_signed_nchw_quant8_signed_all_inputs_as_internal", get_test_model_nchw_quant8_signed_all_inputs_as_internal());

}  // namespace generated_tests::batch_to_space_quant8_signed

namespace generated_tests::batch_to_space_quant8_signed {

const TestModel& get_test_model_nhwc_quant8_signed_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {4, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32})
                        }, { // param1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2, 2})
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
                        }, { // op41
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({2, 10, 4, 12, 18, 26, 20, 28, 6, 14, 8, 16, 22, 30, 24, 32})
                        }},
                .operations = {{
                            .type = TestOperationType::BATCH_TO_SPACE_ND,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {0},
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

const auto dummy_test_model_nhwc_quant8_signed_2 = TestModelManager::get().add("batch_to_space_quant8_signed_nhwc_quant8_signed_2", get_test_model_nhwc_quant8_signed_2());

}  // namespace generated_tests::batch_to_space_quant8_signed

namespace generated_tests::batch_to_space_quant8_signed {

const TestModel& get_test_model_nhwc_quant8_signed_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {4, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2, 2})
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
                        }, { // op41
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({2, 10, 4, 12, 18, 26, 20, 28, 6, 14, 8, 16, 22, 30, 24, 32})
                        }, { // op11_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {4, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32})
                        }, { // placeholder4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::BATCH_TO_SPACE_ND,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4},
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

const auto dummy_test_model_nhwc_quant8_signed_all_inputs_as_internal_2 = TestModelManager::get().add("batch_to_space_quant8_signed_nhwc_quant8_signed_all_inputs_as_internal_2", get_test_model_nhwc_quant8_signed_all_inputs_as_internal_2());

}  // namespace generated_tests::batch_to_space_quant8_signed

namespace generated_tests::batch_to_space_quant8_signed {

const TestModel& get_test_model_nchw_quant8_signed_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {4, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32})
                        }, { // param1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2, 2})
                        }, { // layout
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // op41
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({2, 10, 4, 12, 18, 26, 20, 28, 6, 14, 8, 16, 22, 30, 24, 32})
                        }},
                .operations = {{
                            .type = TestOperationType::BATCH_TO_SPACE_ND,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {0},
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

const auto dummy_test_model_nchw_quant8_signed_2 = TestModelManager::get().add("batch_to_space_quant8_signed_nchw_quant8_signed_2", get_test_model_nchw_quant8_signed_2());

}  // namespace generated_tests::batch_to_space_quant8_signed

namespace generated_tests::batch_to_space_quant8_signed {

const TestModel& get_test_model_nchw_quant8_signed_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {4, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2, 2})
                        }, { // layout
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // op41
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({2, 10, 4, 12, 18, 26, 20, 28, 6, 14, 8, 16, 22, 30, 24, 32})
                        }, { // op11_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {4, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32})
                        }, { // placeholder5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
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
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::BATCH_TO_SPACE_ND,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4},
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

const auto dummy_test_model_nchw_quant8_signed_all_inputs_as_internal_2 = TestModelManager::get().add("batch_to_space_quant8_signed_nchw_quant8_signed_all_inputs_as_internal_2", get_test_model_nchw_quant8_signed_all_inputs_as_internal_2());

}  // namespace generated_tests::batch_to_space_quant8_signed

