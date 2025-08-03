// Generated from l2_normalization_v1_2.mod.py
// DO NOT EDIT
// clang-format off
#include "TestHarness.h"
using namespace test_helper;  // NOLINT(google-build-using-namespace)

namespace generated_tests::l2_normalization_v1_2 {

const TestModel& get_test_model_dim4_axis3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 3.0f, 4.0f, 3.0f, 0.0f, 4.0f, 8.0f, 6.0f, 0.0f, 12.0f, 0.0f, 9.0f, 9.0f, 12.0f, 20.0f, 12.0f, 15.0f, 16.0f, 20.0f, 9.0f, 12.0f, 16.0f, 15.0f, 12.0f})
                        }, { // op2
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.6f, 0.8f, 0.6f, 0.0f, 0.8f, 0.8f, 0.6f, 0.0f, 0.8f, 0.0f, 0.6f, 0.36f, 0.48f, 0.8f, 0.48f, 0.6f, 0.64f, 0.8f, 0.36f, 0.48f, 0.64f, 0.6f, 0.48f})
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
        .minSupportedVersion = TestHalVersion::V1_0
    };
    return model;
}

const auto dummy_test_model_dim4_axis3 = TestModelManager::get().add("l2_normalization_v1_2_dim4_axis3", get_test_model_dim4_axis3());

}  // namespace generated_tests::l2_normalization_v1_2

namespace generated_tests::l2_normalization_v1_2 {

const TestModel& get_test_model_dim4_axis3_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // op2
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.6f, 0.8f, 0.6f, 0.0f, 0.8f, 0.8f, 0.6f, 0.0f, 0.8f, 0.0f, 0.6f, 0.36f, 0.48f, 0.8f, 0.48f, 0.6f, 0.64f, 0.8f, 0.36f, 0.48f, 0.64f, 0.6f, 0.48f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 3.0f, 4.0f, 3.0f, 0.0f, 4.0f, 8.0f, 6.0f, 0.0f, 12.0f, 0.0f, 9.0f, 9.0f, 12.0f, 20.0f, 12.0f, 15.0f, 16.0f, 20.0f, 9.0f, 12.0f, 16.0f, 15.0f, 12.0f})
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
        .minSupportedVersion = TestHalVersion::V1_0
    };
    return model;
}

const auto dummy_test_model_dim4_axis3_all_inputs_as_internal = TestModelManager::get().add("l2_normalization_v1_2_dim4_axis3_all_inputs_as_internal", get_test_model_dim4_axis3_all_inputs_as_internal());

}  // namespace generated_tests::l2_normalization_v1_2

namespace generated_tests::l2_normalization_v1_2 {

const TestModel& get_test_model_dim4_axis3_relaxed() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 3.0f, 4.0f, 3.0f, 0.0f, 4.0f, 8.0f, 6.0f, 0.0f, 12.0f, 0.0f, 9.0f, 9.0f, 12.0f, 20.0f, 12.0f, 15.0f, 16.0f, 20.0f, 9.0f, 12.0f, 16.0f, 15.0f, 12.0f})
                        }, { // op2
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.6f, 0.8f, 0.6f, 0.0f, 0.8f, 0.8f, 0.6f, 0.0f, 0.8f, 0.0f, 0.6f, 0.36f, 0.48f, 0.8f, 0.48f, 0.6f, 0.64f, 0.8f, 0.36f, 0.48f, 0.64f, 0.6f, 0.48f})
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
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_dim4_axis3_relaxed = TestModelManager::get().add("l2_normalization_v1_2_dim4_axis3_relaxed", get_test_model_dim4_axis3_relaxed());

}  // namespace generated_tests::l2_normalization_v1_2

namespace generated_tests::l2_normalization_v1_2 {

const TestModel& get_test_model_dim4_axis3_relaxed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // op2
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.6f, 0.8f, 0.6f, 0.0f, 0.8f, 0.8f, 0.6f, 0.0f, 0.8f, 0.0f, 0.6f, 0.36f, 0.48f, 0.8f, 0.48f, 0.6f, 0.64f, 0.8f, 0.36f, 0.48f, 0.64f, 0.6f, 0.48f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 3.0f, 4.0f, 3.0f, 0.0f, 4.0f, 8.0f, 6.0f, 0.0f, 12.0f, 0.0f, 9.0f, 9.0f, 12.0f, 20.0f, 12.0f, 15.0f, 16.0f, 20.0f, 9.0f, 12.0f, 16.0f, 15.0f, 12.0f})
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
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim4_axis3_relaxed_all_inputs_as_internal = TestModelManager::get().add("l2_normalization_v1_2_dim4_axis3_relaxed_all_inputs_as_internal", get_test_model_dim4_axis3_relaxed_all_inputs_as_internal());

}  // namespace generated_tests::l2_normalization_v1_2

namespace generated_tests::l2_normalization_v1_2 {

const TestModel& get_test_model_dim4_axis3_float16() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 3.0f, 4.0f, 3.0f, 0.0f, 4.0f, 8.0f, 6.0f, 0.0f, 12.0f, 0.0f, 9.0f, 9.0f, 12.0f, 20.0f, 12.0f, 15.0f, 16.0f, 20.0f, 9.0f, 12.0f, 16.0f, 15.0f, 12.0f})
                        }, { // op2
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 0.6000000238418579f, 0.800000011920929f, 0.6000000238418579f, 0.0f, 0.800000011920929f, 0.800000011920929f, 0.6000000238418579f, 0.0f, 0.800000011920929f, 0.0f, 0.6000000238418579f, 0.36000001430511475f, 0.47999998927116394f, 0.800000011920929f, 0.47999998927116394f, 0.6000000238418579f, 0.6399999856948853f, 0.800000011920929f, 0.36000001430511475f, 0.47999998927116394f, 0.6399999856948853f, 0.6000000238418579f, 0.47999998927116394f})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim4_axis3_float16 = TestModelManager::get().add("l2_normalization_v1_2_dim4_axis3_float16", get_test_model_dim4_axis3_float16());

}  // namespace generated_tests::l2_normalization_v1_2

namespace generated_tests::l2_normalization_v1_2 {

const TestModel& get_test_model_dim4_axis3_float16_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // op2
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 0.6000000238418579f, 0.800000011920929f, 0.6000000238418579f, 0.0f, 0.800000011920929f, 0.800000011920929f, 0.6000000238418579f, 0.0f, 0.800000011920929f, 0.0f, 0.6000000238418579f, 0.36000001430511475f, 0.47999998927116394f, 0.800000011920929f, 0.47999998927116394f, 0.6000000238418579f, 0.6399999856948853f, 0.800000011920929f, 0.36000001430511475f, 0.47999998927116394f, 0.6399999856948853f, 0.6000000238418579f, 0.47999998927116394f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 3.0f, 4.0f, 3.0f, 0.0f, 4.0f, 8.0f, 6.0f, 0.0f, 12.0f, 0.0f, 9.0f, 9.0f, 12.0f, 20.0f, 12.0f, 15.0f, 16.0f, 20.0f, 9.0f, 12.0f, 16.0f, 15.0f, 12.0f})
                        }, { // placeholder2
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim4_axis3_float16_all_inputs_as_internal = TestModelManager::get().add("l2_normalization_v1_2_dim4_axis3_float16_all_inputs_as_internal", get_test_model_dim4_axis3_float16_all_inputs_as_internal());

}  // namespace generated_tests::l2_normalization_v1_2

namespace generated_tests::l2_normalization_v1_2 {

const TestModel& get_test_model_dim4_axis3_quant8() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 32,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({32, 62, 72, 62, 32, 72, 112, 92, 32, 152, 32, 122, 122, 152, 232, 152, 182, 192, 232, 122, 152, 192, 182, 152})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 205, 230, 205, 128, 230, 230, 205, 128, 230, 128, 205, 174, 189, 230, 189, 205, 210, 230, 174, 189, 210, 205, 189})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim4_axis3_quant8 = TestModelManager::get().add("l2_normalization_v1_2_dim4_axis3_quant8", get_test_model_dim4_axis3_quant8());

}  // namespace generated_tests::l2_normalization_v1_2

namespace generated_tests::l2_normalization_v1_2 {

const TestModel& get_test_model_dim4_axis3_quant8_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 32,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 205, 230, 205, 128, 230, 230, 205, 128, 230, 128, 205, 174, 189, 230, 189, 205, 210, 230, 174, 189, 210, 205, 189})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 32,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({32, 62, 72, 62, 32, 72, 112, 92, 32, 152, 32, 122, 122, 152, 232, 152, 182, 192, 232, 122, 152, 192, 182, 152})
                        }, { // placeholder3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 32,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({32})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim4_axis3_quant8_all_inputs_as_internal = TestModelManager::get().add("l2_normalization_v1_2_dim4_axis3_quant8_all_inputs_as_internal", get_test_model_dim4_axis3_quant8_all_inputs_as_internal());

}  // namespace generated_tests::l2_normalization_v1_2

namespace generated_tests::l2_normalization_v1_2 {

const TestModel& get_test_model_dim3_axis2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 3.0f, 4.0f, 3.0f, 0.0f, 4.0f, 8.0f, 6.0f, 0.0f, 12.0f, 0.0f, 9.0f})
                        }, { // op2
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.6f, 0.8f, 0.6f, 0.0f, 0.8f, 0.8f, 0.6f, 0.0f, 0.8f, 0.0f, 0.6f})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim3_axis2 = TestModelManager::get().add("l2_normalization_v1_2_dim3_axis2", get_test_model_dim3_axis2());

}  // namespace generated_tests::l2_normalization_v1_2

namespace generated_tests::l2_normalization_v1_2 {

const TestModel& get_test_model_dim3_axis2_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // op2
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.6f, 0.8f, 0.6f, 0.0f, 0.8f, 0.8f, 0.6f, 0.0f, 0.8f, 0.0f, 0.6f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 3.0f, 4.0f, 3.0f, 0.0f, 4.0f, 8.0f, 6.0f, 0.0f, 12.0f, 0.0f, 9.0f})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim3_axis2_all_inputs_as_internal = TestModelManager::get().add("l2_normalization_v1_2_dim3_axis2_all_inputs_as_internal", get_test_model_dim3_axis2_all_inputs_as_internal());

}  // namespace generated_tests::l2_normalization_v1_2

namespace generated_tests::l2_normalization_v1_2 {

const TestModel& get_test_model_dim3_axis2_relaxed() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 3.0f, 4.0f, 3.0f, 0.0f, 4.0f, 8.0f, 6.0f, 0.0f, 12.0f, 0.0f, 9.0f})
                        }, { // op2
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.6f, 0.8f, 0.6f, 0.0f, 0.8f, 0.8f, 0.6f, 0.0f, 0.8f, 0.0f, 0.6f})
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
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_dim3_axis2_relaxed = TestModelManager::get().add("l2_normalization_v1_2_dim3_axis2_relaxed", get_test_model_dim3_axis2_relaxed());

}  // namespace generated_tests::l2_normalization_v1_2

namespace generated_tests::l2_normalization_v1_2 {

const TestModel& get_test_model_dim3_axis2_relaxed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // op2
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.6f, 0.8f, 0.6f, 0.0f, 0.8f, 0.8f, 0.6f, 0.0f, 0.8f, 0.0f, 0.6f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 3.0f, 4.0f, 3.0f, 0.0f, 4.0f, 8.0f, 6.0f, 0.0f, 12.0f, 0.0f, 9.0f})
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
                            .type = TestOperationType::L2_NORMALIZATION,
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

const auto dummy_test_model_dim3_axis2_relaxed_all_inputs_as_internal = TestModelManager::get().add("l2_normalization_v1_2_dim3_axis2_relaxed_all_inputs_as_internal", get_test_model_dim3_axis2_relaxed_all_inputs_as_internal());

}  // namespace generated_tests::l2_normalization_v1_2

namespace generated_tests::l2_normalization_v1_2 {

const TestModel& get_test_model_dim3_axis2_float16() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 3.0f, 4.0f, 3.0f, 0.0f, 4.0f, 8.0f, 6.0f, 0.0f, 12.0f, 0.0f, 9.0f})
                        }, { // op2
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 0.6000000238418579f, 0.800000011920929f, 0.6000000238418579f, 0.0f, 0.800000011920929f, 0.800000011920929f, 0.6000000238418579f, 0.0f, 0.800000011920929f, 0.0f, 0.6000000238418579f})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim3_axis2_float16 = TestModelManager::get().add("l2_normalization_v1_2_dim3_axis2_float16", get_test_model_dim3_axis2_float16());

}  // namespace generated_tests::l2_normalization_v1_2

namespace generated_tests::l2_normalization_v1_2 {

const TestModel& get_test_model_dim3_axis2_float16_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // op2
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 0.6000000238418579f, 0.800000011920929f, 0.6000000238418579f, 0.0f, 0.800000011920929f, 0.800000011920929f, 0.6000000238418579f, 0.0f, 0.800000011920929f, 0.0f, 0.6000000238418579f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 3.0f, 4.0f, 3.0f, 0.0f, 4.0f, 8.0f, 6.0f, 0.0f, 12.0f, 0.0f, 9.0f})
                        }, { // placeholder6
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim3_axis2_float16_all_inputs_as_internal = TestModelManager::get().add("l2_normalization_v1_2_dim3_axis2_float16_all_inputs_as_internal", get_test_model_dim3_axis2_float16_all_inputs_as_internal());

}  // namespace generated_tests::l2_normalization_v1_2

namespace generated_tests::l2_normalization_v1_2 {

const TestModel& get_test_model_dim3_axis2_quant8() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 32,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({32, 62, 72, 62, 32, 72, 112, 92, 32, 152, 32, 122})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 205, 230, 205, 128, 230, 230, 205, 128, 230, 128, 205})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim3_axis2_quant8 = TestModelManager::get().add("l2_normalization_v1_2_dim3_axis2_quant8", get_test_model_dim3_axis2_quant8());

}  // namespace generated_tests::l2_normalization_v1_2

namespace generated_tests::l2_normalization_v1_2 {

const TestModel& get_test_model_dim3_axis2_quant8_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 32,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 205, 230, 205, 128, 230, 230, 205, 128, 230, 128, 205})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 32,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({32, 62, 72, 62, 32, 72, 112, 92, 32, 152, 32, 122})
                        }, { // placeholder7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 32,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({32})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim3_axis2_quant8_all_inputs_as_internal = TestModelManager::get().add("l2_normalization_v1_2_dim3_axis2_quant8_all_inputs_as_internal", get_test_model_dim3_axis2_quant8_all_inputs_as_internal());

}  // namespace generated_tests::l2_normalization_v1_2

namespace generated_tests::l2_normalization_v1_2 {

const TestModel& get_test_model_dim2_axis1() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 3.0f, 4.0f, 3.0f, 0.0f, 4.0f})
                        }, { // op2
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.6f, 0.8f, 0.6f, 0.0f, 0.8f})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim2_axis1 = TestModelManager::get().add("l2_normalization_v1_2_dim2_axis1", get_test_model_dim2_axis1());

}  // namespace generated_tests::l2_normalization_v1_2

namespace generated_tests::l2_normalization_v1_2 {

const TestModel& get_test_model_dim2_axis1_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // op2
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.6f, 0.8f, 0.6f, 0.0f, 0.8f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 3.0f, 4.0f, 3.0f, 0.0f, 4.0f})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim2_axis1_all_inputs_as_internal = TestModelManager::get().add("l2_normalization_v1_2_dim2_axis1_all_inputs_as_internal", get_test_model_dim2_axis1_all_inputs_as_internal());

}  // namespace generated_tests::l2_normalization_v1_2

namespace generated_tests::l2_normalization_v1_2 {

const TestModel& get_test_model_dim2_axis1_relaxed() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 3.0f, 4.0f, 3.0f, 0.0f, 4.0f})
                        }, { // op2
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.6f, 0.8f, 0.6f, 0.0f, 0.8f})
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
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_dim2_axis1_relaxed = TestModelManager::get().add("l2_normalization_v1_2_dim2_axis1_relaxed", get_test_model_dim2_axis1_relaxed());

}  // namespace generated_tests::l2_normalization_v1_2

namespace generated_tests::l2_normalization_v1_2 {

const TestModel& get_test_model_dim2_axis1_relaxed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // op2
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.6f, 0.8f, 0.6f, 0.0f, 0.8f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 3.0f, 4.0f, 3.0f, 0.0f, 4.0f})
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
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_dim2_axis1_relaxed_all_inputs_as_internal = TestModelManager::get().add("l2_normalization_v1_2_dim2_axis1_relaxed_all_inputs_as_internal", get_test_model_dim2_axis1_relaxed_all_inputs_as_internal());

}  // namespace generated_tests::l2_normalization_v1_2

namespace generated_tests::l2_normalization_v1_2 {

const TestModel& get_test_model_dim2_axis1_float16() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 3.0f, 4.0f, 3.0f, 0.0f, 4.0f})
                        }, { // op2
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 0.6000000238418579f, 0.800000011920929f, 0.6000000238418579f, 0.0f, 0.800000011920929f})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim2_axis1_float16 = TestModelManager::get().add("l2_normalization_v1_2_dim2_axis1_float16", get_test_model_dim2_axis1_float16());

}  // namespace generated_tests::l2_normalization_v1_2

namespace generated_tests::l2_normalization_v1_2 {

const TestModel& get_test_model_dim2_axis1_float16_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // op2
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 0.6000000238418579f, 0.800000011920929f, 0.6000000238418579f, 0.0f, 0.800000011920929f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 3.0f, 4.0f, 3.0f, 0.0f, 4.0f})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim2_axis1_float16_all_inputs_as_internal = TestModelManager::get().add("l2_normalization_v1_2_dim2_axis1_float16_all_inputs_as_internal", get_test_model_dim2_axis1_float16_all_inputs_as_internal());

}  // namespace generated_tests::l2_normalization_v1_2

namespace generated_tests::l2_normalization_v1_2 {

const TestModel& get_test_model_dim2_axis1_quant8() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 32,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({32, 62, 72, 62, 32, 72})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 205, 230, 205, 128, 230})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim2_axis1_quant8 = TestModelManager::get().add("l2_normalization_v1_2_dim2_axis1_quant8", get_test_model_dim2_axis1_quant8());

}  // namespace generated_tests::l2_normalization_v1_2

namespace generated_tests::l2_normalization_v1_2 {

const TestModel& get_test_model_dim2_axis1_quant8_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 32,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 205, 230, 205, 128, 230})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 32,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({32, 62, 72, 62, 32, 72})
                        }, { // placeholder11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 32,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({32})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim2_axis1_quant8_all_inputs_as_internal = TestModelManager::get().add("l2_normalization_v1_2_dim2_axis1_quant8_all_inputs_as_internal", get_test_model_dim2_axis1_quant8_all_inputs_as_internal());

}  // namespace generated_tests::l2_normalization_v1_2

namespace generated_tests::l2_normalization_v1_2 {

const TestModel& get_test_model_dim1_axis0() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 3.0f, 4.0f})
                        }, { // op2
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.6f, 0.8f})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim1_axis0 = TestModelManager::get().add("l2_normalization_v1_2_dim1_axis0", get_test_model_dim1_axis0());

}  // namespace generated_tests::l2_normalization_v1_2

namespace generated_tests::l2_normalization_v1_2 {

const TestModel& get_test_model_dim1_axis0_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // op2
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.6f, 0.8f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 3.0f, 4.0f})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim1_axis0_all_inputs_as_internal = TestModelManager::get().add("l2_normalization_v1_2_dim1_axis0_all_inputs_as_internal", get_test_model_dim1_axis0_all_inputs_as_internal());

}  // namespace generated_tests::l2_normalization_v1_2

namespace generated_tests::l2_normalization_v1_2 {

const TestModel& get_test_model_dim1_axis0_relaxed() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 3.0f, 4.0f})
                        }, { // op2
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.6f, 0.8f})
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
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_dim1_axis0_relaxed = TestModelManager::get().add("l2_normalization_v1_2_dim1_axis0_relaxed", get_test_model_dim1_axis0_relaxed());

}  // namespace generated_tests::l2_normalization_v1_2

namespace generated_tests::l2_normalization_v1_2 {

const TestModel& get_test_model_dim1_axis0_relaxed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // op2
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.6f, 0.8f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 3.0f, 4.0f})
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
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_dim1_axis0_relaxed_all_inputs_as_internal = TestModelManager::get().add("l2_normalization_v1_2_dim1_axis0_relaxed_all_inputs_as_internal", get_test_model_dim1_axis0_relaxed_all_inputs_as_internal());

}  // namespace generated_tests::l2_normalization_v1_2

namespace generated_tests::l2_normalization_v1_2 {

const TestModel& get_test_model_dim1_axis0_float16() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 3.0f, 4.0f})
                        }, { // op2
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 0.6000000238418579f, 0.800000011920929f})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim1_axis0_float16 = TestModelManager::get().add("l2_normalization_v1_2_dim1_axis0_float16", get_test_model_dim1_axis0_float16());

}  // namespace generated_tests::l2_normalization_v1_2

namespace generated_tests::l2_normalization_v1_2 {

const TestModel& get_test_model_dim1_axis0_float16_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // op2
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 0.6000000238418579f, 0.800000011920929f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 3.0f, 4.0f})
                        }, { // placeholder14
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim1_axis0_float16_all_inputs_as_internal = TestModelManager::get().add("l2_normalization_v1_2_dim1_axis0_float16_all_inputs_as_internal", get_test_model_dim1_axis0_float16_all_inputs_as_internal());

}  // namespace generated_tests::l2_normalization_v1_2

namespace generated_tests::l2_normalization_v1_2 {

const TestModel& get_test_model_dim1_axis0_quant8() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 32,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({32, 62, 72})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 205, 230})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim1_axis0_quant8 = TestModelManager::get().add("l2_normalization_v1_2_dim1_axis0_quant8", get_test_model_dim1_axis0_quant8());

}  // namespace generated_tests::l2_normalization_v1_2

namespace generated_tests::l2_normalization_v1_2 {

const TestModel& get_test_model_dim1_axis0_quant8_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 32,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // op2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 205, 230})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 32,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({32, 62, 72})
                        }, { // placeholder15
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 32,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({32})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim1_axis0_quant8_all_inputs_as_internal = TestModelManager::get().add("l2_normalization_v1_2_dim1_axis0_quant8_all_inputs_as_internal", get_test_model_dim1_axis0_quant8_all_inputs_as_internal());

}  // namespace generated_tests::l2_normalization_v1_2

