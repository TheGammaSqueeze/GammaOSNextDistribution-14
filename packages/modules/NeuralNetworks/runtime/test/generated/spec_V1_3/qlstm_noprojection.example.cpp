// Generated from qlstm_noprojection.mod.py
// DO NOT EDIT
// clang-format off
#include "TestHarness.h"
using namespace test_helper;  // NOLINT(google-build-using-namespace)

namespace generated_tests::qlstm_noprojection {

const TestModel& get_test_model() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 5},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({90, 102, 13, 26, 38, 102, 13, 26, 51, 64})
                        }, { // input_to_input_weights
                            .type = TestOperandType::TENSOR_QUANT8_SYMM,
                            .dimensions = {4, 5},
                            .numberOfConsumers = 1,
                            .scale = 0.00784314f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input_to_forget_weights
                            .type = TestOperandType::TENSOR_QUANT8_SYMM,
                            .dimensions = {4, 5},
                            .numberOfConsumers = 1,
                            .scale = 0.00784314f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-77, -13, 38, 25, 115, -64, -25, -51, 38, -102, -51, 38, -64, -51, -77, 38, -51, -77, -64, -64})
                        }, { // input_to_cell_weights
                            .type = TestOperandType::TENSOR_QUANT8_SYMM,
                            .dimensions = {4, 5},
                            .numberOfConsumers = 1,
                            .scale = 0.00784314f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-51, -38, -25, -13, -64, 64, -25, -38, -25, -77, 77, -13, -51, -38, -89, 89, -115, -64, 102, 77})
                        }, { // input_to_output_weights
                            .type = TestOperandType::TENSOR_QUANT8_SYMM,
                            .dimensions = {4, 5},
                            .numberOfConsumers = 1,
                            .scale = 0.00784314f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-102, -51, -25, -115, -13, -89, 38, -38, -102, -25, 77, -25, 51, -89, -38, -64, 13, 64, -77, -51})
                        }, { // recurrent_to_input_weights
                            .type = TestOperandType::TENSOR_QUANT8_SYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00784314f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // recurrent_to_forget_weights
                            .type = TestOperandType::TENSOR_QUANT8_SYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00784314f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-64, -38, -64, -25, 77, 51, 115, 38, -13, 25, 64, 25, 25, 38, -13, 51})
                        }, { // recurrent_to_cell_weights
                            .type = TestOperandType::TENSOR_QUANT8_SYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00784314f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-38, 25, 13, -38, 102, -10, -25, 38, 102, -77, -13, 25, 38, -13, 25, 64})
                        }, { // recurrent_to_output_weights
                            .type = TestOperandType::TENSOR_QUANT8_SYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00784314f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({38, -13, 13, -25, -64, -89, -25, -77, -13, -51, -89, -25, 13, 64, 25, -38})
                        }, { // cell_to_input_weights
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({})
                        }, { // cell_to_forget_weights
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({})
                        }, { // cell_to_output_weights
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({})
                        }, { // input_gate_bias
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // forget_gate_bias
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2147484, -6442451, -4294968, 2147484})
                        }, { // cell_gate_bias
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-1073742, 15461883, 5368709, 1717987})
                        }, { // output_gate_bias
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1073742, -214748, 4294968, 2147484})
                        }, { // projection_weights
                            .type = TestOperandType::TENSOR_QUANT8_SYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00392157f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // projection_bias
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // output_state_in
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 4},
                            .numberOfConsumers = 1,
                            .scale = 3.05176e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 0, 0, 0, 0, 0, 0, 0})
                        }, { // cell_state_in
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {2, 4},
                            .numberOfConsumers = 1,
                            .scale = 3.05176e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({0, 0, 0, 0, 0, 0, 0, 0})
                        }, { // input_layer_norm_weights
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.05182e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({})
                        }, { // forget_layer_norm_weights
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.05182e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({6553, 6553, 13107, 9830})
                        }, { // cell_layer_norm_weights
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.05182e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({22937, 6553, 9830, 26214})
                        }, { // output_layer_norm_weights
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.05182e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({19660, 6553, 6553, 16384})
                        }, { // cell_clip
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
                        }, { // projection_clip
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
                        }, { // input_intermediate_scale
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.007059f})
                        }, { // forget_intermediate_scale
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.007812f})
                        }, { // cell_intermediate_scale
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.007059f})
                        }, { // output_intermediate_scale
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.007812f})
                        }, { // hidden_state_zero_point
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // hidden_state_scale
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.007f})
                        }, { // output_state_out
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 4},
                            .numberOfConsumers = 0,
                            .scale = 3.05176e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-15, 21, 14, 20, -15, 15, 5, 27})
                        }, { // cell_state_out
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {2, 4},
                            .numberOfConsumers = 0,
                            .scale = 3.05176e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({-11692, 9960, 5491, 8861, -9422, 7726, 2056, 13149})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 4},
                            .numberOfConsumers = 0,
                            .scale = 3.05176e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-15, 21, 14, 20, -15, 15, 5, 27})
                        }},
                .operations = {{
                            .type = TestOperationType::QUANTIZED_LSTM,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31},
                            .outputs = {32, 33, 34}
                        }},
                .inputIndexes = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23},
                .outputIndexes = {32, 33, 34}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model = TestModelManager::get().add("qlstm_noprojection", get_test_model());

}  // namespace generated_tests::qlstm_noprojection

namespace generated_tests::qlstm_noprojection {

const TestModel& get_test_model_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 5},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input_to_input_weights
                            .type = TestOperandType::TENSOR_QUANT8_SYMM,
                            .dimensions = {4, 5},
                            .numberOfConsumers = 1,
                            .scale = 0.00784314f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // input_to_forget_weights
                            .type = TestOperandType::TENSOR_QUANT8_SYMM,
                            .dimensions = {4, 5},
                            .numberOfConsumers = 1,
                            .scale = 0.00784314f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-77, -13, 38, 25, 115, -64, -25, -51, 38, -102, -51, 38, -64, -51, -77, 38, -51, -77, -64, -64})
                        }, { // input_to_cell_weights
                            .type = TestOperandType::TENSOR_QUANT8_SYMM,
                            .dimensions = {4, 5},
                            .numberOfConsumers = 1,
                            .scale = 0.00784314f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-51, -38, -25, -13, -64, 64, -25, -38, -25, -77, 77, -13, -51, -38, -89, 89, -115, -64, 102, 77})
                        }, { // input_to_output_weights
                            .type = TestOperandType::TENSOR_QUANT8_SYMM,
                            .dimensions = {4, 5},
                            .numberOfConsumers = 1,
                            .scale = 0.00784314f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-102, -51, -25, -115, -13, -89, 38, -38, -102, -25, 77, -25, 51, -89, -38, -64, 13, 64, -77, -51})
                        }, { // recurrent_to_input_weights
                            .type = TestOperandType::TENSOR_QUANT8_SYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00784314f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // recurrent_to_forget_weights
                            .type = TestOperandType::TENSOR_QUANT8_SYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00784314f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-64, -38, -64, -25, 77, 51, 115, 38, -13, 25, 64, 25, 25, 38, -13, 51})
                        }, { // recurrent_to_cell_weights
                            .type = TestOperandType::TENSOR_QUANT8_SYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00784314f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-38, 25, 13, -38, 102, -10, -25, 38, 102, -77, -13, 25, 38, -13, 25, 64})
                        }, { // recurrent_to_output_weights
                            .type = TestOperandType::TENSOR_QUANT8_SYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00784314f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({38, -13, 13, -25, -64, -89, -25, -77, -13, -51, -89, -25, 13, 64, 25, -38})
                        }, { // cell_to_input_weights
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({})
                        }, { // cell_to_forget_weights
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({})
                        }, { // cell_to_output_weights
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 1.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({})
                        }, { // input_gate_bias
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // forget_gate_bias
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({2147484, -6442451, -4294968, 2147484})
                        }, { // cell_gate_bias
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-1073742, 15461883, 5368709, 1717987})
                        }, { // output_gate_bias
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({1073742, -214748, 4294968, 2147484})
                        }, { // projection_weights
                            .type = TestOperandType::TENSOR_QUANT8_SYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00392157f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // projection_bias
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({})
                        }, { // output_state_in
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 4},
                            .numberOfConsumers = 1,
                            .scale = 3.05176e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({})
                        }, { // cell_state_in
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {2, 4},
                            .numberOfConsumers = 1,
                            .scale = 3.05176e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({0, 0, 0, 0, 0, 0, 0, 0})
                        }, { // input_layer_norm_weights
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.05182e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({})
                        }, { // forget_layer_norm_weights
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.05182e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({6553, 6553, 13107, 9830})
                        }, { // cell_layer_norm_weights
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.05182e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({22937, 6553, 9830, 26214})
                        }, { // output_layer_norm_weights
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.05182e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({19660, 6553, 6553, 16384})
                        }, { // cell_clip
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
                        }, { // projection_clip
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
                        }, { // input_intermediate_scale
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.007059f})
                        }, { // forget_intermediate_scale
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.007812f})
                        }, { // cell_intermediate_scale
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.007059f})
                        }, { // output_intermediate_scale
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.007812f})
                        }, { // hidden_state_zero_point
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({0})
                        }, { // hidden_state_scale
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.007f})
                        }, { // output_state_out
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 4},
                            .numberOfConsumers = 0,
                            .scale = 3.05176e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-15, 21, 14, 20, -15, 15, 5, 27})
                        }, { // cell_state_out
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {2, 4},
                            .numberOfConsumers = 0,
                            .scale = 3.05176e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({-11692, 9960, 5491, 8861, -9422, 7726, 2056, 13149})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 4},
                            .numberOfConsumers = 0,
                            .scale = 3.05176e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({-15, 21, 14, 20, -15, 15, 5, 27})
                        }, { // input_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 5},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({90, 102, 13, 26, 38, 102, 13, 26, 51, 64})
                        }, { // placeholder
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
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
                        }, { // output_state_in_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {2, 4},
                            .numberOfConsumers = 1,
                            .scale = 3.05176e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int8_t>({0, 0, 0, 0, 0, 0, 0, 0})
                        }, { // placeholder1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 3.05176e-05f,
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
                            .inputs = {35, 36, 37},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {38, 39, 40},
                            .outputs = {18}
                        }, {
                            .type = TestOperationType::QUANTIZED_LSTM,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31},
                            .outputs = {32, 33, 34}
                        }},
                .inputIndexes = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 19, 20, 21, 22, 23, 35, 38},
                .outputIndexes = {32, 33, 34}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_3
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal = TestModelManager::get().add("qlstm_noprojection_all_inputs_as_internal", get_test_model_all_inputs_as_internal());

}  // namespace generated_tests::qlstm_noprojection

