// Generated from channel_shuffle.mod.py
// DO NOT EDIT
// clang-format off
#include "TestHarness.h"
using namespace test_helper;  // NOLINT(google-build-using-namespace)

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_dim4_axis0() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim4_axis0 = TestModelManager::get().add("channel_shuffle_dim4_axis0", get_test_model_dim4_axis0());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_dim4_axis0_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim4_axis0_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_dim4_axis0_all_inputs_as_internal", get_test_model_dim4_axis0_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_dim4_axis0_neg() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim4_axis0_neg = TestModelManager::get().add("channel_shuffle_dim4_axis0_neg", get_test_model_dim4_axis0_neg());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_dim4_axis0_neg_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim4_axis0_neg_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_dim4_axis0_neg_all_inputs_as_internal", get_test_model_dim4_axis0_neg_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_dim4_axis1() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 12, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim4_axis1 = TestModelManager::get().add("channel_shuffle_dim4_axis1", get_test_model_dim4_axis1());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_dim4_axis1_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 12, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim4_axis1_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_dim4_axis1_all_inputs_as_internal", get_test_model_dim4_axis1_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_dim4_axis1_neg() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 12, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim4_axis1_neg = TestModelManager::get().add("channel_shuffle_dim4_axis1_neg", get_test_model_dim4_axis1_neg());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_dim4_axis1_neg_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 12, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
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
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim4_axis1_neg_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_dim4_axis1_neg_all_inputs_as_internal", get_test_model_dim4_axis1_neg_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_dim4_axis2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 1.0f, 13.0f, 25.0f, 2.0f, 14.0f, 26.0f, 3.0f, 15.0f, 27.0f, 4.0f, 16.0f, 28.0f, 5.0f, 17.0f, 29.0f, 6.0f, 18.0f, 30.0f, 7.0f, 19.0f, 31.0f, 8.0f, 20.0f, 32.0f, 9.0f, 21.0f, 33.0f, 10.0f, 22.0f, 34.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 37.0f, 49.0f, 61.0f, 38.0f, 50.0f, 62.0f, 39.0f, 51.0f, 63.0f, 40.0f, 52.0f, 64.0f, 41.0f, 53.0f, 65.0f, 42.0f, 54.0f, 66.0f, 43.0f, 55.0f, 67.0f, 44.0f, 56.0f, 68.0f, 45.0f, 57.0f, 69.0f, 46.0f, 58.0f, 70.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 73.0f, 85.0f, 97.0f, 74.0f, 86.0f, 98.0f, 75.0f, 87.0f, 99.0f, 76.0f, 88.0f, 100.0f, 77.0f, 89.0f, 101.0f, 78.0f, 90.0f, 102.0f, 79.0f, 91.0f, 103.0f, 80.0f, 92.0f, 104.0f, 81.0f, 93.0f, 105.0f, 82.0f, 94.0f, 106.0f, 83.0f, 95.0f, 107.0f, 108.0f, 120.0f, 132.0f, 109.0f, 121.0f, 133.0f, 110.0f, 122.0f, 134.0f, 111.0f, 123.0f, 135.0f, 112.0f, 124.0f, 136.0f, 113.0f, 125.0f, 137.0f, 114.0f, 126.0f, 138.0f, 115.0f, 127.0f, 139.0f, 116.0f, 128.0f, 140.0f, 117.0f, 129.0f, 141.0f, 118.0f, 130.0f, 142.0f, 119.0f, 131.0f, 143.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 4.0f, 16.0f, 28.0f, 8.0f, 20.0f, 32.0f, 1.0f, 13.0f, 25.0f, 5.0f, 17.0f, 29.0f, 9.0f, 21.0f, 33.0f, 2.0f, 14.0f, 26.0f, 6.0f, 18.0f, 30.0f, 10.0f, 22.0f, 34.0f, 3.0f, 15.0f, 27.0f, 7.0f, 19.0f, 31.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 40.0f, 52.0f, 64.0f, 44.0f, 56.0f, 68.0f, 37.0f, 49.0f, 61.0f, 41.0f, 53.0f, 65.0f, 45.0f, 57.0f, 69.0f, 38.0f, 50.0f, 62.0f, 42.0f, 54.0f, 66.0f, 46.0f, 58.0f, 70.0f, 39.0f, 51.0f, 63.0f, 43.0f, 55.0f, 67.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 76.0f, 88.0f, 100.0f, 80.0f, 92.0f, 104.0f, 73.0f, 85.0f, 97.0f, 77.0f, 89.0f, 101.0f, 81.0f, 93.0f, 105.0f, 74.0f, 86.0f, 98.0f, 78.0f, 90.0f, 102.0f, 82.0f, 94.0f, 106.0f, 75.0f, 87.0f, 99.0f, 79.0f, 91.0f, 103.0f, 83.0f, 95.0f, 107.0f, 108.0f, 120.0f, 132.0f, 112.0f, 124.0f, 136.0f, 116.0f, 128.0f, 140.0f, 109.0f, 121.0f, 133.0f, 113.0f, 125.0f, 137.0f, 117.0f, 129.0f, 141.0f, 110.0f, 122.0f, 134.0f, 114.0f, 126.0f, 138.0f, 118.0f, 130.0f, 142.0f, 111.0f, 123.0f, 135.0f, 115.0f, 127.0f, 139.0f, 119.0f, 131.0f, 143.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim4_axis2 = TestModelManager::get().add("channel_shuffle_dim4_axis2", get_test_model_dim4_axis2());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_dim4_axis2_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 4.0f, 16.0f, 28.0f, 8.0f, 20.0f, 32.0f, 1.0f, 13.0f, 25.0f, 5.0f, 17.0f, 29.0f, 9.0f, 21.0f, 33.0f, 2.0f, 14.0f, 26.0f, 6.0f, 18.0f, 30.0f, 10.0f, 22.0f, 34.0f, 3.0f, 15.0f, 27.0f, 7.0f, 19.0f, 31.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 40.0f, 52.0f, 64.0f, 44.0f, 56.0f, 68.0f, 37.0f, 49.0f, 61.0f, 41.0f, 53.0f, 65.0f, 45.0f, 57.0f, 69.0f, 38.0f, 50.0f, 62.0f, 42.0f, 54.0f, 66.0f, 46.0f, 58.0f, 70.0f, 39.0f, 51.0f, 63.0f, 43.0f, 55.0f, 67.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 76.0f, 88.0f, 100.0f, 80.0f, 92.0f, 104.0f, 73.0f, 85.0f, 97.0f, 77.0f, 89.0f, 101.0f, 81.0f, 93.0f, 105.0f, 74.0f, 86.0f, 98.0f, 78.0f, 90.0f, 102.0f, 82.0f, 94.0f, 106.0f, 75.0f, 87.0f, 99.0f, 79.0f, 91.0f, 103.0f, 83.0f, 95.0f, 107.0f, 108.0f, 120.0f, 132.0f, 112.0f, 124.0f, 136.0f, 116.0f, 128.0f, 140.0f, 109.0f, 121.0f, 133.0f, 113.0f, 125.0f, 137.0f, 117.0f, 129.0f, 141.0f, 110.0f, 122.0f, 134.0f, 114.0f, 126.0f, 138.0f, 118.0f, 130.0f, 142.0f, 111.0f, 123.0f, 135.0f, 115.0f, 127.0f, 139.0f, 119.0f, 131.0f, 143.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 1.0f, 13.0f, 25.0f, 2.0f, 14.0f, 26.0f, 3.0f, 15.0f, 27.0f, 4.0f, 16.0f, 28.0f, 5.0f, 17.0f, 29.0f, 6.0f, 18.0f, 30.0f, 7.0f, 19.0f, 31.0f, 8.0f, 20.0f, 32.0f, 9.0f, 21.0f, 33.0f, 10.0f, 22.0f, 34.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 37.0f, 49.0f, 61.0f, 38.0f, 50.0f, 62.0f, 39.0f, 51.0f, 63.0f, 40.0f, 52.0f, 64.0f, 41.0f, 53.0f, 65.0f, 42.0f, 54.0f, 66.0f, 43.0f, 55.0f, 67.0f, 44.0f, 56.0f, 68.0f, 45.0f, 57.0f, 69.0f, 46.0f, 58.0f, 70.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 73.0f, 85.0f, 97.0f, 74.0f, 86.0f, 98.0f, 75.0f, 87.0f, 99.0f, 76.0f, 88.0f, 100.0f, 77.0f, 89.0f, 101.0f, 78.0f, 90.0f, 102.0f, 79.0f, 91.0f, 103.0f, 80.0f, 92.0f, 104.0f, 81.0f, 93.0f, 105.0f, 82.0f, 94.0f, 106.0f, 83.0f, 95.0f, 107.0f, 108.0f, 120.0f, 132.0f, 109.0f, 121.0f, 133.0f, 110.0f, 122.0f, 134.0f, 111.0f, 123.0f, 135.0f, 112.0f, 124.0f, 136.0f, 113.0f, 125.0f, 137.0f, 114.0f, 126.0f, 138.0f, 115.0f, 127.0f, 139.0f, 116.0f, 128.0f, 140.0f, 117.0f, 129.0f, 141.0f, 118.0f, 130.0f, 142.0f, 119.0f, 131.0f, 143.0f})
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
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim4_axis2_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_dim4_axis2_all_inputs_as_internal", get_test_model_dim4_axis2_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_dim4_axis2_neg() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 1.0f, 13.0f, 25.0f, 2.0f, 14.0f, 26.0f, 3.0f, 15.0f, 27.0f, 4.0f, 16.0f, 28.0f, 5.0f, 17.0f, 29.0f, 6.0f, 18.0f, 30.0f, 7.0f, 19.0f, 31.0f, 8.0f, 20.0f, 32.0f, 9.0f, 21.0f, 33.0f, 10.0f, 22.0f, 34.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 37.0f, 49.0f, 61.0f, 38.0f, 50.0f, 62.0f, 39.0f, 51.0f, 63.0f, 40.0f, 52.0f, 64.0f, 41.0f, 53.0f, 65.0f, 42.0f, 54.0f, 66.0f, 43.0f, 55.0f, 67.0f, 44.0f, 56.0f, 68.0f, 45.0f, 57.0f, 69.0f, 46.0f, 58.0f, 70.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 73.0f, 85.0f, 97.0f, 74.0f, 86.0f, 98.0f, 75.0f, 87.0f, 99.0f, 76.0f, 88.0f, 100.0f, 77.0f, 89.0f, 101.0f, 78.0f, 90.0f, 102.0f, 79.0f, 91.0f, 103.0f, 80.0f, 92.0f, 104.0f, 81.0f, 93.0f, 105.0f, 82.0f, 94.0f, 106.0f, 83.0f, 95.0f, 107.0f, 108.0f, 120.0f, 132.0f, 109.0f, 121.0f, 133.0f, 110.0f, 122.0f, 134.0f, 111.0f, 123.0f, 135.0f, 112.0f, 124.0f, 136.0f, 113.0f, 125.0f, 137.0f, 114.0f, 126.0f, 138.0f, 115.0f, 127.0f, 139.0f, 116.0f, 128.0f, 140.0f, 117.0f, 129.0f, 141.0f, 118.0f, 130.0f, 142.0f, 119.0f, 131.0f, 143.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 4.0f, 16.0f, 28.0f, 8.0f, 20.0f, 32.0f, 1.0f, 13.0f, 25.0f, 5.0f, 17.0f, 29.0f, 9.0f, 21.0f, 33.0f, 2.0f, 14.0f, 26.0f, 6.0f, 18.0f, 30.0f, 10.0f, 22.0f, 34.0f, 3.0f, 15.0f, 27.0f, 7.0f, 19.0f, 31.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 40.0f, 52.0f, 64.0f, 44.0f, 56.0f, 68.0f, 37.0f, 49.0f, 61.0f, 41.0f, 53.0f, 65.0f, 45.0f, 57.0f, 69.0f, 38.0f, 50.0f, 62.0f, 42.0f, 54.0f, 66.0f, 46.0f, 58.0f, 70.0f, 39.0f, 51.0f, 63.0f, 43.0f, 55.0f, 67.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 76.0f, 88.0f, 100.0f, 80.0f, 92.0f, 104.0f, 73.0f, 85.0f, 97.0f, 77.0f, 89.0f, 101.0f, 81.0f, 93.0f, 105.0f, 74.0f, 86.0f, 98.0f, 78.0f, 90.0f, 102.0f, 82.0f, 94.0f, 106.0f, 75.0f, 87.0f, 99.0f, 79.0f, 91.0f, 103.0f, 83.0f, 95.0f, 107.0f, 108.0f, 120.0f, 132.0f, 112.0f, 124.0f, 136.0f, 116.0f, 128.0f, 140.0f, 109.0f, 121.0f, 133.0f, 113.0f, 125.0f, 137.0f, 117.0f, 129.0f, 141.0f, 110.0f, 122.0f, 134.0f, 114.0f, 126.0f, 138.0f, 118.0f, 130.0f, 142.0f, 111.0f, 123.0f, 135.0f, 115.0f, 127.0f, 139.0f, 119.0f, 131.0f, 143.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim4_axis2_neg = TestModelManager::get().add("channel_shuffle_dim4_axis2_neg", get_test_model_dim4_axis2_neg());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_dim4_axis2_neg_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 4.0f, 16.0f, 28.0f, 8.0f, 20.0f, 32.0f, 1.0f, 13.0f, 25.0f, 5.0f, 17.0f, 29.0f, 9.0f, 21.0f, 33.0f, 2.0f, 14.0f, 26.0f, 6.0f, 18.0f, 30.0f, 10.0f, 22.0f, 34.0f, 3.0f, 15.0f, 27.0f, 7.0f, 19.0f, 31.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 40.0f, 52.0f, 64.0f, 44.0f, 56.0f, 68.0f, 37.0f, 49.0f, 61.0f, 41.0f, 53.0f, 65.0f, 45.0f, 57.0f, 69.0f, 38.0f, 50.0f, 62.0f, 42.0f, 54.0f, 66.0f, 46.0f, 58.0f, 70.0f, 39.0f, 51.0f, 63.0f, 43.0f, 55.0f, 67.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 76.0f, 88.0f, 100.0f, 80.0f, 92.0f, 104.0f, 73.0f, 85.0f, 97.0f, 77.0f, 89.0f, 101.0f, 81.0f, 93.0f, 105.0f, 74.0f, 86.0f, 98.0f, 78.0f, 90.0f, 102.0f, 82.0f, 94.0f, 106.0f, 75.0f, 87.0f, 99.0f, 79.0f, 91.0f, 103.0f, 83.0f, 95.0f, 107.0f, 108.0f, 120.0f, 132.0f, 112.0f, 124.0f, 136.0f, 116.0f, 128.0f, 140.0f, 109.0f, 121.0f, 133.0f, 113.0f, 125.0f, 137.0f, 117.0f, 129.0f, 141.0f, 110.0f, 122.0f, 134.0f, 114.0f, 126.0f, 138.0f, 118.0f, 130.0f, 142.0f, 111.0f, 123.0f, 135.0f, 115.0f, 127.0f, 139.0f, 119.0f, 131.0f, 143.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 1.0f, 13.0f, 25.0f, 2.0f, 14.0f, 26.0f, 3.0f, 15.0f, 27.0f, 4.0f, 16.0f, 28.0f, 5.0f, 17.0f, 29.0f, 6.0f, 18.0f, 30.0f, 7.0f, 19.0f, 31.0f, 8.0f, 20.0f, 32.0f, 9.0f, 21.0f, 33.0f, 10.0f, 22.0f, 34.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 37.0f, 49.0f, 61.0f, 38.0f, 50.0f, 62.0f, 39.0f, 51.0f, 63.0f, 40.0f, 52.0f, 64.0f, 41.0f, 53.0f, 65.0f, 42.0f, 54.0f, 66.0f, 43.0f, 55.0f, 67.0f, 44.0f, 56.0f, 68.0f, 45.0f, 57.0f, 69.0f, 46.0f, 58.0f, 70.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 73.0f, 85.0f, 97.0f, 74.0f, 86.0f, 98.0f, 75.0f, 87.0f, 99.0f, 76.0f, 88.0f, 100.0f, 77.0f, 89.0f, 101.0f, 78.0f, 90.0f, 102.0f, 79.0f, 91.0f, 103.0f, 80.0f, 92.0f, 104.0f, 81.0f, 93.0f, 105.0f, 82.0f, 94.0f, 106.0f, 83.0f, 95.0f, 107.0f, 108.0f, 120.0f, 132.0f, 109.0f, 121.0f, 133.0f, 110.0f, 122.0f, 134.0f, 111.0f, 123.0f, 135.0f, 112.0f, 124.0f, 136.0f, 113.0f, 125.0f, 137.0f, 114.0f, 126.0f, 138.0f, 115.0f, 127.0f, 139.0f, 116.0f, 128.0f, 140.0f, 117.0f, 129.0f, 141.0f, 118.0f, 130.0f, 142.0f, 119.0f, 131.0f, 143.0f})
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
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim4_axis2_neg_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_dim4_axis2_neg_all_inputs_as_internal", get_test_model_dim4_axis2_neg_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_dim4_axis3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 35.0f, 36.0f, 37.0f, 38.0f, 39.0f, 40.0f, 41.0f, 42.0f, 43.0f, 44.0f, 45.0f, 46.0f, 47.0f, 48.0f, 49.0f, 50.0f, 51.0f, 52.0f, 53.0f, 54.0f, 55.0f, 56.0f, 57.0f, 58.0f, 59.0f, 60.0f, 61.0f, 62.0f, 63.0f, 64.0f, 65.0f, 66.0f, 67.0f, 68.0f, 69.0f, 70.0f, 71.0f, 72.0f, 73.0f, 74.0f, 75.0f, 76.0f, 77.0f, 78.0f, 79.0f, 80.0f, 81.0f, 82.0f, 83.0f, 84.0f, 85.0f, 86.0f, 87.0f, 88.0f, 89.0f, 90.0f, 91.0f, 92.0f, 93.0f, 94.0f, 95.0f, 96.0f, 97.0f, 98.0f, 99.0f, 100.0f, 101.0f, 102.0f, 103.0f, 104.0f, 105.0f, 106.0f, 107.0f, 108.0f, 109.0f, 110.0f, 111.0f, 112.0f, 113.0f, 114.0f, 115.0f, 116.0f, 117.0f, 118.0f, 119.0f, 120.0f, 121.0f, 122.0f, 123.0f, 124.0f, 125.0f, 126.0f, 127.0f, 128.0f, 129.0f, 130.0f, 131.0f, 132.0f, 133.0f, 134.0f, 135.0f, 136.0f, 137.0f, 138.0f, 139.0f, 140.0f, 141.0f, 142.0f, 143.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f, 12.0f, 16.0f, 20.0f, 13.0f, 17.0f, 21.0f, 14.0f, 18.0f, 22.0f, 15.0f, 19.0f, 23.0f, 24.0f, 28.0f, 32.0f, 25.0f, 29.0f, 33.0f, 26.0f, 30.0f, 34.0f, 27.0f, 31.0f, 35.0f, 36.0f, 40.0f, 44.0f, 37.0f, 41.0f, 45.0f, 38.0f, 42.0f, 46.0f, 39.0f, 43.0f, 47.0f, 48.0f, 52.0f, 56.0f, 49.0f, 53.0f, 57.0f, 50.0f, 54.0f, 58.0f, 51.0f, 55.0f, 59.0f, 60.0f, 64.0f, 68.0f, 61.0f, 65.0f, 69.0f, 62.0f, 66.0f, 70.0f, 63.0f, 67.0f, 71.0f, 72.0f, 76.0f, 80.0f, 73.0f, 77.0f, 81.0f, 74.0f, 78.0f, 82.0f, 75.0f, 79.0f, 83.0f, 84.0f, 88.0f, 92.0f, 85.0f, 89.0f, 93.0f, 86.0f, 90.0f, 94.0f, 87.0f, 91.0f, 95.0f, 96.0f, 100.0f, 104.0f, 97.0f, 101.0f, 105.0f, 98.0f, 102.0f, 106.0f, 99.0f, 103.0f, 107.0f, 108.0f, 112.0f, 116.0f, 109.0f, 113.0f, 117.0f, 110.0f, 114.0f, 118.0f, 111.0f, 115.0f, 119.0f, 120.0f, 124.0f, 128.0f, 121.0f, 125.0f, 129.0f, 122.0f, 126.0f, 130.0f, 123.0f, 127.0f, 131.0f, 132.0f, 136.0f, 140.0f, 133.0f, 137.0f, 141.0f, 134.0f, 138.0f, 142.0f, 135.0f, 139.0f, 143.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim4_axis3 = TestModelManager::get().add("channel_shuffle_dim4_axis3", get_test_model_dim4_axis3());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_dim4_axis3_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f, 12.0f, 16.0f, 20.0f, 13.0f, 17.0f, 21.0f, 14.0f, 18.0f, 22.0f, 15.0f, 19.0f, 23.0f, 24.0f, 28.0f, 32.0f, 25.0f, 29.0f, 33.0f, 26.0f, 30.0f, 34.0f, 27.0f, 31.0f, 35.0f, 36.0f, 40.0f, 44.0f, 37.0f, 41.0f, 45.0f, 38.0f, 42.0f, 46.0f, 39.0f, 43.0f, 47.0f, 48.0f, 52.0f, 56.0f, 49.0f, 53.0f, 57.0f, 50.0f, 54.0f, 58.0f, 51.0f, 55.0f, 59.0f, 60.0f, 64.0f, 68.0f, 61.0f, 65.0f, 69.0f, 62.0f, 66.0f, 70.0f, 63.0f, 67.0f, 71.0f, 72.0f, 76.0f, 80.0f, 73.0f, 77.0f, 81.0f, 74.0f, 78.0f, 82.0f, 75.0f, 79.0f, 83.0f, 84.0f, 88.0f, 92.0f, 85.0f, 89.0f, 93.0f, 86.0f, 90.0f, 94.0f, 87.0f, 91.0f, 95.0f, 96.0f, 100.0f, 104.0f, 97.0f, 101.0f, 105.0f, 98.0f, 102.0f, 106.0f, 99.0f, 103.0f, 107.0f, 108.0f, 112.0f, 116.0f, 109.0f, 113.0f, 117.0f, 110.0f, 114.0f, 118.0f, 111.0f, 115.0f, 119.0f, 120.0f, 124.0f, 128.0f, 121.0f, 125.0f, 129.0f, 122.0f, 126.0f, 130.0f, 123.0f, 127.0f, 131.0f, 132.0f, 136.0f, 140.0f, 133.0f, 137.0f, 141.0f, 134.0f, 138.0f, 142.0f, 135.0f, 139.0f, 143.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 35.0f, 36.0f, 37.0f, 38.0f, 39.0f, 40.0f, 41.0f, 42.0f, 43.0f, 44.0f, 45.0f, 46.0f, 47.0f, 48.0f, 49.0f, 50.0f, 51.0f, 52.0f, 53.0f, 54.0f, 55.0f, 56.0f, 57.0f, 58.0f, 59.0f, 60.0f, 61.0f, 62.0f, 63.0f, 64.0f, 65.0f, 66.0f, 67.0f, 68.0f, 69.0f, 70.0f, 71.0f, 72.0f, 73.0f, 74.0f, 75.0f, 76.0f, 77.0f, 78.0f, 79.0f, 80.0f, 81.0f, 82.0f, 83.0f, 84.0f, 85.0f, 86.0f, 87.0f, 88.0f, 89.0f, 90.0f, 91.0f, 92.0f, 93.0f, 94.0f, 95.0f, 96.0f, 97.0f, 98.0f, 99.0f, 100.0f, 101.0f, 102.0f, 103.0f, 104.0f, 105.0f, 106.0f, 107.0f, 108.0f, 109.0f, 110.0f, 111.0f, 112.0f, 113.0f, 114.0f, 115.0f, 116.0f, 117.0f, 118.0f, 119.0f, 120.0f, 121.0f, 122.0f, 123.0f, 124.0f, 125.0f, 126.0f, 127.0f, 128.0f, 129.0f, 130.0f, 131.0f, 132.0f, 133.0f, 134.0f, 135.0f, 136.0f, 137.0f, 138.0f, 139.0f, 140.0f, 141.0f, 142.0f, 143.0f})
                        }, { // placeholder6
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim4_axis3_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_dim4_axis3_all_inputs_as_internal", get_test_model_dim4_axis3_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_dim4_axis3_neg() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 35.0f, 36.0f, 37.0f, 38.0f, 39.0f, 40.0f, 41.0f, 42.0f, 43.0f, 44.0f, 45.0f, 46.0f, 47.0f, 48.0f, 49.0f, 50.0f, 51.0f, 52.0f, 53.0f, 54.0f, 55.0f, 56.0f, 57.0f, 58.0f, 59.0f, 60.0f, 61.0f, 62.0f, 63.0f, 64.0f, 65.0f, 66.0f, 67.0f, 68.0f, 69.0f, 70.0f, 71.0f, 72.0f, 73.0f, 74.0f, 75.0f, 76.0f, 77.0f, 78.0f, 79.0f, 80.0f, 81.0f, 82.0f, 83.0f, 84.0f, 85.0f, 86.0f, 87.0f, 88.0f, 89.0f, 90.0f, 91.0f, 92.0f, 93.0f, 94.0f, 95.0f, 96.0f, 97.0f, 98.0f, 99.0f, 100.0f, 101.0f, 102.0f, 103.0f, 104.0f, 105.0f, 106.0f, 107.0f, 108.0f, 109.0f, 110.0f, 111.0f, 112.0f, 113.0f, 114.0f, 115.0f, 116.0f, 117.0f, 118.0f, 119.0f, 120.0f, 121.0f, 122.0f, 123.0f, 124.0f, 125.0f, 126.0f, 127.0f, 128.0f, 129.0f, 130.0f, 131.0f, 132.0f, 133.0f, 134.0f, 135.0f, 136.0f, 137.0f, 138.0f, 139.0f, 140.0f, 141.0f, 142.0f, 143.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f, 12.0f, 16.0f, 20.0f, 13.0f, 17.0f, 21.0f, 14.0f, 18.0f, 22.0f, 15.0f, 19.0f, 23.0f, 24.0f, 28.0f, 32.0f, 25.0f, 29.0f, 33.0f, 26.0f, 30.0f, 34.0f, 27.0f, 31.0f, 35.0f, 36.0f, 40.0f, 44.0f, 37.0f, 41.0f, 45.0f, 38.0f, 42.0f, 46.0f, 39.0f, 43.0f, 47.0f, 48.0f, 52.0f, 56.0f, 49.0f, 53.0f, 57.0f, 50.0f, 54.0f, 58.0f, 51.0f, 55.0f, 59.0f, 60.0f, 64.0f, 68.0f, 61.0f, 65.0f, 69.0f, 62.0f, 66.0f, 70.0f, 63.0f, 67.0f, 71.0f, 72.0f, 76.0f, 80.0f, 73.0f, 77.0f, 81.0f, 74.0f, 78.0f, 82.0f, 75.0f, 79.0f, 83.0f, 84.0f, 88.0f, 92.0f, 85.0f, 89.0f, 93.0f, 86.0f, 90.0f, 94.0f, 87.0f, 91.0f, 95.0f, 96.0f, 100.0f, 104.0f, 97.0f, 101.0f, 105.0f, 98.0f, 102.0f, 106.0f, 99.0f, 103.0f, 107.0f, 108.0f, 112.0f, 116.0f, 109.0f, 113.0f, 117.0f, 110.0f, 114.0f, 118.0f, 111.0f, 115.0f, 119.0f, 120.0f, 124.0f, 128.0f, 121.0f, 125.0f, 129.0f, 122.0f, 126.0f, 130.0f, 123.0f, 127.0f, 131.0f, 132.0f, 136.0f, 140.0f, 133.0f, 137.0f, 141.0f, 134.0f, 138.0f, 142.0f, 135.0f, 139.0f, 143.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim4_axis3_neg = TestModelManager::get().add("channel_shuffle_dim4_axis3_neg", get_test_model_dim4_axis3_neg());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_dim4_axis3_neg_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f, 12.0f, 16.0f, 20.0f, 13.0f, 17.0f, 21.0f, 14.0f, 18.0f, 22.0f, 15.0f, 19.0f, 23.0f, 24.0f, 28.0f, 32.0f, 25.0f, 29.0f, 33.0f, 26.0f, 30.0f, 34.0f, 27.0f, 31.0f, 35.0f, 36.0f, 40.0f, 44.0f, 37.0f, 41.0f, 45.0f, 38.0f, 42.0f, 46.0f, 39.0f, 43.0f, 47.0f, 48.0f, 52.0f, 56.0f, 49.0f, 53.0f, 57.0f, 50.0f, 54.0f, 58.0f, 51.0f, 55.0f, 59.0f, 60.0f, 64.0f, 68.0f, 61.0f, 65.0f, 69.0f, 62.0f, 66.0f, 70.0f, 63.0f, 67.0f, 71.0f, 72.0f, 76.0f, 80.0f, 73.0f, 77.0f, 81.0f, 74.0f, 78.0f, 82.0f, 75.0f, 79.0f, 83.0f, 84.0f, 88.0f, 92.0f, 85.0f, 89.0f, 93.0f, 86.0f, 90.0f, 94.0f, 87.0f, 91.0f, 95.0f, 96.0f, 100.0f, 104.0f, 97.0f, 101.0f, 105.0f, 98.0f, 102.0f, 106.0f, 99.0f, 103.0f, 107.0f, 108.0f, 112.0f, 116.0f, 109.0f, 113.0f, 117.0f, 110.0f, 114.0f, 118.0f, 111.0f, 115.0f, 119.0f, 120.0f, 124.0f, 128.0f, 121.0f, 125.0f, 129.0f, 122.0f, 126.0f, 130.0f, 123.0f, 127.0f, 131.0f, 132.0f, 136.0f, 140.0f, 133.0f, 137.0f, 141.0f, 134.0f, 138.0f, 142.0f, 135.0f, 139.0f, 143.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 35.0f, 36.0f, 37.0f, 38.0f, 39.0f, 40.0f, 41.0f, 42.0f, 43.0f, 44.0f, 45.0f, 46.0f, 47.0f, 48.0f, 49.0f, 50.0f, 51.0f, 52.0f, 53.0f, 54.0f, 55.0f, 56.0f, 57.0f, 58.0f, 59.0f, 60.0f, 61.0f, 62.0f, 63.0f, 64.0f, 65.0f, 66.0f, 67.0f, 68.0f, 69.0f, 70.0f, 71.0f, 72.0f, 73.0f, 74.0f, 75.0f, 76.0f, 77.0f, 78.0f, 79.0f, 80.0f, 81.0f, 82.0f, 83.0f, 84.0f, 85.0f, 86.0f, 87.0f, 88.0f, 89.0f, 90.0f, 91.0f, 92.0f, 93.0f, 94.0f, 95.0f, 96.0f, 97.0f, 98.0f, 99.0f, 100.0f, 101.0f, 102.0f, 103.0f, 104.0f, 105.0f, 106.0f, 107.0f, 108.0f, 109.0f, 110.0f, 111.0f, 112.0f, 113.0f, 114.0f, 115.0f, 116.0f, 117.0f, 118.0f, 119.0f, 120.0f, 121.0f, 122.0f, 123.0f, 124.0f, 125.0f, 126.0f, 127.0f, 128.0f, 129.0f, 130.0f, 131.0f, 132.0f, 133.0f, 134.0f, 135.0f, 136.0f, 137.0f, 138.0f, 139.0f, 140.0f, 141.0f, 142.0f, 143.0f})
                        }, { // placeholder7
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim4_axis3_neg_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_dim4_axis3_neg_all_inputs_as_internal", get_test_model_dim4_axis3_neg_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_dim3_axis0() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim3_axis0 = TestModelManager::get().add("channel_shuffle_dim3_axis0", get_test_model_dim3_axis0());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_dim3_axis0_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f})
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
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim3_axis0_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_dim3_axis0_all_inputs_as_internal", get_test_model_dim3_axis0_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_dim3_axis0_neg() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim3_axis0_neg = TestModelManager::get().add("channel_shuffle_dim3_axis0_neg", get_test_model_dim3_axis0_neg());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_dim3_axis0_neg_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f})
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
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim3_axis0_neg_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_dim3_axis0_neg_all_inputs_as_internal", get_test_model_dim3_axis0_neg_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_dim3_axis1() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 1.0f, 13.0f, 25.0f, 2.0f, 14.0f, 26.0f, 3.0f, 15.0f, 27.0f, 4.0f, 16.0f, 28.0f, 5.0f, 17.0f, 29.0f, 6.0f, 18.0f, 30.0f, 7.0f, 19.0f, 31.0f, 8.0f, 20.0f, 32.0f, 9.0f, 21.0f, 33.0f, 10.0f, 22.0f, 34.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 37.0f, 49.0f, 61.0f, 38.0f, 50.0f, 62.0f, 39.0f, 51.0f, 63.0f, 40.0f, 52.0f, 64.0f, 41.0f, 53.0f, 65.0f, 42.0f, 54.0f, 66.0f, 43.0f, 55.0f, 67.0f, 44.0f, 56.0f, 68.0f, 45.0f, 57.0f, 69.0f, 46.0f, 58.0f, 70.0f, 47.0f, 59.0f, 71.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 4.0f, 16.0f, 28.0f, 8.0f, 20.0f, 32.0f, 1.0f, 13.0f, 25.0f, 5.0f, 17.0f, 29.0f, 9.0f, 21.0f, 33.0f, 2.0f, 14.0f, 26.0f, 6.0f, 18.0f, 30.0f, 10.0f, 22.0f, 34.0f, 3.0f, 15.0f, 27.0f, 7.0f, 19.0f, 31.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 40.0f, 52.0f, 64.0f, 44.0f, 56.0f, 68.0f, 37.0f, 49.0f, 61.0f, 41.0f, 53.0f, 65.0f, 45.0f, 57.0f, 69.0f, 38.0f, 50.0f, 62.0f, 42.0f, 54.0f, 66.0f, 46.0f, 58.0f, 70.0f, 39.0f, 51.0f, 63.0f, 43.0f, 55.0f, 67.0f, 47.0f, 59.0f, 71.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim3_axis1 = TestModelManager::get().add("channel_shuffle_dim3_axis1", get_test_model_dim3_axis1());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_dim3_axis1_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 4.0f, 16.0f, 28.0f, 8.0f, 20.0f, 32.0f, 1.0f, 13.0f, 25.0f, 5.0f, 17.0f, 29.0f, 9.0f, 21.0f, 33.0f, 2.0f, 14.0f, 26.0f, 6.0f, 18.0f, 30.0f, 10.0f, 22.0f, 34.0f, 3.0f, 15.0f, 27.0f, 7.0f, 19.0f, 31.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 40.0f, 52.0f, 64.0f, 44.0f, 56.0f, 68.0f, 37.0f, 49.0f, 61.0f, 41.0f, 53.0f, 65.0f, 45.0f, 57.0f, 69.0f, 38.0f, 50.0f, 62.0f, 42.0f, 54.0f, 66.0f, 46.0f, 58.0f, 70.0f, 39.0f, 51.0f, 63.0f, 43.0f, 55.0f, 67.0f, 47.0f, 59.0f, 71.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 1.0f, 13.0f, 25.0f, 2.0f, 14.0f, 26.0f, 3.0f, 15.0f, 27.0f, 4.0f, 16.0f, 28.0f, 5.0f, 17.0f, 29.0f, 6.0f, 18.0f, 30.0f, 7.0f, 19.0f, 31.0f, 8.0f, 20.0f, 32.0f, 9.0f, 21.0f, 33.0f, 10.0f, 22.0f, 34.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 37.0f, 49.0f, 61.0f, 38.0f, 50.0f, 62.0f, 39.0f, 51.0f, 63.0f, 40.0f, 52.0f, 64.0f, 41.0f, 53.0f, 65.0f, 42.0f, 54.0f, 66.0f, 43.0f, 55.0f, 67.0f, 44.0f, 56.0f, 68.0f, 45.0f, 57.0f, 69.0f, 46.0f, 58.0f, 70.0f, 47.0f, 59.0f, 71.0f})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim3_axis1_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_dim3_axis1_all_inputs_as_internal", get_test_model_dim3_axis1_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_dim3_axis1_neg() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 1.0f, 13.0f, 25.0f, 2.0f, 14.0f, 26.0f, 3.0f, 15.0f, 27.0f, 4.0f, 16.0f, 28.0f, 5.0f, 17.0f, 29.0f, 6.0f, 18.0f, 30.0f, 7.0f, 19.0f, 31.0f, 8.0f, 20.0f, 32.0f, 9.0f, 21.0f, 33.0f, 10.0f, 22.0f, 34.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 37.0f, 49.0f, 61.0f, 38.0f, 50.0f, 62.0f, 39.0f, 51.0f, 63.0f, 40.0f, 52.0f, 64.0f, 41.0f, 53.0f, 65.0f, 42.0f, 54.0f, 66.0f, 43.0f, 55.0f, 67.0f, 44.0f, 56.0f, 68.0f, 45.0f, 57.0f, 69.0f, 46.0f, 58.0f, 70.0f, 47.0f, 59.0f, 71.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 4.0f, 16.0f, 28.0f, 8.0f, 20.0f, 32.0f, 1.0f, 13.0f, 25.0f, 5.0f, 17.0f, 29.0f, 9.0f, 21.0f, 33.0f, 2.0f, 14.0f, 26.0f, 6.0f, 18.0f, 30.0f, 10.0f, 22.0f, 34.0f, 3.0f, 15.0f, 27.0f, 7.0f, 19.0f, 31.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 40.0f, 52.0f, 64.0f, 44.0f, 56.0f, 68.0f, 37.0f, 49.0f, 61.0f, 41.0f, 53.0f, 65.0f, 45.0f, 57.0f, 69.0f, 38.0f, 50.0f, 62.0f, 42.0f, 54.0f, 66.0f, 46.0f, 58.0f, 70.0f, 39.0f, 51.0f, 63.0f, 43.0f, 55.0f, 67.0f, 47.0f, 59.0f, 71.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim3_axis1_neg = TestModelManager::get().add("channel_shuffle_dim3_axis1_neg", get_test_model_dim3_axis1_neg());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_dim3_axis1_neg_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 4.0f, 16.0f, 28.0f, 8.0f, 20.0f, 32.0f, 1.0f, 13.0f, 25.0f, 5.0f, 17.0f, 29.0f, 9.0f, 21.0f, 33.0f, 2.0f, 14.0f, 26.0f, 6.0f, 18.0f, 30.0f, 10.0f, 22.0f, 34.0f, 3.0f, 15.0f, 27.0f, 7.0f, 19.0f, 31.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 40.0f, 52.0f, 64.0f, 44.0f, 56.0f, 68.0f, 37.0f, 49.0f, 61.0f, 41.0f, 53.0f, 65.0f, 45.0f, 57.0f, 69.0f, 38.0f, 50.0f, 62.0f, 42.0f, 54.0f, 66.0f, 46.0f, 58.0f, 70.0f, 39.0f, 51.0f, 63.0f, 43.0f, 55.0f, 67.0f, 47.0f, 59.0f, 71.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 1.0f, 13.0f, 25.0f, 2.0f, 14.0f, 26.0f, 3.0f, 15.0f, 27.0f, 4.0f, 16.0f, 28.0f, 5.0f, 17.0f, 29.0f, 6.0f, 18.0f, 30.0f, 7.0f, 19.0f, 31.0f, 8.0f, 20.0f, 32.0f, 9.0f, 21.0f, 33.0f, 10.0f, 22.0f, 34.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 37.0f, 49.0f, 61.0f, 38.0f, 50.0f, 62.0f, 39.0f, 51.0f, 63.0f, 40.0f, 52.0f, 64.0f, 41.0f, 53.0f, 65.0f, 42.0f, 54.0f, 66.0f, 43.0f, 55.0f, 67.0f, 44.0f, 56.0f, 68.0f, 45.0f, 57.0f, 69.0f, 46.0f, 58.0f, 70.0f, 47.0f, 59.0f, 71.0f})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim3_axis1_neg_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_dim3_axis1_neg_all_inputs_as_internal", get_test_model_dim3_axis1_neg_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_dim3_axis2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 35.0f, 36.0f, 37.0f, 38.0f, 39.0f, 40.0f, 41.0f, 42.0f, 43.0f, 44.0f, 45.0f, 46.0f, 47.0f, 48.0f, 49.0f, 50.0f, 51.0f, 52.0f, 53.0f, 54.0f, 55.0f, 56.0f, 57.0f, 58.0f, 59.0f, 60.0f, 61.0f, 62.0f, 63.0f, 64.0f, 65.0f, 66.0f, 67.0f, 68.0f, 69.0f, 70.0f, 71.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f, 12.0f, 16.0f, 20.0f, 13.0f, 17.0f, 21.0f, 14.0f, 18.0f, 22.0f, 15.0f, 19.0f, 23.0f, 24.0f, 28.0f, 32.0f, 25.0f, 29.0f, 33.0f, 26.0f, 30.0f, 34.0f, 27.0f, 31.0f, 35.0f, 36.0f, 40.0f, 44.0f, 37.0f, 41.0f, 45.0f, 38.0f, 42.0f, 46.0f, 39.0f, 43.0f, 47.0f, 48.0f, 52.0f, 56.0f, 49.0f, 53.0f, 57.0f, 50.0f, 54.0f, 58.0f, 51.0f, 55.0f, 59.0f, 60.0f, 64.0f, 68.0f, 61.0f, 65.0f, 69.0f, 62.0f, 66.0f, 70.0f, 63.0f, 67.0f, 71.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim3_axis2 = TestModelManager::get().add("channel_shuffle_dim3_axis2", get_test_model_dim3_axis2());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_dim3_axis2_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f, 12.0f, 16.0f, 20.0f, 13.0f, 17.0f, 21.0f, 14.0f, 18.0f, 22.0f, 15.0f, 19.0f, 23.0f, 24.0f, 28.0f, 32.0f, 25.0f, 29.0f, 33.0f, 26.0f, 30.0f, 34.0f, 27.0f, 31.0f, 35.0f, 36.0f, 40.0f, 44.0f, 37.0f, 41.0f, 45.0f, 38.0f, 42.0f, 46.0f, 39.0f, 43.0f, 47.0f, 48.0f, 52.0f, 56.0f, 49.0f, 53.0f, 57.0f, 50.0f, 54.0f, 58.0f, 51.0f, 55.0f, 59.0f, 60.0f, 64.0f, 68.0f, 61.0f, 65.0f, 69.0f, 62.0f, 66.0f, 70.0f, 63.0f, 67.0f, 71.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 35.0f, 36.0f, 37.0f, 38.0f, 39.0f, 40.0f, 41.0f, 42.0f, 43.0f, 44.0f, 45.0f, 46.0f, 47.0f, 48.0f, 49.0f, 50.0f, 51.0f, 52.0f, 53.0f, 54.0f, 55.0f, 56.0f, 57.0f, 58.0f, 59.0f, 60.0f, 61.0f, 62.0f, 63.0f, 64.0f, 65.0f, 66.0f, 67.0f, 68.0f, 69.0f, 70.0f, 71.0f})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim3_axis2_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_dim3_axis2_all_inputs_as_internal", get_test_model_dim3_axis2_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_dim3_axis2_neg() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 35.0f, 36.0f, 37.0f, 38.0f, 39.0f, 40.0f, 41.0f, 42.0f, 43.0f, 44.0f, 45.0f, 46.0f, 47.0f, 48.0f, 49.0f, 50.0f, 51.0f, 52.0f, 53.0f, 54.0f, 55.0f, 56.0f, 57.0f, 58.0f, 59.0f, 60.0f, 61.0f, 62.0f, 63.0f, 64.0f, 65.0f, 66.0f, 67.0f, 68.0f, 69.0f, 70.0f, 71.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f, 12.0f, 16.0f, 20.0f, 13.0f, 17.0f, 21.0f, 14.0f, 18.0f, 22.0f, 15.0f, 19.0f, 23.0f, 24.0f, 28.0f, 32.0f, 25.0f, 29.0f, 33.0f, 26.0f, 30.0f, 34.0f, 27.0f, 31.0f, 35.0f, 36.0f, 40.0f, 44.0f, 37.0f, 41.0f, 45.0f, 38.0f, 42.0f, 46.0f, 39.0f, 43.0f, 47.0f, 48.0f, 52.0f, 56.0f, 49.0f, 53.0f, 57.0f, 50.0f, 54.0f, 58.0f, 51.0f, 55.0f, 59.0f, 60.0f, 64.0f, 68.0f, 61.0f, 65.0f, 69.0f, 62.0f, 66.0f, 70.0f, 63.0f, 67.0f, 71.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim3_axis2_neg = TestModelManager::get().add("channel_shuffle_dim3_axis2_neg", get_test_model_dim3_axis2_neg());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_dim3_axis2_neg_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f, 12.0f, 16.0f, 20.0f, 13.0f, 17.0f, 21.0f, 14.0f, 18.0f, 22.0f, 15.0f, 19.0f, 23.0f, 24.0f, 28.0f, 32.0f, 25.0f, 29.0f, 33.0f, 26.0f, 30.0f, 34.0f, 27.0f, 31.0f, 35.0f, 36.0f, 40.0f, 44.0f, 37.0f, 41.0f, 45.0f, 38.0f, 42.0f, 46.0f, 39.0f, 43.0f, 47.0f, 48.0f, 52.0f, 56.0f, 49.0f, 53.0f, 57.0f, 50.0f, 54.0f, 58.0f, 51.0f, 55.0f, 59.0f, 60.0f, 64.0f, 68.0f, 61.0f, 65.0f, 69.0f, 62.0f, 66.0f, 70.0f, 63.0f, 67.0f, 71.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 35.0f, 36.0f, 37.0f, 38.0f, 39.0f, 40.0f, 41.0f, 42.0f, 43.0f, 44.0f, 45.0f, 46.0f, 47.0f, 48.0f, 49.0f, 50.0f, 51.0f, 52.0f, 53.0f, 54.0f, 55.0f, 56.0f, 57.0f, 58.0f, 59.0f, 60.0f, 61.0f, 62.0f, 63.0f, 64.0f, 65.0f, 66.0f, 67.0f, 68.0f, 69.0f, 70.0f, 71.0f})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim3_axis2_neg_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_dim3_axis2_neg_all_inputs_as_internal", get_test_model_dim3_axis2_neg_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_dim2_axis0() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 1.0f, 13.0f, 25.0f, 2.0f, 14.0f, 26.0f, 3.0f, 15.0f, 27.0f, 4.0f, 16.0f, 28.0f, 5.0f, 17.0f, 29.0f, 6.0f, 18.0f, 30.0f, 7.0f, 19.0f, 31.0f, 8.0f, 20.0f, 32.0f, 9.0f, 21.0f, 33.0f, 10.0f, 22.0f, 34.0f, 11.0f, 23.0f, 35.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 4.0f, 16.0f, 28.0f, 8.0f, 20.0f, 32.0f, 1.0f, 13.0f, 25.0f, 5.0f, 17.0f, 29.0f, 9.0f, 21.0f, 33.0f, 2.0f, 14.0f, 26.0f, 6.0f, 18.0f, 30.0f, 10.0f, 22.0f, 34.0f, 3.0f, 15.0f, 27.0f, 7.0f, 19.0f, 31.0f, 11.0f, 23.0f, 35.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim2_axis0 = TestModelManager::get().add("channel_shuffle_dim2_axis0", get_test_model_dim2_axis0());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_dim2_axis0_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 4.0f, 16.0f, 28.0f, 8.0f, 20.0f, 32.0f, 1.0f, 13.0f, 25.0f, 5.0f, 17.0f, 29.0f, 9.0f, 21.0f, 33.0f, 2.0f, 14.0f, 26.0f, 6.0f, 18.0f, 30.0f, 10.0f, 22.0f, 34.0f, 3.0f, 15.0f, 27.0f, 7.0f, 19.0f, 31.0f, 11.0f, 23.0f, 35.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 1.0f, 13.0f, 25.0f, 2.0f, 14.0f, 26.0f, 3.0f, 15.0f, 27.0f, 4.0f, 16.0f, 28.0f, 5.0f, 17.0f, 29.0f, 6.0f, 18.0f, 30.0f, 7.0f, 19.0f, 31.0f, 8.0f, 20.0f, 32.0f, 9.0f, 21.0f, 33.0f, 10.0f, 22.0f, 34.0f, 11.0f, 23.0f, 35.0f})
                        }, { // placeholder14
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim2_axis0_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_dim2_axis0_all_inputs_as_internal", get_test_model_dim2_axis0_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_dim2_axis0_neg() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 1.0f, 13.0f, 25.0f, 2.0f, 14.0f, 26.0f, 3.0f, 15.0f, 27.0f, 4.0f, 16.0f, 28.0f, 5.0f, 17.0f, 29.0f, 6.0f, 18.0f, 30.0f, 7.0f, 19.0f, 31.0f, 8.0f, 20.0f, 32.0f, 9.0f, 21.0f, 33.0f, 10.0f, 22.0f, 34.0f, 11.0f, 23.0f, 35.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 4.0f, 16.0f, 28.0f, 8.0f, 20.0f, 32.0f, 1.0f, 13.0f, 25.0f, 5.0f, 17.0f, 29.0f, 9.0f, 21.0f, 33.0f, 2.0f, 14.0f, 26.0f, 6.0f, 18.0f, 30.0f, 10.0f, 22.0f, 34.0f, 3.0f, 15.0f, 27.0f, 7.0f, 19.0f, 31.0f, 11.0f, 23.0f, 35.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim2_axis0_neg = TestModelManager::get().add("channel_shuffle_dim2_axis0_neg", get_test_model_dim2_axis0_neg());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_dim2_axis0_neg_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 4.0f, 16.0f, 28.0f, 8.0f, 20.0f, 32.0f, 1.0f, 13.0f, 25.0f, 5.0f, 17.0f, 29.0f, 9.0f, 21.0f, 33.0f, 2.0f, 14.0f, 26.0f, 6.0f, 18.0f, 30.0f, 10.0f, 22.0f, 34.0f, 3.0f, 15.0f, 27.0f, 7.0f, 19.0f, 31.0f, 11.0f, 23.0f, 35.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 1.0f, 13.0f, 25.0f, 2.0f, 14.0f, 26.0f, 3.0f, 15.0f, 27.0f, 4.0f, 16.0f, 28.0f, 5.0f, 17.0f, 29.0f, 6.0f, 18.0f, 30.0f, 7.0f, 19.0f, 31.0f, 8.0f, 20.0f, 32.0f, 9.0f, 21.0f, 33.0f, 10.0f, 22.0f, 34.0f, 11.0f, 23.0f, 35.0f})
                        }, { // placeholder15
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim2_axis0_neg_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_dim2_axis0_neg_all_inputs_as_internal", get_test_model_dim2_axis0_neg_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_dim2_axis1() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 35.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f, 12.0f, 16.0f, 20.0f, 13.0f, 17.0f, 21.0f, 14.0f, 18.0f, 22.0f, 15.0f, 19.0f, 23.0f, 24.0f, 28.0f, 32.0f, 25.0f, 29.0f, 33.0f, 26.0f, 30.0f, 34.0f, 27.0f, 31.0f, 35.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim2_axis1 = TestModelManager::get().add("channel_shuffle_dim2_axis1", get_test_model_dim2_axis1());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_dim2_axis1_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f, 12.0f, 16.0f, 20.0f, 13.0f, 17.0f, 21.0f, 14.0f, 18.0f, 22.0f, 15.0f, 19.0f, 23.0f, 24.0f, 28.0f, 32.0f, 25.0f, 29.0f, 33.0f, 26.0f, 30.0f, 34.0f, 27.0f, 31.0f, 35.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 35.0f})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim2_axis1_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_dim2_axis1_all_inputs_as_internal", get_test_model_dim2_axis1_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_dim2_axis1_neg() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 35.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f, 12.0f, 16.0f, 20.0f, 13.0f, 17.0f, 21.0f, 14.0f, 18.0f, 22.0f, 15.0f, 19.0f, 23.0f, 24.0f, 28.0f, 32.0f, 25.0f, 29.0f, 33.0f, 26.0f, 30.0f, 34.0f, 27.0f, 31.0f, 35.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim2_axis1_neg = TestModelManager::get().add("channel_shuffle_dim2_axis1_neg", get_test_model_dim2_axis1_neg());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_dim2_axis1_neg_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f, 12.0f, 16.0f, 20.0f, 13.0f, 17.0f, 21.0f, 14.0f, 18.0f, 22.0f, 15.0f, 19.0f, 23.0f, 24.0f, 28.0f, 32.0f, 25.0f, 29.0f, 33.0f, 26.0f, 30.0f, 34.0f, 27.0f, 31.0f, 35.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 35.0f})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim2_axis1_neg_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_dim2_axis1_neg_all_inputs_as_internal", get_test_model_dim2_axis1_neg_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_dim1_axis0() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim1_axis0 = TestModelManager::get().add("channel_shuffle_dim1_axis0", get_test_model_dim1_axis0());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_dim1_axis0_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim1_axis0_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_dim1_axis0_all_inputs_as_internal", get_test_model_dim1_axis0_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_dim1_axis0_neg() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim1_axis0_neg = TestModelManager::get().add("channel_shuffle_dim1_axis0_neg", get_test_model_dim1_axis0_neg());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_dim1_axis0_neg_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_dim1_axis0_neg_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_dim1_axis0_neg_all_inputs_as_internal", get_test_model_dim1_axis0_neg_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_relaxed_dim4_axis0() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_dim4_axis0 = TestModelManager::get().add("channel_shuffle_relaxed_dim4_axis0", get_test_model_relaxed_dim4_axis0());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_relaxed_dim4_axis0_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
                        }, { // placeholder20
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_dim4_axis0_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_relaxed_dim4_axis0_all_inputs_as_internal", get_test_model_relaxed_dim4_axis0_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_relaxed_dim4_axis0_neg() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_dim4_axis0_neg = TestModelManager::get().add("channel_shuffle_relaxed_dim4_axis0_neg", get_test_model_relaxed_dim4_axis0_neg());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_relaxed_dim4_axis0_neg_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
                        }, { // placeholder21
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_dim4_axis0_neg_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_relaxed_dim4_axis0_neg_all_inputs_as_internal", get_test_model_relaxed_dim4_axis0_neg_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_relaxed_dim4_axis1() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 12, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_dim4_axis1 = TestModelManager::get().add("channel_shuffle_relaxed_dim4_axis1", get_test_model_relaxed_dim4_axis1());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_relaxed_dim4_axis1_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 12, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
                        }, { // placeholder22
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_dim4_axis1_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_relaxed_dim4_axis1_all_inputs_as_internal", get_test_model_relaxed_dim4_axis1_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_relaxed_dim4_axis1_neg() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 12, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_dim4_axis1_neg = TestModelManager::get().add("channel_shuffle_relaxed_dim4_axis1_neg", get_test_model_relaxed_dim4_axis1_neg());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_relaxed_dim4_axis1_neg_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 12, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
                        }, { // placeholder23
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_dim4_axis1_neg_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_relaxed_dim4_axis1_neg_all_inputs_as_internal", get_test_model_relaxed_dim4_axis1_neg_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_relaxed_dim4_axis2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 1.0f, 13.0f, 25.0f, 2.0f, 14.0f, 26.0f, 3.0f, 15.0f, 27.0f, 4.0f, 16.0f, 28.0f, 5.0f, 17.0f, 29.0f, 6.0f, 18.0f, 30.0f, 7.0f, 19.0f, 31.0f, 8.0f, 20.0f, 32.0f, 9.0f, 21.0f, 33.0f, 10.0f, 22.0f, 34.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 37.0f, 49.0f, 61.0f, 38.0f, 50.0f, 62.0f, 39.0f, 51.0f, 63.0f, 40.0f, 52.0f, 64.0f, 41.0f, 53.0f, 65.0f, 42.0f, 54.0f, 66.0f, 43.0f, 55.0f, 67.0f, 44.0f, 56.0f, 68.0f, 45.0f, 57.0f, 69.0f, 46.0f, 58.0f, 70.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 73.0f, 85.0f, 97.0f, 74.0f, 86.0f, 98.0f, 75.0f, 87.0f, 99.0f, 76.0f, 88.0f, 100.0f, 77.0f, 89.0f, 101.0f, 78.0f, 90.0f, 102.0f, 79.0f, 91.0f, 103.0f, 80.0f, 92.0f, 104.0f, 81.0f, 93.0f, 105.0f, 82.0f, 94.0f, 106.0f, 83.0f, 95.0f, 107.0f, 108.0f, 120.0f, 132.0f, 109.0f, 121.0f, 133.0f, 110.0f, 122.0f, 134.0f, 111.0f, 123.0f, 135.0f, 112.0f, 124.0f, 136.0f, 113.0f, 125.0f, 137.0f, 114.0f, 126.0f, 138.0f, 115.0f, 127.0f, 139.0f, 116.0f, 128.0f, 140.0f, 117.0f, 129.0f, 141.0f, 118.0f, 130.0f, 142.0f, 119.0f, 131.0f, 143.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 4.0f, 16.0f, 28.0f, 8.0f, 20.0f, 32.0f, 1.0f, 13.0f, 25.0f, 5.0f, 17.0f, 29.0f, 9.0f, 21.0f, 33.0f, 2.0f, 14.0f, 26.0f, 6.0f, 18.0f, 30.0f, 10.0f, 22.0f, 34.0f, 3.0f, 15.0f, 27.0f, 7.0f, 19.0f, 31.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 40.0f, 52.0f, 64.0f, 44.0f, 56.0f, 68.0f, 37.0f, 49.0f, 61.0f, 41.0f, 53.0f, 65.0f, 45.0f, 57.0f, 69.0f, 38.0f, 50.0f, 62.0f, 42.0f, 54.0f, 66.0f, 46.0f, 58.0f, 70.0f, 39.0f, 51.0f, 63.0f, 43.0f, 55.0f, 67.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 76.0f, 88.0f, 100.0f, 80.0f, 92.0f, 104.0f, 73.0f, 85.0f, 97.0f, 77.0f, 89.0f, 101.0f, 81.0f, 93.0f, 105.0f, 74.0f, 86.0f, 98.0f, 78.0f, 90.0f, 102.0f, 82.0f, 94.0f, 106.0f, 75.0f, 87.0f, 99.0f, 79.0f, 91.0f, 103.0f, 83.0f, 95.0f, 107.0f, 108.0f, 120.0f, 132.0f, 112.0f, 124.0f, 136.0f, 116.0f, 128.0f, 140.0f, 109.0f, 121.0f, 133.0f, 113.0f, 125.0f, 137.0f, 117.0f, 129.0f, 141.0f, 110.0f, 122.0f, 134.0f, 114.0f, 126.0f, 138.0f, 118.0f, 130.0f, 142.0f, 111.0f, 123.0f, 135.0f, 115.0f, 127.0f, 139.0f, 119.0f, 131.0f, 143.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_dim4_axis2 = TestModelManager::get().add("channel_shuffle_relaxed_dim4_axis2", get_test_model_relaxed_dim4_axis2());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_relaxed_dim4_axis2_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 4.0f, 16.0f, 28.0f, 8.0f, 20.0f, 32.0f, 1.0f, 13.0f, 25.0f, 5.0f, 17.0f, 29.0f, 9.0f, 21.0f, 33.0f, 2.0f, 14.0f, 26.0f, 6.0f, 18.0f, 30.0f, 10.0f, 22.0f, 34.0f, 3.0f, 15.0f, 27.0f, 7.0f, 19.0f, 31.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 40.0f, 52.0f, 64.0f, 44.0f, 56.0f, 68.0f, 37.0f, 49.0f, 61.0f, 41.0f, 53.0f, 65.0f, 45.0f, 57.0f, 69.0f, 38.0f, 50.0f, 62.0f, 42.0f, 54.0f, 66.0f, 46.0f, 58.0f, 70.0f, 39.0f, 51.0f, 63.0f, 43.0f, 55.0f, 67.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 76.0f, 88.0f, 100.0f, 80.0f, 92.0f, 104.0f, 73.0f, 85.0f, 97.0f, 77.0f, 89.0f, 101.0f, 81.0f, 93.0f, 105.0f, 74.0f, 86.0f, 98.0f, 78.0f, 90.0f, 102.0f, 82.0f, 94.0f, 106.0f, 75.0f, 87.0f, 99.0f, 79.0f, 91.0f, 103.0f, 83.0f, 95.0f, 107.0f, 108.0f, 120.0f, 132.0f, 112.0f, 124.0f, 136.0f, 116.0f, 128.0f, 140.0f, 109.0f, 121.0f, 133.0f, 113.0f, 125.0f, 137.0f, 117.0f, 129.0f, 141.0f, 110.0f, 122.0f, 134.0f, 114.0f, 126.0f, 138.0f, 118.0f, 130.0f, 142.0f, 111.0f, 123.0f, 135.0f, 115.0f, 127.0f, 139.0f, 119.0f, 131.0f, 143.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 1.0f, 13.0f, 25.0f, 2.0f, 14.0f, 26.0f, 3.0f, 15.0f, 27.0f, 4.0f, 16.0f, 28.0f, 5.0f, 17.0f, 29.0f, 6.0f, 18.0f, 30.0f, 7.0f, 19.0f, 31.0f, 8.0f, 20.0f, 32.0f, 9.0f, 21.0f, 33.0f, 10.0f, 22.0f, 34.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 37.0f, 49.0f, 61.0f, 38.0f, 50.0f, 62.0f, 39.0f, 51.0f, 63.0f, 40.0f, 52.0f, 64.0f, 41.0f, 53.0f, 65.0f, 42.0f, 54.0f, 66.0f, 43.0f, 55.0f, 67.0f, 44.0f, 56.0f, 68.0f, 45.0f, 57.0f, 69.0f, 46.0f, 58.0f, 70.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 73.0f, 85.0f, 97.0f, 74.0f, 86.0f, 98.0f, 75.0f, 87.0f, 99.0f, 76.0f, 88.0f, 100.0f, 77.0f, 89.0f, 101.0f, 78.0f, 90.0f, 102.0f, 79.0f, 91.0f, 103.0f, 80.0f, 92.0f, 104.0f, 81.0f, 93.0f, 105.0f, 82.0f, 94.0f, 106.0f, 83.0f, 95.0f, 107.0f, 108.0f, 120.0f, 132.0f, 109.0f, 121.0f, 133.0f, 110.0f, 122.0f, 134.0f, 111.0f, 123.0f, 135.0f, 112.0f, 124.0f, 136.0f, 113.0f, 125.0f, 137.0f, 114.0f, 126.0f, 138.0f, 115.0f, 127.0f, 139.0f, 116.0f, 128.0f, 140.0f, 117.0f, 129.0f, 141.0f, 118.0f, 130.0f, 142.0f, 119.0f, 131.0f, 143.0f})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_dim4_axis2_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_relaxed_dim4_axis2_all_inputs_as_internal", get_test_model_relaxed_dim4_axis2_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_relaxed_dim4_axis2_neg() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 1.0f, 13.0f, 25.0f, 2.0f, 14.0f, 26.0f, 3.0f, 15.0f, 27.0f, 4.0f, 16.0f, 28.0f, 5.0f, 17.0f, 29.0f, 6.0f, 18.0f, 30.0f, 7.0f, 19.0f, 31.0f, 8.0f, 20.0f, 32.0f, 9.0f, 21.0f, 33.0f, 10.0f, 22.0f, 34.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 37.0f, 49.0f, 61.0f, 38.0f, 50.0f, 62.0f, 39.0f, 51.0f, 63.0f, 40.0f, 52.0f, 64.0f, 41.0f, 53.0f, 65.0f, 42.0f, 54.0f, 66.0f, 43.0f, 55.0f, 67.0f, 44.0f, 56.0f, 68.0f, 45.0f, 57.0f, 69.0f, 46.0f, 58.0f, 70.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 73.0f, 85.0f, 97.0f, 74.0f, 86.0f, 98.0f, 75.0f, 87.0f, 99.0f, 76.0f, 88.0f, 100.0f, 77.0f, 89.0f, 101.0f, 78.0f, 90.0f, 102.0f, 79.0f, 91.0f, 103.0f, 80.0f, 92.0f, 104.0f, 81.0f, 93.0f, 105.0f, 82.0f, 94.0f, 106.0f, 83.0f, 95.0f, 107.0f, 108.0f, 120.0f, 132.0f, 109.0f, 121.0f, 133.0f, 110.0f, 122.0f, 134.0f, 111.0f, 123.0f, 135.0f, 112.0f, 124.0f, 136.0f, 113.0f, 125.0f, 137.0f, 114.0f, 126.0f, 138.0f, 115.0f, 127.0f, 139.0f, 116.0f, 128.0f, 140.0f, 117.0f, 129.0f, 141.0f, 118.0f, 130.0f, 142.0f, 119.0f, 131.0f, 143.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 4.0f, 16.0f, 28.0f, 8.0f, 20.0f, 32.0f, 1.0f, 13.0f, 25.0f, 5.0f, 17.0f, 29.0f, 9.0f, 21.0f, 33.0f, 2.0f, 14.0f, 26.0f, 6.0f, 18.0f, 30.0f, 10.0f, 22.0f, 34.0f, 3.0f, 15.0f, 27.0f, 7.0f, 19.0f, 31.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 40.0f, 52.0f, 64.0f, 44.0f, 56.0f, 68.0f, 37.0f, 49.0f, 61.0f, 41.0f, 53.0f, 65.0f, 45.0f, 57.0f, 69.0f, 38.0f, 50.0f, 62.0f, 42.0f, 54.0f, 66.0f, 46.0f, 58.0f, 70.0f, 39.0f, 51.0f, 63.0f, 43.0f, 55.0f, 67.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 76.0f, 88.0f, 100.0f, 80.0f, 92.0f, 104.0f, 73.0f, 85.0f, 97.0f, 77.0f, 89.0f, 101.0f, 81.0f, 93.0f, 105.0f, 74.0f, 86.0f, 98.0f, 78.0f, 90.0f, 102.0f, 82.0f, 94.0f, 106.0f, 75.0f, 87.0f, 99.0f, 79.0f, 91.0f, 103.0f, 83.0f, 95.0f, 107.0f, 108.0f, 120.0f, 132.0f, 112.0f, 124.0f, 136.0f, 116.0f, 128.0f, 140.0f, 109.0f, 121.0f, 133.0f, 113.0f, 125.0f, 137.0f, 117.0f, 129.0f, 141.0f, 110.0f, 122.0f, 134.0f, 114.0f, 126.0f, 138.0f, 118.0f, 130.0f, 142.0f, 111.0f, 123.0f, 135.0f, 115.0f, 127.0f, 139.0f, 119.0f, 131.0f, 143.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_dim4_axis2_neg = TestModelManager::get().add("channel_shuffle_relaxed_dim4_axis2_neg", get_test_model_relaxed_dim4_axis2_neg());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_relaxed_dim4_axis2_neg_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 4.0f, 16.0f, 28.0f, 8.0f, 20.0f, 32.0f, 1.0f, 13.0f, 25.0f, 5.0f, 17.0f, 29.0f, 9.0f, 21.0f, 33.0f, 2.0f, 14.0f, 26.0f, 6.0f, 18.0f, 30.0f, 10.0f, 22.0f, 34.0f, 3.0f, 15.0f, 27.0f, 7.0f, 19.0f, 31.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 40.0f, 52.0f, 64.0f, 44.0f, 56.0f, 68.0f, 37.0f, 49.0f, 61.0f, 41.0f, 53.0f, 65.0f, 45.0f, 57.0f, 69.0f, 38.0f, 50.0f, 62.0f, 42.0f, 54.0f, 66.0f, 46.0f, 58.0f, 70.0f, 39.0f, 51.0f, 63.0f, 43.0f, 55.0f, 67.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 76.0f, 88.0f, 100.0f, 80.0f, 92.0f, 104.0f, 73.0f, 85.0f, 97.0f, 77.0f, 89.0f, 101.0f, 81.0f, 93.0f, 105.0f, 74.0f, 86.0f, 98.0f, 78.0f, 90.0f, 102.0f, 82.0f, 94.0f, 106.0f, 75.0f, 87.0f, 99.0f, 79.0f, 91.0f, 103.0f, 83.0f, 95.0f, 107.0f, 108.0f, 120.0f, 132.0f, 112.0f, 124.0f, 136.0f, 116.0f, 128.0f, 140.0f, 109.0f, 121.0f, 133.0f, 113.0f, 125.0f, 137.0f, 117.0f, 129.0f, 141.0f, 110.0f, 122.0f, 134.0f, 114.0f, 126.0f, 138.0f, 118.0f, 130.0f, 142.0f, 111.0f, 123.0f, 135.0f, 115.0f, 127.0f, 139.0f, 119.0f, 131.0f, 143.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 1.0f, 13.0f, 25.0f, 2.0f, 14.0f, 26.0f, 3.0f, 15.0f, 27.0f, 4.0f, 16.0f, 28.0f, 5.0f, 17.0f, 29.0f, 6.0f, 18.0f, 30.0f, 7.0f, 19.0f, 31.0f, 8.0f, 20.0f, 32.0f, 9.0f, 21.0f, 33.0f, 10.0f, 22.0f, 34.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 37.0f, 49.0f, 61.0f, 38.0f, 50.0f, 62.0f, 39.0f, 51.0f, 63.0f, 40.0f, 52.0f, 64.0f, 41.0f, 53.0f, 65.0f, 42.0f, 54.0f, 66.0f, 43.0f, 55.0f, 67.0f, 44.0f, 56.0f, 68.0f, 45.0f, 57.0f, 69.0f, 46.0f, 58.0f, 70.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 73.0f, 85.0f, 97.0f, 74.0f, 86.0f, 98.0f, 75.0f, 87.0f, 99.0f, 76.0f, 88.0f, 100.0f, 77.0f, 89.0f, 101.0f, 78.0f, 90.0f, 102.0f, 79.0f, 91.0f, 103.0f, 80.0f, 92.0f, 104.0f, 81.0f, 93.0f, 105.0f, 82.0f, 94.0f, 106.0f, 83.0f, 95.0f, 107.0f, 108.0f, 120.0f, 132.0f, 109.0f, 121.0f, 133.0f, 110.0f, 122.0f, 134.0f, 111.0f, 123.0f, 135.0f, 112.0f, 124.0f, 136.0f, 113.0f, 125.0f, 137.0f, 114.0f, 126.0f, 138.0f, 115.0f, 127.0f, 139.0f, 116.0f, 128.0f, 140.0f, 117.0f, 129.0f, 141.0f, 118.0f, 130.0f, 142.0f, 119.0f, 131.0f, 143.0f})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_dim4_axis2_neg_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_relaxed_dim4_axis2_neg_all_inputs_as_internal", get_test_model_relaxed_dim4_axis2_neg_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_relaxed_dim4_axis3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 35.0f, 36.0f, 37.0f, 38.0f, 39.0f, 40.0f, 41.0f, 42.0f, 43.0f, 44.0f, 45.0f, 46.0f, 47.0f, 48.0f, 49.0f, 50.0f, 51.0f, 52.0f, 53.0f, 54.0f, 55.0f, 56.0f, 57.0f, 58.0f, 59.0f, 60.0f, 61.0f, 62.0f, 63.0f, 64.0f, 65.0f, 66.0f, 67.0f, 68.0f, 69.0f, 70.0f, 71.0f, 72.0f, 73.0f, 74.0f, 75.0f, 76.0f, 77.0f, 78.0f, 79.0f, 80.0f, 81.0f, 82.0f, 83.0f, 84.0f, 85.0f, 86.0f, 87.0f, 88.0f, 89.0f, 90.0f, 91.0f, 92.0f, 93.0f, 94.0f, 95.0f, 96.0f, 97.0f, 98.0f, 99.0f, 100.0f, 101.0f, 102.0f, 103.0f, 104.0f, 105.0f, 106.0f, 107.0f, 108.0f, 109.0f, 110.0f, 111.0f, 112.0f, 113.0f, 114.0f, 115.0f, 116.0f, 117.0f, 118.0f, 119.0f, 120.0f, 121.0f, 122.0f, 123.0f, 124.0f, 125.0f, 126.0f, 127.0f, 128.0f, 129.0f, 130.0f, 131.0f, 132.0f, 133.0f, 134.0f, 135.0f, 136.0f, 137.0f, 138.0f, 139.0f, 140.0f, 141.0f, 142.0f, 143.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f, 12.0f, 16.0f, 20.0f, 13.0f, 17.0f, 21.0f, 14.0f, 18.0f, 22.0f, 15.0f, 19.0f, 23.0f, 24.0f, 28.0f, 32.0f, 25.0f, 29.0f, 33.0f, 26.0f, 30.0f, 34.0f, 27.0f, 31.0f, 35.0f, 36.0f, 40.0f, 44.0f, 37.0f, 41.0f, 45.0f, 38.0f, 42.0f, 46.0f, 39.0f, 43.0f, 47.0f, 48.0f, 52.0f, 56.0f, 49.0f, 53.0f, 57.0f, 50.0f, 54.0f, 58.0f, 51.0f, 55.0f, 59.0f, 60.0f, 64.0f, 68.0f, 61.0f, 65.0f, 69.0f, 62.0f, 66.0f, 70.0f, 63.0f, 67.0f, 71.0f, 72.0f, 76.0f, 80.0f, 73.0f, 77.0f, 81.0f, 74.0f, 78.0f, 82.0f, 75.0f, 79.0f, 83.0f, 84.0f, 88.0f, 92.0f, 85.0f, 89.0f, 93.0f, 86.0f, 90.0f, 94.0f, 87.0f, 91.0f, 95.0f, 96.0f, 100.0f, 104.0f, 97.0f, 101.0f, 105.0f, 98.0f, 102.0f, 106.0f, 99.0f, 103.0f, 107.0f, 108.0f, 112.0f, 116.0f, 109.0f, 113.0f, 117.0f, 110.0f, 114.0f, 118.0f, 111.0f, 115.0f, 119.0f, 120.0f, 124.0f, 128.0f, 121.0f, 125.0f, 129.0f, 122.0f, 126.0f, 130.0f, 123.0f, 127.0f, 131.0f, 132.0f, 136.0f, 140.0f, 133.0f, 137.0f, 141.0f, 134.0f, 138.0f, 142.0f, 135.0f, 139.0f, 143.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_dim4_axis3 = TestModelManager::get().add("channel_shuffle_relaxed_dim4_axis3", get_test_model_relaxed_dim4_axis3());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_relaxed_dim4_axis3_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f, 12.0f, 16.0f, 20.0f, 13.0f, 17.0f, 21.0f, 14.0f, 18.0f, 22.0f, 15.0f, 19.0f, 23.0f, 24.0f, 28.0f, 32.0f, 25.0f, 29.0f, 33.0f, 26.0f, 30.0f, 34.0f, 27.0f, 31.0f, 35.0f, 36.0f, 40.0f, 44.0f, 37.0f, 41.0f, 45.0f, 38.0f, 42.0f, 46.0f, 39.0f, 43.0f, 47.0f, 48.0f, 52.0f, 56.0f, 49.0f, 53.0f, 57.0f, 50.0f, 54.0f, 58.0f, 51.0f, 55.0f, 59.0f, 60.0f, 64.0f, 68.0f, 61.0f, 65.0f, 69.0f, 62.0f, 66.0f, 70.0f, 63.0f, 67.0f, 71.0f, 72.0f, 76.0f, 80.0f, 73.0f, 77.0f, 81.0f, 74.0f, 78.0f, 82.0f, 75.0f, 79.0f, 83.0f, 84.0f, 88.0f, 92.0f, 85.0f, 89.0f, 93.0f, 86.0f, 90.0f, 94.0f, 87.0f, 91.0f, 95.0f, 96.0f, 100.0f, 104.0f, 97.0f, 101.0f, 105.0f, 98.0f, 102.0f, 106.0f, 99.0f, 103.0f, 107.0f, 108.0f, 112.0f, 116.0f, 109.0f, 113.0f, 117.0f, 110.0f, 114.0f, 118.0f, 111.0f, 115.0f, 119.0f, 120.0f, 124.0f, 128.0f, 121.0f, 125.0f, 129.0f, 122.0f, 126.0f, 130.0f, 123.0f, 127.0f, 131.0f, 132.0f, 136.0f, 140.0f, 133.0f, 137.0f, 141.0f, 134.0f, 138.0f, 142.0f, 135.0f, 139.0f, 143.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 35.0f, 36.0f, 37.0f, 38.0f, 39.0f, 40.0f, 41.0f, 42.0f, 43.0f, 44.0f, 45.0f, 46.0f, 47.0f, 48.0f, 49.0f, 50.0f, 51.0f, 52.0f, 53.0f, 54.0f, 55.0f, 56.0f, 57.0f, 58.0f, 59.0f, 60.0f, 61.0f, 62.0f, 63.0f, 64.0f, 65.0f, 66.0f, 67.0f, 68.0f, 69.0f, 70.0f, 71.0f, 72.0f, 73.0f, 74.0f, 75.0f, 76.0f, 77.0f, 78.0f, 79.0f, 80.0f, 81.0f, 82.0f, 83.0f, 84.0f, 85.0f, 86.0f, 87.0f, 88.0f, 89.0f, 90.0f, 91.0f, 92.0f, 93.0f, 94.0f, 95.0f, 96.0f, 97.0f, 98.0f, 99.0f, 100.0f, 101.0f, 102.0f, 103.0f, 104.0f, 105.0f, 106.0f, 107.0f, 108.0f, 109.0f, 110.0f, 111.0f, 112.0f, 113.0f, 114.0f, 115.0f, 116.0f, 117.0f, 118.0f, 119.0f, 120.0f, 121.0f, 122.0f, 123.0f, 124.0f, 125.0f, 126.0f, 127.0f, 128.0f, 129.0f, 130.0f, 131.0f, 132.0f, 133.0f, 134.0f, 135.0f, 136.0f, 137.0f, 138.0f, 139.0f, 140.0f, 141.0f, 142.0f, 143.0f})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_dim4_axis3_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_relaxed_dim4_axis3_all_inputs_as_internal", get_test_model_relaxed_dim4_axis3_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_relaxed_dim4_axis3_neg() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 35.0f, 36.0f, 37.0f, 38.0f, 39.0f, 40.0f, 41.0f, 42.0f, 43.0f, 44.0f, 45.0f, 46.0f, 47.0f, 48.0f, 49.0f, 50.0f, 51.0f, 52.0f, 53.0f, 54.0f, 55.0f, 56.0f, 57.0f, 58.0f, 59.0f, 60.0f, 61.0f, 62.0f, 63.0f, 64.0f, 65.0f, 66.0f, 67.0f, 68.0f, 69.0f, 70.0f, 71.0f, 72.0f, 73.0f, 74.0f, 75.0f, 76.0f, 77.0f, 78.0f, 79.0f, 80.0f, 81.0f, 82.0f, 83.0f, 84.0f, 85.0f, 86.0f, 87.0f, 88.0f, 89.0f, 90.0f, 91.0f, 92.0f, 93.0f, 94.0f, 95.0f, 96.0f, 97.0f, 98.0f, 99.0f, 100.0f, 101.0f, 102.0f, 103.0f, 104.0f, 105.0f, 106.0f, 107.0f, 108.0f, 109.0f, 110.0f, 111.0f, 112.0f, 113.0f, 114.0f, 115.0f, 116.0f, 117.0f, 118.0f, 119.0f, 120.0f, 121.0f, 122.0f, 123.0f, 124.0f, 125.0f, 126.0f, 127.0f, 128.0f, 129.0f, 130.0f, 131.0f, 132.0f, 133.0f, 134.0f, 135.0f, 136.0f, 137.0f, 138.0f, 139.0f, 140.0f, 141.0f, 142.0f, 143.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f, 12.0f, 16.0f, 20.0f, 13.0f, 17.0f, 21.0f, 14.0f, 18.0f, 22.0f, 15.0f, 19.0f, 23.0f, 24.0f, 28.0f, 32.0f, 25.0f, 29.0f, 33.0f, 26.0f, 30.0f, 34.0f, 27.0f, 31.0f, 35.0f, 36.0f, 40.0f, 44.0f, 37.0f, 41.0f, 45.0f, 38.0f, 42.0f, 46.0f, 39.0f, 43.0f, 47.0f, 48.0f, 52.0f, 56.0f, 49.0f, 53.0f, 57.0f, 50.0f, 54.0f, 58.0f, 51.0f, 55.0f, 59.0f, 60.0f, 64.0f, 68.0f, 61.0f, 65.0f, 69.0f, 62.0f, 66.0f, 70.0f, 63.0f, 67.0f, 71.0f, 72.0f, 76.0f, 80.0f, 73.0f, 77.0f, 81.0f, 74.0f, 78.0f, 82.0f, 75.0f, 79.0f, 83.0f, 84.0f, 88.0f, 92.0f, 85.0f, 89.0f, 93.0f, 86.0f, 90.0f, 94.0f, 87.0f, 91.0f, 95.0f, 96.0f, 100.0f, 104.0f, 97.0f, 101.0f, 105.0f, 98.0f, 102.0f, 106.0f, 99.0f, 103.0f, 107.0f, 108.0f, 112.0f, 116.0f, 109.0f, 113.0f, 117.0f, 110.0f, 114.0f, 118.0f, 111.0f, 115.0f, 119.0f, 120.0f, 124.0f, 128.0f, 121.0f, 125.0f, 129.0f, 122.0f, 126.0f, 130.0f, 123.0f, 127.0f, 131.0f, 132.0f, 136.0f, 140.0f, 133.0f, 137.0f, 141.0f, 134.0f, 138.0f, 142.0f, 135.0f, 139.0f, 143.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_dim4_axis3_neg = TestModelManager::get().add("channel_shuffle_relaxed_dim4_axis3_neg", get_test_model_relaxed_dim4_axis3_neg());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_relaxed_dim4_axis3_neg_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f, 12.0f, 16.0f, 20.0f, 13.0f, 17.0f, 21.0f, 14.0f, 18.0f, 22.0f, 15.0f, 19.0f, 23.0f, 24.0f, 28.0f, 32.0f, 25.0f, 29.0f, 33.0f, 26.0f, 30.0f, 34.0f, 27.0f, 31.0f, 35.0f, 36.0f, 40.0f, 44.0f, 37.0f, 41.0f, 45.0f, 38.0f, 42.0f, 46.0f, 39.0f, 43.0f, 47.0f, 48.0f, 52.0f, 56.0f, 49.0f, 53.0f, 57.0f, 50.0f, 54.0f, 58.0f, 51.0f, 55.0f, 59.0f, 60.0f, 64.0f, 68.0f, 61.0f, 65.0f, 69.0f, 62.0f, 66.0f, 70.0f, 63.0f, 67.0f, 71.0f, 72.0f, 76.0f, 80.0f, 73.0f, 77.0f, 81.0f, 74.0f, 78.0f, 82.0f, 75.0f, 79.0f, 83.0f, 84.0f, 88.0f, 92.0f, 85.0f, 89.0f, 93.0f, 86.0f, 90.0f, 94.0f, 87.0f, 91.0f, 95.0f, 96.0f, 100.0f, 104.0f, 97.0f, 101.0f, 105.0f, 98.0f, 102.0f, 106.0f, 99.0f, 103.0f, 107.0f, 108.0f, 112.0f, 116.0f, 109.0f, 113.0f, 117.0f, 110.0f, 114.0f, 118.0f, 111.0f, 115.0f, 119.0f, 120.0f, 124.0f, 128.0f, 121.0f, 125.0f, 129.0f, 122.0f, 126.0f, 130.0f, 123.0f, 127.0f, 131.0f, 132.0f, 136.0f, 140.0f, 133.0f, 137.0f, 141.0f, 134.0f, 138.0f, 142.0f, 135.0f, 139.0f, 143.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 35.0f, 36.0f, 37.0f, 38.0f, 39.0f, 40.0f, 41.0f, 42.0f, 43.0f, 44.0f, 45.0f, 46.0f, 47.0f, 48.0f, 49.0f, 50.0f, 51.0f, 52.0f, 53.0f, 54.0f, 55.0f, 56.0f, 57.0f, 58.0f, 59.0f, 60.0f, 61.0f, 62.0f, 63.0f, 64.0f, 65.0f, 66.0f, 67.0f, 68.0f, 69.0f, 70.0f, 71.0f, 72.0f, 73.0f, 74.0f, 75.0f, 76.0f, 77.0f, 78.0f, 79.0f, 80.0f, 81.0f, 82.0f, 83.0f, 84.0f, 85.0f, 86.0f, 87.0f, 88.0f, 89.0f, 90.0f, 91.0f, 92.0f, 93.0f, 94.0f, 95.0f, 96.0f, 97.0f, 98.0f, 99.0f, 100.0f, 101.0f, 102.0f, 103.0f, 104.0f, 105.0f, 106.0f, 107.0f, 108.0f, 109.0f, 110.0f, 111.0f, 112.0f, 113.0f, 114.0f, 115.0f, 116.0f, 117.0f, 118.0f, 119.0f, 120.0f, 121.0f, 122.0f, 123.0f, 124.0f, 125.0f, 126.0f, 127.0f, 128.0f, 129.0f, 130.0f, 131.0f, 132.0f, 133.0f, 134.0f, 135.0f, 136.0f, 137.0f, 138.0f, 139.0f, 140.0f, 141.0f, 142.0f, 143.0f})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_dim4_axis3_neg_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_relaxed_dim4_axis3_neg_all_inputs_as_internal", get_test_model_relaxed_dim4_axis3_neg_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_relaxed_dim3_axis0() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_dim3_axis0 = TestModelManager::get().add("channel_shuffle_relaxed_dim3_axis0", get_test_model_relaxed_dim3_axis0());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_relaxed_dim3_axis0_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f})
                        }, { // placeholder28
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_dim3_axis0_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_relaxed_dim3_axis0_all_inputs_as_internal", get_test_model_relaxed_dim3_axis0_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_relaxed_dim3_axis0_neg() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_dim3_axis0_neg = TestModelManager::get().add("channel_shuffle_relaxed_dim3_axis0_neg", get_test_model_relaxed_dim3_axis0_neg());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_relaxed_dim3_axis0_neg_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f})
                        }, { // placeholder29
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_dim3_axis0_neg_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_relaxed_dim3_axis0_neg_all_inputs_as_internal", get_test_model_relaxed_dim3_axis0_neg_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_relaxed_dim3_axis1() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 1.0f, 13.0f, 25.0f, 2.0f, 14.0f, 26.0f, 3.0f, 15.0f, 27.0f, 4.0f, 16.0f, 28.0f, 5.0f, 17.0f, 29.0f, 6.0f, 18.0f, 30.0f, 7.0f, 19.0f, 31.0f, 8.0f, 20.0f, 32.0f, 9.0f, 21.0f, 33.0f, 10.0f, 22.0f, 34.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 37.0f, 49.0f, 61.0f, 38.0f, 50.0f, 62.0f, 39.0f, 51.0f, 63.0f, 40.0f, 52.0f, 64.0f, 41.0f, 53.0f, 65.0f, 42.0f, 54.0f, 66.0f, 43.0f, 55.0f, 67.0f, 44.0f, 56.0f, 68.0f, 45.0f, 57.0f, 69.0f, 46.0f, 58.0f, 70.0f, 47.0f, 59.0f, 71.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 4.0f, 16.0f, 28.0f, 8.0f, 20.0f, 32.0f, 1.0f, 13.0f, 25.0f, 5.0f, 17.0f, 29.0f, 9.0f, 21.0f, 33.0f, 2.0f, 14.0f, 26.0f, 6.0f, 18.0f, 30.0f, 10.0f, 22.0f, 34.0f, 3.0f, 15.0f, 27.0f, 7.0f, 19.0f, 31.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 40.0f, 52.0f, 64.0f, 44.0f, 56.0f, 68.0f, 37.0f, 49.0f, 61.0f, 41.0f, 53.0f, 65.0f, 45.0f, 57.0f, 69.0f, 38.0f, 50.0f, 62.0f, 42.0f, 54.0f, 66.0f, 46.0f, 58.0f, 70.0f, 39.0f, 51.0f, 63.0f, 43.0f, 55.0f, 67.0f, 47.0f, 59.0f, 71.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_dim3_axis1 = TestModelManager::get().add("channel_shuffle_relaxed_dim3_axis1", get_test_model_relaxed_dim3_axis1());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_relaxed_dim3_axis1_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 4.0f, 16.0f, 28.0f, 8.0f, 20.0f, 32.0f, 1.0f, 13.0f, 25.0f, 5.0f, 17.0f, 29.0f, 9.0f, 21.0f, 33.0f, 2.0f, 14.0f, 26.0f, 6.0f, 18.0f, 30.0f, 10.0f, 22.0f, 34.0f, 3.0f, 15.0f, 27.0f, 7.0f, 19.0f, 31.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 40.0f, 52.0f, 64.0f, 44.0f, 56.0f, 68.0f, 37.0f, 49.0f, 61.0f, 41.0f, 53.0f, 65.0f, 45.0f, 57.0f, 69.0f, 38.0f, 50.0f, 62.0f, 42.0f, 54.0f, 66.0f, 46.0f, 58.0f, 70.0f, 39.0f, 51.0f, 63.0f, 43.0f, 55.0f, 67.0f, 47.0f, 59.0f, 71.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 1.0f, 13.0f, 25.0f, 2.0f, 14.0f, 26.0f, 3.0f, 15.0f, 27.0f, 4.0f, 16.0f, 28.0f, 5.0f, 17.0f, 29.0f, 6.0f, 18.0f, 30.0f, 7.0f, 19.0f, 31.0f, 8.0f, 20.0f, 32.0f, 9.0f, 21.0f, 33.0f, 10.0f, 22.0f, 34.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 37.0f, 49.0f, 61.0f, 38.0f, 50.0f, 62.0f, 39.0f, 51.0f, 63.0f, 40.0f, 52.0f, 64.0f, 41.0f, 53.0f, 65.0f, 42.0f, 54.0f, 66.0f, 43.0f, 55.0f, 67.0f, 44.0f, 56.0f, 68.0f, 45.0f, 57.0f, 69.0f, 46.0f, 58.0f, 70.0f, 47.0f, 59.0f, 71.0f})
                        }, { // placeholder30
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_dim3_axis1_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_relaxed_dim3_axis1_all_inputs_as_internal", get_test_model_relaxed_dim3_axis1_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_relaxed_dim3_axis1_neg() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 1.0f, 13.0f, 25.0f, 2.0f, 14.0f, 26.0f, 3.0f, 15.0f, 27.0f, 4.0f, 16.0f, 28.0f, 5.0f, 17.0f, 29.0f, 6.0f, 18.0f, 30.0f, 7.0f, 19.0f, 31.0f, 8.0f, 20.0f, 32.0f, 9.0f, 21.0f, 33.0f, 10.0f, 22.0f, 34.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 37.0f, 49.0f, 61.0f, 38.0f, 50.0f, 62.0f, 39.0f, 51.0f, 63.0f, 40.0f, 52.0f, 64.0f, 41.0f, 53.0f, 65.0f, 42.0f, 54.0f, 66.0f, 43.0f, 55.0f, 67.0f, 44.0f, 56.0f, 68.0f, 45.0f, 57.0f, 69.0f, 46.0f, 58.0f, 70.0f, 47.0f, 59.0f, 71.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 4.0f, 16.0f, 28.0f, 8.0f, 20.0f, 32.0f, 1.0f, 13.0f, 25.0f, 5.0f, 17.0f, 29.0f, 9.0f, 21.0f, 33.0f, 2.0f, 14.0f, 26.0f, 6.0f, 18.0f, 30.0f, 10.0f, 22.0f, 34.0f, 3.0f, 15.0f, 27.0f, 7.0f, 19.0f, 31.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 40.0f, 52.0f, 64.0f, 44.0f, 56.0f, 68.0f, 37.0f, 49.0f, 61.0f, 41.0f, 53.0f, 65.0f, 45.0f, 57.0f, 69.0f, 38.0f, 50.0f, 62.0f, 42.0f, 54.0f, 66.0f, 46.0f, 58.0f, 70.0f, 39.0f, 51.0f, 63.0f, 43.0f, 55.0f, 67.0f, 47.0f, 59.0f, 71.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_dim3_axis1_neg = TestModelManager::get().add("channel_shuffle_relaxed_dim3_axis1_neg", get_test_model_relaxed_dim3_axis1_neg());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_relaxed_dim3_axis1_neg_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 4.0f, 16.0f, 28.0f, 8.0f, 20.0f, 32.0f, 1.0f, 13.0f, 25.0f, 5.0f, 17.0f, 29.0f, 9.0f, 21.0f, 33.0f, 2.0f, 14.0f, 26.0f, 6.0f, 18.0f, 30.0f, 10.0f, 22.0f, 34.0f, 3.0f, 15.0f, 27.0f, 7.0f, 19.0f, 31.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 40.0f, 52.0f, 64.0f, 44.0f, 56.0f, 68.0f, 37.0f, 49.0f, 61.0f, 41.0f, 53.0f, 65.0f, 45.0f, 57.0f, 69.0f, 38.0f, 50.0f, 62.0f, 42.0f, 54.0f, 66.0f, 46.0f, 58.0f, 70.0f, 39.0f, 51.0f, 63.0f, 43.0f, 55.0f, 67.0f, 47.0f, 59.0f, 71.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 1.0f, 13.0f, 25.0f, 2.0f, 14.0f, 26.0f, 3.0f, 15.0f, 27.0f, 4.0f, 16.0f, 28.0f, 5.0f, 17.0f, 29.0f, 6.0f, 18.0f, 30.0f, 7.0f, 19.0f, 31.0f, 8.0f, 20.0f, 32.0f, 9.0f, 21.0f, 33.0f, 10.0f, 22.0f, 34.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 37.0f, 49.0f, 61.0f, 38.0f, 50.0f, 62.0f, 39.0f, 51.0f, 63.0f, 40.0f, 52.0f, 64.0f, 41.0f, 53.0f, 65.0f, 42.0f, 54.0f, 66.0f, 43.0f, 55.0f, 67.0f, 44.0f, 56.0f, 68.0f, 45.0f, 57.0f, 69.0f, 46.0f, 58.0f, 70.0f, 47.0f, 59.0f, 71.0f})
                        }, { // placeholder31
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_dim3_axis1_neg_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_relaxed_dim3_axis1_neg_all_inputs_as_internal", get_test_model_relaxed_dim3_axis1_neg_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_relaxed_dim3_axis2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 35.0f, 36.0f, 37.0f, 38.0f, 39.0f, 40.0f, 41.0f, 42.0f, 43.0f, 44.0f, 45.0f, 46.0f, 47.0f, 48.0f, 49.0f, 50.0f, 51.0f, 52.0f, 53.0f, 54.0f, 55.0f, 56.0f, 57.0f, 58.0f, 59.0f, 60.0f, 61.0f, 62.0f, 63.0f, 64.0f, 65.0f, 66.0f, 67.0f, 68.0f, 69.0f, 70.0f, 71.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f, 12.0f, 16.0f, 20.0f, 13.0f, 17.0f, 21.0f, 14.0f, 18.0f, 22.0f, 15.0f, 19.0f, 23.0f, 24.0f, 28.0f, 32.0f, 25.0f, 29.0f, 33.0f, 26.0f, 30.0f, 34.0f, 27.0f, 31.0f, 35.0f, 36.0f, 40.0f, 44.0f, 37.0f, 41.0f, 45.0f, 38.0f, 42.0f, 46.0f, 39.0f, 43.0f, 47.0f, 48.0f, 52.0f, 56.0f, 49.0f, 53.0f, 57.0f, 50.0f, 54.0f, 58.0f, 51.0f, 55.0f, 59.0f, 60.0f, 64.0f, 68.0f, 61.0f, 65.0f, 69.0f, 62.0f, 66.0f, 70.0f, 63.0f, 67.0f, 71.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_dim3_axis2 = TestModelManager::get().add("channel_shuffle_relaxed_dim3_axis2", get_test_model_relaxed_dim3_axis2());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_relaxed_dim3_axis2_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f, 12.0f, 16.0f, 20.0f, 13.0f, 17.0f, 21.0f, 14.0f, 18.0f, 22.0f, 15.0f, 19.0f, 23.0f, 24.0f, 28.0f, 32.0f, 25.0f, 29.0f, 33.0f, 26.0f, 30.0f, 34.0f, 27.0f, 31.0f, 35.0f, 36.0f, 40.0f, 44.0f, 37.0f, 41.0f, 45.0f, 38.0f, 42.0f, 46.0f, 39.0f, 43.0f, 47.0f, 48.0f, 52.0f, 56.0f, 49.0f, 53.0f, 57.0f, 50.0f, 54.0f, 58.0f, 51.0f, 55.0f, 59.0f, 60.0f, 64.0f, 68.0f, 61.0f, 65.0f, 69.0f, 62.0f, 66.0f, 70.0f, 63.0f, 67.0f, 71.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 35.0f, 36.0f, 37.0f, 38.0f, 39.0f, 40.0f, 41.0f, 42.0f, 43.0f, 44.0f, 45.0f, 46.0f, 47.0f, 48.0f, 49.0f, 50.0f, 51.0f, 52.0f, 53.0f, 54.0f, 55.0f, 56.0f, 57.0f, 58.0f, 59.0f, 60.0f, 61.0f, 62.0f, 63.0f, 64.0f, 65.0f, 66.0f, 67.0f, 68.0f, 69.0f, 70.0f, 71.0f})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_dim3_axis2_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_relaxed_dim3_axis2_all_inputs_as_internal", get_test_model_relaxed_dim3_axis2_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_relaxed_dim3_axis2_neg() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 35.0f, 36.0f, 37.0f, 38.0f, 39.0f, 40.0f, 41.0f, 42.0f, 43.0f, 44.0f, 45.0f, 46.0f, 47.0f, 48.0f, 49.0f, 50.0f, 51.0f, 52.0f, 53.0f, 54.0f, 55.0f, 56.0f, 57.0f, 58.0f, 59.0f, 60.0f, 61.0f, 62.0f, 63.0f, 64.0f, 65.0f, 66.0f, 67.0f, 68.0f, 69.0f, 70.0f, 71.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f, 12.0f, 16.0f, 20.0f, 13.0f, 17.0f, 21.0f, 14.0f, 18.0f, 22.0f, 15.0f, 19.0f, 23.0f, 24.0f, 28.0f, 32.0f, 25.0f, 29.0f, 33.0f, 26.0f, 30.0f, 34.0f, 27.0f, 31.0f, 35.0f, 36.0f, 40.0f, 44.0f, 37.0f, 41.0f, 45.0f, 38.0f, 42.0f, 46.0f, 39.0f, 43.0f, 47.0f, 48.0f, 52.0f, 56.0f, 49.0f, 53.0f, 57.0f, 50.0f, 54.0f, 58.0f, 51.0f, 55.0f, 59.0f, 60.0f, 64.0f, 68.0f, 61.0f, 65.0f, 69.0f, 62.0f, 66.0f, 70.0f, 63.0f, 67.0f, 71.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_dim3_axis2_neg = TestModelManager::get().add("channel_shuffle_relaxed_dim3_axis2_neg", get_test_model_relaxed_dim3_axis2_neg());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_relaxed_dim3_axis2_neg_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f, 12.0f, 16.0f, 20.0f, 13.0f, 17.0f, 21.0f, 14.0f, 18.0f, 22.0f, 15.0f, 19.0f, 23.0f, 24.0f, 28.0f, 32.0f, 25.0f, 29.0f, 33.0f, 26.0f, 30.0f, 34.0f, 27.0f, 31.0f, 35.0f, 36.0f, 40.0f, 44.0f, 37.0f, 41.0f, 45.0f, 38.0f, 42.0f, 46.0f, 39.0f, 43.0f, 47.0f, 48.0f, 52.0f, 56.0f, 49.0f, 53.0f, 57.0f, 50.0f, 54.0f, 58.0f, 51.0f, 55.0f, 59.0f, 60.0f, 64.0f, 68.0f, 61.0f, 65.0f, 69.0f, 62.0f, 66.0f, 70.0f, 63.0f, 67.0f, 71.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 35.0f, 36.0f, 37.0f, 38.0f, 39.0f, 40.0f, 41.0f, 42.0f, 43.0f, 44.0f, 45.0f, 46.0f, 47.0f, 48.0f, 49.0f, 50.0f, 51.0f, 52.0f, 53.0f, 54.0f, 55.0f, 56.0f, 57.0f, 58.0f, 59.0f, 60.0f, 61.0f, 62.0f, 63.0f, 64.0f, 65.0f, 66.0f, 67.0f, 68.0f, 69.0f, 70.0f, 71.0f})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_dim3_axis2_neg_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_relaxed_dim3_axis2_neg_all_inputs_as_internal", get_test_model_relaxed_dim3_axis2_neg_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_relaxed_dim2_axis0() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 1.0f, 13.0f, 25.0f, 2.0f, 14.0f, 26.0f, 3.0f, 15.0f, 27.0f, 4.0f, 16.0f, 28.0f, 5.0f, 17.0f, 29.0f, 6.0f, 18.0f, 30.0f, 7.0f, 19.0f, 31.0f, 8.0f, 20.0f, 32.0f, 9.0f, 21.0f, 33.0f, 10.0f, 22.0f, 34.0f, 11.0f, 23.0f, 35.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 4.0f, 16.0f, 28.0f, 8.0f, 20.0f, 32.0f, 1.0f, 13.0f, 25.0f, 5.0f, 17.0f, 29.0f, 9.0f, 21.0f, 33.0f, 2.0f, 14.0f, 26.0f, 6.0f, 18.0f, 30.0f, 10.0f, 22.0f, 34.0f, 3.0f, 15.0f, 27.0f, 7.0f, 19.0f, 31.0f, 11.0f, 23.0f, 35.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_dim2_axis0 = TestModelManager::get().add("channel_shuffle_relaxed_dim2_axis0", get_test_model_relaxed_dim2_axis0());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_relaxed_dim2_axis0_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 4.0f, 16.0f, 28.0f, 8.0f, 20.0f, 32.0f, 1.0f, 13.0f, 25.0f, 5.0f, 17.0f, 29.0f, 9.0f, 21.0f, 33.0f, 2.0f, 14.0f, 26.0f, 6.0f, 18.0f, 30.0f, 10.0f, 22.0f, 34.0f, 3.0f, 15.0f, 27.0f, 7.0f, 19.0f, 31.0f, 11.0f, 23.0f, 35.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 1.0f, 13.0f, 25.0f, 2.0f, 14.0f, 26.0f, 3.0f, 15.0f, 27.0f, 4.0f, 16.0f, 28.0f, 5.0f, 17.0f, 29.0f, 6.0f, 18.0f, 30.0f, 7.0f, 19.0f, 31.0f, 8.0f, 20.0f, 32.0f, 9.0f, 21.0f, 33.0f, 10.0f, 22.0f, 34.0f, 11.0f, 23.0f, 35.0f})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_dim2_axis0_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_relaxed_dim2_axis0_all_inputs_as_internal", get_test_model_relaxed_dim2_axis0_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_relaxed_dim2_axis0_neg() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 1.0f, 13.0f, 25.0f, 2.0f, 14.0f, 26.0f, 3.0f, 15.0f, 27.0f, 4.0f, 16.0f, 28.0f, 5.0f, 17.0f, 29.0f, 6.0f, 18.0f, 30.0f, 7.0f, 19.0f, 31.0f, 8.0f, 20.0f, 32.0f, 9.0f, 21.0f, 33.0f, 10.0f, 22.0f, 34.0f, 11.0f, 23.0f, 35.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 4.0f, 16.0f, 28.0f, 8.0f, 20.0f, 32.0f, 1.0f, 13.0f, 25.0f, 5.0f, 17.0f, 29.0f, 9.0f, 21.0f, 33.0f, 2.0f, 14.0f, 26.0f, 6.0f, 18.0f, 30.0f, 10.0f, 22.0f, 34.0f, 3.0f, 15.0f, 27.0f, 7.0f, 19.0f, 31.0f, 11.0f, 23.0f, 35.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_dim2_axis0_neg = TestModelManager::get().add("channel_shuffle_relaxed_dim2_axis0_neg", get_test_model_relaxed_dim2_axis0_neg());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_relaxed_dim2_axis0_neg_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 4.0f, 16.0f, 28.0f, 8.0f, 20.0f, 32.0f, 1.0f, 13.0f, 25.0f, 5.0f, 17.0f, 29.0f, 9.0f, 21.0f, 33.0f, 2.0f, 14.0f, 26.0f, 6.0f, 18.0f, 30.0f, 10.0f, 22.0f, 34.0f, 3.0f, 15.0f, 27.0f, 7.0f, 19.0f, 31.0f, 11.0f, 23.0f, 35.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 12.0f, 24.0f, 1.0f, 13.0f, 25.0f, 2.0f, 14.0f, 26.0f, 3.0f, 15.0f, 27.0f, 4.0f, 16.0f, 28.0f, 5.0f, 17.0f, 29.0f, 6.0f, 18.0f, 30.0f, 7.0f, 19.0f, 31.0f, 8.0f, 20.0f, 32.0f, 9.0f, 21.0f, 33.0f, 10.0f, 22.0f, 34.0f, 11.0f, 23.0f, 35.0f})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_dim2_axis0_neg_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_relaxed_dim2_axis0_neg_all_inputs_as_internal", get_test_model_relaxed_dim2_axis0_neg_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_relaxed_dim2_axis1() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 35.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f, 12.0f, 16.0f, 20.0f, 13.0f, 17.0f, 21.0f, 14.0f, 18.0f, 22.0f, 15.0f, 19.0f, 23.0f, 24.0f, 28.0f, 32.0f, 25.0f, 29.0f, 33.0f, 26.0f, 30.0f, 34.0f, 27.0f, 31.0f, 35.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_dim2_axis1 = TestModelManager::get().add("channel_shuffle_relaxed_dim2_axis1", get_test_model_relaxed_dim2_axis1());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_relaxed_dim2_axis1_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f, 12.0f, 16.0f, 20.0f, 13.0f, 17.0f, 21.0f, 14.0f, 18.0f, 22.0f, 15.0f, 19.0f, 23.0f, 24.0f, 28.0f, 32.0f, 25.0f, 29.0f, 33.0f, 26.0f, 30.0f, 34.0f, 27.0f, 31.0f, 35.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 35.0f})
                        }, { // placeholder36
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_dim2_axis1_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_relaxed_dim2_axis1_all_inputs_as_internal", get_test_model_relaxed_dim2_axis1_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_relaxed_dim2_axis1_neg() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 35.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f, 12.0f, 16.0f, 20.0f, 13.0f, 17.0f, 21.0f, 14.0f, 18.0f, 22.0f, 15.0f, 19.0f, 23.0f, 24.0f, 28.0f, 32.0f, 25.0f, 29.0f, 33.0f, 26.0f, 30.0f, 34.0f, 27.0f, 31.0f, 35.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_dim2_axis1_neg = TestModelManager::get().add("channel_shuffle_relaxed_dim2_axis1_neg", get_test_model_relaxed_dim2_axis1_neg());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_relaxed_dim2_axis1_neg_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f, 12.0f, 16.0f, 20.0f, 13.0f, 17.0f, 21.0f, 14.0f, 18.0f, 22.0f, 15.0f, 19.0f, 23.0f, 24.0f, 28.0f, 32.0f, 25.0f, 29.0f, 33.0f, 26.0f, 30.0f, 34.0f, 27.0f, 31.0f, 35.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 35.0f})
                        }, { // placeholder37
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_dim2_axis1_neg_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_relaxed_dim2_axis1_neg_all_inputs_as_internal", get_test_model_relaxed_dim2_axis1_neg_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_relaxed_dim1_axis0() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_dim1_axis0 = TestModelManager::get().add("channel_shuffle_relaxed_dim1_axis0", get_test_model_relaxed_dim1_axis0());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_relaxed_dim1_axis0_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f})
                        }, { // placeholder38
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_dim1_axis0_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_relaxed_dim1_axis0_all_inputs_as_internal", get_test_model_relaxed_dim1_axis0_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_relaxed_dim1_axis0_neg() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {0},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_dim1_axis0_neg = TestModelManager::get().add("channel_shuffle_relaxed_dim1_axis0_neg", get_test_model_relaxed_dim1_axis0_neg());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_relaxed_dim1_axis0_neg_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
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
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f})
                        }, { // placeholder39
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
                            .inputs = {0, 1, 2},
                            .outputs = {3}
                        }},
                .inputIndexes = {4},
                .outputIndexes = {3}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_dim1_axis0_neg_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_relaxed_dim1_axis0_neg_all_inputs_as_internal", get_test_model_relaxed_dim1_axis0_neg_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_quant8_dim4_axis0() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {12, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 255, 255, 255, 255, 255, 255, 255, 255, 255, 132, 180, 228, 255, 255, 255, 255, 255, 255, 255, 255, 255, 136, 184, 232, 255, 255, 255, 255, 255, 255, 255, 255, 255, 140, 188, 236, 255, 255, 255, 255, 255, 255, 255, 255, 255, 144, 192, 240, 255, 255, 255, 255, 255, 255, 255, 255, 255, 148, 196, 244, 255, 255, 255, 255, 255, 255, 255, 255, 255, 152, 200, 248, 255, 255, 255, 255, 255, 255, 255, 255, 255, 156, 204, 252, 255, 255, 255, 255, 255, 255, 255, 255, 255, 160, 208, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 164, 212, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 168, 216, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 172, 220, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {12, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 255, 255, 255, 255, 255, 255, 255, 255, 255, 144, 192, 240, 255, 255, 255, 255, 255, 255, 255, 255, 255, 160, 208, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 132, 180, 228, 255, 255, 255, 255, 255, 255, 255, 255, 255, 148, 196, 244, 255, 255, 255, 255, 255, 255, 255, 255, 255, 164, 212, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 136, 184, 232, 255, 255, 255, 255, 255, 255, 255, 255, 255, 152, 200, 248, 255, 255, 255, 255, 255, 255, 255, 255, 255, 168, 216, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 140, 188, 236, 255, 255, 255, 255, 255, 255, 255, 255, 255, 156, 204, 252, 255, 255, 255, 255, 255, 255, 255, 255, 255, 172, 220, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_dim4_axis0 = TestModelManager::get().add("channel_shuffle_quant8_dim4_axis0", get_test_model_quant8_dim4_axis0());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_quant8_dim4_axis0_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {12, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {12, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 255, 255, 255, 255, 255, 255, 255, 255, 255, 144, 192, 240, 255, 255, 255, 255, 255, 255, 255, 255, 255, 160, 208, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 132, 180, 228, 255, 255, 255, 255, 255, 255, 255, 255, 255, 148, 196, 244, 255, 255, 255, 255, 255, 255, 255, 255, 255, 164, 212, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 136, 184, 232, 255, 255, 255, 255, 255, 255, 255, 255, 255, 152, 200, 248, 255, 255, 255, 255, 255, 255, 255, 255, 255, 168, 216, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 140, 188, 236, 255, 255, 255, 255, 255, 255, 255, 255, 255, 156, 204, 252, 255, 255, 255, 255, 255, 255, 255, 255, 255, 172, 220, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {12, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 255, 255, 255, 255, 255, 255, 255, 255, 255, 132, 180, 228, 255, 255, 255, 255, 255, 255, 255, 255, 255, 136, 184, 232, 255, 255, 255, 255, 255, 255, 255, 255, 255, 140, 188, 236, 255, 255, 255, 255, 255, 255, 255, 255, 255, 144, 192, 240, 255, 255, 255, 255, 255, 255, 255, 255, 255, 148, 196, 244, 255, 255, 255, 255, 255, 255, 255, 255, 255, 152, 200, 248, 255, 255, 255, 255, 255, 255, 255, 255, 255, 156, 204, 252, 255, 255, 255, 255, 255, 255, 255, 255, 255, 160, 208, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 164, 212, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 168, 216, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 172, 220, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder40
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_dim4_axis0_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_quant8_dim4_axis0_all_inputs_as_internal", get_test_model_quant8_dim4_axis0_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_quant8_dim4_axis0_neg() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {12, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 255, 255, 255, 255, 255, 255, 255, 255, 255, 132, 180, 228, 255, 255, 255, 255, 255, 255, 255, 255, 255, 136, 184, 232, 255, 255, 255, 255, 255, 255, 255, 255, 255, 140, 188, 236, 255, 255, 255, 255, 255, 255, 255, 255, 255, 144, 192, 240, 255, 255, 255, 255, 255, 255, 255, 255, 255, 148, 196, 244, 255, 255, 255, 255, 255, 255, 255, 255, 255, 152, 200, 248, 255, 255, 255, 255, 255, 255, 255, 255, 255, 156, 204, 252, 255, 255, 255, 255, 255, 255, 255, 255, 255, 160, 208, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 164, 212, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 168, 216, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 172, 220, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {12, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 255, 255, 255, 255, 255, 255, 255, 255, 255, 144, 192, 240, 255, 255, 255, 255, 255, 255, 255, 255, 255, 160, 208, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 132, 180, 228, 255, 255, 255, 255, 255, 255, 255, 255, 255, 148, 196, 244, 255, 255, 255, 255, 255, 255, 255, 255, 255, 164, 212, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 136, 184, 232, 255, 255, 255, 255, 255, 255, 255, 255, 255, 152, 200, 248, 255, 255, 255, 255, 255, 255, 255, 255, 255, 168, 216, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 140, 188, 236, 255, 255, 255, 255, 255, 255, 255, 255, 255, 156, 204, 252, 255, 255, 255, 255, 255, 255, 255, 255, 255, 172, 220, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_dim4_axis0_neg = TestModelManager::get().add("channel_shuffle_quant8_dim4_axis0_neg", get_test_model_quant8_dim4_axis0_neg());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_quant8_dim4_axis0_neg_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {12, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {12, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 255, 255, 255, 255, 255, 255, 255, 255, 255, 144, 192, 240, 255, 255, 255, 255, 255, 255, 255, 255, 255, 160, 208, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 132, 180, 228, 255, 255, 255, 255, 255, 255, 255, 255, 255, 148, 196, 244, 255, 255, 255, 255, 255, 255, 255, 255, 255, 164, 212, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 136, 184, 232, 255, 255, 255, 255, 255, 255, 255, 255, 255, 152, 200, 248, 255, 255, 255, 255, 255, 255, 255, 255, 255, 168, 216, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 140, 188, 236, 255, 255, 255, 255, 255, 255, 255, 255, 255, 156, 204, 252, 255, 255, 255, 255, 255, 255, 255, 255, 255, 172, 220, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {12, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 255, 255, 255, 255, 255, 255, 255, 255, 255, 132, 180, 228, 255, 255, 255, 255, 255, 255, 255, 255, 255, 136, 184, 232, 255, 255, 255, 255, 255, 255, 255, 255, 255, 140, 188, 236, 255, 255, 255, 255, 255, 255, 255, 255, 255, 144, 192, 240, 255, 255, 255, 255, 255, 255, 255, 255, 255, 148, 196, 244, 255, 255, 255, 255, 255, 255, 255, 255, 255, 152, 200, 248, 255, 255, 255, 255, 255, 255, 255, 255, 255, 156, 204, 252, 255, 255, 255, 255, 255, 255, 255, 255, 255, 160, 208, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 164, 212, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 168, 216, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 172, 220, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder41
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_dim4_axis0_neg_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_quant8_dim4_axis0_neg_all_inputs_as_internal", get_test_model_quant8_dim4_axis0_neg_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_quant8_dim4_axis1() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 255, 255, 255, 132, 180, 228, 255, 255, 255, 136, 184, 232, 255, 255, 255, 140, 188, 236, 255, 255, 255, 144, 192, 240, 255, 255, 255, 148, 196, 244, 255, 255, 255, 152, 200, 248, 255, 255, 255, 156, 204, 252, 255, 255, 255, 160, 208, 255, 255, 255, 255, 164, 212, 255, 255, 255, 255, 168, 216, 255, 255, 255, 255, 172, 220, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 12, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 255, 255, 255, 144, 192, 240, 255, 255, 255, 160, 208, 255, 255, 255, 255, 132, 180, 228, 255, 255, 255, 148, 196, 244, 255, 255, 255, 164, 212, 255, 255, 255, 255, 136, 184, 232, 255, 255, 255, 152, 200, 248, 255, 255, 255, 168, 216, 255, 255, 255, 255, 140, 188, 236, 255, 255, 255, 156, 204, 252, 255, 255, 255, 172, 220, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_dim4_axis1 = TestModelManager::get().add("channel_shuffle_quant8_dim4_axis1", get_test_model_quant8_dim4_axis1());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_quant8_dim4_axis1_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 12, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 255, 255, 255, 144, 192, 240, 255, 255, 255, 160, 208, 255, 255, 255, 255, 132, 180, 228, 255, 255, 255, 148, 196, 244, 255, 255, 255, 164, 212, 255, 255, 255, 255, 136, 184, 232, 255, 255, 255, 152, 200, 248, 255, 255, 255, 168, 216, 255, 255, 255, 255, 140, 188, 236, 255, 255, 255, 156, 204, 252, 255, 255, 255, 172, 220, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 255, 255, 255, 132, 180, 228, 255, 255, 255, 136, 184, 232, 255, 255, 255, 140, 188, 236, 255, 255, 255, 144, 192, 240, 255, 255, 255, 148, 196, 244, 255, 255, 255, 152, 200, 248, 255, 255, 255, 156, 204, 252, 255, 255, 255, 160, 208, 255, 255, 255, 255, 164, 212, 255, 255, 255, 255, 168, 216, 255, 255, 255, 255, 172, 220, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder42
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_dim4_axis1_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_quant8_dim4_axis1_all_inputs_as_internal", get_test_model_quant8_dim4_axis1_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_quant8_dim4_axis1_neg() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 255, 255, 255, 132, 180, 228, 255, 255, 255, 136, 184, 232, 255, 255, 255, 140, 188, 236, 255, 255, 255, 144, 192, 240, 255, 255, 255, 148, 196, 244, 255, 255, 255, 152, 200, 248, 255, 255, 255, 156, 204, 252, 255, 255, 255, 160, 208, 255, 255, 255, 255, 164, 212, 255, 255, 255, 255, 168, 216, 255, 255, 255, 255, 172, 220, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 12, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 255, 255, 255, 144, 192, 240, 255, 255, 255, 160, 208, 255, 255, 255, 255, 132, 180, 228, 255, 255, 255, 148, 196, 244, 255, 255, 255, 164, 212, 255, 255, 255, 255, 136, 184, 232, 255, 255, 255, 152, 200, 248, 255, 255, 255, 168, 216, 255, 255, 255, 255, 140, 188, 236, 255, 255, 255, 156, 204, 252, 255, 255, 255, 172, 220, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_dim4_axis1_neg = TestModelManager::get().add("channel_shuffle_quant8_dim4_axis1_neg", get_test_model_quant8_dim4_axis1_neg());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_quant8_dim4_axis1_neg_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 12, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 255, 255, 255, 144, 192, 240, 255, 255, 255, 160, 208, 255, 255, 255, 255, 132, 180, 228, 255, 255, 255, 148, 196, 244, 255, 255, 255, 164, 212, 255, 255, 255, 255, 136, 184, 232, 255, 255, 255, 152, 200, 248, 255, 255, 255, 168, 216, 255, 255, 255, 255, 140, 188, 236, 255, 255, 255, 156, 204, 252, 255, 255, 255, 172, 220, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 255, 255, 255, 132, 180, 228, 255, 255, 255, 136, 184, 232, 255, 255, 255, 140, 188, 236, 255, 255, 255, 144, 192, 240, 255, 255, 255, 148, 196, 244, 255, 255, 255, 152, 200, 248, 255, 255, 255, 156, 204, 252, 255, 255, 255, 160, 208, 255, 255, 255, 255, 164, 212, 255, 255, 255, 255, 168, 216, 255, 255, 255, 255, 172, 220, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder43
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_dim4_axis1_neg_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_quant8_dim4_axis1_neg_all_inputs_as_internal", get_test_model_quant8_dim4_axis1_neg_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_quant8_dim4_axis2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 132, 180, 228, 136, 184, 232, 140, 188, 236, 144, 192, 240, 148, 196, 244, 152, 200, 248, 156, 204, 252, 160, 208, 255, 164, 212, 255, 168, 216, 255, 172, 220, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2, 12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 144, 192, 240, 160, 208, 255, 132, 180, 228, 148, 196, 244, 164, 212, 255, 136, 184, 232, 152, 200, 248, 168, 216, 255, 140, 188, 236, 156, 204, 252, 172, 220, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_dim4_axis2 = TestModelManager::get().add("channel_shuffle_quant8_dim4_axis2", get_test_model_quant8_dim4_axis2());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_quant8_dim4_axis2_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2, 12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 144, 192, 240, 160, 208, 255, 132, 180, 228, 148, 196, 244, 164, 212, 255, 136, 184, 232, 152, 200, 248, 168, 216, 255, 140, 188, 236, 156, 204, 252, 172, 220, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 132, 180, 228, 136, 184, 232, 140, 188, 236, 144, 192, 240, 148, 196, 244, 152, 200, 248, 156, 204, 252, 160, 208, 255, 164, 212, 255, 168, 216, 255, 172, 220, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder44
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_dim4_axis2_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_quant8_dim4_axis2_all_inputs_as_internal", get_test_model_quant8_dim4_axis2_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_quant8_dim4_axis2_neg() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 132, 180, 228, 136, 184, 232, 140, 188, 236, 144, 192, 240, 148, 196, 244, 152, 200, 248, 156, 204, 252, 160, 208, 255, 164, 212, 255, 168, 216, 255, 172, 220, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2, 12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 144, 192, 240, 160, 208, 255, 132, 180, 228, 148, 196, 244, 164, 212, 255, 136, 184, 232, 152, 200, 248, 168, 216, 255, 140, 188, 236, 156, 204, 252, 172, 220, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_dim4_axis2_neg = TestModelManager::get().add("channel_shuffle_quant8_dim4_axis2_neg", get_test_model_quant8_dim4_axis2_neg());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_quant8_dim4_axis2_neg_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2, 12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 144, 192, 240, 160, 208, 255, 132, 180, 228, 148, 196, 244, 164, 212, 255, 136, 184, 232, 152, 200, 248, 168, 216, 255, 140, 188, 236, 156, 204, 252, 172, 220, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 132, 180, 228, 136, 184, 232, 140, 188, 236, 144, 192, 240, 148, 196, 244, 152, 200, 248, 156, 204, 252, 160, 208, 255, 164, 212, 255, 168, 216, 255, 172, 220, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder45
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_dim4_axis2_neg_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_quant8_dim4_axis2_neg_all_inputs_as_internal", get_test_model_quant8_dim4_axis2_neg_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_quant8_dim4_axis3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 132, 136, 140, 144, 148, 152, 156, 160, 164, 168, 172, 176, 180, 184, 188, 192, 196, 200, 204, 208, 212, 216, 220, 224, 228, 232, 236, 240, 244, 248, 252, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2, 3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 144, 160, 132, 148, 164, 136, 152, 168, 140, 156, 172, 176, 192, 208, 180, 196, 212, 184, 200, 216, 188, 204, 220, 224, 240, 255, 228, 244, 255, 232, 248, 255, 236, 252, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_dim4_axis3 = TestModelManager::get().add("channel_shuffle_quant8_dim4_axis3", get_test_model_quant8_dim4_axis3());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_quant8_dim4_axis3_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2, 3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 144, 160, 132, 148, 164, 136, 152, 168, 140, 156, 172, 176, 192, 208, 180, 196, 212, 184, 200, 216, 188, 204, 220, 224, 240, 255, 228, 244, 255, 232, 248, 255, 236, 252, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 132, 136, 140, 144, 148, 152, 156, 160, 164, 168, 172, 176, 180, 184, 188, 192, 196, 200, 204, 208, 212, 216, 220, 224, 228, 232, 236, 240, 244, 248, 252, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder46
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_dim4_axis3_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_quant8_dim4_axis3_all_inputs_as_internal", get_test_model_quant8_dim4_axis3_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_quant8_dim4_axis3_neg() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 132, 136, 140, 144, 148, 152, 156, 160, 164, 168, 172, 176, 180, 184, 188, 192, 196, 200, 204, 208, 212, 216, 220, 224, 228, 232, 236, 240, 244, 248, 252, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2, 3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 144, 160, 132, 148, 164, 136, 152, 168, 140, 156, 172, 176, 192, 208, 180, 196, 212, 184, 200, 216, 188, 204, 220, 224, 240, 255, 228, 244, 255, 232, 248, 255, 236, 252, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_dim4_axis3_neg = TestModelManager::get().add("channel_shuffle_quant8_dim4_axis3_neg", get_test_model_quant8_dim4_axis3_neg());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_quant8_dim4_axis3_neg_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2, 3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 144, 160, 132, 148, 164, 136, 152, 168, 140, 156, 172, 176, 192, 208, 180, 196, 212, 184, 200, 216, 188, 204, 220, 224, 240, 255, 228, 244, 255, 232, 248, 255, 236, 252, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 132, 136, 140, 144, 148, 152, 156, 160, 164, 168, 172, 176, 180, 184, 188, 192, 196, 200, 204, 208, 212, 216, 220, 224, 228, 232, 236, 240, 244, 248, 252, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder47
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_dim4_axis3_neg_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_quant8_dim4_axis3_neg_all_inputs_as_internal", get_test_model_quant8_dim4_axis3_neg_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_quant8_dim3_axis0() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 255, 255, 255, 132, 180, 228, 255, 255, 255, 136, 184, 232, 255, 255, 255, 140, 188, 236, 255, 255, 255, 144, 192, 240, 255, 255, 255, 148, 196, 244, 255, 255, 255, 152, 200, 248, 255, 255, 255, 156, 204, 252, 255, 255, 255, 160, 208, 255, 255, 255, 255, 164, 212, 255, 255, 255, 255, 168, 216, 255, 255, 255, 255, 172, 220, 255, 255, 255, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {12, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 255, 255, 255, 144, 192, 240, 255, 255, 255, 160, 208, 255, 255, 255, 255, 132, 180, 228, 255, 255, 255, 148, 196, 244, 255, 255, 255, 164, 212, 255, 255, 255, 255, 136, 184, 232, 255, 255, 255, 152, 200, 248, 255, 255, 255, 168, 216, 255, 255, 255, 255, 140, 188, 236, 255, 255, 255, 156, 204, 252, 255, 255, 255, 172, 220, 255, 255, 255, 255})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_dim3_axis0 = TestModelManager::get().add("channel_shuffle_quant8_dim3_axis0", get_test_model_quant8_dim3_axis0());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_quant8_dim3_axis0_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {12, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 255, 255, 255, 144, 192, 240, 255, 255, 255, 160, 208, 255, 255, 255, 255, 132, 180, 228, 255, 255, 255, 148, 196, 244, 255, 255, 255, 164, 212, 255, 255, 255, 255, 136, 184, 232, 255, 255, 255, 152, 200, 248, 255, 255, 255, 168, 216, 255, 255, 255, 255, 140, 188, 236, 255, 255, 255, 156, 204, 252, 255, 255, 255, 172, 220, 255, 255, 255, 255})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 255, 255, 255, 132, 180, 228, 255, 255, 255, 136, 184, 232, 255, 255, 255, 140, 188, 236, 255, 255, 255, 144, 192, 240, 255, 255, 255, 148, 196, 244, 255, 255, 255, 152, 200, 248, 255, 255, 255, 156, 204, 252, 255, 255, 255, 160, 208, 255, 255, 255, 255, 164, 212, 255, 255, 255, 255, 168, 216, 255, 255, 255, 255, 172, 220, 255, 255, 255, 255})
                        }, { // placeholder48
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_dim3_axis0_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_quant8_dim3_axis0_all_inputs_as_internal", get_test_model_quant8_dim3_axis0_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_quant8_dim3_axis0_neg() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 255, 255, 255, 132, 180, 228, 255, 255, 255, 136, 184, 232, 255, 255, 255, 140, 188, 236, 255, 255, 255, 144, 192, 240, 255, 255, 255, 148, 196, 244, 255, 255, 255, 152, 200, 248, 255, 255, 255, 156, 204, 252, 255, 255, 255, 160, 208, 255, 255, 255, 255, 164, 212, 255, 255, 255, 255, 168, 216, 255, 255, 255, 255, 172, 220, 255, 255, 255, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {12, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 255, 255, 255, 144, 192, 240, 255, 255, 255, 160, 208, 255, 255, 255, 255, 132, 180, 228, 255, 255, 255, 148, 196, 244, 255, 255, 255, 164, 212, 255, 255, 255, 255, 136, 184, 232, 255, 255, 255, 152, 200, 248, 255, 255, 255, 168, 216, 255, 255, 255, 255, 140, 188, 236, 255, 255, 255, 156, 204, 252, 255, 255, 255, 172, 220, 255, 255, 255, 255})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_dim3_axis0_neg = TestModelManager::get().add("channel_shuffle_quant8_dim3_axis0_neg", get_test_model_quant8_dim3_axis0_neg());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_quant8_dim3_axis0_neg_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {12, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 255, 255, 255, 144, 192, 240, 255, 255, 255, 160, 208, 255, 255, 255, 255, 132, 180, 228, 255, 255, 255, 148, 196, 244, 255, 255, 255, 164, 212, 255, 255, 255, 255, 136, 184, 232, 255, 255, 255, 152, 200, 248, 255, 255, 255, 168, 216, 255, 255, 255, 255, 140, 188, 236, 255, 255, 255, 156, 204, 252, 255, 255, 255, 172, 220, 255, 255, 255, 255})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 255, 255, 255, 132, 180, 228, 255, 255, 255, 136, 184, 232, 255, 255, 255, 140, 188, 236, 255, 255, 255, 144, 192, 240, 255, 255, 255, 148, 196, 244, 255, 255, 255, 152, 200, 248, 255, 255, 255, 156, 204, 252, 255, 255, 255, 160, 208, 255, 255, 255, 255, 164, 212, 255, 255, 255, 255, 168, 216, 255, 255, 255, 255, 172, 220, 255, 255, 255, 255})
                        }, { // placeholder49
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_dim3_axis0_neg_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_quant8_dim3_axis0_neg_all_inputs_as_internal", get_test_model_quant8_dim3_axis0_neg_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_quant8_dim3_axis1() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 132, 180, 228, 136, 184, 232, 140, 188, 236, 144, 192, 240, 148, 196, 244, 152, 200, 248, 156, 204, 252, 160, 208, 255, 164, 212, 255, 168, 216, 255, 172, 220, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 144, 192, 240, 160, 208, 255, 132, 180, 228, 148, 196, 244, 164, 212, 255, 136, 184, 232, 152, 200, 248, 168, 216, 255, 140, 188, 236, 156, 204, 252, 172, 220, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_dim3_axis1 = TestModelManager::get().add("channel_shuffle_quant8_dim3_axis1", get_test_model_quant8_dim3_axis1());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_quant8_dim3_axis1_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 144, 192, 240, 160, 208, 255, 132, 180, 228, 148, 196, 244, 164, 212, 255, 136, 184, 232, 152, 200, 248, 168, 216, 255, 140, 188, 236, 156, 204, 252, 172, 220, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 132, 180, 228, 136, 184, 232, 140, 188, 236, 144, 192, 240, 148, 196, 244, 152, 200, 248, 156, 204, 252, 160, 208, 255, 164, 212, 255, 168, 216, 255, 172, 220, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder50
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_dim3_axis1_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_quant8_dim3_axis1_all_inputs_as_internal", get_test_model_quant8_dim3_axis1_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_quant8_dim3_axis1_neg() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 132, 180, 228, 136, 184, 232, 140, 188, 236, 144, 192, 240, 148, 196, 244, 152, 200, 248, 156, 204, 252, 160, 208, 255, 164, 212, 255, 168, 216, 255, 172, 220, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 144, 192, 240, 160, 208, 255, 132, 180, 228, 148, 196, 244, 164, 212, 255, 136, 184, 232, 152, 200, 248, 168, 216, 255, 140, 188, 236, 156, 204, 252, 172, 220, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_dim3_axis1_neg = TestModelManager::get().add("channel_shuffle_quant8_dim3_axis1_neg", get_test_model_quant8_dim3_axis1_neg());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_quant8_dim3_axis1_neg_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 144, 192, 240, 160, 208, 255, 132, 180, 228, 148, 196, 244, 164, 212, 255, 136, 184, 232, 152, 200, 248, 168, 216, 255, 140, 188, 236, 156, 204, 252, 172, 220, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 132, 180, 228, 136, 184, 232, 140, 188, 236, 144, 192, 240, 148, 196, 244, 152, 200, 248, 156, 204, 252, 160, 208, 255, 164, 212, 255, 168, 216, 255, 172, 220, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder51
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_dim3_axis1_neg_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_quant8_dim3_axis1_neg_all_inputs_as_internal", get_test_model_quant8_dim3_axis1_neg_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_quant8_dim3_axis2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 132, 136, 140, 144, 148, 152, 156, 160, 164, 168, 172, 176, 180, 184, 188, 192, 196, 200, 204, 208, 212, 216, 220, 224, 228, 232, 236, 240, 244, 248, 252, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 144, 160, 132, 148, 164, 136, 152, 168, 140, 156, 172, 176, 192, 208, 180, 196, 212, 184, 200, 216, 188, 204, 220, 224, 240, 255, 228, 244, 255, 232, 248, 255, 236, 252, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_dim3_axis2 = TestModelManager::get().add("channel_shuffle_quant8_dim3_axis2", get_test_model_quant8_dim3_axis2());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_quant8_dim3_axis2_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 144, 160, 132, 148, 164, 136, 152, 168, 140, 156, 172, 176, 192, 208, 180, 196, 212, 184, 200, 216, 188, 204, 220, 224, 240, 255, 228, 244, 255, 232, 248, 255, 236, 252, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 132, 136, 140, 144, 148, 152, 156, 160, 164, 168, 172, 176, 180, 184, 188, 192, 196, 200, 204, 208, 212, 216, 220, 224, 228, 232, 236, 240, 244, 248, 252, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder52
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_dim3_axis2_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_quant8_dim3_axis2_all_inputs_as_internal", get_test_model_quant8_dim3_axis2_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_quant8_dim3_axis2_neg() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 132, 136, 140, 144, 148, 152, 156, 160, 164, 168, 172, 176, 180, 184, 188, 192, 196, 200, 204, 208, 212, 216, 220, 224, 228, 232, 236, 240, 244, 248, 252, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 144, 160, 132, 148, 164, 136, 152, 168, 140, 156, 172, 176, 192, 208, 180, 196, 212, 184, 200, 216, 188, 204, 220, 224, 240, 255, 228, 244, 255, 232, 248, 255, 236, 252, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_dim3_axis2_neg = TestModelManager::get().add("channel_shuffle_quant8_dim3_axis2_neg", get_test_model_quant8_dim3_axis2_neg());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_quant8_dim3_axis2_neg_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 144, 160, 132, 148, 164, 136, 152, 168, 140, 156, 172, 176, 192, 208, 180, 196, 212, 184, 200, 216, 188, 204, 220, 224, 240, 255, 228, 244, 255, 232, 248, 255, 236, 252, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 132, 136, 140, 144, 148, 152, 156, 160, 164, 168, 172, 176, 180, 184, 188, 192, 196, 200, 204, 208, 212, 216, 220, 224, 228, 232, 236, 240, 244, 248, 252, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255})
                        }, { // placeholder53
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_dim3_axis2_neg_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_quant8_dim3_axis2_neg_all_inputs_as_internal", get_test_model_quant8_dim3_axis2_neg_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_quant8_dim2_axis0() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 132, 180, 228, 136, 184, 232, 140, 188, 236, 144, 192, 240, 148, 196, 244, 152, 200, 248, 156, 204, 252, 160, 208, 255, 164, 212, 255, 168, 216, 255, 172, 220, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 144, 192, 240, 160, 208, 255, 132, 180, 228, 148, 196, 244, 164, 212, 255, 136, 184, 232, 152, 200, 248, 168, 216, 255, 140, 188, 236, 156, 204, 252, 172, 220, 255})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_dim2_axis0 = TestModelManager::get().add("channel_shuffle_quant8_dim2_axis0", get_test_model_quant8_dim2_axis0());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_quant8_dim2_axis0_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 144, 192, 240, 160, 208, 255, 132, 180, 228, 148, 196, 244, 164, 212, 255, 136, 184, 232, 152, 200, 248, 168, 216, 255, 140, 188, 236, 156, 204, 252, 172, 220, 255})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 132, 180, 228, 136, 184, 232, 140, 188, 236, 144, 192, 240, 148, 196, 244, 152, 200, 248, 156, 204, 252, 160, 208, 255, 164, 212, 255, 168, 216, 255, 172, 220, 255})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_dim2_axis0_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_quant8_dim2_axis0_all_inputs_as_internal", get_test_model_quant8_dim2_axis0_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_quant8_dim2_axis0_neg() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 132, 180, 228, 136, 184, 232, 140, 188, 236, 144, 192, 240, 148, 196, 244, 152, 200, 248, 156, 204, 252, 160, 208, 255, 164, 212, 255, 168, 216, 255, 172, 220, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 144, 192, 240, 160, 208, 255, 132, 180, 228, 148, 196, 244, 164, 212, 255, 136, 184, 232, 152, 200, 248, 168, 216, 255, 140, 188, 236, 156, 204, 252, 172, 220, 255})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_dim2_axis0_neg = TestModelManager::get().add("channel_shuffle_quant8_dim2_axis0_neg", get_test_model_quant8_dim2_axis0_neg());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_quant8_dim2_axis0_neg_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 144, 192, 240, 160, 208, 255, 132, 180, 228, 148, 196, 244, 164, 212, 255, 136, 184, 232, 152, 200, 248, 168, 216, 255, 140, 188, 236, 156, 204, 252, 172, 220, 255})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 176, 224, 132, 180, 228, 136, 184, 232, 140, 188, 236, 144, 192, 240, 148, 196, 244, 152, 200, 248, 156, 204, 252, 160, 208, 255, 164, 212, 255, 168, 216, 255, 172, 220, 255})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_dim2_axis0_neg_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_quant8_dim2_axis0_neg_all_inputs_as_internal", get_test_model_quant8_dim2_axis0_neg_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_quant8_dim2_axis1() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 132, 136, 140, 144, 148, 152, 156, 160, 164, 168, 172, 176, 180, 184, 188, 192, 196, 200, 204, 208, 212, 216, 220, 224, 228, 232, 236, 240, 244, 248, 252, 255, 255, 255, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 144, 160, 132, 148, 164, 136, 152, 168, 140, 156, 172, 176, 192, 208, 180, 196, 212, 184, 200, 216, 188, 204, 220, 224, 240, 255, 228, 244, 255, 232, 248, 255, 236, 252, 255})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_dim2_axis1 = TestModelManager::get().add("channel_shuffle_quant8_dim2_axis1", get_test_model_quant8_dim2_axis1());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_quant8_dim2_axis1_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 144, 160, 132, 148, 164, 136, 152, 168, 140, 156, 172, 176, 192, 208, 180, 196, 212, 184, 200, 216, 188, 204, 220, 224, 240, 255, 228, 244, 255, 232, 248, 255, 236, 252, 255})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 132, 136, 140, 144, 148, 152, 156, 160, 164, 168, 172, 176, 180, 184, 188, 192, 196, 200, 204, 208, 212, 216, 220, 224, 228, 232, 236, 240, 244, 248, 252, 255, 255, 255, 255})
                        }, { // placeholder56
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_dim2_axis1_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_quant8_dim2_axis1_all_inputs_as_internal", get_test_model_quant8_dim2_axis1_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_quant8_dim2_axis1_neg() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 132, 136, 140, 144, 148, 152, 156, 160, 164, 168, 172, 176, 180, 184, 188, 192, 196, 200, 204, 208, 212, 216, 220, 224, 228, 232, 236, 240, 244, 248, 252, 255, 255, 255, 255})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 144, 160, 132, 148, 164, 136, 152, 168, 140, 156, 172, 176, 192, 208, 180, 196, 212, 184, 200, 216, 188, 204, 220, 224, 240, 255, 228, 244, 255, 232, 248, 255, 236, 252, 255})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_dim2_axis1_neg = TestModelManager::get().add("channel_shuffle_quant8_dim2_axis1_neg", get_test_model_quant8_dim2_axis1_neg());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_quant8_dim2_axis1_neg_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 144, 160, 132, 148, 164, 136, 152, 168, 140, 156, 172, 176, 192, 208, 180, 196, 212, 184, 200, 216, 188, 204, 220, 224, 240, 255, 228, 244, 255, 232, 248, 255, 236, 252, 255})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 132, 136, 140, 144, 148, 152, 156, 160, 164, 168, 172, 176, 180, 184, 188, 192, 196, 200, 204, 208, 212, 216, 220, 224, 228, 232, 236, 240, 244, 248, 252, 255, 255, 255, 255})
                        }, { // placeholder57
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_dim2_axis1_neg_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_quant8_dim2_axis1_neg_all_inputs_as_internal", get_test_model_quant8_dim2_axis1_neg_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_quant8_dim1_axis0() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {12},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 132, 136, 140, 144, 148, 152, 156, 160, 164, 168, 172})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {12},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 144, 160, 132, 148, 164, 136, 152, 168, 140, 156, 172})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_dim1_axis0 = TestModelManager::get().add("channel_shuffle_quant8_dim1_axis0", get_test_model_quant8_dim1_axis0());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_quant8_dim1_axis0_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {12},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {12},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 144, 160, 132, 148, 164, 136, 152, 168, 140, 156, 172})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {12},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 132, 136, 140, 144, 148, 152, 156, 160, 164, 168, 172})
                        }, { // placeholder58
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_dim1_axis0_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_quant8_dim1_axis0_all_inputs_as_internal", get_test_model_quant8_dim1_axis0_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_quant8_dim1_axis0_neg() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {12},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 132, 136, 140, 144, 148, 152, 156, 160, 164, 168, 172})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {12},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 144, 160, 132, 148, 164, 136, 152, 168, 140, 156, 172})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_dim1_axis0_neg = TestModelManager::get().add("channel_shuffle_quant8_dim1_axis0_neg", get_test_model_quant8_dim1_axis0_neg());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_quant8_dim1_axis0_neg_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {12},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
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
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {12},
                            .numberOfConsumers = 0,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 144, 160, 132, 148, 164, 136, 152, 168, 140, 156, 172})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {12},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128, 132, 136, 140, 144, 148, 152, 156, 160, 164, 168, 172})
                        }, { // placeholder59
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.25f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_quant8_dim1_axis0_neg_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_quant8_dim1_axis0_neg_all_inputs_as_internal", get_test_model_quant8_dim1_axis0_neg_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_float16_dim4_axis0() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {12, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {12, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_dim4_axis0 = TestModelManager::get().add("channel_shuffle_float16_dim4_axis0", get_test_model_float16_dim4_axis0());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_float16_dim4_axis0_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {12, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {12, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {12, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_dim4_axis0_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_float16_dim4_axis0_all_inputs_as_internal", get_test_model_float16_dim4_axis0_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_float16_dim4_axis0_neg() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {12, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {12, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_dim4_axis0_neg = TestModelManager::get().add("channel_shuffle_float16_dim4_axis0_neg", get_test_model_float16_dim4_axis0_neg());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_float16_dim4_axis0_neg_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {12, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {12, 2, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {12, 2, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_dim4_axis0_neg_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_float16_dim4_axis0_neg_all_inputs_as_internal", get_test_model_float16_dim4_axis0_neg_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_float16_dim4_axis1() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 12, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_dim4_axis1 = TestModelManager::get().add("channel_shuffle_float16_dim4_axis1", get_test_model_float16_dim4_axis1());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_float16_dim4_axis1_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 12, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
                        }, { // placeholder62
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_dim4_axis1_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_float16_dim4_axis1_all_inputs_as_internal", get_test_model_float16_dim4_axis1_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_float16_dim4_axis1_neg() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 12, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_dim4_axis1_neg = TestModelManager::get().add("channel_shuffle_float16_dim4_axis1_neg", get_test_model_float16_dim4_axis1_neg());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_float16_dim4_axis1_neg_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 12, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 108.0f, 120.0f, 132.0f, 73.0f, 85.0f, 97.0f, 109.0f, 121.0f, 133.0f, 74.0f, 86.0f, 98.0f, 110.0f, 122.0f, 134.0f, 75.0f, 87.0f, 99.0f, 111.0f, 123.0f, 135.0f, 76.0f, 88.0f, 100.0f, 112.0f, 124.0f, 136.0f, 77.0f, 89.0f, 101.0f, 113.0f, 125.0f, 137.0f, 78.0f, 90.0f, 102.0f, 114.0f, 126.0f, 138.0f, 79.0f, 91.0f, 103.0f, 115.0f, 127.0f, 139.0f, 80.0f, 92.0f, 104.0f, 116.0f, 128.0f, 140.0f, 81.0f, 93.0f, 105.0f, 117.0f, 129.0f, 141.0f, 82.0f, 94.0f, 106.0f, 118.0f, 130.0f, 142.0f, 83.0f, 95.0f, 107.0f, 119.0f, 131.0f, 143.0f})
                        }, { // placeholder63
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_dim4_axis1_neg_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_float16_dim4_axis1_neg_all_inputs_as_internal", get_test_model_float16_dim4_axis1_neg_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_float16_dim4_axis2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 1.0f, 13.0f, 25.0f, 2.0f, 14.0f, 26.0f, 3.0f, 15.0f, 27.0f, 4.0f, 16.0f, 28.0f, 5.0f, 17.0f, 29.0f, 6.0f, 18.0f, 30.0f, 7.0f, 19.0f, 31.0f, 8.0f, 20.0f, 32.0f, 9.0f, 21.0f, 33.0f, 10.0f, 22.0f, 34.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 37.0f, 49.0f, 61.0f, 38.0f, 50.0f, 62.0f, 39.0f, 51.0f, 63.0f, 40.0f, 52.0f, 64.0f, 41.0f, 53.0f, 65.0f, 42.0f, 54.0f, 66.0f, 43.0f, 55.0f, 67.0f, 44.0f, 56.0f, 68.0f, 45.0f, 57.0f, 69.0f, 46.0f, 58.0f, 70.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 73.0f, 85.0f, 97.0f, 74.0f, 86.0f, 98.0f, 75.0f, 87.0f, 99.0f, 76.0f, 88.0f, 100.0f, 77.0f, 89.0f, 101.0f, 78.0f, 90.0f, 102.0f, 79.0f, 91.0f, 103.0f, 80.0f, 92.0f, 104.0f, 81.0f, 93.0f, 105.0f, 82.0f, 94.0f, 106.0f, 83.0f, 95.0f, 107.0f, 108.0f, 120.0f, 132.0f, 109.0f, 121.0f, 133.0f, 110.0f, 122.0f, 134.0f, 111.0f, 123.0f, 135.0f, 112.0f, 124.0f, 136.0f, 113.0f, 125.0f, 137.0f, 114.0f, 126.0f, 138.0f, 115.0f, 127.0f, 139.0f, 116.0f, 128.0f, 140.0f, 117.0f, 129.0f, 141.0f, 118.0f, 130.0f, 142.0f, 119.0f, 131.0f, 143.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2, 12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 4.0f, 16.0f, 28.0f, 8.0f, 20.0f, 32.0f, 1.0f, 13.0f, 25.0f, 5.0f, 17.0f, 29.0f, 9.0f, 21.0f, 33.0f, 2.0f, 14.0f, 26.0f, 6.0f, 18.0f, 30.0f, 10.0f, 22.0f, 34.0f, 3.0f, 15.0f, 27.0f, 7.0f, 19.0f, 31.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 40.0f, 52.0f, 64.0f, 44.0f, 56.0f, 68.0f, 37.0f, 49.0f, 61.0f, 41.0f, 53.0f, 65.0f, 45.0f, 57.0f, 69.0f, 38.0f, 50.0f, 62.0f, 42.0f, 54.0f, 66.0f, 46.0f, 58.0f, 70.0f, 39.0f, 51.0f, 63.0f, 43.0f, 55.0f, 67.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 76.0f, 88.0f, 100.0f, 80.0f, 92.0f, 104.0f, 73.0f, 85.0f, 97.0f, 77.0f, 89.0f, 101.0f, 81.0f, 93.0f, 105.0f, 74.0f, 86.0f, 98.0f, 78.0f, 90.0f, 102.0f, 82.0f, 94.0f, 106.0f, 75.0f, 87.0f, 99.0f, 79.0f, 91.0f, 103.0f, 83.0f, 95.0f, 107.0f, 108.0f, 120.0f, 132.0f, 112.0f, 124.0f, 136.0f, 116.0f, 128.0f, 140.0f, 109.0f, 121.0f, 133.0f, 113.0f, 125.0f, 137.0f, 117.0f, 129.0f, 141.0f, 110.0f, 122.0f, 134.0f, 114.0f, 126.0f, 138.0f, 118.0f, 130.0f, 142.0f, 111.0f, 123.0f, 135.0f, 115.0f, 127.0f, 139.0f, 119.0f, 131.0f, 143.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_dim4_axis2 = TestModelManager::get().add("channel_shuffle_float16_dim4_axis2", get_test_model_float16_dim4_axis2());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_float16_dim4_axis2_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2, 12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 4.0f, 16.0f, 28.0f, 8.0f, 20.0f, 32.0f, 1.0f, 13.0f, 25.0f, 5.0f, 17.0f, 29.0f, 9.0f, 21.0f, 33.0f, 2.0f, 14.0f, 26.0f, 6.0f, 18.0f, 30.0f, 10.0f, 22.0f, 34.0f, 3.0f, 15.0f, 27.0f, 7.0f, 19.0f, 31.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 40.0f, 52.0f, 64.0f, 44.0f, 56.0f, 68.0f, 37.0f, 49.0f, 61.0f, 41.0f, 53.0f, 65.0f, 45.0f, 57.0f, 69.0f, 38.0f, 50.0f, 62.0f, 42.0f, 54.0f, 66.0f, 46.0f, 58.0f, 70.0f, 39.0f, 51.0f, 63.0f, 43.0f, 55.0f, 67.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 76.0f, 88.0f, 100.0f, 80.0f, 92.0f, 104.0f, 73.0f, 85.0f, 97.0f, 77.0f, 89.0f, 101.0f, 81.0f, 93.0f, 105.0f, 74.0f, 86.0f, 98.0f, 78.0f, 90.0f, 102.0f, 82.0f, 94.0f, 106.0f, 75.0f, 87.0f, 99.0f, 79.0f, 91.0f, 103.0f, 83.0f, 95.0f, 107.0f, 108.0f, 120.0f, 132.0f, 112.0f, 124.0f, 136.0f, 116.0f, 128.0f, 140.0f, 109.0f, 121.0f, 133.0f, 113.0f, 125.0f, 137.0f, 117.0f, 129.0f, 141.0f, 110.0f, 122.0f, 134.0f, 114.0f, 126.0f, 138.0f, 118.0f, 130.0f, 142.0f, 111.0f, 123.0f, 135.0f, 115.0f, 127.0f, 139.0f, 119.0f, 131.0f, 143.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 1.0f, 13.0f, 25.0f, 2.0f, 14.0f, 26.0f, 3.0f, 15.0f, 27.0f, 4.0f, 16.0f, 28.0f, 5.0f, 17.0f, 29.0f, 6.0f, 18.0f, 30.0f, 7.0f, 19.0f, 31.0f, 8.0f, 20.0f, 32.0f, 9.0f, 21.0f, 33.0f, 10.0f, 22.0f, 34.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 37.0f, 49.0f, 61.0f, 38.0f, 50.0f, 62.0f, 39.0f, 51.0f, 63.0f, 40.0f, 52.0f, 64.0f, 41.0f, 53.0f, 65.0f, 42.0f, 54.0f, 66.0f, 43.0f, 55.0f, 67.0f, 44.0f, 56.0f, 68.0f, 45.0f, 57.0f, 69.0f, 46.0f, 58.0f, 70.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 73.0f, 85.0f, 97.0f, 74.0f, 86.0f, 98.0f, 75.0f, 87.0f, 99.0f, 76.0f, 88.0f, 100.0f, 77.0f, 89.0f, 101.0f, 78.0f, 90.0f, 102.0f, 79.0f, 91.0f, 103.0f, 80.0f, 92.0f, 104.0f, 81.0f, 93.0f, 105.0f, 82.0f, 94.0f, 106.0f, 83.0f, 95.0f, 107.0f, 108.0f, 120.0f, 132.0f, 109.0f, 121.0f, 133.0f, 110.0f, 122.0f, 134.0f, 111.0f, 123.0f, 135.0f, 112.0f, 124.0f, 136.0f, 113.0f, 125.0f, 137.0f, 114.0f, 126.0f, 138.0f, 115.0f, 127.0f, 139.0f, 116.0f, 128.0f, 140.0f, 117.0f, 129.0f, 141.0f, 118.0f, 130.0f, 142.0f, 119.0f, 131.0f, 143.0f})
                        }, { // placeholder64
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_dim4_axis2_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_float16_dim4_axis2_all_inputs_as_internal", get_test_model_float16_dim4_axis2_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_float16_dim4_axis2_neg() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 1.0f, 13.0f, 25.0f, 2.0f, 14.0f, 26.0f, 3.0f, 15.0f, 27.0f, 4.0f, 16.0f, 28.0f, 5.0f, 17.0f, 29.0f, 6.0f, 18.0f, 30.0f, 7.0f, 19.0f, 31.0f, 8.0f, 20.0f, 32.0f, 9.0f, 21.0f, 33.0f, 10.0f, 22.0f, 34.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 37.0f, 49.0f, 61.0f, 38.0f, 50.0f, 62.0f, 39.0f, 51.0f, 63.0f, 40.0f, 52.0f, 64.0f, 41.0f, 53.0f, 65.0f, 42.0f, 54.0f, 66.0f, 43.0f, 55.0f, 67.0f, 44.0f, 56.0f, 68.0f, 45.0f, 57.0f, 69.0f, 46.0f, 58.0f, 70.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 73.0f, 85.0f, 97.0f, 74.0f, 86.0f, 98.0f, 75.0f, 87.0f, 99.0f, 76.0f, 88.0f, 100.0f, 77.0f, 89.0f, 101.0f, 78.0f, 90.0f, 102.0f, 79.0f, 91.0f, 103.0f, 80.0f, 92.0f, 104.0f, 81.0f, 93.0f, 105.0f, 82.0f, 94.0f, 106.0f, 83.0f, 95.0f, 107.0f, 108.0f, 120.0f, 132.0f, 109.0f, 121.0f, 133.0f, 110.0f, 122.0f, 134.0f, 111.0f, 123.0f, 135.0f, 112.0f, 124.0f, 136.0f, 113.0f, 125.0f, 137.0f, 114.0f, 126.0f, 138.0f, 115.0f, 127.0f, 139.0f, 116.0f, 128.0f, 140.0f, 117.0f, 129.0f, 141.0f, 118.0f, 130.0f, 142.0f, 119.0f, 131.0f, 143.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2, 12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 4.0f, 16.0f, 28.0f, 8.0f, 20.0f, 32.0f, 1.0f, 13.0f, 25.0f, 5.0f, 17.0f, 29.0f, 9.0f, 21.0f, 33.0f, 2.0f, 14.0f, 26.0f, 6.0f, 18.0f, 30.0f, 10.0f, 22.0f, 34.0f, 3.0f, 15.0f, 27.0f, 7.0f, 19.0f, 31.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 40.0f, 52.0f, 64.0f, 44.0f, 56.0f, 68.0f, 37.0f, 49.0f, 61.0f, 41.0f, 53.0f, 65.0f, 45.0f, 57.0f, 69.0f, 38.0f, 50.0f, 62.0f, 42.0f, 54.0f, 66.0f, 46.0f, 58.0f, 70.0f, 39.0f, 51.0f, 63.0f, 43.0f, 55.0f, 67.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 76.0f, 88.0f, 100.0f, 80.0f, 92.0f, 104.0f, 73.0f, 85.0f, 97.0f, 77.0f, 89.0f, 101.0f, 81.0f, 93.0f, 105.0f, 74.0f, 86.0f, 98.0f, 78.0f, 90.0f, 102.0f, 82.0f, 94.0f, 106.0f, 75.0f, 87.0f, 99.0f, 79.0f, 91.0f, 103.0f, 83.0f, 95.0f, 107.0f, 108.0f, 120.0f, 132.0f, 112.0f, 124.0f, 136.0f, 116.0f, 128.0f, 140.0f, 109.0f, 121.0f, 133.0f, 113.0f, 125.0f, 137.0f, 117.0f, 129.0f, 141.0f, 110.0f, 122.0f, 134.0f, 114.0f, 126.0f, 138.0f, 118.0f, 130.0f, 142.0f, 111.0f, 123.0f, 135.0f, 115.0f, 127.0f, 139.0f, 119.0f, 131.0f, 143.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_dim4_axis2_neg = TestModelManager::get().add("channel_shuffle_float16_dim4_axis2_neg", get_test_model_float16_dim4_axis2_neg());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_float16_dim4_axis2_neg_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2, 12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 4.0f, 16.0f, 28.0f, 8.0f, 20.0f, 32.0f, 1.0f, 13.0f, 25.0f, 5.0f, 17.0f, 29.0f, 9.0f, 21.0f, 33.0f, 2.0f, 14.0f, 26.0f, 6.0f, 18.0f, 30.0f, 10.0f, 22.0f, 34.0f, 3.0f, 15.0f, 27.0f, 7.0f, 19.0f, 31.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 40.0f, 52.0f, 64.0f, 44.0f, 56.0f, 68.0f, 37.0f, 49.0f, 61.0f, 41.0f, 53.0f, 65.0f, 45.0f, 57.0f, 69.0f, 38.0f, 50.0f, 62.0f, 42.0f, 54.0f, 66.0f, 46.0f, 58.0f, 70.0f, 39.0f, 51.0f, 63.0f, 43.0f, 55.0f, 67.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 76.0f, 88.0f, 100.0f, 80.0f, 92.0f, 104.0f, 73.0f, 85.0f, 97.0f, 77.0f, 89.0f, 101.0f, 81.0f, 93.0f, 105.0f, 74.0f, 86.0f, 98.0f, 78.0f, 90.0f, 102.0f, 82.0f, 94.0f, 106.0f, 75.0f, 87.0f, 99.0f, 79.0f, 91.0f, 103.0f, 83.0f, 95.0f, 107.0f, 108.0f, 120.0f, 132.0f, 112.0f, 124.0f, 136.0f, 116.0f, 128.0f, 140.0f, 109.0f, 121.0f, 133.0f, 113.0f, 125.0f, 137.0f, 117.0f, 129.0f, 141.0f, 110.0f, 122.0f, 134.0f, 114.0f, 126.0f, 138.0f, 118.0f, 130.0f, 142.0f, 111.0f, 123.0f, 135.0f, 115.0f, 127.0f, 139.0f, 119.0f, 131.0f, 143.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 1.0f, 13.0f, 25.0f, 2.0f, 14.0f, 26.0f, 3.0f, 15.0f, 27.0f, 4.0f, 16.0f, 28.0f, 5.0f, 17.0f, 29.0f, 6.0f, 18.0f, 30.0f, 7.0f, 19.0f, 31.0f, 8.0f, 20.0f, 32.0f, 9.0f, 21.0f, 33.0f, 10.0f, 22.0f, 34.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 37.0f, 49.0f, 61.0f, 38.0f, 50.0f, 62.0f, 39.0f, 51.0f, 63.0f, 40.0f, 52.0f, 64.0f, 41.0f, 53.0f, 65.0f, 42.0f, 54.0f, 66.0f, 43.0f, 55.0f, 67.0f, 44.0f, 56.0f, 68.0f, 45.0f, 57.0f, 69.0f, 46.0f, 58.0f, 70.0f, 47.0f, 59.0f, 71.0f, 72.0f, 84.0f, 96.0f, 73.0f, 85.0f, 97.0f, 74.0f, 86.0f, 98.0f, 75.0f, 87.0f, 99.0f, 76.0f, 88.0f, 100.0f, 77.0f, 89.0f, 101.0f, 78.0f, 90.0f, 102.0f, 79.0f, 91.0f, 103.0f, 80.0f, 92.0f, 104.0f, 81.0f, 93.0f, 105.0f, 82.0f, 94.0f, 106.0f, 83.0f, 95.0f, 107.0f, 108.0f, 120.0f, 132.0f, 109.0f, 121.0f, 133.0f, 110.0f, 122.0f, 134.0f, 111.0f, 123.0f, 135.0f, 112.0f, 124.0f, 136.0f, 113.0f, 125.0f, 137.0f, 114.0f, 126.0f, 138.0f, 115.0f, 127.0f, 139.0f, 116.0f, 128.0f, 140.0f, 117.0f, 129.0f, 141.0f, 118.0f, 130.0f, 142.0f, 119.0f, 131.0f, 143.0f})
                        }, { // placeholder65
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_dim4_axis2_neg_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_float16_dim4_axis2_neg_all_inputs_as_internal", get_test_model_float16_dim4_axis2_neg_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_float16_dim4_axis3() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 35.0f, 36.0f, 37.0f, 38.0f, 39.0f, 40.0f, 41.0f, 42.0f, 43.0f, 44.0f, 45.0f, 46.0f, 47.0f, 48.0f, 49.0f, 50.0f, 51.0f, 52.0f, 53.0f, 54.0f, 55.0f, 56.0f, 57.0f, 58.0f, 59.0f, 60.0f, 61.0f, 62.0f, 63.0f, 64.0f, 65.0f, 66.0f, 67.0f, 68.0f, 69.0f, 70.0f, 71.0f, 72.0f, 73.0f, 74.0f, 75.0f, 76.0f, 77.0f, 78.0f, 79.0f, 80.0f, 81.0f, 82.0f, 83.0f, 84.0f, 85.0f, 86.0f, 87.0f, 88.0f, 89.0f, 90.0f, 91.0f, 92.0f, 93.0f, 94.0f, 95.0f, 96.0f, 97.0f, 98.0f, 99.0f, 100.0f, 101.0f, 102.0f, 103.0f, 104.0f, 105.0f, 106.0f, 107.0f, 108.0f, 109.0f, 110.0f, 111.0f, 112.0f, 113.0f, 114.0f, 115.0f, 116.0f, 117.0f, 118.0f, 119.0f, 120.0f, 121.0f, 122.0f, 123.0f, 124.0f, 125.0f, 126.0f, 127.0f, 128.0f, 129.0f, 130.0f, 131.0f, 132.0f, 133.0f, 134.0f, 135.0f, 136.0f, 137.0f, 138.0f, 139.0f, 140.0f, 141.0f, 142.0f, 143.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2, 3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f, 12.0f, 16.0f, 20.0f, 13.0f, 17.0f, 21.0f, 14.0f, 18.0f, 22.0f, 15.0f, 19.0f, 23.0f, 24.0f, 28.0f, 32.0f, 25.0f, 29.0f, 33.0f, 26.0f, 30.0f, 34.0f, 27.0f, 31.0f, 35.0f, 36.0f, 40.0f, 44.0f, 37.0f, 41.0f, 45.0f, 38.0f, 42.0f, 46.0f, 39.0f, 43.0f, 47.0f, 48.0f, 52.0f, 56.0f, 49.0f, 53.0f, 57.0f, 50.0f, 54.0f, 58.0f, 51.0f, 55.0f, 59.0f, 60.0f, 64.0f, 68.0f, 61.0f, 65.0f, 69.0f, 62.0f, 66.0f, 70.0f, 63.0f, 67.0f, 71.0f, 72.0f, 76.0f, 80.0f, 73.0f, 77.0f, 81.0f, 74.0f, 78.0f, 82.0f, 75.0f, 79.0f, 83.0f, 84.0f, 88.0f, 92.0f, 85.0f, 89.0f, 93.0f, 86.0f, 90.0f, 94.0f, 87.0f, 91.0f, 95.0f, 96.0f, 100.0f, 104.0f, 97.0f, 101.0f, 105.0f, 98.0f, 102.0f, 106.0f, 99.0f, 103.0f, 107.0f, 108.0f, 112.0f, 116.0f, 109.0f, 113.0f, 117.0f, 110.0f, 114.0f, 118.0f, 111.0f, 115.0f, 119.0f, 120.0f, 124.0f, 128.0f, 121.0f, 125.0f, 129.0f, 122.0f, 126.0f, 130.0f, 123.0f, 127.0f, 131.0f, 132.0f, 136.0f, 140.0f, 133.0f, 137.0f, 141.0f, 134.0f, 138.0f, 142.0f, 135.0f, 139.0f, 143.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_dim4_axis3 = TestModelManager::get().add("channel_shuffle_float16_dim4_axis3", get_test_model_float16_dim4_axis3());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_float16_dim4_axis3_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2, 3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f, 12.0f, 16.0f, 20.0f, 13.0f, 17.0f, 21.0f, 14.0f, 18.0f, 22.0f, 15.0f, 19.0f, 23.0f, 24.0f, 28.0f, 32.0f, 25.0f, 29.0f, 33.0f, 26.0f, 30.0f, 34.0f, 27.0f, 31.0f, 35.0f, 36.0f, 40.0f, 44.0f, 37.0f, 41.0f, 45.0f, 38.0f, 42.0f, 46.0f, 39.0f, 43.0f, 47.0f, 48.0f, 52.0f, 56.0f, 49.0f, 53.0f, 57.0f, 50.0f, 54.0f, 58.0f, 51.0f, 55.0f, 59.0f, 60.0f, 64.0f, 68.0f, 61.0f, 65.0f, 69.0f, 62.0f, 66.0f, 70.0f, 63.0f, 67.0f, 71.0f, 72.0f, 76.0f, 80.0f, 73.0f, 77.0f, 81.0f, 74.0f, 78.0f, 82.0f, 75.0f, 79.0f, 83.0f, 84.0f, 88.0f, 92.0f, 85.0f, 89.0f, 93.0f, 86.0f, 90.0f, 94.0f, 87.0f, 91.0f, 95.0f, 96.0f, 100.0f, 104.0f, 97.0f, 101.0f, 105.0f, 98.0f, 102.0f, 106.0f, 99.0f, 103.0f, 107.0f, 108.0f, 112.0f, 116.0f, 109.0f, 113.0f, 117.0f, 110.0f, 114.0f, 118.0f, 111.0f, 115.0f, 119.0f, 120.0f, 124.0f, 128.0f, 121.0f, 125.0f, 129.0f, 122.0f, 126.0f, 130.0f, 123.0f, 127.0f, 131.0f, 132.0f, 136.0f, 140.0f, 133.0f, 137.0f, 141.0f, 134.0f, 138.0f, 142.0f, 135.0f, 139.0f, 143.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 35.0f, 36.0f, 37.0f, 38.0f, 39.0f, 40.0f, 41.0f, 42.0f, 43.0f, 44.0f, 45.0f, 46.0f, 47.0f, 48.0f, 49.0f, 50.0f, 51.0f, 52.0f, 53.0f, 54.0f, 55.0f, 56.0f, 57.0f, 58.0f, 59.0f, 60.0f, 61.0f, 62.0f, 63.0f, 64.0f, 65.0f, 66.0f, 67.0f, 68.0f, 69.0f, 70.0f, 71.0f, 72.0f, 73.0f, 74.0f, 75.0f, 76.0f, 77.0f, 78.0f, 79.0f, 80.0f, 81.0f, 82.0f, 83.0f, 84.0f, 85.0f, 86.0f, 87.0f, 88.0f, 89.0f, 90.0f, 91.0f, 92.0f, 93.0f, 94.0f, 95.0f, 96.0f, 97.0f, 98.0f, 99.0f, 100.0f, 101.0f, 102.0f, 103.0f, 104.0f, 105.0f, 106.0f, 107.0f, 108.0f, 109.0f, 110.0f, 111.0f, 112.0f, 113.0f, 114.0f, 115.0f, 116.0f, 117.0f, 118.0f, 119.0f, 120.0f, 121.0f, 122.0f, 123.0f, 124.0f, 125.0f, 126.0f, 127.0f, 128.0f, 129.0f, 130.0f, 131.0f, 132.0f, 133.0f, 134.0f, 135.0f, 136.0f, 137.0f, 138.0f, 139.0f, 140.0f, 141.0f, 142.0f, 143.0f})
                        }, { // placeholder66
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
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
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_dim4_axis3_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_float16_dim4_axis3_all_inputs_as_internal", get_test_model_float16_dim4_axis3_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_float16_dim4_axis3_neg() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 35.0f, 36.0f, 37.0f, 38.0f, 39.0f, 40.0f, 41.0f, 42.0f, 43.0f, 44.0f, 45.0f, 46.0f, 47.0f, 48.0f, 49.0f, 50.0f, 51.0f, 52.0f, 53.0f, 54.0f, 55.0f, 56.0f, 57.0f, 58.0f, 59.0f, 60.0f, 61.0f, 62.0f, 63.0f, 64.0f, 65.0f, 66.0f, 67.0f, 68.0f, 69.0f, 70.0f, 71.0f, 72.0f, 73.0f, 74.0f, 75.0f, 76.0f, 77.0f, 78.0f, 79.0f, 80.0f, 81.0f, 82.0f, 83.0f, 84.0f, 85.0f, 86.0f, 87.0f, 88.0f, 89.0f, 90.0f, 91.0f, 92.0f, 93.0f, 94.0f, 95.0f, 96.0f, 97.0f, 98.0f, 99.0f, 100.0f, 101.0f, 102.0f, 103.0f, 104.0f, 105.0f, 106.0f, 107.0f, 108.0f, 109.0f, 110.0f, 111.0f, 112.0f, 113.0f, 114.0f, 115.0f, 116.0f, 117.0f, 118.0f, 119.0f, 120.0f, 121.0f, 122.0f, 123.0f, 124.0f, 125.0f, 126.0f, 127.0f, 128.0f, 129.0f, 130.0f, 131.0f, 132.0f, 133.0f, 134.0f, 135.0f, 136.0f, 137.0f, 138.0f, 139.0f, 140.0f, 141.0f, 142.0f, 143.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2, 3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f, 12.0f, 16.0f, 20.0f, 13.0f, 17.0f, 21.0f, 14.0f, 18.0f, 22.0f, 15.0f, 19.0f, 23.0f, 24.0f, 28.0f, 32.0f, 25.0f, 29.0f, 33.0f, 26.0f, 30.0f, 34.0f, 27.0f, 31.0f, 35.0f, 36.0f, 40.0f, 44.0f, 37.0f, 41.0f, 45.0f, 38.0f, 42.0f, 46.0f, 39.0f, 43.0f, 47.0f, 48.0f, 52.0f, 56.0f, 49.0f, 53.0f, 57.0f, 50.0f, 54.0f, 58.0f, 51.0f, 55.0f, 59.0f, 60.0f, 64.0f, 68.0f, 61.0f, 65.0f, 69.0f, 62.0f, 66.0f, 70.0f, 63.0f, 67.0f, 71.0f, 72.0f, 76.0f, 80.0f, 73.0f, 77.0f, 81.0f, 74.0f, 78.0f, 82.0f, 75.0f, 79.0f, 83.0f, 84.0f, 88.0f, 92.0f, 85.0f, 89.0f, 93.0f, 86.0f, 90.0f, 94.0f, 87.0f, 91.0f, 95.0f, 96.0f, 100.0f, 104.0f, 97.0f, 101.0f, 105.0f, 98.0f, 102.0f, 106.0f, 99.0f, 103.0f, 107.0f, 108.0f, 112.0f, 116.0f, 109.0f, 113.0f, 117.0f, 110.0f, 114.0f, 118.0f, 111.0f, 115.0f, 119.0f, 120.0f, 124.0f, 128.0f, 121.0f, 125.0f, 129.0f, 122.0f, 126.0f, 130.0f, 123.0f, 127.0f, 131.0f, 132.0f, 136.0f, 140.0f, 133.0f, 137.0f, 141.0f, 134.0f, 138.0f, 142.0f, 135.0f, 139.0f, 143.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_dim4_axis3_neg = TestModelManager::get().add("channel_shuffle_float16_dim4_axis3_neg", get_test_model_float16_dim4_axis3_neg());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_float16_dim4_axis3_neg_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2, 3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f, 12.0f, 16.0f, 20.0f, 13.0f, 17.0f, 21.0f, 14.0f, 18.0f, 22.0f, 15.0f, 19.0f, 23.0f, 24.0f, 28.0f, 32.0f, 25.0f, 29.0f, 33.0f, 26.0f, 30.0f, 34.0f, 27.0f, 31.0f, 35.0f, 36.0f, 40.0f, 44.0f, 37.0f, 41.0f, 45.0f, 38.0f, 42.0f, 46.0f, 39.0f, 43.0f, 47.0f, 48.0f, 52.0f, 56.0f, 49.0f, 53.0f, 57.0f, 50.0f, 54.0f, 58.0f, 51.0f, 55.0f, 59.0f, 60.0f, 64.0f, 68.0f, 61.0f, 65.0f, 69.0f, 62.0f, 66.0f, 70.0f, 63.0f, 67.0f, 71.0f, 72.0f, 76.0f, 80.0f, 73.0f, 77.0f, 81.0f, 74.0f, 78.0f, 82.0f, 75.0f, 79.0f, 83.0f, 84.0f, 88.0f, 92.0f, 85.0f, 89.0f, 93.0f, 86.0f, 90.0f, 94.0f, 87.0f, 91.0f, 95.0f, 96.0f, 100.0f, 104.0f, 97.0f, 101.0f, 105.0f, 98.0f, 102.0f, 106.0f, 99.0f, 103.0f, 107.0f, 108.0f, 112.0f, 116.0f, 109.0f, 113.0f, 117.0f, 110.0f, 114.0f, 118.0f, 111.0f, 115.0f, 119.0f, 120.0f, 124.0f, 128.0f, 121.0f, 125.0f, 129.0f, 122.0f, 126.0f, 130.0f, 123.0f, 127.0f, 131.0f, 132.0f, 136.0f, 140.0f, 133.0f, 137.0f, 141.0f, 134.0f, 138.0f, 142.0f, 135.0f, 139.0f, 143.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 35.0f, 36.0f, 37.0f, 38.0f, 39.0f, 40.0f, 41.0f, 42.0f, 43.0f, 44.0f, 45.0f, 46.0f, 47.0f, 48.0f, 49.0f, 50.0f, 51.0f, 52.0f, 53.0f, 54.0f, 55.0f, 56.0f, 57.0f, 58.0f, 59.0f, 60.0f, 61.0f, 62.0f, 63.0f, 64.0f, 65.0f, 66.0f, 67.0f, 68.0f, 69.0f, 70.0f, 71.0f, 72.0f, 73.0f, 74.0f, 75.0f, 76.0f, 77.0f, 78.0f, 79.0f, 80.0f, 81.0f, 82.0f, 83.0f, 84.0f, 85.0f, 86.0f, 87.0f, 88.0f, 89.0f, 90.0f, 91.0f, 92.0f, 93.0f, 94.0f, 95.0f, 96.0f, 97.0f, 98.0f, 99.0f, 100.0f, 101.0f, 102.0f, 103.0f, 104.0f, 105.0f, 106.0f, 107.0f, 108.0f, 109.0f, 110.0f, 111.0f, 112.0f, 113.0f, 114.0f, 115.0f, 116.0f, 117.0f, 118.0f, 119.0f, 120.0f, 121.0f, 122.0f, 123.0f, 124.0f, 125.0f, 126.0f, 127.0f, 128.0f, 129.0f, 130.0f, 131.0f, 132.0f, 133.0f, 134.0f, 135.0f, 136.0f, 137.0f, 138.0f, 139.0f, 140.0f, 141.0f, 142.0f, 143.0f})
                        }, { // placeholder67
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
                        }, { // param68
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
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_dim4_axis3_neg_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_float16_dim4_axis3_neg_all_inputs_as_internal", get_test_model_float16_dim4_axis3_neg_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_float16_dim3_axis0() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {12, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_dim3_axis0 = TestModelManager::get().add("channel_shuffle_float16_dim3_axis0", get_test_model_float16_dim3_axis0());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_float16_dim3_axis0_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {12, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f})
                        }, { // placeholder68
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
                        }, { // param69
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
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_dim3_axis0_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_float16_dim3_axis0_all_inputs_as_internal", get_test_model_float16_dim3_axis0_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_float16_dim3_axis0_neg() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {12, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_dim3_axis0_neg = TestModelManager::get().add("channel_shuffle_float16_dim3_axis0_neg", get_test_model_float16_dim3_axis0_neg());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_float16_dim3_axis0_neg_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {12, 2, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {12, 2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 36.0f, 48.0f, 60.0f, 1.0f, 13.0f, 25.0f, 37.0f, 49.0f, 61.0f, 2.0f, 14.0f, 26.0f, 38.0f, 50.0f, 62.0f, 3.0f, 15.0f, 27.0f, 39.0f, 51.0f, 63.0f, 4.0f, 16.0f, 28.0f, 40.0f, 52.0f, 64.0f, 5.0f, 17.0f, 29.0f, 41.0f, 53.0f, 65.0f, 6.0f, 18.0f, 30.0f, 42.0f, 54.0f, 66.0f, 7.0f, 19.0f, 31.0f, 43.0f, 55.0f, 67.0f, 8.0f, 20.0f, 32.0f, 44.0f, 56.0f, 68.0f, 9.0f, 21.0f, 33.0f, 45.0f, 57.0f, 69.0f, 10.0f, 22.0f, 34.0f, 46.0f, 58.0f, 70.0f, 11.0f, 23.0f, 35.0f, 47.0f, 59.0f, 71.0f})
                        }, { // placeholder69
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
                        }, { // param70
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
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_dim3_axis0_neg_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_float16_dim3_axis0_neg_all_inputs_as_internal", get_test_model_float16_dim3_axis0_neg_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_float16_dim3_axis1() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 1.0f, 13.0f, 25.0f, 2.0f, 14.0f, 26.0f, 3.0f, 15.0f, 27.0f, 4.0f, 16.0f, 28.0f, 5.0f, 17.0f, 29.0f, 6.0f, 18.0f, 30.0f, 7.0f, 19.0f, 31.0f, 8.0f, 20.0f, 32.0f, 9.0f, 21.0f, 33.0f, 10.0f, 22.0f, 34.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 37.0f, 49.0f, 61.0f, 38.0f, 50.0f, 62.0f, 39.0f, 51.0f, 63.0f, 40.0f, 52.0f, 64.0f, 41.0f, 53.0f, 65.0f, 42.0f, 54.0f, 66.0f, 43.0f, 55.0f, 67.0f, 44.0f, 56.0f, 68.0f, 45.0f, 57.0f, 69.0f, 46.0f, 58.0f, 70.0f, 47.0f, 59.0f, 71.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 4.0f, 16.0f, 28.0f, 8.0f, 20.0f, 32.0f, 1.0f, 13.0f, 25.0f, 5.0f, 17.0f, 29.0f, 9.0f, 21.0f, 33.0f, 2.0f, 14.0f, 26.0f, 6.0f, 18.0f, 30.0f, 10.0f, 22.0f, 34.0f, 3.0f, 15.0f, 27.0f, 7.0f, 19.0f, 31.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 40.0f, 52.0f, 64.0f, 44.0f, 56.0f, 68.0f, 37.0f, 49.0f, 61.0f, 41.0f, 53.0f, 65.0f, 45.0f, 57.0f, 69.0f, 38.0f, 50.0f, 62.0f, 42.0f, 54.0f, 66.0f, 46.0f, 58.0f, 70.0f, 39.0f, 51.0f, 63.0f, 43.0f, 55.0f, 67.0f, 47.0f, 59.0f, 71.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_dim3_axis1 = TestModelManager::get().add("channel_shuffle_float16_dim3_axis1", get_test_model_float16_dim3_axis1());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_float16_dim3_axis1_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 4.0f, 16.0f, 28.0f, 8.0f, 20.0f, 32.0f, 1.0f, 13.0f, 25.0f, 5.0f, 17.0f, 29.0f, 9.0f, 21.0f, 33.0f, 2.0f, 14.0f, 26.0f, 6.0f, 18.0f, 30.0f, 10.0f, 22.0f, 34.0f, 3.0f, 15.0f, 27.0f, 7.0f, 19.0f, 31.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 40.0f, 52.0f, 64.0f, 44.0f, 56.0f, 68.0f, 37.0f, 49.0f, 61.0f, 41.0f, 53.0f, 65.0f, 45.0f, 57.0f, 69.0f, 38.0f, 50.0f, 62.0f, 42.0f, 54.0f, 66.0f, 46.0f, 58.0f, 70.0f, 39.0f, 51.0f, 63.0f, 43.0f, 55.0f, 67.0f, 47.0f, 59.0f, 71.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 1.0f, 13.0f, 25.0f, 2.0f, 14.0f, 26.0f, 3.0f, 15.0f, 27.0f, 4.0f, 16.0f, 28.0f, 5.0f, 17.0f, 29.0f, 6.0f, 18.0f, 30.0f, 7.0f, 19.0f, 31.0f, 8.0f, 20.0f, 32.0f, 9.0f, 21.0f, 33.0f, 10.0f, 22.0f, 34.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 37.0f, 49.0f, 61.0f, 38.0f, 50.0f, 62.0f, 39.0f, 51.0f, 63.0f, 40.0f, 52.0f, 64.0f, 41.0f, 53.0f, 65.0f, 42.0f, 54.0f, 66.0f, 43.0f, 55.0f, 67.0f, 44.0f, 56.0f, 68.0f, 45.0f, 57.0f, 69.0f, 46.0f, 58.0f, 70.0f, 47.0f, 59.0f, 71.0f})
                        }, { // placeholder70
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
                        }, { // param71
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
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_dim3_axis1_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_float16_dim3_axis1_all_inputs_as_internal", get_test_model_float16_dim3_axis1_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_float16_dim3_axis1_neg() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 1.0f, 13.0f, 25.0f, 2.0f, 14.0f, 26.0f, 3.0f, 15.0f, 27.0f, 4.0f, 16.0f, 28.0f, 5.0f, 17.0f, 29.0f, 6.0f, 18.0f, 30.0f, 7.0f, 19.0f, 31.0f, 8.0f, 20.0f, 32.0f, 9.0f, 21.0f, 33.0f, 10.0f, 22.0f, 34.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 37.0f, 49.0f, 61.0f, 38.0f, 50.0f, 62.0f, 39.0f, 51.0f, 63.0f, 40.0f, 52.0f, 64.0f, 41.0f, 53.0f, 65.0f, 42.0f, 54.0f, 66.0f, 43.0f, 55.0f, 67.0f, 44.0f, 56.0f, 68.0f, 45.0f, 57.0f, 69.0f, 46.0f, 58.0f, 70.0f, 47.0f, 59.0f, 71.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 4.0f, 16.0f, 28.0f, 8.0f, 20.0f, 32.0f, 1.0f, 13.0f, 25.0f, 5.0f, 17.0f, 29.0f, 9.0f, 21.0f, 33.0f, 2.0f, 14.0f, 26.0f, 6.0f, 18.0f, 30.0f, 10.0f, 22.0f, 34.0f, 3.0f, 15.0f, 27.0f, 7.0f, 19.0f, 31.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 40.0f, 52.0f, 64.0f, 44.0f, 56.0f, 68.0f, 37.0f, 49.0f, 61.0f, 41.0f, 53.0f, 65.0f, 45.0f, 57.0f, 69.0f, 38.0f, 50.0f, 62.0f, 42.0f, 54.0f, 66.0f, 46.0f, 58.0f, 70.0f, 39.0f, 51.0f, 63.0f, 43.0f, 55.0f, 67.0f, 47.0f, 59.0f, 71.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_dim3_axis1_neg = TestModelManager::get().add("channel_shuffle_float16_dim3_axis1_neg", get_test_model_float16_dim3_axis1_neg());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_float16_dim3_axis1_neg_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 4.0f, 16.0f, 28.0f, 8.0f, 20.0f, 32.0f, 1.0f, 13.0f, 25.0f, 5.0f, 17.0f, 29.0f, 9.0f, 21.0f, 33.0f, 2.0f, 14.0f, 26.0f, 6.0f, 18.0f, 30.0f, 10.0f, 22.0f, 34.0f, 3.0f, 15.0f, 27.0f, 7.0f, 19.0f, 31.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 40.0f, 52.0f, 64.0f, 44.0f, 56.0f, 68.0f, 37.0f, 49.0f, 61.0f, 41.0f, 53.0f, 65.0f, 45.0f, 57.0f, 69.0f, 38.0f, 50.0f, 62.0f, 42.0f, 54.0f, 66.0f, 46.0f, 58.0f, 70.0f, 39.0f, 51.0f, 63.0f, 43.0f, 55.0f, 67.0f, 47.0f, 59.0f, 71.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 1.0f, 13.0f, 25.0f, 2.0f, 14.0f, 26.0f, 3.0f, 15.0f, 27.0f, 4.0f, 16.0f, 28.0f, 5.0f, 17.0f, 29.0f, 6.0f, 18.0f, 30.0f, 7.0f, 19.0f, 31.0f, 8.0f, 20.0f, 32.0f, 9.0f, 21.0f, 33.0f, 10.0f, 22.0f, 34.0f, 11.0f, 23.0f, 35.0f, 36.0f, 48.0f, 60.0f, 37.0f, 49.0f, 61.0f, 38.0f, 50.0f, 62.0f, 39.0f, 51.0f, 63.0f, 40.0f, 52.0f, 64.0f, 41.0f, 53.0f, 65.0f, 42.0f, 54.0f, 66.0f, 43.0f, 55.0f, 67.0f, 44.0f, 56.0f, 68.0f, 45.0f, 57.0f, 69.0f, 46.0f, 58.0f, 70.0f, 47.0f, 59.0f, 71.0f})
                        }, { // placeholder71
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
                        }, { // param72
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
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_dim3_axis1_neg_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_float16_dim3_axis1_neg_all_inputs_as_internal", get_test_model_float16_dim3_axis1_neg_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_float16_dim3_axis2() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 35.0f, 36.0f, 37.0f, 38.0f, 39.0f, 40.0f, 41.0f, 42.0f, 43.0f, 44.0f, 45.0f, 46.0f, 47.0f, 48.0f, 49.0f, 50.0f, 51.0f, 52.0f, 53.0f, 54.0f, 55.0f, 56.0f, 57.0f, 58.0f, 59.0f, 60.0f, 61.0f, 62.0f, 63.0f, 64.0f, 65.0f, 66.0f, 67.0f, 68.0f, 69.0f, 70.0f, 71.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f, 12.0f, 16.0f, 20.0f, 13.0f, 17.0f, 21.0f, 14.0f, 18.0f, 22.0f, 15.0f, 19.0f, 23.0f, 24.0f, 28.0f, 32.0f, 25.0f, 29.0f, 33.0f, 26.0f, 30.0f, 34.0f, 27.0f, 31.0f, 35.0f, 36.0f, 40.0f, 44.0f, 37.0f, 41.0f, 45.0f, 38.0f, 42.0f, 46.0f, 39.0f, 43.0f, 47.0f, 48.0f, 52.0f, 56.0f, 49.0f, 53.0f, 57.0f, 50.0f, 54.0f, 58.0f, 51.0f, 55.0f, 59.0f, 60.0f, 64.0f, 68.0f, 61.0f, 65.0f, 69.0f, 62.0f, 66.0f, 70.0f, 63.0f, 67.0f, 71.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_dim3_axis2 = TestModelManager::get().add("channel_shuffle_float16_dim3_axis2", get_test_model_float16_dim3_axis2());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_float16_dim3_axis2_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f, 12.0f, 16.0f, 20.0f, 13.0f, 17.0f, 21.0f, 14.0f, 18.0f, 22.0f, 15.0f, 19.0f, 23.0f, 24.0f, 28.0f, 32.0f, 25.0f, 29.0f, 33.0f, 26.0f, 30.0f, 34.0f, 27.0f, 31.0f, 35.0f, 36.0f, 40.0f, 44.0f, 37.0f, 41.0f, 45.0f, 38.0f, 42.0f, 46.0f, 39.0f, 43.0f, 47.0f, 48.0f, 52.0f, 56.0f, 49.0f, 53.0f, 57.0f, 50.0f, 54.0f, 58.0f, 51.0f, 55.0f, 59.0f, 60.0f, 64.0f, 68.0f, 61.0f, 65.0f, 69.0f, 62.0f, 66.0f, 70.0f, 63.0f, 67.0f, 71.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 35.0f, 36.0f, 37.0f, 38.0f, 39.0f, 40.0f, 41.0f, 42.0f, 43.0f, 44.0f, 45.0f, 46.0f, 47.0f, 48.0f, 49.0f, 50.0f, 51.0f, 52.0f, 53.0f, 54.0f, 55.0f, 56.0f, 57.0f, 58.0f, 59.0f, 60.0f, 61.0f, 62.0f, 63.0f, 64.0f, 65.0f, 66.0f, 67.0f, 68.0f, 69.0f, 70.0f, 71.0f})
                        }, { // placeholder72
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
                        }, { // param73
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
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_dim3_axis2_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_float16_dim3_axis2_all_inputs_as_internal", get_test_model_float16_dim3_axis2_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_float16_dim3_axis2_neg() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 35.0f, 36.0f, 37.0f, 38.0f, 39.0f, 40.0f, 41.0f, 42.0f, 43.0f, 44.0f, 45.0f, 46.0f, 47.0f, 48.0f, 49.0f, 50.0f, 51.0f, 52.0f, 53.0f, 54.0f, 55.0f, 56.0f, 57.0f, 58.0f, 59.0f, 60.0f, 61.0f, 62.0f, 63.0f, 64.0f, 65.0f, 66.0f, 67.0f, 68.0f, 69.0f, 70.0f, 71.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f, 12.0f, 16.0f, 20.0f, 13.0f, 17.0f, 21.0f, 14.0f, 18.0f, 22.0f, 15.0f, 19.0f, 23.0f, 24.0f, 28.0f, 32.0f, 25.0f, 29.0f, 33.0f, 26.0f, 30.0f, 34.0f, 27.0f, 31.0f, 35.0f, 36.0f, 40.0f, 44.0f, 37.0f, 41.0f, 45.0f, 38.0f, 42.0f, 46.0f, 39.0f, 43.0f, 47.0f, 48.0f, 52.0f, 56.0f, 49.0f, 53.0f, 57.0f, 50.0f, 54.0f, 58.0f, 51.0f, 55.0f, 59.0f, 60.0f, 64.0f, 68.0f, 61.0f, 65.0f, 69.0f, 62.0f, 66.0f, 70.0f, 63.0f, 67.0f, 71.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_dim3_axis2_neg = TestModelManager::get().add("channel_shuffle_float16_dim3_axis2_neg", get_test_model_float16_dim3_axis2_neg());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_float16_dim3_axis2_neg_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f, 12.0f, 16.0f, 20.0f, 13.0f, 17.0f, 21.0f, 14.0f, 18.0f, 22.0f, 15.0f, 19.0f, 23.0f, 24.0f, 28.0f, 32.0f, 25.0f, 29.0f, 33.0f, 26.0f, 30.0f, 34.0f, 27.0f, 31.0f, 35.0f, 36.0f, 40.0f, 44.0f, 37.0f, 41.0f, 45.0f, 38.0f, 42.0f, 46.0f, 39.0f, 43.0f, 47.0f, 48.0f, 52.0f, 56.0f, 49.0f, 53.0f, 57.0f, 50.0f, 54.0f, 58.0f, 51.0f, 55.0f, 59.0f, 60.0f, 64.0f, 68.0f, 61.0f, 65.0f, 69.0f, 62.0f, 66.0f, 70.0f, 63.0f, 67.0f, 71.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {2, 3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 35.0f, 36.0f, 37.0f, 38.0f, 39.0f, 40.0f, 41.0f, 42.0f, 43.0f, 44.0f, 45.0f, 46.0f, 47.0f, 48.0f, 49.0f, 50.0f, 51.0f, 52.0f, 53.0f, 54.0f, 55.0f, 56.0f, 57.0f, 58.0f, 59.0f, 60.0f, 61.0f, 62.0f, 63.0f, 64.0f, 65.0f, 66.0f, 67.0f, 68.0f, 69.0f, 70.0f, 71.0f})
                        }, { // placeholder73
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
                        }, { // param74
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
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_dim3_axis2_neg_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_float16_dim3_axis2_neg_all_inputs_as_internal", get_test_model_float16_dim3_axis2_neg_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_float16_dim2_axis0() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 1.0f, 13.0f, 25.0f, 2.0f, 14.0f, 26.0f, 3.0f, 15.0f, 27.0f, 4.0f, 16.0f, 28.0f, 5.0f, 17.0f, 29.0f, 6.0f, 18.0f, 30.0f, 7.0f, 19.0f, 31.0f, 8.0f, 20.0f, 32.0f, 9.0f, 21.0f, 33.0f, 10.0f, 22.0f, 34.0f, 11.0f, 23.0f, 35.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 4.0f, 16.0f, 28.0f, 8.0f, 20.0f, 32.0f, 1.0f, 13.0f, 25.0f, 5.0f, 17.0f, 29.0f, 9.0f, 21.0f, 33.0f, 2.0f, 14.0f, 26.0f, 6.0f, 18.0f, 30.0f, 10.0f, 22.0f, 34.0f, 3.0f, 15.0f, 27.0f, 7.0f, 19.0f, 31.0f, 11.0f, 23.0f, 35.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_dim2_axis0 = TestModelManager::get().add("channel_shuffle_float16_dim2_axis0", get_test_model_float16_dim2_axis0());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_float16_dim2_axis0_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 4.0f, 16.0f, 28.0f, 8.0f, 20.0f, 32.0f, 1.0f, 13.0f, 25.0f, 5.0f, 17.0f, 29.0f, 9.0f, 21.0f, 33.0f, 2.0f, 14.0f, 26.0f, 6.0f, 18.0f, 30.0f, 10.0f, 22.0f, 34.0f, 3.0f, 15.0f, 27.0f, 7.0f, 19.0f, 31.0f, 11.0f, 23.0f, 35.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 1.0f, 13.0f, 25.0f, 2.0f, 14.0f, 26.0f, 3.0f, 15.0f, 27.0f, 4.0f, 16.0f, 28.0f, 5.0f, 17.0f, 29.0f, 6.0f, 18.0f, 30.0f, 7.0f, 19.0f, 31.0f, 8.0f, 20.0f, 32.0f, 9.0f, 21.0f, 33.0f, 10.0f, 22.0f, 34.0f, 11.0f, 23.0f, 35.0f})
                        }, { // placeholder74
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
                        }, { // param75
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
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_dim2_axis0_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_float16_dim2_axis0_all_inputs_as_internal", get_test_model_float16_dim2_axis0_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_float16_dim2_axis0_neg() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 1.0f, 13.0f, 25.0f, 2.0f, 14.0f, 26.0f, 3.0f, 15.0f, 27.0f, 4.0f, 16.0f, 28.0f, 5.0f, 17.0f, 29.0f, 6.0f, 18.0f, 30.0f, 7.0f, 19.0f, 31.0f, 8.0f, 20.0f, 32.0f, 9.0f, 21.0f, 33.0f, 10.0f, 22.0f, 34.0f, 11.0f, 23.0f, 35.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 4.0f, 16.0f, 28.0f, 8.0f, 20.0f, 32.0f, 1.0f, 13.0f, 25.0f, 5.0f, 17.0f, 29.0f, 9.0f, 21.0f, 33.0f, 2.0f, 14.0f, 26.0f, 6.0f, 18.0f, 30.0f, 10.0f, 22.0f, 34.0f, 3.0f, 15.0f, 27.0f, 7.0f, 19.0f, 31.0f, 11.0f, 23.0f, 35.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_dim2_axis0_neg = TestModelManager::get().add("channel_shuffle_float16_dim2_axis0_neg", get_test_model_float16_dim2_axis0_neg());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_float16_dim2_axis0_neg_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {12, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 4.0f, 16.0f, 28.0f, 8.0f, 20.0f, 32.0f, 1.0f, 13.0f, 25.0f, 5.0f, 17.0f, 29.0f, 9.0f, 21.0f, 33.0f, 2.0f, 14.0f, 26.0f, 6.0f, 18.0f, 30.0f, 10.0f, 22.0f, 34.0f, 3.0f, 15.0f, 27.0f, 7.0f, 19.0f, 31.0f, 11.0f, 23.0f, 35.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {12, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 12.0f, 24.0f, 1.0f, 13.0f, 25.0f, 2.0f, 14.0f, 26.0f, 3.0f, 15.0f, 27.0f, 4.0f, 16.0f, 28.0f, 5.0f, 17.0f, 29.0f, 6.0f, 18.0f, 30.0f, 7.0f, 19.0f, 31.0f, 8.0f, 20.0f, 32.0f, 9.0f, 21.0f, 33.0f, 10.0f, 22.0f, 34.0f, 11.0f, 23.0f, 35.0f})
                        }, { // placeholder75
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
                        }, { // param76
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
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_dim2_axis0_neg_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_float16_dim2_axis0_neg_all_inputs_as_internal", get_test_model_float16_dim2_axis0_neg_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_float16_dim2_axis1() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 35.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f, 12.0f, 16.0f, 20.0f, 13.0f, 17.0f, 21.0f, 14.0f, 18.0f, 22.0f, 15.0f, 19.0f, 23.0f, 24.0f, 28.0f, 32.0f, 25.0f, 29.0f, 33.0f, 26.0f, 30.0f, 34.0f, 27.0f, 31.0f, 35.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_dim2_axis1 = TestModelManager::get().add("channel_shuffle_float16_dim2_axis1", get_test_model_float16_dim2_axis1());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_float16_dim2_axis1_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f, 12.0f, 16.0f, 20.0f, 13.0f, 17.0f, 21.0f, 14.0f, 18.0f, 22.0f, 15.0f, 19.0f, 23.0f, 24.0f, 28.0f, 32.0f, 25.0f, 29.0f, 33.0f, 26.0f, 30.0f, 34.0f, 27.0f, 31.0f, 35.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 35.0f})
                        }, { // placeholder76
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
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
                        }},
                .operations = {{
                            .type = TestOperationType::ADD,
                            .inputs = {4, 5, 6},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_dim2_axis1_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_float16_dim2_axis1_all_inputs_as_internal", get_test_model_float16_dim2_axis1_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_float16_dim2_axis1_neg() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 35.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f, 12.0f, 16.0f, 20.0f, 13.0f, 17.0f, 21.0f, 14.0f, 18.0f, 22.0f, 15.0f, 19.0f, 23.0f, 24.0f, 28.0f, 32.0f, 25.0f, 29.0f, 33.0f, 26.0f, 30.0f, 34.0f, 27.0f, 31.0f, 35.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_dim2_axis1_neg = TestModelManager::get().add("channel_shuffle_float16_dim2_axis1_neg", get_test_model_float16_dim2_axis1_neg());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_float16_dim2_axis1_neg_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f, 12.0f, 16.0f, 20.0f, 13.0f, 17.0f, 21.0f, 14.0f, 18.0f, 22.0f, 15.0f, 19.0f, 23.0f, 24.0f, 28.0f, 32.0f, 25.0f, 29.0f, 33.0f, 26.0f, 30.0f, 34.0f, 27.0f, 31.0f, 35.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {3, 12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f, 25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f, 33.0f, 34.0f, 35.0f})
                        }, { // placeholder77
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
                        }, { // param78
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
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_dim2_axis1_neg_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_float16_dim2_axis1_neg_all_inputs_as_internal", get_test_model_float16_dim2_axis1_neg_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_float16_dim1_axis0() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_dim1_axis0 = TestModelManager::get().add("channel_shuffle_float16_dim1_axis0", get_test_model_float16_dim1_axis0());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_float16_dim1_axis0_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f})
                        }, { // placeholder78
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
                        }, { // param79
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
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_dim1_axis0_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_float16_dim1_axis0_all_inputs_as_internal", get_test_model_float16_dim1_axis0_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_float16_dim1_axis0_neg() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f})
                        }},
                .operations = {{
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_dim1_axis0_neg = TestModelManager::get().add("channel_shuffle_float16_dim1_axis0_neg", get_test_model_float16_dim1_axis0_neg());

}  // namespace generated_tests::channel_shuffle

namespace generated_tests::channel_shuffle {

const TestModel& get_test_model_float16_dim1_axis0_neg_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // op1
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({})
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
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {12},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 4.0f, 8.0f, 1.0f, 5.0f, 9.0f, 2.0f, 6.0f, 10.0f, 3.0f, 7.0f, 11.0f})
                        }, { // op1_new
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {12},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f})
                        }, { // placeholder79
                            .type = TestOperandType::TENSOR_FLOAT16,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<_Float16>({0.0f})
                        }, { // param80
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
                            .type = TestOperationType::CHANNEL_SHUFFLE,
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
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_float16_dim1_axis0_neg_all_inputs_as_internal = TestModelManager::get().add("channel_shuffle_float16_dim1_axis0_neg_all_inputs_as_internal", get_test_model_float16_dim1_axis0_neg_all_inputs_as_internal());

}  // namespace generated_tests::channel_shuffle

