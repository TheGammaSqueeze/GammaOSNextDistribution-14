// Generated from space_to_depth_quant8_signed.mod.py
// DO NOT EDIT
// clang-format off
#include "TestHarness.h"
using namespace test_helper;  // NOLINT(google-build-using-namespace)

namespace generated_tests::space_to_depth_quant8_signed {

const TestModel& get_test_model() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127, -126, -125, -124, 124, 125, 126, 127})
                        }, { // radius
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 1, 8},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127, -126, -125, -124, 124, 125, 126, 127})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_DEPTH,
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

const auto dummy_test_model = TestModelManager::get().add("space_to_depth_quant8_signed", get_test_model());

}  // namespace generated_tests::space_to_depth_quant8_signed

namespace generated_tests::space_to_depth_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // radius
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 1, 8},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127, -126, -125, -124, 124, 125, 126, 127})
                        }, { // input_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-127, -126, -125, -124, 124, 125, 126, 127})
                        }, { // placeholder
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
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
                            .type = TestOperationType::SPACE_TO_DEPTH,
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

const auto dummy_test_model_all_inputs_as_internal = TestModelManager::get().add("space_to_depth_quant8_signed_all_inputs_as_internal", get_test_model_all_inputs_as_internal());

}  // namespace generated_tests::space_to_depth_quant8_signed

namespace generated_tests::space_to_depth_quant8_signed {

const TestModel& get_test_model_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // input1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, -122, -121, 120, 121, 122, 123, 124, 125, 126, 127})
                        }, { // radius1
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // output1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -124, -123, -126, -125, -122, -121, 120, 121, 124, 125, 122, 123, 126, 127})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_DEPTH,
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

const auto dummy_test_model_2 = TestModelManager::get().add("space_to_depth_quant8_signed_2", get_test_model_2());

}  // namespace generated_tests::space_to_depth_quant8_signed

namespace generated_tests::space_to_depth_quant8_signed {

const TestModel& get_test_model_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // input1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // radius1
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
                        }, { // output1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -124, -123, -126, -125, -122, -121, 120, 121, 124, 125, 122, 123, 126, 127})
                        }, { // input1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128, -127, -126, -125, -124, -123, -122, -121, 120, 121, 122, 123, 124, 125, 126, 127})
                        }, { // placeholder1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
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
                            .type = TestOperationType::SPACE_TO_DEPTH,
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

const auto dummy_test_model_all_inputs_as_internal_2 = TestModelManager::get().add("space_to_depth_quant8_signed_all_inputs_as_internal_2", get_test_model_all_inputs_as_internal_2());

}  // namespace generated_tests::space_to_depth_quant8_signed

namespace generated_tests::space_to_depth_quant8_signed {

const TestModel& get_test_model_nhwc_quant8_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-114, -105, -96, -87, -74, -65, -56, -47})
                        }, { // param
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
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
                            .dimensions = {1, 1, 1, 8},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-114, -105, -96, -87, -74, -65, -56, -47})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_DEPTH,
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

const auto dummy_test_model_nhwc_quant8_signed = TestModelManager::get().add("space_to_depth_quant8_signed_nhwc_quant8_signed", get_test_model_nhwc_quant8_signed());

}  // namespace generated_tests::space_to_depth_quant8_signed

namespace generated_tests::space_to_depth_quant8_signed {

const TestModel& get_test_model_nhwc_quant8_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
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
                            .data = TestBuffer::createFromVector<int32_t>({2})
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
                            .dimensions = {1, 1, 1, 8},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-114, -105, -96, -87, -74, -65, -56, -47})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-114, -105, -96, -87, -74, -65, -56, -47})
                        }, { // placeholder2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
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
                            .type = TestOperationType::SPACE_TO_DEPTH,
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

const auto dummy_test_model_nhwc_quant8_signed_all_inputs_as_internal = TestModelManager::get().add("space_to_depth_quant8_signed_nhwc_quant8_signed_all_inputs_as_internal", get_test_model_nhwc_quant8_signed_all_inputs_as_internal());

}  // namespace generated_tests::space_to_depth_quant8_signed

namespace generated_tests::space_to_depth_quant8_signed {

const TestModel& get_test_model_nchw_quant8_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-114, -96, -74, -56, -105, -87, -65, -47})
                        }, { // param
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2})
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
                            .dimensions = {1, 8, 1, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-114, -105, -96, -87, -74, -65, -56, -47})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_DEPTH,
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

const auto dummy_test_model_nchw_quant8_signed = TestModelManager::get().add("space_to_depth_quant8_signed_nchw_quant8_signed", get_test_model_nchw_quant8_signed());

}  // namespace generated_tests::space_to_depth_quant8_signed

namespace generated_tests::space_to_depth_quant8_signed {

const TestModel& get_test_model_nchw_quant8_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
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
                            .data = TestBuffer::createFromVector<int32_t>({2})
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
                            .dimensions = {1, 8, 1, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.1f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-114, -105, -96, -87, -74, -65, -56, -47})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-114, -96, -74, -56, -105, -87, -65, -47})
                        }, { // placeholder3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.1f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
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
                            .type = TestOperationType::SPACE_TO_DEPTH,
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

const auto dummy_test_model_nchw_quant8_signed_all_inputs_as_internal = TestModelManager::get().add("space_to_depth_quant8_signed_nchw_quant8_signed_all_inputs_as_internal", get_test_model_nchw_quant8_signed_all_inputs_as_internal());

}  // namespace generated_tests::space_to_depth_quant8_signed

namespace generated_tests::space_to_depth_quant8_signed {

const TestModel& get_test_model_nhwc_quant8_signed_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({2, 4, 10, 12, 6, 8, 14, 16, 18, 20, 26, 28, 22, 24, 30, 32})
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
                            .dimensions = {1, 2, 2, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_DEPTH,
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

const auto dummy_test_model_nhwc_quant8_signed_2 = TestModelManager::get().add("space_to_depth_quant8_signed_nhwc_quant8_signed_2", get_test_model_nhwc_quant8_signed_2());

}  // namespace generated_tests::space_to_depth_quant8_signed

namespace generated_tests::space_to_depth_quant8_signed {

const TestModel& get_test_model_nhwc_quant8_signed_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                            .dimensions = {1, 2, 2, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32})
                        }, { // op11_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 4, 4, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({2, 4, 10, 12, 6, 8, 14, 16, 18, 20, 26, 28, 22, 24, 30, 32})
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
                            .type = TestOperationType::SPACE_TO_DEPTH,
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

const auto dummy_test_model_nhwc_quant8_signed_all_inputs_as_internal_2 = TestModelManager::get().add("space_to_depth_quant8_signed_nhwc_quant8_signed_all_inputs_as_internal_2", get_test_model_nhwc_quant8_signed_all_inputs_as_internal_2());

}  // namespace generated_tests::space_to_depth_quant8_signed

namespace generated_tests::space_to_depth_quant8_signed {

const TestModel& get_test_model_nchw_quant8_signed_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({2, 4, 10, 12, 6, 8, 14, 16, 18, 20, 26, 28, 22, 24, 30, 32})
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
                            .dimensions = {1, 4, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({2, 10, 18, 26, 4, 12, 20, 28, 6, 14, 22, 30, 8, 16, 24, 32})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_DEPTH,
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

const auto dummy_test_model_nchw_quant8_signed_2 = TestModelManager::get().add("space_to_depth_quant8_signed_nchw_quant8_signed_2", get_test_model_nchw_quant8_signed_2());

}  // namespace generated_tests::space_to_depth_quant8_signed

namespace generated_tests::space_to_depth_quant8_signed {

const TestModel& get_test_model_nchw_quant8_signed_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                            .dimensions = {1, 4, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({2, 10, 18, 26, 4, 12, 20, 28, 6, 14, 22, 30, 8, 16, 24, 32})
                        }, { // op11_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 1, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.5f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({2, 4, 10, 12, 6, 8, 14, 16, 18, 20, 26, 28, 22, 24, 30, 32})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_DEPTH,
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

const auto dummy_test_model_nchw_quant8_signed_all_inputs_as_internal_2 = TestModelManager::get().add("space_to_depth_quant8_signed_nchw_quant8_signed_all_inputs_as_internal_2", get_test_model_nchw_quant8_signed_all_inputs_as_internal_2());

}  // namespace generated_tests::space_to_depth_quant8_signed

namespace generated_tests::space_to_depth_quant8_signed {

const TestModel& get_test_model_nhwc_quant8_signed_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 4, 4, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-118, -108, -117, -107, -116, -106, -115, -105, -114, -104, -113, -103, -112, -102, -111, -101, -110, -100, -109, -99, -18, 82, -17, 83, -16, 84, -15, 85, -14, 86, -13, 87})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 8},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-118, -108, -117, -107, -114, -104, -113, -103, -116, -106, -115, -105, -112, -102, -111, -101, -110, -100, -109, -99, -16, 84, -15, 85, -18, 82, -17, 83, -14, 86, -13, 87})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_DEPTH,
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

const auto dummy_test_model_nhwc_quant8_signed_3 = TestModelManager::get().add("space_to_depth_quant8_signed_nhwc_quant8_signed_3", get_test_model_nhwc_quant8_signed_3());

}  // namespace generated_tests::space_to_depth_quant8_signed

namespace generated_tests::space_to_depth_quant8_signed {

const TestModel& get_test_model_nhwc_quant8_signed_all_inputs_as_internal_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 4, 4, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 2, 8},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-118, -108, -117, -107, -114, -104, -113, -103, -116, -106, -115, -105, -112, -102, -111, -101, -110, -100, -109, -99, -16, 84, -15, 85, -18, 82, -17, 83, -14, 86, -13, 87})
                        }, { // op12_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 4, 4, 2},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-118, -108, -117, -107, -116, -106, -115, -105, -114, -104, -113, -103, -112, -102, -111, -101, -110, -100, -109, -99, -18, 82, -17, 83, -16, 84, -15, 85, -14, 86, -13, 87})
                        }, { // placeholder6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
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
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_DEPTH,
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

const auto dummy_test_model_nhwc_quant8_signed_all_inputs_as_internal_3 = TestModelManager::get().add("space_to_depth_quant8_signed_nhwc_quant8_signed_all_inputs_as_internal_3", get_test_model_nhwc_quant8_signed_all_inputs_as_internal_3());

}  // namespace generated_tests::space_to_depth_quant8_signed

namespace generated_tests::space_to_depth_quant8_signed {

const TestModel& get_test_model_nchw_quant8_signed_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-118, -117, -116, -115, -114, -113, -112, -111, -110, -109, -18, -17, -16, -15, -14, -13, -108, -107, -106, -105, -104, -103, -102, -101, -100, -99, 82, 83, 84, 85, 86, 87})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 8, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-118, -116, -110, -18, -108, -106, -100, 82, -117, -115, -109, -17, -107, -105, -99, 83, -114, -112, -16, -14, -104, -102, 84, 86, -113, -111, -15, -13, -103, -101, 85, 87})
                        }},
                .operations = {{
                            .type = TestOperationType::SPACE_TO_DEPTH,
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

const auto dummy_test_model_nchw_quant8_signed_3 = TestModelManager::get().add("space_to_depth_quant8_signed_nchw_quant8_signed_3", get_test_model_nchw_quant8_signed_3());

}  // namespace generated_tests::space_to_depth_quant8_signed

namespace generated_tests::space_to_depth_quant8_signed {

const TestModel& get_test_model_nchw_quant8_signed_all_inputs_as_internal_3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 8, 2, 2},
                            .numberOfConsumers = 0,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-118, -116, -110, -18, -108, -106, -100, 82, -117, -115, -109, -17, -107, -105, -99, 83, -114, -112, -16, -14, -104, -102, 84, 86, -113, -111, -15, -13, -103, -101, 85, 87})
                        }, { // op12_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1, 2, 4, 4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-118, -117, -116, -115, -114, -113, -112, -111, -110, -109, -18, -17, -16, -15, -14, -13, -108, -107, -106, -105, -104, -103, -102, -101, -100, -99, 82, 83, 84, 85, 86, 87})
                        }, { // placeholder7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = -128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-128})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::SPACE_TO_DEPTH,
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

const auto dummy_test_model_nchw_quant8_signed_all_inputs_as_internal_3 = TestModelManager::get().add("space_to_depth_quant8_signed_nchw_quant8_signed_all_inputs_as_internal_3", get_test_model_nchw_quant8_signed_all_inputs_as_internal_3());

}  // namespace generated_tests::space_to_depth_quant8_signed

