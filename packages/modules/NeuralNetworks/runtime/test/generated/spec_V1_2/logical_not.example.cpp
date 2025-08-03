// Generated from logical_not.mod.py
// DO NOT EDIT
// clang-format off
#include "TestHarness.h"
using namespace test_helper;  // NOLINT(google-build-using-namespace)

namespace generated_tests::logical_not {

const TestModel& get_test_model() {
    static TestModel model = {
        .main = {
                .operands = {{ // input0
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1, 1, 4, 1, 1},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({true, false, false, true})
                        }, { // output0
                            .type = TestOperandType::TENSOR_BOOL8,
                            .dimensions = {1, 1, 4, 1, 1},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false, true, true, false})
                        }},
                .operations = {{
                            .type = TestOperationType::LOGICAL_NOT,
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

const auto dummy_test_model = TestModelManager::get().add("logical_not", get_test_model());

}  // namespace generated_tests::logical_not

