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

#define LOG_TAG "ShimConverter"

#include "ShimConverter.h"

#include <aidlcommonsupport/NativeHandle.h>
#include <android-base/logging.h>
#include <android-base/mapped_file.h>
#include <android-base/scopeguard.h>
#include <android/hardware_buffer.h>
#include <cutils/native_handle.h>
#include <nnapi/TypeUtils.h>
#include <nnapi/hal/aidl/Conversions.h>
#include <nnapi/hal/aidl/Utils.h>
#include <sys/mman.h>
#include <vndk/hardware_buffer.h>

#include <algorithm>
#include <memory>
#include <string>
#include <utility>
#include <vector>

using namespace ::android::nn::sl_wrapper;

namespace aidl::android::hardware::neuralnetworks {

namespace {

// Assumes that isValid(model) holds
ANeuralNetworksModel* convertSubgraphFromHAL(
        const NnApiSupportLibrary* nnapi,
        const std::vector<std::unique_ptr<::android::nn::sl_wrapper::Memory>>& memoryPools,
        const neuralnetworks::Model& model,
        std::vector<std::optional<::android::nn::sl_wrapper::Model>>* allModels,
        size_t subgraphIndex, const std::vector<uint8_t>& copiedOperandValues,
        ErrorStatus* errorStatus) {
    *errorStatus = ErrorStatus::NONE;
    if (allModels == nullptr || subgraphIndex >= (*allModels).size()) {
        *errorStatus = ErrorStatus::INVALID_ARGUMENT;
        return nullptr;
    }
    if ((*allModels)[subgraphIndex].has_value()) {
        return (*allModels)[subgraphIndex]->getHandle();
    }

    const auto& subgraph = subgraphIndex == 0 ? model.main : model.referenced[subgraphIndex - 1];
    ::android::nn::sl_wrapper::Model resultModel(nnapi);

    resultModel.relaxComputationFloat32toFloat16(model.relaxComputationFloat32toFloat16);

    auto getExtensionName = [&](uint16_t prefix) -> const std::string* {
        for (const auto& nameToPrefix : model.extensionNameToPrefix) {
            if (prefix == nameToPrefix.prefix) {
                return &nameToPrefix.name;
            }
        }
        return nullptr;
    };

    for (int i = 0; i < subgraph.operands.size(); ++i) {
        const auto& operand = subgraph.operands[i];

        const std::vector<uint32_t> dimensions =
                ::android::nn::toUnsigned(operand.dimensions).value();

        ::android::nn::wrapper::OperandType operandType(
                static_cast<::android::nn::wrapper::Type>(operand.type), dimensions, operand.scale,
                operand.zeroPoint);

        if (operand.type == OperandType::TENSOR_QUANT8_SYMM_PER_CHANNEL) {
            const auto& params = operand.extraParams->get<OperandExtraParams::Tag::channelQuant>();
            operandType.channelQuant = ::android::nn::wrapper::SymmPerChannelQuantParams(
                    params.scales, static_cast<uint32_t>(params.channelDim));
        }

        if (::android::nn::isExtension(static_cast<::android::nn::OperandType>(operand.type))) {
            uint16_t extensionPrefix =
                    ::android::nn::getExtensionPrefix(static_cast<uint32_t>(operand.type));
            uint16_t typeWithinExtension =
                    ::android::nn::getTypeWithinExtension(static_cast<uint32_t>(operand.type));

            auto* extensionName = getExtensionName(extensionPrefix);
            if (extensionName == nullptr) {
                LOG(ERROR) << "Unknown extension prefix " << extensionPrefix;
                *errorStatus = ErrorStatus::INVALID_ARGUMENT;
                return nullptr;
            }
            resultModel.getExtensionOperandType(*extensionName, typeWithinExtension,
                                                &operandType.operandType.type);
            if (!resultModel.isValid()) {
                LOG(ERROR) << "Failed to get extension operand with index " << i;
                *errorStatus = ErrorStatus::INVALID_ARGUMENT;
                return nullptr;
            }
        }

        uint32_t operandIndex = resultModel.addOperand(&operandType);
        if (!resultModel.isValid()) {
            LOG(ERROR) << "Failed to add operand with index " << i;
            *errorStatus = ErrorStatus::INVALID_ARGUMENT;
            return nullptr;
        }

        if (operand.extraParams &&
            operand.extraParams->getTag() == OperandExtraParams::Tag::extension) {
            const auto& extensionData =
                    operand.extraParams->get<OperandExtraParams::Tag::extension>();
            resultModel.setOperandExtensionData(operandIndex, extensionData.data(),
                                                extensionData.size());
            if (!resultModel.isValid()) {
                LOG(ERROR) << "Failed to add extension data for operand with index " << i;
                *errorStatus = ErrorStatus::INVALID_ARGUMENT;
                return nullptr;
            }
        }

        switch (operand.lifetime) {
            case OperandLifeTime::CONSTANT_COPY: {
                if (operand.location.length + operand.location.offset >
                    model.operandValues.size()) {
                    *errorStatus = ErrorStatus::INVALID_ARGUMENT;
                    return nullptr;
                }

                if (operand.location.length <=
                    ANEURALNETWORKS_MAX_SIZE_OF_IMMEDIATELY_COPIED_VALUES) {
                    resultModel.setOperandValue(
                            i, model.operandValues.data() + operand.location.offset,
                            operand.location.length);
                } else {
                    // If length is larger than 128 bytes, we are responsible for making sure
                    // that value outlives the model. If this case exists, then we created
                    // an internal copy, that is used here:
                    resultModel.setOperandValue(
                            i, copiedOperandValues.data() + operand.location.offset,
                            operand.location.length);
                }
                break;
            }
            case OperandLifeTime::CONSTANT_POOL: {
                if (operand.location.poolIndex >= memoryPools.size()) {
                    *errorStatus = ErrorStatus::INVALID_ARGUMENT;
                    return nullptr;
                }
                resultModel.setOperandValueFromMemory(
                        i, memoryPools[operand.location.poolIndex].get(), operand.location.offset,
                        operand.location.length);
                break;
            }
            case OperandLifeTime::SUBGRAPH: {
                ErrorStatus otherErrorStatus = ErrorStatus::NONE;
                auto subgraph = convertSubgraphFromHAL(nnapi, memoryPools, model, allModels,
                                                       operand.location.offset + 1,
                                                       copiedOperandValues, &otherErrorStatus);
                if (subgraph) {
                    resultModel.setOperandValueFromModel(i, subgraph);
                } else {
                    LOG(ERROR) << "Failed to set subgraph operand value";
                    *errorStatus = otherErrorStatus;
                    return nullptr;
                }
                break;
            }
            case OperandLifeTime::NO_VALUE: {
                resultModel.setOperandValue(i, nullptr, 0);
                break;
            }
            case OperandLifeTime::TEMPORARY_VARIABLE:
            case OperandLifeTime::SUBGRAPH_OUTPUT:
            case OperandLifeTime::SUBGRAPH_INPUT: {
                break;
            }
            default:
                LOG(ERROR) << "Invalid operand type: " << static_cast<int>(operand.lifetime);
                *errorStatus = ErrorStatus::INVALID_ARGUMENT;
                return nullptr;
        }

        if (!resultModel.isValid()) {
            LOG(ERROR) << "Failed to add operand with index " << i;
            *errorStatus = ErrorStatus::INVALID_ARGUMENT;
            return nullptr;
        }
    }

    for (int i = 0; i < subgraph.operations.size(); ++i) {
        const auto& operation = subgraph.operations[i];

        std::vector<uint32_t> inputs(operation.inputs.begin(), operation.inputs.end());
        std::vector<uint32_t> outputs(operation.outputs.begin(), operation.outputs.end());

        int operationType = static_cast<int>(operation.type);
        if (::android::nn::isExtension(static_cast<::android::nn::OperationType>(operationType))) {
            uint16_t extensionPrefix =
                    ::android::nn::getExtensionPrefix(static_cast<uint32_t>(operationType));
            uint16_t typeWithinExtension =
                    ::android::nn::getTypeWithinExtension(static_cast<uint32_t>(operationType));
            auto* extensionName = getExtensionName(extensionPrefix);
            if (extensionName == nullptr) {
                LOG(ERROR) << "Unknown extension prefix " << extensionPrefix;
                *errorStatus = ErrorStatus::INVALID_ARGUMENT;
                return nullptr;
            }
            resultModel.getExtensionOperationType(*extensionName, typeWithinExtension,
                                                  &operationType);
            if (!resultModel.isValid()) {
                LOG(ERROR) << "Failed to get extension operation with index " << i;
                *errorStatus = ErrorStatus::INVALID_ARGUMENT;
                return nullptr;
            }
        }

        resultModel.addOperation(operationType, inputs, outputs);

        if (!resultModel.isValid()) {
            LOG(ERROR) << "Failed to add operation with index " << i;
            *errorStatus = ErrorStatus::INVALID_ARGUMENT;
            return nullptr;
        }
    }

    std::vector<uint32_t> inputIndexes(subgraph.inputIndexes.begin(), subgraph.inputIndexes.end());
    std::vector<uint32_t> outputIndexes(subgraph.outputIndexes.begin(),
                                        subgraph.outputIndexes.end());

    resultModel.identifyInputsAndOutputs(inputIndexes, outputIndexes);
    if (!resultModel.isValid()) {
        LOG(ERROR) << "Model identifyInputsAndOutputs failed";
        *errorStatus = ErrorStatus::INVALID_ARGUMENT;
        return nullptr;
    }

    if (resultModel.finish() != Result::NO_ERROR) {
        LOG(ERROR) << "Model finish failed";
        *errorStatus = ErrorStatus::INVALID_ARGUMENT;
        return nullptr;
    }

    if (!resultModel.isValid()) {
        LOG(ERROR) << "Invalid model";
        *errorStatus = ErrorStatus::INVALID_ARGUMENT;
        return nullptr;
    }

    (*allModels)[subgraphIndex] = std::move(resultModel);
    return (*allModels)[subgraphIndex]->getHandle();
}

// This is needed for CONSTANT_COPY operands > 128 bytes, we have to
// store them in intenal buffer
bool needsCopiedOperandValues(const neuralnetworks::Model& model) {
    for (int sindex = 0; sindex < model.referenced.size() + 1; ++sindex) {
        const auto& subgraph = sindex == 0 ? model.main : model.referenced[sindex - 1];
        for (int i = 0; i < subgraph.operands.size(); ++i) {
            const auto& operand = subgraph.operands[i];

            if (operand.lifetime == OperandLifeTime::CONSTANT_COPY) {
                if (operand.location.length >
                    ANEURALNETWORKS_MAX_SIZE_OF_IMMEDIATELY_COPIED_VALUES) {
                    return true;
                }
            }
        }
    }
    return false;
}

bool isValid(const Subgraph& subgraph) {
    // Either the operand has a known value before model execution begins, or we've seen a writer
    // for this operand while walking operands in execution order. Initialize to known operands.
    std::vector<bool> operandValueKnown;
    operandValueKnown.reserve(subgraph.operands.size());
    std::transform(subgraph.operands.begin(), subgraph.operands.end(),
                   std::back_inserter(operandValueKnown), [](const Operand& operand) {
                       return operand.lifetime != OperandLifeTime::TEMPORARY_VARIABLE &&
                              operand.lifetime != OperandLifeTime::SUBGRAPH_OUTPUT;
                   });

    // Validate that operations are sorted into execution order.
    //
    // If there is a cycle in the graph, the operations will not
    // appear to be sorted into execution order: Some operation will
    // have an input for which operandValueKnown[] is false.
    for (size_t i = 0; i < subgraph.operations.size(); ++i) {
        const auto& operation = subgraph.operations[i];

        for (size_t j = 0; j < operation.inputs.size(); ++j) {
            const uint32_t k = operation.inputs[j];
            if (!operandValueKnown[k]) {
                LOG(ERROR) << "Operation " << i << " input " << j << " (operand " << k
                           << ") is read before it is written";
                return false;
            }
        }

        for (size_t j = 0; j < operation.outputs.size(); ++j) {
            const uint32_t k = operation.outputs[j];
            // Assuming validateOperations() has not returned an error, we know that this output is
            // TEMPORARY_VARIABLE or MODEL_OUTPUT, and so the only way operandValueKnown[k] can be
            // true is if we've already seen a writer for this operand.
            if (operandValueKnown[k]) {
                LOG(ERROR) << "Operation " << i << " output " << j << " (operand " << k
                           << ") has already been written";
                return false;
            }
            operandValueKnown[k] = true;
        }
    }

    // Verify all operands are written.
    for (size_t i = 0; i < subgraph.operands.size(); ++i) {
        if (!operandValueKnown[i]) {
            LOG(ERROR) << "Operand " << i << " is never written";
            return false;
        }
        const auto& operand = subgraph.operands[i];

        if (operand.lifetime == OperandLifeTime::SUBGRAPH_OUTPUT) {
            if (std::find(subgraph.outputIndexes.begin(), subgraph.outputIndexes.end(), i) ==
                subgraph.outputIndexes.end()) {
                LOG(ERROR) << "Op with output liftime, but not on output list: " << i;
                return false;
            }
        }
    }

    // Validate input and output lifetime
    for (auto index : subgraph.inputIndexes) {
        if (subgraph.operands[index].lifetime != OperandLifeTime::SUBGRAPH_INPUT) {
            LOG(ERROR) << "Input with index" << index << " has invalid lifetime";
            return false;
        }
    }
    for (auto index : subgraph.outputIndexes) {
        if (subgraph.operands[index].lifetime != OperandLifeTime::SUBGRAPH_OUTPUT) {
            LOG(ERROR) << "Output with index" << index << " has invalid lifetime";
            return false;
        }
    }

    // TODO(b/77871786): verify that every operation has at least one output operand that is read?
    return true;
}

}  // namespace

bool isValid(const neuralnetworks::Model& model) {
    return (isValid(model.main) &&
            std::all_of(model.referenced.begin(), model.referenced.end(),
                        [](const Subgraph& subgraph) { return isValid(subgraph); }));
}

std::optional<ShimConvertedModel> convertFromHAL(const NnApiSupportLibrary* nnapi,
                                                 const neuralnetworks::Model& model,
                                                 std::vector<uint8_t>* copiedOperandValues,
                                                 ErrorStatus* errorStatus) {
    CHECK(copiedOperandValues != nullptr);

    *errorStatus = ErrorStatus::NONE;

    // Using this pulls in OperationResolver and huge chunk of dependencies.
    // TODO(172925288): Replace as followup work
    //    if (!::aidl::android::hardware::neuralnetworks::utils::valid(model)) {
    if (!isValid(model)) {
        LOG(ERROR) << "Invalid HAL model, failed to convert into SL model";
        *errorStatus = ErrorStatus::INVALID_ARGUMENT;
        return std::nullopt;
    }

    std::vector<std::unique_ptr<::android::nn::sl_wrapper::Memory>> memoryPools;
    memoryPools.reserve(model.pools.size());
    for (const auto& pool : model.pools) {
        std::unique_ptr<::android::nn::sl_wrapper::Memory> memory = convertFromHAL(nnapi, pool);
        if (!memory) {
            LOG(ERROR) << "Failed to convert HAL memory into SL memory";
            *errorStatus = ErrorStatus::INVALID_ARGUMENT;
            return std::nullopt;
        }
        memoryPools.push_back(std::move(memory));
    }

    std::vector<std::optional<::android::nn::sl_wrapper::Model>> allModels(model.referenced.size() +
                                                                           1);

    if (needsCopiedOperandValues(model)) {
        *copiedOperandValues = model.operandValues;
    }

    for (size_t i = 0; i < allModels.size(); ++i) {
        if (convertSubgraphFromHAL(nnapi, memoryPools, model, &allModels, i, *copiedOperandValues,
                                   errorStatus) == nullptr) {
            LOG(ERROR) << "Failed to convert HAL subgraphs into SL subgraphs, index: " << i;
            // Error status already set by convertSubgraphFromHAL
            return std::nullopt;
        }
    }

    std::vector<::android::nn::sl_wrapper::Model> result;
    result.reserve(allModels.size());
    for (size_t i = 0; i < allModels.size(); ++i) {
        if (!allModels[i].has_value()) {
            LOG(ERROR) << "Missing SL subgraph";
            *errorStatus = ErrorStatus::INVALID_ARGUMENT;
            return std::nullopt;
        }
        result.push_back(std::move(*allModels[i]));
    }

    return ShimConvertedModel{.memory = std::move(memoryPools), .models = std::move(result)};
}

std::unique_ptr<::android::nn::sl_wrapper::Memory> convertFromHAL(
        const NnApiSupportLibrary* nnapi, const neuralnetworks::Memory& pool) {
    using Tag = neuralnetworks::Memory::Tag;
    switch (pool.getTag()) {
        case Tag::ashmem: {
            const auto& ashmem = pool.get<Tag::ashmem>();
            size_t size = ashmem.size;
            int fd = ashmem.fd.get();

            auto memory = std::make_unique<::android::nn::sl_wrapper::Memory>(
                    nnapi, size, PROT_READ | PROT_WRITE, fd, 0, /*ownsFd=*/false);
            if (!memory->isValid()) {
                return nullptr;
            }
            return memory;
        }
        case Tag::mappableFile: {
            const auto& mappableFile = pool.get<Tag::mappableFile>();
            size_t size = mappableFile.length;
            int fd = mappableFile.fd.get();
            int prot = mappableFile.prot & (PROT_READ | PROT_WRITE);
            size_t offset = mappableFile.offset;

            auto memory = std::make_unique<::android::nn::sl_wrapper::Memory>(
                    nnapi, size, prot, fd, offset, /*ownsFd=*/false);
            if (!memory->isValid()) {
                return nullptr;
            }
            return memory;
        }
        case Tag::hardwareBuffer: {
            const auto& hardwareBuffer = pool.get<Tag::hardwareBuffer>();

            native_handle_t* handle = ::android::dupFromAidl(hardwareBuffer.handle);
            if (handle == nullptr) {
                LOG(ERROR) << "Dup of the hardware_buffer_blob memory pool failed";
                return nullptr;
            }
            const auto handleGuard = ::android::base::make_scope_guard([handle] {
                native_handle_close(handle);
                native_handle_delete(handle);
            });
            for (size_t i = 0; i < handle->numFds; ++i) {
                if (handle->data[i] == -1) {
                    LOG(ERROR) << "Dup of the hardware_buffer_blob memory pool failed";
                    return nullptr;
                }
            }

            const AHardwareBuffer_Desc desc{
                    .width = static_cast<uint32_t>(hardwareBuffer.description.width),
                    .height = static_cast<uint32_t>(hardwareBuffer.description.height),
                    .layers = static_cast<uint32_t>(hardwareBuffer.description.layers),
                    .format = static_cast<uint32_t>(hardwareBuffer.description.format),
                    .usage = static_cast<uint64_t>(hardwareBuffer.description.usage),
                    .stride = static_cast<uint32_t>(hardwareBuffer.description.stride),
            };
            AHardwareBuffer* ahwb = nullptr;
            const ::android::status_t status = AHardwareBuffer_createFromHandle(
                    &desc, handle, AHARDWAREBUFFER_CREATE_FROM_HANDLE_METHOD_CLONE, &ahwb);
            if (status != ::android::NO_ERROR) {
                LOG(ERROR) << "createFromHandle failed";
                return nullptr;
            }

            const bool isBlob = desc.format == AHARDWAREBUFFER_FORMAT_BLOB;
            const size_t size = isBlob ? desc.width : 0;

            // Takes ownership of hardwareBuffer, handle gets closed
            auto memory =
                    std::make_unique<::android::nn::sl_wrapper::Memory>(nnapi, ahwb,
                                                                        /*ownAHB=*/true, size);
            if (!memory->isValid()) {
                return nullptr;
            }
            return memory;
        }
    }
    LOG(ERROR) << "Can't convert to SL Memory, unknown pool tag: "
               << utils::underlyingType(pool.getTag());
    return nullptr;
}

}  // namespace aidl::android::hardware::neuralnetworks
