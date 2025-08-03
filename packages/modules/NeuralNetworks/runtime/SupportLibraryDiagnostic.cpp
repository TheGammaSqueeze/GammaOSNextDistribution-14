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
 */

#include <android-base/logging.h>

#include <functional>
#include <utility>

#include "ExecutionBuilder.h"
#include "NeuralNetworksSupportLibraryImpl.h"
#include "Telemetry.h"

namespace {

using android::nn::ExecutionMode;
using android::nn::telemetry::DataClass;
using android::nn::telemetry::DiagnosticCompilationInfo;
using android::nn::telemetry::DiagnosticExecutionInfo;

const DiagnosticCompilationInfo* castTo(const ANeuralNetworksDiagnosticCompilationInfo* info) {
    CHECK(info != nullptr);
    return reinterpret_cast<const DiagnosticCompilationInfo*>(info);
}

const DiagnosticExecutionInfo* castTo(const ANeuralNetworksDiagnosticExecutionInfo* info) {
    CHECK(info != nullptr);
    return reinterpret_cast<const DiagnosticExecutionInfo*>(info);
}

const ANeuralNetworksDiagnosticCompilationInfo* castFrom(const DiagnosticCompilationInfo* info) {
    CHECK(info != nullptr);
    return reinterpret_cast<const ANeuralNetworksDiagnosticCompilationInfo*>(info);
}

const ANeuralNetworksDiagnosticExecutionInfo* castFrom(const DiagnosticExecutionInfo* info) {
    CHECK(info != nullptr);
    return reinterpret_cast<const ANeuralNetworksDiagnosticExecutionInfo*>(info);
}

ANeuralNetworksDiagnosticDataClass convert(DataClass dataClass) {
    switch (dataClass) {
        case DataClass::UNKNOWN:
            return ANNDIAG_DATA_CLASS_UNKNOWN;
        case DataClass::OTHER:
            return ANNDIAG_DATA_CLASS_OTHER;
        case DataClass::FLOAT32:
            return ANNDIAG_DATA_CLASS_FLOAT32;
        case DataClass::FLOAT16:
            return ANNDIAG_DATA_CLASS_FLOAT16;
        case DataClass::QUANT:
            return ANNDIAG_DATA_CLASS_QUANT;
        case DataClass::MIXED:
            return ANNDIAG_DATA_CLASS_MIXED;
    }
    LOG(FATAL) << "Unrecognized DataClass " << static_cast<int32_t>(dataClass);
    return ANNDIAG_DATA_CLASS_UNKNOWN;
}

ANeuralNetworksDiagnosticExecutionMode convert(ExecutionMode executionMode) {
    switch (executionMode) {
        case ExecutionMode::ASYNC:
            return ANNDIAG_EXECUTION_MODE_ASYNC;
        case ExecutionMode::SYNC:
            return ANNDIAG_EXECUTION_MODE_SYNC;
        case ExecutionMode::BURST:
            return ANNDIAG_EXECUTION_MODE_BURST;
        case ExecutionMode::ASYNC_WITH_DEPS:
            return ANNDIAG_EXECUTION_MODE_ASYNC_WITH_DEPS;
    }
    LOG(FATAL) << "Unrecognized ExecutionMode " << static_cast<int32_t>(executionMode);
    return ANNDIAG_EXECUTION_MODE_UNKNOWN;
}

}  // namespace

int32_t SL_ANeuralNetworksDiagnosticCompilationInfo_getSessionId(
        const ANeuralNetworksDiagnosticCompilationInfo* /*diagnosticCompilationInfo*/) {
    return android::nn::telemetry::getSessionId();
}

int64_t SL_ANeuralNetworksDiagnosticCompilationInfo_getNnApiVersion(
        const ANeuralNetworksDiagnosticCompilationInfo* /*diagnosticCompilationInfo*/) {
    return android::nn::DeviceManager::get()->getRuntimeFeatureLevel();
}

const uint8_t* SL_ANeuralNetworksDiagnosticCompilationInfo_getModelArchHash(
        const ANeuralNetworksDiagnosticCompilationInfo* diagnosticCompilationInfo) {
    return castTo(diagnosticCompilationInfo)->modelArchHash;
}

const char* SL_ANeuralNetworksDiagnosticCompilationInfo_getDeviceIds(
        const ANeuralNetworksDiagnosticCompilationInfo* diagnosticCompilationInfo) {
    return castTo(diagnosticCompilationInfo)->deviceId.c_str();
}

int32_t SL_ANeuralNetworksDiagnosticCompilationInfo_getErrorCode(
        const ANeuralNetworksDiagnosticCompilationInfo* diagnosticCompilationInfo) {
    return castTo(diagnosticCompilationInfo)->errorCode;
}

ANeuralNetworksDiagnosticDataClass SL_ANeuralNetworksDiagnosticCompilationInfo_getInputDataClass(
        const ANeuralNetworksDiagnosticCompilationInfo* diagnosticCompilationInfo) {
    return convert(castTo(diagnosticCompilationInfo)->inputDataClass);
}

ANeuralNetworksDiagnosticDataClass SL_ANeuralNetworksDiagnosticCompilationInfo_getOutputDataClass(
        const ANeuralNetworksDiagnosticCompilationInfo* diagnosticCompilationInfo) {
    return convert(castTo(diagnosticCompilationInfo)->outputDataClass);
}

uint64_t SL_ANeuralNetworksDiagnosticCompilationInfo_getCompilationTimeNanos(
        const ANeuralNetworksDiagnosticCompilationInfo* diagnosticCompilationInfo) {
    return castTo(diagnosticCompilationInfo)->compilationTimeNanos;
}

bool SL_ANeuralNetworksDiagnosticCompilationInfo_isCachingEnabled(
        const ANeuralNetworksDiagnosticCompilationInfo* diagnosticCompilationInfo) {
    return castTo(diagnosticCompilationInfo)->cacheEnabled;
}

bool SL_ANeuralNetworksDiagnosticCompilationInfo_isControlFlowUsed(
        const ANeuralNetworksDiagnosticCompilationInfo* diagnosticCompilationInfo) {
    return castTo(diagnosticCompilationInfo)->hasControlFlow;
}

bool SL_ANeuralNetworksDiagnosticCompilationInfo_areDynamicTensorsUsed(
        const ANeuralNetworksDiagnosticCompilationInfo* diagnosticCompilationInfo) {
    return castTo(diagnosticCompilationInfo)->hasDynamicTemporaries;
}

int32_t SL_ANeuralNetworksDiagnosticExecutionInfo_getSessionId(
        const ANeuralNetworksDiagnosticExecutionInfo* /*diagnosticExecutionInfo*/) {
    return android::nn::telemetry::getSessionId();
}

int64_t SL_ANeuralNetworksDiagnosticExecutionInfo_getNnApiVersion(
        const ANeuralNetworksDiagnosticExecutionInfo* /*diagnosticExecutionInfo*/) {
    return android::nn::DeviceManager::get()->getRuntimeFeatureLevel();
}

const uint8_t* SL_ANeuralNetworksDiagnosticExecutionInfo_getModelArchHash(
        const ANeuralNetworksDiagnosticExecutionInfo* diagnosticExecutionInfo) {
    return castTo(diagnosticExecutionInfo)->modelArchHash;
}

const char* SL_ANeuralNetworksDiagnosticExecutionInfo_getDeviceIds(
        const ANeuralNetworksDiagnosticExecutionInfo* diagnosticExecutionInfo) {
    return castTo(diagnosticExecutionInfo)->deviceId.c_str();
}

ANeuralNetworksDiagnosticExecutionMode SL_ANeuralNetworksDiagnosticExecutionInfo_getExecutionMode(
        const ANeuralNetworksDiagnosticExecutionInfo* diagnosticExecutionInfo) {
    return convert(castTo(diagnosticExecutionInfo)->executionMode);
}

ANeuralNetworksDiagnosticDataClass SL_ANeuralNetworksDiagnosticExecutionInfo_getInputDataClass(
        const ANeuralNetworksDiagnosticExecutionInfo* diagnosticExecutionInfo) {
    return convert(castTo(diagnosticExecutionInfo)->inputDataClass);
}

ANeuralNetworksDiagnosticDataClass SL_ANeuralNetworksDiagnosticExecutionInfo_getOutputDataClass(
        const ANeuralNetworksDiagnosticExecutionInfo* diagnosticExecutionInfo) {
    return convert(castTo(diagnosticExecutionInfo)->outputDataClass);
}

uint32_t SL_ANeuralNetworksDiagnosticExecutionInfo_getErrorCode(
        const ANeuralNetworksDiagnosticExecutionInfo* diagnosticExecutionInfo) {
    return castTo(diagnosticExecutionInfo)->errorCode;
}

uint64_t SL_ANeuralNetworksDiagnosticExecutionInfo_getRuntimeExecutionTimeNanos(
        const ANeuralNetworksDiagnosticExecutionInfo* diagnosticExecutionInfo) {
    return castTo(diagnosticExecutionInfo)->durationRuntimeNanos;
}

uint64_t SL_ANeuralNetworksDiagnosticExecutionInfo_getDriverExecutionTimeNanos(
        const ANeuralNetworksDiagnosticExecutionInfo* diagnosticExecutionInfo) {
    return castTo(diagnosticExecutionInfo)->durationDriverNanos;
}

uint64_t SL_ANeuralNetworksDiagnosticExecutionInfo_getHardwareExecutionTimeNanos(
        const ANeuralNetworksDiagnosticExecutionInfo* diagnosticExecutionInfo) {
    return castTo(diagnosticExecutionInfo)->durationHardwareNanos;
}

bool SL_ANeuralNetworksDiagnosticExecutionInfo_isCachingEnabled(
        const ANeuralNetworksDiagnosticExecutionInfo* diagnosticExecutionInfo) {
    return castTo(diagnosticExecutionInfo)->cacheEnabled;
}

bool SL_ANeuralNetworksDiagnosticExecutionInfo_isControlFlowUsed(
        const ANeuralNetworksDiagnosticExecutionInfo* diagnosticExecutionInfo) {
    return castTo(diagnosticExecutionInfo)->hasControlFlow;
}

bool SL_ANeuralNetworksDiagnosticExecutionInfo_areDynamicTensorsUsed(
        const ANeuralNetworksDiagnosticExecutionInfo* diagnosticExecutionInfo) {
    return castTo(diagnosticExecutionInfo)->hasDynamicTemporaries;
}

void SL_ANeuralNetworksDiagnostic_registerCallbacks(
        ANeuralNetworksDiagnosticCompilationFinishedCallback compilationCallback,
        ANeuralNetworksDiagnosticExecutionFinishedCallback executionCallback,
        void* callbackContext) {
    using android::nn::telemetry::registerTelemetryCallbacks;

    std::function<void(const DiagnosticCompilationInfo*)> compilation =
            [compilationCallback, callbackContext](const DiagnosticCompilationInfo* info) {
                compilationCallback(callbackContext, castFrom(info));
            };
    std::function<void(const DiagnosticExecutionInfo*)> execution =
            [executionCallback, callbackContext](const DiagnosticExecutionInfo* info) {
                executionCallback(callbackContext, castFrom(info));
            };

    registerTelemetryCallbacks(std::move(compilation), std::move(execution));
}
