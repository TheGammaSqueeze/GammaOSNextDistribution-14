/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <android-base/scopeguard.h>
#include <gtest/gtest.h>
#include <utility>
#include <vector>

#include "Telemetry.h"
#include "TestNeuralNetworksWrapper.h"

using android::nn::telemetry::DataClass;
using android::nn::test_wrapper::Compilation;
using android::nn::test_wrapper::Execution;
using android::nn::test_wrapper::Model;
using android::nn::test_wrapper::OperandType;
using android::nn::test_wrapper::Result;
using android::nn::test_wrapper::Type;

namespace {

typedef float Matrix3x4[3][4];

class TelemetryTest : public ::testing::Test {};

TEST_F(TelemetryTest, TestAtomGeneration) {
    std::atomic_uint executions = 0;
    std::atomic_uint compilations = 0;

    android::nn::telemetry::registerTelemetryCallbacks(
            [&compilations](const android::nn::telemetry::DiagnosticCompilationInfo*) {
                compilations++;
            },
            [&executions](const android::nn::telemetry::DiagnosticExecutionInfo*) {
                executions++;
            });

    Model modelAdd2;
    OperandType matrixType(Type::TENSOR_FLOAT32, {3, 4});
    OperandType scalarType(Type::INT32, {});
    auto a = modelAdd2.addOperand(&matrixType);
    auto b = modelAdd2.addOperand(&matrixType);
    auto c = modelAdd2.addOperand(&matrixType);
    auto d = modelAdd2.addConstantOperand(&scalarType, ANEURALNETWORKS_FUSED_NONE);
    modelAdd2.addOperation(ANEURALNETWORKS_ADD, {a, b, d}, {c});
    modelAdd2.identifyInputsAndOutputs({a, b}, {c});
    ASSERT_TRUE(modelAdd2.isValid());
    modelAdd2.finish();

    Matrix3x4 matrix;
    memset(&matrix, 0, sizeof(matrix));
    Compilation compilation(&modelAdd2);
    compilation.finish();
    Execution execution(&compilation);
    ASSERT_EQ(execution.setInput(0, matrix, sizeof(Matrix3x4)), Result::NO_ERROR);
    ASSERT_EQ(execution.setInput(1, matrix, sizeof(Matrix3x4)), Result::NO_ERROR);
    ASSERT_EQ(execution.setOutput(0, matrix, sizeof(Matrix3x4)), Result::NO_ERROR);
    ASSERT_EQ(execution.compute(), Result::NO_ERROR);
    ASSERT_EQ(executions, 1u);
    ASSERT_EQ(compilations, 1u);

    android::nn::telemetry::clearTelemetryCallbacks();
}

TEST_F(TelemetryTest, TestEvalDataClass) {
    std::vector<std::pair<DataClass, std::vector<android::nn::OperandType>>> data = {
            {DataClass::FLOAT32, {android::nn::OperandType::TENSOR_FLOAT32}},
            {DataClass::FLOAT32,
             {android::nn::OperandType::TENSOR_FLOAT32, android::nn::OperandType::FLOAT32}},
            {DataClass::FLOAT32,
             {android::nn::OperandType::FLOAT32, android::nn::OperandType::TENSOR_FLOAT32}},
            {DataClass::OTHER, {android::nn::OperandType::FLOAT32}},
            {DataClass::UNKNOWN, {}},
            {DataClass::FLOAT16,
             {android::nn::OperandType::FLOAT32, android::nn::OperandType::TENSOR_FLOAT16,
              android::nn::OperandType::TENSOR_INT32}},
            {DataClass::MIXED,
             {android::nn::OperandType::FLOAT32, android::nn::OperandType::TENSOR_FLOAT16,
              android::nn::OperandType::TENSOR_FLOAT32}},
            {DataClass::QUANT,
             {android::nn::OperandType::FLOAT32, android::nn::OperandType::TENSOR_QUANT8_ASYMM}},
    };

    for (auto& pair : data) {
        DataClass result = DataClass::UNKNOWN;
        for (auto v : pair.second) {
            result = android::nn::telemetry::evalDataClass(v, result);
        }
        ASSERT_EQ(result, pair.first);
    }
}

}  // namespace
