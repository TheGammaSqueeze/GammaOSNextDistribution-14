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

#define LOG_TAG "Telemetry"

#include "Telemetry.h"

#include <algorithm>
#include <functional>
#include <limits>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "Manager.h"
#include "NeuralNetworks.h"
#include "Tracing.h"

#if defined(__ANDROID__) && !defined(NN_COMPATIBILITY_LIBRARY_BUILD)
#include "TelemetryStatsd.h"
#endif  // defined(__ANDROID__) && !defined(NN_COMPATIBILITY_LIBRARY_BUILD)

namespace android::nn::telemetry {
namespace {

constexpr uint64_t kNoTimeReported = std::numeric_limits<uint64_t>::max();

std::function<void(const DiagnosticCompilationInfo*)> gCompilationCallback;
std::function<void(const DiagnosticExecutionInfo*)> gExecutionCallback;
std::atomic_bool gLoggingCallbacksSet = false;

// Convert list of Device object into a single string with all
// identifiers, sorted by name in form of "name1=version1,name2=version2,..."
std::string makeDeviceId(const std::vector<std::shared_ptr<Device>>& devices) {
    // Sort device identifiers in alphabetical order
    std::vector<std::string> names;
    names.reserve(devices.size());
    size_t totalSize = 0;
    for (size_t i = 0; i < devices.size(); ++i) {
        if (!names.empty()) {
            totalSize++;
        }
        names.push_back(devices[i]->getName() + "=" + devices[i]->getVersionString());
        totalSize += names.back().size();
    }
    sort(names.begin(), names.end());

    // Concatenate them
    std::string result;
    result.reserve(totalSize);
    for (auto& name : names) {
        if (!result.empty()) {
            result += ',';
        }
        result += name;
    }
    return result;
}

// Generate logging session identifier based on millisecond timestamp and pid
int32_t generateSessionId() {
    auto now = std::chrono::system_clock::now();
    auto duration = now.time_since_epoch();
    // Taking millisecond timestamp and pid modulo a large prime to make the id less identifiable,
    // but still unique within the device scope.
    auto timestamp = std::chrono::duration_cast<std::chrono::milliseconds>(duration).count();
    return (getpid() * 123 + timestamp) % 999983;
}

// Operand type to atom datatype
DataClass operandToDataClass(const OperandType& op) {
    switch (op) {
        case OperandType::TENSOR_FLOAT32:
            return DataClass::FLOAT32;
        case OperandType::TENSOR_FLOAT16:
            return DataClass::FLOAT16;
        case OperandType::TENSOR_QUANT8_ASYMM:
        case OperandType::TENSOR_QUANT16_SYMM:
        case OperandType::TENSOR_QUANT8_SYMM_PER_CHANNEL:
        case OperandType::TENSOR_QUANT16_ASYMM:
        case OperandType::TENSOR_QUANT8_SYMM:
        case OperandType::TENSOR_QUANT8_ASYMM_SIGNED:
            return DataClass::QUANT;
        default:
            // we ignore operand of other types
            return DataClass::OTHER;
    }
}

// Evaluate a coarse category of model inputs
DataClass evalInputDataClass(const ModelBuilder* m) {
    DataClass result = DataClass::UNKNOWN;
    for (size_t i = 0; i < m->inputCount(); i++) {
        result = evalDataClass(m->getInputOperand(i).type, result);
    }
    return result;
}

// Evaluate a coarse category of model outputs
DataClass evalOutputDataClass(const ModelBuilder* m) {
    DataClass result = DataClass::UNKNOWN;
    for (size_t i = 0; i < m->outputCount(); i++) {
        result = evalDataClass(m->getOutputOperand(i).type, result);
    }
    return result;
}

}  // namespace

// Infer a data class from an operand type. Call iteratievly on operands set, previousDataClass is
// result of evalDataClass evaluation on previous operands or DataClass::UNKNOWN value if called on
// first operand
DataClass evalDataClass(const OperandType& op, DataClass previousDataClass) {
    DataClass operandClass = operandToDataClass(op);
    if (operandClass == DataClass::OTHER) {
        if (previousDataClass == DataClass::UNKNOWN) {
            return operandClass;
        }
        return previousDataClass;
    }

    if (previousDataClass == DataClass::UNKNOWN || previousDataClass == DataClass::OTHER) {
        return operandClass;
    } else if (operandClass != previousDataClass) {
        return DataClass::MIXED;
    }
    return operandClass;
}

// Generate and store session identifier
int32_t getSessionId() {
    static int32_t ident = generateSessionId();
    return ident;
}

void onCompilationFinish(CompilationBuilder* c, int resultCode) {
    NNTRACE_RT(NNTRACE_PHASE_UNSPECIFIED, "onCompilationFinish");

    // Allow to emit even only if compilation was finished
    if (!c->isFinished()) {
        LOG(ERROR) << "telemetry::onCompilationFinish called on unfinished compilation";
        return;
    }

    const bool loggingCallbacksSet = gLoggingCallbacksSet;
    if (!loggingCallbacksSet && !DeviceManager::get()->isPlatformTelemetryEnabled()) {
        return;
    }

    const DiagnosticCompilationInfo info{
            .modelArchHash = c->getModel()->getModelArchHash(),
            .deviceId = makeDeviceId(c->getDevices()),
            .errorCode = resultCode,
            .inputDataClass = evalInputDataClass(c->getModel()),
            .outputDataClass = evalOutputDataClass(c->getModel()),
            .compilationTimeNanos = c->getTelemetryInfo()->compilationTimeNanos,
            .fallbackToCpuFromError = c->getTelemetryInfo()->fallbackToCpuFromError,
            .introspectionEnabled = c->createdWithExplicitDeviceList(),
            .cacheEnabled = c->isCacheInfoProvided(),
            .hasControlFlow = c->getModel()->hasControlFlow(),
            .hasDynamicTemporaries = c->hasDynamicTemporaries(),
    };

#if defined(__ANDROID__) && !defined(NN_COMPATIBILITY_LIBRARY_BUILD)
    if (DeviceManager::get()->isPlatformTelemetryEnabled()) {
        logCompilationToStatsd(&info);
    }
#endif  // defined(__ANDROID__) && !defined(NN_COMPATIBILITY_LIBRARY_BUILD)

    if (loggingCallbacksSet) {
        gCompilationCallback(&info);
    }
}

void onExecutionFinish(ExecutionBuilder* e, ExecutionMode executionMode, int resultCode) {
    NNTRACE_RT(NNTRACE_PHASE_UNSPECIFIED, "onExecutionFinish");

    // Allow to emit even only if execution was finished
    if (!e->completed()) {
        LOG(ERROR) << "telemetry::onExecutionFinish called on unfinished execution";
        return;
    }

    const bool loggingCallbacksSet = gLoggingCallbacksSet;
    if (!loggingCallbacksSet && !DeviceManager::get()->isPlatformTelemetryEnabled()) {
        return;
    }

    auto compilation = e->getCompilation();
    uint64_t duration_driver_ns = kNoTimeReported;
    uint64_t duration_hardware_ns = kNoTimeReported;
    uint64_t duration_runtime_ns = kNoTimeReported;

    if (e->measureTiming()) {
        e->getDuration(ANEURALNETWORKS_DURATION_ON_HARDWARE, &duration_hardware_ns);
        e->getDuration(ANEURALNETWORKS_DURATION_IN_DRIVER, &duration_driver_ns);
    }

    // Ignore runtime execution time if the call was async with dependencies, because waiting for
    // the result may have been much later than when the execution actually finished.
    if (executionMode != ExecutionMode::ASYNC_WITH_DEPS) {
        duration_runtime_ns = TimeNanoMeasurer::currentDuration(e->getComputeStartTimePoint());
    }

    const DiagnosticExecutionInfo info{
            .modelArchHash = e->getModel()->getModelArchHash(),
            .deviceId = makeDeviceId(compilation->getDevices()),
            .executionMode = executionMode,
            .inputDataClass = evalInputDataClass(e->getModel()),
            .outputDataClass = evalOutputDataClass(e->getModel()),
            .errorCode = resultCode,
            .durationRuntimeNanos = duration_runtime_ns,
            .durationDriverNanos = duration_driver_ns,
            .durationHardwareNanos = duration_hardware_ns,
            .introspectionEnabled = compilation->createdWithExplicitDeviceList(),
            .cacheEnabled = compilation->isCacheInfoProvided(),
            .hasControlFlow = compilation->getModel()->hasControlFlow(),
            .hasDynamicTemporaries = compilation->hasDynamicTemporaries(),
    };

#if defined(__ANDROID__) && !defined(NN_COMPATIBILITY_LIBRARY_BUILD)
    if (DeviceManager::get()->isPlatformTelemetryEnabled()) {
        logExecutionToStatsd(&info);
    }
#endif  // defined(__ANDROID__) && !defined(NN_COMPATIBILITY_LIBRARY_BUILD)

    if (loggingCallbacksSet) {
        gExecutionCallback(&info);
    }
}

void registerTelemetryCallbacks(std::function<void(const DiagnosticCompilationInfo*)> compilation,
                                std::function<void(const DiagnosticExecutionInfo*)> execution) {
    gCompilationCallback = std::move(compilation);
    gExecutionCallback = std::move(execution);
    gLoggingCallbacksSet = true;
}

void clearTelemetryCallbacks() {
    gLoggingCallbacksSet = false;
}

}  // namespace android::nn::telemetry
