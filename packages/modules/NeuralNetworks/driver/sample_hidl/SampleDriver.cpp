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

#define LOG_TAG "SampleDriver"

#include "SampleDriver.h"

#include <CpuExecutor.h>
#include <ExecutionBurstServer.h>
#include <HalBufferTracker.h>
#include <HalInterfaces.h>
#include <Tracing.h>
#include <ValidateHal.h>
#include <android-base/logging.h>
#include <android-base/properties.h>
#include <hidl/LegacySupport.h>
#include <nnapi/Types.h>
#include <nnapi/hal/1.3/Conversions.h>

#include <algorithm>
#include <chrono>
#include <map>
#include <memory>
#include <optional>
#include <set>
#include <thread>
#include <tuple>
#include <utility>
#include <vector>

#include "SampleDriverUtils.h"

namespace android {
namespace nn {
namespace sample_driver {

namespace {

uint64_t microsecondsDuration(TimePoint end, TimePoint start) {
    using Microseconds = std::chrono::duration<uint64_t, std::micro>;
    return std::chrono::duration_cast<Microseconds>(end - start).count();
};

}  // namespace

static const V1_2::Timing kNoTiming = {.timeOnDevice = UINT64_MAX, .timeInDriver = UINT64_MAX};

hardware::Return<void> SampleDriver::getCapabilities(getCapabilities_cb cb) {
    NNTRACE_FULL(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_INITIALIZATION,
                 "SampleDriver::getCapabilities");
    return getCapabilities_1_3(
            [&](V1_3::ErrorStatus error, const V1_3::Capabilities& capabilities) {
                // TODO(dgross): Do we need to check compliantWithV1_0(capabilities)?
                cb(convertToV1_0(error), convertToV1_0(capabilities));
            });
}

hardware::Return<void> SampleDriver::getCapabilities_1_1(getCapabilities_1_1_cb cb) {
    NNTRACE_FULL(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_INITIALIZATION,
                 "SampleDriver::getCapabilities_1_1");
    return getCapabilities_1_3(
            [&](V1_3::ErrorStatus error, const V1_3::Capabilities& capabilities) {
                // TODO(dgross): Do we need to check compliantWithV1_1(capabilities)?
                cb(convertToV1_0(error), convertToV1_1(capabilities));
            });
}

hardware::Return<void> SampleDriver::getCapabilities_1_2(getCapabilities_1_2_cb cb) {
    NNTRACE_FULL(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_INITIALIZATION,
                 "SampleDriver::getCapabilities_1_2");
    return getCapabilities_1_3(
            [&](V1_3::ErrorStatus error, const V1_3::Capabilities& capabilities) {
                // TODO(dgross): Do we need to check compliantWithV1_2(capabilities)?
                cb(convertToV1_0(error), convertToV1_2(capabilities));
            });
}

hardware::Return<void> SampleDriver::getVersionString(getVersionString_cb cb) {
    NNTRACE_FULL(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_INITIALIZATION,
                 "SampleDriver::getVersionString");
    cb(V1_0::ErrorStatus::NONE, "JUST_AN_EXAMPLE");
    return hardware::Void();
}

hardware::Return<void> SampleDriver::getType(getType_cb cb) {
    NNTRACE_FULL(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_INITIALIZATION, "SampleDriver::getType");
    cb(V1_0::ErrorStatus::NONE, V1_2::DeviceType::CPU);
    return hardware::Void();
}

hardware::Return<void> SampleDriver::getSupportedExtensions(getSupportedExtensions_cb cb) {
    NNTRACE_FULL(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_INITIALIZATION,
                 "SampleDriver::getSupportedExtensions");
    cb(V1_0::ErrorStatus::NONE, {/* No extensions. */});
    return hardware::Void();
}

hardware::Return<void> SampleDriver::getSupportedOperations(const V1_0::Model& model,
                                                            getSupportedOperations_cb cb) {
    NNTRACE_FULL(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_COMPILATION,
                 "SampleDriver::getSupportedOperations");
    if (!validateModel(model)) {
        VLOG(DRIVER) << "getSupportedOperations";
        cb(V1_0::ErrorStatus::INVALID_ARGUMENT, {});
        return hardware::Void();
    }
    return getSupportedOperations_1_3(
            convertToV1_3(model),
            [&](V1_3::ErrorStatus status, const hardware::hidl_vec<bool>& supported) {
                cb(convertToV1_0(status), supported);
            });
}

hardware::Return<void> SampleDriver::getSupportedOperations_1_1(const V1_1::Model& model,
                                                                getSupportedOperations_1_1_cb cb) {
    NNTRACE_FULL(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_COMPILATION,
                 "SampleDriver::getSupportedOperations_1_1");
    if (!validateModel(model)) {
        VLOG(DRIVER) << "getSupportedOperations_1_1";
        cb(V1_0::ErrorStatus::INVALID_ARGUMENT, {});
        return hardware::Void();
    }
    return getSupportedOperations_1_3(
            convertToV1_3(model),
            [&](V1_3::ErrorStatus status, const hardware::hidl_vec<bool>& supported) {
                cb(convertToV1_0(status), supported);
            });
}

hardware::Return<void> SampleDriver::getSupportedOperations_1_2(const V1_2::Model& model,
                                                                getSupportedOperations_1_2_cb cb) {
    NNTRACE_FULL(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_COMPILATION,
                 "SampleDriver::getSupportedOperations_1_2");
    if (!validateModel(model)) {
        VLOG(DRIVER) << "getSupportedOperations_1_2";
        cb(V1_0::ErrorStatus::INVALID_ARGUMENT, {});
        return hardware::Void();
    }
    return getSupportedOperations_1_3(
            convertToV1_3(model),
            [&](V1_3::ErrorStatus status, const hardware::hidl_vec<bool>& supported) {
                cb(convertToV1_0(status), supported);
            });
}

hardware::Return<void> SampleDriver::getNumberOfCacheFilesNeeded(
        getNumberOfCacheFilesNeeded_cb cb) {
    NNTRACE_FULL(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_INITIALIZATION,
                 "SampleDriver::getNumberOfCacheFilesNeeded");
    // Set both numbers to be 0 for cache not supported.
    cb(V1_0::ErrorStatus::NONE, /*numModelCache=*/0, /*numDataCache=*/0);
    return hardware::Void();
}

hardware::Return<V1_0::ErrorStatus> SampleDriver::prepareModel(
        const V1_0::Model& model, const sp<V1_0::IPreparedModelCallback>& callback) {
    NNTRACE_FULL(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_COMPILATION, "SampleDriver::prepareModel");
    const V1_3::ErrorStatus status =
            prepareModelBase(model, this, V1_1::ExecutionPreference::FAST_SINGLE_ANSWER,
                             kDefaultPriority13, {}, callback);
    return convertToV1_0(status);
}

hardware::Return<V1_0::ErrorStatus> SampleDriver::prepareModel_1_1(
        const V1_1::Model& model, V1_1::ExecutionPreference preference,
        const sp<V1_0::IPreparedModelCallback>& callback) {
    NNTRACE_FULL(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_COMPILATION, "SampleDriver::prepareModel_1_1");
    const V1_3::ErrorStatus status =
            prepareModelBase(model, this, preference, kDefaultPriority13, {}, callback);
    return convertToV1_0(status);
}

hardware::Return<V1_0::ErrorStatus> SampleDriver::prepareModel_1_2(
        const V1_2::Model& model, V1_1::ExecutionPreference preference,
        const hardware::hidl_vec<hardware::hidl_handle>&,
        const hardware::hidl_vec<hardware::hidl_handle>&, const HalCacheToken&,
        const sp<V1_2::IPreparedModelCallback>& callback) {
    NNTRACE_FULL(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_COMPILATION, "SampleDriver::prepareModel_1_2");
    const V1_3::ErrorStatus status =
            prepareModelBase(model, this, preference, kDefaultPriority13, {}, callback);
    return convertToV1_0(status);
}

hardware::Return<V1_3::ErrorStatus> SampleDriver::prepareModel_1_3(
        const V1_3::Model& model, V1_1::ExecutionPreference preference, V1_3::Priority priority,
        const V1_3::OptionalTimePoint& deadline, const hardware::hidl_vec<hardware::hidl_handle>&,
        const hardware::hidl_vec<hardware::hidl_handle>&, const HalCacheToken&,
        const sp<V1_3::IPreparedModelCallback>& callback) {
    NNTRACE_FULL(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_COMPILATION, "SampleDriver::prepareModel_1_3");
    return prepareModelBase(model, this, preference, priority, deadline, callback);
}

hardware::Return<V1_0::ErrorStatus> SampleDriver::prepareModelFromCache(
        const hardware::hidl_vec<hardware::hidl_handle>&,
        const hardware::hidl_vec<hardware::hidl_handle>&, const HalCacheToken&,
        const sp<V1_2::IPreparedModelCallback>& callback) {
    NNTRACE_FULL(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_COMPILATION,
                 "SampleDriver::prepareModelFromCache");
    notify(callback, V1_3::ErrorStatus::GENERAL_FAILURE, nullptr);
    return V1_0::ErrorStatus::GENERAL_FAILURE;
}

hardware::Return<V1_3::ErrorStatus> SampleDriver::prepareModelFromCache_1_3(
        const V1_3::OptionalTimePoint& /*deadline*/,
        const hardware::hidl_vec<hardware::hidl_handle>&,
        const hardware::hidl_vec<hardware::hidl_handle>&, const HalCacheToken&,
        const sp<V1_3::IPreparedModelCallback>& callback) {
    NNTRACE_FULL(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_COMPILATION,
                 "SampleDriver::prepareModelFromCache_1_3");
    notify(callback, V1_3::ErrorStatus::GENERAL_FAILURE, nullptr);
    return V1_3::ErrorStatus::GENERAL_FAILURE;
}

hardware::Return<V1_0::DeviceStatus> SampleDriver::getStatus() {
    NNTRACE_FULL(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_UNSPECIFIED, "SampleDriver::getStatus");
    VLOG(DRIVER) << "getStatus()";
    return V1_0::DeviceStatus::AVAILABLE;
}

// Safely downcast an IPreparedModel object to SamplePreparedModel.
// This function will return nullptr if the IPreparedModel object is not originated from the sample
// driver process.
static const SamplePreparedModel* castToSamplePreparedModel(
        const sp<V1_3::IPreparedModel>& preparedModel) {
    if (preparedModel->isRemote()) {
        return nullptr;
    } else {
        // This static_cast is safe because SamplePreparedModel is the only class that implements
        // the IPreparedModel interface in the sample driver process.
        return static_cast<const SamplePreparedModel*>(preparedModel.get());
    }
}

hardware::Return<void> SampleDriver::allocate(
        const V1_3::BufferDesc& desc,
        const hardware::hidl_vec<sp<V1_3::IPreparedModel>>& preparedModels,
        const hardware::hidl_vec<V1_3::BufferRole>& inputRoles,
        const hardware::hidl_vec<V1_3::BufferRole>& outputRoles, allocate_cb cb) {
    constexpr uint32_t kInvalidBufferToken = 0;

    VLOG(DRIVER) << "SampleDriver::allocate";
    std::set<HalPreparedModelRole> roles;
    V1_3::Operand operand;
    auto getModel = [](const sp<V1_3::IPreparedModel>& preparedModel) -> const V1_3::Model* {
        const auto* samplePreparedModel = castToSamplePreparedModel(preparedModel);
        if (samplePreparedModel == nullptr) {
            LOG(ERROR) << "SampleDriver::allocate -- unknown remote IPreparedModel.";
            return nullptr;
        }
        return samplePreparedModel->getModel();
    };
    if (!validateMemoryDesc(desc, preparedModels, inputRoles, outputRoles, getModel, &roles,
                            &operand)) {
        LOG(ERROR) << "SampleDriver::allocate -- validation failed.";
        cb(V1_3::ErrorStatus::INVALID_ARGUMENT, nullptr, kInvalidBufferToken);
        return hardware::Void();
    }

    if (isExtensionOperandType(operand.type)) {
        LOG(ERROR) << "SampleDriver::allocate -- does not support extension type.";
        cb(V1_3::ErrorStatus::GENERAL_FAILURE, nullptr, kInvalidBufferToken);
        return hardware::Void();
    }

    // TODO(xusongw): Support allocating buffers with unknown dimensions or rank.
    uint32_t size = nonExtensionOperandSizeOfData(operand.type, operand.dimensions);
    VLOG(DRIVER) << "SampleDriver::allocate -- type = " << toString(operand.type)
                 << ", dimensions = " << toString(operand.dimensions) << ", size = " << size;
    if (size == 0) {
        LOG(ERROR) << "SampleDriver::allocate -- does not support dynamic output shape.";
        cb(V1_3::ErrorStatus::GENERAL_FAILURE, nullptr, kInvalidBufferToken);
        return hardware::Void();
    }

    auto bufferWrapper =
            HalManagedBuffer::create(size, std::move(roles), uncheckedConvert(operand));
    if (bufferWrapper == nullptr) {
        LOG(ERROR) << "SampleDriver::allocate -- not enough memory.";
        cb(V1_3::ErrorStatus::GENERAL_FAILURE, nullptr, kInvalidBufferToken);
        return hardware::Void();
    }

    auto token = mHalBufferTracker->add(bufferWrapper);
    if (token == nullptr) {
        LOG(ERROR) << "SampleDriver::allocate -- HalBufferTracker returned invalid token.";
        cb(V1_3::ErrorStatus::GENERAL_FAILURE, nullptr, kInvalidBufferToken);
        return hardware::Void();
    }

    const uint32_t tokenValue = token->get();
    sp<SampleBuffer> sampleBuffer = new SampleBuffer(std::move(bufferWrapper), std::move(token));
    VLOG(DRIVER) << "SampleDriver::allocate -- successfully allocates the requested memory";
    cb(V1_3::ErrorStatus::NONE, std::move(sampleBuffer), tokenValue);
    return hardware::Void();
}

static void copyRunTimePoolInfos(const RunTimePoolInfo& srcPool, const RunTimePoolInfo& dstPool) {
    CHECK(srcPool.getBuffer() != nullptr);
    CHECK(dstPool.getBuffer() != nullptr);
    CHECK(srcPool.getSize() == dstPool.getSize());
    std::copy(srcPool.getBuffer(), srcPool.getBuffer() + srcPool.getSize(), dstPool.getBuffer());
    dstPool.flush();
}

hardware::Return<V1_3::ErrorStatus> SampleBuffer::copyTo(const hardware::hidl_memory& dst) {
    const auto dstPool = RunTimePoolInfo::createFromMemory(uncheckedConvert(dst));
    if (!dstPool.has_value()) {
        LOG(ERROR) << "SampleBuffer::copyTo -- unable to map dst memory.";
        return V1_3::ErrorStatus::GENERAL_FAILURE;
    }
    const V1_3::ErrorStatus validationStatus =
            convertToV1_3(kBuffer->validateCopyTo(dstPool->getSize()));
    if (validationStatus != V1_3::ErrorStatus::NONE) {
        return validationStatus;
    }
    const auto srcPool = kBuffer->createRunTimePoolInfo();
    copyRunTimePoolInfos(srcPool, dstPool.value());
    return V1_3::ErrorStatus::NONE;
}

static V1_3::ErrorStatus copyFromInternal(const hardware::hidl_memory& src,
                                          const hardware::hidl_vec<uint32_t>& dimensions,
                                          const std::shared_ptr<HalManagedBuffer>& bufferWrapper) {
    CHECK(bufferWrapper != nullptr);
    const auto srcPool = RunTimePoolInfo::createFromMemory(uncheckedConvert(src));
    if (!srcPool.has_value()) {
        LOG(ERROR) << "SampleBuffer::copyFrom -- unable to map src memory.";
        return V1_3::ErrorStatus::GENERAL_FAILURE;
    }
    const V1_3::ErrorStatus validationStatus =
            convertToV1_3(bufferWrapper->validateCopyFrom(dimensions, srcPool->getSize()));
    if (validationStatus != V1_3::ErrorStatus::NONE) {
        return validationStatus;
    }
    const auto dstPool = bufferWrapper->createRunTimePoolInfo();
    copyRunTimePoolInfos(srcPool.value(), dstPool);
    return V1_3::ErrorStatus::NONE;
}

hardware::Return<V1_3::ErrorStatus> SampleBuffer::copyFrom(
        const hardware::hidl_memory& src, const hardware::hidl_vec<uint32_t>& dimensions) {
    const auto status = copyFromInternal(src, dimensions, kBuffer);
    if (status == V1_3::ErrorStatus::NONE) {
        kBuffer->updateDimensions(dimensions);
        kBuffer->setInitialized(true);
    } else {
        kBuffer->setInitialized(false);
    }
    return status;
}

bool SamplePreparedModel::initialize() {
    return setRunTimePoolInfosFromCanonicalMemories(&mPoolInfos, uncheckedConvert(mModel.pools));
}

static std::tuple<V1_3::ErrorStatus, std::vector<RunTimePoolInfo>,
                  std::vector<std::shared_ptr<HalManagedBuffer>>>
createRunTimePoolInfos(const V1_3::Request& request, const SampleDriver& driver,
                       const SamplePreparedModel* preparedModel) {
    std::vector<RunTimePoolInfo> requestPoolInfos;
    std::vector<std::shared_ptr<HalManagedBuffer>> bufferWrappers;
    requestPoolInfos.reserve(request.pools.size());
    bufferWrappers.reserve(request.pools.size());
    for (uint32_t i = 0; i < request.pools.size(); i++) {
        auto& pool = request.pools[i];
        switch (pool.getDiscriminator()) {
            case V1_3::Request::MemoryPool::hidl_discriminator::hidlMemory: {
                auto buffer =
                        RunTimePoolInfo::createFromMemory(uncheckedConvert(pool.hidlMemory()));
                if (!buffer.has_value()) {
                    LOG(ERROR) << "createRuntimeMemoriesFromMemoryPools -- could not map pools";
                    return {V1_3::ErrorStatus::GENERAL_FAILURE, {}, {}};
                }
                requestPoolInfos.push_back(std::move(*buffer));
                bufferWrappers.push_back(nullptr);
            } break;
            case V1_3::Request::MemoryPool::hidl_discriminator::token: {
                auto bufferWrapper = driver.getHalBufferTracker()->get(pool.token());
                if (bufferWrapper == nullptr) {
                    return {V1_3::ErrorStatus::INVALID_ARGUMENT, {}, {}};
                }
                const auto validationStatus = convertToV1_3(bufferWrapper->validateRequest(
                        i, uncheckedConvert(request), preparedModel));
                if (validationStatus != V1_3::ErrorStatus::NONE) {
                    return {validationStatus, {}, {}};
                }
                requestPoolInfos.push_back(bufferWrapper->createRunTimePoolInfo());
                bufferWrappers.push_back(std::move(bufferWrapper));
            } break;
        }
    }
    return {V1_3::ErrorStatus::NONE, std::move(requestPoolInfos), std::move(bufferWrappers)};
}

static V1_3::ErrorStatus updateDeviceMemories(
        V1_3::ErrorStatus status, const V1_3::Request& request,
        const std::vector<std::shared_ptr<HalManagedBuffer>>& bufferWrappers,
        const hardware::hidl_vec<V1_2::OutputShape>& outputShapes) {
    if (status == V1_3::ErrorStatus::NONE) {
        for (uint32_t i = 0; i < request.outputs.size(); i++) {
            const uint32_t poolIndex = request.outputs[i].location.poolIndex;
            const auto& pool = request.pools[poolIndex];
            if (pool.getDiscriminator() == V1_3::Request::MemoryPool::hidl_discriminator::token) {
                if (!bufferWrappers[poolIndex]->updateDimensions(outputShapes[i].dimensions)) {
                    return V1_3::ErrorStatus::GENERAL_FAILURE;
                }
            }
        }
        for (uint32_t i = 0; i < request.outputs.size(); i++) {
            const uint32_t poolIndex = request.outputs[i].location.poolIndex;
            const auto& pool = request.pools[poolIndex];
            if (pool.getDiscriminator() == V1_3::Request::MemoryPool::hidl_discriminator::token) {
                bufferWrappers[poolIndex]->setInitialized(true);
            }
        }
    } else if (status == V1_3::ErrorStatus::OUTPUT_INSUFFICIENT_SIZE) {
        // If CpuExecutor reports OUTPUT_INSUFFCIENT_SIZE on a device memory, this is because the
        // dimensions of the device memory are incorrectly specified. The driver should return
        // GENERAL_FAILURE instead in this case.
        for (uint32_t i = 0; i < request.outputs.size(); i++) {
            const uint32_t poolIndex = request.outputs[i].location.poolIndex;
            const auto& pool = request.pools[poolIndex];
            if (pool.getDiscriminator() == V1_3::Request::MemoryPool::hidl_discriminator::token) {
                if (!outputShapes[i].isSufficient) {
                    LOG(ERROR) << "Invalid dimensions for output " << i
                               << ": actual shape = " << toString(outputShapes[i].dimensions);
                    return V1_3::ErrorStatus::GENERAL_FAILURE;
                }
            }
        }
    }
    return V1_3::ErrorStatus::NONE;
}

template <typename T_IExecutionCallback>
void asyncExecute(const V1_3::Request& request, V1_2::MeasureTiming measure, TimePoint driverStart,
                  const V1_3::Model& model, const SampleDriver& driver,
                  const SamplePreparedModel* preparedModel,
                  const std::vector<RunTimePoolInfo>& poolInfos, const OptionalTimePoint& deadline,
                  const V1_3::OptionalTimeoutDuration& loopTimeoutDuration,
                  const sp<T_IExecutionCallback>& callback) {
    NNTRACE_FULL(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_INPUTS_AND_OUTPUTS,
                 "SampleDriver::asyncExecute");

    const auto [poolStatus, requestPoolInfos, bufferWrappers] =
            createRunTimePoolInfos(request, driver, preparedModel);
    if (poolStatus != V1_3::ErrorStatus::NONE) {
        notify(callback, poolStatus, {}, kNoTiming);
        return;
    }

    NNTRACE_FULL_SWITCH(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_EXECUTION,
                        "SampleDriver::asyncExecute");
    CpuExecutor executor = driver.getExecutor();
    if (loopTimeoutDuration.getDiscriminator() !=
        V1_3::OptionalTimeoutDuration::hidl_discriminator::none) {
        executor.setLoopTimeout(loopTimeoutDuration.nanoseconds());
    }
    if (deadline.has_value()) {
        executor.setDeadline(*deadline);
    }
    TimePoint driverEnd, deviceStart, deviceEnd;
    if (measure == V1_2::MeasureTiming::YES) deviceStart = Clock::now();
    int n = executor.run(uncheckedConvert(model), uncheckedConvert(request), poolInfos,
                         requestPoolInfos);
    if (measure == V1_2::MeasureTiming::YES) deviceEnd = Clock::now();
    VLOG(DRIVER) << "executor.run returned " << n;
    V1_3::ErrorStatus executionStatus = convertResultCodeToHalErrorStatus(n);
    hardware::hidl_vec<V1_2::OutputShape> outputShapes = convertToV1_2(executor.getOutputShapes());

    // Update device memory metadata.
    const V1_3::ErrorStatus updateStatus =
            updateDeviceMemories(executionStatus, request, bufferWrappers, outputShapes);
    if (updateStatus != V1_3::ErrorStatus::NONE) {
        notify(callback, updateStatus, {}, kNoTiming);
        return;
    }

    if (measure == V1_2::MeasureTiming::YES && executionStatus == V1_3::ErrorStatus::NONE) {
        driverEnd = Clock::now();
        V1_2::Timing timing = {
                .timeOnDevice = uint64_t(microsecondsDuration(deviceEnd, deviceStart)),
                .timeInDriver = uint64_t(microsecondsDuration(driverEnd, driverStart))};
        VLOG(DRIVER) << "SampleDriver::asyncExecute timing = " << toString(timing);
        notify(callback, executionStatus, outputShapes, timing);
    } else {
        notify(callback, executionStatus, outputShapes, kNoTiming);
    }
}

template <typename T_IExecutionCallback>
V1_3::ErrorStatus executeBase(const V1_3::Request& request, V1_2::MeasureTiming measure,
                              const V1_3::Model& model, const SampleDriver& driver,
                              const SamplePreparedModel* preparedModel,
                              const std::vector<RunTimePoolInfo>& poolInfos,
                              const V1_3::OptionalTimePoint& halDeadline,
                              const V1_3::OptionalTimeoutDuration& loopTimeoutDuration,
                              const sp<T_IExecutionCallback>& callback) {
    NNTRACE_FULL(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_EXECUTION, "SampleDriver::executeBase");
    VLOG(DRIVER) << "executeBase(" << SHOW_IF_DEBUG(toString(request)) << ")";

    TimePoint driverStart;
    if (measure == V1_2::MeasureTiming::YES) driverStart = Clock::now();

    if (callback.get() == nullptr) {
        LOG(ERROR) << "invalid callback passed to executeBase";
        return V1_3::ErrorStatus::INVALID_ARGUMENT;
    }
    if (!validateRequest(request, model)) {
        notify(callback, V1_3::ErrorStatus::INVALID_ARGUMENT, {}, kNoTiming);
        return V1_3::ErrorStatus::INVALID_ARGUMENT;
    }
    const auto deadline = convert(halDeadline).value();
    if (hasDeadlinePassed(deadline)) {
        notify(callback, V1_3::ErrorStatus::MISSED_DEADLINE_PERSISTENT, {}, kNoTiming);
        return V1_3::ErrorStatus::NONE;
    }

    // This thread is intentionally detached because the sample driver service
    // is expected to live forever.
    std::thread([&model, &driver, preparedModel, &poolInfos, request, measure, driverStart,
                 deadline, loopTimeoutDuration, callback] {
        asyncExecute(request, measure, driverStart, model, driver, preparedModel, poolInfos,
                     deadline, loopTimeoutDuration, callback);
    }).detach();

    return V1_3::ErrorStatus::NONE;
}

hardware::Return<V1_0::ErrorStatus> SamplePreparedModel::execute(
        const V1_0::Request& request, const sp<V1_0::IExecutionCallback>& callback) {
    const V1_3::ErrorStatus status =
            executeBase(convertToV1_3(request), V1_2::MeasureTiming::NO, mModel, *mDriver, this,
                        mPoolInfos, {}, {}, callback);
    return convertToV1_0(status);
}

hardware::Return<V1_0::ErrorStatus> SamplePreparedModel::execute_1_2(
        const V1_0::Request& request, V1_2::MeasureTiming measure,
        const sp<V1_2::IExecutionCallback>& callback) {
    const V1_3::ErrorStatus status = executeBase(convertToV1_3(request), measure, mModel, *mDriver,
                                                 this, mPoolInfos, {}, {}, callback);
    return convertToV1_0(status);
}

hardware::Return<V1_3::ErrorStatus> SamplePreparedModel::execute_1_3(
        const V1_3::Request& request, V1_2::MeasureTiming measure,
        const V1_3::OptionalTimePoint& deadline,
        const V1_3::OptionalTimeoutDuration& loopTimeoutDuration,
        const sp<V1_3::IExecutionCallback>& callback) {
    return executeBase(request, measure, mModel, *mDriver, this, mPoolInfos, deadline,
                       loopTimeoutDuration, callback);
}

static std::tuple<V1_3::ErrorStatus, hardware::hidl_vec<V1_2::OutputShape>, V1_2::Timing>
executeSynchronouslyBase(const V1_3::Request& request, V1_2::MeasureTiming measure,
                         const V1_3::Model& model, const SampleDriver& driver,
                         const SamplePreparedModel* preparedModel,
                         const std::vector<RunTimePoolInfo>& poolInfos,
                         const V1_3::OptionalTimePoint& halDeadline,
                         const V1_3::OptionalTimeoutDuration& loopTimeoutDuration) {
    NNTRACE_FULL(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_EXECUTION,
                 "SampleDriver::executeSynchronouslyBase");
    VLOG(DRIVER) << "executeSynchronouslyBase(" << SHOW_IF_DEBUG(toString(request)) << ")";

    TimePoint driverStart, driverEnd, deviceStart, deviceEnd;
    if (measure == V1_2::MeasureTiming::YES) driverStart = Clock::now();

    if (!validateRequest(request, model)) {
        return {V1_3::ErrorStatus::INVALID_ARGUMENT, {}, kNoTiming};
    }
    const auto deadline = convert(halDeadline).value();
    if (hasDeadlinePassed(deadline)) {
        return {V1_3::ErrorStatus::MISSED_DEADLINE_PERSISTENT, {}, kNoTiming};
    }

    NNTRACE_FULL_SWITCH(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_INPUTS_AND_OUTPUTS,
                        "SampleDriver::executeSynchronouslyBase");
    const auto [poolStatus, requestPoolInfos, bufferWrappers] =
            createRunTimePoolInfos(request, driver, preparedModel);
    if (poolStatus != V1_3::ErrorStatus::NONE) {
        return {poolStatus, {}, kNoTiming};
    }

    NNTRACE_FULL_SWITCH(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_EXECUTION,
                        "SampleDriver::executeSynchronouslyBase");
    CpuExecutor executor = driver.getExecutor();
    if (loopTimeoutDuration.getDiscriminator() !=
        V1_3::OptionalTimeoutDuration::hidl_discriminator::none) {
        executor.setLoopTimeout(loopTimeoutDuration.nanoseconds());
    }
    if (deadline.has_value()) {
        executor.setDeadline(*deadline);
    }
    if (measure == V1_2::MeasureTiming::YES) deviceStart = Clock::now();
    int n = executor.run(uncheckedConvert(model), uncheckedConvert(request), poolInfos,
                         requestPoolInfos);
    if (measure == V1_2::MeasureTiming::YES) deviceEnd = Clock::now();
    VLOG(DRIVER) << "executor.run returned " << n;
    V1_3::ErrorStatus executionStatus = convertResultCodeToHalErrorStatus(n);
    hardware::hidl_vec<V1_2::OutputShape> outputShapes = convertToV1_2(executor.getOutputShapes());

    // Update device memory metadata.
    const V1_3::ErrorStatus updateStatus =
            updateDeviceMemories(executionStatus, request, bufferWrappers, outputShapes);
    if (updateStatus != V1_3::ErrorStatus::NONE) {
        return {updateStatus, {}, kNoTiming};
    }

    if (measure == V1_2::MeasureTiming::YES && executionStatus == V1_3::ErrorStatus::NONE) {
        driverEnd = Clock::now();
        V1_2::Timing timing = {
                .timeOnDevice = uint64_t(microsecondsDuration(deviceEnd, deviceStart)),
                .timeInDriver = uint64_t(microsecondsDuration(driverEnd, driverStart))};
        VLOG(DRIVER) << "executeSynchronouslyBase timing = " << toString(timing);
        return {executionStatus, std::move(outputShapes), timing};
    }
    return {executionStatus, std::move(outputShapes), kNoTiming};
}

hardware::Return<void> SamplePreparedModel::executeSynchronously(const V1_0::Request& request,
                                                                 V1_2::MeasureTiming measure,
                                                                 executeSynchronously_cb cb) {
    auto [status, outputShapes, timing] = executeSynchronouslyBase(
            convertToV1_3(request), measure, mModel, *mDriver, this, mPoolInfos, {}, {});
    cb(convertToV1_0(status), std::move(outputShapes), timing);
    return hardware::Void();
}

hardware::Return<void> SamplePreparedModel::executeSynchronously_1_3(
        const V1_3::Request& request, V1_2::MeasureTiming measure,
        const V1_3::OptionalTimePoint& deadline,
        const V1_3::OptionalTimeoutDuration& loopTimeoutDuration, executeSynchronously_1_3_cb cb) {
    auto [status, outputShapes, timing] = executeSynchronouslyBase(
            request, measure, mModel, *mDriver, this, mPoolInfos, deadline, loopTimeoutDuration);
    cb(status, std::move(outputShapes), timing);
    return hardware::Void();
}

// The sample driver will finish the execution and then return.
hardware::Return<void> SamplePreparedModel::executeFenced(
        const V1_3::Request& request, const hardware::hidl_vec<hardware::hidl_handle>& waitFor,
        V1_2::MeasureTiming measure, const V1_3::OptionalTimePoint& halDeadline,
        const V1_3::OptionalTimeoutDuration& loopTimeoutDuration,
        const V1_3::OptionalTimeoutDuration& duration, executeFenced_cb cb) {
    NNTRACE_FULL(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_EXECUTION,
                 "SamplePreparedModel::executeFenced");
    VLOG(DRIVER) << "executeFenced(" << SHOW_IF_DEBUG(toString(request)) << ")";

    TimePoint driverStart, driverEnd, deviceStart, deviceEnd;
    if (measure == V1_2::MeasureTiming::YES) driverStart = Clock::now();

    if (!validateRequest(request, mModel, /*allowUnspecifiedOutput=*/false)) {
        cb(V1_3::ErrorStatus::INVALID_ARGUMENT, hardware::hidl_handle(nullptr), nullptr);
        return hardware::Void();
    }
    const auto deadline = convert(halDeadline).value();
    if (hasDeadlinePassed(deadline)) {
        cb(V1_3::ErrorStatus::MISSED_DEADLINE_PERSISTENT, hardware::hidl_handle(nullptr), nullptr);
        return hardware::Void();
    }

    // Wait for the dependent events to signal
    for (const auto& fenceHandle : waitFor) {
        if (!fenceHandle.getNativeHandle()) {
            cb(V1_3::ErrorStatus::INVALID_ARGUMENT, hardware::hidl_handle(nullptr), nullptr);
            return hardware::Void();
        }
        int syncFenceFd = fenceHandle.getNativeHandle()->data[0];
        if (syncWait(syncFenceFd, -1) != FenceState::SIGNALED) {
            LOG(ERROR) << "syncWait failed";
            cb(V1_3::ErrorStatus::GENERAL_FAILURE, hardware::hidl_handle(nullptr), nullptr);
            return hardware::Void();
        }
    }

    // Update deadline if the timeout duration is closer than the deadline.
    auto closestDeadline = deadline;
    if (duration.getDiscriminator() != V1_3::OptionalTimeoutDuration::hidl_discriminator::none) {
        const auto timeoutDurationDeadline = makeDeadline(duration.nanoseconds());
        if (!closestDeadline.has_value() || *closestDeadline > timeoutDurationDeadline) {
            closestDeadline = timeoutDurationDeadline;
        }
    }

    TimePoint driverStartAfterFence;
    if (measure == V1_2::MeasureTiming::YES) driverStartAfterFence = Clock::now();

    NNTRACE_FULL_SWITCH(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_INPUTS_AND_OUTPUTS,
                        "SamplePreparedModel::executeFenced");
    const auto [poolStatus, requestPoolInfos, bufferWrappers] =
            createRunTimePoolInfos(request, *mDriver, this);
    if (poolStatus != V1_3::ErrorStatus::NONE) {
        cb(poolStatus, hardware::hidl_handle(nullptr), nullptr);
        return hardware::Void();
    }

    NNTRACE_FULL_SWITCH(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_EXECUTION,
                        "SamplePreparedModel::executeFenced");
    CpuExecutor executor = mDriver->getExecutor();
    if (loopTimeoutDuration.getDiscriminator() !=
        V1_3::OptionalTimeoutDuration::hidl_discriminator::none) {
        executor.setLoopTimeout(loopTimeoutDuration.nanoseconds());
    }
    if (closestDeadline.has_value()) {
        executor.setDeadline(*closestDeadline);
    }
    if (measure == V1_2::MeasureTiming::YES) deviceStart = Clock::now();
    int n = executor.run(uncheckedConvert(mModel), uncheckedConvert(request), mPoolInfos,
                         requestPoolInfos);
    if (measure == V1_2::MeasureTiming::YES) deviceEnd = Clock::now();
    VLOG(DRIVER) << "executor.run returned " << n;
    V1_3::ErrorStatus executionStatus = convertResultCodeToHalErrorStatus(n);
    if (executionStatus != V1_3::ErrorStatus::NONE) {
        cb(executionStatus, hardware::hidl_handle(nullptr), nullptr);
        return hardware::Void();
    }

    // Set output memories to the initialized state.
    if (executionStatus == V1_3::ErrorStatus::NONE) {
        for (const auto& output : request.outputs) {
            const uint32_t poolIndex = output.location.poolIndex;
            const auto& pool = request.pools[poolIndex];
            if (pool.getDiscriminator() == V1_3::Request::MemoryPool::hidl_discriminator::token) {
                bufferWrappers[poolIndex]->setInitialized(true);
            }
        }
    }

    V1_2::Timing timingSinceLaunch = {.timeOnDevice = UINT64_MAX, .timeInDriver = UINT64_MAX};
    V1_2::Timing timingAfterFence = {.timeOnDevice = UINT64_MAX, .timeInDriver = UINT64_MAX};
    if (measure == V1_2::MeasureTiming::YES) {
        driverEnd = Clock::now();
        timingSinceLaunch = {
                .timeOnDevice = uint64_t(microsecondsDuration(deviceEnd, deviceStart)),
                .timeInDriver = uint64_t(microsecondsDuration(driverEnd, driverStart))};
        timingAfterFence = {
                .timeOnDevice = uint64_t(microsecondsDuration(deviceEnd, deviceStart)),
                .timeInDriver = uint64_t(microsecondsDuration(driverEnd, driverStartAfterFence))};
        VLOG(DRIVER) << "executeFenced timingSinceLaunch = " << toString(timingSinceLaunch);
        VLOG(DRIVER) << "executeFenced timingAfterFence = " << toString(timingAfterFence);
    }
    sp<SampleFencedExecutionCallback> fencedExecutionCallback =
            new SampleFencedExecutionCallback(timingSinceLaunch, timingAfterFence, executionStatus);
    cb(executionStatus, hardware::hidl_handle(nullptr), fencedExecutionCallback);
    return hardware::Void();
}

// BurstExecutorWithCache maps hidl_memory when it is first seen, and preserves
// the mapping until either (1) the memory is freed in the runtime, or (2) the
// burst object is destroyed. This allows for subsequent executions operating on
// pools that have been used before to reuse the mapping instead of mapping and
// unmapping the memory on each execution.
class BurstExecutorWithCache : public ExecutionBurstServer::IBurstExecutorWithCache {
   public:
    BurstExecutorWithCache(const V1_3::Model& model, const SampleDriver* driver,
                           const std::vector<RunTimePoolInfo>& poolInfos)
        : mModel(model), mDriver(driver), mModelPoolInfos(poolInfos) {}

    bool isCacheEntryPresent(int32_t slot) const override {
        const auto it = mMemoryCache.find(slot);
        return (it != mMemoryCache.end()) && it->second.has_value();
    }

    void addCacheEntry(const hardware::hidl_memory& memory, int32_t slot) override {
        mMemoryCache[slot] = RunTimePoolInfo::createFromMemory(uncheckedConvert(memory));
    }

    void removeCacheEntry(int32_t slot) override { mMemoryCache.erase(slot); }

    std::tuple<V1_0::ErrorStatus, hardware::hidl_vec<V1_2::OutputShape>, V1_2::Timing> execute(
            const V1_0::Request& request, const std::vector<int32_t>& slots,
            V1_2::MeasureTiming measure) override {
        NNTRACE_FULL(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_EXECUTION,
                     "BurstExecutorWithCache::execute");

        TimePoint driverStart, driverEnd, deviceStart, deviceEnd;
        if (measure == V1_2::MeasureTiming::YES) driverStart = Clock::now();

        // ensure all relevant pools are valid
        if (!std::all_of(slots.begin(), slots.end(),
                         [this](int32_t slot) { return isCacheEntryPresent(slot); })) {
            return {V1_0::ErrorStatus::INVALID_ARGUMENT, {}, kNoTiming};
        }

        // finish the request object (for validation)
        hardware::hidl_vec<V1_3::Request::MemoryPool> pools(slots.size());
        std::transform(slots.begin(), slots.end(), pools.begin(), [this](int32_t slot) {
            V1_3::Request::MemoryPool pool;
            pool.hidlMemory(convertToV1_0(mMemoryCache[slot]->getMemory()));
            return pool;
        });
        V1_3::Request fullRequest = {.inputs = request.inputs, .outputs = request.outputs};
        fullRequest.pools = std::move(pools);

        // validate request object against the model
        if (!validateRequest(fullRequest, mModel)) {
            return {V1_0::ErrorStatus::INVALID_ARGUMENT, {}, kNoTiming};
        }

        // select relevant entries from cache
        std::vector<RunTimePoolInfo> requestPoolInfos;
        requestPoolInfos.reserve(slots.size());
        std::transform(slots.begin(), slots.end(), std::back_inserter(requestPoolInfos),
                       [this](int32_t slot) { return *mMemoryCache[slot]; });

        // execution
        // Configuring the loop timeout duration is not supported. This is OK
        // because burst does not support HAL 1.3 and hence does not support
        // WHILE loops.
        CpuExecutor executor = mDriver->getExecutor();
        if (measure == V1_2::MeasureTiming::YES) deviceStart = Clock::now();
        int n = executor.run(uncheckedConvert(mModel), uncheckedConvert(fullRequest),
                             mModelPoolInfos, requestPoolInfos);
        if (measure == V1_2::MeasureTiming::YES) deviceEnd = Clock::now();
        VLOG(DRIVER) << "executor.run returned " << n;
        V1_0::ErrorStatus executionStatus = convertToV1_0(convertResultCodeToHalErrorStatus(n));
        hardware::hidl_vec<V1_2::OutputShape> outputShapes =
                convertToV1_2(executor.getOutputShapes());
        if (measure == V1_2::MeasureTiming::YES && executionStatus == V1_0::ErrorStatus::NONE) {
            driverEnd = Clock::now();
            V1_2::Timing timing = {
                    .timeOnDevice = uint64_t(microsecondsDuration(deviceEnd, deviceStart)),
                    .timeInDriver = uint64_t(microsecondsDuration(driverEnd, driverStart))};
            VLOG(DRIVER) << "BurstExecutorWithCache::execute timing = " << toString(timing);
            return std::make_tuple(executionStatus, outputShapes, timing);
        } else {
            return std::make_tuple(executionStatus, outputShapes, kNoTiming);
        }
    }

   private:
    const V1_3::Model mModel;
    const SampleDriver* const mDriver;
    const std::vector<RunTimePoolInfo> mModelPoolInfos;
    std::map<int32_t, std::optional<RunTimePoolInfo>> mMemoryCache;  // cached requestPoolInfos
};

// This is the amount of time the ExecutionBurstServer should spend polling the
// FMQ to see if it has data available before it should fall back to waiting on
// the futex.
static std::chrono::microseconds getPollingTimeWindow() {
    constexpr int32_t defaultPollingTimeWindow = 50;
#ifdef NN_DEBUGGABLE
    constexpr int32_t minPollingTimeWindow = 0;
    const int32_t selectedPollingTimeWindow =
            base::GetIntProperty("debug.nn.sample-driver-burst-polling-window",
                                 defaultPollingTimeWindow, minPollingTimeWindow);
    return std::chrono::microseconds{selectedPollingTimeWindow};
#else
    return std::chrono::microseconds{defaultPollingTimeWindow};
#endif  // NN_DEBUGGABLE
}

hardware::Return<void> SamplePreparedModel::configureExecutionBurst(
        const sp<V1_2::IBurstCallback>& callback,
        const MQDescriptorSync<V1_2::FmqRequestDatum>& requestChannel,
        const MQDescriptorSync<V1_2::FmqResultDatum>& resultChannel,
        configureExecutionBurst_cb cb) {
    NNTRACE_FULL(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_EXECUTION,
                 "SampleDriver::configureExecutionBurst");

    const bool preferPowerOverLatency = (kPreference == V1_1::ExecutionPreference::LOW_POWER);
    const auto pollingTimeWindow =
            (preferPowerOverLatency ? std::chrono::microseconds{0} : getPollingTimeWindow());

    // Alternatively, the burst could be configured via:
    // const sp<V1_2::IBurstContext> burst =
    //         ExecutionBurstServer::create(callback, requestChannel,
    //                                      resultChannel, this,
    //                                      pollingTimeWindow);
    //
    // However, this alternative representation does not include a memory map
    // caching optimization, and adds overhead.
    const std::shared_ptr<BurstExecutorWithCache> executorWithCache =
            std::make_shared<BurstExecutorWithCache>(mModel, mDriver, mPoolInfos);
    const sp<V1_2::IBurstContext> burst = ExecutionBurstServer::create(
            callback, requestChannel, resultChannel, executorWithCache, pollingTimeWindow);

    if (burst == nullptr) {
        cb(V1_0::ErrorStatus::GENERAL_FAILURE, {});
    } else {
        cb(V1_0::ErrorStatus::NONE, burst);
    }

    return hardware::Void();
}

}  // namespace sample_driver
}  // namespace nn
}  // namespace android
