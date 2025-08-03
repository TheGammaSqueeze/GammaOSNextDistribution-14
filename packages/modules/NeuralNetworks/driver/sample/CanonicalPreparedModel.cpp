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

#include "CanonicalPreparedModel.h"

#include <DefaultExecution.h>
#include <Tracing.h>
#include <nnapi/IPreparedModel.h>
#include <nnapi/Result.h>
#include <nnapi/TypeUtils.h>
#include <nnapi/Types.h>
#include <nnapi/Validation.h>

#include <memory>
#include <tuple>
#include <utility>
#include <vector>

#include "CanonicalBurst.h"
#include "CanonicalDevice.h"

namespace android::nn::sample {
namespace {

GeneralResult<std::pair<std::vector<RunTimePoolInfo>, std::vector<std::shared_ptr<ManagedBuffer>>>>
createRunTimePoolInfos(const Request& request, const BufferTracker& bufferTracker,
                       const PreparedModel& preparedModel) {
    std::vector<RunTimePoolInfo> requestPoolInfos;
    std::vector<std::shared_ptr<ManagedBuffer>> bufferWrappers;
    requestPoolInfos.reserve(request.pools.size());
    bufferWrappers.reserve(request.pools.size());
    for (uint32_t i = 0; i < request.pools.size(); ++i) {
        auto& pool = request.pools[i];
        if (const auto* maybeMemory = std::get_if<SharedMemory>(&pool)) {
            auto buffer = RunTimePoolInfo::createFromMemory(*maybeMemory);
            if (!buffer.has_value()) {
                return NN_ERROR(ErrorStatus::GENERAL_FAILURE)
                       << "createRuntimeMemoriesFromMemoryPools -- could not map pools";
            }
            requestPoolInfos.push_back(std::move(*buffer));
            bufferWrappers.push_back(nullptr);
        } else if (const auto* maybeToken = std::get_if<Request::MemoryDomainToken>(&pool)) {
            auto bufferWrapper = bufferTracker.get(*maybeToken);
            if (bufferWrapper == nullptr) {
                return NN_ERROR(ErrorStatus::INVALID_ARGUMENT);
            }
            const auto validationStatus =
                    bufferWrapper->validateRequest(i, request, &preparedModel);
            if (validationStatus != ErrorStatus::NONE) {
                return NN_ERROR(validationStatus);
            }
            requestPoolInfos.push_back(bufferWrapper->createRunTimePoolInfo());
            bufferWrappers.push_back(std::move(bufferWrapper));
        }
    }
    return std::make_pair(std::move(requestPoolInfos), std::move(bufferWrappers));
}

ErrorStatus updateDeviceMemories(ErrorStatus status, const Request& request,
                                 const std::vector<std::shared_ptr<ManagedBuffer>>& bufferWrappers,
                                 const std::vector<OutputShape>& outputShapes) {
    if (status == ErrorStatus::NONE) {
        for (uint32_t i = 0; i < request.outputs.size(); i++) {
            if (request.outputs[i].lifetime != Request::Argument::LifeTime::POOL) continue;
            const uint32_t poolIndex = request.outputs[i].location.poolIndex;
            const auto& pool = request.pools[poolIndex];
            if (std::holds_alternative<Request::MemoryDomainToken>(pool)) {
                if (!bufferWrappers[poolIndex]->updateDimensions(outputShapes[i].dimensions)) {
                    return ErrorStatus::GENERAL_FAILURE;
                }
            }
        }
        for (uint32_t i = 0; i < request.outputs.size(); i++) {
            if (request.outputs[i].lifetime != Request::Argument::LifeTime::POOL) continue;
            const uint32_t poolIndex = request.outputs[i].location.poolIndex;
            const auto& pool = request.pools[poolIndex];
            if (std::holds_alternative<Request::MemoryDomainToken>(pool)) {
                bufferWrappers[poolIndex]->setInitialized(true);
            }
        }
    } else if (status == ErrorStatus::OUTPUT_INSUFFICIENT_SIZE) {
        // If CpuExecutor reports OUTPUT_INSUFFCIENT_SIZE on a device memory, this is because the
        // dimensions of the device memory are incorrectly specified. The driver should return
        // GENERAL_FAILURE instead in this case.
        for (uint32_t i = 0; i < request.outputs.size(); i++) {
            if (request.outputs[i].lifetime != Request::Argument::LifeTime::POOL) continue;
            const uint32_t poolIndex = request.outputs[i].location.poolIndex;
            const auto& pool = request.pools[poolIndex];
            if (std::holds_alternative<Request::MemoryDomainToken>(pool)) {
                if (!outputShapes[i].isSufficient) {
                    LOG(ERROR) << "Invalid dimensions for output " << i
                               << ": actual shape = " << toString(outputShapes[i].dimensions);
                    return ErrorStatus::GENERAL_FAILURE;
                }
            }
        }
    }
    return ErrorStatus::NONE;
}

}  // namespace

PreparedModel::PreparedModel(Model model, ExecutionPreference preference, Priority priority,
                             const IOperationResolver* operationResolver,
                             std::shared_ptr<BufferTracker> bufferTracker,
                             std::vector<RunTimePoolInfo> poolInfos)
    : kModel(std::move(model)),
      kExecutionPreference(preference),
      kExecutionPriority(priority),
      kOperationResolver(*operationResolver),
      kBufferTracker(std::move(bufferTracker)),
      kPoolInfos(std::move(poolInfos)) {
    CHECK(operationResolver != nullptr);
    CHECK(kBufferTracker != nullptr);
}

ExecutionResult<std::pair<std::vector<OutputShape>, Timing>> PreparedModel::execute(
        const Request& request, MeasureTiming measure, const OptionalTimePoint& deadline,
        const OptionalDuration& loopTimeoutDuration, const std::vector<TokenValuePair>& /*hints*/,
        const std::vector<ExtensionNameAndPrefix>& /*extensionNameToPrefix*/) const {
    NNTRACE_FULL(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_EXECUTION, "sample::PreparedModel::execute");
    VLOG(DRIVER) << "sample::PreparedModel::execute(" << SHOW_IF_DEBUG(request) << ")";

    TimePoint driverStart, driverEnd, deviceStart, deviceEnd;
    if (measure == MeasureTiming::YES) driverStart = Clock::now();

    if (const auto result = validateRequestForModel(request, kModel); !result.ok()) {
        return NN_ERROR(ErrorStatus::INVALID_ARGUMENT) << result.error();
    }
    if (hasDeadlinePassed(deadline)) {
        return NN_ERROR(ErrorStatus::MISSED_DEADLINE_PERSISTENT);
    }

    NNTRACE_FULL_SWITCH(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_INPUTS_AND_OUTPUTS,
                        "sample::Device::execute");
    const auto [requestPoolInfos, bufferWrappers] =
            NN_TRY(createRunTimePoolInfos(request, *kBufferTracker, *this));

    NNTRACE_FULL_SWITCH(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_EXECUTION, "sample::Device::execute");
    auto executor = CpuExecutor(&kOperationResolver);
    if (loopTimeoutDuration.has_value()) {
        executor.setLoopTimeout(loopTimeoutDuration->count());
    }
    if (deadline.has_value()) {
        executor.setDeadline(*deadline);
    }

    // Perform execution.
    if (measure == MeasureTiming::YES) deviceStart = Clock::now();
    int n = executor.run(kModel, request, kPoolInfos, requestPoolInfos);
    if (measure == MeasureTiming::YES) deviceEnd = Clock::now();
    VLOG(DRIVER) << "executor.run returned " << n;
    ErrorStatus executionStatus = convertResultCodeToErrorStatus(n);
    const auto& outputShapes = executor.getOutputShapes();

    // Update device memory metadata.
    const ErrorStatus updateStatus =
            updateDeviceMemories(executionStatus, request, bufferWrappers, outputShapes);
    if (updateStatus != ErrorStatus::NONE) {
        return NN_ERROR(updateStatus);
    }
    if (executionStatus != ErrorStatus::NONE) {
        return NN_ERROR(executionStatus, outputShapes);
    }

    Timing timing = {};
    if (measure == MeasureTiming::YES) {
        driverEnd = Clock::now();
        timing = {.timeOnDevice = deviceEnd - deviceStart, .timeInDriver = driverEnd - driverStart};
        VLOG(DRIVER) << "sample::PreparedModel::execute timing = " << timing;
    }

    return std::make_pair(outputShapes, timing);
}

GeneralResult<std::pair<SyncFence, ExecuteFencedInfoCallback>> PreparedModel::executeFenced(
        const Request& request, const std::vector<SyncFence>& waitFor, MeasureTiming measure,
        const OptionalTimePoint& deadline, const OptionalDuration& loopTimeoutDuration,
        const OptionalDuration& timeoutDurationAfterFence,
        const std::vector<TokenValuePair>& /*hints*/,
        const std::vector<ExtensionNameAndPrefix>& /*extensionNameToPrefix*/) const {
    NNTRACE_FULL(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_EXECUTION,
                 "sample::PreparedModel::executeFenced");
    VLOG(DRIVER) << "executeFenced(" << SHOW_IF_DEBUG(request) << ")";

    TimePoint driverStart, driverEnd, deviceStart, deviceEnd;
    if (measure == MeasureTiming::YES) driverStart = Clock::now();

    if (const auto result =
                validateRequestForModel(request, kModel, /*allowUnspecifiedOutput=*/false);
        !result.ok()) {
        return NN_ERROR(ErrorStatus::INVALID_ARGUMENT) << result.error();
    }
    if (std::any_of(waitFor.begin(), waitFor.end(),
                    [](const SyncFence& syncFence) { return !syncFence.getSharedHandle(); })) {
        return NN_ERROR(ErrorStatus::INVALID_ARGUMENT)
               << "sample::PreparedModel::executeFenced passed an empty SyncFence";
    }
    if (hasDeadlinePassed(deadline)) {
        return NN_ERROR(ErrorStatus::MISSED_DEADLINE_PERSISTENT);
    }

    // Wait for the dependent events to signal
    for (const auto& syncFence : waitFor) {
        if (syncFence.syncWait({}) != SyncFence::FenceState::SIGNALED) {
            return NN_ERROR(ErrorStatus::GENERAL_FAILURE) << "syncWait failed";
        }
    }

    // Update deadline if the timeout duration is closer than the deadline.
    auto closestDeadline = deadline;
    if (timeoutDurationAfterFence.has_value()) {
        const auto timeoutDurationDeadline = makeDeadline(*timeoutDurationAfterFence);
        if (!closestDeadline.has_value() || *closestDeadline > timeoutDurationDeadline) {
            closestDeadline = timeoutDurationDeadline;
        }
    }

    TimePoint driverStartAfterFence;
    if (measure == MeasureTiming::YES) driverStartAfterFence = Clock::now();

    NNTRACE_FULL_SWITCH(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_INPUTS_AND_OUTPUTS,
                        "sample::PreparedModel::executeFenced");
    const auto [requestPoolInfos, bufferWrappers] =
            NN_TRY(createRunTimePoolInfos(request, *kBufferTracker, *this));

    NNTRACE_FULL_SWITCH(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_EXECUTION,
                        "sample::PreparedModel::executeFenced");
    auto executor = CpuExecutor(&kOperationResolver);
    if (loopTimeoutDuration.has_value()) {
        executor.setLoopTimeout(loopTimeoutDuration->count());
    }
    if (closestDeadline.has_value()) {
        executor.setDeadline(*closestDeadline);
    }
    if (measure == MeasureTiming::YES) deviceStart = Clock::now();
    int n = executor.run(kModel, request, kPoolInfos, requestPoolInfos);
    if (measure == MeasureTiming::YES) deviceEnd = Clock::now();
    VLOG(DRIVER) << "executor.run returned " << n;
    ErrorStatus executionStatus = convertResultCodeToErrorStatus(n);
    if (executionStatus != ErrorStatus::NONE) {
        return NN_ERROR(executionStatus);
    }

    // Set output memories to the initialized state.
    for (const auto& output : request.outputs) {
        if (output.lifetime != Request::Argument::LifeTime::POOL) continue;
        const uint32_t poolIndex = output.location.poolIndex;
        const auto& pool = request.pools[poolIndex];
        if (std::holds_alternative<Request::MemoryDomainToken>(pool)) {
            bufferWrappers[poolIndex]->setInitialized(true);
        }
    }

    Timing timingSinceLaunch = {};
    Timing timingAfterFence = {};
    if (measure == MeasureTiming::YES) {
        driverEnd = Clock::now();
        timingSinceLaunch = {.timeOnDevice = deviceEnd - deviceStart,
                             .timeInDriver = driverEnd - driverStart};
        timingAfterFence = {.timeOnDevice = deviceEnd - deviceStart,
                            .timeInDriver = driverEnd - driverStartAfterFence};
        VLOG(DRIVER) << "executeFenced timingSinceLaunch = " << timingSinceLaunch;
        VLOG(DRIVER) << "executeFenced timingAfterFence = " << timingAfterFence;
    }

    ExecuteFencedInfoCallback fencedExecutionCallback = [timingSinceLaunch, timingAfterFence]() {
        return std::make_pair(timingSinceLaunch, timingAfterFence);
    };
    return std::make_pair(SyncFence::createAsSignaled(), std::move(fencedExecutionCallback));
}

GeneralResult<SharedExecution> PreparedModel::createReusableExecution(
        const Request& request, MeasureTiming measure, const OptionalDuration& loopTimeoutDuration,
        const std::vector<TokenValuePair>& /*hints*/,
        const std::vector<ExtensionNameAndPrefix>& /*extensionNameToPrefix*/) const {
    NNTRACE_FULL(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_EXECUTION,
                 "sample::PreparedModel::createReusableExecution");
    return std::make_shared<DefaultExecution>(shared_from_this(), request, measure,
                                              loopTimeoutDuration);
}

GeneralResult<SharedBurst> PreparedModel::configureExecutionBurst() const {
    return std::make_shared<const Burst>(shared_from_this());
}

std::any PreparedModel::getUnderlyingResource() const {
    return &kModel;
}

}  // namespace android::nn::sample
