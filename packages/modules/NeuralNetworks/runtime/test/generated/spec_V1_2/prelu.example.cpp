// Generated from prelu.mod.py
// DO NOT EDIT
// clang-format off
#include "TestHarness.h"
using namespace test_helper;  // NOLINT(google-build-using-namespace)

namespace generated_tests::prelu {

const TestModel& get_test_model() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, -1.0f, -1.0f, -1.0f, -2.0f, -2.0f, -2.0f})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f})
                        }, { // output
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f, -1.0f, -2.0f, 0.0f, -2.0f, -4.0f})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model = TestModelManager::get().add("prelu", get_test_model());

}  // namespace generated_tests::prelu

namespace generated_tests::prelu {

const TestModel& get_test_model_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f})
                        }, { // output
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f, -1.0f, -2.0f, 0.0f, -2.0f, -4.0f})
                        }, { // input_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, -1.0f, -1.0f, -1.0f, -2.0f, -2.0f, -2.0f})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal = TestModelManager::get().add("prelu_all_inputs_as_internal", get_test_model_all_inputs_as_internal());

}  // namespace generated_tests::prelu

namespace generated_tests::prelu {

const TestModel& get_test_model_all_tensors_as_inputs() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, -1.0f, -1.0f, -1.0f, -2.0f, -2.0f, -2.0f})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f})
                        }, { // output
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f, -1.0f, -2.0f, 0.0f, -2.0f, -4.0f})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_tensors_as_inputs = TestModelManager::get().add("prelu_all_tensors_as_inputs", get_test_model_all_tensors_as_inputs());

}  // namespace generated_tests::prelu

namespace generated_tests::prelu {

const TestModel& get_test_model_all_tensors_as_inputs_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // output
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f, -1.0f, -2.0f, 0.0f, -2.0f, -4.0f})
                        }, { // input_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, -1.0f, -1.0f, -1.0f, -2.0f, -2.0f, -2.0f})
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
                        }, { // alpha_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_tensors_as_inputs_all_inputs_as_internal = TestModelManager::get().add("prelu_all_tensors_as_inputs_all_inputs_as_internal", get_test_model_all_tensors_as_inputs_all_inputs_as_internal());

}  // namespace generated_tests::prelu

namespace generated_tests::prelu {

const TestModel& get_test_model_relaxed() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, -1.0f, -1.0f, -1.0f, -2.0f, -2.0f, -2.0f})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f})
                        }, { // output
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f, -1.0f, -2.0f, 0.0f, -2.0f, -4.0f})
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
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed = TestModelManager::get().add("prelu_relaxed", get_test_model_relaxed());

}  // namespace generated_tests::prelu

namespace generated_tests::prelu {

const TestModel& get_test_model_relaxed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f})
                        }, { // output
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f, -1.0f, -2.0f, 0.0f, -2.0f, -4.0f})
                        }, { // input_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, -1.0f, -1.0f, -1.0f, -2.0f, -2.0f, -2.0f})
                        }, { // placeholder3
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
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
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_all_inputs_as_internal = TestModelManager::get().add("prelu_relaxed_all_inputs_as_internal", get_test_model_relaxed_all_inputs_as_internal());

}  // namespace generated_tests::prelu

namespace generated_tests::prelu {

const TestModel& get_test_model_relaxed_all_tensors_as_inputs() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, -1.0f, -1.0f, -1.0f, -2.0f, -2.0f, -2.0f})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f})
                        }, { // output
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f, -1.0f, -2.0f, 0.0f, -2.0f, -4.0f})
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
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_all_tensors_as_inputs = TestModelManager::get().add("prelu_relaxed_all_tensors_as_inputs", get_test_model_relaxed_all_tensors_as_inputs());

}  // namespace generated_tests::prelu

namespace generated_tests::prelu {

const TestModel& get_test_model_relaxed_all_tensors_as_inputs_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // output
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f, -1.0f, -2.0f, 0.0f, -2.0f, -4.0f})
                        }, { // input_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, -1.0f, -1.0f, -1.0f, -2.0f, -2.0f, -2.0f})
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
                        }, { // alpha_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f})
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
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_all_tensors_as_inputs_all_inputs_as_internal = TestModelManager::get().add("prelu_relaxed_all_tensors_as_inputs_all_inputs_as_internal", get_test_model_relaxed_all_tensors_as_inputs_all_inputs_as_internal());

}  // namespace generated_tests::prelu

namespace generated_tests::prelu {

const TestModel& get_test_model_quant8() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128, 128, 132, 132, 132, 124, 124, 124, 120, 120, 120})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 50,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({50, 54, 58})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 120, 120, 122, 122, 122, 120, 118, 116, 120, 116, 112})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8 = TestModelManager::get().add("prelu_quant8", get_test_model_quant8());

}  // namespace generated_tests::prelu

namespace generated_tests::prelu {

const TestModel& get_test_model_quant8_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 50,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({50, 54, 58})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 120, 120, 122, 122, 122, 120, 118, 116, 120, 116, 112})
                        }, { // input_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128, 128, 132, 132, 132, 124, 124, 124, 120, 120, 120})
                        }, { // placeholder6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_all_inputs_as_internal = TestModelManager::get().add("prelu_quant8_all_inputs_as_internal", get_test_model_quant8_all_inputs_as_internal());

}  // namespace generated_tests::prelu

namespace generated_tests::prelu {

const TestModel& get_test_model_quant8_all_tensors_as_inputs() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128, 128, 132, 132, 132, 124, 124, 124, 120, 120, 120})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 50,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({50, 54, 58})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 120, 120, 122, 122, 122, 120, 118, 116, 120, 116, 112})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_all_tensors_as_inputs = TestModelManager::get().add("prelu_quant8_all_tensors_as_inputs", get_test_model_quant8_all_tensors_as_inputs());

}  // namespace generated_tests::prelu

namespace generated_tests::prelu {

const TestModel& get_test_model_quant8_all_tensors_as_inputs_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 50,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 120, 120, 122, 122, 122, 120, 118, 116, 120, 116, 112})
                        }, { // input_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128, 128, 132, 132, 132, 124, 124, 124, 120, 120, 120})
                        }, { // placeholder7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 50,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({50, 54, 58})
                        }, { // placeholder8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 50,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({50})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_all_tensors_as_inputs_all_inputs_as_internal = TestModelManager::get().add("prelu_quant8_all_tensors_as_inputs_all_inputs_as_internal", get_test_model_quant8_all_tensors_as_inputs_all_inputs_as_internal());

}  // namespace generated_tests::prelu

namespace generated_tests::prelu {

const TestModel& get_test_model_quant8_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128, 128, 132, 132, 132, 124, 124, 124, 120, 120, 120})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 50,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({50, 54, 58})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 120, 120, 124, 124, 124, 120, 116, 112, 120, 112, 104})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_2 = TestModelManager::get().add("prelu_quant8_2", get_test_model_quant8_2());

}  // namespace generated_tests::prelu

namespace generated_tests::prelu {

const TestModel& get_test_model_quant8_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 50,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({50, 54, 58})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 120, 120, 124, 124, 124, 120, 116, 112, 120, 112, 104})
                        }, { // input_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128, 128, 132, 132, 132, 124, 124, 124, 120, 120, 120})
                        }, { // placeholder9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_all_inputs_as_internal_2 = TestModelManager::get().add("prelu_quant8_all_inputs_as_internal_2", get_test_model_quant8_all_inputs_as_internal_2());

}  // namespace generated_tests::prelu

namespace generated_tests::prelu {

const TestModel& get_test_model_quant8_all_tensors_as_inputs_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128, 128, 132, 132, 132, 124, 124, 124, 120, 120, 120})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 50,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({50, 54, 58})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 120, 120, 124, 124, 124, 120, 116, 112, 120, 112, 104})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_all_tensors_as_inputs_2 = TestModelManager::get().add("prelu_quant8_all_tensors_as_inputs_2", get_test_model_quant8_all_tensors_as_inputs_2());

}  // namespace generated_tests::prelu

namespace generated_tests::prelu {

const TestModel& get_test_model_quant8_all_tensors_as_inputs_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 50,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 120, 120, 124, 124, 124, 120, 116, 112, 120, 112, 104})
                        }, { // input_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128, 128, 132, 132, 132, 124, 124, 124, 120, 120, 120})
                        }, { // placeholder10
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 50,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({50, 54, 58})
                        }, { // placeholder11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 50,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({50})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_all_tensors_as_inputs_all_inputs_as_internal_2 = TestModelManager::get().add("prelu_quant8_all_tensors_as_inputs_all_inputs_as_internal_2", get_test_model_quant8_all_tensors_as_inputs_all_inputs_as_internal_2());

}  // namespace generated_tests::prelu

namespace generated_tests::prelu {

const TestModel& get_test_model_quant8_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128, 128, 132, 132, 132, 124, 124, 124, 120, 120, 120})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 50,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({50, 52, 54})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.125f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 120, 120, 128, 128, 128, 120, 112, 104, 120, 104, 88})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_3 = TestModelManager::get().add("prelu_quant8_3", get_test_model_quant8_3());

}  // namespace generated_tests::prelu

namespace generated_tests::prelu {

const TestModel& get_test_model_quant8_all_inputs_as_internal_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 50,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({50, 52, 54})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.125f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 120, 120, 128, 128, 128, 120, 112, 104, 120, 104, 88})
                        }, { // input_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128, 128, 132, 132, 132, 124, 124, 124, 120, 120, 120})
                        }, { // placeholder12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_all_inputs_as_internal_3 = TestModelManager::get().add("prelu_quant8_all_inputs_as_internal_3", get_test_model_quant8_all_inputs_as_internal_3());

}  // namespace generated_tests::prelu

namespace generated_tests::prelu {

const TestModel& get_test_model_quant8_all_tensors_as_inputs_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128, 128, 132, 132, 132, 124, 124, 124, 120, 120, 120})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 50,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({50, 52, 54})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.125f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 120, 120, 128, 128, 128, 120, 112, 104, 120, 104, 88})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_all_tensors_as_inputs_3 = TestModelManager::get().add("prelu_quant8_all_tensors_as_inputs_3", get_test_model_quant8_all_tensors_as_inputs_3());

}  // namespace generated_tests::prelu

namespace generated_tests::prelu {

const TestModel& get_test_model_quant8_all_tensors_as_inputs_all_inputs_as_internal_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 50,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.125f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 120, 120, 128, 128, 128, 120, 112, 104, 120, 104, 88})
                        }, { // input_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128, 128, 132, 132, 132, 124, 124, 124, 120, 120, 120})
                        }, { // placeholder13
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
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
                        }, { // alpha_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 50,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({50, 52, 54})
                        }, { // placeholder14
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 50,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({50})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_all_tensors_as_inputs_all_inputs_as_internal_3 = TestModelManager::get().add("prelu_quant8_all_tensors_as_inputs_all_inputs_as_internal_3", get_test_model_quant8_all_tensors_as_inputs_all_inputs_as_internal_3());

}  // namespace generated_tests::prelu

namespace generated_tests::prelu {

const TestModel& get_test_model_quant8_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128, 128, 132, 132, 132, 124, 124, 124, 120, 120, 120})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 50,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({50, 52, 54})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 120, 120, 130, 130, 130, 120, 110, 100, 120, 100, 80})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_4 = TestModelManager::get().add("prelu_quant8_4", get_test_model_quant8_4());

}  // namespace generated_tests::prelu

namespace generated_tests::prelu {

const TestModel& get_test_model_quant8_all_inputs_as_internal_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 50,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({50, 52, 54})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 120, 120, 130, 130, 130, 120, 110, 100, 120, 100, 80})
                        }, { // input_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128, 128, 132, 132, 132, 124, 124, 124, 120, 120, 120})
                        }, { // placeholder15
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_all_inputs_as_internal_4 = TestModelManager::get().add("prelu_quant8_all_inputs_as_internal_4", get_test_model_quant8_all_inputs_as_internal_4());

}  // namespace generated_tests::prelu

namespace generated_tests::prelu {

const TestModel& get_test_model_quant8_all_tensors_as_inputs_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128, 128, 132, 132, 132, 124, 124, 124, 120, 120, 120})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 50,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({50, 52, 54})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 120, 120, 130, 130, 130, 120, 110, 100, 120, 100, 80})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_all_tensors_as_inputs_4 = TestModelManager::get().add("prelu_quant8_all_tensors_as_inputs_4", get_test_model_quant8_all_tensors_as_inputs_4());

}  // namespace generated_tests::prelu

namespace generated_tests::prelu {

const TestModel& get_test_model_quant8_all_tensors_as_inputs_all_inputs_as_internal_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 50,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = 120,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({120, 120, 120, 130, 130, 130, 120, 110, 100, 120, 100, 80})
                        }, { // input_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128, 128, 132, 132, 132, 124, 124, 124, 120, 120, 120})
                        }, { // placeholder16
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
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
                        }, { // alpha_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 50,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({50, 52, 54})
                        }, { // placeholder17
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 50,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({50})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_all_tensors_as_inputs_all_inputs_as_internal_4 = TestModelManager::get().add("prelu_quant8_all_tensors_as_inputs_all_inputs_as_internal_4", get_test_model_quant8_all_tensors_as_inputs_all_inputs_as_internal_4());

}  // namespace generated_tests::prelu

namespace generated_tests::prelu {

const TestModel& get_test_model_float16() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, -1.0f, -1.0f, -1.0f, -2.0f, -2.0f, -2.0f})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 1.0f, 2.0f})
                        }, { // output
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f, -1.0f, -2.0f, 0.0f, -2.0f, -4.0f})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16 = TestModelManager::get().add("prelu_float16", get_test_model_float16());

}  // namespace generated_tests::prelu

namespace generated_tests::prelu {

const TestModel& get_test_model_float16_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 1.0f, 2.0f})
                        }, { // output
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f, -1.0f, -2.0f, 0.0f, -2.0f, -4.0f})
                        }, { // input_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, -1.0f, -1.0f, -1.0f, -2.0f, -2.0f, -2.0f})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_all_inputs_as_internal = TestModelManager::get().add("prelu_float16_all_inputs_as_internal", get_test_model_float16_all_inputs_as_internal());

}  // namespace generated_tests::prelu

namespace generated_tests::prelu {

const TestModel& get_test_model_float16_all_tensors_as_inputs() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, -1.0f, -1.0f, -1.0f, -2.0f, -2.0f, -2.0f})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 1.0f, 2.0f})
                        }, { // output
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f, -1.0f, -2.0f, 0.0f, -2.0f, -4.0f})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_all_tensors_as_inputs = TestModelManager::get().add("prelu_float16_all_tensors_as_inputs", get_test_model_float16_all_tensors_as_inputs());

}  // namespace generated_tests::prelu

namespace generated_tests::prelu {

const TestModel& get_test_model_float16_all_tensors_as_inputs_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // alpha
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // output
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f, -1.0f, -2.0f, 0.0f, -2.0f, -4.0f})
                        }, { // input_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, -1.0f, -1.0f, -1.0f, -2.0f, -2.0f, -2.0f})
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
                        }, { // alpha_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 1, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 1.0f, 2.0f})
                        }, { // placeholder20
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_all_tensors_as_inputs_all_inputs_as_internal = TestModelManager::get().add("prelu_float16_all_tensors_as_inputs_all_inputs_as_internal", get_test_model_float16_all_tensors_as_inputs_all_inputs_as_internal());

}  // namespace generated_tests::prelu

