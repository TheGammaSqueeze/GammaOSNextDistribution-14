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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_MANAGER_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_MANAGER_H

#include <LegacyUtils.h>
#include <android-base/macros.h>
#include <nnapi/IBurst.h>
#include <nnapi/IDevice.h>
#include <nnapi/Types.h>

#include <functional>
#include <map>
#include <memory>
#include <string>
#include <tuple>
#include <unordered_set>
#include <utility>
#include <vector>

#include "ExecutionCallback.h"
#include "Memory.h"

namespace android {
namespace nn {

// Forward declaration
class Device;
class MetaModel;
class ModelArgumentInfo;

// A unified interface for a reusable execution with cached resources.
// This object provides no thread-safety guarantee. The caller must guarantee there is at most one
// call to RuntimeExecution::compute or RuntimeExecution::computeFenced on the same RuntimeExecution
// object in flight at a time.
class RuntimeExecution {
    DISALLOW_COPY_AND_ASSIGN(RuntimeExecution);

   public:
    RuntimeExecution() = default;
    virtual ~RuntimeExecution() = default;

    virtual std::tuple<int, std::vector<OutputShape>, Timing> compute(
            const SharedBurst& burstController, const OptionalTimePoint& deadline) const = 0;

    // The returned timing information is only valid if the callback is nullptr.
    // Returns error_code, sync_fence, callback and timing.
    virtual std::tuple<int, int, ExecuteFencedInfoCallback, Timing> computeFenced(
            const std::vector<int>& waitFor, const OptionalTimePoint& deadline,
            const OptionalDuration& timeoutDurationAfterFence) const = 0;
};

// A unified interface for actual driver prepared model as well as the CPU.
class RuntimePreparedModel {
    DISALLOW_COPY_AND_ASSIGN(RuntimePreparedModel);

   public:
    RuntimePreparedModel() = default;
    virtual ~RuntimePreparedModel() = default;

    virtual const Device* getDevice() const = 0;
    virtual SharedPreparedModel getInterface() const = 0;

    // Perform computation with given input/output argument info and memory pools.
    virtual std::tuple<int, std::vector<OutputShape>, Timing> execute(
            const std::vector<ModelArgumentInfo>& inputs,
            const std::vector<ModelArgumentInfo>& outputs,
            const std::vector<const RuntimeMemory*>& memories, const SharedBurst& burstController,
            MeasureTiming measure, const OptionalTimePoint& deadline,
            const OptionalDuration& loopTimeoutDuration,
            const std::vector<TokenValuePair>& metaData) const = 0;

    // Perform fenced computation with given input/output argument info and memory pools.
    // The returned timing information is only valid if the callback is nullptr.
    // Returns error_code, sync_fence, callback and timing.
    virtual std::tuple<int, int, ExecuteFencedInfoCallback, Timing> executeFenced(
            const std::vector<ModelArgumentInfo>& inputs,
            const std::vector<ModelArgumentInfo>& outputs,
            const std::vector<const RuntimeMemory*>& memories, const std::vector<int>& waitFor,
            MeasureTiming measure, const OptionalTimePoint& deadline,
            const OptionalDuration& loopTimeoutDuration,
            const OptionalDuration& timeoutDurationAfterFence,
            const std::vector<TokenValuePair>& metaData) const = 0;

    // Create a reusable execution with given input/output argument info and memory pools.
    virtual std::pair<int, std::shared_ptr<RuntimeExecution>> createReusableExecution(
            const std::vector<ModelArgumentInfo>& inputs,
            const std::vector<ModelArgumentInfo>& outputs,
            const std::vector<const RuntimeMemory*>& memories, MeasureTiming measure,
            const OptionalDuration& loopTimeoutDuration,
            const std::vector<TokenValuePair>& metaData) const = 0;

    virtual GeneralResult<SharedBurst> configureExecutionBurst() const = 0;

    virtual MemoryPreference getMemoryPreference() const = 0;
};

using ModelFactory = std::function<Model()>;

struct CacheHandles {
    std::vector<SharedHandle> modelCache;
    std::vector<SharedHandle> dataCache;
};

using CacheDir = std::string;

struct CacheInfo {
    std::variant<CacheDir, CacheHandles> variant;
};

// A unified interface for actual driver devices as well as the CPU
class Device {
    DISALLOW_COPY_AND_ASSIGN(Device);

   public:
    Device() = default;
    virtual ~Device() = default;

    // Introspection methods returning device information
    virtual const std::string& getName() const = 0;
    virtual const std::string& getVersionString() const = 0;
    virtual Version getFeatureLevel() const = 0;
    virtual int32_t getType() const = 0;
    virtual const std::vector<Extension>& getSupportedExtensions() const = 0;

    // See the MetaModel class in MetaModel.h for more details.
    virtual std::vector<bool> getSupportedOperations(const MetaModel& metaModel) const = 0;

    virtual const Capabilities& getCapabilities() const = 0;
    virtual Capabilities::PerformanceInfo getPerformance(OperandType type) const = 0;
    virtual Capabilities::PerformanceInfo getRelaxedFloat32toFloat16PerformanceScalar() const = 0;
    virtual Capabilities::PerformanceInfo getRelaxedFloat32toFloat16PerformanceTensor() const = 0;
    virtual Capabilities::PerformanceInfo getIfPerformance() const = 0;
    virtual Capabilities::PerformanceInfo getWhilePerformance() const = 0;
    virtual std::pair<uint32_t, uint32_t> getNumberOfCacheFilesNeeded() const = 0;
    virtual bool isCachingSupported() const = 0;
    virtual int wait() const = 0;

    virtual std::pair<int, std::shared_ptr<RuntimePreparedModel>> prepareModel(
            const ModelFactory& makeModel, ExecutionPreference preference, Priority priority,
            const OptionalTimePoint& deadline, const CacheInfo& cacheInfo,
            const std::optional<CacheToken>& maybeToken,
            const std::vector<TokenValuePair>& metaData,
            const std::vector<ExtensionNameAndPrefix>& extensionNameAndPrefix) const = 0;

    // The caller is responsible for making sure the MemoryDescriptor only contains
    // PreparedModels from the same Device.
    virtual std::pair<int, std::unique_ptr<RuntimeMemory>> allocate(const MemoryDescriptor& desc,
                                                                    OperandType type) const = 0;
};

// Manages the NN HAL devices.  Only one instance of this class will exist.
// Use get() to retrieve it.
class DeviceManager {
   public:
    const std::vector<std::shared_ptr<Device>>& getDrivers() const {
        if (mSetCpuOnly || mDebugNNCpuOnly) {
            return mDevicesCpuOnly;
        }
        return mDevices;
    }

    // Gets the runtime version corresponding to getServerFeatureLevelFlag (in ServerFlag.h).
    Version getRuntimeVersion() const { return mRuntimeVersion; }

    // Gets the runtime feature level corresponding to getServerFeatureLevelFlag (in ServerFlag.h).
    int64_t getRuntimeFeatureLevel() const;

    // Convert the internal Version level representation to the NDK representation.
    static int64_t versionToFeatureLevel(Version::Level versionLevel);

    // Returns whether platform telemetry is enabled.
    bool isPlatformTelemetryEnabled() const { return mIsPlatformTelemetryEnabled; }

    // For testing only:
    void setUseCpuOnly(bool useCpuOnly) { mSetCpuOnly = useCpuOnly; }
    bool getUseCpuOnly() const { return mSetCpuOnly; }

    bool syncExecCpu() const { return mSyncExecCpu; }
    bool syncExecRuntime() const { return mSyncExecRuntime; }

    // How to handle graph partitioning?
    // 0 - Don't do graph partitioning.
    // 1 - Do graph partitioning; but fall back to non-partitioned
    //     execution if there is a partitioning failure.
    // 2 - Do graph partitioning, and rely on it; there is no fallback.
    enum { kPartitioningNo = 0, kPartitioningWithFallback = 1, kPartitioningWithoutFallback = 2 };
    uint32_t getPartitioning() const { return mPartitioning; }
    static bool partitioningAllowsFallback(uint32_t partitioning) {
        return partitioning == kPartitioningWithFallback;
    }

    bool strictSlicing() const { return mStrictSlicing; }

    // Returns the singleton manager.
    static DeviceManager* get();

    // Returns the singleton Cpu device.
    static std::shared_ptr<Device> getCpuDevice();

    // The forTest_* functions below are solely intended for use by unit tests.

    // Returns all devices (ignores the cpu-only flags).
    std::vector<std::shared_ptr<Device>> forTest_getDevices() const { return mDevices; }

    // Sets the device list (does not affect cpu-only queries).
    void forTest_setDevices(std::vector<std::shared_ptr<Device>> devices) {
        mDevices = std::move(devices);
    }

    // Register a test device.
    void forTest_registerDevice(const SharedDevice& device) { registerDevice(device); }

    // Re-initialize the list of available devices.
    void forTest_reInitializeDeviceList() {
        mDevices.clear();
        mDevicesCpuOnly.clear();
        findAvailableDevices();
    }

    // Make a test device
    static std::shared_ptr<Device> forTest_makeDriverDevice(const SharedDevice& device);

    bool forTest_isCpuDevice(const ANeuralNetworksDevice* device) const {
        return reinterpret_cast<const Device*>(device) == getCpuDevice().get();
    }

   private:
    // Builds the list of available drivers and queries their capabilities.
    DeviceManager();

    // Adds a device for the manager to use.
    void registerDevice(const SharedDevice& device);

    void findAvailableDevices();

    // Runtime version corresponding to getServerFeatureLevelFlag (in ServerFlag.h).
    Version mRuntimeVersion;

    // Holds whether platform telemetry is enabled, as indicated by getServerTelemetryEnableFlag (in
    // ServerFlag.h).
    bool mIsPlatformTelemetryEnabled;

    // List of all the devices we discovered (including CpuDevice).
    std::vector<std::shared_ptr<Device>> mDevices;

    // We set this one to have CpuDevice only. To be used when m*CpuOnly is true.
    std::vector<std::shared_ptr<Device>> mDevicesCpuOnly;

    // If either of these is true, we'll ignore the drivers that are
    // on the device and run everything on the CPU.
    bool mSetCpuOnly = false;      // set by setUseCpuOnly()
    bool mDebugNNCpuOnly = false;  // derived from system property debug.nn.cpuonly

    // synchronous execution
    bool mSyncExecCpu = true;
    bool mSyncExecRuntime = false;

    static const uint32_t kPartitioningDefault = kPartitioningWithFallback;
    uint32_t mPartitioning = kPartitioningDefault;

    bool mStrictSlicing = false;
};

std::vector<SharedDevice> getDevices();

}  // namespace nn
}  // namespace android

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_MANAGER_H
