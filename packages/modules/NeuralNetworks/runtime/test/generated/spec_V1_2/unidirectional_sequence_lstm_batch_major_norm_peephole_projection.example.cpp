// Generated from unidirectional_sequence_lstm_batch_major_norm_peephole_projection.mod.py
// DO NOT EDIT
// clang-format off
#include "TestHarness.h"
using namespace test_helper;  // NOLINT(google-build-using-namespace)

namespace generated_tests::unidirectional_sequence_lstm_batch_major_norm_peephole_projection {

const TestModel& get_test_model() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.7f, 0.8f, 0.1f, 0.2f, 0.3f, 0.8f, 0.1f, 0.2f, 0.4f, 0.5f, 0.2f, 0.7f, 0.7f, 0.1f, 0.7f, 0.3f, 0.2f, 0.9f, 0.8f, 0.1f, 0.1f, 0.5f, 0.2f, 0.4f, 0.2f, 0.6f, 0.9f, 0.2f, 0.5f, 0.7f})
                        }, { // input_to_input_weights
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.5f, 0.6f, 0.7f, -0.8f, -0.9f, 0.1f, 0.2f, 0.3f, -0.4f, 0.5f, -0.8f, 0.7f, -0.6f, 0.5f, -0.4f, -0.5f, -0.4f, -0.3f, -0.2f, -0.1f})
                        }, { // input_to_forget_weights
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({-0.6f, -0.1f, 0.3f, 0.2f, 0.9f, -0.5f, -0.2f, -0.4f, 0.3f, -0.8f, -0.4f, 0.3f, -0.5f, -0.4f, -0.6f, 0.3f, -0.4f, -0.6f, -0.5f, -0.5f})
                        }, { // input_to_cell_weights
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({-0.4f, -0.3f, -0.2f, -0.1f, -0.5f, 0.5f, -0.2f, -0.3f, -0.2f, -0.6f, 0.6f, -0.1f, -0.4f, -0.3f, -0.7f, 0.7f, -0.9f, -0.5f, 0.8f, 0.6f})
                        }, { // input_to_output_weights
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({-0.8f, -0.4f, -0.2f, -0.9f, -0.1f, -0.7f, 0.3f, -0.3f, -0.8f, -0.2f, 0.6f, -0.2f, 0.4f, -0.7f, -0.3f, -0.5f, 0.1f, 0.5f, -0.6f, -0.4f})
                        }, { // recurrent_to_input_weights
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({-0.2f, -0.3f, 0.4f, 0.1f, -0.5f, 0.9f, -0.2f, -0.3f, -0.7f, 0.05f, -0.2f, -0.6f})
                        }, { // recurrent_to_forget_weights
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({-0.5f, -0.3f, -0.5f, -0.2f, 0.6f, 0.4f, 0.9f, 0.3f, -0.1f, 0.2f, 0.5f, 0.2f})
                        }, { // recurrent_to_cell_weights
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({-0.3f, 0.2f, 0.1f, -0.3f, 0.8f, -0.08f, -0.2f, 0.3f, 0.8f, -0.6f, -0.1f, 0.2f})
                        }, { // recurrent_to_output_weights
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f, -0.1f, 0.1f, -0.2f, -0.5f, -0.7f, -0.2f, -0.6f, -0.1f, -0.4f, -0.7f, -0.2f})
                        }, { // cell_to_input_weights
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.05f, 0.1f, 0.25f, 0.15f})
                        }, { // cell_to_forget_weights
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({-0.02f, -0.15f, -0.25f, -0.03f})
                        }, { // cell_to_output_weights
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.1f, -0.1f, -0.5f, 0.05f})
                        }, { // input_gate_bias
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.03f, 0.15f, 0.22f, 0.38f})
                        }, { // forget_gate_bias
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.1f, -0.3f, -0.2f, 0.1f})
                        }, { // cell_gate_bias
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({-0.05f, 0.72f, 0.25f, 0.08f})
                        }, { // output_gate_bias
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.05f, -0.01f, 0.2f, 0.1f})
                        }, { // projection_weights
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({-0.1f, 0.2f, 0.01f, -0.2f, 0.1f, 0.5f, 0.3f, 0.08f, 0.07f, 0.2f, -0.4f, 0.2f})
                        }, { // projection_bias
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {0},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // output_state_in
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f})
                        }, { // cell_state_in
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f})
                        }, { // activation_param
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({4})
                        }, { // cell_clip_param
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
                        }, { // proj_clip_param
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
                        }, { // time_major_param
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // input_layer_norm_weights
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.1f, 0.2f, 0.3f, 0.5f})
                        }, { // forget_layer_norm_weights
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.2f, 0.2f, 0.4f, 0.3f})
                        }, { // cell_layer_norm_weights
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.7f, 0.2f, 0.3f, 0.8f})
                        }, { // output_layer_norm_weights
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.6f, 0.2f, 0.2f, 0.5f})
                        }, { // output
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.024407668039203f, 0.128027379512787f, -0.001709178090096f, 0.013764165341854f, 0.140751048922539f, 0.039583537727594f, -0.004592306911945f, 0.155278354883194f, 0.083737745881081f, -0.006924282759428f, 0.08487406373024f, 0.06344497948885f, -0.004039138555527f, 0.139963015913963f, 0.072681039571762f, 0.007527053356171f, 0.161902531981468f, 0.056137066334486f})
                        }},
                .operations = {{
                            .type = TestOperationType::UNIDIRECTIONAL_SEQUENCE_LSTM,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27},
                            .outputs = {28}
                        }},
                .inputIndexes = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 24, 25, 26, 27},
                .outputIndexes = {28}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model = TestModelManager::get().add("unidirectional_sequence_lstm_batch_major_norm_peephole_projection", get_test_model());

}  // namespace generated_tests::unidirectional_sequence_lstm_batch_major_norm_peephole_projection

namespace generated_tests::unidirectional_sequence_lstm_batch_major_norm_peephole_projection {

const TestModel& get_test_model_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // input_to_input_weights
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // input_to_forget_weights
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // input_to_cell_weights
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // input_to_output_weights
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // recurrent_to_input_weights
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // recurrent_to_forget_weights
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // recurrent_to_cell_weights
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // recurrent_to_output_weights
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // cell_to_input_weights
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // cell_to_forget_weights
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // cell_to_output_weights
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // input_gate_bias
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // forget_gate_bias
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // cell_gate_bias
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // output_gate_bias
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // projection_weights
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // projection_bias
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {0},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // output_state_in
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // cell_state_in
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // activation_param
                            .type = TestOperandType::INT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({4})
                        }, { // cell_clip_param
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
                        }, { // proj_clip_param
                            .type = TestOperandType::FLOAT32,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f})
                        }, { // time_major_param
                            .type = TestOperandType::BOOL,
                            .dimensions = {},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<bool8>({false})
                        }, { // input_layer_norm_weights
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // forget_layer_norm_weights
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // cell_layer_norm_weights
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // output_layer_norm_weights
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({})
                        }, { // output
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 3},
                            .numberOfConsumers = 0,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.024407668039203f, 0.128027379512787f, -0.001709178090096f, 0.013764165341854f, 0.140751048922539f, 0.039583537727594f, -0.004592306911945f, 0.155278354883194f, 0.083737745881081f, -0.006924282759428f, 0.08487406373024f, 0.06344497948885f, -0.004039138555527f, 0.139963015913963f, 0.072681039571762f, 0.007527053356171f, 0.161902531981468f, 0.056137066334486f})
                        }, { // input_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3, 5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.7f, 0.8f, 0.1f, 0.2f, 0.3f, 0.8f, 0.1f, 0.2f, 0.4f, 0.5f, 0.2f, 0.7f, 0.7f, 0.1f, 0.7f, 0.3f, 0.2f, 0.9f, 0.8f, 0.1f, 0.1f, 0.5f, 0.2f, 0.4f, 0.2f, 0.6f, 0.9f, 0.2f, 0.5f, 0.7f})
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
                        }, { // input_to_input_weights_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.5f, 0.6f, 0.7f, -0.8f, -0.9f, 0.1f, 0.2f, 0.3f, -0.4f, 0.5f, -0.8f, 0.7f, -0.6f, 0.5f, -0.4f, -0.5f, -0.4f, -0.3f, -0.2f, -0.1f})
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
                        }, { // input_to_forget_weights_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({-0.6f, -0.1f, 0.3f, 0.2f, 0.9f, -0.5f, -0.2f, -0.4f, 0.3f, -0.8f, -0.4f, 0.3f, -0.5f, -0.4f, -0.6f, 0.3f, -0.4f, -0.6f, -0.5f, -0.5f})
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
                        }, { // input_to_cell_weights_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({-0.4f, -0.3f, -0.2f, -0.1f, -0.5f, 0.5f, -0.2f, -0.3f, -0.2f, -0.6f, 0.6f, -0.1f, -0.4f, -0.3f, -0.7f, 0.7f, -0.9f, -0.5f, 0.8f, 0.6f})
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
                        }, { // input_to_output_weights_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 5},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({-0.8f, -0.4f, -0.2f, -0.9f, -0.1f, -0.7f, 0.3f, -0.3f, -0.8f, -0.2f, 0.6f, -0.2f, 0.4f, -0.7f, -0.3f, -0.5f, 0.1f, 0.5f, -0.6f, -0.4f})
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
                        }, { // recurrent_to_input_weights_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({-0.2f, -0.3f, 0.4f, 0.1f, -0.5f, 0.9f, -0.2f, -0.3f, -0.7f, 0.05f, -0.2f, -0.6f})
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
                        }, { // recurrent_to_forget_weights_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({-0.5f, -0.3f, -0.5f, -0.2f, 0.6f, 0.4f, 0.9f, 0.3f, -0.1f, 0.2f, 0.5f, 0.2f})
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
                        }, { // recurrent_to_cell_weights_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({-0.3f, 0.2f, 0.1f, -0.3f, 0.8f, -0.08f, -0.2f, 0.3f, 0.8f, -0.6f, -0.1f, 0.2f})
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
                        }, { // recurrent_to_output_weights_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.3f, -0.1f, 0.1f, -0.2f, -0.5f, -0.7f, -0.2f, -0.6f, -0.1f, -0.4f, -0.7f, -0.2f})
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
                        }, { // cell_to_input_weights_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.05f, 0.1f, 0.25f, 0.15f})
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
                        }, { // cell_to_forget_weights_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({-0.02f, -0.15f, -0.25f, -0.03f})
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
                        }, { // cell_to_output_weights_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.1f, -0.1f, -0.5f, 0.05f})
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
                        }, { // input_gate_bias_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.03f, 0.15f, 0.22f, 0.38f})
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
                        }, { // forget_gate_bias_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.1f, -0.3f, -0.2f, 0.1f})
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
                        }, { // cell_gate_bias_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({-0.05f, 0.72f, 0.25f, 0.08f})
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
                        }, { // output_gate_bias_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.05f, -0.01f, 0.2f, 0.1f})
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
                        }, { // projection_weights_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {3, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({-0.1f, 0.2f, 0.01f, -0.2f, 0.1f, 0.5f, 0.3f, 0.08f, 0.07f, 0.2f, -0.4f, 0.2f})
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
                        }, { // output_state_in_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 3},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f})
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
                        }, { // cell_state_in_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {2, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f})
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
                        }, { // input_layer_norm_weights_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.1f, 0.2f, 0.3f, 0.5f})
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
                        }, { // forget_layer_norm_weights_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.2f, 0.2f, 0.4f, 0.3f})
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
                        }, { // cell_layer_norm_weights_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.7f, 0.2f, 0.3f, 0.8f})
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
                        }, { // output_layer_norm_weights_new
                            .type = TestOperandType::TENSOR_FLOAT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 0.0f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<float>({0.6f, 0.2f, 0.2f, 0.5f})
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
                            .inputs = {29, 30, 31},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {32, 33, 34},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {35, 36, 37},
                            .outputs = {2}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {38, 39, 40},
                            .outputs = {3}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {41, 42, 43},
                            .outputs = {4}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {44, 45, 46},
                            .outputs = {5}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {47, 48, 49},
                            .outputs = {6}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {50, 51, 52},
                            .outputs = {7}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {53, 54, 55},
                            .outputs = {8}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {56, 57, 58},
                            .outputs = {9}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {59, 60, 61},
                            .outputs = {10}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {62, 63, 64},
                            .outputs = {11}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {65, 66, 67},
                            .outputs = {12}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {68, 69, 70},
                            .outputs = {13}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {71, 72, 73},
                            .outputs = {14}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {74, 75, 76},
                            .outputs = {15}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {77, 78, 79},
                            .outputs = {16}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {80, 81, 82},
                            .outputs = {18}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {83, 84, 85},
                            .outputs = {19}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {86, 87, 88},
                            .outputs = {24}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {89, 90, 91},
                            .outputs = {25}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {92, 93, 94},
                            .outputs = {26}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {95, 96, 97},
                            .outputs = {27}
                        }, {
                            .type = TestOperationType::UNIDIRECTIONAL_SEQUENCE_LSTM,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27},
                            .outputs = {28}
                        }},
                .inputIndexes = {17, 29, 32, 35, 38, 41, 44, 47, 50, 53, 56, 59, 62, 65, 68, 71, 74, 77, 80, 83, 86, 89, 92, 95},
                .outputIndexes = {28}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal = TestModelManager::get().add("unidirectional_sequence_lstm_batch_major_norm_peephole_projection_all_inputs_as_internal", get_test_model_all_inputs_as_internal());

}  // namespace generated_tests::unidirectional_sequence_lstm_batch_major_norm_peephole_projection

