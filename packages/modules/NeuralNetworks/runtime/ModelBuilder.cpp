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

#define LOG_TAG "ModelBuilder"

#include "ModelBuilder.h"

#include <GraphDump.h>
#include <LegacyUtils.h>
#include <ModelUtils.h>
#include <android-base/logging.h>
#include <nnapi/Validation.h>

#include <algorithm>
#include <map>
#include <memory>
#include <set>
#include <utility>
#include <vector>

#include "CompilationBuilder.h"
#include "Manager.h"
#include "ModelArchHasher.h"
#include "TypeManager.h"

namespace android {
namespace nn {

// The maximum number of operands and operations that a model may have.
const uint32_t MAX_NUMBER_OF_OPERANDS = 0xFFFFFFFE;
const uint32_t MAX_NUMBER_OF_OPERATIONS = 0xFFFFFFFE;

#define NN_VALIDATE_NULL_OR_SIZED(tag, data, length)                                          \
    if ((data == nullptr) != (length == 0)) {                                                 \
        LOG(ERROR) << "ANeuralNetworksModel_" << tag << " " << #data << " is "                \
                   << (data == nullptr ? "null" : "not null") << " but " << #length << " is " \
                   << length;                                                                 \
        return ANEURALNETWORKS_BAD_DATA;                                                      \
    }

template <typename Type>
static std::vector<Type> makeVector(const Type* data, uint32_t length) {
    return length > 0 ? std::vector<Type>(data, data + length) : std::vector<Type>();
}

bool ModelBuilder::badState(const char* name) {
    if (mCompletedModel) {
        LOG(ERROR) << "ANeuralNetworksModel_" << name << " can't modify after model finished";
        return true;
    }
    if (mInvalidModel) {
        LOG(ERROR) << "ANeuralNetworksModel_" << name << " can't modify an invalid model";
        return true;
    }
    return false;
}

int ModelBuilder::getExtensionType(const char* extensionName, uint16_t typeWithinExtension,
                                   int32_t* type) {
    return TypeManager::get()->getExtensionType(extensionName, typeWithinExtension, type)
                   ? ANEURALNETWORKS_NO_ERROR
                   : ANEURALNETWORKS_BAD_DATA;
}

int ModelBuilder::addOperand(const ANeuralNetworksOperandType& type) {
    if (badState("addOperand")) {
        return ANEURALNETWORKS_BAD_STATE;
    }

    OperandType operandType = static_cast<OperandType>(type.type);
    if (isExtension(operandType) && !TypeManager::get()->areExtensionsAllowed()) {
        LOG(ERROR) << "Extensions are not supported for this process.";
        return ANEURALNETWORKS_BAD_DATA;
    }
    bool isOemOperand =
            operandType == OperandType::OEM || operandType == OperandType::TENSOR_OEM_BYTE;
    if (isOemOperand && !mHasOEMOperand) {
        LOG(WARNING) << "OEM data type is deprecated. Use Extensions instead.";
    }

    const Extension::OperandTypeInformation* info = nullptr;
    if (isExtension(operandType) &&
        !TypeManager::get()->getExtensionOperandTypeInfo(operandType, &info)) {
        LOG(ERROR) << "Extension operand type " << operandType << " is not registered";
        return ANEURALNETWORKS_BAD_DATA;
    }
    NN_VALIDATE_NULL_OR_SIZED("addOperand", type.dimensions, type.dimensionCount);
    Operand operand = {
            .type = operandType,
            .dimensions = makeVector(type.dimensions, type.dimensionCount),
            .scale = type.scale,
            .zeroPoint = type.zeroPoint,
            .lifetime = Operand::LifeTime::TEMPORARY_VARIABLE,
            .location = {.poolIndex = 0, .offset = 0, .length = 0},
            .extraParams = {},
    };
    if (auto result = validateOperandType(operand, info, "ANeuralNetworksModel_addOperand", true);
        !result.ok()) {
        LOG(ERROR) << result.error();
        return ANEURALNETWORKS_BAD_DATA;
    }

    size_t idx = mOperands.size();
    if (idx >= MAX_NUMBER_OF_OPERANDS) {
        LOG(ERROR) << "ANeuralNetworksModel_addOperand exceed max operands";
        return ANEURALNETWORKS_BAD_DATA;
    }

    mOperands.push_back(std::move(operand));
    mHasOEMOperand |= isOemOperand;
    mHasControlFlow |= (operandType == OperandType::SUBGRAPH);
    return ANEURALNETWORKS_NO_ERROR;
}

int ModelBuilder::setOperandValue(uint32_t index, const void* buffer, size_t length) {
    VLOG(MODEL) << __func__ << " for operand " << index << " size " << length;
    if (badState("setOperandValue")) {
        return ANEURALNETWORKS_BAD_STATE;
    }

    if (index >= operandCount()) {
        LOG(ERROR) << "ANeuralNetworksModel_setOperandValue setting operand " << index << " of "
                   << operandCount();
        return ANEURALNETWORKS_BAD_DATA;
    }
    Operand& operand = mOperands[index];
    NN_VALIDATE_NULL_OR_SIZED("setOperandValue", buffer, length);
    if (buffer == nullptr) {
        operand.lifetime = Operand::LifeTime::NO_VALUE;
        // The location is unused and is set to zeros.
        operand.location = {.poolIndex = 0, .offset = 0, .length = 0};
    } else {
        if (TypeManager::get()->isTensorType(operand.type) &&
            tensorHasUnspecifiedDimensions(operand)) {
            LOG(ERROR) << "ANeuralNetworksModel_setOperandValue setting operand " << index
                       << " which has operand type that is not fully specified";
            return ANEURALNETWORKS_BAD_DATA;
        }
        if (length > 0xFFFFFFFF) {
            LOG(ERROR) << "ANeuralNetworksModel_setOperandValue value length of " << length
                       << " exceeds max size";
            return ANEURALNETWORKS_BAD_DATA;
        }
        uint32_t valueLength = static_cast<uint32_t>(length);
        if (operand.type != OperandType::OEM) {
            uint32_t neededLength = TypeManager::get()->getSizeOfData(operand);
            if (neededLength != valueLength) {
                LOG(ERROR) << "ANeuralNetworksModel_setOperandValue setting " << valueLength
                           << " bytes when needing " << neededLength;
                return ANEURALNETWORKS_BAD_DATA;
            }
        }
        if (valueLength <= ANEURALNETWORKS_MAX_SIZE_OF_IMMEDIATELY_COPIED_VALUES) {
            uint32_t existingSize = static_cast<uint32_t>(mSmallOperandValues.size());
            uint32_t extraBytes = alignBytesNeeded(existingSize, valueLength);
            mSmallOperandValues.resize(existingSize + extraBytes + valueLength);
            operand.lifetime = Operand::LifeTime::CONSTANT_COPY;
            operand.location = {
                    .poolIndex = 0, .offset = existingSize + extraBytes, .length = valueLength};
            memcpy(&mSmallOperandValues[operand.location.offset], buffer, valueLength);
            VLOG(MODEL) << "Copied small value to offset " << operand.location.offset;
        } else {
            VLOG(MODEL) << "Saving large value";
            operand.lifetime = Operand::LifeTime::CONSTANT_REFERENCE;
            // The values for poolIndex and offset will be set when the model is finished.
            typedef decltype(operand.location.poolIndex) PoolIndexType;
            typedef decltype(operand.location.offset) OffsetType;
            operand.location = {.poolIndex = ~PoolIndexType(0),
                                .offset = ~OffsetType(0),
                                .length = valueLength};
            // We keep track of the buffers. We'll allocate the shared memory only
            // once we know the total size, to avoid needless copies.
            mLargeOperandValues.push_back(LargeValue{.operandIndex = index, .buffer = buffer});
        }
    }
    return ANEURALNETWORKS_NO_ERROR;
}

int ModelBuilder::setOperandValueFromModel(uint32_t index, const ModelBuilder* value) {
    VLOG(MODEL) << __func__ << " for operand " << index << " model " << value;
    if (badState("setOperandValueFromModel")) {
        return ANEURALNETWORKS_BAD_STATE;
    }
    if (!value->mCompletedModel) {
        LOG(ERROR) << "ANeuralNetworksModel_setOperandValueFromModel value model must be finished";
        return ANEURALNETWORKS_BAD_STATE;
    }
    if (value->mInvalidModel) {
        LOG(ERROR) << "ANeuralNetworksModel_setOperandValueFromModel value model is invalid";
        return ANEURALNETWORKS_BAD_STATE;
    }
    if (index >= operandCount()) {
        LOG(ERROR) << "ANeuralNetworksModel_setOperandValueFromModel setting operand " << index
                   << " of " << operandCount();
        return ANEURALNETWORKS_BAD_DATA;
    }
    Operand& operand = mOperands[index];
    operand.lifetime = Operand::LifeTime::SUBGRAPH;
    operand.location = {
            .poolIndex = 0,
            .offset = static_cast<uint32_t>(mReferencedModels.size()),
            .length = 0,
    };
    mReferencedModels.push_back(value);
    mReferencedSubgraphsForValidation.push_back(value->makeModel().main);
    return ANEURALNETWORKS_NO_ERROR;
}

int ModelBuilder::setOperandSymmPerChannelQuantParams(
        uint32_t index, const ANeuralNetworksSymmPerChannelQuantParams& channelQuant) {
    if (badState("setOperandSymmPerChannelQuantParams")) {
        return ANEURALNETWORKS_BAD_STATE;
    }

    if (index >= operandCount()) {
        LOG(ERROR) << "ANeuralNetworksModel_setOperandSymmPerChannelQuantParams "
                   << "setting per-channel quantization parameters for operand " << index << " of "
                   << operandCount();
        return ANEURALNETWORKS_BAD_DATA;
    }
    Operand& operand = mOperands[index];

    NN_VALIDATE_NULL_OR_SIZED("setOperandSymmPerChannelQuantParams", channelQuant.scales,
                              channelQuant.scaleCount);
    Operand::SymmPerChannelQuantParams extraParams = {
            .scales = makeVector(channelQuant.scales, channelQuant.scaleCount),
            .channelDim = channelQuant.channelDim,
    };
    if (auto result = validateOperandSymmPerChannelQuantParams(
                operand, extraParams, "ANeuralNetworksModel_setOperandSymmPerChannelQuantParams");
        !result.ok()) {
        LOG(ERROR) << result.error();
        return ANEURALNETWORKS_BAD_DATA;
    }
    switch (operand.type) {
        case OperandType::TENSOR_QUANT8_SYMM_PER_CHANNEL:
            operand.extraParams = std::move(extraParams);
            break;
        default:
            LOG(ERROR) << "ANeuralNetworksModel_setOperandSymmPerChannelQuantParams "
                       << "invalid operand type " << static_cast<int32_t>(operand.type);
            return ANEURALNETWORKS_BAD_DATA;
    }
    return ANEURALNETWORKS_NO_ERROR;
}

int ModelBuilder::setOperandExtensionData(uint32_t index, const void* data, size_t length) {
    if (badState("setOperandExtensionData")) {
        return ANEURALNETWORKS_BAD_STATE;
    }

    if (index >= operandCount()) {
        LOG(ERROR) << "ANeuralNetworksModel_setOperandExtensionData "
                   << "setting extension data for operand " << index << " of " << operandCount();
        return ANEURALNETWORKS_BAD_DATA;
    }
    Operand& operand = mOperands[index];

    if (!isExtension(operand.type)) {
        LOG(ERROR) << "ANeuralNetworksModel_setOperandExtensionData "
                   << "setting extension data for a base operand type "
                   << static_cast<int32_t>(operand.type);
        return ANEURALNETWORKS_BAD_DATA;
    }

    NN_VALIDATE_NULL_OR_SIZED("setOperandExtensionData", data, length);
    if (data == nullptr) {
        operand.extraParams = {};
    } else {
        operand.extraParams = Operand::ExtensionParams(
                std::vector<uint8_t>(reinterpret_cast<const uint8_t*>(data),
                                     reinterpret_cast<const uint8_t*>(data) + length));
    }
    return ANEURALNETWORKS_NO_ERROR;
}

int ModelBuilder::copyLargeValuesToSharedMemory() {
    VLOG(MODEL) << __func__ << " has " << mLargeOperandValues.size() << " values.";
    if (!mLargeOperandValues.empty()) {
        // Calculate the size of the shared memory needed for all the large values.
        // Also sets the offset for each value within the memory.
        size_t poolSize = 0;
        for (LargeValue& l : mLargeOperandValues) {
            Operand& operand = mOperands[l.operandIndex];
            CHECK_EQ(operand.lifetime, Operand::LifeTime::CONSTANT_REFERENCE);
            poolSize += alignBytesNeeded(poolSize, operand.location.length);
            operand.location.offset = poolSize;
            poolSize += operand.location.length;
        }

        // Allocate the shared memory.
        int n;
        std::tie(n, mLargeValueMemory) = MemoryAshmem::create(poolSize);
        NN_RETURN_IF_ERROR(n);
        uint8_t* memoryPointer = mLargeValueMemory->getPointer();
        uint32_t poolIndex = mMemories.add(mLargeValueMemory.get());
        VLOG(MODEL) << "Allocated large value pool of size " << poolSize << " at index "
                    << poolIndex;

        // Copy the values to this memory.
        for (LargeValue& l : mLargeOperandValues) {
            Operand& operand = mOperands[l.operandIndex];
            operand.location.poolIndex = poolIndex;
            memcpy(memoryPointer + operand.location.offset, l.buffer, operand.location.length);
        }
    }

    return ANEURALNETWORKS_NO_ERROR;
}

int ModelBuilder::setOperandValueFromMemory(uint32_t index, const RuntimeMemory* memory,
                                            uint32_t offset, size_t length) {
    VLOG(MODEL) << __func__ << " for operand " << index << " offset " << offset << " size "
                << length;
    if (badState("setOperandValueFromMemory")) {
        return ANEURALNETWORKS_BAD_STATE;
    }

    if (index >= operandCount()) {
        LOG(ERROR) << "ANeuralNetworksModel_setOperandValueFromMemory setting operand " << index
                   << " of " << operandCount();
        return ANEURALNETWORKS_BAD_DATA;
    }
    Operand& operand = mOperands[index];
    if (TypeManager::get()->isTensorType(operand.type) && tensorHasUnspecifiedDimensions(operand)) {
        LOG(ERROR) << "ANeuralNetworksModel_setOperandValueFromMemory setting operand " << index
                   << " which has operand type that is not fully specified";
        return ANEURALNETWORKS_BAD_DATA;
    }
    uint32_t neededLength = TypeManager::get()->getSizeOfData(operand);
    if (neededLength != length) {
        LOG(ERROR) << "ANeuralNetworksModel_setOperandValueFromMemory setting " << length
                   << " bytes when needing " << neededLength;
        return ANEURALNETWORKS_BAD_DATA;
    }
    // Set compilation = nullptr to indicate that the memory is used for a model constant.
    // In this case, IOType::INPUT is a placeholder value that is ignored by the validator.
    if (!memory->getValidator().validate(/*compilation=*/nullptr, /*placeholder*/ IOType::INPUT,
                                         index, nullptr, offset, length)) {
        return ANEURALNETWORKS_BAD_DATA;
    }
    operand.lifetime = Operand::LifeTime::CONSTANT_REFERENCE;
    operand.location = {.poolIndex = mMemories.add(memory),
                        .offset = offset,
                        .length = static_cast<uint32_t>(length)};
    return ANEURALNETWORKS_NO_ERROR;
}

int ModelBuilder::addOperation(ANeuralNetworksOperationType type, uint32_t inputCount,
                               const uint32_t* inputs, uint32_t outputCount,
                               const uint32_t* outputs) {
    if (badState("addOperation")) {
        return ANEURALNETWORKS_BAD_STATE;
    }

    OperationType operationType = static_cast<OperationType>(type);
    if (isExtension(operationType) && !TypeManager::get()->areExtensionsAllowed()) {
        LOG(ERROR) << "Extensions are not supported for this process.";
        return ANEURALNETWORKS_BAD_DATA;
    }
    if (operationType == OperationType::OEM_OPERATION && !mHasOEMOperation) {
        LOG(WARNING) << "OEM_OPERATION is deprecated. Use Extensions instead.";
    }

    if (!isExtension(operationType)) {
        bool allowExperimental = false;
#ifdef NN_EXPERIMENTAL_FEATURE
        if (type >= BuiltinOperationResolver::kStartOfExperimentalOperations &&
            type < BuiltinOperationResolver::kStartOfExperimentalOperations +
                            BuiltinOperationResolver::kNumberOfExperimentalOperationTypes) {
            allowExperimental = true;
        }
#endif  // NN_EXPERIMENTAL_FEATURE
        if (!validCode(kNumberOfOperationTypes, kNumberOfOperationTypesOEM, type) &&
            !allowExperimental) {
            LOG(ERROR) << "ANeuralNetworksModel_addOperation invalid operation type " << type;
            return ANEURALNETWORKS_BAD_DATA;
        }
    } else {
        const Extension* extension;
        uint16_t extensionPrefix = getExtensionPrefix(static_cast<uint32_t>(operationType));
        if (!TypeManager::get()->getExtensionInfo(extensionPrefix, &extension)) {
            LOG(ERROR) << "Extension operation type " << operationType << " is not recognized";
            return ANEURALNETWORKS_BAD_DATA;
        }
    }

    NN_VALIDATE_NULL_OR_SIZED("addOperation", inputs, inputCount);
    NN_VALIDATE_NULL_OR_SIZED("addOperation", outputs, outputCount);
    Operation operation = {
            .type = operationType,
            .inputs = makeVector(inputs, inputCount),
            .outputs = makeVector(outputs, outputCount),
    };
    if (auto result = validateOperationButNotOperands(operation, mOperands,
                                                      mReferencedSubgraphsForValidation);
        !result.ok()) {
        LOG(ERROR) << "Invalid Operation: " << result.error();
        return ANEURALNETWORKS_BAD_DATA;
    }

    uint32_t operationIndex = operationCount();
    if (operationIndex >= MAX_NUMBER_OF_OPERATIONS) {
        LOG(ERROR) << "ANeuralNetworksModel_addOperation exceed max operations";
        return ANEURALNETWORKS_BAD_DATA;
    }

    mOperations.push_back(std::move(operation));
    mHasOEMOperation |= (operationType == OperationType::OEM_OPERATION);
    mHasExtensionOperation |= isExtension(operationType);
    mHasControlFlow |=
            (operationType == OperationType::IF || operationType == OperationType::WHILE);

    return ANEURALNETWORKS_NO_ERROR;
}

int ModelBuilder::identifyInputsAndOutputs(uint32_t inputCount, const uint32_t* inputs,
                                           uint32_t outputCount, const uint32_t* outputs) {
    if (badState("identifyInputsAndOutputs")) {
        return ANEURALNETWORKS_BAD_STATE;
    }

    NN_VALIDATE_NULL_OR_SIZED("identifyInputsAndOutputs", inputs, inputCount);
    if (auto result = validateOperandList(makeVector(inputs, inputCount), operandCount(),
                                          "ANeuralNetworksModel_identifyInputsAndOutputs inputs");
        !result.ok()) {
        LOG(ERROR) << result.error();
        return ANEURALNETWORKS_BAD_DATA;
    }
    NN_VALIDATE_NULL_OR_SIZED("identifyInputsAndOutputs", outputs, outputCount);
    if (auto result = validateOperandList(makeVector(outputs, outputCount), operandCount(),
                                          "ANeuralNetworksModel_identifyInputsAndOutputs outputs");
        !result.ok()) {
        LOG(ERROR) << result.error();
        return ANEURALNETWORKS_BAD_DATA;
    }

    // Makes a copy of the index list, validates the arguments, and changes
    // the lifetime info of the corresponding operand.
    auto setArguments = [&](std::vector<uint32_t>* indexVector, uint32_t indexCount,
                            const uint32_t* indexList, Operand::LifeTime lifetime) -> bool {
        indexVector->resize(indexCount);
        for (uint32_t i = 0; i < indexCount; i++) {
            const uint32_t operandIndex = indexList[i];
            if (operandIndex >= mOperands.size()) {
                LOG(ERROR) << "ANeuralNetworksModel_identifyInputsAndOutputs Can't set input or "
                              "output "
                              "to be "
                           << operandIndex << " as this exceeds the numbe of operands "
                           << mOperands.size();
                return false;
            }
            (*indexVector)[i] = operandIndex;
            Operand& operand = mOperands[operandIndex];
            if (operand.lifetime != Operand::LifeTime::TEMPORARY_VARIABLE) {
                LOG(ERROR) << "ANeuralNetworksModel_identifyInputsAndOutputs Can't set operand "
                           << operandIndex
                           << " to be an input or output.  Check that it's not a constant or "
                              "already an input or output";
                return false;
            }
            operand.lifetime = lifetime;
        }
        return true;
    };

    if (!setArguments(&mInputIndexes, inputCount, inputs, Operand::LifeTime::SUBGRAPH_INPUT) ||
        !setArguments(&mOutputIndexes, outputCount, outputs, Operand::LifeTime::SUBGRAPH_OUTPUT)) {
        return ANEURALNETWORKS_BAD_DATA;
    }

    return ANEURALNETWORKS_NO_ERROR;
}

int ModelBuilder::relaxComputationFloat32toFloat16(bool allow) {
    if (badState("relaxComputationFloat32toFloat16")) {
        return ANEURALNETWORKS_BAD_STATE;
    }

    mRelaxComputationFloat32toFloat16 = allow;

    return ANEURALNETWORKS_NO_ERROR;
}

int ModelBuilder::createCompilation(CompilationBuilder** compilation,
                                    const std::vector<std::shared_ptr<Device>>& devices,
                                    bool explicitDeviceList) {
    if (!mCompletedModel || mInvalidModel) {
        LOG(ERROR) << "ANeuralNetworksCompilation_create passed an unfinished or invalid model";
        *compilation = nullptr;
        return ANEURALNETWORKS_BAD_STATE;
    }
    *compilation = new (std::nothrow) CompilationBuilder(this, devices, explicitDeviceList);
    return (*compilation ? ANEURALNETWORKS_NO_ERROR : ANEURALNETWORKS_OUT_OF_MEMORY);
}

int ModelBuilder::finish() {
    if (mCompletedModel) {
        LOG(ERROR) << "ANeuralNetworksModel_finish called more than once";
        return ANEURALNETWORKS_BAD_STATE;
    }
    if (mInvalidModel) {
        LOG(ERROR) << "ANeuralNetworksModel_finish called on an invalid model";
        return ANEURALNETWORKS_BAD_STATE;
    }

    int n = copyLargeValuesToSharedMemory();
    if (n != ANEURALNETWORKS_NO_ERROR) {
        return n;
    }

    // We sort the operations so that they will be in the appropriate
    // order for a single-threaded, op at a time execution.
    // TODO: we don't need this if we always run the partitioner.
    if (!sortIntoRunOrder()) {
        // We expect sortIntoRunOrder() to have logged an appropriate error message.
        mInvalidModel = true;
        return ANEURALNETWORKS_BAD_DATA;
    }

    // TODO: Modify validation so that it can be called without creating a Model.
    // NOTE: Must sortIntoRunOrder() before validation; validator expects operations
    //       to have been sorted.
    // NOTE: Must copyLargeValuesToSharedMemory() before validation; otherwise,
    //       a CONSTANT_REFERENCE operand will not have correct .poolIndex, and
    //       validation will not work properly.
    const Model modelForValidation = makeModel();
    const auto maybeVersion = validate(modelForValidation);
    if (!maybeVersion.ok()) {
        LOG(ERROR) << "ANeuralNetworksModel_finish called on invalid model: "
                   << maybeVersion.error();
        mInvalidModel = true;
        return ANEURALNETWORKS_BAD_DATA;
    }
    if (!isCompliantVersion(maybeVersion.value(), DeviceManager::get()->getRuntimeVersion())) {
        LOG(ERROR) << "ANeuralNetworksModel_finish called on a model that is newer what is "
                      "allowed. Model version needed: "
                   << maybeVersion.value() << ", current runtime version supported: "
                   << DeviceManager::get()->getRuntimeVersion();
        mInvalidModel = true;
        return ANEURALNETWORKS_BAD_DATA;
    }
    if (VLOG_IS_ON(MODEL)) {
        graphDump("ModelBuilder::finish", modelForValidation, nullptr);
    }

    removeTrailingArgumentsWithDefaultValues();
    simplifyModel();

    mCompletedModel = true;
    CHECK(calcModelArchHash(modelForValidation, mModelArchHash))
            << "Failed to calculate model arch hash";
    return ANEURALNETWORKS_NO_ERROR;
}

static void logRemoval(const Operation& operation, uint32_t count,
                       const std::vector<Operand>& operands) {
    std::ostringstream message;
    message << "Operation " << operation.type << " with inputs {";
    for (uint32_t i = 0; i < operation.inputs.size(); ++i) {
        if (i != 0) {
            message << ", ";
        }
        message << operands[operation.inputs[i]].type;
    }
    message << "} has trailing optional inputs set to default values. Removing " << count
            << " trailing inputs.";
    VLOG(MODEL) << message.str();
}

void ModelBuilder::removeTrailingArgumentsWithDefaultValues() {
    for (Operation& operation : mOperations) {
        const uint32_t count = getNumTrailingArgumentsToRemove(operation);
        if (count == 0) {
            continue;
        }
        if (VLOG_IS_ON(MODEL)) {
            logRemoval(operation, count, mOperands);
        }
        const uint32_t inputCount = operation.inputs.size();
        CHECK_LT(count, inputCount);
        const uint32_t newInputCount = inputCount - count;
        operation.inputs.resize(newInputCount);
    }
}

// See countMatchingTrailingArguments().
enum class TailSpec {
    BOOL_FALSE,
    INT32_ONE,
    INT32_NEGATIVE_ONE,
};

// See countMatchingTrailingArguments().
static bool matchesSpec(TailSpec spec, const Operand& operand,
                        const std::vector<uint8_t>& mSmallOperandValues) {
    const void* valuePtr = nullptr;
    if (operand.lifetime == Operand::LifeTime::CONSTANT_COPY) {
        valuePtr = static_cast<const void*>(&mSmallOperandValues[operand.location.offset]);
    } else if (operand.lifetime == Operand::LifeTime::POINTER) {
        valuePtr = std::get<const void*>(operand.location.pointer);
    } else {
        // CONSTANT_REFERENCE operands are not supported to avoid mapping memory
        // during compilation.
        return false;
    }
    switch (spec) {
        case TailSpec::BOOL_FALSE:
            return operand.type == OperandType::BOOL &&
                   *static_cast<const bool8*>(valuePtr) == false;
        case TailSpec::INT32_ONE:
            return operand.type == OperandType::INT32 &&
                   *static_cast<const int32_t*>(valuePtr) == 1;
        case TailSpec::INT32_NEGATIVE_ONE:
            return operand.type == OperandType::INT32 &&
                   *static_cast<const int32_t*>(valuePtr) == -1;
        default:
            CHECK(false) << "Unhandled TailSpec: " << static_cast<int>(spec);
    }
}

// Returns the number of trailing operation inputs that match the specification.
//
// Example:
//     opeation.inputs = {BOOL_TRUE, BOOL_TRUE,  INT32_ONE, INT32_NEGATIVE_ONE}
//     tail            =            {BOOL_FALSE, INT32_ONE, INT32_NEGATIVE_ONE}
//     tailStartIndex  = 1    matching elements: ^^^^^^^^^  ^^^^^^^^^^^^^^^^^^
static uint32_t countMatchingTrailingArguments(uint32_t tailStartIndex,
                                               const std::vector<TailSpec>& tail,
                                               const Operation& operation,
                                               const std::vector<Operand>& operands,
                                               const std::vector<uint8_t>& smallOperandValues) {
    const uint32_t inputCount = operation.inputs.size();
    uint32_t count = 0;
    for (uint32_t i = inputCount - 1; i >= tailStartIndex; --i) {
        const Operand& operand = operands[operation.inputs[i]];
        if (!matchesSpec(tail[i - tailStartIndex], operand, smallOperandValues)) {
            break;
        }
        ++count;
    }
    return count;
}

uint32_t ModelBuilder::getNumTrailingArgumentsToRemove(const Operation& operation) const {
    const uint32_t inputCount = operation.inputs.size();
    auto getCount = [this, &operation](uint32_t tailStartIndex, const std::vector<TailSpec>& tail) {
        return countMatchingTrailingArguments(tailStartIndex, tail, operation, mOperands,
                                              mSmallOperandValues);
    };
    using TS = TailSpec;
    // Check if the operation has optional arguments that might be set to default
    // values. Skip the counting if no optional arguments are present.
    switch (operation.type) {
        case OperationType::AVERAGE_POOL_2D: {
            if (inputCount == 11 && mOperands[operation.inputs[7]].type == OperandType::INT32) {
                // Explicit padding
                // API level 29: 10 to 11 inputs
                // API level 27: 10 inputs
                return getCount(10, {TS::BOOL_FALSE});
            } else if (inputCount == 8 &&
                       mOperands[operation.inputs[7]].type == OperandType::BOOL) {
                // Implicit padding
                // API level 29: 7 to 8 inputs
                // API level 27: 7 inputs
                return getCount(7, {TS::BOOL_FALSE});
            }
        } break;
        case OperationType::CONV_2D: {
            if (10 < inputCount && inputCount <= 13 &&
                mOperands[operation.inputs[7]].type == OperandType::INT32) {
                // Explicit padding
                // API level 29: 10 to 13 inputs
                // API level 27: 10 inputs
                uint32_t count = getCount(10, {TS::BOOL_FALSE, TS::INT32_ONE, TS::INT32_ONE});
                // Inputs 11 and 12 must come together.
                return inputCount - count == 12 ? 0 : count;
            } else if (7 < inputCount && inputCount <= 10 &&
                       mOperands[operation.inputs[7]].type == OperandType::BOOL) {
                // Implicit padding
                // API level 29: 7 to 10 inputs
                // API level 27: 7 inputs
                uint32_t count = getCount(7, {TS::BOOL_FALSE, TS::INT32_ONE, TS::INT32_ONE});
                // Inputs 8 and 9 must come together.
                return inputCount - count == 9 ? 0 : count;
            }
        } break;
        case OperationType::DEPTHWISE_CONV_2D: {
            if (11 < inputCount && inputCount <= 14 &&
                mOperands[operation.inputs[8]].type == OperandType::INT32) {
                // Explicit padding
                // API level 29: 11 to 14 inputs
                // API level 27: 11 inputs
                uint32_t count = getCount(11, {TS::BOOL_FALSE, TS::INT32_ONE, TS::INT32_ONE});
                // Inputs 12 and 13 must come together.
                return inputCount - count == 13 ? 0 : count;
            } else if (8 < inputCount && inputCount <= 11 &&
                       mOperands[operation.inputs[8]].type == OperandType::BOOL) {
                // Implicit padding
                // API level 29: 8 to 11 inputs
                // API level 27: 8 inputs
                uint32_t count = getCount(8, {TS::BOOL_FALSE, TS::INT32_ONE, TS::INT32_ONE});
                // Inputs 9 and 10 must come together.
                return inputCount - count == 10 ? 0 : count;
            }
        } break;
        case OperationType::DEPTH_TO_SPACE: {
            if (inputCount == 3) {
                // API level 29: 2 to 3 inputs
                // API level 27: 2 inputs
                return getCount(2, {TS::BOOL_FALSE});
            }
        } break;
        case OperationType::L2_NORMALIZATION: {
            if (inputCount == 2) {
                // API level 29: 1 to 2 inputs
                // API level 27: 1 inputs
                return getCount(1, {TS::INT32_NEGATIVE_ONE});
            }
        } break;
        case OperationType::L2_POOL_2D: {
            if (inputCount == 11 && mOperands[operation.inputs[7]].type == OperandType::INT32) {
                // Explicit padding
                // API level 29: 10 to 11 inputs
                // API level 27: 10 inputs
                return getCount(10, {TS::BOOL_FALSE});
            } else if (inputCount == 8 &&
                       mOperands[operation.inputs[7]].type == OperandType::BOOL) {
                // Implicit padding
                // API level 29: 7 to 8 inputs
                // API level 27: 7 inputs
                return getCount(7, {TS::BOOL_FALSE});
            }
        } break;
        case OperationType::LOCAL_RESPONSE_NORMALIZATION: {
            if (inputCount == 6) {
                // API level 29: 5 to 6 inputs
                // API level 27: 5 inputs
                return getCount(5, {TS::INT32_NEGATIVE_ONE});
            }
        } break;
        case OperationType::MAX_POOL_2D: {
            if (inputCount == 11 && mOperands[operation.inputs[7]].type == OperandType::INT32) {
                // Explicit padding
                // API level 29: 10 to 11 inputs
                // API level 27: 10 inputs
                return getCount(10, {TS::BOOL_FALSE});
            } else if (inputCount == 8 &&
                       mOperands[operation.inputs[7]].type == OperandType::BOOL) {
                // Implicit padding
                // API level 29: 7 to 8 inputs
                // API level 27: 7 inputs
                return getCount(7, {TS::BOOL_FALSE});
            }
        } break;
        case OperationType::RESIZE_BILINEAR: {
            if (3 < inputCount && inputCount <= 6) {
                // By shape:
                //     API level 30: 3 to 6 inputs
                //     API level 29: 3 to 4 inputs
                //     API level 27: 3 inputs
                // By scale:
                //     API level 30: 3 to 6 inputs
                //     API level 29: 3 to 4 inputs
                return getCount(3, {TS::BOOL_FALSE, TS::BOOL_FALSE, TS::BOOL_FALSE});
            }
        } break;
        case OperationType::SOFTMAX: {
            if (inputCount == 3) {
                // API level 29: 2 to 3 inputs
                // API level 27: 2 inputs
                return getCount(2, {TS::INT32_NEGATIVE_ONE});
            }
        } break;
        case OperationType::SPACE_TO_DEPTH: {
            if (inputCount == 3) {
                // API level 29: 2 to 3 inputs
                // API level 27: 2 inputs
                return getCount(2, {TS::BOOL_FALSE});
            }
        } break;
        case OperationType::BATCH_TO_SPACE_ND: {
            if (inputCount == 3) {
                // API level 29: 2 to 3 inputs
                // API level 28: 2 inputs
                return getCount(2, {TS::BOOL_FALSE});
            }
        } break;
        case OperationType::SPACE_TO_BATCH_ND: {
            if (inputCount == 4) {
                // API level 29: 3 to 4 inputs
                // API level 28: 3 inputs
                return getCount(3, {TS::BOOL_FALSE});
            }
        } break;
        case OperationType::RESIZE_NEAREST_NEIGHBOR: {
            if (4 < inputCount && inputCount <= 6) {
                // By shape or scale
                // API level 30: 4 to 6 inputs
                // API level 29: 4 inputs
                return getCount(4, {TS::BOOL_FALSE, TS::BOOL_FALSE});
            }
        } break;
        default: {
            // Do nothing.
        } break;
    }
    // No trailing optional arguments to check.
    return 0;
}

bool ModelBuilder::sortIntoRunOrder() {
    // Note that this may be called before the model has been
    // validated, so we must code defensively.  However, we can assume
    // an Operation's inputs and outputs have legal indices -- this
    // should have been checked in addOperation().

    if (!mSortedOperationIndexMap.empty()) {
        LOG(ERROR) << "Operations were already sorted into run order.";
        return true;
    }

    // Tracks the operations that can be executed.
    std::vector<uint32_t> sortedOperationIndexMap;
    std::vector<uint32_t> opsReadyToRun;
    std::vector<Operation> runOrder;

    // Tracks how many inputs are needed for each operation to be ready to run.
    std::multimap<uint32_t, uint32_t> operandToOperations;
    std::vector<uint32_t> unknownInputCount(operationCount());
    for (uint32_t operationIndex = 0; operationIndex < operationCount(); operationIndex++) {
        uint32_t& count = unknownInputCount[operationIndex];
        count = 0;
        for (uint32_t operandIndex : mOperations[operationIndex].inputs) {
            auto lifetime = mOperands[operandIndex].lifetime;
            if (lifetime == Operand::LifeTime::TEMPORARY_VARIABLE ||
                lifetime == Operand::LifeTime::SUBGRAPH_OUTPUT) {
                count++;
                operandToOperations.insert(
                        std::pair<uint32_t, uint32_t>(operandIndex, operationIndex));
            }
        }
        if (count == 0) {
            opsReadyToRun.push_back(operationIndex);
        }
    }

    while (opsReadyToRun.size() > 0) {
        // Execute the next op
        int opIndex = opsReadyToRun.back();
        opsReadyToRun.pop_back();
        const Operation& operation = mOperations[opIndex];

        runOrder.push_back(mOperations[opIndex]);
        sortedOperationIndexMap.push_back(opIndex);

        // Mark all its outputs as known.
        for (uint32_t operandIndex : operation.outputs) {
            auto range = operandToOperations.equal_range(operandIndex);
            for (auto i = range.first; i != range.second; i++) {
                uint32_t& count = unknownInputCount[i->second];
                if (--count == 0) {
                    opsReadyToRun.push_back(i->second);
                }
            }
        }
    }

    if (runOrder.size() != mOperations.size()) {
        CHECK_LT(runOrder.size(), mOperations.size());
        // Graph must contain at least one cycle or one never-written
        // operand, because there is at least one Operation that never
        // became ready.
        LOG(ERROR) << "Graph contains at least one cycle or one never-written operand";
        return false;
    }

    mSortedOperationIndexMap = std::move(sortedOperationIndexMap);
    mOperations = std::move(runOrder);
    return true;
}

// A helper class to simplify state management when creating a Model.
class ModelBuilder::ModelMaker {
   public:
    static Model run(const ModelBuilder* model, bool simplifyModel);

   private:
    static Model::Subgraph makeSubgraph(const ModelBuilder* model);
    explicit ModelMaker(bool simplifyModel) : mSimplifyModel(simplifyModel) {}
    Model makeModel(const ModelBuilder* mainModel);
    uint32_t addSubgraph(const ModelBuilder* refModel);
    void updateOperandLocations(const ModelBuilder* refModel, Model::Subgraph* subgraph);
    void addExtensions(const ModelBuilder* model);
    void addExtensionWithPrefix(uint16_t prefix);

    bool mSimplifyModel;
    std::vector<Model::Subgraph> mRefSubgraphs;
    Model::OperandValues mOperandValues;
    MemoryTracker mMemories;
    std::vector<ExtensionNameAndPrefix> mExtensionNameToPrefix;
    std::set<uint16_t> mPrefixSet;
};

void ModelBuilder::simplifyModel() {
    mSimplifyModel = true;
}

Model ModelBuilder::makeModel() const {
    // TODO: Cache the Model to speed up subsequent calls.
    return ModelMaker::run(this, mSimplifyModel);
}

Model ModelBuilder::ModelMaker::run(const ModelBuilder* model, bool simplifyModel) {
    // run() ensures the state of ModelMaker is destroyed after the call.
    return ModelMaker(simplifyModel).makeModel(model);
}

Model ModelBuilder::ModelMaker::makeModel(const ModelBuilder* mainModel) {
    addExtensions(mainModel);
    Model model;
    model.main = makeSubgraph(mainModel);
    updateOperandLocations(mainModel, &model.main);
    model.referenced = std::move(mRefSubgraphs);
    model.operandValues = std::move(mOperandValues);
    model.pools.reserve(mMemories.size());
    std::transform(mMemories.begin(), mMemories.end(), std::back_inserter(model.pools),
                   [](const RuntimeMemory* m) { return m->getMemory(); });
    model.relaxComputationFloat32toFloat16 = mainModel->mRelaxComputationFloat32toFloat16;
    model.extensionNameToPrefix = std::move(mExtensionNameToPrefix);
    if (mSimplifyModel) {
        removeDeadOperands(&model);
    }
    return model;
}

Model::Subgraph ModelBuilder::ModelMaker::makeSubgraph(const ModelBuilder* model) {
    Model::Subgraph subgraph;
    subgraph.operands = model->mOperands;
    subgraph.operations = model->mOperations;
    subgraph.inputIndexes = model->mInputIndexes;
    subgraph.outputIndexes = model->mOutputIndexes;
    return subgraph;
}

void ModelBuilder::ModelMaker::updateOperandLocations(const ModelBuilder* refModel,
                                                      Model::Subgraph* subgraph) {
    for (Operand& operand : subgraph->operands) {
        if (operand.lifetime == Operand::LifeTime::CONSTANT_COPY) {
            uint32_t valueLength = operand.location.length;
            uint32_t originalOffset = operand.location.offset;
            operand.location = mOperandValues.append(&refModel->mSmallOperandValues[originalOffset],
                                                     valueLength);
        } else if (operand.lifetime == Operand::LifeTime::CONSTANT_REFERENCE) {
            uint32_t originalPoolIndex = operand.location.poolIndex;
            operand.location.poolIndex = mMemories.add(refModel->mMemories[originalPoolIndex]);
        }
    }
    // Do recursive calls at the end to improve locality of mOperandValues.
    for (Operand& operand : subgraph->operands) {
        if (operand.lifetime == Operand::LifeTime::SUBGRAPH) {
            uint32_t refModelIndex = operand.location.offset;
            // TODO(b/147875885): Avoid creating duplicate refSubgraphs when
            // a single refModel is referenced multiple times.
            operand.location.offset = addSubgraph(refModel->mReferencedModels[refModelIndex]);
        }
    }
}

uint32_t ModelBuilder::ModelMaker::addSubgraph(const ModelBuilder* refModel) {
    uint32_t index = mRefSubgraphs.size();
    mRefSubgraphs.push_back(makeSubgraph(refModel));
    updateOperandLocations(refModel, &mRefSubgraphs.back());
    return index;
}

void ModelBuilder::ModelMaker::addExtensions(const ModelBuilder* model) {
    for (const auto& operand : model->mOperands) {
        if (isExtension(operand.type)) {
            addExtensionWithPrefix(static_cast<uint32_t>(operand.type) >> kExtensionTypeBits);
        }
    }
    for (const auto& operation : model->mOperations) {
        if (isExtension(operation.type)) {
            addExtensionWithPrefix(static_cast<uint32_t>(operation.type) >> kExtensionTypeBits);
        }
    }
    for (const auto& refModel : model->mReferencedModels) {
        addExtensions(refModel);
    }
}

void ModelBuilder::ModelMaker::addExtensionWithPrefix(uint16_t prefix) {
    if (!mPrefixSet.insert(prefix).second) {
        return;
    }
    const Extension* extension;
    CHECK(TypeManager::get()->getExtensionInfo(prefix, &extension));
    mExtensionNameToPrefix.push_back({
            .name = extension->name,
            .prefix = prefix,
    });
}

const uint8_t* ModelBuilder::getModelArchHash() const {
    CHECK(mCompletedModel) << "Calling getModelArchHash on non completed model";
    return mModelArchHash;
}

#undef NN_VALIDATE_NULL_OR_SIZED

}  // namespace nn
}  // namespace android
