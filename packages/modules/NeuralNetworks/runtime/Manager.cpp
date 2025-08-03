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

#define LOG_TAG "Manager"

#include "Manager.h"

#include <CpuExecutor.h>
#include <LegacyUtils.h>
#include <MetaModel.h>
#include <Tracing.h>
#include <android-base/properties.h>
#include <nnapi/IBurst.h>
#include <nnapi/IDevice.h>
#include <nnapi/IExecution.h>
#include <nnapi/IPreparedModel.h>
#include <nnapi/SharedMemory.h>
#include <nnapi/TypeUtils.h>
#include <nnapi/Types.h>
#include <nnapi/Validation.h>

#include <algorithm>
#include <functional>
#include <iterator>
#include <map>
#include <memory>
#include <regex>
#include <set>
#include <string>
#include <tuple>
#include <utility>
#include <vector>

#include "ExecutionCallback.h"
#include "Memory.h"
#include "ModelArgumentInfo.h"
#include "ServerFlag.h"
#include "TypeManager.h"

#ifndef NN_COMPATIBILITY_LIBRARY_BUILD
#include <build/version.h>
#include <cutils/native_handle.h>
#include <nnapi/hal/1.3/Buffer.h>
#include <nnapi/hal/Service.h>
#endif  // NN_COMPATIBILITY_LIBRARY_BUILD

#ifdef NN_EXPERIMENTAL_FEATURE
#include "NeuralNetworksExperimentalFeatures.h"
#endif  // NN_EXPERIMENTAL_FEATURE

namespace android {
namespace nn {
namespace {

Version getRuntimeFeatureLevelVersionHelper() {
#if defined(NN_EXPERIMENTAL_FEATURE) && defined(NN_COMPATIBILITY_LIBRARY_BUILD)
#error "NN_EXPERIMENTAL_FEATURE is not supported when NN_COMPATIBILITY_LIBRARY_BUILD is defined"
#elif defined(NN_EXPERIMENTAL_FEATURE)
    auto version = kVersionFeatureLevelExperimental;
    // Enable "runtimeOnlyFeatures" to indicate that the runtime feature level version supports
    // features that are only available in the runtime.
    version.runtimeOnlyFeatures = true;
#elif defined(NN_COMPATIBILITY_LIBRARY_BUILD)
    auto version = serverFeatureLevelToVersion(kMaxFeatureLevelNum);
#else   // !defined(NN_COMPATIBILITY_LIBRARY_BUILD) && !defined(NN_EXPERIMENTAL_FEATURE)
    auto version = serverFeatureLevelToVersion(getServerFeatureLevelFlag());
    // Enable "runtimeOnlyFeatures" to indicate that the runtime feature level version supports
    // features that are only available in the runtime.
    version.runtimeOnlyFeatures = true;
#endif  // !defined(NN_COMPATIBILITY_LIBRARY_BUILD) && !defined(NN_EXPERIMENTAL_FEATURE)
    return version;
}

Version getRuntimeFeatureLevelVersion() {
    static const Version version = getRuntimeFeatureLevelVersionHelper();
    return version;
}

bool getWhetherPlatformTelemetryIsEnabled() {
#if !defined(NN_COMPATIBILITY_LIBRARY_BUILD) && !defined(NN_EXPERIMENTAL_FEATURE)
    // b/287186978, force enable telemetry on the platform NNAPI
    // return getServerTelemetryEnableFlag();
    return true;
#else   // !defined(NN_COMPATIBILITY_LIBRARY_BUILD) && !defined(NN_EXPERIMENTAL_FEATURE)
    return false;
#endif  // !defined(NN_COMPATIBILITY_LIBRARY_BUILD) && !defined(NN_EXPERIMENTAL_FEATURE)
}

}  // namespace

// A Device with actual underlying driver
class DriverDevice : public Device {
   public:
    // Create a DriverDevice from a name and a DeviceFactory function.
    // Returns nullptr on failure.
    static std::shared_ptr<DriverDevice> create(SharedDevice device);

    // Prefer using DriverDevice::create
    explicit DriverDevice(SharedDevice device);

    const std::string& getName() const override { return kInterface->getName(); }
    const std::string& getVersionString() const override { return kInterface->getVersionString(); }
    Version getFeatureLevel() const override { return kInterface->getFeatureLevel(); }
    int32_t getType() const override { return static_cast<int32_t>(kInterface->getType()); }
    const std::vector<Extension>& getSupportedExtensions() const override {
        return kInterface->getSupportedExtensions();
    }
    std::vector<bool> getSupportedOperations(const MetaModel& metaModel) const override;
    const Capabilities& getCapabilities() const override { return kInterface->getCapabilities(); }
    Capabilities::PerformanceInfo getPerformance(OperandType type) const override {
        return getCapabilities().operandPerformance.lookup(type);
    }
    Capabilities::PerformanceInfo getRelaxedFloat32toFloat16PerformanceScalar() const override {
        return getCapabilities().relaxedFloat32toFloat16PerformanceScalar;
    }
    Capabilities::PerformanceInfo getRelaxedFloat32toFloat16PerformanceTensor() const override {
        return getCapabilities().relaxedFloat32toFloat16PerformanceTensor;
    }
    Capabilities::PerformanceInfo getIfPerformance() const override {
        return getCapabilities().ifPerformance;
    }
    Capabilities::PerformanceInfo getWhilePerformance() const override {
        return getCapabilities().whilePerformance;
    }
    std::pair<uint32_t, uint32_t> getNumberOfCacheFilesNeeded() const override {
        return kInterface->getNumberOfCacheFilesNeeded();
    }
    bool isCachingSupported() const override {
        // Caching is supported if either of numModelCache or numDataCache is greater than 0.
        const auto [numModelCacheFiles, numDataCacheFiles] = getNumberOfCacheFilesNeeded();
        return numModelCacheFiles > 0 || numDataCacheFiles > 0;
    }
    int wait() const override {
        auto result = kInterface->wait();
        if (!result.ok()) {
            LOG(ERROR) << "DriverDevice::wait error: " << result.error().message;
            return convertErrorStatusToResultCode(result.error().code);
        }
        return ANEURALNETWORKS_NO_ERROR;
    }

    std::pair<int, std::shared_ptr<RuntimePreparedModel>> prepareModel(
            const ModelFactory& makeModel, ExecutionPreference preference, Priority priority,
            const OptionalTimePoint& deadline, const CacheInfo& cacheInfo,
            const std::optional<CacheToken>& maybeToken,
            const std::vector<TokenValuePair>& metaData,
            const std::vector<ExtensionNameAndPrefix>& extensionNameAndPrefix) const override;

    std::pair<int, std::unique_ptr<RuntimeMemory>> allocate(const MemoryDescriptor& desc,
                                                            OperandType) const override;

   private:
    const SharedDevice kInterface;

    GeneralResult<std::vector<bool>> getSupportedOperationsImpl(const MetaModel& metaModel) const;
    GeneralResult<SharedPreparedModel> prepareModelFromCacheInternal(
            const OptionalTimePoint& deadline, const CacheInfo& cacheInfo,
            const CacheToken& token) const;

#ifdef NN_DEBUGGABLE
    // For debugging: behavior of IDevice::getSupportedOperations for SampleDriver.
    // 0 - all operations reported by IDevice::getSupportedOperations() supported
    // 1 - some operations reported by IDevice::getSupportedOperations() supported
    uint32_t mSupported = 0;
#endif  // NN_DEBUGGABLE
};

// A RuntimePreparedModel with underlying IPreparedModel instance return by actual driver.
class DriverPreparedModel : public RuntimePreparedModel {
   public:
    DriverPreparedModel(const Device* device, const SharedPreparedModel& preparedModel)
        : mDevice(device), mPreparedModel(preparedModel) {
        CHECK(mDevice != nullptr);
        CHECK(mPreparedModel != nullptr);
    }

    const Device* getDevice() const override { return mDevice; }
    SharedPreparedModel getInterface() const override { return mPreparedModel; }

    std::tuple<int, std::vector<OutputShape>, Timing> execute(
            const std::vector<ModelArgumentInfo>& inputs,
            const std::vector<ModelArgumentInfo>& outputs,
            const std::vector<const RuntimeMemory*>& memories, const SharedBurst& burstController,
            MeasureTiming measure, const OptionalTimePoint& deadline,
            const OptionalDuration& loopTimeoutDuration,
            const std::vector<TokenValuePair>& metaData) const override;

    std::tuple<int, int, ExecuteFencedInfoCallback, Timing> executeFenced(
            const std::vector<ModelArgumentInfo>& inputs,
            const std::vector<ModelArgumentInfo>& outputs,
            const std::vector<const RuntimeMemory*>& memories, const std::vector<int>& waitFor,
            MeasureTiming measure, const OptionalTimePoint& deadline,
            const OptionalDuration& loopTimeoutDuration,
            const OptionalDuration& timeoutDurationAfterFence,
            const std::vector<TokenValuePair>& metaData) const override;

    std::pair<int, std::shared_ptr<RuntimeExecution>> createReusableExecution(
            const std::vector<ModelArgumentInfo>& inputs,
            const std::vector<ModelArgumentInfo>& outputs,
            const std::vector<const RuntimeMemory*>& memories, MeasureTiming measure,
            const OptionalDuration& loopTimeoutDuration,
            const std::vector<TokenValuePair>& metaData) const override;

    GeneralResult<SharedBurst> configureExecutionBurst() const override {
        return mPreparedModel->configureExecutionBurst();
    }

    MemoryPreference getMemoryPreference() const override {
        if (isCompliantVersion(kVersionFeatureLevel5, mDevice->getFeatureLevel())) {
            return {kDefaultRequestMemoryAlignment, kDefaultRequestMemoryPadding};
        } else {
            // We are not able to pass memory padding information to HIDL drivers, so return the
            // minimum padding.
            return {kDefaultRequestMemoryAlignment, kMinMemoryPadding};
        }
    }

   private:
    const Device* mDevice;
    const SharedPreparedModel mPreparedModel;
};

class DriverExecution : public RuntimeExecution {
   public:
    DriverExecution(SharedExecution execution, Request request,
                    std::vector<const RuntimeMemory*> memories, MeasureTiming measure,
                    OptionalDuration loopTimeoutDuration, Version deviceFeatureLevel,
                    const std::vector<TokenValuePair>& metaData)
        : kExecution(std::move(execution)),
          kRequest(std::move(request)),
          kMemories(std::move(memories)),
          kMeasure(measure),
          kLoopTimeoutDuration(std::move(loopTimeoutDuration)),
          kDeviceFeatureLevel(deviceFeatureLevel),
          kMetaData(metaData) {
        CHECK(kExecution != nullptr);
    }

    std::tuple<int, std::vector<OutputShape>, Timing> compute(
            const SharedBurst& burstController, const OptionalTimePoint& deadline) const override;

    std::tuple<int, int, ExecuteFencedInfoCallback, Timing> computeFenced(
            const std::vector<int>& waitFor, const OptionalTimePoint& deadline,
            const OptionalDuration& timeoutDurationAfterFence) const override;

   private:
    const SharedExecution kExecution;

    // For burst execution.
    const Request kRequest;
    const std::vector<const RuntimeMemory*> kMemories;
    const MeasureTiming kMeasure;
    const OptionalDuration kLoopTimeoutDuration;
    mutable std::map<const IBurst*, SharedExecution> mCachedBurstExecutions;

    // For fenced execution.
    const Version kDeviceFeatureLevel;

    // Execution metadata.
    std::vector<TokenValuePair> kMetaData;
};

DriverDevice::DriverDevice(SharedDevice device) : kInterface(std::move(device)) {
    CHECK(kInterface != nullptr);
#ifdef NN_DEBUGGABLE
    static const char samplePrefix[] = "sample";
    if (getName().substr(0, sizeof(samplePrefix) - 1) == samplePrefix) {
        mSupported = getProp("debug.nn.sample.supported");
    }
#endif  // NN_DEBUGGABLE
}

std::shared_ptr<DriverDevice> DriverDevice::create(SharedDevice device) {
    if (device == nullptr) {
        LOG(ERROR) << "DriverDevice::create called with nullptr";
        return nullptr;
    }

    return std::make_shared<DriverDevice>(std::move(device));
}

int64_t DeviceManager::versionToFeatureLevel(Version::Level versionLevel) {
    switch (versionLevel) {
        case Version::Level::FEATURE_LEVEL_1:
            return ANEURALNETWORKS_FEATURE_LEVEL_1;
        case Version::Level::FEATURE_LEVEL_2:
            return ANEURALNETWORKS_FEATURE_LEVEL_2;
        case Version::Level::FEATURE_LEVEL_3:
            return ANEURALNETWORKS_FEATURE_LEVEL_3;
        case Version::Level::FEATURE_LEVEL_4:
            return ANEURALNETWORKS_FEATURE_LEVEL_4;
        case Version::Level::FEATURE_LEVEL_5:
            return ANEURALNETWORKS_FEATURE_LEVEL_5;
        case Version::Level::FEATURE_LEVEL_6:
            return ANEURALNETWORKS_FEATURE_LEVEL_6;
        case Version::Level::FEATURE_LEVEL_7:
            return ANEURALNETWORKS_FEATURE_LEVEL_7;
        case Version::Level::FEATURE_LEVEL_8:
            return ANEURALNETWORKS_FEATURE_LEVEL_8;
#ifdef NN_EXPERIMENTAL_FEATURE
        case Version::Level::FEATURE_LEVEL_EXPERIMENTAL:
            return ANEURALNETWORKS_FEATURE_LEVEL_EXPERIMENTAL;
#endif  // NN_EXPERIMENTAL_FEATURE
    }
    LOG(FATAL) << "Unrecognized version " << versionLevel;
    return -1;
}

GeneralResult<std::vector<bool>> DriverDevice::getSupportedOperationsImpl(
        const MetaModel& metaModel) const {
    const auto featureLevel = kInterface->getFeatureLevel();
    const auto slice = metaModel.getSlice(featureLevel);
    if (!slice.has_value()) {
        return NN_ERROR() << "getSlice(" << featureLevel << ") failed";
    }

    const auto& [sliceModel, slicedModelOperationIndexToModelOperationIndex] = *slice;
    const std::vector<bool> supported = NN_TRY(kInterface->getSupportedOperations(sliceModel));
    const uint32_t slicedOperationCount = sliceModel.main.operations.size();
    if (supported.size() != slicedOperationCount) {
        return NN_ERROR() << "IDevice::getSupportedOperations returned a vector of length "
                          << supported.size() << " when expecting " << slicedOperationCount;
    }

    const Model& model = metaModel.getModel();
    const uint32_t operationCount = model.main.operations.size();
    std::vector<bool> remappedSupported(operationCount, false);
    for (size_t i = 0; i < supported.size(); ++i) {
        if (supported[i]) {
            remappedSupported[slicedModelOperationIndexToModelOperationIndex(i)] = true;
        }
    }
    return remappedSupported;
}

std::vector<bool> DriverDevice::getSupportedOperations(const MetaModel& metaModel) const {
    const Model& model = metaModel.getModel();

    auto result = getSupportedOperationsImpl(metaModel);
    if (!result.ok()) {
        LOG(ERROR) << "getSupportedOperations failed with code " << result.error().code << ": "
                   << result.error().message;
        // Set the supported operation vectors to all false, so we won't use this driver.
        return std::vector<bool>(model.main.operations.size(), false);
    }

    std::vector<bool>& supportedOperations = result.value();
#ifdef NN_DEBUGGABLE
    if (mSupported != 1) {
        return supportedOperations;
    }

    const uint32_t baseAccumulator = std::hash<std::string>{}(getName());
    for (size_t operationIndex = 0; operationIndex < supportedOperations.size(); operationIndex++) {
        if (!supportedOperations[operationIndex]) {
            continue;
        }

        uint32_t accumulator = baseAccumulator;
        const Operation& operation = model.main.operations[operationIndex];
        accumulator ^= static_cast<uint32_t>(operation.type);
        auto accumulateOperands = [&model, &accumulator](const std::vector<uint32_t>& operands) {
            for (uint32_t operandIndex : operands) {
                const Operand& operand = model.main.operands[operandIndex];
                accumulator ^= static_cast<uint32_t>(operand.type);
                accumulator ^= operand.dimensions.size();
                for (const Dimension& dimension : operand.dimensions) {
                    accumulator ^= dimension;
                    if (operand.lifetime == Operand::LifeTime::CONSTANT_COPY ||
                        operand.lifetime == Operand::LifeTime::CONSTANT_REFERENCE ||
                        operand.lifetime == Operand::LifeTime::POINTER) {
                        accumulator ^= 1;
                    }
                }
            }
        };
        accumulateOperands(operation.inputs);
        accumulateOperands(operation.outputs);
        if (accumulator & 1) {
            supportedOperations[operationIndex] = false;
        }
    }
#endif  // NN_DEBUGGABLE

    return supportedOperations;
}

// Opens a cache file for reading and writing and returns a shared handle.
static GeneralResult<SharedHandle> createCacheHandle(const std::string& filename,
                                                     bool createIfNotExist) {
    auto fd = base::unique_fd(open(filename.c_str(), createIfNotExist ? (O_RDWR | O_CREAT) : O_RDWR,
                                   S_IRUSR | S_IWUSR));
    if (!fd.ok()) {
        return NN_ERROR(ErrorStatus::GENERAL_FAILURE)
               << "Failed to " << (createIfNotExist ? "open or create" : "open") << " cache file "
               << filename;
    }
    return std::make_shared<const Handle>(std::move(fd));
}

// Opens a list of cache files and returns a vector of shared handles. The files
// are always opened with both read and write permissions.
static GeneralResult<std::vector<SharedHandle>> createCacheHandleVec(
        uint32_t numCacheFiles, const std::string& baseFilename, bool createIfNotExist) {
    CHECK(numCacheFiles <= kMaxNumberOfCacheFiles);
    std::vector<SharedHandle> handles;
    handles.reserve(numCacheFiles);
    for (uint32_t i = 0; i < numCacheFiles; i++) {
        std::string filename = baseFilename + std::to_string(i);
        VLOG(COMPILATION) << "Cache " << i << ": " << filename;
        handles.push_back(NN_TRY(createCacheHandle(filename, createIfNotExist)));
    }
    return handles;
}

// Maps a token to cache file names and returns a pair of vectors of shared
// handles to the opened files.
static GeneralResult<CacheHandles> getCacheHandles(
        const CacheInfo& cacheInfo, const CacheToken& token,
        const std::pair<uint32_t, uint32_t>& numCacheFiles, bool createIfNotExist) {
    if (const auto* cacheHandles = std::get_if<CacheHandles>(&cacheInfo.variant)) {
        if (cacheHandles->modelCache.size() != numCacheFiles.first) {
            return NN_ERROR(ErrorStatus::GENERAL_FAILURE)
                   << "Expected " << numCacheFiles.first << " model cache handles, got "
                   << cacheHandles->modelCache.size();
        }
        if (cacheHandles->dataCache.size() != numCacheFiles.second) {
            return NN_ERROR(ErrorStatus::GENERAL_FAILURE)
                   << "Expected " << numCacheFiles.second << " data cache handles, got "
                   << cacheHandles->dataCache.size();
        }
        return *cacheHandles;
    }

    // The filename includes kByteSizeOfCacheToken * 2 characters for token,
    // and 1 character for model/data cache identifier.
    std::string filename(kByteSizeOfCacheToken * 2 + 1, '0');
    for (uint32_t i = 0; i < kByteSizeOfCacheToken; i++) {
        filename[i * 2] = 'A' + (token[i] & 0x0F);
        filename[i * 2 + 1] = 'A' + (token[i] >> 4);
    }

    const auto& cacheDir = std::get<CacheDir>(cacheInfo.variant);
    CHECK(cacheDir.empty() || cacheDir.back() == '/');
    std::string cacheFileName = cacheDir + filename;
    const uint32_t cacheTypeIdentifierIndex = cacheDir.size() + kByteSizeOfCacheToken * 2;

    cacheFileName[cacheTypeIdentifierIndex] = '1';
    std::vector<SharedHandle> modelCache =
            NN_TRY(createCacheHandleVec(numCacheFiles.first, cacheFileName, createIfNotExist));

    cacheFileName[cacheTypeIdentifierIndex] = '2';
    std::vector<SharedHandle> dataCache =
            NN_TRY(createCacheHandleVec(numCacheFiles.second, cacheFileName, createIfNotExist));

    return CacheHandles{
            .modelCache = std::move(modelCache),
            .dataCache = std::move(dataCache),
    };
}

GeneralResult<SharedPreparedModel> DriverDevice::prepareModelFromCacheInternal(
        const OptionalTimePoint& deadline, const CacheInfo& cacheInfo,
        const CacheToken& token) const {
    // Get cache files if they exist, otherwise return from the function early.
    auto cache = NN_TRY(getCacheHandles(cacheInfo, token, kInterface->getNumberOfCacheFilesNeeded(),
                                        /*createIfNotExist=*/false));
    return kInterface->prepareModelFromCache(deadline, cache.modelCache, cache.dataCache, token);
}

std::pair<int, std::shared_ptr<RuntimePreparedModel>> DriverDevice::prepareModel(
        const ModelFactory& makeModel, ExecutionPreference preference, Priority priority,
        const OptionalTimePoint& deadline, const CacheInfo& cacheInfo,
        const std::optional<CacheToken>& maybeToken, const std::vector<TokenValuePair>& metaData,
        const std::vector<ExtensionNameAndPrefix>& extensionNameAndPrefix) const {
    // Attempt to compile from cache if token is present.
    if (maybeToken.has_value()) {
        auto result = prepareModelFromCacheInternal(deadline, cacheInfo, *maybeToken);
        if (result.has_value()) {
            LOG(INFO) << "prepareModelFromCache: successfully prepared model from cache";
            return {ANEURALNETWORKS_NO_ERROR,
                    std::make_shared<DriverPreparedModel>(this, std::move(result).value())};
        } else {
            LOG(ERROR) << "prepareModelFromCache failure (" << result.error().code
                       << "): " << result.error().message;
        }
    }

    // Get cache files if they exist, otherwise create them.
    CacheHandles cache;
    if (maybeToken.has_value()) {
        auto result =
                getCacheHandles(cacheInfo, *maybeToken, kInterface->getNumberOfCacheFilesNeeded(),
                                /*createIfNotExist=*/true);
        if (result.has_value()) {
            cache = std::move(result).value();
        } else {
            LOG(ERROR) << "getCacheHandles failure (" << result.error().code
                       << "): " << result.error().message;
        }
    }

    // Get the token if it exists, otherwise get a null token.
    static constexpr CacheToken kNullToken = {};
    const CacheToken token = maybeToken.value_or(kNullToken);

    // Fallback to full compilation (possibly with token) if
    // prepareModelFromCache could not be used or failed.
    const Model model = makeModel();
    auto result =
            kInterface->prepareModel(model, preference, priority, deadline, cache.modelCache,
                                     cache.dataCache, token, metaData, extensionNameAndPrefix);
    if (!result.ok()) {
        LOG(ERROR) << "IDevice::prepareModel() error: " << result.error().message;
        return {convertErrorStatusToResultCode(result.error().code), nullptr};
    }
    SharedPreparedModel preparedModel = std::move(result).value();
    CHECK(preparedModel != nullptr)
            << "IDevice::prepareModel() returned nullptr without error code";
    return {ANEURALNETWORKS_NO_ERROR,
            std::make_shared<DriverPreparedModel>(this, std::move(preparedModel))};
}

std::pair<int, std::unique_ptr<RuntimeMemory>> DriverDevice::allocate(const MemoryDescriptor& desc,
                                                                      OperandType) const {
    const BufferDesc bufferDesc = {.dimensions = desc.dimensions};
    std::vector<SharedPreparedModel> preparedModels(desc.preparedModels.size());
    std::transform(desc.preparedModels.begin(), desc.preparedModels.end(), preparedModels.begin(),
                   [](const auto* preparedModel) {
                       const auto versionedPreparedModel = preparedModel->getInterface();
                       CHECK(versionedPreparedModel != nullptr);
                       return versionedPreparedModel;
                   });
    auto result =
            kInterface->allocate(bufferDesc, preparedModels, desc.inputRoles, desc.outputRoles);
    if (!result.ok()) {
        LOG(ERROR) << "DriverDevice::allocate -- memory allocation on device " << getName()
                   << " failed!";
        return {convertErrorStatusToResultCode(result.error().code), nullptr};
    }
    return MemoryFromDevice::create(std::move(result).value());
}

static Request createDriverRequest(const std::vector<ModelArgumentInfo>& inputs,
                                   const std::vector<ModelArgumentInfo>& outputs,
                                   const std::vector<const RuntimeMemory*>& memories) {
    Request request;
    request.inputs.reserve(inputs.size());
    std::transform(inputs.begin(), inputs.end(), std::back_inserter(request.inputs),
                   [](const auto& input) { return input.createRequestArgument(); });
    request.outputs.reserve(outputs.size());
    std::transform(outputs.begin(), outputs.end(), std::back_inserter(request.outputs),
                   [](const auto& output) { return output.createRequestArgument(); });
    request.pools.reserve(memories.size());
    std::transform(memories.begin(), memories.end(), std::back_inserter(request.pools),
                   [](const RuntimeMemory* memory) { return memory->getMemoryPool(); });
    return request;
}

// Perform computation on an actual device driver.
//
// Because HIDL cannot take raw pointers, two separate memory pools will be allocated for inputs and
// outputs specified by pointers. The input pointer data will be copied to the input pool prior to
// execution, and the output pointer data will be copied out from the output pool after the
// execution.
std::tuple<int, std::vector<OutputShape>, Timing> DriverPreparedModel::execute(
        const std::vector<ModelArgumentInfo>& inputs, const std::vector<ModelArgumentInfo>& outputs,
        const std::vector<const RuntimeMemory*>& memories, const SharedBurst& burstController,
        MeasureTiming measure, const OptionalTimePoint& deadline,
        const OptionalDuration& loopTimeoutDuration,
        const std::vector<TokenValuePair>& metaData) const {
    NNTRACE_RT(NNTRACE_PHASE_INPUTS_AND_OUTPUTS, "DriverPreparedModel::execute");

    auto request = createDriverRequest(inputs, outputs, memories);

    NNTRACE_RT_SWITCH(NNTRACE_PHASE_EXECUTION, "DriverPreparedModel::execute::execute");

    ExecutionResult<std::pair<std::vector<OutputShape>, Timing>> result;

    // compute using burst if present, otherwise compute from IPreparedModel
    const bool burstCompute = (burstController != nullptr);
    if (burstCompute) {
        for (const RuntimeMemory* memory : memories) {
            const auto pool = memory->getMemoryPool();
            if (const auto* maybeMemory = std::get_if<SharedMemory>(&pool)) {
                auto cacheHold = burstController->cacheMemory(*maybeMemory);
                memory->hold(cacheHold);
            }
        }

        VLOG(EXECUTION) << "Before burstController->execute() " << SHOW_IF_DEBUG(request);
        result = burstController->execute(request, measure, deadline, loopTimeoutDuration, metaData,
                                          TypeManager::get()->getExtensionNameAndPrefix(metaData));
    } else {
        result = mPreparedModel->execute(request, measure, deadline, loopTimeoutDuration, metaData,
                                         TypeManager::get()->getExtensionNameAndPrefix(metaData));
    }

    int n = ANEURALNETWORKS_OP_FAILED;
    std::vector<OutputShape> outputShapes;
    Timing timing;

    if (result.ok()) {
        n = ANEURALNETWORKS_NO_ERROR;
        std::tie(outputShapes, timing) = std::move(result).value();
    } else {
        auto [message, code, returnedOutputShapes] = std::move(result).error();
        VLOG(EXECUTION) << "**Execution failed** (ResultCode = " << n << ")";
        LOG(ERROR) << (burstCompute ? "IBurst" : "IPreparedModel")
                   << "::execute(...) error: " << message;
        n = convertErrorStatusToResultCode(code);
        if (code == ErrorStatus::OUTPUT_INSUFFICIENT_SIZE) {
            outputShapes = std::move(returnedOutputShapes);
        }
        return {n, std::move(outputShapes), timing};
    }

    VLOG(EXECUTION) << "DriverPreparedModel::execute completed";
    return {ANEURALNETWORKS_NO_ERROR, std::move(outputShapes), timing};
}

std::tuple<int, int, ExecuteFencedInfoCallback, Timing> DriverPreparedModel::executeFenced(
        const std::vector<ModelArgumentInfo>& inputs, const std::vector<ModelArgumentInfo>& outputs,
        const std::vector<const RuntimeMemory*>& memories, const std::vector<int>& waitFor,
        MeasureTiming measure, const OptionalTimePoint& deadline,
        const OptionalDuration& loopTimeoutDuration,
        const OptionalDuration& timeoutDurationAfterFence,
        const std::vector<TokenValuePair>& metaData) const {
    NNTRACE_RT(NNTRACE_PHASE_INPUTS_AND_OUTPUTS, "DriverPreparedModel::executeFenced");
    CHECK(std::all_of(waitFor.begin(), waitFor.end(), [](int fd) { return fd >= 0; }));

    auto request = createDriverRequest(inputs, outputs, memories);

    NNTRACE_RT_SWITCH(NNTRACE_PHASE_EXECUTION, "DriverPreparedModel::executeFenced");

    std::vector<SyncFence> waitForHandles;
    waitForHandles.reserve(waitFor.size());
    for (int fd : waitFor) {
        int dupFd = dup(fd);
        if (dupFd < 0) {
            LOG(ERROR) << "Unable to dup the file descriptor";
            return {ANEURALNETWORKS_OP_FAILED, -1, nullptr, {}};
        }
        waitForHandles.push_back(SyncFence::create(base::unique_fd(dupFd)));
    }

    SyncFence syncFence = SyncFence::createAsSignaled();
    ExecuteFencedInfoCallback executeFencedInfoCallback = nullptr;
    Timing timing = {};
    if (isCompliantVersion(kHalVersionV1_3ToApi.canonical, mDevice->getFeatureLevel())) {
        auto result = mPreparedModel->executeFenced(
                request, waitForHandles, measure, deadline, loopTimeoutDuration,
                timeoutDurationAfterFence, metaData,
                TypeManager::get()->getExtensionNameAndPrefix(metaData));
        if (!result.ok()) {
            LOG(ERROR) << "IPreparedModel::executeFenced() error: " << result.error().message;
            VLOG(EXECUTION) << "**executeFenced failed**";
            return {convertErrorStatusToResultCode(result.error().code), -1, nullptr, {}};
        }
        std::tie(syncFence, executeFencedInfoCallback) = std::move(result).value();
    } else {
        // Fallback to synchronous execution if executeFenced is not supported.
        // First wait for all sync fences to be ready.
        LOG(INFO) << "No drivers able to handle sync fences, falling back to regular execution";
        for (const auto& fence : waitForHandles) {
            if (!fence.hasFd() || fence.getFd() < 0) {
                return {ANEURALNETWORKS_BAD_DATA, -1, nullptr, {}};
            }
            auto r = fence.syncWait({/* no timeout */});
            if (r != SyncFence::FenceState::SIGNALED) {
                LOG(ERROR) << "syncWait failed, fd: " << fence.getFd() << ", state: " << r;
                return {ANEURALNETWORKS_OP_FAILED, -1, nullptr, {}};
            }
        }
        auto result =
                mPreparedModel->execute(request, measure, deadline, loopTimeoutDuration, metaData,
                                        TypeManager::get()->getExtensionNameAndPrefix(metaData));
        if (!result.ok()) {
            LOG(ERROR) << "IPreparedModel::execute() error: " << result.error().message;
            return {convertErrorStatusToResultCode(result.error().code), -1, nullptr, {}};
        }
        std::tie(std::ignore, timing) = result.value();
    }

    int syncFenceFd = -1;
    if (syncFence.hasFd()) {
        syncFenceFd = dup(syncFence.getFd());
        if (syncFenceFd < 0) {
            LOG(ERROR) << "Failed to dup the file descriptor";
            return {ANEURALNETWORKS_OP_FAILED, -1, nullptr, timing};
        }
    }

    VLOG(EXECUTION) << "DriverPreparedModel::executeFenced completed";
    return {ANEURALNETWORKS_NO_ERROR, syncFenceFd, executeFencedInfoCallback, timing};
}

std::pair<int, std::shared_ptr<RuntimeExecution>> DriverPreparedModel::createReusableExecution(
        const std::vector<ModelArgumentInfo>& inputs, const std::vector<ModelArgumentInfo>& outputs,
        const std::vector<const RuntimeMemory*>& memories, MeasureTiming measure,
        const OptionalDuration& loopTimeoutDuration,
        const std::vector<TokenValuePair>& metaData) const {
    NNTRACE_RT(NNTRACE_PHASE_INPUTS_AND_OUTPUTS, "DriverPreparedModel::createReusableExecution");

    auto request = createDriverRequest(inputs, outputs, memories);
    auto result = mPreparedModel->createReusableExecution(
            request, measure, loopTimeoutDuration, metaData,
            TypeManager::get()->getExtensionNameAndPrefix(metaData));
    if (!result.ok()) {
        LOG(ERROR) << "IPreparedModel::createReusableExecution() error: " << result.error().message;
        const int n = convertErrorStatusToResultCode(result.error().code);
        return {n, nullptr};
    }
    auto execution = std::make_shared<DriverExecution>(
            std::move(result).value(), std::move(request), memories, measure, loopTimeoutDuration,
            mDevice->getFeatureLevel(), metaData);
    return {ANEURALNETWORKS_NO_ERROR, std::move(execution)};
}

std::tuple<int, std::vector<OutputShape>, Timing> DriverExecution::compute(
        const SharedBurst& burstController, const OptionalTimePoint& deadline) const {
    NNTRACE_RT(NNTRACE_PHASE_EXECUTION, "DriverExecution::compute");

    // compute using burst if present, otherwise compute from IPreparedModel
    SharedExecution execution;
    const bool burstCompute = (burstController != nullptr);
    if (burstCompute) {
        // create a reusable burst execution if the controller is not seen before
        auto burstExecution = mCachedBurstExecutions.find(burstController.get());
        if (burstExecution == mCachedBurstExecutions.end()) {
            for (const RuntimeMemory* memory : kMemories) {
                const auto pool = memory->getMemoryPool();
                if (const auto* maybeMemory = std::get_if<SharedMemory>(&pool)) {
                    auto cacheHold = burstController->cacheMemory(*maybeMemory);
                    memory->hold(cacheHold);
                }
            }
            auto createResult = burstController->createReusableExecution(
                    kRequest, kMeasure, kLoopTimeoutDuration, kMetaData,
                    TypeManager::get()->getExtensionNameAndPrefix(kMetaData));
            if (!createResult.ok()) {
                LOG(ERROR) << "IBurst::createReusableExecution() error: "
                           << createResult.error().message;
                const int n = convertErrorStatusToResultCode(createResult.error().code);
                return {n, {}, {}};
            }
            execution = std::move(createResult).value();
            mCachedBurstExecutions.emplace(burstController.get(), execution);
        } else {
            execution = burstExecution->second;
        }
        VLOG(EXECUTION) << "Before mBurstExecution->compute() " << SHOW_IF_DEBUG(kRequest);
    } else {
        execution = kExecution;
    }

    CHECK(execution != nullptr);
    auto result = execution->compute(deadline);
    if (!result.ok()) {
        auto [message, code, returnedOutputShapes] = std::move(result).error();
        int n = convertErrorStatusToResultCode(code);
        VLOG(EXECUTION) << "**Execution failed** (ResultCode = " << n << ")";
        LOG(ERROR) << (burstCompute ? "IBurst" : "IPreparedModel")
                   << "::execute(...) error: " << message;
        if (code == ErrorStatus::OUTPUT_INSUFFICIENT_SIZE) {
            return {n, std::move(returnedOutputShapes), {}};
        }
        return {n, {}, {}};
    }

    VLOG(EXECUTION) << "DriverExecution::compute completed";
    auto [outputShapes, timing] = std::move(result).value();
    return {ANEURALNETWORKS_NO_ERROR, std::move(outputShapes), timing};
}

std::tuple<int, int, ExecuteFencedInfoCallback, Timing> DriverExecution::computeFenced(
        const std::vector<int>& waitFor, const OptionalTimePoint& deadline,
        const OptionalDuration& timeoutDurationAfterFence) const {
    NNTRACE_RT(NNTRACE_PHASE_EXECUTION, "DriverExecution::computeFenced");
    CHECK(std::all_of(waitFor.begin(), waitFor.end(), [](int fd) { return fd >= 0; }));

    std::vector<SyncFence> waitForHandles;
    waitForHandles.reserve(waitFor.size());
    for (int fd : waitFor) {
        int dupFd = dup(fd);
        if (dupFd < 0) {
            LOG(ERROR) << "Unable to dup the file descriptor";
            return {ANEURALNETWORKS_OP_FAILED, -1, nullptr, {}};
        }
        waitForHandles.push_back(SyncFence::create(base::unique_fd(dupFd)));
    }

    SyncFence syncFence = SyncFence::createAsSignaled();
    ExecuteFencedInfoCallback executeFencedInfoCallback = nullptr;
    Timing timing = {};
    if (isCompliantVersion(kHalVersionV1_3ToApi.canonical, kDeviceFeatureLevel)) {
        auto result =
                kExecution->computeFenced(waitForHandles, deadline, timeoutDurationAfterFence);
        if (!result.ok()) {
            LOG(ERROR) << "IExecution::computeFenced() error: " << result.error().message;
            VLOG(EXECUTION) << "**computeFenced failed**";
            return {convertErrorStatusToResultCode(result.error().code), -1, nullptr, {}};
        }
        std::tie(syncFence, executeFencedInfoCallback) = std::move(result).value();
    } else {
        // Fallback to synchronous execution if computeFenced is not supported.
        // First wait for all sync fences to be ready.
        LOG(INFO) << "No drivers able to handle sync fences, falling back to regular execution";
        for (const auto& fence : waitForHandles) {
            if (!fence.hasFd() || fence.getFd() < 0) {
                return {ANEURALNETWORKS_BAD_DATA, -1, nullptr, {}};
            }
            auto r = fence.syncWait({/* no timeout */});
            if (r != SyncFence::FenceState::SIGNALED) {
                LOG(ERROR) << "syncWait failed, fd: " << fence.getFd() << ", state: " << r;
                return {ANEURALNETWORKS_OP_FAILED, -1, nullptr, {}};
            }
        }
        auto result = kExecution->compute(deadline);
        if (!result.ok()) {
            LOG(ERROR) << "IExecution::compute() error: " << result.error().message;
            return {convertErrorStatusToResultCode(result.error().code), -1, nullptr, {}};
        }
        std::tie(std::ignore, timing) = result.value();
    }

    int syncFenceFd = -1;
    if (syncFence.hasFd()) {
        syncFenceFd = dup(syncFence.getFd());
        if (syncFenceFd < 0) {
            LOG(ERROR) << "Failed to dup the file descriptor";
            return {ANEURALNETWORKS_OP_FAILED, -1, nullptr, timing};
        }
    }

    VLOG(EXECUTION) << "DriverExecution::computeFenced completed";
    return {ANEURALNETWORKS_NO_ERROR, syncFenceFd, executeFencedInfoCallback, timing};
}

static Capabilities createCpuCapabilities() {
    constexpr Capabilities::PerformanceInfo kPerf = {.execTime = 1.0f, .powerUsage = 1.0f};
    return makeCapabilities(kPerf, kPerf, kPerf);
}

// A special abstracted device for the CPU. Only one instance of this class will exist.
// Use get() to retrieve it.
class CpuDevice : public Device {
   public:
    // Returns the singleton CPU fallback device.
    static std::shared_ptr<CpuDevice> get() {
        static std::shared_ptr<CpuDevice> instance(new CpuDevice);
        return instance;
    }

    const std::string& getName() const override { return kName; }
    const std::string& getVersionString() const override { return kVersionString; }
    Version getFeatureLevel() const override { return kVersion; }
    int32_t getType() const override { return ANEURALNETWORKS_DEVICE_CPU; }
    const std::vector<Extension>& getSupportedExtensions() const override {
        return kSupportedExtensions;
    }
    std::vector<bool> getSupportedOperations(const MetaModel& metaModel) const override;
    const Capabilities& getCapabilities() const override { return kCapabilities; }
    Capabilities::PerformanceInfo getPerformance(OperandType) const override {
        return kPerformance;
    }
    Capabilities::PerformanceInfo getRelaxedFloat32toFloat16PerformanceScalar() const override {
        return kPerformance;
    }
    Capabilities::PerformanceInfo getRelaxedFloat32toFloat16PerformanceTensor() const override {
        return kPerformance;
    }
    Capabilities::PerformanceInfo getIfPerformance() const override { return kPerformance; }
    Capabilities::PerformanceInfo getWhilePerformance() const override { return kPerformance; }
    std::pair<uint32_t, uint32_t> getNumberOfCacheFilesNeeded() const override {
        return {/*numModelCache=*/0, /*numDataCache=*/0};
    }
    bool isCachingSupported() const override { return false; }
    int wait() const override { return ANEURALNETWORKS_NO_ERROR; }

    std::pair<int, std::shared_ptr<RuntimePreparedModel>> prepareModel(
            const ModelFactory& makeModel, ExecutionPreference preference, Priority priority,
            const OptionalTimePoint& deadline, const CacheInfo& cacheInfo,
            const std::optional<CacheToken>& maybeToken,
            const std::vector<TokenValuePair>& metaData,
            const std::vector<ExtensionNameAndPrefix>& extensionNameAndPrefix) const override;

    std::pair<int, std::unique_ptr<RuntimeMemory>> allocate(const MemoryDescriptor& desc,
                                                            OperandType type) const override;

   private:
    CpuDevice() = default;
    const Version kVersion = getRuntimeFeatureLevelVersion();
    const std::string kName = "nnapi-reference";
#ifndef NN_COMPATIBILITY_LIBRARY_BUILD
    const std::string kVersionString = build::GetBuildNumber();
#else
    const std::string kVersionString = "UNKNOWN";
#endif  // NN_COMPATIBILITY_LIBRARY_BUILD
    // Since the performance is a ratio compared to the CPU performance,
    // by definition the performance of the CPU is 1.0.
    const Capabilities::PerformanceInfo kPerformance = {.execTime = 1.0f, .powerUsage = 1.0f};
    const Capabilities kCapabilities = createCpuCapabilities();
    const std::vector<Extension> kSupportedExtensions{/* No extensions. */};
};

// A special abstracted RuntimePreparedModel for the CPU, constructed by CpuDevice.
class CpuPreparedModel : public RuntimePreparedModel {
   public:
    // Factory method for CpuPreparedModel. Returns ANEURALNETWORKS_NO_ERROR and
    // a prepared model object if successfully created. Returns an error code
    // and nullptr otherwise.
    static std::pair<int, std::shared_ptr<RuntimePreparedModel>> create(Model model);

    const Device* getDevice() const override { return CpuDevice::get().get(); }
    SharedPreparedModel getInterface() const override { return nullptr; }

    std::tuple<int, std::vector<OutputShape>, Timing> execute(
            const std::vector<ModelArgumentInfo>& inputs,
            const std::vector<ModelArgumentInfo>& outputs,
            const std::vector<const RuntimeMemory*>& memories, const SharedBurst& burstController,
            MeasureTiming measure, const OptionalTimePoint& deadline,
            const OptionalDuration& loopTimeoutDuration,
            const std::vector<TokenValuePair>& metaData) const override;

    GeneralResult<SharedBurst> configureExecutionBurst() const override { return nullptr; }

    std::tuple<int, int, ExecuteFencedInfoCallback, Timing> executeFenced(
            const std::vector<ModelArgumentInfo>& inputs,
            const std::vector<ModelArgumentInfo>& outputs,
            const std::vector<const RuntimeMemory*>& memories, const std::vector<int>& waitFor,
            MeasureTiming measure, const OptionalTimePoint& deadline,
            const OptionalDuration& loopTimeoutDuration,
            const OptionalDuration& timeoutDurationAfterFence,
            const std::vector<TokenValuePair>& metaData) const override;

    std::pair<int, std::shared_ptr<RuntimeExecution>> createReusableExecution(
            const std::vector<ModelArgumentInfo>& inputs,
            const std::vector<ModelArgumentInfo>& outputs,
            const std::vector<const RuntimeMemory*>& memories, MeasureTiming measure,
            const OptionalDuration& loopTimeoutDuration,
            const std::vector<TokenValuePair>& metaData) const override;

    MemoryPreference getMemoryPreference() const override {
        return {kPreferredAlignment, kPreferredPadding};
    }

    // Prefer to use CpuPreparedModel::create.
    CpuPreparedModel(Model model, std::vector<RunTimePoolInfo> poolInfos)
        : mModel(std::move(model)), mModelPoolInfos(std::move(poolInfos)) {}

    const Model& getModel() const { return mModel; }
    const std::vector<RunTimePoolInfo>& getModelPoolInfos() const { return mModelPoolInfos; }

   private:
    // TFLite kernels prefers 64 bytes for padding and alignment.
    static constexpr uint32_t kPreferredAlignment = 64;
    static constexpr uint32_t kPreferredPadding = 64;

    const Model mModel;
    const std::vector<RunTimePoolInfo> mModelPoolInfos;
};

class CpuExecution : public RuntimeExecution {
   public:
    CpuExecution(const CpuPreparedModel& preparedModel, Request request,
                 std::vector<RunTimePoolInfo> requestPoolInfos,
                 OptionalDuration loopTimeoutDuration)
        : kPreparedModel(preparedModel),
          kRequest(std::move(request)),
          kRequestPoolInfos(std::move(requestPoolInfos)),
          kLoopTimeoutDuration(std::move(loopTimeoutDuration)) {}

    std::tuple<int, std::vector<OutputShape>, Timing> compute(
            const SharedBurst& burstController, const OptionalTimePoint& deadline) const override;

    std::tuple<int, int, ExecuteFencedInfoCallback, Timing> computeFenced(
            const std::vector<int>& waitFor, const OptionalTimePoint& deadline,
            const OptionalDuration& timeoutDurationAfterFence) const override;

   private:
    const CpuPreparedModel& kPreparedModel;
    Request kRequest;
    std::vector<RunTimePoolInfo> kRequestPoolInfos;
    const OptionalDuration kLoopTimeoutDuration;
};

std::vector<bool> CpuDevice::getSupportedOperations(const MetaModel& metaModel) const {
    const Model& model = metaModel.getModel();
    const size_t count = model.main.operations.size();
    std::vector<bool> result(count, false);
    for (size_t i = 0; i < count; i++) {
        // TODO(b/119870033): Decide whether and how post-P operations would be supported on CPU.
        //                    We may want to use the slicer for CpuDevice just as we do for
        //                    DriverDevice.
        OperationType operationType = model.main.operations[i].type;
        result[i] = !isExtension(operationType) && operationType != OperationType::OEM_OPERATION;
    }
    return result;
}

template <typename Type>
static Result<void> validateAndCheckCompliance(const Type& object) {
    const auto version = NN_TRY(validate(object));
    if (!isCompliantVersion(version, DeviceManager::get()->getRuntimeVersion())) {
        return NN_ERROR() << "Object than is newer what is allowed. Version needed: " << version
                          << ", current runtime version supported: "
                          << DeviceManager::get()->getRuntimeVersion();
    }
    return {};
}

std::pair<int, std::shared_ptr<RuntimePreparedModel>> CpuDevice::prepareModel(
        const ModelFactory& makeModel, ExecutionPreference preference, Priority priority,
        const OptionalTimePoint& deadline, const CacheInfo& /*cacheInfo*/,
        const std::optional<CacheToken>& maybeToken,
        const std::vector<TokenValuePair>& /*metaData*/,
        const std::vector<ExtensionNameAndPrefix>& /*extensionNameAndPrefix*/) const {
    CHECK(!maybeToken.has_value())
            << "Should never call prepareModel with cache information on CpuDevice";

    const Model model = makeModel();
    if (auto result = validateAndCheckCompliance(model); !result.ok()) {
        LOG(ERROR) << "Invalid Model: " << result.error();
        return {ANEURALNETWORKS_OP_FAILED, nullptr};
    }
    if (auto result = validateAndCheckCompliance(preference); !result.ok()) {
        LOG(ERROR) << "Invalid ExecutionPreference: " << result.error();
        return {ANEURALNETWORKS_OP_FAILED, nullptr};
    }
    if (auto result = validateAndCheckCompliance(priority); !result.ok()) {
        LOG(ERROR) << "Invalid Priority: " << result.error();
        return {ANEURALNETWORKS_OP_FAILED, nullptr};
    }
    if (hasDeadlinePassed(deadline)) {
        return {ANEURALNETWORKS_MISSED_DEADLINE_PERSISTENT, nullptr};
    }

    return CpuPreparedModel::create(model);
}

std::pair<int, std::unique_ptr<RuntimeMemory>> CpuDevice::allocate(const MemoryDescriptor& desc,
                                                                   OperandType type) const {
    uint32_t size = TypeManager::get()->getSizeOfData(type, desc.dimensions);
    if (size == 0) {
        LOG(ERROR) << "CpuDevice::allocate -- does not support unknown dimensions.";
        return {ANEURALNETWORKS_OP_FAILED, nullptr};
    }
    return MemoryAshmem::create(size);
}

std::pair<int, std::shared_ptr<RuntimePreparedModel>> CpuPreparedModel::create(Model model) {
    std::vector<RunTimePoolInfo> poolInfos;
    if (!setRunTimePoolInfosFromCanonicalMemories(&poolInfos, model.pools)) {
        return {ANEURALNETWORKS_UNMAPPABLE, nullptr};
    }

    std::shared_ptr<RuntimePreparedModel> preparedModel =
            std::make_shared<CpuPreparedModel>(std::move(model), std::move(poolInfos));
    return {ANEURALNETWORKS_NO_ERROR, std::move(preparedModel)};
}

static std::tuple<int, std::vector<OutputShape>, Timing> computeOnCpu(
        const Model& model, const Request& request,
        const std::vector<RunTimePoolInfo>& modelPoolInfos,
        const std::vector<RunTimePoolInfo>& requestPoolInfos, const OptionalTimePoint& deadline,
        const OptionalDuration& loopTimeoutDuration) {
    NNTRACE_RT(NNTRACE_PHASE_EXECUTION, "computeOnCpu");
    CpuExecutor executor;
    if (loopTimeoutDuration.has_value()) {
        executor.setLoopTimeout(loopTimeoutDuration->count());
    }
    if (deadline.has_value()) {
        executor.setDeadline(*deadline);
    }
    int err = executor.run(model, request, modelPoolInfos, requestPoolInfos);
    const auto& outputShapes = executor.getOutputShapes();
    return {err, outputShapes, {}};
}

std::tuple<int, int, ExecuteFencedInfoCallback, Timing> CpuPreparedModel::executeFenced(
        const std::vector<ModelArgumentInfo>& inputs, const std::vector<ModelArgumentInfo>& outputs,
        const std::vector<const RuntimeMemory*>& memories, const std::vector<int>& waitFor,
        MeasureTiming measure, const OptionalTimePoint& deadline,
        const OptionalDuration& loopTimeoutDuration, const OptionalDuration& duration,
        const std::vector<TokenValuePair>& /*metaData*/) const {
    VLOG(EXECUTION)
            << "CpuPreparedModel::executeFenced wait for sync fences to signal before execution";
    for (int syncFd : waitFor) {
        if (syncFd > 0) {
            auto r = syncWait(syncFd, -1);
            if (r != FenceState::SIGNALED) {
                LOG(ERROR) << "sync wait failed, fd: " << syncFd;
                return {ANEURALNETWORKS_OP_FAILED, -1, nullptr, {}};
            }
        }
    }

    // Update deadline if the timeout duration is closer than the deadline.
    auto closestDeadline = deadline;
    if (duration.has_value()) {
        const auto timeoutDurationDeadline = makeDeadline(*duration);
        if (!closestDeadline.has_value() || *closestDeadline > timeoutDurationDeadline) {
            closestDeadline = timeoutDurationDeadline;
        }
    }

    const auto [result, outputShapes, timing] = execute(inputs, outputs, memories, nullptr, measure,
                                                        closestDeadline, loopTimeoutDuration, {});
    return {result, -1, nullptr, timing};
}

static std::tuple<int, Request, std::vector<RunTimePoolInfo>> createCpuRequest(
        const std::vector<ModelArgumentInfo>& inputs, const std::vector<ModelArgumentInfo>& outputs,
        const std::vector<const RuntimeMemory*>& memories) {
    std::vector<RunTimePoolInfo> requestPoolInfos;
    requestPoolInfos.reserve(memories.size());
    for (const RuntimeMemory* mem : memories) {
        if (std::optional<RunTimePoolInfo> poolInfo = mem->getRunTimePoolInfo()) {
            requestPoolInfos.emplace_back(*poolInfo);
        } else {
            return {ANEURALNETWORKS_UNMAPPABLE, {}, {}};
        }
    }
    // Create as many pools as there are input / output.
    auto fixPointerArguments =
            [&requestPoolInfos](const std::vector<ModelArgumentInfo>& argumentInfos) {
                std::vector<DataLocation> ptrArgsLocations;
                for (const ModelArgumentInfo& argumentInfo : argumentInfos) {
                    if (argumentInfo.state() == ModelArgumentInfo::POINTER) {
                        ptrArgsLocations.push_back(
                                {.poolIndex = static_cast<uint32_t>(requestPoolInfos.size()),
                                 .offset = 0,
                                 .length = argumentInfo.length(),
                                 .padding = argumentInfo.padding()});
                        requestPoolInfos.emplace_back(RunTimePoolInfo::createFromExistingBuffer(
                                static_cast<uint8_t*>(argumentInfo.buffer())));
                    }
                }
                return ptrArgsLocations;
            };
    const std::vector<DataLocation> inputPtrArgsLocations = fixPointerArguments(inputs);
    const std::vector<DataLocation> outputPtrArgsLocations = fixPointerArguments(outputs);

    Request request;
    request.inputs = createRequestArguments(inputs, inputPtrArgsLocations);
    request.outputs = createRequestArguments(outputs, outputPtrArgsLocations);
    return {ANEURALNETWORKS_NO_ERROR, std::move(request), std::move(requestPoolInfos)};
}

// Perform computation on NNAPI CPU reference implementation.
//
// Contrary to DriverPreparedModel::execute, the NNAPI CPU reference executor lives in the
// same process as the NNAPI runtime and can take raw pointers. We will create as many pools as
// there are input/output in this method to avoid data copying.
//
// Will choose between sync/async execution according to DeviceManager::mSyncExecCpu.
std::tuple<int, std::vector<OutputShape>, Timing> CpuPreparedModel::execute(
        const std::vector<ModelArgumentInfo>& inputs, const std::vector<ModelArgumentInfo>& outputs,
        const std::vector<const RuntimeMemory*>& memories, const SharedBurst& /*burstController*/,
        MeasureTiming /*measure*/, const OptionalTimePoint& deadline,
        const OptionalDuration& loopTimeoutDuration,
        const std::vector<TokenValuePair>& /*metaData*/) const {
    if (hasDeadlinePassed(deadline)) {
        return {ANEURALNETWORKS_MISSED_DEADLINE_PERSISTENT, {}, {}};
    }

    int nCreateRequest;
    Request request;
    std::vector<RunTimePoolInfo> requestPoolInfos;
    std::tie(nCreateRequest, request, requestPoolInfos) =
            createCpuRequest(inputs, outputs, memories);
    if (nCreateRequest != ANEURALNETWORKS_NO_ERROR) {
        return {nCreateRequest, {}, {}};
    }

    if (!DeviceManager::get()->syncExecCpu()) {
        // TODO: use a thread pool
        // TODO(mikie): this could have NNTRACE so we could measure the overhead
        //              of spinning up a new thread.
        std::tuple<int, std::vector<OutputShape>, Timing> result = {};
        std::thread([this, &request, &requestPoolInfos, &deadline, &loopTimeoutDuration, &result] {
            result = computeOnCpu(mModel, request, mModelPoolInfos, requestPoolInfos, deadline,
                                  loopTimeoutDuration);
        }).join();
        return result;
    }

    return computeOnCpu(mModel, request, mModelPoolInfos, requestPoolInfos, deadline,
                        loopTimeoutDuration);
}

std::pair<int, std::shared_ptr<RuntimeExecution>> CpuPreparedModel::createReusableExecution(
        const std::vector<ModelArgumentInfo>& inputs, const std::vector<ModelArgumentInfo>& outputs,
        const std::vector<const RuntimeMemory*>& memories, MeasureTiming /*measure*/,
        const OptionalDuration& loopTimeoutDuration,
        const std::vector<TokenValuePair>& /*metaData*/) const {
    auto [nCreateRequest, request, requestPoolInfos] = createCpuRequest(inputs, outputs, memories);
    if (nCreateRequest != ANEURALNETWORKS_NO_ERROR) {
        return {nCreateRequest, nullptr};
    }
    auto execution = std::make_shared<CpuExecution>(
            *this, std::move(request), std::move(requestPoolInfos), loopTimeoutDuration);
    return {ANEURALNETWORKS_NO_ERROR, std::move(execution)};
}

std::tuple<int, std::vector<OutputShape>, Timing> CpuExecution::compute(
        const SharedBurst& /*burstController*/, const OptionalTimePoint& deadline) const {
    if (hasDeadlinePassed(deadline)) {
        return {ANEURALNETWORKS_MISSED_DEADLINE_PERSISTENT, {}, {}};
    }

    if (!DeviceManager::get()->syncExecCpu()) {
        // TODO: use a thread pool
        // TODO(mikie): this could have NNTRACE so we could measure the overhead
        //              of spinning up a new thread.
        std::tuple<int, std::vector<OutputShape>, Timing> result = {};
        std::thread([this, &deadline, &result] {
            result = computeOnCpu(kPreparedModel.getModel(), kRequest,
                                  kPreparedModel.getModelPoolInfos(), kRequestPoolInfos, deadline,
                                  kLoopTimeoutDuration);
        }).join();
        return result;
    }

    return computeOnCpu(kPreparedModel.getModel(), kRequest, kPreparedModel.getModelPoolInfos(),
                        kRequestPoolInfos, deadline, kLoopTimeoutDuration);
}

std::tuple<int, int, ExecuteFencedInfoCallback, Timing> CpuExecution::computeFenced(
        const std::vector<int>& waitFor, const OptionalTimePoint& deadline,
        const OptionalDuration& duration) const {
    VLOG(EXECUTION)
            << "CpuExecution::computeFenced wait for sync fences to signal before execution";
    for (int syncFd : waitFor) {
        if (syncFd > 0) {
            auto r = syncWait(syncFd, -1);
            if (r != FenceState::SIGNALED) {
                LOG(ERROR) << "sync wait failed, fd: " << syncFd;
                return {ANEURALNETWORKS_OP_FAILED, -1, nullptr, {}};
            }
        }
    }

    // Update deadline if the timeout duration is closer than the deadline.
    auto closestDeadline = deadline;
    if (duration.has_value()) {
        const auto timeoutDurationDeadline = makeDeadline(*duration);
        if (!closestDeadline.has_value() || *closestDeadline > timeoutDurationDeadline) {
            closestDeadline = timeoutDurationDeadline;
        }
    }

    const auto [result, outputShapes, timing] = compute(nullptr, closestDeadline);
    return {result, -1, nullptr, timing};
}

int64_t DeviceManager::getRuntimeFeatureLevel() const {
    return versionToFeatureLevel(mRuntimeVersion.level);
}

DeviceManager* DeviceManager::get() {
    static DeviceManager manager;
    return &manager;
}

std::shared_ptr<Device> DeviceManager::getCpuDevice() {
    return CpuDevice::get();
}

std::shared_ptr<Device> DeviceManager::forTest_makeDriverDevice(const SharedDevice& device) {
    VLOG(MANAGER) << "forTest_makeDriverDevice(" << device->getName() << ")";
    const auto driverDevice = DriverDevice::create(device);
    CHECK(driverDevice != nullptr);
    return driverDevice;
}

#ifndef NN_COMPATIBILITY_LIBRARY_BUILD
std::vector<std::shared_ptr<DriverDevice>> getDriverDevices(
        [[maybe_unused]] Version::Level maxFeatureLevelAllowed) {
#ifdef __ANDROID__
    auto devices = hardware::neuralnetworks::service::getDevices(maxFeatureLevelAllowed);

    std::vector<std::shared_ptr<DriverDevice>> driverDevices;
    driverDevices.reserve(devices.size());
    for (auto& device : devices) {
        driverDevices.push_back(DriverDevice::create(std::move(device)));
    }
    return driverDevices;
#else   // __ANDROID__
    return {};
#endif  // __ANDROID__
}
#else
std::vector<std::shared_ptr<DriverDevice>> getDriverDevices(
        Version::Level /*maxFeatureLevelAllowed*/) {
    auto devices = getDevices();
    std::vector<std::shared_ptr<DriverDevice>> driverDevices;
    driverDevices.reserve(devices.size());
    for (auto& device : devices) {
        driverDevices.push_back(DriverDevice::create(std::move(device)));
    }
    return driverDevices;
}
#endif  // NN_COMPATIBILITY_LIBRARY_BUILD

void DeviceManager::findAvailableDevices() {
    VLOG(MANAGER) << "findAvailableDevices";

#ifdef NN_DEBUGGABLE
    // debug.nn.enabled-devices defines a regex pattern. For all available driver devices, only the
    // ones with name matching the pattern are enabled. Driver devices with unmatched names are
    // ignored. If this property is not set, all available driver devices are enabled by default.
    // This filter only applies to driver devices. nnapi-reference is always enabled.
    std::string patternStr = base::GetProperty("debug.nn.enabled-devices", ".*");
    LOG(INFO) << "Enabled devices: " << patternStr;
    const std::regex pattern(patternStr);
#endif  // NN_DEBUGGABLE

    // register driver devices
    auto driverDevices = getDriverDevices(mRuntimeVersion.level);
    for (auto& driverDevice : driverDevices) {
#ifdef NN_DEBUGGABLE
        if (!std::regex_match(driverDevice->getName(), pattern)) {
            LOG(INFO) << "Ignored interface " << driverDevice->getName()
                      << " (version = " << driverDevice->getVersionString() << ")";
            continue;
        }
#endif  // NN_DEBUGGABLE
        LOG(INFO) << "Found interface " << driverDevice->getName()
                  << " (version = " << driverDevice->getVersionString() << ")";
        mDevices.push_back(std::move(driverDevice));
    }

#ifndef NN_COMPATIBILITY_LIBRARY_BUILD
    // register CPU fallback device
    mDevices.push_back(CpuDevice::get());
    mDevicesCpuOnly.push_back(CpuDevice::get());
#endif  // NN_COMPATIBILITY_LIBRARY_BUILD
}

void DeviceManager::registerDevice(const SharedDevice& device) {
    if (auto driverDevice = DriverDevice::create(device)) {
        mDevices.push_back(std::move(driverDevice));
    }
}

DeviceManager::DeviceManager() {
    VLOG(MANAGER) << "DeviceManager::DeviceManager";
    mRuntimeVersion = getRuntimeFeatureLevelVersion();
    mIsPlatformTelemetryEnabled = getWhetherPlatformTelemetryIsEnabled();
    findAvailableDevices();
#ifdef NN_DEBUGGABLE
    mStrictSlicing = (getProp("debug.nn.strict-slicing") != 0);
    mPartitioning = getProp("debug.nn.partition", kPartitioningDefault);
    mDebugNNCpuOnly = (getProp("debug.nn.cpuonly") != 0);
    mSyncExecCpu = (getProp("debug.nn.syncexec-cpu", 1) != 0);
    mSyncExecRuntime = (getProp("debug.nn.syncexec-runtime") != 0);
#endif  // NN_DEBUGGABLE
}

}  // namespace nn
}  // namespace android
