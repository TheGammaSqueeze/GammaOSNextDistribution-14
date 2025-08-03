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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_DRIVER_SAMPLE_SAMPLE_DRIVER_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_DRIVER_SAMPLE_SAMPLE_DRIVER_H

#include <CpuExecutor.h>
#include <HalBufferTracker.h>
#include <HalInterfaces.h>
#include <hwbinder/IPCThreadState.h>

#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "NeuralNetworks.h"

namespace android {
namespace nn {
namespace sample_driver {

using hardware::MQDescriptorSync;

// Manages the data buffer for an operand.
class SampleBuffer : public V1_3::IBuffer {
   public:
    SampleBuffer(std::shared_ptr<HalManagedBuffer> buffer,
                 std::unique_ptr<HalBufferTracker::Token> token)
        : kBuffer(std::move(buffer)), kToken(std::move(token)) {
        CHECK(kBuffer != nullptr);
        CHECK(kToken != nullptr);
    }
    hardware::Return<V1_3::ErrorStatus> copyTo(const hardware::hidl_memory& dst) override;
    hardware::Return<V1_3::ErrorStatus> copyFrom(
            const hardware::hidl_memory& src,
            const hardware::hidl_vec<uint32_t>& dimensions) override;

   private:
    const std::shared_ptr<HalManagedBuffer> kBuffer;
    const std::unique_ptr<HalBufferTracker::Token> kToken;
};

// Base class used to create sample drivers for the NN HAL.  This class
// provides some implementation of the more common functions.
//
// Since these drivers simulate hardware, they must run the computations
// on the CPU.  An actual driver would not do that.
class SampleDriver : public V1_3::IDevice {
   public:
    SampleDriver(const char* name,
                 const IOperationResolver* operationResolver = BuiltinOperationResolver::get())
        : mName(name),
          mOperationResolver(operationResolver),
          mHalBufferTracker(HalBufferTracker::create()) {
        android::nn::initVLogMask();
    }
    hardware::Return<void> getCapabilities(getCapabilities_cb cb) override;
    hardware::Return<void> getCapabilities_1_1(getCapabilities_1_1_cb cb) override;
    hardware::Return<void> getCapabilities_1_2(getCapabilities_1_2_cb cb) override;
    hardware::Return<void> getVersionString(getVersionString_cb cb) override;
    hardware::Return<void> getType(getType_cb cb) override;
    hardware::Return<void> getSupportedExtensions(getSupportedExtensions_cb) override;
    hardware::Return<void> getSupportedOperations(const V1_0::Model& model,
                                                  getSupportedOperations_cb cb) override;
    hardware::Return<void> getSupportedOperations_1_1(const V1_1::Model& model,
                                                      getSupportedOperations_1_1_cb cb) override;
    hardware::Return<void> getSupportedOperations_1_2(const V1_2::Model& model,
                                                      getSupportedOperations_1_2_cb cb) override;
    hardware::Return<void> getNumberOfCacheFilesNeeded(getNumberOfCacheFilesNeeded_cb cb) override;
    hardware::Return<V1_0::ErrorStatus> prepareModel(
            const V1_0::Model& model, const sp<V1_0::IPreparedModelCallback>& callback) override;
    hardware::Return<V1_0::ErrorStatus> prepareModel_1_1(
            const V1_1::Model& model, V1_1::ExecutionPreference preference,
            const sp<V1_0::IPreparedModelCallback>& callback) override;
    hardware::Return<V1_0::ErrorStatus> prepareModel_1_2(
            const V1_2::Model& model, V1_1::ExecutionPreference preference,
            const hardware::hidl_vec<hardware::hidl_handle>& modelCache,
            const hardware::hidl_vec<hardware::hidl_handle>& dataCache, const HalCacheToken& token,
            const sp<V1_2::IPreparedModelCallback>& callback) override;
    hardware::Return<V1_3::ErrorStatus> prepareModel_1_3(
            const V1_3::Model& model, V1_1::ExecutionPreference preference, V1_3::Priority priority,
            const V1_3::OptionalTimePoint& deadline,
            const hardware::hidl_vec<hardware::hidl_handle>& modelCache,
            const hardware::hidl_vec<hardware::hidl_handle>& dataCache, const HalCacheToken& token,
            const sp<V1_3::IPreparedModelCallback>& callback) override;
    hardware::Return<V1_0::ErrorStatus> prepareModelFromCache(
            const hardware::hidl_vec<hardware::hidl_handle>& modelCache,
            const hardware::hidl_vec<hardware::hidl_handle>& dataCache, const HalCacheToken& token,
            const sp<V1_2::IPreparedModelCallback>& callback) override;
    hardware::Return<V1_3::ErrorStatus> prepareModelFromCache_1_3(
            const V1_3::OptionalTimePoint& deadline,
            const hardware::hidl_vec<hardware::hidl_handle>& modelCache,
            const hardware::hidl_vec<hardware::hidl_handle>& dataCache, const HalCacheToken& token,
            const sp<V1_3::IPreparedModelCallback>& callback) override;
    hardware::Return<V1_0::DeviceStatus> getStatus() override;
    hardware::Return<void> allocate(
            const V1_3::BufferDesc& desc,
            const hardware::hidl_vec<sp<V1_3::IPreparedModel>>& preparedModels,
            const hardware::hidl_vec<V1_3::BufferRole>& inputRoles,
            const hardware::hidl_vec<V1_3::BufferRole>& outputRoles, allocate_cb cb) override;

    CpuExecutor getExecutor() const { return CpuExecutor(mOperationResolver); }
    const std::shared_ptr<HalBufferTracker>& getHalBufferTracker() const {
        return mHalBufferTracker;
    }

   protected:
    std::string mName;
    const IOperationResolver* mOperationResolver;
    const std::shared_ptr<HalBufferTracker> mHalBufferTracker;
};

class SamplePreparedModel : public V1_3::IPreparedModel {
   public:
    SamplePreparedModel(const V1_3::Model& model, const SampleDriver* driver,
                        V1_1::ExecutionPreference preference, uid_t userId, V1_3::Priority priority)
        : mModel(model),
          mDriver(driver),
          kPreference(preference),
          kUserId(userId),
          kPriority(priority) {
        (void)kUserId;
        (void)kPriority;
    }
    bool initialize();
    hardware::Return<V1_0::ErrorStatus> execute(
            const V1_0::Request& request, const sp<V1_0::IExecutionCallback>& callback) override;
    hardware::Return<V1_0::ErrorStatus> execute_1_2(
            const V1_0::Request& request, V1_2::MeasureTiming measure,
            const sp<V1_2::IExecutionCallback>& callback) override;
    hardware::Return<V1_3::ErrorStatus> execute_1_3(
            const V1_3::Request& request, V1_2::MeasureTiming measure,
            const V1_3::OptionalTimePoint& deadline,
            const V1_3::OptionalTimeoutDuration& loopTimeoutDuration,
            const sp<V1_3::IExecutionCallback>& callback) override;
    hardware::Return<void> executeSynchronously(const V1_0::Request& request,
                                                V1_2::MeasureTiming measure,
                                                executeSynchronously_cb cb) override;
    hardware::Return<void> executeSynchronously_1_3(
            const V1_3::Request& request, V1_2::MeasureTiming measure,
            const V1_3::OptionalTimePoint& deadline,
            const V1_3::OptionalTimeoutDuration& loopTimeoutDuration,
            executeSynchronously_1_3_cb cb) override;
    hardware::Return<void> configureExecutionBurst(
            const sp<V1_2::IBurstCallback>& callback,
            const MQDescriptorSync<V1_2::FmqRequestDatum>& requestChannel,
            const MQDescriptorSync<V1_2::FmqResultDatum>& resultChannel,
            configureExecutionBurst_cb cb) override;
    hardware::Return<void> executeFenced(const V1_3::Request& request,
                                         const hardware::hidl_vec<hardware::hidl_handle>& wait_for,
                                         V1_2::MeasureTiming measure,
                                         const V1_3::OptionalTimePoint& deadline,
                                         const V1_3::OptionalTimeoutDuration& loopTimeoutDuration,
                                         const V1_3::OptionalTimeoutDuration& duration,
                                         executeFenced_cb callback) override;
    const V1_3::Model* getModel() const { return &mModel; }

   protected:
    V1_3::Model mModel;
    const SampleDriver* mDriver;
    std::vector<RunTimePoolInfo> mPoolInfos;
    const V1_1::ExecutionPreference kPreference;
    const uid_t kUserId;
    const V1_3::Priority kPriority;
};

class SampleFencedExecutionCallback : public V1_3::IFencedExecutionCallback {
   public:
    SampleFencedExecutionCallback(V1_2::Timing timingSinceLaunch, V1_2::Timing timingAfterFence,
                                  V1_3::ErrorStatus error)
        : kTimingSinceLaunch(timingSinceLaunch),
          kTimingAfterFence(timingAfterFence),
          kErrorStatus(error) {}
    hardware::Return<void> getExecutionInfo(getExecutionInfo_cb callback) override {
        callback(kErrorStatus, kTimingSinceLaunch, kTimingAfterFence);
        return hardware::Void();
    }

   private:
    const V1_2::Timing kTimingSinceLaunch;
    const V1_2::Timing kTimingAfterFence;
    const V1_3::ErrorStatus kErrorStatus;
};

}  // namespace sample_driver
}  // namespace nn
}  // namespace android

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_DRIVER_SAMPLE_SAMPLE_DRIVER_H
