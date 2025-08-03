// Generated from roi_pooling_quant8_signed.mod.py
// DO NOT EDIT
// clang-format off
#include "TestHarness.h"
using namespace test_helper;  // NOLINT(google-build-using-namespace)

namespace generated_tests::roi_pooling_quant8_signed {

const TestModel& get_test_model_nhwc_quant8_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // in
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-40, -4, 16, -20, -32, -8, 36, 4, 28, -8, 12, -28, -8, 40, -12, 20})
                        }, { // roi
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {5, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({16, 16, 32, 32, 0, 0, 48, 48, 16, 0, 32, 48, 0, 16, 48, 32, 64, 64, 64, 64})
                        }, { // param
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0, 0})
                        }, { // param1
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
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
                        }, { // param3
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.0f})
                        }, { // param4
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.0f})
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
                        }, { // out
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {5, 2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, 36, -8, 12, -4, 36, 40, 20, -4, 36, 40, 12, -8, 36, 28, 12, 0, 0, 0, 0})
                        }},
                .operations = {{
                            .type = TestOperationType::ROI_POOLING,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7},
                            .outputs = {8}
                        }},
                .inputIndexes = {0, 1},
                .outputIndexes = {8}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_nhwc_quant8_signed = TestModelManager::get().add("roi_pooling_quant8_signed_nhwc_quant8_signed", get_test_model_nhwc_quant8_signed());

}  // namespace generated_tests::roi_pooling_quant8_signed

namespace generated_tests::roi_pooling_quant8_signed {

const TestModel& get_test_model_nhwc_quant8_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // in
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // roi
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {5, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({16, 16, 32, 32, 0, 0, 48, 48, 16, 0, 32, 48, 0, 16, 48, 32, 64, 64, 64, 64})
                        }, { // param
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0, 0})
                        }, { // param1
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
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
                        }, { // param3
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.0f})
                        }, { // param4
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.0f})
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
                        }, { // out
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {5, 2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, 36, -8, 12, -4, 36, 40, 20, -4, 36, 40, 12, -8, 36, 28, 12, 0, 0, 0, 0})
                        }, { // in_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-40, -4, 16, -20, -32, -8, 36, 4, 28, -8, 12, -28, -8, 40, -12, 20})
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
                            .inputs = {9, 10, 11},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ROI_POOLING,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7},
                            .outputs = {8}
                        }},
                .inputIndexes = {1, 9},
                .outputIndexes = {8}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_nhwc_quant8_signed_all_inputs_as_internal = TestModelManager::get().add("roi_pooling_quant8_signed_nhwc_quant8_signed_all_inputs_as_internal", get_test_model_nhwc_quant8_signed_all_inputs_as_internal());

}  // namespace generated_tests::roi_pooling_quant8_signed

namespace generated_tests::roi_pooling_quant8_signed {

const TestModel& get_test_model_nchw_quant8_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // in
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-40, -4, 16, -20, -32, -8, 36, 4, 28, -8, 12, -28, -8, 40, -12, 20})
                        }, { // roi
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {5, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({16, 16, 32, 32, 0, 0, 48, 48, 16, 0, 32, 48, 0, 16, 48, 32, 64, 64, 64, 64})
                        }, { // param
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0, 0})
                        }, { // param1
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
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
                        }, { // param3
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.0f})
                        }, { // param4
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.0f})
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
                        }, { // out
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {5, 1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, 36, -8, 12, -4, 36, 40, 20, -4, 36, 40, 12, -8, 36, 28, 12, 0, 0, 0, 0})
                        }},
                .operations = {{
                            .type = TestOperationType::ROI_POOLING,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7},
                            .outputs = {8}
                        }},
                .inputIndexes = {0, 1},
                .outputIndexes = {8}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_nchw_quant8_signed = TestModelManager::get().add("roi_pooling_quant8_signed_nchw_quant8_signed", get_test_model_nchw_quant8_signed());

}  // namespace generated_tests::roi_pooling_quant8_signed

namespace generated_tests::roi_pooling_quant8_signed {

const TestModel& get_test_model_nchw_quant8_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // in
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // roi
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {5, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({16, 16, 32, 32, 0, 0, 48, 48, 16, 0, 32, 48, 0, 16, 48, 32, 64, 64, 64, 64})
                        }, { // param
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 0, 0, 0})
                        }, { // param1
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
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
                        }, { // param3
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.0f})
                        }, { // param4
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.0f})
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
                        }, { // out
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {5, 1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, 36, -8, 12, -4, 36, 40, 20, -4, 36, 40, 12, -8, 36, 28, 12, 0, 0, 0, 0})
                        }, { // in_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-40, -4, 16, -20, -32, -8, 36, 4, 28, -8, 12, -28, -8, 40, -12, 20})
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
                            .inputs = {9, 10, 11},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ROI_POOLING,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7},
                            .outputs = {8}
                        }},
                .inputIndexes = {1, 9},
                .outputIndexes = {8}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_nchw_quant8_signed_all_inputs_as_internal = TestModelManager::get().add("roi_pooling_quant8_signed_nchw_quant8_signed_all_inputs_as_internal", get_test_model_nchw_quant8_signed_all_inputs_as_internal());

}  // namespace generated_tests::roi_pooling_quant8_signed

namespace generated_tests::roi_pooling_quant8_signed {

const TestModel& get_test_model_nhwc_quant8_signed_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // in1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {4, 4, 8, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.04f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({93, 94, 57, 12, 121, -19, -126, 63, 34, 109, 61, -53, -106, -53, 30, -18, -87, 40, 26, 87, 18, -49, 50, 42, 16, 38, 0, 83, -1, 50, -57, -98, 81, -106, 69, 114, -79, -95, -17, 119, -124, 97, 105, -117, -2, 34, -101, 110, -96, -74, -77, 66, 64, -112, -24, 50, 6, 70, -102, -91, 97, -104, -26, -112, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, 9, -62, -106, -6, -68, -67, 91, -126, -38, 104, 18, 96, 44, -92, -30, 20, 57, 103, -50, -5, -81, -48, 110, 40, -76, 55, -51, -4, -122, 95, -101, -121, 54, 46, -70, 44, -20, -94, -106, -116, 24, -33, -106, 47, -19, -80, 77, -39, 70, 42, -12, 43, 26, 113, -62, -70, -94, -60, 122, 118, 74, -8, 67, 8})
                        }, { // roi1
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({32, 32, 192, 64, 32, 32, 224, 96, 56, 8, 200, 88, 8, 56, 40, 88})
                        }, { // param5
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 3, 3})
                        }, { // param6
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // param7
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // param8
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({4.0f})
                        }, { // param9
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({4.0f})
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
                        }, { // out1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {4, 2, 3, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.04f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({26, 87, 50, 42, 0, 83, 69, 114, -17, 119, 105, 34, 69, 119, 105, 119, 105, 110, 69, 119, 105, 119, 105, 110, 110, 40, 110, 104, 44, 96, 26, 113, 122, 118, 122, 118, 54, 46, 54, 46, -70, 44, 70, 42, 70, 43, -12, 43})
                        }},
                .operations = {{
                            .type = TestOperationType::ROI_POOLING,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7},
                            .outputs = {8}
                        }},
                .inputIndexes = {0, 1},
                .outputIndexes = {8}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_nhwc_quant8_signed_2 = TestModelManager::get().add("roi_pooling_quant8_signed_nhwc_quant8_signed_2", get_test_model_nhwc_quant8_signed_2());

}  // namespace generated_tests::roi_pooling_quant8_signed

namespace generated_tests::roi_pooling_quant8_signed {

const TestModel& get_test_model_nhwc_quant8_signed_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // in1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {4, 4, 8, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.04f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // roi1
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({32, 32, 192, 64, 32, 32, 224, 96, 56, 8, 200, 88, 8, 56, 40, 88})
                        }, { // param5
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 3, 3})
                        }, { // param6
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // param7
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // param8
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({4.0f})
                        }, { // param9
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({4.0f})
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
                        }, { // out1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {4, 2, 3, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.04f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({26, 87, 50, 42, 0, 83, 69, 114, -17, 119, 105, 34, 69, 119, 105, 119, 105, 110, 69, 119, 105, 119, 105, 110, 110, 40, 110, 104, 44, 96, 26, 113, 122, 118, 122, 118, 54, 46, 54, 46, -70, 44, 70, 42, 70, 43, -12, 43})
                        }, { // in1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {4, 4, 8, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.04f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({93, 94, 57, 12, 121, -19, -126, 63, 34, 109, 61, -53, -106, -53, 30, -18, -87, 40, 26, 87, 18, -49, 50, 42, 16, 38, 0, 83, -1, 50, -57, -98, 81, -106, 69, 114, -79, -95, -17, 119, -124, 97, 105, -117, -2, 34, -101, 110, -96, -74, -77, 66, 64, -112, -24, 50, 6, 70, -102, -91, 97, -104, -26, -112, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, 9, -62, -106, -6, -68, -67, 91, -126, -38, 104, 18, 96, 44, -92, -30, 20, 57, 103, -50, -5, -81, -48, 110, 40, -76, 55, -51, -4, -122, 95, -101, -121, 54, 46, -70, 44, -20, -94, -106, -116, 24, -33, -106, 47, -19, -80, 77, -39, 70, 42, -12, 43, 26, 113, -62, -70, -94, -60, 122, 118, 74, -8, 67, 8})
                        }, { // placeholder2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.04f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
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
                            .inputs = {9, 10, 11},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ROI_POOLING,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7},
                            .outputs = {8}
                        }},
                .inputIndexes = {1, 9},
                .outputIndexes = {8}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_nhwc_quant8_signed_all_inputs_as_internal_2 = TestModelManager::get().add("roi_pooling_quant8_signed_nhwc_quant8_signed_all_inputs_as_internal_2", get_test_model_nhwc_quant8_signed_all_inputs_as_internal_2());

}  // namespace generated_tests::roi_pooling_quant8_signed

namespace generated_tests::roi_pooling_quant8_signed {

const TestModel& get_test_model_nchw_quant8_signed_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // in1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {4, 2, 4, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.04f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({93, 57, 121, -126, 34, 61, -106, 30, -87, 26, 18, 50, 16, 0, -1, -57, 81, 69, -79, -17, -124, 105, -2, -101, -96, -77, 64, -24, 6, -102, 97, -26, 94, 12, -19, 63, 109, -53, -53, -18, 40, 87, -49, 42, 38, 83, 50, -98, -106, 114, -95, 119, 97, -117, 34, 110, -74, 66, -112, 50, 70, -91, -104, -112, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, 9, -106, -68, 91, -38, 18, 44, -30, 57, -50, -81, 110, -76, -51, -122, -101, 54, -70, -20, -106, 24, -106, -19, 77, 70, -12, 26, -62, -94, 122, 74, 67, -62, -6, -67, -126, 104, 96, -92, 20, 103, -5, -48, 40, 55, -4, 95, -121, 46, 44, -94, -116, -33, 47, -80, -39, 42, 43, 113, -70, -60, 118, -8, 8})
                        }, { // roi1
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({32, 32, 192, 64, 32, 32, 224, 96, 56, 8, 200, 88, 8, 56, 40, 88})
                        }, { // param5
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 3, 3})
                        }, { // param6
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // param7
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // param8
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({4.0f})
                        }, { // param9
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({4.0f})
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
                        }, { // out1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {4, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.04f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({26, 50, 0, 69, -17, 105, 87, 42, 83, 114, 119, 34, 69, 105, 105, 69, 105, 105, 119, 119, 110, 119, 119, 110, 110, 110, 44, 26, 122, 122, 40, 104, 96, 113, 118, 118, 54, 54, -70, 70, 70, -12, 46, 46, 44, 42, 43, 43})
                        }},
                .operations = {{
                            .type = TestOperationType::ROI_POOLING,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7},
                            .outputs = {8}
                        }},
                .inputIndexes = {0, 1},
                .outputIndexes = {8}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_nchw_quant8_signed_2 = TestModelManager::get().add("roi_pooling_quant8_signed_nchw_quant8_signed_2", get_test_model_nchw_quant8_signed_2());

}  // namespace generated_tests::roi_pooling_quant8_signed

namespace generated_tests::roi_pooling_quant8_signed {

const TestModel& get_test_model_nchw_quant8_signed_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // in1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {4, 2, 4, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.04f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // roi1
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({32, 32, 192, 64, 32, 32, 224, 96, 56, 8, 200, 88, 8, 56, 40, 88})
                        }, { // param5
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 0, 3, 3})
                        }, { // param6
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // param7
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // param8
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({4.0f})
                        }, { // param9
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({4.0f})
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
                        }, { // out1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {4, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.04f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({26, 50, 0, 69, -17, 105, 87, 42, 83, 114, 119, 34, 69, 105, 105, 69, 105, 105, 119, 119, 110, 119, 119, 110, 110, 110, 44, 26, 122, 122, 40, 104, 96, 113, 118, 118, 54, 54, -70, 70, 70, -12, 46, 46, 44, 42, 43, 43})
                        }, { // in1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {4, 2, 4, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.04f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({93, 57, 121, -126, 34, 61, -106, 30, -87, 26, 18, 50, 16, 0, -1, -57, 81, 69, -79, -17, -124, 105, -2, -101, -96, -77, 64, -24, 6, -102, 97, -26, 94, 12, -19, 63, 109, -53, -53, -18, 40, 87, -49, 42, 38, 83, 50, -98, -106, 114, -95, 119, 97, -117, 34, 110, -74, 66, -112, 50, 70, -91, -104, -112, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, 9, -106, -68, 91, -38, 18, 44, -30, 57, -50, -81, 110, -76, -51, -122, -101, 54, -70, -20, -106, 24, -106, -19, 77, 70, -12, 26, -62, -94, 122, 74, 67, -62, -6, -67, -126, 104, 96, -92, 20, 103, -5, -48, 40, 55, -4, 95, -121, 46, 44, -94, -116, -33, 47, -80, -39, 42, 43, 113, -70, -60, 118, -8, 8})
                        }, { // placeholder3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.04f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
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
                            .inputs = {9, 10, 11},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ROI_POOLING,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7},
                            .outputs = {8}
                        }},
                .inputIndexes = {1, 9},
                .outputIndexes = {8}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_nchw_quant8_signed_all_inputs_as_internal_2 = TestModelManager::get().add("roi_pooling_quant8_signed_nchw_quant8_signed_all_inputs_as_internal_2", get_test_model_nchw_quant8_signed_all_inputs_as_internal_2());

}  // namespace generated_tests::roi_pooling_quant8_signed

namespace generated_tests::roi_pooling_quant8_signed {

const TestModel& get_test_model_nhwc_quant8_signed_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // in2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {4, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -40, -4, 16, -20, -32, -8, 36, 4, 28, -8, 12, -28, -8, 40, -12, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0})
                        }, { // roi2
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {5, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({8, 16, 16, 32, 0, 0, 24, 48, 8, 0, 16, 48, 0, 16, 24, 32, 0, 0, 0, 0})
                        }, { // param10
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2, 2, 2, 2, 2})
                        }, { // param11
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // param12
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // param13
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.0f})
                        }, { // param14
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f})
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
                        }, { // out2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {5, 2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, 36, -8, 12, -4, 36, 40, 20, -4, 36, 40, 12, -8, 36, 28, 12, -40, -40, -40, -40})
                        }},
                .operations = {{
                            .type = TestOperationType::ROI_POOLING,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7},
                            .outputs = {8}
                        }},
                .inputIndexes = {0, 1},
                .outputIndexes = {8}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_nhwc_quant8_signed_3 = TestModelManager::get().add("roi_pooling_quant8_signed_nhwc_quant8_signed_3", get_test_model_nhwc_quant8_signed_3());

}  // namespace generated_tests::roi_pooling_quant8_signed

namespace generated_tests::roi_pooling_quant8_signed {

const TestModel& get_test_model_nhwc_quant8_signed_all_inputs_as_internal_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // in2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {4, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // roi2
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {5, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({8, 16, 16, 32, 0, 0, 24, 48, 8, 0, 16, 48, 0, 16, 24, 32, 0, 0, 0, 0})
                        }, { // param10
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2, 2, 2, 2, 2})
                        }, { // param11
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // param12
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // param13
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.0f})
                        }, { // param14
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f})
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
                        }, { // out2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {5, 2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, 36, -8, 12, -4, 36, 40, 20, -4, 36, 40, 12, -8, 36, 28, 12, -40, -40, -40, -40})
                        }, { // in2_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {4, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -40, -4, 16, -20, -32, -8, 36, 4, 28, -8, 12, -28, -8, 40, -12, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0})
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
                            .inputs = {9, 10, 11},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ROI_POOLING,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7},
                            .outputs = {8}
                        }},
                .inputIndexes = {1, 9},
                .outputIndexes = {8}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_nhwc_quant8_signed_all_inputs_as_internal_3 = TestModelManager::get().add("roi_pooling_quant8_signed_nhwc_quant8_signed_all_inputs_as_internal_3", get_test_model_nhwc_quant8_signed_all_inputs_as_internal_3());

}  // namespace generated_tests::roi_pooling_quant8_signed

namespace generated_tests::roi_pooling_quant8_signed {

const TestModel& get_test_model_nchw_quant8_signed_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // in2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {4, 1, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -40, -4, 16, -20, -32, -8, 36, 4, 28, -8, 12, -28, -8, 40, -12, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0})
                        }, { // roi2
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {5, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({8, 16, 16, 32, 0, 0, 24, 48, 8, 0, 16, 48, 0, 16, 24, 32, 0, 0, 0, 0})
                        }, { // param10
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2, 2, 2, 2, 2})
                        }, { // param11
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // param12
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // param13
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.0f})
                        }, { // param14
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f})
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
                        }, { // out2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {5, 1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, 36, -8, 12, -4, 36, 40, 20, -4, 36, 40, 12, -8, 36, 28, 12, -40, -40, -40, -40})
                        }},
                .operations = {{
                            .type = TestOperationType::ROI_POOLING,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7},
                            .outputs = {8}
                        }},
                .inputIndexes = {0, 1},
                .outputIndexes = {8}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_nchw_quant8_signed_3 = TestModelManager::get().add("roi_pooling_quant8_signed_nchw_quant8_signed_3", get_test_model_nchw_quant8_signed_3());

}  // namespace generated_tests::roi_pooling_quant8_signed

namespace generated_tests::roi_pooling_quant8_signed {

const TestModel& get_test_model_nchw_quant8_signed_all_inputs_as_internal_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // in2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {4, 1, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // roi2
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {5, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({8, 16, 16, 32, 0, 0, 24, 48, 8, 0, 16, 48, 0, 16, 24, 32, 0, 0, 0, 0})
                        }, { // param10
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2, 2, 2, 2, 2})
                        }, { // param11
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // param12
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // param13
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.0f})
                        }, { // param14
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f})
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
                        }, { // out2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {5, 1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-8, 36, -8, 12, -4, 36, 40, 20, -4, 36, 40, 12, -8, 36, 28, 12, -40, -40, -40, -40})
                        }, { // in2_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {4, 1, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -40, -4, 16, -20, -32, -8, 36, 4, 28, -8, 12, -28, -8, 40, -12, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0})
                        }, { // placeholder5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0})
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
                            .inputs = {9, 10, 11},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ROI_POOLING,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7},
                            .outputs = {8}
                        }},
                .inputIndexes = {1, 9},
                .outputIndexes = {8}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_nchw_quant8_signed_all_inputs_as_internal_3 = TestModelManager::get().add("roi_pooling_quant8_signed_nchw_quant8_signed_all_inputs_as_internal_3", get_test_model_nchw_quant8_signed_all_inputs_as_internal_3());

}  // namespace generated_tests::roi_pooling_quant8_signed

