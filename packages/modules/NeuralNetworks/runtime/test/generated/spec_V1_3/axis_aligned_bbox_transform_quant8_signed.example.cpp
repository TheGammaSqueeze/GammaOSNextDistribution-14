// Generated from axis_aligned_bbox_transform_quant8_signed.mod.py
// DO NOT EDIT
// clang-format off
#include "TestHarness.h"
using namespace test_helper;  // NOLINT(google-build-using-namespace)

namespace generated_tests::axis_aligned_bbox_transform_quant8_signed {

const TestModel& get_test_model_quant8_signed() {
    static TestModel model = {
        .main = {
                .operands = {{ // roi
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {5, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({800, 1200, 3200, 3440, 960, 480, 976, 488, 80, 160, 160, 400, 400, 960, 1200, 2000, 3200, 800, 8000, 16000})
                        }, { // bboxDeltas
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({4, 4, 2, 2, 6, -2, -4, 2, -10, 4, 4, -10, -2, -2, 50, 60, -10, -10, 20, 20, 10, 10, -30, -24, 4, 4, -60, -80, 20, -10, 6, 10, 6, -4, 22, -16, 2, 1, -10, -10})
                        }, { // batchSplit
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 1, 2, 2, 3})
                        }, { // imageInfo
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({4096, 4096, 1024, 2048, 2048, 2048, 8192, 4096})
                        }, { // out
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 0,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({1154, 1530, 3806, 4006, 1738, 858, 3702, 3334, 950, 483, 970, 488, 869, 403, 1064, 564, 0, 0, 189, 486, 151, 364, 169, 436, 940, 1678, 980, 1698, 1060, 103, 2048, 1817, 0, 1945, 4096, 8192, 4096, 4550, 4096, 8192})
                        }},
                .operations = {{
                            .type = TestOperationType::AXIS_ALIGNED_BBOX_TRANSFORM,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
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

const auto dummy_test_model_quant8_signed = TestModelManager::get().add("axis_aligned_bbox_transform_quant8_signed_quant8_signed", get_test_model_quant8_signed());

}  // namespace generated_tests::axis_aligned_bbox_transform_quant8_signed

namespace generated_tests::axis_aligned_bbox_transform_quant8_signed {

const TestModel& get_test_model_quant8_signed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // roi
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {5, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({800, 1200, 3200, 3440, 960, 480, 976, 488, 80, 160, 160, 400, 400, 960, 1200, 2000, 3200, 800, 8000, 16000})
                        }, { // bboxDeltas
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // batchSplit
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 1, 2, 2, 3})
                        }, { // imageInfo
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({4096, 4096, 1024, 2048, 2048, 2048, 8192, 4096})
                        }, { // out
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 0,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({1154, 1530, 3806, 4006, 1738, 858, 3702, 3334, 950, 483, 970, 488, 869, 403, 1064, 564, 0, 0, 189, 486, 151, 364, 169, 436, 940, 1678, 980, 1698, 1060, 103, 2048, 1817, 0, 1945, 4096, 8192, 4096, 4550, 4096, 8192})
                        }, { // bboxDeltas_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({4, 4, 2, 2, 6, -2, -4, 2, -10, 4, 4, -10, -2, -2, 50, 60, -10, -10, 20, 20, 10, 10, -30, -24, 4, 4, -60, -80, 20, -10, 6, 10, 6, -4, 22, -16, 2, 1, -10, -10})
                        }, { // placeholder
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.05f,
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
                            .inputs = {5, 6, 7},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::AXIS_ALIGNED_BBOX_TRANSFORM,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 2, 3, 5},
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

const auto dummy_test_model_quant8_signed_all_inputs_as_internal = TestModelManager::get().add("axis_aligned_bbox_transform_quant8_signed_quant8_signed_all_inputs_as_internal", get_test_model_quant8_signed_all_inputs_as_internal());

}  // namespace generated_tests::axis_aligned_bbox_transform_quant8_signed

namespace generated_tests::axis_aligned_bbox_transform_quant8_signed {

const TestModel& get_test_model_quant8_signed_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // roi1
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {5, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({800, 1200, 3200, 3440, 960, 480, 976, 488, 80, 160, 160, 400, 400, 960, 1200, 2000, 3200, 800, 8000, 16000})
                        }, { // bboxDeltas1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({4, 4, 2, 2, 6, -2, -4, 2, -10, 4, 4, -10, -2, -2, 50, 60, -10, -10, 20, 20, 10, 10, -30, -24, 4, 4, -60, -80, 20, -10, 6, 10, 6, -4, 22, -16, 2, 1, -10, -10})
                        }, { // batchSplit1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 2, 5, 5, 6})
                        }, { // imageInfo1
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {7, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({4096, 4096, 256, 256, 1024, 2048, 256, 256, 256, 256, 2048, 2048, 8192, 4096})
                        }, { // out1
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 0,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({1154, 1530, 3806, 4006, 1738, 858, 3702, 3334, 950, 483, 970, 488, 869, 403, 1064, 564, 0, 0, 189, 486, 151, 364, 169, 436, 940, 1678, 980, 1698, 1060, 103, 2048, 1817, 0, 1945, 4096, 8192, 4096, 4550, 4096, 8192})
                        }},
                .operations = {{
                            .type = TestOperationType::AXIS_ALIGNED_BBOX_TRANSFORM,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 1, 2, 3},
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

const auto dummy_test_model_quant8_signed_2 = TestModelManager::get().add("axis_aligned_bbox_transform_quant8_signed_quant8_signed_2", get_test_model_quant8_signed_2());

}  // namespace generated_tests::axis_aligned_bbox_transform_quant8_signed

namespace generated_tests::axis_aligned_bbox_transform_quant8_signed {

const TestModel& get_test_model_quant8_signed_all_inputs_as_internal_2() {
    static TestModel model = {
        .main = {
                .operands = {{ // roi1
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {5, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({800, 1200, 3200, 3440, 960, 480, 976, 488, 80, 160, 160, 400, 400, 960, 1200, 2000, 3200, 800, 8000, 16000})
                        }, { // bboxDeltas1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // batchSplit1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0, 2, 5, 5, 6})
                        }, { // imageInfo1
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {7, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({4096, 4096, 256, 256, 1024, 2048, 256, 256, 256, 256, 2048, 2048, 8192, 4096})
                        }, { // out1
                            .type = TestOperandType::TENSOR_QUANT16_ASYMM,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 0,
                            .scale = 0.125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint16_t>({1154, 1530, 3806, 4006, 1738, 858, 3702, 3334, 950, 483, 970, 488, 869, 403, 1064, 564, 0, 0, 189, 486, 151, 364, 169, 436, 940, 1678, 980, 1698, 1060, 103, 2048, 1817, 0, 1945, 4096, 8192, 4096, 4550, 4096, 8192})
                        }, { // bboxDeltas1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {5, 8},
                            .numberOfConsumers = 1,
                            .scale = 0.05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({4, 4, 2, 2, 6, -2, -4, 2, -10, 4, 4, -10, -2, -2, 50, 60, -10, -10, 20, 20, 10, 10, -30, -24, 4, 4, -60, -80, 20, -10, 6, 10, 6, -4, 22, -16, 2, 1, -10, -10})
                        }, { // placeholder1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.05f,
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
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {5, 6, 7},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::AXIS_ALIGNED_BBOX_TRANSFORM,
                            .inputs = {0, 1, 2, 3},
                            .outputs = {4}
                        }},
                .inputIndexes = {0, 2, 3, 5},
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

const auto dummy_test_model_quant8_signed_all_inputs_as_internal_2 = TestModelManager::get().add("axis_aligned_bbox_transform_quant8_signed_quant8_signed_all_inputs_as_internal_2", get_test_model_quant8_signed_all_inputs_as_internal_2());

}  // namespace generated_tests::axis_aligned_bbox_transform_quant8_signed

