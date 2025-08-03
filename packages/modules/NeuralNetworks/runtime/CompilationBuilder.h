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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_COMPILATION_BUILDER_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_COMPILATION_BUILDER_H

#include <nnapi/Types.h>

#include <chrono>
#include <limits>
#include <memory>
#include <optional>
#include <set>
#include <string>
#include <utility>
#include <vector>

#include "ExecutionPlan.h"
#include "Manager.h"
#include "NeuralNetworks.h"

namespace android {
namespace nn {

class BurstBuilder;
class Device;
class ExecutionBuilder;
class ModelBuilder;

class CompilationBuilder {
   public:
    friend class ExecutionBuilder;  // TODO remove this

    // explicitDeviceList is true if the list of devices was provided explicitly
    // via the ANeuralNetworksModel_createForDevices API (which has certain
    // special semantics) and false otherwise.
    CompilationBuilder(const ModelBuilder* model,
                       const std::vector<std::shared_ptr<Device>>& devices,
                       bool explicitDeviceList = false);

    int setPreference(int32_t preference);

    int setCaching(const std::string& cacheDir, const uint8_t* token);
    // Dups the fds
    int setCachingFromFds(const int* modelCacheFds, const uint32_t numModelCacheFiles,
                          const int* dataCacheFds, const uint32_t numDataCacheFiles,
                          const uint8_t* token);

    int setPriority(int32_t priority);

    int setTimeoutDuration(uint64_t duration);

    int addExtensionAttribute(const char* extensionName, uint16_t attributeCodeWithinExtension,
                              const void* data, size_t length);

    int finish();

    int getPreferredMemoryAlignmentForInput(uint32_t index, uint32_t* alignment) const;
    int getPreferredMemoryPaddingForInput(uint32_t index, uint32_t* padding) const;
    int getPreferredMemoryAlignmentForOutput(uint32_t index, uint32_t* alignment) const;
    int getPreferredMemoryPaddingForOutput(uint32_t index, uint32_t* padding) const;

    int createExecution(ExecutionBuilder** execution);

    int createBurst(BurstBuilder** burst);

    const ModelBuilder* getModel() const { return mModel; }
    const std::vector<std::shared_ptr<Device>>& getDevices() const { return mDevices; }

    int forEachStepRoleOfInput(uint32_t index, const StepRoleCallback& callback) const;
    int forEachStepRoleOfOutput(uint32_t index, const StepRoleCallback& callback) const;

    bool createdWithExplicitDeviceList() const { return mExplicitDeviceList; }

    bool hasDynamicTemporaries() const { return mPlan.hasDynamicTemporaries(); }
    bool isCacheInfoProvided() const { return mIsCacheInfoProvided; }
    bool isFinished() const { return mFinished; }

    // These functions are solely intended for use by unit tests of the
    // partitioning algorithm.
    const ExecutionPlan& forTest_getExecutionPlan() const { return mPlan; }
    int forTest_setPartitioning(uint32_t partitioning);
    int forTest_failPartitioning(
            int resultCode);  // If not ANEURALNETWORKS_NO_ERROR, then simulate partitioning failure

    struct TelemetryInfo {
        uint64_t compilationTimeNanos = std::numeric_limits<uint64_t>::max();
        bool fallbackToCpuFromError = false;
    };
    const std::optional<TelemetryInfo>& getTelemetryInfo() const { return mTelemetryInfo; }

   private:
    const ModelBuilder* mModel;

    ExecutionPlan mPlan;

    // Whether the application prefers to go fast or use low power for this execution.
    int32_t mPreference = ANEURALNETWORKS_PREFER_FAST_SINGLE_ANSWER;

    // See class DeviceManager.  When CompilationBuilder is
    // instantiated, we capture partitioning from DeviceManager; but
    // we can override this later.
    uint32_t mPartitioning;

    // For testing purposes, simulate partitioning failure.
    int mFailPartitioning = ANEURALNETWORKS_NO_ERROR;

    // Once the compilation has been finished, we should not allow further
    // modifications to the compilation.
    bool mFinished = false;

    // The set of devices that the partitioning algorithm operates on when
    // finish() is called.
    std::vector<std::shared_ptr<Device>> mDevices;

    // mExplicitDeviceList is true if the list of devices was provided
    // explicitly via the ANeuralNetworksModel_createForDevices API (which has
    // certain special semantics) and false otherwise.
    bool mExplicitDeviceList;

    // Compilation caching information.
    CacheInfo mCacheInfo;
    uint8_t mToken[ANEURALNETWORKS_BYTE_SIZE_OF_CACHE_TOKEN];
    bool mIsCacheInfoProvided = false;

    // Compilation priority information.
    int32_t mPriority = ANEURALNETWORKS_PRIORITY_DEFAULT;

    // Amount of time to complete or abort the execution.
    std::optional<uint64_t> mTimeoutDuration;

    // Supplementary compilation info for Telemetry use
    std::optional<TelemetryInfo> mTelemetryInfo;

    // Vendor specific metadata
    std::vector<TokenValuePair> mMetadata;
};

}  // namespace nn
}  // namespace android

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_COMPILATION_BUILDER_H
