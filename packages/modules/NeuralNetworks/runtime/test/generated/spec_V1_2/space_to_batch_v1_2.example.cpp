// Generated from space_to_batch_v1_2.mod.py
// DO NOT EDIT
// clang-format off
#include "TestHarness.h"
using namespace test_helper;  // NOLINT(google-build-using-namespace)

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.4f, 2.3f, 3.2f, 4.1f, 5.4f, 6.3f, 7.2f, 8.1f})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 1, 1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.4f, 2.3f, 3.2f, 4.1f, 5.4f, 6.3f, 7.2f, 8.1f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_1
    };
    return model;
}

const auto dummy_test_model_nhwc = TestModelManager::get().add("space_to_batch_v1_2_nhwc", get_test_model_nhwc());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 1, 1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.4f, 2.3f, 3.2f, 4.1f, 5.4f, 6.3f, 7.2f, 8.1f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.4f, 2.3f, 3.2f, 4.1f, 5.4f, 6.3f, 7.2f, 8.1f})
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_1
    };
    return model;
}

const auto dummy_test_model_nhwc_all_inputs_as_internal = TestModelManager::get().add("space_to_batch_v1_2_nhwc_all_inputs_as_internal", get_test_model_nhwc_all_inputs_as_internal());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_all_tensors_as_inputs() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.4f, 2.3f, 3.2f, 4.1f, 5.4f, 6.3f, 7.2f, 8.1f})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 1, 1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.4f, 2.3f, 3.2f, 4.1f, 5.4f, 6.3f, 7.2f, 8.1f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 2},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_1
    };
    return model;
}

const auto dummy_test_model_nhwc_all_tensors_as_inputs = TestModelManager::get().add("space_to_batch_v1_2_nhwc_all_tensors_as_inputs", get_test_model_nhwc_all_tensors_as_inputs());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_all_tensors_as_inputs_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 1, 1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.4f, 2.3f, 3.2f, 4.1f, 5.4f, 6.3f, 7.2f, 8.1f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.4f, 2.3f, 3.2f, 4.1f, 5.4f, 6.3f, 7.2f, 8.1f})
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {2, 5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_1
    };
    return model;
}

const auto dummy_test_model_nhwc_all_tensors_as_inputs_all_inputs_as_internal = TestModelManager::get().add("space_to_batch_v1_2_nhwc_all_tensors_as_inputs_all_inputs_as_internal", get_test_model_nhwc_all_tensors_as_inputs_all_inputs_as_internal());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_relaxed() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.4f, 2.3f, 3.2f, 4.1f, 5.4f, 6.3f, 7.2f, 8.1f})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 1, 1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.4f, 2.3f, 3.2f, 4.1f, 5.4f, 6.3f, 7.2f, 8.1f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_nhwc_relaxed = TestModelManager::get().add("space_to_batch_v1_2_nhwc_relaxed", get_test_model_nhwc_relaxed());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_relaxed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 1, 1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.4f, 2.3f, 3.2f, 4.1f, 5.4f, 6.3f, 7.2f, 8.1f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.4f, 2.3f, 3.2f, 4.1f, 5.4f, 6.3f, 7.2f, 8.1f})
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_nhwc_relaxed_all_inputs_as_internal = TestModelManager::get().add("space_to_batch_v1_2_nhwc_relaxed_all_inputs_as_internal", get_test_model_nhwc_relaxed_all_inputs_as_internal());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_relaxed_all_tensors_as_inputs() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.4f, 2.3f, 3.2f, 4.1f, 5.4f, 6.3f, 7.2f, 8.1f})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 1, 1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.4f, 2.3f, 3.2f, 4.1f, 5.4f, 6.3f, 7.2f, 8.1f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 2},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_nhwc_relaxed_all_tensors_as_inputs = TestModelManager::get().add("space_to_batch_v1_2_nhwc_relaxed_all_tensors_as_inputs", get_test_model_nhwc_relaxed_all_tensors_as_inputs());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_relaxed_all_tensors_as_inputs_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 1, 1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.4f, 2.3f, 3.2f, 4.1f, 5.4f, 6.3f, 7.2f, 8.1f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.4f, 2.3f, 3.2f, 4.1f, 5.4f, 6.3f, 7.2f, 8.1f})
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {2, 5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_nhwc_relaxed_all_tensors_as_inputs_all_inputs_as_internal = TestModelManager::get().add("space_to_batch_v1_2_nhwc_relaxed_all_tensors_as_inputs_all_inputs_as_internal", get_test_model_nhwc_relaxed_all_tensors_as_inputs_all_inputs_as_internal());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_float16() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.399999976158142f, 2.299999952316284f, 3.200000047683716f, 4.099999904632568f, 5.400000095367432f, 6.300000190734863f, 7.199999809265137f, 8.100000381469727f})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {4, 1, 1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.399999976158142f, 2.299999952316284f, 3.200000047683716f, 4.099999904632568f, 5.400000095367432f, 6.300000190734863f, 7.199999809265137f, 8.100000381469727f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nhwc_float16 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_float16", get_test_model_nhwc_float16());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_float16_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {4, 1, 1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.399999976158142f, 2.299999952316284f, 3.200000047683716f, 4.099999904632568f, 5.400000095367432f, 6.300000190734863f, 7.199999809265137f, 8.100000381469727f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.399999976158142f, 2.299999952316284f, 3.200000047683716f, 4.099999904632568f, 5.400000095367432f, 6.300000190734863f, 7.199999809265137f, 8.100000381469727f})
                        }, { // placeholder4
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nhwc_float16_all_inputs_as_internal = TestModelManager::get().add("space_to_batch_v1_2_nhwc_float16_all_inputs_as_internal", get_test_model_nhwc_float16_all_inputs_as_internal());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_float16_all_tensors_as_inputs() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.399999976158142f, 2.299999952316284f, 3.200000047683716f, 4.099999904632568f, 5.400000095367432f, 6.300000190734863f, 7.199999809265137f, 8.100000381469727f})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {4, 1, 1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.399999976158142f, 2.299999952316284f, 3.200000047683716f, 4.099999904632568f, 5.400000095367432f, 6.300000190734863f, 7.199999809265137f, 8.100000381469727f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 2},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nhwc_float16_all_tensors_as_inputs = TestModelManager::get().add("space_to_batch_v1_2_nhwc_float16_all_tensors_as_inputs", get_test_model_nhwc_float16_all_tensors_as_inputs());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_float16_all_tensors_as_inputs_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {4, 1, 1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.399999976158142f, 2.299999952316284f, 3.200000047683716f, 4.099999904632568f, 5.400000095367432f, 6.300000190734863f, 7.199999809265137f, 8.100000381469727f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.399999976158142f, 2.299999952316284f, 3.200000047683716f, 4.099999904632568f, 5.400000095367432f, 6.300000190734863f, 7.199999809265137f, 8.100000381469727f})
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {2, 5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nhwc_float16_all_tensors_as_inputs_all_inputs_as_internal = TestModelManager::get().add("space_to_batch_v1_2_nhwc_float16_all_tensors_as_inputs_all_inputs_as_internal", get_test_model_nhwc_float16_all_tensors_as_inputs_all_inputs_as_internal());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_quant8() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({14, 23, 32, 41, 54, 63, 72, 81})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 1, 1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({14, 23, 32, 41, 54, 63, 72, 81})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_1
    };
    return model;
}

const auto dummy_test_model_nhwc_quant8 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_quant8", get_test_model_nhwc_quant8());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_quant8_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 1, 1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({14, 23, 32, 41, 54, 63, 72, 81})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({14, 23, 32, 41, 54, 63, 72, 81})
                        }, { // placeholder6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_1
    };
    return model;
}

const auto dummy_test_model_nhwc_quant8_all_inputs_as_internal = TestModelManager::get().add("space_to_batch_v1_2_nhwc_quant8_all_inputs_as_internal", get_test_model_nhwc_quant8_all_inputs_as_internal());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_quant8_all_tensors_as_inputs() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({14, 23, 32, 41, 54, 63, 72, 81})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 1, 1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({14, 23, 32, 41, 54, 63, 72, 81})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 2},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_1
    };
    return model;
}

const auto dummy_test_model_nhwc_quant8_all_tensors_as_inputs = TestModelManager::get().add("space_to_batch_v1_2_nhwc_quant8_all_tensors_as_inputs", get_test_model_nhwc_quant8_all_tensors_as_inputs());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_quant8_all_tensors_as_inputs_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 1, 1, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({14, 23, 32, 41, 54, 63, 72, 81})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({14, 23, 32, 41, 54, 63, 72, 81})
                        }, { // placeholder7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {2, 5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_1
    };
    return model;
}

const auto dummy_test_model_nhwc_quant8_all_tensors_as_inputs_all_inputs_as_internal = TestModelManager::get().add("space_to_batch_v1_2_nhwc_quant8_all_tensors_as_inputs_all_inputs_as_internal", get_test_model_nhwc_quant8_all_tensors_as_inputs_all_inputs_as_internal());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.4f, 3.2f, 5.4f, 7.2f, 2.3f, 4.1f, 6.3f, 8.1f})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 2, 1, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.4f, 2.3f, 3.2f, 4.1f, 5.4f, 6.3f, 7.2f, 8.1f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw = TestModelManager::get().add("space_to_batch_v1_2_nchw", get_test_model_nchw());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 2, 1, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.4f, 2.3f, 3.2f, 4.1f, 5.4f, 6.3f, 7.2f, 8.1f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.4f, 3.2f, 5.4f, 7.2f, 2.3f, 4.1f, 6.3f, 8.1f})
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_all_inputs_as_internal = TestModelManager::get().add("space_to_batch_v1_2_nchw_all_inputs_as_internal", get_test_model_nchw_all_inputs_as_internal());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_all_tensors_as_inputs() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.4f, 3.2f, 5.4f, 7.2f, 2.3f, 4.1f, 6.3f, 8.1f})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 2, 1, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.4f, 2.3f, 3.2f, 4.1f, 5.4f, 6.3f, 7.2f, 8.1f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 2},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_all_tensors_as_inputs = TestModelManager::get().add("space_to_batch_v1_2_nchw_all_tensors_as_inputs", get_test_model_nchw_all_tensors_as_inputs());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_all_tensors_as_inputs_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 2, 1, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.4f, 2.3f, 3.2f, 4.1f, 5.4f, 6.3f, 7.2f, 8.1f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.4f, 3.2f, 5.4f, 7.2f, 2.3f, 4.1f, 6.3f, 8.1f})
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {2, 5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_all_tensors_as_inputs_all_inputs_as_internal = TestModelManager::get().add("space_to_batch_v1_2_nchw_all_tensors_as_inputs_all_inputs_as_internal", get_test_model_nchw_all_tensors_as_inputs_all_inputs_as_internal());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_relaxed() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.4f, 3.2f, 5.4f, 7.2f, 2.3f, 4.1f, 6.3f, 8.1f})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 2, 1, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.4f, 2.3f, 3.2f, 4.1f, 5.4f, 6.3f, 7.2f, 8.1f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_nchw_relaxed = TestModelManager::get().add("space_to_batch_v1_2_nchw_relaxed", get_test_model_nchw_relaxed());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_relaxed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 2, 1, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.4f, 2.3f, 3.2f, 4.1f, 5.4f, 6.3f, 7.2f, 8.1f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.4f, 3.2f, 5.4f, 7.2f, 2.3f, 4.1f, 6.3f, 8.1f})
                        }, { // placeholder10
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_nchw_relaxed_all_inputs_as_internal = TestModelManager::get().add("space_to_batch_v1_2_nchw_relaxed_all_inputs_as_internal", get_test_model_nchw_relaxed_all_inputs_as_internal());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_relaxed_all_tensors_as_inputs() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.4f, 3.2f, 5.4f, 7.2f, 2.3f, 4.1f, 6.3f, 8.1f})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 2, 1, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.4f, 2.3f, 3.2f, 4.1f, 5.4f, 6.3f, 7.2f, 8.1f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 2},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_nchw_relaxed_all_tensors_as_inputs = TestModelManager::get().add("space_to_batch_v1_2_nchw_relaxed_all_tensors_as_inputs", get_test_model_nchw_relaxed_all_tensors_as_inputs());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_relaxed_all_tensors_as_inputs_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 2, 1, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.4f, 2.3f, 3.2f, 4.1f, 5.4f, 6.3f, 7.2f, 8.1f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.4f, 3.2f, 5.4f, 7.2f, 2.3f, 4.1f, 6.3f, 8.1f})
                        }, { // placeholder11
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {2, 5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_nchw_relaxed_all_tensors_as_inputs_all_inputs_as_internal = TestModelManager::get().add("space_to_batch_v1_2_nchw_relaxed_all_tensors_as_inputs_all_inputs_as_internal", get_test_model_nchw_relaxed_all_tensors_as_inputs_all_inputs_as_internal());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_float16() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.399999976158142f, 3.200000047683716f, 5.400000095367432f, 7.199999809265137f, 2.299999952316284f, 4.099999904632568f, 6.300000190734863f, 8.100000381469727f})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {4, 2, 1, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.399999976158142f, 2.299999952316284f, 3.200000047683716f, 4.099999904632568f, 5.400000095367432f, 6.300000190734863f, 7.199999809265137f, 8.100000381469727f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_float16 = TestModelManager::get().add("space_to_batch_v1_2_nchw_float16", get_test_model_nchw_float16());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_float16_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {4, 2, 1, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.399999976158142f, 2.299999952316284f, 3.200000047683716f, 4.099999904632568f, 5.400000095367432f, 6.300000190734863f, 7.199999809265137f, 8.100000381469727f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.399999976158142f, 3.200000047683716f, 5.400000095367432f, 7.199999809265137f, 2.299999952316284f, 4.099999904632568f, 6.300000190734863f, 8.100000381469727f})
                        }, { // placeholder12
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_float16_all_inputs_as_internal = TestModelManager::get().add("space_to_batch_v1_2_nchw_float16_all_inputs_as_internal", get_test_model_nchw_float16_all_inputs_as_internal());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_float16_all_tensors_as_inputs() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.399999976158142f, 3.200000047683716f, 5.400000095367432f, 7.199999809265137f, 2.299999952316284f, 4.099999904632568f, 6.300000190734863f, 8.100000381469727f})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {4, 2, 1, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.399999976158142f, 2.299999952316284f, 3.200000047683716f, 4.099999904632568f, 5.400000095367432f, 6.300000190734863f, 7.199999809265137f, 8.100000381469727f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 2},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_float16_all_tensors_as_inputs = TestModelManager::get().add("space_to_batch_v1_2_nchw_float16_all_tensors_as_inputs", get_test_model_nchw_float16_all_tensors_as_inputs());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_float16_all_tensors_as_inputs_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {4, 2, 1, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.399999976158142f, 2.299999952316284f, 3.200000047683716f, 4.099999904632568f, 5.400000095367432f, 6.300000190734863f, 7.199999809265137f, 8.100000381469727f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.399999976158142f, 3.200000047683716f, 5.400000095367432f, 7.199999809265137f, 2.299999952316284f, 4.099999904632568f, 6.300000190734863f, 8.100000381469727f})
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {2, 5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_float16_all_tensors_as_inputs_all_inputs_as_internal = TestModelManager::get().add("space_to_batch_v1_2_nchw_float16_all_tensors_as_inputs_all_inputs_as_internal", get_test_model_nchw_float16_all_tensors_as_inputs_all_inputs_as_internal());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_quant8() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({14, 32, 54, 72, 23, 41, 63, 81})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2, 1, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({14, 23, 32, 41, 54, 63, 72, 81})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_quant8 = TestModelManager::get().add("space_to_batch_v1_2_nchw_quant8", get_test_model_nchw_quant8());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_quant8_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2, 1, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({14, 23, 32, 41, 54, 63, 72, 81})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({14, 32, 54, 72, 23, 41, 63, 81})
                        }, { // placeholder14
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_quant8_all_inputs_as_internal = TestModelManager::get().add("space_to_batch_v1_2_nchw_quant8_all_inputs_as_internal", get_test_model_nchw_quant8_all_inputs_as_internal());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_quant8_all_tensors_as_inputs() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({14, 32, 54, 72, 23, 41, 63, 81})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2, 1, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({14, 23, 32, 41, 54, 63, 72, 81})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 2},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_quant8_all_tensors_as_inputs = TestModelManager::get().add("space_to_batch_v1_2_nchw_quant8_all_tensors_as_inputs", get_test_model_nchw_quant8_all_tensors_as_inputs());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_quant8_all_tensors_as_inputs_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2, 1, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({14, 23, 32, 41, 54, 63, 72, 81})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({14, 32, 54, 72, 23, 41, 63, 81})
                        }, { // placeholder15
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {2, 5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_quant8_all_tensors_as_inputs_all_inputs_as_internal = TestModelManager::get().add("space_to_batch_v1_2_nchw_quant8_all_tensors_as_inputs_all_inputs_as_internal", get_test_model_nchw_quant8_all_tensors_as_inputs_all_inputs_as_internal());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 3.0f, 9.0f, 11.0f, 2.0f, 4.0f, 10.0f, 12.0f, 5.0f, 7.0f, 13.0f, 15.0f, 6.0f, 8.0f, 14.0f, 16.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_1
    };
    return model;
}

const auto dummy_test_model_nhwc_2 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_2", get_test_model_nhwc_2());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 3.0f, 9.0f, 11.0f, 2.0f, 4.0f, 10.0f, 12.0f, 5.0f, 7.0f, 13.0f, 15.0f, 6.0f, 8.0f, 14.0f, 16.0f})
                        }, { // op11_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f})
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_1
    };
    return model;
}

const auto dummy_test_model_nhwc_all_inputs_as_internal_2 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_all_inputs_as_internal_2", get_test_model_nhwc_all_inputs_as_internal_2());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_all_tensors_as_inputs_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 3.0f, 9.0f, 11.0f, 2.0f, 4.0f, 10.0f, 12.0f, 5.0f, 7.0f, 13.0f, 15.0f, 6.0f, 8.0f, 14.0f, 16.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 2},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_1
    };
    return model;
}

const auto dummy_test_model_nhwc_all_tensors_as_inputs_2 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_all_tensors_as_inputs_2", get_test_model_nhwc_all_tensors_as_inputs_2());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_all_tensors_as_inputs_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 3.0f, 9.0f, 11.0f, 2.0f, 4.0f, 10.0f, 12.0f, 5.0f, 7.0f, 13.0f, 15.0f, 6.0f, 8.0f, 14.0f, 16.0f})
                        }, { // op11_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f})
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {2, 5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_1
    };
    return model;
}

const auto dummy_test_model_nhwc_all_tensors_as_inputs_all_inputs_as_internal_2 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_all_tensors_as_inputs_all_inputs_as_internal_2", get_test_model_nhwc_all_tensors_as_inputs_all_inputs_as_internal_2());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_relaxed_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 3.0f, 9.0f, 11.0f, 2.0f, 4.0f, 10.0f, 12.0f, 5.0f, 7.0f, 13.0f, 15.0f, 6.0f, 8.0f, 14.0f, 16.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_nhwc_relaxed_2 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_relaxed_2", get_test_model_nhwc_relaxed_2());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_relaxed_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 3.0f, 9.0f, 11.0f, 2.0f, 4.0f, 10.0f, 12.0f, 5.0f, 7.0f, 13.0f, 15.0f, 6.0f, 8.0f, 14.0f, 16.0f})
                        }, { // op11_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f})
                        }, { // placeholder18
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_nhwc_relaxed_all_inputs_as_internal_2 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_relaxed_all_inputs_as_internal_2", get_test_model_nhwc_relaxed_all_inputs_as_internal_2());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_relaxed_all_tensors_as_inputs_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 3.0f, 9.0f, 11.0f, 2.0f, 4.0f, 10.0f, 12.0f, 5.0f, 7.0f, 13.0f, 15.0f, 6.0f, 8.0f, 14.0f, 16.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 2},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_nhwc_relaxed_all_tensors_as_inputs_2 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_relaxed_all_tensors_as_inputs_2", get_test_model_nhwc_relaxed_all_tensors_as_inputs_2());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_relaxed_all_tensors_as_inputs_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 3.0f, 9.0f, 11.0f, 2.0f, 4.0f, 10.0f, 12.0f, 5.0f, 7.0f, 13.0f, 15.0f, 6.0f, 8.0f, 14.0f, 16.0f})
                        }, { // op11_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f})
                        }, { // placeholder19
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {2, 5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_nhwc_relaxed_all_tensors_as_inputs_all_inputs_as_internal_2 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_relaxed_all_tensors_as_inputs_all_inputs_as_internal_2", get_test_model_nhwc_relaxed_all_tensors_as_inputs_all_inputs_as_internal_2());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_float16_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {4, 2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f, 3.0f, 9.0f, 11.0f, 2.0f, 4.0f, 10.0f, 12.0f, 5.0f, 7.0f, 13.0f, 15.0f, 6.0f, 8.0f, 14.0f, 16.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nhwc_float16_2 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_float16_2", get_test_model_nhwc_float16_2());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_float16_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {4, 2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f, 3.0f, 9.0f, 11.0f, 2.0f, 4.0f, 10.0f, 12.0f, 5.0f, 7.0f, 13.0f, 15.0f, 6.0f, 8.0f, 14.0f, 16.0f})
                        }, { // op11_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f})
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
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nhwc_float16_all_inputs_as_internal_2 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_float16_all_inputs_as_internal_2", get_test_model_nhwc_float16_all_inputs_as_internal_2());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_float16_all_tensors_as_inputs_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {4, 2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f, 3.0f, 9.0f, 11.0f, 2.0f, 4.0f, 10.0f, 12.0f, 5.0f, 7.0f, 13.0f, 15.0f, 6.0f, 8.0f, 14.0f, 16.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 2},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nhwc_float16_all_tensors_as_inputs_2 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_float16_all_tensors_as_inputs_2", get_test_model_nhwc_float16_all_tensors_as_inputs_2());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_float16_all_tensors_as_inputs_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {4, 2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f, 3.0f, 9.0f, 11.0f, 2.0f, 4.0f, 10.0f, 12.0f, 5.0f, 7.0f, 13.0f, 15.0f, 6.0f, 8.0f, 14.0f, 16.0f})
                        }, { // op11_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f})
                        }, { // placeholder21
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {2, 5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nhwc_float16_all_tensors_as_inputs_all_inputs_as_internal_2 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_float16_all_tensors_as_inputs_all_inputs_as_internal_2", get_test_model_nhwc_float16_all_tensors_as_inputs_all_inputs_as_internal_2());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_quant8_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({2, 6, 18, 22, 4, 8, 20, 24, 10, 14, 26, 30, 12, 16, 28, 32})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_1
    };
    return model;
}

const auto dummy_test_model_nhwc_quant8_2 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_quant8_2", get_test_model_nhwc_quant8_2());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_quant8_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({2, 6, 18, 22, 4, 8, 20, 24, 10, 14, 26, 30, 12, 16, 28, 32})
                        }, { // op11_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32})
                        }, { // placeholder22
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
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
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_1
    };
    return model;
}

const auto dummy_test_model_nhwc_quant8_all_inputs_as_internal_2 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_quant8_all_inputs_as_internal_2", get_test_model_nhwc_quant8_all_inputs_as_internal_2());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_quant8_all_tensors_as_inputs_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({2, 6, 18, 22, 4, 8, 20, 24, 10, 14, 26, 30, 12, 16, 28, 32})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 2},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_1
    };
    return model;
}

const auto dummy_test_model_nhwc_quant8_all_tensors_as_inputs_2 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_quant8_all_tensors_as_inputs_2", get_test_model_nhwc_quant8_all_tensors_as_inputs_2());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_quant8_all_tensors_as_inputs_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({2, 6, 18, 22, 4, 8, 20, 24, 10, 14, 26, 30, 12, 16, 28, 32})
                        }, { // op11_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32})
                        }, { // placeholder23
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {2, 5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_1
    };
    return model;
}

const auto dummy_test_model_nhwc_quant8_all_tensors_as_inputs_all_inputs_as_internal_2 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_quant8_all_tensors_as_inputs_all_inputs_as_internal_2", get_test_model_nhwc_quant8_all_tensors_as_inputs_all_inputs_as_internal_2());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 3.0f, 9.0f, 11.0f, 2.0f, 4.0f, 10.0f, 12.0f, 5.0f, 7.0f, 13.0f, 15.0f, 6.0f, 8.0f, 14.0f, 16.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_2 = TestModelManager::get().add("space_to_batch_v1_2_nchw_2", get_test_model_nchw_2());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 3.0f, 9.0f, 11.0f, 2.0f, 4.0f, 10.0f, 12.0f, 5.0f, 7.0f, 13.0f, 15.0f, 6.0f, 8.0f, 14.0f, 16.0f})
                        }, { // op11_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f})
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
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_all_inputs_as_internal_2 = TestModelManager::get().add("space_to_batch_v1_2_nchw_all_inputs_as_internal_2", get_test_model_nchw_all_inputs_as_internal_2());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_all_tensors_as_inputs_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 3.0f, 9.0f, 11.0f, 2.0f, 4.0f, 10.0f, 12.0f, 5.0f, 7.0f, 13.0f, 15.0f, 6.0f, 8.0f, 14.0f, 16.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 2},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_all_tensors_as_inputs_2 = TestModelManager::get().add("space_to_batch_v1_2_nchw_all_tensors_as_inputs_2", get_test_model_nchw_all_tensors_as_inputs_2());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_all_tensors_as_inputs_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 3.0f, 9.0f, 11.0f, 2.0f, 4.0f, 10.0f, 12.0f, 5.0f, 7.0f, 13.0f, 15.0f, 6.0f, 8.0f, 14.0f, 16.0f})
                        }, { // op11_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f})
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {2, 5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_all_tensors_as_inputs_all_inputs_as_internal_2 = TestModelManager::get().add("space_to_batch_v1_2_nchw_all_tensors_as_inputs_all_inputs_as_internal_2", get_test_model_nchw_all_tensors_as_inputs_all_inputs_as_internal_2());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_relaxed_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 3.0f, 9.0f, 11.0f, 2.0f, 4.0f, 10.0f, 12.0f, 5.0f, 7.0f, 13.0f, 15.0f, 6.0f, 8.0f, 14.0f, 16.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_nchw_relaxed_2 = TestModelManager::get().add("space_to_batch_v1_2_nchw_relaxed_2", get_test_model_nchw_relaxed_2());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_relaxed_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 3.0f, 9.0f, 11.0f, 2.0f, 4.0f, 10.0f, 12.0f, 5.0f, 7.0f, 13.0f, 15.0f, 6.0f, 8.0f, 14.0f, 16.0f})
                        }, { // op11_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f})
                        }, { // placeholder26
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
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
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_nchw_relaxed_all_inputs_as_internal_2 = TestModelManager::get().add("space_to_batch_v1_2_nchw_relaxed_all_inputs_as_internal_2", get_test_model_nchw_relaxed_all_inputs_as_internal_2());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_relaxed_all_tensors_as_inputs_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 3.0f, 9.0f, 11.0f, 2.0f, 4.0f, 10.0f, 12.0f, 5.0f, 7.0f, 13.0f, 15.0f, 6.0f, 8.0f, 14.0f, 16.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 2},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_nchw_relaxed_all_tensors_as_inputs_2 = TestModelManager::get().add("space_to_batch_v1_2_nchw_relaxed_all_tensors_as_inputs_2", get_test_model_nchw_relaxed_all_tensors_as_inputs_2());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_relaxed_all_tensors_as_inputs_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 3.0f, 9.0f, 11.0f, 2.0f, 4.0f, 10.0f, 12.0f, 5.0f, 7.0f, 13.0f, 15.0f, 6.0f, 8.0f, 14.0f, 16.0f})
                        }, { // op11_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f})
                        }, { // placeholder27
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {2, 5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_nchw_relaxed_all_tensors_as_inputs_all_inputs_as_internal_2 = TestModelManager::get().add("space_to_batch_v1_2_nchw_relaxed_all_tensors_as_inputs_all_inputs_as_internal_2", get_test_model_nchw_relaxed_all_tensors_as_inputs_all_inputs_as_internal_2());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_float16_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {4, 1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f, 3.0f, 9.0f, 11.0f, 2.0f, 4.0f, 10.0f, 12.0f, 5.0f, 7.0f, 13.0f, 15.0f, 6.0f, 8.0f, 14.0f, 16.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_float16_2 = TestModelManager::get().add("space_to_batch_v1_2_nchw_float16_2", get_test_model_nchw_float16_2());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_float16_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {4, 1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f, 3.0f, 9.0f, 11.0f, 2.0f, 4.0f, 10.0f, 12.0f, 5.0f, 7.0f, 13.0f, 15.0f, 6.0f, 8.0f, 14.0f, 16.0f})
                        }, { // op11_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f})
                        }, { // placeholder28
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
                        }, { // param32
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_float16_all_inputs_as_internal_2 = TestModelManager::get().add("space_to_batch_v1_2_nchw_float16_all_inputs_as_internal_2", get_test_model_nchw_float16_all_inputs_as_internal_2());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_float16_all_tensors_as_inputs_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {4, 1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f, 3.0f, 9.0f, 11.0f, 2.0f, 4.0f, 10.0f, 12.0f, 5.0f, 7.0f, 13.0f, 15.0f, 6.0f, 8.0f, 14.0f, 16.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 2},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_float16_all_tensors_as_inputs_2 = TestModelManager::get().add("space_to_batch_v1_2_nchw_float16_all_tensors_as_inputs_2", get_test_model_nchw_float16_all_tensors_as_inputs_2());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_float16_all_tensors_as_inputs_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {4, 1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f, 3.0f, 9.0f, 11.0f, 2.0f, 4.0f, 10.0f, 12.0f, 5.0f, 7.0f, 13.0f, 15.0f, 6.0f, 8.0f, 14.0f, 16.0f})
                        }, { // op11_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f})
                        }, { // placeholder29
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
                        }, { // param33
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {2, 5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_float16_all_tensors_as_inputs_all_inputs_as_internal_2 = TestModelManager::get().add("space_to_batch_v1_2_nchw_float16_all_tensors_as_inputs_all_inputs_as_internal_2", get_test_model_nchw_float16_all_tensors_as_inputs_all_inputs_as_internal_2());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_quant8_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({2, 6, 18, 22, 4, 8, 20, 24, 10, 14, 26, 30, 12, 16, 28, 32})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_quant8_2 = TestModelManager::get().add("space_to_batch_v1_2_nchw_quant8_2", get_test_model_nchw_quant8_2());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_quant8_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({2, 6, 18, 22, 4, 8, 20, 24, 10, 14, 26, 30, 12, 16, 28, 32})
                        }, { // op11_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32})
                        }, { // placeholder30
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
                        }, { // param34
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_quant8_all_inputs_as_internal_2 = TestModelManager::get().add("space_to_batch_v1_2_nchw_quant8_all_inputs_as_internal_2", get_test_model_nchw_quant8_all_inputs_as_internal_2());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_quant8_all_tensors_as_inputs_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({2, 6, 18, 22, 4, 8, 20, 24, 10, 14, 26, 30, 12, 16, 28, 32})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 2},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_quant8_all_tensors_as_inputs_2 = TestModelManager::get().add("space_to_batch_v1_2_nchw_quant8_all_tensors_as_inputs_2", get_test_model_nchw_quant8_all_tensors_as_inputs_2());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_quant8_all_tensors_as_inputs_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                        }, { // paddings
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({2, 6, 18, 22, 4, 8, 20, 24, 10, 14, 26, 30, 12, 16, 28, 32})
                        }, { // op11_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32})
                        }, { // placeholder31
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({0})
                        }, { // param35
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {2, 5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_quant8_all_tensors_as_inputs_all_inputs_as_internal_2 = TestModelManager::get().add("space_to_batch_v1_2_nchw_quant8_all_tensors_as_inputs_all_inputs_as_internal_2", get_test_model_nchw_quant8_all_tensors_as_inputs_all_inputs_as_internal_2());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op12
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 5, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 0, 2, 0})
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
                        }, { // op42
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {6, 2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 1.0f, 0.0f, 7.0f, 0.0f, 2.0f, 0.0f, 8.0f, 0.0f, 3.0f, 0.0f, 9.0f, 0.0f, 4.0f, 0.0f, 10.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_1
    };
    return model;
}

const auto dummy_test_model_nhwc_3 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_3", get_test_model_nhwc_3());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_all_inputs_as_internal_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op12
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 5, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 0, 2, 0})
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
                        }, { // op42
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {6, 2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 1.0f, 0.0f, 7.0f, 0.0f, 2.0f, 0.0f, 8.0f, 0.0f, 3.0f, 0.0f, 9.0f, 0.0f, 4.0f, 0.0f, 10.0f})
                        }, { // op12_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 5, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f})
                        }, { // placeholder32
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
                        }, { // param36
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_1
    };
    return model;
}

const auto dummy_test_model_nhwc_all_inputs_as_internal_3 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_all_inputs_as_internal_3", get_test_model_nhwc_all_inputs_as_internal_3());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_all_tensors_as_inputs_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op12
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 5, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 0, 2, 0})
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
                        }, { // op42
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {6, 2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 1.0f, 0.0f, 7.0f, 0.0f, 2.0f, 0.0f, 8.0f, 0.0f, 3.0f, 0.0f, 9.0f, 0.0f, 4.0f, 0.0f, 10.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 2},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_1
    };
    return model;
}

const auto dummy_test_model_nhwc_all_tensors_as_inputs_3 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_all_tensors_as_inputs_3", get_test_model_nhwc_all_tensors_as_inputs_3());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_all_tensors_as_inputs_all_inputs_as_internal_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op12
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 5, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 0, 2, 0})
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
                        }, { // op42
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {6, 2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 1.0f, 0.0f, 7.0f, 0.0f, 2.0f, 0.0f, 8.0f, 0.0f, 3.0f, 0.0f, 9.0f, 0.0f, 4.0f, 0.0f, 10.0f})
                        }, { // op12_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 5, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f})
                        }, { // placeholder33
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
                        }, { // param37
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {2, 5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_1
    };
    return model;
}

const auto dummy_test_model_nhwc_all_tensors_as_inputs_all_inputs_as_internal_3 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_all_tensors_as_inputs_all_inputs_as_internal_3", get_test_model_nhwc_all_tensors_as_inputs_all_inputs_as_internal_3());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_relaxed_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op12
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 5, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 0, 2, 0})
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
                        }, { // op42
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {6, 2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 1.0f, 0.0f, 7.0f, 0.0f, 2.0f, 0.0f, 8.0f, 0.0f, 3.0f, 0.0f, 9.0f, 0.0f, 4.0f, 0.0f, 10.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_nhwc_relaxed_3 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_relaxed_3", get_test_model_nhwc_relaxed_3());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_relaxed_all_inputs_as_internal_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op12
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 5, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 0, 2, 0})
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
                        }, { // op42
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {6, 2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 1.0f, 0.0f, 7.0f, 0.0f, 2.0f, 0.0f, 8.0f, 0.0f, 3.0f, 0.0f, 9.0f, 0.0f, 4.0f, 0.0f, 10.0f})
                        }, { // op12_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 5, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f})
                        }, { // placeholder34
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
                        }, { // param38
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_nhwc_relaxed_all_inputs_as_internal_3 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_relaxed_all_inputs_as_internal_3", get_test_model_nhwc_relaxed_all_inputs_as_internal_3());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_relaxed_all_tensors_as_inputs_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op12
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 5, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 0, 2, 0})
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
                        }, { // op42
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {6, 2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 1.0f, 0.0f, 7.0f, 0.0f, 2.0f, 0.0f, 8.0f, 0.0f, 3.0f, 0.0f, 9.0f, 0.0f, 4.0f, 0.0f, 10.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 2},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_nhwc_relaxed_all_tensors_as_inputs_3 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_relaxed_all_tensors_as_inputs_3", get_test_model_nhwc_relaxed_all_tensors_as_inputs_3());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_relaxed_all_tensors_as_inputs_all_inputs_as_internal_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op12
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 5, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 0, 2, 0})
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
                        }, { // op42
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {6, 2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 1.0f, 0.0f, 7.0f, 0.0f, 2.0f, 0.0f, 8.0f, 0.0f, 3.0f, 0.0f, 9.0f, 0.0f, 4.0f, 0.0f, 10.0f})
                        }, { // op12_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 5, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f})
                        }, { // placeholder35
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
                        }, { // param39
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {2, 5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_nhwc_relaxed_all_tensors_as_inputs_all_inputs_as_internal_3 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_relaxed_all_tensors_as_inputs_all_inputs_as_internal_3", get_test_model_nhwc_relaxed_all_tensors_as_inputs_all_inputs_as_internal_3());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_float16_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op12
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 5, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 0, 2, 0})
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
                        }, { // op42
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {6, 2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 1.0f, 0.0f, 7.0f, 0.0f, 2.0f, 0.0f, 8.0f, 0.0f, 3.0f, 0.0f, 9.0f, 0.0f, 4.0f, 0.0f, 10.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nhwc_float16_3 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_float16_3", get_test_model_nhwc_float16_3());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_float16_all_inputs_as_internal_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op12
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 5, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 0, 2, 0})
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
                        }, { // op42
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {6, 2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 1.0f, 0.0f, 7.0f, 0.0f, 2.0f, 0.0f, 8.0f, 0.0f, 3.0f, 0.0f, 9.0f, 0.0f, 4.0f, 0.0f, 10.0f})
                        }, { // op12_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 5, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f})
                        }, { // placeholder36
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
                        }, { // param40
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nhwc_float16_all_inputs_as_internal_3 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_float16_all_inputs_as_internal_3", get_test_model_nhwc_float16_all_inputs_as_internal_3());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_float16_all_tensors_as_inputs_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op12
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 5, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 0, 2, 0})
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
                        }, { // op42
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {6, 2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 1.0f, 0.0f, 7.0f, 0.0f, 2.0f, 0.0f, 8.0f, 0.0f, 3.0f, 0.0f, 9.0f, 0.0f, 4.0f, 0.0f, 10.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 2},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nhwc_float16_all_tensors_as_inputs_3 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_float16_all_tensors_as_inputs_3", get_test_model_nhwc_float16_all_tensors_as_inputs_3());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_float16_all_tensors_as_inputs_all_inputs_as_internal_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op12
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 5, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 0, 2, 0})
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
                        }, { // op42
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {6, 2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 1.0f, 0.0f, 7.0f, 0.0f, 2.0f, 0.0f, 8.0f, 0.0f, 3.0f, 0.0f, 9.0f, 0.0f, 4.0f, 0.0f, 10.0f})
                        }, { // op12_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 5, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f})
                        }, { // placeholder37
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
                        }, { // param41
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {2, 5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nhwc_float16_all_tensors_as_inputs_all_inputs_as_internal_3 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_float16_all_tensors_as_inputs_all_inputs_as_internal_3", get_test_model_nhwc_float16_all_tensors_as_inputs_all_inputs_as_internal_3());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_quant8_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 5, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({130, 132, 134, 136, 138, 140, 142, 144, 146, 148})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 0, 2, 0})
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
                        }, { // op42
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {6, 2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128, 128, 138, 128, 128, 128, 140, 128, 130, 128, 142, 128, 132, 128, 144, 128, 134, 128, 146, 128, 136, 128, 148})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nhwc_quant8_3 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_quant8_3", get_test_model_nhwc_quant8_3());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_quant8_all_inputs_as_internal_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 5, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 0, 2, 0})
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
                        }, { // op42
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {6, 2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128, 128, 138, 128, 128, 128, 140, 128, 130, 128, 142, 128, 132, 128, 144, 128, 134, 128, 146, 128, 136, 128, 148})
                        }, { // op12_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 5, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({130, 132, 134, 136, 138, 140, 142, 144, 146, 148})
                        }, { // placeholder38
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
                        }, { // param42
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nhwc_quant8_all_inputs_as_internal_3 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_quant8_all_inputs_as_internal_3", get_test_model_nhwc_quant8_all_inputs_as_internal_3());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_quant8_all_tensors_as_inputs_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 5, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({130, 132, 134, 136, 138, 140, 142, 144, 146, 148})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 0, 2, 0})
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
                        }, { // op42
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {6, 2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128, 128, 138, 128, 128, 128, 140, 128, 130, 128, 142, 128, 132, 128, 144, 128, 134, 128, 146, 128, 136, 128, 148})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 2},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nhwc_quant8_all_tensors_as_inputs_3 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_quant8_all_tensors_as_inputs_3", get_test_model_nhwc_quant8_all_tensors_as_inputs_3());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_quant8_all_tensors_as_inputs_all_inputs_as_internal_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 5, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 0, 2, 0})
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
                        }, { // op42
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {6, 2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128, 128, 138, 128, 128, 128, 140, 128, 130, 128, 142, 128, 132, 128, 144, 128, 134, 128, 146, 128, 136, 128, 148})
                        }, { // op12_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 5, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({130, 132, 134, 136, 138, 140, 142, 144, 146, 148})
                        }, { // placeholder39
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
                        }, { // param43
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {2, 5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nhwc_quant8_all_tensors_as_inputs_all_inputs_as_internal_3 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_quant8_all_tensors_as_inputs_all_inputs_as_internal_3", get_test_model_nhwc_quant8_all_tensors_as_inputs_all_inputs_as_internal_3());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op12
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 5, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 0, 2, 0})
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
                        }, { // op42
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {6, 1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 1.0f, 0.0f, 7.0f, 0.0f, 2.0f, 0.0f, 8.0f, 0.0f, 3.0f, 0.0f, 9.0f, 0.0f, 4.0f, 0.0f, 10.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_3 = TestModelManager::get().add("space_to_batch_v1_2_nchw_3", get_test_model_nchw_3());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_all_inputs_as_internal_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op12
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 5, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 0, 2, 0})
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
                        }, { // op42
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {6, 1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 1.0f, 0.0f, 7.0f, 0.0f, 2.0f, 0.0f, 8.0f, 0.0f, 3.0f, 0.0f, 9.0f, 0.0f, 4.0f, 0.0f, 10.0f})
                        }, { // op12_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 5, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f})
                        }, { // placeholder40
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
                        }, { // param44
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_all_inputs_as_internal_3 = TestModelManager::get().add("space_to_batch_v1_2_nchw_all_inputs_as_internal_3", get_test_model_nchw_all_inputs_as_internal_3());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_all_tensors_as_inputs_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op12
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 5, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 0, 2, 0})
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
                        }, { // op42
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {6, 1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 1.0f, 0.0f, 7.0f, 0.0f, 2.0f, 0.0f, 8.0f, 0.0f, 3.0f, 0.0f, 9.0f, 0.0f, 4.0f, 0.0f, 10.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 2},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_all_tensors_as_inputs_3 = TestModelManager::get().add("space_to_batch_v1_2_nchw_all_tensors_as_inputs_3", get_test_model_nchw_all_tensors_as_inputs_3());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_all_tensors_as_inputs_all_inputs_as_internal_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op12
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 5, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 0, 2, 0})
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
                        }, { // op42
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {6, 1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 1.0f, 0.0f, 7.0f, 0.0f, 2.0f, 0.0f, 8.0f, 0.0f, 3.0f, 0.0f, 9.0f, 0.0f, 4.0f, 0.0f, 10.0f})
                        }, { // op12_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 5, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f})
                        }, { // placeholder41
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
                        }, { // param45
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {2, 5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_all_tensors_as_inputs_all_inputs_as_internal_3 = TestModelManager::get().add("space_to_batch_v1_2_nchw_all_tensors_as_inputs_all_inputs_as_internal_3", get_test_model_nchw_all_tensors_as_inputs_all_inputs_as_internal_3());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_relaxed_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op12
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 5, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 0, 2, 0})
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
                        }, { // op42
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {6, 1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 1.0f, 0.0f, 7.0f, 0.0f, 2.0f, 0.0f, 8.0f, 0.0f, 3.0f, 0.0f, 9.0f, 0.0f, 4.0f, 0.0f, 10.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_nchw_relaxed_3 = TestModelManager::get().add("space_to_batch_v1_2_nchw_relaxed_3", get_test_model_nchw_relaxed_3());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_relaxed_all_inputs_as_internal_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op12
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 5, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 0, 2, 0})
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
                        }, { // op42
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {6, 1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 1.0f, 0.0f, 7.0f, 0.0f, 2.0f, 0.0f, 8.0f, 0.0f, 3.0f, 0.0f, 9.0f, 0.0f, 4.0f, 0.0f, 10.0f})
                        }, { // op12_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 5, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f})
                        }, { // placeholder42
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
                        }, { // param46
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_nchw_relaxed_all_inputs_as_internal_3 = TestModelManager::get().add("space_to_batch_v1_2_nchw_relaxed_all_inputs_as_internal_3", get_test_model_nchw_relaxed_all_inputs_as_internal_3());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_relaxed_all_tensors_as_inputs_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op12
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 5, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 0, 2, 0})
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
                        }, { // op42
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {6, 1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 1.0f, 0.0f, 7.0f, 0.0f, 2.0f, 0.0f, 8.0f, 0.0f, 3.0f, 0.0f, 9.0f, 0.0f, 4.0f, 0.0f, 10.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 2},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_nchw_relaxed_all_tensors_as_inputs_3 = TestModelManager::get().add("space_to_batch_v1_2_nchw_relaxed_all_tensors_as_inputs_3", get_test_model_nchw_relaxed_all_tensors_as_inputs_3());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_relaxed_all_tensors_as_inputs_all_inputs_as_internal_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op12
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 5, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 0, 2, 0})
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
                        }, { // op42
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {6, 1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 1.0f, 0.0f, 7.0f, 0.0f, 2.0f, 0.0f, 8.0f, 0.0f, 3.0f, 0.0f, 9.0f, 0.0f, 4.0f, 0.0f, 10.0f})
                        }, { // op12_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 5, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f})
                        }, { // placeholder43
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
                        }, { // param47
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {2, 5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_nchw_relaxed_all_tensors_as_inputs_all_inputs_as_internal_3 = TestModelManager::get().add("space_to_batch_v1_2_nchw_relaxed_all_tensors_as_inputs_all_inputs_as_internal_3", get_test_model_nchw_relaxed_all_tensors_as_inputs_all_inputs_as_internal_3());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_float16_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op12
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 1, 5, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 0, 2, 0})
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
                        }, { // op42
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {6, 1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 1.0f, 0.0f, 7.0f, 0.0f, 2.0f, 0.0f, 8.0f, 0.0f, 3.0f, 0.0f, 9.0f, 0.0f, 4.0f, 0.0f, 10.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_float16_3 = TestModelManager::get().add("space_to_batch_v1_2_nchw_float16_3", get_test_model_nchw_float16_3());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_float16_all_inputs_as_internal_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op12
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 1, 5, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 0, 2, 0})
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
                        }, { // op42
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {6, 1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 1.0f, 0.0f, 7.0f, 0.0f, 2.0f, 0.0f, 8.0f, 0.0f, 3.0f, 0.0f, 9.0f, 0.0f, 4.0f, 0.0f, 10.0f})
                        }, { // op12_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 1, 5, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f})
                        }, { // placeholder44
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
                        }, { // param48
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_float16_all_inputs_as_internal_3 = TestModelManager::get().add("space_to_batch_v1_2_nchw_float16_all_inputs_as_internal_3", get_test_model_nchw_float16_all_inputs_as_internal_3());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_float16_all_tensors_as_inputs_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op12
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 1, 5, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 0, 2, 0})
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
                        }, { // op42
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {6, 1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 1.0f, 0.0f, 7.0f, 0.0f, 2.0f, 0.0f, 8.0f, 0.0f, 3.0f, 0.0f, 9.0f, 0.0f, 4.0f, 0.0f, 10.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 2},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_float16_all_tensors_as_inputs_3 = TestModelManager::get().add("space_to_batch_v1_2_nchw_float16_all_tensors_as_inputs_3", get_test_model_nchw_float16_all_tensors_as_inputs_3());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_float16_all_tensors_as_inputs_all_inputs_as_internal_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op12
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 1, 5, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 0, 2, 0})
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
                        }, { // op42
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {6, 1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 1.0f, 0.0f, 7.0f, 0.0f, 2.0f, 0.0f, 8.0f, 0.0f, 3.0f, 0.0f, 9.0f, 0.0f, 4.0f, 0.0f, 10.0f})
                        }, { // op12_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 1, 5, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f})
                        }, { // placeholder45
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
                        }, { // param49
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {2, 5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_float16_all_tensors_as_inputs_all_inputs_as_internal_3 = TestModelManager::get().add("space_to_batch_v1_2_nchw_float16_all_tensors_as_inputs_all_inputs_as_internal_3", get_test_model_nchw_float16_all_tensors_as_inputs_all_inputs_as_internal_3());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_quant8_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 1, 5, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({130, 132, 134, 136, 138, 140, 142, 144, 146, 148})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 0, 2, 0})
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
                        }, { // op42
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {6, 1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128, 128, 138, 128, 128, 128, 140, 128, 130, 128, 142, 128, 132, 128, 144, 128, 134, 128, 146, 128, 136, 128, 148})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_quant8_3 = TestModelManager::get().add("space_to_batch_v1_2_nchw_quant8_3", get_test_model_nchw_quant8_3());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_quant8_all_inputs_as_internal_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 1, 5, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 0, 2, 0})
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
                        }, { // op42
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {6, 1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128, 128, 138, 128, 128, 128, 140, 128, 130, 128, 142, 128, 132, 128, 144, 128, 134, 128, 146, 128, 136, 128, 148})
                        }, { // op12_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 1, 5, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({130, 132, 134, 136, 138, 140, 142, 144, 146, 148})
                        }, { // placeholder46
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
                        }, { // param50
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_quant8_all_inputs_as_internal_3 = TestModelManager::get().add("space_to_batch_v1_2_nchw_quant8_all_inputs_as_internal_3", get_test_model_nchw_quant8_all_inputs_as_internal_3());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_quant8_all_tensors_as_inputs_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 1, 5, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({130, 132, 134, 136, 138, 140, 142, 144, 146, 148})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 0, 2, 0})
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
                        }, { // op42
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {6, 1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128, 128, 138, 128, 128, 128, 140, 128, 130, 128, 142, 128, 132, 128, 144, 128, 134, 128, 146, 128, 136, 128, 148})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 2},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_quant8_all_tensors_as_inputs_3 = TestModelManager::get().add("space_to_batch_v1_2_nchw_quant8_all_tensors_as_inputs_3", get_test_model_nchw_quant8_all_tensors_as_inputs_3());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_quant8_all_tensors_as_inputs_all_inputs_as_internal_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 1, 5, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 0, 2, 0})
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
                        }, { // op42
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {6, 1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128, 128, 138, 128, 128, 128, 140, 128, 130, 128, 142, 128, 132, 128, 144, 128, 134, 128, 146, 128, 136, 128, 148})
                        }, { // op12_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 1, 5, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({130, 132, 134, 136, 138, 140, 142, 144, 146, 148})
                        }, { // placeholder47
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
                        }, { // param51
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {2, 5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_quant8_all_tensors_as_inputs_all_inputs_as_internal_3 = TestModelManager::get().add("space_to_batch_v1_2_nchw_quant8_all_tensors_as_inputs_all_inputs_as_internal_3", get_test_model_nchw_quant8_all_tensors_as_inputs_all_inputs_as_internal_3());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // op13
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 4, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 2, 4})
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
                        }, { // op43
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {6, 2, 4, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 7.0f, 0.0f, 0.0f, 0.0f, 2.0f, 0.0f, 0.0f, 0.0f, 8.0f, 0.0f, 0.0f, 0.0f, 3.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 4.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_1
    };
    return model;
}

const auto dummy_test_model_nhwc_4 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_4", get_test_model_nhwc_4());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_all_inputs_as_internal_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // op13
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 4, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 2, 4})
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
                        }, { // op43
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {6, 2, 4, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 7.0f, 0.0f, 0.0f, 0.0f, 2.0f, 0.0f, 0.0f, 0.0f, 8.0f, 0.0f, 0.0f, 0.0f, 3.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 4.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f})
                        }, { // op13_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 4, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f})
                        }, { // placeholder48
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
                        }, { // param52
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_1
    };
    return model;
}

const auto dummy_test_model_nhwc_all_inputs_as_internal_4 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_all_inputs_as_internal_4", get_test_model_nhwc_all_inputs_as_internal_4());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_all_tensors_as_inputs_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // op13
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 4, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 2, 4})
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
                        }, { // op43
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {6, 2, 4, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 7.0f, 0.0f, 0.0f, 0.0f, 2.0f, 0.0f, 0.0f, 0.0f, 8.0f, 0.0f, 0.0f, 0.0f, 3.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 4.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 2},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_1
    };
    return model;
}

const auto dummy_test_model_nhwc_all_tensors_as_inputs_4 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_all_tensors_as_inputs_4", get_test_model_nhwc_all_tensors_as_inputs_4());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_all_tensors_as_inputs_all_inputs_as_internal_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // op13
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 4, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 2, 4})
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
                        }, { // op43
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {6, 2, 4, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 7.0f, 0.0f, 0.0f, 0.0f, 2.0f, 0.0f, 0.0f, 0.0f, 8.0f, 0.0f, 0.0f, 0.0f, 3.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 4.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f})
                        }, { // op13_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 4, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f})
                        }, { // placeholder49
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
                        }, { // param53
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {2, 5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_1
    };
    return model;
}

const auto dummy_test_model_nhwc_all_tensors_as_inputs_all_inputs_as_internal_4 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_all_tensors_as_inputs_all_inputs_as_internal_4", get_test_model_nhwc_all_tensors_as_inputs_all_inputs_as_internal_4());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_relaxed_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // op13
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 4, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 2, 4})
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
                        }, { // op43
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {6, 2, 4, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 7.0f, 0.0f, 0.0f, 0.0f, 2.0f, 0.0f, 0.0f, 0.0f, 8.0f, 0.0f, 0.0f, 0.0f, 3.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 4.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_nhwc_relaxed_4 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_relaxed_4", get_test_model_nhwc_relaxed_4());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_relaxed_all_inputs_as_internal_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // op13
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 4, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 2, 4})
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
                        }, { // op43
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {6, 2, 4, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 7.0f, 0.0f, 0.0f, 0.0f, 2.0f, 0.0f, 0.0f, 0.0f, 8.0f, 0.0f, 0.0f, 0.0f, 3.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 4.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f})
                        }, { // op13_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 4, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f})
                        }, { // placeholder50
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
                        }, { // param54
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_nhwc_relaxed_all_inputs_as_internal_4 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_relaxed_all_inputs_as_internal_4", get_test_model_nhwc_relaxed_all_inputs_as_internal_4());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_relaxed_all_tensors_as_inputs_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // op13
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 4, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 2, 4})
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
                        }, { // op43
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {6, 2, 4, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 7.0f, 0.0f, 0.0f, 0.0f, 2.0f, 0.0f, 0.0f, 0.0f, 8.0f, 0.0f, 0.0f, 0.0f, 3.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 4.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 2},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_nhwc_relaxed_all_tensors_as_inputs_4 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_relaxed_all_tensors_as_inputs_4", get_test_model_nhwc_relaxed_all_tensors_as_inputs_4());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_relaxed_all_tensors_as_inputs_all_inputs_as_internal_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // op13
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 4, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 2, 4})
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
                        }, { // op43
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {6, 2, 4, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 7.0f, 0.0f, 0.0f, 0.0f, 2.0f, 0.0f, 0.0f, 0.0f, 8.0f, 0.0f, 0.0f, 0.0f, 3.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 4.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f})
                        }, { // op13_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 4, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f})
                        }, { // placeholder51
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
                        }, { // param55
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {2, 5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_nhwc_relaxed_all_tensors_as_inputs_all_inputs_as_internal_4 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_relaxed_all_tensors_as_inputs_all_inputs_as_internal_4", get_test_model_nhwc_relaxed_all_tensors_as_inputs_all_inputs_as_internal_4());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_float16_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // op13
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 4, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 2, 4})
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
                        }, { // op43
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {6, 2, 4, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 7.0f, 0.0f, 0.0f, 0.0f, 2.0f, 0.0f, 0.0f, 0.0f, 8.0f, 0.0f, 0.0f, 0.0f, 3.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 4.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nhwc_float16_4 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_float16_4", get_test_model_nhwc_float16_4());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_float16_all_inputs_as_internal_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // op13
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 4, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 2, 4})
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
                        }, { // op43
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {6, 2, 4, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 7.0f, 0.0f, 0.0f, 0.0f, 2.0f, 0.0f, 0.0f, 0.0f, 8.0f, 0.0f, 0.0f, 0.0f, 3.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 4.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f})
                        }, { // op13_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 4, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f})
                        }, { // placeholder52
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
                        }, { // param56
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nhwc_float16_all_inputs_as_internal_4 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_float16_all_inputs_as_internal_4", get_test_model_nhwc_float16_all_inputs_as_internal_4());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_float16_all_tensors_as_inputs_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // op13
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 4, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 2, 4})
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
                        }, { // op43
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {6, 2, 4, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 7.0f, 0.0f, 0.0f, 0.0f, 2.0f, 0.0f, 0.0f, 0.0f, 8.0f, 0.0f, 0.0f, 0.0f, 3.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 4.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 2},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nhwc_float16_all_tensors_as_inputs_4 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_float16_all_tensors_as_inputs_4", get_test_model_nhwc_float16_all_tensors_as_inputs_4());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_float16_all_tensors_as_inputs_all_inputs_as_internal_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // op13
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 4, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 2, 4})
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
                        }, { // op43
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {6, 2, 4, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 7.0f, 0.0f, 0.0f, 0.0f, 2.0f, 0.0f, 0.0f, 0.0f, 8.0f, 0.0f, 0.0f, 0.0f, 3.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 4.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f})
                        }, { // op13_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 4, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f})
                        }, { // placeholder53
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
                        }, { // param57
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {2, 5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nhwc_float16_all_tensors_as_inputs_all_inputs_as_internal_4 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_float16_all_tensors_as_inputs_all_inputs_as_internal_4", get_test_model_nhwc_float16_all_tensors_as_inputs_all_inputs_as_internal_4());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_quant8_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // op13
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 4, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({132, 136, 140, 144, 148, 152, 156, 160})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 2, 4})
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
                        }, { // op43
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {6, 2, 4, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128, 128, 128, 128, 148, 128, 128, 128, 128, 128, 128, 128, 152, 128, 128, 128, 132, 128, 128, 128, 156, 128, 128, 128, 136, 128, 128, 128, 160, 128, 128, 128, 140, 128, 128, 128, 128, 128, 128, 128, 144, 128, 128, 128, 128, 128, 128})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nhwc_quant8_4 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_quant8_4", get_test_model_nhwc_quant8_4());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_quant8_all_inputs_as_internal_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // op13
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 4, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 2, 4})
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
                        }, { // op43
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {6, 2, 4, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128, 128, 128, 128, 148, 128, 128, 128, 128, 128, 128, 128, 152, 128, 128, 128, 132, 128, 128, 128, 156, 128, 128, 128, 136, 128, 128, 128, 160, 128, 128, 128, 140, 128, 128, 128, 128, 128, 128, 128, 144, 128, 128, 128, 128, 128, 128})
                        }, { // op13_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 4, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({132, 136, 140, 144, 148, 152, 156, 160})
                        }, { // placeholder54
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
                        }, { // param58
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nhwc_quant8_all_inputs_as_internal_4 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_quant8_all_inputs_as_internal_4", get_test_model_nhwc_quant8_all_inputs_as_internal_4());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_quant8_all_tensors_as_inputs_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // op13
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 4, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({132, 136, 140, 144, 148, 152, 156, 160})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 2, 4})
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
                        }, { // op43
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {6, 2, 4, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128, 128, 128, 128, 148, 128, 128, 128, 128, 128, 128, 128, 152, 128, 128, 128, 132, 128, 128, 128, 156, 128, 128, 128, 136, 128, 128, 128, 160, 128, 128, 128, 140, 128, 128, 128, 128, 128, 128, 128, 144, 128, 128, 128, 128, 128, 128})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 2},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nhwc_quant8_all_tensors_as_inputs_4 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_quant8_all_tensors_as_inputs_4", get_test_model_nhwc_quant8_all_tensors_as_inputs_4());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nhwc_quant8_all_tensors_as_inputs_all_inputs_as_internal_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // op13
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 4, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 2, 4})
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
                        }, { // op43
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {6, 2, 4, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128, 128, 128, 128, 148, 128, 128, 128, 128, 128, 128, 128, 152, 128, 128, 128, 132, 128, 128, 128, 156, 128, 128, 128, 136, 128, 128, 128, 160, 128, 128, 128, 140, 128, 128, 128, 128, 128, 128, 128, 144, 128, 128, 128, 128, 128, 128})
                        }, { // op13_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 4, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({132, 136, 140, 144, 148, 152, 156, 160})
                        }, { // placeholder55
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
                        }, { // param59
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {2, 5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nhwc_quant8_all_tensors_as_inputs_all_inputs_as_internal_4 = TestModelManager::get().add("space_to_batch_v1_2_nhwc_quant8_all_tensors_as_inputs_all_inputs_as_internal_4", get_test_model_nhwc_quant8_all_tensors_as_inputs_all_inputs_as_internal_4());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // op13
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 2, 4})
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
                        }, { // op43
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {6, 1, 2, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 7.0f, 0.0f, 0.0f, 0.0f, 2.0f, 0.0f, 0.0f, 0.0f, 8.0f, 0.0f, 0.0f, 0.0f, 3.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 4.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_4 = TestModelManager::get().add("space_to_batch_v1_2_nchw_4", get_test_model_nchw_4());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_all_inputs_as_internal_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // op13
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 2, 4})
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
                        }, { // op43
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {6, 1, 2, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 7.0f, 0.0f, 0.0f, 0.0f, 2.0f, 0.0f, 0.0f, 0.0f, 8.0f, 0.0f, 0.0f, 0.0f, 3.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 4.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f})
                        }, { // op13_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f})
                        }, { // placeholder56
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
                        }, { // param60
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_all_inputs_as_internal_4 = TestModelManager::get().add("space_to_batch_v1_2_nchw_all_inputs_as_internal_4", get_test_model_nchw_all_inputs_as_internal_4());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_all_tensors_as_inputs_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // op13
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 2, 4})
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
                        }, { // op43
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {6, 1, 2, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 7.0f, 0.0f, 0.0f, 0.0f, 2.0f, 0.0f, 0.0f, 0.0f, 8.0f, 0.0f, 0.0f, 0.0f, 3.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 4.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 2},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_all_tensors_as_inputs_4 = TestModelManager::get().add("space_to_batch_v1_2_nchw_all_tensors_as_inputs_4", get_test_model_nchw_all_tensors_as_inputs_4());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_all_tensors_as_inputs_all_inputs_as_internal_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // op13
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 2, 4})
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
                        }, { // op43
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {6, 1, 2, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 7.0f, 0.0f, 0.0f, 0.0f, 2.0f, 0.0f, 0.0f, 0.0f, 8.0f, 0.0f, 0.0f, 0.0f, 3.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 4.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f})
                        }, { // op13_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f})
                        }, { // placeholder57
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
                        }, { // param61
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {2, 5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_all_tensors_as_inputs_all_inputs_as_internal_4 = TestModelManager::get().add("space_to_batch_v1_2_nchw_all_tensors_as_inputs_all_inputs_as_internal_4", get_test_model_nchw_all_tensors_as_inputs_all_inputs_as_internal_4());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_relaxed_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // op13
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 2, 4})
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
                        }, { // op43
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {6, 1, 2, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 7.0f, 0.0f, 0.0f, 0.0f, 2.0f, 0.0f, 0.0f, 0.0f, 8.0f, 0.0f, 0.0f, 0.0f, 3.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 4.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_nchw_relaxed_4 = TestModelManager::get().add("space_to_batch_v1_2_nchw_relaxed_4", get_test_model_nchw_relaxed_4());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_relaxed_all_inputs_as_internal_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // op13
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 2, 4})
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
                        }, { // op43
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {6, 1, 2, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 7.0f, 0.0f, 0.0f, 0.0f, 2.0f, 0.0f, 0.0f, 0.0f, 8.0f, 0.0f, 0.0f, 0.0f, 3.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 4.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f})
                        }, { // op13_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f})
                        }, { // placeholder58
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
                        }, { // param62
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_nchw_relaxed_all_inputs_as_internal_4 = TestModelManager::get().add("space_to_batch_v1_2_nchw_relaxed_all_inputs_as_internal_4", get_test_model_nchw_relaxed_all_inputs_as_internal_4());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_relaxed_all_tensors_as_inputs_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // op13
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 2, 4})
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
                        }, { // op43
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {6, 1, 2, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 7.0f, 0.0f, 0.0f, 0.0f, 2.0f, 0.0f, 0.0f, 0.0f, 8.0f, 0.0f, 0.0f, 0.0f, 3.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 4.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 2},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_nchw_relaxed_all_tensors_as_inputs_4 = TestModelManager::get().add("space_to_batch_v1_2_nchw_relaxed_all_tensors_as_inputs_4", get_test_model_nchw_relaxed_all_tensors_as_inputs_4());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_relaxed_all_tensors_as_inputs_all_inputs_as_internal_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // op13
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 2, 4})
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
                        }, { // op43
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {6, 1, 2, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 7.0f, 0.0f, 0.0f, 0.0f, 2.0f, 0.0f, 0.0f, 0.0f, 8.0f, 0.0f, 0.0f, 0.0f, 3.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 4.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f})
                        }, { // op13_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1, 1, 4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f})
                        }, { // placeholder59
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
                        }, { // param63
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {2, 5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_nchw_relaxed_all_tensors_as_inputs_all_inputs_as_internal_4 = TestModelManager::get().add("space_to_batch_v1_2_nchw_relaxed_all_tensors_as_inputs_all_inputs_as_internal_4", get_test_model_nchw_relaxed_all_tensors_as_inputs_all_inputs_as_internal_4());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_float16_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // op13
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 1, 4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 2, 4})
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
                        }, { // op43
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {6, 1, 2, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 7.0f, 0.0f, 0.0f, 0.0f, 2.0f, 0.0f, 0.0f, 0.0f, 8.0f, 0.0f, 0.0f, 0.0f, 3.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 4.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_float16_4 = TestModelManager::get().add("space_to_batch_v1_2_nchw_float16_4", get_test_model_nchw_float16_4());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_float16_all_inputs_as_internal_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // op13
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 1, 4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 2, 4})
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
                        }, { // op43
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {6, 1, 2, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 7.0f, 0.0f, 0.0f, 0.0f, 2.0f, 0.0f, 0.0f, 0.0f, 8.0f, 0.0f, 0.0f, 0.0f, 3.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 4.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f})
                        }, { // op13_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 1, 4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f})
                        }, { // placeholder60
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
                        }, { // param64
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_float16_all_inputs_as_internal_4 = TestModelManager::get().add("space_to_batch_v1_2_nchw_float16_all_inputs_as_internal_4", get_test_model_nchw_float16_all_inputs_as_internal_4());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_float16_all_tensors_as_inputs_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // op13
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 1, 4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 2, 4})
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
                        }, { // op43
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {6, 1, 2, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 7.0f, 0.0f, 0.0f, 0.0f, 2.0f, 0.0f, 0.0f, 0.0f, 8.0f, 0.0f, 0.0f, 0.0f, 3.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 4.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 2},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_float16_all_tensors_as_inputs_4 = TestModelManager::get().add("space_to_batch_v1_2_nchw_float16_all_tensors_as_inputs_4", get_test_model_nchw_float16_all_tensors_as_inputs_4());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_float16_all_tensors_as_inputs_all_inputs_as_internal_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // op13
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 1, 4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 2, 4})
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
                        }, { // op43
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {6, 1, 2, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 7.0f, 0.0f, 0.0f, 0.0f, 2.0f, 0.0f, 0.0f, 0.0f, 8.0f, 0.0f, 0.0f, 0.0f, 3.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 4.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f})
                        }, { // op13_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1, 1, 4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f})
                        }, { // placeholder61
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
                        }, { // param65
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {2, 5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_float16_all_tensors_as_inputs_all_inputs_as_internal_4 = TestModelManager::get().add("space_to_batch_v1_2_nchw_float16_all_tensors_as_inputs_all_inputs_as_internal_4", get_test_model_nchw_float16_all_tensors_as_inputs_all_inputs_as_internal_4());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_quant8_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // op13
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 1, 4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({132, 136, 140, 144, 148, 152, 156, 160})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 2, 4})
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
                        }, { // op43
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {6, 1, 2, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128, 128, 128, 128, 148, 128, 128, 128, 128, 128, 128, 128, 152, 128, 128, 128, 132, 128, 128, 128, 156, 128, 128, 128, 136, 128, 128, 128, 160, 128, 128, 128, 140, 128, 128, 128, 128, 128, 128, 128, 144, 128, 128, 128, 128, 128, 128})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_quant8_4 = TestModelManager::get().add("space_to_batch_v1_2_nchw_quant8_4", get_test_model_nchw_quant8_4());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_quant8_all_inputs_as_internal_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // op13
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 1, 4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 2, 4})
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
                        }, { // op43
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {6, 1, 2, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128, 128, 128, 128, 148, 128, 128, 128, 128, 128, 128, 128, 152, 128, 128, 128, 132, 128, 128, 128, 156, 128, 128, 128, 136, 128, 128, 128, 160, 128, 128, 128, 140, 128, 128, 128, 128, 128, 128, 128, 144, 128, 128, 128, 128, 128, 128})
                        }, { // op13_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 1, 4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({132, 136, 140, 144, 148, 152, 156, 160})
                        }, { // placeholder62
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
                        }, { // param66
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_quant8_all_inputs_as_internal_4 = TestModelManager::get().add("space_to_batch_v1_2_nchw_quant8_all_inputs_as_internal_4", get_test_model_nchw_quant8_all_inputs_as_internal_4());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_quant8_all_tensors_as_inputs_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // op13
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 1, 4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({132, 136, 140, 144, 148, 152, 156, 160})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 2, 4})
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
                        }, { // op43
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {6, 1, 2, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128, 128, 128, 128, 148, 128, 128, 128, 128, 128, 128, 128, 152, 128, 128, 128, 132, 128, 128, 128, 156, 128, 128, 128, 136, 128, 128, 128, 160, 128, 128, 128, 140, 128, 128, 128, 128, 128, 128, 128, 144, 128, 128, 128, 128, 128, 128})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 2},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_quant8_all_tensors_as_inputs_4 = TestModelManager::get().add("space_to_batch_v1_2_nchw_quant8_all_tensors_as_inputs_4", get_test_model_nchw_quant8_all_tensors_as_inputs_4());

}  // namespace generated_tests::space_to_batch_v1_2

namespace generated_tests::space_to_batch_v1_2 {

const TestModel& get_test_model_nchw_quant8_all_tensors_as_inputs_all_inputs_as_internal_4() {
    static TestModel model = {
        .main = {
                .operands = {{ // op13
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 1, 4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // param3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3, 2})
                        }, { // paddings2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1, 1, 2, 4})
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
                        }, { // op43
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {6, 1, 2, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 128, 128, 128, 128, 148, 128, 128, 128, 128, 128, 128, 128, 152, 128, 128, 128, 132, 128, 128, 128, 156, 128, 128, 128, 136, 128, 128, 128, 160, 128, 128, 128, 140, 128, 128, 128, 128, 128, 128, 128, 144, 128, 128, 128, 128, 128, 128})
                        }, { // op13_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 1, 4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({132, 136, 140, 144, 148, 152, 156, 160})
                        }, { // placeholder63
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
                        }, { // param67
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
                            .inputs = {5, 6, 7},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_BATCH_ND,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {2, 5},
                .outputIndexes = {4}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_nchw_quant8_all_tensors_as_inputs_all_inputs_as_internal_4 = TestModelManager::get().add("space_to_batch_v1_2_nchw_quant8_all_tensors_as_inputs_all_inputs_as_internal_4", get_test_model_nchw_quant8_all_tensors_as_inputs_all_inputs_as_internal_4());

}  // namespace generated_tests::space_to_batch_v1_2

