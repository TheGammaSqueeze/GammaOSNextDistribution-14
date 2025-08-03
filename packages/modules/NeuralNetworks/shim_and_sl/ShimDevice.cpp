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

#define LOG_TAG "ShimDevice"

#include "ShimDevice.h"

#include <NeuralNetworks.h>
#include <aidl/android/hardware/neuralnetworks/DataLocation.h>
#include <aidl/android/hardware/neuralnetworks/ErrorStatus.h>
#include <aidl/android/hardware/neuralnetworks/Extension.h>
#include <aidl/android/hardware/neuralnetworks/ExtensionOperandTypeInformation.h>
#include <aidl/android/hardware/neuralnetworks/Memory.h>
#include <aidl/android/hardware/neuralnetworks/NumberOfCacheFiles.h>
#include <aidl/android/hardware/neuralnetworks/OperandLifeTime.h>
#include <aidl/android/hardware/neuralnetworks/OperandPerformance.h>
#include <android-base/logging.h>
#include <android-base/scopeguard.h>
#include <android/binder_auto_utils.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <nnapi/TypeUtils.h>
#include <nnapi/hal/aidl/Conversions.h>

#include <algorithm>
#include <limits>
#include <memory>
#include <optional>
#include <string>
#include <unordered_map>
#include <utility>
#include <vector>

#include "ShimConverter.h"
#include "ShimPreparedModel.h"
#include "ShimUtils.h"
#include "SupportLibrary.h"

using namespace ::android::nn::sl_wrapper;

namespace aidl::android::hardware::neuralnetworks {

namespace {

constexpr std::optional<::android::nn::wrapper::ExecutePriority> convertToNDKPriority(
        Priority priority) {
    switch (priority) {
        case Priority::LOW:
            return ::android::nn::wrapper::ExecutePriority::LOW;
        case Priority::MEDIUM:
            return ::android::nn::wrapper::ExecutePriority::MEDIUM;
        case Priority::HIGH:
            return ::android::nn::wrapper::ExecutePriority::HIGH;
    }
    LOG(ERROR) << "unrecognized priority: " << static_cast<int32_t>(priority);
    return std::nullopt;
}

constexpr std::optional<::android::nn::wrapper::ExecutePreference> convertToNDKPreference(
        ExecutionPreference preference) {
    switch (preference) {
        case ExecutionPreference::LOW_POWER:
            return ::android::nn::wrapper::ExecutePreference::PREFER_LOW_POWER;
        case ExecutionPreference::FAST_SINGLE_ANSWER:
            return ::android::nn::wrapper::ExecutePreference::PREFER_FAST_SINGLE_ANSWER;
        case ExecutionPreference::SUSTAINED_SPEED:
            return ::android::nn::wrapper::ExecutePreference::PREFER_SUSTAINED_SPEED;
    }
    LOG(ERROR) << "unrecognized preference: " << static_cast<int32_t>(preference);
    return std::nullopt;
}

// Safely downcast an IPreparedModel object to ShimPreparedModel.
// This function will return nullptr if the IPreparedModel object is not originated from the
// shim process.
const ShimPreparedModel* castToShimPreparedModel(IPreparedModel* preparedModel) {
    if (preparedModel->isRemote()) {
        return nullptr;
    }
    // This static_cast is safe because ShimPreparedModel is the only class that implements
    // the IPreparedModel interface in the sample driver process.
    return static_cast<const ShimPreparedModel*>(preparedModel);
}

static PerformanceInfo convertPerformanceInfo(const SL_ANeuralNetworksPerformanceInfo& info) {
    return {.execTime = info.execTime, .powerUsage = info.powerUsage};
}

Capabilities getCapabilities(const NnApiSupportLibrary* nnapi, ANeuralNetworksDevice* device) {
    Capabilities capabilities;
    SL_ANeuralNetworksPerformanceInfo performanceInfo;

    nnapi->getFL5()->SL_ANeuralNetworksDevice_getPerformanceInfo(
            device, SL_ANEURALNETWORKS_CAPABILITIES_PERFORMANCE_RELAXED_SCALAR, &performanceInfo);
    capabilities.relaxedFloat32toFloat16PerformanceScalar = convertPerformanceInfo(performanceInfo);

    nnapi->getFL5()->SL_ANeuralNetworksDevice_getPerformanceInfo(
            device, SL_ANEURALNETWORKS_CAPABILITIES_PERFORMANCE_RELAXED_TENSOR, &performanceInfo);
    capabilities.relaxedFloat32toFloat16PerformanceTensor = convertPerformanceInfo(performanceInfo);

    nnapi->getFL5()->SL_ANeuralNetworksDevice_getPerformanceInfo(
            device, SL_ANEURALNETWORKS_CAPABILITIES_PERFORMANCE_IF, &performanceInfo);
    capabilities.ifPerformance = convertPerformanceInfo(performanceInfo);

    nnapi->getFL5()->SL_ANeuralNetworksDevice_getPerformanceInfo(
            device, SL_ANEURALNETWORKS_CAPABILITIES_PERFORMANCE_WHILE, &performanceInfo);
    capabilities.whilePerformance = convertPerformanceInfo(performanceInfo);

    constexpr auto fn = [](SL_ANeuralNetworksOperandPerformanceInfo info, void* context) {
        auto* out = static_cast<std::vector<OperandPerformance>*>(context);
        out->push_back(OperandPerformance{
                .type = static_cast<OperandType>(info.operandType),
                .info = convertPerformanceInfo(info.performanceInfo),
        });
    };

    nnapi->getFL5()->SL_ANeuralNetworksDevice_forEachOperandPerformanceInfo(
            device, static_cast<void*>(&capabilities.operandPerformance), fn);

    return capabilities;
}

NumberOfCacheFiles getNumberOfCacheFilesNeeded(const NnApiSupportLibrary* nnapi,
                                               ANeuralNetworksDevice* device) {
    uint32_t numModelCacheFiles;
    uint32_t numDataCacheFiles;
    nnapi->getFL5()->SL_ANeuralNetworksDevice_getNumberOfCacheFilesNeeded(
            device, &numModelCacheFiles, &numDataCacheFiles);
    return {
            .numModelCache = static_cast<int32_t>(numModelCacheFiles),
            .numDataCache = static_cast<int32_t>(numDataCacheFiles),
    };
}

std::vector<Extension> getVendorExtensions(const NnApiSupportLibrary* nnapi,
                                           ANeuralNetworksDevice* device) {
    uint32_t vendorExtensionCount;
    nnapi->getFL5()->SL_ANeuralNetworksDevice_getVendorExtensionCount(device,
                                                                      &vendorExtensionCount);

    std::vector<Extension> extensions(vendorExtensionCount);

    for (uint32_t vendorExtensionIndex = 0; vendorExtensionIndex < vendorExtensionCount;
         ++vendorExtensionIndex) {
        auto& extension = extensions[vendorExtensionIndex];

        const char* extensionName;
        nnapi->getFL5()->SL_ANeuralNetworksDevice_getVendorExtensionName(
                device, vendorExtensionIndex, &extensionName);
        extension.name = extensionName;

        constexpr auto fn = [](SL_ANeuralNetworksExtensionOperandTypeInformation info,
                               void* context) {
            auto* out = static_cast<std::vector<ExtensionOperandTypeInformation>*>(context);
            out->push_back(ExtensionOperandTypeInformation{
                    .type = info.type,
                    .isTensor = info.isTensor,
                    .byteSize = static_cast<int32_t>(info.byteSize),
            });
        };
        nnapi->getFL5()->SL_ANeuralNetworksDevice_forEachVendorExtensionOperandTypeInformation(
                device, vendorExtensionIndex, static_cast<void*>(&extension.operandTypes), fn);
    }

    return extensions;
}

}  // namespace

ShimDevice::ShimDevice(std::shared_ptr<const NnApiSupportLibrary> nnapi,
                       ANeuralNetworksDevice* device, std::string serviceName)
    : mNnapi(std::move(nnapi)),
      mBufferTracker(ShimBufferTracker::create()),
      mServiceName(std::move(serviceName)),
      mDevice(device),
      mCapabilities(neuralnetworks::getCapabilities(mNnapi.get(), mDevice)),
      mNumberOfCacheFiles(neuralnetworks::getNumberOfCacheFilesNeeded(mNnapi.get(), mDevice)),
      mExtensions(neuralnetworks::getVendorExtensions(mNnapi.get(), mDevice)) {}

// Manages the data buffer for an operand.
class ShimBuffer : public BnBuffer {
   public:
    ShimBuffer(const NnApiSupportLibrary* nnApi, const ::android::nn::Dimensions initialDimensions,
               const ::android::nn::OperandType type,
               std::shared_ptr<::android::nn::sl_wrapper::Memory> memory,
               std::unique_ptr<ShimBufferTracker::Token> token)
        : kInitialDimensions(initialDimensions),
          kType(type),
          mNnApi(nnApi),
          mMemory(std::move(memory)),
          kToken(std::move(token)) {}

    bool tensorHasUnspecifiedDimensions(::android::nn::OperandType type,
                                        const ::android::nn::Dimensions& dimensions) {
        if (!::android::nn::isExtension(type)) {
            if (isNonExtensionScalar(type)) {
                return false;
            }
        }
        return dimensions.size() == 0 || std::any_of(dimensions.begin(), dimensions.end(),
                                                     [](int32_t dim) { return dim == 0; });
    }

    bool validateDimensions(const ::android::nn::Dimensions& dimensions) {
        if (isNonExtensionScalar(kType)) {
            if (!dimensions.empty()) {
                LOG(ERROR) << "ShimBuffer::validateDimensions -- invalid dimensions for scalar "
                              "operand";
                return false;
            }
            return true;
        }

        if (dimensions.empty()) {
            if (tensorHasUnspecifiedDimensions(kType, kInitialDimensions)) {
                LOG(ERROR) << "ShimBuffer::validateDimensions -- the initial dimensions are not "
                              "fully specified and no dimension update is provided: ";

                return false;
            }
        } else {
            if (tensorHasUnspecifiedDimensions(kType, dimensions)) {
                LOG(ERROR) << "ShimBuffer::validateDimensions -- the updated dimensions are not "
                              "fully specified: ";

                return false;
            }
        }

        const auto combined = ::android::nn::combineDimensions(kInitialDimensions, dimensions);
        if (!combined.has_value()) {
            LOG(ERROR) << "ShimBuffer::validateDimensions -- incompatible dimensions";
            return false;
        }
        return true;
    }

    ndk::ScopedAStatus copyFrom(const aidl::android::hardware::neuralnetworks::Memory& src,
                                const std::vector<int32_t>& dimensions) override {
        auto memory = convertFromHAL(mNnApi, src);

        if (!memory) {
            LOG(ERROR) << "Failed to convert HAL Memory to SL memory";
            return toAStatus(ErrorStatus::INVALID_ARGUMENT);
        }
        const auto unsignedDimensions = ::android::nn::toUnsigned(dimensions);
        if (!unsignedDimensions.has_value()) {
            return toAStatus(ErrorStatus::INVALID_ARGUMENT, unsignedDimensions.error().message);
        }

        if (!validateDimensions(unsignedDimensions.value())) {
            LOG(ERROR) << "Invalid dimensions";
            return toAStatus(ErrorStatus::INVALID_ARGUMENT);
        }
        Result result = memory->copyTo(*mMemory.get());

        // Special case expected error status for uninitialized source memory
        if (result == Result::BAD_DATA) {
            // NNAPI Runtime reports both uninitialized memory
            // and incompatible dimensions as BAD_DATA, but
            // VTS expects to see INVALID_ARGUMENT for bad dimensions,
            // and GENERAL_FAILURE for uninitialized memory.
            if (memory->getSize() != mMemory->getSize()) {
                return toAStatus(ErrorStatus::INVALID_ARGUMENT, "Incompatible sizes");
            }

            return toAStatus(ErrorStatus::GENERAL_FAILURE);
        }
        SLW2SAS_RETURN_IF_ERROR(result);
        return ndk::ScopedAStatus::ok();
    }

    ndk::ScopedAStatus copyTo(const Memory& dst) override {
        auto memory = convertFromHAL(mNnApi, dst);

        if (!memory) {
            LOG(ERROR) << "Failed to convert HAL Memory to SL memory";
            return toAStatus(ErrorStatus::INVALID_ARGUMENT);
        }

        Result result = mMemory->copyTo(*memory);
        // Special case expected error status for uninitialized source memory
        if (result == Result::BAD_DATA) {
            // NNAPI Runtime reports both uninitialized memory
            // and incompatible dimensions as BAD_DATA, but
            // VTS expects to see INVALID_ARGUMENT for bad dimensions,
            // and GENERAL_FAILURE for uninitialized memory.
            if (memory->getSize() != mMemory->getSize()) {
                return toAStatus(ErrorStatus::INVALID_ARGUMENT, "Incompatible sizes");
            }
            return toAStatus(ErrorStatus::GENERAL_FAILURE);
        }
        SLW2SAS_RETURN_IF_ERROR(result);
        return ndk::ScopedAStatus::ok();
    }

   private:
    const ::android::nn::Dimensions kInitialDimensions;
    const ::android::nn::OperandType kType;

    const NnApiSupportLibrary* mNnApi;
    std::shared_ptr<::android::nn::sl_wrapper::Memory> mMemory;
    const std::unique_ptr<ShimBufferTracker::Token> kToken;
};

::ndk::ScopedAStatus ShimDevice::allocate(const BufferDesc& desc,
                                          const std::vector<IPreparedModelParcel>& preparedModels,
                                          const std::vector<BufferRole>& inputRoles,
                                          const std::vector<BufferRole>& outputRoles,
                                          DeviceBuffer* buffer) {
    if (!isValidDimension(desc.dimensions)) {
        LOG(ERROR) << "ShimDriver::allocate -- passed invalid dimension values.";
        return toAStatus(ErrorStatus::INVALID_ARGUMENT,
                         "ShimDriver::allocate -- passed invalid dimension values");
    }
    ANeuralNetworksMemoryDesc* slDesc = nullptr;
    mNnapi->getFL5()->ANeuralNetworksMemoryDesc_create(&slDesc);
    const auto slDescGuard = ::android::base::make_scope_guard(
            [this, slDesc] { mNnapi->getFL5()->ANeuralNetworksMemoryDesc_free(slDesc); });

    auto unsignedDimensions = ::android::nn::toUnsigned(desc.dimensions).value();
    if (mNnapi->getFL5()->ANeuralNetworksMemoryDesc_setDimensions(slDesc, desc.dimensions.size(),
                                                                  unsignedDimensions.data()) !=
        ANEURALNETWORKS_NO_ERROR) {
        LOG(ERROR) << "ShimDriver::allocate -- ANeuralNetworksMemoryDesc_setDimensions fail.";
        return toAStatus(ErrorStatus::INVALID_ARGUMENT,
                         "ShimDriver::allocate -- ANeuralNetworksMemoryDesc_setDimensions fail");
    }

    constexpr auto getCompilation = [](IPreparedModel* preparedModel) -> const ShimPreparedModel* {
        const auto* samplePreparedModel = castToShimPreparedModel(preparedModel);
        if (samplePreparedModel == nullptr) {
            LOG(ERROR) << "ShimDriver::allocate -- unknown remote IPreparedModel.";
            return nullptr;
        }
        return samplePreparedModel;
    };

    std::optional<::android::nn::OperandType> type;
    std::vector<uint32_t> dimensions = ::android::nn::toUnsigned(desc.dimensions).value();

    for (const auto& role : inputRoles) {
        if (role.modelIndex < 0 || role.modelIndex >= preparedModels.size()) {
            LOG(ERROR) << "Invalid modelIndex value " << role.modelIndex;
            return toAStatus(ErrorStatus::INVALID_ARGUMENT,
                             "ShimDriver::allocate -- Input role modeIndex with invalid value");
        }
        auto preparedModel = preparedModels[role.modelIndex];
        if (preparedModel.preparedModel == nullptr) {
            return toAStatus(ErrorStatus::INVALID_ARGUMENT,
                             "ShimDriver::allocate -- nullptr model");
        }

        auto pmodel = getCompilation(preparedModel.preparedModel.get());
        if (pmodel == nullptr) {
            return toAStatus(ErrorStatus::INVALID_ARGUMENT,
                             "ShimDriver::allocate -- nullptr model");
        }

        auto result = mNnapi->getFL5()->ANeuralNetworksMemoryDesc_addInputRole(
                slDesc, pmodel->getCompilation().getHandle(), role.ioIndex, role.probability);

        if (result != ANEURALNETWORKS_NO_ERROR) {
            LOG(ERROR) << "SampleDriver::allocate -- ANeuralNetworksMemoryDesc_addInputRole fail.";
            return toAStatus(ErrorStatus::INVALID_ARGUMENT,
                             "ShimDriver::allocate -- ANeuralNetworksMemoryDesc_addInputRole fail");
        }

        const auto& model = pmodel->getMainModel();
        const auto& op = model.getOperands()[model.getInputs()[role.ioIndex]];
        auto operandType = static_cast<::android::nn::OperandType>(op.operandType.type);
        if (!type) {
            type = operandType;
        }
        if (dimensions.empty()) {
            dimensions = op.dimensions;
        }
    }

    for (const auto& role : outputRoles) {
        if (role.modelIndex < 0 || role.modelIndex >= preparedModels.size()) {
            LOG(ERROR) << "Invalid modelIndex value " << role.modelIndex;
            return toAStatus(ErrorStatus::INVALID_ARGUMENT,
                             "ShimDriver::allocate -- Ou0tput role modeIndex with invalid value");
        }
        auto preparedModel = preparedModels[role.modelIndex];
        if (preparedModel.preparedModel == nullptr) {
            return toAStatus(ErrorStatus::INVALID_ARGUMENT,
                             "ShimDriver::allocate -- nullptr model");
        }

        auto pmodel = getCompilation(preparedModel.preparedModel.get());
        if (pmodel == nullptr) {
            return toAStatus(ErrorStatus::INVALID_ARGUMENT,
                             "ShimDriver::allocate -- nullptr model");
        }

        auto result = mNnapi->getFL5()->ANeuralNetworksMemoryDesc_addOutputRole(
                slDesc, pmodel->getCompilation().getHandle(), role.ioIndex, role.probability);

        if (result != ANEURALNETWORKS_NO_ERROR) {
            LOG(ERROR) << "SampleDriver::allocate -- ANeuralNetworksMemoryDesc_addInputRole fail.";
            return toAStatus(ErrorStatus::INVALID_ARGUMENT,
                             "ShimDriver::allocate -- ANeuralNetworksMemoryDesc_addInputRole fail");
        }
        const auto& model = pmodel->getMainModel();
        const auto& op = model.getOperands()[model.getOutputs()[role.ioIndex]];
        auto operandType = static_cast<::android::nn::OperandType>(op.operandType.type);
        if (!type) {
            type = operandType;
        }
        if (dimensions.empty()) {
            dimensions = op.dimensions;
        }
    }

    auto typeSize = ::android::nn::getNonExtensionSize(*type, dimensions);
    if (!typeSize.has_value()) {
        return toAStatus(ErrorStatus::INVALID_ARGUMENT,
                         "ShimDriver::allocate -- failed to get underlying type size, "
                         "possibly an extension type");
    }

    mNnapi->getFL5()->ANeuralNetworksMemoryDesc_finish(slDesc);
    auto memory =
            std::make_shared<::android::nn::sl_wrapper::Memory>(mNnapi.get(), slDesc, *typeSize);

    if (!memory->isValid()) {
        LOG(ERROR) << "ShimDriver::allocate -- ANeuralNetworksMemory_createFromDesc failed.";
        return toAStatus(ErrorStatus::GENERAL_FAILURE,
                         "ShimDriver::allocate -- ANeuralNetworksMemory_createFromDesc failed");
    }

    auto token = mBufferTracker->add(memory);
    if (token == nullptr) {
        LOG(ERROR) << "ShimDriver::allocate -- ShimBufferTracker returned invalid token.";
        return toAStatus(ErrorStatus::GENERAL_FAILURE,
                         "ShimDriver::allocate -- ShimBufferTracker returned invalid token.");
    }
    const uint32_t tokenValue = token->get();
    auto shimbuffer = ndk::SharedRefBase::make<ShimBuffer>(mNnapi.get(), dimensions, *type,
                                                           std::move(memory), std::move(token));
    buffer->buffer = std::move(shimbuffer);
    buffer->token = tokenValue;

    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus ShimDevice::getCapabilities(Capabilities* capabilities) {
    *capabilities = mCapabilities;
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus ShimDevice::getNumberOfCacheFilesNeeded(NumberOfCacheFiles* numberOfCacheFiles) {
    *numberOfCacheFiles = mNumberOfCacheFiles;
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus ShimDevice::getSupportedExtensions(std::vector<Extension>* extensions) {
    *extensions = mExtensions;
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus ShimDevice::getSupportedOperations(const Model& model,
                                                      std::vector<bool>* supportedOperations) {
    const auto numOperations = model.main.operations.size();
    supportedOperations->resize(numOperations);

    ErrorStatus convertErrorStatus = ErrorStatus::NONE;
    std::vector<uint8_t> copiedOperandValues;
    auto modelAndMemory =
            convertFromHAL(mNnapi.get(), model, &copiedOperandValues, &convertErrorStatus);
    if (!modelAndMemory || modelAndMemory->models.empty()) {
        LOG(ERROR) << "Failed to convert HAL model to SL model";
        return toAStatus(convertErrorStatus);
    }

    auto annModel = modelAndMemory->models[0].getHandle();
    auto supportedOps = std::make_unique<bool[]>(numOperations);

    auto result = mNnapi->getFL5()->ANeuralNetworksModel_getSupportedOperationsForDevices(
            annModel, &mDevice, /*numDevices=*/1, supportedOps.get());
    SLW2SAS_RETURN_IF_ERROR(result);

    std::copy(supportedOps.get(), supportedOps.get() + numOperations, supportedOperations->begin());
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus ShimDevice::getType(DeviceType* type) {
    int32_t deviceType;
    auto result = mNnapi->getFL5()->ANeuralNetworksDevice_getType(mDevice, &deviceType);
    SLW2SAS_RETURN_IF_ERROR(result);
    *type = static_cast<DeviceType>(deviceType);
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus ShimDevice::getVersionString(std::string* versionString) {
    const char* buffer;
    auto result = mNnapi->getFL5()->ANeuralNetworksDevice_getVersion(mDevice, &buffer);
    SLW2SAS_RETURN_IF_ERROR(result);

    *versionString = std::string(buffer);
    return ndk::ScopedAStatus::ok();
}

static std::vector<int> getIntFds(const std::vector<::ndk::ScopedFileDescriptor>& scopedFds) {
    std::vector<int> fds;
    fds.reserve(scopedFds.size());
    for (const auto& scopedFd : scopedFds) {
        fds.push_back(scopedFd.get());
    }
    return fds;
}

ndk::ScopedAStatus ShimDevice::prepareModelCommon(
        const Model& model, ExecutionPreference preference, Priority priority, int64_t deadlineNs,
        const std::vector<::ndk::ScopedFileDescriptor>& modelCache,
        const std::vector<::ndk::ScopedFileDescriptor>& dataCache,
        const std::vector<uint8_t>& token, const std::vector<TokenValuePair>& compilationHints,
        const std::vector<ExtensionNameAndPrefix>& extensionNameToPrefix,
        const std::shared_ptr<IPreparedModelCallback>& callback) {
    // TODO(183398748): Run model preparation in detached thread.
    if (callback == nullptr) {
        return toAStatus(ErrorStatus::INVALID_ARGUMENT);
    }

    auto ndkPreference = convertToNDKPreference(preference);
    if (!ndkPreference) {
        callback->notify(ErrorStatus::INVALID_ARGUMENT, nullptr);
        return toAStatus(ErrorStatus::INVALID_ARGUMENT);
    }
    auto ndkPriority = convertToNDKPriority(priority);
    if (!ndkPriority) {
        callback->notify(ErrorStatus::INVALID_ARGUMENT, nullptr);
        return toAStatus(ErrorStatus::INVALID_ARGUMENT);
    }

    ErrorStatus convertErrorStatus = ErrorStatus::NONE;
    std::vector<uint8_t> copiedOperandValues;
    auto modelAndMemory =
            convertFromHAL(mNnapi.get(), model, &copiedOperandValues, &convertErrorStatus);

    if (!modelAndMemory || modelAndMemory->models.empty()) {
        callback->notify(ErrorStatus::INVALID_ARGUMENT, nullptr);
        return toAStatus(convertErrorStatus);
    }

    // b/185976051, past this point we pretend that compilation is asynchronous, and in
    /// case of error we return OK status, but communicate the error through the callback.
    auto compilation = ::android::nn::sl_wrapper::Compilation::createForDevice(
            mNnapi.get(), &modelAndMemory->models[0], mDevice);

    SLW2SAS_OK_RETURN_AND_ERROR_CALLBACK_IF_ERROR(compilation.first, callback);
    SLW2SAS_OK_RETURN_AND_ERROR_CALLBACK_IF_ERROR(compilation.second.setPreference(*ndkPreference),
                                                  callback);
    SLW2SAS_OK_RETURN_AND_ERROR_CALLBACK_IF_ERROR(compilation.second.setPriority(*ndkPriority),
                                                  callback);
    if (deadlineNs > -1) {
        std::chrono::time_point<::android::base::boot_clock> deadlinePoint(
                std::chrono::nanoseconds{deadlineNs});
        const auto currentTime = ::android::base::boot_clock::now();
        const auto timeoutDuration = std::chrono::nanoseconds(deadlinePoint - currentTime);
        if (timeoutDuration <= std::chrono::nanoseconds::zero()) {
            callback->notify(ErrorStatus::MISSED_DEADLINE_TRANSIENT, nullptr);
            return ndk::ScopedAStatus::ok();
        }
        SLW2SAS_OK_RETURN_AND_ERROR_CALLBACK_IF_ERROR(
                compilation.second.setTimeout(std::max<uint64_t>(1, timeoutDuration.count())),
                callback);
    }
    if (!modelCache.empty() || !dataCache.empty()) {
        SLW2SAS_OK_RETURN_AND_ERROR_CALLBACK_IF_ERROR(
                compilation.second.setCachingFromFds(getIntFds(modelCache), getIntFds(dataCache),
                                                     token),
                callback);
    }
    if (!compilationHints.empty() || !extensionNameToPrefix.empty()) {
        std::unordered_map<uint16_t, std::string> prefixToName;
        for (const auto [name, prefix] : extensionNameToPrefix) {
            prefixToName.emplace(prefix, name);
        }

        for (const auto& [token, value] : compilationHints) {
            const auto uToken = static_cast<uint32_t>(token);
            const auto prefix = ::android::nn::getExtensionPrefix(uToken);
            const auto attributeCodeWithinExtension = ::android::nn::getTypeWithinExtension(uToken);

            const auto it = prefixToName.find(prefix);
            if (it == prefixToName.end()) {
                callback->notify(ErrorStatus::INVALID_ARGUMENT, nullptr);
                return toAStatus(ErrorStatus::INVALID_ARGUMENT);
            }
            const std::string& extensionName = it->second;

            SLW2SAS_OK_RETURN_AND_ERROR_CALLBACK_IF_ERROR(
                    compilation.second.addExtensionAttribute(extensionName,
                                                             attributeCodeWithinExtension, value),
                    callback);
        }
    }

    SLW2SAS_OK_RETURN_AND_ERROR_CALLBACK_IF_ERROR(compilation.second.finish(), callback);

    const std::shared_ptr<ShimPreparedModel> preparedModel =
            ndk::SharedRefBase::make<ShimPreparedModel>(
                    mNnapi, mBufferTracker, std::move(compilation.second),
                    std::move(modelAndMemory->models), std::move(modelAndMemory->memory),
                    std::move(copiedOperandValues));

    callback->notify(ErrorStatus::NONE, preparedModel);
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus ShimDevice::prepareModel(
        const Model& model, ExecutionPreference preference, Priority priority, int64_t deadlineNs,
        const std::vector<::ndk::ScopedFileDescriptor>& modelCache,
        const std::vector<::ndk::ScopedFileDescriptor>& dataCache,
        const std::vector<uint8_t>& token,
        const std::shared_ptr<IPreparedModelCallback>& callback) {
    return prepareModelCommon(model, preference, priority, deadlineNs, modelCache, dataCache, token,
                              /*compilationHints=*/{}, /*extensionNameToPrefix=*/{}, callback);
}

ndk::ScopedAStatus ShimDevice::prepareModelWithConfig(
        const Model& model, const PrepareModelConfig& config,
        const std::shared_ptr<IPreparedModelCallback>& callback) {
    return prepareModelCommon(model, config.preference, config.priority, config.deadlineNs,
                              config.modelCache, config.dataCache, utils::toVec(config.cacheToken),
                              config.compilationHints, config.extensionNameToPrefix, callback);
}

ndk::ScopedAStatus ShimDevice::prepareModelFromCache(
        int64_t /*deadlineNs*/, const std::vector<::ndk::ScopedFileDescriptor>& /*modelCache*/,
        const std::vector<::ndk::ScopedFileDescriptor>& /*dataCache*/,
        const std::vector<uint8_t>& /*token*/,
        const std::shared_ptr<IPreparedModelCallback>& callback) {
    if (callback == nullptr) {
        return toAStatus(ErrorStatus::INVALID_ARGUMENT);
    }
    // The NNAPI runtime will attempt to call this before falling back to
    // ShimDevice::prepareModel(). This is not a LOG(ERROR) to avoid producing
    // misleading logcat messages on every compilation request because there is
    // technically nothing wrong.
    LOG(DEBUG) << "ShimDevice::prepareModelFromCache() is not supported. Use "
                  "ShimDevice::prepareModel() instead.";
    const auto ret = callback->notify(ErrorStatus::GENERAL_FAILURE, nullptr);
    return toAStatus(ErrorStatus::GENERAL_FAILURE);
}

}  // namespace aidl::android::hardware::neuralnetworks
