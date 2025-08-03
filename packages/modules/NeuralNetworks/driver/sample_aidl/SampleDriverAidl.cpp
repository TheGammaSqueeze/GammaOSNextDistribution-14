/*
 * Copyright (C) 2017 The Android Open Source Project
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

#define LOG_TAG "SampleDriverAidl"

#include "SampleDriverAidl.h"

#include <android-base/logging.h>
#include <android-base/properties.h>
#include <android-base/scopeguard.h>
#include <android/binder_auto_utils.h>
#include <android/binder_interface_utils.h>
#include <nnapi/Result.h>
#include <nnapi/Types.h>
#include <nnapi/Validation.h>
#include <nnapi/hal/aidl/BufferTracker.h>
#include <nnapi/hal/aidl/Conversions.h>
#include <nnapi/hal/aidl/HalUtils.h>
#include <nnapi/hal/aidl/Utils.h>

#include <algorithm>
#include <chrono>
#include <map>
#include <memory>
#include <optional>
#include <set>
#include <string>
#include <thread>
#include <tuple>
#include <utility>
#include <variant>
#include <vector>

#include "CpuExecutor.h"
#include "SampleDriverAidlUtils.h"
#include "Tracing.h"
#include "Utils.h"

namespace android {
namespace nn {
namespace sample_driver_aidl {

namespace {

int64_t nanosecondsDuration(TimePoint end, TimePoint start) {
    return std::chrono::duration_cast<std::chrono::nanoseconds>(end - start).count();
};

constexpr aidl_hal::Timing kNoTiming = {.timeOnDeviceNs = -1, .timeInDriverNs = -1};

aidl_hal::ErrorStatus convertResultCodeToAidlErrorStatus(int resultCode) {
    const auto errorStatus = aidl_hal::utils::convert(convertResultCodeToErrorStatus(resultCode));
    CHECK(errorStatus.has_value()) << "Unhandled error (" << errorStatus.error().code
                                   << "): " << errorStatus.error().message;
    return errorStatus.value();
}

}  // namespace

ndk::ScopedAStatus SampleDriver::getVersionString(std::string* versionString) {
    NNTRACE_FULL(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_INITIALIZATION,
                 "SampleDriver::getVersionString");
    *versionString = "JUST_AN_EXAMPLE";
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus SampleDriver::getType(aidl_hal::DeviceType* deviceType) {
    NNTRACE_FULL(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_INITIALIZATION, "SampleDriver::getType");
    *deviceType = aidl_hal::DeviceType::CPU;
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus SampleDriver::getSupportedExtensions(
        std::vector<aidl_hal::Extension>* supportedExtensions) {
    NNTRACE_FULL(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_INITIALIZATION,
                 "SampleDriver::getSupportedExtensions");
    *supportedExtensions = {/* No extensions. */};
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus SampleDriver::getNumberOfCacheFilesNeeded(
        aidl_hal::NumberOfCacheFiles* numberOfCacheFiles) {
    NNTRACE_FULL(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_INITIALIZATION,
                 "SampleDriver::getNumberOfCacheFilesNeeded");
    // Set both numbers to be 0 for cache not supported.
    numberOfCacheFiles->numDataCache = 0;
    numberOfCacheFiles->numModelCache = 0;
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus SampleDriver::prepareModel(
        const aidl_hal::Model& model, aidl_hal::ExecutionPreference preference,
        aidl_hal::Priority priority, int64_t deadlineNs,
        const std::vector<ndk::ScopedFileDescriptor>& /*modelCache*/,
        const std::vector<ndk::ScopedFileDescriptor>& /*dataCache*/,
        const std::vector<uint8_t>& /*token*/,
        const std::shared_ptr<aidl_hal::IPreparedModelCallback>& callback) {
    NNTRACE_FULL(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_COMPILATION, "SampleDriver::prepareModel");
    auto copiedModel = aidl_hal::utils::clone(model);
    if (!copiedModel.has_value()) {
        return toAStatus(aidl_hal::ErrorStatus::GENERAL_FAILURE, copiedModel.error().message);
    }
    return prepareModelBase(std::move(copiedModel).value(), this, preference, priority, deadlineNs,
                            callback);
}
ndk::ScopedAStatus SampleDriver::prepareModelWithConfig(
        const aidl_hal::Model& model, const aidl_hal::PrepareModelConfig& config,
        const std::shared_ptr<aidl_hal::IPreparedModelCallback>& callback) {
    NNTRACE_FULL(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_COMPILATION,
                 "SampleDriver::prepareModelWithConfig");
    auto copiedModel = aidl_hal::utils::clone(model);
    if (!copiedModel.has_value()) {
        return toAStatus(aidl_hal::ErrorStatus::GENERAL_FAILURE, copiedModel.error().message);
    }
    return prepareModelBase(std::move(copiedModel).value(), this, config.preference,
                            config.priority, config.deadlineNs, callback);
}

ndk::ScopedAStatus SampleDriver::prepareModelFromCache(
        int64_t /*deadlineNs*/, const std::vector<ndk::ScopedFileDescriptor>& /*modelCache*/,
        const std::vector<ndk::ScopedFileDescriptor>& /*dataCache*/,
        const std::vector<uint8_t>& /*token*/,
        const std::shared_ptr<aidl_hal::IPreparedModelCallback>& callback) {
    NNTRACE_FULL(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_COMPILATION,
                 "SampleDriver::prepareModelFromCache");
    notify(callback, aidl_hal::ErrorStatus::GENERAL_FAILURE, nullptr);
    return toAStatus(aidl_hal::ErrorStatus::GENERAL_FAILURE,
                     "Caching is not supported in the sample driver.");
}

// Safely downcast an IPreparedModel object to SamplePreparedModel.
// This function will return nullptr if the IPreparedModel object is not originated from the sample
// driver process.
static const SamplePreparedModel* castToSamplePreparedModel(
        const std::shared_ptr<aidl_hal::IPreparedModel>& preparedModel) {
    if (preparedModel->isRemote()) {
        return nullptr;
    } else {
        // This static_cast is safe because SamplePreparedModel is the only class that implements
        // the IPreparedModel interface in the sample driver process.
        return static_cast<const SamplePreparedModel*>(preparedModel.get());
    }
}

ndk::ScopedAStatus SampleDriver::allocate(
        const aidl_hal::BufferDesc& desc,
        const std::vector<aidl_hal::IPreparedModelParcel>& halPreparedModels,
        const std::vector<aidl_hal::BufferRole>& inputRoles,
        const std::vector<aidl_hal::BufferRole>& outputRoles, aidl_hal::DeviceBuffer* buffer) {
    VLOG(DRIVER) << "SampleDriver::allocate";
    constexpr auto getModel = [](const std::shared_ptr<aidl_hal::IPreparedModel>& preparedModel)
            -> const aidl_hal::Model* {
        const auto* samplePreparedModel = castToSamplePreparedModel(preparedModel);
        if (samplePreparedModel == nullptr) {
            LOG(ERROR) << "SampleDriver::allocate -- unknown remote IPreparedModel.";
            return nullptr;
        }
        return samplePreparedModel->getModel();
    };

    std::vector<std::shared_ptr<aidl_hal::IPreparedModel>> preparedModels;
    preparedModels.reserve(halPreparedModels.size());
    for (const auto& halPreparedModelParcel : halPreparedModels) {
        preparedModels.push_back(halPreparedModelParcel.preparedModel);
    }
    std::set<AidlHalPreparedModelRole> roles;
    aidl_hal::Operand operand;
    if (!validateMemoryDesc(desc, preparedModels, inputRoles, outputRoles, getModel, &roles,
                            &operand)) {
        LOG(ERROR) << "SampleDriver::allocate -- validation failed.";
        return toAStatus(aidl_hal::ErrorStatus::INVALID_ARGUMENT,
                         "SampleDriver::allocate -- validation failed.");
    }

    if (isExtensionOperandType(operand.type)) {
        LOG(ERROR) << "SampleDriver::allocate -- does not support extension type.";
        return toAStatus(aidl_hal::ErrorStatus::GENERAL_FAILURE,
                         "SampleDriver::allocate -- does not support extension type.");
    }

    // TODO(xusongw): Support allocating buffers with unknown dimensions or rank.

    // An operand obtained from validateMemoryDesc is guaranteed to be representable in canonical
    // types.
    uint32_t size = nonExtensionOperandSizeOfData(convert(operand.type).value(),
                                                  toUnsigned(operand.dimensions).value());
    VLOG(DRIVER) << "SampleDriver::allocate -- type = " << toString(operand.type)
                 << ", dimensions = " << toString(operand.dimensions) << ", size = " << size;
    if (size == 0) {
        LOG(ERROR) << "SampleDriver::allocate -- does not support dynamic output shape.";
        return toAStatus(aidl_hal::ErrorStatus::GENERAL_FAILURE,
                         "SampleDriver::allocate -- does not support dynamic output shape.");
    }

    // An operand obtained from validateMemoryDesc is guaranteed to be representable in canonical
    // types, so it safe to do an unvalidated conversion here.
    auto bufferWrapper =
            AidlManagedBuffer::create(size, std::move(roles), unvalidatedConvert(operand).value());
    if (bufferWrapper == nullptr) {
        LOG(ERROR) << "SampleDriver::allocate -- not enough memory.";
        return toAStatus(aidl_hal::ErrorStatus::GENERAL_FAILURE,
                         "SampleDriver::allocate -- not enough memory.");
    }

    auto token = mBufferTracker->add(bufferWrapper);
    if (token == nullptr) {
        LOG(ERROR) << "SampleDriver::allocate -- AidlBufferTracker returned invalid token.";
        return toAStatus(aidl_hal::ErrorStatus::GENERAL_FAILURE,
                         "SampleDriver::allocate -- AidlBufferTracker returned invalid token.");
    }

    const uint32_t tokenValue = token->get();
    std::shared_ptr<SampleBuffer> sampleBuffer =
            ndk::SharedRefBase::make<SampleBuffer>(std::move(bufferWrapper), std::move(token));
    VLOG(DRIVER) << "SampleDriver::allocate -- successfully allocates the requested memory";
    buffer->buffer = std::move(sampleBuffer);
    buffer->token = tokenValue;
    return ndk::ScopedAStatus::ok();
}

static void copyRunTimePoolInfos(const RunTimePoolInfo& srcPool, const RunTimePoolInfo& dstPool) {
    CHECK(srcPool.getBuffer() != nullptr);
    CHECK(dstPool.getBuffer() != nullptr);
    CHECK(srcPool.getSize() == dstPool.getSize());
    std::copy(srcPool.getBuffer(), srcPool.getBuffer() + srcPool.getSize(), dstPool.getBuffer());
    dstPool.flush();
}

ndk::ScopedAStatus SampleBuffer::copyTo(const aidl_hal::Memory& dst) {
    const auto canonicalMemory = convert(dst);
    if (!canonicalMemory.has_value()) {
        return toAStatus(aidl_hal::ErrorStatus::INVALID_ARGUMENT, canonicalMemory.error().message);
    }
    const auto dstPool = RunTimePoolInfo::createFromMemory(canonicalMemory.value());
    if (!dstPool.has_value()) {
        LOG(ERROR) << "SampleBuffer::copyTo -- unable to map dst memory.";
        return toAStatus(aidl_hal::ErrorStatus::GENERAL_FAILURE,
                         "SampleBuffer::copyTo -- unable to map dst memory.");
    }
    const auto validationStatus =
            aidl_hal::utils::convert(kBuffer->validateCopyTo(dstPool->getSize())).value();
    if (validationStatus != aidl_hal::ErrorStatus::NONE) {
        return toAStatus(validationStatus);
    }
    const auto srcPool =
            RunTimePoolInfo::createFromExistingBuffer(kBuffer->getPointer(), kBuffer->getSize());
    copyRunTimePoolInfos(srcPool, dstPool.value());
    return ndk::ScopedAStatus::ok();
}

static aidl_hal::ErrorStatus copyFromInternal(
        const aidl_hal::Memory& src, const std::vector<uint32_t>& dimensions,
        const std::shared_ptr<AidlManagedBuffer>& bufferWrapper) {
    CHECK(bufferWrapper != nullptr);
    const auto canonicalMemory = convert(src);
    if (!canonicalMemory.has_value()) {
        return aidl_hal::ErrorStatus::INVALID_ARGUMENT;
    }
    const auto srcPool = RunTimePoolInfo::createFromMemory(canonicalMemory.value());
    if (!srcPool.has_value()) {
        LOG(ERROR) << "SampleBuffer::copyFrom -- unable to map src memory.";
        return aidl_hal::ErrorStatus::GENERAL_FAILURE;
    }
    const auto validationStatus = aidl_hal::utils::convert(bufferWrapper->validateCopyFrom(
                                                                   dimensions, srcPool->getSize()))
                                          .value();
    if (validationStatus != aidl_hal::ErrorStatus::NONE) {
        return validationStatus;
    }
    const auto dstPool = RunTimePoolInfo::createFromExistingBuffer(bufferWrapper->getPointer(),
                                                                   bufferWrapper->getSize());
    copyRunTimePoolInfos(srcPool.value(), dstPool);
    return aidl_hal::ErrorStatus::NONE;
}

ndk::ScopedAStatus SampleBuffer::copyFrom(const aidl_hal::Memory& src,
                                          const std::vector<int32_t>& dimensions) {
    const auto unsignedDimensions = toUnsigned(dimensions);
    if (!unsignedDimensions.has_value()) {
        return toAStatus(aidl_hal::ErrorStatus::INVALID_ARGUMENT,
                         unsignedDimensions.error().message);
    }
    const auto status = copyFromInternal(src, unsignedDimensions.value(), kBuffer);
    if (status != aidl_hal::ErrorStatus::NONE) {
        kBuffer->setInitialized(false);
        return toAStatus(status);
    }
    kBuffer->updateDimensions(unsignedDimensions.value());
    kBuffer->setInitialized(true);
    return ndk::ScopedAStatus::ok();
}

bool SamplePreparedModel::initialize() {
    const auto canonicalPools = convert(mModel.pools);
    if (!canonicalPools.has_value()) {
        return false;
    }
    return setRunTimePoolInfosFromCanonicalMemories(&mPoolInfos, canonicalPools.value());
}

static std::tuple<aidl_hal::ErrorStatus, std::vector<RunTimePoolInfo>,
                  std::vector<std::shared_ptr<AidlManagedBuffer>>>
createRunTimePoolInfos(const Request& request, const SampleDriver& driver,
                       const SamplePreparedModel* preparedModel) {
    std::vector<RunTimePoolInfo> requestPoolInfos;
    std::vector<std::shared_ptr<AidlManagedBuffer>> bufferWrappers;
    requestPoolInfos.reserve(request.pools.size());
    bufferWrappers.reserve(request.pools.size());
    for (uint32_t i = 0; i < request.pools.size(); i++) {
        const auto& pool = request.pools[i];
        if (const auto* memory = std::get_if<SharedMemory>(&pool)) {
            auto buffer = RunTimePoolInfo::createFromMemory(*memory);
            if (!buffer.has_value()) {
                LOG(ERROR) << "createRuntimeMemoriesFromMemoryPools -- could not map pools";
                return {aidl_hal::ErrorStatus::GENERAL_FAILURE, {}, {}};
            }
            requestPoolInfos.push_back(std::move(*buffer));
            bufferWrappers.push_back(nullptr);
        } else if (const auto* token = std::get_if<Request::MemoryDomainToken>(&pool)) {
            auto bufferWrapper = driver.getBufferTracker()->get(static_cast<uint32_t>(*token));
            if (bufferWrapper == nullptr) {
                return {aidl_hal::ErrorStatus::INVALID_ARGUMENT, {}, {}};
            }
            const auto validationStatus =
                    aidl_hal::utils::convert(
                            bufferWrapper->validateRequest(i, request, preparedModel))
                            .value();
            if (validationStatus != aidl_hal::ErrorStatus::NONE) {
                return {validationStatus, {}, {}};
            }
            requestPoolInfos.push_back(RunTimePoolInfo::createFromExistingBuffer(
                    bufferWrapper->getPointer(), bufferWrapper->getSize()));
            bufferWrappers.push_back(std::move(bufferWrapper));
        } else {
            // If the pool is not a Memory or a token, the input is invalid.
            return {aidl_hal::ErrorStatus::INVALID_ARGUMENT, {}, {}};
        }
    }
    return {aidl_hal::ErrorStatus::NONE, std::move(requestPoolInfos), std::move(bufferWrappers)};
}

static aidl_hal::ErrorStatus updateDeviceMemories(
        aidl_hal::ErrorStatus status, const Request& request,
        const std::vector<std::shared_ptr<AidlManagedBuffer>>& bufferWrappers,
        const std::vector<aidl_hal::OutputShape>& outputShapes) {
    if (status == aidl_hal::ErrorStatus::NONE) {
        for (uint32_t i = 0; i < request.outputs.size(); i++) {
            const uint32_t poolIndex = request.outputs[i].location.poolIndex;
            const auto& pool = request.pools[poolIndex];
            if (std::holds_alternative<Request::MemoryDomainToken>(pool)) {
                const auto unsignedDimensions = toUnsigned(outputShapes[i].dimensions).value();
                if (!bufferWrappers[poolIndex]->updateDimensions(unsignedDimensions)) {
                    return aidl_hal::ErrorStatus::GENERAL_FAILURE;
                }
            }
        }
        for (uint32_t i = 0; i < request.outputs.size(); i++) {
            const uint32_t poolIndex = request.outputs[i].location.poolIndex;
            const auto& pool = request.pools[poolIndex];
            if (std::holds_alternative<Request::MemoryDomainToken>(pool)) {
                bufferWrappers[poolIndex]->setInitialized(true);
            }
        }
    } else if (status == aidl_hal::ErrorStatus::OUTPUT_INSUFFICIENT_SIZE) {
        // If CpuExecutor reports OUTPUT_INSUFFCIENT_SIZE on a device memory, this is because the
        // dimensions of the device memory are incorrectly specified. The driver should return
        // GENERAL_FAILURE instead in this case.
        for (uint32_t i = 0; i < request.outputs.size(); i++) {
            const uint32_t poolIndex = request.outputs[i].location.poolIndex;
            const auto& pool = request.pools[poolIndex];
            if (std::holds_alternative<Request::MemoryDomainToken>(pool)) {
                if (!outputShapes[i].isSufficient) {
                    LOG(ERROR) << "Invalid dimensions for output " << i
                               << ": actual shape = " << toString(outputShapes[i].dimensions);
                    return aidl_hal::ErrorStatus::GENERAL_FAILURE;
                }
            }
        }
    }
    return aidl_hal::ErrorStatus::NONE;
}

ndk::ScopedAStatus SamplePreparedModel::executeSynchronously(
        const aidl_hal::Request& halRequest, bool measureTiming, int64_t halDeadlineNs,
        int64_t loopTimeoutDurationNs, aidl_hal::ExecutionResult* executionResult) {
    NNTRACE_FULL(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_EXECUTION,
                 "SampleDriver::executeSynchronously");
    VLOG(DRIVER) << "executeSynchronously(" << SHOW_IF_DEBUG(halRequest.toString()) << ")";

    TimePoint driverStart, driverEnd, deviceStart, deviceEnd;
    if (measureTiming) driverStart = Clock::now();

    const auto model = convert(mModel).value();

    auto maybeRequest = convert(halRequest);
    if (!maybeRequest.has_value()) {
        return toAStatus(aidl_hal::ErrorStatus::INVALID_ARGUMENT, maybeRequest.error().message);
    }
    const auto request = std::move(maybeRequest).value();

    const auto validationResult = validateRequestForModel(request, model);
    if (!validationResult.ok()) {
        return toAStatus(aidl_hal::ErrorStatus::INVALID_ARGUMENT, validationResult.error());
    }

    if (halDeadlineNs < -1) {
        return toAStatus(aidl_hal::ErrorStatus::INVALID_ARGUMENT,
                         "Invalid deadline: " + toString(halDeadlineNs));
    }
    if (loopTimeoutDurationNs < -1) {
        return toAStatus(aidl_hal::ErrorStatus::INVALID_ARGUMENT,
                         "Invalid loop timeout duration: " + toString(loopTimeoutDurationNs));
    }

    const auto deadline = makeDeadline(halDeadlineNs);
    if (hasDeadlinePassed(deadline)) {
        return toAStatus(aidl_hal::ErrorStatus::MISSED_DEADLINE_PERSISTENT);
    }

    NNTRACE_FULL_SWITCH(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_INPUTS_AND_OUTPUTS,
                        "SampleDriver::executeSynchronouslyBase");
    const auto [poolStatus, requestPoolInfos, bufferWrappers] =
            createRunTimePoolInfos(request, *mDriver, this);
    if (poolStatus != aidl_hal::ErrorStatus::NONE) {
        return toAStatus(poolStatus);
    }

    NNTRACE_FULL_SWITCH(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_EXECUTION,
                        "SampleDriver::executeSynchronouslyBase");
    CpuExecutor executor = mDriver->getExecutor();
    if (loopTimeoutDurationNs >= 0) {
        executor.setLoopTimeout(loopTimeoutDurationNs);
    }
    if (deadline.has_value()) {
        executor.setDeadline(*deadline);
    }
    if (measureTiming) deviceStart = Clock::now();
    int n = executor.run(model, request, mPoolInfos, requestPoolInfos);
    if (measureTiming) deviceEnd = Clock::now();
    VLOG(DRIVER) << "executor.run returned " << n;
    aidl_hal::ErrorStatus executionStatus = convertResultCodeToAidlErrorStatus(n);
    if (executionStatus != aidl_hal::ErrorStatus::NONE &&
        executionStatus != aidl_hal::ErrorStatus::OUTPUT_INSUFFICIENT_SIZE) {
        return toAStatus(executionStatus);
    }
    auto outputShapes = aidl_hal::utils::convert(executor.getOutputShapes()).value();

    // Update device memory metadata.
    const aidl_hal::ErrorStatus updateStatus =
            updateDeviceMemories(executionStatus, request, bufferWrappers, outputShapes);
    if (updateStatus != aidl_hal::ErrorStatus::NONE) {
        return toAStatus(updateStatus);
    }

    executionResult->outputSufficientSize =
            executionStatus != aidl_hal::ErrorStatus::OUTPUT_INSUFFICIENT_SIZE;
    executionResult->outputShapes = std::move(outputShapes);
    executionResult->timing = kNoTiming;
    if (measureTiming && executionStatus == aidl_hal::ErrorStatus::NONE) {
        driverEnd = Clock::now();
        aidl_hal::Timing timing = {.timeOnDeviceNs = nanosecondsDuration(deviceEnd, deviceStart),
                                   .timeInDriverNs = nanosecondsDuration(driverEnd, driverStart)};
        VLOG(DRIVER) << "executeSynchronously timing = " << timing.toString();

        executionResult->timing = timing;
    }
    return ndk::ScopedAStatus::ok();
}

// The sample driver will finish the execution and then return.
ndk::ScopedAStatus SamplePreparedModel::executeFenced(
        const aidl_hal::Request& halRequest, const std::vector<ndk::ScopedFileDescriptor>& waitFor,
        bool measureTiming, int64_t halDeadlineNs, int64_t loopTimeoutDurationNs,
        int64_t durationNs, aidl_hal::FencedExecutionResult* executionResult) {
    NNTRACE_FULL(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_EXECUTION,
                 "SamplePreparedModel::executeFenced");
    VLOG(DRIVER) << "executeFenced(" << SHOW_IF_DEBUG(halRequest.toString()) << ")";

    TimePoint driverStart, driverEnd, deviceStart, deviceEnd;
    if (measureTiming) driverStart = Clock::now();

    const auto model = convert(mModel).value();

    auto maybeRequest = convert(halRequest);
    if (!maybeRequest.has_value()) {
        return toAStatus(aidl_hal::ErrorStatus::INVALID_ARGUMENT, maybeRequest.error().message);
    }
    const auto request = std::move(maybeRequest).value();

    const auto validationResult =
            validateRequestForModel(request, model, /*allowUnspecifiedOutput=*/false);
    if (!validationResult.ok()) {
        return toAStatus(aidl_hal::ErrorStatus::INVALID_ARGUMENT, validationResult.error());
    }

    if (halDeadlineNs < -1) {
        return toAStatus(aidl_hal::ErrorStatus::INVALID_ARGUMENT,
                         "Invalid deadline: " + toString(halDeadlineNs));
    }
    if (loopTimeoutDurationNs < -1) {
        return toAStatus(aidl_hal::ErrorStatus::INVALID_ARGUMENT,
                         "Invalid loop timeout duration: " + toString(loopTimeoutDurationNs));
    }
    if (durationNs < -1) {
        return toAStatus(aidl_hal::ErrorStatus::INVALID_ARGUMENT,
                         "Invalid fenced execution duration: " + toString(durationNs));
    }

    const auto deadline = makeDeadline(halDeadlineNs);
    if (hasDeadlinePassed(deadline)) {
        return toAStatus(aidl_hal::ErrorStatus::MISSED_DEADLINE_PERSISTENT);
    }

    // Wait for the dependent events to signal
    for (const auto& fenceHandle : waitFor) {
        int syncFenceFd = fenceHandle.get();
        if (syncWait(syncFenceFd, -1) != FenceState::SIGNALED) {
            LOG(ERROR) << "syncWait failed";
            return toAStatus(aidl_hal::ErrorStatus::GENERAL_FAILURE, "syncWait failed");
        }
    }

    // Update deadline if the timeout duration is closer than the deadline.
    auto closestDeadline = deadline;
    if (durationNs >= 0) {
        const auto timeoutDurationDeadline = makeDeadline(durationNs);
        if (!closestDeadline.has_value() || *closestDeadline > timeoutDurationDeadline) {
            closestDeadline = timeoutDurationDeadline;
        }
    }

    TimePoint driverStartAfterFence;
    if (measureTiming) driverStartAfterFence = Clock::now();

    NNTRACE_FULL_SWITCH(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_INPUTS_AND_OUTPUTS,
                        "SamplePreparedModel::executeFenced");
    const auto [poolStatus, requestPoolInfos, bufferWrappers] =
            createRunTimePoolInfos(request, *mDriver, this);
    if (poolStatus != aidl_hal::ErrorStatus::NONE) {
        return toAStatus(poolStatus);
    }

    NNTRACE_FULL_SWITCH(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_EXECUTION,
                        "SamplePreparedModel::executeFenced");
    CpuExecutor executor = mDriver->getExecutor();
    if (loopTimeoutDurationNs >= 0) {
        executor.setLoopTimeout(loopTimeoutDurationNs);
    }
    if (closestDeadline.has_value()) {
        executor.setDeadline(*closestDeadline);
    }
    if (measureTiming) deviceStart = Clock::now();
    int n = executor.run(model, request, mPoolInfos, requestPoolInfos);
    if (measureTiming) deviceEnd = Clock::now();
    VLOG(DRIVER) << "executor.run returned " << n;
    aidl_hal::ErrorStatus executionStatus = convertResultCodeToAidlErrorStatus(n);
    if (executionStatus != aidl_hal::ErrorStatus::NONE) {
        return toAStatus(executionStatus);
    }

    // Set output memories to the initialized state.
    if (executionStatus == aidl_hal::ErrorStatus::NONE) {
        for (const auto& output : request.outputs) {
            const uint32_t poolIndex = output.location.poolIndex;
            const auto& pool = request.pools[poolIndex];
            if (std::holds_alternative<Request::MemoryDomainToken>(pool)) {
                bufferWrappers[poolIndex]->setInitialized(true);
            }
        }
    }

    aidl_hal::Timing timingSinceLaunch = kNoTiming;
    aidl_hal::Timing timingAfterFence = kNoTiming;
    if (measureTiming) {
        driverEnd = Clock::now();
        timingSinceLaunch = {.timeOnDeviceNs = nanosecondsDuration(deviceEnd, deviceStart),
                             .timeInDriverNs = nanosecondsDuration(driverEnd, driverStart)};
        timingAfterFence = {
                .timeOnDeviceNs = nanosecondsDuration(deviceEnd, deviceStart),
                .timeInDriverNs = nanosecondsDuration(driverEnd, driverStartAfterFence)};
        VLOG(DRIVER) << "executeFenced timingSinceLaunch = " << timingSinceLaunch.toString();
        VLOG(DRIVER) << "executeFenced timingAfterFence = " << timingAfterFence.toString();
    }

    executionResult->callback = ndk::SharedRefBase::make<SampleFencedExecutionCallback>(
            timingSinceLaunch, timingAfterFence, executionStatus);
    executionResult->syncFence = ndk::ScopedFileDescriptor();
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus SamplePreparedModel::executeSynchronouslyWithConfig(
        const aidl_hal::Request& request, const aidl_hal::ExecutionConfig& config,
        int64_t deadlineNs, aidl_hal::ExecutionResult* executionResult) {
    return executeSynchronously(request, config.measureTiming, deadlineNs,
                                config.loopTimeoutDurationNs, executionResult);
}
ndk::ScopedAStatus SamplePreparedModel::executeFencedWithConfig(
        const aidl_hal::Request& request, const std::vector<ndk::ScopedFileDescriptor>& waitFor,
        const aidl_hal::ExecutionConfig& config, int64_t deadlineNs, int64_t durationNs,
        aidl_hal::FencedExecutionResult* executionResult) {
    return executeFenced(request, waitFor, config.measureTiming, deadlineNs,
                         config.loopTimeoutDurationNs, durationNs, executionResult);
}

ndk::ScopedAStatus SamplePreparedModel::configureExecutionBurst(
        std::shared_ptr<aidl_hal::IBurst>* burst) {
    std::shared_ptr<SamplePreparedModel> self = this->template ref<SamplePreparedModel>();
    *burst = ndk::SharedRefBase::make<SampleBurst>(std::move(self));
    return ndk::ScopedAStatus::ok();
}

SampleBurst::SampleBurst(std::shared_ptr<SamplePreparedModel> preparedModel)
    : kPreparedModel(std::move(preparedModel)) {
    CHECK(kPreparedModel != nullptr);
}

ndk::ScopedAStatus SampleBurst::executeSynchronously(
        const aidl_hal::Request& request, const std::vector<int64_t>& memoryIdentifierTokens,
        bool measureTiming, int64_t deadlineNs, int64_t loopTimeoutDurationNs,
        aidl_hal::ExecutionResult* executionResult) {
    if (request.pools.size() != memoryIdentifierTokens.size()) {
        return toAStatus(aidl_hal::ErrorStatus::INVALID_ARGUMENT,
                         "request.pools.size() != memoryIdentifierTokens.size()");
    }
    if (!std::all_of(memoryIdentifierTokens.begin(), memoryIdentifierTokens.end(),
                     [](int64_t token) { return token >= -1; })) {
        return toAStatus(aidl_hal::ErrorStatus::INVALID_ARGUMENT, "Invalid memoryIdentifierTokens");
    }

    // Ensure at most one execution is in flight at a time.
    const bool executionAlreadyInFlight = mExecutionInFlight.test_and_set();
    if (executionAlreadyInFlight) {
        return toAStatus(aidl_hal::ErrorStatus::GENERAL_FAILURE,
                         "Burst object supports at most one execution at a time");
    }
    const auto guard = base::make_scope_guard([this] { mExecutionInFlight.clear(); });

    return kPreparedModel->executeSynchronously(request, measureTiming, deadlineNs,
                                                loopTimeoutDurationNs, executionResult);
}

ndk::ScopedAStatus SampleBurst::executeSynchronouslyWithConfig(
        const aidl_hal::Request& request, const std::vector<int64_t>& memoryIdentifierTokens,
        const aidl_hal::ExecutionConfig& config, int64_t deadlineNs,
        aidl_hal::ExecutionResult* executionResult) {
    return executeSynchronously(request, memoryIdentifierTokens, config.measureTiming, deadlineNs,
                                config.loopTimeoutDurationNs, executionResult);
}

ndk::ScopedAStatus SampleBurst::releaseMemoryResource(int64_t memoryIdentifierToken) {
    if (memoryIdentifierToken < -1) {
        return toAStatus(aidl_hal::ErrorStatus::INVALID_ARGUMENT, "Invalid memoryIdentifierToken");
    }
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus SamplePreparedModel::createReusableExecution(
        const aidl_hal::Request& halRequest, const aidl_hal::ExecutionConfig& config,
        std::shared_ptr<aidl_hal::IExecution>* execution) {
    NNTRACE_FULL(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_EXECUTION,
                 "SamplePreparedModel::createReusableExecution");
    VLOG(DRIVER) << "SamplePreparedModel::createReusableExecution";

    auto maybeClonedRequest = aidl_hal::utils::clone(halRequest);
    if (!maybeClonedRequest.ok()) {
        return toAStatus(aidl_hal::ErrorStatus::GENERAL_FAILURE,
                         maybeClonedRequest.error().message);
    }

    std::shared_ptr<SamplePreparedModel> self = this->template ref<SamplePreparedModel>();
    *execution = ndk::SharedRefBase::make<SampleExecution>(
            std::move(self), std::move(maybeClonedRequest).value(), config.measureTiming,
            config.loopTimeoutDurationNs);
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus SampleExecution::executeSynchronously(
        int64_t halDeadlineNs, aidl_hal::ExecutionResult* executionResult) {
    NNTRACE_FULL(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_EXECUTION,
                 "SampleExecution::executeSynchronously");
    VLOG(DRIVER) << "SampleExecution::executeSynchronously";

    // Ensure at most one computation is in flight at a time.
    const bool computationAlreadyInFlight = mComputationInFlight.test_and_set();
    if (computationAlreadyInFlight) {
        return toAStatus(aidl_hal::ErrorStatus::GENERAL_FAILURE,
                         "Execution object supports at most one computation at a time");
    }
    const auto guard = base::make_scope_guard([this] { mComputationInFlight.clear(); });

    return kPreparedModel->executeSynchronously(kHalRequest, kMeasureTiming, halDeadlineNs,
                                                kLoopTimeoutDurationNs, executionResult);
}

ndk::ScopedAStatus SampleExecution::executeFenced(
        const std::vector<ndk::ScopedFileDescriptor>& waitFor, int64_t deadlineNs,
        int64_t durationNs, aidl_hal::FencedExecutionResult* executionResult) {
    NNTRACE_FULL(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_EXECUTION, "SampleExecution::executeFenced");
    VLOG(DRIVER) << "SampleExecution::executeFenced";

    // Ensure at most one computation is in flight at a time.
    const bool computationAlreadyInFlight = mComputationInFlight.test_and_set();
    if (computationAlreadyInFlight) {
        return toAStatus(aidl_hal::ErrorStatus::GENERAL_FAILURE,
                         "Execution object supports at most one computation at a time");
    }
    const auto guard = base::make_scope_guard([this] { mComputationInFlight.clear(); });

    return kPreparedModel->executeFenced(kHalRequest, waitFor, kMeasureTiming, deadlineNs,
                                         kLoopTimeoutDurationNs, durationNs, executionResult);
}

}  // namespace sample_driver_aidl
}  // namespace nn
}  // namespace android
