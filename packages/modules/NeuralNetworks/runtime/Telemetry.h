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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_TELEMETRY_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_TELEMETRY_H

#include <functional>
#include <string>

#include "CompilationBuilder.h"
#include "ExecutionBuilder.h"

namespace android::nn::telemetry {

// Generate telemetry event on successful compilation
void onCompilationFinish(CompilationBuilder* c, int resultCode);

// Generate telemetry event on successful execution
void onExecutionFinish(ExecutionBuilder* e, ExecutionMode executionMode, int resultCode);

// Data class of inputs and outputs
enum class DataClass {
    UNKNOWN = 0,
    OTHER = 1,
    FLOAT32 = 2,
    FLOAT16 = 3,
    QUANT = 4,
    MIXED = 5,
};

// Infer data class of operand set
DataClass evalDataClass(const OperandType& op, DataClass previousDataClass);

// Get the ID that identifies a single session of client interacting with NNAPI runtime.
int32_t getSessionId();

struct DiagnosticCompilationInfo {
    // The hash of the model architecture (without weights).
    const uint8_t* modelArchHash;
    // The device IDs as a comma-concatenated string.
    const std::string deviceId;
    // The error code during compilation.
    int32_t errorCode;
    // Data class of the input to the model.
    DataClass inputDataClass;
    // Data class of the output from the model.
    DataClass outputDataClass;
    // Duration of the compilation in the runtime.
    // UINT64_MAX indicates no timing information is available.
    uint64_t compilationTimeNanos;
    // Did the compilation fallback to the CPU?
    bool fallbackToCpuFromError;
    // Is the client compiling with explicit set of devices?
    bool introspectionEnabled;
    // Is caching enabled?
    bool cacheEnabled;
    // Is control flow used?
    bool hasControlFlow;
    // Are dynamic tensors used?
    bool hasDynamicTemporaries;
};

struct DiagnosticExecutionInfo {
    // The hash of the model architecture (without weights).
    const uint8_t* modelArchHash;
    // The device IDs as a comma-concatenated string.
    const std::string deviceId;
    // Execution mode (e.g. Sync, Burst)
    ExecutionMode executionMode;
    // Data class of the input to the model.
    DataClass inputDataClass;
    // Data class of the output from the model.
    DataClass outputDataClass;
    // The error code during compilation.
    int32_t errorCode;
    // Duration of the execution in the runtime.
    // UINT64_MAX indicates no timing information is available.
    uint64_t durationRuntimeNanos;
    // Duration of the execution in the service driver.
    // UINT64_MAX indicates no timing information is available.
    uint64_t durationDriverNanos;
    // Duration of the execution running on the hardware.
    // UINT64_MAX indicates no timing information is available.
    uint64_t durationHardwareNanos;
    // Is the client compiling with explicit set of devices?
    bool introspectionEnabled;
    // Is caching enabled?
    bool cacheEnabled;
    // Is control flow used?
    bool hasControlFlow;
    // Are dynamic tensors used?
    bool hasDynamicTemporaries;
};

void registerTelemetryCallbacks(std::function<void(const DiagnosticCompilationInfo*)> compilation,
                                std::function<void(const DiagnosticExecutionInfo*)> execution);
void clearTelemetryCallbacks();

}  // namespace android::nn::telemetry

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_TELEMETRY_H
