/*
 * Copyright (C) 2021 The Android Open Source Project
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
 *
 */

#if __has_include("NeuralNetworks.h")
#include <NeuralNetworks.h>
#else // __has_include("NeuralNetworks.h")
#include <android/NeuralNetworks.h>
#endif // __has_include("NeuralNetworks.h")

#include <android-base/logging.h>
#include <jni.h>

#include <chrono>
#include <thread>

namespace {

using Matrix3x4 = float[3][4];
using InsufficientMatrixSize = float[2][3];

const int32_t kNoActivation = ANEURALNETWORKS_FUSED_NONE;
const uint32_t kDimensions[] = {3, 4};
const uint32_t kOperationInputs[] = {0, 1, 3};
const uint32_t kOperationOutputs[] = {2};
const uint32_t kModelInputs[] = {0, 1};
const uint32_t kModelOutputs[] = {2};
const uint32_t kDimensionsUnknown[] = {0, 0};

const ANeuralNetworksOperandType kMatrixType{
        .type = ANEURALNETWORKS_TENSOR_FLOAT32,
        .dimensionCount = std::size(kDimensions),
        .dimensions = std::data(kDimensions),
        .scale = 0.0f,
        .zeroPoint = 0,
};
const ANeuralNetworksOperandType kScalarType{
        .type = ANEURALNETWORKS_INT32,
        .dimensionCount = 0,
        .dimensions = nullptr,
        .scale = 0.0f,
        .zeroPoint = 0,
};
const ANeuralNetworksOperandType kMatrixUnknownDimensionsType{
        .type = ANEURALNETWORKS_TENSOR_FLOAT32,
        .dimensionCount = std::size(kDimensionsUnknown),
        .dimensions = std::data(kDimensionsUnknown),
        .scale = 0.0f,
        .zeroPoint = 0,
};

const Matrix3x4 kMatrix1 = {{1.f, 2.f, 3.f, 4.f}, {5.f, 6.f, 7.f, 8.f}, {9.f, 10.f, 11.f, 12.f}};
const Matrix3x4 kMatrix2 = {{100.f, 200.f, 300.f, 400.f},
                            {500.f, 600.f, 700.f, 800.f},
                            {900.f, 1000.f, 1100.f, 1200.f}};

// Create a model that can add two tensors using a one node graph.
void compilationSuccess() {
    // Create model
    ANeuralNetworksModel* model = nullptr;
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR, ANeuralNetworksModel_create(&model));
    CHECK(model != nullptr);
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR, ANeuralNetworksModel_addOperand(model, &kMatrixType));
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR, ANeuralNetworksModel_addOperand(model, &kMatrixType));
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR, ANeuralNetworksModel_addOperand(model, &kMatrixType));
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR, ANeuralNetworksModel_addOperand(model, &kScalarType));
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR,
             ANeuralNetworksModel_setOperandValue(model, 3, &kNoActivation, sizeof(kNoActivation)));
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR,
             ANeuralNetworksModel_addOperation(model, ANEURALNETWORKS_ADD,
                                               std::size(kOperationInputs),
                                               std::data(kOperationInputs),
                                               std::size(kOperationOutputs),
                                               std::data(kOperationOutputs)));
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR,
             ANeuralNetworksModel_identifyInputsAndOutputs(model, std::size(kModelInputs),
                                                           std::data(kModelInputs),
                                                           std::size(kModelOutputs),
                                                           std::data(kModelOutputs)));
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR, ANeuralNetworksModel_finish(model));

    // Create compilation
    ANeuralNetworksCompilation* compilation = nullptr;
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR, ANeuralNetworksCompilation_create(model, &compilation));
    CHECK(compilation != nullptr);
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR, ANeuralNetworksCompilation_finish(compilation));

    // Cleanup
    ANeuralNetworksCompilation_free(compilation);
    ANeuralNetworksModel_free(model);
}

// Create a model that can add two tensors using a one node graph.
void compilationFailure() {
    // Create model
    ANeuralNetworksModel* model = nullptr;
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR, ANeuralNetworksModel_create(&model));
    CHECK(model != nullptr);
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR, ANeuralNetworksModel_addOperand(model, &kMatrixType));
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR, ANeuralNetworksModel_addOperand(model, &kMatrixType));
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR, ANeuralNetworksModel_addOperand(model, &kMatrixType));
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR, ANeuralNetworksModel_addOperand(model, &kScalarType));
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR,
             ANeuralNetworksModel_setOperandValue(model, 3, &kNoActivation, sizeof(kNoActivation)));
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR,
             ANeuralNetworksModel_addOperation(model, ANEURALNETWORKS_ADD,
                                               std::size(kOperationInputs),
                                               std::data(kOperationInputs),
                                               std::size(kOperationOutputs),
                                               std::data(kOperationOutputs)));
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR,
             ANeuralNetworksModel_identifyInputsAndOutputs(model, std::size(kModelInputs),
                                                           std::data(kModelInputs),
                                                           std::size(kModelOutputs),
                                                           std::data(kModelOutputs)));
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR, ANeuralNetworksModel_finish(model));

    // Create compilation
    ANeuralNetworksCompilation* compilation = nullptr;
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR, ANeuralNetworksCompilation_create(model, &compilation));
    CHECK(compilation != nullptr);
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR, ANeuralNetworksCompilation_finish(compilation));
    // Second call to CompilationFinish fails.
    CHECK_EQ(ANEURALNETWORKS_BAD_STATE, ANeuralNetworksCompilation_finish(compilation));

    // Cleanup
    ANeuralNetworksModel_free(model);
}

// Create a model that can add two tensors using a one node graph.
void executionSuccess() {
    // Create model
    ANeuralNetworksModel* model = nullptr;
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR, ANeuralNetworksModel_create(&model));
    CHECK(model != nullptr);
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR, ANeuralNetworksModel_addOperand(model, &kMatrixType));
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR, ANeuralNetworksModel_addOperand(model, &kMatrixType));
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR, ANeuralNetworksModel_addOperand(model, &kMatrixType));
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR, ANeuralNetworksModel_addOperand(model, &kScalarType));
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR,
             ANeuralNetworksModel_setOperandValue(model, 3, &kNoActivation, sizeof(kNoActivation)));
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR,
             ANeuralNetworksModel_addOperation(model, ANEURALNETWORKS_ADD,
                                               std::size(kOperationInputs),
                                               std::data(kOperationInputs),
                                               std::size(kOperationOutputs),
                                               std::data(kOperationOutputs)));
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR,
             ANeuralNetworksModel_identifyInputsAndOutputs(model, std::size(kModelInputs),
                                                           std::data(kModelInputs),
                                                           std::size(kModelOutputs),
                                                           std::data(kModelOutputs)));
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR, ANeuralNetworksModel_finish(model));

    // Create compilation
    ANeuralNetworksCompilation* compilation = nullptr;
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR, ANeuralNetworksCompilation_create(model, &compilation));
    CHECK(compilation != nullptr);
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR, ANeuralNetworksCompilation_finish(compilation));

    // Create execution
    Matrix3x4 output;
    ANeuralNetworksExecution* execution = nullptr;
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR, ANeuralNetworksExecution_create(compilation, &execution));
    CHECK(execution != nullptr);
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR,
             ANeuralNetworksExecution_setInput(execution, 0, nullptr, &kMatrix1, sizeof(kMatrix1)));
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR,
             ANeuralNetworksExecution_setInput(execution, 1, nullptr, &kMatrix2, sizeof(kMatrix2)));
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR,
             ANeuralNetworksExecution_setOutput(execution, 0, nullptr, &output, sizeof(output)));
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR, ANeuralNetworksExecution_compute(execution));

    // Cleanup
    ANeuralNetworksExecution_free(execution);
    ANeuralNetworksCompilation_free(compilation);
    ANeuralNetworksModel_free(model);
}

// Create a model that can add two tensors using a one node graph.
void executionFailure() {
    // Create model
    ANeuralNetworksModel* model = nullptr;
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR, ANeuralNetworksModel_create(&model));
    CHECK(model != nullptr);
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR, ANeuralNetworksModel_addOperand(model, &kMatrixType));
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR, ANeuralNetworksModel_addOperand(model, &kMatrixType));
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR,
             ANeuralNetworksModel_addOperand(model, &kMatrixUnknownDimensionsType));
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR, ANeuralNetworksModel_addOperand(model, &kScalarType));
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR,
             ANeuralNetworksModel_setOperandValue(model, 3, &kNoActivation, sizeof(kNoActivation)));
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR,
             ANeuralNetworksModel_addOperation(model, ANEURALNETWORKS_ADD,
                                               std::size(kOperationInputs),
                                               std::data(kOperationInputs),
                                               std::size(kOperationOutputs),
                                               std::data(kOperationOutputs)));
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR,
             ANeuralNetworksModel_identifyInputsAndOutputs(model, std::size(kModelInputs),
                                                           std::data(kModelInputs),
                                                           std::size(kModelOutputs),
                                                           std::data(kModelOutputs)));
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR, ANeuralNetworksModel_finish(model));

    // Create compilation
    ANeuralNetworksCompilation* compilation = nullptr;
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR, ANeuralNetworksCompilation_create(model, &compilation));
    CHECK(compilation != nullptr);
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR, ANeuralNetworksCompilation_finish(compilation));

    // Create execution
    InsufficientMatrixSize output;
    ANeuralNetworksExecution* execution = nullptr;
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR, ANeuralNetworksExecution_create(compilation, &execution));
    CHECK(execution != nullptr);
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR,
             ANeuralNetworksExecution_setInput(execution, 0, nullptr, &kMatrix1, sizeof(kMatrix1)));
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR,
             ANeuralNetworksExecution_setInput(execution, 1, nullptr, &kMatrix2, sizeof(kMatrix2)));
    // This will cause ANeuralNetworksExecution_compute to fail because the provided output buffer
    // is too small.
    CHECK_EQ(ANEURALNETWORKS_NO_ERROR,
             ANeuralNetworksExecution_setOutput(execution, 0, nullptr, &output, sizeof(output)));
    CHECK_EQ(ANEURALNETWORKS_OUTPUT_INSUFFICIENT_SIZE, ANeuralNetworksExecution_compute(execution));

    // Cleanup
    ANeuralNetworksCompilation_free(compilation);
    ANeuralNetworksModel_free(model);
}

} // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_android_nn_stats_app_NnapiDeviceActivity_trigger_1libneuralnetworks_1atoms(
        JNIEnv*, jobject /*this*/) {
    compilationSuccess();
    compilationFailure();
    executionSuccess();
    executionFailure();

    // Sleep for a short period of time to make sure all the atoms have been sent.
    std::this_thread::sleep_for(std::chrono::seconds(1));
}
