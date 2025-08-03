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

#include "CanonicalDevice.h"

#include <Tracing.h>
#include <android-base/logging.h>
#include <nnapi/IBuffer.h>
#include <nnapi/IDevice.h>
#include <nnapi/IPreparedModel.h>
#include <nnapi/OperandTypes.h>
#include <nnapi/Result.h>
#include <nnapi/Types.h>
#include <nnapi/Validation.h>

#include <algorithm>
#include <any>
#include <functional>
#include <iterator>
#include <memory>
#include <optional>
#include <set>
#include <string>
#include <utility>
#include <vector>

#include "CanonicalBuffer.h"
#include "CanonicalPreparedModel.h"

namespace android::nn::sample {
namespace {

Capabilities makeCapabilities() {
    constexpr float kPerf = 1.0f;
    const Capabilities::PerformanceInfo kPerfInfo = {.execTime = kPerf, .powerUsage = kPerf};

    constexpr OperandType kOperandsTypes[] = {
            OperandType::FLOAT32,
            OperandType::INT32,
            OperandType::UINT32,
            OperandType::TENSOR_FLOAT32,
            OperandType::TENSOR_INT32,
            OperandType::TENSOR_QUANT8_ASYMM,
            OperandType::BOOL,
            OperandType::TENSOR_QUANT16_SYMM,
            OperandType::TENSOR_FLOAT16,
            OperandType::TENSOR_BOOL8,
            OperandType::FLOAT16,
            OperandType::TENSOR_QUANT8_SYMM_PER_CHANNEL,
            OperandType::TENSOR_QUANT16_ASYMM,
            OperandType::TENSOR_QUANT8_SYMM,
            OperandType::TENSOR_QUANT8_ASYMM_SIGNED,
    };

    std::vector<Capabilities::OperandPerformance> operandPerformance;
    operandPerformance.reserve(std::size(kOperandsTypes));
    std::transform(std::begin(kOperandsTypes), std::end(kOperandsTypes),
                   std::back_inserter(operandPerformance), [kPerfInfo](OperandType op) {
                       return Capabilities::OperandPerformance{.type = op, .info = kPerfInfo};
                   });
    auto table =
            Capabilities::OperandPerformanceTable::create(std::move(operandPerformance)).value();

    return {.relaxedFloat32toFloat16PerformanceScalar = kPerfInfo,
            .relaxedFloat32toFloat16PerformanceTensor = kPerfInfo,
            .operandPerformance = std::move(table),
            .ifPerformance = kPerfInfo,
            .whilePerformance = kPerfInfo};
}

std::string toString(const Dimensions& dimensions) {
    std::ostringstream oss;
    oss << "[";
    for (size_t i = 0; i < dimensions.size(); ++i) {
        if (i != 0) oss << ", ";
        oss << dimensions[i];
    }
    oss << "]";
    return oss.str();
}

}  // namespace

Device::Device(std::string name, const IOperationResolver* operationResolver)
    : kName(std::move(name)), kOperationResolver(*operationResolver) {
    CHECK(operationResolver != nullptr);
    initVLogMask();
}

const std::string& Device::getName() const {
    return kName;
}

const std::string& Device::getVersionString() const {
    static const std::string kVersionString = "JUST_AN_EXAMPLE";
    return kVersionString;
}

Version Device::getFeatureLevel() const {
    return kVersionFeatureLevel8;
}

DeviceType Device::getType() const {
    return DeviceType::CPU;
}

const std::vector<Extension>& Device::getSupportedExtensions() const {
    static const std::vector<Extension> kExtensions = {/* No extensions. */};
    return kExtensions;
}

const Capabilities& Device::getCapabilities() const {
    static const Capabilities kCapabilities = makeCapabilities();
    return kCapabilities;
}

std::pair<uint32_t, uint32_t> Device::getNumberOfCacheFilesNeeded() const {
    return std::make_pair(/*numModelCache=*/0, /*numDataCache=*/0);
}

GeneralResult<void> Device::wait() const {
    return {};
}

GeneralResult<std::vector<bool>> Device::getSupportedOperations(const Model& model) const {
    VLOG(DRIVER) << "sample::Device::getSupportedOperations";

    // Validate arguments.
    if (const auto result = validate(model); !result.ok()) {
        return NN_ERROR(ErrorStatus::INVALID_ARGUMENT) << result.error();
    }

    // Mark all operations except extension operations as supported.
    std::vector<bool> supported;
    supported.reserve(model.main.operations.size());
    std::transform(model.main.operations.begin(), model.main.operations.end(),
                   std::back_inserter(supported), [](const Operation& operation) {
                       return !isExtensionOperationType(operation.type) &&
                              operation.type != OperationType::OEM_OPERATION;
                   });

    return supported;
}

GeneralResult<SharedPreparedModel> Device::prepareModel(
        const Model& model, ExecutionPreference preference, Priority priority,
        OptionalTimePoint deadline, const std::vector<SharedHandle>& /*modelCache*/,
        const std::vector<SharedHandle>& /*dataCache*/, const CacheToken& /*token*/,
        const std::vector<TokenValuePair>& /*hints*/,
        const std::vector<ExtensionNameAndPrefix>& /*extensionNameToPrefix*/) const {
    if (VLOG_IS_ON(DRIVER)) {
        VLOG(DRIVER) << "sample::Device::prepareModel";
        logModelToInfo(model);
    }

    // Validate arguments.
    if (const auto result = validate(model); !result.ok()) {
        return NN_ERROR(ErrorStatus::INVALID_ARGUMENT) << "Invalid Model: " << result.error();
    }
    if (const auto result = validate(preference); !result.ok()) {
        return NN_ERROR(ErrorStatus::INVALID_ARGUMENT)
               << "Invalid ExecutionPreference: " << result.error();
    }
    if (const auto result = validate(priority); !result.ok()) {
        return NN_ERROR(ErrorStatus::INVALID_ARGUMENT) << "Invalid Priority: " << result.error();
    }

    // Check if deadline has passed.
    if (hasDeadlinePassed(deadline)) {
        return NN_ERROR(ErrorStatus::MISSED_DEADLINE_PERSISTENT);
    }

    std::vector<RunTimePoolInfo> poolInfos;
    if (!setRunTimePoolInfosFromCanonicalMemories(&poolInfos, model.pools)) {
        return NN_ERROR() << "setRunTimePoolInfosFromCanonicalMemories failed";
    }

    // Create the prepared model.
    return std::make_shared<const PreparedModel>(model, preference, priority, &kOperationResolver,
                                                 kBufferTracker, std::move(poolInfos));
}

GeneralResult<SharedPreparedModel> Device::prepareModelFromCache(
        OptionalTimePoint /*deadline*/, const std::vector<SharedHandle>& /*modelCache*/,
        const std::vector<SharedHandle>& /*dataCache*/, const CacheToken& /*token*/) const {
    NNTRACE_FULL(NNTRACE_LAYER_DRIVER, NNTRACE_PHASE_COMPILATION,
                 "sample::Device::prepareModelFromCache");
    return NN_ERROR(ErrorStatus::GENERAL_FAILURE)
           << "prepareModelFromCache not supported on sample::Device::prepareModelFromCache("
           << kName << ")";
}

GeneralResult<SharedBuffer> Device::allocate(const BufferDesc& desc,
                                             const std::vector<SharedPreparedModel>& preparedModels,
                                             const std::vector<BufferRole>& inputRoles,
                                             const std::vector<BufferRole>& outputRoles) const {
    VLOG(DRIVER) << "sample::Device::allocate";
    std::set<PreparedModelRole> roles;
    Operand operand;
    auto getModel = [](const SharedPreparedModel& preparedModel) -> const Model* {
        std::any resource = preparedModel->getUnderlyingResource();
        const Model** maybeModel = std::any_cast<const Model*>(&resource);
        if (maybeModel == nullptr) {
            LOG(ERROR) << "sample::Device::allocate -- unknown remote IPreparedModel.";
            return nullptr;
        }
        return *maybeModel;
    };
    if (const auto result = validateMemoryDesc(desc, preparedModels, inputRoles, outputRoles,
                                               getModel, &roles, &operand);
        !result.ok()) {
        return NN_ERROR(ErrorStatus::INVALID_ARGUMENT)
               << "sample::Device::allocate -- validation failed: " << result.error();
    }

    if (isExtensionOperandType(operand.type)) {
        return NN_ERROR(ErrorStatus::GENERAL_FAILURE)
               << "sample::Device::allocate -- does not support extension type.";
    }

    // TODO(xusongw): Support allocating buffers with unknown dimensions or rank.
    uint32_t size = nonExtensionOperandSizeOfData(operand.type, operand.dimensions);
    VLOG(DRIVER) << "sample::Device::allocate -- type = " << operand.type
                 << ", dimensions = " << toString(operand.dimensions) << ", size = " << size;
    if (size == 0) {
        return NN_ERROR(ErrorStatus::GENERAL_FAILURE)
               << "sample::Device::allocate -- does not support dynamic output shape.";
    }

    auto bufferWrapper = ManagedBuffer::create(size, std::move(roles), operand);
    if (bufferWrapper == nullptr) {
        return NN_ERROR(ErrorStatus::GENERAL_FAILURE)
               << "sample::Device::allocate -- not enough memory.";
    }

    auto token = kBufferTracker->add(bufferWrapper);
    if (token == nullptr) {
        return NN_ERROR(ErrorStatus::GENERAL_FAILURE)
               << "sample::Device::allocate -- BufferTracker returned invalid token.";
    }

    auto sampleBuffer = std::make_shared<const Buffer>(std::move(bufferWrapper), std::move(token));
    VLOG(DRIVER) << "sample::Device::allocate -- successfully allocates the requested memory";
    return sampleBuffer;
}

}  // namespace android::nn::sample
