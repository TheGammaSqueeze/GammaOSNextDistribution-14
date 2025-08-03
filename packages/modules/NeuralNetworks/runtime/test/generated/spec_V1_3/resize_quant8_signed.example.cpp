// Generated from resize_quant8_signed.mod.py
// DO NOT EDIT
// clang-format off
#include "TestHarness.h"
using namespace test_helper;  // NOLINT(google-build-using-namespace)

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_nhwc_quant8_signed() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28, -28, 72, 72})
                        }, { // param
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // param1
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
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
                            .dimensions = {1, 3, 3, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28, -28, -28, 39, 39, 39, 72, 72, 72})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_BILINEAR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_shape_nhwc_quant8_signed = TestModelManager::get().add("resize_quant8_signed_shape_nhwc_quant8_signed", get_test_model_shape_nhwc_quant8_signed());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_nhwc_quant8_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // param1
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
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
                            .dimensions = {1, 3, 3, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28, -28, -28, 39, 39, 39, 72, 72, 72})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28, -28, 72, 72})
                        }, { // placeholder
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param104
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
                            .type = TestOperationType::RESIZE_BILINEAR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_shape_nhwc_quant8_signed_all_inputs_as_internal = TestModelManager::get().add("resize_quant8_signed_shape_nhwc_quant8_signed_all_inputs_as_internal", get_test_model_shape_nhwc_quant8_signed_all_inputs_as_internal());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_nchw_quant8_signed() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28, -28, 72, 72})
                        }, { // param
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // param1
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
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
                            .dimensions = {1, 1, 3, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28, -28, -28, 39, 39, 39, 72, 72, 72})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_BILINEAR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_shape_nchw_quant8_signed = TestModelManager::get().add("resize_quant8_signed_shape_nchw_quant8_signed", get_test_model_shape_nchw_quant8_signed());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_nchw_quant8_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // param1
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
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
                            .dimensions = {1, 1, 3, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28, -28, -28, 39, 39, 39, 72, 72, 72})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28, -28, 72, 72})
                        }, { // placeholder1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param105
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
                            .type = TestOperationType::RESIZE_BILINEAR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_shape_nchw_quant8_signed_all_inputs_as_internal = TestModelManager::get().add("resize_quant8_signed_shape_nchw_quant8_signed_all_inputs_as_internal", get_test_model_shape_nchw_quant8_signed_all_inputs_as_internal());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_nhwc_quant8_signed() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28, -28, 72, 72})
                        }, { // param2
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.5f})
                        }, { // param3
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.5f})
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
                            .dimensions = {1, 3, 3, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28, -28, -28, 39, 39, 39, 72, 72, 72})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_BILINEAR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_scale_nhwc_quant8_signed = TestModelManager::get().add("resize_quant8_signed_scale_nhwc_quant8_signed", get_test_model_scale_nhwc_quant8_signed());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_nhwc_quant8_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param2
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.5f})
                        }, { // param3
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.5f})
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
                            .dimensions = {1, 3, 3, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28, -28, -28, 39, 39, 39, 72, 72, 72})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28, -28, 72, 72})
                        }, { // placeholder2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param106
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
                            .type = TestOperationType::RESIZE_BILINEAR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_scale_nhwc_quant8_signed_all_inputs_as_internal = TestModelManager::get().add("resize_quant8_signed_scale_nhwc_quant8_signed_all_inputs_as_internal", get_test_model_scale_nhwc_quant8_signed_all_inputs_as_internal());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_nchw_quant8_signed() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28, -28, 72, 72})
                        }, { // param2
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.5f})
                        }, { // param3
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.5f})
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
                            .dimensions = {1, 1, 3, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28, -28, -28, 39, 39, 39, 72, 72, 72})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_BILINEAR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_scale_nchw_quant8_signed = TestModelManager::get().add("resize_quant8_signed_scale_nchw_quant8_signed", get_test_model_scale_nchw_quant8_signed());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_nchw_quant8_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param2
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.5f})
                        }, { // param3
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.5f})
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
                            .dimensions = {1, 1, 3, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28, -28, -28, 39, 39, 39, 72, 72, 72})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28, -28, 72, 72})
                        }, { // placeholder3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param107
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
                            .type = TestOperationType::RESIZE_BILINEAR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_scale_nchw_quant8_signed_all_inputs_as_internal = TestModelManager::get().add("resize_quant8_signed_scale_nchw_quant8_signed_all_inputs_as_internal", get_test_model_scale_nchw_quant8_signed_all_inputs_as_internal());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_nhwc_quant8_signed_2() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-116, -112, -104, -88, -92, -88, -80, -64})
                        }, { // param4
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // param5
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
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
                            .dimensions = {1, 3, 3, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-116, -112, -108, -96, -104, -88, -100, -96, -92, -80, -88, -72, -92, -88, -84, -72, -80, -64})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_BILINEAR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_shape_nhwc_quant8_signed_2 = TestModelManager::get().add("resize_quant8_signed_shape_nhwc_quant8_signed_2", get_test_model_shape_nhwc_quant8_signed_2());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_nhwc_quant8_signed_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param4
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // param5
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
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
                            .dimensions = {1, 3, 3, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-116, -112, -108, -96, -104, -88, -100, -96, -92, -80, -88, -72, -92, -88, -84, -72, -80, -64})
                        }, { // op11_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-116, -112, -104, -88, -92, -88, -80, -64})
                        }, { // placeholder4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param108
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
                            .type = TestOperationType::RESIZE_BILINEAR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_shape_nhwc_quant8_signed_all_inputs_as_internal_2 = TestModelManager::get().add("resize_quant8_signed_shape_nhwc_quant8_signed_all_inputs_as_internal_2", get_test_model_shape_nhwc_quant8_signed_all_inputs_as_internal_2());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_nchw_quant8_signed_2() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-116, -104, -92, -80, -112, -88, -88, -64})
                        }, { // param4
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // param5
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
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
                            .dimensions = {1, 2, 3, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-116, -108, -104, -100, -92, -88, -92, -84, -80, -112, -96, -88, -96, -80, -72, -88, -72, -64})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_BILINEAR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_shape_nchw_quant8_signed_2 = TestModelManager::get().add("resize_quant8_signed_shape_nchw_quant8_signed_2", get_test_model_shape_nchw_quant8_signed_2());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_nchw_quant8_signed_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param4
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // param5
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
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
                            .dimensions = {1, 2, 3, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-116, -108, -104, -100, -92, -88, -92, -84, -80, -112, -96, -88, -96, -80, -72, -88, -72, -64})
                        }, { // op11_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-116, -104, -92, -80, -112, -88, -88, -64})
                        }, { // placeholder5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param109
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
                            .type = TestOperationType::RESIZE_BILINEAR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_shape_nchw_quant8_signed_all_inputs_as_internal_2 = TestModelManager::get().add("resize_quant8_signed_shape_nchw_quant8_signed_all_inputs_as_internal_2", get_test_model_shape_nchw_quant8_signed_all_inputs_as_internal_2());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_nhwc_quant8_signed_2() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-116, -112, -104, -88, -92, -88, -80, -64})
                        }, { // param6
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.6f})
                        }, { // param7
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.6f})
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
                            .dimensions = {1, 3, 3, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-116, -112, -108, -96, -104, -88, -100, -96, -92, -80, -88, -72, -92, -88, -84, -72, -80, -64})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_BILINEAR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_scale_nhwc_quant8_signed_2 = TestModelManager::get().add("resize_quant8_signed_scale_nhwc_quant8_signed_2", get_test_model_scale_nhwc_quant8_signed_2());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_nhwc_quant8_signed_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param6
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.6f})
                        }, { // param7
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.6f})
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
                            .dimensions = {1, 3, 3, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-116, -112, -108, -96, -104, -88, -100, -96, -92, -80, -88, -72, -92, -88, -84, -72, -80, -64})
                        }, { // op11_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-116, -112, -104, -88, -92, -88, -80, -64})
                        }, { // placeholder6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param110
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
                            .type = TestOperationType::RESIZE_BILINEAR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_scale_nhwc_quant8_signed_all_inputs_as_internal_2 = TestModelManager::get().add("resize_quant8_signed_scale_nhwc_quant8_signed_all_inputs_as_internal_2", get_test_model_scale_nhwc_quant8_signed_all_inputs_as_internal_2());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_nchw_quant8_signed_2() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-116, -104, -92, -80, -112, -88, -88, -64})
                        }, { // param6
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.6f})
                        }, { // param7
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.6f})
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
                            .dimensions = {1, 2, 3, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-116, -108, -104, -100, -92, -88, -92, -84, -80, -112, -96, -88, -96, -80, -72, -88, -72, -64})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_BILINEAR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_scale_nchw_quant8_signed_2 = TestModelManager::get().add("resize_quant8_signed_scale_nchw_quant8_signed_2", get_test_model_scale_nchw_quant8_signed_2());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_nchw_quant8_signed_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param6
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.6f})
                        }, { // param7
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.6f})
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
                            .dimensions = {1, 2, 3, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-116, -108, -104, -100, -92, -88, -92, -84, -80, -112, -96, -88, -96, -80, -72, -88, -72, -64})
                        }, { // op11_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-116, -104, -92, -80, -112, -88, -88, -64})
                        }, { // placeholder7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param111
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
                            .type = TestOperationType::RESIZE_BILINEAR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_scale_nchw_quant8_signed_all_inputs_as_internal_2 = TestModelManager::get().add("resize_quant8_signed_scale_nchw_quant8_signed_all_inputs_as_internal_2", get_test_model_scale_nchw_quant8_signed_all_inputs_as_internal_2());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_quant8_signed() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // op12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28, -28, 72, 72})
                        }, { // param8
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // param9
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // op42
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 3, 3, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28, -28, -28, 39, 39, 39, 72, 72, 72})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_BILINEAR,
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

const auto dummy_test_model_shape_quant8_signed = TestModelManager::get().add("resize_quant8_signed_shape_quant8_signed", get_test_model_shape_quant8_signed());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_quant8_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // op12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param8
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // param9
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // op42
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 3, 3, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28, -28, -28, 39, 39, 39, 72, 72, 72})
                        }, { // op12_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28, -28, 72, 72})
                        }, { // placeholder8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param112
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
                            .type = TestOperationType::RESIZE_BILINEAR,
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

const auto dummy_test_model_shape_quant8_signed_all_inputs_as_internal = TestModelManager::get().add("resize_quant8_signed_shape_quant8_signed_all_inputs_as_internal", get_test_model_shape_quant8_signed_all_inputs_as_internal());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_quant8_signed() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // op12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28, -28, 72, 72})
                        }, { // param10
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.8f})
                        }, { // param11
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.8f})
                        }, { // op42
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 3, 3, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28, -28, -28, 39, 39, 39, 72, 72, 72})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_BILINEAR,
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

const auto dummy_test_model_scale_quant8_signed = TestModelManager::get().add("resize_quant8_signed_scale_quant8_signed", get_test_model_scale_quant8_signed());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_quant8_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // op12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param10
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.8f})
                        }, { // param11
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.8f})
                        }, { // op42
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 3, 3, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.01f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28, -28, -28, 39, 39, 39, 72, 72, 72})
                        }, { // op12_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28, -28, 72, 72})
                        }, { // placeholder9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.01f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param113
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
                            .type = TestOperationType::RESIZE_BILINEAR,
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

const auto dummy_test_model_scale_quant8_signed_all_inputs_as_internal = TestModelManager::get().add("resize_quant8_signed_scale_quant8_signed_all_inputs_as_internal", get_test_model_scale_quant8_signed_all_inputs_as_internal());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_zero_sized_nhwc_quant8_signed() {
    static TestModel model = {
        .main = { // zero_sized
                .operands = {{ // scores
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({9, 1})
                        }, { // roi
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {1, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({8, 8, 80, 80, 0, 0, 80, 80})
                        }, { // param12
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param13
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // param14
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-1})
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
                        }, { // param16
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.4f})
                        }, { // param17
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f})
                        }, { // param18
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // scoresOut
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {0},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // roiOut
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {0, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({})
                        }, { // classesOut
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {0},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // batchSplitOut
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {0},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // in
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 1, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({10})
                        }, { // param19
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // param20
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // param21
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.0f})
                        }, { // param22
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.0f})
                        }, { // param23
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({4})
                        }, { // param24
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({4})
                        }, { // layout
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 2,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // featureMap
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {0, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param25
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // param26
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // out
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {0, 3, 3, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }},
                .operations = {{
                            .type = TestOperationType::BOX_WITH_NMS_LIMIT,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8},
                            .outputs = {9, 10, 11, 12}
                        }, {
                            .type = TestOperationType::ROI_ALIGN,
                            .inputs = {13, 10, 12, 14, 15, 16, 17, 18, 19, 20},
                            .outputs = {21}
                        }, {
                            .type = TestOperationType::RESIZE_BILINEAR,
                            .inputs = {21, 22, 23, 20},
                            .outputs = {24}
                        }},
                .inputIndexes = {13},
                .outputIndexes = {9, 11, 24}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_zero_sized_nhwc_quant8_signed = TestModelManager::get().add("resize_quant8_signed_zero_sized_nhwc_quant8_signed", get_test_model_zero_sized_nhwc_quant8_signed());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_zero_sized_nchw_quant8_signed() {
    static TestModel model = {
        .main = { // zero_sized
                .operands = {{ // scores
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({9, 1})
                        }, { // roi
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {1, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({8, 8, 80, 80, 0, 0, 80, 80})
                        }, { // param12
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param13
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // param14
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-1})
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
                        }, { // param16
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.4f})
                        }, { // param17
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f})
                        }, { // param18
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // scoresOut
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {0},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // roiOut
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {0, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({})
                        }, { // classesOut
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {0},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // batchSplitOut
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {0},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // in
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 1, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({10})
                        }, { // param19
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // param20
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // param21
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.0f})
                        }, { // param22
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.0f})
                        }, { // param23
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({4})
                        }, { // param24
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({4})
                        }, { // layout
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 2,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // featureMap
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {0, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param25
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // param26
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // out
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {0, 1, 3, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }},
                .operations = {{
                            .type = TestOperationType::BOX_WITH_NMS_LIMIT,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8},
                            .outputs = {9, 10, 11, 12}
                        }, {
                            .type = TestOperationType::ROI_ALIGN,
                            .inputs = {13, 10, 12, 14, 15, 16, 17, 18, 19, 20},
                            .outputs = {21}
                        }, {
                            .type = TestOperationType::RESIZE_BILINEAR,
                            .inputs = {21, 22, 23, 20},
                            .outputs = {24}
                        }},
                .inputIndexes = {13},
                .outputIndexes = {9, 11, 24}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_zero_sized_nchw_quant8_signed = TestModelManager::get().add("resize_quant8_signed_zero_sized_nchw_quant8_signed", get_test_model_zero_sized_nchw_quant8_signed());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_zero_sized_nhwc_quant8_signed_2() {
    static TestModel model = {
        .main = { // zero_sized
                .operands = {{ // scores1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({9, 1})
                        }, { // roi1
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {1, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({8, 8, 80, 80, 0, 0, 80, 80})
                        }, { // param27
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param28
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // param29
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-1})
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
                        }, { // param31
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.4f})
                        }, { // param32
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f})
                        }, { // param33
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // scoresOut1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {0},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // roiOut1
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {0, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({})
                        }, { // classesOut1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {0},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // batchSplitOut1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {0},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // in1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 1, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({10})
                        }, { // param34
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // param35
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // param36
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.0f})
                        }, { // param37
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.0f})
                        }, { // param38
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({4})
                        }, { // param39
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({4})
                        }, { // layout
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 2,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // featureMap1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {0, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param40
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.6f})
                        }, { // param41
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.6f})
                        }, { // out1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {0, 3, 3, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }},
                .operations = {{
                            .type = TestOperationType::BOX_WITH_NMS_LIMIT,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8},
                            .outputs = {9, 10, 11, 12}
                        }, {
                            .type = TestOperationType::ROI_ALIGN,
                            .inputs = {13, 10, 12, 14, 15, 16, 17, 18, 19, 20},
                            .outputs = {21}
                        }, {
                            .type = TestOperationType::RESIZE_BILINEAR,
                            .inputs = {21, 22, 23, 20},
                            .outputs = {24}
                        }},
                .inputIndexes = {13},
                .outputIndexes = {9, 11, 24}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_zero_sized_nhwc_quant8_signed_2 = TestModelManager::get().add("resize_quant8_signed_zero_sized_nhwc_quant8_signed_2", get_test_model_zero_sized_nhwc_quant8_signed_2());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_zero_sized_nchw_quant8_signed_2() {
    static TestModel model = {
        .main = { // zero_sized
                .operands = {{ // scores1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({9, 1})
                        }, { // roi1
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {1, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({8, 8, 80, 80, 0, 0, 80, 80})
                        }, { // param27
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param28
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // param29
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-1})
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
                        }, { // param31
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.4f})
                        }, { // param32
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f})
                        }, { // param33
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // scoresOut1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {0},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // roiOut1
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {0, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({})
                        }, { // classesOut1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {0},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // batchSplitOut1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {0},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // in1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 1, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({10})
                        }, { // param34
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // param35
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // param36
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.0f})
                        }, { // param37
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.0f})
                        }, { // param38
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({4})
                        }, { // param39
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({4})
                        }, { // layout
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 2,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // featureMap1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {0, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param40
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.6f})
                        }, { // param41
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.6f})
                        }, { // out1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {0, 1, 3, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }},
                .operations = {{
                            .type = TestOperationType::BOX_WITH_NMS_LIMIT,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8},
                            .outputs = {9, 10, 11, 12}
                        }, {
                            .type = TestOperationType::ROI_ALIGN,
                            .inputs = {13, 10, 12, 14, 15, 16, 17, 18, 19, 20},
                            .outputs = {21}
                        }, {
                            .type = TestOperationType::RESIZE_BILINEAR,
                            .inputs = {21, 22, 23, 20},
                            .outputs = {24}
                        }},
                .inputIndexes = {13},
                .outputIndexes = {9, 11, 24}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_zero_sized_nchw_quant8_signed_2 = TestModelManager::get().add("resize_quant8_signed_zero_sized_nchw_quant8_signed_2", get_test_model_zero_sized_nchw_quant8_signed_2());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_nhwc_quant8_signed_3() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // in2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({4, 8, 12, 16})
                        }, { // param42
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param43
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // layout1
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
                            .dimensions = {1, 1, 1, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({4})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_shape_nhwc_quant8_signed_3 = TestModelManager::get().add("resize_quant8_signed_shape_nhwc_quant8_signed_3", get_test_model_shape_nhwc_quant8_signed_3());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_nhwc_quant8_signed_all_inputs_as_internal_3() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // in2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param42
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param43
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // layout1
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
                            .dimensions = {1, 1, 1, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({4})
                        }, { // in2_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({4, 8, 12, 16})
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
                        }, { // param114
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
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_shape_nhwc_quant8_signed_all_inputs_as_internal_3 = TestModelManager::get().add("resize_quant8_signed_shape_nhwc_quant8_signed_all_inputs_as_internal_3", get_test_model_shape_nhwc_quant8_signed_all_inputs_as_internal_3());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_nchw_quant8_signed_3() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // in2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({4, 8, 12, 16})
                        }, { // param42
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param43
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // layout1
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
                            .dimensions = {1, 1, 1, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({4})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_shape_nchw_quant8_signed_3 = TestModelManager::get().add("resize_quant8_signed_shape_nchw_quant8_signed_3", get_test_model_shape_nchw_quant8_signed_3());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_nchw_quant8_signed_all_inputs_as_internal_3() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // in2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param42
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // param43
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1})
                        }, { // layout1
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
                            .dimensions = {1, 1, 1, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({4})
                        }, { // in2_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({4, 8, 12, 16})
                        }, { // placeholder11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0})
                        }, { // param115
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
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_shape_nchw_quant8_signed_all_inputs_as_internal_3 = TestModelManager::get().add("resize_quant8_signed_shape_nchw_quant8_signed_all_inputs_as_internal_3", get_test_model_shape_nchw_quant8_signed_all_inputs_as_internal_3());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_nhwc_quant8_signed_3() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // in2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({4, 8, 12, 16})
                        }, { // param44
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.5f})
                        }, { // param45
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.5f})
                        }, { // layout1
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
                            .dimensions = {1, 1, 1, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({4})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_scale_nhwc_quant8_signed_3 = TestModelManager::get().add("resize_quant8_signed_scale_nhwc_quant8_signed_3", get_test_model_scale_nhwc_quant8_signed_3());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_nhwc_quant8_signed_all_inputs_as_internal_3() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // in2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param44
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.5f})
                        }, { // param45
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.5f})
                        }, { // layout1
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
                            .dimensions = {1, 1, 1, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({4})
                        }, { // in2_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({4, 8, 12, 16})
                        }, { // placeholder12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0})
                        }, { // param116
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
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_scale_nhwc_quant8_signed_all_inputs_as_internal_3 = TestModelManager::get().add("resize_quant8_signed_scale_nhwc_quant8_signed_all_inputs_as_internal_3", get_test_model_scale_nhwc_quant8_signed_all_inputs_as_internal_3());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_nchw_quant8_signed_3() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // in2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({4, 8, 12, 16})
                        }, { // param44
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.5f})
                        }, { // param45
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.5f})
                        }, { // layout1
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
                            .dimensions = {1, 1, 1, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({4})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_scale_nchw_quant8_signed_3 = TestModelManager::get().add("resize_quant8_signed_scale_nchw_quant8_signed_3", get_test_model_scale_nchw_quant8_signed_3());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_nchw_quant8_signed_all_inputs_as_internal_3() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // in2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param44
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.5f})
                        }, { // param45
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.5f})
                        }, { // layout1
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
                            .dimensions = {1, 1, 1, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({4})
                        }, { // in2_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({4, 8, 12, 16})
                        }, { // placeholder13
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0})
                        }, { // param117
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
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_scale_nchw_quant8_signed_all_inputs_as_internal_3 = TestModelManager::get().add("resize_quant8_signed_scale_nchw_quant8_signed_all_inputs_as_internal_3", get_test_model_scale_nchw_quant8_signed_all_inputs_as_internal_3());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_nhwc_quant8_signed_4() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // in3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-124, -120, -116, -112})
                        }, { // param46
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // param47
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // out3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 3, 3, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-124, -124, -120, -124, -124, -120, -116, -116, -112})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_shape_nhwc_quant8_signed_4 = TestModelManager::get().add("resize_quant8_signed_shape_nhwc_quant8_signed_4", get_test_model_shape_nhwc_quant8_signed_4());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_nhwc_quant8_signed_all_inputs_as_internal_4() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // in3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param46
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // param47
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // out3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 3, 3, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-124, -124, -120, -124, -124, -120, -116, -116, -112})
                        }, { // in3_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-124, -120, -116, -112})
                        }, { // placeholder14
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param118
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
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_shape_nhwc_quant8_signed_all_inputs_as_internal_4 = TestModelManager::get().add("resize_quant8_signed_shape_nhwc_quant8_signed_all_inputs_as_internal_4", get_test_model_shape_nhwc_quant8_signed_all_inputs_as_internal_4());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_nchw_quant8_signed_4() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // in3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-124, -120, -116, -112})
                        }, { // param46
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // param47
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // out3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 3, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-124, -124, -120, -124, -124, -120, -116, -116, -112})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_shape_nchw_quant8_signed_4 = TestModelManager::get().add("resize_quant8_signed_shape_nchw_quant8_signed_4", get_test_model_shape_nchw_quant8_signed_4());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_nchw_quant8_signed_all_inputs_as_internal_4() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // in3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param46
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // param47
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // out3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 3, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-124, -124, -120, -124, -124, -120, -116, -116, -112})
                        }, { // in3_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-124, -120, -116, -112})
                        }, { // placeholder15
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param119
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
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_shape_nchw_quant8_signed_all_inputs_as_internal_4 = TestModelManager::get().add("resize_quant8_signed_shape_nchw_quant8_signed_all_inputs_as_internal_4", get_test_model_shape_nchw_quant8_signed_all_inputs_as_internal_4());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_nhwc_quant8_signed_4() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // in3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-124, -120, -116, -112})
                        }, { // param48
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.5f})
                        }, { // param49
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.5f})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // out3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 3, 3, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-124, -124, -120, -124, -124, -120, -116, -116, -112})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_scale_nhwc_quant8_signed_4 = TestModelManager::get().add("resize_quant8_signed_scale_nhwc_quant8_signed_4", get_test_model_scale_nhwc_quant8_signed_4());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_nhwc_quant8_signed_all_inputs_as_internal_4() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // in3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param48
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.5f})
                        }, { // param49
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.5f})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // out3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 3, 3, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-124, -124, -120, -124, -124, -120, -116, -116, -112})
                        }, { // in3_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-124, -120, -116, -112})
                        }, { // placeholder16
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param120
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
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_scale_nhwc_quant8_signed_all_inputs_as_internal_4 = TestModelManager::get().add("resize_quant8_signed_scale_nhwc_quant8_signed_all_inputs_as_internal_4", get_test_model_scale_nhwc_quant8_signed_all_inputs_as_internal_4());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_nchw_quant8_signed_4() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // in3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-124, -120, -116, -112})
                        }, { // param48
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.5f})
                        }, { // param49
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.5f})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // out3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 3, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-124, -124, -120, -124, -124, -120, -116, -116, -112})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_scale_nchw_quant8_signed_4 = TestModelManager::get().add("resize_quant8_signed_scale_nchw_quant8_signed_4", get_test_model_scale_nchw_quant8_signed_4());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_nchw_quant8_signed_all_inputs_as_internal_4() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // in3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param48
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.5f})
                        }, { // param49
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.5f})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // out3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 3, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-124, -124, -120, -124, -124, -120, -116, -116, -112})
                        }, { // in3_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-124, -120, -116, -112})
                        }, { // placeholder17
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
                        }, { // param121
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
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_scale_nchw_quant8_signed_all_inputs_as_internal_4 = TestModelManager::get().add("resize_quant8_signed_scale_nchw_quant8_signed_all_inputs_as_internal_4", get_test_model_scale_nchw_quant8_signed_all_inputs_as_internal_4());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_nhwc_quant8_signed_5() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // in4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 3, 3, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12, -8, -4, 0, 4, 8})
                        }, { // param50
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // param51
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // out4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -12, -8})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_shape_nhwc_quant8_signed_5 = TestModelManager::get().add("resize_quant8_signed_shape_nhwc_quant8_signed_5", get_test_model_shape_nhwc_quant8_signed_5());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_nhwc_quant8_signed_all_inputs_as_internal_5() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // in4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 3, 3, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param50
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // param51
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // out4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -12, -8})
                        }, { // in4_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 3, 3, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12, -8, -4, 0, 4, 8})
                        }, { // placeholder18
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28})
                        }, { // param122
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
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_shape_nhwc_quant8_signed_all_inputs_as_internal_5 = TestModelManager::get().add("resize_quant8_signed_shape_nhwc_quant8_signed_all_inputs_as_internal_5", get_test_model_shape_nhwc_quant8_signed_all_inputs_as_internal_5());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_nchw_quant8_signed_5() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // in4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 3, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12, -8, -4, 0, 4, 8})
                        }, { // param50
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // param51
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // out4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -12, -8})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_shape_nchw_quant8_signed_5 = TestModelManager::get().add("resize_quant8_signed_shape_nchw_quant8_signed_5", get_test_model_shape_nchw_quant8_signed_5());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_nchw_quant8_signed_all_inputs_as_internal_5() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // in4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 3, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param50
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // param51
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // out4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -12, -8})
                        }, { // in4_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 3, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12, -8, -4, 0, 4, 8})
                        }, { // placeholder19
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28})
                        }, { // param123
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
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_shape_nchw_quant8_signed_all_inputs_as_internal_5 = TestModelManager::get().add("resize_quant8_signed_shape_nchw_quant8_signed_all_inputs_as_internal_5", get_test_model_shape_nchw_quant8_signed_all_inputs_as_internal_5());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_nhwc_quant8_signed_5() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // in4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 3, 3, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12, -8, -4, 0, 4, 8})
                        }, { // param52
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.8f})
                        }, { // param53
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.8f})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // out4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -12, -8})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_scale_nhwc_quant8_signed_5 = TestModelManager::get().add("resize_quant8_signed_scale_nhwc_quant8_signed_5", get_test_model_scale_nhwc_quant8_signed_5());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_nhwc_quant8_signed_all_inputs_as_internal_5() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // in4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 3, 3, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param52
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.8f})
                        }, { // param53
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.8f})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // out4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -12, -8})
                        }, { // in4_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 3, 3, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12, -8, -4, 0, 4, 8})
                        }, { // placeholder20
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28})
                        }, { // param124
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
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_scale_nhwc_quant8_signed_all_inputs_as_internal_5 = TestModelManager::get().add("resize_quant8_signed_scale_nhwc_quant8_signed_all_inputs_as_internal_5", get_test_model_scale_nhwc_quant8_signed_all_inputs_as_internal_5());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_nchw_quant8_signed_5() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // in4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 3, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12, -8, -4, 0, 4, 8})
                        }, { // param52
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.8f})
                        }, { // param53
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.8f})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // out4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -12, -8})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_scale_nchw_quant8_signed_5 = TestModelManager::get().add("resize_quant8_signed_scale_nchw_quant8_signed_5", get_test_model_scale_nchw_quant8_signed_5());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_nchw_quant8_signed_all_inputs_as_internal_5() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // in4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 3, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param52
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.8f})
                        }, { // param53
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.8f})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // out4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -12, -8})
                        }, { // in4_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 3, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12, -8, -4, 0, 4, 8})
                        }, { // placeholder21
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28})
                        }, { // param125
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
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_scale_nchw_quant8_signed_all_inputs_as_internal_5 = TestModelManager::get().add("resize_quant8_signed_scale_nchw_quant8_signed_all_inputs_as_internal_5", get_test_model_scale_nchw_quant8_signed_all_inputs_as_internal_5());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_nhwc_quant8_signed_6() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // in5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12})
                        }, { // param54
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({5})
                        }, { // param55
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // out5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 5, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -24, -24, -20, -20, -16, -16, -16, -12, -12})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_shape_nhwc_quant8_signed_6 = TestModelManager::get().add("resize_quant8_signed_shape_nhwc_quant8_signed_6", get_test_model_shape_nhwc_quant8_signed_6());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_nhwc_quant8_signed_all_inputs_as_internal_6() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // in5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param54
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({5})
                        }, { // param55
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // out5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 5, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -24, -24, -20, -20, -16, -16, -16, -12, -12})
                        }, { // in5_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12})
                        }, { // placeholder22
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28})
                        }, { // param126
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
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_shape_nhwc_quant8_signed_all_inputs_as_internal_6 = TestModelManager::get().add("resize_quant8_signed_shape_nhwc_quant8_signed_all_inputs_as_internal_6", get_test_model_shape_nhwc_quant8_signed_all_inputs_as_internal_6());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_nchw_quant8_signed_6() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // in5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12})
                        }, { // param54
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({5})
                        }, { // param55
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // out5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 5},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -24, -24, -20, -20, -16, -16, -16, -12, -12})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_shape_nchw_quant8_signed_6 = TestModelManager::get().add("resize_quant8_signed_shape_nchw_quant8_signed_6", get_test_model_shape_nchw_quant8_signed_6());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_nchw_quant8_signed_all_inputs_as_internal_6() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // in5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param54
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({5})
                        }, { // param55
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // out5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 5},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -24, -24, -20, -20, -16, -16, -16, -12, -12})
                        }, { // in5_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12})
                        }, { // placeholder23
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28})
                        }, { // param127
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
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_shape_nchw_quant8_signed_all_inputs_as_internal_6 = TestModelManager::get().add("resize_quant8_signed_shape_nchw_quant8_signed_all_inputs_as_internal_6", get_test_model_shape_nchw_quant8_signed_all_inputs_as_internal_6());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_nhwc_quant8_signed_6() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // in5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12})
                        }, { // param56
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.6f})
                        }, { // param57
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.1f})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // out5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 5, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -24, -24, -20, -20, -16, -16, -16, -12, -12})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_scale_nhwc_quant8_signed_6 = TestModelManager::get().add("resize_quant8_signed_scale_nhwc_quant8_signed_6", get_test_model_scale_nhwc_quant8_signed_6());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_nhwc_quant8_signed_all_inputs_as_internal_6() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // in5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param56
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.6f})
                        }, { // param57
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.1f})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // out5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 5, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -24, -24, -20, -20, -16, -16, -16, -12, -12})
                        }, { // in5_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12})
                        }, { // placeholder24
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28})
                        }, { // param128
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
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_scale_nhwc_quant8_signed_all_inputs_as_internal_6 = TestModelManager::get().add("resize_quant8_signed_scale_nhwc_quant8_signed_all_inputs_as_internal_6", get_test_model_scale_nhwc_quant8_signed_all_inputs_as_internal_6());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_nchw_quant8_signed_6() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // in5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12})
                        }, { // param56
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.6f})
                        }, { // param57
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.1f})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // out5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 5},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -24, -24, -20, -20, -16, -16, -16, -12, -12})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_scale_nchw_quant8_signed_6 = TestModelManager::get().add("resize_quant8_signed_scale_nchw_quant8_signed_6", get_test_model_scale_nchw_quant8_signed_6());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_nchw_quant8_signed_all_inputs_as_internal_6() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // in5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param56
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.6f})
                        }, { // param57
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.1f})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // out5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 5},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -24, -24, -20, -20, -16, -16, -16, -12, -12})
                        }, { // in5_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12})
                        }, { // placeholder25
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28})
                        }, { // param129
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
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_scale_nchw_quant8_signed_all_inputs_as_internal_6 = TestModelManager::get().add("resize_quant8_signed_scale_nchw_quant8_signed_all_inputs_as_internal_6", get_test_model_scale_nchw_quant8_signed_all_inputs_as_internal_6());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_nhwc_quant8_signed_7() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // in6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12, -8, -4, 0, 4, 8, 12, 16, 20, 24, 28, 32, 36})
                        }, { // param58
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // param59
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // out6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 3, 3, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -8, -4, 0, 8, 12, 16})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_shape_nhwc_quant8_signed_7 = TestModelManager::get().add("resize_quant8_signed_shape_nhwc_quant8_signed_7", get_test_model_shape_nhwc_quant8_signed_7());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_nhwc_quant8_signed_all_inputs_as_internal_7() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // in6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param58
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // param59
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // out6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 3, 3, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -8, -4, 0, 8, 12, 16})
                        }, { // in6_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12, -8, -4, 0, 4, 8, 12, 16, 20, 24, 28, 32, 36})
                        }, { // placeholder26
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28})
                        }, { // param130
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
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_shape_nhwc_quant8_signed_all_inputs_as_internal_7 = TestModelManager::get().add("resize_quant8_signed_shape_nhwc_quant8_signed_all_inputs_as_internal_7", get_test_model_shape_nhwc_quant8_signed_all_inputs_as_internal_7());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_nchw_quant8_signed_7() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // in6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12, -8, -4, 0, 4, 8, 12, 16, 20, 24, 28, 32, 36})
                        }, { // param58
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // param59
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // out6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 3, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -8, -4, 0, 8, 12, 16})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_shape_nchw_quant8_signed_7 = TestModelManager::get().add("resize_quant8_signed_shape_nchw_quant8_signed_7", get_test_model_shape_nchw_quant8_signed_7());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_nchw_quant8_signed_all_inputs_as_internal_7() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // in6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param58
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // param59
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // out6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 3, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -8, -4, 0, 8, 12, 16})
                        }, { // in6_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12, -8, -4, 0, 4, 8, 12, 16, 20, 24, 28, 32, 36})
                        }, { // placeholder27
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28})
                        }, { // param131
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
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_shape_nchw_quant8_signed_all_inputs_as_internal_7 = TestModelManager::get().add("resize_quant8_signed_shape_nchw_quant8_signed_all_inputs_as_internal_7", get_test_model_shape_nchw_quant8_signed_all_inputs_as_internal_7());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_nhwc_quant8_signed_7() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // in6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12, -8, -4, 0, 4, 8, 12, 16, 20, 24, 28, 32, 36})
                        }, { // param60
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.9f})
                        }, { // param61
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.9f})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // out6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 3, 3, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -8, -4, 0, 8, 12, 16})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_scale_nhwc_quant8_signed_7 = TestModelManager::get().add("resize_quant8_signed_scale_nhwc_quant8_signed_7", get_test_model_scale_nhwc_quant8_signed_7());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_nhwc_quant8_signed_all_inputs_as_internal_7() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // in6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param60
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.9f})
                        }, { // param61
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.9f})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // out6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 3, 3, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -8, -4, 0, 8, 12, 16})
                        }, { // in6_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12, -8, -4, 0, 4, 8, 12, 16, 20, 24, 28, 32, 36})
                        }, { // placeholder28
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28})
                        }, { // param132
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
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_scale_nhwc_quant8_signed_all_inputs_as_internal_7 = TestModelManager::get().add("resize_quant8_signed_scale_nhwc_quant8_signed_all_inputs_as_internal_7", get_test_model_scale_nhwc_quant8_signed_all_inputs_as_internal_7());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_nchw_quant8_signed_7() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // in6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12, -8, -4, 0, 4, 8, 12, 16, 20, 24, 28, 32, 36})
                        }, { // param60
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.9f})
                        }, { // param61
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.9f})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // out6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 3, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -8, -4, 0, 8, 12, 16})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_scale_nchw_quant8_signed_7 = TestModelManager::get().add("resize_quant8_signed_scale_nchw_quant8_signed_7", get_test_model_scale_nchw_quant8_signed_7());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_nchw_quant8_signed_all_inputs_as_internal_7() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // in6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param60
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.9f})
                        }, { // param61
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.9f})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // out6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 3, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -8, -4, 0, 8, 12, 16})
                        }, { // in6_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12, -8, -4, 0, 4, 8, 12, 16, 20, 24, 28, 32, 36})
                        }, { // placeholder29
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28})
                        }, { // param133
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
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_scale_nchw_quant8_signed_all_inputs_as_internal_7 = TestModelManager::get().add("resize_quant8_signed_scale_nchw_quant8_signed_all_inputs_as_internal_7", get_test_model_scale_nchw_quant8_signed_all_inputs_as_internal_7());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_nhwc_quant8_signed_8() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // in7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12})
                        }, { // param62
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // param63
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({5})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // out7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 5, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -24, -20, -24, -20, -16, -12, -16, -12})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_shape_nhwc_quant8_signed_8 = TestModelManager::get().add("resize_quant8_signed_shape_nhwc_quant8_signed_8", get_test_model_shape_nhwc_quant8_signed_8());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_nhwc_quant8_signed_all_inputs_as_internal_8() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // in7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param62
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // param63
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({5})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // out7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 5, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -24, -20, -24, -20, -16, -12, -16, -12})
                        }, { // in7_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12})
                        }, { // placeholder30
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28})
                        }, { // param134
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
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_shape_nhwc_quant8_signed_all_inputs_as_internal_8 = TestModelManager::get().add("resize_quant8_signed_shape_nhwc_quant8_signed_all_inputs_as_internal_8", get_test_model_shape_nhwc_quant8_signed_all_inputs_as_internal_8());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_nchw_quant8_signed_8() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // in7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12})
                        }, { // param62
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // param63
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({5})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // out7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 5, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -24, -20, -24, -20, -16, -12, -16, -12})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_shape_nchw_quant8_signed_8 = TestModelManager::get().add("resize_quant8_signed_shape_nchw_quant8_signed_8", get_test_model_shape_nchw_quant8_signed_8());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_nchw_quant8_signed_all_inputs_as_internal_8() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // in7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param62
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // param63
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({5})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // out7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 5, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -24, -20, -24, -20, -16, -12, -16, -12})
                        }, { // in7_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12})
                        }, { // placeholder31
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28})
                        }, { // param135
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
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_shape_nchw_quant8_signed_all_inputs_as_internal_8 = TestModelManager::get().add("resize_quant8_signed_shape_nchw_quant8_signed_all_inputs_as_internal_8", get_test_model_shape_nchw_quant8_signed_all_inputs_as_internal_8());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_nhwc_quant8_signed_8() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // in7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12})
                        }, { // param64
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.4f})
                        }, { // param65
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.8f})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // out7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 5, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -24, -20, -24, -20, -16, -12, -16, -12})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_scale_nhwc_quant8_signed_8 = TestModelManager::get().add("resize_quant8_signed_scale_nhwc_quant8_signed_8", get_test_model_scale_nhwc_quant8_signed_8());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_nhwc_quant8_signed_all_inputs_as_internal_8() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // in7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param64
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.4f})
                        }, { // param65
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.8f})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // out7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 5, 2, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -24, -20, -24, -20, -16, -12, -16, -12})
                        }, { // in7_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12})
                        }, { // placeholder32
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28})
                        }, { // param136
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
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_scale_nhwc_quant8_signed_all_inputs_as_internal_8 = TestModelManager::get().add("resize_quant8_signed_scale_nhwc_quant8_signed_all_inputs_as_internal_8", get_test_model_scale_nhwc_quant8_signed_all_inputs_as_internal_8());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_nchw_quant8_signed_8() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // in7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12})
                        }, { // param64
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.4f})
                        }, { // param65
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.8f})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // out7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 5, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -24, -20, -24, -20, -16, -12, -16, -12})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_scale_nchw_quant8_signed_8 = TestModelManager::get().add("resize_quant8_signed_scale_nchw_quant8_signed_8", get_test_model_scale_nchw_quant8_signed_8());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_nchw_quant8_signed_all_inputs_as_internal_8() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // in7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param64
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.4f})
                        }, { // param65
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.8f})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // out7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 5, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -24, -20, -24, -20, -16, -12, -16, -12})
                        }, { // in7_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12})
                        }, { // placeholder33
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28})
                        }, { // param137
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
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_scale_nchw_quant8_signed_all_inputs_as_internal_8 = TestModelManager::get().add("resize_quant8_signed_scale_nchw_quant8_signed_all_inputs_as_internal_8", get_test_model_scale_nchw_quant8_signed_all_inputs_as_internal_8());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_nhwc_quant8_signed_9() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // in8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12})
                        }, { // param66
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({4})
                        }, { // param67
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({4})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // out8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -24, -20, -20, -24, -24, -20, -20, -16, -16, -12, -12, -16, -16, -12, -12})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_shape_nhwc_quant8_signed_9 = TestModelManager::get().add("resize_quant8_signed_shape_nhwc_quant8_signed_9", get_test_model_shape_nhwc_quant8_signed_9());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_nhwc_quant8_signed_all_inputs_as_internal_9() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // in8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param66
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({4})
                        }, { // param67
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({4})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // out8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -24, -20, -20, -24, -24, -20, -20, -16, -16, -12, -12, -16, -16, -12, -12})
                        }, { // in8_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12})
                        }, { // placeholder34
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28})
                        }, { // param138
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
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_shape_nhwc_quant8_signed_all_inputs_as_internal_9 = TestModelManager::get().add("resize_quant8_signed_shape_nhwc_quant8_signed_all_inputs_as_internal_9", get_test_model_shape_nhwc_quant8_signed_all_inputs_as_internal_9());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_nchw_quant8_signed_9() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // in8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12})
                        }, { // param66
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({4})
                        }, { // param67
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({4})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // out8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -24, -20, -20, -24, -24, -20, -20, -16, -16, -12, -12, -16, -16, -12, -12})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_shape_nchw_quant8_signed_9 = TestModelManager::get().add("resize_quant8_signed_shape_nchw_quant8_signed_9", get_test_model_shape_nchw_quant8_signed_9());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_nchw_quant8_signed_all_inputs_as_internal_9() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // in8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param66
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({4})
                        }, { // param67
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({4})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // out8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -24, -20, -20, -24, -24, -20, -20, -16, -16, -12, -12, -16, -16, -12, -12})
                        }, { // in8_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12})
                        }, { // placeholder35
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28})
                        }, { // param139
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
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_shape_nchw_quant8_signed_all_inputs_as_internal_9 = TestModelManager::get().add("resize_quant8_signed_shape_nchw_quant8_signed_all_inputs_as_internal_9", get_test_model_shape_nchw_quant8_signed_all_inputs_as_internal_9());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_nhwc_quant8_signed_9() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // in8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12})
                        }, { // param68
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.0f})
                        }, { // param69
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.0f})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // out8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -24, -20, -20, -24, -24, -20, -20, -16, -16, -12, -12, -16, -16, -12, -12})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_scale_nhwc_quant8_signed_9 = TestModelManager::get().add("resize_quant8_signed_scale_nhwc_quant8_signed_9", get_test_model_scale_nhwc_quant8_signed_9());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_nhwc_quant8_signed_all_inputs_as_internal_9() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // in8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param68
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.0f})
                        }, { // param69
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.0f})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // out8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -24, -20, -20, -24, -24, -20, -20, -16, -16, -12, -12, -16, -16, -12, -12})
                        }, { // in8_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12})
                        }, { // placeholder36
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28})
                        }, { // param140
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
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_scale_nhwc_quant8_signed_all_inputs_as_internal_9 = TestModelManager::get().add("resize_quant8_signed_scale_nhwc_quant8_signed_all_inputs_as_internal_9", get_test_model_scale_nhwc_quant8_signed_all_inputs_as_internal_9());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_nchw_quant8_signed_9() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // in8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12})
                        }, { // param68
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.0f})
                        }, { // param69
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.0f})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // out8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -24, -20, -20, -24, -24, -20, -20, -16, -16, -12, -12, -16, -16, -12, -12})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_scale_nchw_quant8_signed_9 = TestModelManager::get().add("resize_quant8_signed_scale_nchw_quant8_signed_9", get_test_model_scale_nchw_quant8_signed_9());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_nchw_quant8_signed_all_inputs_as_internal_9() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // in8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param68
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.0f})
                        }, { // param69
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.0f})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // out8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -24, -20, -20, -24, -24, -20, -20, -16, -16, -12, -12, -16, -16, -12, -12})
                        }, { // in8_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12})
                        }, { // placeholder37
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28})
                        }, { // param141
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
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_scale_nchw_quant8_signed_all_inputs_as_internal_9 = TestModelManager::get().add("resize_quant8_signed_scale_nchw_quant8_signed_all_inputs_as_internal_9", get_test_model_scale_nchw_quant8_signed_all_inputs_as_internal_9());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_nhwc_quant8_signed_10() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // in9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -24, -20, -20, -16, -16, -12, -12, -8, -8, -4, -4, 0, 0, 4, 4})
                        }, { // param70
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // param71
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // out9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 3, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -24, -24, -24, -20, -20, -24, -24, -24, -24, -20, -20, -16, -16, -16, -16, -12, -12, -8, -8, -8, -8, -4, -4, -8, -8, -8, -8, -4, -4, 0, 0, 0, 0, 4, 4})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_shape_nhwc_quant8_signed_10 = TestModelManager::get().add("resize_quant8_signed_shape_nhwc_quant8_signed_10", get_test_model_shape_nhwc_quant8_signed_10());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_nhwc_quant8_signed_all_inputs_as_internal_10() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // in9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param70
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // param71
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // out9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 3, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -24, -24, -24, -20, -20, -24, -24, -24, -24, -20, -20, -16, -16, -16, -16, -12, -12, -8, -8, -8, -8, -4, -4, -8, -8, -8, -8, -4, -4, 0, 0, 0, 0, 4, 4})
                        }, { // in9_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -24, -20, -20, -16, -16, -12, -12, -8, -8, -4, -4, 0, 0, 4, 4})
                        }, { // placeholder38
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28})
                        }, { // param142
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
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_shape_nhwc_quant8_signed_all_inputs_as_internal_10 = TestModelManager::get().add("resize_quant8_signed_shape_nhwc_quant8_signed_all_inputs_as_internal_10", get_test_model_shape_nhwc_quant8_signed_all_inputs_as_internal_10());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_nchw_quant8_signed_10() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // in9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12, -24, -20, -16, -12, -8, -4, 0, 4, -8, -4, 0, 4})
                        }, { // param70
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // param71
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // out9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 3, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -24, -20, -24, -24, -20, -16, -16, -12, -24, -24, -20, -24, -24, -20, -16, -16, -12, -8, -8, -4, -8, -8, -4, 0, 0, 4, -8, -8, -4, -8, -8, -4, 0, 0, 4})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_shape_nchw_quant8_signed_10 = TestModelManager::get().add("resize_quant8_signed_shape_nchw_quant8_signed_10", get_test_model_shape_nchw_quant8_signed_10());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_shape_nchw_quant8_signed_all_inputs_as_internal_10() {
    static TestModel model = {
        .main = { // shape
                .operands = {{ // in9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param70
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // param71
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // out9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 3, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -24, -20, -24, -24, -20, -16, -16, -12, -24, -24, -20, -24, -24, -20, -16, -16, -12, -8, -8, -4, -8, -8, -4, 0, 0, 4, -8, -8, -4, -8, -8, -4, 0, 0, 4})
                        }, { // in9_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12, -24, -20, -16, -12, -8, -4, 0, 4, -8, -4, 0, 4})
                        }, { // placeholder39
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28})
                        }, { // param143
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
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_shape_nchw_quant8_signed_all_inputs_as_internal_10 = TestModelManager::get().add("resize_quant8_signed_shape_nchw_quant8_signed_all_inputs_as_internal_10", get_test_model_shape_nchw_quant8_signed_all_inputs_as_internal_10());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_nhwc_quant8_signed_10() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // in9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -24, -20, -20, -16, -16, -12, -12, -8, -8, -4, -4, 0, 0, 4, 4})
                        }, { // param72
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.6f})
                        }, { // param73
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.8f})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // out9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 3, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -24, -24, -24, -20, -20, -24, -24, -24, -24, -20, -20, -16, -16, -16, -16, -12, -12, -8, -8, -8, -8, -4, -4, -8, -8, -8, -8, -4, -4, 0, 0, 0, 0, 4, 4})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_scale_nhwc_quant8_signed_10 = TestModelManager::get().add("resize_quant8_signed_scale_nhwc_quant8_signed_10", get_test_model_scale_nhwc_quant8_signed_10());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_nhwc_quant8_signed_all_inputs_as_internal_10() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // in9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param72
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.6f})
                        }, { // param73
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.8f})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // out9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 3, 3, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -24, -24, -24, -20, -20, -24, -24, -24, -24, -20, -20, -16, -16, -16, -16, -12, -12, -8, -8, -8, -8, -4, -4, -8, -8, -8, -8, -4, -4, 0, 0, 0, 0, 4, 4})
                        }, { // in9_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -24, -20, -20, -16, -16, -12, -12, -8, -8, -4, -4, 0, 0, 4, 4})
                        }, { // placeholder40
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28})
                        }, { // param144
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
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_scale_nhwc_quant8_signed_all_inputs_as_internal_10 = TestModelManager::get().add("resize_quant8_signed_scale_nhwc_quant8_signed_all_inputs_as_internal_10", get_test_model_scale_nhwc_quant8_signed_all_inputs_as_internal_10());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_nchw_quant8_signed_10() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // in9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12, -24, -20, -16, -12, -8, -4, 0, 4, -8, -4, 0, 4})
                        }, { // param72
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.6f})
                        }, { // param73
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.8f})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // out9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 3, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -24, -20, -24, -24, -20, -16, -16, -12, -24, -24, -20, -24, -24, -20, -16, -16, -12, -8, -8, -4, -8, -8, -4, 0, 0, 4, -8, -8, -4, -8, -8, -4, 0, 0, 4})
                        }},
                .operations = {{
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_scale_nchw_quant8_signed_10 = TestModelManager::get().add("resize_quant8_signed_scale_nchw_quant8_signed_10", get_test_model_scale_nchw_quant8_signed_10());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_scale_nchw_quant8_signed_all_inputs_as_internal_10() {
    static TestModel model = {
        .main = { // scale
                .operands = {{ // in9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param72
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.6f})
                        }, { // param73
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.8f})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // out9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 3, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -24, -20, -24, -24, -20, -16, -16, -12, -24, -24, -20, -24, -24, -20, -16, -16, -12, -8, -8, -4, -8, -8, -4, 0, 0, 4, -8, -8, -4, -8, -8, -4, 0, 0, 4})
                        }, { // in9_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-24, -20, -16, -12, -24, -20, -16, -12, -8, -4, 0, 4, -8, -4, 0, 4})
                        }, { // placeholder41
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = -28,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-28})
                        }, { // param145
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
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
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
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_scale_nchw_quant8_signed_all_inputs_as_internal_10 = TestModelManager::get().add("resize_quant8_signed_scale_nchw_quant8_signed_all_inputs_as_internal_10", get_test_model_scale_nchw_quant8_signed_all_inputs_as_internal_10());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_zero_sized_nhwc_quant8_signed_3() {
    static TestModel model = {
        .main = { // zero_sized
                .operands = {{ // scores2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({9, 1})
                        }, { // roi2
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {1, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({8, 8, 80, 80, 0, 0, 80, 80})
                        }, { // param74
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param75
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // param76
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-1})
                        }, { // param77
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param78
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.4f})
                        }, { // param79
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f})
                        }, { // param80
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // scoresOut2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {0},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // roiOut2
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {0, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({})
                        }, { // classesOut2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {0},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // batchSplitOut2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {0},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // in10
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 1, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({10})
                        }, { // param81
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // param82
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // param83
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.0f})
                        }, { // param84
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.0f})
                        }, { // param85
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({4})
                        }, { // param86
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({4})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 2,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // featureMap2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {0, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param87
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // param88
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // out10
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {0, 3, 3, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }},
                .operations = {{
                            .type = TestOperationType::BOX_WITH_NMS_LIMIT,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8},
                            .outputs = {9, 10, 11, 12}
                        }, {
                            .type = TestOperationType::ROI_ALIGN,
                            .inputs = {13, 10, 12, 14, 15, 16, 17, 18, 19, 20},
                            .outputs = {21}
                        }, {
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
                            .inputs = {21, 22, 23, 20},
                            .outputs = {24}
                        }},
                .inputIndexes = {13},
                .outputIndexes = {9, 11, 24}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_zero_sized_nhwc_quant8_signed_3 = TestModelManager::get().add("resize_quant8_signed_zero_sized_nhwc_quant8_signed_3", get_test_model_zero_sized_nhwc_quant8_signed_3());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_zero_sized_nchw_quant8_signed_3() {
    static TestModel model = {
        .main = { // zero_sized
                .operands = {{ // scores2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({9, 1})
                        }, { // roi2
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {1, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({8, 8, 80, 80, 0, 0, 80, 80})
                        }, { // param74
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param75
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // param76
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-1})
                        }, { // param77
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param78
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.4f})
                        }, { // param79
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f})
                        }, { // param80
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // scoresOut2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {0},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // roiOut2
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {0, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({})
                        }, { // classesOut2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {0},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // batchSplitOut2
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {0},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // in10
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 1, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({10})
                        }, { // param81
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // param82
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // param83
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.0f})
                        }, { // param84
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.0f})
                        }, { // param85
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({4})
                        }, { // param86
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({4})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 2,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // featureMap2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {0, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param87
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // param88
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({3})
                        }, { // out10
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {0, 1, 3, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }},
                .operations = {{
                            .type = TestOperationType::BOX_WITH_NMS_LIMIT,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8},
                            .outputs = {9, 10, 11, 12}
                        }, {
                            .type = TestOperationType::ROI_ALIGN,
                            .inputs = {13, 10, 12, 14, 15, 16, 17, 18, 19, 20},
                            .outputs = {21}
                        }, {
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
                            .inputs = {21, 22, 23, 20},
                            .outputs = {24}
                        }},
                .inputIndexes = {13},
                .outputIndexes = {9, 11, 24}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_zero_sized_nchw_quant8_signed_3 = TestModelManager::get().add("resize_quant8_signed_zero_sized_nchw_quant8_signed_3", get_test_model_zero_sized_nchw_quant8_signed_3());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_zero_sized_nhwc_quant8_signed_4() {
    static TestModel model = {
        .main = { // zero_sized
                .operands = {{ // scores3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({9, 1})
                        }, { // roi3
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {1, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({8, 8, 80, 80, 0, 0, 80, 80})
                        }, { // param89
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param90
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // param91
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-1})
                        }, { // param92
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param93
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.4f})
                        }, { // param94
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f})
                        }, { // param95
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // scoresOut3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {0},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // roiOut3
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {0, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({})
                        }, { // classesOut3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {0},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // batchSplitOut3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {0},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // in11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 1, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({10})
                        }, { // param96
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // param97
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // param98
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.0f})
                        }, { // param99
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.0f})
                        }, { // param100
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({4})
                        }, { // param101
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({4})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 2,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // featureMap3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {0, 2, 2, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param102
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.6f})
                        }, { // param103
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.6f})
                        }, { // out11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {0, 3, 3, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }},
                .operations = {{
                            .type = TestOperationType::BOX_WITH_NMS_LIMIT,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8},
                            .outputs = {9, 10, 11, 12}
                        }, {
                            .type = TestOperationType::ROI_ALIGN,
                            .inputs = {13, 10, 12, 14, 15, 16, 17, 18, 19, 20},
                            .outputs = {21}
                        }, {
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
                            .inputs = {21, 22, 23, 20},
                            .outputs = {24}
                        }},
                .inputIndexes = {13},
                .outputIndexes = {9, 11, 24}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_zero_sized_nhwc_quant8_signed_4 = TestModelManager::get().add("resize_quant8_signed_zero_sized_nhwc_quant8_signed_4", get_test_model_zero_sized_nhwc_quant8_signed_4());

}  // namespace generated_tests::resize_quant8_signed

namespace generated_tests::resize_quant8_signed {

const TestModel& get_test_model_zero_sized_nchw_quant8_signed_4() {
    static TestModel model = {
        .main = { // zero_sized
                .operands = {{ // scores3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({9, 1})
                        }, { // roi3
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {1, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({8, 8, 80, 80, 0, 0, 80, 80})
                        }, { // param89
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param90
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // param91
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-1})
                        }, { // param92
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // param93
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.4f})
                        }, { // param94
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.0f})
                        }, { // param95
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f})
                        }, { // scoresOut3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {0},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // roiOut3
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {0, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({})
                        }, { // classesOut3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {0},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // batchSplitOut3
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {0},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // in11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 1, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({10})
                        }, { // param96
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // param97
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // param98
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.0f})
                        }, { // param99
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({2.0f})
                        }, { // param100
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({4})
                        }, { // param101
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({4})
                        }, { // layout1
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 2,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true})
                        }, { // featureMap3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {0, 1, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // param102
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.6f})
                        }, { // param103
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({1.6f})
                        }, { // out11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {0, 1, 3, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }},
                .operations = {{
                            .type = TestOperationType::BOX_WITH_NMS_LIMIT,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8},
                            .outputs = {9, 10, 11, 12}
                        }, {
                            .type = TestOperationType::ROI_ALIGN,
                            .inputs = {13, 10, 12, 14, 15, 16, 17, 18, 19, 20},
                            .outputs = {21}
                        }, {
                            .type = TestOperationType::RESIZE_NEAREST_NEIGHBOR,
                            .inputs = {21, 22, 23, 20},
                            .outputs = {24}
                        }},
                .inputIndexes = {13},
                .outputIndexes = {9, 11, 24}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_zero_sized_nchw_quant8_signed_4 = TestModelManager::get().add("resize_quant8_signed_zero_sized_nchw_quant8_signed_4", get_test_model_zero_sized_nchw_quant8_signed_4());

}  // namespace generated_tests::resize_quant8_signed

