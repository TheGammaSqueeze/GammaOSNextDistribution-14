// Generated from transpose_float_1_relaxed.mod.py
// DO NOT EDIT
// clang-format off
#include "TestHarness.h"
using namespace test_helper;  // NOLINT(google-build-using-namespace)

namespace generated_tests::transpose_float_1_relaxed {

const TestModel& get_test_model() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 4, 5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 35.0f, 36.0f, 37.0f, 38.0f, 39.0f, 40.0f, 41.0f, 42.0f, 43.0f, 44.0f, 45.0f, 46.0f, 47.0f, 48.0f, 49.0f, 50.0f, 51.0f, 52.0f, 53.0f, 54.0f, 55.0f, 56.0f, 57.0f, 58.0f, 59.0f, 60.0f, 61.0f, 62.0f, 63.0f, 64.0f, 65.0f, 66.0f, 67.0f, 68.0f, 69.0f, 70.0f, 71.0f, 72.0f, 73.0f, 74.0f, 75.0f, 76.0f, 77.0f, 78.0f, 79.0f, 80.0f, 81.0f, 82.0f, 83.0f, 84.0f, 85.0f, 86.0f, 87.0f, 88.0f, 89.0f, 90.0f, 91.0f, 92.0f, 93.0f, 94.0f, 95.0f, 96.0f, 97.0f, 98.0f, 99.0f, 100.0f, 101.0f, 102.0f, 103.0f, 104.0f, 105.0f, 106.0f, 107.0f, 108.0f, 109.0f, 110.0f, 111.0f, 112.0f, 113.0f, 114.0f, 115.0f, 116.0f, 117.0f, 118.0f, 119.0f})
                        }, { // perms
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2, 0, 1, 3})
                        }, { // output
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 2, 3, 5},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 40.0f, 41.0f, 42.0f, 43.0f, 44.0f, 60.0f, 61.0f, 62.0f, 63.0f, 64.0f, 80.0f, 81.0f, 82.0f, 83.0f, 84.0f, 100.0f, 101.0f, 102.0f, 103.0f, 104.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 45.0f, 46.0f, 47.0f, 48.0f, 49.0f, 65.0f, 66.0f, 67.0f, 68.0f, 69.0f, 85.0f, 86.0f, 87.0f, 88.0f, 89.0f, 105.0f, 106.0f, 107.0f, 108.0f, 109.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 50.0f, 51.0f, 52.0f, 53.0f, 54.0f, 70.0f, 71.0f, 72.0f, 73.0f, 74.0f, 90.0f, 91.0f, 92.0f, 93.0f, 94.0f, 110.0f, 111.0f, 112.0f, 113.0f, 114.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 35.0f, 36.0f, 37.0f, 38.0f, 39.0f, 55.0f, 56.0f, 57.0f, 58.0f, 59.0f, 75.0f, 76.0f, 77.0f, 78.0f, 79.0f, 95.0f, 96.0f, 97.0f, 98.0f, 99.0f, 115.0f, 116.0f, 117.0f, 118.0f, 119.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::TRANSPOSE,
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

const auto dummy_test_model = TestModelManager::get().add("transpose_float_1_relaxed", get_test_model());

}  // namespace generated_tests::transpose_float_1_relaxed

namespace generated_tests::transpose_float_1_relaxed {

const TestModel& get_test_model_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 4, 5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // perms
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2, 0, 1, 3})
                        }, { // output
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 2, 3, 5},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 40.0f, 41.0f, 42.0f, 43.0f, 44.0f, 60.0f, 61.0f, 62.0f, 63.0f, 64.0f, 80.0f, 81.0f, 82.0f, 83.0f, 84.0f, 100.0f, 101.0f, 102.0f, 103.0f, 104.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 45.0f, 46.0f, 47.0f, 48.0f, 49.0f, 65.0f, 66.0f, 67.0f, 68.0f, 69.0f, 85.0f, 86.0f, 87.0f, 88.0f, 89.0f, 105.0f, 106.0f, 107.0f, 108.0f, 109.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 50.0f, 51.0f, 52.0f, 53.0f, 54.0f, 70.0f, 71.0f, 72.0f, 73.0f, 74.0f, 90.0f, 91.0f, 92.0f, 93.0f, 94.0f, 110.0f, 111.0f, 112.0f, 113.0f, 114.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 35.0f, 36.0f, 37.0f, 38.0f, 39.0f, 55.0f, 56.0f, 57.0f, 58.0f, 59.0f, 75.0f, 76.0f, 77.0f, 78.0f, 79.0f, 95.0f, 96.0f, 97.0f, 98.0f, 99.0f, 115.0f, 116.0f, 117.0f, 118.0f, 119.0f})
                        }, { // input_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 4, 5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 35.0f, 36.0f, 37.0f, 38.0f, 39.0f, 40.0f, 41.0f, 42.0f, 43.0f, 44.0f, 45.0f, 46.0f, 47.0f, 48.0f, 49.0f, 50.0f, 51.0f, 52.0f, 53.0f, 54.0f, 55.0f, 56.0f, 57.0f, 58.0f, 59.0f, 60.0f, 61.0f, 62.0f, 63.0f, 64.0f, 65.0f, 66.0f, 67.0f, 68.0f, 69.0f, 70.0f, 71.0f, 72.0f, 73.0f, 74.0f, 75.0f, 76.0f, 77.0f, 78.0f, 79.0f, 80.0f, 81.0f, 82.0f, 83.0f, 84.0f, 85.0f, 86.0f, 87.0f, 88.0f, 89.0f, 90.0f, 91.0f, 92.0f, 93.0f, 94.0f, 95.0f, 96.0f, 97.0f, 98.0f, 99.0f, 100.0f, 101.0f, 102.0f, 103.0f, 104.0f, 105.0f, 106.0f, 107.0f, 108.0f, 109.0f, 110.0f, 111.0f, 112.0f, 113.0f, 114.0f, 115.0f, 116.0f, 117.0f, 118.0f, 119.0f})
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
                            .type = TestOperationType::TRANSPOSE,
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

const auto dummy_test_model_all_inputs_as_internal = TestModelManager::get().add("transpose_float_1_relaxed_all_inputs_as_internal", get_test_model_all_inputs_as_internal());

}  // namespace generated_tests::transpose_float_1_relaxed

namespace generated_tests::transpose_float_1_relaxed {

const TestModel& get_test_model_all_tensors_as_inputs() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 4, 5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 35.0f, 36.0f, 37.0f, 38.0f, 39.0f, 40.0f, 41.0f, 42.0f, 43.0f, 44.0f, 45.0f, 46.0f, 47.0f, 48.0f, 49.0f, 50.0f, 51.0f, 52.0f, 53.0f, 54.0f, 55.0f, 56.0f, 57.0f, 58.0f, 59.0f, 60.0f, 61.0f, 62.0f, 63.0f, 64.0f, 65.0f, 66.0f, 67.0f, 68.0f, 69.0f, 70.0f, 71.0f, 72.0f, 73.0f, 74.0f, 75.0f, 76.0f, 77.0f, 78.0f, 79.0f, 80.0f, 81.0f, 82.0f, 83.0f, 84.0f, 85.0f, 86.0f, 87.0f, 88.0f, 89.0f, 90.0f, 91.0f, 92.0f, 93.0f, 94.0f, 95.0f, 96.0f, 97.0f, 98.0f, 99.0f, 100.0f, 101.0f, 102.0f, 103.0f, 104.0f, 105.0f, 106.0f, 107.0f, 108.0f, 109.0f, 110.0f, 111.0f, 112.0f, 113.0f, 114.0f, 115.0f, 116.0f, 117.0f, 118.0f, 119.0f})
                        }, { // perms
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2, 0, 1, 3})
                        }, { // output
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 2, 3, 5},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 40.0f, 41.0f, 42.0f, 43.0f, 44.0f, 60.0f, 61.0f, 62.0f, 63.0f, 64.0f, 80.0f, 81.0f, 82.0f, 83.0f, 84.0f, 100.0f, 101.0f, 102.0f, 103.0f, 104.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 45.0f, 46.0f, 47.0f, 48.0f, 49.0f, 65.0f, 66.0f, 67.0f, 68.0f, 69.0f, 85.0f, 86.0f, 87.0f, 88.0f, 89.0f, 105.0f, 106.0f, 107.0f, 108.0f, 109.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 50.0f, 51.0f, 52.0f, 53.0f, 54.0f, 70.0f, 71.0f, 72.0f, 73.0f, 74.0f, 90.0f, 91.0f, 92.0f, 93.0f, 94.0f, 110.0f, 111.0f, 112.0f, 113.0f, 114.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 35.0f, 36.0f, 37.0f, 38.0f, 39.0f, 55.0f, 56.0f, 57.0f, 58.0f, 59.0f, 75.0f, 76.0f, 77.0f, 78.0f, 79.0f, 95.0f, 96.0f, 97.0f, 98.0f, 99.0f, 115.0f, 116.0f, 117.0f, 118.0f, 119.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::TRANSPOSE,
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

const auto dummy_test_model_all_tensors_as_inputs = TestModelManager::get().add("transpose_float_1_relaxed_all_tensors_as_inputs", get_test_model_all_tensors_as_inputs());

}  // namespace generated_tests::transpose_float_1_relaxed

namespace generated_tests::transpose_float_1_relaxed {

const TestModel& get_test_model_all_tensors_as_inputs_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 4, 5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // perms
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2, 0, 1, 3})
                        }, { // output
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 2, 3, 5},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 40.0f, 41.0f, 42.0f, 43.0f, 44.0f, 60.0f, 61.0f, 62.0f, 63.0f, 64.0f, 80.0f, 81.0f, 82.0f, 83.0f, 84.0f, 100.0f, 101.0f, 102.0f, 103.0f, 104.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 45.0f, 46.0f, 47.0f, 48.0f, 49.0f, 65.0f, 66.0f, 67.0f, 68.0f, 69.0f, 85.0f, 86.0f, 87.0f, 88.0f, 89.0f, 105.0f, 106.0f, 107.0f, 108.0f, 109.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 50.0f, 51.0f, 52.0f, 53.0f, 54.0f, 70.0f, 71.0f, 72.0f, 73.0f, 74.0f, 90.0f, 91.0f, 92.0f, 93.0f, 94.0f, 110.0f, 111.0f, 112.0f, 113.0f, 114.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 35.0f, 36.0f, 37.0f, 38.0f, 39.0f, 55.0f, 56.0f, 57.0f, 58.0f, 59.0f, 75.0f, 76.0f, 77.0f, 78.0f, 79.0f, 95.0f, 96.0f, 97.0f, 98.0f, 99.0f, 115.0f, 116.0f, 117.0f, 118.0f, 119.0f})
                        }, { // input_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 4, 5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 35.0f, 36.0f, 37.0f, 38.0f, 39.0f, 40.0f, 41.0f, 42.0f, 43.0f, 44.0f, 45.0f, 46.0f, 47.0f, 48.0f, 49.0f, 50.0f, 51.0f, 52.0f, 53.0f, 54.0f, 55.0f, 56.0f, 57.0f, 58.0f, 59.0f, 60.0f, 61.0f, 62.0f, 63.0f, 64.0f, 65.0f, 66.0f, 67.0f, 68.0f, 69.0f, 70.0f, 71.0f, 72.0f, 73.0f, 74.0f, 75.0f, 76.0f, 77.0f, 78.0f, 79.0f, 80.0f, 81.0f, 82.0f, 83.0f, 84.0f, 85.0f, 86.0f, 87.0f, 88.0f, 89.0f, 90.0f, 91.0f, 92.0f, 93.0f, 94.0f, 95.0f, 96.0f, 97.0f, 98.0f, 99.0f, 100.0f, 101.0f, 102.0f, 103.0f, 104.0f, 105.0f, 106.0f, 107.0f, 108.0f, 109.0f, 110.0f, 111.0f, 112.0f, 113.0f, 114.0f, 115.0f, 116.0f, 117.0f, 118.0f, 119.0f})
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
                            .inputs = {3, 4, 5},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::TRANSPOSE,
                            .inputs = {0, 1},
                            .outputs = {2}
                        }},
                .inputIndexes = {1, 3},
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

const auto dummy_test_model_all_tensors_as_inputs_all_inputs_as_internal = TestModelManager::get().add("transpose_float_1_relaxed_all_tensors_as_inputs_all_inputs_as_internal", get_test_model_all_tensors_as_inputs_all_inputs_as_internal());

}  // namespace generated_tests::transpose_float_1_relaxed

