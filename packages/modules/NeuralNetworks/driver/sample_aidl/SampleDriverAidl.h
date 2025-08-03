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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_DRIVER_SAMPLE_AIDL_SAMPLE_DRIVER_AIDL_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_DRIVER_SAMPLE_AIDL_SAMPLE_DRIVER_AIDL_H

#include <android/binder_auto_utils.h>
#include <nnapi/hal/aidl/BufferTracker.h>
#include <nnapi/hal/aidl/HalInterfaces.h>

#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "CpuExecutor.h"
#include "NeuralNetworks.h"

namespace android {
namespace nn {
namespace sample_driver_aidl {

// Manages the data buffer for an operand.
class SampleBuffer : public aidl_hal::BnBuffer {
   public:
    SampleBuffer(std::shared_ptr<AidlManagedBuffer> buffer,
                 std::unique_ptr<AidlBufferTracker::Token> token)
        : kBuffer(std::move(buffer)), kToken(std::move(token)) {
        CHECK(kBuffer != nullptr);
        CHECK(kToken != nullptr);
    }
    ndk::ScopedAStatus copyFrom(const aidl_hal::Memory& src,
                                const std::vector<int32_t>& dimensions) override;
    ndk::ScopedAStatus copyTo(const aidl_hal::Memory& dst) override;

   private:
    const std::shared_ptr<AidlManagedBuffer> kBuffer;
    const std::unique_ptr<AidlBufferTracker::Token> kToken;
};

// Base class used to create sample drivers for the NN HAL.  This class
// provides some implementation of the more common functions.
//
// Since these drivers simulate hardware, they must run the computations
// on the CPU.  An actual driver would not do that.
class SampleDriver : public aidl_hal::BnDevice {
   public:
    SampleDriver(const char* name,
                 const IOperationResolver* operationResolver = BuiltinOperationResolver::get())
        : mName(name),
          mOperationResolver(operationResolver),
          mBufferTracker(AidlBufferTracker::create()) {
        android::nn::initVLogMask();
    }
    ndk::ScopedAStatus allocate(const aidl_hal::BufferDesc& desc,
                                const std::vector<aidl_hal::IPreparedModelParcel>& preparedModels,
                                const std::vector<aidl_hal::BufferRole>& inputRoles,
                                const std::vector<aidl_hal::BufferRole>& outputRoles,
                                aidl_hal::DeviceBuffer* buffer) override;
    ndk::ScopedAStatus getNumberOfCacheFilesNeeded(
            aidl_hal::NumberOfCacheFiles* numberOfCacheFiles) override;
    ndk::ScopedAStatus getSupportedExtensions(
            std::vector<aidl_hal::Extension>* extensions) override;
    ndk::ScopedAStatus getType(aidl_hal::DeviceType* deviceType) override;
    ndk::ScopedAStatus getVersionString(std::string* version) override;
    ndk::ScopedAStatus prepareModel(
            const aidl_hal::Model& model, aidl_hal::ExecutionPreference preference,
            aidl_hal::Priority priority, int64_t deadlineNs,
            const std::vector<ndk::ScopedFileDescriptor>& modelCache,
            const std::vector<ndk::ScopedFileDescriptor>& dataCache,
            const std::vector<uint8_t>& token,
            const std::shared_ptr<aidl_hal::IPreparedModelCallback>& callback) override;
    ndk::ScopedAStatus prepareModelWithConfig(
            const aidl_hal::Model& model, const aidl_hal::PrepareModelConfig& config,
            const std::shared_ptr<aidl_hal::IPreparedModelCallback>& callback) override;
    ndk::ScopedAStatus prepareModelFromCache(
            int64_t deadlineNs, const std::vector<ndk::ScopedFileDescriptor>& modelCache,
            const std::vector<ndk::ScopedFileDescriptor>& dataCache,
            const std::vector<uint8_t>& token,
            const std::shared_ptr<aidl_hal::IPreparedModelCallback>& callback) override;

    CpuExecutor getExecutor() const { return CpuExecutor(mOperationResolver); }
    const std::shared_ptr<AidlBufferTracker>& getBufferTracker() const { return mBufferTracker; }

   protected:
    std::string mName;
    const IOperationResolver* mOperationResolver;
    const std::shared_ptr<AidlBufferTracker> mBufferTracker;
};

class SamplePreparedModel : public aidl_hal::BnPreparedModel {
   public:
    SamplePreparedModel(aidl_hal::Model&& model, const SampleDriver* driver,
                        aidl_hal::ExecutionPreference preference, uid_t userId,
                        aidl_hal::Priority priority)
        : mModel(std::move(model)),
          mDriver(driver),
          kPreference(preference),
          kUserId(userId),
          kPriority(priority) {
        (void)kUserId;
        (void)kPriority;
    }
    bool initialize();
    ndk::ScopedAStatus executeSynchronously(const aidl_hal::Request& request, bool measureTiming,
                                            int64_t deadlineNs, int64_t loopTimeoutDurationNs,
                                            aidl_hal::ExecutionResult* executionResult) override;
    ndk::ScopedAStatus executeFenced(const aidl_hal::Request& request,
                                     const std::vector<ndk::ScopedFileDescriptor>& waitFor,
                                     bool measureTiming, int64_t deadlineNs,
                                     int64_t loopTimeoutDurationNs, int64_t durationNs,
                                     aidl_hal::FencedExecutionResult* executionResult) override;
    ndk::ScopedAStatus executeSynchronouslyWithConfig(
            const aidl_hal::Request& request, const aidl_hal::ExecutionConfig& config,
            int64_t deadlineNs, aidl_hal::ExecutionResult* executionResult) override;
    ndk::ScopedAStatus executeFencedWithConfig(
            const aidl_hal::Request& request, const std::vector<ndk::ScopedFileDescriptor>& waitFor,
            const aidl_hal::ExecutionConfig& config, int64_t deadlineNs, int64_t durationNs,
            aidl_hal::FencedExecutionResult* executionResult) override;
    ndk::ScopedAStatus configureExecutionBurst(std::shared_ptr<aidl_hal::IBurst>* burst) override;
    ndk::ScopedAStatus createReusableExecution(
            const aidl_hal::Request& request, const aidl_hal::ExecutionConfig& config,
            std::shared_ptr<aidl_hal::IExecution>* execution) override;
    const aidl_hal::Model* getModel() const { return &mModel; }

   protected:
    aidl_hal::Model mModel;
    const SampleDriver* mDriver;
    std::vector<RunTimePoolInfo> mPoolInfos;
    const aidl_hal::ExecutionPreference kPreference;
    const uid_t kUserId;
    const aidl_hal::Priority kPriority;
};

class SampleFencedExecutionCallback : public aidl_hal::BnFencedExecutionCallback {
   public:
    SampleFencedExecutionCallback(aidl_hal::Timing timingSinceLaunch,
                                  aidl_hal::Timing timingAfterFence, aidl_hal::ErrorStatus error)
        : kTimingSinceLaunch(timingSinceLaunch),
          kTimingAfterFence(timingAfterFence),
          kErrorStatus(error) {}
    ndk::ScopedAStatus getExecutionInfo(aidl_hal::Timing* timingLaunched,
                                        aidl_hal::Timing* timingFenced,
                                        aidl_hal::ErrorStatus* errorStatus) override {
        *timingLaunched = kTimingSinceLaunch;
        *timingFenced = kTimingAfterFence;
        *errorStatus = kErrorStatus;
        return ndk::ScopedAStatus::ok();
    }

   private:
    const aidl_hal::Timing kTimingSinceLaunch;
    const aidl_hal::Timing kTimingAfterFence;
    const aidl_hal::ErrorStatus kErrorStatus;
};

class SampleBurst : public aidl_hal::BnBurst {
   public:
    // Precondition: preparedModel != nullptr
    explicit SampleBurst(std::shared_ptr<SamplePreparedModel> preparedModel);

    ndk::ScopedAStatus executeSynchronously(const aidl_hal::Request& request,
                                            const std::vector<int64_t>& memoryIdentifierTokens,
                                            bool measureTiming, int64_t deadlineNs,
                                            int64_t loopTimeoutDurationNs,
                                            aidl_hal::ExecutionResult* executionResult) override;
    ndk::ScopedAStatus executeSynchronouslyWithConfig(
            const aidl_hal::Request& request, const std::vector<int64_t>& memoryIdentifierTokens,
            const aidl_hal::ExecutionConfig& config, int64_t deadlineNs,
            aidl_hal::ExecutionResult* executionResult) override;
    ndk::ScopedAStatus releaseMemoryResource(int64_t memoryIdentifierToken) override;

   protected:
    std::atomic_flag mExecutionInFlight = ATOMIC_FLAG_INIT;
    const std::shared_ptr<SamplePreparedModel> kPreparedModel;
};

class SampleExecution : public aidl_hal::BnExecution {
   public:
    SampleExecution(std::shared_ptr<SamplePreparedModel> preparedModel, aidl_hal::Request request,
                    bool measureTiming, int64_t loopTimeoutDurationNs)
        : kPreparedModel(std::move(preparedModel)),
          kHalRequest(std::move(request)),
          kMeasureTiming(measureTiming),
          kLoopTimeoutDurationNs(loopTimeoutDurationNs) {
        CHECK(kPreparedModel != nullptr);
    }

    ndk::ScopedAStatus executeSynchronously(int64_t deadlineNs,
                                            aidl_hal::ExecutionResult* executionResult) override;

    ndk::ScopedAStatus executeFenced(const std::vector<ndk::ScopedFileDescriptor>& waitFor,
                                     int64_t deadlineNs, int64_t durationNs,
                                     aidl_hal::FencedExecutionResult* executionResult) override;

   private:
    std::atomic_flag mComputationInFlight = ATOMIC_FLAG_INIT;
    const std::shared_ptr<SamplePreparedModel> kPreparedModel;

    const aidl_hal::Request kHalRequest;
    const bool kMeasureTiming;
    const int64_t kLoopTimeoutDurationNs;
};

}  // namespace sample_driver_aidl
}  // namespace nn
}  // namespace android

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_DRIVER_SAMPLE_AIDL_SAMPLE_DRIVER_AIDL_H
