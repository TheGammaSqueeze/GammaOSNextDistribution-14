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

#include "LimitedSupportDevice.h"

#include <android-base/logging.h>
#include <nnapi/IBuffer.h>
#include <nnapi/IDevice.h>
#include <nnapi/IPreparedModel.h>
#include <nnapi/OperandTypes.h>
#include <nnapi/Result.h>
#include <nnapi/TypeUtils.h>
#include <nnapi/Types.h>
#include <nnapi/Validation.h>

#include <algorithm>
#include <any>
#include <chrono>
#include <functional>
#include <iterator>
#include <memory>
#include <optional>
#include <set>
#include <string>
#include <utility>
#include <vector>

#include "CanonicalDevice.h"

namespace android::nn::sample {
namespace {

Capabilities makeCapabilitiesFloatFast() {
    const Capabilities::PerformanceInfo defaultInfo = {.execTime = 1.0f, .powerUsage = 1.0f};
    const Capabilities::PerformanceInfo float32Info = {.execTime = 0.8f, .powerUsage = 1.2f};
    const Capabilities::PerformanceInfo relaxedInfo = {.execTime = 0.7f, .powerUsage = 1.1f};
    return makeCapabilities(defaultInfo, float32Info, relaxedInfo);
}

Capabilities makeCapabilitiesFloatSlow() {
    const Capabilities::PerformanceInfo defaultInfo = {.execTime = 1.0f, .powerUsage = 1.0f};
    const Capabilities::PerformanceInfo float32Info = {.execTime = 1.3f, .powerUsage = 0.7f};
    const Capabilities::PerformanceInfo relaxedInfo = {.execTime = 1.2f, .powerUsage = 0.6f};
    return makeCapabilities(defaultInfo, float32Info, relaxedInfo);
}

Capabilities makeCapabilitiesMinimal() {
    const Capabilities::PerformanceInfo defaultInfo = {.execTime = 1.0f, .powerUsage = 1.0f};
    const Capabilities::PerformanceInfo float32Info = {.execTime = 0.4f, .powerUsage = 0.5f};
    const Capabilities::PerformanceInfo relaxedInfo = {.execTime = 0.4f, .powerUsage = 0.5f};
    return makeCapabilities(defaultInfo, float32Info, relaxedInfo);
}

Capabilities makeCapabilitiesQuant() {
    const Capabilities::PerformanceInfo info = {.execTime = 50.0f, .powerUsage = 1.0f};
    return makeCapabilities(info, info, info);
}

GeneralResult<std::vector<bool>> getSupportedOperationsFloat(const Model& model) {
    const size_t count = model.main.operations.size();
    std::vector<bool> supported(count);
    for (size_t i = 0; i < count; i++) {
        const Operation& operation = model.main.operations[i];
        if (!isExtension(operation.type) && !operation.inputs.empty()) {
            const Operand& firstOperand = model.main.operands[operation.inputs[0]];
            supported[i] = firstOperand.type == OperandType::TENSOR_FLOAT32;
        }
    }
    return supported;
}

GeneralResult<std::vector<bool>> getSupportedOperationsMinimal(const Model& model) {
    const size_t count = model.main.operations.size();
    std::vector<bool> supported(count);
    // Simulate supporting just a few ops
    for (size_t i = 0; i < count; i++) {
        supported[i] = false;
        const Operation& operation = model.main.operations[i];
        switch (operation.type) {
            case OperationType::ADD:
            case OperationType::CONCATENATION:
            case OperationType::CONV_2D: {
                const Operand& firstOperand = model.main.operands[operation.inputs[0]];
                if (firstOperand.type == OperandType::TENSOR_FLOAT32) {
                    supported[i] = true;
                }
                break;
            }
            default:
                break;
        }
    }
    return supported;
}

bool isQuantized(OperandType opType) {
    return opType == OperandType::TENSOR_QUANT8_ASYMM ||
           opType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED;
}

GeneralResult<std::vector<bool>> getSupportedOperationsQuant(const Model& model) {
    const size_t count = model.main.operations.size();
    std::vector<bool> supported(count);
    for (size_t i = 0; i < count; i++) {
        const Operation& operation = model.main.operations[i];
        if (!isExtension(operation.type) && !operation.inputs.empty()) {
            const Operand& firstOperand = model.main.operands[operation.inputs[0]];
            supported[i] = isQuantized(firstOperand.type);
            if (operation.type == OperationType::SELECT) {
                const Operand& secondOperand = model.main.operands[operation.inputs[1]];
                supported[i] = isQuantized(secondOperand.type);
            }
        }
    }
    return supported;
}

SharedDevice makeDevice(std::string name, Capabilities capabilities,
                        LimitedSupportDevice::SupportedOperationsFunction getSupportedOperations) {
    auto device = std::make_shared<const Device>(std::move(name));
    auto limitedDevice = std::make_shared<const LimitedSupportDevice>(
            std::move(device), std::move(capabilities), std::move(getSupportedOperations));
    return limitedDevice;
}

}  // namespace

LimitedSupportDevice::LimitedSupportDevice(SharedDevice device, Capabilities capabilities,
                                           SupportedOperationsFunction supportedOperationsFunction)
    : kDevice(std::move(device)),
      kCapabilities(std::move(capabilities)),
      kSupportedOperationsFunction(std::move(supportedOperationsFunction)) {
    CHECK(kDevice != nullptr);
    CHECK(kSupportedOperationsFunction != nullptr);
    const auto result = validate(kCapabilities);
    CHECK(result.has_value()) << result.error();
}

const std::string& LimitedSupportDevice::getName() const {
    return kDevice->getName();
}

const std::string& LimitedSupportDevice::getVersionString() const {
    return kDevice->getVersionString();
}

Version LimitedSupportDevice::getFeatureLevel() const {
    return kDevice->getFeatureLevel();
}

DeviceType LimitedSupportDevice::getType() const {
    return kDevice->getType();
}

const std::vector<Extension>& LimitedSupportDevice::getSupportedExtensions() const {
    return kDevice->getSupportedExtensions();
}

const Capabilities& LimitedSupportDevice::getCapabilities() const {
    return kCapabilities;
}

std::pair<uint32_t, uint32_t> LimitedSupportDevice::getNumberOfCacheFilesNeeded() const {
    return kDevice->getNumberOfCacheFilesNeeded();
}

GeneralResult<void> LimitedSupportDevice::wait() const {
    return kDevice->wait();
}

GeneralResult<std::vector<bool>> LimitedSupportDevice::getSupportedOperations(
        const Model& model) const {
    return kSupportedOperationsFunction(model);
}

GeneralResult<SharedPreparedModel> LimitedSupportDevice::prepareModel(
        const Model& model, ExecutionPreference preference, Priority priority,
        OptionalTimePoint deadline, const std::vector<SharedHandle>& modelCache,
        const std::vector<SharedHandle>& dataCache, const CacheToken& token,
        const std::vector<TokenValuePair>& /*hints*/,
        const std::vector<ExtensionNameAndPrefix>& /*extensionNameToPrefix*/) const {
    const auto supportedOperations = NN_TRY(kSupportedOperationsFunction(model));
    constexpr auto id = [](auto v) { return v; };
    if (!std::all_of(supportedOperations.begin(), supportedOperations.end(), id)) {
        return NN_ERROR(nn::ErrorStatus::INVALID_ARGUMENT) << "Not all operations are supported";
    }
    return kDevice->prepareModel(model, preference, priority, deadline, modelCache, dataCache,
                                 token, {}, {});
}

GeneralResult<SharedPreparedModel> LimitedSupportDevice::prepareModelFromCache(
        OptionalTimePoint deadline, const std::vector<SharedHandle>& modelCache,
        const std::vector<SharedHandle>& dataCache, const CacheToken& token) const {
    return kDevice->prepareModelFromCache(deadline, modelCache, dataCache, token);
}

GeneralResult<SharedBuffer> LimitedSupportDevice::allocate(
        const BufferDesc& desc, const std::vector<SharedPreparedModel>& preparedModels,
        const std::vector<BufferRole>& inputRoles,
        const std::vector<BufferRole>& outputRoles) const {
    return kDevice->allocate(desc, preparedModels, inputRoles, outputRoles);
}

std::vector<SharedDevice> getExampleLimitedDevices() {
    SharedDevice device;
    std::vector<SharedDevice> devices;
    devices.reserve(4);

    device = makeDevice("nnapi-sample_float_fast", makeCapabilitiesFloatFast(),
                        getSupportedOperationsFloat);
    devices.push_back(std::move(device));

    device = makeDevice("nnapi-sample_float_slow", makeCapabilitiesFloatSlow(),
                        getSupportedOperationsFloat);
    devices.push_back(std::move(device));

    device = makeDevice("nnapi-sample_minimal", makeCapabilitiesMinimal(),
                        getSupportedOperationsMinimal);
    devices.push_back(std::move(device));

    device = makeDevice("nnapi-sample_quant", makeCapabilitiesQuant(), getSupportedOperationsQuant);
    devices.push_back(std::move(device));

    return devices;
}

}  // namespace android::nn::sample
