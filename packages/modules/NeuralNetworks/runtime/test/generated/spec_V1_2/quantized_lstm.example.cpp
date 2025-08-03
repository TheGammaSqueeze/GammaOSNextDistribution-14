// Generated from quantized_lstm.mod.py
// DO NOT EDIT
// clang-format off
#include "TestHarness.h"
using namespace test_helper;  // NOLINT(google-build-using-namespace)

namespace generated_tests::quantized_lstm {

const TestModel& get_test_model() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({166, 179, 50, 150})
                        }, { // inputToInputWeights
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({146, 250, 235, 171, 10, 218, 171, 108})
                        }, { // inputToForgetWeights
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({24, 50, 132, 179, 158, 110, 3, 169})
                        }, { // inputToCellWeights
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({133, 34, 29, 49, 206, 109, 54, 183})
                        }, { // inputToOutputWeights
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({195, 187, 11, 99, 109, 10, 218, 48})
                        }, { // recurrentToInputWeights
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({254, 206, 77, 168, 71, 20, 215, 6, 223, 7, 118, 225, 59, 130, 174, 26})
                        }, { // recurrentToForgetWeights
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({137, 240, 103, 52, 68, 51, 237, 112, 0, 220, 89, 23, 69, 4, 207, 253})
                        }, { // recurrentToCellWeights
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({172, 60, 205, 65, 14, 0, 140, 168, 240, 223, 133, 56, 142, 64, 246, 216})
                        }, { // recurrentToOutputWeights
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({106, 214, 67, 23, 59, 158, 45, 3, 119, 132, 49, 205, 129, 218, 11, 98})
                        }, { // inputGateBias
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-7876, 13488, -726, 32839})
                        }, { // forgetGateBias
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({9206, -46884, -11693, -38724})
                        }, { // cellGateBias
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({39481, 48624, 48976, -21419})
                        }, { // outputGateBias
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-58999, -17050, -41852, -40538})
                        }, { // prevCellState
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {2, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00048828125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({876, 1034, 955, -909, 761, 1029, 796, -1036})
                        }, { // prevOutput
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({136, 150, 140, 115, 135, 152, 138, 112})
                        }, { // cellStateOut
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {2, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.00048828125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({1485, 1177, 1373, -1023, 1019, 1355, 1097, -1235})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({140, 151, 146, 112, 136, 156, 142, 112})
                        }},
                .operations = {{
                            .type = TestOperationType::QUANTIZED_16BIT_LSTM,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14},
                            .outputs = {15, 16}
                        }},
                .inputIndexes = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14},
                .outputIndexes = {15, 16}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model = TestModelManager::get().add("quantized_lstm", get_test_model());

}  // namespace generated_tests::quantized_lstm

namespace generated_tests::quantized_lstm {

const TestModel& get_test_model_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // inputToInputWeights
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // inputToForgetWeights
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // inputToCellWeights
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // inputToOutputWeights
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // recurrentToInputWeights
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // recurrentToForgetWeights
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // recurrentToCellWeights
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // recurrentToOutputWeights
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // inputGateBias
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-7876, 13488, -726, 32839})
                        }, { // forgetGateBias
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({9206, -46884, -11693, -38724})
                        }, { // cellGateBias
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({39481, 48624, 48976, -21419})
                        }, { // outputGateBias
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-58999, -17050, -41852, -40538})
                        }, { // prevCellState
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {2, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00048828125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({876, 1034, 955, -909, 761, 1029, 796, -1036})
                        }, { // prevOutput
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // cellStateOut
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {2, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.00048828125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({1485, 1177, 1373, -1023, 1019, 1355, 1097, -1235})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({140, 151, 146, 112, 136, 156, 142, 112})
                        }, { // input_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({166, 179, 50, 150})
                        }, { // placeholder
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
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
                        }, { // inputToInputWeights_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({146, 250, 235, 171, 10, 218, 171, 108})
                        }, { // placeholder1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({100})
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
                        }, { // inputToForgetWeights_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({24, 50, 132, 179, 158, 110, 3, 169})
                        }, { // placeholder2
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({100})
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
                        }, { // inputToCellWeights_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({133, 34, 29, 49, 206, 109, 54, 183})
                        }, { // placeholder3
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({100})
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
                        }, { // inputToOutputWeights_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({195, 187, 11, 99, 109, 10, 218, 48})
                        }, { // placeholder4
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({100})
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
                        }, { // recurrentToInputWeights_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({254, 206, 77, 168, 71, 20, 215, 6, 223, 7, 118, 225, 59, 130, 174, 26})
                        }, { // placeholder5
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({100})
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
                        }, { // recurrentToForgetWeights_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({137, 240, 103, 52, 68, 51, 237, 112, 0, 220, 89, 23, 69, 4, 207, 253})
                        }, { // placeholder6
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({100})
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
                        }, { // recurrentToCellWeights_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({172, 60, 205, 65, 14, 0, 140, 168, 240, 223, 133, 56, 142, 64, 246, 216})
                        }, { // placeholder7
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({100})
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
                        }, { // recurrentToOutputWeights_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({106, 214, 67, 23, 59, 158, 45, 3, 119, 132, 49, 205, 129, 218, 11, 98})
                        }, { // placeholder8
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({100})
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
                        }, { // prevOutput_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({136, 150, 140, 115, 135, 152, 138, 112})
                        }, { // placeholder9
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
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
                            .inputs = {17, 18, 19},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {20, 21, 22},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {23, 24, 25},
                            .outputs = {2}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {26, 27, 28},
                            .outputs = {3}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {29, 30, 31},
                            .outputs = {4}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {32, 33, 34},
                            .outputs = {5}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {35, 36, 37},
                            .outputs = {6}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {38, 39, 40},
                            .outputs = {7}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {41, 42, 43},
                            .outputs = {8}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {44, 45, 46},
                            .outputs = {14}
                        }, {
                            .type = TestOperationType::QUANTIZED_16BIT_LSTM,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14},
                            .outputs = {15, 16}
                        }},
                .inputIndexes = {9, 10, 11, 12, 13, 17, 20, 23, 26, 29, 32, 35, 38, 41, 44},
                .outputIndexes = {15, 16}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_all_inputs_as_internal = TestModelManager::get().add("quantized_lstm_all_inputs_as_internal", get_test_model_all_inputs_as_internal());

}  // namespace generated_tests::quantized_lstm

namespace generated_tests::quantized_lstm {

const TestModel& get_test_model_relaxed() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({166, 179, 50, 150})
                        }, { // inputToInputWeights
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({146, 250, 235, 171, 10, 218, 171, 108})
                        }, { // inputToForgetWeights
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({24, 50, 132, 179, 158, 110, 3, 169})
                        }, { // inputToCellWeights
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({133, 34, 29, 49, 206, 109, 54, 183})
                        }, { // inputToOutputWeights
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({195, 187, 11, 99, 109, 10, 218, 48})
                        }, { // recurrentToInputWeights
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({254, 206, 77, 168, 71, 20, 215, 6, 223, 7, 118, 225, 59, 130, 174, 26})
                        }, { // recurrentToForgetWeights
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({137, 240, 103, 52, 68, 51, 237, 112, 0, 220, 89, 23, 69, 4, 207, 253})
                        }, { // recurrentToCellWeights
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({172, 60, 205, 65, 14, 0, 140, 168, 240, 223, 133, 56, 142, 64, 246, 216})
                        }, { // recurrentToOutputWeights
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({106, 214, 67, 23, 59, 158, 45, 3, 119, 132, 49, 205, 129, 218, 11, 98})
                        }, { // inputGateBias
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-7876, 13488, -726, 32839})
                        }, { // forgetGateBias
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({9206, -46884, -11693, -38724})
                        }, { // cellGateBias
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({39481, 48624, 48976, -21419})
                        }, { // outputGateBias
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-58999, -17050, -41852, -40538})
                        }, { // prevCellState
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {2, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00048828125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({876, 1034, 955, -909, 761, 1029, 796, -1036})
                        }, { // prevOutput
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({136, 150, 140, 115, 135, 152, 138, 112})
                        }, { // cellStateOut
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {2, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.00048828125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({1485, 1177, 1373, -1023, 1019, 1355, 1097, -1235})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({140, 151, 146, 112, 136, 156, 142, 112})
                        }},
                .operations = {{
                            .type = TestOperationType::QUANTIZED_16BIT_LSTM,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14},
                            .outputs = {15, 16}
                        }},
                .inputIndexes = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14},
                .outputIndexes = {15, 16}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed = TestModelManager::get().add("quantized_lstm_relaxed", get_test_model_relaxed());

}  // namespace generated_tests::quantized_lstm

namespace generated_tests::quantized_lstm {

const TestModel& get_test_model_relaxed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // input
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // inputToInputWeights
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // inputToForgetWeights
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // inputToCellWeights
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // inputToOutputWeights
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // recurrentToInputWeights
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // recurrentToForgetWeights
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // recurrentToCellWeights
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // recurrentToOutputWeights
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // inputGateBias
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-7876, 13488, -726, 32839})
                        }, { // forgetGateBias
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({9206, -46884, -11693, -38724})
                        }, { // cellGateBias
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({39481, 48624, 48976, -21419})
                        }, { // outputGateBias
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-58999, -17050, -41852, -40538})
                        }, { // prevCellState
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {2, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00048828125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({876, 1034, 955, -909, 761, 1029, 796, -1036})
                        }, { // prevOutput
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // cellStateOut
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {2, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.00048828125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({1485, 1177, 1373, -1023, 1019, 1355, 1097, -1235})
                        }, { // output
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({140, 151, 146, 112, 136, 156, 142, 112})
                        }, { // input_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({166, 179, 50, 150})
                        }, { // placeholder10
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
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
                        }, { // inputToInputWeights_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({146, 250, 235, 171, 10, 218, 171, 108})
                        }, { // placeholder11
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({100})
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
                        }, { // inputToForgetWeights_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({24, 50, 132, 179, 158, 110, 3, 169})
                        }, { // placeholder12
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({100})
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
                        }, { // inputToCellWeights_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({133, 34, 29, 49, 206, 109, 54, 183})
                        }, { // placeholder13
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({100})
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
                        }, { // inputToOutputWeights_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({195, 187, 11, 99, 109, 10, 218, 48})
                        }, { // placeholder14
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({100})
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
                        }, { // recurrentToInputWeights_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({254, 206, 77, 168, 71, 20, 215, 6, 223, 7, 118, 225, 59, 130, 174, 26})
                        }, { // placeholder15
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({100})
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
                        }, { // recurrentToForgetWeights_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({137, 240, 103, 52, 68, 51, 237, 112, 0, 220, 89, 23, 69, 4, 207, 253})
                        }, { // placeholder16
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({100})
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
                        }, { // recurrentToCellWeights_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({172, 60, 205, 65, 14, 0, 140, 168, 240, 223, 133, 56, 142, 64, 246, 216})
                        }, { // placeholder17
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({100})
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
                        }, { // recurrentToOutputWeights_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({106, 214, 67, 23, 59, 158, 45, 3, 119, 132, 49, 205, 129, 218, 11, 98})
                        }, { // placeholder18
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({100})
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
                        }, { // prevOutput_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {2, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({136, 150, 140, 115, 135, 152, 138, 112})
                        }, { // placeholder19
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
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
                            .inputs = {17, 18, 19},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {20, 21, 22},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {23, 24, 25},
                            .outputs = {2}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {26, 27, 28},
                            .outputs = {3}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {29, 30, 31},
                            .outputs = {4}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {32, 33, 34},
                            .outputs = {5}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {35, 36, 37},
                            .outputs = {6}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {38, 39, 40},
                            .outputs = {7}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {41, 42, 43},
                            .outputs = {8}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {44, 45, 46},
                            .outputs = {14}
                        }, {
                            .type = TestOperationType::QUANTIZED_16BIT_LSTM,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14},
                            .outputs = {15, 16}
                        }},
                .inputIndexes = {9, 10, 11, 12, 13, 17, 20, 23, 26, 29, 32, 35, 38, 41, 44},
                .outputIndexes = {15, 16}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_relaxed_all_inputs_as_internal = TestModelManager::get().add("quantized_lstm_relaxed_all_inputs_as_internal", get_test_model_relaxed_all_inputs_as_internal());

}  // namespace generated_tests::quantized_lstm

namespace generated_tests::quantized_lstm {

const TestModel& get_test_model_constant_weights() {
    static TestModel model = {
        .main = {
                .operands = {{ // input1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({166, 179})
                        }, { // inputToInputWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({146, 250, 235, 171, 10, 218, 171, 108})
                        }, { // inputToForgetWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({24, 50, 132, 179, 158, 110, 3, 169})
                        }, { // inputToCellWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({133, 34, 29, 49, 206, 109, 54, 183})
                        }, { // inputToOutputWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({195, 187, 11, 99, 109, 10, 218, 48})
                        }, { // recurrentToInputWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({254, 206, 77, 168, 71, 20, 215, 6, 223, 7, 118, 225, 59, 130, 174, 26})
                        }, { // recurrentToForgetWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({137, 240, 103, 52, 68, 51, 237, 112, 0, 220, 89, 23, 69, 4, 207, 253})
                        }, { // recurrentToCellWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({172, 60, 205, 65, 14, 0, 140, 168, 240, 223, 133, 56, 142, 64, 246, 216})
                        }, { // recurrentToOutputWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({106, 214, 67, 23, 59, 158, 45, 3, 119, 132, 49, 205, 129, 218, 11, 98})
                        }, { // inputGateBias1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-7876, 13488, -726, 32839})
                        }, { // forgetGateBias1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({9206, -46884, -11693, -38724})
                        }, { // cellGateBias1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({39481, 48624, 48976, -21419})
                        }, { // outputGateBias1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-58999, -17050, -41852, -40538})
                        }, { // prevCellState1
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {1, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00048828125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({876, 1034, 955, -909})
                        }, { // prevOutput1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({136, 150, 140, 115})
                        }, { // cellStateOut1
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {1, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.00048828125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({1485, 1177, 1373, -1023})
                        }, { // output1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({140, 151, 146, 112})
                        }},
                .operations = {{
                            .type = TestOperationType::QUANTIZED_16BIT_LSTM,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14},
                            .outputs = {15, 16}
                        }},
                .inputIndexes = {0, 13, 14},
                .outputIndexes = {15, 16}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_constant_weights = TestModelManager::get().add("quantized_lstm_constant_weights", get_test_model_constant_weights());

}  // namespace generated_tests::quantized_lstm

namespace generated_tests::quantized_lstm {

const TestModel& get_test_model_constant_weights_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // input1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // inputToInputWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({146, 250, 235, 171, 10, 218, 171, 108})
                        }, { // inputToForgetWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({24, 50, 132, 179, 158, 110, 3, 169})
                        }, { // inputToCellWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({133, 34, 29, 49, 206, 109, 54, 183})
                        }, { // inputToOutputWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({195, 187, 11, 99, 109, 10, 218, 48})
                        }, { // recurrentToInputWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({254, 206, 77, 168, 71, 20, 215, 6, 223, 7, 118, 225, 59, 130, 174, 26})
                        }, { // recurrentToForgetWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({137, 240, 103, 52, 68, 51, 237, 112, 0, 220, 89, 23, 69, 4, 207, 253})
                        }, { // recurrentToCellWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({172, 60, 205, 65, 14, 0, 140, 168, 240, 223, 133, 56, 142, 64, 246, 216})
                        }, { // recurrentToOutputWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({106, 214, 67, 23, 59, 158, 45, 3, 119, 132, 49, 205, 129, 218, 11, 98})
                        }, { // inputGateBias1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-7876, 13488, -726, 32839})
                        }, { // forgetGateBias1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({9206, -46884, -11693, -38724})
                        }, { // cellGateBias1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({39481, 48624, 48976, -21419})
                        }, { // outputGateBias1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-58999, -17050, -41852, -40538})
                        }, { // prevCellState1
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {1, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00048828125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({876, 1034, 955, -909})
                        }, { // prevOutput1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // cellStateOut1
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {1, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.00048828125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({1485, 1177, 1373, -1023})
                        }, { // output1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({140, 151, 146, 112})
                        }, { // input1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({166, 179})
                        }, { // placeholder20
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
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
                        }, { // prevOutput1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({136, 150, 140, 115})
                        }, { // placeholder21
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
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
                            .inputs = {17, 18, 19},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {20, 21, 22},
                            .outputs = {14}
                        }, {
                            .type = TestOperationType::QUANTIZED_16BIT_LSTM,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14},
                            .outputs = {15, 16}
                        }},
                .inputIndexes = {13, 17, 20},
                .outputIndexes = {15, 16}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_constant_weights_all_inputs_as_internal = TestModelManager::get().add("quantized_lstm_constant_weights_all_inputs_as_internal", get_test_model_constant_weights_all_inputs_as_internal());

}  // namespace generated_tests::quantized_lstm

namespace generated_tests::quantized_lstm {

const TestModel& get_test_model_constant_weights_all_tensors_as_inputs() {
    static TestModel model = {
        .main = {
                .operands = {{ // input1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({166, 179})
                        }, { // inputToInputWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({146, 250, 235, 171, 10, 218, 171, 108})
                        }, { // inputToForgetWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({24, 50, 132, 179, 158, 110, 3, 169})
                        }, { // inputToCellWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({133, 34, 29, 49, 206, 109, 54, 183})
                        }, { // inputToOutputWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({195, 187, 11, 99, 109, 10, 218, 48})
                        }, { // recurrentToInputWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({254, 206, 77, 168, 71, 20, 215, 6, 223, 7, 118, 225, 59, 130, 174, 26})
                        }, { // recurrentToForgetWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({137, 240, 103, 52, 68, 51, 237, 112, 0, 220, 89, 23, 69, 4, 207, 253})
                        }, { // recurrentToCellWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({172, 60, 205, 65, 14, 0, 140, 168, 240, 223, 133, 56, 142, 64, 246, 216})
                        }, { // recurrentToOutputWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({106, 214, 67, 23, 59, 158, 45, 3, 119, 132, 49, 205, 129, 218, 11, 98})
                        }, { // inputGateBias1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-7876, 13488, -726, 32839})
                        }, { // forgetGateBias1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({9206, -46884, -11693, -38724})
                        }, { // cellGateBias1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({39481, 48624, 48976, -21419})
                        }, { // outputGateBias1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-58999, -17050, -41852, -40538})
                        }, { // prevCellState1
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {1, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00048828125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({876, 1034, 955, -909})
                        }, { // prevOutput1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({136, 150, 140, 115})
                        }, { // cellStateOut1
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {1, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.00048828125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({1485, 1177, 1373, -1023})
                        }, { // output1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({140, 151, 146, 112})
                        }},
                .operations = {{
                            .type = TestOperationType::QUANTIZED_16BIT_LSTM,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14},
                            .outputs = {15, 16}
                        }},
                .inputIndexes = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14},
                .outputIndexes = {15, 16}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_constant_weights_all_tensors_as_inputs = TestModelManager::get().add("quantized_lstm_constant_weights_all_tensors_as_inputs", get_test_model_constant_weights_all_tensors_as_inputs());

}  // namespace generated_tests::quantized_lstm

namespace generated_tests::quantized_lstm {

const TestModel& get_test_model_constant_weights_all_tensors_as_inputs_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // input1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // inputToInputWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // inputToForgetWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // inputToCellWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // inputToOutputWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // recurrentToInputWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // recurrentToForgetWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // recurrentToCellWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // recurrentToOutputWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // inputGateBias1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-7876, 13488, -726, 32839})
                        }, { // forgetGateBias1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({9206, -46884, -11693, -38724})
                        }, { // cellGateBias1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({39481, 48624, 48976, -21419})
                        }, { // outputGateBias1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-58999, -17050, -41852, -40538})
                        }, { // prevCellState1
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {1, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00048828125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({876, 1034, 955, -909})
                        }, { // prevOutput1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // cellStateOut1
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {1, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.00048828125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({1485, 1177, 1373, -1023})
                        }, { // output1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({140, 151, 146, 112})
                        }, { // input1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({166, 179})
                        }, { // placeholder22
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
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
                        }, { // inputToInputWeights1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({146, 250, 235, 171, 10, 218, 171, 108})
                        }, { // placeholder23
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({100})
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
                        }, { // inputToForgetWeights1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({24, 50, 132, 179, 158, 110, 3, 169})
                        }, { // placeholder24
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({100})
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
                        }, { // inputToCellWeights1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({133, 34, 29, 49, 206, 109, 54, 183})
                        }, { // placeholder25
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({100})
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
                        }, { // inputToOutputWeights1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({195, 187, 11, 99, 109, 10, 218, 48})
                        }, { // placeholder26
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({100})
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
                        }, { // recurrentToInputWeights1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({254, 206, 77, 168, 71, 20, 215, 6, 223, 7, 118, 225, 59, 130, 174, 26})
                        }, { // placeholder27
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({100})
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
                        }, { // recurrentToForgetWeights1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({137, 240, 103, 52, 68, 51, 237, 112, 0, 220, 89, 23, 69, 4, 207, 253})
                        }, { // placeholder28
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({100})
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
                        }, { // recurrentToCellWeights1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({172, 60, 205, 65, 14, 0, 140, 168, 240, 223, 133, 56, 142, 64, 246, 216})
                        }, { // placeholder29
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({100})
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
                        }, { // recurrentToOutputWeights1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({106, 214, 67, 23, 59, 158, 45, 3, 119, 132, 49, 205, 129, 218, 11, 98})
                        }, { // placeholder30
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({100})
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
                        }, { // prevOutput1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({136, 150, 140, 115})
                        }, { // placeholder31
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
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
                            .inputs = {17, 18, 19},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {20, 21, 22},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {23, 24, 25},
                            .outputs = {2}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {26, 27, 28},
                            .outputs = {3}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {29, 30, 31},
                            .outputs = {4}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {32, 33, 34},
                            .outputs = {5}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {35, 36, 37},
                            .outputs = {6}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {38, 39, 40},
                            .outputs = {7}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {41, 42, 43},
                            .outputs = {8}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {44, 45, 46},
                            .outputs = {14}
                        }, {
                            .type = TestOperationType::QUANTIZED_16BIT_LSTM,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14},
                            .outputs = {15, 16}
                        }},
                .inputIndexes = {9, 10, 11, 12, 13, 17, 20, 23, 26, 29, 32, 35, 38, 41, 44},
                .outputIndexes = {15, 16}
            },
        .referenced = {},
        .isRelaxed = false,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::V1_2
    };
    return model;
}

const auto dummy_test_model_constant_weights_all_tensors_as_inputs_all_inputs_as_internal = TestModelManager::get().add("quantized_lstm_constant_weights_all_tensors_as_inputs_all_inputs_as_internal", get_test_model_constant_weights_all_tensors_as_inputs_all_inputs_as_internal());

}  // namespace generated_tests::quantized_lstm

namespace generated_tests::quantized_lstm {

const TestModel& get_test_model_constant_weights_relaxed() {
    static TestModel model = {
        .main = {
                .operands = {{ // input1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({166, 179})
                        }, { // inputToInputWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({146, 250, 235, 171, 10, 218, 171, 108})
                        }, { // inputToForgetWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({24, 50, 132, 179, 158, 110, 3, 169})
                        }, { // inputToCellWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({133, 34, 29, 49, 206, 109, 54, 183})
                        }, { // inputToOutputWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({195, 187, 11, 99, 109, 10, 218, 48})
                        }, { // recurrentToInputWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({254, 206, 77, 168, 71, 20, 215, 6, 223, 7, 118, 225, 59, 130, 174, 26})
                        }, { // recurrentToForgetWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({137, 240, 103, 52, 68, 51, 237, 112, 0, 220, 89, 23, 69, 4, 207, 253})
                        }, { // recurrentToCellWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({172, 60, 205, 65, 14, 0, 140, 168, 240, 223, 133, 56, 142, 64, 246, 216})
                        }, { // recurrentToOutputWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({106, 214, 67, 23, 59, 158, 45, 3, 119, 132, 49, 205, 129, 218, 11, 98})
                        }, { // inputGateBias1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-7876, 13488, -726, 32839})
                        }, { // forgetGateBias1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({9206, -46884, -11693, -38724})
                        }, { // cellGateBias1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({39481, 48624, 48976, -21419})
                        }, { // outputGateBias1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-58999, -17050, -41852, -40538})
                        }, { // prevCellState1
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {1, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00048828125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({876, 1034, 955, -909})
                        }, { // prevOutput1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({136, 150, 140, 115})
                        }, { // cellStateOut1
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {1, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.00048828125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({1485, 1177, 1373, -1023})
                        }, { // output1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({140, 151, 146, 112})
                        }},
                .operations = {{
                            .type = TestOperationType::QUANTIZED_16BIT_LSTM,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14},
                            .outputs = {15, 16}
                        }},
                .inputIndexes = {0, 13, 14},
                .outputIndexes = {15, 16}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_constant_weights_relaxed = TestModelManager::get().add("quantized_lstm_constant_weights_relaxed", get_test_model_constant_weights_relaxed());

}  // namespace generated_tests::quantized_lstm

namespace generated_tests::quantized_lstm {

const TestModel& get_test_model_constant_weights_relaxed_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // input1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // inputToInputWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({146, 250, 235, 171, 10, 218, 171, 108})
                        }, { // inputToForgetWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({24, 50, 132, 179, 158, 110, 3, 169})
                        }, { // inputToCellWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({133, 34, 29, 49, 206, 109, 54, 183})
                        }, { // inputToOutputWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({195, 187, 11, 99, 109, 10, 218, 48})
                        }, { // recurrentToInputWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({254, 206, 77, 168, 71, 20, 215, 6, 223, 7, 118, 225, 59, 130, 174, 26})
                        }, { // recurrentToForgetWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({137, 240, 103, 52, 68, 51, 237, 112, 0, 220, 89, 23, 69, 4, 207, 253})
                        }, { // recurrentToCellWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({172, 60, 205, 65, 14, 0, 140, 168, 240, 223, 133, 56, 142, 64, 246, 216})
                        }, { // recurrentToOutputWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({106, 214, 67, 23, 59, 158, 45, 3, 119, 132, 49, 205, 129, 218, 11, 98})
                        }, { // inputGateBias1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-7876, 13488, -726, 32839})
                        }, { // forgetGateBias1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({9206, -46884, -11693, -38724})
                        }, { // cellGateBias1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({39481, 48624, 48976, -21419})
                        }, { // outputGateBias1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-58999, -17050, -41852, -40538})
                        }, { // prevCellState1
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {1, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00048828125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({876, 1034, 955, -909})
                        }, { // prevOutput1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // cellStateOut1
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {1, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.00048828125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({1485, 1177, 1373, -1023})
                        }, { // output1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({140, 151, 146, 112})
                        }, { // input1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({166, 179})
                        }, { // placeholder32
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
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
                        }, { // prevOutput1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({136, 150, 140, 115})
                        }, { // placeholder33
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
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
                            .inputs = {17, 18, 19},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {20, 21, 22},
                            .outputs = {14}
                        }, {
                            .type = TestOperationType::QUANTIZED_16BIT_LSTM,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14},
                            .outputs = {15, 16}
                        }},
                .inputIndexes = {13, 17, 20},
                .outputIndexes = {15, 16}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_constant_weights_relaxed_all_inputs_as_internal = TestModelManager::get().add("quantized_lstm_constant_weights_relaxed_all_inputs_as_internal", get_test_model_constant_weights_relaxed_all_inputs_as_internal());

}  // namespace generated_tests::quantized_lstm

namespace generated_tests::quantized_lstm {

const TestModel& get_test_model_constant_weights_relaxed_all_tensors_as_inputs() {
    static TestModel model = {
        .main = {
                .operands = {{ // input1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({166, 179})
                        }, { // inputToInputWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({146, 250, 235, 171, 10, 218, 171, 108})
                        }, { // inputToForgetWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({24, 50, 132, 179, 158, 110, 3, 169})
                        }, { // inputToCellWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({133, 34, 29, 49, 206, 109, 54, 183})
                        }, { // inputToOutputWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({195, 187, 11, 99, 109, 10, 218, 48})
                        }, { // recurrentToInputWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({254, 206, 77, 168, 71, 20, 215, 6, 223, 7, 118, 225, 59, 130, 174, 26})
                        }, { // recurrentToForgetWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({137, 240, 103, 52, 68, 51, 237, 112, 0, 220, 89, 23, 69, 4, 207, 253})
                        }, { // recurrentToCellWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({172, 60, 205, 65, 14, 0, 140, 168, 240, 223, 133, 56, 142, 64, 246, 216})
                        }, { // recurrentToOutputWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({106, 214, 67, 23, 59, 158, 45, 3, 119, 132, 49, 205, 129, 218, 11, 98})
                        }, { // inputGateBias1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-7876, 13488, -726, 32839})
                        }, { // forgetGateBias1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({9206, -46884, -11693, -38724})
                        }, { // cellGateBias1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({39481, 48624, 48976, -21419})
                        }, { // outputGateBias1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-58999, -17050, -41852, -40538})
                        }, { // prevCellState1
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {1, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00048828125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({876, 1034, 955, -909})
                        }, { // prevOutput1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({136, 150, 140, 115})
                        }, { // cellStateOut1
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {1, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.00048828125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({1485, 1177, 1373, -1023})
                        }, { // output1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({140, 151, 146, 112})
                        }},
                .operations = {{
                            .type = TestOperationType::QUANTIZED_16BIT_LSTM,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14},
                            .outputs = {15, 16}
                        }},
                .inputIndexes = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14},
                .outputIndexes = {15, 16}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_constant_weights_relaxed_all_tensors_as_inputs = TestModelManager::get().add("quantized_lstm_constant_weights_relaxed_all_tensors_as_inputs", get_test_model_constant_weights_relaxed_all_tensors_as_inputs());

}  // namespace generated_tests::quantized_lstm

namespace generated_tests::quantized_lstm {

const TestModel& get_test_model_constant_weights_relaxed_all_tensors_as_inputs_all_inputs_as_internal() {
    static TestModel model = {
        .main = {
                .operands = {{ // input1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // inputToInputWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // inputToForgetWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // inputToCellWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // inputToOutputWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // recurrentToInputWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // recurrentToForgetWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // recurrentToCellWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // recurrentToOutputWeights1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // inputGateBias1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-7876, 13488, -726, 32839})
                        }, { // forgetGateBias1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({9206, -46884, -11693, -38724})
                        }, { // cellGateBias1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({39481, 48624, 48976, -21419})
                        }, { // outputGateBias1
                            .type = TestOperandType::TENSOR_INT32,
                            .dimensions = {4},
                            .numberOfConsumers = 1,
                            .scale = 3.1876640625e-05f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int32_t>({-58999, -17050, -41852, -40538})
                        }, { // prevCellState1
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {1, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00048828125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({876, 1034, 955, -909})
                        }, { // prevOutput1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::TEMPORARY_VARIABLE,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({})
                        }, { // cellStateOut1
                            .type = TestOperandType::TENSOR_QUANT16_SYMM,
                            .dimensions = {1, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.00048828125f,
                            .zeroPoint = 0,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<int16_t>({1485, 1177, 1373, -1023})
                        }, { // output1
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 4},
                            .numberOfConsumers = 0,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_OUTPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({140, 151, 146, 112})
                        }, { // input1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({166, 179})
                        }, { // placeholder34
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({128})
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
                        }, { // inputToInputWeights1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({146, 250, 235, 171, 10, 218, 171, 108})
                        }, { // placeholder35
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({100})
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
                        }, { // inputToForgetWeights1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({24, 50, 132, 179, 158, 110, 3, 169})
                        }, { // placeholder36
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({100})
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
                        }, { // inputToCellWeights1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({133, 34, 29, 49, 206, 109, 54, 183})
                        }, { // placeholder37
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({100})
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
                        }, { // inputToOutputWeights1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 2},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({195, 187, 11, 99, 109, 10, 218, 48})
                        }, { // placeholder38
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({100})
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
                        }, { // recurrentToInputWeights1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({254, 206, 77, 168, 71, 20, 215, 6, 223, 7, 118, 225, 59, 130, 174, 26})
                        }, { // placeholder39
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({100})
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
                        }, { // recurrentToForgetWeights1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({137, 240, 103, 52, 68, 51, 237, 112, 0, 220, 89, 23, 69, 4, 207, 253})
                        }, { // placeholder40
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({100})
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
                        }, { // recurrentToCellWeights1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({172, 60, 205, 65, 14, 0, 140, 168, 240, 223, 133, 56, 142, 64, 246, 216})
                        }, { // placeholder41
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({100})
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
                        }, { // recurrentToOutputWeights1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {4, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({106, 214, 67, 23, 59, 158, 45, 3, 119, 132, 49, 205, 129, 218, 11, 98})
                        }, { // placeholder42
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.00408021f,
                            .zeroPoint = 100,
                            .lifetime = TestOperandLifeTime::CONSTANT_COPY,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({100})
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
                        }, { // prevOutput1_new
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1, 4},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
                            .zeroPoint = 128,
                            .lifetime = TestOperandLifeTime::SUBGRAPH_INPUT,
                            .channelQuant = {},
                            .isIgnored = false,
                            .data = TestBuffer::createFromVector<uint8_t>({136, 150, 140, 115})
                        }, { // placeholder43
                            .type = TestOperandType::TENSOR_QUANT8_ASYMM,
                            .dimensions = {1},
                            .numberOfConsumers = 1,
                            .scale = 0.0078125f,
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
                            .inputs = {17, 18, 19},
                            .outputs = {0}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {20, 21, 22},
                            .outputs = {1}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {23, 24, 25},
                            .outputs = {2}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {26, 27, 28},
                            .outputs = {3}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {29, 30, 31},
                            .outputs = {4}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {32, 33, 34},
                            .outputs = {5}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {35, 36, 37},
                            .outputs = {6}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {38, 39, 40},
                            .outputs = {7}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {41, 42, 43},
                            .outputs = {8}
                        }, {
                            .type = TestOperationType::ADD,
                            .inputs = {44, 45, 46},
                            .outputs = {14}
                        }, {
                            .type = TestOperationType::QUANTIZED_16BIT_LSTM,
                            .inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14},
                            .outputs = {15, 16}
                        }},
                .inputIndexes = {9, 10, 11, 12, 13, 17, 20, 23, 26, 29, 32, 35, 38, 41, 44},
                .outputIndexes = {15, 16}
            },
        .referenced = {},
        .isRelaxed = true,
        .expectedMultinomialDistributionTolerance = 0,
        .expectFailure = false,
        .minSupportedVersion = TestHalVersion::UNKNOWN
    };
    return model;
}

const auto dummy_test_model_constant_weights_relaxed_all_tensors_as_inputs_all_inputs_as_internal = TestModelManager::get().add("quantized_lstm_constant_weights_relaxed_all_tensors_as_inputs_all_inputs_as_internal", get_test_model_constant_weights_relaxed_all_tensors_as_inputs_all_inputs_as_internal());

}  // namespace generated_tests::quantized_lstm

